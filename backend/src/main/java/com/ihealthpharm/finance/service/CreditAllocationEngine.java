package com.ihealthpharm.finance.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
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

import lombok.extern.slf4j.Slf4j;

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
@Slf4j
public class CreditAllocationEngine {

	public static final String STATUS_PAID = "Paid";
	public static final String STATUS_PARTIALLY_PAID = "Partially Paid";

	/**
	 * Every amount is held to whole cents.
	 *
	 * The source columns (account_receivables.AMOUNT_TO_BE_RECEIVED,
	 * sales.BALANCE_AMOUNT) are FLOAT, so a bill displayed as 1,904.36 actually
	 * holds 1904.3599853516. Cascading on the raw value leaves a fraction of a
	 * cent over after the bill is cleared, which then lands on the next bill and
	 * wrongly marks it partially paid. Rounding on the way in removes that.
	 */
	private static final int MONEY_SCALE = 2;
	private static final RoundingMode MONEY_ROUNDING = RoundingMode.HALF_UP;

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

		BigDecimal payment = validatedPayment(paymentAmount);
		LocalDate asOf = validatedToday(today);
		String order = resolveOrder(allocationOrder);
		List<OpenBillDTO> settleable = settleableBills(openBills);

		CreditAllocationPlanDTO plan = new CreditAllocationPlanDTO();
		plan.setPaymentAmount(payment);
		plan.setAllocationOrder(order);
		plan.setOpenBillCount(settleable.size());
		plan.setAllocatedAmount(money(BigDecimal.ZERO));
		plan.setUnallocatedAmount(payment);

		if (settleable.isEmpty()) {
			// Nothing to settle - the whole payment is a credit on the account.
			log.info("Credit allocation: no open bills, {} left unallocated", payment);
			return plan;
		}

		sort(settleable, order);
		plan.setOldestBillDate(oldestBillDate(settleable));

		if (confirmationNeeded(plan.getOldestBillDate(), asOf, orderConfirmed)) {
			// Deliberately no lines: the caller must come back with a confirmed order.
			plan.setOrderConfirmationRequired(true);
			log.info("Credit allocation held for order confirmation: oldest open bill {} is beyond the {} year threshold",
					plan.getOldestBillDate(), ageingThresholdYears);
			return plan;
		}

		cascade(plan, settleable, payment);

		log.info("Credit allocation planned: payment {} over {} open bills, {} settled across {} bills, {} unallocated ({} order)",
				payment, settleable.size(), plan.getAllocatedAmount(), plan.getLines().size(),
				plan.getUnallocatedAmount(), order);

		return plan;
	}

	/** Fills each bill in turn until the payment is exhausted. */
	private void cascade(CreditAllocationPlanDTO plan, List<OpenBillDTO> bills, BigDecimal paymentAmount) {
		BigDecimal remaining = paymentAmount;
		BigDecimal allocated = money(BigDecimal.ZERO);
		int sequence = 1;

		for (OpenBillDTO bill : bills) {
			if (remaining.signum() <= 0) {
				break;
			}

			BigDecimal outstanding = money(bill.getOutstanding());
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
			line.setResultingBalance(outstanding.subtract(applied));
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
			if (!Objects.nonNull(bill) || !Objects.nonNull(bill.getOutstanding())) {
				continue;
			}
			if (money(bill.getOutstanding()).signum() <= 0) {
				continue;
			}
			if (!Objects.nonNull(bill.getAccountReceivablesId())) {
				// Refuse rather than skip: skipping would silently send the
				// customer's money to a different bill than the one on screen.
				throw new IHealthPharmException(
						"Cannot allocate payment: an outstanding bill is missing its receivable reference.",
						HttpStatus.INTERNAL_SERVER_ERROR);
			}
			settleable.add(bill);
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
		if (ageingThresholdYears <= 0) {
			// Misconfigured threshold: ask rather than assume it is safe to cascade.
			log.warn("credit.allocation.ageing.threshold.years is {}, which is not usable - "
					+ "requiring explicit order confirmation", ageingThresholdYears);
			return true;
		}
		return oldestBillDate.isBefore(today.minusYears(ageingThresholdYears));
	}

	private BigDecimal validatedPayment(BigDecimal paymentAmount) {
		if (!Objects.nonNull(paymentAmount)) {
			throw new IHealthPharmException("Payment amount is required.", HttpStatus.BAD_REQUEST);
		}
		BigDecimal payment = money(paymentAmount);
		if (payment.signum() <= 0) {
			throw new IHealthPharmException("Payment amount must be greater than zero.", HttpStatus.BAD_REQUEST);
		}
		return payment;
	}

	private LocalDate validatedToday(LocalDate today) {
		if (!Objects.nonNull(today)) {
			throw new IHealthPharmException("A date is required to apply the credit ageing rule.",
					HttpStatus.INTERNAL_SERVER_ERROR);
		}
		return today;
	}

	private String resolveOrder(String allocationOrder) {
		String requested = Objects.nonNull(allocationOrder) ? allocationOrder.trim() : "";

		if (CreditAllocationBatchModel.ORDER_NEWEST.equalsIgnoreCase(requested)) {
			return CreditAllocationBatchModel.ORDER_NEWEST;
		}
		if (!requested.isEmpty() && !CreditAllocationBatchModel.ORDER_OLDEST.equalsIgnoreCase(requested)) {
			// Falling back silently would settle the wrong bills if the caller
			// meant newest-first, so make the mismatch visible.
			log.warn("Unrecognised allocation order '{}' - defaulting to {}", allocationOrder,
					CreditAllocationBatchModel.ORDER_OLDEST);
		}
		// Oldest first is the default.
		return CreditAllocationBatchModel.ORDER_OLDEST;
	}

	/** Rounds to whole cents so the cascade cannot leave sub-cent remainders behind. */
	private BigDecimal money(BigDecimal amount) {
		return amount.setScale(MONEY_SCALE, MONEY_ROUNDING);
	}

	protected void setAgeingThresholdYears(int ageingThresholdYears) {
		this.ageingThresholdYears = ageingThresholdYears;
	}

}
