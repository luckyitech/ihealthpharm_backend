package com.ihealthpharm.twofactor;

import java.util.Properties;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

/**
 * A mail sender of its own for two-factor codes.
 *
 * <p>Codes go out from a dedicated mailbox — {@code security@docpharmkenya.com}
 * — rather than the {@code request@} account the quotation and purchase-order
 * mail uses. Three reasons, in order of how much they matter:
 *
 * <ul>
 *   <li><b>Staff can recognise it.</b> Sign-in codes always arrive from one
 *       address and nothing else does. A code appearing to come from anywhere
 *       else is a phishing attempt, and that is a rule a person can actually
 *       hold in their head.</li>
 *   <li><b>The channels are separated.</b> Somebody who gets into the
 *       purchasing mailbox cannot send anything that looks like a sign-in
 *       code, and vice versa.</li>
 *   <li><b>Replies go nowhere useful anyway</b>, so they should not land in a
 *       mailbox somebody is working from.</li>
 * </ul>
 *
 * <p>This needs a separate sender rather than just a different From header,
 * because mail servers generally refuse to send a From that does not match the
 * account that authenticated — so the From address and the credentials have to
 * travel together.
 *
 * <p>Every setting falls back to the application's main {@code spring.mail.*}
 * configuration, so leaving the {@code twofactor.mail.*} properties unset keeps
 * the previous behaviour instead of breaking mail.
 */
@Configuration
public class TwoFactorMailConfig {

	public static final String SENDER_BEAN = "twoFactorMailSender";

	@Value("${twofactor.mail.host:${spring.mail.host:}}")
	private String host;

	@Value("${twofactor.mail.port:${spring.mail.port:465}}")
	private int port;

	@Value("${twofactor.mail.username:${spring.mail.username:}}")
	private String username;

	@Value("${twofactor.mail.password:${spring.mail.password:}}")
	private String password;

	@Bean(name = SENDER_BEAN)
	public JavaMailSender twoFactorMailSender() {
		JavaMailSenderImpl sender = new JavaMailSenderImpl();
		sender.setHost(host);
		sender.setPort(port);
		sender.setUsername(username);
		sender.setPassword(password);

		Properties mail = sender.getJavaMailProperties();
		mail.put("mail.smtp.auth", "true");
		mail.put("mail.smtp.socketFactory.port", String.valueOf(port));
		mail.put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory");

		// Bounded so a slow or unreachable mail server cannot hang a login.
		// Without these JavaMail waits on the OS default - minutes - and the
		// user sees a frozen browser rather than an error they can act on.
		mail.put("mail.smtp.connectiontimeout", "5000");
		mail.put("mail.smtp.timeout", "5000");
		mail.put("mail.smtp.writetimeout", "5000");

		return sender;
	}
}
