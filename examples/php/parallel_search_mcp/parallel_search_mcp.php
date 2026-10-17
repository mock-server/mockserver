<?php

declare(strict_types=1);

// Capture and replay a remote MCP server with MockServer.
//
// Records an MCP (Model Context Protocol) client's JSON-RPC calls to the live
// Parallel Search MCP server (https://search.parallel.ai/mcp) through MockServer,
// then replays them offline from expectations that match on the JSON-RPC method
// and echo the caller's JSON-RPC id with a Velocity template.
//
//   php parallel_search_mcp.php                    # replay the shared fixture (offline)
//   MCP_CAPTURE=true php parallel_search_mcp.php   # capture live, then replay the capture
//
// Run against a MockServer on localhost:1080 (override with MOCKSERVER_HOST /
// MOCKSERVER_PORT). An optional PARALLEL_API_KEY is sent upstream during capture.

require_once __DIR__ . '/../../../mockserver-client-php/vendor/autoload.php';

use MockServer\HttpRequest;
use MockServer\MockServerClient;

$host = getenv('MOCKSERVER_HOST') ?: 'localhost';
$port = (int) (getenv('MOCKSERVER_PORT') ?: '1080');
const UPSTREAM_HOST = 'search.parallel.ai';
define('BASE_URL', "http://{$host}:{$port}");
define('FIXTURE', __DIR__ . '/../../json/parallel_search_mcp/replay_expectations.json');

function check(bool $condition, string $message): void
{
    if (!$condition) {
        fwrite(STDERR, "FAILED: {$message}\n");
        exit(1);
    }
}

/** Send a body with curl; returns [status, body]. */
function http(string $method, string $url, string $body, array $headers): array
{
    $ch = curl_init($url);
    curl_setopt_array($ch, [
        CURLOPT_CUSTOMREQUEST => $method,
        CURLOPT_POSTFIELDS => $body,
        CURLOPT_HTTPHEADER => $headers,
        CURLOPT_RETURNTRANSFER => true,
        CURLOPT_TIMEOUT => 120,
    ]);
    $response = curl_exec($ch);
    check($response !== false, "{$method} {$url}: " . curl_error($ch));
    return [curl_getinfo($ch, CURLINFO_HTTP_CODE), $response];
}

/** Raw control-plane call: the PHP client has no typed override-forward or JSON_RPC body builders. */
function controlPlane(string $path, mixed $payload): string
{
    [$status, $body] = http('PUT', BASE_URL . $path, json_encode($payload, JSON_THROW_ON_ERROR),
        ['Content-Type: application/json']);
    check($status < 400, "PUT {$path} returned {$status}: {$body}");
    return $body;
}

/** Minimal MCP Streamable HTTP client: one JSON-RPC message per POST. */
final class McpClient
{
    private int $nextId = 1;

    public function __construct(private string $url, private ?string $apiKey = null)
    {
    }

    public function send(string $method, ?object $params = null, bool $notification = false): ?object
    {
        $message = ['jsonrpc' => '2.0', 'method' => $method];
        if ($params !== null) {
            $message['params'] = $params;
        }
        if (!$notification) {
            $message['id'] = $this->nextId++;
        }
        // an explicit User-Agent: Parallel's CDN rejects some library defaults
        $headers = ['Content-Type: application/json', 'Accept: application/json, text/event-stream',
            'User-Agent: mockserver-example/1.0'];
        if ($this->apiKey) {
            $headers[] = "Authorization: Bearer {$this->apiKey}";
        }
        [$status, $body] = http('POST', $this->url, json_encode($message, JSON_THROW_ON_ERROR), $headers);
        check($status < 400, "{$method}: HTTP {$status}");
        if ($notification) {
            return null;
        }
        $reply = json_decode($body, false, 512, JSON_THROW_ON_ERROR);
        check(($reply->id ?? null) === $message['id'], "{$method}: id " . json_encode($reply->id ?? null) . " != {$message['id']}");
        check(property_exists($reply, 'result'), "{$method}: " . json_encode($reply->error ?? $reply));
        return $reply->result;
    }

    public function callTool(string $name, array $arguments): object
    {
        $result = $this->send('tools/call', (object) ['name' => $name, 'arguments' => $arguments]);
        return json_decode($result->content[0]->text, false, 512, JSON_THROW_ON_ERROR); // payload is JSON text
    }
}

/** The application under test: initialize, list tools, search, then fetch. */
function runSession(McpClient $mcp): array
{
    $init = $mcp->send('initialize', (object) ['protocolVersion' => '2025-06-18', 'capabilities' => new stdClass(),
        'clientInfo' => ['name' => 'mockserver-example', 'version' => '1.0.0']]);
    $mcp->send('notifications/initialized', null, true);
    $tools = array_map(fn ($tool) => $tool->name, $mcp->send('tools/list', new stdClass())->tools);
    $search = $mcp->callTool('web_search', ['objective' => 'What is MockServer?',
        'search_queries' => ['MockServer mock HTTP server']]);
    $fetch = $mcp->callTool('web_fetch', ['urls' => ['https://www.mock-server.com/'],
        'objective' => 'What is MockServer?']);
    return [$init->serverInfo->name, $tools, $search->results, $fetch->results];
}

