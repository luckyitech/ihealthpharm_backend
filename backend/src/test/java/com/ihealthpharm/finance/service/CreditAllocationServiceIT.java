package com.ihealthpharm.finance.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.math.BigDecimal;
import java.util.List;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import com.ihealthpharm.finance.dto.CreditAllocationPlanDTO;
import com.ihealthpharm.finance.dto.CreditAllocationPlanLineDTO;
import com.ihealthpharm.finance.dto.CreditAllocationRequestDTO;
import com.ihealthpharm.finance.dto.OpenBillDTO;

/**
 * Exercises the read path against the configured database.
 *
 * The engine's own rules are covered by {@link CreditAllocationEngineTest},
 * which needs nothing running. What is checked here is the part unit tests
 * cannot reach: that the native queries return what they claim to, and that
 * the projected columns map onto the DTO in the right order and types.
 *
 * Named *IT so it is not picked up by the default unit test run - it needs a
 * populated database.
 */
@RunWith(SpringRunner.class)
@SpringBootTest
public class CreditAllocationServiceIT {

	/** A credit account carrying a long tail of open bills. */
	private static final String MASTER_ACCOUNT_NO = "DPCR2109200005";

	@Autowired
	private CreditAllocationService creditAllocationService;

	private CreditAllocationRequestDTO request(String amount) {
		CreditAllocationRequestDTO request = new CreditAllocationRequestDTO();
		request.setMasterAccountNo(MASTER_ACCOUNT_NO);
		request.setPaymentAmount(new BigDecimal(amount));
		request.setAllocationOrder("OLDEST");
		request.setOrderConfirmed(true);
		return request;
	}

	@Test
	public void openBillsLoadWithEveryFieldPopulated() {
		List<OpenBillDTO> openBills = creditAllocationService.findOpenBills(request("1000"));

		assertFalse("expected open bills on this credit account", openBills.isEmpty());

		for (OpenBillDTO bill : openBills) {
			assertNotNull("receivable id is required to allocate", bill.getAccountReceivablesId());
			assertNotNull("outstanding is required to allocate", bill.getOutstanding());
			assertTrue("only positive debt should be returned", bill.getOutstanding().signum() > 0);
			// Bill date may fall back to the receipt date, but must never be absent.
			assertNotNull("every bill needs a date to order on", bill.getBillDate());
		}
	}

	@Test
	public void planSettlesOldestFirstAndAccountsForEveryCent() {
		BigDecimal payment = new BigDecimal("40000");
		CreditAllocationPlanDTO plan = creditAllocationService.previewAllocation(request("40000"));

		assertFalse(plan.getLines().isEmpty());

		BigDecimal applied = BigDecimal.ZERO;
		for (int i = 0; i < plan.getLines().size(); i++) {
			CreditAllocationPlanLineDTO line = plan.getLines().get(i);
			assertEquals(Integer.valueOf(i + 1), line.getSequenceNo());
			assertEquals("amounts must be held to whole cents", 2, line.getAmountApplied().scale());
			applied = applied.add(line.getAmountApplied());

			if (i > 0) {
				// Oldest first: each bill is no older than the one before it.
				assertFalse(line.getBillDate().isBefore(plan.getLines().get(i - 1).getBillDate()));
			}
		}

		assertEquals("lines plus remainder must equal the payment", 0,
				applied.add(plan.getUnallocatedAmount()).compareTo(payment));
		assertEquals(0, applied.compareTo(plan.getAllocatedAmount()));
	}

	@Test
	public void everyBillButTheLastIsSettledInFull() {
		CreditAllocationPlanDTO plan = creditAllocationService.previewAllocation(request("40000"));

		for (int i = 0; i < plan.getLines().size() - 1; i++) {
			assertEquals("only the bill the money runs out on may be partial",
					CreditAllocationEngine.STATUS_PAID, plan.getLines().get(i).getResultingPaymentStatus());
		}
	}

	@Test
	public void previewChangesNothing() {
		CreditAllocationPlanDTO before = creditAllocationService.previewAllocation(request("40000"));
		CreditAllocationPlanDTO after = creditAllocationService.previewAllocation(request("40000"));

		// Running it twice returns the same answer, which it could not do if the
		// first run had settled anything.
		assertEquals(before.getLines().size(), after.getLines().size());
		assertEquals(0, before.getAllocatedAmount().compareTo(after.getAllocatedAmount()));
	}

}
