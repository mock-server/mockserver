#!/usr/bin/env bash
# Capture and replay a remote MCP server (Parallel Search) with the REST API.
#
#   bash parallel_search_mcp.sh                    # replay the shared fixture (offline)
#   MCP_CAPTURE=true bash parallel_search_mcp.sh   # capture live (needs jq), then replay it
#
# Self-asserting: exits non-zero if replay misbehaves or anything reaches Parallel.
# Honours MOCKSERVER_URL, or MOCKSERVER_HOST/MOCKSERVER_PORT (default localhost:1080).
# An optional PARALLEL_API_KEY is sent upstream during capture only.
set -euo pipefail

MS="${MOCKSERVER_URL:-http://${MOCKSERVER_HOST:-localhost}:${MOCKSERVER_PORT:-1080}}"
JSON_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../json/parallel_search_mcp" && pwd)"

fail() { echo "FAILED: $*" >&2; exit 1; }
auth_config() { [ -z "${API_KEY:-}" ] || printf 'header = "Authorization: Bearer %s"\n' "${API_KEY}"; }

# One JSON-RPC message per POST; an explicit User-Agent because Parallel's CDN
# rejects some client defaults. Usage: rpc <id> <method> <params-json>
# Any API key reaches curl as a config file on stdin (-K -), never as an argument visible in ps.
rpc() {
  local reply
  reply=$(auth_config | curl -sf -m 120 -K - -X POST "${MS}/mcp" \
    -H 'Content-Type: application/json' -H 'Accept: application/json, text/event-stream' \
    -H 'User-Agent: mockserver-example/1.0' \
    -d "{\"jsonrpc\": \"2.0\", \"id\": $1, \"method\": \"$2\", \"params\": $3}") || fail "$2 request failed"
  grep -Eq "\"id\" *: *$1([^0-9]|\$)" <<<"${reply}" || fail "$2 reply does not echo id $1"
  printf '%s' "${reply}"
}

# The application under test: initialize, list tools, search, then fetch.
run_session() {
  INIT=$(rpc 1 initialize '{"protocolVersion": "2025-06-18", "capabilities": {}, "clientInfo": {"name": "mockserver-example", "version": "1.0.0"}}')
  auth_config | curl -sf -o /dev/null -K - -X POST "${MS}/mcp" -H 'Content-Type: application/json' -H 'User-Agent: mockserver-example/1.0' \
    -d '{"jsonrpc": "2.0", "method": "notifications/initialized"}' || fail "notifications/initialized failed"
  TOOLS=$(rpc 2 tools/list '{}')
  SEARCH=$(rpc 3 tools/call '{"name": "web_search", "arguments": {"objective": "What is MockServer?", "search_queries": ["MockServer mock HTTP server"]}}')
  FETCH=$(rpc 4 tools/call '{"name": "web_fetch", "arguments": {"urls": ["https://www.mock-server.com/"], "objective": "What is MockServer?"}}')
}

