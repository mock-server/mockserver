<?php

declare(strict_types=1);

namespace MockServer;

/**
 * Fluent builder for an HTTP request matcher.
 *
 * The same class also carries the other request matchers an expectation's
 * {@code httpRequest} can hold: an OpenAPI matcher ({@see HttpRequest::openAPI()})
 * and a conditional if/then/else matcher ({@see HttpRequest::conditional()}).
 *
 * @example
 *   $request = HttpRequest::request()
 *       ->method('GET')
 *       ->path('/hello')
 *       ->queryStringParameter('q', 'value')
 *       ->header('Accept', 'application/json')
 *       ->headerMatcher('X-Issued-At', DateTimeMatcher::after('now -1 hours'))
 *       ->body('request body');
 *
 * Header, query string parameter, path parameter and cookie values are plain
 * strings or {@see DateTimeMatcher}s. A value read by {@see fromArray()} may
 * also be a decoded JSON object (for example a {@code {not, value}} or
 * {@code {schema}} matcher), kept as read.
 */
class HttpRequest implements \JsonSerializable
{
    private ?string $method = null;
    private ?string $path = null;
    /** @var array<string, list<string|DateTimeMatcher|array<string, mixed>>> */
    private array $queryStringParameters = [];
    /** @var array<string, list<string|DateTimeMatcher|array<string, mixed>>> */
    private array $headers = [];
    /** @var array<string, string|DateTimeMatcher|array<array-key, mixed>> */
    private array $cookies = [];
    /** @var array<string, list<string|DateTimeMatcher|array<string, mixed>>> */
    private array $pathParameters = [];
    private string|array|null $body = null;
    private ?bool $keepAlive = null;
    private ?bool $secure = null;
    private ?array $socketAddress = null;
    private ?array $jwt = null;
    private ?bool $not = null;
    private string|array|null $specUrlOrPayload = null;
    private ?string $operationId = null;
    private ?string $contextPathPrefix = null;
    private ?HttpRequest $ifRequest = null;
    private ?HttpRequest $thenRequest = null;
    private ?HttpRequest $elseRequest = null;
    /**
     * Fields read by {@see HttpRequest::fromArray()} that this class does not
     * model, written back unchanged by {@see HttpRequest::toArray()}.
     *
     * @var array<string, mixed>
     */
    private array $additionalFields = [];

    /**
     * Static factory for fluent construction.
     */
    public static function request(): self
    {
        return new self();
    }

    /**
     * A conditional (if/then/else) request matcher, written as an
     * {@code httpRequest} holding {@code if}, {@code then} and {@code else}.
     *
     * When {@code $ifRequest} matches, {@code $thenRequest} must match too;
     * otherwise {@code $elseRequest} must. With no else branch the matcher
     * matches whenever {@code $ifRequest} does not. Each branch is an HTTP
     * matcher, an OpenAPI matcher ({@see openAPI()}) or another conditional.
     *
     * @example
     *   HttpRequest::conditional(
     *       HttpRequest::request()->method('POST'),
     *       HttpRequest::request()->path('/orders'),
     *       HttpRequest::request()->method('GET'),
     *   );
     */
    public static function conditional(
        HttpRequest $ifRequest,
        ?HttpRequest $thenRequest = null,
        ?HttpRequest $elseRequest = null,
    ): self {
        $request = new self();
        $request->ifRequest = $ifRequest;
        $request->thenRequest = $thenRequest;
        $request->elseRequest = $elseRequest;
        return $request;
    }

    /**
     * An OpenAPI request matcher: matches requests valid for the spec (or for
     * one of its operations when {@code $operationId} is given).
     *
     * @param string|array<string, mixed> $specUrlOrPayload a URL, file path or
     *        inline JSON/YAML spec, or the spec as a decoded array
     */
    public static function openAPI(string|array $specUrlOrPayload, ?string $operationId = null): self
    {
        $request = new self();
        $request->specUrlOrPayload = $specUrlOrPayload;
        $request->operationId = $operationId;
        return $request;
    }

    public function method(string $method): self
    {
        $this->method = $method;
        return $this;
    }

    public function path(string $path): self
    {
        $this->path = $path;
        return $this;
    }

    /**
     * Add a query string parameter (multi-value supported).
     */
    public function queryStringParameter(string $name, string ...$values): self
    {
        if (!isset($this->queryStringParameters[$name])) {
            $this->queryStringParameters[$name] = [];
        }
        foreach ($values as $value) {
            $this->queryStringParameters[$name][] = $value;
        }
        return $this;
    }

