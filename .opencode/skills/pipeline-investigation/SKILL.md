---
name: pipeline-investigation
description: Investigates Buildkite pipeline failures to find root causes. MUST be launched as a Task subagent with subagent_type "pipeline-investigator" — do NOT load directly via the skill tool. Returns structured JSON to the parent for formatting. Triggers when users ask about failing pipelines, build errors, or need help debugging CI/CD issues. Accepts Buildkite build URLs or build numbers and performs deep investigation.

---

# Buildkite Pipeline Failure Investigation

Investigate Buildkite pipeline failures systematically to identify root causes. This skill uses the Buildkite CLI (`bk`) as the primary interface, with REST API fallback.

## Prerequisites — Buildkite CLI Authentication

The `bk` CLI is the preferred way to interact with Buildkite. Before investigating, ensure it is installed and authenticated.

### Step 0: Check and Install the CLI

```bash
which bk || echo "bk CLI not installed"
```

If `bk` is not installed, install it:

```bash
brew tap buildkite/buildkite && brew install buildkite/buildkite/bk
```

### Step 1: Check Authentication

```bash
bk auth status
```

**If authenticated**, you will see the org slug, token UUID, scopes, and user info. Proceed to investigation.

**If not authenticated**, you will see:
```
Error: you are not authenticated. Run bk auth login to authenticate, or run bk use to select a configured organization
```

In this case, **stop and ask the user** to authenticate:

> Please run `bk auth login` in a terminal to authenticate with Buildkite via browser-based OAuth. This is a one-time setup similar to `aws sso login`. Once done, run `bk auth switch mockserver` to select the organization.

**Do NOT attempt to run `bk auth login` yourself** — it requires interactive browser OAuth that cannot be completed in a non-TTY terminal.

### Step 2: Select Organization (if needed)

If `bk auth status` shows `selected_org: ""`, the org hasn't been selected:

```bash
bk auth switch mockserver
```

### Step 3: Get API Token for REST API Calls

When you need the REST API (for endpoints not covered by `bk` CLI):

```bash
TOKEN=$(bk auth token 2>/dev/null)
if [ -z "$TOKEN" ]; then
  echo "ERROR: bk auth token failed — run 'bk auth login' first"
  exit 1
fi
curl -sH "Authorization: Bearer $TOKEN" "https://api.buildkite.com/v2/..."
```

This extracts the OAuth token from the CLI's keychain storage. Always validate the token is non-empty before using it. **Never ask the user to manually create API tokens** — the CLI handles this automatically.

## Investigation Workflow

### Step 1: Parse Input

Extract organization, pipeline, and build number from the Buildkite URL or user input.

**URL Pattern:**
```
https://buildkite.com/{org}/{pipeline}/builds/{build_number}
```

The organization has multiple pipelines sharing the same agent pool:
- `mockserver` — primary Java build and test
- `mockserver-client-node` — Node.js client
- `mockserver-node` — Node.js module
- `mockserver-performance-test` — performance tests

### Step 2: Get Build Overview

**Using `bk` CLI (preferred):**
```bash
bk build view {build_number} -p {pipeline} --json
```

**Using REST API (fallback):**
```bash
TOKEN=$(bk auth token)
curl -sH "Authorization: Bearer $TOKEN" \
  "https://api.buildkite.com/v2/organizations/mockserver/pipelines/{pipeline}/builds/{build_number}"
```

**Save the `commit` SHA and `created_at`** — you will need these later to check whether fixes have already been pushed.

### Step 3: Check Agent Availability

**Using `bk` CLI:**
```bash
bk agent list --json
```

This shows all agents across all pipelines, their connection state, and current job (if busy).

**Key fields to check:**
- `connection_state` — should be `connected`
- `job` — if present, agent is busy; check which pipeline/build it's running
- `meta_data` — agent tags including `queue=default`

### Step 4: List Builds Across All Pipelines

To understand queue contention, check builds across all pipelines sharing the agent pool:

```bash
TOKEN=$(bk auth token)
curl -sH "Authorization: Bearer $TOKEN" \
  "https://api.buildkite.com/v2/organizations/mockserver/builds?state[]=scheduled&state[]=running&per_page=50"
```

### Step 5: Identify Failed Jobs

```bash
bk build view {build_number} -p {pipeline} --json
```

Filter for failed jobs in the JSON output. Look at `jobs[].state == "failed"`.

### Step 6: Retrieve Job Logs

```bash
TOKEN=$(bk auth token)
curl -sH "Authorization: Bearer $TOKEN" \
  "https://api.buildkite.com/v2/organizations/mockserver/pipelines/{pipeline}/builds/{build_number}/jobs/{job_id}/log" \
  | jq -r '.content'
```

**For large logs, save to file:**
```bash
TOKEN=$(bk auth token)
curl -sH "Authorization: Bearer $TOKEN" \
  "https://api.buildkite.com/v2/organizations/mockserver/pipelines/{pipeline}/builds/{build_number}/jobs/{job_id}/log" \
  | jq -r '.content' > .tmp/buildkite-{build_number}-log.txt
```

### Step 7: Download and Analyze Build Artifacts

```bash
TOKEN=$(bk auth token)
curl -sH "Authorization: Bearer $TOKEN" \
  "https://api.buildkite.com/v2/organizations/mockserver/pipelines/{pipeline}/builds/{build_number}/artifacts" \
  | jq '.[] | {filename,path,download_url}'
```

**Download an artifact:**
```bash
TOKEN=$(bk auth token)
curl -sLH "Authorization: Bearer $TOKEN" \
  "{download_url}" -o .tmp/buildkite-{build_number}-artifact.log
```

### Step 8: Check GitHub Actions (Secondary CI)

If the Buildkite build passed but related GitHub Actions failed (CodeQL):

