package com.ihealthpharm.finance.service.impl;

import java.text.DecimalFormat;
import java.time.LocalDate;
import java.util.Collections;
import java.util.Arrays;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;

import javax.transaction.Transactional;

import org.json.simple.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.ihealthpharm.exception.IHealthPharmException;
import com.ihealthpharm.finance.dao.AccountReceivablesRepository;
import com.ihealthpharm.finance.dao.CreditNoteRepository;
import com.ihealthpharm.finance.dao.DebitNoteRepository;
import com.ihealthpharm.finance.dto.AccRecievablesCustomerDTO;
import com.ihealthpharm.finance.dto.RecieptMoneyCalDTO;
import com.ihealthpharm.finance.helper.AccountReceivablesHelper;
import com.ihealthpharm.finance.model.AccountReceivablesModel;
import com.ihealthpharm.finance.model.CreditNoteModel;
import com.ihealthpharm.finance.model.DebitNoteModel;
import com.ihealthpharm.finance.service.AccountReceivablesService;
import com.ihealthpharm.masters.dao.CustomerInsuranceRepository;
import com.ihealthpharm.masters.dao.MasterAccountRepository;
import com.ihealthpharm.masters.model.MasterAccountModel;
import com.ihealthpharm.masters.service.MasterAccountService;
import com.ihealthpharm.sales.dao.SalesRepository;
import com.ihealthpharm.sales.model.SalesModel;

import lombok.extern.slf4j.Slf4j;

@Service
@Transactional
@Slf4j
public class AccountReceivablesServiceImpl implements AccountReceivablesService {

	@Autowired
	AccountReceivablesRepository accountReceivablesRepository;

	@Autowired
	SalesRepository salesRepository;

	@Autowired
	CustomerInsuranceRepository customerInsuranceRepository;

	@Autowired
	AccountReceivablesHelper accountReceivablesHelper;

	@Autowired
	MasterAccountService masterService;

	@Autowired
	MasterAccountRepository masterAccRepo;

	@Autowired
	CreditNoteRepository creditNoteRepo;

	@Autowired
	DebitNoteRepository debitNoteRepo;

	@Override
	public AccountReceivablesModel saveAccountReceivablesData(AccountReceivablesModel accountReceivables) {
		
		List<AccountReceivablesModel> accountReceivablesModelList=accountReceivablesRepository.
				findAccountReceivablesByBillIdAndStatus(accountReceivables.getSourceRef(),
						accountReceivables.getSourceType(),accountReceivables.getPaymentStatus());
		if(accountReceivablesModelList.size()>0) {
			
			accountReceivablesRepository.
			deleteAccountReceivablesByBillIdAndStatus(accountReceivables.getSourceRef(),
					accountReceivables.getSourceType(),accountReceivables.getPaymentStatus());
			
		}

		AccountReceivablesModel accountReceivablesRes = accountReceivablesRepository.save(accountReceivables);
		log.info("AccountReceivables data with ID : " + accountReceivablesRes.getAccountReceivablesId()
		+ " Saved succesfully");
        float amountRecived = 0;
		if(accountReceivables.getPaymentStatus().equals("Cancel") && accountReceivables.getSourceType().equals("Sales Billing")) {

			AccountReceivablesModel accRecExistedRecord=accountReceivablesRepository.findBySourceRef(accountReceivables.getSourceRef());
			System.out.println(accRecExistedRecord);
			if(Objects.nonNull(accRecExistedRecord)) {
			accRecExistedRecord.setPaymentStatus("Cancel");
			accRecExistedRecord.setStatus("Approved");
			if (accRecExistedRecord.getAmountToBeReceived() > 0) {
				amountRecived += -1 * accountReceivables.getAmountToBeReceived();
			}
			accRecExistedRecord.setAmountToBeReceived(amountRecived);
			AccountReceivablesModel saveRes = accountReceivablesRepository.save(accRecExistedRecord);
			}
		}
		

		return accountReceivablesRes;
	}

	@Override
	public AccountReceivablesModel updateAccountReceivablesData(AccountReceivablesModel accountReceivables) {
		AccountReceivablesModel accountReceivablesRes = getValidAccountsReceivables(
				accountReceivables.getAccountReceivablesId());
		if (!Objects.nonNull(accountReceivablesRes)) {
			throw new IHealthPharmException(accountReceivablesHelper.getNotFoundAccountReceivablesMessage(),
					HttpStatus.NOT_FOUND);
		}

		accountReceivablesRes = accountReceivablesRepository.save(accountReceivables);
		log.info("AccountReceivables data with ID : " + accountReceivablesRes.getAccountReceivablesId()
		+ " updated succesfully");
		return accountReceivablesRes;
	}

