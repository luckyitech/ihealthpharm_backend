package com.ihealthpharm.masters.service.impl;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;

import javax.transaction.Transactional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.ihealthpharm.config.jwt.LegacyAwarePasswordEncoder;
import com.ihealthpharm.exception.IHealthPharmException;
import com.ihealthpharm.masters.dao.EmployeeCredentialsRepository;
import com.ihealthpharm.masters.dao.EmployeeCredentialsRetriveRepository;
import com.ihealthpharm.masters.helper.EmployeeCredentialsHelper;
import com.ihealthpharm.masters.model.EmployeeCredentialsModel;
import com.ihealthpharm.masters.model.EmployeeCredentialsRetriveModel;
import com.ihealthpharm.masters.model.EmployeeModel;
import com.ihealthpharm.masters.service.EmployeeCredentialsService;

import lombok.extern.slf4j.Slf4j;

@Service
@Transactional
@Slf4j
public class EmployeeCredentialsImpl implements EmployeeCredentialsService {

	@Autowired
	EmployeeCredentialsRepository employeeCredentialsRepository;
	
	@Autowired
	EmployeeCredentialsRetriveRepository employeeCredentialsRetriveRepository;
	

	@Autowired
	EmployeeCredentialsHelper employeeCredentialsHelper;

	@Autowired
	PasswordEncoder passwordEncoder;

	@Override
	public EmployeeCredentialsModel saveEmployeeCredentialsData(EmployeeCredentialsModel employeeCredentialsModel) {
		encodePasswordFields(employeeCredentialsModel);
		employeeCredentialsModel = employeeCredentialsRepository.save(employeeCredentialsModel);
		log.info("Employee Credentials data with ID:" + employeeCredentialsModel.getEmployeeCredentialsId()
				+ " saved succesfully");
		return employeeCredentialsModel;
	}

	@Override
	public EmployeeCredentialsModel updateEmployeeCredentialsData(EmployeeCredentialsModel employeeCredentialsModel) {
		EmployeeCredentialsModel employeeCredentialsRes = getValidSupplierContract(
				employeeCredentialsModel.getEmployeeCredentialsId());
		if (!Objects.nonNull(employeeCredentialsRes)) {
			throw new IHealthPharmException(employeeCredentialsHelper.getNotFoundEmployeeCredentialsMessage(),
					HttpStatus.NOT_FOUND);
		}

		encodePasswordFields(employeeCredentialsModel);
		employeeCredentialsRes = employeeCredentialsRepository.save(employeeCredentialsModel);
		log.info("Employee Credentials data with ID : " + employeeCredentialsRes.getEmployeeCredentialsId()
				+ " updated succesfully");
		return employeeCredentialsRes;
	}

	@Override
	public List<EmployeeCredentialsModel> updateEmployeeCredentialsData(
			List<EmployeeCredentialsModel> employeeCredentialsModels) {
		for (EmployeeCredentialsModel employeeCredentialsModel : employeeCredentialsModels) {
			EmployeeCredentialsModel employeeCredentialsRes = getValidSupplierContract(
					employeeCredentialsModel.getEmployeeCredentialsId());
			if (!Objects.nonNull(employeeCredentialsRes)) {
				throw new IHealthPharmException(employeeCredentialsHelper.getNotFoundEmployeeCredentialsMessage(),
						HttpStatus.NOT_FOUND);
			}

			encodePasswordFields(employeeCredentialsModel);
			employeeCredentialsRes = employeeCredentialsRepository.save(employeeCredentialsModel);
			log.info("Employee Credentials data with ID : " + employeeCredentialsRes.getEmployeeCredentialsId()
					+ " updated succesfully");
		}
		return employeeCredentialsModels;
	}

	@Override
	public List<EmployeeCredentialsModel> findAllEmployeeCredentials() {
		log.info(employeeCredentialsHelper.getRetrieveEmployeeCredentialsMessage());
		return employeeCredentialsRepository.findAll();
	}

	@Override
	public EmployeeCredentialsModel findEmployeeCredentialsById(Integer employeeCredentialId) {
		EmployeeCredentialsModel employeeCredentialsRes = getValidSupplierContract(employeeCredentialId);
		log.info(
				employeeCredentialsHelper.getRetrieveEmployeeCredentialsMessage() + " With Id:" + employeeCredentialId);
		return employeeCredentialsRes;
	}

	@Override
	public void deleteEmployeeCredentialsById(Integer employeeCredentialId) {

		EmployeeCredentialsModel employeeCredentialsRes = getValidSupplierContract(employeeCredentialId);
		employeeCredentialsRepository.delete(employeeCredentialsRes);
		log.info(employeeCredentialsHelper.getDeleteEmployeeCredentialsMessage() + " With Id:" + employeeCredentialId);

	}

