// MockServer .NET client -- capture and replay a remote MCP server.
//
// Records an MCP (Model Context Protocol) client's JSON-RPC calls to the live
// Parallel Search MCP server (https://search.parallel.ai/mcp) through MockServer,
// then replays them offline from expectations that match on the JSON-RPC method
// and echo the caller's JSON-RPC id with a Velocity template.
//
//   dotnet run                      # replay the shared fixture (offline)
//   MCP_CAPTURE=true dotnet run     # capture live, then replay the capture
//
// Run against a MockServer on localhost:1080 (override with MOCKSERVER_HOST /
// MOCKSERVER_PORT). An optional PARALLEL_API_KEY is sent upstream during capture.

using System.Net.Http.Headers;
using System.Runtime.CompilerServices;
using System.Text;
using System.Text.Json;
using System.Text.Json.Nodes;
using MockServer.Client;
using MockServer.Client.Models;
using static Example;

var host = Environment.GetEnvironmentVariable("MOCKSERVER_HOST") is { Length: > 0 } h ? h : "localhost";
var port = int.TryParse(Environment.GetEnvironmentVariable("MOCKSERVER_PORT"), out var p) ? p : 1080;
var mcpUrl = $"http://{host}:{port}/mcp";
const string UpstreamHost = "search.parallel.ai";
var fixture = Path.GetFullPath(Path.Combine(SourceDir(), "..", "..", "json", "parallel_search_mcp", "replay_expectations.json"));

using var client = new MockServerClient(host, port);
if (Environment.GetEnvironmentVariable("MCP_CAPTURE") == "true")
{
    await Replay(await Capture(), "the live capture");
}
else
{
    await Replay(JsonSerializer.Deserialize<List<Expectation>>(File.ReadAllText(fixture))!, Path.GetFileName(fixture));
}

async Task<List<Expectation>> Capture()
{
    client.Reset();
    // keep credentials (e.g. the forwarded Authorization header) out of MockServer's own log
    client.UpdateConfiguration("{\"redactSecretsInLog\": true}");
    var requestOverride = HttpRequest.Request().WithHeader("Host", UpstreamHost).Build();
    requestOverride.SocketAddress = new SocketAddress { Host = UpstreamHost, Port = 443, Scheme = SocketScheme.HTTPS };
    client.Upsert(new Expectation
    {
        HttpRequest = HttpRequest.Request().WithPath("/mcp"),
        HttpOverrideForwardedRequest = new HttpOverrideForwardedRequest { HttpRequest = requestOverride }
    });
    Console.WriteLine($"capture: forwarding /mcp to https://{UpstreamHost}/mcp");
    var (server, tools, search, fetch) = await RunSession(new McpClient(mcpUrl, Environment.GetEnvironmentVariable("PARALLEL_API_KEY")));
    Console.WriteLine($"capture: {server}, tools [{string.Join(", ", tools)}], {search.Count} search results, fetched {fetch[0]?["url"]}");
    // MockServer records every proxied exchange as an expectation; rewrite each one for replay
    var recorded = client.RetrieveRecordedExpectations(HttpRequest.Request().WithPath("/mcp"));
    var expectations = recorded.Select(ReplayExpectation).ToList();
    Console.WriteLine($"capture: recorded {recorded.Count} exchanges -> {expectations.Count} replay expectations");
    return expectations;
}

async Task Replay(List<Expectation> expectations, string source)
{
    client.Reset(); // drops the forward expectation and the recorded log
    client.Upsert(expectations.ToArray());
    Console.WriteLine($"replay: loaded {expectations.Count} expectations from {source}");
    var (server, tools, search, fetch) = await RunSession(new McpClient(mcpUrl));
    Check(tools.Contains("web_search") && tools.Contains("web_fetch"), $"tools/list returned [{string.Join(", ", tools)}]");
    Check(search.Count > 0 && search.All(r => !string.IsNullOrEmpty((string?)r?["url"])), "web_search returned no results");
    var excerpt = fetch.Count > 0 ? (string?)fetch[0]?["excerpts"]?[0] : null;
    Check(!string.IsNullOrEmpty(excerpt), "web_fetch returned no content");
    var forwarded = client.RetrieveRecordedExpectations(HttpRequest.Request().WithPath("/mcp")); // only proxied traffic is recorded
    Check(forwarded.Count == 0, $"{forwarded.Count} requests were forwarded upstream during replay");
    Console.WriteLine($"replay: {server}, tools [{string.Join(", ", tools)}]");
    Console.WriteLine($"replay: web_search -> {search.Count} results, first {search[0]?["url"]}");
    // count code points, not UTF-16 units, so the figure matches other languages' character counts
    Console.WriteLine($"replay: web_fetch -> {fetch[0]?["url"]} ({excerpt!.EnumerateRunes().Count()} chars)");
    Console.WriteLine($"replay: 0 requests forwarded to {UpstreamHost} -- answered from recorded fixtures");
}

