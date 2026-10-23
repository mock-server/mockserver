#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

exec "$SCRIPT_DIR/../run-in-docker.sh" \
  -i alpine/helm:3.17.3 \
  --entrypoint sh \
  -- -c '
    errors=0

    for chart in helm/mockserver helm/mockserver-config; do
      if [ -d "$chart" ]; then
        echo "--- Linting $chart"
        helm lint "$chart" || errors=$((errors + 1))

        echo "--- Rendering $chart"
        helm template test-release "$chart" > /dev/null || errors=$((errors + 1))
      fi
    done

    echo "--- Rendering mockserver with inline config enabled"
    helm template test-release helm/mockserver \
      --set app.config.enabled=true \
      --set app.config.properties="mockserver.initializationJsonPath=/config/initializerJson.json" \
      --set-string "app.config.initializerJson=[{\"httpRequest\":{\"path\":\"/example\"}\,\"httpResponse\":{\"body\":\"response\"}}]" \
      > /dev/null || errors=$((errors + 1))

    echo "--- Rendering mockserver with ingress enabled"
    helm template test-release helm/mockserver \
      --set ingress.enabled=true \
      > /dev/null || errors=$((errors + 1))

    echo "--- Rendering mockserver with persistence enabled (chart-managed PVC)"
    helm template test-release helm/mockserver \
      --set app.persistence.enabled=true \
      > /dev/null || errors=$((errors + 1))

    echo "--- Rendering mockserver with persistence enabled (existing PVC)"
    helm template test-release helm/mockserver \
      --set app.persistence.enabled=true \
      --set app.persistence.existingClaimName=my-existing-pvc \
      > /dev/null || errors=$((errors + 1))

    # ASSERTING render: rendering to /dev/null only catches parse failures, not a
    # silently-dropped field. The pod-level securityContext is gated behind a
    # {{- with .Values.podSecurityContext }} block in deployment.yaml; a refactor
    # that drops that block renders cleanly but emits NO securityContext (issue
    # #2320, fix d2fb9a8cf). Set podSecurityContext.fsGroup and assert the rendered
    # Deployment actually contains it, so the regression fails the build.
    echo "--- Asserting podSecurityContext.fsGroup renders into the Deployment (issue #2320)"
    rendered_pod_sc=$(helm template test-release helm/mockserver \
      --set podSecurityContext.fsGroup=2000 2>/dev/null) || {
        echo "FAILED: helm template with podSecurityContext.fsGroup failed to render"
        errors=$((errors + 1))
        rendered_pod_sc=""
      }
    # fsGroup is a pod-level field rendered ONLY from the podSecurityContext block
    # (the container-level securityContext uses runAsUser/capabilities, never
    # fsGroup), so a single grep for it is the sole, sufficient discriminator —
    # drop the block and this line vanishes. (A separate securityContext: grep
    # would be inert: the container securityContext always renders it.)
    # A heredoc, not a here-string: this payload runs under busybox sh, which has no <<<.
    if grep -qE "^[[:space:]]+fsGroup:[[:space:]]+2000[[:space:]]*$" <<EOF; then
$rendered_pod_sc
EOF
      echo "PASS: podSecurityContext.fsGroup: 2000 present in rendered Deployment"
    else
      echo "FAILED: podSecurityContext.fsGroup: 2000 NOT rendered into the Deployment securityContext (issue #2320 regression)"
      errors=$((errors + 1))
    fi

    # The extensions volume must mount read-only at /extensions (the images put /extensions/* on the
    # classpath), carry the user volume source verbatim, and set the class list; absent by default.
    echo "--- Asserting extensions.volume mounts read-only at /extensions"
    rendered_ext=$(helm template test-release helm/mockserver \
      --set extensions.classes=com.acme.AcmeExtension \
      --set extensions.volume.configMap.name=acme-ext 2>/dev/null) || rendered_ext=""
    ext_mount=$(printf "%s\n" "$rendered_ext" | grep -A2 -E "^[[:space:]]+- name: extensions-volume$" | tr -s " " | tr "\n" "|")
    ext_ok=1
    case "$ext_mount" in *"mountPath: /extensions| readOnly: true|"*) ;; *) ext_ok=0 ;; esac
    case "$ext_mount" in *"configMap:| name: acme-ext|"*) ;; *) ext_ok=0 ;; esac
    case "$rendered_ext" in *"value: \"com.acme.AcmeExtension\""*) ;; *) ext_ok=0 ;; esac
    if [ "$ext_ok" -eq 1 ]; then
      echo "PASS: extensions volume, read-only mount and MOCKSERVER_EXTENSIONS rendered"
    else
      echo "FAILED: extensions.volume did not render a read-only /extensions mount with its volume source and class list: $ext_mount"
      errors=$((errors + 1))
    fi
    rendered_default=$(helm template test-release helm/mockserver 2>/dev/null) || rendered_default="RENDER-FAILED"
    case "$rendered_default" in
      *extensions-volume*|*MOCKSERVER_EXTENSION*|RENDER-FAILED)
        echo "FAILED: the default render failed, or mounts an extensions volume or sets an extension property"
        errors=$((errors + 1)) ;;
    esac

    # image.variant selects a published tag suffix; assert the exact rendered image so a template
    # edit cannot silently point pods at a tag that does not exist.
    assert_image() {
      expected="$1"; shift
      rendered=$(helm template test-release helm/mockserver "$@" 2>/dev/null | grep "image: mockserver/" || true)
      if [ "$(echo $rendered)" = "image: $expected" ]; then
        echo "PASS: $* -> $expected"
      else
        echo "FAILED: $* rendered \"$rendered\", expected image: $expected"
        errors=$((errors + 1))
      fi
    }
    echo "--- Asserting image.variant renders the published tag"
    app_version=$(sed -n "s/^appVersion: *//p" helm/mockserver/Chart.yaml | tr -d "\"")
    assert_image "mockserver/mockserver:mockserver-$app_version"
    assert_image "mockserver/mockserver:mockserver-$app_version-http3" --set image.variant=http3
    assert_image "mockserver/mockserver:mockserver-snapshot-graaljs" --set image.snapshot=true --set image.variant=graaljs
    assert_image "mockserver/mockserver:custom-tag" --set image.variant=http3 --set image.repositoryNameAndTag=mockserver/mockserver:custom-tag
    if helm template test-release helm/mockserver --set image.variant=bogus >/dev/null 2>&1; then
      echo "FAILED: image.variant=bogus rendered; the schema enum must reject it"
      errors=$((errors + 1))
    else
      echo "PASS: image.variant=bogus rejected by the schema"
    fi

    if [ "$errors" -eq 0 ]; then
      echo "All Helm validations passed"
    else
      echo "FAILED: $errors validation(s) failed"
    fi
    exit $errors
  '
