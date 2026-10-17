package com.ihealthpharm.masters.dao;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ihealthpharm.masters.model.EmployeeCredentialsModel;
import com.ihealthpharm.masters.model.EmployeeModel;

public interface EmployeeCredentialsRepository extends JpaRepository<EmployeeCredentialsModel, Integer>{

	public EmployeeCredentialsModel findByUserName(String userName);
	
	public EmployeeCredentialsModel findByEmployee(EmployeeModel employeeModel);

	/**
	 * Active accounts whose work email matches, compared case-insensitively
	 * because nobody types their own address the same way twice.
	 *
	 * <p>Returns a list rather than a single row on purpose: the column is
	 * deliberately not unique, so several accounts can share one supervisor's
	 * mailbox — the intended arrangement for relief staff on a shared login.
	 * Signing in by email therefore only works when the address identifies
	 * exactly one account; people sharing a mailbox sign in by username.
	 */
	@Query("select ec from employee_credentials ec"
			+ " where lower(ec.twoFactorEmail) = lower(:email) and ec.activeS = 'Y'")
	List<EmployeeCredentialsModel> findActiveByWorkEmail(@Param("email") String email);

	@Modifying
	@Query("update employee_credentials ec set ec.previousPassword1=ec.currentPassword,ec.currentPassword=:newPwd where ec.employeeCredentialsId=:empCredId")
   void updateEmpCredentials(@Param ("empCredId")int empCredId,@Param ("newPwd")String newPwd);

	/*
	 * Two-factor state is written only through the targeted updates below, never
	 * through save(). The Employee Access screen posts a whole
	 * EmployeeCredentialsModel without these fields, so a full-row save would
	 * null them and silently un-enrol the user - the same shape as the
	 * PUT /update/stock bug that wiped PHARMACY_ID.
	 */

	/** Stores a newly entered address as unproven. No code is sent to it until it is confirmed. */
	@Modifying
	@Query("update employee_credentials ec set ec.twoFactorEmail = :email,"
			+ " ec.twoFactorEmailVerifiedS = 'N' where ec.employeeCredentialsId = :empCredId")
	void setUnverifiedTwoFactorEmail(@Param("empCredId") Integer empCredId,
			@Param("email") String email);

	/**
	 * An administrator setting the work email on Master &rarr; Employee.
	 *
	 * <p>Marked usable straight away, so codes go there from the next sign-in
	 * with nothing for the user to confirm. That is the agreed behaviour: the
	 * administrator is trusted to know the address.
	 *
	 * <p>The cost is that a typo locks that person out until an administrator
	 * corrects it or switches them to password-only — neither of which the user
	 * can do themselves. The benefit is that an address set in advance cannot be
	 * claimed by somebody holding a stolen password, which is the hole that
	 * exists while an enabled account has no address yet.
	 */
	@Modifying
	@Query("update employee_credentials ec set ec.twoFactorEmail = :email,"
			+ " ec.twoFactorEmailVerifiedS = 'Y' where ec.employeeCredentialsId = :empCredId")
	void setWorkEmailByAdministrator(@Param("empCredId") Integer empCredId,
			@Param("email") String email);

	/** Marks the stored address proven and turns two-factor on, once a code sent to it came back. */
	@Modifying
	@Query("update employee_credentials ec set ec.twoFactorEmailVerifiedS = 'Y',"
			+ " ec.twoFactorEnabledS = 'Y' where ec.employeeCredentialsId = :empCredId")
	void confirmTwoFactorEmail(@Param("empCredId") Integer empCredId);

	/** Switches the challenge on or off for an account, leaving the stored address alone. */
	@Modifying
	@Query("update employee_credentials ec set ec.twoFactorEnabledS = :enabled"
			+ " where ec.employeeCredentialsId = :empCredId")
	void setTwoFactorEnabled(@Param("empCredId") Integer empCredId,
			@Param("enabled") Character enabled);

	/**
	 * Clears only the address, leaving the enabled flag alone — an administrator
	 * emptying the work email field so the user supplies their own again.
	 */
	@Modifying
	@Query("update employee_credentials ec set ec.twoFactorEmail = null,"
			+ " ec.twoFactorEmailVerifiedS = 'N' where ec.employeeCredentialsId = :empCredId")
	void clearTwoFactorEmailOnly(@Param("empCredId") Integer empCredId);

	/**
	 * An administrator reset: clears the address and its proof and switches the
	 * challenge off, so the user enrols again at their next login. The caller
	 * revokes the trusted devices and writes the audit row.
	 */
	@Modifying
	@Query("update employee_credentials ec set ec.twoFactorEmail = null,"
			+ " ec.twoFactorEmailVerifiedS = 'N', ec.twoFactorEnabledS = 'N'"
			+ " where ec.employeeCredentialsId = :empCredId")
	void clearTwoFactorEnrolment(@Param("empCredId") Integer empCredId);

}
