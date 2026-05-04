# Skill: Dependabot and Snyk PR Management

Interact with Dependabot and Snyk pull requests for dependency upgrades and security fixes.

## When to Use

Use when:
- User asks to "rebase Dependabot PR", "merge Dependabot PR", or similar
- User asks to "update dependencies", "fix Snyk alerts", or similar
- Investigating failed dependency upgrade PRs
- Reviewing security vulnerability PRs from Snyk or Dependabot

## Dependabot Commands

Dependabot responds to commands in PR comments. All commands are invoked via `@dependabot <command>`.

### Common Commands

| Command | Purpose | When to Use |
|---------|---------|-------------|
| `@dependabot rebase` | Rebase PR onto latest base branch | When PR is behind master or needs to pick up recent fixes |
| `@dependabot recreate` | Close and recreate PR from scratch | When PR is in a bad state or has conflicts |
| `@dependabot merge` | Merge PR (if all checks pass) | To auto-merge a passing PR |
| `@dependabot squash and merge` | Squash and merge PR | Preferred for dependency bumps to keep history clean |
| `@dependabot cancel merge` | Cancel a previously requested merge | If you change your mind |
| `@dependabot close` | Close PR without merging | To reject an upgrade |
| `@dependabot reopen` | Reopen a closed PR | To reconsider a previously rejected upgrade |
| `@dependabot ignore this dependency` | Never upgrade this dependency | For permanently pinned dependencies |
| `@dependabot ignore this major version` | Skip this major version | When major version requires code changes |
| `@dependabot ignore this minor version` | Skip this minor version | When minor version has issues |
| `@dependabot unignore this dependency` | Remove ignore rule | Re-enable upgrades for a dependency |

### Example Usage

**Rebase a Dependabot PR:**
```bash
gh pr comment <PR_NUMBER> --body "@dependabot rebase"
```

**Merge a Dependabot PR (squash):**
```bash
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

2. **Test the fix:**
   - Snyk PRs trigger the same CI checks as any PR
   - Wait for Buildkite, CodeQL, and Snyk checks to pass
   - Run local tests if needed

3. **Merge or close:**
   - If tests pass: merge via GitHub UI or `gh pr merge`
   - If tests fail: investigate failure (may need code changes)
   - If false positive: close and mark as ignored in Snyk UI

### Snyk Commands

Snyk bot does **not** respond to comments like Dependabot. To manage Snyk PRs:

**Merge:**
```bash
gh pr merge <PR_NUMBER> --squash
```

**Close:**
```bash
gh pr close <PR_NUMBER>
```

**Rebase manually (if needed):**
```bash
gh pr comment <PR_NUMBER> --body "Rebasing onto latest master"
git fetch origin pull/<PR_NUMBER>/head:snyk-fix
git checkout snyk-fix
git pull --rebase origin master
git push --force-with-lease origin snyk-fix:snyk/<branch-name>
```

## Dependabot Configuration

Dependabot is configured in `.github/dependabot.yml`. Key settings:

```yaml
version: 2
updates:
  - package-ecosystem: "maven"
    directory: "/"
    schedule:
      interval: "weekly"
    open-pull-request-limit: 10
    reviewers:
      - "jamesdbloom"
    ignore:
      # Java 17+ dependencies blocked (Java 11 compatibility)
      - dependency-name: "org.springframework:*"
        versions: ["6.x"]
      - dependency-name: "jakarta.*:*"
        versions: ["9.x", "10.x"]
```

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

**Solution:**
```bash
# List all open Dependabot PRs
gh pr list --author app/dependabot --state open --json number,title

# Merge each passing PR
for pr in $(gh pr list --author app/dependabot --state open --json number --jq '.[].number'); do
  gh pr comment $pr --body "@dependabot squash and merge"
