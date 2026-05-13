---
description: Review and update architecture documentation against the codebase
---
Use the Agent tool to spawn a `docs-writer` subagent to review architecture documentation against the codebase and update docs for the following request:

$ARGUMENTS

The docs-writer agent will:
1. Compare current docs with actual code behavior.
2. Apply minimal, factual updates only where drift is found.
3. Cite source paths that justify each documentation change.
