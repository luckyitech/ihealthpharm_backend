package com.ihealthpharm.finance.model;

import java.math.BigDecimal;
import java.time.LocalDate;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.ihealthpharm.masters.model.AuditModel;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * One bill settled by a {@link CreditAllocationBatchModel}.
 *
 * The priorBalance and priorPaymentStatus are captured at the moment the
 * allocation is applied. A reversal restores those exact values rather than
 * recalculating, so a bill that was already Partially Paid before the payment
 * goes back to Partially Paid and not to Pending.
 */
@Entity(name = "credit_allocation_line")
@Data
@EqualsAndHashCode(of = "allocationLineId", callSuper = false)
public class CreditAllocationLineModel extends AuditModel {

	private static final long serialVersionUID = 1L;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "ALLOCATION_LINE_ID")
	private Integer allocationLineId;

	@JsonBackReference
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "ALLOCATION_BATCH_ID")
	private CreditAllocationBatchModel allocationBatch;

	/** Position in the cascade - 1 is the first bill the money was applied to. */
	@Column(name = "SEQUENCE_NO")
	private Integer sequenceNo;

	@Column(name = "ACCOUNT_RECEIVABLES_ID")
	private Integer accountReceivablesId;

	@Column(name = "SALES_BILL_ID")
	private Integer salesBillId;

	@Column(name = "BILL_CODE", length = 30)
	private String billCode;

	/** Denormalised from the bill - this is the date the ordering sorted on. */
	@Column(name = "BILL_DATE")
	private LocalDate billDate;

	@Column(name = "AMOUNT_APPLIED")
	private BigDecimal amountApplied;

	@Column(name = "PRIOR_BALANCE")
	private BigDecimal priorBalance;

	@Column(name = "PRIOR_PAYMENT_STATUS", length = 20)
	private String priorPaymentStatus;

	@Column(name = "RESULTING_PAYMENT_STATUS", length = 20)
	private String resultingPaymentStatus;

	@Column(name = "AUDIT_ID")
	private Integer auditId;

	@Column(name = "ACTIVE_S", length = 1)
	private Character activeS;

}