    /**
     * Add a header (multi-value supported).
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
     * Add a cookie.
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
     * Add query string parameter values that are {@see DateTimeMatcher}s
     * (multi-value supported).
     */
    public function queryStringParameterMatcher(string $name, DateTimeMatcher ...$values): self
    {
        if (!isset($this->queryStringParameters[$name])) {
            $this->queryStringParameters[$name] = [];
        }
        foreach ($values as $value) {
            $this->queryStringParameters[$name][] = $value;
        }
        return $this;
    }

    /**
     * Add header values that are {@see DateTimeMatcher}s (multi-value supported).
     */
    public function headerMatcher(string $name, DateTimeMatcher ...$values): self
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
     * Add a cookie whose value is a {@see DateTimeMatcher}.
     */
    public function cookieMatcher(string $name, DateTimeMatcher $value): self
    {
        if (!isset($this->cookies[$name])) {
            $this->cookies[$name] = [];
        }
        $this->cookies[$name][] = $value;
        return $this;
    }

    /**
     * Add a path parameter (multi-value supported), matched against the
     * {@code {name}} placeholder of the path. Each value is a plain string or a
     * {@see DateTimeMatcher}.
     */
    public function pathParameter(string $name, string|DateTimeMatcher ...$values): self
    {
        if (!isset($this->pathParameters[$name])) {
            $this->pathParameters[$name] = [];
        }
        foreach ($values as $value) {
            $this->pathParameters[$name][] = $value;
        }
        return $this;
    }

    /**
     * Set the request body as a plain string.
     */
    public function body(string $body): self
    {
        $this->body = $body;
        return $this;
    }

    /**
     * Set the request body as a typed JSON body.
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
     * Set the request body to a JSON_SCHEMA matcher: the body must be JSON
     * valid against {@code $jsonSchema}.
     */
    public function jsonSchemaBody(string $jsonSchema): self
    {
        $this->body = [
            'type' => 'JSON_SCHEMA',
            'jsonSchema' => $jsonSchema,
        ];
        return $this;
    }

    /**
     * Set the request body as a file reference.
     *
     * @param string $filePath Path to the file to serve
     * @param string|null $contentType Optional content type
     * @param string|null $templateType Optional template type ("VELOCITY" or "MUSTACHE") for templating the file
     */
    public function fileBody(string $filePath, ?string $contentType = null, ?string $templateType = null): self
    {
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
        $this->body = $body;
        return $this;
    }

    /**
     * Build a JSON_PATH body matcher array (for composition, e.g. inside allOfBody()).
     *
     * @return array<string, string>
     */
    public static function jsonPathBody(string $jsonPath): array
    {
        return [
            'type' => 'JSON_PATH',
            'jsonPath' => $jsonPath,
        ];
    }

    /**
     * Build a REGEX body matcher array (for composition, e.g. inside allOfBody()).
     *
     * @return array<string, string>
     */
    public static function regexBody(string $regex): array
    {
        return [
            'type' => 'REGEX',
            'regex' => $regex,
        ];
    }

    /**
     * Set the request body to an ALL_OF matcher that requires every supplied
     * sub-body matcher to match.
     *
     * Each sub-body is a typed body matcher array such as those produced by
     * {@see jsonPathBody()} / {@see regexBody()}, or any other body matcher
     * array (e.g. {@code ['type' => 'JSON', 'json' => '...']}).
     *
     * @param array<int, array<string, mixed>> $bodies list of body matcher arrays
     */
    public function allOfBody(array $bodies): self
    {
        $this->body = [
            'type' => 'ALL_OF',
            'bodyAllOf' => array_values($bodies),
        ];
        return $this;
    }

    /**
     * Match the request against a JSON Web Token carried on the request.
     *
     * @param Jwt|array<string, mixed> $jwt a {@see Jwt} builder or a raw jwt matcher array
     */
    public function jwt(Jwt|array $jwt): self
    {
        $this->jwt = $jwt instanceof Jwt ? $jwt->toArray() : $jwt;
        return $this;
    }

    public function keepAlive(bool $keepAlive): self
    {
        $this->keepAlive = $keepAlive;
        return $this;
    }

    public function socketAddress(string $host, int $port, string $scheme = 'HTTP'): self
    {
        $this->socketAddress = ['host' => $host, 'port' => $port, 'scheme' => $scheme];
        return $this;
    }

    public function secure(bool $secure): self
    {
        $this->secure = $secure;
        return $this;
    }

    /**
     * Invert the matcher: it matches requests the rest of it does not.
     */
    public function not(bool $not = true): self
    {
        $this->not = $not;
        return $this;
    }

    /**
     * Set the OpenAPI spec of an OpenAPI matcher (see {@see openAPI()}).
     *
     * @param string|array<string, mixed> $specUrlOrPayload
     */
    public function specUrlOrPayload(string|array $specUrlOrPayload): self
    {
        $this->specUrlOrPayload = $specUrlOrPayload;
        return $this;
    }

