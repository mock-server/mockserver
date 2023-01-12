#!/usr/bin/env bash

set -euo pipefail

CLUSTER_NAME="mockserver"
KUBE_CONTEXT="kind-${CLUSTER_NAME}"

function start-up-k8s() {
  local SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" >/dev/null && pwd)"
  if [[ "${REBUILD_CLUSTER:-false}" == "true" ]]; then
    runCommand "kind delete cluster --name ${CLUSTER_NAME}"
  fi

  if [[ "$(kind get clusters 2>&1 | grep -w ${CLUSTER_NAME})" == "${CLUSTER_NAME}" ]]; then
    printMessage "Found existing cluster"
  else
    runCommand "kind create cluster --config ${SCRIPT_DIR}/kind-config.yaml --name ${CLUSTER_NAME}"
  fi

  runCommand "kind load docker-image --name ${CLUSTER_NAME} mockserver/mockserver:integration_testing"
}

function tear-down-k8s() {
  if [[ "${DELETE_CLUSTER:-false}" == "true" ]]; then
    runCommand "kind delete cluster --name mockserver"
  fi
}

function start-up() {
  local SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" >/dev/null && pwd)"
  runCommand "helm --kube-context ${KUBE_CONTEXT} upgrade --install --namespace ${2:-mockserver} --create-namespace ${1:-} --debug --wait --version 5.15.0 ${2:-mockserver} ${SCRIPT_DIR}/../helm/mockserver"
  runCommand "(ps -ef | grep port-forward | grep ${3:-1080} | awk '{ print \$2 }' | xargs kill) || true"
  runCommand "kubectl --context ${KUBE_CONTEXT} --namespace ${2:-mockserver} port-forward svc/${2:-mockserver} ${3:-1080}:${3:-1080} &"
  export MOCKSERVER_HOST=127.0.0.1:${3:-1080}
  # TODO add poll for startup and port-forward
  sleep 3
}

function tear-down() {
  runCommand "helm --kube-context ${KUBE_CONTEXT} --namespace ${1:-mockserver} delete ${1:-mockserver}"
  runCommand "(ps -ef | grep port-forward | grep ${2:-1080} | awk '{ print \$2 }' | xargs kill) || true"
}

function container-logs() {
  printMessage "${1:-mockserver} logs"
  runCommand "kubectl --context ${KUBE_CONTEXT} --namespace ${1:-mockserver} logs $(kubectl --context ${KUBE_CONTEXT} --namespace ${1:-mockserver} get po -l app=mockserver,release=${1:-mockserver} -o=jsonpath='{.items[0].metadata.name}')"
}
