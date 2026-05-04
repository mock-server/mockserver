---
description: Critical review of a specification or design document
agent: review-final
subtask: true
---
Perform a deep, read-only specification review for the following request:

$ARGUMENTS

Workflow:
1. Build an independent model of the problem and scope.
2. Check clarity, testability, edge cases, constraints, and rollout risk.
3. Return PASS or BLOCK with severity-ranked findings and actionable fixes.
4. Cite concrete sections or file references for each finding.
