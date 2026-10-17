-- ============================================================================
-- LOCAL DEVELOPMENT ONLY - a test employee for exercising two-factor
-- ============================================================================
--   mysql -u root -p ihealthpharm < scripts/dev-create-test-employee.sql
--
-- ***** NEVER RUN THIS ON PRODUCTION. *****
--
-- This creates DATA, not schema. The working agreement for this project is
-- "to production: code and schema migrations only, never data", and a login
-- account with a known password is the last thing that should ever reach a
-- live pharmacy system. It lives in scripts/ rather than backend/database/
-- for exactly that reason - nothing here is a migration.
--
-- WHY IT EXISTS
--
-- Testing two-factor on your own account risks locking yourself out of the
-- system you are testing with. This gives you somebody else to switch on,
-- enrol, reset and break, while you stay signed in as an administrator.
--
-- WHAT IT CREATES
--
--   employee                a person, Test Twofactor
--   employee_credentials    username testtwofa / password TestUser@2026
--                           approval PIN 135790
--   employee_pharmacy_role  PHARMACIST (role 4) at branch 1
--   employee_access         a copy of employee 201's permissions, so the
--                           account can actually open screens
--
-- The password is stored as PLAINTEXT on purpose. LegacyAwarePasswordEncoder
-- accepts it and EmployeeCredentialsImpl#upgradeLegacyPasswordIfNeeded
-- re-hashes it to BCrypt on the first successful login - so this account also
-- demonstrates the password migration working, which is worth seeing once.
--
-- Re-runnable: it removes its own rows first, so running it twice resets the
-- account to a clean state rather than failing or duplicating.
--
-- ROLE_ID 4 is deliberate. Five accounts in this database point at ROLE_ID 6,
-- which does not exist in pharmacy_roles, and they all 500 on login - see
-- AUTH-HARDENING-PLAN.md section 6c. Do not copy a role id without checking it
-- exists.
-- ============================================================================

SET @USER_NM   = 'testtwofa';
SET @PASSWORD  = 'TestUser@2026';
SET @PIN       = '135790';
SET @ROLE_ID   = 4;      -- PHARMACIST
SET @BRANCH_ID = 1;
SET @TEMPLATE  = 201;    -- whose permissions to copy


-- ---------------------------------------------------------------------------
-- Clean up a previous run. By username, which is unique, rather than by a
-- predicate over names - so this can only ever match the account it created.
-- ---------------------------------------------------------------------------
SET @EMP_ID = (SELECT EMPLOYEE_ID FROM employee_credentials WHERE USER_NM = @USER_NM);

DELETE FROM two_factor_trusted_device
 WHERE EMPLOYEE_CREDENTIALS_ID IN
       (SELECT EMPLOYEE_CREDENTIALS_ID FROM employee_credentials WHERE USER_NM = @USER_NM);
DELETE FROM two_factor_challenge
 WHERE EMPLOYEE_CREDENTIALS_ID IN
       (SELECT EMPLOYEE_CREDENTIALS_ID FROM employee_credentials WHERE USER_NM = @USER_NM);
DELETE FROM two_factor_reset_audit
 WHERE EMPLOYEE_CREDENTIALS_ID IN
       (SELECT EMPLOYEE_CREDENTIALS_ID FROM employee_credentials WHERE USER_NM = @USER_NM);
DELETE FROM employee_credentials WHERE USER_NM = @USER_NM;
DELETE FROM employee_access         WHERE EMPLOYEE_ID = @EMP_ID AND @EMP_ID IS NOT NULL;
DELETE FROM employee_pharmacy_role  WHERE EMPLOYEE_ID = @EMP_ID AND @EMP_ID IS NOT NULL;
DELETE FROM employee                WHERE EMPLOYEE_ID = @EMP_ID AND @EMP_ID IS NOT NULL;


-- ---------------------------------------------------------------------------
-- The person.
-- ---------------------------------------------------------------------------
INSERT INTO employee
  (FIRST_NM, LAST_NM, EMPLOYEE_CD, TITLE, GENDER_CD, ACTIVE_S, DESIGNATION_NM,
   EMAIL_ID, PHONE_NBR, PHARMACY_ID, CREATION_TS, CREATION_USER_ID)
VALUES
  ('Test', 'Twofactor', CONCAT('DPEMTEST', LPAD(FLOOR(RAND()*9999), 4, '0')),
   'Mr', 'M', 'Y', 'Test Account',
   -- No email on the employee record on purpose: the two-factor address is
   -- stored separately and is set by the user, never copied from HR data.
   NULL, NULL, 1, NOW(), 'dev-script');

