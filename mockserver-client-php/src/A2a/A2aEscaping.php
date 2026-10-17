<?php

declare(strict_types=1);

namespace MockServer\A2a;

use MockServer\Mcp\McpEscaping;

/**
 * Escaping/serialisation helpers ported 1:1 from the Java
 * {@code org.mockserver.client.A2aMockBuilder} so the produced template and
 * matcher strings are byte-identical across clients.
 *
 * @internal
 */
final class A2aEscaping
{
    private function __construct()
    {
    }

    /**
     * JSON-escape a string for inlining inside a JSON string literal, returning
     * the contents WITHOUT the surrounding quotes. Mirrors Jackson's
     * {@code writeValueAsString(value)} then stripping the outer quotes (and the
     * Java builder's null-to-empty-string fallback).
     */
    public static function escapeJson(?string $value): string
    {
        if ($value === null || $value === '') {
            return '';
        }
        $quoted = json_encode($value, JSON_THROW_ON_ERROR | JSON_UNESCAPED_SLASHES | JSON_UNESCAPED_UNICODE);
        return substr($quoted, 1, -1);
    }

    /**
     * Escape Velocity meta-characters so literal {@code $} and {@code #} in mock
     * content are not interpreted as Velocity references/directives. Only applied
     * to text that flows through a Velocity-templated response body. Shares the
     * MCP rules, including backslash runs before {@code $}, {@code #} or the end.
     */
    public static function escapeVelocity(?string $value): ?string
    {
        return McpEscaping::escapeVelocity($value);
    }

    /**
     * Build the Velocity template string that renders a JSON-RPC 2.0 success
     * response wrapping the supplied result JSON. The id is echoed back from the
     * inbound request via the {@code jsonRpcRawId} binding.
     */
    public static function velocityJsonRpcResponse(string $resultJson): string
    {
        return '{"statusCode": 200, '
            . '"headers": [{"name": "Content-Type", "values": ["application/json"]}], '
            . '"body": {"jsonrpc": "2.0", "result": ' . $resultJson . ', "id": $!{request.jsonRpcRawId}}}';
    }

    /**
     * Parse a push-notification webhook URL into its host / port / scheme / path
     * components, mirroring the Java {@code WebhookTarget.parse}.
     *
     * @return array{host: string, port: int, secure: bool, path: string}
     */
    public static function parseWebhook(string $url): array
    {
        $parts = parse_url($url);
        if ($parts === false || !isset($parts['host']) || $parts['host'] === '') {
            throw new \InvalidArgumentException('Invalid push-notification webhook URL (no host): ' . $url);
        }
        $secure = isset($parts['scheme']) && strtolower($parts['scheme']) === 'https';
        $host = $parts['host'];
        $port = $parts['port'] ?? ($secure ? 443 : 80);
        $path = $parts['path'] ?? '';
        if ($path === '') {
            $path = '/';
        }
        return ['host' => $host, 'port' => (int) $port, 'secure' => $secure, 'path' => $path];
    }
}
