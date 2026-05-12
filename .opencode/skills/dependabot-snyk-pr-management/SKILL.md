---
name: dependabot-snyk-pr-management
description: Interact with Dependabot and Snyk pull requests for dependency upgrades and security fixes. Documents Dependabot commands, Java 11 compatibility checks, safe merge workflows, and troubleshooting. Use when managing dependency upgrade PRs or security fix PRs.

---

# Dependabot and Snyk PR Management

Interact with Dependabot and Snyk pull requests for dependency upgrades and security fixes.

## When to Use

Use when:
- User asks to "rebase Dependabot PR", "merge Dependabot PR", or similar
- User asks to "update dependencies", "fix Snyk alerts", or similar
- Investigating failed dependency upgrade PRs
- Reviewing security vulnerability PRs from Snyk or Dependabot

## Safety Rules

**IMPORTANT:** Before merging, rebasing, or closing any PR:
1. Verify PR author is `app/dependabot` or a Snyk bot
2. Check CI status (all checks must pass for merge)
3. Verify Java 11 compatibility (see section below)
4. Review changed dependencies and scope
5. Never use `git add .` — stage explicit files only
6. Never force-push to bot-owned branches without explicit user approval

## Dependabot Commands

Dependabot responds to commands in PR comments. All commands are invoked via `@dependabot <command>`.

**Response behavior:** Dependabot reacts with 👍 and may take several minutes to process the command.

### Common Commands

| Command | Purpose | When to Use |
|---------|---------|-------------|
| `@dependabot rebase` | Rebase PR onto latest base branch | When PR is behind master or needs to pick up recent fixes |
| `@dependabot recreate` | Close and recreate PR from scratch | When PR is in a bad state or has conflicts |
| `@dependabot merge` | Merge PR (if all checks pass) | To auto-merge via Dependabot (alternative: `gh pr merge --auto`) |
| `@dependabot squash and merge` | Squash and merge PR | Preferred for dependency bumps to keep history clean |
| `@dependabot cancel merge` | Cancel a previously requested merge | If you change your mind |
| `@dependabot close` | Close PR without merging | To reject an upgrade |
| `@dependabot reopen` | Reopen a closed PR | To reconsider a previously rejected upgrade |
| `@dependabot ignore this dependency` | Never upgrade this dependency | For permanently pinned dependencies |
| `@dependabot ignore this major version` | Skip this major version | When major version requires code changes |
| `@dependabot ignore this minor version` | Skip this minor version | When minor version has issues |
| `@dependabot ignore this patch version` | Skip this patch version | When patch version has issues |
| `@dependabot show <dep> ignore conditions` | Show current ignore rules for dependency | Before removing ignore rules |
| `@dependabot unignore <dep>` | Remove all ignore rules for dependency | Re-enable upgrades for a specific dependency |

**Note:** Ignore commands create repository-level preferences. For team visibility, **also update `.github/dependabot.yml`** when adding permanent ignore rules.

### Example Usage

**Rebase a Dependabot PR:**
```bash
gh pr comment <PR_NUMBER> --body "@dependabot rebase"
```

**Merge a Dependabot PR (preferred: use GitHub native merge):**
```bash
# Preferred: GitHub native merge with auto-merge when checks pass
gh pr merge <PR_NUMBER> --squash --auto

# Alternative: delegate to Dependabot
gh pr comment <PR_NUMBER> --body "@dependabot squash and merge"
```

**Ignore a major version:**
```bash
gh pr comment <PR_NUMBER> --body "@dependabot ignore this major version"
```

**Close and reject an upgrade:**
```bash
gh pr comment <PR_NUMBER> --body "@dependabot close"
```

### Workflow: Rebase to Trigger Fresh Build

When a Dependabot PR fails due to a flaky test or a fix has been merged to master:

1. **Ask Dependabot to rebase:**
   ```bash
   gh pr comment <PR_NUMBER> --body "@dependabot rebase"
   ```

2. **Dependabot will:**
   - Rebase the PR branch onto latest master
   - Push the rebased commits
   - Trigger all CI checks (Buildkite, CodeQL, Snyk, etc.)

3. **Monitor the rebuild:**
   ```bash
   gh pr checks <PR_NUMBER> --watch
   ```

**This is the preferred method** for re-triggering builds on Dependabot PRs because:
- ✅ Simpler than manual empty commits
- ✅ Picks up latest master changes automatically
- ✅ Dependabot manages the rebase (no local checkout needed)
- ✅ Maintains Dependabot authorship and metadata

## Snyk PRs

Snyk creates PRs to fix security vulnerabilities. These PRs are created by the Snyk bot and follow similar patterns to Dependabot.

### Snyk PR Workflow