	@Override
	public List<AccountReceivablesModel> updateAccountsReceivablesData(
			List<AccountReceivablesModel> accountsReceivables) {

		Boolean creditNoteTransaction = false;
		String CustomerName = "";

		for (AccountReceivablesModel accountReceivables : accountsReceivables) {

			AccountReceivablesModel accountReceivablesRes = getValidAccountsReceivables(
					accountReceivables.getAccountReceivablesId());
			if (!Objects.nonNull(accountReceivablesRes)) {
				throw new IHealthPharmException(accountReceivablesHelper.getNotFoundAccountReceivablesMessage(),
						HttpStatus.NOT_FOUND);
			}

			accountReceivablesRes = accountReceivablesRepository.save(accountReceivables);

			// to update credit and debit note payment status
			if (accountReceivables.getSourceType().contains("Credit Note")) {
				CreditNoteModel c = creditNoteRepo.getCreditNoteDataById(accountReceivables.getSource());
				creditNoteRepo.updatePaymentStatus(c.getCreditNoteId(),"Paid");
				
				//c.setPaymentStatus("Paid");
				//creditNoteRepo.save(c);
			} else if (accountReceivables.getSourceType().contains("Debit Note")) {
				DebitNoteModel d = debitNoteRepo.getDebitNoteDataById(accountReceivables.getSource());
				d.setPaymentStatus("Paid");
				debitNoteRepo.save(d);
			}

			// if it is credit note record
			if (accountReceivables.getPaymentType().equals("Credit Note")) {
				creditNoteTransaction = true;
			} else if (Objects.nonNull(accountReceivables.getPartiallyPaid())) {
				if (accountReceivables.getPartiallyPaid().equals("Credit Note")) {
					CustomerName = accountReceivables.getCustomerName();
					creditNoteTransaction = true;
				}
			}
		}

		if (creditNoteTransaction) {
			Double amountRecived = 0.0;
			String creditNoteRefNo = "";
			SalesModel salesRecord = null;
			Double anotherModePayments = 0.0;
			DecimalFormat df = new DecimalFormat(".##");
			for (AccountReceivablesModel accountReceivables : accountsReceivables) {

				if (!accountReceivables.getSourceType().contains("Sales Billing")) {

					if (accountReceivables.getSourceType().contains("Credit")) {
						if (accountReceivables.getAmountReceived() > 0) {
							amountRecived += accountReceivables.getAmountReceived();
						} else if (accountReceivables.getAmountReceived() < 0) {
							amountRecived += -1 * accountReceivables.getAmountReceived();
						}

						if (creditNoteRefNo.equals("")) {
							creditNoteRefNo = accountReceivables.getSourceRef();
						} else {
							creditNoteRefNo += "," + accountReceivables.getSourceRef();
						}
					}
				} else {
					if (Objects.nonNull(accountReceivables.getSourceRef())) {
						salesRecord = accountReceivablesRepository
								.getSalesByBillCode(accountReceivables.getSourceRef().trim());
						String lastUpdatedUserId = Integer.toString(accountReceivables.getLastUpdateUser());
						salesRecord.setLastUpdateUserId(lastUpdatedUserId);
					}
				}

				if (Objects.nonNull(salesRecord)) {

					Double anotherPayAmt = 0.0;
					if (salesRecord.getChequeAmount() != null) {
						anotherPayAmt += salesRecord.getChequeAmount();
					}
					if (salesRecord.getCreditCardAmount() != null) {
						anotherPayAmt += salesRecord.getCreditCardAmount().doubleValue();
					}
					if (salesRecord.getCashAmount() != null) {
						anotherPayAmt += salesRecord.getCashAmount().doubleValue();
					}
					if (salesRecord.getUpiAmount() != null) {
						anotherPayAmt += salesRecord.getUpiAmount().doubleValue();
					}
					if (salesRecord.getCreditNoteAmount() != null) {
						anotherPayAmt += salesRecord.getCreditNoteAmount();
					}

					if (accountReceivables.getPaymentType().equals("Card")) {
						anotherModePayments = (double) accountReceivables.getCreditCardAmount();
						salesRecord.setCreditCardAmount(accountReceivables.getCreditCardAmount());
						salesRecord.setCreditCardNo(accountReceivables.getCreditCardNo());
						salesRecord.setCreditCardAuthNo(accountReceivables.getCardAuthCode());

					}
					if (accountReceivables.getPaymentType().equals("Cash")) {
						anotherModePayments = (double) accountReceivables.getCashAmount();
						salesRecord.setCashAmount(accountReceivables.getCashAmount());

					}
					if ((accountReceivables.getPaymentType().equals("MPesa"))) {
						anotherModePayments = (double) accountReceivables.getUpiAmount();
						salesRecord.setUpiAmount(accountReceivables.getUpiAmount());
						salesRecord.setUpiPhoneNo(accountReceivables.getUpiPhoneNo());
						salesRecord.setUpiTransactionId(accountReceivables.getUpiAuthCode());

					}
					if ((accountReceivables.getPaymentType().equals("Cheque"))) {
						anotherModePayments = (double) accountReceivables.getChequeAmount();
						salesRecord.setChequeAmount(accountReceivables.getChequeAmount());
						LocalDate chequeDt = accountReceivables.getChequeDate();
						salesRecord.setChequeDate(chequeDt.toString());
						salesRecord.setChequeNumber(accountReceivables.getChequeNumber());
					}
					double bal = 0.0;
					if (salesRecord.getPaymentStatus().equals("Pending")) {
						bal = salesRecord.getNetAmount() - Double.parseDouble(df.format(amountRecived));
					} else {
						bal = salesRecord.getBalanceAmount() - Double.parseDouble(df.format(amountRecived));
					}

					Double balance = 0.0;
					if (Objects.nonNull(anotherModePayments)) {

						balance = ((double) salesRecord.getNetAmount())
								- (Double.parseDouble(df.format(anotherModePayments))
										+ Double.parseDouble(df.format(amountRecived))
										+ Double.parseDouble(df.format(anotherPayAmt)));

					} else {
						balance = (double) bal;
					}
					System.out.println(balance + "final bal");
					if (balance > 0) {
						salesRecord.setPaymentStatus("Partially Paid");
						double creditAmt = salesRecord.getCreditAmount()
								- (Double.parseDouble(df.format(amountRecived)));
						salesRecord.setBalanceAmount((float) (Double.parseDouble(df.format(creditAmt))));
						salesRecord.setCreditAmount((Double.parseDouble(df.format(creditAmt))));
						salesRecord
						.setPaidAmount((float) Double.parseDouble(df.format(salesRecord.getNetAmount() - bal)));
						if (salesRecord.getCreditNoteAmount() != null) {
							salesRecord.setCreditNoteAmount((double) Double
									.parseDouble(df.format(amountRecived + salesRecord.getCreditNoteAmount())));
						} else {
							salesRecord.setCreditNoteAmount((double) Double.parseDouble(df.format(amountRecived)));
						}
						if (salesRecord.getSalesCreditRefNo() != null) {
							salesRecord.setSalesCreditRefNo(salesRecord.getSalesCreditRefNo() + creditNoteRefNo);
						} else {
							salesRecord.setSalesCreditRefNo(creditNoteRefNo);
						}

					} else {
						salesRecord.setBalanceAmount((float) 0);
						salesRecord.setPaymentStatus("Paid");
						salesRecord.setCreditNoteAmount((double) Double.parseDouble(df.format(amountRecived)));
						salesRecord.setSalesCreditRefNo(creditNoteRefNo);
						salesRecord.setCreditAmount((double) 0);
						salesRecord.setPaidAmount(salesRecord.getNetAmount());
					}

					SalesModel salesRes = salesRepository.save(salesRecord);
					if (Objects.nonNull(salesRes)) {
						MasterAccountModel masterAccObj = null;
						if (Objects.nonNull(salesRes.getCreditAccountNo())) {
							if (Objects.nonNull(salesRes.getCreditAccountNo())) {
								masterAccObj = masterAccRepo.getDataByMasterCreditNumber(salesRes.getCreditAccountNo());
							}
						}
						if (Objects.nonNull(masterAccObj)) {
							Double creditAmountUpdate = 0.0;
							if (salesRes.getPaymentStatus().equals("Paid")) {
								creditAmountUpdate = (double) Double.parseDouble(df.format(salesRes.getNetAmount()));
							} else {
								creditAmountUpdate = (double) Double.parseDouble(df.format(amountRecived));
							}
							double amount = (double) Double.parseDouble(
									df.format(creditAmountUpdate + (double) masterAccObj.getCreditLimitLeft()));
							masterAccObj.setCreditLimitLeft((int) amount);
							masterAccRepo.save(masterAccObj);

						}
					}

				} else {
					if (!CustomerName.equals("")) {
						MasterAccountModel masterAccObj = null;
						SalesModel salesResponse = null;
						if (Objects.nonNull(accountReceivables.getBillRefNo())) {
							salesResponse = accountReceivablesRepository
									.getSalesByBillCode(accountReceivables.getBillRefNo());
							if (Objects.nonNull(salesResponse.getCreditAccountNo())) {
								masterAccObj = masterAccRepo
										.getDataByMasterCreditNumber(salesResponse.getCreditAccountNo());
							}
						} else {
							salesResponse = salesRepository.getSalesRecordById(accountReceivables.getSalesBillId());
							if (Objects.nonNull(salesResponse.getCreditAccountNo())) {
								masterAccObj = masterAccRepo
										.getDataByMasterCreditNumber(salesResponse.getCreditAccountNo());
							}
						}

						if (Objects.nonNull(masterAccObj)) {
							if (salesResponse.getPaymentStatus().equalsIgnoreCase("Partially Paid")) {
								double amt = (double) Double
										.parseDouble(df.format(-1 * accountReceivables.getAmountReceived()));
								double amount = (double) Double
										.parseDouble(df.format(amt + (double) masterAccObj.getCreditLimitLeft()));
								masterAccObj.setCreditLimitLeft((int) amount);
							} else if (salesResponse.getPaymentStatus().equalsIgnoreCase("Paid")) {
								double amt = (double) Double
										.parseDouble(df.format(-1 * accountReceivables.getAmountReceived()));
								double amount = (double) Double
										.parseDouble(df.format(amt + (double) masterAccObj.getCreditLimitLeft()));
								masterAccObj.setCreditLimitLeft((int) amount);
							} else {
								double amt = (double) Double
										.parseDouble(df.format(-1 * accountReceivables.getAmountReceived()));
								double amount = (double) Double
										.parseDouble(df.format(amt + (double) masterAccObj.getCreditLimitLeft()));
								masterAccObj.setCreditLimitLeft((int) amount);
							}
							masterAccRepo.save(masterAccObj);
						}
					}
				}
			}
		} else {
			System.out.println("[[[[[[[[[[]]]]]]]]]]]]]]]]]]]]]]]]][]]]]]]]]]]]]]]]]]]]]]]]]]]]]]");
			for (AccountReceivablesModel accountReceivables : accountsReceivables) {

				// to update the sales billing by using sales return credit note
				if (accountReceivables.getPaymentStatus().equalsIgnoreCase("Paid")) {
					System.out.println(accountReceivables.getPaymentType() + " ././../...");

					// Below condition is for partial payment of sales bill with credit note payment
					// type
					if (accountReceivables.getPartiallyPaid() == null) {
						// from accont recievables the bill is completely paid
						// if the payment type is credit note
						if (accountReceivables.getPaymentType().equals("Credit Note")) {

							DecimalFormat df = new DecimalFormat(".##");

						} else if (accountReceivables.getSourceType().equals("Sales Returns - Credit Note")
								&& accountReceivables.getPaymentStatus().equals("Paid")) {
							SalesModel salesRecord = null;
							Double creditAmount = 0.0;
							if (Objects.nonNull(accountReceivables.getSalesBillId())) {
								salesRecord = salesRepository.getSalesRecordById(accountReceivables.getSalesBillId());
								creditAmount = Objects.nonNull(salesRecord.getCreditAmount())
										? salesRecord.getCreditAmount()
												: 0;
							} else {
								salesRecord = accountReceivablesRepository
										.getSalesByBillCode(accountReceivables.getBillRefNo().trim());
								creditAmount = Objects.nonNull(salesRecord.getCreditAmount())
										? salesRecord.getCreditAmount()
												: 0;
							}

							double finalAmt = salesRecord.getNetAmount()
									- (-1 * accountReceivables.getAmountReceived());
							DecimalFormat df = new DecimalFormat(".##");
							double balAmt = Double.parseDouble(df.format(finalAmt));
							if (balAmt != 0) {
								salesRecord.setBalanceAmount((float) balAmt);
								salesRecord.setPaymentStatus("Partially Paid");
								salesRecord.setPaidAmount(salesRecord.getNetAmount() - salesRecord.getBalanceAmount());
								String lastUpdatedUserId = Integer.toString(accountReceivables.getLastUpdateUser());
								salesRecord.setLastUpdateUserId(lastUpdatedUserId);
								double creditAmt = balAmt;
								salesRecord.setCreditAmount((Double.parseDouble(df.format(creditAmt))));
								if (accountReceivables.getPaymentcreditRefNo() != null) {
									salesRecord.setSalesCreditRefNo(accountReceivables.getPaymentcreditRefNo());
									salesRecord.setCreditNoteAmount((double) Double
											.parseDouble(df.format(-1 * accountReceivables.getAmountReceived())));
								} else {
									if (salesRecord.getBillCode().equals(accountReceivables.getSourceRef().trim())) {
										if (Objects.isNull(salesRecord.getSalesCreditRefNo())) {
											salesRecord.setChequeAmount((double) Double.parseDouble(
													df.format(-1 * accountReceivables.getAmountReceived())));
											salesRecord.setChequeNumber(accountReceivables.getReceiptNumber());
											salesRecord.setCreditNoteAmount((double) 0);
											LocalDate chequeDt = LocalDate.now();
											salesRecord.setChequeDate(chequeDt.toString());
										}
									} else {
										salesRecord.setSalesCreditRefNo(accountReceivables.getSourceRef());
										salesRecord.setCreditNoteAmount((double) Double
												.parseDouble(df.format(-1 * accountReceivables.getAmountReceived())));
									}

								}
							} else {
								salesRecord.setBalanceAmount((float) 0);
								salesRecord.setPaymentStatus("Paid");

								if (accountReceivables.getPaymentcreditRefNo() != null) {
									salesRecord.setSalesCreditRefNo(accountReceivables.getPaymentcreditRefNo());
									salesRecord.setCreditNoteAmount(
											(double) (-1 * accountReceivables.getAmountReceived()));
								} else {
									if (salesRecord.getBillCode().equals(accountReceivables.getSourceRef().trim())) {
										if (Objects.isNull(salesRecord.getSalesCreditRefNo())) {
											salesRecord.setChequeAmount((double) Double.parseDouble(
													df.format(-1 * accountReceivables.getAmountReceived())));
											salesRecord.setChequeNumber(accountReceivables.getReceiptNumber());
											salesRecord.setCreditNoteAmount((double) 0);
											LocalDate chequeDt = LocalDate.now();
											salesRecord.setChequeDate(chequeDt.toString());
										}
									} else {
										salesRecord.setSalesCreditRefNo(accountReceivables.getSourceRef());
										salesRecord.setCreditNoteAmount(
												(double) (-1 * accountReceivables.getAmountReceived()));
									}

								}
								salesRecord.setCreditAmount((double) 0);
								String lastUpdatedUserId = Integer.toString(accountReceivables.getLastUpdateUser());
								salesRecord.setLastUpdateUserId(lastUpdatedUserId);
								salesRecord.setPaidAmount(Objects.nonNull(salesRecord.getPaidAmount())
										? salesRecord.getPaidAmount() + (-1 * accountReceivables.getAmountReceived())
												: (-1 * accountReceivables.getAmountReceived()));

							}
							salesRepository.save(salesRecord);
						} else if (accountReceivables.getSourceType().equals("Credit Note")
								&& accountReceivables.getPaymentStatus().equals("Paid")) {
							SalesModel salesData = null;
							Double creditAmount = 0.0;
							if (Objects.nonNull(accountReceivables.getSalesBillId())) {
								salesData = salesRepository.getSalesRecordById(accountReceivables.getSalesBillId());
								creditAmount = Objects.nonNull(salesData.getCreditAmount())
										? salesData.getCreditAmount()
												: 0;
							} else {
								salesData = accountReceivablesRepository
										.getSalesByBillCode(accountReceivables.getBillRefNo().trim());
								creditAmount = Objects.nonNull(salesData.getCreditAmount())
										? salesData.getCreditAmount()
												: 0;
							}
							double recievedAmt = -1 * accountReceivables.getAmountReceived();

							double paidAmt = (double) (salesData.getPaidAmount() + recievedAmt);
							DecimalFormat df = new DecimalFormat(".##");
							String lastUpdatedUserId = Integer.toString(accountReceivables.getLastUpdateUser());
							salesData.setLastUpdateUserId(lastUpdatedUserId);
							if (salesData.getBalanceAmount() == recievedAmt) {
								System.out.println(salesData.getPaidAmount() + " paid amt");
								System.out.println(recievedAmt + "... recieved am");
								salesData.setBalanceAmount((float) 0);
								salesData.setPaymentStatus("Paid");
								salesData.setPaidAmount((float) Double.parseDouble(df.format(paidAmt)));
								double creditAmt = (salesData.getCreditAmount() != null ? salesData.getCreditAmount()
										: 0) - (recievedAmt);
								salesData.setCreditAmount((Double.parseDouble(df.format(creditAmt))));
								double creditNoteAmt = salesData.getCreditNoteAmount() != null
										? salesData.getCreditNoteAmount()
												: 0;
										double creditNOteAmttoFixed = (double) Double.parseDouble(df.format(creditNoteAmt));
										double d = (double) Double.parseDouble(df.format(recievedAmt));
										double amt = (double) (creditNOteAmttoFixed + d);
										String salesCreditRef = salesData.getSalesCreditRefNo() != null
												? salesData.getSalesCreditRefNo()
														: " ";
												salesData.setSalesCreditRefNo(salesCreditRef + "," + accountReceivables.getSourceRef());
							} else {
								if (Objects.nonNull(accountReceivables.getBillRefNo())) {
									if (accountReceivables.getBillRefNo().equals(salesData.getBillCode())) {
										salesData.setSalesCreditRefNo(accountReceivables.getSourceRef());
										salesData.setCreditNoteAmount(
												(double) Double.parseDouble(df.format(recievedAmt)));
									}
								}

							}
							salesRepository.save(salesData);
						}
					}

					if (accountReceivables.getSourceType().equalsIgnoreCase("Sales Billing")
							&& Objects.nonNull(accountReceivables.getSourceRef())
							&& accountReceivables.getPaymentStatus().equalsIgnoreCase("Paid")
							&& Objects.nonNull(accountReceivables.getCreditNumber())) {
						DecimalFormat df = new DecimalFormat(".##");
						SalesModel salesRecord = accountReceivablesRepository
								.getSalesByBillCode(accountReceivables.getSourceRef().trim());
						Double creditAmount = Objects.nonNull(salesRecord.getCreditAmount())
								? salesRecord.getCreditAmount()
										: 0;
								if (creditAmount > 0) {

									Double anotherPayAmt = 0.0;
									if (salesRecord.getChequeAmount() != null) {
										anotherPayAmt += salesRecord.getChequeAmount();
									}
									if (salesRecord.getCreditCardAmount() != null) {
										anotherPayAmt += salesRecord.getCreditCardAmount().doubleValue();
									}
									if (salesRecord.getCashAmount() != null) {
										anotherPayAmt += salesRecord.getCashAmount().doubleValue();
									}
									if (salesRecord.getUpiAmount() != null) {
										anotherPayAmt += salesRecord.getUpiAmount().doubleValue();
									}
									if (salesRecord.getCreditNoteAmount() != null) {
										anotherPayAmt += salesRecord.getCreditNoteAmount();
									}

									if (Objects.nonNull(accountReceivables.getCashAmount())) {
										salesRecord.setPaymentStatus("Paid");
										Float cashAmount = Objects.nonNull(salesRecord.getCashAmount())
												? salesRecord.getCashAmount()
														: 0;
												if (salesRecord.getBalanceAmount() != 0) {

													if (salesRecord.getCreditNoteAmount() != null) {
														Float paidAmount = (float) (cashAmount + creditAmount);
														salesRecord.setCashAmount((float) paidAmount);
														String lastUpdatedUserId = Integer
																.toString(accountReceivables.getLastUpdateUser());
														salesRecord.setLastUpdateUserId(lastUpdatedUserId);
														System.out.println("paid amount "+paidAmount);
														System.out.println("another payment "+anotherPayAmt);
														double amt = Double.parseDouble(df.format(salesRecord.getBalanceAmount()))
																+ Double.parseDouble(df.format(anotherPayAmt));
														System.out.println(salesRecord.getNetAmount());
														System.out.println(salesRecord.getBalanceAmount());
														if (amt == salesRecord.getNetAmount()) {
															salesRecord.setPaymentStatus("Paid");
															salesRecord.setCreditAmount((double) 0);
															salesRecord.setBalanceAmount((float) 0);
															salesRecord.setPaidAmount((float) amt);
														} else {
															double bal = salesRecord.getBalanceAmount() - salesRecord.getPaidAmount();
															if (bal == 0) {
																salesRecord.setPaymentStatus("Paid");
																salesRecord.setPaidAmount((float) salesRecord.getNetAmount());
																salesRecord.setCreditAmount((double) 0);
															} else {
																System.out.println(salesRecord.getCreditAmount() + " cedit amt");
																salesRecord.setPaymentStatus("Partially Paid");
																salesRecord.setPaidAmount(
																		(float) Double.parseDouble(df.format((salesRecord.getNetAmount()
																				- salesRecord.getBalanceAmount()))));
																salesRecord.setCreditAmount((double) bal);
															}
															salesRecord.setBalanceAmount((float) bal);
														}
													} else {
														Float paidAmount = (float) (cashAmount + creditAmount);
														salesRecord.setPaymentStatus("Paid");
														salesRecord.setCashAmount((float) paidAmount);
														salesRecord.setCreditAmount((double) 0);
														salesRecord.setPaidAmount((float) salesRecord.getNetAmount());
														salesRecord.setBalanceAmount((float) 0);
														String lastUpdatedUserId = Integer
																.toString(accountReceivables.getLastUpdateUser());
														salesRecord.setLastUpdateUserId(lastUpdatedUserId);
													}
												} else {
													salesRecord.setPaymentStatus("Paid");
													salesRecord.setCreditAmount((double) 0);
													salesRecord.setPaidAmount((float) salesRecord.getNetAmount());
													salesRecord.setBalanceAmount((float) 0);
													String lastUpdatedUserId = Integer.toString(accountReceivables.getLastUpdateUser());
													salesRecord.setLastUpdateUserId(lastUpdatedUserId);
												}

									} else if (Objects.nonNull(accountReceivables.getChequeAmount())) {
										Double chequeAmount = Objects.nonNull(salesRecord.getChequeAmount())
												? salesRecord.getChequeAmount()
														: 0;
												if (salesRecord.getBalanceAmount() != 0) {

													if (salesRecord.getCreditNoteAmount() != null) {
														Float paidAmount = (float) (chequeAmount + creditAmount);
														salesRecord.setChequeAmount((double) paidAmount);
														LocalDate chequeDt = accountReceivables.getChequeDate();
														salesRecord.setChequeDate(chequeDt.toString());
														salesRecord.setChequeNumber(accountReceivables.getChequeNumber());
														String lastUpdatedUserId = Integer
																.toString(accountReceivables.getLastUpdateUser());
														salesRecord.setLastUpdateUserId(lastUpdatedUserId);
														double amt = Double.parseDouble(df.format(paidAmount))
																+ Double.parseDouble(df.format(anotherPayAmt));
														if (amt == salesRecord.getNetAmount()) {
															salesRecord.setPaymentStatus("Paid");
															salesRecord.setCreditAmount((double) 0);
															salesRecord.setBalanceAmount((float) 0);
															salesRecord.setPaidAmount((float) amt);
														} else {
															double bal = salesRecord.getBalanceAmount() - salesRecord.getPaidAmount();
															if (bal == 0) {
																salesRecord.setPaymentStatus("Paid");
																salesRecord.setCreditAmount((double) 0);
																salesRecord.setPaidAmount(
																		salesRecord.getNetAmount() - salesRecord.getBalanceAmount());
															} else {
																salesRecord.setPaymentStatus("Partially Paid");
																salesRecord.setCreditAmount((double) bal);
															}
															salesRecord.setBalanceAmount((float) bal);
														}

													} else {
														if (accountReceivables.getSourceType().equals("Credit Note")) {
															Float paidAmount = (float) (chequeAmount + creditAmount);
															salesRecord.setPaymentStatus("Paid");
															double paidAmt = salesRecord.getNetAmount()
																	- (salesRecord.getCreditNoteAmount() != null
																	? salesRecord.getCreditNoteAmount()
																			: 0);
															salesRecord.setChequeAmount((double) paidAmt);
															salesRecord.setCreditAmount((double) 0);
															salesRecord.setPaidAmount((float) paidAmount);

															salesRecord.setSalesCreditRefNo(accountReceivables.getSourceRef());
															salesRecord.setBalanceAmount((float) 0);
															LocalDate chequeDt = accountReceivables.getChequeDate();
															salesRecord.setChequeDate(chequeDt.toString());
															salesRecord.setChequeNumber(accountReceivables.getChequeNumber());
															String lastUpdatedUserId = Integer
																	.toString(accountReceivables.getLastUpdateUser());
															salesRecord.setLastUpdateUserId(lastUpdatedUserId);

														} else {
															Float paidAmount = (float) (chequeAmount + creditAmount);
															salesRecord.setPaymentStatus("Paid");
															salesRecord.setChequeAmount((double) paidAmount);
															salesRecord.setCreditAmount((double) 0);
															salesRecord.setPaidAmount((float) paidAmount);
															salesRecord.setBalanceAmount((float) 0);
															LocalDate chequeDt = accountReceivables.getChequeDate();
															salesRecord.setChequeDate(chequeDt.toString());
															salesRecord.setChequeNumber(accountReceivables.getChequeNumber());
															String lastUpdatedUserId = Integer
																	.toString(accountReceivables.getLastUpdateUser());
															salesRecord.setLastUpdateUserId(lastUpdatedUserId);
														}
													}
												} else {
													salesRecord.setPaymentStatus("Paid");
													salesRecord.setCreditAmount((double) 0);
													salesRecord.setPaidAmount((float) salesRecord.getNetAmount());
													salesRecord.setBalanceAmount((float) 0);
													String lastUpdatedUserId = Integer.toString(accountReceivables.getLastUpdateUser());
													salesRecord.setLastUpdateUserId(lastUpdatedUserId);
												}
									} else if (Objects.nonNull(accountReceivables.getCreditCardAmount())) {
										if (salesRecord.getBalanceAmount() != 0) {
											Float creditCardAmount = Objects.nonNull(salesRecord.getCreditCardAmount())
													? salesRecord.getCreditCardAmount()
															: 0;
													if (salesRecord.getCreditNoteAmount() != null) {

														Float paidAmount = (float) (creditCardAmount + creditAmount);
														salesRecord.setCreditCardAmount(paidAmount);
														salesRecord.setCreditCardNo(accountReceivables.getCreditCardNo());
														salesRecord.setCreditCardAuthNo(accountReceivables.getCardAuthCode());
														String lastUpdatedUserId = Integer
																.toString(accountReceivables.getLastUpdateUser());
														salesRecord.setLastUpdateUserId(lastUpdatedUserId);
														// after doing partial amt again paying the full amt with any payment type
														double amt = Double.parseDouble(df.format(paidAmount))
																+ +Double.parseDouble(df.format(anotherPayAmt));
														System.out.println(salesRecord.getNetAmount());
														if (amt == salesRecord.getNetAmount()) {
															salesRecord.setPaymentStatus("Paid");
															salesRecord.setCreditAmount((double) 0);
															salesRecord.setBalanceAmount((float) 0);
															salesRecord.setPaidAmount((float) amt);
														} else {

															double bal = salesRecord.getBalanceAmount() - salesRecord.getPaidAmount();
															if (bal == 0) {
																salesRecord.setPaymentStatus("Paid");
																salesRecord.setCreditAmount((double) 0);
															} else {
																salesRecord.setPaymentStatus("Partially Paid");
																salesRecord.setCreditAmount((double) bal);
															}
															salesRecord.setBalanceAmount((float) bal);

															if (salesRecord.getCreditNoteAmount() != null) {
																double amtPaid = salesRecord.getCreditNoteAmount() + paidAmount;
																salesRecord.setPaidAmount((float) amtPaid);
															}
														}
													} else {
														Float paidAmount = (float) (creditCardAmount + creditAmount);
														salesRecord.setPaymentStatus("Paid");
														salesRecord.setCreditCardAmount((float) paidAmount);
														salesRecord.setCreditCardNo(accountReceivables.getCreditCardNo());
														salesRecord.setCreditCardAuthNo(accountReceivables.getCardAuthCode());
														salesRecord.setCreditAmount((double) 0);
														salesRecord.setPaidAmount((float) paidAmount);
														salesRecord.setBalanceAmount((float) 0);
														String lastUpdatedUserId = Integer
																.toString(accountReceivables.getLastUpdateUser());
														salesRecord.setLastUpdateUserId(lastUpdatedUserId);
													}
										} else {
											salesRecord.setPaymentStatus("Paid");
											salesRecord.setCreditAmount((double) 0);
											salesRecord.setPaidAmount((float) salesRecord.getNetAmount());
											salesRecord.setBalanceAmount((float) 0);
											String lastUpdatedUserId = Integer.toString(accountReceivables.getLastUpdateUser());
											salesRecord.setLastUpdateUserId(lastUpdatedUserId);
										}

									} else if (Objects.nonNull(accountReceivables.getUpiAmount())) {
										Float upiAmount = Objects.nonNull(salesRecord.getUpiAmount())
												? salesRecord.getUpiAmount()
														: 0;

												if (salesRecord.getCreditNoteAmount() != null) {

													Float paidAmount = (float) (upiAmount + creditAmount);
													String lastUpdatedUserId = Integer.toString(accountReceivables.getLastUpdateUser());
													salesRecord.setLastUpdateUserId(lastUpdatedUserId);
													salesRecord.setUpiAmount(paidAmount);
													salesRecord.setUpiPhoneNo(accountReceivables.getUpiPhoneNo());
													salesRecord.setUpiTransactionId(accountReceivables.getUpiAuthCode());
													double amt = Double.parseDouble(df.format(paidAmount))
															+ Double.parseDouble(df.format(anotherPayAmt));
													if (amt == salesRecord.getNetAmount()) {
														salesRecord.setPaymentStatus("Paid");
														salesRecord.setCreditAmount((double) 0);
														salesRecord.setBalanceAmount((float) 0);
														salesRecord.setPaidAmount((float) amt);
													} else {

														double bal = salesRecord.getBalanceAmount() - salesRecord.getPaidAmount();
														if (bal == 0) {
															salesRecord.setPaymentStatus("Paid");
															salesRecord.setCreditAmount((double) 0);
														} else {
															salesRecord.setPaymentStatus("Partially Paid");
															salesRecord.setCreditAmount((double) bal);
														}
														salesRecord.setBalanceAmount((float) bal);

														if (salesRecord.getCreditNoteAmount() != null) {
															double amtPaid = salesRecord.getCreditNoteAmount() + paidAmount;
															salesRecord.setPaidAmount((float) amtPaid);
														}
													}

												} else {
													Float paidAmount = (float) (upiAmount + creditAmount);
													salesRecord.setPaymentStatus("Paid");
													salesRecord.setUpiAmount((float) paidAmount);
													salesRecord.setUpiPhoneNo(accountReceivables.getUpiPhoneNo());
													salesRecord.setUpiTransactionId(accountReceivables.getUpiAuthCode());
													salesRecord.setCreditAmount((double) 0);
													salesRecord.setPaidAmount((float) paidAmount);
													salesRecord.setBalanceAmount((float) 0);
													String lastUpdatedUserId = Integer.toString(accountReceivables.getLastUpdateUser());
													salesRecord.setLastUpdateUserId(lastUpdatedUserId);
												}

									}
									SalesModel salesData = salesRepository.save(salesRecord);

									if (Objects.nonNull(salesData)) {
										MasterAccountModel masterAccObj = null;
										if (Objects.nonNull(salesData.getCreditAccountNo())) {
											if (Objects.nonNull(salesData.getCreditAccountNo())) {
												masterAccObj = masterAccRepo
														.getDataByMasterCreditNumber(salesData.getCreditAccountNo());
											}
										}
										if (Objects.nonNull(masterAccObj)) {
											Double creditAmountUpdate = 0.0;
											creditAmountUpdate = (double) Double.parseDouble(df.format(creditAmount));

											double amount = (double) Double.parseDouble(
													df.format(creditAmountUpdate + (double) masterAccObj.getCreditLimitLeft()));
											masterAccObj.setCreditLimitLeft((int) amount);
											masterAccRepo.save(masterAccObj);

										}
									}

								}
					}

					log.info("AccountReceivables data with ID : " + accountReceivables.getAccountReceivablesId()
					+ " updated succesfully");
				}

				// if payment is partially paid
				if (accountReceivables.getSourceType().equalsIgnoreCase("Sales Billing")
						&& accountReceivables.getPaymentStatus().equalsIgnoreCase("Partially Paid")) {

					System.out.println("Sales bill with partially paid");
					SalesModel salesRes = salesRepository.getSalesRecordByNo(accountReceivables.getSourceRef().trim());

					salesRes.setBalanceAmount(salesRes.getBalanceAmount() - accountReceivables.getPartialAmt());
					if (accountReceivables.getPaymentType() != null) {
						DecimalFormat df = new DecimalFormat(".##");
						if (accountReceivables.getPaymentType().equals("Card")) {

							salesRes.setCreditCardAmount(
									(salesRes.getCreditCardAmount() != null && salesRes.getCreditCardAmount() > 0)
									? salesRes.getCreditCardAmount() + accountReceivables.getPartialAmt()
									: accountReceivables.getPartialAmt());
							salesRes.setCreditCardNo(salesRes.getCreditCardNo() != null
									? salesRes.getCreditCardNo() + "," + accountReceivables.getCreditCardNo()
									: accountReceivables.getCreditCardNo());
							salesRes.setCreditCardAuthNo(salesRes.getCreditCardAuthNo() != null
									? salesRes.getCreditCardAuthNo() + " " + accountReceivables.getCardAuthCode()
									: accountReceivables.getCardAuthCode());

						} else if (accountReceivables.getPaymentType().equals("Cash")) {

							salesRes.setCashAmount((salesRes.getCashAmount() != null && salesRes.getCashAmount() > 0)
									? salesRes.getCashAmount() + accountReceivables.getPartialAmt()
									: accountReceivables.getPartialAmt());

						} else if ((accountReceivables.getPaymentType().equals("MPesa"))) {

							salesRes.setUpiAmount((salesRes.getUpiAmount() != null && salesRes.getUpiAmount() > 0)
									? salesRes.getUpiAmount() + accountReceivables.getPartialAmt()
									: accountReceivables.getPartialAmt());
							salesRes.setUpiPhoneNo(accountReceivables.getUpiPhoneNo());
							salesRes.setUpiTransactionId(accountReceivables.getUpiAuthCode());

						} else if ((accountReceivables.getPaymentType().equals("Cheque"))) {

							salesRes.setChequeAmount(
									(salesRes.getChequeAmount() != null && salesRes.getChequeAmount() > 0)
									? salesRes.getChequeAmount() + accountReceivables.getPartialAmt()
									: accountReceivables.getPartialAmt());
							LocalDate chequeDt = accountReceivables.getChequeDate();
							salesRes.setChequeDate(chequeDt.toString());
							salesRes.setChequeNumber(salesRes.getChequeNumber() != null
									? salesRes.getChequeNumber() + "," + accountReceivables.getChequeNumber()
									: accountReceivables.getChequeNumber());

						}
						if (accountReceivables.getAmountToBeReceived() != null) {
							salesRes.setCreditAmount((double) Double.parseDouble(
									df.format((salesRes.getCreditAmount() - accountReceivables.getAmountReceived()))));
							salesRes.setBalanceAmount(
									(float) Double.parseDouble(df.format((salesRes.getCreditAmount()))));
							salesRes.setPaidAmount((float) Double
									.parseDouble(df.format((salesRes.getNetAmount() - salesRes.getBalanceAmount()))));
							double bal = salesRes.getBalanceAmount();
							if (bal > 0) {
								salesRes.setPaymentStatus("Partially Paid");
							} else {
								salesRes.setPaymentStatus("Paid");
							}
						} else {
							salesRes.setCreditAmount(salesRes.getCreditAmount() - accountReceivables.getPartialAmt());
							if (salesRes.getBalanceAmount() > 0) {
								salesRes.setPaymentStatus("Partially Paid");

							} else if (salesRes.getBalanceAmount() == 0) {
								salesRes.setPaymentStatus("Paid");
							}
						}
						salesRepository.save(salesRes);

						//update credit limit left
						if (Objects.nonNull(salesRes)) {
							MasterAccountModel masterAccObj = null;
							if (Objects.nonNull(salesRes.getCreditAccountNo())) {
								if (Objects.nonNull(salesRes.getCreditAccountNo())) {
									masterAccObj = masterAccRepo.getDataByMasterCreditNumber(salesRes.getCreditAccountNo());
								}
							}
							if (Objects.nonNull(masterAccObj)) {
								Double creditAmountUpdate = 0.0;
								if (salesRes.getPaymentStatus().equals("Paid")) {
									creditAmountUpdate = (double) Double.parseDouble(df.format(salesRes.getNetAmount()));
								} else {
									creditAmountUpdate = (double) Double.parseDouble(df.format(salesRes.getPaidAmount()));
								}
								double amount = (double) Double.parseDouble(
										df.format(creditAmountUpdate + (double) masterAccObj.getCreditLimitLeft()));
								masterAccObj.setCreditLimitLeft((int) amount);
								masterAccRepo.save(masterAccObj);

							}
						}//End

					}
				}

				// to update credit note amount if it goes for multiple creditnotes
				for (int i = 0; i < accountsReceivables.size(); i++) {
					SalesModel salesRecord = null;
					DecimalFormat df = new DecimalFormat(".##");
					if (Objects.nonNull(accountsReceivables.get(i).getBillRefNo())) {
						salesRecord = accountReceivablesRepository
								.getSalesByBillCode(accountsReceivables.get(i).getBillRefNo().trim());
					} else {
						salesRecord = salesRepository.getSalesRecordById(accountsReceivables.get(i).getSalesBillId());
					}

					if (Objects.nonNull(salesRecord)) {
						if (salesRecord.getPaymentStatus().equals("Paid") && salesRecord.getBalanceAmount() == 0) {
							if (Objects.isNull(accountsReceivables.get(i).getCashAmount())
									&& Objects.isNull(accountsReceivables.get(i).getCreditCardAmount())
									&& Objects.isNull(accountsReceivables.get(i).getChequeAmount())
									&& Objects.isNull(accountsReceivables.get(i).getUpiAmount())) {

								salesRecord.setCreditNoteAmount(
										(double) Double.parseDouble(df.format(salesRecord.getPaidAmount())));

							}
						} else if (Objects.isNull(salesRecord.getSalesCreditRefNo())
								&& salesRecord.getPaymentStatus().equals("Partially Paid")) {
							/*condition in else if is commented*/
							//&& salesRecord.getChequeNumber().contains("DPAR")
							salesRecord.setCreditNoteAmount(null);
						}
						salesRepository.save(salesRecord);
						
					}

				}
			}
		}

		return accountsReceivables;
	}