    /**
     * Restrict an OpenAPI matcher to one operation of its spec.
     */
    public function operationId(string $operationId): self
    {
        $this->operationId = $operationId;
        return $this;
    }

    /**
     * Set the path prefix an OpenAPI matcher strips before matching the spec's paths.
     */
    public function contextPathPrefix(string $contextPathPrefix): self
    {
        $this->contextPathPrefix = $contextPathPrefix;
        return $this;
    }

    /**
     * Set the {@code if} branch of a conditional matcher (see {@see conditional()}).
     */
    public function ifRequest(HttpRequest $ifRequest): self
    {
        $this->ifRequest = $ifRequest;
        return $this;
    }

    /**
     * Set the {@code then} branch of a conditional matcher (see {@see conditional()}).
     */
    public function thenRequest(HttpRequest $thenRequest): self
    {
        $this->thenRequest = $thenRequest;
        return $this;
    }

    /**
     * Set the {@code else} branch of a conditional matcher (see {@see conditional()}).
     */
    public function elseRequest(HttpRequest $elseRequest): self
    {
        $this->elseRequest = $elseRequest;
        return $this;
    }

    public function getMethod(): ?string
    {
        return $this->method;
    }

    public function getPath(): ?string
    {
        return $this->path;
    }

    /**
     * @return array<string, list<string|DateTimeMatcher|array<string, mixed>>>
     */
    public function getQueryStringParameters(): array
    {
        return $this->queryStringParameters;
    }

    /**
     * @return array<string, list<string|DateTimeMatcher|array<string, mixed>>>
     */
    public function getHeaders(): array
    {
        return $this->headers;
    }

    /**
     * Cookies added by {@see cookie()} hold a list of values; cookies read by
     * {@see fromArray()} keep the shape they were read in (usually one value).
     *
     * @return array<string, string|DateTimeMatcher|array<array-key, mixed>>
     */
    public function getCookies(): array
    {
        return $this->cookies;
    }

    /**
     * @return array<string, list<string|DateTimeMatcher|array<string, mixed>>>
     */
    public function getPathParameters(): array
    {
        return $this->pathParameters;
    }

    public function getBody(): string|array|null
    {
        return $this->body;
    }

    /**
     * @return array<string, mixed>|null
     */
    public function getJwt(): ?array
    {
        return $this->jwt;
    }

    public function getNot(): ?bool
    {
        return $this->not;
    }

    /**
     * @return string|array<string, mixed>|null
     */
    public function getSpecUrlOrPayload(): string|array|null
    {
        return $this->specUrlOrPayload;
    }

    public function getOperationId(): ?string
    {
        return $this->operationId;
    }

    public function getContextPathPrefix(): ?string
    {
        return $this->contextPathPrefix;
    }

    public function getIfRequest(): ?HttpRequest
    {
        return $this->ifRequest;
    }

    public function getThenRequest(): ?HttpRequest
    {
        return $this->thenRequest;
    }

    public function getElseRequest(): ?HttpRequest
    {
        return $this->elseRequest;
    }

    /**
     * Whether this is a conditional (if/then/else) matcher.
     */
    public function isConditional(): bool
    {
        return $this->ifRequest !== null;
    }

    /**
     * Whether this is an OpenAPI matcher.
     */
    public function isOpenAPI(): bool
    {
        return $this->specUrlOrPayload !== null;
    }

    /**
     * Read a request matcher from a decoded JSON object, such as the
     * {@code httpRequest} of an expectation the server returns.
     *
     * An {@code httpRequest} holding {@code if} reads as a conditional matcher
     * whose branches are read the same way, recursively; one naming
     * {@code specUrlOrPayload} reads as an OpenAPI matcher. A field this class
     * does not model, or one whose JSON shape it cannot hold (for example a
     * {@code method} written as a {@code {not, value}} object), is kept as read
     * and written back by {@see toArray()}, so nothing is lost.
     *
     * @param array<string, mixed> $data
     */
    public static function fromArray(array $data): self
    {
        $request = new self();
        foreach ($data as $key => $value) {
            if ($value === null) {
                continue;
            }
            $key = (string) $key;
            if (!$request->readField($key, $value)) {
                $request->additionalFields[$key] = $value;
            }
        }
        return $request;
    }

