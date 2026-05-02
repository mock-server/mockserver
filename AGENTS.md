# MockServer — Agent Instructions

## Instruction Priority

1. Direct user instructions (highest)
2. Rules in `.opencode/rules/`
3. This file (`AGENTS.md`)
4. Skills in `.opencode/skills/`
5. Reference docs in `.opencode/reference/`

## Project Overview

MockServer is an open-source HTTP(S) mock server and proxy for testing, written in Java. It uses Netty as the HTTP server framework, Maven for builds, and is deployed as Docker containers, JARs, and WARs.

**Tech stack:** Java 8+, Netty 4.1, Jackson 2.14, Maven (multi-module), Docker, Helm, Jekyll (documentation site)
**CI/CD:** Buildkite (primary CI), GitHub Actions (Docker image builds, CodeQL)
**Infrastructure:** AWS, Docker Hub (container images)

### AWS Accounts

| Account ID | Purpose |
|------------|---------|
| 814548061024 | Pipeline build agents and infrastructure |
| 014848309742 | Website (S3, CloudFront, DNS, TLS) |
**Repository:** GitHub (github.com)

## Git Policy

- NEVER commit or push without explicit user request
- NEVER run destructive git commands without confirmation (see `.opencode/rules/git-safety.md`)
- NEVER add Co-Authored-By, Signed-off-by, or any other trailers to commit messages
- NEVER amend commits that have been pushed to remote

## Pre-Commit Testing

Before every commit:
1. Run unit tests for affected modules: `./mvnw test -pl <module>`
2. Run code review on `git diff` against project conventions
3. Only then commit

## Code Navigation

- Use grep/glob for finding code across the codebase
- Read surrounding context before making changes
- Follow existing code conventions in neighboring files

## Fix Placement Policy

Always fix bugs and add features at the architecturally correct layer. If a bug surfaces in `mockserver-netty` but the root cause is in `mockserver-core`, fix it in `mockserver-core`.

## Temporary Files

Use `.tmp/` at the repo root for scratch files — never `/tmp/`. See `.opencode/rules/tmp-directory.md`.

## Code Review Routing

When the user asks for a code review:
- Quick pre-commit check: use `code-reviewer` agent
- Deep audit: use `/review-code` command
- Spec/design review: use `/review-spec` command

## Subagent Routing

| Task | Subagent Type |
|------|---------------|
| Code review (pre-commit) | `code-reviewer` |
| Intermediate deep review | `review-cheap` |
| Final authoritative review | `review-final` |
| Implementation work | `impl-worker` |
| Code simplification | `simplifier` |
| Test execution | `test-runner` |
| Security audit | `security-auditor` |
| Documentation writing | `docs-writer` |
| Pipeline investigation | `pipeline-investigator` |
| Debugging/investigation | `debugger` |
| Task decomposition | `taskify-agent` |
| Design council seat | `council-seat` |

## Convention-Based Skill Invocation

Skills whose description contains `MUST be launched as a Task subagent with subagent_type "<type>"` MUST be launched via the Task tool with the specified `subagent_type`. NEVER load them directly with the `skill` tool.

## Research-First Problem Solving

When investigating issues or answering technical questions:
1. Search the codebase first
2. Search online documentation
3. Only then rely on training data
