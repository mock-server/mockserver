#!/usr/bin/env bash

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" >/dev/null && pwd)"

function download_and_extract() {
  wget --max-redirect=10 -O "${SCRIPT_DIR}/../target/${1}.jar" "${2}" -q --show-progress
  echo "expanding: ${1}"
  mkdir "${SCRIPT_DIR}/../target/${1}"
  cd "${SCRIPT_DIR}/../target/${1}"
  jar xf "${SCRIPT_DIR}/../target/${1}.jar"
  cd -
}

download_and_extract "mockserver-netty-jar-with-dependencies" "https://oss.sonatype.org/service/local/artifact/maven/redirect?r=releases&g=org.mock-server&a=mockserver-netty&c=jar-with-dependencies&e=jar&v=RELEASE" &
download_and_extract "mockserver-netty-shaded" "https://oss.sonatype.org/service/local/artifact/maven/redirect?r=releases&g=org.mock-server&a=mockserver-netty&c=shaded&e=jar&v=RELEASE" &
download_and_extract "mockserver-netty-no-dependencies" "https://oss.sonatype.org/service/local/artifact/maven/redirect?r=releases&g=org.mock-server&a=mockserver-netty-no-dependencies&e=jar&v=RELEASE" &
download_and_extract "mockserver-junit-jupiter-no-dependencies" "https://oss.sonatype.org/service/local/artifact/maven/redirect?r=releases&g=org.mock-server&a=mockserver-junit-jupiter-no-dependencies&e=jar&v=RELEASE" &