	@Override
	public List<AccountReceivablesModel> findAllAccountsReceivables() {
		return accountReceivablesRepository.findAllByOrderByLastUpdateTimestampDesc();
	}

	@Override
	public AccountReceivablesModel findAccountReceivablesById(Integer accountReceivablesId) {
		AccountReceivablesModel accountReceivablesRes = getValidAccountsReceivables(accountReceivablesId);
		if (!Objects.nonNull(accountReceivablesRes)) {
			throw new IHealthPharmException(accountReceivablesHelper.getNotFoundAccountReceivablesMessage(),
					HttpStatus.NOT_FOUND);
		}

		log.info("AccountReceivables data with ID : " + accountReceivablesRes.getAccountReceivablesId()
		+ " retrieved succesfully");
		return accountReceivablesRes;
	}

	@Override
	public void deleteAccountReceivablesById(Integer accountReceivablesId) {
		AccountReceivablesModel accountReceivablesRes = accountReceivablesRepository.getOne(accountReceivablesId);
		if (!Objects.nonNull(accountReceivablesRes)) {
			throw new IHealthPharmException(accountReceivablesHelper.getNotFoundAccountReceivablesMessage(),
					HttpStatus.NOT_FOUND);
		}

		log.info("AccountReceivables data with ID : " + accountReceivablesRes.getAccountReceivablesId()
		+ " Deleted succesfully");
		accountReceivablesRepository.delete(accountReceivablesRes);
	}

