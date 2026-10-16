package com.ihealthpharm.twofactor.service;

import com.ihealthpharm.masters.model.EmployeeCredentialsModel;
import com.ihealthpharm.twofactor.dto.IssuedChallenge;
import com.ihealthpharm.twofactor.dto.TwoFactorResetResult;
import com.ihealthpharm.twofactor.model.TwoFactorChallengeModel;

/**
 * Issuing and answering the one-time codes, and remembering machines.
 *
 * <p>Nothing here decides whether an account needs a code — that is the
 * authentication flow's judgement. This is the mechanism it uses.
 */
public interface TwoFactorService {

	/**
	 * Issues a login code to an enrolled account's confirmed address and closes
	 * any code already outstanding, so an older one left in an inbox stops
	 * working.
	 *
	 * @throws com.ihealthpharm.exception.IHealthPharmException if the account is
	 *         not enrolled, or has been issued too many codes this hour
	 */
	IssuedChallenge issueLoginChallenge(EmployeeCredentialsModel credentials);

	/**
	 * Stores a newly entered address as unproven and sends a code to it.
	 *
	 * <p>The address is deliberately not trusted at this point: it is recorded
	 * so the confirmation can find it, but no login code will ever go there
	 * until {@link #confirmEmail} succeeds. A typo therefore costs the user one
	 * retry rather than their account.
	 */
	IssuedChallenge issueEmailVerificationChallenge(EmployeeCredentialsModel credentials, String email);

	/**
	 * Opens a capture handle for an account that must supply an address before
	 * it can be sent codes.
	 *
	 * <p>Issued once the password has been accepted, so it stands for "this
	 * password was right" without being a session token.
	 *
	 * @return the handle, and how long it lasts
	 */
	IssuedChallenge issueCaptureHandle(EmployeeCredentialsModel credentials);

	/**
	 * Resolves an unanswered handle — a capture token — to the account it was
	 * issued for, without consuming it.
	 *
	 * @throws com.ihealthpharm.exception.IHealthPharmException if unknown,
	 *         expired or already used
	 */
	EmployeeCredentialsModel resolveOpenHandle(String challengeToken, String expectedPurpose);

	/** Marks a handle used, once the step it was carrying has completed. */
	void consumeHandle(String challengeToken);

	/**
	 * Issues a replacement for a code the user did not use in time.
	 *
	 * <p>Works for a login code and an address confirmation alike, and
	 * deliberately accepts an <b>expired</b> challenge: with a one-minute code
	 * the moment somebody wants a new one is precisely the moment the old one
	 * stopped working. Refusing an expired challenge here would make resending
	 * fail exactly when it is needed.
	 *
	 * <p>A challenge that was already answered is refused — that login is done.
	 *
	 * @return the replacement, which the caller must mail
	 */
	IssuedChallenge resendChallenge(String challengeToken);

	/**
	 * Checks a code against an open challenge and marks it used.
	 *
	 * <p>Failures increment the attempt count; past the configured maximum the
	 * challenge stops accepting anything, so a six-digit code cannot be walked
	 * through.
	 *
	 * @return the challenge that was answered
	 * @throws com.ihealthpharm.exception.IHealthPharmException if unknown,
	 *         expired, already used, out of attempts, or simply wrong
	 */
	TwoFactorChallengeModel verifyCode(String challengeToken, String code);

	/**
	 * Completes enrolment: marks the proven address confirmed and switches the
	 * challenge on for that account.
	 */
	void confirmEmail(TwoFactorChallengeModel verifiedChallenge);

	/**
	 * Remembers a machine for the configured window.
	 *
	 * @return the raw token to put in the cookie. Only its hash is stored, so
	 *         this is the one and only time the value exists outside the
	 *         browser.
	 */
	String rememberDevice(EmployeeCredentialsModel credentials, String label);

	/**
	 * Whether a presented device cookie still waives the second factor for this
	 * account, recording the use when it does.
	 */
	boolean isDeviceTrusted(EmployeeCredentialsModel credentials, String rawDeviceToken);

	/**
	 * Sets the work email an administrator typed on Master &rarr; Employee.
	 *
	 * <p>Live immediately — codes go there from the next sign-in and the user
	 * confirms nothing. Passing null or blank clears it, which puts the account
	 * back to enrolling itself at next sign-in.
	 *
	 * @return the address as stored, trimmed
	 */
	String setWorkEmail(Integer employeeCredentialsId, String email);

	/**
	 * Turns the challenge on or off for one account, leaving the stored address
	 * untouched so switching it back on does not force re-enrolment.
	 */
	void setEnabled(Integer employeeCredentialsId, boolean enabled);

	/**
	 * Administrator reset: clears the address and its proof, switches the
	 * challenge off, revokes every remembered machine, and records who did it.
	 *
	 * <p>The administrator never supplies an address — the user enrols again at
	 * their next login. That keeps the decision to allow a change with
	 * management and the proof of ownership with whoever can open the mailbox.
	 */
	TwoFactorResetResult resetEnrolment(Integer employeeCredentialsId, Integer resetByEmployeeId,
			String reason);
}
