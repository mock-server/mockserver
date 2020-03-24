## Installing MockServer

### Prerequisites

- Kubernetes (i.e. [minikube](https://kubernetes.io/docs/tasks/tools/install-minikube/) or [Docker for Desktop](https://www.docker.com/products/docker-desktop)) 
- [Helm](https://docs.helm.sh/using_helm/#quickstart-guide)

### Helm Install

To run MockServer in Kubernetes the easiest way is to use the existing [MockServer helm chart](http://www.mock-server.com/mockserver-5.10.0.tgz).

This is available by using `www.mock-server.com` as a chart repo, with the following command:

```bash
helm upgrade --install --namespace mockserver mockserver http://www.mock-server.com/mockserver-5.10.0.tgz
```

**OR** 

If you have helm chart source folder (i.e. you have the repository cloned):

```bash
helm upgrade --install --namespace mockserver mockserver helm/mockserver
```

The two commands above will install MockServer into a **namespace** called `mockserver` with default configuration (as per the embedded [values.yaml](https://github.com/mock-server/mockserver/blob/master/helm/mockserver/values.yaml)).  
MockServer will then be available on domain name `mockserver.mockserver.svc.cluster.local`, as long as the namespace you are calling from isn't prevented (by network policy) to call the `mockserver` namespace.

**THEN**

To view the logs:

```bash
kubectl -n mockserver logs --tail=100 -l app=mockserver,release=mockserver
```

To wait until the deployment is complete run:

```bash
kubectl -n mockserver rollout status deployments mockserver
```

To check the status of the deployment without waiting, run the following command and confirm the `mockserver` has the `Running` status:

```bash 
kubectl -n mockserver get po -l release=mockserver
```

### Basic MockServer Configuration 

Modify the arguments used to start the docker container by setting values explicitly using `--set`, as follows:

```bash
helm upgrade --install --namespace mockserver --set app.serverPort=1080 --set app.logLevel=INFO mockserver http://www.mock-server.com/mockserver-5.10.0.tgz
```

The following values are supported:
- `app.serverPort` (default: 1080)
- `app.logLevel` (default: INFO)
- `app.proxyRemoteHost` (no default)
- `app.proxyRemotePort` (no default)
- `app.jvmOptions` (no default)
- `image.snapshot` (default: false) - set `true` to use latest snapshot version

For example configure a proxyRemoteHost and proxyRemotePort, as follows:

```bash
helm upgrade --install --namespace mockserver --set app.serverPort=1080 --set app.proxyRemoteHost=www.mock-server.com --set app.proxyRemotePort=443 mockserver http://www.mock-server.com/mockserver-5.10.0.tgz
```

Double check the correct arguments have been passed to the pod, as follows:

```bash
kubectl -n mockserver logs -l app=mockserver,release=mockserver
``` 

### Detailed MockServer Configuration

The MockServer helm chart also supports providing detailed configuration such as:
- a **properties file** to configure all MockServer properties, or 
- a **json expectation initialization file** to initialize expectations at start-up.
  
This is done by deploying a **configmap** to kubernetes with files embedded in the yaml as shown in [mockserver-config](https://github.com/mock-server/mockserver/tree/master/helm/mockserver-config) helm chart.
The MockServer helm chart to configure to control how to mount the files from the **configmap**, as follows: 
- `app.mountConfigMap` (default: false) - enables the mounting of the configmap into the MockServer container file system
- `app.mountedConfigMapName` (default: mockserver-config) - name of the configmap (in the same namespace) to mount
- `app.propertiesFileName` (default: mockserver.properties) - name of the property file in the configmap
- `app.initializationJsonFileName` (default: initializerJson.json) - name of the JSON initialization file in the configmap

For example:

```bash
helm upgrade --install --namespace mockserver --set app.mountConfigMap=true --set app.mountedConfigMapName=mockserver-config --set app.propertiesFileNamem=mockserver.properties --set app.initializationJsonFileName=initializerJson.json mockserver helm/mockserver
```

### MockServer URL

#### Local Kubernetes Cluster (i.e. [minikube](https://github.com/kubernetes/minikube), [microk8s](https://microk8s.io/))

If the `service` type hasn't been modified the following will provide the MockServer URL from outside the cluster.

```bash
export NODE_PORT=$(kubectl get -n mockserver -o jsonpath="{.spec.ports[0].nodePort}" services mockserver)
export NODE_IP=$(kubectl get nodes -n mockserver -o jsonpath="{.items[0].status.addresses[0].address}")
export MOCKSERVER_HOST=$NODE_IP:$NODE_PORT
echo http://$MOCKSERVER_HOST
```

To test the installation the following `curl` command should return the ports MockServer is bound to:

```bash
curl -v -X PUT http://$MOCKSERVER_HOST/status
```

#### Docker for Desktop

[Docker for Desktop](https://www.docker.com/products/docker-desktop) creates automatic port bindings for LoadBalancer or NodePort services.  
In addition on MacOS Docker for Desktop runs inside [Hyperkit](https://github.com/moby/hyperkit) so the node IP address is not reachable, therefore the only way to call NodePort or LoadBalancer services is via the port bindings added by Docker for Desktop.

If the `service` type hasn't been modified the following will provide the MockServer URL from outside the cluster.

```bash
export NODE_PORT=$(kubectl get -n mockserver -o jsonpath="{.spec.ports[0].nodePort}" services mockserver)
export MOCKSERVER_HOST=127.0.0.1:$NODE_PORT
echo http://$MOCKSERVER_HOST
```

#### Outside Remote Kubernetes Cluster (i.e. Azure AKS, AWS EKS, etc)

```bash
kubectl -n mockserver port-forward svc/mockserver 1080:1080 &
export MOCKSERVER_HOST=127.0.0.1:1080
echo http://$MOCKSERVER_HOST
```

#### Inside Kubernetes Cluster

If a [DNS server](https://kubernetes.io/docs/concepts/services-networking/service/#dns) has been installed in the Kubernetes cluster the following DNS name should be available `mockserver.<namespace>.svc.cluster.local`, i.e. `mockserver.mockserver.svc.cluster.local`

### Helm Delete

To completely remove the chart:

```bash
helm delete mockserver --purge
```