1. **Review the vulnerability:**
   - Check the Snyk PR description for CVE details
   - Review the severity (critical, high, medium, low)
   - Check if the fix is a direct or transitive dependency upgrade
   - **Check Java 11 compatibility** (see section below)

2. **Test the fix:**
   - Snyk PRs trigger the same CI checks as any PR
   - Wait for Buildkite, CodeQL, and Snyk checks to pass
   - Run local tests if needed

3. **Merge or close:**
   - If tests pass and Java 11 compatible: `gh pr merge <PR_NUMBER> --squash`
   - If Java 17+ required: close and document workaround (pin old version or accept vulnerability)
   - If tests fail: investigate failure (may need code changes)
   - If false positive: close and mark as ignored in Snyk UI

### Snyk Commands

Snyk bot does **not** respond to comments like Dependabot. To manage Snyk PRs:

**Merge (preferred):**
```bash
gh pr merge <PR_NUMBER> --squash
```

**Close:**
```bash
gh pr close <PR_NUMBER>
```

**Rebase:**

Snyk PRs **cannot** be safely rebased via git commands because:
- Snyk bot owns the branch on the Snyk fork
- Force pushing to bot-owned branches can cause sync conflicts
- The PR cannot be updated from external forks without special permissions

**Safe alternatives:**
1. Use GitHub UI "Update branch" button (if available)
2. Close the PR and wait for Snyk to create a new one
3. Ask Snyk to recreate via Snyk UI: Project Settings → Integrations → GitHub → Re-test

## Dependabot Configuration

Dependabot is configured in `.github/dependabot.yml`. **Always check that file for the complete, authoritative configuration.**

**Key ecosystems:**
- **Maven** (`/mockserver`) - Java dependencies, weekly schedule Monday, 10 PR limit
- **npm** (`/.opencode`) - OpenCode config dependencies, weekly schedule Monday, 5 PR limit  
- **GitHub Actions** (`/`) - Workflow dependencies, weekly schedule Monday, 5 PR limit

**Java 11 compatibility blocks:**

The configuration blocks major version upgrades for dependencies requiring Java 17+ or Jakarta namespace migration using `update-types: ["version-update:semver-major"]`. See the `ignore` section in `.github/dependabot.yml` for the complete list, which includes:

- **Spring Framework/Boot** - Blocks 6.x+ (requires Java 17)
- **Tomcat, Jetty** - Blocks 10.x+ (requires Java 17 or Jakarta namespace)
- **Servlet** - Blocks major versions (Jakarta `javax.*` → `jakarta.*` migration)
- **Prometheus** - Blocks major versions (API redesign from `simpleclient` to `prometheus-metrics`)
- **json-schema-validator, Checkstyle, json-unit, json-path** - Blocks major versions (API breaks)

## Common Scenarios

### Scenario 1: Dependabot PR Fails with Flaky Test

**Problem:** Buildkite build fails on Dependabot PR due to intermittent test failure.

**Solution:**
1. Check if the test failure is related to the dependency upgrade:
   ```bash
   gh pr view <PR_NUMBER>
   gh pr checks <PR_NUMBER>
   ```
2. If unrelated (flaky test), rebase to re-run:
   ```bash
   gh pr comment <PR_NUMBER> --body "@dependabot rebase"
   ```
3. If still failing, investigate the test failure before merging

### Scenario 2: Dependabot PR is Behind Master

**Problem:** PR shows "This branch is out-of-date with the base branch".

**Solution:**
```bash
gh pr comment <PR_NUMBER> --body "@dependabot rebase"
```

### Scenario 3: Merge Multiple Passing Dependabot PRs

**Problem:** Many Dependabot PRs have passed checks and are ready to merge.

**Solution (SAFE - requires checks and confirmation):**
```bash
# List all open Dependabot PRs with passing checks
gh pr list --author app/dependabot --state open --json number,title,statusCheckRollup \
  --jq '.[] | select(.statusCheckRollup | all(.conclusion == "SUCCESS" or .conclusion == null)) | {number: .number, title: .title}'

# Review the list, then merge each individually:
gh pr merge <PR_NUMBER> --squash --auto

# Alternative: ask Dependabot to merge (after verifying checks)
gh pr comment <PR_NUMBER> --body "@dependabot squash and merge"
```

**WARNING:** Do NOT use a blind loop. Always verify:
- All checks are passing
- Java 11 compatibility
- No breaking changes
- PR scope is reasonable

### Scenario 4: Dependabot PR Has Merge Conflicts

**Problem:** Dependabot PR shows merge conflicts.

**Solution:**
```bash
# Ask Dependabot to recreate the PR (resolves conflicts automatically)
gh pr comment <PR_NUMBER> --body "@dependabot recreate"
```

