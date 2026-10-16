-- ============================================================================
-- Who has a login, and what two-factor needs from each of them
-- ============================================================================
--   mysql -u root -p ihealthpharm < scripts/two-factor-rollout-list.sql
--
-- Read-only. Run it on production too - it is the list of people to collect
-- work email addresses from, and the order to switch them on in.
--
-- NEEDS is the column to act on:
--   ask for an email   - no work email on record; get one before switching on
--   ready to switch on - has a work email, two-factor still off
--   done               - switched on and able to receive codes
--   never used         - no login recorded; consider deactivating instead
-- ============================================================================

SELECT
    CONCAT(e.FIRST_NM, ' ', IFNULL(e.LAST_NM, ''))        AS name,
    ec.USER_NM                                            AS username,
    IFNULL(pr.ROLE_NM, '(no role)')                       AS role,

    -- What still has to happen for this person.
    CASE
        WHEN ec.ACTIVE_S <> 'Y'                   THEN 'account disabled'
        WHEN uh.logins IS NULL                    THEN 'never used - consider deactivating'
        WHEN ec.TWO_FACTOR_ENABLED_S = 'Y'
         AND ec.TWO_FACTOR_EMAIL IS NOT NULL      THEN 'done'
        WHEN ec.TWO_FACTOR_EMAIL IS NULL          THEN 'ask for an email'
        ELSE                                           'ready to switch on'
    END                                                   AS needs,

    IFNULL(ec.TWO_FACTOR_EMAIL, '—')                      AS work_email,
    IFNULL(NULLIF(TRIM(e.EMAIL_ID), ''), '—')             AS personal_email_hr,
    IFNULL(uh.logins, 0)                                  AS logins,
    IFNULL(DATE(uh.last_login), '—')                      AS last_login
FROM employee_credentials ec
JOIN employee e                       ON e.EMPLOYEE_ID = ec.EMPLOYEE_ID
LEFT JOIN employee_pharmacy_role epr  ON epr.EMPLOYEE_ID = e.EMPLOYEE_ID
LEFT JOIN pharmacy_roles pr           ON pr.ROLE_ID = epr.ROLE_ID
LEFT JOIN (
    SELECT USER_ID, COUNT(*) AS logins, MAX(CREATION_TS) AS last_login
      FROM user_history WHERE ACTION = 'login' GROUP BY USER_ID
) uh                                  ON uh.USER_ID = e.EMPLOYEE_ID
WHERE ec.ACTIVE_S = 'Y'
  -- Only people who have actually used the system. Drop this line to see the
  -- vendor test accounts too - there are about thirty of them.
  AND uh.logins IS NOT NULL
GROUP BY name, username, role, needs, work_email, personal_email_hr, logins, last_login
ORDER BY uh.last_login DESC;
