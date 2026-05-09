## Install MockServer Config Helm Chart

A Helm chart to create the ConfigMap used by [MockServer Helm Chart](../mockserver/README.md) for configuration files (properties, expectation initialization JSON, TLS certificates).

### Prerequisites

- Kubernetes (i.e. [minikube](https://kubernetes.io/docs/tasks/tools/install-minikube/))
- [Helm](https://docs.helm.sh/using_helm/#quickstart-guide)

### Helm Install

1. Clone repo or copy/download helm chart
2. Modify configuration files as needed
3. Install with Helm:

```bash
helm upgrade --install --namespace mockserver mockserver-config helm/mockserver-config
```

### Helm Delete

To completely remove the chart:

```bash
helm delete mockserver-config --purge
```

## Community, Issues & Contributing

See the [main MockServer README](../../README.md) for community links, how to report issues, and contribution guidelines.
