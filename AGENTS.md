# MockServer — Agent Instructions

## Instruction Priority

1. Direct user instructions (highest)
2. Rules in `.opencode/rules/`
3. This file (`AGENTS.md`)
4. Skills in `.opencode/skills/`
5. Reference docs in `.opencode/reference/`

## Project Overview

MockServer is an open-source HTTP(S) mock server and proxy for testing, written in Java. It uses Netty as the HTTP server framework, Maven for builds, and is deployed as Docker containers, JARs, and WARs.

**Tech stack:** Java 11+ (minimum supported), Netty 4.1, Jackson 2.14, Maven (multi-module), Docker, Helm, Jekyll (documentation site)
**CI/CD:** Buildkite (primary CI), GitHub Actions (Docker image builds, CodeQL)
**Infrastructure:** AWS (Buildkite build agents, documentation site hosting), Docker Hub (container images)
**Repository:** GitHub (github.com)

### Project Documentation

Comprehensive internal documentation is maintained in `docs/`. **Always consult these docs before making changes** to understand architecture, conventions, and dependencies:

| Document | When to consult |
|----------|----------------|
| [docs/README.md](docs/README.md) | Documentation index and quick reference |
| [docs/code/overview.md](docs/code/overview.md) | Before modifying any module — understand module boundaries and dependencies |
| [docs/code/netty-pipeline.md](docs/code/netty-pipeline.md) | Before modifying Netty handlers, protocol detection, or TLS |
| [docs/code/request-processing.md](docs/code/request-processing.md) | Before modifying mock matching, proxy forwarding, or action dispatch |
| [docs/code/event-system.md](docs/code/event-system.md) | Before modifying event logging, verification, or persistence |
| [docs/code/memory-management.md](docs/code/memory-management.md) | Before modifying maxLogEntries, maxExpectations, ring buffer sizing, or memory defaults |
| [docs/code/dashboard-ui.md](docs/code/dashboard-ui.md) | Before modifying the dashboard UI or WebSocket communication |
| [docs/code/domain-model.md](docs/code/domain-model.md) | Before modifying domain model, matchers, codecs, or configuration |
| [docs/code/tls-and-security.md](docs/code/tls-and-security.md) | Before modifying TLS, mTLS, certificates, or authentication |
| [docs/code/client-and-integrations.md](docs/code/client-and-integrations.md) | Before modifying client library, JUnit rules, or Spring integration |
| [docs/operations/build-system.md](docs/operations/build-system.md) | Before changing Maven config, plugins, or build scripts |
| [docs/infrastructure/ci-cd.md](docs/infrastructure/ci-cd.md) | Before modifying Buildkite or GitHub Actions pipelines |
| [docs/infrastructure/aws-infrastructure.md](docs/infrastructure/aws-infrastructure.md) | Before investigating AWS, Terraform, or Buildkite agent issues |
| [docs/infrastructure/docker.md](docs/infrastructure/docker.md) | Before modifying Dockerfiles, images, or Compose configs |
| [docs/infrastructure/helm.md](docs/infrastructure/helm.md) | Before modifying Helm charts or Kubernetes deployment |
| [docs/operations/website.md](docs/operations/website.md) | Before modifying the Jekyll documentation site |
| [docs/operations/dependencies.md](docs/operations/dependencies.md) | Before adding, removing, or upgrading dependencies |
| [docs/operations/release-process.md](docs/operations/release-process.md) | When performing or automating releases |
| [docs/operations/opencode-configuration.md](docs/operations/opencode-configuration.md) | Before modifying opencode config, agents, rules, skills, or commands |
| [docs/gaps-and-recommendations.md](docs/gaps-and-recommendations.md) | For improvement opportunities and known gaps |

When making changes to the project, **update the relevant docs/ file** if the change affects architecture, dependencies, build process, CI/CD, infrastructure, or deployment.

### Consumer Documentation

The consumer-facing documentation lives in `jekyll-www.mock-server.com/` and is published to https://www.mock-server.com. **Always consider consumer docs when making changes:**

1. **Read for context** — before changing behaviour, check the consumer docs to understand what users have been told to expect. Key pages:
   - `jekyll-www.mock-server.com/mock_server/configuration_properties.html` — all configuration properties with defaults and examples
   - `jekyll-www.mock-server.com/mock_server/_includes/running_docker_container.html` — Docker usage examples
   - `jekyll-www.mock-server.com/mock_server/_includes/performance_configuration.html` — performance tuning
2. **Update when behaviour changes** — if you change defaults, add properties, modify behaviour, or fix bugs that affect user-visible behaviour, update the consumer docs to match. Keep changes simple and clear — assume users have limited context.
3. **User-friendly language** — consumer docs should explain *what* a setting does and *why* a user might change it, not internal implementation details. Include practical guidance (e.g., "each HTTP request generates 2-3 log entries") rather than code-level details.

### AWS Accounts

| Account ID | Purpose |
|------------|---------|
| 814548061024 | Pipeline build agents and infrastructure |
| 014848309742 | Website (S3, CloudFront, DNS, TLS) |

### AWS Prerequisites

To investigate or manage AWS infrastructure:

