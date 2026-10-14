<?php

declare(strict_types=1);

namespace MockServer\Diagnostics;

/**
 * Recent requests that matched no expectation, each with its closest
 * expectations; returned by {@see \MockServer\MockServerClient::explainUnmatched()}.
 */
final class UnmatchedExplanation
{
    /**
     * @param string|null $correlationId The correlation ID of this analysis.
     * @param string|null $timestamp When the analysis was computed (ISO-8601).
     * @param int $unmatchedRequestCount How many unmatched requests are explained.
     * @param bool $truncated Whether the evaluation budget ran out before every request was explained.
     * @param list<UnmatchedRequest> $unmatchedRequests The unmatched requests.
     */
    public function __construct(
        public readonly ?string $correlationId,
        public readonly ?string $timestamp,
        public readonly int $unmatchedRequestCount,
        public readonly bool $truncated,
        public readonly array $unmatchedRequests,
    ) {
    }

    /**
     * @param array<string, mixed> $data A decoded {@code PUT /mockserver/explainUnmatched} response.
     */
    public static function fromArray(array $data): self
    {
        return new self(
            Fields::string($data, 'correlationId'),
            Fields::string($data, 'timestamp'),
            Fields::int($data, 'unmatchedRequestCount') ?? 0,
            Fields::bool($data, 'truncated') ?? false,
            array_values(array_map(
                static fn(array $item): UnmatchedRequest => UnmatchedRequest::fromArray($item),
                array_filter(Fields::list($data, 'unmatchedRequests'), 'is_array'),
            )),
        );
    }
}
