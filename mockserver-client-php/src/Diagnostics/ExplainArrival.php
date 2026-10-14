<?php

declare(strict_types=1);

namespace MockServer\Diagnostics;

/**
 * How a request was handled when it arrived, read from the event log.
 */
final class ExplainArrival
{
    /** An expectation served it. */
    public const MATCHED = 'MATCHED';

    /** No expectation matched. */
    public const NO_MATCH = 'NO_MATCH';

    /** It was proxied without an expectation. */
    public const FORWARDED_WITHOUT_EXPECTATION = 'FORWARDED_WITHOUT_EXPECTATION';

    /** It was answered without an expectation. */
    public const RESPONDED_WITHOUT_EXPECTATION = 'RESPONDED_WITHOUT_EXPECTATION';

    /** The outcome is not recorded. */
    public const UNKNOWN = 'UNKNOWN';

    /**
     * @param string $outcome One of the constants above, or an outcome a newer server added.
     * @param string|null $expectationId The expectation that served it; set only when the outcome is MATCHED.
     * @param bool|null $expectationStillStored Whether that expectation is still stored; set only when the outcome is MATCHED.
     */
    public function __construct(
        public readonly string $outcome,
        public readonly ?string $expectationId,
        public readonly ?bool $expectationStillStored,
    ) {
    }

    /** @param array<string, mixed> $data */
    public static function fromArray(array $data): self
    {
        return new self(
            Fields::string($data, 'outcome') ?? self::UNKNOWN,
            Fields::string($data, 'expectationId'),
            Fields::bool($data, 'expectationStillStored'),
        );
    }
}
