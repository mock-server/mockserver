# MockServer Documentation

Comprehensive internal documentation for the MockServer project covering code architecture, infrastructure, build system, CI/CD, and deployment.

## Documentation Index

### Code Architecture

Deep-dive documentation of MockServer's codebase, from high-level module structure down to individual subsystems.

| Document | Level | Description |
|----------|-------|-------------|
| [Code Overview](code/overview.md) | High | Module hierarchy, dependency graph, package layout |
| [Netty Pipeline](code/netty-pipeline.md) | Medium | Port unification, protocol detection, channel handlers, MCP handler, relay pattern |
| [Request Processing](code/request-processing.md) | Medium | Mock matching, proxy forwarding, action dispatch, WAR bridge |
| [Event System](code/event-system.md) | Medium | LMAX Disruptor ring buffer, verification, persistence, observers |
| [Dashboard UI](code/dashboard-ui.md) | Medium | React SPA, Redux state, WebSocket communication, data assembly |
| [Domain Model](code/domain-model.md) | Low | Model hierarchy, matchers, codecs, OpenAPI support, configuration (incl. MCP) |
| [TLS & Security](code/tls-and-security.md) | Low | BouncyCastle CA, SNI, mTLS, JWT auth, control plane security |
| [Client & Integrations](code/client-and-integrations.md) | Low | MockServerClient, JUnit 4/5, Spring, WebSocket callbacks |
| [Memory Management](code/memory-management.md) | Medium | Log entry and expectation memory analysis, default limit calculation, tuning guide |
| [Metrics & Monitoring](code/metrics.md) | Low | Prometheus metrics, memory monitoring, CSV export |
| [AI & RPC Protocol Mocking](code/ai-protocol-mocking.md) | Medium | SSE streaming, JSON-RPC matching, MCP and A2A mock builders, gRPC mocking |

### Infrastructure

AWS accounts, CI/CD pipelines, container images, and Kubernetes deployment.

| Document | Description |
|----------|-------------|
| [AWS Infrastructure](infrastructure/aws-infrastructure.md) | AWS accounts, Terraform IaC, EC2 agents, S3 hosting, CloudFront CDN |
| [CI/CD](infrastructure/ci-cd.md) | Buildkite pipelines and GitHub Actions workflows |
| [Docker](infrastructure/docker.md) | Docker images, variants, multi-arch builds, and Compose examples |
| [Helm & Kubernetes](infrastructure/helm.md) | Helm charts, deployment templates, and Kind-based testing |

### Operations

Build process, releases, dependencies, security scanning, and the documentation website.

| Document | Description |
|----------|-------------|
| [Build System](operations/build-system.md) | Maven configuration, profiles, plugins, and build scripts |
| [Release Process](operations/release-process.md) | End-to-end release workflow with Mermaid diagrams |
| [Security](operations/security.md) | Consolidated security overview: CodeQL, Dependabot, Snyk, AI security review, SNAPSHOT policy |
| [Snyk Security](operations/snyk-security.md) | Vulnerability scanning, CLI usage, Java 11 constraints, triage workflow |
| [Website](operations/website.md) | Jekyll documentation site structure and publishing |
| [Testing](testing.md) | Test frameworks, module inventory, architecture, configuration, coverage gaps, CI execution |
| [AI-Assisted Development](operations/ai-assisted-development.md) | AI development approach, adversarial review, testing backstop, structural safety |
| [OpenCode Configuration](operations/opencode-configuration.md) | AI harness: config, agents, rules, skills, commands, plugins |
| [OpenCode Building Blocks](operations/opencode-building-blocks.md) | Generic guide to the 9 building blocks: what each controls, when to use which, and how they fit together |

### Plans

| Document | Description |
|----------|-------------|
| [Java 17 Migration](plans/java-17-migration.md) | GraalVM JS, Spring 6, jakarta, DataFaker, language modernisation |
| [Build Optimisation](plans/build-optimisation.md) | Maven build speed improvements |
| [Testing Improvements](plans/testing-improvements.md) | Test coverage and quality improvements |
| [Security Remediation](plans/security-remediation.md) | Vulnerability fixes and security hardening |

### Other

| Document | Description |
|----------|-------------|
| [Architecture](architecture.md) | Original high-level architecture overview (see also [Code Overview](code/overview.md)) |
| [Gaps & Recommendations](gaps-and-recommendations.md) | Review of missing documentation and improvement areas |

## Quick Reference

```
mockserver-monorepo/
├── mockserver/                     # Java server (multi-module Maven project)
│   ├── mockserver-core/            # Core domain model, matching, serialisation
│   ├── mockserver-client-java/     # Java client library
│   ├── mockserver-netty/           # Netty-based HTTP server (main artifact)
│   ├── mockserver-war/             # WAR-packaged mock server
│   ├── mockserver-proxy-war/       # WAR-packaged proxy
│   ├── mockserver-junit-rule/      # JUnit 4 integration
│   ├── mockserver-junit-jupiter/   # JUnit 5 integration
│   ├── mockserver-spring-test-listener/ # Spring test integration
│   ├── mockserver-testing/         # Shared test utilities
│   ├── mockserver-integration-testing/ # Integration test infrastructure
│   └── mockserver-examples/        # Usage examples & Docker Compose samples
├── mockserver-ui/                  # React dashboard UI (Vite + TypeScript)
├── mockserver-node/                # Node.js MockServer launcher (npm)
├── mockserver-client-node/         # Node.js/browser client library (npm)
├── mockserver-client-python/       # Python client library (PyPI)
├── mockserver-client-ruby/         # Ruby client library (RubyGems)
├── mockserver-maven-plugin/        # Maven plugin for starting/stopping MockServer
├── mockserver-performance-test/    # Locust-based performance tests
├── container_integration_tests/    # Docker & Helm integration tests
├── jekyll-www.mock-server.com/     # Jekyll documentation website
├── helm/                           # Helm charts (mockserver + mockserver-config)
├── docker/                         # Production Docker images (5 variants)
├── docker_build/                   # CI build Docker images
├── terraform/                      # Terraform IaC (Buildkite agents + pipelines)
├── scripts/                        # Build, deploy, and utility scripts
└── docs/                           # This documentation (you are here)
    ├── code/                       #   Code architecture (11 docs)
    ├── infrastructure/             #   AWS, CI/CD, Docker, Helm (4 docs)
    ├── operations/                 #   Build, release, deps, security, website (6 docs)
    └── testing.md                  #   Test frameworks, architecture, config, coverage, CI
```

## Key Links

- **Website:** https://www.mock-server.com
- **GitHub:** https://github.com/mock-server/mockserver-monorepo
- **Docker Hub:** https://hub.docker.com/r/mockserver/mockserver
- **Maven Central:** `org.mock-server:mockserver-netty`
- **Helm Chart Repo:** https://www.mock-server.com/mockserver-5.15.0.tgz
- **SwaggerHub API:** https://app.swaggerhub.com/apis/jamesdbloom/mock-server-openapi
- **Buildkite:** https://buildkite.com/mockserver/mockserver
- **Snyk:** https://app.snyk.io/org/mockserver/projects
