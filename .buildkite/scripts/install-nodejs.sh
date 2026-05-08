#!/usr/bin/env bash
set -euo pipefail

NODE_MAJOR_VERSION="${NODE_MAJOR_VERSION:-22}"

apt-get update -y
apt-get install -y --no-install-recommends ca-certificates curl gnupg

mkdir -p /usr/share/keyrings
curl --max-time 60 --connect-timeout 10 -fsSL \
  https://deb.nodesource.com/gpgkey/nodesource-repo.gpg.key \
  | gpg --dearmor -o /usr/share/keyrings/nodesource.gpg
chmod 644 /usr/share/keyrings/nodesource.gpg

ARCH="$(dpkg --print-architecture)"
cat > /etc/apt/sources.list.d/nodesource.sources <<EOF
Types: deb
URIs: https://deb.nodesource.com/node_${NODE_MAJOR_VERSION}.x
Suites: nodistro
Components: main
Architectures: ${ARCH}
Signed-By: /usr/share/keyrings/nodesource.gpg
EOF

apt-get update -y
apt-get install -y --no-install-recommends nodejs
