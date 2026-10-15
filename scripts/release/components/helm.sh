#!/usr/bin/env bash
# Publish Helm chart for MockServer.
#
# Dry-run: package + lint chart, skip S3 upload + git push.

set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
source "$SCRIPT_DIR/_lib.sh"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --dry-run) DRY_RUN=true; shift ;;
    --execute) DRY_RUN=false; shift ;;
    -h|--help) echo "Usage: $0 [--dry-run|--execute]"; exit 0 ;;
    *) log_error "Unknown arg: $1"; exit 2 ;;
  esac
done

require_cmd docker
require_release_inputs
skip_unless_release_type "helm" full,post-maven

log_step "Publish Helm chart $RELEASE_VERSION (dry-run=$DRY_RUN)"
sync_to_origin_master

CHART_FILE="$REPO_ROOT/helm/mockserver/Chart.yaml"

log_info "Update Chart.yaml to $RELEASE_VERSION"
if is_dry_run; then
  log_dry "would: set version + appVersion in $CHART_FILE"
else
  sed -i.bak "s/^version: .*/version: \"$RELEASE_VERSION\"/" "$CHART_FILE"
  sed -i.bak "s/^appVersion: .*/appVersion: \"$RELEASE_VERSION\"/" "$CHART_FILE"
  rm -f "$CHART_FILE.bak"
fi

log_info "Lint chart"
in_docker "$HELM_IMAGE" -w /build -- lint ./helm/mockserver/

log_info "Package chart"
# In dry-run write to a scratch dir so we don't touch the tracked
# helm/charts/ (which holds the already-published release artifacts). We
# also pass --version/--app-version because Chart.yaml isn't bumped in
# dry-run, so otherwise the tgz would be named for the *previous* release
# and would clobber it.
if is_dry_run; then
  mkdir -p "$REPO_ROOT/.tmp/helm-charts"
  # --destination is relative to the container's /build workdir (the repo is
  # mounted at /build by in_docker), so .tmp/helm-charts/ here = $REPO_ROOT/.tmp/helm-charts/.
  in_docker "$HELM_IMAGE" -w /build -- package ./helm/mockserver/ \
    --version "$RELEASE_VERSION" --app-version "$RELEASE_VERSION" \
    --destination .tmp/helm-charts/
else
  mkdir -p "$REPO_ROOT/helm/charts"
  in_docker "$HELM_IMAGE" -w /build -- package ./helm/mockserver/ --destination helm/charts/
fi

if is_dry_run; then
  log_dry "skip: helm registry login + helm push to oci://ghcr.io/mock-server/charts"
  log_dry "skip: cosign sign of the OCI chart (only when mockserver-release/cosign-key is configured)"
  log_dry "skip: aws s3 download index, repack, upload, commit/push"
  log_info "Built chart: .tmp/helm-charts/mockserver-$RELEASE_VERSION.tgz"