SET @EMP_ID = LAST_INSERT_ID();


-- ---------------------------------------------------------------------------
-- The login. Plaintext password, upgraded to BCrypt on first successful login.
-- ---------------------------------------------------------------------------
INSERT INTO employee_credentials
  (EMPLOYEE_ID, USER_NM, CURRENT_PASSWORD, APPROVAL_ACCESS_PIN, ACTIVE_S,
   TWO_FACTOR_ENABLED_S, TWO_FACTOR_EMAIL, TWO_FACTOR_EMAIL_VERIFIED_S,
   CREATION_TS, CREATION_USER_ID)
VALUES
  (@EMP_ID, @USER_NM, @PASSWORD, @PIN, 'Y',
   -- Starts with two-factor OFF and no address, which is the state every real
   -- account is in before an administrator switches it on.
   'N', NULL, 'N',
   NOW(), 'dev-script');


-- ---------------------------------------------------------------------------
-- Role and permissions.
-- ---------------------------------------------------------------------------
INSERT INTO employee_pharmacy_role
  (EMPLOYEE_ID, PHARMACY_BRANCH_ID, ROLE_ID, CREATION_TS, CREATION_USER_ID)
VALUES
  (@EMP_ID, @BRANCH_ID, @ROLE_ID, NOW(), 'dev-script');

INSERT INTO employee_access (EMPLOYEE_ID, PHARMA_ACCESS_ID, ACTIVE_S, CREATION_TS, CREATION_USER_ID)
SELECT @EMP_ID, PHARMA_ACCESS_ID, ACTIVE_S, NOW(), 'dev-script'
  FROM employee_access WHERE EMPLOYEE_ID = @TEMPLATE;


-- ---------------------------------------------------------------------------
-- What was created.
-- ---------------------------------------------------------------------------
SELECT e.EMPLOYEE_ID        AS employee_id,
       CONCAT(e.FIRST_NM,' ',e.LAST_NM) AS name,
       ec.EMPLOYEE_CREDENTIALS_ID       AS credentials_id,
       ec.USER_NM                       AS username,
       ec.CURRENT_PASSWORD              AS password,
       ec.APPROVAL_ACCESS_PIN           AS approval_pin,
       ec.TWO_FACTOR_ENABLED_S          AS two_factor,
       pr.ROLE_NM                       AS role,
       (SELECT COUNT(*) FROM employee_access a WHERE a.EMPLOYEE_ID = e.EMPLOYEE_ID) AS permissions
  FROM employee e
  JOIN employee_credentials ec   ON ec.EMPLOYEE_ID = e.EMPLOYEE_ID
  LEFT JOIN employee_pharmacy_role epr ON epr.EMPLOYEE_ID = e.EMPLOYEE_ID
  LEFT JOIN pharmacy_roles pr          ON pr.ROLE_ID = epr.ROLE_ID
 WHERE ec.USER_NM = @USER_NM;


-- ---------------------------------------------------------------------------
-- TO REMOVE IT COMPLETELY
--
--   SET @E = (SELECT EMPLOYEE_ID FROM employee_credentials WHERE USER_NM='testtwofa');
--   DELETE FROM two_factor_trusted_device WHERE EMPLOYEE_CREDENTIALS_ID IN
--     (SELECT EMPLOYEE_CREDENTIALS_ID FROM employee_credentials WHERE USER_NM='testtwofa');
--   DELETE FROM two_factor_challenge WHERE EMPLOYEE_CREDENTIALS_ID IN
--     (SELECT EMPLOYEE_CREDENTIALS_ID FROM employee_credentials WHERE USER_NM='testtwofa');
--   DELETE FROM two_factor_reset_audit WHERE EMPLOYEE_CREDENTIALS_ID IN
--     (SELECT EMPLOYEE_CREDENTIALS_ID FROM employee_credentials WHERE USER_NM='testtwofa');
--   DELETE FROM employee_credentials WHERE USER_NM='testtwofa';
--   DELETE FROM employee_access        WHERE EMPLOYEE_ID=@E;
--   DELETE FROM employee_pharmacy_role WHERE EMPLOYEE_ID=@E;
--   DELETE FROM employee               WHERE EMPLOYEE_ID=@E;
-- ---------------------------------------------------------------------------
