#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/common.sh"

require_cmd docker
require_cmd git
require_cmd python3

log_step "Deploying next SNAPSHOT version $NEXT_VERSION"

log_info "Updating pom.xml versions to $NEXT_VERSION"
python3 - "$RELEASE_VERSION" "$NEXT_VERSION" "$REPO_ROOT/mockserver" << 'PYEOF'
import sys, pathlib

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

log_info "Deploying SNAPSHOT to Sonatype"
SONATYPE_USERNAME=$(load_secret "mockserver-build/sonatype" "username")
SONATYPE_PASSWORD=$(load_secret "mockserver-build/sonatype" "password")

"$REPO_ROOT/.buildkite/scripts/run-in-docker.sh" \
  -i "$MAVEN_IMAGE" \
  -w /build/mockserver \
  -v mockserver-m2-cache:/root/.m2 \
  -e "SONATYPE_USERNAME=$SONATYPE_USERNAME" \
  -e "SONATYPE_PASSWORD=$SONATYPE_PASSWORD" \
  -- mvn -T 1C clean deploy -DskipTests \
       -Djava.security.egd=file:/dev/./urandom \
       --settings .buildkite-settings.xml

log_info "Committing SNAPSHOT version"
cd "$REPO_ROOT"
git add mockserver/
git commit -m "release: set next development version $NEXT_VERSION"
git push origin HEAD:master

log_info "SNAPSHOT $NEXT_VERSION deployed and committed"
