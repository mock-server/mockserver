locals {
  repository = "https://github.com/mock-server/mockserver-monorepo.git"

  pipelines = {
    "pipeline" = {
      name        = "MockServer"
      description = "Monorepo CI — path-based pipeline orchestrator"
      file        = ".buildkite/pipeline.yml"
      emoji       = ":pipeline:"
      trigger     = "code"
    }
    "java" = {
      name        = "MockServer Java"
      description = "Java server build and test (all Maven modules)"
      file        = ".buildkite/pipeline-java.yml"
      emoji       = ":maven:"
      trigger     = "none"
    }
    "ui" = {
      name        = "MockServer UI"
      description = "Dashboard React SPA — lint, test, build"
      file        = ".buildkite/pipeline-ui.yml"
      emoji       = ":react:"
      trigger     = "none"
    }
    "node" = {
      name        = "MockServer Node"
      description = "Node.js client and launcher — lint and typecheck"
      file        = ".buildkite/pipeline-node.yml"
      emoji       = ":node:"
      trigger     = "none"
    }
    "python" = {
      name        = "MockServer Python"
      description = "Python client — unit and integration tests"
      file        = ".buildkite/pipeline-python.yml"
      emoji       = ":python:"
      trigger     = "none"
    }
    "ruby" = {
      name        = "MockServer Ruby"
      description = "Ruby client — unit and integration tests"
      file        = ".buildkite/pipeline-ruby.yml"
      emoji       = ":ruby:"
      trigger     = "none"
    }
    "maven-plugin" = {
      name        = "MockServer Maven Plugin"
      description = "Maven plugin build and test"
      file        = ".buildkite/pipeline-maven-plugin.yml"
      emoji       = ":maven:"
      trigger     = "none"
    }
    "perf-test" = {
      name        = "MockServer Performance Test"
      description = "Performance test validation"
      file        = ".buildkite/pipeline-perf-test.yml"
      emoji       = ":chart_with_upwards_trend:"
      trigger     = "none"
    }
    "container-tests" = {
      name        = "MockServer Container Tests"
      description = "Container integration test script validation"
      file        = ".buildkite/pipeline-container-tests.yml"
      emoji       = ":docker:"
      trigger     = "none"
    }
    "website" = {
      name        = "MockServer Website"
      description = "Jekyll documentation site build"
      file        = ".buildkite/pipeline-website.yml"
      emoji       = ":jekyll:"
      trigger     = "none"
    }
    "infra" = {
      name        = "MockServer Infra"
      description = "Infrastructure, CI/CD, and shared config validation"
      file        = ".buildkite/pipeline-infra.yml"
      emoji       = ":terraform:"
      trigger     = "none"
    }
    "docker-push-maven" = {
      name        = "MockServer Build Image"
      description = "Build and push mockserver/mockserver:maven CI image"
      file        = ".buildkite/docker-push-maven.yml"
      emoji       = ":docker:"
      trigger     = "none"
    }
    "docker-push-release" = {
      name        = "MockServer Release Image"
      description = "Build and push multi-arch release image to Docker Hub"
      file        = ".buildkite/docker-push-release.yml"
      emoji       = ":docker:"
      trigger     = "none"
    }
  }
}

resource "buildkite_pipeline" "pipeline" {
  for_each = local.pipelines

  name           = each.value.name
  description    = each.value.description
  repository     = local.repository
  default_branch = "master"
  emoji          = each.value.emoji

  cancel_intermediate_builds = true
  skip_intermediate_builds   = true

  steps = "steps:\n  - label: \":pipeline:\"\n    command: \"buildkite-agent pipeline upload ${each.value.file}\"\n"

  provider_settings = {
    trigger_mode          = each.value.trigger
    build_branches        = each.value.trigger == "code"
    build_pull_requests   = each.value.trigger == "code"
    build_tags            = false
    publish_commit_status = each.value.trigger == "code"
  }
}
