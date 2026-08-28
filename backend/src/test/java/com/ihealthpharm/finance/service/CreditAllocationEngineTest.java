package com.ihealthpharm.finance.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Before;
import org.junit.Test;

import com.ihealthpharm.exception.IHealthPharmException;
import com.ihealthpharm.finance.dto.CreditAllocationPlanDTO;
import com.ihealthpharm.finance.dto.CreditAllocationPlanLineDTO;
import com.ihealthpharm.finance.dto.OpenBillDTO;

/**
 * The cascade is what decides where a customer's money goes, so it is tested
 * directly rather than through the screen. The figures in
 * {@link #drRoopsOldestBillsFirst()} are real outstanding bills taken from the
 * live data.
 */
public class CreditAllocationEngineTest {

	/** Fixed so the ageing rule does not depend on when the suite runs. */
	private static final LocalDate TODAY = LocalDate.parse("2026-08-28");

	private CreditAllocationEngine engine;

	@Before
	public void setUp() {
		engine = new CreditAllocationEngine();
		engine.setAgeingThresholdYears(3);
	}

	private OpenBillDTO bill(int id, String billDate, String outstanding, String status) {
		OpenBillDTO openBill = new OpenBillDTO();
		openBill.setAccountReceivablesId(id);
		openBill.setSalesBillId(id);
		openBill.setBillCode("DPSB" + id);
		openBill.setBillDate(billDate == null ? null : LocalDate.parse(billDate));
		openBill.setOutstanding(new BigDecimal(outstanding));
		openBill.setCurrentPaymentStatus(status);
		return openBill;
	}

	/** Dr Roopkamal Saini's four oldest open bills. */
	private List<OpenBillDTO> drRoopsOpenBills() {
		return new ArrayList<>(Arrays.asList(
				bill(1, "2020-09-02", "33338", "Pending"),
				bill(2, "2020-09-05", "5125", "Pending"),
				bill(3, "2020-09-18", "1680", "Pending"),
				bill(4, "2021-05-17", "750", "Partially Paid")));
	}

	@Test
	public void drRoopsOldestBillsFirst() {
		CreditAllocationPlanDTO plan = engine.buildPlan(drRoopsOpenBills(), new BigDecimal("40000"),
				"OLDEST", true, TODAY);

		assertEquals(3, plan.getLines().size());

		CreditAllocationPlanLineDTO first = plan.getLines().get(0);
		assertEquals(0, first.getAmountApplied().compareTo(new BigDecimal("33338")));
		assertEquals(CreditAllocationEngine.STATUS_PAID, first.getResultingPaymentStatus());

		assertEquals(CreditAllocationEngine.STATUS_PAID, plan.getLines().get(1).getResultingPaymentStatus());

		// The money runs out part way through the third bill.
		CreditAllocationPlanLineDTO third = plan.getLines().get(2);
		assertEquals(0, third.getAmountApplied().compareTo(new BigDecimal("1537")));
		assertEquals(0, third.getPriorBalance().compareTo(new BigDecimal("1680")));
		assertEquals(CreditAllocationEngine.STATUS_PARTIALLY_PAID, third.getResultingPaymentStatus());

		assertEquals(0, plan.getUnallocatedAmount().signum());
		assertTrue(plan.isFullyAllocated());
	}

	@Test
	public void linesAlwaysSumBackToThePayment() {
		BigDecimal payment = new BigDecimal("40000");
		CreditAllocationPlanDTO plan = engine.buildPlan(drRoopsOpenBills(), payment, "OLDEST", true, TODAY);

		BigDecimal summed = plan.getLines().stream().map(CreditAllocationPlanLineDTO::getAmountApplied)
				.reduce(BigDecimal.ZERO, BigDecimal::add);

		assertEquals(0, summed.add(plan.getUnallocatedAmount()).compareTo(payment));
		assertEquals(0, summed.compareTo(plan.getAllocatedAmount()));
	}

	@Test
	public void newestFirstStartsAtTheMostRecentBill() {
		CreditAllocationPlanDTO plan = engine.buildPlan(drRoopsOpenBills(), new BigDecimal("1000"),
				"NEWEST", true, TODAY);

		assertEquals(LocalDate.parse("2021-05-17"), plan.getLines().get(0).getBillDate());
	}

