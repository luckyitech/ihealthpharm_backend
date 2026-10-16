package com.ihealthpharm.twofactor.service.impl;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Date;
import java.util.List;

import javax.transaction.Transactional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.ihealthpharm.exception.IHealthPharmException;
import com.ihealthpharm.masters.dao.EmployeeCredentialsRepository;
import com.ihealthpharm.masters.model.EmployeeCredentialsModel;
import com.ihealthpharm.twofactor.TwoFactorProperties;
import com.ihealthpharm.twofactor.dao.TwoFactorChallengeRepository;
import com.ihealthpharm.twofactor.dao.TwoFactorResetAuditRepository;
import com.ihealthpharm.twofactor.dao.TwoFactorTrustedDeviceRepository;
import com.ihealthpharm.twofactor.dto.IssuedChallenge;
import com.ihealthpharm.twofactor.dto.TwoFactorResetResult;
import com.ihealthpharm.twofactor.model.TwoFactorChallengeModel;
import com.ihealthpharm.twofactor.model.TwoFactorResetAuditModel;
import com.ihealthpharm.twofactor.model.TwoFactorTrustedDeviceModel;
import com.ihealthpharm.twofactor.service.TwoFactorService;

import lombok.extern.slf4j.Slf4j;

@Service
@Transactional
@Slf4j
public class TwoFactorServiceImpl implements TwoFactorService {

	/** Seeded by the OS; never Math.random, whose output is predictable from a few samples. */
	private static final SecureRandom RANDOM = new SecureRandom();

	@Autowired
	private TwoFactorChallengeRepository challengeRepository;

	@Autowired
	private TwoFactorTrustedDeviceRepository trustedDeviceRepository;

	@Autowired
	private TwoFactorResetAuditRepository resetAuditRepository;

	@Autowired
	private EmployeeCredentialsRepository employeeCredentialsRepository;

	/** The same encoder passwords use, so codes and device tokens are hashed the one agreed way. */
	@Autowired
	private PasswordEncoder passwordEncoder;

	@Autowired
	private TwoFactorProperties properties;

	// ------------------------------------------------------------------
	// Issuing
	// ------------------------------------------------------------------

	@Override
	public IssuedChallenge issueLoginChallenge(EmployeeCredentialsModel credentials) {
		if (!credentials.isTwoFactorActive()) {
			// The caller should not have got here; refuse rather than mail an
			// unverified address.
			throw new IHealthPharmException(
					"This account is not enrolled for two-factor authentication.",
					HttpStatus.BAD_REQUEST);
		}
		return issue(credentials, TwoFactorChallengeModel.PURPOSE_LOGIN,
				credentials.getTwoFactorEmail());
	}

	@Override
	public IssuedChallenge issueEmailVerificationChallenge(EmployeeCredentialsModel credentials,
			String email) {
		String candidate = email == null ? "" : email.trim();
		if (!isPlausibleEmail(candidate)) {
			throw new IHealthPharmException("That does not look like an email address.",
					HttpStatus.BAD_REQUEST);
		}

		// Stored unproven so the confirmation can find it. Nothing is sent to it
		// again until a code comes back, so a typo costs one retry, not the
		// account.
		employeeCredentialsRepository.setUnverifiedTwoFactorEmail(
				credentials.getEmployeeCredentialsId(), candidate);

		return issue(credentials, TwoFactorChallengeModel.PURPOSE_EMAIL_VERIFY, candidate);
	}

