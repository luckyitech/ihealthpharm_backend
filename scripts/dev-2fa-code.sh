#!/usr/bin/env bash
#
# Prints the two-factor code currently waiting, and how long it has left.
# Development only - it reads the code from the backend log, which only
# contains codes because no MAIL_PASSWORD is configured. With real mail
# configured the code is never logged and this prints nothing.
#
#   scripts/dev-2fa-code.sh [username]
#
# Default username is the dev account.

set -uo pipefail
USER_NM="${1:-Lucky201}"
LOG="${BACKEND_LOG:-/private/tmp/claude-501/-Users-itec2026-Desktop-iHealth/9f4bd572-4d03-4b88-801f-bb597c9b623b/scratchpad/backend.log}"

# The database is the authority on what is still answerable; the log only
# supplies the digits, which it cannot be asked for.
read -r PURPOSE TARGET SECONDS_LEFT <<<"$(mysql -uroot -proot ihealthpharm -N -B -e "
  SELECT c.PURPOSE, IFNULL(c.TARGET_EMAIL,'-'),
         TIMESTAMPDIFF(SECOND, NOW(), c.EXPIRES_TS)
    FROM two_factor_challenge c
    JOIN employee_credentials e ON e.EMPLOYEE_CREDENTIALS_ID = c.EMPLOYEE_CREDENTIALS_ID
   WHERE e.USER_NM = '${USER_NM}'
     AND c.CONSUMED_TS IS NULL
     AND c.EXPIRES_TS > NOW()
     AND c.PURPOSE <> 'EMAIL_CAPTURE'
   ORDER BY c.TWO_FACTOR_CHALLENGE_ID DESC LIMIT 1;" 2>/dev/null)"

if [[ -z "${PURPOSE:-}" ]]; then
  echo "No code is waiting for ${USER_NM}."
  echo "Sign in at localhost:4200, or press 'Send a new code' if you are already on that step."
  exit 1
fi

CODE=$(grep -E "code for ${TARGET}:" "$LOG" | tail -1 | sed 's/.*: //')

echo
echo "  code:    ${CODE:-"(not in the log - is MAIL_PASSWORD set?)"}"
echo "  sent to: ${TARGET}"
echo "  expires: ${SECONDS_LEFT}s"
[[ "${SECONDS_LEFT}" -lt 15 ]] && echo "  ** nearly gone - press 'Send a new code' and run this again **"
echo