	@Override
	public void deleteEmployeesCredentialsById(Integer[] employeeCredentialIds) {
		EmployeeCredentialsModel employeeCredentialsRes = null;
		for (int employeeCredentialId : employeeCredentialIds) {
			employeeCredentialsRes = getValidSupplierContract(employeeCredentialId);
			employeeCredentialsRepository.delete(employeeCredentialsRes);
			log.info(employeeCredentialsHelper.getDeleteEmployeeCredentialsMessage() + " With Id:"
					+ employeeCredentialId);
		}
	}
	
	@Override
	public EmployeeCredentialsModel findEmployeeCredentialsByUserName(String userName) {
		EmployeeCredentialsModel employeeCredentialsRes = employeeCredentialsRepository.findByUserName(userName);
		if (!Objects.nonNull(employeeCredentialsRes)) {
			throw new IHealthPharmException(employeeCredentialsHelper.getNotFoundEmployeeCredentialsMessage(),
					HttpStatus.NOT_FOUND);
		}
		return employeeCredentialsRes;
	}
	
	public EmployeeCredentialsModel getValidSupplierContract(int employeeCredentialId) {
		EmployeeCredentialsModel employeeCredentialsRes = null;

		try {
			employeeCredentialsRes = employeeCredentialsRepository.findById(employeeCredentialId).get();
			return employeeCredentialsRes;
		} catch (NoSuchElementException noSuchElementException) {
			throw new IHealthPharmException(employeeCredentialsHelper.getNotFoundEmployeeCredentialsMessage(),
					HttpStatus.NOT_FOUND);
		}
	}

	@Override
	public EmployeeCredentialsRetriveModel findEmployeeCredentialByUserName(String userName) {
		
		return employeeCredentialsRetriveRepository.findByUserName(userName);
	}

	@Override
	public EmployeeCredentialsModel findEmployeeCredentialsByEmployee(EmployeeModel employeeModel) {
		
		return employeeCredentialsRepository.findByEmployee(employeeModel);
	}

	@Override
	public EmployeeCredentialsModel updateEmpCredentialsData(int empCredId, String newPwd) {
		 employeeCredentialsRepository.updateEmpCredentials(empCredId, passwordEncoder.encode(newPwd));
		 return null;
	}

	@Override
	public void verifyApprovalPin(String userName, String rawPin) {
		if (rawPin == null || rawPin.trim().isEmpty()) {
			throw new IHealthPharmException("Approval PIN is required.", HttpStatus.UNAUTHORIZED);
		}

		EmployeeCredentialsModel credentials = findEmployeeCredentialsByUserName(userName);
		String storedPin = credentials.getApprovalAccessPin();

		if (storedPin == null || storedPin.trim().isEmpty()) {
			throw new IHealthPharmException(
					"No approval PIN is set for this user. Ask an administrator to set one.",
					HttpStatus.FORBIDDEN);
		}

		if (!passwordEncoder.matches(rawPin, storedPin)) {
			log.warn("Approval PIN rejected for user {}", userName);
			throw new IHealthPharmException("Approval PIN is incorrect.", HttpStatus.UNAUTHORIZED);
		}

		// The stored PIN is left exactly as it is, plain text included. Receipts, Credit Note,
		// Debit Note and Pending Payables fetch the stored PIN and compare it in the browser, so
		// hashing it here would silently stop those screens approving anything, and would leave
		// the PIN uneditable on Master > Employee > Access, which validates the field as six
		// digits. Hashing can follow once those screens verify the PIN server-side.
	}

	@Override
	public void upgradeLegacyPasswordIfNeeded(EmployeeCredentialsModel employeeCredentialsModel, String rawPassword) {
		if (LegacyAwarePasswordEncoder.isBCryptHash(employeeCredentialsModel.getCurrentPassword())) {
			return;
		}
		employeeCredentialsModel.setCurrentPassword(passwordEncoder.encode(rawPassword));
		employeeCredentialsRepository.save(employeeCredentialsModel);
		log.info("Migrated legacy plaintext password to BCrypt for employee credentials ID:"
				+ employeeCredentialsModel.getEmployeeCredentialsId());
	}

	/**
	 * Encodes currentPassword/previousPassword1/previousPassword2 in place,
	 * skipping any value that is already a BCrypt hash so callers can safely
	 * re-save a model without double-hashing an untouched password.
	 */
	private void encodePasswordFields(EmployeeCredentialsModel employeeCredentialsModel) {
		employeeCredentialsModel.setCurrentPassword(encodeIfPlaintext(employeeCredentialsModel.getCurrentPassword()));
		employeeCredentialsModel.setPreviousPassword1(encodeIfPlaintext(employeeCredentialsModel.getPreviousPassword1()));
		employeeCredentialsModel.setPreviousPassword2(encodeIfPlaintext(employeeCredentialsModel.getPreviousPassword2()));
	}

	private String encodeIfPlaintext(String password) {
		if (password == null || LegacyAwarePasswordEncoder.isBCryptHash(password)) {
			return password;
		}
		return passwordEncoder.encode(password);
	}

}
