## Installing MockServer Config

### Prerequisites

- Kubernetes (i.e. [minikube](https://kubernetes.io/docs/tasks/tools/install-minikube/)) 
- [Helm](https://docs.helm.sh/using_helm/#quickstart-guide)

### Helm Install

1. clone repo or copy / download helm chart
1. modify configuration
1. install with helm

```bash
helm upgrade --install --namespace mockserver mockserver-config helm/mockserver-config
```

### Helm Delete

To completely remove the chart:

```bash
helm delete mockserver-config --purge
```