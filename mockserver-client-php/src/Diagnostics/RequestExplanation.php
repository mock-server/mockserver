<?php

declare(strict_types=1);

namespace MockServer\Diagnostics;

/**
 * Which expectation would serve a recorded request now, why each expectation
 * evaluated ahead of it was passed over, and how the request was handled when
 * it arrived; returned by {@see \MockServer\MockServerClient::explain()}.
 * Recomputed when asked for, against the current expectations, scenario states
 * and remaining Times.
 */
final class RequestExplanation
{
    /**
     * @param string|null $correlationId The correlation ID of the explained request's log entries.
     * @param string|null $timestamp When the explanation was computed (ISO-8601).
     * @param bool $recomputed Always true: recomputed, not recorded when the request arrived.
     * @param string|null $note The server's reminder that the result is recomputed.
     * @param ExplainedRequest $request The recorded request that was explained.
     * @param ExplainArrival $atArrival How the request was handled when it arrived.
     * @param bool|null $sameOutcomeAsAtArrival Whether it would be handled the same way now, or null when that cannot be told.
     * @param bool $expectationsChangedSince Whether the expectations changed at or after the moment the request arrived.
     * @param string|null $expectationsLastChanged When the expectations last changed (ISO-8601), or null if they never have.
     * @param ExplainedExpectation|null $matchedExpectation The expectation that would serve the request now, or null.
     * @param string|null $closestExpectationId When none would, the expectation with the fewest differing fields.
     * @param list<ExplainedExpectation> $skippedExpectations Those evaluated ahead of the matched one, each with a reason.
     * @param list<ExplainedExpectation> $shadowedExpectations Lower-priority ones that also match but are never reached.
     * @param int $totalExpectations How many expectations are stored.
     * @param int $evaluatedExpectations How many were evaluated.
     * @param bool $truncated Whether the evaluation budget ran out before every expectation was evaluated.
     * @param int|null $maxExpectationsEvaluated The evaluation budget when truncated, otherwise null.
     */
    public function __construct(
        public readonly ?string $correlationId,
        public readonly ?string $timestamp,
        public readonly bool $recomputed,
        public readonly ?string $note,
        public readonly ExplainedRequest $request,
        public readonly ExplainArrival $atArrival,
        public readonly ?bool $sameOutcomeAsAtArrival,
        public readonly bool $expectationsChangedSince,
        public readonly ?string $expectationsLastChanged,
        public readonly ?ExplainedExpectation $matchedExpectation,
        public readonly ?string $closestExpectationId,
        public readonly array $skippedExpectations,
        public readonly array $shadowedExpectations,
        public readonly int $totalExpectations,
        public readonly int $evaluatedExpectations,
        public readonly bool $truncated,
        public readonly ?int $maxExpectationsEvaluated,
    ) {
    }

    /**
     * @param array<string, mixed> $data A decoded {@code PUT /mockserver/explain} response.
     */
    public static function fromArray(array $data): self
    {
        $matched = Fields::object($data, 'matchedExpectation');
        return new self(
            Fields::string($data, 'correlationId'),
            Fields::string($data, 'timestamp'),
            Fields::bool($data, 'recomputed') ?? false,
            Fields::string($data, 'note'),
            ExplainedRequest::fromArray(Fields::object($data, 'request') ?? []),
            ExplainArrival::fromArray(Fields::object($data, 'atArrival') ?? []),
            Fields::bool($data, 'sameOutcomeAsAtArrival'),
            Fields::bool($data, 'expectationsChangedSince') ?? false,
            Fields::string($data, 'expectationsLastChanged'),
            $matched !== null ? ExplainedExpectation::fromArray($matched) : null,
            Fields::string($data, 'closestExpectationId'),
            Fields::expectations($data, 'skippedExpectations'),
            Fields::expectations($data, 'shadowedExpectations'),
            Fields::int($data, 'totalExpectations') ?? 0,
            Fields::int($data, 'evaluatedExpectations') ?? 0,
            Fields::bool($data, 'truncated') ?? false,
            Fields::int($data, 'maxExpectationsEvaluated'),
        );
    }
}
