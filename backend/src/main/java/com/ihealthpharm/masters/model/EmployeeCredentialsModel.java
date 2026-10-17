package com.ihealthpharm.masters.model;

import java.io.Serializable;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.OneToOne;

import com.fasterxml.jackson.annotation.JsonIdentityInfo;
import com.fasterxml.jackson.annotation.ObjectIdGenerators;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@Entity(name="employee_credentials")
@JsonIdentityInfo(generator = ObjectIdGenerators.PropertyGenerator.class, property = "employeeCredentialsId")
@EqualsAndHashCode(of="employeeCredentialsId",callSuper=false)
public class EmployeeCredentialsModel extends AuditModel implements Serializable{

	/**
	 * 
	 */
	private static final long serialVersionUID = -4900919938997174124L;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "EMPLOYEE_CREDENTIALS_ID" ,length=11)
	private Integer employeeCredentialsId;
	
	@OneToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "EMPLOYEE_ID")
	private EmployeeModel employee;
	
	@Column(name="USER_NM", length=50)
	private String userName;
	
	@Column(name="CURRENT_PASSWORD", length=100)
	private String currentPassword;

	@Column(name="APPROVAL_ACCESS_PIN", length=100)
	private String approvalAccessPin;

	@Column(name="PREVIOUS_PASSWORD1", length=100)
	private String previousPassword1;

	@Column(name="PREVIOUS_PASSWORD2", length=100)
	private String previousPassword2;
	
	@Column( name = "ACTIVE_S",length=1)
	private Character activeS;
	
	@Column( name = "AUDIT_ID",length=11)
	private Integer auditId;
	
	@Column( name = "PASSWORD_STATUS",length=4)
	private Integer passwordStatus;

	/**
	 * Y when this account is challenged for an emailed code at login.
	 *
	 * <p>The three TWO_FACTOR_* fields below are NOT written through
	 * {@code save()}. The Employee Access screen posts a whole
	 * EmployeeCredentialsModel and does not send them, so a full-row save would
	 * write nulls over a user's enrolment and silently un-enrol them. They are
	 * written only by the targeted updates on
	 * {@code EmployeeCredentialsRepository}, and
	 * {@code EmployeeCredentialsImpl.updateEmployeeCredentialsData} copies them
	 * off the stored row before saving so the screen cannot clear them.
	 */
	@Column(name="TWO_FACTOR_ENABLED_S", length=1)
	private Character twoFactorEnabledS;

	/**
	 * Where login codes are sent. Set by the user who proved they can read it,
	 * never by an administrator, and deliberately separate from
	 * {@code employee.EMAIL_ID} - that is HR data, was never collected to prove
	 * identity, and in at least one case holds another employee's address.
	 */
	@Column(name="TWO_FACTOR_EMAIL", length=100)
	private String twoFactorEmail;

	/** Y only once a code sent to twoFactorEmail came back. No code is ever sent to an unverified address. */
	@Column(name="TWO_FACTOR_EMAIL_VERIFIED_S", length=1)
	private Character twoFactorEmailVerifiedS;

	/** True when this account should be asked for a code: enabled, with an address that was proven. */
	public boolean isTwoFactorActive() {
		return twoFactorEnabledS != null && twoFactorEnabledS == 'Y'
				&& twoFactorEmailVerifiedS != null && twoFactorEmailVerifiedS == 'Y'
				&& twoFactorEmail != null && !twoFactorEmail.trim().isEmpty();
	}
}
