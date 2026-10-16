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

	/**
	 * Turns whatever somebody typed into the login box into a username.
	 *
	 * <p>People may sign in with their username or with their work email, so
	 * every later step needs one canonical answer. This is the single place that
	 * decides it.
	 *
	 * <p>Anything that does not resolve to exactly one account is returned
	 * unchanged, so authentication fails the ordinary way with the ordinary
	 * message. That is deliberate: answering differently for "no such email"
	 * than for "wrong password" would turn the login box into a way of finding
	 * out which addresses have accounts.
	 */
	String resolveLoginIdentifier(String identifier);
	
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

	/**
	 * Checks an approval PIN for the signed-in user.
	 *
	 * Accepts the plaintext PINs that predate encryption and re-hashes them on
	 * first correct use, the same way passwords were migrated. Raises rather
	 * than returning false when the PIN is wrong or absent, so a caller cannot
	 * proceed by ignoring the result.
	 */
	void verifyApprovalPin(String userName, String rawPin);
}
