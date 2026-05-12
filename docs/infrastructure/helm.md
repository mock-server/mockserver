# Helm & Kubernetes

## Charts Overview

MockServer provides two Helm charts. The main `mockserver` chart can optionally create its own ConfigMap via inline configuration, or mount an externally-created ConfigMap:

```mermaid
graph LR
    subgraph "mockserver chart"
        DEP[Deployment]
        SVC[Service]
        ING[Ingress]
        CM_INLINE["ConfigMap
inline, optional"]
        CM_REF[ConfigMap volume mount]
    end

    subgraph "mockserver-config chart (legacy)"
        CM_EXT["ConfigMap
mockserver.properties
initializerJson.json"]
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
| `pvc.yaml` | Optional PersistentVolumeClaim (when `app.persistence.enabled` and no `existingClaimName`) |
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
  persistence:
    enabled: false
    existingClaimName: ""
    storageClass: ""
    accessModes:
      - ReadWriteOnce
    size: 256Mi
    mountPath: /persistence
    annotations: {}
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
podLabels: {}
resources: {}
nodeSelector: {}
tolerations: []
affinity: {}
imagePullSecrets: []
releasenameOverride: ""
```

### Deployment Architecture

```mermaid
graph TB
    ING["Ingress
optional"] --> SVC["Service
NodePort :1080"]
    SVC --> POD[Pod]

    subgraph POD
        CONT["MockServer Container
Port 1080"]
        VOL_PROPS["/config/
mockserver.properties"]
        VOL_LIBS["/libs/
additional JARs"]
        VOL_PERSIST["/persistence/
persistedExpectations.json
optional"]
    end

    CM["ConfigMap
mockserver-config"] -->|volume mount| VOL_PROPS
    CM -->|volume mount| VOL_LIBS
    PVC["PersistentVolumeClaim
optional"] -->|volume mount| VOL_PERSIST
    CONT --> VOL_PROPS
    CONT --> VOL_LIBS
    CONT --> VOL_PERSIST
```

### Persistence

When `app.persistence.enabled=true`, the chart:

1. Creates a PersistentVolumeClaim (unless `app.persistence.existingClaimName` references an existing one)
2. Mounts the PVC at `app.persistence.mountPath` (default `/persistence`)
3. Injects environment variables to enable MockServer's file-based persistence:
   - `MOCKSERVER_PERSIST_EXPECTATIONS=true`
   - `MOCKSERVER_PERSISTED_EXPECTATIONS_PATH=/persistence/persistedExpectations.json`
   - `MOCKSERVER_INITIALIZATION_JSON_PATH=/persistence/persistedExpectations.json`

**Property precedence:** These environment variables are safe defaults. MockServer's property resolution order is: system property > property file > environment variable > hardcoded default. So any matching property in the user's `mockserver.properties` file overrides the chart-injected env vars.

| Value | Type | Default | Description |
|-------|------|---------|-------------|
| `app.persistence.enabled` | bool | `false` | Enable persistent storage |
| `app.persistence.existingClaimName` | string | `""` | Use existing PVC (skip PVC creation) |
| `app.persistence.storageClass` | string | `""` | StorageClass (empty = cluster default) |
| `app.persistence.accessModes` | list | `[ReadWriteOnce]` | PVC access modes |
| `app.persistence.size` | string | `256Mi` | PVC size |
| `app.persistence.mountPath` | string | `/persistence` | Container mount path |
| `app.persistence.annotations` | map | `{}` | PVC annotations |

**Backward compatibility:** Disabled by default. When disabled, no PVC, volumes, volumeMounts, or env vars are added — the chart behaves identically to before this feature was added.

