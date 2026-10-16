package com.ihealthpharm.twofactor.dao;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ihealthpharm.twofactor.model.TwoFactorResetAuditModel;

public interface TwoFactorResetAuditRepository extends JpaRepository<TwoFactorResetAuditModel, Integer> {

	/** Every reset an account has had, most recent first. */
	List<TwoFactorResetAuditModel> findByEmployeeCredentialsIdOrderByTwoFactorResetIdDesc(
			Integer employeeCredentialsId);

	/** Every reset one administrator has performed — the review that makes the permission safe to grant. */
	List<TwoFactorResetAuditModel> findByResetByEmployeeIdOrderByTwoFactorResetIdDesc(
			Integer resetByEmployeeId);
}
