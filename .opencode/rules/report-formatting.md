# Skill Output Handling & Report Formatting

Some skills return **structured JSON** instead of formatted reports. The parent
agent (you) is responsible for converting JSON into the final output.

## CRITICAL: Convention-Based Subagent Detection

**You do NOT need a hardcoded list.** Any skill whose description in `<available_skills>` contains the phrase
`MUST be launched as a Task subagent with subagent_type "<type>"` MUST be launched via the **Task tool**
with the specified `subagent_type`. NEVER load it directly with the `skill` tool.

**How to detect:** When a skill is invoked (slash command or conversational), check its description in
`<available_skills>`. If the subagent marker is present, extract the `subagent_type` and route through Task.

**How to find the report template:** Always at `.opencode/skills/<skill-name>/report-template.md`.

**Correct flow:**
1. User invokes a skill (e.g., `/pipeline-investigation <url>` or "investigate my pipeline")
2. Check the skill's description in `<available_skills>` for the subagent routing marker
3. If marker found: launch a `Task` with the extracted `subagent_type`, passing the user's full message
4. The subagent loads the skill via the `skill` tool, executes it, and returns structured JSON
5. Parent agent reads `.opencode/skills/<skill-name>/report-template.md`, formats the JSON, displays to user

**If no marker found:** Load the skill directly via the `skill` tool as normal.

## Workflow After Receiving JSON From a Skill

1. **Read the skill's `report-template.md`** — it contains the markdown template with field placeholders matching the JSON schema.
2. **Map JSON fields to the template** — replace placeholders with actual values from the returned JSON. Follow the template's severity-based detail rules.
3. **Write the report** to the file path specified in the template's naming convention section.

## Key Principles

- **The subagent does investigation only** — it queries data sources, analyses findings, and returns structured JSON. It does NOT write files.
- **The parent agent does formatting only** — it reads the template, maps JSON to markdown, writes files. It does NOT re-investigate.
- **The executive summary from the subagent** (3-5 lines after the JSON) can be shown to the user immediately as a quick status update while you format the full report.
