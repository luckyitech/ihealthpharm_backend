package com.ihealthpharm.twofactor.model;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;

import com.ihealthpharm.masters.model.AuditModel;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * A record of one administrator resetting an account's two-factor enrolment.
 *
 * <p>A reset clears the stored address and its verified flag, and revokes every
 * machine the account had remembered. Whoever holds that permission can do it
 * to any account, so the only real control on it is that each use is
 * attributable afterwards.
 *
 * <p>This is a table of its own rather than {@code employee_credentials}' audit
 * columns, which hold only the most recent change and are overwritten by the
 * next one — that is not a trail. It is also not {@code user_history}, which
 * records a single actor per row, while a reset has two parties: the
 * administrator and the account.
 *
 * <p>The risk it exists to make visible is social engineering — <i>"I've lost
 * my email, please reset my two-factor"</i> from someone who already has the
 * password. No code can prevent that; only verifying the person can, and only
 * a record makes it reviewable afterwards.
 */
@Data
@Entity(name = "two_factor_reset_audit")
@EqualsAndHashCode(of = "twoFactorResetId", callSuper = false)
public class TwoFactorResetAuditModel extends AuditModel {

	private static final long serialVersionUID = 2958361507744288021L;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "TWO_FACTOR_RESET_ID")
	private Integer twoFactorResetId;

	/** The account that was reset. */
	@Column(name = "EMPLOYEE_CREDENTIALS_ID", nullable = false)
	private Integer employeeCredentialsId;

	/** The administrator who did it. */
	@Column(name = "RESET_BY_EMPLOYEE_ID")
	private Integer resetByEmployeeId;

	/** What they were told, in their words. */
	@Column(name = "REASON", length = 200)
	private String reason;

	/** The address that was cleared, so a mistaken reset can be understood later. */
	@Column(name = "PREVIOUS_EMAIL", length = 100)
	private String previousEmail;

	/** How many remembered machines this logged out. */
	@Column(name = "DEVICES_REVOKED", nullable = false)
	private Integer devicesRevoked = 0;

	@Column(name = "AUDIT_ID")
	private Integer auditId;
}