/** MockServer records a JSON body as a string, or as {"json": ...} / {"string": ...}. */
function bodyJson(mixed $body): mixed
{
    if (is_object($body)) {
        $body = $body->json ?? $body->string ?? $body;
    }
    return is_string($body) && $body !== '' ? json_decode($body, false, 512, JSON_THROW_ON_ERROR) : ($body ?: null);
}

/** Turn one recorded JSON-RPC exchange into a replay expectation that echoes the caller's id. */
function replayExpectation(object $recorded): array
{
    $requestBody = bodyJson($recorded->httpRequest->body);
    $statusCode = $recorded->httpResponse->statusCode ?? 200;
    $responseBody = bodyJson($recorded->httpResponse->body ?? null);
    $method = $requestBody->method;
    $matcher = $method === 'tools/call'
        ? ['type' => 'JSON_PATH', 'jsonPath' => "\$[?(@.method == 'tools/call' && @.params.name == '{$requestBody->params->name}')]"]
        : ['type' => 'JSON_RPC', 'method' => $method];
    $request = ['method' => 'POST', 'path' => '/mcp', 'body' => $matcher];
    if (empty($responseBody)) { // a notification: replay the bare status (202 Accepted)
        return ['httpRequest' => $request, 'httpResponse' => ['statusCode' => $statusCode]];
    }
    $key = property_exists($responseBody, 'result') ? 'result' : 'error';
    // escape Velocity metacharacters so recorded text (e.g. markdown "#") renders literally;
    // "\" goes last because the $ and # replacements contain none, while its own contains a "$"
    $json = str_replace(['$', '#', '\\'], ['${esc.d}', '${esc.h}', '${esc.b}'],
        json_encode($responseBody->$key, JSON_THROW_ON_ERROR | JSON_UNESCAPED_SLASHES));
    $template = sprintf('{"statusCode": %d, "headers": [{"name": "Content-Type", "values": ["application/json"]}], '
        . '"body": {"jsonrpc": "2.0", "id": $!{request.jsonRpcRawId}, "%s": %s}}', $statusCode, $key, $json);
    return ['httpRequest' => $request, 'httpResponseTemplate' => ['templateType' => 'VELOCITY', 'template' => $template]];
}

function capture(MockServerClient $client): array
{
    $client->reset();
    // keep credentials (e.g. the forwarded Authorization header) out of MockServer's own log
    $client->updateConfiguration('{"redactSecretsInLog": true}');
    controlPlane('/mockserver/expectation', [[
        'httpRequest' => ['path' => '/mcp'],
        'httpOverrideForwardedRequest' => ['requestOverride' => [
            'headers' => ['Host' => [UPSTREAM_HOST]],
            'socketAddress' => ['host' => UPSTREAM_HOST, 'port' => 443, 'scheme' => 'HTTPS'],
        ]],
    ]]);
    echo 'capture: forwarding /mcp to https://' . UPSTREAM_HOST . "/mcp\n";
    [$server, $tools, $search, $fetch] = runSession(new McpClient(BASE_URL . '/mcp', getenv('PARALLEL_API_KEY') ?: null));
    printf("capture: %s, tools [%s], %d search results, fetched %s\n", $server, implode(', ', $tools), count($search), $fetch[0]->url);
    // MockServer records every proxied exchange as an expectation; rewrite each one for replay.
    // Raw REST (not retrieveRecordedExpectations) so JSON objects stay objects: {} must not become [].
    $recorded = json_decode(controlPlane('/mockserver/retrieve?type=RECORDED_EXPECTATIONS&format=JSON', ['path' => '/mcp']),
        false, 512, JSON_THROW_ON_ERROR);
    $expectations = array_map('replayExpectation', $recorded);
    printf("capture: recorded %d exchanges -> %d replay expectations\n", count($recorded), count($expectations));
    return $expectations;
}

function replay(MockServerClient $client, array $expectations, string $source): void
{
    $client->reset(); // drops the forward expectation and the recorded log
    controlPlane('/mockserver/expectation', $expectations);
    printf("replay: loaded %d expectations from %s\n", count($expectations), $source);
    [$server, $tools, $search, $fetch] = runSession(new McpClient(BASE_URL . '/mcp'));
    check(!array_diff(['web_search', 'web_fetch'], $tools), 'tools/list returned [' . implode(', ', $tools) . ']');
    check($search && !array_filter($search, fn ($result) => empty($result->url)), 'web_search returned no results');
    check($fetch && !empty($fetch[0]->excerpts[0]), 'web_fetch returned no content');
    $forwarded = $client->retrieveRecordedExpectations(HttpRequest::request()->path('/mcp')); // only proxied traffic is recorded
    check(!$forwarded, count($forwarded) . ' requests were forwarded upstream during replay');
    printf("replay: %s, tools [%s]\n", $server, implode(', ', $tools));
    printf("replay: web_search -> %d results, first %s\n", count($search), $search[0]->url);
    printf("replay: web_fetch -> %s (%d chars)\n", $fetch[0]->url, mb_strlen($fetch[0]->excerpts[0], 'UTF-8'));
    echo 'replay: 0 requests forwarded to ' . UPSTREAM_HOST . " -- answered from recorded fixtures\n";
}

$client = new MockServerClient($host, $port);
if (getenv('MCP_CAPTURE') === 'true') {
    replay($client, capture($client), 'the live capture');
} else {
    replay($client, json_decode(file_get_contents(FIXTURE), false, 512, JSON_THROW_ON_ERROR), basename(FIXTURE));
}
