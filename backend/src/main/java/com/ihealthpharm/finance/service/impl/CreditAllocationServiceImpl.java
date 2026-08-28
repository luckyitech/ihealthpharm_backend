package com.ihealthpharm.finance.service.impl;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;

import javax.transaction.Transactional;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.ihealthpharm.exception.IHealthPharmException;
import com.ihealthpharm.finance.dao.AccountReceivablesRepository;
import com.ihealthpharm.finance.dao.CreditAllocationBatchRepository;
import com.ihealthpharm.finance.dao.CreditAllocationLineRepository;
import com.ihealthpharm.finance.dao.OpenBillRepository;
import com.ihealthpharm.finance.dto.CreditAllocationPlanDTO;
import com.ihealthpharm.finance.dto.CreditAllocationPlanLineDTO;
import com.ihealthpharm.finance.dto.CreditAllocationRequestDTO;
import com.ihealthpharm.finance.dto.OpenBillDTO;
import com.ihealthpharm.finance.model.AccountReceivablesModel;
import com.ihealthpharm.finance.model.CreditAllocationBatchModel;
import com.ihealthpharm.finance.model.CreditAllocationLineModel;
import com.ihealthpharm.finance.service.AccountReceivablesService;
import com.ihealthpharm.finance.service.CreditAllocationEngine;
import com.ihealthpharm.finance.service.CreditAllocationService;
import com.ihealthpharm.finance.service.GeneralLedgerService;
import com.ihealthpharm.masters.service.EmployeeCredentialsService;

import lombok.extern.slf4j.Slf4j;

/**
 * Loads a customer's outstanding bills and asks the engine what a payment
 * would settle.
 *
 * Reading is kept apart from applying on purpose: this class can be called
 * safely against live data to show a user what would happen, or to compare the
 * cascade against the manual process, without touching a record.
 */
@Service
@Transactional
@Slf4j
public class CreditAllocationServiceImpl implements CreditAllocationService {

	/** Status a receivable carries once its payment has been approved. */
	private static final String STATUS_APPROVED = "Approved";

	@Autowired
	OpenBillRepository openBillRepository;

	@Autowired
	CreditAllocationEngine creditAllocationEngine;

	@Autowired
	CreditAllocationBatchRepository creditAllocationBatchRepository;

	@Autowired
	CreditAllocationLineRepository creditAllocationLineRepository;

	@Autowired
	AccountReceivablesRepository accountReceivablesRepository;

	@Autowired
	AccountReceivablesService accountReceivablesService;

	@Autowired
	EmployeeCredentialsService employeeCredentialsService;

	@Autowired
	GeneralLedgerService generalLedgerService;

	@Override
	public List<OpenBillDTO> findOpenBills(CreditAllocationRequestDTO request) {
		validateRequest(request);

		if (StringUtils.isNotBlank(request.getMasterAccountNo())) {
			return toOpenBills(
					openBillRepository.findOpenBillsByMasterAccount(request.getMasterAccountNo().trim()));
		}

		String customerName = request.getCustomerName().trim();
		List<String> accounts = openBillRepository.findMasterAccountsForCustomerName(customerName);

		if (accounts.size() > 1) {
			// Names are not unique. Guessing here would settle one person's bills
			// with another person's money.
			log.warn("Refusing to allocate by name '{}': it matches {} credit accounts", customerName,
					accounts.size());
			throw new IHealthPharmException(
					"More than one credit account is open under this name. Select the credit account to settle.",
					HttpStatus.CONFLICT);
		}

		return toOpenBills(openBillRepository.findOpenBillsByCustomerName(customerName));
	}

	@Override
	public CreditAllocationPlanDTO previewAllocation(CreditAllocationRequestDTO request) {
		List<OpenBillDTO> openBills = findOpenBills(request);

		return creditAllocationEngine.buildPlan(openBills, request.getPaymentAmount(),
				request.getAllocationOrder(), request.isOrderConfirmed());
	}

