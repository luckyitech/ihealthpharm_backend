package com.ihealthpharm.finance.dto;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;

import lombok.Data;

/**
 * One bill in a proposed allocation, and what applying the payment would do to it.
 */
@Data
public class CreditAllocationPlanLineDTO implements Serializable {

	private static final long serialVersionUID = 1L;

	/** Position in the cascade - 1 is the first bill the money reaches. */
	private Integer sequenceNo;

	private Integer accountReceivablesId;

	private Integer salesBillId;

	private String billCode;

	private LocalDate billDate;

	private BigDecimal amountApplied;

	private BigDecimal priorBalance;

	private String priorPaymentStatus;

	/** "Paid" when the bill is cleared, "Partially Paid" when the money ran out on it. */
	private String resultingPaymentStatus;

}