	@Override
	public void deleteAccountsReceivablesById(Integer[] accountReceivablesIds) {
		AccountReceivablesModel accountReceivablesRes;
		for (int accountReceivables : accountReceivablesIds) {
			accountReceivablesRes = getValidAccountsReceivables(accountReceivables);
			if (!Objects.nonNull(accountReceivablesRes)) {
				throw new IHealthPharmException(accountReceivablesHelper.getNotFoundAccountReceivablesMessage(),
						HttpStatus.NOT_FOUND);
			}
			accountReceivablesRepository.delete(accountReceivablesRes);
			log.info("AccountReceivables data with ID: " + accountReceivablesRes.getAccountReceivablesId()
			+ " deleted succesfully");
		}

	}

	public AccountReceivablesModel getValidAccountsReceivables(Integer accountReceivablesId) {
		AccountReceivablesModel accountReceivablesRes = null;

		try {
			accountReceivablesRes = accountReceivablesRepository.findById(accountReceivablesId).get();
			return accountReceivablesRes;
		} catch (NoSuchElementException noSuchElementException) {
			throw new IHealthPharmException(accountReceivablesHelper.getNotFoundAccountReceivablesMessage(),
					HttpStatus.NOT_FOUND);
		}
	}

	@Override
	public List<SalesModel> getAllBillsByCustomerId(Integer customerId) {
		log.info("given  id :" + customerId);
		List<SalesModel> res = accountReceivablesRepository.getAllBillsByCustomerId(customerId);
		return res;
	}

