package com.ihealthpharm.finance.dto;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import lombok.Data;

/**
 * What a payment would settle, worked out but not yet applied.
 *
 * Producing the plan separately from applying it keeps the cascade testable,
 * lets the screen show the user exactly which bills are about to be cleared,
 * and allows the engine to be run alongside the manual process for comparison
 * before it is switched on.
 */
@Data
public class CreditAllocationPlanDTO implements Serializable {

	private static final long serialVersionUID = 1L;

	private List<CreditAllocationPlanLineDTO> lines = new ArrayList<>();

	private BigDecimal paymentAmount;

	/** Sum of the lines. */
	private BigDecimal allocatedAmount;

	/** Left over once every open bill is settled - drives the refund-or-hold choice. */
	private BigDecimal unallocatedAmount;

	private String allocationOrder;

	/**
	 * True when the customer has debt older than the ageing threshold. The
	 * cascade is not run in that case: the user has to confirm the order first,
	 * so a payment is never silently absorbed by very old bills.
	 */
	private boolean orderConfirmationRequired;

	/** Oldest open bill, so the screen can explain why confirmation is being asked for. */
	private LocalDate oldestBillDate;

	private int openBillCount;

	/**
	 * The settlement this plan became, once it has been applied.
	 *
	 * Null for a preview. The screen used to decide what to tell the user by
	 * looking at the payment type it had just sent, which is a guess about what
	 * the server did rather than a report of it - and it guessed wrong, saying
	 * a cheque was held when the cheque had in fact settled the bills. What was
	 * actually written is carried back here instead.
	 */
	private String batchRef;

	/** ALLOCATED or HELD_PENDING_APPROVAL, as recorded on the batch. */
	private String status;

	public boolean isFullyAllocated() {
		return unallocatedAmount != null && unallocatedAmount.signum() == 0;
	}

}
