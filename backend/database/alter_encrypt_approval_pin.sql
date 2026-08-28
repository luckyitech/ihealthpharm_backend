-- ============================================================================
-- Widen APPROVAL_ACCESS_PIN so it can hold a BCrypt hash
-- ============================================================================
-- The PIN is currently varchar(6) holding the digits in the clear. Nothing in
-- the system verified it, so it was only ever stored - credit settlement is the
-- first feature to actually check it, and it should not check a plaintext value.
--
--   mysql -u root -p ihealthpharm < backend/database/alter_encrypt_approval_pin.sql
--
-- Existing PINs are left as they are and keep working: each one is re-hashed to
-- BCrypt the first time its owner enters it correctly, the same way passwords
-- were migrated (see EmployeeCredentialsImpl#upgradeLegacyApprovalPinIfNeeded).
-- ============================================================================

ALTER TABLE `employee_credentials`
  MODIFY `APPROVAL_ACCESS_PIN` varchar(100) DEFAULT NULL;
