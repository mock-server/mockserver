---
name: pr-review
description: >
  Reviews all open pull requests on the MockServer GitHub repository and produces
  a structured report. Classifies PRs as mergeable, needs-work, stale/out-of-date,
  or duplicate. Use when the user says "review PRs", "PR report", "check pull
  requests", "PR status", "open PRs", or "duplicate PRs".

---

# Pull Request Review Report

Analyze all open pull requests on the MockServer GitHub repository and produce
a comprehensive report covering mergeability, staleness, actionability, and duplicates.

## Prerequisites

- **`GITHUB_TOKEN`** environment variable set with a GitHub personal access token
  that has `repo` scope for `github.com`, OR
- **`gh` CLI** authenticated to `github.com` (`gh auth login --hostname github.com`)

Check which is available:

```bash
if [ -n "$GITHUB_TOKEN" ]; then
  echo "Using GITHUB_TOKEN"
elif gh auth status --hostname github.com 2>/dev/null; then
  echo "Using gh CLI"
else
  echo "ERROR: Set GITHUB_TOKEN or run: gh auth login --hostname github.com"
  exit 1
fi
```

## Repository

- **Owner:** `mock-server`
- **Repo:** `mockserver`
- **API Base:** `https://api.github.com/repos/mock-server/mockserver-monorepo`

## Step 1: Fetch All Open PRs

Fetch all open PRs with full metadata. Paginate if there are more than 100:

```bash
# Using GITHUB_TOKEN
curl -sH "Authorization: token $GITHUB_TOKEN" \
  -H "Accept: application/vnd.github.v3+json" \
  "https://api.github.com/repos/mock-server/mockserver-monorepo/pulls?state=open&per_page=100&page=1" \
  | jq '[.[] | {
    number,
    title,
    user: .user.login,
    created_at,
    updated_at,
    head_ref: .head.ref,
    head_sha: .head.sha,
    base_ref: .base.ref,
    mergeable_state: .mergeable_state,
    draft: .draft,
    labels: [.labels[].name],
    body: (.body // "" | .[0:200])
  }]'
```

```bash
# Using gh CLI (if authenticated to github.com)
gh pr list --repo mock-server/mockserver-monorepo --state open --limit 100 \
  --json number,title,author,createdAt,updatedAt,headRefName,baseRefName,isDraft,labels,mergeable,reviewDecision
```

## Step 2: Get Detailed PR Info

For each PR, fetch merge status and review details:

```bash
# Merge status (requires individual PR fetch for mergeable field)
curl -sH "Authorization: token $GITHUB_TOKEN" \
  -H "Accept: application/vnd.github.v3+json" \
  "https://api.github.com/repos/mock-server/mockserver-monorepo/pulls/{number}" \
  | jq '{number, mergeable, mergeable_state, rebaseable, maintainer_can_modify, changed_files, additions, deletions}'
```

```bash
# Reviews
curl -sH "Authorization: token $GITHUB_TOKEN" \
  -H "Accept: application/vnd.github.v3+json" \
  "https://api.github.com/repos/mock-server/mockserver-monorepo/pulls/{number}/reviews" \
  | jq '[.[] | {user: .user.login, state, submitted_at}]'
```

```bash
# CI status checks
curl -sH "Authorization: token $GITHUB_TOKEN" \
  -H "Accept: application/vnd.github.v3+json" \
  "https://api.github.com/repos/mock-server/mockserver-monorepo/commits/{head_sha}/status" \
  | jq '{state, total_count, statuses: [.statuses[] | {context, state, description}]}'
```

```bash
# Check runs (GitHub Actions)
curl -sH "Authorization: token $GITHUB_TOKEN" \
  -H "Accept: application/vnd.github.v3+json" \
  "https://api.github.com/repos/mock-server/mockserver-monorepo/commits/{head_sha}/check-runs" \
  | jq '{total_count, check_runs: [.check_runs[] | {name, status, conclusion}]}'
```

## Step 3: Check Base Branch Freshness

Determine if each PR is up-to-date with its base branch:

```bash
# Compare base branch with PR head
curl -sH "Authorization: token $GITHUB_TOKEN" \
  -H "Accept: application/vnd.github.v3+json" \
  "https://api.github.com/repos/mock-server/mockserver-monorepo/compare/{base_ref}...{head_sha}" \
  | jq '{behind_by, ahead_by, status}'
```

- `behind_by > 0` means the PR is out-of-date with the base branch
- `status: "diverged"` means both branches have new commits

## Step 4: Classify Each PR

Apply these classifications in order (first match wins):

### Category: STALE
- Last updated more than 6 months ago
- No activity (comments, commits, reviews) in 6+ months

### Category: OUT_OF_DATE
- PR is behind the base branch (`behind_by > 0`)
- May have merge conflicts (`mergeable: false`)

### Category: MERGEABLE
- `mergeable: true`
- All CI checks pass (status: success)
- Has at least one approving review, OR is from a bot (dependabot, snyk)
- Not a draft

### Category: NEEDS_WORK
- Has requested changes in reviews
- CI checks are failing
- Is a draft PR
- Has merge conflicts but is recently active

### Category: BLOCKED
- Waiting on external dependency
- Explicitly labeled as blocked

## Step 5: Detect Duplicates

Compare PRs for potential duplicates using these signals:

1. **Same files changed:** PRs modifying the same set of files
2. **Similar titles:** PRs with similar titles (e.g., both mention "snyk", "dependabot", same dependency)
3. **Same author + same dependency:** Multiple Snyk/Dependabot PRs for the same package
4. **Overlapping branches:** PRs targeting the same files with similar diffs

```bash
# Get files changed per PR
curl -sH "Authorization: token $GITHUB_TOKEN" \
  -H "Accept: application/vnd.github.v3+json" \
  "https://api.github.com/repos/mock-server/mockserver-monorepo/pulls/{number}/files" \
  | jq '[.[] | .filename]'
```

Group PRs by:
- Dependencies they update (parse from title/body: "bump X from Y to Z")
- Files they modify
- Author (bot PRs from same bot often overlap)

## Step 6: Generate Report

Produce a structured report with these sections:

### Output Format

```markdown
# MockServer PR Review Report

**Generated:** {date}
**Open PRs:** {count}
**Repository:** mock-server/mockserver-monorepo

## Summary

| Category | Count | Action |
|----------|-------|--------|
| Mergeable | N | Ready to merge |
| Needs Work | N | Requires changes or CI fixes |
| Out of Date | N | Needs rebase/update |
| Stale | N | Consider closing |
| Duplicates | N groups | Consolidate |

## Mergeable PRs (Ready to Merge)

| PR | Title | Author | Age | CI | Reviews |
|----|-------|--------|-----|-----|---------|
| #N | title | author | Xd | pass | approved |

## PRs Needing Work

| PR | Title | Author | Issue | Last Activity |
|----|-------|--------|-------|---------------|
| #N | title | author | CI failing / changes requested | Xd ago |

## Out-of-Date PRs

| PR | Title | Author | Behind By | Last Updated | Has Conflicts |
|----|-------|--------|-----------|--------------|---------------|
| #N | title | author | N commits | date | yes/no |

## Stale PRs (Recommend Closing)

| PR | Title | Author | Last Activity | Reason |
|----|-------|--------|---------------|--------|
| #N | title | author | date | No activity for X months |

## Potential Duplicates

### Group 1: {description}
| PR | Title | Author | Files Changed |
|----|-------|--------|---------------|
| #N | title | author | file1, file2 |
| #M | title | author | file1, file2 |
**Recommendation:** Keep #N (newer/more complete), close #M

## Recommendations

1. **Merge immediately:** List PR numbers
2. **Close as stale:** List PR numbers with reasoning
3. **Consolidate duplicates:** Which to keep and which to close
4. **Request updates:** Which PRs need author action
```

## Notes

- Bot PRs (Dependabot, Snyk) often accumulate and become stale; prioritize identifying these
- PRs that only modify `pom.xml` for version bumps are often duplicated by multiple bots
- Check if a stale PR's changes have already been superseded by a newer PR or direct commit
- For dependency update PRs, check if the dependency was already updated in the main branch
