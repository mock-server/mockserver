#!/usr/bin/env bash
# Test the release pipeline's cosign Helm-chart signing END-TO-END, locally,
# reading the key from Secrets Manager EXACTLY as scripts/release/components/helm.sh does.
#
# It sources the release library and calls the same load_secret + in_docker
# helpers the release uses, and runs the same in-container cosign block, so a
# pass here is strong evidence the release step will work.
#
# Two modes:
#   (default) preflight — NON-MUTATING. Loads the secret, proves the key+password
#             decrypt (cosign public-key), pulls the pinned cosign, logs in to GHCR.
#             Exercises everything except the final signature push.
#   --sign    Signs the real, already-published chart tags and verifies them.
#             Identical code path to the release; signing is additive (no cleanup),
#             and earns the Artifact Hub "Signed" badge immediately.
#
# Usage:
#   scripts/release/test-cosign-signing.sh                 # preflight only
#   scripts/release/test-cosign-signing.sh --sign          # sign + verify 6.1.0 and 6.0.0
#   scripts/release/test-cosign-signing.sh --sign --tags "6.1.0"
#   AWS_PROFILE=other scripts/release/test-cosign-signing.sh   # override profile
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# We are NOT doing a release, but we DO want load_secret to read the real
# Secrets Manager values (it returns placeholders in dry-run otherwise).
export LOAD_REAL_SECRETS_IN_DRY_RUN=1
# Local runs need an explicit profile; CI agents use an instance role (no profile).
export AWS_PROFILE="${AWS_PROFILE:-mockserver-build}"

# shellcheck source=/dev/null
source "$HERE/_lib.sh"   # provides load_secret, in_docker, HELM_IMAGE, REGION, log_*

DO_SIGN=false
# Example currently-published tags — update these as new versions are released,
# or override with --tags. Signing is idempotent, so re-signing is harmless.
TAGS="6.1.0 6.0.0"
COSIGN_SECRET="mockserver-release/cosign-key"
GHCR_SECRET="mockserver-release/ghcr-token"
CHART_REPO="ghcr.io/mock-server/charts/mockserver"

usage() {
  # Print the header comment block (everything after the shebang up to the first
  # non-comment line), stripping the leading "# ".
  awk 'NR==1{next} /^#/{sub(/^# ?/,"");print;next} {exit}' "${BASH_SOURCE[0]}"
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --sign)  DO_SIGN=true; shift ;;
    --tags)  TAGS="$2"; shift 2 ;;
    -h|--help) usage; exit 0 ;;
    *) log_error "Unknown arg: $1"; usage; exit 2 ;;
  esac
done

require_cmd aws
require_cmd docker
require_cmd jq

log_step "Cosign signing test (sign=$DO_SIGN, tags='$TAGS', region=$REGION, profile=$AWS_PROFILE)"

# 1. Secret must exist — same guard the release step uses.
if ! aws secretsmanager describe-secret \
       --region "$REGION" --profile "$AWS_PROFILE" \
       --secret-id "$COSIGN_SECRET" >/dev/null 2>&1; then
  log_error "Secret '$COSIGN_SECRET' not found in $REGION (profile $AWS_PROFILE)."
  log_error "Create it first — see docs/infrastructure/helm.md."
  exit 1
fi
log_info "Found $COSIGN_SECRET"

# 2. Load secrets via the SAME helper the release uses (proves the JSON shape parses).
COSIGN_KEY=$(load_secret "$COSIGN_SECRET" "key")
COSIGN_PASSWORD=$(load_secret "$COSIGN_SECRET" "password")
GHCR_USERNAME=$(load_secret "$GHCR_SECRET" "username")
GHCR_TOKEN=$(load_secret "$GHCR_SECRET" "token")

# cosign keys carry a "... PRIVATE KEY" PEM header — older builds emit
# "ENCRYPTED COSIGN PRIVATE KEY", newer ones "ENCRYPTED SIGSTORE PRIVATE KEY".
# Match the common suffix so either is accepted; cosign public-key below is the
# definitive check that the key + password actually decrypt.
case "$COSIGN_KEY" in
  *"PRIVATE KEY"*) : ;;
  *) log_error "Secret '.key' isn't a PEM private key (no 'PRIVATE KEY' header)."
     log_error "The secret must be JSON: {\"key\": \"<contents of cosign.key>\", \"password\": \"...\"}."
     exit 1 ;;
