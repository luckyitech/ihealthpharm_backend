package com.ihealthpharm.finance.dao;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ihealthpharm.finance.model.CreditAllocationLineModel;

public interface CreditAllocationLineRepository extends JpaRepository<CreditAllocationLineModel, Integer> {

	/** The bills a batch settled, in the order the cascade filled them. */
	List<CreditAllocationLineModel> findByAllocationBatchAllocationBatchIdOrderBySequenceNoAsc(Integer allocationBatchId);

	/** Every allocation that has touched a bill - the audit trail for one invoice. */
	List<CreditAllocationLineModel> findBySalesBillIdOrderByAllocationLineIdAsc(Integer salesBillId);

	List<CreditAllocationLineModel> findByAccountReceivablesIdOrderByAllocationLineIdAsc(Integer accountReceivablesId);

}
