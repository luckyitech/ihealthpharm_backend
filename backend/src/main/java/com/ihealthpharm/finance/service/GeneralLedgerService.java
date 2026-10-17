package com.ihealthpharm.finance.service;

import java.math.BigDecimal;
import java.util.List;

import com.ihealthpharm.finance.model.AccountPayablesModel;
import com.ihealthpharm.finance.model.AccountReceivablesModel;
import com.ihealthpharm.finance.model.GeneralLedgerModel;

public interface GeneralLedgerService {
	
	GeneralLedgerModel saveGeneraledger(GeneralLedgerModel generalLedgerModel);
	
	List<GeneralLedgerModel> saveMutipleLedgersData(List<AccountPayablesModel> generalLedgerModels);
	
    List<GeneralLedgerModel> saveMultipleLedgersDataForAccRecievables(List<AccountReceivablesModel> generalLedgerModels);
    
    GeneralLedgerModel saveGeneralLedgerData(AccountReceivablesModel generalLedgerModel);

    /**
     * Books one movement against a receivable for an amount the caller states,
     * rather than for the row's cumulative AMOUNT_RECEIVED. Positive is money
     * received and is credited; negative reverses it and is debited.
     */
    GeneralLedgerModel saveReceivableLedgerEntry(AccountReceivablesModel receivable,
            BigDecimal amount, String journalRef);
    
    Double getTotalLimit();
}