esac
if [[ -z "$GHCR_USERNAME" || "$GHCR_USERNAME" == "null" || -z "$GHCR_TOKEN" || "$GHCR_TOKEN" == "null" ]]; then
  log_error "Could not load GHCR username/token from $GHCR_SECRET."
  exit 1
fi
log_info "Loaded cosign key + password and GHCR credentials"

# 3. Run the same in-container path as the release step.
#    The private key is written to a 0600 file under the repo's .tmp/ (mounted at
#    /build in the container) and referenced by path — NOT passed via `docker run -e` —
#    so the PEM never appears in the host process table. Matches helm.sh.
KEY_FILE="$REPO_ROOT/.tmp/cosign-key-test.$$"
mkdir -p "$REPO_ROOT/.tmp"
trap 'rm -f "$KEY_FILE"' EXIT
( umask 077; printf "%s" "$COSIGN_KEY" > "$KEY_FILE" )

#    POSIX sh (alpine). The container runs the linux cosign for the host's arch,
#    so this runs NATIVELY on arm64 and amd64 hosts; the release pins
#    linux-amd64 for its amd64 agents. Version and checksums come from _lib.sh.
COSPLATFORM="$(cosign_host_platform)" || { log_error "no pinned cosign for this host's architecture"; exit 1; }
COSPLATFORM="linux-${COSPLATFORM#*-}"
in_docker "$HELM_IMAGE" --entrypoint sh -w /build \
  -e "COSURL=$(cosign_url "$COSPLATFORM")" -e "COSSHA=$(cosign_sha256 "$COSPLATFORM")" \
  -e "GHCR_USERNAME=$GHCR_USERNAME" --secret-env "GHCR_TOKEN=$GHCR_TOKEN" \
  --secret-env "COSIGN_PASSWORD=$COSIGN_PASSWORD" \
  -e "COSIGN_KEY_FILE=/build/.tmp/cosign-key-test.$$" \
  -e "DO_SIGN=$DO_SIGN" -e "TAGS=$TAGS" -e "CHART_REPO=$CHART_REPO" \
  -- -ec '
    set +x
    echo "--- [container] fetching cosign $COSURL"
    wget -qO /usr/local/bin/cosign "$COSURL"
    echo "$COSSHA  /usr/local/bin/cosign" | sha256sum -c -
    chmod +x /usr/local/bin/cosign
    cosign version

    echo "--- [container] proving the private key decrypts with the stored password"
    # cosign public-key fails unless the key + COSIGN_PASSWORD are a valid, matching pair.
    cosign public-key --key "$COSIGN_KEY_FILE" > /tmp/cosign.pub
    echo "--- [container] derived public key:"
    cat /tmp/cosign.pub
    if [ -f /build/helm/mockserver/cosign.pub ]; then
      if cmp -s /tmp/cosign.pub /build/helm/mockserver/cosign.pub; then
        echo "--- [container] OK: secret key matches committed helm/mockserver/cosign.pub"
      else
        echo "--- [container] WARNING: derived public key does NOT match committed helm/mockserver/cosign.pub"
      fi
    fi

    echo "--- [container] logging in to ghcr.io"
    printf "%s" "$GHCR_TOKEN" | cosign login ghcr.io --username "$GHCR_USERNAME" --password-stdin

    if [ "$DO_SIGN" = "true" ]; then
      for tag in $TAGS; do            # intentional word-split on space-separated TAGS
        ref="$CHART_REPO:$tag"
        echo "--- [container] signing $ref"
        cosign sign --yes --key "$COSIGN_KEY_FILE" "$ref"
        echo "--- [container] verifying $ref"
        cosign verify --key /tmp/cosign.pub "$ref" >/dev/null
        echo "--- [container] verified $ref"
      done
    else
      echo "--- [container] preflight only — NOT signing (no registry mutation)"
    fi
  '

if [[ "$DO_SIGN" == "true" ]]; then
  log_info ":white_check_mark: Signed + verified [$TAGS] via the release code path — the release step will work."
  log_info "Artifact Hub will show the Signed badge on its next scan."
else
  log_info ":white_check_mark: Preflight passed: secret loads, key+password decrypt, cosign fetched, GHCR login OK."
  log_info "Re-run with --sign to actually sign [$TAGS] (and earn the badge)."
fi
