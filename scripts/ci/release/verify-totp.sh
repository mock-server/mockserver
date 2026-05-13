#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/common.sh"

require_cmd python3

log_step "Verifying TOTP authorization"

TOTP_CODE="${TOTP_CODE:-$(buildkite-agent meta-data get totp-code 2>/dev/null || echo '')}"

if [[ -z "$TOTP_CODE" ]]; then
  read -rp "Enter TOTP code: " TOTP_CODE
fi

if [[ ! "$TOTP_CODE" =~ ^[0-9]{6}$ ]]; then
  log_error "TOTP code must be exactly 6 digits"
  exit 1
fi

TOTP_SEED=$(load_secret "mockserver-release/totp-seed" "seed")

# Generate expected codes for current, previous, and next 30-second windows
# using Python stdlib (hmac + hashlib + struct) — no oathtool required
totp_code() {
  local offset="$1"
  python3 - "$TOTP_SEED" "$offset" << 'PYEOF'
import hmac, hashlib, struct, time, base64, sys

def totp(seed_b32, offset=0):
    seed_clean = seed_b32.upper().replace(' ', '').replace('-', '')
    padding = (8 - len(seed_clean) % 8) % 8
    seed_clean += '=' * padding
    key = base64.b32decode(seed_clean, casefold=True)
    counter = int(time.time() / 30) + offset
    msg = struct.pack('>Q', counter)
    h = hmac.new(key, msg, hashlib.sha1).digest()
    ob = h[-1] & 0x0F
    code = struct.unpack('>I', h[ob:ob+4])[0] & 0x7FFFFFFF
    return str(code % 1000000).zfill(6)

print(totp(sys.argv[1], int(sys.argv[2])))
PYEOF
}

EXPECTED=$(totp_code 0)
EXPECTED_PREV=$(totp_code -1)
EXPECTED_NEXT=$(totp_code 1)

if [[ "$TOTP_CODE" == "$EXPECTED" || "$TOTP_CODE" == "$EXPECTED_PREV" || "$TOTP_CODE" == "$EXPECTED_NEXT" ]]; then
  log_info "TOTP verified successfully"
else
  log_error "TOTP verification FAILED — code did not match current, previous, or next window"
  exit 1
fi
