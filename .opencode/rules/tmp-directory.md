# Temporary Files — Use `.tmp/` Not `/tmp/`

## Rule

Any skill, helper script, or tool that needs a scratch file
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
cat > .tmp/my-report.json <<'EOF'
...
EOF

jq '.' .tmp/my-report.json > .tmp/formatted.json
```

```bash
# BAD — /tmp triggers a permission prompt
cat > /tmp/my-report.json <<'EOF'
...
EOF
```

### In bash scripts (.sh)

```bash
#!/usr/bin/env bash
set -euo pipefail

REPO_ROOT="$(git rev-parse --show-toplevel 2>/dev/null || pwd)"
SCRATCH_DIR="${SCRATCH_DIR:-$REPO_ROOT/.tmp}"
mkdir -p "$SCRATCH_DIR"

curl ... -o "$SCRATCH_DIR/download.bin"
```

## Naming Convention for Scratch Files

Prefix files with the skill or tool name:

| Good | Bad |
|------|-----|
| `.tmp/pipeline-12345-log.txt` | `.tmp/log.txt` |
| `.tmp/codeql-report.md` | `.tmp/report.md` |
| `.tmp/buildkite-build-artifacts/` | `.tmp/artifacts/` |

## Cleanup

Skills SHOULD clean up their own temp files when they finish
successfully, but `.tmp/` contents are never committed so leaving
artefacts behind for debugging is acceptable during development.

If `.tmp/` grows large, `rm -rf .tmp/*` (preserving `.gitkeep`) is safe
— nothing there is authoritative.