	@Override
	public List<SalesModel> getAllCustomersByCustomerId(Integer customerId) {
		log.info("given  id :" + customerId);
		List<SalesModel> res = accountReceivablesRepository.getAllCustomersByCustomerId(customerId);
		return res;
	}

	@Override
	public List<AccountReceivablesModel> findAccountReceivablesByBillId(Integer billId) {

		List<AccountReceivablesModel> response = accountReceivablesRepository.getAccountRecievablesBillId(billId);
		return response;
	}

	@Override
	public List<SalesModel> getAllSalesBySearch(String billCode) {
		return accountReceivablesRepository.getSalesBasedOnSalesSearch(billCode);
	}

	@Override
	public List<AccountReceivablesModel> getAllByBillCodeSearch(String billCode, String customerName) {
		return accountReceivablesRepository.getAllAccRecievablesBySearchBillCode(billCode, customerName);
	}

	@Override
	public List<AccountReceivablesModel> getAllAccountPayables() {
		return accountReceivablesRepository.getAllAccountPayables();
	}

	@Override
	public List<AccountReceivablesModel> getAllCustomersBasedonCustomerName(String customerName) {
		return accountReceivablesRepository.getAllCustomersBasedOnName(customerName);
	}

	@Override
	public List<String> findReceiptNobysearchAR(String searchTerm) {
		return accountReceivablesRepository.findReceiptNoBySearch(searchTerm);
	}

