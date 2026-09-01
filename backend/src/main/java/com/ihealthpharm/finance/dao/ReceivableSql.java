package com.ihealthpharm.finance.dao;

/**
 * How a receivable relates to its sales bill, in one place.
 *
 * These fragments used to be written out inside whichever query needed them,
 * and the copies drifted apart. The open-bill queries read a receivable's bill
 * code as COALESCE(BILL_NO, SOURCE_REF); the sum that rebuilds a bill's balance
 * matched on BILL_NO alone. 436 settleable receivables keep their code only in
 * SOURCE_REF, so settling one of those rebuilt its bill from a sum of nothing:
 * the payments already recorded against it were wiped and its debt restated as
 * the full invoice. Bill DPSB1001200065 would have gone from 1,120 outstanding
 * to 9,600 on being paid.
 *
 * Anything that has to answer "which bill is this receivable for" or "is this
 * bill still settleable" reads it from here, so two queries cannot answer
 * differently again.
 */
public final class ReceivableSql {

	/**
	 * The three ways a receivable reaches its sales bill.
	 *
	 * Most rows carry the bill id, some only the bill code, and 528 open rows
	 * carry neither - they hold the code in SOURCE_REF instead. Every one of
	 * those resolves to a real bill, so all three paths are followed: without
	 * the third the bill cannot be found at all, which costs both its date and
	 * the staleness check below.
	 */
	public static final String BILL_JOINS =
		"LEFT JOIN sales s1 ON s1.BILL_ID   = ar.SALES_BILL_ID "
		+ "LEFT JOIN sales s2 ON s2.BILL_CODE = ar.BILL_NO "
		+ "LEFT JOIN sales s3 ON s3.BILL_CODE = ar.SOURCE_REF ";

	/** The bill code, wherever the row happens to keep it. */
	public static final String BILL_CODE = "COALESCE(ar.BILL_NO, ar.SOURCE_REF)";

	/**
	 * {@link #BILL_CODE} as a predicate, for the queries that go the other way
	 * and gather every receivable belonging to one bill.
	 *
	 * Written as two sargable comparisons rather than COALESCE(...) = :billCode
	 * so both columns can still use an index. The guard on BILL_NO IS NULL is
	 * what keeps it equivalent to the COALESCE: a row naming one bill in
	 * BILL_NO is never also counted against a different one in SOURCE_REF.
	 */
	public static final String BILL_CODE_MATCH =
		"(ar.BILL_NO = :billCode OR (ar.BILL_NO IS NULL AND ar.SOURCE_REF = :billCode))";

	/** The date to order on, falling back through every join before the receipt date. */
	public static final String BILL_DATE =
		"COALESCE(s1.BILL_DATE, s2.BILL_DATE, s3.BILL_DATE, ar.RECEIPT_DATE)";

	/**
	 * Keeps settled bills out of the cascade.
	 *
	 * 615 open receivables claim money on sales bills that carry no balance -
	 * around 5.7m in total. The bill was paid but its receivable rows were never
	 * closed, so the debt only exists on one side. Settling one of those drives
	 * the bill's balance negative: DPSB2405210150 has a NET_AMOUNT of 7,894 and
	 * has already received exactly that, yet two of its rows still claim 12,453
	 * outstanding.
	 *
	 * A receivable that reaches no bill by any of the three paths is kept -
	 * there is nothing to contradict it.
	 */
	public static final String STALE_BILL_GUARD =
		"  AND (COALESCE(s1.BILL_ID, s2.BILL_ID, s3.BILL_ID) IS NULL "
		+ "       OR COALESCE(s1.BALANCE_AMOUNT, s2.BALANCE_AMOUNT, s3.BALANCE_AMOUNT) > 0) ";

	/**
	 * What counts as a bill a payment can be applied to.
	 *
	 * Shared by every query that lists open bills so the account lookup, the
	 * bill list and the cascade all agree on what is outstanding - a dropdown
	 * promising three bills and a grid then showing two would read as a bug.
	 *
	 * Only "Sales Billing" rows qualify: credit and debit notes stay a manual
	 * decision.
	 */
	public static final String OPEN_BILL_PREDICATE =
		"  AND ar.SOURCE_TYPE = 'Sales Billing' "
		+ "  AND ar.PAYMENT_STATUS IN ('Pending','Partially Paid') "
		+ "  AND ar.AMOUNT_TO_BE_RECEIVED > 0 "
		+ "  AND COALESCE(ar.ACTIVE_S,'Y') = 'Y' ";

	/**
	 * A receivable belonging to no credit account.
	 *
	 * The search box offers such customers as a separate entry - "no credit
	 * account" - counting only these rows. Anything that then loads their bills
	 * has to use the same rule, or the two disagree: HAWAII DIBA was offered as
	 * one bill owing 14,864 and, once picked, produced 21 bills owing 474,641,
	 * because the bill lookup matched on name alone and swept in the twenty
	 * bills sitting on her credit account.
	 */
	public static final String NO_CREDIT_ACCOUNT =
		"  AND (ar.MASTER_ACC_NO IS NULL OR ar.MASTER_ACC_NO = '') ";

	/**
	 * Which rows count towards what a bill has received.
	 *
	 * Wider than {@link #OPEN_BILL_PREDICATE} on purpose: a bill's total
	 * received has to include the rows that are already settled, which is
	 * exactly what the open-bill list excludes.
	 */
	public static final String RECEIVED_PREDICATE =
		"  AND ar.SOURCE_TYPE = 'Sales Billing' "
		+ "  AND COALESCE(ar.ACTIVE_S,'Y') = 'Y' "
		+ "  AND ar.PAYMENT_STATUS <> 'Cancel' ";

	private ReceivableSql() {
		// Constants only.
	}

}
