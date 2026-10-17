package com.ihealthpharm.twofactor.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * A code that has just been issued, and everything needed to deliver it.
 *
 * <p>Carries the plain code for exactly as long as it takes to hand it to the
 * mail service — it is stored only as a BCrypt hash, so this object is the one
 * place it exists in readable form.
 *
 * <p>Getters are declared per field rather than on the class, so neither the
 * code nor the unmasked address gets one. Reading either means calling a named
 * method, which is awkward enough not to happen by accident and keeps both out
 * of anything that serialises by bean properties.
 */
@AllArgsConstructor
public class IssuedChallenge {

	/** The handle the browser holds between the two halves of a login. */
	@Getter
	private final String challengeToken;

	/** Where the code went, masked for display: {@code v••••@docpharmkenya.com}. */
	@Getter
	private final String maskedEmail;

	/** Seconds until the code stops working, so the screen can count down. */
	@Getter
	private final int expiresInSeconds;

	/**
	 * True when this code confirms a new address rather than finishing a login.
	 * Changes the wording of the email and nothing else.
	 */
	@Getter
	private final boolean enrolment;

	/** No getter, and transient so no serialiser reaches it. */
	private final transient String code;

	/** No getter: the unmasked address is for delivery, not for display. */
	private final transient String targetEmail;

	/** For the mail service only. Never put the result in a response. */
	public String revealCode() {
		return code;
	}

	/** For the mail service only: where to actually send it. */
	public String deliveryAddress() {
		return targetEmail;
	}
}