1. **Install AWS CLI**: `brew install awscli`
2. **Configure SSO profile**: `aws configure sso --profile mockserver-build` (SSO region: `eu-west-1`, default region: `us-east-1`)
3. **Authenticate**: `aws sso login --profile mockserver-build`
4. **Corporate TLS proxy**: if behind a TLS inspection proxy, set `AWS_CA_BUNDLE` to your corporate root CA PEM file (e.g. `export AWS_CA_BUNDLE=$NODE_EXTRA_CA_CERTS`)
5. **macOS + Python 3.14 + Homebrew**: if you get `pyexpat` symbol errors, set `export DYLD_LIBRARY_PATH=/opt/homebrew/opt/expat/lib`

### Buildkite Agent Infrastructure

Build agents run on EC2 Spot instances in an AutoScaling Group, managed by a Lambda-based autoscaler. Infrastructure is managed by Terraform in `terraform/buildkite-agents/`. See [docs/infrastructure/aws-infrastructure.md](docs/infrastructure/aws-infrastructure.md) for full details.

| Property | Current (Terraform) | Legacy (CloudFormation) |
|----------|---------------------|------------------------|
| Region | `eu-west-2` | `us-east-1` |
| Instance Type | `t3.large` (Spot) | `t3.large` (On-Demand) |
| IaC in repo | Yes (`terraform/`) | No |
| Scaling | 0–2 instances, Lambda-based | 0–2 instances, Lambda-based |

The scaler runs every minute, scales 0-2 instances based on Buildkite job queue depth. `MIN_SIZE=0` means agents scale to zero when idle.

## Git Policy

- NEVER commit or push without explicit user request
- NEVER run `git commit` without first completing the full pre-commit workflow in `.opencode/rules/commit-workflow.md` (classify → validate → adversarial review → commit). Use the `/commit` command to ensure the workflow is followed.
- NEVER run destructive git commands without confirmation (see `.opencode/rules/git-safety.md`)
- NEVER add Co-Authored-By, Signed-off-by, or any other trailers to commit messages
- NEVER amend commits that have been pushed to remote

### Parallel Session Safety

Multiple opencode sessions may run concurrently on the same repository. You MUST:

- **Only commit files you changed in THIS session.** Never stage files modified by another session.
- **Never use `git add .` or `git add -A`.** Always stage files by explicit path.
- **Re-read files before editing.** Another session may have modified them since you last read.
- **Check `git status` before committing.** If unexpected changes appear, stop and ask the user.
- **Pull before push.** Run `git pull --rebase` before pushing — another session may have pushed.

See `.opencode/rules/commit-workflow.md` for the full parallel session safety rules.

## Pre-Commit Workflow

Before every commit, follow the full workflow in `.opencode/rules/commit-workflow.md`:

1. **Classify** changed files by category (java, terraform, bash, docs, etc.)
2. **Validate** per category (Java → `mvnw test`, Terraform → `fmt`/`validate`/`plan`, Bash → `bash -n`, etc.)
3. **Adversarial review** using `review-cheap` subagent (different model, fresh context)
4. Only then commit

See `.opencode/rules/commit-workflow.md` for the full workflow and skip conditions.

## Diagrams and Formatting

- **Always use Mermaid** for diagrams in markdown files. Never use ASCII art for flowcharts, sequence diagrams, or architecture diagrams.
- Use `flowchart`, `sequenceDiagram`, `graph`, or `classDiagram` as appropriate.
- Keep diagrams concise — if a diagram needs more than ~15 nodes, split it into multiple diagrams.

## Code Navigation

- Use grep/glob for finding code across the codebase
- Read surrounding context before making changes
- Follow existing code conventions in neighboring files

## Java Compatibility Policy

MockServer targets **Java 11** as the minimum supported version. This is a deliberate decision to maximise compatibility — approximately 23% of Java projects still run on Java 11.

**Rules:**
- The Maven compiler source/target MUST remain at `11` (`pom.xml` properties `maven.compiler.source` and `maven.compiler.target`)
- NEVER accept dependency upgrades that require Java 17+ (e.g., Spring 6, Jakarta EE 9+, Jetty 10+/12+)
- NEVER use Java language features or APIs introduced after Java 11
- When evaluating Snyk/Dependabot PRs, reject any that pull in transitive dependencies requiring Java 17+
- The `javax` namespace is used throughout — do NOT migrate to `jakarta` namespace
- Spring 5.x, Tomcat 9.x, and Jetty 9.x are the highest major versions compatible with Java 11 + `javax`

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
| Implementation work | `implementer` |
| Code simplification | `simplifier` |
| Test execution | `test-runner` |
| Security audit | `security-auditor` |
| Documentation writing | `docs-writer` |
| Pipeline investigation | `pipeline-investigator` |
| Debugging/investigation | `debugger` |
| AWS infrastructure | `debugger` (with `aws-investigation` skill) |
| Task decomposition | `taskify-agent` |
| Design council seat | `council-seat` |

## Convention-Based Skill Invocation

Skills whose description contains `MUST be launched as a Task subagent with subagent_type "<type>"` MUST be launched via the Task tool with the specified `subagent_type`. NEVER load them directly with the `skill` tool.

## Research-First Problem Solving

When investigating issues or answering technical questions:
1. Search the codebase first
2. Search online documentation
3. Only then rely on training data
