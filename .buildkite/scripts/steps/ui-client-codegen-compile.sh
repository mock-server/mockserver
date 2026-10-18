#!/usr/bin/env bash
# Permanent CI gate: compile / syntax-check the dashboard composer's GENERATED
# client code for Python, Ruby, Go, Rust and C#.
#
# The composer emits client code in seven languages. Java is gated by
# ui-java-codegen-compile.sh (javac); Node is gated by the tsc type-proof in
# src/lib/codegen/node.test.ts (run under `npm test`). C#'s
# ComposerCodegenEquivalenceTests.cs is a frozen snapshot of emitter output, so it
# never sees a csharp.ts change. These five had no compile gate: a client-API
# rename or an emitter bug that produced invalid source would ship broken
# generated code silently, caught by nothing (the existing per-language tests only string/byte
# compare the emitter output; they never feed it to a compiler).
#
# This gate drives the SHARED representative composer matrix
# (mockserver-ui/src/lib/codegen/extractParityCases.ts — the exact `combos` the
# byte-identity parity tests use) through the requested language's emitter and
# runs the lightest CREDIBLE check for that toolchain:
#
#   python  python -m py_compile   syntax/parse (dynamic client -> no static API check)
#   ruby    ruby -c                syntax/parse (dynamic client -> no static API check)
#   go      go build/vet ./...     FULL compile against the real mockserver-client-go
#   rust    cargo check --bins     FULL compile against the real mockserver-client-rust
#   csharp  dotnet build           FULL compile against the real mockserver-client-dotnet
#
# Go, Rust and C# are statically typed and their clients live in this monorepo, so
# their checks catch API drift (a renamed client method fails the build) — the
# direct analog of the Java javac gate. Python and Ruby are dynamically typed with
# no shipped type stubs, so a syntax check is the strongest credible static gate:
# it fails on any emitter bug that produces malformed source. All five are proven
# to go red (see the commit's red/green evidence).
#
# TWO phases, each in the toolchain it needs:
#   1. node:22           run the emitter (Node native TS type-stripping; NO npm ci,
#                        the codegen modules are dependency-free).
#   2. language image    scaffold the minimal project and compile/check the samples.
#
# CI runs each phase inside its Docker image via run-in-docker.sh (the raw agent
# has no node/python/ruby/go/cargo). For fast local validation set
# CODEGEN_COMPILE_USE_DOCKER=false to run the exact same command strings against
# host toolchains instead.
#
# USAGE: ui-client-codegen-compile.sh <python|ruby|go|rust|csharp>
set -euo pipefail

LANG_ARG="${1:-}"
case "$LANG_ARG" in
  python|ruby|go|rust|csharp) ;;
  *) echo "usage: $(basename "$0") <python|ruby|go|rust|csharp>" >&2; exit 2 ;;
esac

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../../.." && pwd)"

USE_DOCKER="${CODEGEN_COMPILE_USE_DOCKER:-true}"

# BASE is the repo root as seen INSIDE the execution context — /build when mounted
# in Docker, the real repo root when running natively on the host. Sample files and
# the client crates/modules are all addressed relative to it.
if [ "$USE_DOCKER" = "true" ]; then
  BASE="/build"
else
  BASE="$REPO_ROOT"
fi

# Where the emitted samples physically live on the host (always the real repo
# root, since that path is what Docker bind-mounts at /build) vs how they are
# addressed inside the command strings (BASE).
HOST_WORK="$REPO_ROOT/.tmp/client-codegen-gate/$LANG_ARG"
WORK_DIR="$BASE/.tmp/client-codegen-gate/$LANG_ARG"

# Coverage floor (mirrors the Java gate's COR-02): a regression that silently
# shrinks the emitter matrix must fail loudly, not pass with reduced coverage.
MIN_SAMPLES=24

# ---- Phase 1: emit the samples (Node, no npm ci) ------------------------------
EMIT_CMD="$(cat <<EOF
set -eu
echo '--- :node: emitting ${LANG_ARG} composer samples'
node "$BASE/mockserver-ui/scripts/emit-client-codegen-samples.mjs" "$LANG_ARG" "$WORK_DIR"
EOF
)"

# ---- Phase 2 command builder --------------------------------------------------
# Each check first enforces the coverage floor, then compiles/checks the samples.
case "$LANG_ARG" in
  python)
    IMAGE="python:3.12"; CACHE=""
    CHECK_CMD="$(cat <<EOF
set -eu
cd "$WORK_DIR"
n=\$(ls sample_*.py | wc -l | tr -d ' ')
if [ "\$n" -lt $MIN_SAMPLES ]; then echo "FATAL: expected >= $MIN_SAMPLES samples, found \$n" >&2; exit 1; fi
echo "--- :python: py_compile \$n generated sample(s)"
python -m py_compile sample_*.py
echo "--- :white_check_mark: all generated Python compiled"
EOF
)"
    ;;
  ruby)
    IMAGE="ruby:3.3"; CACHE=""
    CHECK_CMD="$(cat <<EOF
