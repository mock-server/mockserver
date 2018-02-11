## Installing Vault

### Prerequisites

- Kubernetes (i.e. [minikube](https://kubernetes.io/docs/tasks/tools/install-minikube/)) 
- [Helm](https://docs.helm.sh/using_helm/#quickstart-guide)

### Helm Install
To install the chart with: 
- **release name:** `mockserver` 
- **namespace:** `mockserver`
- **configuration:** `values` (see [values.yaml](values.yaml) for values definitions)

```bash
helm upgrade --install --values helm/mockserver/values.yaml --namespace mockserver mockserver helm/mockserver
```

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

### MockServer URL
#### Outside Kubernetes Cluster

Notes on how to retrieve the URL to call MockServer from outside the Kubernetes cluster are printed out when the installation is complete.
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

#### Inside Kubernetes Cluster

If a [DNS server](https://kubernetes.io/docs/concepts/services-networking/service/#dns) has been installed in the Kubernetes cluster the following DNS names should be available:
- **within `mockserver` namespace:** `mockserver`
- **outside `mockserver` namespace:** `mockserver.mockserver`  
- **DNS SRV query:** `_serviceport._tcp.mockserver.mockserver`

### Helm Delete

To completely remove the chart:

```bash
helm delete mockserver --purge
```