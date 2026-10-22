<?php

declare(strict_types=1);

namespace MockServer\Diagnostics;

/**
 * One expectation as a diagnostic call evaluated it. From
 * {@see \MockServer\MockServerClient::explain()}, the matched expectation has
 * no reason and every skipped or shadowed one has a reason and an explanation.
 * One compared field by field also carries the field counts, differences and
 * remediation hints.
 */
final class ExplainedExpectation
{
    /** The expectation belongs to another namespace. */
    public const REASON_NAMESPACE = 'NAMESPACE';

    /** The expectation is used up or expired. */
    public const REASON_INACTIVE = 'INACTIVE';

    /** One or more request fields differ. */
    public const REASON_FIELDS_DIFFER = 'FIELDS_DIFFER';

    /** A {@code not} request matcher excluded the request. */
    public const REASON_NOT_OPERATOR = 'NOT_OPERATOR';

    /** An LLM conversation matcher did not match. */
    public const REASON_CONVERSATION = 'CONVERSATION';

    /** The scenario is not in the required state. */
    public const REASON_SCENARIO_STATE = 'SCENARIO_STATE';

    /** The expectation's percentage roll did not select it. */
    public const REASON_PERCENTAGE = 'PERCENTAGE';

    /** It also matches, but a higher-priority expectation serves the request. */
    public const REASON_LOWER_PRIORITY = 'LOWER_PRIORITY';

    /**
     * @param string|null $expectationId The expectation's id.
     * @param string|null $expectationMethod The method it matches, or null.
     * @param string|null $expectationPath The path it matches, or null.
     * @param int|null $position Its 1-based position in the order expectations are evaluated (explain only).
     * @param int|null $priority Its priority (explain only).
     * @param string|null $reason Why it was passed over: a REASON_* constant, a reason a newer server added,
     *        or null for the matched expectation.
     * @param string|null $explanation Why it was passed over, in plain language, or null.
     * @param bool|null $matches Whether the request matches its request matcher, or null when an earlier gate decided.
     * @param int|null $matchedFieldCount How many fields matched, when compared field by field.
     * @param int|null $totalFieldCount How many fields were counted, when compared field by field.
     * @param int|null $differingFieldCount How many fields differ, or null when not compared field by field.
     * @param array<string, list<string>> $differences For each differing field, the differences found.
     * @param array<string, string> $remediation For each differing field, a hint on how to change the expectation.
     * @param SuggestedFix|null $suggestedFix A patch that would make it match; only from
     *        {@see \MockServer\MockServerClient::explainUnmatched()} with suggestFixes.
     * @param AnyOfBranchComparison|null $anyOf For an anyOf request matcher that does not match, how each
     *        branch compared; null otherwise.
     */
    public function __construct(
        public readonly ?string $expectationId,
        public readonly ?string $expectationMethod,
        public readonly ?string $expectationPath,
        public readonly ?int $position,
        public readonly ?int $priority,
        public readonly ?string $reason,
        public readonly ?string $explanation,
        public readonly ?bool $matches,
        public readonly ?int $matchedFieldCount,
        public readonly ?int $totalFieldCount,
        public readonly ?int $differingFieldCount,
        public readonly array $differences,
        public readonly array $remediation,
        public readonly ?SuggestedFix $suggestedFix,
        public readonly ?AnyOfBranchComparison $anyOf = null,
    ) {
    }

    /** @param array<string, mixed> $data */
    public static function fromArray(array $data): self
    {
        $differences = [];
        foreach (Fields::object($data, 'differences') ?? [] as $field => $values) {
            $differences[(string) $field] = is_array($values)
                ? array_values(array_filter($values, 'is_string'))
                : [];
        }
        $remediation = [];
        foreach (Fields::object($data, 'remediation') ?? [] as $field => $hint) {
            if (is_string($hint)) {
                $remediation[(string) $field] = $hint;
            }
        }
        $fix = Fields::object($data, 'suggestedFix');
        $anyOf = Fields::object($data, 'anyOf');
        return new self(
            Fields::string($data, 'expectationId'),
            Fields::string($data, 'expectationMethod'),
            Fields::string($data, 'expectationPath'),
            Fields::int($data, 'position'),
            Fields::int($data, 'priority'),
            Fields::string($data, 'reason'),
            Fields::string($data, 'explanation'),
            Fields::bool($data, 'matches'),
            Fields::int($data, 'matchedFieldCount'),
            Fields::int($data, 'totalFieldCount'),
            Fields::int($data, 'differingFieldCount'),
            $differences,
            $remediation,
            $fix !== null ? SuggestedFix::fromArray($fix) : null,
            $anyOf !== null ? AnyOfBranchComparison::fromArray($anyOf) : null,
        );
    }
}