	@Override
	public IssuedChallenge issueCaptureHandle(EmployeeCredentialsModel credentials) {
		Date now = new Date();
		Integer credentialsId = credentials.getEmployeeCredentialsId();

		challengeRepository.closeOpenChallenges(credentialsId,
				TwoFactorChallengeModel.PURPOSE_EMAIL_CAPTURE, now);

		TwoFactorChallengeModel handle = new TwoFactorChallengeModel();
		handle.setEmployeeCredentialsId(credentialsId);
		handle.setPurpose(TwoFactorChallengeModel.PURPOSE_EMAIL_CAPTURE);
		handle.setChallengeToken(generateOpaqueToken());
		// No code is sent at this step - there is nowhere to send one yet - so
		// this hash is of a value nobody holds and is never checked. The column
		// is NOT NULL and the row is wanted for its handle, expiry and
		// single-use flag.
		handle.setCodeHash(passwordEncoder.encode(generateOpaqueToken()));
		handle.setExpiresTimestamp(new Date(now.getTime() + properties.handleTtlMillis()));
		handle.setAttemptCount(0);
		handle.setCreationTimeStamp(now);
		handle.setLastUpdateTimestamp(now);
		challengeRepository.save(handle);

		log.info("Two-factor address capture opened for credentials ID {}", credentialsId);
		return new IssuedChallenge(handle.getChallengeToken(), null,
				properties.getHandleTtlSeconds(), true, null, null);
	}

	@Override
	public EmployeeCredentialsModel resolveOpenHandle(String challengeToken, String expectedPurpose) {
		TwoFactorChallengeModel handle = challengeToken == null ? null
				: challengeRepository.findByChallengeToken(challengeToken);

		if (handle == null || !expectedPurpose.equals(handle.getPurpose())
				|| !handle.isOpen(new Date(), properties.getMaxAttempts())) {
			throw new IHealthPharmException("This step has expired. Sign in again.",
					HttpStatus.UNAUTHORIZED);
		}

		return employeeCredentialsRepository.findById(handle.getEmployeeCredentialsId())
				.orElseThrow(() -> new IHealthPharmException("No such account.",
						HttpStatus.NOT_FOUND));
	}

	@Override
	public IssuedChallenge resendChallenge(String challengeToken) {
		TwoFactorChallengeModel previous = challengeToken == null ? null
				: challengeRepository.findByChallengeToken(challengeToken);

		if (previous == null
				|| TwoFactorChallengeModel.PURPOSE_EMAIL_CAPTURE.equals(previous.getPurpose())) {
			throw new IHealthPharmException("Sign in again to request a new code.",
					HttpStatus.UNAUTHORIZED);
		}

		// Expiry is NOT checked: wanting a new code and the old one having
		// expired are the same moment. Only an answered challenge is refused -
		// that login already completed.
		if (previous.getConsumedTimestamp() != null) {
			throw new IHealthPharmException("That code was already used. Sign in again.",
					HttpStatus.UNAUTHORIZED);
		}

		EmployeeCredentialsModel credentials = employeeCredentialsRepository
				.findById(previous.getEmployeeCredentialsId())
				.orElseThrow(() -> new IHealthPharmException("No such account.",
						HttpStatus.NOT_FOUND));

		// Reissued for whatever the original was for, to the same address, so
		// one link on the screen serves a login and an enrolment alike.
		return issue(credentials, previous.getPurpose(), previous.getTargetEmail());
	}

	@Override
	public void consumeHandle(String challengeToken) {
		TwoFactorChallengeModel handle = challengeRepository.findByChallengeToken(challengeToken);
		if (handle != null) {
			challengeRepository.markConsumed(handle.getTwoFactorChallengeId(), new Date());
		}
	}

