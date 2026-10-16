package com.ihealthpharm.twofactor.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * What an administrator reset actually did, so the screen can say so rather
 * than reporting a bare success.
 *
 * <p>{@link #devicesRevoked} is the part worth showing: a reset that logged out
 * four remembered machines is a different event from one that logged out none,
 * and the administrator should see which they just caused.
 */
@Getter
@AllArgsConstructor
public class TwoFactorResetResult {

	/** The address that was cleared, masked. Null when the account had none. */
	private final String previousMaskedEmail;

	/** How many remembered machines were logged out. */
	private final int devicesRevoked;
}
