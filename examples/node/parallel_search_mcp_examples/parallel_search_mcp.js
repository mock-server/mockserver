/*
 * MockServer Node client -- capture and replay a remote MCP server.
 *
 * Records an MCP (Model Context Protocol) client's JSON-RPC calls to the live
 * Parallel Search MCP server (https://search.parallel.ai/mcp) through MockServer,
 * then replays them offline from expectations that match on the JSON-RPC method
 * and echo the caller's JSON-RPC id with a Velocity template.
 *
 *   node parallel_search_mcp.js                    # replay the shared fixture (offline)
 *   MCP_CAPTURE=true node parallel_search_mcp.js   # capture live, then replay the capture
 *
 * Run against a MockServer on localhost:1080 (override with MOCKSERVER_HOST /
 * MOCKSERVER_PORT). An optional PARALLEL_API_KEY is sent upstream during capture.
 */
var fs = require('fs');
var path = require('path');
var mockServerClient = require('mockserver-client').mockServerClient;

var MOCK_HOST = process.env.MOCKSERVER_HOST || 'localhost';
var MOCK_PORT = parseInt(process.env.MOCKSERVER_PORT || '1080', 10);
var MCP_URL = 'http://' + MOCK_HOST + ':' + MOCK_PORT + '/mcp';
var UPSTREAM_HOST = 'search.parallel.ai';
var FIXTURE = path.resolve(__dirname, '..', '..', 'json', 'parallel_search_mcp', 'replay_expectations.json');

function check(condition, message) {
    if (!condition) {
        throw new Error('FAILED: ' + message);
    }
}

// Minimal MCP Streamable HTTP client: one JSON-RPC message per POST.
function McpClient(url, apiKey) {
    var nextId = 1;

    async function send(method, params, notification) {
        var message = {jsonrpc: '2.0', method: method};
        if (params !== undefined) {
            message.params = params;
        }
        if (!notification) {
            message.id = nextId++;
        }
        // an explicit User-Agent: Parallel's CDN rejects some library defaults
        var headers = {
            'Content-Type': 'application/json',
            'Accept': 'application/json, text/event-stream',
            'User-Agent': 'mockserver-example/1.0'
        };
        if (apiKey) {
            headers['Authorization'] = 'Bearer ' + apiKey;
        }
        var response = await fetch(url, {
            method: 'POST', headers: headers, body: JSON.stringify(message), signal: AbortSignal.timeout(120000)
        });
        var body = await response.text();
        check(response.ok, method + ': HTTP ' + response.status + ' ' + body);
        if (notification) {
            return null;
        }
        var reply = JSON.parse(body);
        check(reply.id === message.id, method + ': id ' + reply.id + ' != ' + message.id);
        return reply.result;
    }

    async function callTool(name, args) {
        var result = await send('tools/call', {name: name, arguments: args});
        return JSON.parse(result.content[0].text); // Parallel returns its payload as JSON text
    }

    return {send: send, callTool: callTool};
}

// The application under test: initialize, list tools, search, then fetch.
async function runSession(mcp) {
    var init = await mcp.send('initialize', {
        protocolVersion: '2025-06-18', capabilities: {},
        clientInfo: {name: 'mockserver-example', version: '1.0.0'}
    });
    await mcp.send('notifications/initialized', undefined, true);
    var tools = (await mcp.send('tools/list', {})).tools.map(function (tool) {
        return tool.name;
    });
    var search = await mcp.callTool('web_search', {
        objective: 'What is MockServer?', search_queries: ['MockServer mock HTTP server']
    });
    var fetched = await mcp.callTool('web_fetch', {
        urls: ['https://www.mock-server.com/'], objective: 'What is MockServer?'
    });
    return {server: init.serverInfo.name, tools: tools, search: search.results, fetch: fetched.results};
}

// MockServer records a JSON body as a string, or as {"json": ...} / {"string": ...}.
function bodyJson(body) {
    if (body && typeof body === 'object') {
        body = 'json' in body ? body.json : ('string' in body ? body.string : body);
    }
    return typeof body === 'string' ? (body ? JSON.parse(body) : null) : body;
}

