package com.ihealthpharm.finance.dao;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ihealthpharm.finance.model.CreditAllocationBatchModel;

public interface CreditAllocationBatchRepository extends JpaRepository<CreditAllocationBatchModel, Integer> {

	CreditAllocationBatchModel findByBatchRef(String batchRef);

	List<CreditAllocationBatchModel> findByCustomerIdAndActiveSOrderByAllocationBatchIdDesc(Integer customerId,
			Character activeS);

	List<CreditAllocationBatchModel> findByMasterAccountIdAndActiveSOrderByAllocationBatchIdDesc(Integer masterAccountId,
			Character activeS);

	/** Cheque batches waiting on approval before their bills are settled. */
	List<CreditAllocationBatchModel> findByChequeIdAndStatus(Integer chequeId, String status);

}
