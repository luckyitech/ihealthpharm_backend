package com.ihealthpharm.twofactor.model;

import java.util.Date;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;

import com.ihealthpharm.masters.model.AuditModel;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * One row per one-time code issued, either to prove a login or to prove that a
 * newly entered address can be read by the person entering it.
 *
 * <p>The code itself is never stored. {@link #codeHash} holds a BCrypt hash of
 * it, for the same reason passwords are hashed: this table is readable by
 * anyone who can read the database, and a six-digit code sitting in plaintext
 * is a password sitting in plaintext.
 *
 * <p>{@link #challengeToken} is the handle the browser holds between the two
 * halves of a login. It is opaque and carries no username, so the step before
 * authentication completes leaks nothing about who is logging in.
 */
@Data
@Entity(name = "two_factor_challenge")
@EqualsAndHashCode(of = "twoFactorChallengeId", callSuper = false)
public class TwoFactorChallengeModel extends AuditModel {

	private static final long serialVersionUID = 4471933017155642889L;

	/** Proving a login for an account already enrolled. */
	public static final String PURPOSE_LOGIN = "LOGIN";

	/** Proving a newly entered address, before it is trusted with codes. */
	public static final String PURPOSE_EMAIL_VERIFY = "EMAIL_VERIFY";

	/**
	 * Carries identity between a correct password and the address the user is
	 * about to type, for an account that is enrolled but has no confirmed
	 * address yet.
	 *
	 * <p>It is a challenge with no answer: there is nothing to send a code to
	 * yet, so {@code codeHash} holds an unguessable value that is never checked.
	 * The row is used for the handle, its expiry and its single-use flag, which
	 * are exactly the properties this step needs — rather than inventing a
	 * second short-lived-token mechanism alongside this one.
	 */
	public static final String PURPOSE_EMAIL_CAPTURE = "EMAIL_CAPTURE";

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "TWO_FACTOR_CHALLENGE_ID")
	private Integer twoFactorChallengeId;

	@Column(name = "EMPLOYEE_CREDENTIALS_ID", nullable = false)
	private Integer employeeCredentialsId;

	/** {@link #PURPOSE_LOGIN} or {@link #PURPOSE_EMAIL_VERIFY}. */
	@Column(name = "PURPOSE", length = 20, nullable = false)
	private String purpose;

	@Column(name = "CHALLENGE_TOKEN", length = 64, nullable = false)
	private String challengeToken;

	@Column(name = "CODE_HASH", length = 100, nullable = false)
	private String codeHash;

	/**
	 * Where the code was sent. For {@link #PURPOSE_EMAIL_VERIFY} this is the
	 * address being proven, which is deliberately not yet on the account.
	 */
	@Column(name = "TARGET_EMAIL", length = 100)
	private String targetEmail;

	@Column(name = "EXPIRES_TS", nullable = false)
	private Date expiresTimestamp;

	@Column(name = "ATTEMPT_COUNT", nullable = false)
	private Integer attemptCount = 0;

	/** Set when the code is accepted. A challenge is single use. */
	@Column(name = "CONSUMED_TS")
	private Date consumedTimestamp;

	@Column(name = "AUDIT_ID")
	private Integer auditId;

	/** True when this challenge can still be answered. */
	public boolean isOpen(Date now, int maximumAttempts) {
		return consumedTimestamp == null
				&& expiresTimestamp != null && expiresTimestamp.after(now)
				&& attemptCount != null && attemptCount < maximumAttempts;
	}
}
