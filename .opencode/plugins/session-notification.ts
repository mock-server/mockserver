import type { Plugin } from "@opencode-ai/plugin"

export const SessionNotification: Plugin = async ({ $ }) => {
  return {
    event: async ({ event }) => {
      if (event.type === "session.idle") {
        await $`osascript -e 'display notification "Task completed" with title "opencode — MockServer"'`
      }
      if (event.type === "session.error") {
        await $`osascript -e 'display notification "Session error occurred" with title "opencode — MockServer" sound name "Basso"'`
      }
    },
  }
}
