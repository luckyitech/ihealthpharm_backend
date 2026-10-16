#!/usr/bin/env bash
#
# Sets up the development secrets once, so they survive new terminals and
# restarts and never have to be typed again.
#
#   scripts/dev-setup-secrets.sh
#
# Stores them in the macOS Keychain, not in a file, and adds a line to
# ~/.zshrc that reads them back. Nothing secret ends up in the repository or
# in a plaintext dotfile, and nothing is printed.
#
# Run it once. After that, starting the backend is just:
#
#   cd ihealthpharm_backend/backend && mvn spring-boot:run

set -uo pipefail

JAVA_11="/opt/homebrew/Cellar/openjdk@11/11.0.32.1/libexec/openjdk.jdk/Contents/Home"
MARKER="# iHealthPharm development secrets"

store() {
    local service="$1" prompt="$2" value
    if security find-generic-password -a "$USER" -s "$service" -w >/dev/null 2>&1; then
        printf "  %-28s already stored - keeping it\n" "$service"
        return
    fi
    printf "  %s" "$prompt"
    read -rs value
    echo
    if [[ -z "$value" ]]; then
        echo "     nothing entered - skipped"
        return
    fi
    security add-generic-password -a "$USER" -s "$service" -w "$value" -U
    printf "     stored (%d characters)\n" "${#value}"
}

echo
echo "Storing development secrets in the macOS Keychain."
echo

store "ihealthpharm-mail" "Mail password for security@docpharmkenya.com: "

# The signing key is generated rather than asked for: it only has to be long
# and random, and a stable one means the browser session survives restarts.
if ! security find-generic-password -a "$USER" -s "ihealthpharm-jwt" -w >/dev/null 2>&1; then
    security add-generic-password -a "$USER" -s "ihealthpharm-jwt" \
        -w "$(openssl rand -base64 64 | tr -d '\n')" -U
    echo "  ihealthpharm-jwt             generated and stored"
else
    echo "  ihealthpharm-jwt             already stored - keeping it"
fi

# Replace any previous block rather than appending a second one.
if grep -q "$MARKER" ~/.zshrc 2>/dev/null; then
    python3 - "$MARKER" <<'PY'
import io, os, re, sys
marker = sys.argv[1]
path = os.path.expanduser("~/.zshrc")
text = io.open(path, encoding="utf-8").read()
text = re.sub(re.escape(marker) + r".*?# end iHealthPharm\n", "", text, flags=re.S)
io.open(path, "w", encoding="utf-8").write(text)
PY
fi

cat >> ~/.zshrc <<EOF
$MARKER
export JAVA_HOME="$JAVA_11"
export TWOFACTOR_MAIL_PASSWORD="\$(security find-generic-password -a "\$USER" -s ihealthpharm-mail -w 2>/dev/null)"
export JWT_SIGNING_SECRET="\$(security find-generic-password -a "\$USER" -s ihealthpharm-jwt -w 2>/dev/null)"
# end iHealthPharm
EOF

echo
echo "  ~/.zshrc updated."
echo
echo "  Now run:   source ~/.zshrc"
echo "  Check:     echo \"mail \${#TWOFACTOR_MAIL_PASSWORD}  jwt \${#JWT_SIGNING_SECRET}\""
echo "             expect: mail 16  jwt 88"
echo
echo "  Then start the backend in that same terminal:"
echo "    cd $(cd "$(dirname "$0")/.." && pwd)/backend && mvn spring-boot:run"
echo