    /**
     * Set the modelled field {@code $key} from its JSON value; false when the
     * field is not modelled or its value does not fit the field's type.
     */
    private function readField(string $key, mixed $value): bool
    {
        switch ($key) {
            case 'method':
            case 'path':
            case 'operationId':
            case 'contextPathPrefix':
                if (!is_string($value)) {
                    return false;
                }
                $this->{$key} = $value;
                return true;
            case 'keepAlive':
            case 'secure':
            case 'not':
                if (!is_bool($value)) {
                    return false;
                }
                $this->{$key} = $value;
                return true;
            case 'queryStringParameters':
            case 'headers':
            case 'cookies':
            case 'pathParameters':
                // toArray() omits these when empty, so keep an empty one raw.
                if (!is_array($value) || $value === []) {
                    return false;
                }
                $this->{$key} = self::readMatcherValues($value);
                return true;
            case 'socketAddress':
            case 'jwt':
                if (!is_array($value)) {
                    return false;
                }
                $this->{$key} = $value;
                return true;
            case 'body':
            case 'specUrlOrPayload':
                if (!is_string($value) && !is_array($value)) {
                    return false;
                }
                $this->{$key} = $value;
                return true;
            case 'if':
            case 'then':
            case 'else':
                if (!is_array($value)) {
                    return false;
                }
                $this->{$key . 'Request'} = self::fromArray($value);
                return true;
            default:
                return false;
        }
    }

    /**
     * Read each date-time matcher value of a header, query string parameter,
     * path parameter or cookie collection as a {@see DateTimeMatcher}; every
     * other value, and the collection's shape (a name-keyed map or a list of
     * {@code {name, values}} entries), is kept as read.
     *
     * @param array<array-key, mixed> $collection
     * @return array<array-key, mixed>
     */
    private static function readMatcherValues(array $collection): array
    {
        $isList = array_is_list($collection);
        foreach ($collection as $key => $entry) {
            if ($isList && is_array($entry) && array_key_exists('name', $entry)) {
                foreach (['values', 'value'] as $field) {
                    if (array_key_exists($field, $entry)) {
                        $entry[$field] = self::readMatcherValue($entry[$field]);
                    }
                }
                $collection[$key] = $entry;
            } else {
                $collection[$key] = self::readMatcherValue($entry);
            }
        }
        return $collection;
    }

    private static function readMatcherValue(mixed $value): mixed
    {
        if (is_array($value) && array_is_list($value)) {
            return array_map(static fn (mixed $v): mixed => DateTimeMatcher::fromArray($v) ?? $v, $value);
        }
        return DateTimeMatcher::fromArray($value) ?? $value;
    }

    /**
     * Write every {@see DateTimeMatcher} in a value collection as its JSON object.
     */
    private static function writeMatcherValues(mixed $value): mixed
    {
        if ($value instanceof DateTimeMatcher) {
            return $value->toArray();
        }
        if (is_array($value)) {
            return array_map(static fn (mixed $v): mixed => self::writeMatcherValues($v), $value);
        }
        return $value;
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

        if ($this->method !== null) {
            $data['method'] = $this->method;
        }
        if ($this->path !== null) {
            $data['path'] = $this->path;
        }
        if (!empty($this->pathParameters)) {
            $data['pathParameters'] = self::writeMatcherValues($this->pathParameters);
        }
        if (!empty($this->queryStringParameters)) {
            $data['queryStringParameters'] = self::writeMatcherValues($this->queryStringParameters);
        }
        if (!empty($this->headers)) {
            $data['headers'] = self::writeMatcherValues($this->headers);
        }
        if (!empty($this->cookies)) {
            $data['cookies'] = self::writeMatcherValues($this->cookies);
        }
        if ($this->body !== null) {
            $data['body'] = $this->body;
        }
        if ($this->jwt !== null) {
            $data['jwt'] = $this->jwt;
        }
        if ($this->keepAlive !== null) {
            $data['keepAlive'] = $this->keepAlive;
        }
        if ($this->secure !== null) {
            $data['secure'] = $this->secure;
        }
        if ($this->socketAddress !== null) {
            $data['socketAddress'] = $this->socketAddress;
        }
        if ($this->not !== null) {
            $data['not'] = $this->not;
        }
        if ($this->specUrlOrPayload !== null) {
            $data['specUrlOrPayload'] = $this->specUrlOrPayload;
        }
        if ($this->operationId !== null) {
            $data['operationId'] = $this->operationId;
        }
        if ($this->contextPathPrefix !== null) {
            $data['contextPathPrefix'] = $this->contextPathPrefix;
        }
        if ($this->ifRequest !== null) {
            $data['if'] = $this->ifRequest->toArray();
        }
        if ($this->thenRequest !== null) {
            $data['then'] = $this->thenRequest->toArray();
        }
        if ($this->elseRequest !== null) {
            $data['else'] = $this->elseRequest->toArray();
        }
        foreach ($this->additionalFields as $key => $value) {
            if (!array_key_exists($key, $data)) {
                $data[$key] = $value;
            }
        }

        return $data;
    }
}
