// MockServer Rust client -- capture and replay a remote MCP server.
//
// Records an MCP (Model Context Protocol) client's JSON-RPC calls to the live
// Parallel Search MCP server (https://search.parallel.ai/mcp) through MockServer,
// then replays them offline from expectations that match on the JSON-RPC method
// and echo the caller's JSON-RPC id with a Velocity template.
//
//   cargo run                    # replay the shared fixture (offline)
//   MCP_CAPTURE=true cargo run   # capture live, then replay the capture
//
// Run against a MockServer on localhost:1080 (override with MOCKSERVER_HOST /
// MOCKSERVER_PORT). An optional PARALLEL_API_KEY is sent upstream during capture.

use std::time::Duration;

use mockserver_client::{
    Body, ClientBuilder, Expectation, HttpOverrideForwardedRequest, HttpRequest, HttpResponse,
    HttpTemplate, MockServerClient, SocketAddress,
};
use serde_json::{json, Value};

type Res<T> = Result<T, Box<dyn std::error::Error>>;
/// (server name, tool names, web_search results, web_fetch results)
type Session = (String, Vec<String>, Vec<Value>, Vec<Value>);

const UPSTREAM_HOST: &str = "search.parallel.ai";
const FIXTURE: &str = concat!(env!("CARGO_MANIFEST_DIR"), "/../../json/parallel_search_mcp/replay_expectations.json");

fn check(condition: bool, message: impl Into<String>) -> Res<()> {
    if condition { Ok(()) } else { Err(message.into().into()) }
}

/// Minimal MCP Streamable HTTP client: one JSON-RPC message per POST.
struct McpClient {
    url: String,
    api_key: Option<String>,
    next_id: u64,
    http: reqwest::blocking::Client,
}

impl McpClient {
    fn new(url: &str, api_key: Option<String>) -> Res<Self> {
        let http = reqwest::blocking::Client::builder().timeout(Duration::from_secs(120)).build()?;
        Ok(Self { url: url.to_string(), api_key, next_id: 1, http })
    }

    fn send(&mut self, method: &str, params: Option<Value>, notification: bool) -> Res<Value> {
        let mut message = json!({"jsonrpc": "2.0", "method": method});
        if let Some(params) = params {
            message["params"] = params;
        }
        let id = self.next_id;
        if !notification {
            message["id"] = json!(id);
            self.next_id += 1;
        }
        // an explicit User-Agent: Parallel's CDN rejects some library defaults
        let mut request = self.http.post(&self.url)
            .header("Content-Type", "application/json")
            .header("Accept", "application/json, text/event-stream")
            .header("User-Agent", "mockserver-example/1.0")
            .body(message.to_string());
        if let Some(key) = self.api_key.as_deref().filter(|k| !k.is_empty()) {
            request = request.bearer_auth(key);
        }
        let body = request.send()?.error_for_status()?.text()?;
        if notification {
            return Ok(Value::Null);
        }
        let reply: Value = serde_json::from_str(&body)?;
        check(reply["id"] == json!(id), format!("{method}: id {} != {id}", reply["id"]))?;
        Ok(reply["result"].clone())
    }

    fn call_tool(&mut self, name: &str, arguments: Value) -> Res<Value> {
        let result = self.send("tools/call", Some(json!({"name": name, "arguments": arguments})), false)?;
        // Parallel returns its payload as JSON text
        let text = result["content"][0]["text"].as_str().ok_or(format!("{name}: no text content"))?;
        Ok(serde_json::from_str(text)?)
    }
}

/// The application under test: initialize, list tools, search, then fetch.
fn run_session(mcp: &mut McpClient) -> Res<Session> {
    let init = mcp.send("initialize", Some(json!({"protocolVersion": "2025-06-18", "capabilities": {},
        "clientInfo": {"name": "mockserver-example", "version": "1.0.0"}})), false)?;
    mcp.send("notifications/initialized", None, true)?;
    let tools = mcp.send("tools/list", Some(json!({})), false)?;
    let tools = tools["tools"].as_array().into_iter().flatten()
        .filter_map(|tool| tool["name"].as_str().map(String::from)).collect();
    let search = mcp.call_tool("web_search", json!({"objective": "What is MockServer?",
        "search_queries": ["MockServer mock HTTP server"]}))?;
    let fetch = mcp.call_tool("web_fetch", json!({"urls": ["https://www.mock-server.com/"],
        "objective": "What is MockServer?"}))?;
    let results = |v: &Value| v["results"].as_array().cloned().unwrap_or_default();
    Ok((init["serverInfo"]["name"].as_str().unwrap_or_default().to_string(), tools, results(&search), results(&fetch)))
}

/// MockServer records a JSON body as a string, or as {"json": ...} / {"string": ...}.
fn body_json(body: &Value) -> Value {
    let body = body.get("json").or_else(|| body.get("string")).unwrap_or(body);
    match body {
        Value::String(text) => serde_json::from_str(text).unwrap_or(Value::Null),
        other => other.clone(),
    }
}

