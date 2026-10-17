package com.ihealthpharm.finance.dao;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.ihealthpharm.finance.model.CreditAllocationBatchModel;

/**
 * Reads the outstanding bills a credit payment can be applied to.
 *
 * Hangs off {@link CreditAllocationBatchModel} only because Spring Data needs an
 * entity to bind to - every query here is native and returns projections rather
 * than that entity.
 *
 * The joins and predicates come from {@link ReceivableSql} rather than being
 * written out here, so the rules these queries use to find a bill are the same
 * ones every other query uses.
 */
@Repository
public interface OpenBillRepository extends JpaRepository<CreditAllocationBatchModel, Integer> {

	/**
	 * Credit accounts matching what the user has typed, for the search box.
	 *
	 * Matches on either the account number or a customer name, because a
	 * pharmacist taking a payment knows the person, not the code. Columns, in
	 * order: account number, the names on it, how many names, open bill count,
	 * total outstanding.
	 *
	 * Every name on the account is returned rather than one of them: an account
	 * can carry several customers - DPCR1410220002 holds bills for two - and
	 * showing only the first would hide who the payment is actually settling
	 * for.
	 *
	 * The second half of the union covers customers with no credit account at
	 * all, who between them still owe about 1.6m across 359 bills. They come
	 * back with a null account number, and are settled by name. Both halves are
	 * ordered together by what is owed, so the account a payment is most likely
	 * meant for comes first whichever kind it is.
	 */
	@Query(nativeQuery = true, value =
		"SELECT ar.MASTER_ACC_NO, "
		+ "       GROUP_CONCAT(DISTINCT ar.CUSTOMER_NAME ORDER BY ar.CUSTOMER_NAME SEPARATOR ', '), "
		+ "       COUNT(DISTINCT ar.CUSTOMER_NAME), "
		+ "       COUNT(*), "
		+ "       SUM(ar.AMOUNT_TO_BE_RECEIVED) "
		+ "FROM account_receivables ar "
		+ ReceivableSql.BILL_JOINS
		+ "WHERE ar.MASTER_ACC_NO IS NOT NULL AND ar.MASTER_ACC_NO <> '' "
		+ "  AND (ar.MASTER_ACC_NO LIKE :term OR ar.CUSTOMER_NAME LIKE :term) "
		+ ReceivableSql.OPEN_BILL_PREDICATE
		+ ReceivableSql.STALE_BILL_GUARD
		+ "GROUP BY ar.MASTER_ACC_NO "
		+ "UNION ALL "
		+ "SELECT NULL, ar.CUSTOMER_NAME, 1, COUNT(*), SUM(ar.AMOUNT_TO_BE_RECEIVED) "
		+ "FROM account_receivables ar "
		+ ReceivableSql.BILL_JOINS
		+ "WHERE ar.CUSTOMER_NAME LIKE :term "
		+ ReceivableSql.NO_CREDIT_ACCOUNT
		+ ReceivableSql.OPEN_BILL_PREDICATE
		+ ReceivableSql.STALE_BILL_GUARD
		+ "GROUP BY ar.CUSTOMER_NAME "
		+ "ORDER BY 5 DESC "
		+ "LIMIT 20")
	List<Object[]> searchCreditAccounts(@Param("term") String term);

	/**
	 * Columns, in order: receivable id, sales bill id, bill code, bill date,
	 * outstanding, payment status.
	 *
	 * Ordered oldest bill first. The cascade sorts again for itself, but the
	 * screen shows this list as it arrives and labels the first row as the
	 * oldest debt, which is only true if the query says so.
	 *
	 * Only "Sales Billing" rows are returned - credit and debit notes stay a
	 * manual decision. About 17% of the oldest rows resolve to no sales bill at
	 * all and fall back to the receipt date for ordering.
	 */
	@Query(nativeQuery = true, value =
		"SELECT ar.ACCOUNT_RECEIVABLES_ID, ar.SALES_BILL_ID, " + ReceivableSql.BILL_CODE + ", "
		+ "       " + ReceivableSql.BILL_DATE + " AS BILL_DATE, "
		+ "       ar.AMOUNT_TO_BE_RECEIVED, ar.PAYMENT_STATUS "
		+ "FROM account_receivables ar "
		+ ReceivableSql.BILL_JOINS
		+ "WHERE ar.MASTER_ACC_NO = :masterAccountNo "
		+ ReceivableSql.OPEN_BILL_PREDICATE
		+ ReceivableSql.STALE_BILL_GUARD
		+ "ORDER BY BILL_DATE ASC, ar.ACCOUNT_RECEIVABLES_ID ASC")
	List<Object[]> findOpenBillsByMasterAccount(@Param("masterAccountNo") String masterAccountNo);

	/**
	 * As above, for a customer with no credit account.
	 *
	 * Restricted to rows carrying no account, which is what the search offered
	 * under that name. Matching on the name alone returned every bill the
	 * customer has, including the ones on their credit account - so picking
	 * "no credit account" for HAWAII DIBA loaded 21 bills owing 474,641 when
	 * the search had promised 1 owing 14,864, and a payment meant for that one
	 * bill would have cascaded across the other twenty.
	 */
	@Query(nativeQuery = true, value =
		"SELECT ar.ACCOUNT_RECEIVABLES_ID, ar.SALES_BILL_ID, " + ReceivableSql.BILL_CODE + ", "
		+ "       " + ReceivableSql.BILL_DATE + " AS BILL_DATE, "
		+ "       ar.AMOUNT_TO_BE_RECEIVED, ar.PAYMENT_STATUS "
		+ "FROM account_receivables ar "
		+ ReceivableSql.BILL_JOINS
		+ "WHERE ar.CUSTOMER_NAME = :customerName "
		+ ReceivableSql.NO_CREDIT_ACCOUNT
		+ ReceivableSql.OPEN_BILL_PREDICATE
		+ ReceivableSql.STALE_BILL_GUARD
		+ "ORDER BY BILL_DATE ASC, ar.ACCOUNT_RECEIVABLES_ID ASC")
	List<Object[]> findOpenBillsByCustomerName(@Param("customerName") String customerName);

	/**
	 * Distinct credit accounts holding open bills under this name.
	 *
	 * Customer names are not unique - the master holds 1,206 duplicates - so a
	 * name matching more than one account is refused rather than allocated,
	 * which would otherwise settle one person's bills with another's money.
	 *
	 * Counts only accounts that actually have something settleable, using the
	 * same rules as the bill list. Without the stale guard a name whose bills
	 * are all stale would be reported as ambiguous when there is in fact
	 * nothing to allocate against it.
	 */
	@Query(nativeQuery = true, value =
		"SELECT DISTINCT ar.MASTER_ACC_NO FROM account_receivables ar "
		+ ReceivableSql.BILL_JOINS
		+ "WHERE ar.CUSTOMER_NAME = :customerName "
		+ "  AND ar.MASTER_ACC_NO IS NOT NULL "
		+ ReceivableSql.OPEN_BILL_PREDICATE
		+ ReceivableSql.STALE_BILL_GUARD)
	List<String> findMasterAccountsForCustomerName(@Param("customerName") String customerName);

}
