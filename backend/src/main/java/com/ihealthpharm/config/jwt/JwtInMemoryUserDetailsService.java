package com.ihealthpharm.config.jwt;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Service;

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
		EmployeeCredentialsModel credentials = employeeCredentialsService.findEmployeeCredentialsByUserName(username);
		EmployeePharmacyRoleDTO role = employeePharmacyRoleService
				.findEmployeePharmacyRoleDataByEmployeeId(credentials.getEmployee());

		return new JwtUserDetails(
				credentials.getEmployee().getEmployeeId(),
				credentials.getUserName(),
				credentials.getCurrentPassword(),
				role.getPharmacyRolesModel().getRoleNm());
	}

}
