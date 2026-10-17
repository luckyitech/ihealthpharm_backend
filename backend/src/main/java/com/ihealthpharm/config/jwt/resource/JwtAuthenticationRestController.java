package com.ihealthpharm.config.jwt.resource;

import static org.springframework.http.HttpStatus.OK;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import com.ihealthpharm.commons.BaseDto;
import com.ihealthpharm.config.jwt.JwtTokenUtil;
import com.ihealthpharm.config.jwt.JwtUserDetails;
import com.ihealthpharm.exception.IHealthPharmException;
import com.ihealthpharm.masters.dto.EmployeePharmacyRoleDTO;
import com.ihealthpharm.masters.model.EmployeeAccessModel;
import com.ihealthpharm.masters.model.EmployeeCredentialsModel;
import com.ihealthpharm.masters.model.EmployeePharmacyRoleModel;
import com.ihealthpharm.masters.model.PharmacyModel;
import com.ihealthpharm.masters.service.EmployeeAccessService;
import com.ihealthpharm.masters.service.EmployeeCredentialsService;
import com.ihealthpharm.masters.service.EmployeePharmacyRoleService;
import com.ihealthpharm.twofactor.dto.EmailCaptureRequest;
import com.ihealthpharm.twofactor.dto.IssuedChallenge;
import com.ihealthpharm.twofactor.dto.TwoFactorCodeRequest;
import com.ihealthpharm.twofactor.dto.TwoFactorStepResponse;
import com.ihealthpharm.twofactor.model.TwoFactorChallengeModel;
import com.ihealthpharm.twofactor.service.TwoFactorMailService;
import com.ihealthpharm.twofactor.service.TwoFactorService;

@RestController
/*
 * The trusted-device cookie is only sent if the browser is allowed to send
 * credentials, and a browser refuses credentialed responses that answer with
 * "Access-Control-Allow-Origin: *" - which is what a bare @CrossOrigin sends.
 * So the origins are named.
 *
 * This matters in development only. In production IIS serves the Angular files
 * and proxies /ihealthpharmservice to this application, so the browser sees one
 * origin and no CORS is involved at all.
 */
@CrossOrigin(origins = {
		"${twofactor.allowed-origin-dev:http://localhost:4200}",
		"${twofactor.allowed-origin-live:https://ipharmke.com}" },
		allowCredentials = "true")
public class JwtAuthenticationRestController {

	@Value("${jwt.http.request.header}")
	private String tokenHeader;

	@Autowired
	private AuthenticationManager authenticationManager;

	@Autowired
	private JwtTokenUtil jwtTokenUtil;

	@Autowired
	private UserDetailsService jwtInMemoryUserDetailsService;

	@Autowired
	EmployeeCredentialsService employeeCredentialsService;
	
	@Autowired
	EmployeeAccessService employeeAccessService;
	
	@Autowired
	EmployeePharmacyRoleService employeePharmacyRoleService;

	@Autowired
	TwoFactorService twoFactorService;

	@Autowired
	TwoFactorMailService twoFactorMailService;

	@Autowired
	com.ihealthpharm.twofactor.TwoFactorProperties twoFactorProperties;

	/** Name of the cookie that remembers a machine. */
	private static final String TRUSTED_DEVICE_COOKIE = "ihp_td";
	
	/**
	 * Step one: the password.
	 *
	 * <p>Returns a session token exactly as it always did when the account is
	 * not enrolled for two-factor, or is signing in on a machine it already
	 * trusts, so nothing changes for anyone not switched on. Otherwise it
	 * returns a {@link TwoFactorStepResponse} carrying <b>no token</b>, and the
	 * login is only finished by {@link #verifyTwoFactorCode} or
	 * {@link #confirmTwoFactorEmail}.
	 *
	 * <p>A correct password alone therefore never yields a usable token for an
	 * enrolled account, which is the property that makes this two-factor rather
	 * than a confirmation screen.
	 *
	 * <p>The login box accepts a username or a work email. It is resolved to a
	 * username once, here, so every step after this one deals with a single
	 * canonical identity.
	 */
	@RequestMapping(value = "${jwt.get.token.uri}", method = RequestMethod.POST)
	public ResponseEntity<BaseDto<Object>> createAuthenticationToken(
			@RequestBody JwtTokenRequest authenticationRequest, HttpServletRequest request)
			throws AuthenticationException {

		String userName = employeeCredentialsService
				.resolveLoginIdentifier(authenticationRequest.getUserName());

		authenticate(userName, authenticationRequest.getPassword());
		EmployeeCredentialsModel users = employeeCredentialsService.findEmployeeCredentialsByUserName(userName);
		employeeCredentialsService.upgradeLegacyPasswordIfNeeded(users, authenticationRequest.getPassword());
		if(users.getActiveS()== 'N')
		{
			throw new IHealthPharmException("Account Disabled", HttpStatus.NOT_FOUND);
		}

		// Not switched on: unchanged behaviour, unchanged response shape.
		if (!isTwoFactorRequested(users)) {
			return issueSession(users);
		}

		// Enrolled, and this machine was remembered: the password is enough.
		if (users.isTwoFactorActive()
				&& twoFactorService.isDeviceTrusted(users, readTrustedDeviceCookie(request))) {
			return issueSession(users);
		}

		// Enrolled but no confirmed address yet - the user supplies one now.
		// An administrator never types it: that keeps the decision to allow a
		// change with management and the proof of ownership with whoever can
		// open the mailbox.
		if (!users.isTwoFactorActive()) {
			IssuedChallenge handle = twoFactorService.issueCaptureHandle(users);
			return step(TwoFactorStepResponse.captureRequired(
					handle.getChallengeToken(), handle.getExpiresInSeconds()),
					"Confirm the email address for your sign-in codes");
		}

		IssuedChallenge challenge = twoFactorService.issueLoginChallenge(users);
		twoFactorMailService.sendCode(challenge);
		return step(TwoFactorStepResponse.codeSent(challenge), "A sign-in code has been sent");
	}

