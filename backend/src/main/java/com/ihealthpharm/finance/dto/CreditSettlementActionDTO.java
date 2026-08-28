package com.ihealthpharm.finance.dto;

import java.io.Serializable;

import lombok.Data;

/**
 * Approving or reversing a settlement that has already been recorded.
 */
@Data
public class CreditSettlementActionDTO implements Serializable {

	private static final long serialVersionUID = 1L;

	/** The batch to act on, e.g. DPCA2808260001. */
	private String batchRef;

	/** Approval PIN of the user performing the action. */
	private String approvalPin;

	/** Why the settlement is being reversed - required, and kept on the batch. */
	private String reason;

	/**
	 * When false the batch is only flagged for review rather than unwound, so a
	 * bounced cheque can either reverse itself or wait for someone to look at it.
	 */
	private boolean reverseAutomatically = true;

	private Integer approvedBy;

}
