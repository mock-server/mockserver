---
name: pr-monitor
description: >
  Monitors Dependabot and Snyk dependency upgrade PRs, automatically merging
  them when builds pass. Handles Java 11 compatibility validation and provides
  detailed status reporting. Use when the user says "monitor PRs", "watch builds",
  "auto-merge PRs", "merge passing PRs", or "watch dependency PRs".

---

# PR Monitor & Auto-Merge Skill

## Purpose

Monitors Dependabot and Snyk dependency upgrade PRs, automatically merging them when builds pass. Handles Java 11 compatibility validation and provides detailed status reporting.

## When to Use

- After creating/updating dependency upgrade PRs
- When dependency PRs are failing and you've pushed fixes
- When user says "monitor PRs", "watch builds", "merge when passing"
- After fixing build issues for Dependabot/Snyk PRs

## Prerequisites

- Repository must be a git repository with GitHub remote
- `gh` CLI must be authenticated
- PRs must exist on GitHub
- Working directory must be the repository root

## Workflow

### 1. Identify PRs to Monitor

Get PRs from today or specific PR numbers:

```bash
# Get all PRs from today
gh pr list --json number,title,createdAt,author --jq '.[] | select(.createdAt | startswith("'$(date +%Y-%m-%d)'"))'

# Or use specific PR numbers provided by user
```

### 2. Check Current Status

For each PR, check:
- Build status (Buildkite, GitHub Actions)
- Java compatibility (if dependency upgrade)
- PR state (open, closed, merged)

Use the bundled `pr-monitor.sh` script (see Tool Usage below).

### 3. Handle Different States

**PASSING builds:**
- Merge immediately with `gh pr merge <number> --squash --auto`
- Report success to user

**PENDING builds:**
- Continue monitoring
- Report current status

**FAILING builds:**
- Check if failure is due to Java version incompatibility
- Report failure details to user
- Do NOT merge

**CLOSED/MERGED:**
- Skip and report status
- Remove from monitoring list

### 4. Monitor Loop

Poll every 2 minutes (120 seconds) for up to 2 hours:
- Check status of all pending PRs
- Merge any that have passed
- Report status changes
- Exit when all PRs are merged or closed

## Tool Usage

Use the bundled `pr-monitor.sh` script:

```bash
.opencode/skills/pr-monitor/pr-monitor.sh <pr_numbers_or_'today'>
```

Examples:
```bash
# Monitor all PRs from today
.opencode/skills/pr-monitor/pr-monitor.sh today

# Monitor specific PRs
.opencode/skills/pr-monitor/pr-monitor.sh 2001 2005 2006

# One-time check (no loop)
.opencode/skills/pr-monitor/pr-monitor.sh --check-once 2001
```

## Output Format

The script provides real-time status:

```
[2026-05-04 20:54:25] Iteration 1/60
-------------------------------------------
  PR #2017: ✅ PASSING - Attempting to merge...
  PR #2017: 🎉 MERGED SUCCESSFULLY
  PR #2001: ⏳ PENDING - Buildkite still running
  PR #2005: ❌ FAILING - Buildkite: FAILURE
  PR #2006: ✅ MERGED

Next check in 120s...
```

## Error Handling

**Cannot fetch PR status:**
- Verify `gh` authentication: `gh auth status`
- Check PR number exists: `gh pr view <number>`

**Merge fails:**
- Check if PR has merge conflicts
- Verify branch protection rules
- Check if PR requires reviews

**Java 11 incompatibility:**
- Do NOT merge
- Report to user which dependency requires Java 17+
- Suggest closing the PR

## Integration with Dependabot

After fixing build issues (like in today's session):

1. Push fixes to master
2. Trigger Dependabot rebase: `@dependabot rebase` in PR comments
3. Start monitoring: `.opencode/skills/pr-monitor/pr-monitor.sh today`
4. Script will auto-merge as builds pass

## Notes

- The script uses `--squash` merge by default (matches MockServer convention)
- Uses `--auto` flag to enable auto-merge queue (waits for required checks)
- Maximum monitoring time: 2 hours (set `MAX_ITERATIONS` at top of `pr-monitor.sh`, default 60)
- Check interval: 2 minutes (set `SLEEP_INTERVAL` at top of `pr-monitor.sh`, default 120)
- Exits successfully when all PRs are merged/closed
- Exits with error code 1 if timeout reached with pending PRs
- GitHub API rate limit: authenticated `gh` CLI allows 5,000 requests/hour; each PR check uses ~2-3 API calls, so monitoring up to ~20 PRs at 2-minute intervals is well within limits

## Example Session

```
User: "monitor the PRs raised today and merge them when builds pass"

Agent: I'll monitor all dependency PRs from today and auto-merge them when builds pass.

[Runs: .opencode/skills/pr-monitor/pr-monitor.sh today]

[Output shows monitoring progress...]

Agent: All PRs processed:
- PR #2017: ✅ MERGED
- PR #2001: ✅ MERGED  
- PR #2005: ✅ MERGED
- PR #2006: ✅ MERGED
```
