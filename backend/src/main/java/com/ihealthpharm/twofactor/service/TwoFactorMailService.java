package com.ihealthpharm.twofactor.service;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import javax.mail.internet.MimeMessage;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.ui.freemarker.FreeMarkerTemplateUtils;

import com.ihealthpharm.exception.IHealthPharmException;
import com.ihealthpharm.twofactor.TwoFactorMailConfig;
import com.ihealthpharm.twofactor.TwoFactorProperties;
import com.ihealthpharm.twofactor.dto.IssuedChallenge;

import freemarker.template.Configuration;
import freemarker.template.Template;
import lombok.extern.slf4j.Slf4j;

/**
 * Delivers one-time codes.
 *
 * <p>Sending happens on the request thread on purpose. The alternative — hand
 * it to a background thread and return immediately — means a user whose code
 * never sends waits at a code box for a message that is not coming, with
 * nothing to tell them so. SMTP timeouts are bounded in
 * {@code application.properties} to five seconds, so the worst case is a slow
 * login that then reports the failure honestly.
 */
@Service
@Slf4j
public class TwoFactorMailService {

	/** The two-factor sender, not the application-wide one. See TwoFactorMailConfig. */
	@Autowired
	@Qualifier(TwoFactorMailConfig.SENDER_BEAN)
	private JavaMailSender javaMailSender;

	@Autowired
	private Configuration freemarkerConfiguration;

	@Autowired
	private TwoFactorProperties properties;

	/** Whatever account the two-factor sender authenticates as, so From matches it. */
	@Value("${twofactor.mail.username:${spring.mail.username:}}")
	private String fromAddress;

	/** Shown as the sender name beside the address. */
	@Value("${twofactor.mail.from-name:Doc Pharma}")
	private String fromName;

	@Value("${twofactor.pharmacy-name:Doc Pharma}")
	private String pharmacyName;

	/**
	 * Sends a code to the address the challenge was issued against.
	 *
	 * <p>The challenge carries its own destination and purpose, so there is one
	 * argument and no call site can pair a code with the wrong address.
	 */
	public void sendCode(IssuedChallenge challenge) {
		final String toEmail = challenge.deliveryAddress();
		final boolean isEnrolment = challenge.isEnrolment();

		if (!properties.isMailConfigured()) {
			logCodeInsteadOfSending(toEmail, challenge, isEnrolment);
			return;
		}

		try {
			Map<String, Object> model = new HashMap<>();
			model.put("code", challenge.revealCode());
			model.put("expiryMinutes", Math.max(1, properties.getCodeTtlSeconds() / 60));
			model.put("purposeIsEnrolment", isEnrolment);
			model.put("pharmacyName", pharmacyName);

			Template template = freemarkerConfiguration.getTemplate("two-factor-code.ftl");
			String html = FreeMarkerTemplateUtils.processTemplateIntoString(template, model);

			MimeMessage message = javaMailSender.createMimeMessage();
			MimeMessageHelper helper = new MimeMessageHelper(message,
					MimeMessageHelper.MULTIPART_MODE_NO, StandardCharsets.UTF_8.name());
			helper.setTo(toEmail);
			helper.setFrom(new javax.mail.internet.InternetAddress(fromAddress, fromName));

			// Marks this as machine-generated. Well-behaved mail systems use it
			// to suppress out-of-office and other auto-replies, which would
			// otherwise bounce back into a mailbox nobody reads.
			message.setHeader("Auto-Submitted", "auto-generated");
			helper.setSubject(isEnrolment
					? "Confirm this email address"
					: "Your sign-in code");
			helper.setText(html, true);

			javaMailSender.send(message);

			// The address is never logged in full: these logs are read by more
			// people than the mailbox belongs to.
			log.info("Two-factor code sent to {}", challenge.getMaskedEmail());

		} catch (Exception e) {
			// Deliberately not rethrowing the cause: an SMTP error can carry the
			// server's own diagnostics, which do not belong in a user's browser.
			log.error("Two-factor code could not be sent to {}: {}",
					challenge.getMaskedEmail(), e.getMessage());
			throw new IHealthPharmException(
					"The code could not be sent. Try again, and tell your administrator if it keeps failing.",
					HttpStatus.SERVICE_UNAVAILABLE);
		}
	}

	/**
	 * Development fallback: no mail credentials, so put the code where a
	 * developer can read it.
	 *
	 * <p>Guarded twice — it runs only when {@code MAIL_PASSWORD} is genuinely
	 * absent, and only when the property switching it on is set. A one-time code
	 * in a log file is a password in a log file, so a server with working mail
	 * can never reach this path regardless of configuration.
	 */
	private void logCodeInsteadOfSending(String toEmail, IssuedChallenge challenge,
			boolean isEnrolment) {
		if (!properties.isLogCodesWhenMailUnconfigured()) {
			log.error("No MAIL_PASSWORD is configured, so the two-factor code for {} cannot be sent.",
					challenge.getMaskedEmail());
			throw new IHealthPharmException(
					"Email is not configured on this server, so codes cannot be sent."
							+ " Tell your administrator.",
					HttpStatus.SERVICE_UNAVAILABLE);
		}

		log.warn("================ DEVELOPMENT ONLY ================");
		log.warn("No MAIL_PASSWORD set, so this code was NOT emailed.");
		log.warn("  {} code for {}: {}", isEnrolment ? "Address confirmation" : "Sign-in",
				toEmail, challenge.revealCode());
		log.warn("  Set MAIL_PASSWORD to send real email instead.");
		log.warn("==================================================");
	}
}
