package com.ihealthpharm.finance.controller;

import static org.springframework.http.HttpStatus.OK;

import java.util.List;

import javax.validation.Valid;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.ihealthpharm.commons.BaseDto;
import com.ihealthpharm.finance.dto.CreditAllocationPlanDTO;
import com.ihealthpharm.finance.dto.CreditAllocationRequestDTO;
import com.ihealthpharm.finance.dto.OpenBillDTO;
import com.ihealthpharm.finance.helper.CreditAllocationHelper;
import com.ihealthpharm.finance.service.CreditAllocationService;

import lombok.extern.slf4j.Slf4j;

/**
 * Read-only endpoints for credit settlement.
 *
 * Nothing here changes a record: they show which bills are outstanding and what
 * a payment would settle. Applying an allocation is deliberately a separate
 * endpoint still to be added, so the cascade can be exercised against live data
 * first.
 */
@RestController
@CrossOrigin
@Slf4j
public class CreditAllocationController {

	@Autowired
	CreditAllocationService creditAllocationService;

	@Autowired
	CreditAllocationHelper creditAllocationHelper;

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

}
