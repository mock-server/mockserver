---
description: Deep audit of code for correctness, security, testing, and resilience
---
Use the Agent tool to spawn a `review-final` subagent to perform a deep, read-only audit for the following request:

$ARGUMENTS

The review-final agent will:
1. Read `.opencode/rules/review-constitution.md` and apply all 8 lenses
2. Inspect staged and unstaged diffs plus surrounding file context
3. Evaluate correctness, security, test adequacy, resilience, and maintainability
4. Format findings using constitution finding format with principle IDs (e.g., [SEC-06], [COR-10])
5. Complete the Review Completeness Check
6. Return PASS or BLOCK with severity-ranked findings and `file:line` evidence
7. Include a concise remediation plan for every CRITICAL/MAJOR finding
