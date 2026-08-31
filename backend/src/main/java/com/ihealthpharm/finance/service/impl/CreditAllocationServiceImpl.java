package com.ihealthpharm.finance.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
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
import com.ihealthpharm.finance.dto.CreditAccountSearchDTO;
import com.ihealthpharm.finance.dto.CreditAllocationPlanDTO;
import com.ihealthpharm.finance.dto.CreditAllocationPlanLineDTO;
import com.ihealthpharm.finance.dto.CreditAllocationRequestDTO;
import com.ihealthpharm.finance.dto.CreditSettlementActionDTO;
import com.ihealthpharm.finance.dto.CreditSettlementAccessDTO;
import com.ihealthpharm.finance.dto.OpenBillDTO;
import com.ihealthpharm.finance.dto.SettlementSummaryDTO;
import com.ihealthpharm.finance.helper.CreditSettlementAccessGuard;
import com.ihealthpharm.finance.model.AccountReceivablesModel;
import com.ihealthpharm.finance.model.CreditAllocationBatchModel;
import com.ihealthpharm.finance.model.CreditAllocationLineModel;
import com.ihealthpharm.finance.service.AccountReceivablesService;
import com.ihealthpharm.finance.service.CreditAllocationEngine;
import com.ihealthpharm.finance.service.CreditAllocationService;
import com.ihealthpharm.finance.service.GeneralLedgerService;
import com.ihealthpharm.masters.dao.MasterAccountRepository;
import com.ihealthpharm.masters.model.EmployeeCredentialsModel;
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

	/**
	 * Carried by a receivable holding money the customer has not used yet.
	 *
	 * The system already records this as a negative AMOUNT_TO_BE_RECEIVED - 472
	 * receivables sit that way, holding about 3.3m between them - so credit left
	 * on account follows the same convention rather than a new one.
	 */
	private static final String STATUS_OVER_PAID = "Over Paid";

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
	CreditSettlementAccessGuard creditSettlementAccessGuard;

	@Autowired
	GeneralLedgerService generalLedgerService;

	@Autowired
	SalesRepository salesRepository;

	@Autowired
	MasterAccountRepository masterAccountRepository;

	@Override
	public CreditSettlementAccessDTO getAccess(String userName) {
		return creditSettlementAccessGuard.describe(userName);
	}

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
		// Permission first: someone who may not settle at all should not be able to
		// use this endpoint to find out whether a PIN is right.
		creditSettlementAccessGuard.requireSettle(userName);
		employeeCredentialsService.verifyApprovalPin(userName, request.getApprovalPin());

		// Taken from the signed-in user, never from the request body - the same
		// stance the controller takes on identity. Nothing was sending it, so
		// every settled receivable was being stamped Approved by nobody.
		request.setApprovedBy(resolveEmployeeId(userName));

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

		// What was written, so the screen reports it rather than inferring it.
		plan.setBatchRef(batch.getBatchRef());
		plan.setStatus(batch.getStatus());

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
		creditSettlementAccessGuard.requireSettle(userName);
		employeeCredentialsService.verifyApprovalPin(userName, action.getApprovalPin());

		CreditAllocationBatchModel batch = requireBatch(action.getBatchRef());

		if (!CreditAllocationBatchModel.STATUS_HELD_PENDING_APPROVAL.equals(batch.getStatus())) {
			throw new IHealthPharmException(
					"Settlement " + batch.getBatchRef() + " is " + batch.getStatus()
					+ " and is not waiting on cheque approval.", HttpStatus.CONFLICT);
		}

		// Recorded before settling: toRequest reads it back off the batch, so the
		// receivables are stamped with whoever approved the cheque rather than
		// with whoever first recorded it.
		batch.setApprovedBy(resolveEmployeeId(userName));

		CreditAllocationPlanDTO plan = planFromBatch(batch);
		settle(toRequest(batch), plan, batch);

		batch.setStatus(CreditAllocationBatchModel.STATUS_ALLOCATED);
		batch.setApprovedDate(new Date());
		log.info("Cheque settlement {} approved by {}: {} across {} bills", batch.getBatchRef(), userName,
				batch.getAllocatedAmount(), plan.getLines().size());

		return creditAllocationBatchRepository.save(batch);
	}

	@Override
	public CreditAllocationBatchModel reverseSettlement(CreditSettlementActionDTO action, String userName) {
		creditSettlementAccessGuard.requireReverse(userName);
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
		batch.setReversedBy(resolveEmployeeId(userName));
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
			receivable.setChequeNumber(line.getPriorChequeNumber());
			receivable.setChequeAmount(Objects.nonNull(line.getPriorChequeAmt())
					? line.getPriorChequeAmt().doubleValue() : null);
			receivable.setChequeDate(line.getPriorChequeDt());
			accountReceivablesRepository.save(receivable);

			// Contra entry. Applying the settlement credited this amount to the
			// ledger, so unwinding it has to debit the same amount back out;
			// otherwise a bounced cheque leaves the accounts still claiming
			// money that was handed back. Referenced by the same batch, so the
			// pair nets to nothing and can be seen to.
			generalLedgerService.saveReceivableLedgerEntry(receivable,
					line.getAmountApplied().negate(), batch.getBatchRef());

			CreditAllocationPlanLineDTO planLine = new CreditAllocationPlanLineDTO();
			planLine.setBillCode(line.getBillCode());
			restored.getLines().add(planLine);

			line.setActiveS('N');
			creditAllocationLineRepository.save(line);
		}

		// The overpayment held on account was booked as cash received too, so it
		// comes back out with everything else.
		if (CreditAllocationBatchModel.OVERPAYMENT_HOLD_ON_ACCOUNT
				.equalsIgnoreCase(batch.getOverpaymentAction())
				&& Objects.nonNull(batch.getUnallocatedAmount())
				&& batch.getUnallocatedAmount().signum() > 0) {

			CreditAllocationLineModel lastLine = lines.get(lines.size() - 1);
			accountReceivablesRepository.findById(lastLine.getAccountReceivablesId())
					.ifPresent(receivable -> generalLedgerService.saveReceivableLedgerEntry(
							receivable, batch.getUnallocatedAmount().negate(), batch.getBatchRef()));
		}

		restoreBillPaymentDetails(lines);

		// Rebuild the bills from the receivables just restored.
		reconcileBills(restored);
		restoreCreditLimit(batch);
	}

	/**
	 * Puts back what settlement changed on each sales bill besides its balance.
	 *
	 * Two things need restoring. The receivables update adds the cheque number
	 * to a comma separated list and adds to the bill's cheque amount, so a
	 * reversed cheque would otherwise stay listed against the bill with its
	 * amount counted twice. It also moves the settled amount out of the bill's
	 * credit total and into the payment type it was settled with, which a
	 * reversal that only rebuilt the balance would leave behind.
	 */
	private void restoreBillPaymentDetails(List<CreditAllocationLineModel> lines) {
		Set<Integer> done = new LinkedHashSet<>();
		for (CreditAllocationLineModel line : lines) {
			if (!Objects.nonNull(line.getSalesBillId()) || !done.add(line.getSalesBillId())) {
				continue;
			}
			SalesModel bill = salesRepository.findById(line.getSalesBillId()).orElse(null);
			if (!Objects.nonNull(bill)) {
				continue;
			}
			Double priorBillCheque = Objects.nonNull(line.getPriorBillChequeAmt())
					? Double.valueOf(line.getPriorBillChequeAmt().doubleValue()) : null;
			bill.setChequeNumber(line.getPriorBillChequeNumber());
			bill.setChequeAmount(priorBillCheque);
			bill.setChequeDate(line.getPriorBillChequeDate());

			if (Objects.nonNull(line.getPriorBillCashAmount())) {
				bill.setCashAmount(line.getPriorBillCashAmount().floatValue());
			}
			if (Objects.nonNull(line.getPriorBillCreditAmount())) {
				bill.setCreditAmount(line.getPriorBillCreditAmount().doubleValue());
			}

			salesRepository.save(bill);
		}
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
		request.setChequeDate(batch.getChequeDate());
		// Both were decided when the payment was taken and are only acted on at
		// settlement, which for a cheque is here. Leaving the overpayment action
		// behind would quietly drop money the user had asked to hold on account.
		request.setOverpaymentAction(batch.getOverpaymentAction());
		request.setReceiptNumber(batch.getReceiptNumber());
		return request;
	}

	/**
	 * The employee behind a signed-in user name.
	 *
	 * The Receipts screen instead has the operator pick an approver from a list
	 * and checks that person's PIN in the browser, which means the name on the
	 * record is whoever was chosen rather than whoever acted. Settlement proves
	 * identity server-side against the caller's own PIN, so the approver is the
	 * caller and is looked up here rather than trusted from the request.
	 */
	private Integer resolveEmployeeId(String userName) {
		EmployeeCredentialsModel credentials = employeeCredentialsService
				.findEmployeeCredentialsByUserName(userName);

		if (!Objects.nonNull(credentials) || !Objects.nonNull(credentials.getEmployee())) {
			// The PIN check has already passed, so this would mean the account
			// exists but is not linked to an employee. Better to stop than to
			// record another settlement approved by nobody.
			throw new IHealthPharmException(
					"Your user account is not linked to an employee record, so the settlement "
					+ "cannot be attributed. Ask an administrator to check it.",
					HttpStatus.CONFLICT);
		}
		return credentials.getEmployee().getEmployeeId();
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

	/**
	 * A cheque is recorded on receipt but only settles once approved.
	 *
	 * Decided by the payment type. This used to ask whether the request carried
	 * a CHEQUE_ID, which nothing populates - not the controller and not the
	 * screen - so it was always false: every cheque settled its bills on the
	 * spot while the screen told the operator they were still outstanding, and
	 * no batch could ever reach HELD_PENDING_APPROVAL for the approval step to
	 * find. The rule the business stated is about the payment being a cheque,
	 * so that is what is asked. A CHEQUE_ID still links the batch to a cheque
	 * record where one exists, but no longer decides anything.
	 */
	private boolean isChequeHeld(CreditAllocationRequestDTO request) {
		return PAYMENT_TYPE_CHEQUE.equalsIgnoreCase(request.getPaymentType());
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
		batch.setReceiptNumber(request.getReceiptNumber());
		batch.setChequeId(request.getChequeId());
		batch.setChequeDate(request.getChequeDate());
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
		Map<String, BigDecimal> appliedPerBill = new HashMap<>();

		for (CreditAllocationPlanLineDTO line : plan.getLines()) {
			AccountReceivablesModel receivable = accountReceivablesRepository
					.findById(line.getAccountReceivablesId())
					.orElseThrow(() -> new IHealthPharmException(
							"Outstanding bill " + line.getBillCode() + " could not be loaded.",
							HttpStatus.NOT_FOUND));

			refuseIfBillCannotAbsorb(line, appliedPerBill);

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

			// The payment's receipt number is deliberately not written here. A
			// receivable's RECEIPT_NO is its own generated identifier (DPAR...)
			// and is what the receipt and statement reports print, so putting a
			// payment's number over it loses that reference. It is kept on the
			// batch instead.

			payload.add(receivable);
		}

		BigDecimal heldOnAccount = holdRemainderOnAccount(request, plan, payload);

		accountReceivablesService.updateAccountsReceivablesData(payload);

		// The Receipts screen makes two calls, not one: after saving the
		// receivables it posts the ledger entries separately. Settling without
		// this leaves the receivable updated and the accounts unbalanced.
		postLedgerEntries(plan, payload, heldOnAccount, batch.getBatchRef());

		reconcileBills(plan);
	}

	/**
	 * Books what the settlement actually moved.
	 *
	 * One entry per bill, for the amount this payment applied to it. The amount
	 * has to be passed in rather than read off the receivable: a bill that
	 * already held a part payment carries the running total in AMOUNT_RECEIVED,
	 * and booking that would count the earlier money a second time.
	 *
	 * This replaces saveMultipleLedgersDataForAccRecievables, which sets a
	 * debit or a credit only for credit and debit notes. Settlement only ever
	 * touches "Sales Billing" rows, which fall through every branch there, so
	 * each entry it wrote carried no amount at all - the 200,000 settlement of
	 * 29 August posted 59 ledger rows summing to zero.
	 *
	 * Entries are referenced by the batch, so the ledger traces back to the
	 * settlement that produced it and the pair can be reconciled.
	 *
	 * payload is built one element per plan line, in order, so the two are
	 * indexed together.
	 */
	private void postLedgerEntries(CreditAllocationPlanDTO plan, List<AccountReceivablesModel> payload,
			BigDecimal heldOnAccount, String batchRef) {

		for (int i = 0; i < plan.getLines().size(); i++) {
			generalLedgerService.saveReceivableLedgerEntry(payload.get(i),
					plan.getLines().get(i).getAmountApplied(), batchRef);
		}

		if (Objects.nonNull(heldOnAccount) && heldOnAccount.signum() > 0) {
			// Money the customer overpaid and chose to leave on account is still
			// cash the business took in, so it is booked like the rest of it.
			generalLedgerService.saveReceivableLedgerEntry(payload.get(payload.size() - 1),
					heldOnAccount, batchRef);
		}
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
			line.setPriorChequeNumber(receivable.getChequeNumber());
			line.setPriorChequeAmt(Objects.nonNull(receivable.getChequeAmount())
					? BigDecimal.valueOf(receivable.getChequeAmount()) : null);
			line.setPriorChequeDt(receivable.getChequeDate());

			SalesModel priorBill = Objects.nonNull(planLine.getSalesBillId())
					? salesRepository.findById(planLine.getSalesBillId()).orElse(null) : null;
			if (Objects.nonNull(priorBill)) {
				line.setPriorBillChequeNumber(priorBill.getChequeNumber());
				line.setPriorBillChequeAmt(Objects.nonNull(priorBill.getChequeAmount())
						? BigDecimal.valueOf(priorBill.getChequeAmount()) : null);
				line.setPriorBillChequeDate(priorBill.getChequeDate());
				line.setPriorBillCashAmount(toBigDecimal(priorBill.getCashAmount()));
				line.setPriorBillCreditAmount(Objects.nonNull(priorBill.getCreditAmount())
						? BigDecimal.valueOf(priorBill.getCreditAmount()) : null);
			}
			creditAllocationLineRepository.save(line);
			return;
		}
	}

	private BigDecimal toBigDecimal(Float value) {
		return Objects.nonNull(value) ? BigDecimal.valueOf(value) : null;
	}

	/**
	 * Leaves money the customer overpaid sitting on their account.
	 *
	 * Recorded the way the system already records it: the last bill settled
	 * carries the extra in AMOUNT_RECEIVED and shows a negative
	 * AMOUNT_TO_BE_RECEIVED, meaning the customer is in credit by that much.
	 * The next payment run picks it up as an ordinary open item.
	 *
	 * A refund is deliberately not applied here. Nothing in the system pays
	 * money back out, so inventing a mechanism would leave a payment recorded
	 * against bills that never received it. The decision is kept on the batch
	 * and the money stays unallocated for whoever pays it out.
	 */
	private BigDecimal holdRemainderOnAccount(CreditAllocationRequestDTO request,
			CreditAllocationPlanDTO plan, List<AccountReceivablesModel> payload) {

		if (plan.getUnallocatedAmount().signum() <= 0 || payload.isEmpty()) {
			return null;
		}
		if (!CreditAllocationBatchModel.OVERPAYMENT_HOLD_ON_ACCOUNT
				.equalsIgnoreCase(request.getOverpaymentAction())) {
			log.info("Overpayment of {} left unallocated for refund", plan.getUnallocatedAmount());
			return null;
		}

		BigDecimal remainder = plan.getUnallocatedAmount();
		AccountReceivablesModel lastSettled = payload.get(payload.size() - 1);

		float received = Objects.nonNull(lastSettled.getAmountReceived())
				? lastSettled.getAmountReceived() : 0f;

		lastSettled.setAmountReceived(received + remainder.floatValue());
		lastSettled.setAmountToBeReceived(remainder.negate().floatValue());
		lastSettled.setPaymentStatus(STATUS_OVER_PAID);

		log.info("Overpayment of {} held on account against {}", remainder, lastSettled.getBillRefNo());
		return remainder;
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
	private void refuseIfBillCannotAbsorb(CreditAllocationPlanLineDTO line,
			Map<String, BigDecimal> appliedPerBill) {

		SalesModel bill = resolveBill(line);
		if (!Objects.nonNull(bill) || !Objects.nonNull(bill.getBalanceAmount())
				|| StringUtils.isBlank(line.getBillCode())) {
			// Nothing to contradict the receivable, so it stands.
			return;
		}

		// Against everything this settlement has already put on the same bill,
		// not just this line. 91 bill codes carry more than one open receivable
		// and 39 of them claim more between them than the bill has outstanding -
		// DPSB3004210007 has three claims totalling 22,000 against a balance of
		// 6,000. Checked line by line they each look affordable, because the
		// bill is not written until the batch finishes; the payment would then
		// disappear into the duplicate claim and the bill would simply cap
		// itself at paid.
		String billCode = line.getBillCode().trim();
		BigDecimal cumulative = appliedPerBill.getOrDefault(billCode, BigDecimal.ZERO)
				.add(line.getAmountApplied());
		BigDecimal billBalance = BigDecimal.valueOf(bill.getBalanceAmount());

		if (billBalance.compareTo(cumulative) < 0) {
			log.warn("Refusing to settle {}: bill balance {} cannot absorb {} ({} already applied "
					+ "to it by this settlement)", billCode, billBalance, cumulative,
					cumulative.subtract(line.getAmountApplied()));
			throw new IHealthPharmException(
					"Bill " + billCode + " shows " + billBalance
					+ " outstanding but the receivables against it claim " + cumulative
					+ ". Settle it manually after the records are corrected.",
					HttpStatus.CONFLICT);
		}
		appliedPerBill.put(billCode, cumulative);
	}

	/**
	 * The sales bill behind an allocation line.
	 *
	 * By id where the receivable carries one and by code otherwise: 616 of the
	 * 2,980 settleable rows have no SALES_BILL_ID, and looking only by id
	 * skipped the check above for every one of them. Bill codes are unique, so
	 * the fallback resolves to one bill or none.
	 */
	private SalesModel resolveBill(CreditAllocationPlanLineDTO line) {
		if (Objects.nonNull(line.getSalesBillId())) {
			SalesModel bill = salesRepository.findById(line.getSalesBillId()).orElse(null);
			if (Objects.nonNull(bill)) {
				return bill;
			}
		}
		return StringUtils.isNotBlank(line.getBillCode())
				? salesRepository.getSalesRecordByNo(line.getBillCode().trim()) : null;
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
	/** Shortest search that is worth running against 31,000 receivables. */
	private static final int MIN_SEARCH_LENGTH = 2;

	@Override
	public List<CreditAccountSearchDTO> searchCreditAccounts(String term) {
		List<CreditAccountSearchDTO> accounts = new ArrayList<>();

		// One character would match most of the ledger and tell the user
		// nothing. The screen holds off too; this is the guard behind it.
		if (StringUtils.isBlank(term) || term.trim().length() < MIN_SEARCH_LENGTH) {
			return accounts;
		}

		List<Object[]> rows = openBillRepository.searchCreditAccounts("%" + term.trim() + "%");
		if (!Objects.nonNull(rows)) {
			return accounts;
		}

		for (Object[] row : rows) {
			CreditAccountSearchDTO account = new CreditAccountSearchDTO();
			account.setMasterAccountNo(toString(row[0]));
			account.setCustomerNames(toString(row[1]));
			account.setCustomerCount(Objects.nonNull(row[2]) ? ((Number) row[2]).intValue() : 0);
			account.setOpenBills(Objects.nonNull(row[3]) ? ((Number) row[3]).intValue() : 0);
			// Summing a FLOAT column spills fractions of a cent - 33137.549987...
			// for a balance of 33137.55 - so round the total as money before it
			// is shown, the same way the engine rounds what it allocates.
			BigDecimal outstanding = toBigDecimal(row[4]);
			account.setOutstanding(Objects.nonNull(outstanding)
					? outstanding.setScale(2, RoundingMode.HALF_UP) : null);
			accounts.add(account);
		}
		return accounts;
	}

	@Override
	public List<SettlementSummaryDTO> findRecentSettlements(String masterAccountNo) {
		List<SettlementSummaryDTO> settlements = new ArrayList<>();

		List<Object[]> rows = creditAllocationBatchRepository.findRecentSettlements(
				StringUtils.isBlank(masterAccountNo) ? null : masterAccountNo.trim());
		if (!Objects.nonNull(rows)) {
			return settlements;
		}

		for (Object[] row : rows) {
			SettlementSummaryDTO settlement = new SettlementSummaryDTO();
			settlement.setBatchRef(toString(row[0]));
			settlement.setMasterAccountNo(toString(row[1]));
			settlement.setPaymentAmount(toBigDecimal(row[2]));
			settlement.setAllocatedAmount(toBigDecimal(row[3]));
			settlement.setPaymentType(toString(row[4]));
			settlement.setPaymentRef(toString(row[5]));
			settlement.setStatus(toString(row[6]));
			settlement.setAllocationOrder(toString(row[7]));
			settlement.setSettledOn(Objects.nonNull(row[8]) ? (Date) row[8] : null);
			settlement.setReversalReason(toString(row[9]));
			settlement.setUnallocatedAmount(toBigDecimal(row[10]));
			settlement.setOverpaymentAction(toString(row[11]));
			settlement.setBillCount(Objects.nonNull(row[12]) ? ((Number) row[12]).intValue() : 0);
			settlements.add(settlement);
		}
		return settlements;
	}

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
