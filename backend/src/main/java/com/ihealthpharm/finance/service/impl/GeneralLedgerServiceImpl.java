package com.ihealthpharm.finance.service.impl;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import javax.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.ihealthpharm.finance.dao.GeneralLedgerRepository;
import com.ihealthpharm.finance.model.AccountPayablesModel;
import com.ihealthpharm.finance.model.AccountReceivablesModel;
import com.ihealthpharm.finance.model.GeneralLedgerModel;
import com.ihealthpharm.finance.service.GeneralLedgerService;
import com.ihealthpharm.masters.service.PharmacyService;
import com.ihealthpharm.uniquecode.service.UniqueCodeService;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@Transactional
public class GeneralLedgerServiceImpl implements GeneralLedgerService {


	@Autowired
	private GeneralLedgerRepository generalLegderRepo;

	//@Autowired
	//private GeneralLedgerHelper generalLedgerHelper;

	@Autowired
	UniqueCodeService uniqueCodeService;

	@Autowired
	private PharmacyService pharmacyService;


	@Override
	public GeneralLedgerModel saveGeneraledger(GeneralLedgerModel generalLedgerModel) {

		GeneralLedgerModel generalRes = generalLegderRepo.save(generalLedgerModel);
		log.info("GeneralLedger data" +generalRes.getGeneralLedgerId() + "saved successfully");
		return generalRes;
	} 



	@Override
	public List<GeneralLedgerModel> saveMutipleLedgersData(List<AccountPayablesModel> accountPayablesModels) {
		List<GeneralLedgerModel> modelRes = new ArrayList<>();

		for (AccountPayablesModel accountPayablesModel : accountPayablesModels) {
			Integer empId = accountPayablesModel.getApprovedBy();
			accountPayablesModel.setApprovedBy(empId);
			GeneralLedgerModel generalLedgerModel=new GeneralLedgerModel();
			generalLedgerModel.setJournalId(uniqueCodeService.findByUniqueCodeName("GL"));
			generalLedgerModel.setJournalRef(accountPayablesModel.getPaymentNumber());
			generalLedgerModel.setEntryNo(accountPayablesModel.getSourceRef());
			generalLedgerModel.setEntryType(accountPayablesModel.getSourceType());
			generalLedgerModel.setCounterParty(accountPayablesModel.getSupplierName());
			generalLedgerModel.setEntryDate(new Date());
			generalLedgerModel.setCreatedUser(accountPayablesModel.getCreatedUser());
			generalLedgerModel.setLastUpdateUser(accountPayablesModel.getLastUpdateUser());
			generalLedgerModel.setParty(pharmacyService.findPharmacyById(accountPayablesModel.getPharmacyModel().getPharmacyId()).getPharmacyName());

			if(accountPayablesModel.getSourceType().equalsIgnoreCase("Debit Note")) {
				generalLedgerModel.setDebit(0.0);
				generalLedgerModel.setCredit(new Float(-1 * accountPayablesModel.getTotalAmountPaid()));

			}else if(accountPayablesModel.getSourceType().equalsIgnoreCase("Credit Note")) {
				generalLedgerModel.setCredit(new Float(0.0));
				generalLedgerModel.setDebit(accountPayablesModel.getTotalAmountPaid());
			}else if(accountPayablesModel.getSourceType().equalsIgnoreCase("Purchase Returns - Debit Note")) {
				generalLedgerModel.setDebit(-1 * accountPayablesModel.getTotalAmountPaid());
				generalLedgerModel.setCredit(new Float(0.0));
			}else if(accountPayablesModel.getSourceType().equalsIgnoreCase("Invoice")) {
				generalLedgerModel.setDebit(accountPayablesModel.getTotalAmountPaid());
				generalLedgerModel.setCredit(new Float(0.0));
			}

			generalLedgerModel.setBalance(0.0);
			generalLedgerModel.setPharmacyModel(accountPayablesModel.getPharmacyModel());

			modelRes.add(generalLegderRepo.save(generalLedgerModel));

			log.info("GeneralLedger data saved succesfully");
		}
		return modelRes;

	}



