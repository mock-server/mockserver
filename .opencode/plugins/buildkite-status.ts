import type { Plugin } from "@opencode-ai/plugin"
import fs from "node:fs/promises"
import path from "node:path"

const THROTTLE_FILE = ".tmp/.buildkite-status-last-run"
const STATUS_FILE = ".tmp/.buildkite-status"
const THROTTLE_HOURS = 1

async function shouldRun(worktree: string): Promise<boolean> {
  try {
    const stat = await fs.stat(path.join(worktree, THROTTLE_FILE))
    return Date.now() - stat.mtimeMs > THROTTLE_HOURS * 3600 * 1000
  } catch {
    return true
  }
}

async function ensureDir(dir: string): Promise<void> {
  await fs.mkdir(dir, { recursive: true })
}

export const BuildkiteStatus: Plugin = async ({ $, client, worktree }) => {
  return {
    event: async ({ event }) => {
      if (event.type !== "session.created") return
      if (!(await shouldRun(worktree))) return

      const tmpDir = path.join(worktree, ".tmp")
      await ensureDir(tmpDir)

      const token = process.env.BUILDKITE_TOKEN
      if (!token) {
        await fs.writeFile(
          path.join(worktree, THROTTLE_FILE),
          new Date().toISOString()
        )
        return
      }

      let builds: Array<{
        number: number
        state: string
        message: string
        branch: string
        web_url: string
        created_at: string
      }>
      try {
        const response = await fetch(
          "https://api.buildkite.com/v2/organizations/mockserver/pipelines/mockserver/builds?per_page=5",
          { headers: { Authorization: `Bearer ${token}` } }
        )
        if (!response.ok) {
          await fs.writeFile(
            path.join(worktree, THROTTLE_FILE),
            new Date().toISOString()
          )
          return
        }
        builds = await response.json()
      } catch {
        await fs.writeFile(
          path.join(worktree, THROTTLE_FILE),
          new Date().toISOString()
        )
        return
      }

      const failed = builds.filter((b) => b.state === "failed")
      const TEN_MINUTES = 10 * 60 * 1000
      const stuck = builds.filter(
        (b) =>
          b.state === "scheduled" &&
          Date.now() - new Date(b.created_at).getTime() > TEN_MINUTES
      )

      const problems: string[] = []
      let toastMessage = ""
      let toastVariant: "warning" | "error" = "warning"

      if (stuck.length > 0) {
        const stuckSummary = stuck
          .map(
            (b) =>
              `- Build #${b.number} (${b.branch}): scheduled ${Math.round((Date.now() - new Date(b.created_at).getTime()) / 60000)}min ago — no agent\n  ${b.web_url}`
          )
          .join("\n")
        problems.push(
          `${stuck.length} build(s) stuck waiting for an agent:\n\n${stuckSummary}`
        )
        toastMessage = `Buildkite: ${stuck.length} build(s) waiting for agent — check AWS ASG`
        toastVariant = "error"
      }

      if (failed.length > 0) {
        const failedSummary = failed
          .map(
            (b) =>
              `- Build #${b.number} (${b.branch}): ${b.message?.split("\n")[0] || "no message"}\n  ${b.web_url}`
          )
          .join("\n")
        problems.push(
          `${failed.length} of ${builds.length} recent builds failed:\n\n${failedSummary}`
        )
        if (!toastMessage) {
          toastMessage = `Buildkite: ${failed.length} recent build(s) failing`
        }
      }

      if (problems.length > 0) {
        await fs.writeFile(
          path.join(worktree, STATUS_FILE),
          `# Buildkite Status\n\nChecked at ${new Date().toISOString()}\n\n${problems.join("\n\n---\n\n")}\n`
        )
        await client.tui.showToast({
          body: {
            message: toastMessage,
            variant: toastVariant,
          },
        })
      } else {
        try {
          await fs.unlink(path.join(worktree, STATUS_FILE))
        } catch {}
      }

      await fs.writeFile(
        path.join(worktree, THROTTLE_FILE),
        new Date().toISOString()
      )
    },
  }
}
