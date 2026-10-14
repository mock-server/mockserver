<?php

declare(strict_types=1);

namespace MockServer\Diagnostics;

/**
 * The closest non-matching expectation in a {@see MismatchDebugResult}.
 */
final class ClosestMatch
{
    /**
     * @param string|null $expectationId The expectation's id.
     * @param int $matchedFields How many fields matched.
     * @param int $totalFields How many fields were counted.
     */
    public function __construct(
        public readonly ?string $expectationId,
        public readonly int $matchedFields,
        public readonly int $totalFields,
    ) {
    }

    /** @param array<string, mixed> $data */
    public static function fromArray(array $data): self
    {
        return new self(
            Fields::string($data, 'expectationId'),
            Fields::int($data, 'matchedFields') ?? 0,
            Fields::int($data, 'totalFields') ?? 0,
        );
    }
}
