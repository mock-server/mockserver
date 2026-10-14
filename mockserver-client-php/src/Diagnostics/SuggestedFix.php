<?php

declare(strict_types=1);

namespace MockServer\Diagnostics;

/**
 * The smallest change to an expectation that would make it match an unmatched
 * request. Nothing is applied: patch the expectation retrieved as active and
 * send it back with {@see \MockServer\MockServerClient::upsertExpectation()}.
 */
final class SuggestedFix
{
    /**
     * @param list<array{op: string, path: string, value?: mixed}> $patch An RFC 6902 JSON patch of the
     *        expectation, each change guarded by a {@code test} operation.
     * @param list<string> $patchedFields The fields the patch changes.
     * @param list<array{field: ?string, reason: ?string}> $unpatchedFields The fields that could not be
     *        patched, and why.
     * @param bool $matchesAfterPatch Whether the patched expectation matches the request.
     */
    public function __construct(
        public readonly array $patch,
        public readonly array $patchedFields,
        public readonly array $unpatchedFields,
        public readonly bool $matchesAfterPatch,
    ) {
    }

    /** @param array<string, mixed> $data */
    public static function fromArray(array $data): self
    {
        $unpatched = array_map(
            static fn(array $item): array => [
                'field' => Fields::string($item, 'field'),
                'reason' => Fields::string($item, 'reason'),
            ],
            array_filter(Fields::list($data, 'unpatchedFields'), 'is_array'),
        );
        return new self(
            array_values(array_filter(Fields::list($data, 'patch'), 'is_array')),
            array_values(array_filter(Fields::list($data, 'patchedFields'), 'is_string')),
            array_values($unpatched),
            Fields::bool($data, 'matchesAfterPatch') ?? false,
        );
    }
}