	@Override
	public CreditAllocationPlanDTO applyAllocation(CreditAllocationRequestDTO request, String userName) {
		employeeCredentialsService.verifyApprovalPin(userName, request.getApprovalPin());

		CreditAllocationPlanDTO plan = previewAllocation(request);

		if (plan.isOrderConfirmationRequired()) {
			// The engine produced no lines; nothing is settled until the user picks an order.
			return plan;
		}
		if (plan.getLines().isEmpty()) {
			throw new IHealthPharmException("This account has no outstanding bills to settle.",
					HttpStatus.CONFLICT);
		}
		if (plan.getUnallocatedAmount().signum() > 0
				&& StringUtils.isBlank(request.getOverpaymentAction())) {
			throw new IHealthPharmException(
					"Payment exceeds everything outstanding. Choose whether to refund the balance or hold it on the account.",
					HttpStatus.CONFLICT);
		}

		CreditAllocationBatchModel batch = saveBatch(request, plan, userName);
		saveLines(batch, plan);

		if (isChequeHeld(request)) {
			// Recorded, but the debt stands until the cheque is approved.
			log.info("Credit settlement {} held pending cheque approval", batch.getBatchRef());
			return plan;
		}

		settle(request, plan);
		log.info("Credit settlement {} applied: {} across {} bills for user {}", batch.getBatchRef(),
				plan.getAllocatedAmount(), plan.getLines().size(), userName);

		return plan;
	}

	/** A cheque is recorded on receipt but only settles once approved. */
	private boolean isChequeHeld(CreditAllocationRequestDTO request) {
		return Objects.nonNull(request.getChequeId());
	}

	private CreditAllocationBatchModel saveBatch(CreditAllocationRequestDTO request,
			CreditAllocationPlanDTO plan, String userName) {

		CreditAllocationBatchModel batch = new CreditAllocationBatchModel();
		batch.setBatchRef(nextBatchRef());
		batch.setMasterAccountNo(request.getMasterAccountNo());
		batch.setCustomerId(request.getCustomerId());
		batch.setPharmacyId(request.getPharmacyId());
		batch.setPaymentAmount(plan.getPaymentAmount());
		batch.setAllocatedAmount(plan.getAllocatedAmount());
		batch.setUnallocatedAmount(plan.getUnallocatedAmount());
		batch.setAllocationOrder(plan.getAllocationOrder());
		batch.setOrderConfirmedS(request.isOrderConfirmed() ? 'Y' : 'N');
		batch.setPaymentType(request.getPaymentType());
		batch.setPaymentRef(request.getPaymentRef());
		batch.setChequeId(request.getChequeId());
		batch.setOverpaymentAction(request.getOverpaymentAction());
		batch.setApprovedBy(request.getApprovedBy());
		batch.setApprovedDate(new Date());
		batch.setBackfillS(request.isBackfill() ? 'Y' : 'N');
		batch.setActiveS('Y');
		batch.setCreatedUser(userName);
		batch.setStatus(isChequeHeld(request)
				? CreditAllocationBatchModel.STATUS_HELD_PENDING_APPROVAL
				: CreditAllocationBatchModel.STATUS_ALLOCATED);

		return creditAllocationBatchRepository.save(batch);
	}

	private void saveLines(CreditAllocationBatchModel batch, CreditAllocationPlanDTO plan) {
		for (CreditAllocationPlanLineDTO planLine : plan.getLines()) {
			CreditAllocationLineModel line = new CreditAllocationLineModel();
			line.setAllocationBatch(batch);
			line.setSequenceNo(planLine.getSequenceNo());
			line.setAccountReceivablesId(planLine.getAccountReceivablesId());
			line.setSalesBillId(planLine.getSalesBillId());
			line.setBillCode(planLine.getBillCode());
			line.setBillDate(planLine.getBillDate());
			line.setAmountApplied(planLine.getAmountApplied());
			line.setPriorBalance(planLine.getPriorBalance());
			line.setPriorPaymentStatus(planLine.getPriorPaymentStatus());
			line.setResultingPaymentStatus(planLine.getResultingPaymentStatus());
			line.setActiveS('Y');
			creditAllocationLineRepository.save(line);
		}
	}

