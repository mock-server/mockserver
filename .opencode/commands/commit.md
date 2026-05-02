---
description: Commit changes following the full pre-commit workflow (classify, validate, review)
---
Follow the COMPLETE pre-commit workflow defined in `.opencode/rules/commit-workflow.md`. Do NOT skip any steps unless the user explicitly says "skip tests", "skip review", or "just commit".

The workflow is:

1. **Classify** — run `git status --short`, identify only files YOU changed in this session, classify by category (java/terraform/bash/docker/docs/config/helm/website)
2. **Validate** — run category-specific validations (Java: `mvnw test`, Terraform: `fmt`/`validate`/`plan`, Bash: `bash -n`, Docs: link check, etc.)
3. **Adversarial review** — launch a `review-cheap` subagent (different model, fresh context) to review the diff. Must return PASS before proceeding.
4. **Commit** — only after all steps pass: stage files by explicit path (NEVER `git add .`), create commit with descriptive message

If the user provided additional instructions: $ARGUMENTS