	/**
	 * Step two for an enrolled account: the code. Issues the session token, and
	 * optionally remembers the machine.
	 */
	@RequestMapping(value = "${jwt.get.token.uri}/2fa", method = RequestMethod.POST)
	public ResponseEntity<BaseDto<Object>> verifyTwoFactorCode(
			@RequestBody TwoFactorCodeRequest codeRequest, HttpServletRequest request,
			HttpServletResponse response) {

		TwoFactorChallengeModel challenge = twoFactorService.verifyCode(
				codeRequest.getChallengeToken(), codeRequest.getCode());

		if (!TwoFactorChallengeModel.PURPOSE_LOGIN.equals(challenge.getPurpose())) {
			throw new IHealthPharmException("That code was not a sign-in code.",
					HttpStatus.BAD_REQUEST);
		}

		EmployeeCredentialsModel users = employeeCredentialsService
				.findEmployeeCredentialsById(challenge.getEmployeeCredentialsId());

		if (codeRequest.isRememberDevice()) {
			rememberThisMachine(users, request, response);
		}
		return issueSession(users);
	}

	/**
	 * Enrolment, part one: the address the user chose. Stored unproven, and sent
	 * a code. A typo costs one retry rather than the account, because nothing is
	 * trusted until the code comes back.
	 */
	@RequestMapping(value = "${jwt.get.token.uri}/2fa/email", method = RequestMethod.POST)
	public ResponseEntity<BaseDto<Object>> captureTwoFactorEmail(
			@RequestBody EmailCaptureRequest captureRequest) {

		EmployeeCredentialsModel users = twoFactorService.resolveOpenHandle(
				captureRequest.getChallengeToken(),
				TwoFactorChallengeModel.PURPOSE_EMAIL_CAPTURE);

		IssuedChallenge challenge = twoFactorService.issueEmailVerificationChallenge(
				users, captureRequest.getEmail());
		twoFactorMailService.sendCode(challenge);
		twoFactorService.consumeHandle(captureRequest.getChallengeToken());

		return step(TwoFactorStepResponse.codeSent(challenge),
				"A confirmation code has been sent to that address");
	}

	/**
	 * Enrolment, part two: the code proving the address can be read. Completes
	 * enrolment and signs the user in, so they are not made to log in twice.
	 */
	@RequestMapping(value = "${jwt.get.token.uri}/2fa/email/confirm", method = RequestMethod.POST)
	public ResponseEntity<BaseDto<Object>> confirmTwoFactorEmail(
			@RequestBody TwoFactorCodeRequest codeRequest, HttpServletRequest request,
			HttpServletResponse response) {

		TwoFactorChallengeModel challenge = twoFactorService.verifyCode(
				codeRequest.getChallengeToken(), codeRequest.getCode());
		twoFactorService.confirmEmail(challenge);

		EmployeeCredentialsModel users = employeeCredentialsService
				.findEmployeeCredentialsById(challenge.getEmployeeCredentialsId());

		if (codeRequest.isRememberDevice()) {
			rememberThisMachine(users, request, response);
		}
		return issueSession(users);
	}

	/**
	 * Sends a replacement code, for a login or an address confirmation alike,
	 * under the same hourly limit.
	 *
	 * <p>Accepts a challenge that has already expired: with a one-minute code,
	 * that is the normal case for anyone asking for a new one.
	 */
	@RequestMapping(value = "${jwt.get.token.uri}/2fa/resend", method = RequestMethod.POST)
	public ResponseEntity<BaseDto<Object>> resendTwoFactorCode(
			@RequestBody TwoFactorCodeRequest codeRequest) {

		IssuedChallenge challenge = twoFactorService.resendChallenge(codeRequest.getChallengeToken());
		twoFactorMailService.sendCode(challenge);
		return step(TwoFactorStepResponse.codeSent(challenge), "A new code has been sent");
	}

