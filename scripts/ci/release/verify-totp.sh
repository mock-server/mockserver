#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/common.sh"

require_cmd oathtool

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

EXPECTED=$(oathtool --totp -b "$TOTP_SEED")
EXPECTED_PREV=$(oathtool --totp -b -N "now - 30 seconds" "$TOTP_SEED")
EXPECTED_NEXT=$(oathtool --totp -b -N "now + 30 seconds" "$TOTP_SEED")

if [[ "$TOTP_CODE" == "$EXPECTED" || "$TOTP_CODE" == "$EXPECTED_PREV" || "$TOTP_CODE" == "$EXPECTED_NEXT" ]]; then
  log_info "TOTP verified successfully"
else
  log_error "TOTP verification FAILED"
  exit 1
fi
