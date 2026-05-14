# Skill Output Handling & Report Formatting

Some skills return **structured JSON** instead of formatted reports. The parent
agent (you) is responsible for converting JSON into the final output.

## CRITICAL: Subagent Routing Source Of Truth

Do not infer routing from skill descriptions. Route subagent-required skills using:

- command frontmatter (`agent:` + `subtask: true`) for slash commands,
- `.opencode/rules/subagent-routing.md` for conversational requests.

**How to find the report template:** Always at `.opencode/skills/<skill-name>/report-template.md`.

**Correct flow:**
1. User invokes a skill (e.g., `/pipeline-investigation <url>` or "investigate my pipeline")
2. Resolve the correct subagent via command metadata or the routing table
3. Launch a `Task` with the resolved `subagent_type`, passing the user's full message
4. The subagent loads the skill via the `skill` tool, executes it, and returns structured JSON
5. Parent agent reads `.opencode/skills/<skill-name>/report-template.md`, formats the JSON, displays to user

If a skill is not listed as subagent-routed, load it directly via the `skill` tool as normal.

**Incorrect flow (causes raw JSON output):**
1. Parent agent loads a subagent-required skill directly via `skill` tool
2. Parent agent follows the "return structured JSON" instructions meant for the subagent
3. Raw JSON is displayed to the user — or worse, the parent lacks the specialist agent's tools

### Special Cases

- Script-based skills use external scripts that handle their own formatting
  and output. They are NOT part of this JSON-return pattern.

## Workflow After Receiving JSON From a Skill

1. **Read the skill's `report-template.md`** — it contains the markdown
   template with field placeholders matching the JSON schema.
2. **Map JSON fields to the template** — replace placeholders with actual values
   from the returned JSON. Follow the template's severity-based detail rules
   (e.g., HEALTHY reports are abbreviated, CRITICAL reports get full detail).
3. **Apply any redaction rules** listed in the template (if applicable).
4. **Write the report** to the file path specified in the template's naming
   convention section (e.g., `docs/investigation/pipelines/{YYYY-MM-DD}-pipeline-status.md`).

## Dual-Mode Contract: Inline vs Manifest

Skills that return structured JSON support two output modes:

| Mode | Flag | Subagent writes to disk? | Returns to caller |
|------|------|-------------------------|-------------------|
| **Inline** (default) | _(none)_ | No | Full structured JSON |
| **Manifest** | `--manifest` | Yes — full report to skill-defined path | Compact manifest (~15 fields) |

**When to use `--manifest`:** Automated workflows and any caller that does not
need the full JSON in-context. The manifest contains the verdict/status, key
summary counts, `artifact_path`, and `next_action` — enough for stop-condition
checks without loading the full report into the caller's context window.

**When to use inline (default):** Interactive user requests and any flow where
the caller needs the full data to format a report or make detailed decisions.

**Manifest schema contract — all manifests share these fields:**

```json
{
  "schema_version": 1,
  "kind": "<skill-name>",
  "status": "<skill-specific status>",
  "summary": "<2-5 line executive summary>",
  "artifact_path": "<relative path to full report on disk>",
  "created_at": "<ISO 8601>",
  "next_action": "<suggested next step or null>"
}
```

Plus skill-specific summary fields (e.g., `findings_count`, `priority_action_counts`).
See each skill's "Output — Manifest Mode" section.

**Artifact path conventions:**

| Skill | Path pattern |
|-------|-------------|
| `pipeline-investigation` | `docs/investigation/pipelines/pipeline-investigation-<buildId>.json` |
| `aws-investigation` | `docs/investigation/aws/aws-investigation-<date>.json` |

## MANDATORY: Attribution in Every Summary

**This rule exists because attribution was repeatedly lost in parent-agent
summaries. It is non-negotiable.**

**Brevity exemption:** Attribution lines are EXEMPT from the system-level
"fewer than 4 lines" brevity constraint. When a subagent result includes
coordination/attribution metadata, the attribution line does NOT count
toward the line limit. Always include it — never omit it to save space.

When a subagent returns structured JSON (or a text result) that includes
coordination/attribution metadata, the parent agent MUST surface it
**prominently at the top** of any summary shown to the user — whether that
summary is a quick status update, a formatted report, or a conversational
response.

### What to surface

| Field | Display |
|-------|---------|
| `coordination.source` | State whether result is **cached**, **forced**, or **fresh** |
| `coordination.investigated_by` | **Always** show who originally investigated (name/email) |
| `coordination.cached_at` | Show when the cached investigation was performed |
| `coordination.other_investigators` | List all other engineers who investigated this build |
| `coordination.prior_investigations` | If multiple entries, show as a table |

### Formatting rule

The attribution line MUST be the **first line after the title** in any summary.
Do NOT bury it in a "Coordination" subsection or omit it for brevity.

**Correct:**
```
## Pipeline Investigation — Build 61070

**Previously investigated by engineer@example.com on 2026-04-29** (cached result)

**Pipeline:** ...
```

**Incorrect:**
```
## Pipeline Investigation — Build 61070

**Pipeline:** ...
(attribution buried later or omitted entirely)
```

### When it applies

- Pipeline investigations (cached results with `coordination` field)
- Any skill that returns investigator/author attribution metadata
- Any time a subagent reports that work was previously done by someone else

**If in doubt, include it.** Engineers need to know who to contact and whether
they are looking at fresh or stale analysis.

## Key Principles

- **The subagent does investigation only** — it queries data sources, analyses
  findings, and returns structured JSON. It does NOT write files
  (except in `--manifest` mode where it writes the report to disk).
- **The parent agent does formatting only** — it reads the template, maps JSON
  to markdown, writes files. It does NOT re-investigate.
- **The executive summary from the subagent** (3-5 lines after the JSON) can be
  shown to the user immediately as a quick status update while you format the
  full report.
