package com.ihealthpharm.twofactor.service.impl;

import java.util.Date;

import javax.transaction.Transactional;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.ihealthpharm.twofactor.TwoFactorProperties;
import com.ihealthpharm.twofactor.dao.TwoFactorChallengeRepository;
import com.ihealthpharm.twofactor.dao.TwoFactorTrustedDeviceRepository;
import com.ihealthpharm.twofactor.model.TwoFactorTrustedDeviceModel;

import lombok.extern.slf4j.Slf4j;

/**
 * Clears out two-factor rows that can no longer do anything.
 *
 * <p>Without this the challenge table grows by one row per login, for ever.
 * Ten people signing in daily is a few thousand rows a year — not a crisis, but
 * rows holding BCrypt hashes of codes that stopped being answerable months ago
 * have no reason to exist, and an unbounded table eventually becomes somebody's
 * problem at an inconvenient moment.
 *
 * <p>Nothing here deletes anything still usable: challenges must be past their
 * expiry by the full retention window, and trusted devices must already have
 * expired. The reset audit is never touched — it is the record of who removed
 * somebody's second factor, and its whole value is that it is kept.
 */
@Component
@Slf4j
public class TwoFactorHousekeepingJob {

	@Autowired
	private TwoFactorChallengeRepository challengeRepository;

	@Autowired
	private TwoFactorTrustedDeviceRepository trustedDeviceRepository;

	@Autowired
	private TwoFactorProperties properties;

	/**
	 * Runs daily. The initial delay keeps it away from startup, when the
	 * application has better things to do than housekeeping.
	 */
	@Scheduled(fixedDelayString = "${twofactor.housekeeping-interval-ms:86400000}",
			initialDelayString = "${twofactor.housekeeping-initial-delay-ms:300000}")
	@Transactional
	public void sweep() {
		try {
			Date cutoff = new Date(System.currentTimeMillis()
					- properties.getChallengeRetentionDays() * 24L * 60L * 60L * 1000L);

			int challenges = challengeRepository.deleteExpiredBefore(cutoff);

			// Expired devices are revoked rather than deleted: the row says a
			// machine was trusted and when, which is worth keeping when
			// somebody asks why an account was reachable from a given PC.
			int devices = trustedDeviceRepository.revokeExpired(
					TwoFactorTrustedDeviceModel.REVOKED_BY_EXPIRY, new Date());

			if (challenges > 0 || devices > 0) {
				log.info("Two-factor housekeeping: {} spent challenges removed, {} lapsed devices closed",
						challenges, devices);
			}
		} catch (Exception e) {
			// Housekeeping failing must never take the application down with it.
			log.error("Two-factor housekeeping run failed: {}", ExceptionUtils.getStackTrace(e));
		}
	}
}
