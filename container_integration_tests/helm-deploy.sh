#!/usr/bin/env bash

set -euo pipefail

function start-up-k8s() {
  runCommand "microk8s start"
  runCommand "docker save mockserver/mockserver:integration_testing > ${SCRIPT_DIR}/mockserver_integration_testing_image.tar"
  runCommand "multipass unmount microk8s-vm"
  runCommand "multipass mount ${SCRIPT_DIR} microk8s-vm"
  runCommand "multipass info microk8s-vm"
  runCommand "microk8s ctr image import ${SCRIPT_DIR}/mockserver_integration_testing_image.tar"
  runCommand "microk8s ctr image list"
}

function tear-down-k8s() {
  runCommand "multipass unmount microk8s-vm"
  runCommand "microk8s stop"
}

function start-up() {
  runCommand "helm --kube-context microk8s upgrade --install --namespace mockserver --create-namespace ${1:-} --wait --version 5.12.0 mockserver ${SCRIPT_DIR}/../../helm/mockserver"
  export NODE_PORT=$(runCommand "kubectl --context microk8s get --namespace mockserver -o jsonpath='{.spec.ports[0].nodePort}' services mockserver")
  export NODE_IP=$(runCommand "kubectl --context microk8s get nodes --namespace mockserver -o jsonpath='{.items[0].status.addresses[0].address}'")
  export MOCKSERVER_HOST="${NODE_IP}:${NODE_PORT}"
  # TODO add poll for startup
  sleep 3
}

function tear-down() {
  runCommand "helm --kube-context microk8s --namespace mockserver delete mockserver"
}
