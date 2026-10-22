#!/usr/bin/env bash
# ──────────────────────────────────────────────────────────────────────
# Fail closed when a NEW shared-RNG call site appears on a server hot path.
#
# WHY THIS EXISTS
#
# Three separate sweeps removed shared-random-number-generator contention from
# MockServer's request hot paths, each by ENUMERATING one call shape:
#   • UUIDService.getUUID()       — 26 sites switched to getNonSecureUUID()
#   • java.util.UUID.randomUUID() — 13 files switched (draws from the JDK's
#                                   own shared static SecureRandom)
#   • Math.random() / new Random()— the third tier (this sweep)
# Every sweep found exactly what it searched for and nothing else, so the NEXT
# shape was invisible until someone thought to grep for it. The enumeration
# method — not the judgement — was the defect, twice. A grep-once fix does not
# stop the shape reappearing; only a standing, always-on control does.
#
# All four of these delegate to a process-wide generator whose core step is a
# synchronized/AtomicLong-CAS loop:
#   • Math.random()          → one shared static java.util.Random
#   • new Random()  (no arg) → seeds from the shared static seedUniquifier CAS
#                              loop AND allocates a generator per call
#   • UUID.randomUUID()      → the JDK's shared static SecureRandom (native
#                              PRNG monitor)
#   • new SecureRandom(...)  → a fresh SecureRandom; flagged so a new one on a
#                              hot path is a conscious, reviewed decision
# On the request path that monitor/CAS serialises the worker event loops under
# load. The contention-free alternatives are ThreadLocalRandom.current() (for a
# value that needs only uniqueness) and UUIDService.getNonSecureUUID() (for an
# internal id). A value whose UNGUESSABILITY is a genuine security property
# keeps its SecureRandom / secure UUID and is allow-listed below with the reason.
#
# Seeded new Random(seed) is deliberately NOT matched: the seeded constructor
# bypasses seedUniquifier, does not contend, and is load-bearing for
# reproducibility (e.g. EmbeddingVectors.seededFallbackVector,
# StreamingPhysicsExpander). Only the no-arg new Random() contends.
#
# SCOPE
#
# The server RUNTIME modules that carry the request hot path: mockserver-core,
# mockserver-netty, mockserver-async (src/main only). Deliberately EXCLUDED:
#   • mockserver-integration-testing/src/main — test-support code, not runtime;
#     its UUID.randomUUID() calls are test assertions, pure noise here.
#   • mockserver-client-java — a client library, not the server hot path.
# Pure comment lines (leading //, * or /*) and any text after a // on a code line are
# stripped before matching, so a pattern named in such a comment never trips the guard.
# An inline C-style /* ... */ span on a code line is NOT stripped, so a pattern named
# inside one would FALSE-POSITIVE (fail the build, noisily) — never a false negative,
# since commented-out code does not run. Rare enough to leave as-is; move such a mention
# to a // comment if it trips.
#
# KNOWN LIMITATION (stated honestly, as check-false-green-guards.sh does)
#
# The allow-list is keyed by FILE, not by line, so it is robust to line drift but
# it will NOT catch a second, new bad use ADDED to a file already allow-listed for
# that pattern. That trade buys zero false positives and no line-number churn. The
# high-value case — a brand-new RNG call site in a file that has none today — is
# exactly what the earlier sweeps missed, and that IS caught: a new site lands in a
# not-yet-listed file and fails the build. A rot check (below) fails the build if
# any allow-list entry no longer matches, so the list can never quietly go stale.
#
# Requires: git, grep, awk. Runs directly on the agent (no Docker). Bash 3.2 safe.
# ──────────────────────────────────────────────────────────────────────
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
cd "$REPO_ROOT"

errors=0

# Server-runtime source roots to scan (see SCOPE above).
SCOPE_DIRS=(
  "mockserver/mockserver-core/src/main"
  "mockserver/mockserver-netty/src/main"
  "mockserver/mockserver-async/src/main"
)

