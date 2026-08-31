package com.ihealthpharm.finance.dto;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

import lombok.Data;

/**
 * One past settlement, as the reversal screen lists it.
 *
 * A slim view of the batch rather than the entity itself, so listing them does
 * not drag in every allocation line.
 */
@Data
public class SettlementSummaryDTO implements Serializable {

	private static final long serialVersionUID = 1L;

	private String batchRef;

	private String masterAccountNo;

	private BigDecimal paymentAmount;

	private BigDecimal allocatedAmount;

	private String paymentType;

	private String paymentRef;

	/** ALLOCATED, HELD_PENDING_APPROVAL, REVERSED or REVERSAL_FLAGGED. */
	private String status;

	private String allocationOrder;

	private Date settledOn;

	private String reversalReason;

	/** How many bills this settlement touched. */
	private int billCount;

	/**
	 * What the payment did not settle, and what the user chose to do with it.
	 *
	 * Both were shown while the settlement was being previewed and then never
	 * again: the batch recorded them but this list did not carry them, so a
	 * refund the business still owes a customer became invisible the moment it
	 * was applied. Money held on account shows up as credit on the customer's
	 * next payment; a refund does not show up anywhere until someone pays it.
	 */
	private BigDecimal unallocatedAmount;

	/** REFUND or HOLD_ON_ACCOUNT. */
	private String overpaymentAction;

}
