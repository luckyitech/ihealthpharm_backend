package com.ihealthpharm.finance.helper;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import com.ihealthpharm.exception.IHealthPharmException;
import com.ihealthpharm.finance.dao.CreditSettlementAccessRepository;
import com.ihealthpharm.finance.dto.CreditSettlementAccessDTO;

import lombok.extern.slf4j.Slf4j;

/**
 * The single place that decides who may settle credit.
 *
 * Both the screen and the endpoints ask this class, so what the user is shown
 * and what the server will actually allow cannot drift apart. The PIN check
 * that already guards these endpoints proves who is calling; this proves they
 * are allowed to, which is a separate question and was previously unasked.
 *
 * Permissions are granted through the existing Master &gt; Employee screen. There
 * is deliberately no role that bypasses this: an administrator who should
 * settle credit is granted the permission there like anyone else, so there is
 * one mechanism to audit rather than two.
 */
@Component
@Slf4j
public class CreditSettlementAccessGuard {

	/** Must match the ACCESS_NAME values inserted by credit_allocation_schema.sql. */
	public static final String PERMISSION_SETTLE = "Credit Settlement";
	public static final String PERMISSION_REVERSE = "Credit Settlement Reversal";

	@Autowired
	CreditSettlementAccessRepository creditSettlementAccessRepository;

	/** What to show the user, asked for once when the screen loads. */
	public CreditSettlementAccessDTO describe(String userName) {
		return new CreditSettlementAccessDTO(holds(userName, PERMISSION_SETTLE),
				holds(userName, PERMISSION_REVERSE));
	}

	/** Raises unless the user may settle credit. */
	public void requireSettle(String userName) {
		require(userName, PERMISSION_SETTLE);
	}

	/** Raises unless the user may reverse a settlement. */
	public void requireReverse(String userName) {
		require(userName, PERMISSION_REVERSE);
	}

	private void require(String userName, String permission) {
		if (!holds(userName, permission)) {
			log.warn("User {} was refused: no '{}' permission", userName, permission);
			throw new IHealthPharmException(
					"You do not have permission to do this. Ask an administrator to grant you '"
							+ permission + "'.",
					HttpStatus.FORBIDDEN);
		}
	}

	private boolean holds(String userName, String permission) {
		// An unauthenticated caller should never reach here, but a null name would
		// otherwise be handed to the query as a value that matches nothing and be
		// reported as an ordinary refusal.
		if (userName == null || userName.trim().isEmpty()) {
			throw new IHealthPharmException("Not signed in.", HttpStatus.UNAUTHORIZED);
		}

		return creditSettlementAccessRepository.countPermission(userName.trim(), permission) > 0;
	}

}
