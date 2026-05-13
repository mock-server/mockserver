#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/common.sh"

require_cmd python3

log_step "Setting release version $RELEASE_VERSION"

if [[ -z "$CURRENT_VERSION" ]]; then
  log_error "CURRENT_VERSION is empty — cannot update pom.xml"
  exit 1
fi

cd "$REPO_ROOT/mockserver"

log_info "Updating pom.xml versions from $CURRENT_VERSION to $RELEASE_VERSION"
python3 - "$CURRENT_VERSION" "$RELEASE_VERSION" "$REPO_ROOT/mockserver" << 'PYEOF'
import os, sys, pathlib

old_version, new_version, mockserver_dir = sys.argv[1], sys.argv[2], sys.argv[3]
old_tag = f"<version>{old_version}</version>"
new_tag = f"<version>{new_version}</version>"

updated = []
for path in pathlib.Path(mockserver_dir).rglob("pom.xml"):
    if "target" in path.parts:
        continue
    text = path.read_text()
    if old_tag in text:
        path.write_text(text.replace(old_tag, new_tag))
        updated.append(str(path.relative_to(mockserver_dir)))

if not updated:
    print(f"ERROR: no pom.xml files contained {old_tag}", file=sys.stderr)
    sys.exit(1)

for p in updated:
    print(f"  updated: {p}")
PYEOF

log_info "Committing version change"
cd "$REPO_ROOT"
git add mockserver/
git commit -m "release: set version $RELEASE_VERSION"

log_info "Creating tag mockserver-$RELEASE_VERSION"
git tag "mockserver-$RELEASE_VERSION"

log_info "Pushing to origin"
git push origin master
git push origin "mockserver-$RELEASE_VERSION"

log_info "Release version $RELEASE_VERSION set and tagged"
