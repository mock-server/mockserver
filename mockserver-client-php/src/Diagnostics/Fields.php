<?php

declare(strict_types=1);

namespace MockServer\Diagnostics;

/**
 * Typed reads of a decoded diagnostic response: a field of the wrong type or
 * absent reads as null (or the given default), never as a coerced value.
 *
 * @internal
 */
final class Fields
{
    private function __construct()
    {
    }

    /** @param array<string, mixed> $data */
    public static function string(array $data, string $key): ?string
    {
        return isset($data[$key]) && is_string($data[$key]) ? $data[$key] : null;
    }

    /** @param array<string, mixed> $data */
    public static function int(array $data, string $key): ?int
    {
        return isset($data[$key]) && is_int($data[$key]) ? $data[$key] : null;
    }

    /** @param array<string, mixed> $data */
    public static function bool(array $data, string $key): ?bool
    {
        return isset($data[$key]) && is_bool($data[$key]) ? $data[$key] : null;
    }

    /**
     * @param array<string, mixed> $data
     * @return array<string, mixed>|null
     */
    public static function object(array $data, string $key): ?array
    {
        return isset($data[$key]) && is_array($data[$key]) ? $data[$key] : null;
    }

    /**
     * @param array<string, mixed> $data
     * @return list<mixed>
     */
    public static function list(array $data, string $key): array
    {
        return isset($data[$key]) && is_array($data[$key]) ? array_values($data[$key]) : [];
    }

    /**
     * @param array<string, mixed> $data
     * @return list<ExplainedExpectation>
     */
    public static function expectations(array $data, string $key): array
    {
        return array_values(array_map(
            static fn(array $item): ExplainedExpectation => ExplainedExpectation::fromArray($item),
            array_filter(self::list($data, $key), 'is_array'),
        ));
    }
}
