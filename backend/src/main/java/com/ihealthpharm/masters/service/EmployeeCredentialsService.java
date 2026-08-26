package com.ihealthpharm.masters.service;

import java.util.List;

import com.ihealthpharm.masters.model.EmployeeCredentialsModel;
import com.ihealthpharm.masters.model.EmployeeCredentialsRetriveModel;
import com.ihealthpharm.masters.model.EmployeeModel;

public interface EmployeeCredentialsService {

	 EmployeeCredentialsModel saveEmployeeCredentialsData(EmployeeCredentialsModel employeeCredentialsModel);
	
	 EmployeeCredentialsModel updateEmployeeCredentialsData(EmployeeCredentialsModel employeeCredentialsModel);
	
	 List<EmployeeCredentialsModel> updateEmployeeCredentialsData(List<EmployeeCredentialsModel> employeeCredentialsModels);

	 EmployeeCredentialsModel findEmployeeCredentialsByUserName(String userName);
	
	 EmployeeCredentialsRetriveModel findEmployeeCredentialByUserName(String userName);
	
	 List<EmployeeCredentialsModel> findAllEmployeeCredentials();
	
	 EmployeeCredentialsModel findEmployeeCredentialsById(Integer employeeCredentialId);
	
	public void deleteEmployeeCredentialsById(Integer employeeCredentialId);
	
	public void deleteEmployeesCredentialsById(Integer[] employeeCredentialIds);
	
	public EmployeeCredentialsModel findEmployeeCredentialsByEmployee(EmployeeModel employeeModel);

	EmployeeCredentialsModel updateEmpCredentialsData(int empCredId,String newPwd);

	/**
	 * Re-hashes and persists {@code rawPassword} for {@code employeeCredentialsModel}
	 * when its stored password is still the plaintext value from before password
	 * encryption was introduced. No-op once the row already holds a BCrypt hash.
	 */
	void upgradeLegacyPasswordIfNeeded(EmployeeCredentialsModel employeeCredentialsModel, String rawPassword);
}
