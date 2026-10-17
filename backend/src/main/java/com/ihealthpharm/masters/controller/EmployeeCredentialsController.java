package com.ihealthpharm.masters.controller;

import static org.springframework.http.HttpStatus.OK;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ihealthpharm.commons.BaseDto;
import com.ihealthpharm.masters.helper.EmployeeCredentialsHelper;
import com.ihealthpharm.masters.model.EmployeeCredentialsModel;
import com.ihealthpharm.masters.model.EmployeeModel;
import com.ihealthpharm.masters.service.EmployeeCredentialsService;

import lombok.extern.slf4j.Slf4j;

@CrossOrigin
@RestController
@Slf4j
public class EmployeeCredentialsController {

	@Autowired
	EmployeeCredentialsHelper employeeCredentialsHelper;

	@Autowired
	EmployeeCredentialsService employeeCredentialsService;

	@PostMapping("save/employeecredentials")
	public ResponseEntity<BaseDto<EmployeeCredentialsModel>> saveEmployeeCredentials(@RequestBody EmployeeCredentialsModel employeeCredentialsModel) {
		log.info(employeeCredentialsModel.toString());
		employeeCredentialsModel = employeeCredentialsService.saveEmployeeCredentialsData(employeeCredentialsModel);
		log.info(employeeCredentialsHelper.getSaveEmployeeCredentialsMessage() + " With Id:"+ employeeCredentialsModel.getEmployeeCredentialsId());
		return new BaseDto<>(employeeCredentialsModel, employeeCredentialsHelper.getSaveEmployeeCredentialsMessage(),
				OK).respond();
	}

	@PutMapping("update/employeecredential")
	public ResponseEntity<BaseDto<EmployeeCredentialsModel>> updateEmployeeCredential(
			@RequestBody EmployeeCredentialsModel employeeCredentialsModel) {
		employeeCredentialsModel = employeeCredentialsService.updateEmployeeCredentialsData(employeeCredentialsModel);
		log.info(employeeCredentialsHelper.getUpdateEmployeeCredentialsMessage() + " With Id:"
				+ employeeCredentialsModel.getEmployeeCredentialsId());
		return new BaseDto<>(employeeCredentialsModel, employeeCredentialsHelper.getUpdateEmployeeCredentialsMessage(),
				OK).respond();
	}

	@PutMapping("update/employeecredentials")
	public ResponseEntity<BaseDto<List<EmployeeCredentialsModel>>> updateEmployeeCredentials(
			@RequestBody List<EmployeeCredentialsModel> employeeCredentialsModels) {
		log.info("Employee Credentials : " + employeeCredentialsModels);
		employeeCredentialsModels = employeeCredentialsService.updateEmployeeCredentialsData(employeeCredentialsModels);
		return new BaseDto<>(employeeCredentialsModels, employeeCredentialsHelper.getUpdateEmployeeCredentialsMessage(),
				OK).respond();
	}

	@DeleteMapping("delete/employeecredential")
	public ResponseEntity<BaseDto<Object>> deleteEmployeeCredential(@RequestParam Integer employeeCredentialId) {
		employeeCredentialsService.deleteEmployeeCredentialsById(employeeCredentialId);
		log.info(employeeCredentialsHelper.getDeleteEmployeeCredentialsMessage() + " With Id:" + employeeCredentialId);
		return new BaseDto<>(employeeCredentialsHelper.getDeleteEmployeeCredentialsMessage(), OK).respond();
	}

	@DeleteMapping("delete/employeecredentials")
	public ResponseEntity<BaseDto<Object>> deleteEmployeeCredentials(
			@RequestParam("employeeCredentialIds") Integer[] employeeCredentialIds) {
		employeeCredentialsService.deleteEmployeesCredentialsById(employeeCredentialIds);
		return new BaseDto<>(employeeCredentialsHelper.getDeleteEmployeeCredentialsMessage(), OK).respond();
	}

	@GetMapping("getemployeecredentialsbyid")
	public ResponseEntity<BaseDto<EmployeeCredentialsModel>> getEmployeeCredentialById(
			@RequestParam("employeeCredentialId") Integer employeeCredentialId) {
		EmployeeCredentialsModel employeeCredentialsModel = employeeCredentialsService
				.findEmployeeCredentialsById(employeeCredentialId);
		return new BaseDto<>(employeeCredentialsModel, employeeCredentialsHelper.getDeleteEmployeeCredentialsMessage(),
				OK).respond();
	}

	@GetMapping("getemployeecredentialsbyusername")
	public ResponseEntity<BaseDto<EmployeeCredentialsModel>> getEmployeeCredentialByUserName(
			@RequestParam("employeeUserName") String employeeUserName) {
		EmployeeCredentialsModel employeeCredentialsModel = employeeCredentialsService
				.findEmployeeCredentialsByUserName(employeeUserName);
		return new BaseDto<>(employeeCredentialsModel, employeeCredentialsHelper.getDeleteEmployeeCredentialsMessage(),
				OK).respond();
	}

	@GetMapping("getallemployeecredentials")
	public ResponseEntity<BaseDto<List<EmployeeCredentialsModel>>> getAllEmployeeCredentials() {
		List<EmployeeCredentialsModel> employeeCredentialsModels = employeeCredentialsService
				.findAllEmployeeCredentials();
		return new BaseDto<>(employeeCredentialsModels, employeeCredentialsHelper.getDeleteEmployeeCredentialsMessage(),
				OK).respond();
	}


	@PostMapping("getemployeecredentialsbyEmployeeid")
	public ResponseEntity<BaseDto<EmployeeCredentialsModel>> getEmployeeCredentialByUserName(@RequestBody EmployeeModel employee) {
		log.info("----------------------------------------------------------------------"+employee.toString());
		EmployeeCredentialsModel employeeCredentialsModel = employeeCredentialsService.findEmployeeCredentialsByEmployee(employee);
		return new BaseDto<>(employeeCredentialsModel, employeeCredentialsHelper.getDeleteEmployeeCredentialsMessage(),
				OK).respond();
	}
	
	@PutMapping("update/employeeresetpwdcredential")
	public ResponseEntity<BaseDto<EmployeeCredentialsModel>> updateEmployeePassword (@RequestBody EmployeeCredentialsModel resetpwd){
	   EmployeeCredentialsModel empCredentials=employeeCredentialsService.updateEmpCredentialsData(resetpwd.getEmployeeCredentialsId(),resetpwd.getCurrentPassword());
		return new BaseDto<>(empCredentials,employeeCredentialsHelper.getUpdateEmployeeCredentialsMessage(),OK).respond();
		
	}

}
