---
name: build-monitor
description: Continuously monitors Buildkite pipeline builds, detects failures, investigates root causes, fixes issues, and pushes fixes. Runs a polling loop that checks build status at configurable intervals for a configurable duration. Use when the user says "monitor builds", "watch pipeline", "watch CI", "continuous monitoring", "keep checking builds", or wants automated build-fix cycles.

---

# Buildkite Build Monitor

Continuously monitor the Buildkite pipeline, detect failures, investigate root causes, fix code issues, perform adversarial review, and push fixes — all in an automated loop.

## Prerequisites — Authentication Check

Before starting the monitoring loop, verify ALL required authentication is in place. **Stop and report any failures** before proceeding.

### Step 1: Buildkite CLI

```bash
which bk || echo "FAIL: bk CLI not installed — run: brew tap buildkite/buildkite && brew install buildkite/buildkite/bk"
bk auth status 2>&1 | head -5
```

If not authenticated, ask the user to run `bk auth login` in a separate terminal — it requires interactive browser OAuth.

If org is not selected:

```bash
bk auth switch mockserver
```

### Step 2: GitHub CLI

```bash
gh auth status 2>&1 | head -3
```

Needed for pushing fixes and creating PRs if required.

### Step 3: Git Status

```bash
git status --porcelain
git branch --show-current
```

Verify:
- Working tree is clean (no uncommitted changes that would conflict with fixes)
- On `master` branch (or confirm which branch to monitor)

### Step 4: Report Authentication Status

Print a summary table:

```
Authentication Status:
  Buildkite CLI: OK (org: mockserver)
  GitHub CLI:    OK
  Git:           clean, on master
```

If any check fails, **stop and report** — do not start monitoring.

## Monitoring Loop

### Parameters

| Parameter | Default | Description |
|-----------|---------|-------------|
| `interval` | 10 minutes | Time between checks |
| `duration` | 90 minutes | Total monitoring duration |
| `branch` | `master` | Branch to monitor (ignore other branches) |
| `auto_fix` | `true` | Automatically fix, review, commit, and push |

Calculate total checks: `duration / interval` (e.g., 90/10 = 9 checks).

### Check Procedure

For each check iteration:

#### 1. Fetch Recent Builds

```bash
TOKEN=$(bk auth token)
curl -sH "Authorization: Bearer $TOKEN" \
  "https://api.buildkite.com/v2/organizations/mockserver/pipelines/mockserver/builds?per_page=10" \
  | python3 -c "
import json, sys
builds = json.load(sys.stdin)
for b in builds:
    state = b['state']
    num = b['number']
    branch = b['branch']
    msg = b['message'].split('\n')[0][:60] if b['message'] else 'N/A'
    jobs = []
    for j in b.get('jobs', []):
        if j.get('type') == 'script' and j.get('name') != ':pipeline:':
            jobs.append(f'{j.get(\"name\",\"?\")}: {j[\"state\"]}')
    print(f'#{num} [{state:>10}] {branch[:30]:30} {msg}')
    if jobs:
        print(f'    Jobs: {\", \".join(jobs)}')
"
```

#### 2. Classify Builds

For each build on the monitored branch:

| State | Action |
|-------|--------|
| `passed` | Log as healthy. No action needed. |
| `running` | Log progress. Check again next interval. |
| `failed` | **Investigate** if not already investigated in this session. |
| `skipped` | Normal (superseded by newer commit). Ignore. |
| `canceled` | Log. No action. |

Track investigated build numbers to avoid re-investigating the same failure.

#### 3. Investigate Failures

For each new failed build:

**a. Classify the failure type:**

```bash
TOKEN=$(bk auth token)
# Get the failed build details
curl -sH "Authorization: Bearer $TOKEN" \
  "https://api.buildkite.com/v2/organizations/mockserver/pipelines/mockserver/builds/{number}" \
  | python3 -c "
import json, sys
b = json.load(sys.stdin)
for j in b.get('jobs', []):
    if j.get('state') == 'failed' and j.get('type') == 'script':
        print(f'job_id={j[\"id\"]} name={j.get(\"name\",\"?\")} exit={j.get(\"exit_status\",\"?\")}')
        agent = j.get('agent', {})
        print(f'agent_state={agent.get(\"connection_state\",\"?\")}')
"
```

| exit_status | agent_state | Diagnosis |
|-------------|-------------|-----------|
| `1` | `connected`/`disconnected` | Build/test failure — investigate logs |
| `-1` | `lost` | Agent died (spot termination, OOM) — infrastructure issue, no code fix needed |
| `0` with failed state | any | Unusual — check pipeline config |

