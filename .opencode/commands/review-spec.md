---
description: Critical review of a specification or design document
agent: review-final
subtask: true
---
Perform a deep, read-only specification review for the following request:

$ARGUMENTS

Workflow:
1. Read `.opencode/rules/review-constitution.md` and apply all 8 lenses
2. Build an independent model of the problem and scope
3. Check clarity, testability, edge cases, constraints, and rollout risk
4. Format findings using constitution finding format with principle IDs (e.g., [AMB-04], [INC-11])
5. Complete the Review Completeness Check
6. Return PASS or BLOCK with severity-ranked findings and actionable fixes
7. Cite concrete sections or file references for each finding
