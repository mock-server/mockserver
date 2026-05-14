#!/usr/bin/env bash
# Buildkite-only TOTP verification step. Validates the 6-digit code the
# operator entered in the block step before any release work begins.
#
# Reads TOTP_CODE from Buildkite meta-data and a base32 seed from AWS
# Secrets Manager (mockserver-release/totp-seed#seed).

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
source "$REPO_ROOT/scripts/release/_lib.sh"

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

# load_secret defaults to returning a placeholder in dry-run mode; force the
# real value here regardless of DRY_RUN.
DRY_RUN=false
TOTP_SEED=$(load_secret "mockserver-release/totp-seed" "seed")

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

# The Verify TOTP step runs on a release-queue agent that scales to zero.
# A cold-start agent takes ~1-3 minutes to provision (Lambda scaler runs
# every 60s + EC2 spot acquisition + agent registration + checkout). The
# operator entered the TOTP minutes before this script actually runs, so we
# accept any code from the last ~5 minutes (±10 × 30s windows). The
# `allowed_teams` GitHub gate on the block step is the primary access
# control; TOTP is the second factor and a wider verification window is a
# routine accommodation for clock drift / network latency.
TOTP_TOLERANCE_WINDOWS=10

matched=false
for offset in $(seq -"$TOTP_TOLERANCE_WINDOWS" "$TOTP_TOLERANCE_WINDOWS"); do
  if [[ "$TOTP_CODE" == "$(totp_code "$offset")" ]]; then
    matched=true
    log_info "TOTP verified successfully (window offset $offset)"
    break
  fi
done

if ! $matched; then
  log_error "TOTP verification FAILED — code did not match any window within ±$TOTP_TOLERANCE_WINDOWS (±$((TOTP_TOLERANCE_WINDOWS * 30))s)"
  exit 1
fi
