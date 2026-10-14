<?php

declare(strict_types=1);

namespace MockServer\Diagnostics;

/**
 * How a request compares with each active expectation; returned by
 * {@see \MockServer\MockServerClient::debugMismatch()}.
 */
final class MismatchDebugResult
{
    /**
     * @param string|null $correlationId The correlation ID of this analysis.
     * @param string|null $timestamp When the analysis was computed (ISO-8601).
     * @param int $totalExpectations How many expectations are stored.
     * @param int $evaluatedExpectations How many were evaluated.
     * @param bool $truncated Whether the evaluation budget ran out before every expectation was evaluated.
     * @param int|null $maxExpectationsEvaluated The evaluation budget when truncated, otherwise null.
     * @param ClosestMatch|null $closestMatch The non-matching expectation with the fewest differing fields, or null.
     * @param list<ExplainedExpectation> $results Each evaluated expectation, whether it matches, and its differences.
     */
    public function __construct(
        public readonly ?string $correlationId,
        public readonly ?string $timestamp,
        public readonly int $totalExpectations,
        public readonly int $evaluatedExpectations,
        public readonly bool $truncated,
        public readonly ?int $maxExpectationsEvaluated,
        public readonly ?ClosestMatch $closestMatch,
        public readonly array $results,
    ) {
    }

    /**
     * @param array<string, mixed> $data A decoded {@code PUT /mockserver/debugMismatch} response.
     */
    public static function fromArray(array $data): self
    {
        $closest = Fields::object($data, 'closestMatch');
        return new self(
            Fields::string($data, 'correlationId'),
            Fields::string($data, 'timestamp'),
            Fields::int($data, 'totalExpectations') ?? 0,
            Fields::int($data, 'evaluatedExpectations') ?? 0,
            Fields::bool($data, 'truncated') ?? false,
            Fields::int($data, 'maxExpectationsEvaluated'),
            $closest !== null ? ClosestMatch::fromArray($closest) : null,
            Fields::expectations($data, 'results'),
        );
    }
}
