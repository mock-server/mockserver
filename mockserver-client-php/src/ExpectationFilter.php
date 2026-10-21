<?php

declare(strict_types=1);

namespace MockServer;

/**
 * Control-plane filter for retrieving and clearing expectations by metadata, by
 * whether they have ever matched a request, and for paging the retrieved list.
 *
 * It is sent as query string parameters on {@code PUT /mockserver/retrieve}
 * and {@code PUT /mockserver/clear}. All selections that are present must
 * hold; an expectation with no metadata matches no metadata selection.
 *
 * Every setter fails closed: a blank key or JSONPath, or a null value, throws
 * {@see \InvalidArgumentException} rather than silently widening the selection,
 * because on clear "no filter" means "clear every expectation".
 */
final class ExpectationFilter
{
    private ?string $metadataKey = null;
    private ?string $metadataValue = null;
    private ?string $metadataJsonPath = null;
    private bool $unmatched = false;
    private ?int $limit = null;
    private ?int $offset = null;

    public static function expectationFilter(): self
    {
        return new self();
    }

    /**
     * Select expectations whose metadata has {@code $key} with a value equal
     * to {@code $value}. A string is sent as text; any other value (int,
     * float, bool, array, object) is sent as its compact JSON, so {@code 42},
     * {@code true} or {@code ['a', 'b']} match the number, boolean or array.
     *
     * @throws \InvalidArgumentException if the key is blank or the value is
     *         null (use {@see withMetadataKey()} to select any value)
     */
    public function withMetadata(string $key, mixed $value): self
    {
        if ($value === null) {
            throw new \InvalidArgumentException(
                'the metadata value must not be null; use withMetadataKey($key) to select any value'
            );
        }
        $this->metadataKey = self::requireNotBlank($key, 'metadata key');
        $this->metadataValue = is_string($value) ? $value : self::toJson($value);
        return $this;
    }

    /**
     * Select expectations whose metadata has {@code $key}, with any value.
     *
     * @throws \InvalidArgumentException if the key is blank
     */
    public function withMetadataKey(string $key): self
    {
        $this->metadataKey = self::requireNotBlank($key, 'metadata key');
        $this->metadataValue = null;
        return $this;
    }

    /**
     * Select expectations whose metadata object the JSONPath selects at least
     * one value from, e.g. {@code $[?(@.owner == 'team-a')]}.
     *
     * @throws \InvalidArgumentException if the JSONPath is blank
     */
    public function withMetadataJsonPath(string $jsonPath): self
    {
        $this->metadataJsonPath = self::requireNotBlank($jsonPath, 'metadata JSONPath');
        return $this;
    }

    /**
     * Select only expectations that have not matched any request.
     */
    public function withUnmatched(bool $unmatched = true): self
    {
        $this->unmatched = $unmatched;
        return $this;
    }

    /**
     * @throws \InvalidArgumentException if the limit is negative
     */
    public function withLimit(?int $limit): self
    {
        $this->limit = self::requireNonNegative($limit, 'limit');
        return $this;
    }

    /**
     * @throws \InvalidArgumentException if the offset is negative
     */
    public function withOffset(?int $offset): self
    {
        $this->offset = self::requireNonNegative($offset, 'offset');
        return $this;
    }

    public function getMetadataKey(): ?string
    {
        return $this->metadataKey;
    }

    public function getMetadataValue(): ?string
    {
        return $this->metadataValue;
    }

    public function getMetadataJsonPath(): ?string
    {
        return $this->metadataJsonPath;
    }

    public function isUnmatched(): bool
    {
        return $this->unmatched;
    }

    public function getLimit(): ?int
    {
        return $this->limit;
    }

    public function getOffset(): ?int
    {
        return $this->offset;
    }

    /**
     * True when this filter selects a subset of expectations (metadata or unmatched).
     */
    public function hasSelection(): bool
    {
        return $this->metadataKey !== null || $this->metadataJsonPath !== null || $this->unmatched;
    }

    /**
     * True when this filter pages the result.
     */
    public function hasPaging(): bool
    {
        return $this->limit !== null || $this->offset !== null;
    }

    /**
     * The query string parameters, in wire order, with literal (unencoded) values.
     *
     * @return array<string, string>
     */
    public function toQueryParameters(): array
    {
        $parameters = [];
        if ($this->metadataKey !== null) {
            $parameters['metadataKey'] = $this->metadataKey;
            if ($this->metadataValue !== null) {
                $parameters['metadataValue'] = $this->metadataValue;
            }
        }
        if ($this->metadataJsonPath !== null) {
            $parameters['metadataJsonPath'] = $this->metadataJsonPath;
        }
        if ($this->unmatched) {
            $parameters['unmatched'] = 'true';
        }
        if ($this->limit !== null) {
            $parameters['limit'] = (string) $this->limit;
        }
        if ($this->offset !== null) {
            $parameters['offset'] = (string) $this->offset;
        }
        return $parameters;
    }

    /**
     * The parameters as an RFC 3986 encoded query string fragment (no leading
     * {@code ?} or {@code &}); empty when the filter has no parameters.
     */
    public function toQueryString(): string
    {
        return http_build_query($this->toQueryParameters(), '', '&', PHP_QUERY_RFC3986);
    }

    private static function requireNotBlank(string $value, string $description): string
    {
        if (trim($value) === '') {
            throw new \InvalidArgumentException("the {$description} must not be blank");
        }
        return $value;
    }

    private static function requireNonNegative(?int $value, string $description): ?int
    {
        if ($value !== null && $value < 0) {
            throw new \InvalidArgumentException("the {$description} must be zero or more, but was {$value}");
        }
        return $value;
    }

    private static function toJson(mixed $value): string
    {
        return json_encode(
            $value,
            JSON_THROW_ON_ERROR | JSON_UNESCAPED_SLASHES | JSON_UNESCAPED_UNICODE | JSON_PRESERVE_ZERO_FRACTION,
        );
    }
}
