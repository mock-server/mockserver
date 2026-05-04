---
description: Convene a design council — role-specialized subagents debate a technical decision with you as CEO
agent: general
subtask: true
---
Run a design-council review for the following request:

$ARGUMENTS

Workflow:
1. Spawn three `council-seat` subtasks with distinct viewpoints.
2. Run one cross-talk round where seats critique each other's concerns.
3. Return a CEO decision with rationale, dissent summary, and action items.
