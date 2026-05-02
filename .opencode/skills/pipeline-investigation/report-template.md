# Pipeline Investigation Report Template

Use this template to convert the `pipeline-investigation/v1` JSON returned by
the skill subagent into a formatted markdown report.

---

## Markdown Report

```markdown
# Pipeline Investigation: {build.pipeline}

**Build Number**: {build.number}
**Branch**: {build.branch}
**Commit**: {build.commit}
**State**: {build.state}
**Failed Job**: {build.failed_job}
**Time**: {build.started_at} - {build.finished_at}

---

## Root Cause

{root_cause.summary}

### Detail

{root_cause.detail}

### Error Excerpt

```
{root_cause.error_excerpt}
```

---

## Fix Status: {fix_status}

### Variant: ALREADY FIXED

Commit `{fix_commit}` on master: "{fix_message}"
The next build should pass.

### Variant: OPEN

No fix found. See Recommended Fix below.

---

## GitHub Actions

<!-- Omit section if github_actions is empty. -->

| Run ID | Workflow | Conclusion | Root Cause |
|--------|----------|------------|------------|
| {run_id} | {workflow} | {conclusion} | {root_cause} |

---

## Recommended Fix

<!-- Omit if fix_status is not OPEN. -->

{recommended_fix}

---

## Summary

- **Root cause**: {root_cause.summary}
- **Fix status**: {fix_status}
- **Action**: {recommended_fix || "None - fix already applied"}
```

---

## File Naming Convention

Save markdown reports to:
```
docs/investigation/pipeline-investigations/{YYYY-MM-DD}-build-{build_number}.md
```

---

## Severity-Based Detail

- **fix_status = OPEN**: Full detail on all sections including recommended fix.
- **fix_status = ALREADY_FIXED**: Abbreviated report. Root cause + fix commit. Omit recommended fix.
