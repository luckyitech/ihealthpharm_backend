package com.ihealthpharm.twofactor.dao;

import java.util.Date;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ihealthpharm.twofactor.model.TwoFactorChallengeModel;

public interface TwoFactorChallengeRepository extends JpaRepository<TwoFactorChallengeModel, Integer> {

	/**
	 * The challenge a browser is answering. {@code CHALLENGE_TOKEN} is unique,
	 * so this is at most one row.
	 */
	TwoFactorChallengeModel findByChallengeToken(String challengeToken);

	/**
	 * Open challenges for an account, newest first. Used to reuse the live
	 * challenge on a resend rather than issuing a second one, and to count what
	 * is outstanding when rate limiting.
	 */
	@Query("select c from two_factor_challenge c"
			+ " where c.employeeCredentialsId = :credentialsId"
			+ "   and c.purpose = :purpose"
			+ "   and c.consumedTimestamp is null"
			+ "   and c.expiresTimestamp > :now"
			+ " order by c.twoFactorChallengeId desc")
	List<TwoFactorChallengeModel> findOpen(@Param("credentialsId") Integer credentialsId,
			@Param("purpose") String purpose, @Param("now") Date now);

	/**
	 * How many codes have actually been SENT to an account since a cut-off,
	 * whatever became of them.
	 *
	 * <p>Counts consumed and expired challenges — otherwise answering a code
	 * would hand the budget back to someone using the endpoint to flood a
	 * mailbox.
	 *
	 * <p>Excludes {@code EMAIL_CAPTURE}, which is a handle rather than a code:
	 * it mails nothing, so it must not consume a budget that exists to protect
	 * a mailbox. Counting it meant that simply reaching the "enter your address"
	 * screen five times locked the account out of enrolling at all — and reaching
	 * that screen is just logging in, which a user may do repeatedly before
	 * they have an address set up.
	 */
	@Query("select count(c) from two_factor_challenge c"
			+ " where c.employeeCredentialsId = :credentialsId"
			+ "   and c.purpose <> com.ihealthpharm.twofactor.model.TwoFactorChallengeModel.PURPOSE_EMAIL_CAPTURE"
			+ "   and c.creationTimeStamp > :since")
	long countIssuedSince(@Param("credentialsId") Integer credentialsId, @Param("since") Date since);

	/**
	 * Records a failed attempt without reloading and saving the whole row, so a
	 * wrong code cannot overwrite anything else on it.
	 */
	@Modifying
	@Query("update two_factor_challenge c set c.attemptCount = c.attemptCount + 1,"
			+ " c.lastUpdateTimestamp = :now where c.twoFactorChallengeId = :challengeId")
	void recordFailedAttempt(@Param("challengeId") Integer challengeId, @Param("now") Date now);

	/** Marks a challenge used. Single use is what stops a code being replayed. */
	@Modifying
	@Query("update two_factor_challenge c set c.consumedTimestamp = :now,"
			+ " c.lastUpdateTimestamp = :now where c.twoFactorChallengeId = :challengeId")
	void markConsumed(@Param("challengeId") Integer challengeId, @Param("now") Date now);

	/**
	 * Closes every open challenge for an account. Issuing a new code invalidates
	 * the previous one, so an old code left in an inbox cannot still be used.
	 */
	@Modifying
	@Query("update two_factor_challenge c set c.consumedTimestamp = :now,"
			+ " c.lastUpdateTimestamp = :now"
			+ " where c.employeeCredentialsId = :credentialsId"
			+ "   and c.purpose = :purpose"
			+ "   and c.consumedTimestamp is null")
	int closeOpenChallenges(@Param("credentialsId") Integer credentialsId,
			@Param("purpose") String purpose, @Param("now") Date now);

	/**
	 * Housekeeping: challenges that are long past answering. Keeping them serves
	 * no purpose and they accumulate at one row per login.
	 */
	@Modifying
	@Query("delete from two_factor_challenge c where c.expiresTimestamp < :before")
	int deleteExpiredBefore(@Param("before") Date before);
}