	@Override
	public List<GeneralLedgerModel> saveMultipleLedgersDataForAccRecievables(List<AccountReceivablesModel> accountRecievabelsModels) {
		List<GeneralLedgerModel> modelRes = new ArrayList<>();

		for (AccountReceivablesModel accountRecievablesModel : accountRecievabelsModels) {
			Integer empId = accountRecievablesModel.getApprovedBy();
			accountRecievablesModel.setApprovedBy(empId);

			GeneralLedgerModel generalLedgerModel=new GeneralLedgerModel();

			generalLedgerModel.setJournalId(uniqueCodeService.findByUniqueCodeName("GL"));
			generalLedgerModel.setJournalRef(accountRecievablesModel.getReceiptNumber());
			generalLedgerModel.setEntryNo(accountRecievablesModel.getSourceRef());
			generalLedgerModel.setEntryType(accountRecievablesModel.getSourceType());
			generalLedgerModel.setCounterParty(accountRecievablesModel.getCustomerName());
			generalLedgerModel.setEntryDate(new Date());
			generalLedgerModel.setCreatedUser(accountRecievablesModel.getCreatedUser());
			generalLedgerModel.setLastUpdateUser(accountRecievablesModel.getLastUpdateUser());
			generalLedgerModel.setParty(pharmacyService.findPharmacyById(accountRecievablesModel.getPharmacyModel().getPharmacyId()).getPharmacyName());

			if(accountRecievablesModel.getSourceType().equalsIgnoreCase("Debit Note")) {

				generalLedgerModel.setDebit(0.0);
				generalLedgerModel.setCredit(accountRecievablesModel.getAmountReceived());

			}else if(accountRecievablesModel.getSourceType().equalsIgnoreCase("Credit Note")) {

				generalLedgerModel.setDebit(new Double(-1 * accountRecievablesModel.getAmountReceived()));
				generalLedgerModel.setCredit(new Float(0.0));

			}else if(accountRecievablesModel.getSourceType().equalsIgnoreCase("Sales Returns - Credit Note")) {
				generalLedgerModel.setDebit(new Double(-1 * accountRecievablesModel.getAmountReceived()));
				generalLedgerModel.setCredit(new Float(0.0));
			}

			generalLedgerModel.setBalance(0.0);
			generalLedgerModel.setPharmacyModel(accountRecievablesModel.getPharmacyModel());

			modelRes.add(generalLegderRepo.save(generalLedgerModel));


			log.info("GeneralLedger data saved succesfully");
		}
		return modelRes;
	}



	@Override
	public GeneralLedgerModel saveGeneralLedgerData(AccountReceivablesModel generalLedgerModel) {
		GeneralLedgerModel ledgerModelObj = newReceivableEntry(generalLedgerModel,
				generalLedgerModel.getReceiptNumber());

		// Left exactly as it was. This books a negative debit rather than a
		// positive one, which disagrees with how the rest of the ledger records
		// a reduction - see saveReceivableLedgerEntry - but it is what the 324
		// existing Sales Billing rows were written by, and correcting it is a
		// separate decision from getting credit settlement right.
		if (generalLedgerModel.getAmountReceived() > 0) {
			ledgerModelObj.setCredit(generalLedgerModel.getAmountReceived());
			ledgerModelObj.setDebit(0.0);
		} else {
			ledgerModelObj.setDebit(new Double(generalLedgerModel.getAmountReceived()));
			ledgerModelObj.setCredit(new Float(0.0));
		}

		return generalLegderRepo.save(ledgerModelObj);
	}

	/**
	 * Everything a ledger entry against a receivable carries except its amount.
	 */
	private GeneralLedgerModel newReceivableEntry(AccountReceivablesModel receivable, String journalRef) {
		GeneralLedgerModel entry = new GeneralLedgerModel();

		entry.setJournalId(uniqueCodeService.findByUniqueCodeName("GL"));
		entry.setJournalRef(journalRef);
		entry.setEntryNo(receivable.getSourceRef());
		entry.setEntryType(receivable.getSourceType());
		entry.setCounterParty(receivable.getCustomerName());
		entry.setParty(pharmacyService
				.findPharmacyById(receivable.getPharmacyModel().getPharmacyId()).getPharmacyName());
		entry.setEntryDate(new Date());
		entry.setCreatedUser(receivable.getCreatedUser());
		entry.setLastUpdateUser(receivable.getLastUpdateUser());
		entry.setBalance(0.0);
		entry.setPharmacyModel(receivable.getPharmacyModel());

		return entry;
	}

	/**
	 * Books one movement against a receivable, for the amount the caller says
	 * moved.
	 *
	 * The multi-row sibling above works off the receivable's cumulative
	 * AMOUNT_RECEIVED and books nothing at all unless the row is a credit or
	 * debit note, which is no use to anything settling a "Sales Billing" row -
	 * a 200,000 credit settlement posted 59 ledger rows carrying no debit and
	 * no credit. Passing the amount in explicitly is what makes the entry both
	 * present and right: a bill that already held a part payment must book only
	 * what this payment added, not the running total.
	 *
	 * A positive amount is money coming in and is credited; a negative one
	 * reverses it and is debited, which is how a settlement is unwound.
	 */
	@Override
	public GeneralLedgerModel saveReceivableLedgerEntry(AccountReceivablesModel receivable,
			BigDecimal amount, String journalRef) {

		GeneralLedgerModel entry = newReceivableEntry(receivable, journalRef);

		// Both columns hold positive amounts, which is how the rest of this
		// ledger is written and the only way its two halves total correctly:
		// an expense posts a debit leg and a credit leg of the same positive
		// amount, and a credit note - the system's own way of taking value back
		// off a customer's receivable, the same shape as reversing a settlement
		// - is stored as a positive debit across 608 rows. Booking a reversal
		// as a negative debit instead would not cancel the collection under
		// credit minus debit; it would count it a second time.
		if (amount.signum() >= 0) {
			entry.setCredit(amount.floatValue());
			entry.setDebit(0.0);
		} else {
			entry.setDebit(amount.negate().doubleValue());
			entry.setCredit(new Float(0.0));
		}

		return generalLegderRepo.save(entry);
	}



	@Override
	public Double getTotalLimit() {
		
		return generalLegderRepo.getTotalLimitFromGL();
	}


}
