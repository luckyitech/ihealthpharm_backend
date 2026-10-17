package com.ihealthpharm.config.jwt;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import com.ihealthpharm.exception.IHealthPharmException;
import com.ihealthpharm.masters.dto.EmployeePharmacyRoleDTO;
import com.ihealthpharm.masters.model.EmployeeCredentialsModel;
import com.ihealthpharm.masters.service.EmployeeCredentialsService;
import com.ihealthpharm.masters.service.EmployeePharmacyRoleService;

@Service
public class JwtInMemoryUserDetailsService implements UserDetailsService {

	@Autowired
	EmployeeCredentialsService employeeCredentialsService;

	@Autowired
	EmployeePharmacyRoleService employeePharmacyRoleService;

	@Override
	public UserDetails loadUserByUsername(String username) {
		EmployeeCredentialsModel credentials;
		try {
			credentials = employeeCredentialsService.findEmployeeCredentialsByUserName(username);
		} catch (IHealthPharmException e) {
			// Spring Security's contract for "no such account" is
			// UsernameNotFoundException, which the provider turns into the same
			// BadCredentialsException a wrong password produces. Letting the
			// not-found exception escape instead answered 500 for an unknown
			// name and 401 for a wrong password - which told anyone willing to
			// try which usernames and work emails have accounts here.
			throw new UsernameNotFoundException("No account for " + username, e);
		}

		EmployeePharmacyRoleDTO role = employeePharmacyRoleService
				.findEmployeePharmacyRoleDataByEmployeeId(credentials.getEmployee());

		return new JwtUserDetails(
				credentials.getEmployee().getEmployeeId(),
				credentials.getUserName(),
				credentials.getCurrentPassword(),
				role.getPharmacyRolesModel().getRoleNm());
	}

}