	/**
	 * Hands the settled bills to the existing receivables update, in the same
	 * shape the Receipts screen sends when the bills are ticked by hand, so the
	 * sales bill, credit limit and ledger are all updated by the code that
	 * already does it.
	 */
	private void settle(CreditAllocationRequestDTO request, CreditAllocationPlanDTO plan) {
		List<AccountReceivablesModel> payload = new ArrayList<>();

		for (CreditAllocationPlanLineDTO line : plan.getLines()) {
			AccountReceivablesModel receivable = accountReceivablesRepository
					.findById(line.getAccountReceivablesId())
					.orElseThrow(() -> new IHealthPharmException(
							"Outstanding bill " + line.getBillCode() + " could not be loaded.",
							HttpStatus.NOT_FOUND));

			boolean settledInFull = CreditAllocationEngine.STATUS_PAID
					.equals(line.getResultingPaymentStatus());

			// Field meanings follow the Receipts screen exactly: a bill cleared in
			// full leaves nothing to receive, while a partial payment leaves the
			// remainder outstanding and records what was paid in both
			// amountReceived and partialAmt.
			receivable.setAmountReceived(line.getAmountApplied().floatValue());
			receivable.setPartialAmt(settledInFull ? 0f : line.getAmountApplied().floatValue());
			receivable.setAmountToBeReceived(settledInFull ? 0f
					: line.getPriorBalance().subtract(line.getAmountApplied()).floatValue());
			receivable.setPaymentStatus(line.getResultingPaymentStatus());
			receivable.setStatus(STATUS_APPROVED);
			receivable.setPaymentType(request.getPaymentType());
			receivable.setApprovedBy(request.getApprovedBy());
			receivable.setApprovedDate(LocalDate.now());

			if (StringUtils.isNotBlank(request.getReceiptNumber())) {
				receivable.setReceiptNumber(request.getReceiptNumber());
			}

			payload.add(receivable);
		}

		List<AccountReceivablesModel> settled = accountReceivablesService
				.updateAccountsReceivablesData(payload);

		// The Receipts screen makes two calls, not one: after saving the
		// receivables it posts the ledger entries separately. Settling without
		// this leaves the receivable updated and the accounts unbalanced.
		generalLedgerService.saveMultipleLedgersDataForAccRecievables(
				Objects.nonNull(settled) && !settled.isEmpty() ? settled : payload);
	}

	/** Batch reference in the house style: DPCA + ddMMyy + a daily sequence. */
	private String nextBatchRef() {
		String prefix = "DPCA" + new SimpleDateFormat("ddMMyy").format(new Date());
		long todayCount = creditAllocationBatchRepository.countByBatchRefStartingWith(prefix);
		return String.format("%s%04d", prefix, todayCount + 1);
	}

	private void validateRequest(CreditAllocationRequestDTO request) {
		if (!Objects.nonNull(request)) {
			throw new IHealthPharmException("Payment details are required.", HttpStatus.BAD_REQUEST);
		}
		if (StringUtils.isBlank(request.getMasterAccountNo()) && StringUtils.isBlank(request.getCustomerName())) {
			throw new IHealthPharmException("Select a customer or credit account to settle.",
					HttpStatus.BAD_REQUEST);
		}
	}

	/**
	 * Columns arrive in the order declared on the query: receivable id, sales
	 * bill id, bill code, bill date, outstanding, payment status.
	 */
	private List<OpenBillDTO> toOpenBills(List<Object[]> rows) {
		List<OpenBillDTO> openBills = new ArrayList<>();
		if (!Objects.nonNull(rows)) {
			return openBills;
		}

		for (Object[] row : rows) {
			OpenBillDTO openBill = new OpenBillDTO();
			openBill.setAccountReceivablesId(toInteger(row[0]));
			openBill.setSalesBillId(toInteger(row[1]));
			openBill.setBillCode(toString(row[2]));
			openBill.setBillDate(toLocalDate(row[3]));
			openBill.setOutstanding(toBigDecimal(row[4]));
			openBill.setCurrentPaymentStatus(toString(row[5]));
			openBills.add(openBill);
		}
		return openBills;
	}

	private Integer toInteger(Object value) {
		return Objects.nonNull(value) ? ((Number) value).intValue() : null;
	}

	private String toString(Object value) {
		return Objects.nonNull(value) ? value.toString() : null;
	}

	private LocalDate toLocalDate(Object value) {
		if (!Objects.nonNull(value)) {
			return null;
		}
		if (value instanceof java.sql.Date) {
			return ((java.sql.Date) value).toLocalDate();
		}
		if (value instanceof Date) {
			return new java.sql.Date(((Date) value).getTime()).toLocalDate();
		}
		return LocalDate.parse(value.toString());
	}

	/**
	 * The source columns are FLOAT, so the value is routed through its decimal
	 * text rather than the binary double - new BigDecimal(1904.36d) would carry
	 * the full 1904.3599853515625 into the cascade.
	 */
	private BigDecimal toBigDecimal(Object value) {
		if (!Objects.nonNull(value)) {
			return null;
		}
		if (value instanceof BigDecimal) {
			return (BigDecimal) value;
		}
		return new BigDecimal(value.toString());
	}

}