set -eu
cd "$WORK_DIR"
n=\$(ls sample_*.rb | wc -l | tr -d ' ')
if [ "\$n" -lt $MIN_SAMPLES ]; then echo "FATAL: expected >= $MIN_SAMPLES samples, found \$n" >&2; exit 1; fi
echo "--- :ruby: ruby -c \$n generated sample(s)"
for f in sample_*.rb; do ruby -c "\$f" >/dev/null; done
echo "--- :white_check_mark: all generated Ruby is syntactically valid"
EOF
)"
    ;;
  go)
    IMAGE="golang:1.23"; CACHE="go"
    GO_CLIENT_MODULE="$(awk '/^module /{print $2; exit}' "$REPO_ROOT/mockserver-client-go/go.mod")"
    CHECK_CMD="$(cat <<EOF
set -eu
cd "$WORK_DIR"
n=\$(ls -d sample_*/ | wc -l | tr -d ' ')
if [ "\$n" -lt $MIN_SAMPLES ]; then echo "FATAL: expected >= $MIN_SAMPLES samples, found \$n" >&2; exit 1; fi
echo "--- :go: building \$n generated sample(s) against mockserver-client-go"
cat > go.mod <<GOMOD
module codegensamples

go 1.21

require $GO_CLIENT_MODULE v${GO_CLIENT_MODULE##*/v}.0.0

replace $GO_CLIENT_MODULE => $BASE/mockserver-client-go
GOMOD
export GOFLAGS=-mod=mod
go mod tidy
go build ./...
go vet ./...
echo "--- :white_check_mark: all generated Go compiled + vetted against the real client"
EOF
)"
    ;;
  rust)
    IMAGE="rust:1"; CACHE="cargo"
    CHECK_CMD="$(cat <<EOF
set -eu
cd "$WORK_DIR"
n=\$(ls src/bin/sample_*.rs | wc -l | tr -d ' ')
if [ "\$n" -lt $MIN_SAMPLES ]; then echo "FATAL: expected >= $MIN_SAMPLES samples, found \$n" >&2; exit 1; fi
echo "--- :rust: cargo check \$n generated sample(s) against mockserver-client-rust"
cat > Cargo.toml <<CARGO
[package]
name = "codegen-samples"
version = "0.0.0"
edition = "2021"

[dependencies]
mockserver-client = { path = "$BASE/mockserver-client-rust" }
serde_json = "1"

[workspace]
CARGO
cargo check --bins
echo "--- :white_check_mark: all generated Rust compiled against the real client"
EOF
)"
    ;;
  csharp)
    IMAGE="mcr.microsoft.com/dotnet/sdk:10.0"; CACHE="nuget"
    # One project per sample: each is a top-level-statement program and C# allows
    # one per compilation. ImplicitUsings matches a `dotnet new console` project.
    CHECK_CMD="$(cat <<EOF
set -eu
cd "$WORK_DIR"
n=\$(ls -d sample_*/ | wc -l | tr -d ' ')
if [ "\$n" -lt $MIN_SAMPLES ]; then echo "FATAL: expected >= $MIN_SAMPLES samples, found \$n" >&2; exit 1; fi
echo "--- :dotnet: building \$n generated sample(s) against mockserver-client-dotnet"
for d in sample_*/; do
  d="\${d%/}"
  cat > "\$d/\$d.csproj" <<CSPROJ
<Project Sdk="Microsoft.NET.Sdk">
  <PropertyGroup>
    <OutputType>Exe</OutputType>
    <TargetFramework>net10.0</TargetFramework>
    <ImplicitUsings>enable</ImplicitUsings>
    <Nullable>enable</Nullable>
  </PropertyGroup>
  <ItemGroup>
    <ProjectReference Include="$BASE/mockserver-client-dotnet/src/MockServer.Client/MockServer.Client.csproj" />
  </ItemGroup>
</Project>
CSPROJ
done
dotnet new sln -n samples --format sln >/dev/null
dotnet sln samples.sln add sample_*/*.csproj >/dev/null
dotnet build samples.sln -nologo -v minimal
echo "--- :white_check_mark: all generated C# compiled against the real client"
EOF
)"
    ;;
esac

if [ "$USE_DOCKER" = "true" ]; then
  "$SCRIPT_DIR/../run-in-docker.sh" \
    -i node:22 \
    -- bash -c "$EMIT_CMD"

  DOCKER_CACHE_ARGS=()
  if [ -n "$CACHE" ]; then DOCKER_CACHE_ARGS=(--cache "$CACHE"); fi
  # Expand with the +-guard idiom so an empty array is safe under `set -u` on
  # bash 3.2 (macOS) as well as modern bash.
  "$SCRIPT_DIR/../run-in-docker.sh" \
    -i "$IMAGE" \
    "${DOCKER_CACHE_ARGS[@]+"${DOCKER_CACHE_ARGS[@]}"}" \
    -- bash -c "$CHECK_CMD"
else
  echo "=== running natively (CODEGEN_COMPILE_USE_DOCKER=false) ==="
  mkdir -p "$HOST_WORK"
  bash -c "$EMIT_CMD"
  bash -c "$CHECK_CMD"
fi
