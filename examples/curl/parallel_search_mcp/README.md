# Parallel Search MCP Capture and Replay (curl)

## What it demonstrates

Recording a real remote MCP (Model Context Protocol) server through MockServer
and replaying it offline, using only the REST control plane. The upstream is
the [Parallel Search MCP](https://docs.parallel.ai/integrations/mcp/search-mcp)
server at `https://search.parallel.ai/mcp`; the same steps work for any MCP
server that answers Streamable HTTP requests with JSON.

| Script | Description |
|--------|-------------|
| `parallel_search_mcp.sh` | Capture `initialize`, `tools/list`, `web_search` and `web_fetch` from the live server (opt-in), then replay them offline and check nothing reached Parallel |

- **Capture** (only with `MCP_CAPTURE=true`): loads
  [`capture_forward.json`](../../json/parallel_search_mcp/capture_forward.json), which
  forwards `/mcp` to `search.parallel.ai:443`, runs the MCP calls through
  MockServer, then reads the recorded exchanges with
  `PUT /mockserver/retrieve?type=RECORDED_EXPECTATIONS` and rewrites them with `jq`
  into replay expectations.
- **Replay**: after a reset, loads the replay expectations (the capture, or by
  default the shared fixture
  [`replay_expectations.json`](../../json/parallel_search_mcp/replay_expectations.json)).
  They match on the JSON-RPC method (`JSON_RPC` body matcher, or `JSON_PATH` on
  `params.name` for `tools/call`), and a Velocity template echoes the caller's
  id with `$!{request.jsonRpcRawId}`.
- **Check**: the script fails unless every reply echoes its request id, both
  tools are listed, search and fetch return URLs and content, and MockServer
  recorded no proxied traffic during replay.

The Parallel Search MCP server works without an API key (free tier). To capture
with your own key, set `PARALLEL_API_KEY`. Only the capture requests send it upstream, as
`Authorization: Bearer <key>`, via a curl config on stdin (never on the command line). It is not written to the replay expectations, which
keep only body matchers and response bodies. Before forwarding anything, capture turns
on MockServer's `redactSecretsInLog` setting (`PUT /mockserver/configuration`), so the
key is masked in MockServer's own log; the setting stays on until MockServer restarts, or until you send `{"redactSecretsInLog": false}` to `PUT /mockserver/configuration`.
If you start MockServer yourself, `MOCKSERVER_REDACT_SECRETS_IN_LOG=true` does the same
from the start. Replay needs no key and no network access.

## Prerequisites

- A running MockServer instance (e.g. `docker run -d -p 1080:1080 mockserver/mockserver`)
- `bash` and `curl`; capture also needs `jq` and outbound HTTPS from MockServer to `search.parallel.ai`
- Optionally `export MOCKSERVER_URL=http://localhost:1080` (the default)

## Run

```bash
# replay the shared fixture (offline)
bash parallel_search_mcp.sh

# capture from the live server, then replay the capture
MCP_CAPTURE=true bash parallel_search_mcp.sh
```

## Expected output

```
replay: loaded expectations from replay_expectations.json
replay: tools/list -> web_search, web_fetch
replay: web_search -> first result https://www.mock-server.com/
replay: web_fetch -> https://www.mock-server.com/
replay: 0 requests forwarded to search.parallel.ai -- answered from recorded fixtures
```

With `MCP_CAPTURE=true`, three `capture:` lines come first and replay reports
`loaded expectations from the live capture`.
