package com.ihealthpharm.finance.service;

import java.util.List;

import com.ihealthpharm.finance.dto.CreditAccountSearchDTO;
import com.ihealthpharm.finance.dto.CreditAllocationPlanDTO;
import com.ihealthpharm.finance.dto.CreditAllocationRequestDTO;
import com.ihealthpharm.finance.dto.CreditSettlementAccessDTO;
import com.ihealthpharm.finance.dto.CreditSettlementActionDTO;
import com.ihealthpharm.finance.dto.OpenBillDTO;
import com.ihealthpharm.finance.dto.SettlementSummaryDTO;
import com.ihealthpharm.finance.model.CreditAllocationBatchModel;

public interface CreditAllocationService {

	/**
	 * What the signed-in user is allowed to do here.
	 *
	 * Lets the screen hide what the user cannot use. The endpoints enforce the
	 * same permissions themselves, so this is only what to show, never the
	 * check itself.
	 */
	CreditSettlementAccessDTO getAccess(String userName);

	/**
	 * Credit accounts matching what the user has typed into the search box.
	 *
	 * Lets the screen offer accounts as the user types, rather than requiring
	 * an account code to be known or typed exactly. Returns nothing for a term
	 * shorter than two characters.
	 */
	List<CreditAccountSearchDTO> searchCreditAccounts(String term);

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

	/**
	 * The most recent settlements, newest first.
	 *
	 * Backs the screen that reverses a settlement or approves a held cheque.
	 * Pass a blank account for recent activity across all accounts.
	 */
	List<SettlementSummaryDTO> findRecentSettlements(String masterAccountNo);

	/**
	 * Settles a cheque batch that was held pending approval.
	 *
	 * Until this runs the debt stands, so a cheque that is never approved simply
	 * leaves the bills outstanding.
	 */
	CreditAllocationBatchModel approveChequeSettlement(CreditSettlementActionDTO action, String userName);

	/**
	 * Unwinds a settlement, putting every bill back exactly as it was.
	 *
	 * Each line restores its receivable from the values recorded when it was
	 * settled, the bills are then rebuilt from those receivables, and the credit
	 * limit returns to what the batch recorded. Set
	 * {@code reverseAutomatically} false to flag the batch for review instead of
	 * unwinding it, so a bounced cheque can either reverse itself or wait for
	 * someone to look at it.
	 */
	CreditAllocationBatchModel reverseSettlement(CreditSettlementActionDTO action, String userName);

}
