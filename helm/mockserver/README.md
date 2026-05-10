## Install MockServer Helm Chart

### Prerequisites

- Kubernetes (i.e. [minikube](https://kubernetes.io/docs/tasks/tools/install-minikube/) or [Docker for Desktop](https://www.docker.com/products/docker-desktop)) 
- [Helm](https://docs.helm.sh/using_helm/#quickstart-guide)

### Helm Install

To run MockServer in Kubernetes the easiest way is to use the existing [MockServer helm chart](https://www.mock-server.com/mockserver-5.15.0.tgz).

This is available by using `www.mock-server.com` as a chart repo, with the following command:

```bash
helm upgrade --install --create-namespace --namespace mockserver mockserver https://www.mock-server.com/mockserver-5.15.0.tgz
```

**OR** 

If you have helm chart source folder (i.e. you have the repository cloned):

```bash
helm upgrade --install --create-namespace --namespace mockserver mockserver helm/mockserver
```

The two commands above will install MockServer into a **namespace** called `mockserver` with default configuration (as per the embedded [values.yaml](https://github.com/mock-server/mockserver-monorepo/blob/master/helm/mockserver/values.yaml)).  
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
helm upgrade --install --create-namespace --namespace mockserver --set app.serverPort=1080 --set app.logLevel=INFO mockserver https://www.mock-server.com/mockserver-5.15.0.tgz
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
helm upgrade --install --create-namespace --namespace mockserver --set app.serverPort=1080 --set app.proxyRemoteHost=www.mock-server.com --set app.proxyRemotePort=443 mockserver https://www.mock-server.com/mockserver-5.15.0.tgz
```

Double check the correct arguments have been passed to the pod, as follows:

```bash
kubectl -n mockserver logs -l app=mockserver,release=mockserver
``` 

### Detailed MockServer Configuration

There are two ways to provide MockServer configuration (properties file, expectation initialization JSON, TLS certificates, etc.):

1. **Inline configuration** (recommended) — provide configuration directly in `values.yaml`
2. **External ConfigMap** — create a ConfigMap separately (manually, via CI, or using the example [mockserver-config](https://github.com/mock-server/mockserver-monorepo/tree/master/helm/mockserver-config) chart)

Both approaches mount configuration into the container at `/config`. See [MockServer Configuration](https://www.mock-server.com/mock_server/configuration_properties.html) for details of all configuration options.

#### Option 1: Inline Configuration (single chart install)

Set `app.config.enabled=true` and provide configuration content directly in values. This creates a ConfigMap as part of the main chart — no separate chart or manual ConfigMap creation is needed.

Using `--set`:

```bash
helm upgrade --install --create-namespace --namespace mockserver \
  --set app.config.enabled=true \
  --set app.config.properties="mockserver.initializationJsonPath=/config/initializerJson.json" \
  --set app.config.initializerJson='[{"httpRequest":{"path":"/example"},"httpResponse":{"body":"response"}}]' \
  mockserver helm/mockserver
```

Or using a `values.yaml` file (recommended for complex configuration):

```yaml
app:
  config:
    enabled: true
    properties: |
      mockserver.initializationJsonPath=/config/initializerJson.json
      mockserver.enableCORSForAPI=true
      mockserver.enableCORSForAllResponses=true
    initializerJson: |
      [
        {
          "httpRequest": { "path": "/example" },
          "httpResponse": { "body": "some response" }
        }
      ]
```

```bash
helm upgrade --install --create-namespace --namespace mockserver -f values.yaml mockserver helm/mockserver
```

The following inline config values are supported:
- `app.config.enabled` (default: false) - set `true` to create a ConfigMap from inline values
- `app.config.properties` (default: "") - content of `mockserver.properties`
- `app.config.initializerJson` (default: "") - content of `initializerJson.json`
- `app.config.extraFiles` (default: {}) - map of additional filenames to content (e.g. TLS certificates)

#### Option 2: External ConfigMap

If a ConfigMap called `mockserver-config` (or a custom name) exists in the same namespace, it will be mounted into the MockServer container at `/config`.
This ConfigMap can contain a `mockserver.properties` file and other related configuration files such as:
- [json expectation initialization](https://www.mock-server.com/mock_server/initializing_expectations.html), or
- custom [TLS CA, X.509 Certificate or Private Key](https://www.mock-server.com/mock_server/HTTPS_TLS.html#configuration)

The `mockserver.properties` file should load these additional files from the directory `/config` which is the `mountPath` for the ConfigMap.

The mapping of the configuration ConfigMap can be configured as follows:
- `app.mountedConfigMapName` (default: mockserver-config) - name of the configuration ConfigMap (in the same namespace) to mount
- `app.propertiesFileName` (default: mockserver.properties) - path of the property file in the ConfigMap

For example:

```bash
helm upgrade --install --create-namespace --namespace mockserver --set app.mountedConfigMapName=other-mockserver-config --set app.propertiesFileName=other-mockserver.properties mockserver helm/mockserver
```

An example of a helm chart to create this ConfigMap is [helm/mockserver-config](https://github.com/mock-server/mockserver-monorepo/tree/master/helm/mockserver-config)

### Extending MockServer Classpath

To use [class callbacks](https://www.mock-server.com/mock_server/creating_expectations.html#button_response_class_callback) or an [expectation initializer class](https://www.mock-server.com/mock_server/initializing_expectations.html#expectation_initializer_class) the classpath for MockServer must include the specified classes.
To support adding classes to the classpath if a configmap called `mockserver-config` exists in the same namespace any jar files contained in this configmap will be added into MockServer classpath.

The mapping of the libs configmap can be configured as follows: 
- `app.mountedLibsConfigMapName` (default: mockserver-config) - name of the libs configmap (in the same namespace) to mount

For example:

```bash
helm upgrade --install --create-namespace --namespace mockserver --set app.mountedLibsConfigMapName=mockserver-libs mockserver helm/mockserver
```

### Persistent Storage

By default, expectations are held in memory and lost when a pod restarts. To persist expectations across pod restarts, enable persistent storage:

```bash
helm upgrade --install --namespace mockserver \
  --set app.persistence.enabled=true \
  mockserver helm/mockserver
```

This creates a PersistentVolumeClaim and automatically configures MockServer to persist and reload expectations from it. No additional configuration is needed.

The following persistence values are supported:
- `app.persistence.enabled` (default: false) — enable persistent storage
- `app.persistence.existingClaimName` (default: "") — use an existing PVC instead of creating one
- `app.persistence.storageClass` (default: "") — StorageClass for the PVC (empty = cluster default)
- `app.persistence.accessModes` (default: [ReadWriteOnce]) — PVC access modes
- `app.persistence.size` (default: 256Mi) — PVC size
- `app.persistence.mountPath` (default: /persistence) — mount path inside the container
- `app.persistence.annotations` (default: {}) — annotations for the PVC

When persistence is enabled, the chart automatically sets `MOCKSERVER_PERSIST_EXPECTATIONS`, `MOCKSERVER_PERSISTED_EXPECTATIONS_PATH`, and `MOCKSERVER_INITIALIZATION_JSON_PATH` environment variables. These are safe defaults — any matching property in your `mockserver.properties` file takes precedence.

**Note:** Chart-managed PVCs are NOT deleted by `helm uninstall`. Delete the PVC manually if you want to remove persisted data.

See the [full persistence documentation](https://www.mock-server.com/where/kubernetes.html#helm_persistent_storage) for more examples including existing PVC usage and clustering with shared storage.

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
helm upgrade --install --namespace mockserver --set service.type=LoadBalancer --set service.port=1080 mockserver https://www.mock-server.com/mockserver-5.15.0.tgz
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

### Available Versions

| Version | Chart Archive |
|---------|---------------|
| 5.15.0 (latest) | [mockserver-5.15.0.tgz](https://www.mock-server.com/mockserver-5.15.0.tgz) |
| 5.14.0 | [mockserver-5.14.0.tgz](https://www.mock-server.com/mockserver-5.14.0.tgz) |
| 5.13.2 | [mockserver-5.13.2.tgz](https://www.mock-server.com/mockserver-5.13.2.tgz) |
| 5.13.1 | [mockserver-5.13.1.tgz](https://www.mock-server.com/mockserver-5.13.1.tgz) |
| 5.13.0 | [mockserver-5.13.0.tgz](https://www.mock-server.com/mockserver-5.13.0.tgz) |
| 5.12.0 | [mockserver-5.12.0.tgz](https://www.mock-server.com/mockserver-5.12.0.tgz) |
| 5.11.2 | [mockserver-5.11.2.tgz](https://www.mock-server.com/mockserver-5.11.2.tgz) |
| 5.11.1 | [mockserver-5.11.1.tgz](https://www.mock-server.com/mockserver-5.11.1.tgz) |
| 5.11.0 | [mockserver-5.11.0.tgz](https://www.mock-server.com/mockserver-5.11.0.tgz) |

### Helm Delete

To completely remove the chart:

```bash
helm delete mockserver --purge
```
