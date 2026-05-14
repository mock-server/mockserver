# Subagent Routing

Routing policy must live in routing configuration, not in skill descriptions.

## Core Rule

- `SKILL.md` files describe what a skill does and how it executes.
- Routing decisions (which agent runs a request) are defined by:
  - command files in `.opencode/commands/` (`agent:` + `subtask: true`),
  - this routing table for conversational requests,
  - permission policy in `opencode.jsonc`.

Do not add caller-only routing directives like:

`MUST be launched as a Task subagent with subagent_type "<type>"`

inside skill descriptions. These directives can cause subagents to attempt self-dispatch.

## Conversational Routing Table

When users ask conversationally (not via slash commands), route as follows:

| Skill | Subagent Type | Typical Requests |
|-------|---------------|------------------|
| `pipeline-investigation` | `pipeline-investigator` | "investigate this build", "why is CI failing" |
| `aws-investigation` | `debugger` | "check build agents", "debug ASG scaling" |

## Implementation Notes

- Slash commands remain authoritative for deterministic routing.
- Conversational routing should use this table when skill descriptions are hidden by permissions.
- If a new subagent-routed skill is added, update:
  1. `.opencode/commands/<skill>.md`
  2. this file's conversational routing table
  3. `scripts/validate_opencode_config.sh` validation assumptions