// Turn one recorded JSON-RPC exchange into a replay expectation that echoes the caller's id.
function replayExpectation(recorded) {
    var requestBody = bodyJson(recorded.httpRequest.body);
    var statusCode = recorded.httpResponse.statusCode || 200;
    var responseBody = bodyJson(recorded.httpResponse.body);
    var method = requestBody.method;
    var matcher = method === 'tools/call'
        ? {type: 'JSON_PATH', jsonPath: "$[?(@.method == 'tools/call' && @.params.name == '" + requestBody.params.name + "')]"}
        : {type: 'JSON_RPC', method: method};
    var request = {method: 'POST', path: '/mcp', body: matcher};
    if (!responseBody) { // a notification: replay the bare status (202 Accepted)
        return {httpRequest: request, httpResponse: {statusCode: statusCode}};
    }
    var key = 'result' in responseBody ? 'result' : 'error';
    // escape Velocity's $, # and \ so recorded text (e.g. markdown "#") renders literally;
    // "\" goes last because the $ and # replacements contain none, while its own contains a "$"
    var recordedJson = JSON.stringify(responseBody[key])
        .replace(/\$/g, '${esc.d}').replace(/#/g, '${esc.h}').replace(/\\/g, '${esc.b}');
    var template = '{"statusCode": ' + statusCode + ', "headers": [{"name": "Content-Type", "values": ["application/json"]}], ' +
        '"body": {"jsonrpc": "2.0", "id": $!{request.jsonRpcRawId}, "' + key + '": ' + recordedJson + '}}';
    return {httpRequest: request, httpResponseTemplate: {templateType: 'VELOCITY', template: template}};
}

async function capture(client) {
    await client.reset();
    // keep credentials (e.g. the forwarded Authorization header) out of MockServer's own log
    await client.updateConfiguration({redactSecretsInLog: true});
    await client.mockAnyResponse({
        httpRequest: {path: '/mcp'},
        httpOverrideForwardedRequest: {
            requestOverride: {
                headers: {Host: [UPSTREAM_HOST]},
                socketAddress: {host: UPSTREAM_HOST, port: 443, scheme: 'HTTPS'}
            }
        }
    });
    console.log('capture: forwarding /mcp to https://' + UPSTREAM_HOST + '/mcp');
    var session = await runSession(McpClient(MCP_URL, process.env.PARALLEL_API_KEY));
    console.log('capture: ' + session.server + ', tools [' + session.tools.join(', ') + '], ' +
        session.search.length + ' search results, fetched ' + session.fetch[0].url);
    // MockServer records every proxied exchange as an expectation; rewrite each one for replay
    var recorded = (await client.retrieveRecordedExpectations({path: '/mcp'})) || [];
    var expectations = recorded.map(replayExpectation);
    console.log('capture: recorded ' + recorded.length + ' exchanges -> ' + expectations.length + ' replay expectations');
    return expectations;
}

async function replay(client, expectations, source) {
    await client.reset(); // drops the forward expectation and the recorded log
    await client.mockAnyResponse(expectations);
    console.log('replay: loaded ' + expectations.length + ' expectations from ' + source);
    var session = await runSession(McpClient(MCP_URL));
    check(['web_search', 'web_fetch'].every(function (name) {
        return session.tools.indexOf(name) >= 0;
    }), 'tools/list returned ' + JSON.stringify(session.tools));
    check(session.search && session.search.length && session.search.every(function (result) {
        return result.url;
    }), 'web_search returned no results');
    check(session.fetch && session.fetch.length && session.fetch[0].excerpts[0], 'web_fetch returned no content');
    var forwarded = (await client.retrieveRecordedExpectations({path: '/mcp'})) || []; // only proxied traffic is recorded
    check(forwarded.length === 0, forwarded.length + ' requests were forwarded upstream during replay');
    var excerpt = session.fetch[0].excerpts[0];
    console.log('replay: ' + session.server + ', tools [' + session.tools.join(', ') + ']');
    console.log('replay: web_search -> ' + session.search.length + ' results, first ' + session.search[0].url);
    console.log('replay: web_fetch -> ' + session.fetch[0].url + ' (' + Array.from(excerpt).length + ' chars)');
    console.log('replay: 0 requests forwarded to ' + UPSTREAM_HOST + ' -- answered from recorded fixtures');
}

async function main() {
    var client = mockServerClient(MOCK_HOST, MOCK_PORT);
    if (process.env.MCP_CAPTURE === 'true') {
        await replay(client, await capture(client), 'the live capture');
    } else {
        var fixture = JSON.parse(fs.readFileSync(FIXTURE, 'utf8'));
        await replay(client, fixture, path.basename(FIXTURE));
    }
}

main().catch(function (error) {
    console.error(error && error.message ? error.message : error);
    process.exit(1);
});
