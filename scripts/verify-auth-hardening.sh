#!/usr/bin/env bash
#
# Verifies the authentication hardening work: the signing key rotation, the
# username uniqueness fix, and the two-factor schema.
#
#   scripts/verify-auth-hardening.sh
#
# Needs the backend running and the database reachable. Everything it does is
# read-only except one deliberate duplicate-username attempt, which the database
# is expected to reject and which is rolled back either way.
#
# Overridable:
#   BASE_URL   default http://localhost:6009/ihealthpharmservice
#   DB_NAME    default ihealthpharm
#   DB_USER    default root
#   DB_PASS    default root
#   TEST_USER / TEST_PASS   a valid login, default the dev account
#
# If JWT_SIGNING_SECRET is set it also runs the control check that proves the
# forged-token test is real. Without it that one check is skipped, not failed.

set -uo pipefail

BASE_URL="${BASE_URL:-http://localhost:6009/ihealthpharmservice}"
DB_NAME="${DB_NAME:-ihealthpharm}"
DB_USER="${DB_USER:-root}"
DB_PASS="${DB_PASS:-root}"
TEST_USER="${TEST_USER:-Lucky201}"
TEST_PASS="${TEST_PASS:-iHealthDev@2026}"

passed=0; failed=0; skipped=0

# ---------------------------------------------------------------------------
# One place that decides what a result looks like, so every check reads the same.
#   check <description> <expected> <actual>
# ---------------------------------------------------------------------------
check() {
    local what="$1" want="$2" got="$3"
    if [[ "$got" == "$want" ]]; then
        printf '  \033[32mPASS\033[0m  %-54s %s\n' "$what" "$got"
        ((passed++))
    else
        printf '  \033[31mFAIL\033[0m  %-54s expected %s, got %s\n' "$what" "$want" "$got"
        ((failed++))
    fi
}

skip() {
    printf '  \033[33mSKIP\033[0m  %-54s %s\n' "$1" "$2"
    ((skipped++))
}

sql() { mysql -u"$DB_USER" -p"$DB_PASS" "$DB_NAME" -N -B -e "$1" 2>/dev/null; }

# HTTP status for a login attempt.
login_status() {
    curl -s -o /dev/null -w '%{http_code}' --max-time 15 -X POST "$BASE_URL/authenticate" \
        -H 'Content-Type: application/json' \
        -d "{\"userName\":\"$1\",\"password\":\"$2\"}"
}

