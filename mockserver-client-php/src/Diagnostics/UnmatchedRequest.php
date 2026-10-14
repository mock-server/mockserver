<?php

declare(strict_types=1);

namespace MockServer\Diagnostics;

/**
 * One unmatched request in an {@see UnmatchedExplanation}.
 */
final class UnmatchedRequest
{
    /**
     * @param string|null $timestamp When the request was recorded, as the event log formats it.
     * @param string|null $method The request method.
     * @param string|null $path The request path.
     * @param list<ExplainedExpectation> $closestExpectations The evaluated expectations, closest first.
     * @param int $totalExpectationsEvaluated How many expectations were evaluated for this request.
     */
    public function __construct(
        public readonly ?string $timestamp,
        public readonly ?string $method,
        public readonly ?string $path,
        public readonly array $closestExpectations,
        public readonly int $totalExpectationsEvaluated,
    ) {
    }

    /** @param array<string, mixed> $data */
    public static function fromArray(array $data): self
    {
        return new self(
            Fields::string($data, 'timestamp'),
            Fields::string($data, 'method'),
            Fields::string($data, 'path'),
            Fields::expectations($data, 'closestExpectations'),
            Fields::int($data, 'totalExpectationsEvaluated') ?? 0,
        );
    }
}
