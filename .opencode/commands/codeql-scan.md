---
description: Run CodeQL security scan on Java code
agent: security-auditor
subtask: true
---
Run a CodeQL-oriented security scan workflow for the following request:

$ARGUMENTS

Workflow:
1. Validate GitHub authentication and discover recent CodeQL workflow runs.
2. If a recent run exists, summarise failed alerts/checks with evidence.
3. If no run exists for the target branch, provide exact `gh` commands to trigger and inspect one.
4. Return findings with severity, impacted files, and remediation guidance.
