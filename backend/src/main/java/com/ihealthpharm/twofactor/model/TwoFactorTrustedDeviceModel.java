package com.ihealthpharm.twofactor.model;

import java.util.Date;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;

import com.ihealthpharm.masters.model.AuditModel;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * A machine the user asked to be remembered, so that logging in on it again
 * does not ask for a fresh code.
 *
 * <p>This is what stops two-factor becoming a daily dependency on the mail
 * provider: staff see a code about once a month per machine rather than at
 * every shift, and a mail outage cannot stop the pharmacy trading.
 *
 * <p>It is <b>not</b> a session token. On its own it grants nothing — it only
 * waives the second factor once the password has already been accepted. The
 * cookie value is still a bearer credential, so {@link #deviceTokenHash} holds
 * a hash of it rather than the value.
 */
@Data
@Entity(name = "two_factor_trusted_device")
@EqualsAndHashCode(of = "trustedDeviceId", callSuper = false)
public class TwoFactorTrustedDeviceModel extends AuditModel {

	private static final long serialVersionUID = -8820074625311903547L;

	/** The user chose to forget this machine. */
	public static final String REVOKED_BY_USER = "USER";

	/** An administrator reset the account; every remembered machine goes with it. */
	public static final String REVOKED_BY_ADMIN_RESET = "ADMIN_RESET";

	/** The password changed, so previously remembered machines are no longer trusted. */
	public static final String REVOKED_BY_PASSWORD_CHANGE = "PASSWORD_CHANGE";

	/** The trust window simply ran out; closed by the nightly sweep. */
	public static final String REVOKED_BY_EXPIRY = "EXPIRED";

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "TRUSTED_DEVICE_ID")
	private Integer trustedDeviceId;

	@Column(name = "EMPLOYEE_CREDENTIALS_ID", nullable = false)
	private Integer employeeCredentialsId;

	@Column(name = "DEVICE_TOKEN_HASH", length = 100, nullable = false)
	private String deviceTokenHash;

	/** Browser and OS summary, so a person can tell which machine this is. */
	@Column(name = "LABEL", length = 100)
	private String label;

	@Column(name = "EXPIRES_TS", nullable = false)
	private Date expiresTimestamp;

	@Column(name = "LAST_USED_TS")
	private Date lastUsedTimestamp;

	@Column(name = "REVOKED_TS")
	private Date revokedTimestamp;

	/** One of the {@code REVOKED_BY_*} constants. */
	@Column(name = "REVOKED_REASON", length = 30)
	private String revokedReason;

	@Column(name = "AUDIT_ID")
	private Integer auditId;

	/** True when this machine may still skip the second factor. */
	public boolean isTrustedAt(Date now) {
		return revokedTimestamp == null
				&& expiresTimestamp != null && expiresTimestamp.after(now);
	}
}
