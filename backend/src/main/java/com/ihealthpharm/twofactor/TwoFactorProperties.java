package com.ihealthpharm.twofactor;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import lombok.Getter;

/**
 * The tunable numbers behind two-factor, in one place so no rule is written as
 * a literal somewhere in the middle of a service.
 *
 * <p>All of them are plain configuration rather than secrets — the signing key
 * and mail password come from the environment, not from here.
 */
@Component
@Getter
public class TwoFactorProperties {

	@Value("${twofactor.code-length:6}")
	private int codeLength;

	@Value("${twofactor.code-ttl-seconds:180}")
	private int codeTtlSeconds;

	/**
	 * How long the enrolment handle lasts — the window in which the user types
	 * their address. Separate from the code, because a minute to read a code is
	 * reasonable and a minute to think of and type an address is not.
	 */
	@Value("${twofactor.handle-ttl-seconds:600}")
	private int handleTtlSeconds;

	/** Wrong answers allowed on one challenge before it stops accepting any. */
	@Value("${twofactor.max-attempts:5}")
	private int maxAttempts;

	@Value("${twofactor.trusted-device-days:1}")
	private int trustedDeviceDays;

	/**
	 * Codes issued per account per hour. Counts consumed and expired challenges
	 * too, so answering one does not hand back budget to someone flooding a
	 * mailbox.
	 */
	@Value("${twofactor.max-codes-per-hour:10}")
	private int maxCodesPerHour;

	@Value("${twofactor.challenge-retention-days:7}")
	private int challengeRetentionDays;

	/**
	 * Development convenience: log the code instead of mailing it when mail is
	 * not configured at all. See
	 * {@code TwoFactorMailService} — it refuses to engage whenever a mail
	 * password is present, because a one-time code in a log file is a password
	 * in a log file.
	 */
	@Value("${twofactor.log-codes-when-mail-unconfigured:false}")
	private boolean logCodesWhenMailUnconfigured;

	/**
	 * The password the two-factor sender authenticates with. Blank whenever
	 * neither TWOFACTOR_MAIL_PASSWORD nor MAIL_PASSWORD has been supplied, which
	 * is what puts the code in the log instead of an inbox.
	 */
	@Value("${twofactor.mail.password:${spring.mail.password:}}")
	private String mailPassword;

	/** True when outgoing mail has credentials and can actually be sent. */
	public boolean isMailConfigured() {
		return mailPassword != null && !mailPassword.trim().isEmpty();
	}

	public long codeTtlMillis() {
		return codeTtlSeconds * 1000L;
	}

	public long handleTtlMillis() {
		return handleTtlSeconds * 1000L;
	}

	public long trustedDeviceMillis() {
		return trustedDeviceDays * 24L * 60L * 60L * 1000L;
	}

	/** False when remembering is switched off, so every login is challenged. */
	public boolean isDeviceTrustAllowed() {
		return trustedDeviceDays > 0;
	}
}
