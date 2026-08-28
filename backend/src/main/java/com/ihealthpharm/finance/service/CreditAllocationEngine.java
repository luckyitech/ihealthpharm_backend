package com.ihealthpharm.finance.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import com.ihealthpharm.exception.IHealthPharmException;
import com.ihealthpharm.finance.dto.CreditAllocationPlanDTO;
import com.ihealthpharm.finance.dto.CreditAllocationPlanLineDTO;
import com.ihealthpharm.finance.dto.OpenBillDTO;
import com.ihealthpharm.finance.model.CreditAllocationBatchModel;

/**
 * Works out which bills a credit payment settles.
 *
 * Bills are filled one at a time in date order until the money runs out: each
 * bill the payment covers in full is cleared, the bill it runs out on is left
 * partially paid, and anything beyond the last bill is reported as unallocated
 * for the caller to refund or hold.
 *
 * This class only calculates - it reads nothing and writes nothing - so the
 * cascade can be tested directly and can be run against live payments for
 * comparison before it is allowed to update anything.
 */
@Component
public class CreditAllocationEngine {

	public static final String STATUS_PAID = "Paid";
	public static final String STATUS_PARTIALLY_PAID = "Partially Paid";

	/**
	 * Debt older than this forces the user to choose the order explicitly rather
	 * than defaulting to oldest-first, so payments are not quietly absorbed by
	 * bills the business may no longer be pursuing.
	 */
	@Value("${credit.allocation.ageing.threshold.years:3}")
	private int ageingThresholdYears;

	public CreditAllocationPlanDTO buildPlan(List<OpenBillDTO> openBills, BigDecimal paymentAmount,
			String allocationOrder, boolean orderConfirmed) {
		return buildPlan(openBills, paymentAmount, allocationOrder, orderConfirmed, LocalDate.now());
	}

	/**
	 * @param today passed in so the ageing rule can be tested without depending
	 *              on the clock.
	 */
	public CreditAllocationPlanDTO buildPlan(List<OpenBillDTO> openBills, BigDecimal paymentAmount,
			String allocationOrder, boolean orderConfirmed, LocalDate today) {

		if (!Objects.nonNull(paymentAmount) || paymentAmount.signum() <= 0) {
			throw new IHealthPharmException("Payment amount must be greater than zero.", HttpStatus.BAD_REQUEST);
		}

		String order = resolveOrder(allocationOrder);
		List<OpenBillDTO> settleable = settleableBills(openBills);

		CreditAllocationPlanDTO plan = new CreditAllocationPlanDTO();
		plan.setPaymentAmount(paymentAmount);
		plan.setAllocationOrder(order);
		plan.setOpenBillCount(settleable.size());
		plan.setAllocatedAmount(BigDecimal.ZERO);
		plan.setUnallocatedAmount(paymentAmount);

		if (settleable.isEmpty()) {
			// Nothing to settle - the whole payment is a credit on the account.
			return plan;
		}

		sort(settleable, order);
		plan.setOldestBillDate(oldestBillDate(settleable));

		if (confirmationNeeded(plan.getOldestBillDate(), today, orderConfirmed)) {
			// Deliberately no lines: the caller must come back with a confirmed order.
			plan.setOrderConfirmationRequired(true);
			return plan;
		}

		cascade(plan, settleable, paymentAmount);
		return plan;
	}

	/** Fills each bill in turn until the payment is exhausted. */
	private void cascade(CreditAllocationPlanDTO plan, List<OpenBillDTO> bills, BigDecimal paymentAmount) {
		BigDecimal remaining = paymentAmount;
		BigDecimal allocated = BigDecimal.ZERO;
		int sequence = 1;

		for (OpenBillDTO bill : bills) {
			if (remaining.signum() <= 0) {
				break;
			}

			BigDecimal outstanding = bill.getOutstanding();
			BigDecimal applied = remaining.min(outstanding);

			CreditAllocationPlanLineDTO line = new CreditAllocationPlanLineDTO();
			line.setSequenceNo(sequence++);
			line.setAccountReceivablesId(bill.getAccountReceivablesId());
			line.setSalesBillId(bill.getSalesBillId());
			line.setBillCode(bill.getBillCode());
			line.setBillDate(bill.getBillDate());
			line.setAmountApplied(applied);
			line.setPriorBalance(outstanding);
			line.setPriorPaymentStatus(bill.getCurrentPaymentStatus());
			line.setResultingPaymentStatus(
					applied.compareTo(outstanding) == 0 ? STATUS_PAID : STATUS_PARTIALLY_PAID);

			plan.getLines().add(line);

			allocated = allocated.add(applied);
			remaining = remaining.subtract(applied);
		}

		plan.setAllocatedAmount(allocated);
		plan.setUnallocatedAmount(remaining);
	}

	/**
	 * Only positive "Sales Billing" debt is settled automatically. Credit and
	 * debit notes are left out on purpose - applying those stays a manual
	 * decision - and non-positive rows would otherwise consume the payment
	 * without clearing anything.
	 */
	private List<OpenBillDTO> settleableBills(List<OpenBillDTO> openBills) {
		List<OpenBillDTO> settleable = new ArrayList<>();
		if (!Objects.nonNull(openBills)) {
			return settleable;
		}
		for (OpenBillDTO bill : openBills) {
			if (Objects.nonNull(bill.getOutstanding()) && bill.getOutstanding().signum() > 0) {
				settleable.add(bill);
			}
		}
		return settleable;
	}

	private void sort(List<OpenBillDTO> bills, String order) {
		// Bills with no resolvable date sort last, so a dateless legacy row never
		// takes priority over a bill whose age is actually known.
		Comparator<OpenBillDTO> byDate = Comparator.comparing(OpenBillDTO::getBillDate,
				Comparator.nullsLast(Comparator.naturalOrder()));

		if (CreditAllocationBatchModel.ORDER_NEWEST.equals(order)) {
			byDate = Comparator.comparing(OpenBillDTO::getBillDate,
					Comparator.nullsLast(Comparator.reverseOrder()));
		}

		// Tie-break on id so two bills on the same date always allocate in a
		// repeatable order.
		bills.sort(byDate.thenComparing(OpenBillDTO::getAccountReceivablesId,
				Comparator.nullsLast(Comparator.naturalOrder())));
	}

	private LocalDate oldestBillDate(List<OpenBillDTO> bills) {
		return bills.stream().map(OpenBillDTO::getBillDate).filter(Objects::nonNull)
				.min(Comparator.naturalOrder()).orElse(null);
	}

	private boolean confirmationNeeded(LocalDate oldestBillDate, LocalDate today, boolean orderConfirmed) {
		if (orderConfirmed || !Objects.nonNull(oldestBillDate)) {
			return false;
		}
		return oldestBillDate.isBefore(today.minusYears(ageingThresholdYears));
	}

	private String resolveOrder(String allocationOrder) {
		if (CreditAllocationBatchModel.ORDER_NEWEST.equalsIgnoreCase(allocationOrder)) {
			return CreditAllocationBatchModel.ORDER_NEWEST;
		}
		// Oldest first is the default.
		return CreditAllocationBatchModel.ORDER_OLDEST;
	}

	protected void setAgeingThresholdYears(int ageingThresholdYears) {
		this.ageingThresholdYears = ageingThresholdYears;
	}

}
