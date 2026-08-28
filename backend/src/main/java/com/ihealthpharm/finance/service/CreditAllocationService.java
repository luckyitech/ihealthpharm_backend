package com.ihealthpharm.finance.service;

import java.util.List;

import com.ihealthpharm.finance.dto.CreditAllocationPlanDTO;
import com.ihealthpharm.finance.dto.CreditAllocationRequestDTO;
import com.ihealthpharm.finance.dto.OpenBillDTO;

public interface CreditAllocationService {

	/** The bills a payment could be applied to, in the order it would fill them. */
	List<OpenBillDTO> findOpenBills(CreditAllocationRequestDTO request);

	/**
	 * Works out what the payment would settle without changing anything.
	 *
	 * Backs the confirmation screen, and lets the cascade be run alongside the
	 * manual process for comparison before it is allowed to update records.
	 */
	CreditAllocationPlanDTO previewAllocation(CreditAllocationRequestDTO request);

}
