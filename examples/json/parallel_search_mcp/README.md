# Parallel Search MCP Capture and Replay Payloads

Expectation payloads for recording a remote MCP (Model Context Protocol) server
and replaying it offline. Every language's `parallel_search_mcp` example shares
the replay fixture here.

| File | Description |
|------|-------------|
| `capture_forward.json` | Forward `/mcp` to the live Parallel Search MCP server (`https://search.parallel.ai/mcp`) so MockServer records each exchange |
| `replay_expectations.json` | Five replay expectations made from a recorded session: `initialize`, `notifications/initialized`, `tools/list`, `tools/call web_search` and `tools/call web_fetch` |

## What it demonstrates

- **Capture**: `capture_forward.json` uses `httpOverrideForwardedRequest` with a
  `socketAddress` (`search.parallel.ai:443`, `HTTPS`) and a `Host` header
  override. MockServer records every proxied exchange as an expectation, which
  `PUT /mockserver/retrieve?type=RECORDED_EXPECTATIONS` returns.
- **Replay**: each recorded exchange is rewritten so that it:
  - matches on the JSON-RPC method with a `JSON_RPC` body matcher, or for
    `tools/call` on the tool name with a `JSON_PATH` matcher
    (`$.method == 'tools/call' && $.params.name == 'web_search'`), not on the
    exact recorded body, so new ids and arguments still match;
  - answers with a `VELOCITY` `httpResponseTemplate` that echoes the caller's id
    with `$!{request.jsonRpcRawId}`. Recorded `$`, `#` and `\` characters are written
    as `${esc.d}`, `${esc.h}` and `${esc.b}` so Velocity renders them literally (for
    example markdown `#` headings in fetched pages, or a `\` just before a `$`);
  - replays a notification (no response body) as its bare status code, `202`.
- The fixture is a trimmed real capture (two search results, one short fetch
  excerpt). It holds no credentials: replay expectations carry only body
  matchers and response bodies, never request headers.

## Prerequisites

- A running MockServer instance (default `http://localhost:1080`).
- For capture: outbound HTTPS from MockServer to `search.parallel.ai`. Parallel's
  free tier needs no API key; send your own as `Authorization: Bearer <key>` if you have one,
  after first turning on `redactSecretsInLog` (below) so MockServer does not print the
  forwarded header in its log. The setting stays on until MockServer restarts, or until
  you send `{"redactSecretsInLog": false}` to `PUT /mockserver/configuration`.

## Run

Replay (offline):

```bash
curl -X PUT "${MOCKSERVER_URL:-http://localhost:1080}/mockserver/expectation" \
  --data-binary @examples/json/parallel_search_mcp/replay_expectations.json

curl -X POST "${MOCKSERVER_URL:-http://localhost:1080}/mcp" \
  -H 'Content-Type: application/json' \
  -d '{"jsonrpc":"2.0","id":"call-7","method":"tools/call","params":{"name":"web_search","arguments":{"objective":"What is MockServer?","search_queries":["MockServer"]}}}'
```

Capture (live), then read what was recorded:

```bash
curl -X PUT "${MOCKSERVER_URL:-http://localhost:1080}/mockserver/reset"
curl -X PUT "${MOCKSERVER_URL:-http://localhost:1080}/mockserver/configuration" \
  -d '{"redactSecretsInLog": true}'
curl -X PUT "${MOCKSERVER_URL:-http://localhost:1080}/mockserver/expectation" \
  --data-binary @examples/json/parallel_search_mcp/capture_forward.json

curl -X POST "${MOCKSERVER_URL:-http://localhost:1080}/mcp" \
  -H 'Content-Type: application/json' -H 'User-Agent: mockserver-example/1.0' \
  -d '{"jsonrpc":"2.0","id":1,"method":"tools/list"}'

curl -X PUT "${MOCKSERVER_URL:-http://localhost:1080}/mockserver/retrieve?type=RECORDED_EXPECTATIONS&format=JSON" \
  -d '{"path":"/mcp"}'
```

[`../../curl/parallel_search_mcp/parallel_search_mcp.sh`](../../curl/parallel_search_mcp/parallel_search_mcp.sh)
runs the whole capture, rewrite and replay flow.

## Expected output

- Loading `replay_expectations.json` returns the five created expectations.
- The replayed `tools/call` returns `{"jsonrpc": "2.0", "id": "call-7", "result": {"content": [{"type": "text", "text": "{\"search_id\": ..., \"results\": [...]}"}], "isError": false}}`
  (pretty-printed), with the id echoed exactly as sent, a string here.
- During capture, `tools/list` returns Parallel's live `web_search` and
  `web_fetch` tools, and the retrieve call returns one recorded expectation per exchange.