	// ------------------------------------------------------------------
	// Shared
	// ------------------------------------------------------------------

	/**
	 * Builds the successful login response. The one place a session token is
	 * created, so every route into the application carries the same payload the
	 * frontend already expects.
	 */
	private ResponseEntity<BaseDto<Object>> issueSession(EmployeeCredentialsModel users) {
		final UserDetails userDetails = jwtInMemoryUserDetailsService
				.loadUserByUsername(users.getUserName());
		EmployeePharmacyRoleDTO employeePharmacyRoleModels = employeePharmacyRoleService.findEmployeePharmacyRoleDataByEmployeeId(users.getEmployee());

		final String token = jwtTokenUtil.generateToken(userDetails);
		List<EmployeeAccessModel> result = employeeAccessService.findByEmployee(users.getEmployee());

		return new BaseDto<Object>(new JwtTokenResponse(token, users.getEmployee().getEmployeeId(),
				result, employeePharmacyRoleModels.getPharmacyModel()),
				"Authentication Success", OK).respond();
	}

	/** An unfinished login: a next step, and deliberately no token. */
	private ResponseEntity<BaseDto<Object>> step(TwoFactorStepResponse body, String message) {
		return new BaseDto<Object>(body, message, OK).respond();
	}

	/** True when this account is meant to be challenged at all. */
	private boolean isTwoFactorRequested(EmployeeCredentialsModel users) {
		return users.getTwoFactorEnabledS() != null && users.getTwoFactorEnabledS() == 'Y';
	}

	private String readTrustedDeviceCookie(HttpServletRequest request) {
		if (request.getCookies() == null) {
			return null;
		}
		for (Cookie cookie : request.getCookies()) {
			if (TRUSTED_DEVICE_COOKIE.equals(cookie.getName())) {
				return cookie.getValue();
			}
		}
		return null;
	}

	/**
	 * Remembers this machine and sets the cookie.
	 *
	 * <p>HttpOnly so script cannot read it, and SameSite=Lax so it is not sent
	 * on a cross-site request - it waives a security check, so it must not be
	 * reachable from another site's page. Secure is set when the request arrived
	 * over HTTPS, which is production; local development is plain HTTP, where a
	 * Secure cookie would simply never come back.
	 */
	private void rememberThisMachine(EmployeeCredentialsModel users, HttpServletRequest request,
			HttpServletResponse response) {
		String label = request.getHeader("User-Agent");
		String rawToken = twoFactorService.rememberDevice(users, label);

		StringBuilder cookie = new StringBuilder();
		cookie.append(TRUSTED_DEVICE_COOKIE).append('=').append(rawToken)
				.append("; Max-Age=").append(trustedDeviceCookieSeconds())
				.append("; Path=/; HttpOnly; SameSite=Lax");
		if (request.isSecure()) {
			cookie.append("; Secure");
		}
		response.addHeader("Set-Cookie", cookie.toString());
	}

	private long trustedDeviceCookieSeconds() {
		return twoFactorProperties.getTrustedDeviceDays() * 24L * 60L * 60L;
	}

	@RequestMapping(value = "${jwt.refresh.token.uri}", method = RequestMethod.GET)
	public ResponseEntity<?> refreshAndGetAuthenticationToken(HttpServletRequest request) {
		String authToken = request.getHeader(tokenHeader);
		final String token = authToken.substring(7);
		String username = jwtTokenUtil.getUsernameFromToken(token);
		JwtUserDetails user = (JwtUserDetails) jwtInMemoryUserDetailsService.loadUserByUsername(username);

		if (jwtTokenUtil.canTokenBeRefreshed(token)) {
			String refreshedToken = jwtTokenUtil.refreshToken(token);
			return ResponseEntity.ok(new JwtTokenResponse(refreshedToken));
		} else {
			return ResponseEntity.badRequest().body(null);
		}
	}

	@ExceptionHandler({ AuthenticationException.class })
	public ResponseEntity<String> handleAuthenticationException(AuthenticationException e) {
		return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(e.getMessage());
	}

	private void authenticate(String username, String password) {
		Objects.requireNonNull(username);
		Objects.requireNonNull(password);

		try {
			authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(username, password));
		} catch (DisabledException e) {
			throw new AuthenticationException("USER_DISABLED", e);
		} catch (BadCredentialsException e) {
			throw new AuthenticationException("INVALID_CREDENTIALS", e);
		}
	}
}
