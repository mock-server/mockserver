#!/usr/bin/env bash
set -euo pipefail
shopt -s nullglob

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
errors=0

command -v python3 >/dev/null 2>&1 || {
  echo "ERROR: python3 is required"
  exit 1
}

agent_skill_enabled() {
  local agent_name="$1"
  python3 - "$repo_root/opencode.jsonc" "$agent_name" <<'PY'
import json, sys

path = sys.argv[1]
agent = sys.argv[2]
text = open(path, encoding="utf-8").read()

out = []
in_string = False
escape = False
i = 0
while i < len(text):
    ch = text[i]
    nxt = text[i + 1] if i + 1 < len(text) else ""
    if in_string:
        out.append(ch)
        if escape:
            escape = False
        elif ch == "\\":
            escape = True
        elif ch == '"':
            in_string = False
        i += 1
        continue
    if ch == '"':
        in_string = True
        out.append(ch)
        i += 1
        continue
    if ch == "/" and nxt == "/":
        i += 2
        while i < len(text) and text[i] != "\n":
            i += 1
        continue
    out.append(ch)
    i += 1

text = "".join(out)
config = json.loads(text)
agent_cfg = config.get("agent", {}).get(agent, {})
tools = agent_cfg.get("tools", {})
print("true" if tools.get("skill", True) else "false")
PY
}

echo "[opencode] validating command -> skill references"
for command_file in "$repo_root"/.opencode/commands/*.md; do
  while IFS= read -r skill_name; do
    [[ -z "$skill_name" ]] && continue
    if [[ ! -f "$repo_root/.opencode/skills/$skill_name/SKILL.md" ]]; then
      rel_command_file="${command_file#$repo_root/}"
      echo "ERROR: ${rel_command_file} references missing skill '$skill_name'"
      errors=1
    fi
  done < <(
    grep -Eo 'Load the `[a-z0-9-]+` skill' "$command_file" \
      | sed -E 's/Load the `([a-z0-9-]+)` skill/\1/'
  )
done

echo "[opencode] validating command -> agent references"
for command_file in "$repo_root"/.opencode/commands/*.md; do
  agent_name="$(grep -E '^agent:' "$command_file" | awk '{print $2}' || true)"
  [[ -z "$agent_name" ]] && continue
  if [[ "$agent_name" == "general" ]]; then
    continue
  fi
  if ! grep -q "\"$agent_name\"[[:space:]]*:" "$repo_root/opencode.jsonc"; then
    rel_command_file="${command_file#$repo_root/}"
    echo "ERROR: ${rel_command_file} references missing agent '$agent_name'"
    errors=1
  fi
done

echo "[opencode] validating agent prompt files exist"
while IFS= read -r agent_prompt_path; do
  [[ -z "$agent_prompt_path" ]] && continue
  if [[ ! -f "$repo_root/$agent_prompt_path" ]]; then
    echo "ERROR: missing agent prompt file '$agent_prompt_path'"
    errors=1
  fi
done < <(grep -Eo '"agent"[[:space:]]*:[[:space:]]*"\.opencode/agents/[a-z0-9-]+\.md"' "$repo_root/opencode.jsonc" \
  | sed -E 's/.*"(\.opencode\/agents\/[a-z0-9-]+\.md)"/\1/')

echo "[opencode] validating subagent-routed skills"
for skill_file in "$repo_root"/.opencode/skills/*/SKILL.md; do
  marker_line="$(grep -m1 -E 'MUST be launched as a Task subagent with subagent_type "[a-z0-9-]+"' "$skill_file" || true)"
  [[ -z "$marker_line" ]] && continue

  skill_name="$(basename "$(dirname "$skill_file")")"
  required_agent="$(printf '%s' "$marker_line" | sed -E 's/.*subagent_type "([a-z0-9-]+)".*/\1/')"
  command_file="$repo_root/.opencode/commands/$skill_name.md"

  if [[ ! -f "$command_file" ]]; then
    echo "ERROR: skill '$skill_name' requires subagent routing but command file is missing"
    errors=1
    continue
  fi

  command_agent="$(grep -E '^agent:' "$command_file" | awk '{print $2}' || true)"
  has_subtask="$(grep -E '^subtask:[[:space:]]*true$' "$command_file" || true)"
  if [[ "$command_agent" != "$required_agent" ]]; then
    rel_command_file="${command_file#$repo_root/}"
    echo "ERROR: ${rel_command_file} uses agent '$command_agent' but '$skill_name' requires '$required_agent'"
    errors=1
  fi
  if [[ -z "$has_subtask" ]]; then
    rel_command_file="${command_file#$repo_root/}"
    echo "ERROR: ${rel_command_file} must set 'subtask: true' for subagent-routed skill '$skill_name'"
    errors=1
  fi

  if [[ "$(agent_skill_enabled "$required_agent")" != "true" ]]; then
    echo "ERROR: agent '$required_agent' must allow skill tool for subagent-routed skill '$skill_name'"
    errors=1
  fi
done

echo "[opencode] validating drift-prone infrastructure literals"
drift_files=(
  "AGENTS.md"
  ".opencode/skills/aws-investigation/SKILL.md"
  ".opencode/skills/terraform-tfvars/SKILL.md"
  ".opencode/skills/build-monitor/SKILL.md"
)

for drift_file in "${drift_files[@]}"; do
  full_path="$repo_root/$drift_file"

  if [[ ! -f "$full_path" ]]; then
    echo "WARN: ${drift_file} not found, skipping"
    continue
  fi

  if grep -nE 't3\.large|c5\.2xlarge|(^|[^[:alnum:]])[0-9]+([^0-9A-Za-z]+[0-9]+)?[[:space:]]+instances?|min_size[[:space:]]*=[[:space:]]*[1-9][0-9]*|max_size[[:space:]]*=[[:space:]]*[0-9]+|on_demand_percentage[[:space:]]*=[[:space:]]*[0-9]+' "$full_path" >/dev/null; then
    echo "ERROR: ${drift_file} contains hardcoded instance or capacity literals"
    grep -nE 't3\.large|c5\.2xlarge|(^|[^[:alnum:]])[0-9]+([^0-9A-Za-z]+[0-9]+)?[[:space:]]+instances?|min_size[[:space:]]*=[[:space:]]*[1-9][0-9]*|max_size[[:space:]]*=[[:space:]]*[0-9]+|on_demand_percentage[[:space:]]*=[[:space:]]*[0-9]+' "$full_path" || true
    errors=1
  fi
done

if [[ "$errors" -ne 0 ]]; then
  echo "[opencode] validation failed"
  exit 1
fi

echo "[opencode] validation passed"
