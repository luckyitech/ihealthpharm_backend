package com.ihealthpharm.finance.model;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.OneToMany;

import com.fasterxml.jackson.annotation.JsonManagedReference;
import com.ihealthpharm.masters.model.AuditModel;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * One credit payment, and the record of which bills it settled.
 *
 * Exists so an allocation can be reported on and reversed exactly - a bounced
 * cheque may have cleared several bills, and each of those has to go back to
 * the status it held before, not a recalculated one.
 */
@Entity(name = "credit_allocation_batch")
@Data
@EqualsAndHashCode(of = "allocationBatchId", callSuper = false)
public class CreditAllocationBatchModel extends AuditModel {

	private static final long serialVersionUID = 1L;

	/** Allocation runs oldest bill first unless the user chooses otherwise. */
	public static final String ORDER_OLDEST = "OLDEST";
	public static final String ORDER_NEWEST = "NEWEST";

	/** Cheques are held here until approved; everything else allocates on receipt. */
	public static final String STATUS_HELD_PENDING_APPROVAL = "HELD_PENDING_APPROVAL";
	public static final String STATUS_ALLOCATED = "ALLOCATED";
	public static final String STATUS_REVERSED = "REVERSED";
	public static final String STATUS_REVERSAL_FLAGGED = "REVERSAL_FLAGGED";

	/** What to do with money left over once every bill is settled. */
	public static final String OVERPAYMENT_REFUND = "REFUND";
	public static final String OVERPAYMENT_HOLD_ON_ACCOUNT = "HOLD_ON_ACCOUNT";

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "ALLOCATION_BATCH_ID")
	private Integer allocationBatchId;

	@Column(name = "BATCH_REF", length = 30)
	private String batchRef;

	@Column(name = "CUSTOMER_ID")
	private Integer customerId;

	@Column(name = "MASTER_ACCOUNT_ID")
	private Integer masterAccountId;

	@Column(name = "PHARMACY_ID")
	private Integer pharmacyId;

	@Column(name = "PAYMENT_AMOUNT")
	private BigDecimal paymentAmount;

	@Column(name = "ALLOCATED_AMOUNT")
	private BigDecimal allocatedAmount;

	@Column(name = "UNALLOCATED_AMOUNT")
	private BigDecimal unallocatedAmount;

	@Column(name = "ALLOCATION_ORDER", length = 10)
	private String allocationOrder;

	/**
	 * 'Y' once the user has explicitly picked the order, which is required when
	 * the customer has bills older than the ageing threshold.
	 */
	@Column(name = "ORDER_CONFIRMED_S", length = 1)
	private Character orderConfirmedS;

	@Column(name = "PAYMENT_TYPE", length = 20)
	private String paymentType;

	@Column(name = "PAYMENT_REF", length = 50)
	private String paymentRef;

	@Column(name = "CHEQUE_ID")
	private Integer chequeId;

	@Column(name = "STATUS", length = 20)
	private String status;

	@Column(name = "OVERPAYMENT_ACTION", length = 20)
	private String overpaymentAction;

	@Column(name = "REVERSES_BATCH_ID")
	private Integer reversesBatchId;

	@Column(name = "REVERSAL_REASON", length = 200)
	private String reversalReason;

	@Column(name = "APPROVED_BY")
	private Integer approvedBy;

	@Column(name = "APPROVED_DATE")
	private Date approvedDate;

	/** 'Y' for historical payments loaded from the EOD records. */
	@Column(name = "BACKFILL_S", length = 1)
	private Character backfillS;

	@Column(name = "AUDIT_ID")
	private Integer auditId;

	@Column(name = "ACTIVE_S", length = 1)
	private Character activeS;

	@JsonManagedReference
	@OneToMany(mappedBy = "allocationBatch", fetch = FetchType.LAZY)
	private List<CreditAllocationLineModel> allocationLines;

}