	@Override
	public List<String> findallReceiptNoAR() {
		return accountReceivablesRepository.findAllReceiptNoINAR();
	}

	@Override
	public List<AccountReceivablesModel> getAllRecievablesCustomerNameSearch(String customerName) {
		return accountReceivablesRepository.getAllRecievablesCustNames(customerName);
	}

	@Override
	public List<String> findallCustNamesAR() {
		return accountReceivablesRepository.findAllCustomersNamesINAR();
	}

	@Override
	public List<String> findCustNamesbysearchAR(String searchTerm) {
		return accountReceivablesRepository.findCustomersNamesBySearch(searchTerm);
	}


	/**
	 * The Receipts status filter offers "Partially Paid & Pending" as one choice:
	 * every bill that still has money owing. Any other value matches itself only.
	 */
	static final String PARTIALLY_PAID_AND_PENDING = "Partially Paid & Pending";

	static List<String> paymentStatusFilter(String paymentStatus) {
		if (PARTIALLY_PAID_AND_PENDING.equals(paymentStatus)) {
			return Arrays.asList("Partially Paid", "Pending");
		}
		return Collections.singletonList(paymentStatus);
	}

	// popup searches

	@Override
	public List<AccountReceivablesModel> searchInAccRecievables(String paymentStatus, String paymentStartDate,
			String paymentEndDate, String sourceRef, Integer pageNumber, Integer pageSize, String customerName) {

		Pageable limit = new PageRequest(pageNumber, pageSize);

		List<AccountReceivablesModel> response = null;

		if ((paymentStatus != null && !paymentStatus.equals("undefined") && !paymentStatus.equals("null"))
				&& (sourceRef != null && !sourceRef.equals("undefined") && !sourceRef.equals("null"))
				&& ((paymentStartDate != null && !paymentStartDate.equals("undefined")
				&& !paymentStartDate.equals("null"))
						&& (paymentEndDate != null && !paymentEndDate.equals("undefined")
						&& !paymentEndDate.equals("null")))) {
			LocalDate start = LocalDate.parse(paymentStartDate);
			LocalDate end = LocalDate.parse(paymentEndDate);
			response = accountReceivablesRepository.findAccReceivablesSearchByStatusSearchDate(start, end, sourceRef,
					customerName, paymentStatusFilter(paymentStatus), limit);
		}

		else if ((sourceRef != null && !sourceRef.equals("undefined") && !sourceRef.equals("null"))
				&& ((paymentStartDate != null && !paymentStartDate.equals("undefined")
				&& !paymentStartDate.equals("null"))
						&& (paymentEndDate != null && !paymentEndDate.equals("undefined")
						&& !paymentEndDate.equals("null")))) {
			LocalDate start = LocalDate.parse(paymentStartDate);
			LocalDate end = LocalDate.parse(paymentEndDate);
			response = accountReceivablesRepository.findAccRecievableSearchByStatusSearchDateAndSourceRef(start, end,
					sourceRef, customerName, limit);
		} else if (((paymentStartDate != null && !paymentStartDate.equals("undefined")
				&& !paymentStartDate.equals("null"))
				&& (paymentEndDate != null && !paymentEndDate.equals("undefined") && !paymentEndDate.equals("null")))
				&& (paymentStatus != null && !paymentStatus.equals("undefined") && !paymentStatus.equals("null"))) {
			LocalDate start = LocalDate.parse(paymentStartDate);
			LocalDate end = LocalDate.parse(paymentEndDate);
			response = accountReceivablesRepository.findAccRecievablesSearchByStatusSearchDatesAndStatus(paymentStatusFilter(paymentStatus),
					start, end, customerName, limit);
		} else if (((paymentStartDate != null && !paymentStartDate.equals("undefined")
				&& !paymentStartDate.equals("null"))
				&& (paymentEndDate != null && !paymentEndDate.equals("undefined") && !paymentEndDate.equals("null")))) {
			LocalDate start = LocalDate.parse(paymentStartDate);
			LocalDate end = LocalDate.parse(paymentEndDate);
			response = accountReceivablesRepository.findAccRecievableSearchByStatusSearchDates(start, end, customerName,
					limit);
		}

		else if ((paymentStatus != null && !paymentStatus.equals("undefined") && !paymentStatus.equals("null"))
				&& (sourceRef != null && !sourceRef.equals("undefined") && !sourceRef.equals("null"))) {
			response = accountReceivablesRepository.findAccRecievablesSearchByStatusSearchBasedonStatusAndSourceRef(
					paymentStatusFilter(paymentStatus), sourceRef, customerName, limit);
		}

		else if (((paymentStartDate != null && !paymentStartDate.equals("undefined")
				&& !paymentStartDate.equals("null"))
				&& (paymentEndDate != null && !paymentEndDate.equals("undefined") && !paymentEndDate.equals("null")))
				&& (sourceRef != null && !sourceRef.equals("undefined") && !sourceRef.equals("null"))) {
			response = accountReceivablesRepository.findAccRecievablesSearchByStatusSearchStatusAndSourceNumber(
					paymentStatusFilter(paymentStatus), sourceRef, customerName, limit);
		}

		else if ((sourceRef != null && !sourceRef.equals("undefined") && !sourceRef.equals("null"))) {
			response = accountReceivablesRepository.findAccRecievablesSearchBySourceRef(sourceRef, customerName, limit);
		} else if ((paymentStatus != null && !paymentStatus.equals("undefined") && !paymentStatus.equals("null"))) {
			response = accountReceivablesRepository.findAccRecievablesSearchByStatus(paymentStatusFilter(paymentStatus), customerName,
					limit);
		}

		return response;
	}

