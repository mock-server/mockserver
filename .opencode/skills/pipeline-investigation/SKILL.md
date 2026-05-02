---
name: pipeline-investigation
description: Investigates Buildkite pipeline failures to find root causes. MUST be launched as a Task subagent with subagent_type "pipeline-investigator" — do NOT load directly via the skill tool. Returns structured JSON to the parent for formatting. Triggers when users ask about failing pipelines, build errors, or need help debugging CI/CD issues. Accepts Buildkite build URLs or build numbers and performs deep investigation.

---

# Buildkite Pipeline Failure Investigation

Investigate Buildkite pipeline failures systematically to identify root causes. This skill uses the Buildkite API and GitHub CLI.

## Prerequisites

The user must have a `BUILDKITE_TOKEN` environment variable set with API access to the mockserver organization.

## Investigation Workflow

### Step 1: Parse Input

Extract organization, pipeline, and build number from the Buildkite URL or user input.

**URL Pattern:**
```
https://buildkite.com/mockserver/mockserver/builds/{build_number}
```

### Step 2: Get Build Overview

Retrieve basic build status and metadata:

```bash
curl -sH "Authorization: Bearer $BUILDKITE_TOKEN" \
  "https://api.buildkite.com/v2/organizations/mockserver/pipelines/mockserver/builds/{build_number}" \
  | jq '{state,branch,message,commit,created_at,started_at,finished_at}'
```

**Save the `commit` SHA and `created_at`** — you will need these later to check whether fixes have already been pushed.

### Step 3: Identify Failed Jobs

Get the build jobs to find which specific steps failed:

```bash
curl -sH "Authorization: Bearer $BUILDKITE_TOKEN" \
  "https://api.buildkite.com/v2/organizations/mockserver/pipelines/mockserver/builds/{build_number}" \
  | jq '.jobs[] | select(.state == "failed") | {name,state,exit_status,agent_query_rules,started_at,finished_at}'
```

### Step 4: Retrieve Job Logs

Once you identify the failed job's ID:

```bash
curl -sH "Authorization: Bearer $BUILDKITE_TOKEN" \
  "https://api.buildkite.com/v2/organizations/mockserver/pipelines/mockserver/builds/{build_number}/jobs/{job_id}/log" \
  | jq -r '.content'
```

**For large logs, save to file:**
```bash
curl -sH "Authorization: Bearer $BUILDKITE_TOKEN" \
  "https://api.buildkite.com/v2/organizations/mockserver/pipelines/mockserver/builds/{build_number}/jobs/{job_id}/log" \
  | jq -r '.content' > .tmp/buildkite-{build_number}-log.txt
```

### Step 5: Download and Analyze Build Artifacts

Failed builds often upload detailed logs as artifacts:

```bash
curl -sH "Authorization: Bearer $BUILDKITE_TOKEN" \
  "https://api.buildkite.com/v2/organizations/mockserver/pipelines/mockserver/builds/{build_number}/artifacts" \
  | jq '.[].{filename,path,download_url}'
```

**Download an artifact:**
```bash
curl -sLH "Authorization: Bearer $BUILDKITE_TOKEN" \
  "{download_url}" -o .tmp/buildkite-{build_number}-artifact.log
```

### Step 6: Check GitHub Actions (Secondary CI)

If the Buildkite build passed but related GitHub Actions failed (Docker builds, CodeQL):

```bash
gh run list --repo mockserver/mockserver --limit 10 --json status,conclusion,name,headBranch,createdAt
gh run view {run_id} --repo mockserver/mockserver --log-failed
```

### Step 7: Additional Investigation

**Get build changes (commits that triggered the build):**
```bash
git log --oneline {commit}..HEAD
```

**Check recent build history for patterns:**
```bash
curl -sH "Authorization: Bearer $BUILDKITE_TOKEN" \
  "https://api.buildkite.com/v2/organizations/mockserver/pipelines/mockserver/builds?per_page=20&state=failed" \
  | jq '.[].{number,state,message,created_at}'
```

### Step 8: Classify Error

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

### Step 9: Check for Already-Pushed Fixes

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

## Output — Structured Data Return

Return this structure in your final message:

```json
{
  "schema": "pipeline-investigation/v1",
  "build": {
    "number": 0,
    "pipeline": "mockserver",
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
- **Always run Step 9** before reporting — check if the issue was already fixed
