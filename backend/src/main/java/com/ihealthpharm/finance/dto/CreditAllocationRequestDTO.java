package com.ihealthpharm.finance.dto;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;

import lombok.Data;

/**
 * A credit payment to be allocated across a customer's outstanding bills.
 */
@Data
public class CreditAllocationRequestDTO implements Serializable {

	private static final long serialVersionUID = 1L;

	/**
	 * Preferred way to identify who paid. Unlike the customer name this is
	 * unambiguous, so it is used whenever the customer has a credit account.
	 */
	private String masterAccountNo;

	/**
	 * Used only when there is no credit account. Names are not unique, so a name
	 * resolving to more than one account is refused rather than guessed at.
	 */
	private String customerName;

	private Integer customerId;

	private Integer pharmacyId;

	private BigDecimal paymentAmount;

	/** OLDEST (default) or NEWEST. */
	private String allocationOrder;

	/**
	 * Set once the user has explicitly picked the order. Required before debt
	 * older than the ageing threshold will be settled.
	 */
	private boolean orderConfirmed;

	private String paymentType;

	/** Cheque number, M-Pesa code or card auth code. */
	private String paymentRef;

	/** Set for cheque payments - allocation then waits on approval. */
	private Integer chequeId;

	/**
	 * Date on the cheque. Required for a cheque payment: the receivables update
	 * writes it onto the sales bill and fails outright if it is missing.
	 */
	private LocalDate chequeDate;

	/** REFUND or HOLD_ON_ACCOUNT, needed only when the payment exceeds all bills. */
	private String overpaymentAction;

	/** 'Y' when loading a historical payment from the EOD records. */
	private boolean backfill;

	/** Approval PIN of the user settling the payment. Required to apply, ignored on preview. */
	private String approvalPin;

	/** Employee recorded as approving the settlement. */
	private Integer approvedBy;


}