# The contending shapes. Each is "<KIND>|<ERE>|<LITERAL>": the ERE is what grep
# scans for; the LITERAL is the same match as a plain string, used by awk's index()
# to re-confirm a hit survives comment-stripping (awk mangles a regex passed via -v,
# and a literal needs no escaping). new Random\(\) matches ONLY the no-arg (unseeded)
# constructor — seeded new Random(seed) is intentionally left out.
PATTERNS=(
  "Math.random()|Math\.random\(|Math.random("
  "new Random() (unseeded)|new Random\(\)|new Random()"
  "new SecureRandom(...)|new SecureRandom\(|new SecureRandom("
  "UUID.randomUUID()|UUID\.randomUUID\(|UUID.randomUUID("
)

# ══════════════════════════════════════════════════════════════════════
# Allow-list — every legitimate current site, each with a one-line reason.
# Format: "<repo-relative path>|<KIND>|<reason>". KIND must exactly match a
# PATTERNS label above. A file may appear once per KIND it legitimately uses.
# ══════════════════════════════════════════════════════════════════════
ALLOWLIST=(
  # ── new Random() (unseeded) ──────────────────────────────────────────
  "mockserver/mockserver-core/src/main/java/org/mockserver/socket/PortFactory.java|new Random() (unseeded)|free-port scanner used at server/proxy startup to pick a bind port, not the request path; needs uniqueness not unguessability and is not hot enough to contend"

  # ── new SecureRandom(...) ────────────────────────────────────────────
  "mockserver/mockserver-core/src/main/java/org/mockserver/uuid/UUIDService.java|new SecureRandom(...)|seeds the canonical secure UUID generator — this IS the secure source getUUID() returns; getNonSecureUUID() (ThreadLocalRandom) is the contention-free alternative for hot-path ids"
  "mockserver/mockserver-core/src/main/java/org/mockserver/oidc/OidcDeviceAuthorizationCallback.java|new SecureRandom(...)|RFC 8628 user_code a human reads and types to approve the device — unguessability is the security property; once per device-auth request, not a per-item loop"
  "mockserver/mockserver-core/src/main/java/org/mockserver/templates/engine/TemplateFunctions.java|new SecureRandom(...)|rand() exposed to user response templates; must stay unpredictable and is user-invoked, not an internal hot-path id"
  "mockserver/mockserver-core/src/main/java/org/mockserver/socket/tls/KeyAndCertificateFactory.java|new SecureRandom(...)|X.509 certificate serial number; per generated cert (rare) and its unpredictability is a security property"
  "mockserver/mockserver-netty/src/main/java/org/mockserver/netty/http3/SourceAddressQuicTokenHandler.java|new SecureRandom(...)|QUIC source-address retry-token secret; unguessability is the anti-spoofing property"
  "mockserver/mockserver-core/src/main/java/org/mockserver/authentication/basic/PasswordHash.java|new SecureRandom(...)|password-hash salts and the control-plane Basic handler's HMAC key; unguessability is the security property, used once per handler build or hash generated, never per request"

  # ── UUID.randomUUID() ────────────────────────────────────────────────
  "mockserver/mockserver-core/src/main/java/org/mockserver/oidc/OidcKeyProvider.java|UUID.randomUUID()|OIDC signing-key id fallback, only when no keyId is configured; per-provider setup, cold"
  "mockserver/mockserver-core/src/main/java/org/mockserver/keys/AsymmetricKeyGenerator.java|UUID.randomUUID()|generated key-pair id, only produced when a key is generated (unconfigured); cold"
  "mockserver/mockserver-core/src/main/java/org/mockserver/mock/action/http/LoadScenarioOrchestrator.java|UUID.randomUUID()|load-scenario run id; once per scenario run, not per request"
  "mockserver/mockserver-core/src/main/java/org/mockserver/echo/tls/UniqueCertificateChainSSLContextBuilder.java|UUID.randomUUID()|temp directory name for a one-off echo-server TLS context; setup-time, cold"
)

# Returns 0 if <path> is allow-listed for <kind>.
is_allowed() {
  local path="$1" kind="$2" entry e_path e_kind
  for entry in "${ALLOWLIST[@]}"; do
    e_path="${entry%%|*}"
    e_kind="${entry#*|}"; e_kind="${e_kind%%|*}"
    if [ "$path" = "$e_path" ] && [ "$kind" = "$e_kind" ]; then
      return 0
    fi
  done
  return 1
}