	/** The one place a challenge is created, whatever it is for. */
	private IssuedChallenge issue(EmployeeCredentialsModel credentials, String purpose,
			String targetEmail) {
		Integer credentialsId = credentials.getEmployeeCredentialsId();
		Date now = new Date();

		enforceIssueRate(credentialsId, now);

		// A new code invalidates the previous one, so an older message left in
		// an inbox stops working.
		challengeRepository.closeOpenChallenges(credentialsId, purpose, now);

		String code = generateCode();

		TwoFactorChallengeModel challenge = new TwoFactorChallengeModel();
		challenge.setEmployeeCredentialsId(credentialsId);
		challenge.setPurpose(purpose);
		challenge.setChallengeToken(generateOpaqueToken());
		challenge.setCodeHash(passwordEncoder.encode(code));
		challenge.setTargetEmail(targetEmail);
		challenge.setExpiresTimestamp(new Date(now.getTime() + properties.codeTtlMillis()));
		challenge.setAttemptCount(0);
		challenge.setCreationTimeStamp(now);
		challenge.setLastUpdateTimestamp(now);
		challengeRepository.save(challenge);

		log.info("Two-factor {} code issued for credentials ID {}", purpose, credentialsId);

		return new IssuedChallenge(challenge.getChallengeToken(), maskEmail(targetEmail),
				properties.getCodeTtlSeconds(),
				TwoFactorChallengeModel.PURPOSE_EMAIL_VERIFY.equals(purpose), code, targetEmail);
	}

	/**
	 * Caps codes per account per hour. Counts consumed and expired challenges
	 * too — otherwise answering a code would hand budget back to someone using
	 * the endpoint to flood a mailbox.
	 */
	private void enforceIssueRate(Integer credentialsId, Date now) {
		Date oneHourAgo = new Date(now.getTime() - 3600_000L);
		long issued = challengeRepository.countIssuedSince(credentialsId, oneHourAgo);
		if (issued >= properties.getMaxCodesPerHour()) {
			log.warn("Two-factor code rate limit hit for credentials ID {} ({} in the last hour)",
					credentialsId, issued);
			throw new IHealthPharmException(
					"Too many codes have been requested for this account. Try again in an hour.",
					HttpStatus.TOO_MANY_REQUESTS);
		}
	}

	// ------------------------------------------------------------------
	// Answering
	// ------------------------------------------------------------------

	/**
	 * {@inheritDoc}
	 *
	 * <p><b>dontRollbackOn is load-bearing.</b> A wrong code increments the
	 * attempt count and then throws, and {@link IHealthPharmException} is a
	 * RuntimeException — so the default behaviour rolled the increment back with
	 * the rest of the transaction. The count stayed at zero however many times
	 * somebody guessed, which meant the attempt limit did not exist: a challenge
	 * accepted unlimited attempts until it expired.
	 *
	 * <p>The attempt limit is what actually stops a six-digit code being walked
	 * through — not the expiry — so losing it silently removed the main defence.
	 * Keep this annotation, and do not add writes to this method that would be
	 * wrong to commit on the failure path.
	 */
	@Override
	@Transactional(dontRollbackOn = IHealthPharmException.class)
	public TwoFactorChallengeModel verifyCode(String challengeToken, String code) {
		TwoFactorChallengeModel challenge = challengeToken == null ? null
				: challengeRepository.findByChallengeToken(challengeToken);

		// One message for every way of not being answerable. Distinguishing
		// "expired" from "already used" from "no such challenge" tells an
		// attacker which of their guesses are worth repeating.
		if (challenge == null || !challenge.isOpen(new Date(), properties.getMaxAttempts())) {
			throw new IHealthPharmException(
					"That code is no longer valid. Request a new one.", HttpStatus.UNAUTHORIZED);
		}

		if (code == null || !passwordEncoder.matches(code.trim(), challenge.getCodeHash())) {
			challengeRepository.recordFailedAttempt(challenge.getTwoFactorChallengeId(), new Date());
			int remaining = properties.getMaxAttempts() - (challenge.getAttemptCount() + 1);
			log.warn("Two-factor code rejected for credentials ID {}, {} attempts left",
					challenge.getEmployeeCredentialsId(), Math.max(remaining, 0));
			throw new IHealthPharmException(
					remaining > 0
							? "That code is not correct. " + remaining
									+ (remaining == 1 ? " attempt left." : " attempts left.")
							: "That code is not correct. Request a new one.",
					HttpStatus.UNAUTHORIZED);
		}

		challengeRepository.markConsumed(challenge.getTwoFactorChallengeId(), new Date());
		return challenge;
	}

