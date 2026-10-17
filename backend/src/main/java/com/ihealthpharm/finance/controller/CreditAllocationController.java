package com.ihealthpharm.finance.controller;

import static org.springframework.http.HttpStatus.OK;

import java.security.Principal;
import java.util.List;

import javax.validation.Valid;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ihealthpharm.commons.BaseDto;
import com.ihealthpharm.finance.dto.CreditAccountSearchDTO;
import com.ihealthpharm.finance.dto.CreditAllocationPlanDTO;
import com.ihealthpharm.finance.dto.CreditAllocationRequestDTO;
import com.ihealthpharm.finance.dto.CreditSettlementAccessDTO;
import com.ihealthpharm.finance.dto.CreditSettlementActionDTO;
import com.ihealthpharm.finance.dto.OpenBillDTO;
import com.ihealthpharm.finance.dto.SettlementSummaryDTO;
import com.ihealthpharm.finance.model.CreditAllocationBatchModel;
import com.ihealthpharm.finance.helper.CreditAllocationHelper;
import com.ihealthpharm.finance.service.CreditAllocationService;

import lombok.extern.slf4j.Slf4j;

/**
 * Endpoints for credit settlement.
 *
 * Looking up bills and previewing an allocation change nothing, so they can be
 * run against live data freely. The three that do change records - apply,
 * approve and reverse - each require both the caller's permission and their
 * approval PIN.
 */
@RestController
@CrossOrigin
@Slf4j
public class CreditAllocationController {

	@Autowired
	CreditAllocationService creditAllocationService;

	@Autowired
	CreditAllocationHelper creditAllocationHelper;

	/**
	 * What the signed-in user may do here, so the screen can hide the rest.
	 *
	 * A GET with no body: the user is taken from the authenticated principal,
	 * so there is nothing for a caller to supply and nothing to tamper with.
	 */
	@GetMapping("/creditAllocation/access")
	public ResponseEntity<BaseDto<CreditSettlementAccessDTO>> getAccess(Principal principal) {

		CreditSettlementAccessDTO access = creditAllocationService.getAccess(principal.getName());

		return new BaseDto<>(access, creditAllocationHelper.getAccessMessage(), OK).respond();
	}

	/**
	 * Credit accounts matching what the user is typing, for the search box.
	 *
	 * Read-only and cheap, so the screen can call it on each keystroke once the
	 * term is long enough to be worth looking up.
	 */
	@GetMapping("/creditAllocation/searchAccounts")
	public ResponseEntity<BaseDto<List<CreditAccountSearchDTO>>> searchAccounts(
			@RequestParam String term) {

		List<CreditAccountSearchDTO> accounts = creditAllocationService.searchCreditAccounts(term);

		return new BaseDto<>(accounts, creditAllocationHelper.getOpenBillsMessage(), OK).respond();
	}

	/** The outstanding bills a payment could be applied to. */
	@PostMapping("/creditAllocation/openBills")
	public ResponseEntity<BaseDto<List<OpenBillDTO>>> getOpenBills(
			@Valid @RequestBody CreditAllocationRequestDTO request) {

		List<OpenBillDTO> openBills = creditAllocationService.findOpenBills(request);
		log.info("Credit settlement: {} open bills found", openBills.size());

		return new BaseDto<>(openBills, creditAllocationHelper.getOpenBillsMessage(), OK).respond();
	}

	/**
	 * What the payment would settle, without settling it.
	 *
	 * A plan comes back with orderConfirmationRequired set and no lines when the
	 * customer has debt past the ageing threshold - the caller should ask which
	 * order to use and send the request again with orderConfirmed.
	 */
	@PostMapping("/creditAllocation/preview")
	public ResponseEntity<BaseDto<CreditAllocationPlanDTO>> previewAllocation(
			@Valid @RequestBody CreditAllocationRequestDTO request) {

		CreditAllocationPlanDTO plan = creditAllocationService.previewAllocation(request);

		String message = plan.isOrderConfirmationRequired()
				? creditAllocationHelper.getOrderConfirmationMessage()
				: creditAllocationHelper.getPreviewMessage();

		return new BaseDto<>(plan, message, OK).respond();
	}

	/**
	 * Settles the bills and records the batch.
	 *
	 * The signed-in user is taken from the authenticated principal rather than
	 * the request body, so a caller cannot settle under someone else's PIN.
	 */
	@PostMapping("/creditAllocation/apply")
	public ResponseEntity<BaseDto<CreditAllocationPlanDTO>> applyAllocation(
			@Valid @RequestBody CreditAllocationRequestDTO request, Principal principal) {

		CreditAllocationPlanDTO plan = creditAllocationService.applyAllocation(request, principal.getName());

		// Reported from the status that was recorded, not from what was asked
		// for: a cheque is written down but settles nothing until approved, and
		// calling that "settled" is what led operators to enter it twice.
		String message;
		if (plan.isOrderConfirmationRequired()) {
			message = creditAllocationHelper.getOrderConfirmationMessage();
		} else if (CreditAllocationBatchModel.STATUS_HELD_PENDING_APPROVAL.equals(plan.getStatus())) {
			message = creditAllocationHelper.getChequeHeldMessage();
		} else {
			message = creditAllocationHelper.getAppliedMessage();
		}

		return new BaseDto<>(plan, message, OK).respond();
	}

	/**
	 * Recent settlements, so one can be picked to reverse or approve.
	 *
	 * Read-only. Pass no account to see recent activity across all accounts.
	 */
	@GetMapping("/creditAllocation/recentSettlements")
	public ResponseEntity<BaseDto<List<SettlementSummaryDTO>>> recentSettlements(
			@RequestParam(required = false) String masterAccountNo) {

		List<SettlementSummaryDTO> settlements =
				creditAllocationService.findRecentSettlements(masterAccountNo);

		return new BaseDto<>(settlements, creditAllocationHelper.getOpenBillsMessage(), OK).respond();
	}

	/** Settles a cheque payment that was held until the cheque cleared. */
	@PostMapping("/creditAllocation/approveCheque")
	public ResponseEntity<BaseDto<CreditAllocationBatchModel>> approveCheque(
			@Valid @RequestBody CreditSettlementActionDTO action, Principal principal) {

		CreditAllocationBatchModel batch = creditAllocationService
				.approveChequeSettlement(action, principal.getName());

		return new BaseDto<>(batch, creditAllocationHelper.getChequeApprovedMessage(), OK).respond();
	}

	/**
	 * Unwinds a settlement, or flags it for review.
	 *
	 * Send reverseAutomatically false to leave the settlement standing and only
	 * mark it, so a bounced cheque can wait for someone to look at it.
	 */
	@PostMapping("/creditAllocation/reverse")
	public ResponseEntity<BaseDto<CreditAllocationBatchModel>> reverseSettlement(
			@Valid @RequestBody CreditSettlementActionDTO action, Principal principal) {

		CreditAllocationBatchModel batch = creditAllocationService
				.reverseSettlement(action, principal.getName());

		String message = action.isReverseAutomatically()
				? creditAllocationHelper.getReversedMessage()
				: creditAllocationHelper.getReversalFlaggedMessage();

		return new BaseDto<>(batch, message, OK).respond();
	}

}
