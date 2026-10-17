-- ============================================================================
-- Two-factor authentication - schema
-- ============================================================================
--   mysql -u root -p ihealthpharm < backend/database/two_factor_schema.sql
--
-- Additive only. Every column and table defaults to two-factor being OFF, so
-- running this changes nothing about how anyone logs in. Accounts are switched
-- on one at a time afterwards (AUTH-HARDENING-PLAN.md section 6).
--
-- RUN employee_credentials_unique_username.sql FIRST. The columns added here
-- are per-username state, and until USER_NM is unique, findByUserName can
-- return either of two rows - so an address would be written to one and read
-- back from the other.
--
-- WHY A SECOND ADDRESS COLUMN RATHER THAN employee.EMAIL_ID
--
-- employee.EMAIL_ID is HR data. It was never collected to prove identity, and
-- it shows: of the ten accounts that actually log in, six hold no address at
-- all, and temp22214 holds m.gaman@docpharmkenya.com - another employee's
-- address. Had codes been sent to EMAIL_ID, that person's login codes would
-- have gone to a colleague's mailbox.
--
-- So the 2FA address is stored separately and is only ever written by the user
-- who proved they can read it. An edit on the HR screen cannot redirect
-- anyone's login codes, which it could if the two shared a column.
--
-- HOW AN ADDRESS GETS SET
--
-- The user sets it themselves, at their first login after 2FA is enabled for
-- their account, and it is not trusted until a code sent to it comes back.
-- An administrator can RESET an account (clearing the address, the verified
-- flag and every trusted device), after which the user enrols again - but an
-- administrator never types the address. That is deliberate: it keeps the
-- decision to allow a change with management while keeping the proof of
-- ownership with the person who owns the mailbox.
-- ============================================================================


-- ---------------------------------------------------------------------------
-- STEP 1 - per-account state.
--
-- All three default to the pre-2FA behaviour, so existing rows are unaffected.
-- ---------------------------------------------------------------------------
ALTER TABLE `employee_credentials`
  ADD COLUMN `TWO_FACTOR_ENABLED_S`        char(1)      DEFAULT 'N'
      COMMENT 'Y when this account is challenged for a code at login',
  ADD COLUMN `TWO_FACTOR_EMAIL`            varchar(100) DEFAULT NULL
      COMMENT 'Where login codes are sent. Set by the user, never by an administrator. Deliberately NOT unique - shared locum accounts may route to one supervisor',
  ADD COLUMN `TWO_FACTOR_EMAIL_VERIFIED_S` char(1)      DEFAULT 'N'
      COMMENT 'Y only once a code sent to TWO_FACTOR_EMAIL came back. No code is ever sent to an unverified address';