**b. For build/test failures (exit_status=1):**

Launch the `pipeline-investigator` subagent:

```
Task(subagent_type="pipeline-investigator", prompt="Investigate Buildkite build #{number}...")
```

The investigator will return:
- Exact error messages
- Root cause analysis
- Affected files/modules
- Suggested fix

**c. For infrastructure failures (exit_status=-1, agent lost):**

Log the failure as infrastructure-related. Optionally trigger a rebuild:

```bash
bk build rebuild {number} -p mockserver -y
```

Do NOT attempt code fixes for infrastructure failures.

#### 4. Fix Code Issues

If the investigator identifies a code issue:

**a. Understand the fix:**
- Read the affected source files
- Understand the surrounding code context and conventions
- Plan the minimal fix

**b. Implement the fix:**
- Edit only the necessary files
- Follow existing code style and conventions
- Do NOT add comments unless the code is genuinely confusing

**c. Validate locally (if possible):**

For Java changes, run the specific failing test:

```bash
./mvnw test -pl {module} -Dtest={TestClassName}#{testMethodName} -Djava.security.egd=file:/dev/./urandom
```

Note: Full integration tests require the Docker CI image and may not run locally. Unit tests should run.

#### 5. Adversarial Review

Before committing, run a review using a different model with fresh context:

```
Task(subagent_type="review-cheap", prompt="Review the following changes for correctness, security, and conventions: {diff}")
```

The reviewer will return PASS or NEEDS_WORK. If NEEDS_WORK, address the feedback before committing.

#### 6. Commit and Push

Follow the commit workflow:

**a. Classify changed files:**

```bash
git diff --name-only
```

**b. Stage by explicit path (NEVER `git add .`):**

```bash
git add path/to/file1.java path/to/file2.java
```

**c. Commit with descriptive message:**

```bash
git commit -m "Fix {description of what was fixed}

{Brief explanation of root cause and fix}"
```

**d. Pull and push:**

```bash
git pull --rebase
git push
```

**e. Verify new build triggered:**

```bash
TOKEN=$(bk auth token)
curl -sH "Authorization: Bearer $TOKEN" \
  "https://api.buildkite.com/v2/organizations/mockserver/pipelines/mockserver/builds?per_page=1&branch=master" \
  | python3 -c "import json,sys; b=json.load(sys.stdin)[0]; print(f'#{b[\"number\"]} {b[\"state\"]} {b[\"commit\"][:10]}')"
```

#### 7. Wait for Next Check

```bash
sleep {interval_seconds}
```

### Status Report

After each check, print a concise status report:

```
=== Build Monitor Check {N}/{total} — {timestamp} ===
Build #{number}: {state} ({branch})
  {jobs summary}
Action: {none|investigating|fixing|pushed fix|waiting for result}
Next check: {timestamp}
```

### End of Monitoring

After all checks complete, print a final summary:

```
=== Build Monitor Summary ===
Duration: {start} to {end}
Checks performed: {N}
Builds observed: {list of build numbers and states}
Failures investigated: {count}
Fixes pushed: {count}
Current pipeline status: {passing|failing|running}
```

## Failure Patterns Reference

| Error Pattern | Category | Typical Fix |
|---|---|---|
| `COMPILATION ERROR` | Build error | Fix Java source code |
| `Tests run:.*Failures:` | Test failure | Fix test or production code |
| `invalid target release` | JDK mismatch | Update Docker image or compiler config |
| `class file has wrong version` | JDK mismatch | Update Docker image |
| `OutOfMemoryError` | Resource | Increase JVM heap in build script |
| `exit_status: -1` + agent `lost` | Infrastructure | Rebuild (spot termination) |
| `Timeout` | Hanging test | Add test timeouts or fix deadlock |
| `Connection refused` | Port conflict | Fix parallel test isolation |

## Important Rules

1. **Only fix failures on the monitored branch** (default: `master`). Ignore dependabot PR branches unless explicitly asked.
2. **Never amend commits that have been pushed.**
3. **Stage files by explicit path** — never `git add .` or `git add -A`.
4. **Pull before push** — `git pull --rebase` to handle concurrent changes.
5. **Check `git status` before committing** — if unexpected changes appear, stop and ask the user.
6. **Track investigated builds** — don't re-investigate the same failure.
7. **Infrastructure failures don't need code fixes** — just rebuild or wait.
8. **Rate limit rebuilds** — don't trigger more than one rebuild per check interval.
