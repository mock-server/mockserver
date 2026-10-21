<?php

declare(strict_types=1);

namespace MockServer;

/**
 * A date-time matcher for a header, query string parameter, path parameter or
 * cookie value: the value must parse as a date-time that is before, after or
 * equal to an expected date-time.
 *
 * The expected value is {@code now}, {@code now <offset>} (for example
 * {@code now -1 hours}) or an ISO-8601, RFC-1123 or epoch date-time. The server
 * validates the expected value and the options.
 *
 * Instances are immutable: every {@code with*()} method returns a new matcher.
 * Use it with {@see HttpRequest::headerMatcher()},
 * {@see HttpRequest::queryStringParameterMatcher()}, {@see HttpRequest::cookieMatcher()}
 * or {@see HttpRequest::pathParameter()}. A plain string value is never a date-time
 * matcher, so {@code "before:2026-01-01"} is still matched literally.
 *
 * @example
 *   HttpRequest::request()
 *       ->headerMatcher('X-Issued-At', DateTimeMatcher::after('now -1 hours'))
 *       ->headerMatcher('If-Modified-Since', DateTimeMatcher::before('2026-10-21T00:00:00Z')->withActualFormat('rfc1123'));
 */
final class DateTimeMatcher implements \JsonSerializable
{
    private const OPERATORS = ['before', 'after', 'equalToDateTime'];
    private const STRING_OPTIONS = ['actualFormat', 'truncateExpected', 'truncateActual', 'zone'];
    private const BOOLEAN_OPTIONS = ['applyTruncationLast', 'not'];

    private ?string $actualFormat = null;
    private ?string $truncateExpected = null;
    private ?string $truncateActual = null;
    private ?bool $applyTruncationLast = null;
    private ?string $zone = null;
    private ?bool $not = null;

    private function __construct(
        private string $operator,
        private string $expected,
    ) {
    }

    /**
     * Matches a value that is before {@code $expected}.
     */
    public static function before(string $expected): self
    {
        return new self('before', $expected);
    }

    /**
     * Matches a value that is after {@code $expected}.
     */
    public static function after(string $expected): self
    {
        return new self('after', $expected);
    }

    /**
     * Matches a value that is equal to {@code $expected} (after any truncation).
     */
    public static function equalToDateTime(string $expected): self
    {
        return new self('equalToDateTime', $expected);
    }

    /**
     * How the actual request value is written: a pattern such as {@code dd/MM/yyyy},
     * or {@code iso}, {@code rfc1123}, {@code epochSeconds} or {@code epochMillis}.
     */
    public function withActualFormat(string $actualFormat): self
    {
        $copy = clone $this;
        $copy->actualFormat = $actualFormat;
        return $copy;
    }

    /**
     * Truncate the expected date-time before comparing, for example
     * {@code first day of month}.
     */
    public function withTruncateExpected(string $truncateExpected): self
    {
        $copy = clone $this;
        $copy->truncateExpected = $truncateExpected;
        return $copy;
    }

    /**
     * Truncate the actual date-time before comparing, for example {@code startOfDay}.
     */
    public function withTruncateActual(string $truncateActual): self
    {
        $copy = clone $this;
        $copy->truncateActual = $truncateActual;
        return $copy;
    }

    /**
     * When true, {@code now} is truncated after its offset is added instead of before.
     */
    public function withApplyTruncationLast(bool $applyTruncationLast): self
    {
        $copy = clone $this;
        $copy->applyTruncationLast = $applyTruncationLast;
        return $copy;
    }

    /**
     * The time zone for values without one, for truncation and for day, month and
     * year offsets, for example {@code Europe/London}; UTC by default.
     */
    public function withZone(string $zone): self
    {
        $copy = clone $this;
        $copy->zone = $zone;
        return $copy;
    }

    /**
     * Invert the matcher. Only {@code true} is written to the wire.
     */
    public function withNot(bool $not): self
    {
        $copy = clone $this;
        $copy->not = $not;
        return $copy;
    }

    /**
     * The operator field name: {@code before}, {@code after} or {@code equalToDateTime}.
     */
    public function getOperator(): string
    {
        return $this->operator;
    }

    public function getExpected(): string
    {
        return $this->expected;
    }

    public function getActualFormat(): ?string
    {
        return $this->actualFormat;
    }

    public function getTruncateExpected(): ?string
    {
        return $this->truncateExpected;
    }

    public function getTruncateActual(): ?string
    {
        return $this->truncateActual;
    }

    public function getApplyTruncationLast(): ?bool
    {
        return $this->applyTruncationLast;
    }

    public function getZone(): ?string
    {
        return $this->zone;
    }

    public function getNot(): ?bool
    {
        return $this->not;
    }

    /**
     * Read a date-time matcher from a decoded JSON object.
     *
     * Returns null when {@code $data} is not exactly a date-time matcher this
     * class writes back unchanged: no operator or more than one, an unknown or
     * mistyped field, or a {@code not: false} (which is never written). The
     * caller then keeps the value as read, so nothing is lost.
     */
    public static function fromArray(mixed $data): ?self
    {
        if (!is_array($data) || $data === [] || array_is_list($data)) {
            return null;
        }
        $operators = array_values(array_intersect(self::OPERATORS, array_keys($data)));
        if (count($operators) !== 1 || !is_string($data[$operators[0]])) {
            return null;
        }
        $matcher = new self($operators[0], $data[$operators[0]]);
        foreach ($data as $key => $value) {
            $key = (string) $key;
            if ($key === $operators[0]) {
                continue;
            }
            if (in_array($key, self::STRING_OPTIONS, true) && is_string($value)) {
                $matcher->{$key} = $value;
            } elseif (in_array($key, self::BOOLEAN_OPTIONS, true) && is_bool($value)) {
                $matcher->{$key} = $value;
            } else {
                return null;
            }
        }
        $written = $matcher->toArray();
        $read = $data;
        ksort($written);
        ksort($read);
        return $written === $read ? $matcher : null;
    }

    /**
     * @return array<string, string|bool>
     */
    public function toArray(): array
    {
        $data = [];
        if ($this->not === true) {
            $data['not'] = true;
        }
        $data[$this->operator] = $this->expected;
        if ($this->actualFormat !== null) {
            $data['actualFormat'] = $this->actualFormat;
        }
        if ($this->truncateExpected !== null) {
            $data['truncateExpected'] = $this->truncateExpected;
        }
        if ($this->truncateActual !== null) {
            $data['truncateActual'] = $this->truncateActual;
        }
        if ($this->applyTruncationLast !== null) {
            $data['applyTruncationLast'] = $this->applyTruncationLast;
        }
        if ($this->zone !== null) {
            $data['zone'] = $this->zone;
        }
        return $data;
    }

    /**
     * @return array<string, string|bool>
     */
    public function jsonSerialize(): array
    {
        return $this->toArray();
    }
}