-- ---------------------------------------------------------------------------
-- STEP 2 - outstanding codes.
--
-- One row per code issued. The code itself is never stored: CODE_HASH holds a
-- BCrypt hash of it, because a six-digit code sitting in plaintext is a
-- password sitting in plaintext, and this table is readable by anyone who can
-- read the database.
--
-- CHALLENGE_TOKEN is the opaque handle given to the browser between the two
-- halves of a login. It identifies the challenge without naming the user, so
-- the pre-authentication step leaks nothing about who is logging in.
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `two_factor_challenge` (
  `TWO_FACTOR_CHALLENGE_ID` int          NOT NULL AUTO_INCREMENT,
  `EMPLOYEE_CREDENTIALS_ID` int          NOT NULL,

  `PURPOSE`                 varchar(20)  NOT NULL
                                         COMMENT 'LOGIN | EMAIL_VERIFY - enrolling an address, or proving a login',
  `CHALLENGE_TOKEN`         varchar(64)  NOT NULL COMMENT 'Opaque handle held by the browser between the two steps',
  `CODE_HASH`               varchar(100) NOT NULL COMMENT 'BCrypt hash of the six-digit code. Never the code itself',
  `TARGET_EMAIL`            varchar(100) DEFAULT NULL
                                         COMMENT 'Address the code was sent to. For EMAIL_VERIFY this is the address being proven, which is not yet on the account',

  `EXPIRES_TS`              timestamp    NOT NULL COMMENT 'Issued + 10 minutes',
  `ATTEMPT_COUNT`           int          NOT NULL DEFAULT 0 COMMENT 'Rejected once this passes the configured maximum',
  `CONSUMED_TS`             timestamp    NULL DEFAULT NULL COMMENT 'Set when accepted. A challenge is single use',

  -- audit columns, matching AuditModel
  `CREATION_TS`             timestamp    NULL DEFAULT NULL,
  `CREATION_USER_ID`        varchar(50)  DEFAULT NULL,
  `LAST_UPDATE_TS`          timestamp    NULL DEFAULT NULL,
  `LAST_UPDATE_USER_ID`     int          DEFAULT NULL,
  `AUDIT_ID`                int          DEFAULT NULL,

  PRIMARY KEY (`TWO_FACTOR_CHALLENGE_ID`),
  UNIQUE KEY `UK_2FA_CHALLENGE_TOKEN` (`CHALLENGE_TOKEN`),
  KEY `IDX_2FA_CHALLENGE_CREDENTIALS` (`EMPLOYEE_CREDENTIALS_ID`),
  KEY `IDX_2FA_CHALLENGE_EXPIRES` (`EXPIRES_TS`),
  CONSTRAINT `FK_2FA_CHALLENGE_CREDENTIALS`
      FOREIGN KEY (`EMPLOYEE_CREDENTIALS_ID`)
      REFERENCES `employee_credentials` (`EMPLOYEE_CREDENTIALS_ID`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


-- ---------------------------------------------------------------------------
-- STEP 3 - remembered machines.
--
-- This is what stops two-factor becoming a daily dependency on the mail
-- provider. A machine the user ticked "remember" on skips the code at the next
-- login, so staff see a code about once a month per machine rather than every
-- shift - and an SMTP outage cannot stop the pharmacy trading.
--
-- DEVICE_TOKEN_HASH is hashed for the same reason CODE_HASH is: the cookie
-- value is a bearer credential, and anyone holding it skips the second factor.
-- It is not a session token - on its own it grants nothing without the password.
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `two_factor_trusted_device` (
  `TRUSTED_DEVICE_ID`       int          NOT NULL AUTO_INCREMENT,
  `EMPLOYEE_CREDENTIALS_ID` int          NOT NULL,

  `DEVICE_TOKEN_HASH`       varchar(100) NOT NULL COMMENT 'BCrypt hash of the cookie value. Never the value itself',
  `LABEL`                   varchar(100) DEFAULT NULL COMMENT 'Browser and OS summary, so a person can recognise which machine this is',

  `EXPIRES_TS`              timestamp    NOT NULL COMMENT 'Issued + the configured window, 30 days by default',
  `LAST_USED_TS`            timestamp    NULL DEFAULT NULL,
  `REVOKED_TS`              timestamp    NULL DEFAULT NULL,
  `REVOKED_REASON`          varchar(30)  DEFAULT NULL COMMENT 'USER | ADMIN_RESET | PASSWORD_CHANGE',

  -- audit columns, matching AuditModel
  `CREATION_TS`             timestamp    NULL DEFAULT NULL,
  `CREATION_USER_ID`        varchar(50)  DEFAULT NULL,
  `LAST_UPDATE_TS`          timestamp    NULL DEFAULT NULL,
  `LAST_UPDATE_USER_ID`     int          DEFAULT NULL,
  `AUDIT_ID`                int          DEFAULT NULL,

  PRIMARY KEY (`TRUSTED_DEVICE_ID`),
  KEY `IDX_2FA_DEVICE_CREDENTIALS` (`EMPLOYEE_CREDENTIALS_ID`),
  KEY `IDX_2FA_DEVICE_TOKEN` (`DEVICE_TOKEN_HASH`),
  KEY `IDX_2FA_DEVICE_EXPIRES` (`EXPIRES_TS`),
  CONSTRAINT `FK_2FA_DEVICE_CREDENTIALS`
      FOREIGN KEY (`EMPLOYEE_CREDENTIALS_ID`)
      REFERENCES `employee_credentials` (`EMPLOYEE_CREDENTIALS_ID`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


-- ---------------------------------------------------------------------------
-- STEP 4 - a record of every administrator reset.
--
-- Resetting an account strips its second factor and logs out every machine it
-- trusted. Whoever holds that permission can do it to any account, so the only
-- real control on it is that each use is attributable afterwards.
--
-- This is a separate table rather than employee_credentials' own audit columns
-- because those hold only the LAST change and are overwritten by the next one -
-- which is not an audit trail. It is also not user_history: that records one
-- actor per row and a reset has two parties, the administrator and the subject.
--
-- The social engineering risk this exists to catch: "I've lost my email, please
-- reset my 2FA" from someone who already has the password. No code can prevent
-- that - only verifying the person, and being able to see afterwards who did.
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `two_factor_reset_audit` (
  `TWO_FACTOR_RESET_ID`     int          NOT NULL AUTO_INCREMENT,

  `EMPLOYEE_CREDENTIALS_ID` int          NOT NULL COMMENT 'The account that was reset',
  `RESET_BY_EMPLOYEE_ID`    int          DEFAULT NULL COMMENT 'The administrator who did it',
  `REASON`                  varchar(200) DEFAULT NULL COMMENT 'What they were told, in their words',

  `PREVIOUS_EMAIL`          varchar(100) DEFAULT NULL COMMENT 'The address that was cleared, so a mistaken reset can be understood later',
  `DEVICES_REVOKED`         int          NOT NULL DEFAULT 0 COMMENT 'How many remembered machines this logged out',

  -- audit columns, matching AuditModel
  `CREATION_TS`             timestamp    NULL DEFAULT NULL,
  `CREATION_USER_ID`        varchar(50)  DEFAULT NULL,
  `LAST_UPDATE_TS`          timestamp    NULL DEFAULT NULL,
  `LAST_UPDATE_USER_ID`     int          DEFAULT NULL,
  `AUDIT_ID`                int          DEFAULT NULL,

  PRIMARY KEY (`TWO_FACTOR_RESET_ID`),
  KEY `IDX_2FA_RESET_CREDENTIALS` (`EMPLOYEE_CREDENTIALS_ID`),
  KEY `IDX_2FA_RESET_BY` (`RESET_BY_EMPLOYEE_ID`),
  KEY `IDX_2FA_RESET_CREATED` (`CREATION_TS`),
  CONSTRAINT `FK_2FA_RESET_CREDENTIALS`
      FOREIGN KEY (`EMPLOYEE_CREDENTIALS_ID`)
      REFERENCES `employee_credentials` (`EMPLOYEE_CREDENTIALS_ID`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


-- ---------------------------------------------------------------------------
-- STEP 5 - confirm.
--
-- Expect: three new columns, three new tables, and every existing account with
-- two-factor off and no address.
-- ---------------------------------------------------------------------------
SELECT COUNT(*) AS new_columns
  FROM INFORMATION_SCHEMA.COLUMNS
 WHERE TABLE_SCHEMA = DATABASE()
   AND TABLE_NAME   = 'employee_credentials'
   AND COLUMN_NAME IN ('TWO_FACTOR_ENABLED_S', 'TWO_FACTOR_EMAIL', 'TWO_FACTOR_EMAIL_VERIFIED_S');

SELECT TABLE_NAME, TABLE_ROWS
  FROM INFORMATION_SCHEMA.TABLES
 WHERE TABLE_SCHEMA = DATABASE()
   AND TABLE_NAME IN ('two_factor_challenge', 'two_factor_trusted_device', 'two_factor_reset_audit');

SELECT SUM(TWO_FACTOR_ENABLED_S = 'Y') AS accounts_with_2fa_on,
       SUM(TWO_FACTOR_EMAIL IS NOT NULL) AS accounts_with_an_address
  FROM employee_credentials;


-- ---------------------------------------------------------------------------
-- TO REVERSE
--
--   DROP TABLE IF EXISTS `two_factor_reset_audit`;
--   DROP TABLE IF EXISTS `two_factor_trusted_device`;
--   DROP TABLE IF EXISTS `two_factor_challenge`;
--   ALTER TABLE `employee_credentials`
--     DROP COLUMN `TWO_FACTOR_ENABLED_S`,
--     DROP COLUMN `TWO_FACTOR_EMAIL`,
--     DROP COLUMN `TWO_FACTOR_EMAIL_VERIFIED_S`;
--
-- Safe at any point before accounts are switched on, since nothing else reads
-- these. After that it turns two-factor off for everyone who had it.
-- ---------------------------------------------------------------------------