# Emit "path:line:code" hits for <ere> across the scope, with comments stripped:
# drop pure-comment lines (leading // * /*) and cut any trailing // comment off a
# code line, then re-test the pattern so a match that lived only in a comment is
# discarded. Prints nothing (and does not fail) when there are no hits.
scan() {
  local ere="$1" lit="$2" dir
  local existing=()
  for dir in "${SCOPE_DIRS[@]}"; do
    [ -d "$dir" ] && existing+=("$dir")
  done
  [ ${#existing[@]} -eq 0 ] && return 0
  # grep exits 1 on no match; under `set -o pipefail` that would abort the caller,
  # so absorb it — a zero-match shape (e.g. Math.random() after this sweep) is the
  # healthy state, not an error.
  { grep -rnE --include='*.java' "$ere" "${existing[@]}" 2>/dev/null || true; } \
    | awk -v lit="$lit" '
        {
          # $0 == path:line:code ; split off path and line, keep code verbatim
          p = index($0, ":"); path = substr($0, 1, p-1); rest = substr($0, p+1);
          q = index(rest, ":"); lineno = substr(rest, 1, q-1); code = substr(rest, q+1);
          trimmed = code; sub(/^[ \t]+/, "", trimmed);
          if (trimmed ~ /^(\/\/|\*|\/\*)/) next;       # pure comment line
          sub(/\/\/.*/, "", code);                      # cut trailing // comment
          if (index(code, lit) == 0) next;             # match was only in the comment
          print path ":" lineno ":" code;
        }'
}

echo "--- :game_die: guard: no NEW shared-RNG call site on a server hot path"

# Scan each shape ONCE (recursive greps dominate the runtime) into a single tagged
# hit file, "<KIND>\t<path>:<line>:<code>" per line, reused by both checks below.
HITS_FILE="$(mktemp "${TMPDIR:-/tmp}/rng-guard.XXXXXX")"
trap 'rm -f "$HITS_FILE"' EXIT
for spec in "${PATTERNS[@]}"; do
  kind="${spec%%|*}"
  rest="${spec#*|}"
  ere="${rest%%|*}"
  lit="${rest#*|}"
  scan "$ere" "$lit" | awk -v k="$kind" 'NF{print k "\t" $0}' >> "$HITS_FILE"
done

while IFS="$(printf '\t')" read -r kind hit; do
  [ -z "$hit" ] && continue
  file="${hit%%:*}"
  lineno_and_code="${hit#*:}"
  lineno="${lineno_and_code%%:*}"
  if is_allowed "$file" "$kind"; then
    continue
  fi
  echo "✗ NEW '${kind}' on a hot path: ${file}:${lineno}"
  echo "    → use ThreadLocalRandom.current() (uniqueness) or UUIDService.getNonSecureUUID() (an id)."
  echo "      If its unguessability is a genuine SECURITY property, add an allow-list entry"
  echo "      in ${BASH_SOURCE[0]##*/} with a one-line reason."
  errors=$((errors + 1))
done < "$HITS_FILE"

# ── Rot check: every allow-list entry must still match something, else it is
#    stale and could quietly mask a regression (mirrors check-false-green-guards.sh).
echo "--- :broom: rot check: every allow-list entry still matches"
for entry in "${ALLOWLIST[@]}"; do
  a_path="${entry%%|*}"
  a_kind="${entry#*|}"; a_kind="${a_kind%%|*}"
  if [ ! -f "$a_path" ]; then
    echo "✗ stale allow-list entry — file no longer exists: ${a_path}"
    errors=$((errors + 1)); continue
  fi
  # A live hit is a line "<a_kind>\t<a_path>:..." in the tagged hit file.
  if ! grep -qF "${a_kind}$(printf '\t')${a_path}:" "$HITS_FILE"; then
    echo "✗ stale allow-list entry — no live '${a_kind}' in ${a_path} (remove the entry)"
    errors=$((errors + 1))
  fi
done

if [ "$errors" -ne 0 ]; then
  echo "^^^ +++"
  echo "✗ shared-RNG hot-path guard failed with ${errors} problem(s)."
  exit 1
fi

echo "✓ no un-allow-listed shared-RNG call sites in scope; allow-list is current."
