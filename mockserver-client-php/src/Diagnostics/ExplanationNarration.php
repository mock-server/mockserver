<?php

declare(strict_types=1);

namespace MockServer\Diagnostics;

/**
 * A plain-language summary of an explanation written by the configured LLM
 * backend, or why none is available; set only when narration was asked for.
 * It is AI-generated and can be wrong; the other fields of the result are the
 * deterministic facts.
 */
final class ExplanationNarration
{
    /**
     * @param bool $available Whether the LLM returned a summary.
     * @param bool $aiGenerated Always true when available: the text was written by an LLM, not by MockServer.
     * @param string|null $label A label to show beside the text, saying it is AI-generated and can be wrong.
     * @param string|null $provider The configured LLM provider that wrote the text.
     * @param string|null $model The model that wrote the text, when known.
     * @param string|null $text The summary, at most 4,000 characters.
     * @param string|null $reason When not available, why.
     */
    public function __construct(
        public readonly bool $available,
        public readonly bool $aiGenerated,
        public readonly ?string $label,
        public readonly ?string $provider,
        public readonly ?string $model,
        public readonly ?string $text,
        public readonly ?string $reason,
    ) {
    }

    /**
     * @param array<string, mixed> $data The decoded 'narration' object of an explain response.
     */
    public static function fromArray(array $data): self
    {
        return new self(
            Fields::bool($data, 'available') ?? false,
            Fields::bool($data, 'aiGenerated') ?? false,
            Fields::string($data, 'label'),
            Fields::string($data, 'provider'),
            Fields::string($data, 'model'),
            Fields::string($data, 'text'),
            Fields::string($data, 'reason'),
        );
    }

    /**
     * @param array<string, mixed> $data A decoded explain or explainUnmatched response.
     */
    public static function fromResponse(array $data): ?self
    {
        $narration = Fields::object($data, 'narration');
        return $narration !== null ? self::fromArray($narration) : null;
    }
}