**PVC retention:** Chart-managed PVCs are NOT deleted by `helm uninstall`. Delete the PVC manually if you want to remove persisted data: `kubectl delete pvc <release-name> -n <namespace>`.

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
# Use --set-string for JSON values — escape commas as \, since --set treats commas as separators
helm install mockserver mockserver/mockserver \
  --set app.config.enabled=true \
  --set app.config.properties="mockserver.initializationJsonPath=/config/initializerJson.json" \
  --set-string 'app.config.initializerJson=[{"httpRequest":{"path":"/example"}\,"httpResponse":{"body":"response"}}]'

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

## Versioning Policy

All MockServer components — Java modules, client libraries, Docker images, and Helm charts — share a single version number. This keeps things simple and transparent for users.

The Helm chart `version` and `appVersion` in `Chart.yaml` **MUST always match the MockServer application version**. Both charts (`mockserver` and `mockserver-config`) follow this rule.

- **NEVER** bump the chart version independently of the MockServer version
- **NEVER** change `version` without also changing `appVersion` to the same value
- The release script `scripts/ci/release/publish-helm.sh` enforces this by setting both fields to `$RELEASE_VERSION`
- Both charts must be kept at the same version

Helm chart changes made between releases are published as part of the next MockServer release, not independently.

## Chart Repository

The Helm chart repository is hosted on S3 alongside the website:

- **Bucket:** Main website S3 bucket (see `~/mockserver-aws-ids.md`)
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

## Testing

### Static Validation (CI)

The infra pipeline runs `helm lint` and `helm template` against both charts on every change to `helm/`. This catches syntax errors, rendering issues, and invalid template logic without needing a Kubernetes cluster.

- **Pipeline step:** `.buildkite/scripts/steps/helm-validate.sh`
- **Validates:** default values, inline config enabled, ingress enabled, persistence enabled (chart-managed and existing PVC)

### k3d-Based Integration Testing

The container integration tests use k3d (k3s in Docker) for Helm testing. k3d was chosen over Kind for faster cluster startup (~10-15s vs ~30-40s) and simpler port mapping.

```mermaid
sequenceDiagram
    participant Script as integration_tests.sh
    participant K3d as k3d Cluster
    participant Helm as Helm
    participant MS as MockServer Pod
    participant Test as helm test Pod

    Script->>K3d: Create cluster (port 1080 mapped)
    Script->>K3d: Import MockServer image
    Script->>Helm: helm install mockserver
    Helm->>MS: Deploy pod
    Script->>Helm: helm test (curl /status)
    Helm->>Test: Run test pod
    Script->>MS: Create expectations (curl PUT)
    Script->>MS: Validate responses (curl PUT)
    Script->>Helm: helm uninstall
    Script->>K3d: Delete cluster
```

**k3d config** (`container_integration_tests/k3d-config.yaml`):

```yaml
apiVersion: k3d.io/v1alpha5
kind: Simple
metadata:
  name: mockserver
servers: 1
ports:
  - port: 1080:1080
    nodeFilters:
      - loadbalancer
```

### Test Cases

| Test | What It Tests |
|------|--------------|
| `helm_default_config` | Default chart deployment (no overrides) |
| `helm_local_docker_container` | Custom local Docker image |
| `helm_custom_server_port` | Custom server port (`app.serverPort=1081`) |
| `helm_remote_host_and_port` | Proxy remote host/port (two MockServer instances) |
| `helm_inline_config` | Inline ConfigMap with pre-loaded expectations (`app.config.enabled=true`) |

Each test also invokes `helm test` to verify the service-test pod can reach MockServer's `/status` endpoint.

### Running Locally

```bash
# Prerequisites: docker, k3d, helm, kubectl
# Build the test image first
SKIP_HELM_TESTS=true container_integration_tests/integration_tests.sh

# Run helm tests only
SKIP_JAVA_BUILD=true SKIP_DOCKER_TESTS=true container_integration_tests/integration_tests.sh

# Clean up cluster afterward
DELETE_CLUSTER=true SKIP_JAVA_BUILD=true SKIP_DOCKER_TESTS=true container_integration_tests/integration_tests.sh
```