	@Test
	public void recentDebtAllocatesWithoutAskingForConfirmation() {
		List<OpenBillDTO> recent = new ArrayList<>(Arrays.asList(
				bill(10, "2026-07-01", "500", "Pending"),
				bill(11, "2026-07-15", "900", "Pending")));

		CreditAllocationPlanDTO plan = engine.buildPlan(recent, new BigDecimal("600"), "OLDEST", false, TODAY);

		assertFalse(plan.isOrderConfirmationRequired());
		assertEquals(2, plan.getLines().size());
	}

	@Test
	public void debtOlderThanThresholdWillNotAllocateUntilOrderIsConfirmed() {
		CreditAllocationPlanDTO plan = engine.buildPlan(drRoopsOpenBills(), new BigDecimal("40000"),
				"OLDEST", false, TODAY);

		assertTrue(plan.isOrderConfirmationRequired());
		assertTrue("no bills may be settled before the user picks an order", plan.getLines().isEmpty());
		assertEquals(LocalDate.parse("2020-09-02"), plan.getOldestBillDate());
	}

	@Test
	public void moneyLeftOverAfterEveryBillIsReportedAsUnallocated() {
		CreditAllocationPlanDTO plan = engine.buildPlan(drRoopsOpenBills(), new BigDecimal("50000"),
				"OLDEST", true, TODAY);

		assertEquals(4, plan.getLines().size());
		assertEquals(0, plan.getUnallocatedAmount().compareTo(new BigDecimal("9107")));
		assertFalse(plan.isFullyAllocated());
	}

	@Test
	public void paymentMatchingABillExactlyClearsItAndNothingElse() {
		CreditAllocationPlanDTO plan = engine.buildPlan(drRoopsOpenBills(), new BigDecimal("33338"),
				"OLDEST", true, TODAY);

		assertEquals(1, plan.getLines().size());
		assertEquals(CreditAllocationEngine.STATUS_PAID, plan.getLines().get(0).getResultingPaymentStatus());
		assertTrue(plan.isFullyAllocated());
	}

	@Test
	public void creditNotesAreLeftOutOfTheCascade() {
		// Credit notes carry a negative outstanding; applying them stays manual.
		List<OpenBillDTO> bills = new ArrayList<>(Arrays.asList(
				bill(20, "2026-01-01", "-5000", "Pending"),
				bill(21, "2026-02-01", "1000", "Pending")));

		CreditAllocationPlanDTO plan = engine.buildPlan(bills, new BigDecimal("1000"), "OLDEST", true, TODAY);

		assertEquals(1, plan.getLines().size());
		assertEquals(Integer.valueOf(21), plan.getLines().get(0).getAccountReceivablesId());
	}

	@Test
	public void aBillWithNoResolvableDateDoesNotJumpTheQueue() {
		List<OpenBillDTO> bills = new ArrayList<>(Arrays.asList(
				bill(30, null, "100", "Pending"),
				bill(31, "2026-01-01", "100", "Pending")));

		CreditAllocationPlanDTO plan = engine.buildPlan(bills, new BigDecimal("100"), "OLDEST", true, TODAY);

		assertEquals(Integer.valueOf(31), plan.getLines().get(0).getAccountReceivablesId());
	}

	@Test
	public void customerWithNothingOutstandingLeavesTheWholePaymentUnallocated() {
		CreditAllocationPlanDTO plan = engine.buildPlan(new ArrayList<OpenBillDTO>(), new BigDecimal("500"),
				"OLDEST", true, TODAY);

		assertTrue(plan.getLines().isEmpty());
		assertEquals(0, plan.getUnallocatedAmount().compareTo(new BigDecimal("500")));
	}

	@Test(expected = IHealthPharmException.class)
	public void zeroPaymentIsRejected() {
		engine.buildPlan(drRoopsOpenBills(), BigDecimal.ZERO, "OLDEST", true, TODAY);
	}

	@Test(expected = IHealthPharmException.class)
	public void negativePaymentIsRejected() {
		engine.buildPlan(drRoopsOpenBills(), new BigDecimal("-5"), "OLDEST", true, TODAY);
	}

	@Test(expected = IHealthPharmException.class)
	public void nullPaymentIsRejected() {
		engine.buildPlan(drRoopsOpenBills(), null, "OLDEST", true, TODAY);
	}

