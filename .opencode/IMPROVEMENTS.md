# Opencode Configuration — Improvements & Recommendations

## Improvements Already Applied

### 1. JSON Schema Reference
Added `"$schema": "https://opencode.ai/config.json"` to `opencode.jsonc` for editor autocomplete and validation.

### 2. Compaction Pruning
Enabled `compaction.prune: true` to automatically remove old tool outputs and save tokens during long sessions.

### 3. Explicit Instructions Loading
Added `"instructions": ["AGENTS.md"]` so the project instructions are always loaded into context, not just discovered by convention.

### 4. Correct Permission Schema
The source repo used `permissions` (plural) which is not the correct opencode config key. Fixed to `permission` with proper nested bash pattern syntax:
```jsonc
"permission": {
  "bash": {
    "*": "allow",
    "git push --force*": "deny"
  }
}
```

### 5. Tool Restrictions Per Agent
Read-only agents now have `write`/`edit` disabled at the agent config level, enforcing their read-only contract structurally rather than relying solely on prompt instructions:
- `code-reviewer`, `review-cheap`, `review-final` — no file modification
- `debugger`, `pipeline-investigator`, `security-auditor`, `test-runner` — no file modification
- `council-seat` — no file modification AND no bash access

### 6. Jekyll Watcher Ignores
Added `_site/**` and `.sass-cache/**` to the watcher ignore list to prevent the Jekyll documentation site's build output from triggering file change events.

### 7. Grill → Review Rename
All references to "grill" have been renamed to "review" for clarity: `/review-code`, `/review-spec`, `review-cheap`, `review-final`.

---

## Recommendations for Future Enhancement

### LSP (Java Language Server) — Already Working
Opencode has built-in support for `jdtls` (Eclipse JDT Language Server) which auto-activates when `.java` files are detected and JDK 21+ is installed. JDK 21.0.3 (Azul Zulu via SDKMAN) is already installed — no action needed. The LLM gets real-time compile error diagnostics, type resolution, and go-to-definition automatically.

### Code Formatter — Not Needed
The project already has comprehensive formatting configuration:
- **`.editorconfig`** (265 lines) — IntelliJ-specific `ij_java_*` settings covering indentation (4 spaces), K&R braces, 220-char max line length, wildcard imports after 3 names, chained call wrapping, annotation placement, and more.
- **`maven-checkstyle-plugin`** (v3.2.1) — runs at the `validate` phase, enforcing no tabs, import ordering, whitespace, naming, and brace placement via `checkstyle.xml`.

`google-java-format` would conflict with this style (it enforces 100-char lines, no wildcard imports, and different wrapping). The existing `.editorconfig` + checkstyle setup is the correct formatter for this project. The `jdtls` LSP will pick up `.editorconfig` settings automatically.

### MCP Servers
Model Context Protocol servers connect external tools to opencode. Consider:

- **Context7** — doc search MCP for looking up library documentation:
  ```jsonc
  "mcp": {
    "context7": {
      "type": "remote",
      "url": "https://mcp.context7.com/mcp"
    }
  }
  ```
- **Buildkite MCP** — if one becomes available, it would give the LLM direct access to build data without shell commands
- **GitHub MCP** — available but adds many tokens to context; the `gh` CLI (already permitted) is more efficient

**Action:** Add Context7 for doc search. Monitor for a Buildkite MCP server.

### Model Per Agent — Configured
Per-agent models are now set in `opencode.jsonc` with a 4-tier cost/quality strategy:

| Tier | Model | Cost | Agents |
|------|-------|------|--------|
| Premium (code gen) | `opus-4.6` | $5/$25 | `implementer`, `simplifier` |
| Standard (analysis) | `sonnet-4.6` | $3/$15 | `code-reviewer`, `security-auditor`, `docs-writer`, `taskify-agent` |
| Independent review | `gpt-5.5` | $5/$30 | `review-final`, `debugger`, `pipeline-investigator` |
| Budget | `kimi-k2.6` | $0.95/$4 | `review-cheap` |
| Cheap/fast | `haiku-4.5` | $1/$5 | `test-runner`, `council-seat`, `small_model` (titles/summaries) |

The `review-final` and `debugger` use GPT-5.5 (different provider) to avoid shared blind spots with the Anthropic models that write the code.

### Default Agent — Configured
Set `"default_agent": "plan"` in `opencode.jsonc`. Opencode now starts in plan mode, proposing changes before making them. Switch to build mode with **Tab** when ready to execute.

### Plugins — Implemented
Two plugins created in `.opencode/plugins/`:

- **`buildkite-status.ts`** — On session start, queries the Buildkite API for the 5 most recent builds. Shows a toast warning if any are failing, and writes details to `.tmp/.buildkite-status`. Throttled to once per hour. Requires `BUILDKITE_TOKEN` env var (silently skips if not set).
- **`session-notification.ts`** — Sends a macOS notification when a session completes or errors, so you know when long-running tasks finish.

### Session Sharing — Configured
Enabled `"share": "manual"` in `opencode.jsonc` (overrides the global `disabled` setting). Use `/share` after investigations or complex debugging sessions to create a shareable link.

### Snapshot (Undo/Redo)
Opencode tracks file changes during agent operations, enabling `/undo` and `/redo` in the TUI. This is enabled by default. For very large repositories it can cause slow indexing — disable with `"snapshot": false` if needed, but for MockServer the default should be fine.

### Skill Permissions — Configured
Agents that have no reason to load skills now have `"skill": false` in their tool config:
- `test-runner`, `code-reviewer`, `security-auditor`, `debugger`, `council-seat` — these are focused agents that should never load skills (they read code or run commands, not orchestrate workflows)

Agents that need skills retain access:
- `pipeline-investigator` — loads the `pipeline-investigation` skill
- `review-cheap`, `review-final` — load review skills (`review-code`, `review-spec`)
- `implementer`, `simplifier`, `docs-writer`, `taskify-agent` — may load skills as part of their workflows
