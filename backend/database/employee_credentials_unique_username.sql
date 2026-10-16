-- ============================================================================
-- employee_credentials - one row per username
-- ============================================================================
--   mysql -u root -p ihealthpharm < backend/database/employee_credentials_unique_username.sql
--
-- READ THIS BEFORE RUNNING. Step 2 carries ids taken from the 17.07.2026 dump.
-- Production is months ahead and may hold different or additional duplicates,
-- so STEP 1 must be run against production first and the id list in STEP 2
-- replaced with what it returns. Do not run this file top to bottom unattended.
--
-- THE PROBLEM
--
-- USER_NM has no uniqueness rule, and three usernames have two rows each. In
-- the dump: Nagesh157 (71, 72), Nagesh158 (73, 74) and Ramya160 (76, 77). Each
-- pair is an exact duplicate - same employee, same password, same approval PIN,
-- created in the same second by the same user in 2019 - so each is a
-- double-submit on the employee screen rather than two different accounts.
--
-- EmployeeCredentialsRepository.findByUserName returns a single
-- EmployeeCredentialsModel, so two matching rows make Spring Data throw
-- IncorrectResultSizeDataAccessException and the login returns 500. Those three
-- accounts cannot log in today. None of the six rows has ever logged in
-- (user_history holds nothing for employees 157, 158 or 160), so nobody has
-- noticed, and one of them is named "Nagesh Derivatives" - this is vendor test
-- data, not staff.
--
-- WHY IT MATTERS NOW
--
-- The two-factor work adds per-user columns to this table (the confirmed 2FA
-- email and whether it is enabled). With two rows for one username, "this
-- user's 2FA email" is ambiguous: it would be written to one row and read back
-- from the other, so a user would enter their address, see it saved, and be
-- asked again at the next login - or have their code mailed nowhere. The
-- uniqueness rule has to exist before those columns do.
--
-- WHY THE REDUNDANT ROW IS RENAMED AND NOT JUST DEACTIVATED
--
-- A UNIQUE constraint applies to every row regardless of ACTIVE_S, so setting
-- ACTIVE_S='N' alone leaves the username still duplicated and STEP 3 would
-- still fail. The redundant row has to stop holding the username. It is renamed
-- rather than deleted so the row, its audit columns and its history survive -
-- deletion is not needed here and is not reversible.
--
-- The surviving row of each pair is the lower id, which is the first inserted.
-- Since the pairs are identical in substance the choice does not affect
-- behaviour; it is fixed so the result is predictable.
--
-- Nothing references employee_credentials by foreign key, so neither the rename
-- nor the index disturbs another table.
-- ============================================================================


-- ----------------------------------------------------------------------------
-- STEP 1 - review. Run this against production and read the result.
--
-- Returns one row per duplicated username. KEEP_ID is the row that survives;
-- RENAME_IDS are the rows that give up the username. Blank usernames are
-- reported too, because more than one would also break STEP 3.
-- ----------------------------------------------------------------------------
SELECT
    IFNULL(NULLIF(TRIM(USER_NM), ''), '(blank)')        AS username,
    COUNT(*)                                            AS rows_held,
    MIN(EMPLOYEE_CREDENTIALS_ID)                        AS keep_id,
    GROUP_CONCAT(EMPLOYEE_CREDENTIALS_ID ORDER BY EMPLOYEE_CREDENTIALS_ID) AS all_ids,
    GROUP_CONCAT(
        CASE WHEN EMPLOYEE_CREDENTIALS_ID >
                  (SELECT MIN(i.EMPLOYEE_CREDENTIALS_ID)
                     FROM employee_credentials i
                    WHERE i.USER_NM <=> employee_credentials.USER_NM)
             THEN EMPLOYEE_CREDENTIALS_ID END
        ORDER BY EMPLOYEE_CREDENTIALS_ID)               AS rename_ids,
    COUNT(DISTINCT EMPLOYEE_ID)                         AS distinct_employees,
    COUNT(DISTINCT CURRENT_PASSWORD)                    AS distinct_passwords
FROM employee_credentials
GROUP BY USER_NM
HAVING COUNT(*) > 1
ORDER BY username;

-- If DISTINCT_EMPLOYEES or DISTINCT_PASSWORDS is greater than 1 for any row,
-- STOP. That pair is not a double-submit - it is two real accounts that happen
-- to share a username, and which one keeps it is a decision for the business,
-- not for this script.


-- ----------------------------------------------------------------------------
-- STEP 2 - release the username on the redundant rows.
--
-- The ids below are from the 17.07.2026 dump. REPLACE THEM with the RENAME_IDS
-- that STEP 1 returned on production. Ids are listed explicitly and never
-- derived from a predicate, so this statement can only touch rows that have
-- been looked at first.
--
-- The new username keeps the original plus the row's own id, which is unique by
-- construction and makes the row's origin obvious to whoever finds it later.
-- ACTIVE_S='N' because these accounts were never real.
-- ----------------------------------------------------------------------------
UPDATE employee_credentials
   SET USER_NM         = CONCAT(USER_NM, '~dup', EMPLOYEE_CREDENTIALS_ID),
       ACTIVE_S        = 'N',
       LAST_UPDATE_TS  = NOW()
 WHERE EMPLOYEE_CREDENTIALS_ID IN (72, 74, 77)   -- <-- REPLACE FROM STEP 1
   AND ACTIVE_S <> 'N';                          -- re-running changes nothing

-- Expected: 3 rows affected on the dump. A second run affects 0.


-- ----------------------------------------------------------------------------
-- STEP 3 - the rule that stops it happening again.
--
-- This is also the check on STEP 2: if any duplicate remains, this fails with
-- "Duplicate entry '<username>' for key" and names the username still at fault.
-- A failure here means STEP 2's id list was incomplete - go back to STEP 1.
-- ----------------------------------------------------------------------------
ALTER TABLE employee_credentials
  ADD CONSTRAINT uq_employee_credentials_user_nm UNIQUE (USER_NM);


-- ----------------------------------------------------------------------------
-- STEP 4 - confirm.
--
-- Expect zero rows. Anything returned means the constraint was not applied.
-- ----------------------------------------------------------------------------
SELECT USER_NM, COUNT(*) AS rows_held
FROM employee_credentials
GROUP BY USER_NM
HAVING COUNT(*) > 1;


-- ----------------------------------------------------------------------------
-- TO REVERSE
--
--   ALTER TABLE employee_credentials DROP INDEX uq_employee_credentials_user_nm;
--   UPDATE employee_credentials
--      SET USER_NM  = SUBSTRING_INDEX(USER_NM, '~dup', 1),
--          ACTIVE_S = 'Y'
--    WHERE EMPLOYEE_CREDENTIALS_ID IN (72, 74, 77);
--
-- Which restores the duplicates, and with them the 500 on those three logins.
-- ----------------------------------------------------------------------------