```bash
gh run list --repo mock-server/mockserver --limit 10 --json status,conclusion,name,headBranch,createdAt
gh run view {run_id} --repo mock-server/mockserver --log-failed
```

### Step 9: Additional Investigation

**Get build changes (commits that triggered the build):**
```bash
git log --oneline {commit}..HEAD
```

**Check recent build history for patterns:**
```bash
TOKEN=$(bk auth token)
curl -sH "Authorization: Bearer $TOKEN" \
  "https://api.buildkite.com/v2/organizations/mockserver/pipelines/{pipeline}/builds?per_page=20&state=failed" \
  | jq '.[] | {number,state,message,created_at}'
```

**Cancel a build (e.g., stuck or blocking):**
```bash
bk build cancel {build_number} -p {pipeline} -y
```

**Rebuild a build:**
```bash
bk build rebuild {build_number} -p {pipeline} -y
```

### Step 10: Classify Error

Match the identified root cause against common failure patterns:

| Error Pattern | Category | Scope |
|---|---|---|
| `BUILD FAILURE` in Maven | `BUILD_ERROR` | ISOLATED |
| `Tests run:.*Failures:` | `TEST_FAILURE` | ISOLATED |
| `OutOfMemoryError` | `RESOURCE_ERROR` | MAY_BE_SYSTEMIC |
| `Connection refused` | `NETWORK_ERROR` | MAY_BE_SYSTEMIC |
| `docker: Error` | `DOCKER_ERROR` | ISOLATED |
| `Timeout` | `TIMEOUT` | MAY_BE_SYSTEMIC |
| Agent did not connect | `AGENT_ERROR` | SYSTEMIC |
| Build `skipped` | `AUTO_SKIPPED` | NORMAL (newer commit superseded) |
| Build `scheduled` (stuck) | `QUEUE_STARVATION` | SYSTEMIC |

### Step 11: Check for Already-Pushed Fixes

**CRITICAL:** Before recommending a fix, check whether the root cause has already been addressed:

```bash
git fetch origin master --quiet
git log --oneline {commit}..origin/master
git diff --name-only {commit}..origin/master
```

**Classify:**

| Classification | Meaning | Report Action |
|---|---|---|
| **ALREADY FIXED** | A commit on `origin/master` addresses this | Mark as fixed, cite the commit |
| **OPEN** | No fix found | Report with recommended fix |

## Build Scheduling and Queue Behaviour

Understanding how Buildkite schedules builds is critical for diagnosing "stuck" builds:

- **`skip_queued_branch_builds`** — if enabled in the Buildkite pipeline settings (check via `bk build view -p {pipeline} --json` under `pipeline.skip_queued_branch_builds`), a newer build for the same branch causes older queued builds to be auto-skipped. This is normal, not a failure. The mockserver pipeline currently has this enabled.
- **`cancel_running_branch_builds`** — if disabled (check via `pipeline.cancel_running_branch_builds`), running builds are NOT automatically cancelled when a newer commit is pushed. Older running builds continue to consume agents. The mockserver pipeline currently has this disabled.
- **Multiple pipelines share the same agent pool** (`queue=default`). Scheduled builds from `mockserver-performance-test`, `mockserver-client-node`, and `mockserver-node` compete with the primary `mockserver` pipeline for agents.
- **ASG max_size** limits the total number of agents. The Lambda scaler cannot add instances beyond this limit. Check the current max with:
  ```bash
  DYLD_LIBRARY_PATH=/opt/homebrew/opt/expat/lib aws autoscaling describe-auto-scaling-groups \
    --auto-scaling-group-names "$(cd terraform/buildkite-agents && terraform output -raw auto_scaling_group_name 2>/dev/null || aws autoscaling describe-auto-scaling-groups --profile mockserver-build --region eu-west-2 --query 'AutoScalingGroups[?contains(Tags[?Key==`Name`].Value, `buildkite-mockserver`)].AutoScalingGroupName' --output text | head -1)" \
    --region eu-west-2 --profile mockserver-build \
    --query 'AutoScalingGroups[0].{MinSize:MinSize,MaxSize:MaxSize,DesiredCapacity:DesiredCapacity}'
  ```

## Output — Structured Data Return

Return this structure in your final message:

```json
{
  "schema": "pipeline-investigation/v1",
  "build": {
    "number": 0,
    "pipeline": "<pipeline slug>",
    "branch": "<branch>",
    "commit": "<commit sha>",
    "state": "failed",
    "failed_job": "<job name>",
    "started_at": "<ISO8601>",
    "finished_at": "<ISO8601>"
  },
  "root_cause": {
    "summary": "<one-line description>",
    "detail": "<technical explanation>",
    "error_excerpt": "<relevant log lines>",
    "failure_category": "<category from table above or null>",
    "scope": "SYSTEMIC|ISOLATED|MAY_BE_SYSTEMIC"
  },
  "fix_status": "OPEN|ALREADY_FIXED",
  "fix_commit": "<sha or null>",
  "fix_message": "<commit message or null>",
  "github_actions": [
    { "run_id": 0, "workflow": "<name>", "conclusion": "failure|success", "root_cause": "<summary or null>" }
  ],
  "recommended_fix": "<actionable steps, null if already fixed>"
}
```

After returning the JSON, provide a brief summary (2-3 lines).

## Notes

- Always investigate the deepest failure first (root cause in logs, not surface symptoms)
- Build artifacts often contain more detailed logs than the console output
- For recurring failures, check recent commits and pipeline definition changes
- **Always run Step 11** before reporting — check if the issue was already fixed
- When builds are stuck/scheduled, check agent availability across ALL pipelines, not just the one being investigated
- The `bk` CLI uses `-p {pipeline}` for pipeline selection, NOT `--org` — the org is set via `bk auth switch`