	@Override
	public void confirmEmail(TwoFactorChallengeModel verifiedChallenge) {
		if (!TwoFactorChallengeModel.PURPOSE_EMAIL_VERIFY.equals(verifiedChallenge.getPurpose())) {
			throw new IHealthPharmException("That code was not an address confirmation.",
					HttpStatus.BAD_REQUEST);
		}
		employeeCredentialsRepository.confirmTwoFactorEmail(
				verifiedChallenge.getEmployeeCredentialsId());
		log.info("Two-factor address confirmed and enabled for credentials ID {}",
				verifiedChallenge.getEmployeeCredentialsId());
	}

	// ------------------------------------------------------------------
	// Remembered machines
	// ------------------------------------------------------------------

	@Override
	public String rememberDevice(EmployeeCredentialsModel credentials, String label) {
		if (!properties.isDeviceTrustAllowed()) {
			// Remembering is switched off, so every login is challenged.
			return null;
		}
		Date now = new Date();
		String rawToken = generateOpaqueToken();

		TwoFactorTrustedDeviceModel device = new TwoFactorTrustedDeviceModel();
		device.setEmployeeCredentialsId(credentials.getEmployeeCredentialsId());
		device.setDeviceTokenHash(passwordEncoder.encode(rawToken));
		device.setLabel(trimTo(label, 100));
		device.setExpiresTimestamp(new Date(now.getTime() + properties.trustedDeviceMillis()));
		device.setLastUsedTimestamp(now);
		device.setCreationTimeStamp(now);
		device.setLastUpdateTimestamp(now);
		trustedDeviceRepository.save(device);

		log.info("Machine remembered for credentials ID {} for {} days",
				credentials.getEmployeeCredentialsId(), properties.getTrustedDeviceDays());
		return rawToken;
	}

	@Override
	public boolean isDeviceTrusted(EmployeeCredentialsModel credentials, String rawDeviceToken) {
		if (!properties.isDeviceTrustAllowed()
				|| rawDeviceToken == null || rawDeviceToken.trim().isEmpty()) {
			return false;
		}

		// The stored value is a hash, so it cannot be looked up directly. An
		// account has a handful of machines at most, so comparing against each
		// is cheap.
		List<TwoFactorTrustedDeviceModel> trusted = trustedDeviceRepository.findTrusted(
				credentials.getEmployeeCredentialsId(), new Date());

		for (TwoFactorTrustedDeviceModel device : trusted) {
			if (passwordEncoder.matches(rawDeviceToken, device.getDeviceTokenHash())) {
				trustedDeviceRepository.recordUsed(device.getTrustedDeviceId(), new Date());
				return true;
			}
		}
		return false;
	}

	// ------------------------------------------------------------------
	// Administrator reset
	// ------------------------------------------------------------------

	@Override
	public String setWorkEmail(Integer employeeCredentialsId, String email) {
		String candidate = email == null ? "" : email.trim();

		if (candidate.isEmpty()) {
			// Clearing it hands enrolment back to the user at their next sign-in.
			employeeCredentialsRepository.clearTwoFactorEmailOnly(employeeCredentialsId);
			log.info("Work email cleared for credentials ID {}", employeeCredentialsId);
			return null;
		}

		if (!isPlausibleEmail(candidate)) {
			throw new IHealthPharmException("That does not look like an email address.",
					HttpStatus.BAD_REQUEST);
		}

		// Any code already outstanding was sent to the previous address, so it
		// must stop working the moment the destination changes.
		challengeRepository.closeOpenChallenges(employeeCredentialsId,
				TwoFactorChallengeModel.PURPOSE_LOGIN, new Date());

		employeeCredentialsRepository.setWorkEmailByAdministrator(employeeCredentialsId, candidate);
		log.info("Work email set by administrator for credentials ID {}", employeeCredentialsId);
		return candidate;
	}

