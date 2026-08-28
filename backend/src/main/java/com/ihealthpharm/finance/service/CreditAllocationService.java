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

	/**
	 * Settles the bills the plan names, and records the batch so it can be
	 * reversed.
	 *
	 * The settlement itself is handed to the existing receivables update, so a
	 * bill cleared here goes through exactly the same path - sales bill status,
	 * credit limit, ledger - as one Vinod ticks by hand.
	 *
	 * Requires the caller's approval PIN. A cheque payment is recorded but not
	 * settled: its batch is held until the cheque is approved.
	 *
	 * @param userName the signed-in user, whose PIN is checked
	 */
	CreditAllocationPlanDTO applyAllocation(CreditAllocationRequestDTO request, String userName);

}
