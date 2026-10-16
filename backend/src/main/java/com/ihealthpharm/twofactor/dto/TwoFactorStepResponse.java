package com.ihealthpharm.twofactor.dto;

import lombok.Getter;

/**
 * Returned by {@code /authenticate} when the password was right but the login
 * is not finished.
 *
 * <p>Deliberately carries no token and nothing identifying. The browser gets a
 * handle, a masked address and a countdown — enough to show the next step, and
 * not enough to tell someone who intercepted it whose account it belongs to.
 *
 * <p>The two flags are mutually exclusive and the frontend must check them
 * before reading anything else: a response carrying either one has no
 * {@code token}, {@code permissions} or {@code pharmacyModel}, so code that
 * assumes a successful login will read undefined.
 */
@Getter
public class TwoFactorStepResponse {

	/** The account is enrolled; a code has been sent and is waiting to be entered. */
	private final boolean twoFactorRequired;

	/** The account must supply an address before it can be sent codes. */
	private final boolean emailCaptureRequired;

	/** Handle for the next call. Opaque, single use, short lived. */
	private final String challengeToken;

	/** {@code v••••@docpharmkenya.com}. Null while capturing, since there is no address yet. */
	private final String maskedEmail;

	/** Seconds until the handle stops working, so the screen can count down. */
	private final int expiresInSeconds;

	private TwoFactorStepResponse(boolean twoFactorRequired, boolean emailCaptureRequired,
			String challengeToken, String maskedEmail, int expiresInSeconds) {
		this.twoFactorRequired = twoFactorRequired;
		this.emailCaptureRequired = emailCaptureRequired;
		this.challengeToken = challengeToken;
		this.maskedEmail = maskedEmail;
		this.expiresInSeconds = expiresInSeconds;
	}

	/** A code has been sent to the account's confirmed address. */
	public static TwoFactorStepResponse codeSent(IssuedChallenge challenge) {
		return new TwoFactorStepResponse(true, false, challenge.getChallengeToken(),
				challenge.getMaskedEmail(), challenge.getExpiresInSeconds());
	}

	/** The account has no confirmed address and must supply one now. */
	public static TwoFactorStepResponse captureRequired(String captureToken, int expiresInSeconds) {
		return new TwoFactorStepResponse(false, true, captureToken, null, expiresInSeconds);
	}
}
