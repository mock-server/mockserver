## Installing MockServer

### Prerequisites

- Kubernetes (i.e. [minikube](https://kubernetes.io/docs/tasks/tools/install-minikube/)) 
- [Helm](https://docs.helm.sh/using_helm/#quickstart-guide)

### Helm Install
To install the chart with: 
- **release name:** `mockserver` 
- **namespace:** `mockserver`
- **configuration:** `values` (see [values.yaml](values.yaml) for values definitions)

Remotely using `www.mock-server.com` as a chart repo:

```bash
helm upgrade --install --namespace mockserver mockserver http://www.mock-server.com/mockserver-5.7.2.tgz
```

**OR** 

If you have helm chart source folder (i.e. you have the repository cloned):

```bash
helm upgrade --install --values helm/mockserver/values.yaml --namespace mockserver mockserver helm/mockserver
```

**THEN**

To wait until the deployment is complete run:

```bash
kubectl rollout status deployments mockserver -n mockserver
```

To check the status of the deployment without waiting, run the following command and confirm the `mockserver` has the `Running` status:

```bash 
kubectl get po -l release=mockserver -n mockserver
```

To view the logs:

```bash
kubectl logs $(kubectl get po -l release=mockserver -n mockserver | awk '{if(NR==2)print $1}') -n mockserver
```

### Modify Start Command

Modify the argument used to start the docker container by setting values explicitly as follows:

```bash
helm upgrade --install --values helm/mockserver/values.yaml --set app.serverPort=1080  --set app.logLevel=INFO --namespace mockserver mockserver helm/mockserver
```

The following values are supported:
- `app.serverPort` (default: 1080)
- `app.logLevel` (default: INFO)
- `app.proxyRemoteHost` (no default)
- `app.proxyRemotePort` (no default)
- `app.jvmOptions` (no default)

For example configure a proxyRemoteHost and proxyRemotePort, as follows:

```bash
helm upgrade --install --values helm/mockserver/values.yaml --set app.serverPort=1080  --set app.logLevel=INFO  --set app.proxyRemoteHost=www.mock-server.com --set app.proxyRemotePort=443 --namespace mockserver mockserver helm/mockserver
```

Double check the correct arguments have been passed to the pod, as follows:

```bash
kubectl -n mockserver logs -l app=mockserver,release=mockserver
``` 

### MockServer URL

#### Outside Local Kubernetes Cluster (i.e. minikube)

If the `service` type hasn't been modified the following will provide the MockServer URL from outside the cluster.

```bash
export NODE_PORT=$(kubectl get --namespace mockserver -o jsonpath="{.spec.ports[0].nodePort}" services mockserver)
export NODE_IP=$(kubectl get nodes --namespace mockserver -o jsonpath="{.items[0].status.addresses[0].address}")
export MOCKSERVER_HOST=$NODE_IP:$NODE_PORT
echo http://$MOCKSERVER_HOST
```

To test the installation the following `curl` command should return the ports MockServer is bound to:

```bash
curl -v -X PUT http://$MOCKSERVER_HOST/status
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
