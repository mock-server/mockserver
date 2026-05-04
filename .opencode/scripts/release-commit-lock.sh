#!/usr/bin/env bash
# Release commit lock held by current session
# Returns 0 if lock released or not held, 1 if lock held by another process

set -euo pipefail

# Validate required environment variable
if [[ -z "${OPENCODE_SESSION_PID:-}" ]]; then
    echo "ERROR: OPENCODE_SESSION_PID must be exported before using commit lock scripts" >&2
    echo "Usage: export OPENCODE_SESSION_PID=\$\$ && $0" >&2
    exit 1
fi
if ! [[ "$OPENCODE_SESSION_PID" =~ ^[0-9]+$ ]] || [[ "$OPENCODE_SESSION_PID" -eq 0 ]]; then
    echo "ERROR: OPENCODE_SESSION_PID must be a positive integer, got: ${OPENCODE_SESSION_PID:-}" >&2
    exit 1
fi

REPO_ROOT="$(git rev-parse --show-toplevel 2>/dev/null)" || {
    echo "ERROR: Not inside a git repository. Run this script from the MockServer repository." >&2
    exit 1
}
LOCK_FILE="$REPO_ROOT/.tmp/.commit-lock"

# Check if lock file exists
if [[ ! -f "$LOCK_FILE" ]]; then
    echo "No commit lock to release"
    exit 0
fi

# Read lock file
lock_pid=""
lock_timestamp=""
read -r lock_pid lock_timestamp < "$LOCK_FILE" || {
    echo "WARNING: Corrupt lock file, removing"
    rm -f "$LOCK_FILE"
    exit 0
}

# Check if lock is owned by this session
if [[ "$lock_pid" == "$OPENCODE_SESSION_PID" ]]; then
    rm -f "$LOCK_FILE"
    echo "Commit lock released (PID: $lock_pid)"
    exit 0
else
    echo "WARNING: Lock is owned by PID $lock_pid, not this session ($OPENCODE_SESSION_PID)"
    echo "Not releasing lock owned by another process"
    exit 1
fi
