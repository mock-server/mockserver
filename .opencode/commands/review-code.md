---
description: Deep audit of code for correctness, security, testing, and resilience
agent: review-final
subtask: true
---
Perform a deep, read-only audit for the following request:

$ARGUMENTS

Workflow:
1. Inspect staged and unstaged diffs plus surrounding file context.
2. Evaluate correctness, security, test adequacy, resilience, and maintainability.
3. Return PASS or BLOCK with severity-ranked findings and `file:line` evidence.
4. Include a concise remediation plan for every BLOCK finding.
