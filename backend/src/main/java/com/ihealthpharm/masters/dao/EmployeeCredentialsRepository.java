package com.ihealthpharm.masters.dao;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ihealthpharm.masters.model.EmployeeCredentialsModel;
import com.ihealthpharm.masters.model.EmployeeModel;

public interface EmployeeCredentialsRepository extends JpaRepository<EmployeeCredentialsModel, Integer>{

	public EmployeeCredentialsModel findByUserName(String userName);
	
	public EmployeeCredentialsModel findByEmployee(EmployeeModel employeeModel);

	@Modifying
	@Query("update employee_credentials ec set ec.previousPassword1=ec.currentPassword,ec.currentPassword=:newPwd where ec.employeeCredentialsId=:empCredId")
   void updateEmpCredentials(@Param ("empCredId")int empCredId,@Param ("newPwd")String newPwd);

}
