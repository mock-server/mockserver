<?php

declare(strict_types=1);

namespace MockServer\Diagnostics;

/**
 * For an anyOf request matcher that does not match the request, which branch
 * came closest and how each branch differed. The expectation's differences are
 * those of the closest branch.
 */
final class AnyOfBranchComparison
{
    /**
     * @param int|null $closestBranch The 0-based index of the branch with the fewest differing fields,
     *        the earlier on a tie.
     * @param list<array{branch: ?int, matches: ?bool, differingFieldCount: ?int, differingFields: list<string>}> $branches
     *        How each branch compared, in anyOf order.
     */
    public function __construct(
        public readonly ?int $closestBranch,
        public readonly array $branches,
    ) {
    }

    /** @param array<string, mixed> $data */
    public static function fromArray(array $data): self
    {
        $branches = array_map(
            static fn(array $item): array => [
                'branch' => Fields::int($item, 'branch'),
                'matches' => Fields::bool($item, 'matches'),
                'differingFieldCount' => Fields::int($item, 'differingFieldCount'),
                'differingFields' => array_values(array_filter(Fields::list($item, 'differingFields'), 'is_string')),
            ],
            array_filter(Fields::list($data, 'branches'), 'is_array'),
        );
        return new self(Fields::int($data, 'closestBranch'), array_values($branches));
    }
}
