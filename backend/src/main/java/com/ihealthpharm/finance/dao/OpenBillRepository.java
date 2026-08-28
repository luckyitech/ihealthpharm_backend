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
 */
@Repository
public interface OpenBillRepository extends JpaRepository<CreditAllocationBatchModel, Integer> {

	/**
	 * Columns, in order: receivable id, sales bill id, bill code, bill date,
	 * outstanding, payment status.
	 *
	 * Only "Sales Billing" rows are returned - credit and debit notes stay a
	 * manual decision. The date falls back through both join paths before
	 * settling for the receipt date: about 17% of the oldest rows (all
	 * pre-2022) no longer resolve to a sales bill at all.
	 */
	@Query(nativeQuery = true, value =
		"SELECT ar.ACCOUNT_RECEIVABLES_ID, ar.SALES_BILL_ID, ar.BILL_NO, "
		+ "       COALESCE(s1.BILL_DATE, s2.BILL_DATE, ar.RECEIPT_DATE) AS BILL_DATE, "
		+ "       ar.AMOUNT_TO_BE_RECEIVED, ar.PAYMENT_STATUS "
		+ "FROM account_receivables ar "
		+ "LEFT JOIN sales s1 ON s1.BILL_ID   = ar.SALES_BILL_ID "
		+ "LEFT JOIN sales s2 ON s2.BILL_CODE = ar.BILL_NO "
		+ "WHERE ar.MASTER_ACC_NO = :masterAccountNo "
		+ "  AND ar.SOURCE_TYPE = 'Sales Billing' "
		+ "  AND ar.PAYMENT_STATUS IN ('Pending','Partially Paid') "
		+ "  AND ar.AMOUNT_TO_BE_RECEIVED > 0 "
		+ "  AND COALESCE(ar.ACTIVE_S,'Y') = 'Y'")
	List<Object[]> findOpenBillsByMasterAccount(@Param("masterAccountNo") String masterAccountNo);

	/** As above, for customers with no credit account. */
	@Query(nativeQuery = true, value =
		"SELECT ar.ACCOUNT_RECEIVABLES_ID, ar.SALES_BILL_ID, ar.BILL_NO, "
		+ "       COALESCE(s1.BILL_DATE, s2.BILL_DATE, ar.RECEIPT_DATE) AS BILL_DATE, "
		+ "       ar.AMOUNT_TO_BE_RECEIVED, ar.PAYMENT_STATUS "
		+ "FROM account_receivables ar "
		+ "LEFT JOIN sales s1 ON s1.BILL_ID   = ar.SALES_BILL_ID "
		+ "LEFT JOIN sales s2 ON s2.BILL_CODE = ar.BILL_NO "
		+ "WHERE ar.CUSTOMER_NAME = :customerName "
		+ "  AND ar.SOURCE_TYPE = 'Sales Billing' "
		+ "  AND ar.PAYMENT_STATUS IN ('Pending','Partially Paid') "
		+ "  AND ar.AMOUNT_TO_BE_RECEIVED > 0 "
		+ "  AND COALESCE(ar.ACTIVE_S,'Y') = 'Y'")
	List<Object[]> findOpenBillsByCustomerName(@Param("customerName") String customerName);

	/**
	 * Distinct credit accounts holding open bills under this name.
	 *
	 * Customer names are not unique - the master holds 1,206 duplicates - so a
	 * name matching more than one account is refused rather than allocated,
	 * which would otherwise settle one person's bills with another's money.
	 */
	@Query(nativeQuery = true, value =
		"SELECT DISTINCT ar.MASTER_ACC_NO FROM account_receivables ar "
		+ "WHERE ar.CUSTOMER_NAME = :customerName "
		+ "  AND ar.SOURCE_TYPE = 'Sales Billing' "
		+ "  AND ar.PAYMENT_STATUS IN ('Pending','Partially Paid') "
		+ "  AND ar.AMOUNT_TO_BE_RECEIVED > 0 "
		+ "  AND ar.MASTER_ACC_NO IS NOT NULL")
	List<String> findMasterAccountsForCustomerName(@Param("customerName") String customerName);

}
