package com.ihealthpharm.config.jwt;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Date;

import org.junit.Before;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;

/**
 * The idle sign-out rests on two things the server must get right: a token is
 * refused once it is older than the configured period, and renewing a token
 * starts that period again.
 */
public class JwtTokenUtilTest {

	private static final String SECRET = "unit-test-signing-secret-not-used-anywhere-else";
	private static final long IDLE_TIMEOUT_SECONDS = 900L;
	private static final long MILLIS_PER_SECOND = 1000L;
	private static final String USER_NAME = "test.user";

	private JwtTokenUtil jwtTokenUtil;
	private JwtUserDetails user;

	@Before
	public void setUp() {
		jwtTokenUtil = new JwtTokenUtil();
		ReflectionTestUtils.setField(jwtTokenUtil, "secret", SECRET);
		ReflectionTestUtils.setField(jwtTokenUtil, "expiration", IDLE_TIMEOUT_SECONDS);
		user = new JwtUserDetails(1, USER_NAME, "irrelevant", "ROLE_USER_2");
	}

	private String tokenIssued(long secondsAgo, long lifetimeSeconds) {
		Date issuedAt = new Date(System.currentTimeMillis() - secondsAgo * MILLIS_PER_SECOND);
		Date expiresAt = new Date(issuedAt.getTime() + lifetimeSeconds * MILLIS_PER_SECOND);
		return Jwts.builder().setSubject(USER_NAME).setIssuedAt(issuedAt).setExpiration(expiresAt)
				.signWith(SignatureAlgorithm.HS512, SECRET).compact();
	}

	@Test
	public void aFreshlyIssuedTokenIsAccepted() {
		assertTrue(jwtTokenUtil.validateToken(jwtTokenUtil.generateToken(user), user));
	}

	@Test
	public void aTokenIsIssuedForTheConfiguredPeriod() {
		String token = jwtTokenUtil.generateToken(user);
		long lifetimeMillis = jwtTokenUtil.getExpirationDateFromToken(token).getTime()
				- jwtTokenUtil.getIssuedAtDateFromToken(token).getTime();
		assertTrue(lifetimeMillis == IDLE_TIMEOUT_SECONDS * MILLIS_PER_SECOND);
	}

	@Test
	public void aTokenStillInsideThePeriodIsAccepted() {
		assertTrue(jwtTokenUtil.validateToken(tokenIssued(IDLE_TIMEOUT_SECONDS - 60, IDLE_TIMEOUT_SECONDS), user));
	}

	/** The old default was about 35 days; such a token must stop working at once. */
	@Test
	public void aTokenIssuedToLastLongerThanThePeriodIsRefused() {
		long oldDefaultSeconds = 3000000L;
		assertFalse(jwtTokenUtil.validateToken(tokenIssued(60, oldDefaultSeconds), user));
	}

	@Test
	public void renewingATokenStartsThePeriodAgain() {
		String nearlyExpired = tokenIssued(IDLE_TIMEOUT_SECONDS - 60, IDLE_TIMEOUT_SECONDS);
		String renewed = jwtTokenUtil.refreshToken(nearlyExpired);

		long remainingMillis = jwtTokenUtil.getExpirationDateFromToken(renewed).getTime() - System.currentTimeMillis();
		assertTrue(remainingMillis > (IDLE_TIMEOUT_SECONDS - 5) * MILLIS_PER_SECOND);
		assertTrue(jwtTokenUtil.validateToken(renewed, user));
	}

	@Test
	public void aTokenPastThePeriodCannotBeRenewed() {
		String expired = tokenIssued(IDLE_TIMEOUT_SECONDS + 60, IDLE_TIMEOUT_SECONDS);
		try {
			jwtTokenUtil.canTokenBeRefreshed(expired);
		} catch (io.jsonwebtoken.ExpiredJwtException expected) {
			return;
		}
		org.junit.Assert.fail("An expired token must not be renewable");
	}
}