	@Test(expected = IHealthPharmException.class)
	public void missingDateIsRejectedRatherThanFailingInsideTheAgeingRule() {
		engine.buildPlan(drRoopsOpenBills(), new BigDecimal("100"), "OLDEST", true, null);
	}

	/**
	 * The money columns are FLOAT, so a bill shown as 1,904.36 is really held as
	 * 1904.3599853516. Cascading on the raw value used to leave 0.0000146484
	 * over, which landed on the next bill and marked it partially paid.
	 */
	@Test
	public void clearingABillDoesNotLeaveASubCentRemainderOnTheNextOne() {
		List<OpenBillDTO> bills = new ArrayList<>(Arrays.asList(
				bill(1, "2026-01-01", "1904.3599853516", "Pending"),
				bill(2, "2026-02-01", "5000", "Pending")));

		CreditAllocationPlanDTO plan = engine.buildPlan(bills, new BigDecimal("1904.36"), "OLDEST", true, TODAY);

		assertEquals("the payment clears one bill and must not touch the next", 1, plan.getLines().size());
		assertEquals(CreditAllocationEngine.STATUS_PAID, plan.getLines().get(0).getResultingPaymentStatus());
		assertTrue(plan.isFullyAllocated());
	}

	@Test
	public void amountsAreHeldToWholeCents() {
		List<OpenBillDTO> bills = new ArrayList<>(Arrays.asList(
				bill(1, "2026-01-01", "0.3499999940", "Pending")));

		CreditAllocationPlanDTO plan = engine.buildPlan(bills, new BigDecimal("0.35"), "OLDEST", true, TODAY);

		assertEquals(2, plan.getLines().get(0).getAmountApplied().scale());
		assertEquals(0, plan.getLines().get(0).getAmountApplied().compareTo(new BigDecimal("0.35")));
		assertTrue(plan.isFullyAllocated());
	}

	@Test
	public void unrecognisedOrderFallsBackToOldestFirst() {
		CreditAllocationPlanDTO plan = engine.buildPlan(drRoopsOpenBills(), new BigDecimal("100"),
				"SOMETHING_ELSE", true, TODAY);

		assertEquals("OLDEST", plan.getAllocationOrder());
		assertEquals(LocalDate.parse("2020-09-02"), plan.getLines().get(0).getBillDate());
	}

	@Test
	public void orderIsMatchedAfterTrimmingWhitespace() {
		CreditAllocationPlanDTO plan = engine.buildPlan(drRoopsOpenBills(), new BigDecimal("100"),
				"  newest  ", true, TODAY);

		assertEquals("NEWEST", plan.getAllocationOrder());
	}

	@Test
	public void nullOrderDefaultsToOldestFirst() {
		CreditAllocationPlanDTO plan = engine.buildPlan(drRoopsOpenBills(), new BigDecimal("100"),
				null, true, TODAY);

		assertEquals("OLDEST", plan.getAllocationOrder());
	}

	@Test(expected = IHealthPharmException.class)
	public void billWithNoReceivableReferenceIsRefusedRatherThanSkipped() {
		// Skipping it would send the customer's money to a different bill than
		// the one shown on screen.
		OpenBillDTO orphan = bill(1, "2026-01-01", "500", "Pending");
		orphan.setAccountReceivablesId(null);

		engine.buildPlan(new ArrayList<>(Arrays.asList(orphan)), new BigDecimal("100"), "OLDEST", true, TODAY);
	}

	@Test
	public void nullEntriesInTheBillListAreIgnored() {
		List<OpenBillDTO> bills = new ArrayList<>(Arrays.asList(
				null, bill(1, "2026-01-01", "500", "Pending")));

		CreditAllocationPlanDTO plan = engine.buildPlan(bills, new BigDecimal("100"), "OLDEST", true, TODAY);

		assertEquals(1, plan.getLines().size());
	}

	@Test
	public void aMisconfiguredAgeingThresholdAsksRatherThanAssuming() {
		engine.setAgeingThresholdYears(0);

		CreditAllocationPlanDTO plan = engine.buildPlan(drRoopsOpenBills(), new BigDecimal("100"),
				"OLDEST", false, TODAY);

		assertTrue(plan.isOrderConfirmationRequired());
		assertTrue(plan.getLines().isEmpty());
	}

}
