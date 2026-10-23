<?php

declare(strict_types=1);

namespace MockServer;

/**
 * Options that shape the expectations returned by
 * {@see MockServerClient::retrieveRecordedExpectationsWithOptions()}.
 *
 * Sent as query string parameters on
 * {@code PUT /mockserver/retrieve?type=RECORDED_EXPECTATIONS}. Each option is
 * off unless set; an invalid value throws {@see \InvalidArgumentException}
 * when it is set, so nothing is sent.
 */
final class RecordedExpectationsOptions
{
    private bool $consolidate = false;
    private bool $parameterize = false;
    private bool $repeatsAsScenarios = false;
    /** @var list<string>|null */
    private ?array $captureHeaders = null;
    private ?int $extractBodiesLargerThan = null;

    public static function recordedExpectationsOptions(): self
    {
        return new self();
    }

    /**
     * Collapse recorded exchanges that share a request shape into one
     * reusable, unlimited-times expectation.
     */
    public function withConsolidate(bool $consolidate = true): self
    {
        $this->consolidate = $consolidate;
        return $this;
    }

    /**
     * Also generalise volatile path, query, header and body values into
     * matchers (implies consolidate).
     */
    public function withParameterize(bool $parameterize = true): self
    {
        $this->parameterize = $parameterize;
        return $this;
    }

    /**
     * Replay a request recorded several times with different responses in the
     * recorded order, then keep returning the last one.
     */
    public function withRepeatsAsScenarios(bool $repeatsAsScenarios = true): self
    {
        $this->repeatsAsScenarios = $repeatsAsScenarios;
        return $this;
    }

    /**
     * Keep only these request headers (case-insensitive) in each recorded
     * matcher; no names keeps none.
     *
     * @throws \InvalidArgumentException if a name is blank or contains a comma
     */
    public function withCaptureHeaders(string ...$headerNames): self
    {
        foreach ($headerNames as $name) {
            if (trim($name) === '' || str_contains($name, ',')) {
                throw new \InvalidArgumentException(
                    "each capture header must be a non-blank header name without a comma, but was '{$name}'"
                );
            }
        }
        $this->captureHeaders = array_values($headerNames);
        return $this;
    }

    /**
     * Write each response body larger than this many bytes to the server's
     * {@code recordedBodyFilesDirectory} and return a FILE body that points at it.
     *
     * @throws \InvalidArgumentException if the threshold is negative
     */
    public function withExtractBodiesLargerThan(int $thresholdBytes): self
    {
        if ($thresholdBytes < 0) {
            throw new \InvalidArgumentException(
                "extractBodiesLargerThan must be zero or more, but was {$thresholdBytes}"
            );
        }
        $this->extractBodiesLargerThan = $thresholdBytes;
        return $this;
    }

    /**
     * The query string parameters, in wire order, with literal (unencoded) values.
     *
     * @return array<string, string>
     */
    public function toQueryParameters(): array
    {
        $parameters = [];
        if ($this->consolidate) {
            $parameters['consolidate'] = 'true';
        }
        if ($this->parameterize) {
            $parameters['parameterize'] = 'true';
        }
        if ($this->repeatsAsScenarios) {
            $parameters['repeatsAsScenarios'] = 'true';
        }
        if ($this->captureHeaders !== null) {
            $parameters['captureHeaders'] = implode(',', $this->captureHeaders);
        }
        if ($this->extractBodiesLargerThan !== null) {
            $parameters['extractBodiesLargerThan'] = (string) $this->extractBodiesLargerThan;
        }
        return $parameters;
    }

    /**
     * The parameters as an RFC 3986 encoded query string fragment (no leading
     * {@code ?} or {@code &}); empty when no option is set.
     */
    public function toQueryString(): string
    {
        return http_build_query($this->toQueryParameters(), '', '&', PHP_QUERY_RFC3986);
    }
}
