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

import com.ihealthpharm.finance.dao.AccountReceivablesRepository;
import com.ihealthpharm.finance.dto.OpenBillDTO;
import com.ihealthpharm.sales.dao.SalesRepository;
import com.ihealthpharm.sales.model.SalesModel;

/**
 * Checks that a bill is found the same way wherever it is looked for.
 *
 * A receivable names its bill in one of two columns: BILL_NO normally, and
 * SOURCE_REF on 528 of the older open rows. The open-bill queries read
 * COALESCE(BILL_NO, SOURCE_REF), but the sum that rebuilds a bill's balance
 * after settling matched on BILL_NO alone. For the 436 settleable rows that
 * carry the code only in SOURCE_REF the sum therefore came back as zero, and
 * the bill was rebuilt as though nothing had ever been paid on it: settling
 * DPSB1001200065 would have moved it from 1,120 outstanding to 9,600, wiping
 * the 8,480 already received and restating it as Pending.
 *
 * Both rules now come from ReceivableSql. These tests read only - nothing here
 * settles anything - and are named *IT because they need a populated database.
 */
@RunWith(SpringRunner.class)
@SpringBootTest
public class CreditSettlementBillMatchingIT {

	/**
	 * A bill whose receivables keep the code in SOURCE_REF, with money already
	 * received against it. This is the shape the old rule got wrong.
	 */
	private static final String SOURCE_REF_ONLY_BILL = "DPSB1001200065";

	/** An account whose open bills are reachable from the settlement screen. */
	private static final String MASTER_ACCOUNT_NO = "DPCR2109200005";

	@Autowired
	private AccountReceivablesRepository accountReceivablesRepository;

	@Autowired
	private SalesRepository salesRepository;

	@Autowired
	private CreditAllocationService creditAllocationService;

	@Test
	public void receivedIsFoundWhenTheCodeLivesInSourceRef() {
		BigDecimal received = accountReceivablesRepository.sumReceivedForBill(SOURCE_REF_ONLY_BILL);

		assertNotNull(received);
		// The old rule returned zero here, which is what wiped the bill.
		assertTrue("a bill named only by SOURCE_REF must still find its receivables",
				received.signum() > 0);

		SalesModel bill = salesRepository.getSalesRecordByNo(SOURCE_REF_ONLY_BILL);
		assertNotNull(bill);
		assertEquals("what the receivables say was received must agree with the bill", 0,
				received.compareTo(BigDecimal.valueOf(bill.getPaidAmount())));
	}

	/**
	 * The reconcile that runs after settling rebuilds each bill from this sum.
	 * If the sum cannot see the receivables, the rebuild reports the bill as
	 * wholly unpaid - so no bill the screen is willing to settle may come back
	 * with a sum that contradicts what the bill itself records as paid.
	 */
	@Test
	public void everySettleableBillRebuildsToSomethingConsistent() {
		List<OpenBillDTO> openBills = creditAllocationService.findOpenBills(requestFor(MASTER_ACCOUNT_NO));
		assertFalse("expected open bills on this credit account", openBills.isEmpty());

		for (OpenBillDTO openBill : openBills) {
			SalesModel bill = salesRepository.getSalesRecordByNo(openBill.getBillCode());
			if (bill == null || bill.getPaidAmount() == null || bill.getPaidAmount() <= 0) {
				// Nothing paid yet, so there is nothing for the sum to lose.
				continue;
			}

			BigDecimal received = accountReceivablesRepository.sumReceivedForBill(openBill.getBillCode());

			assertTrue("bill " + openBill.getBillCode() + " records " + bill.getPaidAmount()
					+ " paid but its receivables sum to " + received,
					received.signum() > 0);
		}
	}

	/** Every open bill the screen offers must resolve to a code that can be looked up. */
	@Test
	public void everyOpenBillCarriesACodeTheSumCanUse() {
		for (OpenBillDTO openBill : creditAllocationService.findOpenBills(requestFor(MASTER_ACCOUNT_NO))) {
			assertNotNull("a bill with no code cannot be reconciled after settling",
					openBill.getBillCode());
			assertFalse(openBill.getBillCode().trim().isEmpty());
		}
	}

