# Contributing to MockServer

## Issues

If you have any problems, please [check the project issues](https://github.com/mock-server/mockserver/issues?state=open) and avoid opening issues that have already been fixed. When you open an issue please provide:

- MockServer version
- How you are running MockServer (Maven plugin, Docker, JAR, etc.)
- MockServer log output at INFO level (or higher)
- What the error is
- What you are trying to do

## Repository Structure

This is a monorepo containing several projects:

| Directory | Language | Description |
|-----------|----------|-------------|
| `mockserver/` | Java | Main MockServer (Netty HTTP server, 11 Maven modules) |
| `mockserver-ui/` | TypeScript | Dashboard React SPA |
| `mockserver-maven-plugin/` | Java | Maven plugin for starting/stopping MockServer |
| `mockserver-node/` | JavaScript | Node.js launcher |
| `mockserver-client-node/` | JavaScript | Node.js/browser API client |
| `mockserver-client-python/` | Python | Python API client |
| `mockserver-client-ruby/` | Ruby | Ruby API client |
| `mockserver-performance-test/` | Python | Locust performance tests |

## Building

### Java (main server)

```bash
cd mockserver && ./mvnw clean install
```

### UI

```bash
cd mockserver-ui && npm ci && npm run build
```

### Node.js client

```bash
cd mockserver-client-node && npm ci && npx grunt
```

### Python client

```bash
cd mockserver-client-python
python3 -m venv .venv
.venv/bin/pip install -e '.[dev]'
.venv/bin/pytest
```

### Ruby client

```bash
cd mockserver-client-ruby
bundle install
bundle exec rspec
```

### Maven plugin

```bash
cd mockserver && ./mvnw clean install -DskipTests
./mvnw -f ../mockserver-maven-plugin/pom.xml clean verify
```

## Contributions

Pull requests are welcome. Please:

1. Open an issue first to discuss what you plan to change
2. Follow existing code conventions in the module you are changing
3. Add tests for any new functionality
4. Ensure all tests pass before submitting

## Feature Requests

Feature requests are submitted to [GitHub issues](https://github.com/mock-server/mockserver/issues?state=open).
