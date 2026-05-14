# Release Pipeline Principles

These principles govern how the MockServer release pipeline is structured. They exist because we've been burned by tight CI coupling before: pipeline failures that couldn't be debugged locally, scripts that needed mock CI state to run anywhere else, and the implicit assumption that the CI provider would always be there to hold the state between steps.

## 1. Release scripts are CI-agnostic

**Release scripts MUST NOT call CI-specific commands.** No `buildkite-agent`, no `gh-actions/*`, no `jenkins-cli`. They read configuration from environment variables only. They emit progress to stdout/stderr. That's the whole contract.

Any CI-specific glue (translating CI metadata to env vars, uploading CI-native artifacts, posting CI annotations) lives in a thin adapter under `.buildkite/scripts/` (or `.github/workflows/`, etc.) that wraps the release scripts.

This means a Buildkite outage cannot block a release: a human with shell access, AWS credentials, and Docker can run the exact same scripts manually and ship.

## 2. Use the minimum surface area of any build system

The Buildkite pipeline YAML should describe **only** the orchestration: which scripts to run, in what order, on which agent queue, with what timeouts. No dynamic pipeline generation. No conditional `if:` expressions on metadata. No reliance on `meta-data set`/`get` for inter-step state. No `artifact` plugins to ship files between steps.

If two steps need to share state, that's a sign they should be one step. If conditional logic is needed, the script itself checks the input and exits cleanly when not applicable.

The pipeline YAML should be readable as "what gets released" without having to mentally execute a shell script. A human switching to a different CI provider should be able to translate it in an hour.

## 3. Locally runnable, locally testable

Every release script — and the orchestrator that runs them all — works on a developer laptop with the same behaviour as on a CI agent. The only host requirements are `docker`, `aws`, `git`, `jq`, `python3`, and `bash`. Every language toolchain (Maven, npm, Helm, Ruby, etc.) runs inside the same pinned Docker image whether the script is invoked locally or in CI.

A developer must be able to:

- Run `./bin/release --dry-run --version X.Y.Z` and exercise the entire pipeline without touching any external system.
- Run a single component script (`./scripts/release/components/npm.sh --dry-run`) to test that one piece.
- Reproduce a CI failure locally by setting the same env vars and running the same script.

## 4. Dry-run is the default safe mode

Every release script accepts `--dry-run`. In that mode the script does everything *except* the destructive external operation (push to npm, deploy to Maven Central, upload to S3, create a GitHub Release, etc.). Build, lint, package, and sanity-check still execute so problems surface before they matter.

The orchestrator defaults to dry-run when not in CI. Real releases require explicit `--execute`.

## 5. One script per deployable component

Each thing we publish (Maven Central, Docker, npm, Helm, PyPI, RubyGems, GitHub Release, etc.) is owned by exactly one script under `scripts/release/components/`. That script knows how to build, package, verify, and publish *its* component — start to finish.

The benefits:

- Easy to test in isolation (`./scripts/release/components/<name>.sh --dry-run`)
- Easy to re-run after a partial-pipeline failure
- Easy to reason about: one component = one file
- No hidden state between steps

The orchestrator (`scripts/release/release.sh`) is a thin loop: prepare → for each component → finalize.

## 6. Inputs are env vars; outputs are exit codes

The full contract:

| Variable | Required | Purpose |
|----------|----------|---------|
| `RELEASE_VERSION` | yes | The version being released (X.Y.Z) |
| `NEXT_VERSION` | no | The next SNAPSHOT (defaults to RELEASE_VERSION patch+1 -SNAPSHOT) |
| `OLD_VERSION` | no | Previous release (auto-derived from latest `mockserver-X.Y.Z` tag) |
| `RELEASE_TYPE` | no | `full` / `maven-only` / `docker-only` / `post-maven` (default: full) |
| `CREATE_VERSIONED_SITE` | no | `yes` / `no` (default: no) |
| `DRY_RUN` | no | `true` / `false` (default: false in CI, true locally) |
| `AWS_PROFILE` | no | Used outside CI for Secrets Manager lookup |

No other variables. No fallback to `BUILDKITE_*`. The Buildkite adapter is responsible for translating its inputs into this contract before invoking the release scripts.

## 7. Transparency over magic

A `cat scripts/release/release.sh` should be enough to understand what a release does. No hidden imports, no dynamically generated YAML, no clever metaprogramming. Bash, with clear function names and explicit step ordering.

When something fails in CI, the operator should be able to:

1. Look at the CI log to find which script failed at which line.
2. Run that exact script locally with `--dry-run` to reproduce.
3. Fix and re-run.

If any of those steps requires understanding CI-specific magic, we've violated this principle.
