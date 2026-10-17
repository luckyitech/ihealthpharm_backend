package com.ihealthpharm.twofactor.dto;

import lombok.Data;

/** Answering a code, for either a login or an address confirmation. */
@Data
public class TwoFactorCodeRequest {

	/** The handle returned by the previous step. */
	private String challengeToken;

	private String code;

	/**
	 * Whether to remember this machine for the configured window. Login only —
	 * ignored when confirming an address, because the password has not yet been
	 * paired with a completed enrolment at that point.
	 */
	private boolean rememberDevice;
}
