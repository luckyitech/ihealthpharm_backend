package com.ihealthpharm.twofactor.dao;

import java.util.Date;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ihealthpharm.twofactor.model.TwoFactorTrustedDeviceModel;

public interface TwoFactorTrustedDeviceRepository extends JpaRepository<TwoFactorTrustedDeviceModel, Integer> {

	/**
	 * Live remembered machines for an account.
	 *
	 * <p>Returns a list rather than one row because the cookie value is hashed:
	 * it cannot be looked up directly, so the caller checks the presented value
	 * against each candidate. An account has a handful of machines at most, so
	 * this stays cheap.
	 */
	@Query("select d from two_factor_trusted_device d"
			+ " where d.employeeCredentialsId = :credentialsId"
			+ "   and d.revokedTimestamp is null"
			+ "   and d.expiresTimestamp > :now"
			+ " order by d.lastUsedTimestamp desc")
	List<TwoFactorTrustedDeviceModel> findTrusted(@Param("credentialsId") Integer credentialsId,
			@Param("now") Date now);

	/** Everything an account has ever trusted, for the "your devices" screen. */
	List<TwoFactorTrustedDeviceModel> findByEmployeeCredentialsIdOrderByTrustedDeviceIdDesc(
			Integer employeeCredentialsId);

	/** Records that a machine was used, without rewriting the rest of the row. */
	@Modifying
	@Query("update two_factor_trusted_device d set d.lastUsedTimestamp = :now,"
			+ " d.lastUpdateTimestamp = :now where d.trustedDeviceId = :deviceId")
	void recordUsed(@Param("deviceId") Integer deviceId, @Param("now") Date now);

	/** Forgets one machine. */
	@Modifying
	@Query("update two_factor_trusted_device d set d.revokedTimestamp = :now,"
			+ " d.revokedReason = :reason, d.lastUpdateTimestamp = :now"
			+ " where d.trustedDeviceId = :deviceId and d.revokedTimestamp is null")
	int revoke(@Param("deviceId") Integer deviceId, @Param("reason") String reason,
			@Param("now") Date now);

	/**
	 * Closes machines whose trust window has run out.
	 *
	 * <p>Revoked rather than deleted: the row records that a machine was trusted
	 * and when, which is worth having when somebody asks why an account was
	 * reachable from a particular PC.
	 */
	@Modifying
	@Query("update two_factor_trusted_device d set d.revokedTimestamp = :now,"
			+ " d.revokedReason = :reason, d.lastUpdateTimestamp = :now"
			+ " where d.revokedTimestamp is null and d.expiresTimestamp < :now")
	int revokeExpired(@Param("reason") String reason, @Param("now") Date now);

	/**
	 * Forgets every machine an account has remembered, returning how many were
	 * live. An administrator reset must do this: leaving thirty-day trusted
	 * machines in place would leave an intruder logged in at the counter, which
	 * is usually the reason the reset was asked for.
	 */
	@Modifying
	@Query("update two_factor_trusted_device d set d.revokedTimestamp = :now,"
			+ " d.revokedReason = :reason, d.lastUpdateTimestamp = :now"
			+ " where d.employeeCredentialsId = :credentialsId and d.revokedTimestamp is null")
	int revokeAllForAccount(@Param("credentialsId") Integer credentialsId,
			@Param("reason") String reason, @Param("now") Date now);
}
