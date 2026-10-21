<?php

declare(strict_types=1);

namespace MockServer;

/**
 * Fluent builder for an HTTP response action.
 *
 * @example
 *   $response = HttpResponse::response()
 *       ->statusCode(200)
 *       ->header('Content-Type', 'application/json')
 *       ->body('{"message":"hello"}');
 */
class HttpResponse implements \JsonSerializable
{
    private ?int $statusCode = null;
    private ?string $reasonPhrase = null;
    /** @var array<string, list<string>> */
    private array $headers = [];
    /** @var array<string, list<string>> */
    private array $cookies = [];
    private string|array|null $body = null;
    private ?Delay $delay = null;
    private ?ConnectionOptions $connectionOptions = null;
    private ?bool $primary = null;
    /** @var array<string, list<string>> */
    private array $trailers = [];
    private ?string $generateFromSchema = null;
    private ?string $statusCodeRange = null;
    private ?RecoverAfter $recoverAfter = null;

    /**
     * Static factory for fluent construction.
     */
    public static function response(): self
    {
        return new self();
    }

    public function statusCode(int $statusCode): self
    {
        $this->statusCode = $statusCode;
        return $this;
    }

    public function reasonPhrase(string $reasonPhrase): self
    {
        $this->reasonPhrase = $reasonPhrase;
        return $this;
    }

    /**
     * Add a response header (multi-value supported).
     */
    public function header(string $name, string ...$values): self
    {
        if (!isset($this->headers[$name])) {
            $this->headers[$name] = [];
        }
        foreach ($values as $value) {
            $this->headers[$name][] = $value;
        }
        return $this;
    }

    /**
     * Add a response cookie.
     */
    public function cookie(string $name, string $value): self
    {
        if (!isset($this->cookies[$name])) {
            $this->cookies[$name] = [];
        }
        $this->cookies[$name][] = $value;
        return $this;
    }

    /**
     * Set the response body as a plain string.
     */
    public function body(string $body): self
    {
        $this->body = $body;
        return $this;
    }

    /**
     * Set the response body as a typed JSON body.
     *
     * @param array|string $json JSON content (string or array that will be JSON-encoded)
     */
    public function jsonBody(array|string $json): self
    {
        $jsonString = is_array($json) ? json_encode($json, JSON_THROW_ON_ERROR) : $json;
        $this->body = [
            'type' => 'JSON',
            'json' => $jsonString,
        ];
        return $this;
    }

    /**
     * Set the response body as a file reference.
     *
     * @param string $filePath Path to the file to serve
     * @param string|null $contentType Optional content type for the response
     * @param string|null $templateType Optional template type ("VELOCITY" or "MUSTACHE") for templating the file
     * @param bool $templatedFilePath When true (with a template type), the file path itself is rendered as a
     *                                template against the request; omitted from the JSON when false
     */
    public function fileBody(
        string $filePath,
        ?string $contentType = null,
        ?string $templateType = null,
        bool $templatedFilePath = false
    ): self {
        $body = [
            'type' => 'FILE',
            'filePath' => $filePath,
        ];
        if ($contentType !== null) {
            $body['contentType'] = $contentType;
        }
        if ($templateType !== null) {
            $body['templateType'] = $templateType;
        }
        if ($templatedFilePath) {
            $body['templatedFilePath'] = true;
        }
        $this->body = $body;
        return $this;
    }

    public function delay(Delay $delay): self
    {
        $this->delay = $delay;
        return $this;
    }

    public function connectionOptions(ConnectionOptions $connectionOptions): self
    {
        $this->connectionOptions = $connectionOptions;
        return $this;
    }

    public function getStatusCode(): ?int
    {
        return $this->statusCode;
    }

    /**
     * @return array<string, list<string>>
     */
    public function getHeaders(): array
    {
        return $this->headers;
    }

    public function getBody(): string|array|null
    {
        return $this->body;
    }

    public function getDelay(): ?Delay
    {
        return $this->delay;
    }

    /**
     * Mark this action as the primary one for the expectation.
     *
     * Only meaningful when an expectation carries more than one action: the
     * server requires exactly one to be marked primary and rejects the
     * expectation otherwise. Omitting it from a re-submitted expectation can
     * silently change which action executes.
     */
    public function primary(bool $primary): self
    {
        $this->primary = $primary;
        return $this;
    }

    /**
     * Add an HTTP trailer (a header sent after the response body, in the
     * chunked trailer section).
     *
     * Mirrors {@see header()}: repeated calls with the same name append.
     */
    public function trailer(string $name, string ...$values): self
    {
        if (!isset($this->trailers[$name])) {
            $this->trailers[$name] = [];
        }
        foreach ($values as $value) {
            $this->trailers[$name][] = $value;
        }
        return $this;
    }

    /**
     * Generate the response body from a JSON schema instead of specifying it.
     *
     * @param string $generateFromSchema a JSON schema, as a JSON string
     */
    public function generateFromSchema(string $generateFromSchema): self
    {
        $this->generateFromSchema = $generateFromSchema;
        return $this;
    }

    /**
     * Return a status code drawn from a range, e.g. "200-299".
     */
    public function statusCodeRange(string $statusCodeRange): self
    {
        $this->statusCodeRange = $statusCodeRange;
        return $this;
    }

    /**
     * Fail the first N matches with a different response before serving this
     * one — a deterministic retry/backoff probe.
     */
    public function recoverAfter(RecoverAfter $recoverAfter): self
    {
        $this->recoverAfter = $recoverAfter;
        return $this;
    }

    /**
     * @return array<string, mixed>
     */
    public function jsonSerialize(): array
    {
        return $this->toArray();
    }

    /**
     * @return array<string, mixed>
     */
    public function toArray(): array
    {
        $data = [];

        if ($this->statusCode !== null) {
            $data['statusCode'] = $this->statusCode;
        }
        if ($this->reasonPhrase !== null) {
            $data['reasonPhrase'] = $this->reasonPhrase;
        }
        if (!empty($this->headers)) {
            $data['headers'] = $this->headers;
        }
        if (!empty($this->cookies)) {
            $data['cookies'] = $this->cookies;
        }
        if ($this->body !== null) {
            $data['body'] = $this->body;
        }
        if ($this->delay !== null) {
            $data['delay'] = $this->delay->toArray();
        }
        if ($this->connectionOptions !== null) {
            $data['connectionOptions'] = $this->connectionOptions->toArray();
        }
        if ($this->primary !== null) {
            $data['primary'] = $this->primary;
        }
        if (!empty($this->trailers)) {
            $data['trailers'] = $this->trailers;
        }
        // The scalar guards below are `!== null`, not truthiness, on purpose. An explicitly-empty
        // value is a user error and should reach the server rather than be silently dropped: for
        // the enum fields elsewhere (templateType) the server rejects "" outright, while
        // generateFromSchema and statusCodeRange are plain `{"type": "string"}` in the schema, so
        // "" is schema-VALID and surfaces downstream instead. Either way the user finds out. A
        // truthy guard would also drop "0", which PHP treats as falsy.
        if ($this->generateFromSchema !== null) {
            $data['generateFromSchema'] = $this->generateFromSchema;
        }
        if ($this->statusCodeRange !== null) {
            $data['statusCodeRange'] = $this->statusCodeRange;
        }
        if ($this->recoverAfter !== null) {
            $data['recoverAfter'] = $this->recoverAfter->toArray();
        }

        return $data;
    }
}