// The application under test: initialize, list tools, search, then fetch.
static async Task<(string Server, List<string> Tools, JsonArray Search, JsonArray Fetch)> RunSession(McpClient mcp)
{
    var init = await mcp.Send("initialize", new JsonObject
    {
        ["protocolVersion"] = "2025-06-18",
        ["capabilities"] = new JsonObject(),
        ["clientInfo"] = new JsonObject { ["name"] = "mockserver-example", ["version"] = "1.0.0" }
    });
    await mcp.Send("notifications/initialized", notification: true);
    var tools = (await mcp.Send("tools/list", new JsonObject()))!["tools"]!.AsArray().Select(t => (string)t!["name"]!).ToList();
    var search = await mcp.CallTool("web_search", new JsonObject
    {
        ["objective"] = "What is MockServer?",
        ["search_queries"] = new JsonArray("MockServer mock HTTP server")
    });
    var fetch = await mcp.CallTool("web_fetch", new JsonObject
    {
        ["urls"] = new JsonArray("https://www.mock-server.com/"),
        ["objective"] = "What is MockServer?"
    });
    return ((string)init!["serverInfo"]!["name"]!, tools, search["results"]!.AsArray(), fetch["results"]!.AsArray());
}

// MockServer records a JSON body as a string, or as {"json": ...} / {"string": ...}.
static JsonNode? BodyJson(object? body)
{
    var node = body is null ? null : JsonSerializer.SerializeToNode(body);
    if (node is JsonObject obj) node = obj["json"] ?? obj["string"] ?? obj;
    if (node is JsonValue value && value.TryGetValue<string>(out var text))
        return string.IsNullOrEmpty(text) ? null : JsonNode.Parse(text);
    return node;
}

// Turn one recorded JSON-RPC exchange into a replay expectation that echoes the caller's id.
static Expectation ReplayExpectation(Expectation recorded)
{
    var requestBody = BodyJson(recorded.HttpRequest?.Body)!;
    var statusCode = recorded.HttpResponse?.StatusCode ?? 200;
    var responseBody = BodyJson(recorded.HttpResponse?.Body);
    var method = (string)requestBody["method"]!;
    var matcher = method == "tools/call"
        ? Body.OfJsonPath($"$[?(@.method == 'tools/call' && @.params.name == '{requestBody["params"]!["name"]}')]")
        : new Body { Type = "JSON_RPC", Method = method };
    var request = new HttpRequest { Method = "POST", Path = "/mcp", Body = matcher };
    if (responseBody is not JsonObject { Count: > 0 } response) // a notification: replay the bare status (202 Accepted)
        return new Expectation { HttpRequest = request, HttpResponse = HttpResponse.Response().WithStatusCode(statusCode) };
    var key = response.ContainsKey("result") ? "result" : "error";
    // escape Velocity metacharacters so recorded text (e.g. markdown "#") renders literally
    // "\" goes last because the $ and # replacements contain none, while its own contains a "$"
    var recordedJson = response[key]!.ToJsonString().Replace("$", "${esc.d}").Replace("#", "${esc.h}").Replace("\\", "${esc.b}");
    var template = "{\"statusCode\": " + statusCode + ", \"headers\": [{\"name\": \"Content-Type\", \"values\": [\"application/json\"]}], "
        + "\"body\": {\"jsonrpc\": \"2.0\", \"id\": $!{request.jsonRpcRawId}, \"" + key + "\": " + recordedJson + "}}";
    return new Expectation { HttpRequest = request, HttpResponseTemplate = HttpTemplate.OfType(TemplateType.VELOCITY).WithTemplate(template) };
}

static class Example
{
    public static void Check(bool condition, string message)
    {
        if (condition) return;
        Console.Error.WriteLine($"FAILED: {message}");
        Environment.Exit(1);
    }

    public static string SourceDir([CallerFilePath] string path = "") => Path.GetDirectoryName(path)!;
}

// Minimal MCP Streamable HTTP client: one JSON-RPC message per POST.
sealed class McpClient(string url, string? apiKey = null)
{
    private static readonly HttpClient Http = new() { Timeout = TimeSpan.FromSeconds(120) };
    private int _nextId = 1;

    public async Task<JsonNode?> Send(string method, JsonNode? @params = null, bool notification = false)
    {
        var message = new JsonObject { ["jsonrpc"] = "2.0", ["method"] = method };
        if (@params is not null) message["params"] = @params;
        var id = notification ? (int?)null : _nextId++;
        if (id is not null) message["id"] = id;
        using var request = new HttpRequestMessage(HttpMethod.Post, url) { Content = new StringContent(message.ToJsonString(), Encoding.UTF8) };
        request.Content.Headers.ContentType = new MediaTypeHeaderValue("application/json");
        request.Headers.Accept.ParseAdd("application/json");
        request.Headers.Accept.ParseAdd("text/event-stream");
        // an explicit User-Agent: Parallel's CDN rejects some library defaults
        request.Headers.UserAgent.ParseAdd("mockserver-example/1.0");
        if (!string.IsNullOrEmpty(apiKey)) request.Headers.Authorization = new AuthenticationHeaderValue("Bearer", apiKey);
        using var response = await Http.SendAsync(request);
        response.EnsureSuccessStatusCode();
        var body = await response.Content.ReadAsStringAsync();
        if (notification) return null;
        var reply = JsonNode.Parse(body)!;
        Check(reply["id"]?.ToJsonString() == id.ToString(), $"{method}: id {reply["id"]?.ToJsonString() ?? "None"} != {id}");
        Check(reply["result"] is not null, $"{method}: no result in {body}");
        return reply["result"];
    }

    public async Task<JsonNode> CallTool(string name, JsonObject arguments)
    {
        var result = await Send("tools/call", new JsonObject { ["name"] = name, ["arguments"] = arguments });
        return JsonNode.Parse((string)result!["content"]![0]!["text"]!)!; // Parallel returns its payload as JSON text
    }
}
