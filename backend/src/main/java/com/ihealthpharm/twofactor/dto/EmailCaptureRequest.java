package com.ihealthpharm.twofactor.dto;

import lombok.Data;

/** Supplying the address that will receive this account's codes. */
@Data
public class EmailCaptureRequest {

	/** The capture handle returned by /authenticate. */
	private String challengeToken;

	/**
	 * The address the user chose. Work or personal is their decision; it is
	 * stored unproven and receives nothing but a confirmation code until that
	 * code comes back.
	 */
	private String email;
}