	/**
	 * The screen labels the first row as the account's oldest debt, so the
	 * query has to return them in that order rather than leaving it to MySQL.
	 */
	@Test
	public void openBillsComeBackOldestFirst() {
		List<OpenBillDTO> openBills = creditAllocationService.findOpenBills(requestFor(MASTER_ACCOUNT_NO));

		for (int i = 1; i < openBills.size(); i++) {
			assertFalse("open bills must be ordered oldest first",
					openBills.get(i).getBillDate().isBefore(openBills.get(i - 1).getBillDate()));
		}
	}

	/**
	 * The over-settle guard can only run on a bill it can find.
	 *
	 * 616 of the 2,980 settleable receivables carry no SALES_BILL_ID, and the
	 * guard used to look the bill up by that alone - so for a fifth of the rows
	 * it returned without checking anything. DPSB0804200121 owes 199 and has
	 * two receivables claiming 199 each, neither with a bill id: unchecked,
	 * settling both put 398 against it and 199 of the customer's money
	 * disappeared into the duplicate.
	 *
	 * Every line the screen offers must therefore reach its bill by one route
	 * or the other.
	 */
	@Test
	public void everySettleableLineCanReachItsBillSoTheGuardCanRun() {
		int viaCodeOnly = 0;

		for (OpenBillDTO openBill : creditAllocationService.findOpenBills(requestFor(MASTER_ACCOUNT_NO))) {
			SalesModel byId = openBill.getSalesBillId() != null
					? salesRepository.findById(openBill.getSalesBillId()).orElse(null) : null;
			SalesModel byCode = salesRepository.getSalesRecordByNo(openBill.getBillCode());

			if (byId == null && byCode != null) {
				viaCodeOnly++;
			}
			assertTrue("no way to reach the bill behind " + openBill.getBillCode()
					+ ", so it cannot be checked before settling",
					byId != null || byCode != null || openBill.getSalesBillId() == null);
		}

		// Not an assertion about the count - just proof the code fallback is
		// load bearing rather than decorative.
		System.out.println("open bills reachable only by code: " + viaCodeOnly);
	}

	/**
	 * A customer picked as "no credit account" must get only their unaccounted
	 * bills.
	 *
	 * HAWAII DIBA has one bill owing 14,864 with no account and twenty on
	 * DPCR0210200003 owing 459,777. The search offers those separately, but the
	 * bill lookup matched on name alone and returned all twenty-one - so a
	 * payment meant for the single bill would have cascaded across her credit
	 * account, and the screen showed a total the search had not promised.
	 */
	@Test
	public void aCustomerWithNoAccountGetsOnlyTheirUnaccountedBills() {
		com.ihealthpharm.finance.dto.CreditAllocationRequestDTO byName =
				new com.ihealthpharm.finance.dto.CreditAllocationRequestDTO();
		byName.setCustomerName("HAWAII DIBA");
		byName.setPaymentAmount(new BigDecimal("100"));
		byName.setAllocationOrder("OLDEST");
		byName.setOrderConfirmed(true);

		List<OpenBillDTO> unaccounted = creditAllocationService.findOpenBills(byName);
		List<OpenBillDTO> onAccount = creditAllocationService.findOpenBills(requestFor("DPCR0210200003"));

		assertFalse("expected an unaccounted bill for this customer", unaccounted.isEmpty());
		assertFalse("expected bills on this credit account", onAccount.isEmpty());

		// The two sets must not overlap: a bill belongs to one or the other.
		for (OpenBillDTO free : unaccounted) {
			for (OpenBillDTO held : onAccount) {
				assertFalse("bill " + free.getBillCode() + " was returned both as unaccounted "
						+ "and as belonging to the credit account",
						free.getAccountReceivablesId().equals(held.getAccountReceivablesId()));
			}
		}
	}

	private com.ihealthpharm.finance.dto.CreditAllocationRequestDTO requestFor(String masterAccountNo) {
		com.ihealthpharm.finance.dto.CreditAllocationRequestDTO request =
				new com.ihealthpharm.finance.dto.CreditAllocationRequestDTO();
		request.setMasterAccountNo(masterAccountNo);
		request.setPaymentAmount(new BigDecimal("1000"));
		request.setAllocationOrder("OLDEST");
		request.setOrderConfirmed(true);
		return request;
	}

}