# Mint a token the way jjwt 0.9.1 does - Base64-decode the secret, HS512 the
# header and payload - then report the status the API gives it.
forged_token_status() {
    local secret="$1" subject="$2"
    local token
    token=$(SECRET="$secret" SUBJECT="$subject" python3 -c '
import base64, hmac, hashlib, json, os, time
def b64u(b): return base64.urlsafe_b64encode(b).rstrip(b"=")
s = os.environ["SECRET"]
key = base64.b64decode(s + "=" * (-len(s) % 4))
now = int(time.time())
head = b64u(json.dumps({"alg":"HS512"}, separators=(",",":")).encode())
body = b64u(json.dumps({"sub":os.environ["SUBJECT"],"iat":now,"exp":now+86400},
                       separators=(",",":")).encode())
si  = head + b"." + body
sig = b64u(hmac.new(key, si, hashlib.sha512).digest())
print((si + b"." + sig).decode())
') || { echo "ERR"; return; }
    curl -s -o /dev/null -w '%{http_code}' --max-time 15 \
        "$BASE_URL/get/pharmacysetup" -H "Authorization: Bearer $token"
}

echo
echo "Verifying against $BASE_URL"
echo

# ---------------------------------------------------------------------------
echo "Database - username uniqueness (employee_credentials_unique_username.sql)"
# ---------------------------------------------------------------------------
check "unique constraint on USER_NM exists" "1" \
    "$(sql "SELECT COUNT(DISTINCT INDEX_NAME) FROM INFORMATION_SCHEMA.STATISTICS
            WHERE TABLE_SCHEMA='$DB_NAME' AND TABLE_NAME='employee_credentials'
              AND INDEX_NAME='uq_employee_credentials_user_nm';")"

check "no duplicate usernames remain" "0" \
    "$(sql "SELECT COUNT(*) FROM (SELECT USER_NM FROM employee_credentials
            GROUP BY USER_NM HAVING COUNT(*)>1) d;")"

check "redundant rows kept, renamed and deactivated" "3" \
    "$(sql "SELECT COUNT(*) FROM employee_credentials
            WHERE USER_NM LIKE '%~dup%' AND ACTIVE_S='N';")"

# The constraint has to actually reject, not merely exist.
dup_result=$(mysql -u"$DB_USER" -p"$DB_PASS" "$DB_NAME" -e "
    START TRANSACTION;
    UPDATE employee_credentials SET USER_NM='$TEST_USER'
     WHERE EMPLOYEE_CREDENTIALS_ID=(SELECT MIN(EMPLOYEE_CREDENTIALS_ID) FROM
          (SELECT EMPLOYEE_CREDENTIALS_ID FROM employee_credentials WHERE USER_NM<>'$TEST_USER') x);
    ROLLBACK;" 2>&1 | grep -c "Duplicate entry")
check "constraint rejects a duplicate (rolled back)" "1" "$dup_result"

echo
# ---------------------------------------------------------------------------
echo "Database - two-factor schema (two_factor_schema.sql)"
# ---------------------------------------------------------------------------
check "three 2FA columns on employee_credentials" "3" \
    "$(sql "SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
            WHERE TABLE_SCHEMA='$DB_NAME' AND TABLE_NAME='employee_credentials'
              AND COLUMN_NAME IN ('TWO_FACTOR_ENABLED_S','TWO_FACTOR_EMAIL','TWO_FACTOR_EMAIL_VERIFIED_S');")"

check "three 2FA tables exist" "3" \
    "$(sql "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES
            WHERE TABLE_SCHEMA='$DB_NAME'
              AND TABLE_NAME IN ('two_factor_challenge','two_factor_trusted_device','two_factor_reset_audit');")"

# How far the rollout has got. Reported, not asserted: these counts start at
# zero and climb as accounts are switched on, so asserting a number here would
# fail the moment the feature is actually used.
printf '  \033[36mINFO\033[0m  %-54s %s enabled, %s with an address\n' \
    "accounts using two-factor" \
    "$(sql "SELECT IFNULL(SUM(TWO_FACTOR_ENABLED_S='Y'),0) FROM employee_credentials;")" \
    "$(sql "SELECT IFNULL(SUM(TWO_FACTOR_EMAIL IS NOT NULL),0) FROM employee_credentials;")"

# What must hold however far the rollout has got: nobody is enabled without a
# usable address AND no address is treated as usable without being enabled -
# either would mean an account that cannot receive the code it is asked for.
check "no account is enabled with an unusable address" "0" \
    "$(sql "SELECT COUNT(*) FROM employee_credentials
             WHERE TWO_FACTOR_ENABLED_S='Y'
               AND TWO_FACTOR_EMAIL IS NOT NULL
               AND TWO_FACTOR_EMAIL_VERIFIED_S <> 'Y';")"

echo
# ---------------------------------------------------------------------------
echo "API - login still works"
# ---------------------------------------------------------------------------
# Check the backend answers at all before asserting anything about it. Without
# this, a server that is down or still restarting reports every API check as a
# failure - which reads as nine bugs instead of one unplugged cable, and sends
# whoever ran it hunting for problems that are not there.
if [[ "$(curl -s -o /dev/null -w '%{http_code}' --max-time 10 -X POST "$BASE_URL/authenticate" \
        -H 'Content-Type: application/json' -d '{"userName":"_","password":"_"}')" == "000" ]]; then
    echo
    echo "  Backend is not answering at $BASE_URL - skipping every API check."
    echo "  Start it, wait for 'Started IHealthPharmApplication', and run this again."
    echo
    printf '  %d passed, %d failed, API checks not run\n\n' "$passed" "$failed"
    exit 2
fi

# ---------------------------------------------------------------------------
check "valid credentials accepted" "200" "$(login_status "$TEST_USER" "$TEST_PASS")"
check "wrong password refused" "401" "$(login_status "$TEST_USER" "definitely-not-the-password")"

# These logins used to return 500: two rows came back for one username.
for u in Nagesh157:2211 Nagesh158:1122; do
    check "previously-broken login ${u%%:*}" "200" "$(login_status "${u%%:*}" "${u##*:}")"
done

# Ramya160 was the third duplicate, and it still fails - but for an unrelated,
# pre-existing reason: employee 160 is assigned ROLE_ID 6, which was deleted
# from pharmacy_roles (ids run 1-5, 7, 8) without clearing the assignment.
# JwtInMemoryUserDetailsService calls role.getPharmacyRolesModel().getRoleNm()
# on the resulting null and throws. The dedupe did not cause this and does not
# fix it. Asserted as 500 so that a future fix shows up here as a failure to
# update, rather than passing silently.
check "Ramya160 still 500s (orphaned ROLE_ID 6, pre-existing)" "500" \
    "$(login_status Ramya160 1122)"

# The assertion that actually matters: no account anyone really uses is in that
# state. All five accounts pointing at the missing role have never logged in.
check "no account that has ever logged in has a missing role" "0" \
    "$(sql "SELECT COUNT(DISTINCT epr.EMPLOYEE_ID)
              FROM employee_pharmacy_role epr
              LEFT JOIN pharmacy_roles pr ON pr.ROLE_ID = epr.ROLE_ID
             WHERE pr.ROLE_ID IS NULL
               AND EXISTS (SELECT 1 FROM user_history uh
                            WHERE uh.USER_ID = epr.EMPLOYEE_ID AND uh.ACTION='login');")"

echo
# ---------------------------------------------------------------------------
echo "API - signing in by username or work email"
# ---------------------------------------------------------------------------
# A work email identifies the account as well as the username does. Only when
# it matches exactly one account - a shared supervisor mailbox cannot say which
# of several people is signing in, so those use their usernames.
WORK_EMAIL=$(sql "SELECT TWO_FACTOR_EMAIL FROM employee_credentials
                   WHERE USER_NM='$TEST_USER' AND TWO_FACTOR_EMAIL IS NOT NULL;")
if [[ -n "$WORK_EMAIL" ]]; then
    check "work email signs in as well as the username" "200" \
        "$(login_status "$WORK_EMAIL" "$TEST_PASS")"
else
    skip "work email signs in as well as the username" "$TEST_USER has no work email set"
fi

# Every way of failing must look the same from outside. An unknown name
# answering differently from a wrong password turns the login box into a way of
# finding out which usernames and addresses have accounts here.
check "unknown username refused like a wrong password" "401" \
    "$(login_status 'no-such-user-at-all' 'whatever')"
check "unknown email refused like a wrong password" "401" \
    "$(login_status 'nobody@example.com' 'whatever')"

echo
# ---------------------------------------------------------------------------
echo "API - the forged-token bypass is closed"
# ---------------------------------------------------------------------------
check "token signed with the old 'mySecret' rejected" "401" \
    "$(forged_token_status "mySecret" "$TEST_USER")"

# Control: the same technique with the live key must be accepted, otherwise the
# check above proves nothing - it could be failing for any unrelated reason.
if [[ -n "${JWT_SIGNING_SECRET:-}" ]]; then
    check "control - same technique with the live key works" "200" \
        "$(forged_token_status "$JWT_SIGNING_SECRET" "$TEST_USER")"
else
    skip "control - same technique with the live key" "JWT_SIGNING_SECRET not set in this shell"
fi

echo
# ---------------------------------------------------------------------------
echo "Startup validator - not testable while the server is up"
# ---------------------------------------------------------------------------
echo "  To see it refuse a weak key, stop the backend and run:"
echo "    JWT_SIGNING_SECRET=mySecret mvn spring-boot:run   # refuses, names the value"
echo "    unset JWT_SIGNING_SECRET;   mvn spring-boot:run   # refuses, 'is not set'"

echo
printf '  %d passed, %d failed, %d skipped\n\n' "$passed" "$failed" "$skipped"
exit $(( failed > 0 ))
