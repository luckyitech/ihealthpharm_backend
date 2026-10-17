package com.ihealthpharm.twofactor.dto;

import lombok.Data;

/**
 * An administrator clearing an account's enrolment.
 *
 * <p>There is no address field, and that is the point: a reset lets the user
 * enrol again, it does not let an administrator choose where someone's codes
 * go. That keeps the decision to allow a change with management and the proof
 * of ownership with whoever can open the mailbox.
 */
@Data
public class TwoFactorResetRequest {

	private Integer employeeCredentialsId;

	/** What the administrator was told, for the audit record. */
	private String reason;
}
