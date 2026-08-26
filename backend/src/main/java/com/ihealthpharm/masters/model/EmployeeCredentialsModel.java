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

	@Column(name="APPROVAL_ACCESS_PIN", length=6)
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
}
