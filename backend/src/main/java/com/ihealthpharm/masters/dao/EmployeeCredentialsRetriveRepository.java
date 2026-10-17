package com.ihealthpharm.masters.dao;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ihealthpharm.masters.model.EmployeeCredentialsModel;
import com.ihealthpharm.masters.model.EmployeeCredentialsRetriveModel;
import com.ihealthpharm.masters.model.EmployeeModel;

public interface EmployeeCredentialsRetriveRepository extends JpaRepository<EmployeeCredentialsRetriveModel, Integer>{

	public EmployeeCredentialsRetriveModel findByUserName(String userName);

	public EmployeeCredentialsModel findByEmployee(EmployeeModel employeeModel);
}