If recreate fails, close the PR and wait for Dependabot to create a new one.

### Scenario 5: Block a Major Version Upgrade

**Problem:** Dependabot proposes a major version upgrade that requires code changes (e.g., Spring 5 → 6).

**Solution:**
```bash
# Ignore this major version
gh pr comment <PR_NUMBER> --body "@dependabot ignore this major version"
```

Then **update `.github/dependabot.yml`** for team visibility:
```yaml
ignore:
  - dependency-name: "org.springframework:*"
    update-types: ["version-update:semver-major"]
```

### Scenario 6: Snyk PR Fixes Critical CVE

**Problem:** Snyk creates PR to fix CVE-2024-12345 (critical severity).

**Solution:**
1. **Check Java 11 compatibility first**
2. Review the CVE and verify severity
3. Check if tests pass
4. Merge immediately if safe:
   ```bash
   gh pr merge <PR_NUMBER> --squash --auto
   ```
5. If tests fail or Java 17+ required, investigate before merging

## Java 11 Compatibility Requirements

MockServer targets Java 11 as the minimum supported version. When reviewing Dependabot PRs, **always check for Java 17+ dependencies.**

See the Java 11 compatibility policy in `../../../AGENTS.md` and the Snyk policy in `../../../.snyk` for version ceilings.

### Quick Check: Is This PR Java 11 Compatible?

```bash
# Check the PR diff for blocked dependencies
gh pr diff <PR_NUMBER> | grep -E "(springframework|jakarta|jetty|tomcat\.embed|prometheus|json-schema-validator|checkstyle|json-unit|jsonpath)"
```

### How to Handle Java 17+ PRs

If Dependabot proposes a Java 17+ dependency:

1. **Close the PR:**
   ```bash
   gh pr comment <PR_NUMBER> --body "@dependabot close"
   ```

2. **Ignore the major version:**
   ```bash
   gh pr comment <PR_NUMBER> --body "@dependabot ignore this major version"
   ```

3. **Update `.github/dependabot.yml`:**
   ```yaml
   ignore:
     - dependency-name: "<dependency-pattern>"
       update-types: ["version-update:semver-major"]
   ```

## Troubleshooting

### Dependabot Command Not Working

**Symptoms:** Comment posted but Dependabot doesn't respond.

**Causes & Solutions:**
- **Typo in command** - Must be exact: `@dependabot rebase`, not `@dependabot please rebase`
- **Delayed processing** - Wait 2-5 minutes; check for Dependabot's 👍 reaction
- **Wrong PR type** - Verify PR author is `app/dependabot`
- **Dependabot disabled** - Check GitHub repository settings → Security → Dependabot

**Debugging:**
1. Check for Dependabot's thumbs-up reaction on your comment
2. Look for Dependabot's response comment (may take minutes)
3. Check the PR's "Dependabot commands and options" section
4. Verify the command is supported for this PR type (single-dependency vs grouped update)

### Dependabot Rebase Creates Conflicts

**Symptoms:** Dependabot comments "I couldn't rebase due to conflicts."

**Solution:**
```bash
# Try recreate instead (creates fresh PR from latest base)
gh pr comment <PR_NUMBER> --body "@dependabot recreate"
```

If recreate also fails, close the PR and wait for Dependabot to create a new one on its next run.

### Snyk PR Tests Fail After Upgrade

**Symptoms:** Snyk upgrades a dependency but tests fail.

**Causes:**
- Breaking change in upgraded dependency
- Transitive dependency incompatibility
- Java 17+ requirement
- Test needs updating for new API

**Solution:**
1. Review the Snyk PR diff:
   ```bash
   gh pr diff <PR_NUMBER>
   ```
2. Check release notes for breaking changes
3. Check Java compatibility (see section above)
4. Run tests locally to debug:
   ```bash
   gh pr checkout <PR_NUMBER>
    cd mockserver && ./mvnw clean test
   ```
5. Either:
   - Fix the code to work with the new version, OR
   - Close the Snyk PR and document the decision (pin old version, accept risk, etc.)

## Reference

- [Dependabot commands documentation](https://docs.github.com/en/code-security/dependabot/working-with-dependabot/managing-pull-requests-for-dependency-updates#managing-dependabot-pull-requests-with-comment-commands)
- [Snyk GitHub integration](https://docs.snyk.io/integrate-with-snyk/git-repositories-scms-integrations-with-snyk/snyk-github-integration)
- [MockServer Java 11 compatibility policy](../../../AGENTS.md#java-compatibility-policy)
- [Security and dependency constraints](../../../docs/operations/security.md)
- [Dependabot configuration file](../../../.github/dependabot.yml)
