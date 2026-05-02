---
name: browser-auth
description: >
  Documents how to use Chrome DevTools MCP to navigate authenticated web pages,
  extract data (tokens, configuration values), take screenshots, and handle
  login flows. Use when you need to interact with Buildkite, AWS Console, or
  other authenticated web UIs via the Chrome browser. Triggers when users ask
  about "browser auth", "extract token from browser", "navigate to buildkite",
  "chrome mcp", or need to scrape data from authenticated pages.

---

# Chrome DevTools MCP — Browser Authentication Patterns

Use the Chrome DevTools MCP server to interact with authenticated web pages
(Buildkite, AWS Console, GitHub, etc.) through the user's existing Chrome
browser session.

## Prerequisites

- **Chrome 144+** installed (check `chrome://version`)
- **Chrome DevTools MCP** configured in OpenCode (see Configuration below)
- **User logged into target sites** in their Chrome browser

## Configuration

The MCP server is configured in `~/.config/opencode/opencode.json`:

```json
{
  "mcp": {
    "chrome-devtools": {
      "type": "local",
      "command": ["npx", "-y", "chrome-devtools-mcp@latest", "--autoConnect"]
    }
  }
}
```

### Connection Modes

| Mode | Config | When to Use |
|------|--------|-------------|
| `--autoConnect` | Connects to running Chrome via DevTools discovery | **Recommended.** Uses existing browser with cookies/sessions. Requires Chrome 144+ and enabling remote debugging in `chrome://inspect/#remote-debugging`. |
| `--browserUrl` | `--browserUrl http://127.0.0.1:9222` | When Chrome is launched with `--remote-debugging-port=9222`. Requires restarting Chrome with the flag. |
| *(default)* | No flags | Launches a **fresh** Chrome instance with no cookies, extensions, or password manager. **Not suitable for authenticated pages.** |

### Enabling `--autoConnect`

1. Open Chrome
2. Navigate to `chrome://inspect/#remote-debugging`
3. Enable the **"Allow remote debugging connections"** toggle
4. Restart OpenCode so the MCP server reconnects

### Using `--browserUrl` (Alternative)

If `--autoConnect` doesn't work, restart Chrome with the debugging port:

```bash
# Quit Chrome first, then relaunch:
/Applications/Google\ Chrome.app/Contents/MacOS/Google\ Chrome \
  --remote-debugging-port=9222 \
  --user-data-dir="$HOME/Library/Application Support/Google/Chrome"
```

Update the MCP config:
```json
"command": ["npx", "-y", "chrome-devtools-mcp@latest", "--browserUrl", "http://127.0.0.1:9222"]
```

**Note:** On macOS, `--remote-debugging-port` requires `--user-data-dir` to be
explicitly set (even if it's the default path). Without it, Chrome prints
"DevTools remote debugging requires a non-default data directory" and the
debugging server does not start.

## Workflow: Navigate and Extract Data

### Step 1: Check if the User is Logged In

```
navigate_page → take_snapshot → check for login page indicators
```

Look for: "Login", "Sign in", "Welcome back", password fields, SSO buttons.

If logged out, **stop and ask the user to log in manually**. Never attempt to
fill in credentials — the user's password manager and MFA handle this.

### Step 2: Navigate to the Target Page

```
navigate_page(type="url", url="https://example.com/settings")
```

Wait for the page to load. If redirected to a login page, see Step 1.

### Step 3: Take a Snapshot

```
take_snapshot()
```

The snapshot returns a text-based accessibility tree with `uid` identifiers
for each element. Use these UIDs to interact with elements.

### Step 4: Extract Data

**Option A — From visible text (snapshot):**
Read the snapshot output to find the data. Token values, configuration
settings, and other text are visible in the accessibility tree.

**Option B — From hidden/copyable elements (JavaScript):**
```
evaluate_script(function="() => document.querySelector('[data-token]').textContent")
```

**Option C — From clipboard (click-to-copy buttons):**
```
click(uid="<copy-button-uid>")
evaluate_script(function="() => navigator.clipboard.readText()")
```

### Step 5: Verify with Screenshot (Optional)

```
take_screenshot()
```

Use screenshots to confirm you're on the right page before extracting
sensitive data.

## Common Patterns

### Extract Buildkite Agent Token

```
1. navigate_page(url="https://buildkite.com/organizations/<org>/agents")
2. take_snapshot()
3. Find the token element in the snapshot (look for "Agent Token" heading)
4. Click "Reveal Agent Token" button if token is hidden
5. take_snapshot() again to read the revealed token
   — OR —
   evaluate_script() to extract it from the DOM
```

The token page shows the agent registration token used in `terraform.tfvars`
and AWS SSM Parameter Store.

### Navigate AWS Console

```
1. navigate_page(url="https://eu-west-2.console.aws.amazon.com/...")
2. take_snapshot()
3. If redirected to SSO login, ask user to complete login
4. Extract data from console pages via snapshots
```

**Tip:** AWS Console pages are heavily JavaScript-rendered. Use
`wait_for(text=["Expected text"])` after navigation to ensure the page
has fully loaded before taking a snapshot.

### Verify a Deployment

```
1. navigate_page(url="https://buildkite.com/organizations/<org>/pipelines/<pipeline>")
2. take_snapshot() — check build status
3. take_screenshot() — capture visual evidence
```

## Login Flow Handling

### When to Ask the User to Log In Manually

**Always.** Never automate login flows because:
- Password managers (1Password, Bitwarden) need user interaction
- MFA/2FA requires physical device or biometric
- SSO flows redirect through corporate identity providers
- Cloudflare challenges require human verification

### Recommended Flow

1. Navigate to the target URL
2. Take a snapshot
3. If login page detected:
   - Take a screenshot so the user sees the state
   - Tell the user: "Please log in to [site] in your Chrome browser"
   - Wait for the user to confirm they've logged in
   - Re-navigate and take a new snapshot
4. Proceed with data extraction

### Detecting Login Pages

Look for these indicators in the snapshot:
- Page title containing "Login", "Sign in", "Welcome back"
- Form elements: email/username + password textboxes
- SSO buttons: "Login with GitHub", "Login with SSO", "Login with Google"
- Cloudflare challenge iframes

## Troubleshooting

| Problem | Cause | Fix |
|---------|-------|-----|
| Login page shown despite user being logged in | MCP connected to fresh Chrome instance, not user's browser | Switch to `--autoConnect` or `--browserUrl` mode |
| `navigate_page` shows "page closed" | Previous page was closed by navigation | Use `list_pages` then `new_page` |
| Snapshot shows empty page | JavaScript hasn't rendered yet | Use `wait_for(text=["expected content"])` before snapshot |
| "Verify you are human" Cloudflare challenge | Bot protection triggered | Ask user to complete challenge manually in browser |
| Port 9222 not responding | Chrome not started with `--remote-debugging-port` or macOS blocking | Use `--autoConnect` mode instead |

## Security Notes

- **Never log credentials.** If you see passwords or tokens in snapshots, redact them in any output shown to the user.
- **Tokens are sensitive.** When extracting tokens (e.g., Buildkite agent token), write them directly to gitignored files (e.g., `terraform.tfvars`) — never include them in commit messages, logs, or documentation.
- **Screenshots may contain secrets.** Avoid saving screenshots of pages containing tokens or keys to non-temporary locations.
