package com.ihealthpharm.finance.service.impl;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

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
import com.ihealthpharm.finance.dto.CreditSettlementActionDTO;
import com.ihealthpharm.finance.dto.OpenBillDTO;
import com.ihealthpharm.finance.model.AccountReceivablesModel;
import com.ihealthpharm.finance.model.CreditAllocationBatchModel;
import com.ihealthpharm.finance.model.CreditAllocationLineModel;
import com.ihealthpharm.finance.service.AccountReceivablesService;
import com.ihealthpharm.finance.service.CreditAllocationEngine;
import com.ihealthpharm.finance.service.CreditAllocationService;
import com.ihealthpharm.finance.service.GeneralLedgerService;
import com.ihealthpharm.masters.dao.MasterAccountRepository;
import com.ihealthpharm.masters.model.MasterAccountModel;
import com.ihealthpharm.masters.service.EmployeeCredentialsService;
import com.ihealthpharm.sales.dao.SalesRepository;
import com.ihealthpharm.sales.model.SalesModel;

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

	/** A bill with nothing received against it. */
	private static final String STATUS_PENDING = "Pending";

	private static final String PAYMENT_TYPE_CHEQUE = "Cheque";

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

	@Autowired
	SalesRepository salesRepository;

	@Autowired
	MasterAccountRepository masterAccountRepository;

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

		requireChequeDetails(request);

		CreditAllocationBatchModel batch = saveBatch(request, plan, userName);
		saveLines(batch, plan);

		if (isChequeHeld(request)) {
			// Recorded, but the debt stands until the cheque is approved.
			log.info("Credit settlement {} held pending cheque approval", batch.getBatchRef());
			return plan;
		}

		settle(request, plan, batch);
		log.info("Credit settlement {} applied: {} across {} bills for user {}", batch.getBatchRef(),
				plan.getAllocatedAmount(), plan.getLines().size(), userName);

		return plan;
	}

	@Override
	public CreditAllocationBatchModel approveChequeSettlement(CreditSettlementActionDTO action, String userName) {
		employeeCredentialsService.verifyApprovalPin(userName, action.getApprovalPin());

		CreditAllocationBatchModel batch = requireBatch(action.getBatchRef());

		if (!CreditAllocationBatchModel.STATUS_HELD_PENDING_APPROVAL.equals(batch.getStatus())) {
			throw new IHealthPharmException(
					"Settlement " + batch.getBatchRef() + " is " + batch.getStatus()
					+ " and is not waiting on cheque approval.", HttpStatus.CONFLICT);
		}

		CreditAllocationPlanDTO plan = planFromBatch(batch);
		settle(toRequest(batch), plan, batch);

		batch.setStatus(CreditAllocationBatchModel.STATUS_ALLOCATED);
		batch.setApprovedBy(action.getApprovedBy());
		batch.setApprovedDate(new Date());
		log.info("Cheque settlement {} approved by {}: {} across {} bills", batch.getBatchRef(), userName,
				batch.getAllocatedAmount(), plan.getLines().size());

		return creditAllocationBatchRepository.save(batch);
	}

	@Override
	public CreditAllocationBatchModel reverseSettlement(CreditSettlementActionDTO action, String userName) {
		employeeCredentialsService.verifyApprovalPin(userName, action.getApprovalPin());

		if (StringUtils.isBlank(action.getReason())) {
			throw new IHealthPharmException("A reason is required to reverse a settlement.",
					HttpStatus.BAD_REQUEST);
		}

		CreditAllocationBatchModel batch = requireBatch(action.getBatchRef());

		if (CreditAllocationBatchModel.STATUS_REVERSED.equals(batch.getStatus())) {
			throw new IHealthPharmException("Settlement " + batch.getBatchRef() + " has already been reversed.",
					HttpStatus.CONFLICT);
		}

		batch.setReversalReason(action.getReason());
		batch.setReversedBy(action.getApprovedBy());
		batch.setReversedDate(new Date());

		if (!action.isReverseAutomatically()) {
			// Flagged only: the settlement stands until someone reviews it.
			batch.setStatus(CreditAllocationBatchModel.STATUS_REVERSAL_FLAGGED);
			log.warn("Settlement {} flagged for reversal by {}: {}", batch.getBatchRef(), userName,
					action.getReason());
			return creditAllocationBatchRepository.save(batch);
		}

		if (CreditAllocationBatchModel.STATUS_HELD_PENDING_APPROVAL.equals(batch.getStatus())) {
			// Never settled, so there is nothing to put back - the lines carry no
			// prior state, and restoring from them would blank the receivables.
			log.info("Held settlement {} cancelled before approval", batch.getBatchRef());
		} else {
			unwind(batch);
		}
		batch.setStatus(CreditAllocationBatchModel.STATUS_REVERSED);
		log.warn("Settlement {} reversed by {}: {}", batch.getBatchRef(), userName, action.getReason());

		return creditAllocationBatchRepository.save(batch);
	}

	/**
	 * Puts every bill in the batch back as it was.
	 *
	 * Each receivable is restored from the values recorded when it was settled -
	 * not recalculated - so a bill that was already Partially Paid returns to
	 * Partially Paid rather than Pending. The bills themselves are then rebuilt
	 * from those receivables, and the credit limit returns to the figure the
	 * batch recorded rather than being adjusted by the amount allocated.
	 */
	private void unwind(CreditAllocationBatchModel batch) {
		List<CreditAllocationLineModel> lines = creditAllocationLineRepository
				.findByAllocationBatchAllocationBatchIdOrderBySequenceNoAsc(batch.getAllocationBatchId());

		if (lines.isEmpty()) {
			throw new IHealthPharmException(
					"Settlement " + batch.getBatchRef() + " has no recorded lines and cannot be reversed.",
					HttpStatus.CONFLICT);
		}

		CreditAllocationPlanDTO restored = new CreditAllocationPlanDTO();

		for (CreditAllocationLineModel line : lines) {
			AccountReceivablesModel receivable = accountReceivablesRepository
					.findById(line.getAccountReceivablesId())
					.orElseThrow(() -> new IHealthPharmException(
							"Receivable behind " + line.getBillCode() + " no longer exists, so the settlement "
							+ "cannot be reversed automatically.", HttpStatus.CONFLICT));

			receivable.setAmountToBeReceived(toFloat(line.getPriorBalance()));
			receivable.setAmountReceived(toFloat(line.getPriorAmountReceived()));
			receivable.setPartialAmt(toFloat(line.getPriorPartialAmt()));
			receivable.setPaymentStatus(line.getPriorPaymentStatus());
			receivable.setStatus(line.getPriorStatus());
			receivable.setPaymentType(line.getPriorPaymentType());
			receivable.setApprovedBy(line.getPriorApprovedBy());
			receivable.setApprovedDate(line.getPriorApprovedDate());
			accountReceivablesRepository.save(receivable);

			CreditAllocationPlanLineDTO planLine = new CreditAllocationPlanLineDTO();
			planLine.setBillCode(line.getBillCode());
			restored.getLines().add(planLine);

			line.setActiveS('N');
			creditAllocationLineRepository.save(line);
		}

		// Rebuild the bills from the receivables just restored.
		reconcileBills(restored);
		restoreCreditLimit(batch);
	}

	private void restoreCreditLimit(CreditAllocationBatchModel batch) {
		if (!Objects.nonNull(batch.getPriorCreditLimitLeft())
				|| StringUtils.isBlank(batch.getMasterAccountNo())) {
			return;
		}

		MasterAccountModel account = masterAccountRepository
				.getDataByMasterCreditNumber(batch.getMasterAccountNo());
		if (!Objects.nonNull(account)) {
			return;
		}

		account.setCreditLimitLeft(batch.getPriorCreditLimitLeft().intValue());
		masterAccountRepository.save(account);
		log.info("Credit limit for {} restored to {}", batch.getMasterAccountNo(),
				batch.getPriorCreditLimitLeft());
	}

	private CreditAllocationBatchModel requireBatch(String batchRef) {
		if (StringUtils.isBlank(batchRef)) {
			throw new IHealthPharmException("A settlement reference is required.", HttpStatus.BAD_REQUEST);
		}
		CreditAllocationBatchModel batch = creditAllocationBatchRepository.findByBatchRef(batchRef.trim());
		if (!Objects.nonNull(batch)) {
			throw new IHealthPharmException("Settlement " + batchRef + " was not found.", HttpStatus.NOT_FOUND);
		}
		return batch;
	}

	/** Rebuilds the plan from the lines recorded when the batch was created. */
	private CreditAllocationPlanDTO planFromBatch(CreditAllocationBatchModel batch) {
		CreditAllocationPlanDTO plan = new CreditAllocationPlanDTO();
		plan.setPaymentAmount(batch.getPaymentAmount());
		plan.setAllocatedAmount(batch.getAllocatedAmount());
		plan.setUnallocatedAmount(batch.getUnallocatedAmount());
		plan.setAllocationOrder(batch.getAllocationOrder());

		for (CreditAllocationLineModel line : creditAllocationLineRepository
				.findByAllocationBatchAllocationBatchIdOrderBySequenceNoAsc(batch.getAllocationBatchId())) {

			CreditAllocationPlanLineDTO planLine = new CreditAllocationPlanLineDTO();
			planLine.setSequenceNo(line.getSequenceNo());
			planLine.setAccountReceivablesId(line.getAccountReceivablesId());
			planLine.setSalesBillId(line.getSalesBillId());
			planLine.setBillCode(line.getBillCode());
			planLine.setBillDate(line.getBillDate());
			planLine.setAmountApplied(line.getAmountApplied());
			planLine.setPriorBalance(line.getPriorBalance());
			planLine.setPriorPaymentStatus(line.getPriorPaymentStatus());
			planLine.setResultingPaymentStatus(line.getResultingPaymentStatus());
			plan.getLines().add(planLine);
		}
		return plan;
	}

	private CreditAllocationRequestDTO toRequest(CreditAllocationBatchModel batch) {
		CreditAllocationRequestDTO request = new CreditAllocationRequestDTO();
		request.setMasterAccountNo(batch.getMasterAccountNo());
		request.setCustomerId(batch.getCustomerId());
		request.setPharmacyId(batch.getPharmacyId());
		request.setPaymentType(batch.getPaymentType());
		request.setPaymentRef(batch.getPaymentRef());
		request.setApprovedBy(batch.getApprovedBy());
		request.setChequeDate(Objects.nonNull(batch.getApprovedDate())
				? new java.sql.Date(batch.getApprovedDate().getTime()).toLocalDate() : LocalDate.now());
		return request;
	}

	private Float toFloat(BigDecimal value) {
		return Objects.nonNull(value) ? value.floatValue() : null;
	}

	/**
	 * A cheque cannot be settled without its number and date - the receivables
	 * update writes both onto the sales bill and reads the date without a null
	 * check, so a missing one fails mid-settlement rather than up front.
	 */
	private void requireChequeDetails(CreditAllocationRequestDTO request) {
		if (!PAYMENT_TYPE_CHEQUE.equalsIgnoreCase(request.getPaymentType())) {
			return;
		}
		if (StringUtils.isBlank(request.getPaymentRef()) || !Objects.nonNull(request.getChequeDate())) {
			throw new IHealthPharmException("A cheque number and cheque date are required to settle by cheque.",
					HttpStatus.BAD_REQUEST);
		}
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
		batch.setPriorCreditLimitLeft(currentCreditLimitLeft(request.getMasterAccountNo()));
		batch.setBackfillS(request.isBackfill() ? 'Y' : 'N');
		batch.setActiveS('Y');
		batch.setCreatedUser(userName);
		batch.setStatus(isChequeHeld(request)
				? CreditAllocationBatchModel.STATUS_HELD_PENDING_APPROVAL
				: CreditAllocationBatchModel.STATUS_ALLOCATED);

		return creditAllocationBatchRepository.save(batch);
	}

	/** Recorded before settling, so reversal restores rather than recalculates. */
	private BigDecimal currentCreditLimitLeft(String masterAccountNo) {
		if (StringUtils.isBlank(masterAccountNo)) {
			return null;
		}
		MasterAccountModel account = masterAccountRepository.getDataByMasterCreditNumber(masterAccountNo.trim());
		return Objects.nonNull(account) && Objects.nonNull(account.getCreditLimitLeft())
				? BigDecimal.valueOf(account.getCreditLimitLeft()) : null;
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
	private void settle(CreditAllocationRequestDTO request, CreditAllocationPlanDTO plan,
			CreditAllocationBatchModel batch) {
		List<AccountReceivablesModel> payload = new ArrayList<>();

		for (CreditAllocationPlanLineDTO line : plan.getLines()) {
			AccountReceivablesModel receivable = accountReceivablesRepository
					.findById(line.getAccountReceivablesId())
					.orElseThrow(() -> new IHealthPharmException(
							"Outstanding bill " + line.getBillCode() + " could not be loaded.",
							HttpStatus.NOT_FOUND));

			refuseIfBillCannotAbsorb(line);

			boolean settledInFull = CreditAllocationEngine.STATUS_PAID
					.equals(line.getResultingPaymentStatus());

			// Field meanings follow the Receipts screen exactly: a bill cleared in
			// full leaves nothing to receive, while a partial payment leaves the
			// remainder outstanding and records what was paid in both
			// amountReceived and partialAmt.
			// Accumulate rather than replace. A row may already carry an earlier
			// payment - DPSB1705210018 has 1,500 net with 750 received and 750
			// still owing - and overwriting would lose it, leaving the bill
			// looking half paid after it had been settled in full.
			float alreadyReceived = Objects.nonNull(receivable.getAmountReceived())
					? receivable.getAmountReceived() : 0f;
			float nowReceived = alreadyReceived + line.getAmountApplied().floatValue();

			// Everything settlement is about to overwrite, recorded before it does.
			// Reversal restores these verbatim rather than inferring them - STATUS
			// in particular cannot be worked out afterwards, since a row may
			// already have been Approved.
			recordPriorState(batch, line, receivable);

			receivable.setAmountReceived(nowReceived);
			receivable.setPartialAmt(settledInFull ? 0f : nowReceived);
			receivable.setAmountToBeReceived(settledInFull ? 0f
					: line.getPriorBalance().subtract(line.getAmountApplied()).floatValue());
			receivable.setPaymentStatus(line.getResultingPaymentStatus());
			receivable.setStatus(STATUS_APPROVED);
			receivable.setPaymentType(request.getPaymentType());
			receivable.setApprovedBy(request.getApprovedBy());
			receivable.setApprovedDate(LocalDate.now());

			if (PAYMENT_TYPE_CHEQUE.equalsIgnoreCase(request.getPaymentType())) {
				// The receivables update copies these onto the sales bill and
				// dereferences the date without checking it, so both must be set.
				receivable.setChequeNumber(request.getPaymentRef());
				receivable.setChequeDate(request.getChequeDate());
				receivable.setChequeAmount(line.getAmountApplied().doubleValue());
			}

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

		reconcileBills(plan);
	}

	/**
	 * Copies a receivable's current values onto its allocation line, so a
	 * reversal can put them back exactly as they were.
	 */
	private void recordPriorState(CreditAllocationBatchModel batch, CreditAllocationPlanLineDTO planLine,
			AccountReceivablesModel receivable) {

		List<CreditAllocationLineModel> lines = creditAllocationLineRepository
				.findByAllocationBatchAllocationBatchIdOrderBySequenceNoAsc(batch.getAllocationBatchId());

		for (CreditAllocationLineModel line : lines) {
			if (!planLine.getSequenceNo().equals(line.getSequenceNo())) {
				continue;
			}
			line.setPriorAmountReceived(toBigDecimal(receivable.getAmountReceived()));
			line.setPriorPartialAmt(toBigDecimal(receivable.getPartialAmt()));
			line.setPriorStatus(receivable.getStatus());
			line.setPriorPaymentType(receivable.getPaymentType());
			line.setPriorApprovedBy(receivable.getApprovedBy());
			line.setPriorApprovedDate(receivable.getApprovedDate());
			creditAllocationLineRepository.save(line);
			return;
		}
	}

	private BigDecimal toBigDecimal(Float value) {
		return Objects.nonNull(value) ? BigDecimal.valueOf(value) : null;
	}

	/**
	 * Brings each settled bill back in line with its receivables.
	 *
	 * The existing update only refreshes the sales bill on some paths - it
	 * cascades for a partial payment but not for one that clears the bill, so
	 * settling without this leaves the receivable Paid while the bill still
	 * shows the full amount owing. That mismatch is the same one that has left
	 * 615 receivables claiming money on bills carrying no balance.
	 *
	 * The figures are derived from the receivables rather than adjusted by the
	 * amount just applied, so running it twice cannot double count.
	 */
	private void reconcileBills(CreditAllocationPlanDTO plan) {
		Set<String> billCodes = new LinkedHashSet<>();
		for (CreditAllocationPlanLineDTO line : plan.getLines()) {
			if (StringUtils.isNotBlank(line.getBillCode())) {
				billCodes.add(line.getBillCode().trim());
			}
		}

		for (String billCode : billCodes) {
			SalesModel bill = salesRepository.getSalesRecordByNo(billCode);
			if (!Objects.nonNull(bill) || !Objects.nonNull(bill.getNetAmount())) {
				continue;
			}

			BigDecimal received = accountReceivablesRepository.sumReceivedForBill(billCode);
			BigDecimal netAmount = BigDecimal.valueOf(bill.getNetAmount());
			BigDecimal balance = netAmount.subtract(Objects.nonNull(received) ? received : BigDecimal.ZERO);

			boolean cleared = balance.signum() <= 0;
			bill.setPaidAmount(cleared ? bill.getNetAmount() : received.floatValue());
			bill.setBalanceAmount(cleared ? 0f : balance.floatValue());
			bill.setCreditAmount(cleared ? 0d : balance.doubleValue());

			// Three states, not two. A bill with nothing against it is Pending -
			// calling it Partially Paid would leave a reversed settlement looking
			// as though something had still been paid.
			if (cleared) {
				bill.setPaymentStatus(CreditAllocationEngine.STATUS_PAID);
			} else if (received.signum() > 0) {
				bill.setPaymentStatus(CreditAllocationEngine.STATUS_PARTIALLY_PAID);
			} else {
				bill.setPaymentStatus(STATUS_PENDING);
			}

			salesRepository.save(bill);
			log.info("Bill {} reconciled: received {} of {}, balance {} -> {}", billCode, received,
					netAmount, balance.max(BigDecimal.ZERO), bill.getPaymentStatus());
		}
	}

	/**
	 * Refuses to settle a bill whose own balance cannot absorb the payment.
	 *
	 * The open-bill query already filters these out, so reaching here means the
	 * bill was settled by some other route between the plan being built and
	 * applied. Settling anyway would drive the bill's balance below zero rather
	 * than reduce a real debt, so it stops instead - the receivable and the bill
	 * disagree, and only a person can say which is right.
	 */
	private void refuseIfBillCannotAbsorb(CreditAllocationPlanLineDTO line) {
		if (!Objects.nonNull(line.getSalesBillId())) {
			// Nothing to check against; 528 of the oldest rows have no bill.
			return;
		}

		SalesModel bill = salesRepository.findById(line.getSalesBillId()).orElse(null);
		if (!Objects.nonNull(bill) || !Objects.nonNull(bill.getBalanceAmount())) {
			return;
		}

		BigDecimal billBalance = BigDecimal.valueOf(bill.getBalanceAmount());
		if (billBalance.compareTo(line.getAmountApplied()) < 0) {
			log.warn("Refusing to settle {}: bill balance {} cannot absorb {}", line.getBillCode(),
					billBalance, line.getAmountApplied());
			throw new IHealthPharmException(
					"Bill " + line.getBillCode() + " shows " + billBalance
					+ " outstanding but the receivable claims more. Settle it manually after the records are corrected.",
					HttpStatus.CONFLICT);
		}
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
