package com.ihealthpharm.finance.helper;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import lombok.Data;

@Configuration
@Data
public class CreditAllocationHelper {

	@Value("${creditAllocation.access.response}")
	public String accessMessage;

	@Value("${creditAllocation.openBills.response}")
	public String openBillsMessage;

	@Value("${creditAllocation.preview.response}")
	public String previewMessage;

	@Value("${creditAllocation.orderConfirmation.response}")
	public String orderConfirmationMessage;

	@Value("${creditAllocation.applied.response}")
	public String appliedMessage;

	/**
	 * A cheque is recorded but settles nothing until it is approved, so saying
	 * the credit was settled would be untrue of the only path that reaches it.
	 */
	@Value("${creditAllocation.chequeHeld.response}")
	public String chequeHeldMessage;

	@Value("${creditAllocation.chequeApproved.response}")
	public String chequeApprovedMessage;

	@Value("${creditAllocation.reversed.response}")
	public String reversedMessage;

	@Value("${creditAllocation.reversalFlagged.response}")
	public String reversalFlaggedMessage;

}
