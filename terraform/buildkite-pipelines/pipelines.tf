locals {
  repository = "https://github.com/mock-server/mockserver.git"

  pipelines = {
    "pipeline" = {
      name        = "MockServer"
      description = "Monorepo CI — path-based pipeline orchestrator"
      file        = ".buildkite/pipeline.yml"
      emoji       = ":pipeline:"
      trigger     = "code"
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

  steps = "steps:\n  - label: \":pipeline:\"\n    command: \"buildkite-agent pipeline upload ${each.value.file}\"\n"

  provider_settings = {
    trigger_mode          = each.value.trigger
    build_branches        = each.value.trigger == "code"
    build_pull_requests   = each.value.trigger == "code"
    build_tags            = false
    publish_commit_status = each.value.trigger == "code"
  }
}
