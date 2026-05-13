---
description: Convene a design council — role-specialized subagents debate a technical decision
---
Run a design-council review for the following request:

$ARGUMENTS

Workflow:
1. Use the Agent tool to spawn three `council-seat` subagents in parallel with distinct role briefs (e.g. security, performance, maintainability).
2. Collect their verdicts (APPROVE / CONCERNS / BLOCK) and position papers.
3. Run one cross-talk round: share each seat's concerns with the others and spawn a second round.
4. Return a CEO decision with rationale, dissent summary, and action items.
