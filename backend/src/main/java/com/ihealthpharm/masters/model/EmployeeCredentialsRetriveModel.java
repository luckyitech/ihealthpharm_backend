package com.ihealthpharm.masters.model;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Flat read-only view of employee_credentials - it holds EMPLOYEE_ID as a plain
 * column where {@link EmployeeCredentialsModel} maps it as a relation.
 *
 * The entity name must stay distinct from EmployeeCredentialsModel's. Both were
 * previously registered as "employee_credentials", and JPQL naming that entity
 * resolved to whichever Hibernate happened to scan first - when it picked this
 * one, EmployeeRepository.getAllEmployeesHavingAccess() failed validation and
 * the application would not start.
 */
@Data
@Entity(name="employee_credentials_retrive")
@Table(name="employee_credentials")
@EqualsAndHashCode(of = "employeeCredentialsId", callSuper = false)
public class EmployeeCredentialsRetriveModel extends AuditModel{

	/**
	 * 
	 */
	private static final long serialVersionUID = -4034731782977576503L;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "EMPLOYEE_CREDENTIALS_ID" ,length=11)
	private Integer employeeCredentialsId;
	
	//@OneToOne(fetch = FetchType.LAZY)
	@Column(name = "EMPLOYEE_ID")
	private Integer employee;
	
	@Column(name="USER_NM", length=50)
	private String userName;
	
	@Column(name="CURRENT_PASSWORD", length=100)
	private String currentPassword;

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
}
