package com.ihealthpharm.finance.dto;

import java.io.Serializable;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * What the signed-in user is allowed to do on the credit settlement screen.
 *
 * The screen asks for this rather than reading the permissions array itself,
 * because that array is positional: it carries the permission as a bare id with
 * no name, and a user who has not been granted every permission gets a shorter
 * array in which a fixed index points at the wrong entry, or past the end.
 */
@Data
@AllArgsConstructor
public class CreditSettlementAccessDTO implements Serializable {

	private static final long serialVersionUID = 1L;

	/** Whether the Credit Settlement tab should be shown at all. */
	private boolean canSettle;

	/** Whether reversing an applied settlement is offered. */
	private boolean canReverse;

}
