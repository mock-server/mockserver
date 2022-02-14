# install prerequisites on mac

```bash
# 1. install docker
open https://docs.docker.com/desktop/mac/install/
# 2. install microk8s (see: https://microk8s.io/)
brew install ubuntu/microk8s/microk8s
microk8s install
microk8s status --wait-ready
microk8s enable dns helm3 ingress
# 3. kubectl (merge output from microk8s config into ~/.kube)
# it is potentially possible to script this using `yq` installed via `brew install python-yq`
microk8s config
# 4. confirm ns list works via kubectl
kubectl --context microk8s get ns
```

# start and stop microk8s

```bash
# start
microk8s start
# stop
microk8s stop
```

# run integration tests

```bash
./integration_test.sh
```

# run integration tests without rebuilding java

```bash
SKIP_JAVA_BUILD=true ./integration_test.sh
```

# building docker container only

This builds to local docker host using tag `mockserver/mockserver:integration_testing`

```bash
SKIP_ALL_TESTS=true ./integration_test.sh
```
