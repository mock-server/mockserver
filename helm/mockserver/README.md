## Installing MockServer

### Prerequisites

- Kubernetes (i.e. [minikube](https://kubernetes.io/docs/tasks/tools/install-minikube/) or [Docker for Desktop](https://www.docker.com/products/docker-desktop)) 
- [Helm](https://docs.helm.sh/using_helm/#quickstart-guide)

### Helm Install

To run MockServer in Kubernetes the easiest way is to use the existing [MockServer helm chart](http://www.mock-server.com/mockserver-5.14.0.tgz).

This is available by using `www.mock-server.com` as a chart repo, with the following command:

```bash
helm upgrade --install --create-namespace --namespace mockserver mockserver http://www.mock-server.com/mockserver-5.14.0.tgz
```

**OR** 

If you have helm chart source folder (i.e. you have the repository cloned):

```bash
helm upgrade --install --create-namespace --namespace mockserver mockserver helm/mockserver
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
helm upgrade --install --create-namespace --namespace mockserver --set app.serverPort=1080 --set app.logLevel=INFO mockserver http://www.mock-server.com/mockserver-5.14.0.tgz
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
helm upgrade --install --create-namespace --namespace mockserver --set app.serverPort=1080 --set app.proxyRemoteHost=www.mock-server.com --set app.proxyRemotePort=443 mockserver http://www.mock-server.com/mockserver-5.14.0.tgz
```

Double check the correct arguments have been passed to the pod, as follows:

```bash
kubectl -n mockserver logs -l app=mockserver,release=mockserver
``` 

### Detailed MockServer Configuration

If a configmap called `mockserver-config` exists in the same namespace this will be mapped into the MockServer container under the `mountPath` `/config`.
This configmap can be used to configure MockServer by containing a `mockserver.properties` file and other related configuration files such as a:
- [json expectation initialization](https://www.mock-server.com/mock_server/initializing_expectations.html), or
- custom [TLS CA, X.509 Certificate or Private Key](https://www.mock-server.com/mock_server/HTTPS_TLS.html#configuration)
The `mockserver.properties` file should load these additional files from the directory `/config` which is the `mountPath` for the configmap. 

See [MockServer Configuration](https://www.mock-server.com/mock_server/configuration_properties.html) for details of all configuration options. 
  
The mapping of the configuration configmap can be configured as follows: 
- `app.mountedConfigMapName` (default: mockserver-config) - name of the configuration configmap (in the same namespace) to mount
- `app.propertiesFileName` (default: mockserver.properties) - path of the property file in the configmap

For example:

```bash
helm upgrade --install --create-namespace --namespace mockserver --set app.mountedConfigMapName=other-mockserver-config --set app.propertiesFileName=other-mockserver.properties mockserver helm/mockserver
```

An example of a helm chart to configure MockServer is [helm/mockserver-config](https://github.com/mock-server/mockserver/tree/master/helm/mockserver-config)

### Extending MockServer Classpath

To use [class callbacks](https://www.mock-server.com/mock_server/creating_expectations.html#button_response_class_callback) or an [expectation initializer class](https://www.mock-server.com/mock_server/initializing_expectations.html#expectation_initializer_class) the classpath for MockServer must include the specified classes.
To support adding classes to the classpath if a configmap called `mockserver-config` exists in the same namespace any jar files contained in this configmap will be added into MockServer classpath.

The mapping of the libs configmap can be configured as follows: 
- `app.mountedLibsConfigMapName` (default: mockserver-config) - name of the libs configmap (in the same namespace) to mount

For example:

```bash
helm upgrade --install --create-namespace --namespace mockserver --set app.mountedLibsConfigMapName=mockserver-libs mockserver helm/mockserver
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

[Docker for Desktop](https://www.docker.com/products/docker-desktop) automatically exposes **LoadBalancer** services.  
On MacOS Docker for Desktop runs inside [Hyperkit](https://github.com/moby/hyperkit) so the node IP address is not reachable, therefore the only way to call services is via the exposed **LoadBalancer** service added by Docker for Desktop.

To ensure that Docker for Desktop exposes MockServer update the service type to **LoadBalancer** using **--set service.type=LoadBalancer** and set the exposed port using **--set service.port=1080**, as follows:

```bash
helm upgrade --install --namespace mockserver --set service.type=LoadBalancer --set service.port=1080 mockserver http://www.mock-server.com/mockserver-5.14.0.tgz
```

MockServer will then be reachable on **http://localhost:1080**

For **LoadBalancer** services it is possible to query kubernetes to programmatically determine the MockServer base URL as follows:

```bash
export SERVICE_IP=$(kubectl get svc --namespace mockserver mockserver -o jsonpath='{.status.loadBalancer.ingress[0].hostname}')
export MOCKSERVER_HOST=$SERVICE_IP:1081
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
