# Helm & Kubernetes

## Charts Overview

MockServer provides two Helm charts. The main `mockserver` chart can optionally create its own ConfigMap via inline configuration, or mount an externally-created ConfigMap:

```mermaid
graph LR
    subgraph "mockserver chart"
        DEP[Deployment]
        SVC[Service]
        ING[Ingress]
        CM_INLINE[ConfigMap<br/><i>inline, optional</i>]
        CM_REF[ConfigMap volume mount]
    end

    subgraph "mockserver-config chart (legacy)"
        CM_EXT[ConfigMap<br/>mockserver.properties<br/>initializerJson.json]
    end

    CM_INLINE -->|when app.config.enabled| CM_REF
    CM_EXT -->|external| CM_REF
    CM_REF -->|mounts /config| DEP
```

| Chart | Path | Version | Purpose |
|-------|------|---------|---------|
| `mockserver` | `helm/mockserver/` | 5.15.0 | Main deployment chart (includes optional ConfigMap) |
| `mockserver-config` | `helm/mockserver-config/` | 5.15.0 | Example external ConfigMap chart (for reference) |

## mockserver Chart

### Templates

| Template | Purpose |
|----------|---------|
| `deployment.yaml` | Single-replica Deployment with ConfigMap volume mount |
| `service.yaml` | Service (NodePort/LoadBalancer/ClusterIP) |
| `ingress.yaml` | Optional Ingress resource |
| `configmap.yaml` | Optional ConfigMap for inline configuration (when `app.config.enabled`) |
| `service-test.yaml` | Helm test pod (curl readiness check) |
| `_helpers.tpl` | Template helper functions |
| `NOTES.txt` | Post-install instructions |

### Default Values

```yaml
replicaCount: 1
app:
  logLevel: "INFO"
  serverPort: "1080"
  mountedConfigMapName: "mockserver-config"
  mountedLibsConfigMapName: "mockserver-config"
  propertiesFileName: "mockserver.properties"
  readOnlyRootFilesystem: false
  serviceAccountName: default
  runAsUser: 65534
  config:
    enabled: false
    properties: ""
    initializerJson: ""
    extraFiles: {}
image:
  repository: mockserver
  snapshot: false
  pullPolicy: IfNotPresent
service:
  type: NodePort
  port: 1080
  annotations: {}
  clusterIP: ""
  externalIPs: []
  loadBalancerIP: ""
  loadBalancerSourceRanges: []
  nodePort: ""
  test:
    image: radial/busyboxplus:curl
ingress:
  enabled: false
  className: ""
  annotations: {}
  hosts:
    - host: mockserver.local
      paths:
        - path: /
          pathType: ImplementationSpecific
  tls: []
podAnnotations: {}
resources: {}
nodeSelector: {}
tolerations: []
affinity: {}
releasenameOverride: ""
```

### Deployment Architecture

```mermaid
graph TB
    ING[Ingress<br/><i>optional</i>] --> SVC[Service<br/>NodePort :1080]
    SVC --> POD[Pod]

    subgraph POD
        CONT[MockServer Container<br/>Port 1080]
        VOL_PROPS["/config/<br/>mockserver.properties"]
        VOL_LIBS["/libs/<br/>additional JARs"]
    end

    CM[ConfigMap<br/>mockserver-config] -->|volume mount| VOL_PROPS
    CM -->|volume mount| VOL_LIBS
    CONT --> VOL_PROPS
    CONT --> VOL_LIBS
```

### Health Checks

- **Readiness probe:** TCP socket check on port 1080
- **Liveness probe:** TCP socket check on port 1080

### Installation

```bash
# Add the chart repo (hosted on S3)
helm repo add mockserver https://www.mock-server.com
helm repo update

# Install with defaults (no configuration)
helm install mockserver mockserver/mockserver

# Install with custom values
helm install mockserver mockserver/mockserver \
  --set app.serverPort=1080 \
  --set service.type=ClusterIP

# Install with inline configuration (single chart — recommended)
helm install mockserver mockserver/mockserver \
  --set app.config.enabled=true \
  --set app.config.properties="mockserver.initializationJsonPath=/config/initializerJson.json" \
  --set app.config.initializerJson='[{"httpRequest":{"path":"/example"},"httpResponse":{"body":"response"}}]'

# Or using a values.yaml file for complex config
helm install mockserver mockserver/mockserver -f my-values.yaml

# Legacy: install external config chart first, then main chart
helm install mockserver-config mockserver/mockserver-config
helm install mockserver mockserver/mockserver
```

## mockserver-config Chart (Legacy / Example)

The separate `mockserver-config` chart is retained as a reference example. For new deployments, use the inline `app.config` values in the main chart instead (see above).

This chart provides a ConfigMap containing:

- `mockserver.properties` — server configuration
- `initializerJson.json` — pre-loaded expectations

### Template

The ConfigMap template loads default files from `static/`:

```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: {{ .Chart.Name }}
data:
  mockserver.properties: |-
    {{ printf "%s" (.Files.Get "static/mockserver.properties") | indent 4 }}
  initializerJson.json: |-
    {{ printf "%s" (.Files.Get "static/initializerJson.json") | indent 4 }}
```

Static defaults are in `helm/mockserver-config/static/`:
- `mockserver.properties` — default MockServer properties
- `initializerJson.json` — default expectation initialiser (empty array)

## Chart Repository

The Helm chart repository is hosted on S3 alongside the website:

- **Bucket:** `aws-website-mockserver-nb9hq`
- **Index:** `helm/charts/index.yaml`
- **Charts:** `helm/charts/mockserver-*.tgz` (versions 5.3.0 through 5.15.0)

### Publishing a New Chart Version

```bash
# Package the chart
cd helm
helm package ./mockserver/

# Move to charts directory
mv mockserver-X.Y.Z.tgz charts/
cd charts

# Regenerate index
helm repo index .

# Upload to S3
# (manual upload via AWS Console or aws s3 cp)
```

## Kind-Based Integration Testing

The container integration tests use Kind (Kubernetes in Docker) for Helm testing:

```mermaid
sequenceDiagram
    participant Script as integration_tests.sh
    participant Kind as Kind Cluster
    participant Helm as Helm
    participant MS as MockServer Pod
    participant Client as Curl Client Pod

    Script->>Kind: Create cluster (port 1080 mapped)
    Script->>Kind: Load MockServer image
    Script->>Helm: helm install mockserver
    Helm->>MS: Deploy pod
    Script->>Client: Create expectations (curl PUT)
    Script->>Client: Validate responses (curl GET)
    Script->>Helm: helm uninstall
    Script->>Kind: Delete cluster
```

**Kind config** (`container_integration_tests/kind-config.yaml`):

```yaml
kind: Cluster
apiVersion: kind.x-k8s.io/v1alpha4
nodes:
  - role: control-plane
    extraPortMappings:
      - containerPort: 1080
        hostPort: 1080
```
