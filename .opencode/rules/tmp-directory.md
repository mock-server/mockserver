# Temporary Files — Use `.tmp/` Not `/tmp/`

## Rule

Any skill, helper script, or Python tool that needs a scratch file
**MUST** write to the project-level `.tmp/` directory at the repo root,
NOT to the system-level `/tmp/`.

## Why

1. **No permission prompts.** Writes inside the project root are
   pre-allowed by opencode; writes to `/tmp/*` trigger a permission
   prompt every time.
2. **No host-wide leakage.** `/tmp/` is shared across all projects and
   sessions on the host. Our temp files stay scoped to this repo.
3. **Gitignored by default.** `/.tmp/*` is listed in `.gitignore` with
   `!/.tmp/.gitkeep` — the directory persists, contents never get
   committed accidentally.
4. **Excluded from context.** `.tmp/**` is in `opencode.jsonc`'s context
   excludes so scratch files never pollute LLM context.

## How to Use It

### In Markdown skills (SKILL.md, report-template.md)

Use **relative paths** starting with `.tmp/`. The agent's CWD is always
the repo root, so this resolves correctly.

```bash
# GOOD — relative path, no env var needed
cat > .tmp/my-card.json <<'EOF'
...
EOF

jq '.' .tmp/my-card.json > .tmp/formatted.json
```

```bash
# BAD — /tmp triggers a permission prompt
cat > /tmp/my-card.json <<'EOF'
...
EOF
```

### In bash scripts (.sh)

Use the `TMP_DIR` variable with a safe default. This works whether
the script is invoked from the repo root or a subdirectory, and lets
callers override if needed.

```bash
#!/usr/bin/env bash
set -euo pipefail

# Resolve repo root and scratch dir
REPO_ROOT="$(git rev-parse --show-toplevel 2>/dev/null || pwd)"
TMP_DIR="${TMP_DIR:-$REPO_ROOT/.tmp}"
mkdir -p "$TMP_DIR"

# Now use $TMP_DIR throughout the script
curl ... -o "$TMP_DIR/download.bin"
jq '...' "$TMP_DIR/download.bin" > "$TMP_DIR/parsed.json"
```

### In Python tools (.py)

```python
import os
from pathlib import Path
import subprocess

def tmp_dir() -> Path:
    """Return the project-level scratch directory, creating it if needed."""
    override = os.environ.get("TMP_DIR")
    if override:
        d = Path(override)
    else:
        repo_root = Path(
            subprocess.check_output(
                ["git", "rev-parse", "--show-toplevel"], text=True
            ).strip()
        )
        d = repo_root / ".tmp"
    d.mkdir(parents=True, exist_ok=True)
    return d

# Usage
scratch = tmp_dir() / "my-file.json"
scratch.write_text("...")
```

## Naming Convention for Scratch Files

To make files self-identifying and easier to clean up manually, prefix
them with the skill or tool name:

| Good | Bad |
|------|-----|
| `.tmp/pipeline-12345-log.txt` | `.tmp/log.txt` |
| `.tmp/codeql-report.md` | `.tmp/report.md` |
| `.tmp/buildkite-build-artifacts/` | `.tmp/artifacts/` |
| `.tmp/docker-build-image.json` | `.tmp/image.json` |

## Cleanup

Skills SHOULD clean up their own temp files when they finish
successfully, but `.tmp/` contents are never committed so leaving
artefacts behind for debugging is acceptable during development.

If `.tmp/` grows large, `rm -rf .tmp/*` (preserving `.gitkeep`) is safe
— nothing there is authoritative.

## Exceptions (Things That MUST Stay As-Is)

These are not violations of this rule — they reference `/tmp/` for
legitimate reasons outside our control:

1. **Remote-host paths** — e.g., `/tmp/cleanup-script.sh` runs on
   remote servers, not on our dev machines. The path is part of the
   remote filesystem contract.
2. **Third-party scripts** — e.g., when invoking an external tool that
   itself writes to `/tmp/` internally.
3. **System tempfile APIs** — Python's `tempfile.NamedTemporaryFile()`,
   Java's `Files.createTempFile()`, etc. These use the system `$TMPDIR`
   (which may or may not be `/tmp/`) and are managed by the stdlib.
   Prefer `.tmp/` for agent-visible files, but stdlib tempfile is fine
   for implementation details the agent never reads back.

## Why a Rule and Not Just Convention?

Because `/tmp/` usage was the dominant papercut driving permission
prompts during skill execution. Standardising on `.tmp/` removes an
entire class of friction. Every new skill and every skill migration
should follow this rule.
