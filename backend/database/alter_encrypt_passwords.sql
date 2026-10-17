-- Widens employee_credentials password columns to fit BCrypt hashes (60 chars).
-- Run this once against the imported database before starting the backend,
-- since spring.jpa.hibernate.ddl-auto=none means Hibernate will not do it for you.
--
--   mysql -u root -p ihealthpharm < backend/database/alter_encrypt_passwords.sql
--
-- Existing plaintext passwords are left as-is and are picked up automatically:
-- each account is transparently re-hashed to BCrypt the next time that user
-- logs in successfully (see EmployeeCredentialsImpl#upgradeLegacyPasswordIfNeeded).

ALTER TABLE `employee_credentials`
  MODIFY `CURRENT_PASSWORD` varchar(100) NOT NULL,
  MODIFY `PREVIOUS_PASSWORD1` varchar(100) DEFAULT NULL,
  MODIFY `PREVIOUS_PASSWORD2` varchar(100) DEFAULT NULL;
