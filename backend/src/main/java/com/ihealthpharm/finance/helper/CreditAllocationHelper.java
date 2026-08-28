package com.ihealthpharm.finance.helper;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import lombok.Data;

@Configuration
@Data
public class CreditAllocationHelper {

	@Value("${creditAllocation.openBills.response}")
	public String openBillsMessage;

	@Value("${creditAllocation.preview.response}")
	public String previewMessage;

	@Value("${creditAllocation.orderConfirmation.response}")
	public String orderConfirmationMessage;

	@Value("${creditAllocation.applied.response}")
	public String appliedMessage;

	@Value("${creditAllocation.chequeApproved.response}")
	public String chequeApprovedMessage;

	@Value("${creditAllocation.reversed.response}")
	public String reversedMessage;

	@Value("${creditAllocation.reversalFlagged.response}")
	public String reversalFlaggedMessage;

}
