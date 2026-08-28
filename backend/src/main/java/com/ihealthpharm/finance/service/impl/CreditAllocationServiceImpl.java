package com.ihealthpharm.finance.service.impl;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import javax.transaction.Transactional;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.ihealthpharm.exception.IHealthPharmException;
import com.ihealthpharm.finance.dao.OpenBillRepository;
import com.ihealthpharm.finance.dto.CreditAllocationPlanDTO;
import com.ihealthpharm.finance.dto.CreditAllocationRequestDTO;
import com.ihealthpharm.finance.dto.OpenBillDTO;
import com.ihealthpharm.finance.service.CreditAllocationEngine;
import com.ihealthpharm.finance.service.CreditAllocationService;

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

	@Autowired
	OpenBillRepository openBillRepository;

	@Autowired
	CreditAllocationEngine creditAllocationEngine;

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
		if (value instanceof Date) {
			return ((Date) value).toLocalDate();
		}
		if (value instanceof java.util.Date) {
			return new Date(((java.util.Date) value).getTime()).toLocalDate();
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
