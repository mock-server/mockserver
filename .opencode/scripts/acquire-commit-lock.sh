#!/usr/bin/env bash
# Acquire commit lock to prevent concurrent commits from parallel opencode sessions
# Returns 0 if lock acquired, 1 if lock held by another session

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

# Validate git repository
REPO_ROOT="$(git rev-parse --show-toplevel 2>/dev/null)" || {
    echo "ERROR: Not inside a git repository. Run this script from the MockServer repository." >&2
    exit 1
}

LOCK_FILE="$REPO_ROOT/.tmp/.commit-lock"
MAX_WAIT_SECONDS="${COMMIT_LOCK_TIMEOUT:-300}"  # Default 5 minutes, override with COMMIT_LOCK_TIMEOUT
POLL_INTERVAL="${COMMIT_LOCK_POLL:-2}"          # Default 2 seconds, override with COMMIT_LOCK_POLL

# Validate environment variable values
if ! [[ "$MAX_WAIT_SECONDS" =~ ^[0-9]+$ ]] || [[ "$MAX_WAIT_SECONDS" -eq 0 ]]; then
    echo "ERROR: COMMIT_LOCK_TIMEOUT must be a positive integer, got: ${COMMIT_LOCK_TIMEOUT:-}" >&2
    exit 1
fi
if ! [[ "$POLL_INTERVAL" =~ ^[0-9]+$ ]] || [[ "$POLL_INTERVAL" -eq 0 ]]; then
    echo "ERROR: COMMIT_LOCK_POLL must be a positive integer, got: ${COMMIT_LOCK_POLL:-}" >&2
    exit 1
fi

# Ensure .tmp directory exists
mkdir -p "$REPO_ROOT/.tmp"

# Function to check if lock is stale (process no longer exists)
is_lock_stale() {
    local lock_file="$1"
    
    if [[ ! -f "$lock_file" ]]; then
        return 1  # No lock file, not stale
    fi
    
    # Read PID and timestamp from lock file
    local lock_pid
    local lock_timestamp
    if ! read -r lock_pid lock_timestamp < "$lock_file"; then
        # Corrupt or empty lock file - treat as stale
        return 0
    fi
    
    # Validate PID is numeric
    if ! [[ "$lock_pid" =~ ^[0-9]+$ ]]; then
        # Non-numeric PID - corrupt lock file
        return 0
    fi
    
    # Check if process exists (works on macOS and Linux)
    if ps -p "$lock_pid" > /dev/null 2>&1; then
        return 1  # Process exists, lock is valid
    else
        return 0  # Process dead, lock is stale
    fi
}

# Function to create lock file
create_lock() {
    local lock_file="$1"
    echo "$OPENCODE_SESSION_PID $(date +%s)" > "$lock_file"
}

# Function to display lock holder info
show_lock_holder() {
    local lock_file="$1"
    
    if [[ ! -f "$lock_file" ]]; then
        return
    fi
    
    local lock_pid
    local lock_timestamp
    read -r lock_pid lock_timestamp < "$lock_file" || return
    
    local lock_age=$(($(date +%s) - lock_timestamp))
    local lock_age_human
    
    if [[ $lock_age -lt 60 ]]; then
        lock_age_human="${lock_age}s"
    elif [[ $lock_age -lt 3600 ]]; then
        lock_age_human="$((lock_age / 60))m $((lock_age % 60))s"
    else
        lock_age_human="$((lock_age / 3600))h $(((lock_age % 3600) / 60))m"
    fi
    
    echo "Lock held by PID $lock_pid (age: $lock_age_human)"
}

# Attempt to acquire lock
attempt=0
start_time=$(date +%s)

while true; do
    # Remove stale lock if present
    if is_lock_stale "$LOCK_FILE"; then
        echo "Removing stale lock (process no longer exists)"
        rm -f "$LOCK_FILE"
    fi
    
    # Try to acquire lock (atomic operation)
    if (set -o noclobber; create_lock "$LOCK_FILE") 2>/dev/null; then
        echo "Commit lock acquired (PID: $OPENCODE_SESSION_PID)"
        exit 0
    fi
    
    # Lock acquisition failed
    elapsed=$(($(date +%s) - start_time))
    
    if [[ $attempt -eq 0 ]]; then
        echo "Another opencode session is currently committing."
        show_lock_holder "$LOCK_FILE"
        echo "Waiting for lock (timeout: ${MAX_WAIT_SECONDS}s)..."
    fi
    
    if [[ $elapsed -ge $MAX_WAIT_SECONDS ]]; then
        echo "ERROR: Failed to acquire commit lock after ${MAX_WAIT_SECONDS}s"
        show_lock_holder "$LOCK_FILE"
        echo ""
        echo "Options:"
        echo "1. Wait for the other session to complete its commit"
        echo "2. If the other session crashed, manually remove: $LOCK_FILE"
        echo "3. Cancel this commit and try again later"
        exit 1
    fi
    
    attempt=$((attempt + 1))
    
    # Sleep for the lesser of poll interval and remaining time
    remaining=$((MAX_WAIT_SECONDS - elapsed))
    sleep_interval="$POLL_INTERVAL"
    [[ "$remaining" -lt "$sleep_interval" ]] && sleep_interval="$remaining"
    sleep "$sleep_interval"
done
