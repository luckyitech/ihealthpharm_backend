package com.ihealthpharm.finance.dto;

import java.io.Serializable;
import java.math.BigDecimal;

import lombok.Data;

/**
 * A credit account offered in the settlement screen's search box.
 *
 * Carries enough for the user to pick the right one without opening it: who is
 * on the account, how many bills are outstanding and what they come to. A
 * pharmacist taking a payment knows the person in front of them, not the
 * account code, and names repeat often enough that the code alone would not
 * settle which account is meant.
 */
@Data
public class CreditAccountSearchDTO implements Serializable {

	private static final long serialVersionUID = 1L;

	/** The credit account code, e.g. DPCR2109200005. */
	private String masterAccountNo;

	/**
	 * Every customer name on the account, comma separated.
	 *
	 * More than one is normal - an account can cover a family or a practice -
	 * and showing them all makes it clear whose bills a payment would settle.
	 */
	private String customerNames;

	private int customerCount;

	private int openBills;

	private BigDecimal outstanding;

}