/// Turn one recorded JSON-RPC exchange into a replay expectation that echoes the caller's id.
fn replay_expectation(recorded: &Value) -> Expectation {
    let request_body = body_json(&recorded["httpRequest"]["body"]);
    let status_code = recorded["httpResponse"]["statusCode"].as_u64().unwrap_or(200);
    let response_body = body_json(&recorded["httpResponse"]["body"]);
    let method = request_body["method"].as_str().unwrap_or_default();
    let matcher = if method == "tools/call" {
        let tool = request_body["params"]["name"].as_str().unwrap_or_default();
        Body::json_path(format!("$[?(@.method == 'tools/call' && @.params.name == '{tool}')]"))
    } else {
        let json_rpc = json!({"type": "JSON_RPC", "method": method});
        Body::object(json_rpc.as_object().cloned().unwrap_or_default())
    };
    let request = HttpRequest::new().method("POST").path("/mcp").body_value(matcher);
    let Some(response) = response_body.as_object().filter(|r| !r.is_empty()) else {
        // a notification: replay the bare status (202 Accepted)
        return Expectation::new(request).respond(HttpResponse::new().status_code(status_code as u16));
    };
    let key = if response.contains_key("result") { "result" } else { "error" };
    // escape Velocity metacharacters so recorded text (e.g. markdown "#") renders literally
    // "\" goes last because the $ and # replacements contain none, while its own contains a "$"
    let recorded = response[key].to_string().replace('$', "${esc.d}").replace('#', "${esc.h}").replace('\\', "${esc.b}");
    let template = format!(
        r#"{{"statusCode": {status_code}, "headers": [{{"name": "Content-Type", "values": ["application/json"]}}], "body": {{"jsonrpc": "2.0", "id": $!{{request.jsonRpcRawId}}, "{key}": {recorded}}}}}"#
    );
    Expectation::new(request).respond_template(HttpTemplate::new("VELOCITY", template))
}

/// Raw REST: the typed `retrieve_recorded_expectations` cannot parse recorded JSON response bodies (objects).
fn retrieve_recorded(mcp_url: &str) -> Res<Vec<Value>> {
    let url = format!("{}/mockserver/retrieve?type=RECORDED_EXPECTATIONS&format=JSON", mcp_url.trim_end_matches("/mcp"));
    let text = reqwest::blocking::Client::new().put(url).body(r#"{"path":"/mcp"}"#).send()?.error_for_status()?.text()?;
    Ok(if text.is_empty() { vec![] } else { serde_json::from_str(&text)? })
}

fn capture(client: &MockServerClient, mcp_url: &str) -> Res<Vec<Expectation>> {
    client.reset()?;
    // keep credentials (e.g. the forwarded Authorization header) out of MockServer's own log
    client.update_configuration(r#"{"redactSecretsInLog": true}"#)?;
    client.upsert(&[Expectation::new(HttpRequest::new().path("/mcp")).override_forwarded_request(
        HttpOverrideForwardedRequest::new().request_override(HttpRequest::new()
            .socket_address(SocketAddress::https(UPSTREAM_HOST, 443))
            .header("Host", UPSTREAM_HOST)))])?;
    println!("capture: forwarding /mcp to https://{UPSTREAM_HOST}/mcp");
    let (server, tools, search, fetch) = run_session(&mut McpClient::new(mcp_url, std::env::var("PARALLEL_API_KEY").ok())?)?;
    check(!fetch.is_empty(), "web_fetch returned no results during capture")?;
    println!("capture: {server}, tools {tools:?}, {} search results, fetched {}", search.len(), fetch[0]["url"].as_str().unwrap_or_default());
    // MockServer records every proxied exchange as an expectation; rewrite each one for replay
    let recorded = retrieve_recorded(mcp_url)?;
    let expectations: Vec<Expectation> = recorded.iter().map(replay_expectation).collect();
    println!("capture: recorded {} exchanges -> {} replay expectations", recorded.len(), expectations.len());
    Ok(expectations)
}

fn replay(client: &MockServerClient, mcp_url: &str, expectations: &[Expectation], source: &str) -> Res<()> {
    client.reset()?; // drops the forward expectation and the recorded log
    client.upsert(expectations)?;
    println!("replay: loaded {} expectations from {source}", expectations.len());
    let (server, tools, search, fetch) = run_session(&mut McpClient::new(mcp_url, None)?)?;
    check(["web_search", "web_fetch"].iter().all(|t| tools.iter().any(|n| n == t)), format!("tools/list returned {tools:?}"))?;
    check(!search.is_empty() && search.iter().all(|r| r["url"].as_str().is_some_and(|u| !u.is_empty())), "web_search returned no results")?;
    let excerpt = fetch.first().and_then(|f| f["excerpts"][0].as_str()).unwrap_or_default();
    check(!excerpt.is_empty(), "web_fetch returned no content")?;
    // only proxied traffic is recorded
    let forwarded = retrieve_recorded(mcp_url)?;
    check(forwarded.is_empty(), format!("{} requests were forwarded upstream during replay", forwarded.len()))?;
    println!("replay: {server}, tools {tools:?}");
    println!("replay: web_search -> {} results, first {}", search.len(), search[0]["url"].as_str().unwrap_or_default());
    println!("replay: web_fetch -> {} ({} chars)", fetch[0]["url"].as_str().unwrap_or_default(), excerpt.chars().count());
    println!("replay: 0 requests forwarded to {UPSTREAM_HOST} -- answered from recorded fixtures");
    Ok(())
}

fn run() -> Res<()> {
    let host = std::env::var("MOCKSERVER_HOST").unwrap_or_else(|_| "localhost".to_string());
    let port: u16 = std::env::var("MOCKSERVER_PORT").ok().and_then(|p| p.parse().ok()).unwrap_or(1080);
    let mcp_url = format!("http://{host}:{port}/mcp");
    let client = ClientBuilder::new(host, port).build()?;
    if std::env::var("MCP_CAPTURE").as_deref() == Ok("true") {
        let expectations = capture(&client, &mcp_url)?;
        replay(&client, &mcp_url, &expectations, "the live capture")
    } else {
        let fixture: Vec<Expectation> = serde_json::from_str(&std::fs::read_to_string(FIXTURE)?)?;
        replay(&client, &mcp_url, &fixture, "replay_expectations.json")
    }
}

fn main() {
    if let Err(e) = run() {
        eprintln!("FAILED: {e}");
        std::process::exit(1);
    }
}
