package com.ihealthpharm.config.jwt.resource;

import static org.springframework.http.HttpStatus.OK;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import javax.servlet.http.HttpServletRequest;

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

@RestController
@CrossOrigin
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
	
	@RequestMapping(value = "${jwt.get.token.uri}", method = RequestMethod.POST)
	public ResponseEntity<BaseDto<JwtTokenResponse>> createAuthenticationToken(@RequestBody JwtTokenRequest authenticationRequest)
			throws AuthenticationException {
		
		authenticate(authenticationRequest.getUserName(), authenticationRequest.getPassword());
		EmployeeCredentialsModel users = employeeCredentialsService.findEmployeeCredentialsByUserName(authenticationRequest.getUserName());
		employeeCredentialsService.upgradeLegacyPasswordIfNeeded(users, authenticationRequest.getPassword());
		if(users.getActiveS()== 'N')
		{
			throw new IHealthPharmException("Account Disabled", HttpStatus.NOT_FOUND);
		}
		final UserDetails userDetails = jwtInMemoryUserDetailsService
				.loadUserByUsername(authenticationRequest.getUserName());
		EmployeePharmacyRoleDTO employeePharmacyRoleModels = employeePharmacyRoleService.findEmployeePharmacyRoleDataByEmployeeId(users.getEmployee());
		
		final String token = jwtTokenUtil.generateToken(userDetails);
		List<EmployeeAccessModel> result = employeeAccessService.findByEmployee(users.getEmployee());
	
		return new BaseDto<>(new JwtTokenResponse(token,users.getEmployee().getEmployeeId(),result,employeePharmacyRoleModels.getPharmacyModel()),"Authentication Success",OK).respond();
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