	@Override
	public void setEnabled(Integer employeeCredentialsId, boolean enabled) {
		employeeCredentialsRepository.setTwoFactorEnabled(employeeCredentialsId,
				enabled ? Character.valueOf('Y') : Character.valueOf('N'));
		log.info("Two-factor {} for credentials ID {}", enabled ? "enabled" : "disabled",
				employeeCredentialsId);
	}

	@Override
	public TwoFactorResetResult resetEnrolment(Integer employeeCredentialsId,
			Integer resetByEmployeeId, String reason) {
		EmployeeCredentialsModel credentials = employeeCredentialsRepository
				.findById(employeeCredentialsId)
				.orElseThrow(() -> new IHealthPharmException("No such account.",
						HttpStatus.NOT_FOUND));

		String previousEmail = credentials.getTwoFactorEmail();
		Date now = new Date();

		// Revoking the remembered machines is the part that must not be
		// forgotten: a reset asked for because an account may be compromised
		// would otherwise leave the intruder logged in at the counter for the
		// rest of the trust window.
		int revoked = trustedDeviceRepository.revokeAllForAccount(employeeCredentialsId,
				TwoFactorTrustedDeviceModel.REVOKED_BY_ADMIN_RESET, now);

		employeeCredentialsRepository.clearTwoFactorEnrolment(employeeCredentialsId);
		challengeRepository.closeOpenChallenges(employeeCredentialsId,
				TwoFactorChallengeModel.PURPOSE_LOGIN, now);

		TwoFactorResetAuditModel audit = new TwoFactorResetAuditModel();
		audit.setEmployeeCredentialsId(employeeCredentialsId);
		audit.setResetByEmployeeId(resetByEmployeeId);
		audit.setReason(trimTo(reason, 200));
		audit.setPreviousEmail(previousEmail);
		audit.setDevicesRevoked(revoked);
		audit.setCreationTimeStamp(now);
		audit.setLastUpdateTimestamp(now);
		resetAuditRepository.save(audit);

		log.warn("Two-factor reset for credentials ID {} by employee {}, {} machines revoked",
				employeeCredentialsId, resetByEmployeeId, revoked);

		return new TwoFactorResetResult(maskEmail(previousEmail), revoked);
	}

	// ------------------------------------------------------------------
	// Helpers
	// ------------------------------------------------------------------

	/**
	 * A zero-padded decimal code of the configured length, uniformly
	 * distributed. {@code nextInt(bound)} rather than {@code nextInt() % bound},
	 * which would make the low codes marginally likelier.
	 */
	private String generateCode() {
		int bound = (int) Math.pow(10, properties.getCodeLength());
		return String.format("%0" + properties.getCodeLength() + "d", RANDOM.nextInt(bound));
	}

	/** 256 bits, URL-safe: used for both challenge handles and device cookies. */
	private String generateOpaqueToken() {
		byte[] bytes = new byte[32];
		RANDOM.nextBytes(bytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}

	/**
	 * {@code vinod@docpharmkenya.com} becomes {@code v••••@docpharmkenya.com} —
	 * enough for the owner to recognise, not enough to tell a stranger whose
	 * mailbox it is.
	 */
	private String maskEmail(String email) {
		if (email == null || !email.contains("@")) {
			return null;
		}
		String local = email.substring(0, email.indexOf('@'));
		String domain = email.substring(email.indexOf('@'));
		return (local.isEmpty() ? "" : local.substring(0, 1)) + "••••" + domain;
	}

	/** Deliberately permissive: the confirmation code is the real check, not a regex. */
	private boolean isPlausibleEmail(String email) {
		int at = email.indexOf('@');
		return email.length() <= 100 && at > 0 && email.indexOf('.', at) > at + 1
				&& !email.endsWith(".") && !email.contains(" ");
	}

	private String trimTo(String value, int max) {
		if (value == null) {
			return null;
		}
		String trimmed = value.trim();
		return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
	}
}
