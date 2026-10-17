package com.ihealthpharm.twofactor.controller;

import static org.springframework.http.HttpStatus.OK;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ihealthpharm.commons.BaseDto;
import com.ihealthpharm.twofactor.dao.TwoFactorResetAuditRepository;
import com.ihealthpharm.twofactor.dto.TwoFactorResetRequest;
import com.ihealthpharm.twofactor.dto.TwoFactorResetResult;
import com.ihealthpharm.twofactor.model.TwoFactorResetAuditModel;
import com.ihealthpharm.twofactor.service.TwoFactorService;

import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * Administering other people's two-factor enrolment, from
 * Master &rarr; Employee &rarr; Access.
 *
 * <p>Unlike the login endpoints, everything here requires a session: these are
 * actions one person takes against another's account.
 *
 * <p><b>Who may call this is a management decision, not a developer's.</b> It
 * is deliberately not wired to a hardcoded role — the working agreement is that
 * role and permission assignment happens on the existing Employee screen. Note
 * while assigning it that whoever holds it can remove the second factor from
 * any account, including those that can move money.
 */
@RestController
@CrossOrigin
@Slf4j
public class TwoFactorAdminController {

	@Autowired
	private TwoFactorService twoFactorService;

	@Autowired
	private TwoFactorResetAuditRepository resetAuditRepository;

	/**
	 * Clears an account's enrolment so the user can enrol again.
	 *
	 * <p>Serves both reasons someone needs this: they have lost access to the
	 * mailbox, or they want to change which address receives their codes. One
	 * action covers both, because the remedy is identical — let them enrol
	 * again. The administrator supplies no address, so nothing is ever set
	 * without the person proving they can read it.
	 *
	 * <p>It also revokes every machine the account had remembered. That is not
	 * incidental: a reset is often asked for <i>because</i> an account may be
	 * compromised, and leaving trusted machines alive would keep the intruder
	 * signed in at the counter for the rest of the trust window.
	 *
	 * <p><b>The caller must have verified the person face to face or by known
	 * voice.</b> No code can enforce that: an attacker who already has the
	 * password only needs an administrator to believe "I've lost my email" to
	 * take the account permanently. The audit row is what makes that reviewable
	 * afterwards, which is why the reason is recorded.
	 */
	@PutMapping("update/twofactor/reset")
	public ResponseEntity<BaseDto<TwoFactorResetResult>> resetTwoFactor(
			@RequestBody TwoFactorResetRequest request,
			@RequestParam(value = "resetByEmployeeId", required = false) Integer resetByEmployeeId) {

		TwoFactorResetResult result = twoFactorService.resetEnrolment(
				request.getEmployeeCredentialsId(), resetByEmployeeId, request.getReason());

		String message = result.getDevicesRevoked() > 0
				? "Two-factor reset. " + result.getDevicesRevoked()
						+ " remembered computer(s) signed out. The user will set up a new address at their next sign-in."
				: "Two-factor reset. The user will set up a new address at their next sign-in.";

		return new BaseDto<>(result, message, OK).respond();
	}

	/**
	 * Sets or clears the work email an account's sign-in codes go to.
	 *
	 * <p>Live immediately: codes go to the new address from the next sign-in and
	 * the user confirms nothing. Clearing it hands enrolment back to the user.
	 *
	 * <p>This is the only place an administrator writes that address. It is a
	 * separate endpoint rather than a field on the employee save, because that
	 * save posts a whole credentials row and would null the two-factor columns
	 * on every unrelated edit.
	 *
	 * <p>Note what it does <b>not</b> touch: {@code employee.EMAIL_ID}, the
	 * personnel address. That stays HR data and is never used for signing in.
	 */
	@PutMapping("update/twofactor/workemail")
	public ResponseEntity<BaseDto<String>> setWorkEmail(
			@RequestParam("employeeCredentialsId") Integer employeeCredentialsId,
			@RequestParam(value = "email", required = false) String email) {

		String stored = twoFactorService.setWorkEmail(employeeCredentialsId, email);

		return new BaseDto<>(stored,
				stored == null
						? "Work email cleared. This person will choose their own at their next sign-in."
						: "Work email saved. Sign-in codes will go to " + stored + " from their next sign-in.",
				OK).respond();
	}

	/**
	 * Switches the challenge on or off for one account.
	 *
	 * <p>Turning it <b>on</b> does not enrol anybody: it means "this account
	 * must use a code", and the user supplies and proves their address at their
	 * next sign-in. That is how the rollout proceeds one person at a time.
	 *
	 * <p>Turning it <b>off</b> leaves the stored address alone, so switching it
	 * back on does not make the user enrol again. Use the reset for that.
	 *
	 * <p>Written with a targeted update rather than a full-row save, because the
	 * Employee screen posts a whole credentials row without these fields and
	 * would otherwise null them.
	 */
	@PutMapping("update/twofactor/enabled")
	public ResponseEntity<BaseDto<String>> setTwoFactorEnabled(
			@RequestParam("employeeCredentialsId") Integer employeeCredentialsId,
			@RequestParam("enabled") boolean enabled) {

		twoFactorService.setEnabled(employeeCredentialsId, enabled);

		return new BaseDto<>(enabled ? "ENABLED" : "DISABLED",
				enabled
						? "Two-factor turned on. This person will set up their email address at their next sign-in."
						: "Two-factor turned off for this account.",
				OK).respond();
	}

	/**
	 * Every reset an account has had.
	 *
	 * <p>The permission is only safe to grant because its use can be reviewed;
	 * this is what makes that possible, so it is part of the feature rather than
	 * a later nicety.
	 */
	@GetMapping("get/twofactor/resethistory")
	public ResponseEntity<BaseDto<List<TwoFactorResetAuditModel>>> resetHistory(
			@RequestParam("employeeCredentialsId") Integer employeeCredentialsId) {

		List<TwoFactorResetAuditModel> history = resetAuditRepository
				.findByEmployeeCredentialsIdOrderByTwoFactorResetIdDesc(employeeCredentialsId);

		return new BaseDto<>(history, "Two-factor reset history", OK).respond();
	}
}
