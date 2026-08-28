package com.ihealthpharm.finance.dto;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;

import lombok.Data;

/**
 * An outstanding bill a credit payment can be applied to.
 *
 * Built from account_receivables rows of source type "Sales Billing". Credit
 * and debit notes are deliberately excluded - those stay a manual decision.
 */
@Data
public class OpenBillDTO implements Serializable {

	private static final long serialVersionUID = 1L;

	private Integer accountReceivablesId;

	private Integer salesBillId;

	private String billCode;

	/**
	 * Date the allocation orders on. Taken from the sales bill where it can be
	 * resolved, otherwise the receipt date - about 17% of the oldest rows
	 * (all pre-2022) no longer join to a sales bill.
	 */
	private LocalDate billDate;

	/** What is still owed on this bill before the payment is applied. */
	private BigDecimal outstanding;

	/** Payment status before the payment is applied, kept so a reversal can restore it. */
	private String currentPaymentStatus;

}