	@Override
	public Integer searchInAccRecievablesForCount(String paymentStatus, String paymentStartDate, String paymentEndDate,
			String sourceRef, Integer pageNumber, Integer pageSize, String customerName) {
		Integer response = 0;

		if ((paymentStatus != null && !paymentStatus.equals("undefined") && !paymentStatus.equals("null"))
				&& (sourceRef != null && !sourceRef.equals("undefined") && !sourceRef.equals("null"))
				&& ((paymentStartDate != null && !paymentStartDate.equals("undefined")
				&& !paymentStartDate.equals("null"))
						&& (paymentEndDate != null && !paymentEndDate.equals("undefined")
						&& !paymentEndDate.equals("null")))) {
			LocalDate start = LocalDate.parse(paymentStartDate);
			LocalDate end = LocalDate.parse(paymentEndDate);
			response = accountReceivablesRepository.findAccReceivablesSearchByStatusSearchDateCount(start, end,
					sourceRef, customerName, paymentStatusFilter(paymentStatus));
		}

		else if ((sourceRef != null && !sourceRef.equals("undefined") && !sourceRef.equals("null"))
				&& ((paymentStartDate != null && !paymentStartDate.equals("undefined")
				&& !paymentStartDate.equals("null"))
						&& (paymentEndDate != null && !paymentEndDate.equals("undefined")
						&& !paymentEndDate.equals("null")))) {
			LocalDate start = LocalDate.parse(paymentStartDate);
			LocalDate end = LocalDate.parse(paymentEndDate);
			response = accountReceivablesRepository.findAccRecievableSearchByStatusSearchDateAndSourceRefCount(start,
					end, sourceRef, customerName);
		} else if (((paymentStartDate != null && !paymentStartDate.equals("undefined")
				&& !paymentStartDate.equals("null"))
				&& (paymentEndDate != null && !paymentEndDate.equals("undefined") && !paymentEndDate.equals("null")))
				&& (paymentStatus != null && !paymentStatus.equals("undefined") && !paymentStatus.equals("null"))) {
			LocalDate start = LocalDate.parse(paymentStartDate);
			LocalDate end = LocalDate.parse(paymentEndDate);
			response = accountReceivablesRepository
					.findAccRecievablesSearchByStatusSearchDatesAndStatusCount(paymentStatusFilter(paymentStatus), start, end, customerName);
		} else if (((paymentStartDate != null && !paymentStartDate.equals("undefined")
				&& !paymentStartDate.equals("null"))
				&& (paymentEndDate != null && !paymentEndDate.equals("undefined") && !paymentEndDate.equals("null")))) {
			LocalDate start = LocalDate.parse(paymentStartDate);
			LocalDate end = LocalDate.parse(paymentEndDate);
			response = accountReceivablesRepository.findAccRecievableSearchByStatusSearchDatesCount(start, end,
					customerName);
		}

		else if ((paymentStatus != null && !paymentStatus.equals("undefined") && !paymentStatus.equals("null"))
				&& (sourceRef != null && !sourceRef.equals("undefined") && !sourceRef.equals("null"))) {
			response = accountReceivablesRepository
					.findAccRecievablesSearchByStatusSearchBasedonStatusAndSourceRefCount(paymentStatusFilter(paymentStatus), sourceRef,
							customerName);
		} else if (((paymentStartDate != null && !paymentStartDate.equals("undefined")
				&& !paymentStartDate.equals("null"))
				&& (paymentEndDate != null && !paymentEndDate.equals("undefined") && !paymentEndDate.equals("null")))
				&& (sourceRef != null && !sourceRef.equals("undefined") && !sourceRef.equals("null"))) {
			response = accountReceivablesRepository.findAccRecievablesSearchByStatusSearchStatusAndSourceNumberCount(
					paymentStatusFilter(paymentStatus), sourceRef, customerName);
		}

		else if ((sourceRef != null && !sourceRef.equals("undefined") && !sourceRef.equals("null"))) {
			response = accountReceivablesRepository.findAccRecievablesSearchBySourceRefCount(sourceRef, customerName);
		} else if ((paymentStatus != null && !paymentStatus.equals("undefined") && !paymentStatus.equals("null"))) {
			response = accountReceivablesRepository.findAccRecievablesSearchByStatusCount(paymentStatusFilter(paymentStatus), customerName);
		}

		return response;
	}

	@Override
	public List<AccRecievablesCustomerDTO> getAllAccountPayablesData() {
		return accountReceivablesRepository.getAllAccountPayablesData();

	}

	@Override
	public List<AccountReceivablesModel> searchInAccRecievablesForAccounts(String paymentStatus,
			String paymentStartDate, String paymentEndDate, String sourceRef, Integer pageNumber, Integer pageSize,
			String creditNumber) {
		Pageable limit = new PageRequest(pageNumber, pageSize);

		List<AccountReceivablesModel> response = null;

		if ((paymentStatus != null && !paymentStatus.equals("undefined") && !paymentStatus.equals("null"))
				&& (sourceRef != null && !sourceRef.equals("undefined") && !sourceRef.equals("null"))
				&& ((paymentStartDate != null && !paymentStartDate.equals("undefined")
				&& !paymentStartDate.equals("null"))
						&& (paymentEndDate != null && !paymentEndDate.equals("undefined")
						&& !paymentEndDate.equals("null")))) {
			LocalDate start = LocalDate.parse(paymentStartDate);
			LocalDate end = LocalDate.parse(paymentEndDate);
			response = accountReceivablesRepository.findAccReceivablesSearchByStatusSearchDateForAccount(start, end,
					sourceRef, creditNumber, paymentStatusFilter(paymentStatus), limit);
		}

		else if ((sourceRef != null && !sourceRef.equals("undefined") && !sourceRef.equals("null"))
				&& ((paymentStartDate != null && !paymentStartDate.equals("undefined")
				&& !paymentStartDate.equals("null"))
						&& (paymentEndDate != null && !paymentEndDate.equals("undefined")
						&& !paymentEndDate.equals("null")))) {
			LocalDate start = LocalDate.parse(paymentStartDate);
			LocalDate end = LocalDate.parse(paymentEndDate);
			response = accountReceivablesRepository.findAccRecievableSearchByStatusSearchDateAndSourceRefForAcc(start,
					end, sourceRef, creditNumber, limit);
		} else if (((paymentStartDate != null && !paymentStartDate.equals("undefined")
				&& !paymentStartDate.equals("null"))
				&& (paymentEndDate != null && !paymentEndDate.equals("undefined") && !paymentEndDate.equals("null")))
				&& (paymentStatus != null && !paymentStatus.equals("undefined") && !paymentStatus.equals("null"))) {
			LocalDate start = LocalDate.parse(paymentStartDate);
			LocalDate end = LocalDate.parse(paymentEndDate);
			response = accountReceivablesRepository.findAccRecievablesSearchByStatusSearchDatesAndStatusForAcc(
					paymentStatusFilter(paymentStatus), start, end, creditNumber, limit);
		} else if (((paymentStartDate != null && !paymentStartDate.equals("undefined")
				&& !paymentStartDate.equals("null"))
				&& (paymentEndDate != null && !paymentEndDate.equals("undefined") && !paymentEndDate.equals("null")))) {
			LocalDate start = LocalDate.parse(paymentStartDate);
			LocalDate end = LocalDate.parse(paymentEndDate);
			response = accountReceivablesRepository.findAccRecievableSearchByStatusSearchDatesForAcc(start, end,
					creditNumber, limit);
		}

		else if ((paymentStatus != null && !paymentStatus.equals("undefined") && !paymentStatus.equals("null"))
				&& (sourceRef != null && !sourceRef.equals("undefined") && !sourceRef.equals("null"))) {
			response = accountReceivablesRepository
					.findAccRecievablesSearchByStatusSearchBasedonStatusAndSourceRefForAcc(paymentStatusFilter(paymentStatus), sourceRef,
							creditNumber, limit);
		} else if (((paymentStartDate != null && !paymentStartDate.equals("undefined")
				&& !paymentStartDate.equals("null"))
				&& (paymentEndDate != null && !paymentEndDate.equals("undefined") && !paymentEndDate.equals("null")))
				&& (sourceRef != null && !sourceRef.equals("undefined") && !sourceRef.equals("null"))) {
			response = accountReceivablesRepository.findAccRecievablesSearchByStatusSearchStatusAndSourceNumberForAcc(
					paymentStatusFilter(paymentStatus), sourceRef, creditNumber, limit);
		}

		else if ((sourceRef != null && !sourceRef.equals("undefined") && !sourceRef.equals("null"))) {
			response = accountReceivablesRepository.findAccRecievablesSearchBySourceRefForAcc(sourceRef, creditNumber,
					limit);
		} else if ((paymentStatus != null && !paymentStatus.equals("undefined") && !paymentStatus.equals("null"))) {
			response = accountReceivablesRepository.findAccRecievablesSearchByStatusForAcc(paymentStatusFilter(paymentStatus), creditNumber,
					limit);
		}
		return response;
	}

