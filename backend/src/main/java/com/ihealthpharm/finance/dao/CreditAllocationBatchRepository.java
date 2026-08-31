package com.ihealthpharm.finance.dao;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ihealthpharm.finance.model.CreditAllocationBatchModel;

public interface CreditAllocationBatchRepository extends JpaRepository<CreditAllocationBatchModel, Integer> {

	CreditAllocationBatchModel findByBatchRef(String batchRef);

	List<CreditAllocationBatchModel> findByCustomerIdAndActiveSOrderByAllocationBatchIdDesc(Integer customerId,
			Character activeS);

	List<CreditAllocationBatchModel> findByMasterAccountIdAndActiveSOrderByAllocationBatchIdDesc(Integer masterAccountId,
			Character activeS);

	/** Cheque batches waiting on approval before their bills are settled. */
	List<CreditAllocationBatchModel> findByChequeIdAndStatus(Integer chequeId, String status);

	/** Drives the daily sequence in a batch reference. */
	long countByBatchRefStartingWith(String batchRefPrefix);

	/**
	 * The most recent settlements, for the screen that reverses them.
	 *
	 * Blank account means every account, so the screen can show recent activity
	 * before anyone has been searched for. Reversed batches stay in the list -
	 * seeing that something was already reversed is the point, and hiding them
	 * would invite a second attempt.
	 */
	@Query(nativeQuery = true, value =
		"SELECT b.BATCH_REF, b.MASTER_ACCOUNT_NO, b.PAYMENT_AMOUNT, b.ALLOCATED_AMOUNT, "
		+ "       b.PAYMENT_TYPE, b.PAYMENT_REF, b.STATUS, b.ALLOCATION_ORDER, b.CREATION_TS, "
		+ "       b.REVERSAL_REASON, b.UNALLOCATED_AMOUNT, b.OVERPAYMENT_ACTION, "
		+ "       (SELECT COUNT(*) FROM credit_allocation_line l "
		+ "         WHERE l.ALLOCATION_BATCH_ID = b.ALLOCATION_BATCH_ID) "
		+ "FROM credit_allocation_batch b "
		+ "WHERE (:masterAccountNo IS NULL OR :masterAccountNo = '' "
		+ "       OR b.MASTER_ACCOUNT_NO = :masterAccountNo) "
		+ "ORDER BY b.ALLOCATION_BATCH_ID DESC "
		+ "LIMIT 30")
	List<Object[]> findRecentSettlements(@Param("masterAccountNo") String masterAccountNo);

}