done
```

**Note:** Only do this if you've verified all checks are passing and upgrades are safe.

### Scenario 4: Dependabot PR Has Merge Conflicts

**Problem:** Dependabot PR shows merge conflicts.

**Solution:**
```bash
# Ask Dependabot to recreate the PR (resolves conflicts automatically)
gh pr comment <PR_NUMBER> --body "@dependabot recreate"
```

If recreate fails, you may need to manually resolve conflicts or close the PR.

### Scenario 5: Block a Major Version Upgrade

**Problem:** Dependabot proposes a major version upgrade that requires code changes (e.g., Spring 5 → 6).

**Solution:**
```bash
gh pr comment <PR_NUMBER> --body "@dependabot ignore this major version"
```

This tells Dependabot to skip this major version but continue proposing minor/patch updates.

### Scenario 6: Snyk PR Fixes Critical CVE

**Problem:** Snyk creates PR to fix CVE-2024-12345 (critical severity).

**Solution:**
1. Review the CVE and verify severity
2. Check if tests pass
3. Merge immediately if safe:
   ```bash
   gh pr merge <PR_NUMBER> --squash --auto
   ```
4. If tests fail, investigate and fix before merging

## Java 11 Compatibility Requirements

MockServer targets Java 11 as the minimum supported version. When reviewing Dependabot PRs, **always check for Java 17+ dependencies:**

### Blocked Upgrades (Java 17+ required)

| Dependency | Max Version (Java 11) | Blocked Version | Reason |
|------------|----------------------|-----------------|---------|
| `org.springframework:*` | 5.x | 6.x+ | Requires Java 17 |
| `jakarta.*:*` | 8.x | 9.x+ | Requires Java 17 |
| `org.eclipse.jetty:*` | 9.x | 10.x+, 12.x+ | Requires Java 17 |
| `org.apache.tomcat:*` | 9.x | 10.x+ | Requires Java 17 |

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

3. **Update `.github/dependabot.yml` if needed:**
   ```yaml
   ignore:
     - dependency-name: "org.springframework:*"
       versions: ["6.x", "7.x"]
   ```

See `docs/operations/dependencies.md` for the full list of Java 11 compatibility constraints.

## Troubleshooting

### Dependabot Command Not Working

**Symptoms:** Comment posted but Dependabot doesn't respond.

**Causes:**
- Typo in command (must be exact: `@dependabot rebase`, not `@dependabot please rebase`)
- Command not supported for this PR type (e.g., non-Dependabot PR)
- Dependabot bot disabled for repository

**Solution:**
- Check command syntax (case-sensitive, exact match)
- Verify PR author is `app/dependabot`
- Check GitHub repository settings → Dependabot

### Dependabot Rebase Creates Conflicts

**Symptoms:** Dependabot comments "I couldn't rebase due to conflicts."

**Solution:**
1. Try `@dependabot recreate` instead (creates fresh PR)
2. If recreate fails, manually rebase:
   ```bash
   gh pr checkout <PR_NUMBER>
   git pull --rebase origin master
   # Resolve conflicts
   git add .
   git rebase --continue
   git push --force-with-lease
   ```

### Snyk PR Tests Fail After Upgrade

**Symptoms:** Snyk upgrades a dependency but tests fail.

**Causes:**
- Breaking change in upgraded dependency
- Transitive dependency incompatibility
- Test needs updating for new API

**Solution:**
1. Review the Snyk PR diff to see what changed
2. Check release notes for breaking changes
3. Run tests locally to debug:
   ```bash
   gh pr checkout <PR_NUMBER>
   ./mvnw clean test
   ```
4. Either:
   - Fix the code to work with the new version, OR
   - Close the Snyk PR and manually pin the old version

## Reference

- [Dependabot commands documentation](https://docs.github.com/en/code-security/dependabot/working-with-dependabot/managing-pull-requests-for-dependency-updates#managing-dependabot-pull-requests-with-comment-commands)
- [Snyk GitHub integration](https://docs.snyk.io/integrate-with-snyk/git-repositories-scms-integrations-with-snyk/snyk-github-integration)
- [MockServer Java 11 compatibility policy](../../AGENTS.md#java-compatibility-policy)
- [Dependency management guide](../../docs/operations/dependencies.md)