# Rewrite each recorded exchange as a replay expectation: match on the JSON-RPC
# method (or tool name) and echo the caller's id; escape Velocity's $, # and \
# ("\" last: the $ and # replacements contain none, while its own contains a "$").
read -r -d '' TO_REPLAY <<'JQ' || true
def parsed: if type == "object" then (.json // .string) else . end
  | if type == "string" and . != "" then fromjson else . end;
[ .[]
  | (.httpRequest.body | parsed) as $req
  | (.httpResponse.body | parsed) as $res
  | (.httpResponse.statusCode // 200) as $status
  | { httpRequest: { method: "POST", path: "/mcp", body: (
        if $req.method == "tools/call"
        then { type: "JSON_PATH", jsonPath: "$[?(@.method == 'tools/call' && @.params.name == '\($req.params.name)')]" }
        else { type: "JSON_RPC", method: $req.method } end) } }
    + if $res == null or $res == "" then { httpResponse: { statusCode: $status } }
      else (if $res | has("result") then "result" else "error" end) as $key
        | { httpResponseTemplate: { templateType: "VELOCITY", template:
            "{\"statusCode\": \($status), \"headers\": [{\"name\": \"Content-Type\", \"values\": [\"application/json\"]}], \"body\": {\"jsonrpc\": \"2.0\", \"id\": $!{request.jsonRpcRawId}, \"\($key)\": \($res[$key] | tojson | gsub("\\$"; "${esc.d}") | gsub("#"; "${esc.h}") | gsub("\\\\"; "${esc.b}"))}}" } }
      end ]
JQ

REPLAY_SOURCE="replay_expectations.json"
REPLAY_EXPECTATIONS=$(cat "${JSON_DIR}/replay_expectations.json")

if [ "${MCP_CAPTURE:-}" = "true" ]; then
  command -v jq >/dev/null || fail "capture needs jq to rewrite the recorded exchanges"
  curl -sf -X PUT "${MS}/mockserver/reset" >/dev/null
  # keep credentials (e.g. the forwarded Authorization header) out of MockServer's own log
  curl -sf -X PUT "${MS}/mockserver/configuration" -d '{"redactSecretsInLog": true}' >/dev/null
  curl -sf -X PUT "${MS}/mockserver/expectation" --data-binary "@${JSON_DIR}/capture_forward.json" >/dev/null
  echo "capture: forwarding /mcp to https://search.parallel.ai/mcp"
  API_KEY="${PARALLEL_API_KEY:-}" run_session
  echo "capture: initialize, tools/list, web_search and web_fetch answered by the live server"
  RECORDED=$(curl -sf -X PUT "${MS}/mockserver/retrieve?type=RECORDED_EXPECTATIONS&format=JSON" -d '{"path": "/mcp"}')
  REPLAY_EXPECTATIONS=$(jq -c "${TO_REPLAY}" <<<"${RECORDED}")
  echo "capture: recorded $(jq length <<<"${RECORDED}") exchanges -> $(jq length <<<"${REPLAY_EXPECTATIONS}") replay expectations"
  REPLAY_SOURCE="the live capture"
fi

# Replay: reset (drops the forward expectation and the recorded log), load, re-run.
curl -sf -X PUT "${MS}/mockserver/reset" >/dev/null
curl -sf -o /dev/null -X PUT "${MS}/mockserver/expectation" --data-binary @- <<<"${REPLAY_EXPECTATIONS}" \
  || fail "MockServer rejected the replay expectations"
echo "replay: loaded expectations from ${REPLAY_SOURCE}"
API_KEY="" run_session

grep -q '"web_search"' <<<"${TOOLS}" && grep -q '"web_fetch"' <<<"${TOOLS}" || fail "tools/list is missing web_search or web_fetch"
echo "replay: tools/list -> web_search, web_fetch"
url='\\"url\\": *\\"(https?://[^\\"]+)'  # tool payloads are JSON text, so their quotes arrive escaped
[[ "${SEARCH}" =~ ${url} ]] || fail "web_search returned no result URLs"
echo "replay: web_search -> first result ${BASH_REMATCH[1]}"
[[ "${FETCH}" =~ ${url} ]] && grep -q '\\"excerpts\\"' <<<"${FETCH}" || fail "web_fetch returned no content"
echo "replay: web_fetch -> ${BASH_REMATCH[1]}"

# MockServer records only proxied exchanges as expectations, so none means nothing reached Parallel
forwarded=$(curl -sf -X PUT "${MS}/mockserver/retrieve?type=RECORDED_EXPECTATIONS&format=JSON" -d '{"path": "/mcp"}')
[ "$(tr -d '[:space:]' <<<"${forwarded}")" = "[]" ] || fail "requests were forwarded upstream during replay"
echo "replay: 0 requests forwarded to search.parallel.ai -- answered from recorded fixtures"
