<?php

declare(strict_types=1);

namespace MockServer\Mcp;

use InvalidArgumentException;

/**
 * Escaping/serialisation helpers ported 1:1 from the Java/Node/Python
 * McpMockBuilder so the produced template strings are byte-identical.
 *
 * @internal
 */
final class McpEscaping
{
    private function __construct()
    {
    }

    /**
     * JSON-escape a string for inlining inside a JSON string literal, returning
     * the contents WITHOUT the surrounding quotes. Mirrors Jackson's
     * {@code writeValueAsString(value)} then stripping the outer quotes.
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
     * content are not interpreted as Velocity references/directives. Velocity
     * consumes backslashes written before a reference or directive, so a run of
     * backslashes followed by {@code $}, {@code #} or the end of the value is
     * escaped too; other backslashes are left as-is.
     */
    public static function escapeVelocity(?string $value): ?string
    {
        if ($value === null) {
            return null;
        }
        // group 2 consumes the metacharacter after a backslash run, is empty at the
        // end and unset (empty alternative) otherwise, so no lookaround is needed
        return preg_replace_callback(
            '/(\\\\+)(?:([$#]|\z)|)|[$#]/',
            static function (array $match): string {
                [$whole, $run, $following] = $match;
                if ($run === null) {
                    return self::velocityMetacharacterEscape($whole);
                }
                if ($following === null) {
                    return $run;
                }
                return str_repeat('${esc.b}', strlen($run)) . self::velocityMetacharacterEscape($following);
            },
            $value,
            flags: PREG_UNMATCHED_AS_NULL
        );
    }

    private static function velocityMetacharacterEscape(string $metacharacter): string
    {
        if ($metacharacter === '$') {
            return '${esc.d}';
        }
        if ($metacharacter === '#') {
            return '${esc.h}';
        }
        return '';
    }

    /**
     * Escape single quotes for safe inclusion inside a JSONPath string literal.
     */
    public static function escapeJsonPath(?string $value): string
    {
        if ($value === null) {
            return '';
        }
        return str_replace("'", "\\'", $value);
    }

    /**
     * Validate that the supplied string is valid JSON and return it re-serialised
     * in compact form. Throws on invalid JSON.
     */
    public static function validateAndSerializeJson(string $json): string
    {
        try {
            $parsed = json_decode($json, true, 512, JSON_THROW_ON_ERROR);
        } catch (\JsonException $e) {
            throw new InvalidArgumentException('Invalid JSON for inputSchema: ' . $e->getMessage(), 0, $e);
        }
        return json_encode($parsed, JSON_THROW_ON_ERROR | JSON_UNESCAPED_SLASHES | JSON_UNESCAPED_UNICODE);
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
}