	@Override
	public Integer searchInAccRecievablesForCountForAccounts(String paymentStatus, String paymentStartDate,
			String paymentEndDate, String sourceRef, Integer pageNumber, Integer pageSize, String creditNumber) {
		Integer response = 0;

		if ((paymentStatus != null && !paymentStatus.equals("undefined") && !paymentStatus.equals("null"))
				&& (sourceRef != null && !sourceRef.equals("undefined") && !sourceRef.equals("null"))
				&& ((paymentStartDate != null && !paymentStartDate.equals("undefined")
				&& !paymentStartDate.equals("null"))
						&& (paymentEndDate != null && !paymentEndDate.equals("undefined")
						&& !paymentEndDate.equals("null")))) {
			LocalDate start = LocalDate.parse(paymentStartDate);
			LocalDate end = LocalDate.parse(paymentEndDate);
			response = accountReceivablesRepository.findAccReceivablesSearchByStatusSearchDateCountForAccounts(start,
					end, sourceRef, creditNumber, paymentStatusFilter(paymentStatus));
		}

		else if ((sourceRef != null && !sourceRef.equals("undefined") && !sourceRef.equals("null"))
				&& ((paymentStartDate != null && !paymentStartDate.equals("undefined")
				&& !paymentStartDate.equals("null"))
						&& (paymentEndDate != null && !paymentEndDate.equals("undefined")
						&& !paymentEndDate.equals("null")))) {
			LocalDate start = LocalDate.parse(paymentStartDate);
			LocalDate end = LocalDate.parse(paymentEndDate);
			response = accountReceivablesRepository
					.findAccRecievableSearchByStatusSearchDateAndSourceRefCountForAccount(start, end, sourceRef,
							creditNumber);
		} else if (((paymentStartDate != null && !paymentStartDate.equals("undefined")
				&& !paymentStartDate.equals("null"))
				&& (paymentEndDate != null && !paymentEndDate.equals("undefined") && !paymentEndDate.equals("null")))
				&& (paymentStatus != null && !paymentStatus.equals("undefined") && !paymentStatus.equals("null"))) {
			LocalDate start = LocalDate.parse(paymentStartDate);
			LocalDate end = LocalDate.parse(paymentEndDate);
			response = accountReceivablesRepository.findAccRecievablesSearchByStatusSearchDatesAndStatusCountForAccount(
					paymentStatusFilter(paymentStatus), start, end, creditNumber);
		} else if (((paymentStartDate != null && !paymentStartDate.equals("undefined")
				&& !paymentStartDate.equals("null"))
				&& (paymentEndDate != null && !paymentEndDate.equals("undefined") && !paymentEndDate.equals("null")))) {
			LocalDate start = LocalDate.parse(paymentStartDate);
			LocalDate end = LocalDate.parse(paymentEndDate);
			response = accountReceivablesRepository.findAccRecievableSearchByStatusSearchDatesCountForAccounts(start,
					end, creditNumber);
		}

		else if ((paymentStatus != null && !paymentStatus.equals("undefined") && !paymentStatus.equals("null"))
				&& (sourceRef != null && !sourceRef.equals("undefined") && !sourceRef.equals("null"))) {
			response = accountReceivablesRepository
					.findAccRecievablesSearchByStatusSearchBasedonStatusAndSourceRefCountForAcc(paymentStatusFilter(paymentStatus),
							sourceRef, creditNumber);
		} else if (((paymentStartDate != null && !paymentStartDate.equals("undefined")
				&& !paymentStartDate.equals("null"))
				&& (paymentEndDate != null && !paymentEndDate.equals("undefined") && !paymentEndDate.equals("null")))
				&& (sourceRef != null && !sourceRef.equals("undefined") && !sourceRef.equals("null"))) {
			response = accountReceivablesRepository
					.findAccRecievablesSearchByStatusSearchStatusAndSourceNumberCountForAcc(paymentStatusFilter(paymentStatus), sourceRef,
							creditNumber);
		}

		else if ((sourceRef != null && !sourceRef.equals("undefined") && !sourceRef.equals("null"))) {
			response = accountReceivablesRepository.findAccRecievablesSearchBySourceRefCountForAcc(sourceRef,
					creditNumber);
		} else if ((paymentStatus != null && !paymentStatus.equals("undefined") && !paymentStatus.equals("null"))) {
			response = accountReceivablesRepository.findAccRecievablesSearchByStatusCountForAcc(paymentStatusFilter(paymentStatus),
					creditNumber);
		}

		return response;
	}

	@Override
	public JSONObject doCalculations(List<RecieptMoneyCalDTO> json) {
		Double billPartialAmt = 0.0;
		Double billTotalDebit = 0.0;
		Double billTotalCredit = 0.0;
		JSONObject res = new JSONObject();
		for (int i = 0; i < json.size(); i++) {
			if (Objects.nonNull(json.get(i).getPayment())) {
				if (json.get(i).getPayment().equals("Partial")) {
					if (json.get(i).getPartialAmt() != null && json.get(i).getPartialAmt() > 0) {
						System.out.println(
								"//////////////////////////////////////////////////////////////////////////////////");
						System.out.println(json.get(i).getPartialAmt());
						billPartialAmt += json.get(i).getPartialAmt();

						if (json.get(i).getAmountReceived() != null && json.get(i).getAmountReceived() < 0.0) {
							billTotalDebit += json.get(i).getAmountReceived() != null ? 0
									: -1 * json.get(i).getAmountReceived();
						} else {
							billTotalCredit += json.get(i).getAmountReceived() != null ? json.get(i).getAmountReceived()
									: 0;
						}

						if (json.get(i).getAmountToBeReceived() != null) {
							if (json.get(i).getAmountToBeReceived() < 0.0) {
								billTotalDebit += json.get(i).getAmountToBeReceived() != null ? 0
										: -1 * json.get(i).getPartialAmt();
							} else {
								if (json.get(i).getPayment().equals("Partial")) {
									billTotalCredit += json.get(i).getAmountToBeReceived() != null
											? json.get(i).getPartialAmt()
													: 0;
								} else {
									billTotalCredit += json.get(i).getAmountToBeReceived() != null ? 0 : 0;
								}
							}

						}

					}
				} else if (json.get(i).getPayment().equals("Full")) {
					System.out.println("else ddddfdvdf" + json.get(i).getAmountToBeReceived());
					billPartialAmt += json.get(i).getAmountToBeReceived();
					System.out.println(billPartialAmt + "/lkuhh");
					if (json.get(i).getAmountReceived() != null && json.get(i).getAmountReceived() < 0.0) {
						System.out.println(json.get(i).getAmountReceived() + "/////...,,.,,,,mmmmmm");
						billTotalDebit += json.get(i).getAmountReceived() != null ? 0
								: -1 * json.get(i).getAmountReceived();
					} else {
						System.out.println(json.get(i).getAmountReceived() + "eslecs");
						billTotalCredit += json.get(i).getAmountReceived() != null ? json.get(i).getAmountReceived()
								: 0;
					}

					if (json.get(i).getAmountToBeReceived() != null) {
						if (json.get(i).getAmountToBeReceived() < 0.0) {
							System.out.println(json.get(i).getAmountReceived() + "not nullssc");
							billTotalDebit += json.get(i).getAmountToBeReceived() != null ? 0
									: -1 * json.get(i).getAmountToBeReceived() + json.get(i).getPartialAmt();
						} else {
							if (json.get(i).getPayment().equals("Full")) {
								System.out.println(json.get(i).getAmountReceived() + "full//");
								billTotalCredit += json.get(i).getAmountToBeReceived() != null
										? json.get(i).getAmountToBeReceived()
												: 0;
							} else {
								System.out.println(json.get(i).getAmountReceived() + "{{}{}{}{{{}{{{");
								billTotalCredit += json.get(i).getAmountToBeReceived() != null
										? json.get(i).getAmountToBeReceived() + json.get(i).getPartialAmt()
												: 0;
							}
						}
					}

				}
			}
		}
		DecimalFormat df = new DecimalFormat(".##");
		System.out.println(billPartialAmt + "mntrfgvhj");
		res.put("totalAmount", Double.parseDouble(df.format(billPartialAmt)));
		res.put("totalCredit", Double.parseDouble(df.format(billTotalCredit)));
		res.put("totalBill", Double.parseDouble(df.format(billTotalCredit - billTotalDebit)));

		return res;
	}

}
