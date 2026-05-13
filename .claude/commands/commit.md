---
description: Commit changes following the full pre-commit workflow (classify, validate, review)
---
Follow the COMPLETE pre-commit workflow defined in `.opencode/rules/commit-workflow.md`. Do NOT skip any steps unless the user explicitly says "skip tests", "skip review", or "just commit".

Validation is mandatory and must be executable where possible. Do not rely only on static inspection.

Stage only files you changed in this session. Never use blanket staging (`git add .` / `git add -A`).

The workflow is:

1. **Classify** — run `git status --short`, identify only files YOU changed in this session, classify by category (java/terraform/bash/docker/docs/config/helm/website)
2. **Validate** — run category-specific validations and command-based verification:
   - Java: `./mvnw test -pl <modules>`
   - Terraform: `terraform fmt -check`, `terraform validate`, and `terraform plan`
   - Bash: `bash -n <script>` and execute each changed script in a safe mode (`--help`, `--version`, or `--dry-run` when available)
   - Docker: run `docker build` for each changed Dockerfile and run a basic smoke command from the built image when feasible
   - Helm: `helm lint` and `helm template`
   - Website: `bundle exec jekyll build`
   - Docs/config: syntax and link checks as applicable
3. **Adversarial review** — use the Agent tool to spawn a `review-cheap` subagent (different model, fresh context) to review the diff. Must return PASS before proceeding.
4. **Commit** — only after all steps pass: stage files by explicit path (NEVER `git add .`), create commit with descriptive message

If the user provided additional instructions: $ARGUMENTS