else
  # Push to GHCR (OCI) before touching S3 so a transient GHCR failure aborts
  # the step before any S3 mutation. helm push is idempotent against the same
  # version tag, so a step retry after a mid-publish failure is safe.
  log_info "Push chart to GHCR (oci://ghcr.io/mock-server/charts)"
  # Write GHCR credentials to 0600 files under .tmp/ (mounted at /build in the
  # container) instead of passing via `docker run -e`. Env vars are readable
  # via /proc/1/environ and `docker inspect`; file-based secrets are not.
  GHCR_CREDS_FILE="$REPO_ROOT/.tmp/ghcr-creds.$$"
  ( umask 077
    printf '%s\n' "$(load_secret "mockserver-release/ghcr-token" "username")" > "$GHCR_CREDS_FILE"
    printf '%s\n' "$(load_secret "mockserver-release/ghcr-token" "token")"   >> "$GHCR_CREDS_FILE"
  )
  trap 'rm -f "$GHCR_CREDS_FILE"' EXIT
  in_docker "$HELM_IMAGE" --entrypoint sh -w /build \
    -- -ec '
      set +x
      GHCR_USERNAME=$(head -1 /build/.tmp/ghcr-creds.'$$')
      GHCR_TOKEN=$(tail -1 /build/.tmp/ghcr-creds.'$$')
      printf "%s" "$GHCR_TOKEN" | helm registry login ghcr.io \
        --username "$GHCR_USERNAME" --password-stdin
      helm push "helm/charts/mockserver-'"$RELEASE_VERSION"'.tgz" \
        oci://ghcr.io/mock-server/charts
    '

  # Optionally cosign-sign the pushed OCI chart so Artifact Hub shows the "Signed" badge.
  # NO-OP until a signing key is stored at mockserver-release/cosign-key (keys: key, password) —
  # the describe-secret guard skips this entirely otherwise, so current releases are unaffected.
  # Signing is additive and STRICTLY non-fatal: cosign_sign_chart handles every failure (IAM,
  # network, bad key, cosign error) explicitly and returns non-zero rather than letting `set -e`
  # abort the release — the chart is already pushed by this point.
  # Validate with scripts/release/test-cosign-signing.sh. See docs/infrastructure/helm.md.
  #
  # The private key is written to a 0600 file under .tmp/ (mounted at /build in the container) and
  # referenced by path, NOT passed via `docker run -e`, so the PEM never appears in the host process
  # table. The password stays in the env (useless without the key). The cosign binary is SHA256-pinned.
  cosign_sign_chart() {
    local key_file="$REPO_ROOT/.tmp/cosign-key.$$" pw_file="$REPO_ROOT/.tmp/cosign-pw.$$" rc=0
    mkdir -p "$REPO_ROOT/.tmp"
    ( umask 077; load_secret "mockserver-release/cosign-key" "key" > "$key_file" ) \
      || { rm -f "$key_file"; return 1; }
    ( umask 077; load_secret "mockserver-release/cosign-key" "password" > "$pw_file" ) \
      || { rm -f "$key_file" "$pw_file"; return 1; }
    # All secrets are file-based: GHCR creds in ghcr-creds.$$ (written above),
    # cosign key in cosign-key.$$, cosign password in cosign-pw.$$. The
    # container reads them from /build/.tmp/ (the repo mount). No `-e` for
    # secrets.
    # HARD with retry: all secrets (cosign-key, ghcr-token) exist, so signing is
    # expected to work. A transient blip (registry 5xx, cosign download hiccup)
    # is retried; a real signing failure sets rc=1 and is propagated so the
    # caller aborts the release rather than publishing an unsigned chart.
    retry 3 5 -- in_docker "$HELM_IMAGE" --entrypoint sh -w /build \
      -- -ec '
        set +x
        GHCR_USERNAME=$(head -1 /build/.tmp/ghcr-creds.'$$')
        GHCR_TOKEN=$(tail -1 /build/.tmp/ghcr-creds.'$$')
        export COSIGN_PASSWORD=$(cat /build/.tmp/cosign-pw.'$$')
        wget -qO /usr/local/bin/cosign "'"$(cosign_url linux-amd64)"'"
        echo "'"$(cosign_sha256 linux-amd64)"'  /usr/local/bin/cosign" | sha256sum -c -
        chmod +x /usr/local/bin/cosign
        printf "%s" "$GHCR_TOKEN" | cosign login ghcr.io --username "$GHCR_USERNAME" --password-stdin
        cosign sign --yes --key /build/.tmp/cosign-key.'$$' "ghcr.io/mock-server/charts/mockserver:'"$RELEASE_VERSION"'"
      ' || rc=1
    rm -f "$key_file" "$pw_file"
    return $rc
  }
  if aws secretsmanager describe-secret --region "$REGION" \
       --secret-id mockserver-release/cosign-key >/dev/null 2>&1; then
    log_info "Cosign-signing the OCI chart (mockserver-release/cosign-key found)"
    # HARD-fail: the presence-gate above decides WHETHER to sign; once the key
    # exists, a signing failure (after retries inside cosign_sign_chart) is a
    # real error and must abort the release rather than publishing an unsigned
    # chart.
    if cosign_sign_chart; then
      log_info "Chart signed with cosign"
    else
      log_error "cosign signing failed — chart could not be signed"
      exit 1
    fi
  else
    log_info "cosign key not configured (mockserver-release/cosign-key) — skipping chart signing; see docs/infrastructure/helm.md"
  fi

  log_info "Sync existing charts from S3"
  if [[ -z "${WEBSITE_BUCKET:-}" ]]; then
    log_error "WEBSITE_BUCKET not set"; exit 1
  fi
  # Subshell: assume_website_role exports website-account credentials. They
  # must not leak into git_commit_and_push below, which reads the GitHub token
  # from a build-account secret and so needs the agent's own credentials.
  (
    assume_website_role
    rm -f "$REPO_ROOT"/helm/charts/index.yaml
    aws s3 sync "s3://$WEBSITE_BUCKET/" "$REPO_ROOT/helm/charts/" \
      --exclude "*" --include "mockserver-*.tgz" --include "index.yaml"

    log_info "Rebuild Helm repo index"
    in_docker "$HELM_IMAGE" -w /build -- repo index helm/charts/ --url "https://www.mock-server.com"

    log_info "Upload to S3"
    # Upload every chart in helm/charts/, not just the new release. The
    # bucket is meant to hold all historical charts referenced by index.yaml,
    # but a new major/minor release lands in a freshly created bucket and
    # the versioned-site mirror has previously failed to carry the older
    # .tgz files across (see issue #2282). Syncing every chart on each run
    # makes the bucket self-heal: helm/charts/ is the canonical set, so any
    # gap created by a missed mirror is closed here.
    aws s3 sync "$REPO_ROOT/helm/charts/" "s3://$WEBSITE_BUCKET/" \
      --exclude "*" --include "mockserver-*.tgz"
    aws s3 cp "$REPO_ROOT/helm/charts/index.yaml" "s3://$WEBSITE_BUCKET/"
  )

  git_commit_and_push "release: Helm chart $RELEASE_VERSION" \
    helm/mockserver/Chart.yaml \
    "helm/charts/mockserver-$RELEASE_VERSION.tgz" \
    helm/charts/index.yaml
fi

log_info "Helm publish complete"
