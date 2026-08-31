package com.ihealthpharm.finance.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import javax.persistence.EntityManager;
import javax.transaction.Transactional;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.junit4.SpringRunner;

import com.ihealthpharm.exception.IHealthPharmException;
import com.ihealthpharm.finance.dto.CreditSettlementAccessDTO;
import com.ihealthpharm.finance.helper.CreditSettlementAccessGuard;

/**
 * Exercises the permission gate against the configured database.
 *
 * The query it rests on joins three tables by column name, and a wrong name
 * there fails at runtime rather than at compile time, so it is checked here
 * against the real schema rather than a mock. Each test grants inside a
 * transaction that is rolled back, so no permission is left behind.
 *
 * Named *IT so it is not picked up by the default unit test run.
 */
@RunWith(SpringRunner.class)
@SpringBootTest
@Transactional
public class CreditSettlementAccessGuardIT {

	/** The local development account; the tests grant and revoke its permissions themselves. */
	private static final String USER_NAME = "Lucky201";

	private static final String UNKNOWN_USER = "no-such-user-9f3c1a";

	@Autowired
	private CreditSettlementAccessGuard creditSettlementAccessGuard;

	@Autowired
	private EntityManager entityManager;

	/**
	 * Starts every test from no settlement permissions at all.
	 *
	 * Without this the tests would depend on whatever happens to be granted in
	 * the database they run against - the local development account is granted
	 * both so the screen can be used - and would pass or fail according to
	 * ambient state rather than the behaviour under test. The surrounding
	 * transaction is rolled back, so the real grants survive.
	 */
	@Before
	public void revokeSettlementPermissions() {
		entityManager.createNativeQuery(
				"DELETE ea FROM employee_access ea "
				+ "JOIN pharma_access pa ON pa.PHARMA_ACCESS_ID = ea.PHARMA_ACCESS_ID "
				+ "JOIN employee_credentials ec ON ec.EMPLOYEE_ID = ea.EMPLOYEE_ID "
				+ "WHERE ec.USER_NM = :userName AND pa.ACCESS_NAME IN (:settle, :reverse)")
				.setParameter("userName", USER_NAME)
				.setParameter("settle", CreditSettlementAccessGuard.PERMISSION_SETTLE)
				.setParameter("reverse", CreditSettlementAccessGuard.PERMISSION_REVERSE)
				.executeUpdate();
		entityManager.flush();
	}

	/**
	 * Grants a permission to the user for the duration of the test.
	 *
	 * Written natively for the same reason the guard's own query is: employee_access
	 * holds the permission as a plain id with no mapped relation to join over.
	 */
	private void grant(String accessName) {
		int granted = entityManager.createNativeQuery(
				"INSERT INTO employee_access (EMPLOYEE_ID, PHARMA_ACCESS_ID, ACTIVE_S, CREATION_TS, CREATION_USER_ID) "
				+ "SELECT ec.EMPLOYEE_ID, pa.PHARMA_ACCESS_ID, 'Y', NOW(), 'test' "
				+ "FROM employee_credentials ec, pharma_access pa "
				+ "WHERE ec.USER_NM = :userName AND pa.ACCESS_NAME = :accessName")
				.setParameter("userName", USER_NAME)
				.setParameter("accessName", accessName)
				.executeUpdate();

		assertEquals("expected exactly one grant - is the permission missing from pharma_access?",
				1, granted);
		// The guard reads through its own query, so the insert has to reach the
		// database before it will be seen.
		entityManager.flush();
	}

	@Test
	public void permissionIsRefusedUntilItIsGranted() {
		CreditSettlementAccessDTO access = creditSettlementAccessGuard.describe(USER_NAME);

		assertNotNull(access);
		assertFalse("no settle permission has been granted yet", access.isCanSettle());
		assertFalse("no reversal permission has been granted yet", access.isCanReverse());
	}

	@Test
	public void grantingSettleAllowsSettlingButNotReversing() {
		grant(CreditSettlementAccessGuard.PERMISSION_SETTLE);

		CreditSettlementAccessDTO access = creditSettlementAccessGuard.describe(USER_NAME);

		assertTrue("settle was granted", access.isCanSettle());
		assertFalse("reversal is a separate permission and was not granted", access.isCanReverse());

		// Should not raise.
		creditSettlementAccessGuard.requireSettle(USER_NAME);
	}

	@Test
	public void grantingReverseAllowsReversing() {
		grant(CreditSettlementAccessGuard.PERMISSION_REVERSE);

		assertTrue(creditSettlementAccessGuard.describe(USER_NAME).isCanReverse());

		creditSettlementAccessGuard.requireReverse(USER_NAME);
	}

	@Test
	public void refusalIsForbiddenRatherThanSilent() {
		try {
			creditSettlementAccessGuard.requireSettle(USER_NAME);
			fail("expected a refusal for a user without the permission");
		} catch (IHealthPharmException expected) {
			assertEquals(HttpStatus.FORBIDDEN, expected.getStatus());
		}
	}

	@Test
	public void unknownUserHoldsNothing() {
		CreditSettlementAccessDTO access = creditSettlementAccessGuard.describe(UNKNOWN_USER);

		assertFalse(access.isCanSettle());
		assertFalse(access.isCanReverse());
	}

	@Test
	public void missingUserNameIsRejectedAsUnauthenticated() {
		try {
			creditSettlementAccessGuard.describe(null);
			fail("expected a null user name to be rejected");
		} catch (IHealthPharmException expected) {
			assertEquals(HttpStatus.UNAUTHORIZED, expected.getStatus());
		}
	}

}
