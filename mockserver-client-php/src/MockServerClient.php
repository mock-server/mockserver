<?php

declare(strict_types=1);

namespace MockServer;

use GuzzleHttp\Client as GuzzleClient;
use GuzzleHttp\Exception\ConnectException;
use GuzzleHttp\Exception\GuzzleException;
use GuzzleHttp\RequestOptions;
use MockServer\Diagnostics\MismatchDebugResult;
use MockServer\Diagnostics\RequestExplanation;
use MockServer\Diagnostics\UnmatchedExplanation;
use MockServer\Exception\ConnectionException;
use MockServer\Exception\FeatureNotEnabledException;
use MockServer\Exception\InvalidRequestException;
use MockServer\Exception\MockServerException;
use MockServer\Exception\VerificationException;

/**
 * PHP client for MockServer.
 *
 * Provides the full MockServer control-plane REST API with a fluent builder DSL.
 *
 * @example Basic usage
 *   $client = new MockServerClient('localhost', 1080);
 *   $client->when(
 *       HttpRequest::request()->method('GET')->path('/hello')
 *   )->respond(
 *       HttpResponse::response()->statusCode(200)->body('world')
 *   );
 *   $client->verify(
 *       HttpRequest::request()->path('/hello'),
 *       VerificationTimes::atLeast(1)
 *   );
 *   $client->reset();
 */
class MockServerClient
{
    /**
     * Operating mode: match expectations only; unmatched requests return 404
     * (proxy-on-no-match disabled). This is the default mode.
     */
    public const MODE_SIMULATE = 'SIMULATE';

    /**
     * Operating mode: match expectations; unmatched requests are forwarded to the
     * real upstream and recorded (proxy-on-no-match enabled).
     */
    public const MODE_SPY = 'SPY';

    /**
     * Operating mode: forward and record. With no expectations defined this
     * captures all traffic (same underlying proxy flag as {@see MODE_SPY}).
     */
    public const MODE_CAPTURE = 'CAPTURE';

    private GuzzleClient $httpClient;
    private string $baseUri;

    /**
     * A static control-plane bearer token, or a callable returning one
     * (a supplier, so the token can refresh between requests). Null when no
     * control-plane authentication is configured.
     *
     * @var string|callable():?string|null
     */
    private $controlPlaneBearerToken;

    /** Path to a PEM CA bundle used to verify the MockServer HTTPS certificate. */
    private ?string $caCertPath = null;

    /** Path to a PEM client certificate presented for mutual TLS. */
    private ?string $clientCertPath = null;

    /** Path to the PEM private key for the client certificate (when separate). */
    private ?string $clientKeyPath = null;

    /**
     * @param string $host MockServer hostname
     * @param int $port MockServer port (default 1080)
     * @param string $contextPath Optional context path prefix
     * @param bool $secure Use HTTPS
     * @param array<string, mixed> $guzzleOptions Additional Guzzle client options
     * @param string|callable():?string|null $controlPlaneBearerToken Optional control-plane
     *        bearer token. May be a string, or a callable returning the current token
     *        (a supplier, evaluated per request so it can refresh). When set, an
     *        {@code Authorization: Bearer <token>} header is attached to every
     *        control-plane request this client issues.
     * @param string|null $caCertPath Optional path to a PEM CA bundle used to verify the
     *        MockServer HTTPS certificate (Guzzle {@see RequestOptions::VERIFY}).
     * @param string|null $clientCertPath Optional path to a PEM client certificate presented
     *        for mutual TLS (Guzzle {@see RequestOptions::CERT}).
     * @param string|null $clientKeyPath Optional path to the PEM private key for the client
     *        certificate when it is stored separately (Guzzle {@see RequestOptions::SSL_KEY}).
     */
    public function __construct(
        string $host = 'localhost',
        int $port = 1080,
        string $contextPath = '',
        bool $secure = false,
        array $guzzleOptions = [],
        string|callable|null $controlPlaneBearerToken = null,
        ?string $caCertPath = null,
        ?string $clientCertPath = null,
        ?string $clientKeyPath = null,
    ) {
        $scheme = $secure ? 'https' : 'http';
        $ctxPath = '';
        if ($contextPath !== '') {
            $ctxPath = str_starts_with($contextPath, '/') ? $contextPath : '/' . $contextPath;
        }
        $this->baseUri = "{$scheme}://{$host}:{$port}{$ctxPath}";

        $this->controlPlaneBearerToken = $controlPlaneBearerToken;
        $this->caCertPath = $caCertPath;
        $this->clientCertPath = $clientCertPath;
        $this->clientKeyPath = $clientKeyPath;

        $defaultOptions = [
            'base_uri' => $this->baseUri,
            'http_errors' => false,
            RequestOptions::TIMEOUT => 60,
            RequestOptions::CONNECT_TIMEOUT => 10,
            RequestOptions::HEADERS => [
                'Content-Type' => 'application/json; charset=utf-8',
            ],
        ];

        $this->httpClient = new GuzzleClient(array_merge($defaultOptions, $guzzleOptions));
    }

    // -----------------------------------------------------------------
    // Control-plane authentication + TLS/mTLS (fluent setters)
    // -----------------------------------------------------------------

    /**
     * Configure a control-plane bearer token.
     *
     * The token is sent as {@code Authorization: Bearer <token>} on every
     * control-plane request this client issues. Supply either a fixed string or
     * a callable (supplier) that returns the current token; the callable is
     * evaluated per request so a rotating/refreshing token works transparently.
     *
     * Passing {@code null} clears any previously configured token.
     *
     * @param string|callable():?string|null $token A token string, a supplier
     *        callable returning the token, or null to clear.
     * @return $this
     */
    public function withControlPlaneBearerToken(string|callable|null $token): self
    {
        $this->controlPlaneBearerToken = $token;
        return $this;
    }

    /**
     * Trust a specific CA certificate when connecting to MockServer over HTTPS.
     *
     * @param string|null $caCertPath Path to a PEM CA bundle (Guzzle
     *        {@see RequestOptions::VERIFY}), or null to clear.
     * @return $this
     */
    public function withCaCertificate(?string $caCertPath): self
    {
        $this->caCertPath = $caCertPath;
        return $this;
    }

    /**
     * Present a client certificate (and optional separate private key) for
     * mutual TLS to a MockServer that requires client authentication.
     *
     * @param string|null $clientCertPath Path to a PEM client certificate (Guzzle
     *        {@see RequestOptions::CERT}), or null to clear.
     * @param string|null $clientKeyPath Optional path to the PEM private key when
     *        stored separately from the certificate (Guzzle {@see RequestOptions::SSL_KEY}).
     * @return $this
     */
    public function withClientCertificate(?string $clientCertPath, ?string $clientKeyPath = null): self
    {
        $this->clientCertPath = $clientCertPath;
        $this->clientKeyPath = $clientKeyPath;
        return $this;
    }

    /**
     * Build the per-request Guzzle options carrying control-plane auth + TLS/mTLS.
     *
     * The returned array contains an {@code Authorization} header (when a bearer
     * token is configured) and {@see RequestOptions::VERIFY} /
     * {@see RequestOptions::CERT} / {@see RequestOptions::SSL_KEY} (when CA /
     * client-cert paths are configured).
     *
     * @return array<string, mixed>
     */
    private function controlPlaneOptions(): array
    {
        $options = [];

        $token = $this->controlPlaneBearerToken;
        if (is_callable($token)) {
            $token = $token();
        }
        if (is_string($token) && $token !== '') {
            $options[RequestOptions::HEADERS] = [
                'Authorization' => 'Bearer ' . $token,
            ];
        }

        if ($this->caCertPath !== null) {
            $options[RequestOptions::VERIFY] = $this->caCertPath;
        }
        if ($this->clientCertPath !== null) {
            $options[RequestOptions::CERT] = $this->clientCertPath;
        }
        if ($this->clientKeyPath !== null) {
            $options[RequestOptions::SSL_KEY] = $this->clientKeyPath;
        }

        return $options;
    }

    /**
     * Merge control-plane auth/TLS options into a set of per-request options.
     *
     * Header maps are merged key-wise (so a per-call Content-Type override and the
     * Authorization header coexist); per-call values take precedence on conflict.
     *
     * @param array<string, mixed> $perRequest
     * @return array<string, mixed>
     */
    private function withControlPlaneOptions(array $perRequest): array
    {
        $base = $this->controlPlaneOptions();

        $baseHeaders = $base[RequestOptions::HEADERS] ?? [];
        $perRequestHeaders = $perRequest[RequestOptions::HEADERS] ?? [];

        $merged = array_merge($base, $perRequest);

        if ($baseHeaders !== [] || $perRequestHeaders !== []) {
            // Per-request headers win on conflict, but the Authorization header
            // from the control plane is preserved when not overridden.
            $merged[RequestOptions::HEADERS] = array_merge($baseHeaders, $perRequestHeaders);
        }

        return $merged;
    }

    // -----------------------------------------------------------------
    // Fluent API
    // -----------------------------------------------------------------

    /**
     * Begin building an expectation via the fluent when/respond API.
     *
     * @param HttpRequest $request The request matcher
     * @param Times|null $times How many times to match (null = unlimited)
     * @param TimeToLive|null $timeToLive How long the expectation lives (null = forever)
     * @param int|null $priority Expectation priority
     * @return ForwardChainExpectation
     */
    public function when(
        HttpRequest $request,
        ?Times $times = null,
        ?TimeToLive $timeToLive = null,
        ?int $priority = null,
    ): ForwardChainExpectation {
        $expectation = new Expectation();
        $expectation->httpRequest($request);

        if ($times !== null) {
            $expectation->times($times);
        }
        if ($timeToLive !== null) {
            $expectation->timeToLive($timeToLive);
        }
        if ($priority !== null) {
            $expectation->priority($priority);
        }

        return new ForwardChainExpectation($this, $expectation);
    }

    // -----------------------------------------------------------------
    // Core API methods
    // -----------------------------------------------------------------

    /**
     * Create or update an expectation.
     *
     * @internal Called by ForwardChainExpectation; prefer when()->respond() for external use.
     * @param Expectation $expectation
     * @return array The server response (created expectations).
     * @throws InvalidRequestException
     * @throws MockServerException
     */
    public function upsertExpectation(Expectation $expectation): array
    {
        $body = json_encode([$expectation->toArray()], JSON_THROW_ON_ERROR);
        $response = $this->put('/mockserver/expectation', $body);

        $status = $response->getStatusCode();
        $responseBody = (string) $response->getBody();

        if ($status === 400 || $status === 406) {
            throw new InvalidRequestException("Invalid expectation: {$responseBody}");
        }
        if ($status >= 400) {
            throw new MockServerException("Failed to create expectation (HTTP {$status}): {$responseBody}");
        }

        if ($responseBody !== '') {
            $parsed = json_decode($responseBody, true);
            if (is_array($parsed)) {
                return $parsed;
            }
        }

        return [$expectation->toArray()];
    }

    /**
     * Verify that a request was received the expected number of times.
     *
     * Optionally also verify the response that was returned. When both
     * $request and $httpResponse are provided, the server checks that the
     * request was received AND the matching response was returned.
     *
     * @param HttpRequest $request The request to verify
     * @param VerificationTimes|null $times Expected call count (null = at least once)
     * @param HttpResponse|null $httpResponse Optional response matcher
     * @throws VerificationException If verification fails
     * @throws MockServerException On communication errors
     */
    public function verify(
        HttpRequest $request,
        ?VerificationTimes $times = null,
        ?HttpResponse $httpResponse = null,
    ): void {
        $payload = ['httpRequest' => $request->toArray() ?: new \stdClass()];
        if ($httpResponse !== null) {
            $payload['httpResponse'] = $httpResponse->toArray();
        }
        if ($times !== null) {
            $payload['times'] = $times->toArray();
        }

        $body = json_encode($payload, JSON_THROW_ON_ERROR);
        $response = $this->put('/mockserver/verify', $body);

        $status = $response->getStatusCode();
        $responseBody = (string) $response->getBody();

        if ($status === 406) {
            throw new VerificationException($responseBody ?: 'Verification failed');
        }
        if ($status >= 400) {
            throw new MockServerException("Verification request failed (HTTP {$status}): {$responseBody}");
        }
    }

    /**
     * Verify that zero interactions (requests) were received by the server.
     *
     * Sends {@code {"httpRequest": {}, "times": {"atMost": 0}}} so the server
     * checks that no request matching the empty (match-everything) matcher was
     * received — i.e. the server received no requests at all.
     *
     * @throws VerificationException If any request was received
     * @throws MockServerException On communication errors
     */
    public function verifyZeroInteractions(): void
    {
        $this->verify(HttpRequest::request(), VerificationTimes::atMost(0));
    }

    /**
     * Verify that a request+response pair was received the expected number of times.
     *
     * Convenience method combining request and response matchers.
     *
     * @param HttpRequest $request The request matcher
     * @param HttpResponse $httpResponse The response matcher
     * @param VerificationTimes|null $times Expected call count (null = at least once)
     * @throws VerificationException If verification fails
     * @throws MockServerException On communication errors
     */
    public function verifyRequestAndResponse(
        HttpRequest $request,
        HttpResponse $httpResponse,
        ?VerificationTimes $times = null,
    ): void {
        $this->verify($request, $times, $httpResponse);
    }

    /**
     * Verify that a response was returned the expected number of times,
     * regardless of which request triggered it (response-only verification).
     *
     * The httpRequest field is omitted from the JSON payload so the server
     * matches any request that produced a response matching $httpResponse.
     *
     * @param HttpResponse $httpResponse The response matcher
     * @param VerificationTimes|null $times Expected call count (null = at least once)
     * @throws VerificationException If verification fails
     * @throws MockServerException On communication errors
     */
    public function verifyResponse(
        HttpResponse $httpResponse,
        ?VerificationTimes $times = null,
    ): void {
        $payload = ['httpResponse' => $httpResponse->toArray()];
        if ($times !== null) {
            $payload['times'] = $times->toArray();
        }

        $body = json_encode($payload, JSON_THROW_ON_ERROR);
        $response = $this->put('/mockserver/verify', $body);

        $status = $response->getStatusCode();
        $responseBody = (string) $response->getBody();

        if ($status === 406) {
            throw new VerificationException($responseBody ?: 'Verification failed');
        }
        if ($status >= 400) {
            throw new MockServerException("Verification request failed (HTTP {$status}): {$responseBody}");
        }
    }

    /**
     * Verify that requests were received in the specified sequence.
     *
     * @param HttpRequest ...$requests The requests in expected order
     * @throws VerificationException If sequence verification fails
     * @throws MockServerException On communication errors
     */
    public function verifySequence(HttpRequest ...$requests): void
    {
        $payload = [
            'httpRequests' => array_map(fn(HttpRequest $r) => $r->toArray() ?: new \stdClass(), $requests),
        ];

        $body = json_encode($payload, JSON_THROW_ON_ERROR);
        $response = $this->put('/mockserver/verifySequence', $body);

        $status = $response->getStatusCode();
        $responseBody = (string) $response->getBody();

        if ($status === 406) {
            throw new VerificationException($responseBody ?: 'Sequence verification failed');
        }
        if ($status >= 400) {
            throw new MockServerException("Verify sequence request failed (HTTP {$status}): {$responseBody}");
        }
    }

    /**
     * Verify that requests were received in sequence with specific responses.
     *
     * Responses are index-aligned with requests: $responses[0] is the expected
     * response for $requests[0], etc. Both arrays must have the same length.
     *
     * When a request entry is null, the httpRequests array entry is omitted
     * for that index (response-only verification for that position).
     *
     * @param array<HttpRequest|null> $requests Request matchers (null entries omitted from payload)
     * @param array<HttpResponse> $responses Response matchers (index-aligned with requests)
     * @throws \InvalidArgumentException If array lengths differ
     * @throws VerificationException If sequence verification fails
     * @throws MockServerException On communication errors
     */
    public function verifySequenceWithResponses(array $requests, array $responses): void
    {
        if (count($requests) !== count($responses)) {
            throw new \InvalidArgumentException(
                sprintf(
                    'Requests and responses arrays must have the same length, got %d requests and %d responses',
                    count($requests),
                    count($responses),
                )
            );
        }

        $payload = [];

        // Build httpRequests array — null entries become absent (not serialized as null)
        $hasRequests = false;
        $httpRequests = [];
        foreach ($requests as $request) {
            if ($request !== null) {
                $hasRequests = true;
                $httpRequests[] = $request->toArray() ?: new \stdClass();
            } else {
                // Placeholder to maintain index alignment; server expects
                // httpRequests and httpResponses to be index-aligned
                $httpRequests[] = new \stdClass();
            }
        }
        if ($hasRequests) {
            $payload['httpRequests'] = $httpRequests;
        }

        $payload['httpResponses'] = array_map(
            fn(HttpResponse $r) => $r->toArray(),
            $responses,
        );

        $body = json_encode($payload, JSON_THROW_ON_ERROR);
        $response = $this->put('/mockserver/verifySequence', $body);

        $status = $response->getStatusCode();
        $responseBody = (string) $response->getBody();

        if ($status === 406) {
            throw new VerificationException($responseBody ?: 'Sequence verification failed');
        }
        if ($status >= 400) {
            throw new MockServerException("Verify sequence request failed (HTTP {$status}): {$responseBody}");
        }
    }

    /**
     * Clear expectations and/or logs matching the request.
     *
     * @param HttpRequest|null $request Matcher to select what to clear (null = clear all)
     * @param string|null $type "EXPECTATIONS", "LOG", or "ALL" (null = ALL)
     */
    public function clear(?HttpRequest $request = null, ?string $type = null): void
    {
        $path = '/mockserver/clear';
        if ($type !== null) {
            $path .= '?type=' . urlencode($type);
        }

        $body = $request !== null ? json_encode($request->toArray() ?: new \stdClass(), JSON_THROW_ON_ERROR) : '';
        $response = $this->put($path, $body);

        $status = $response->getStatusCode();
        if ($status >= 400) {
            $responseBody = (string) $response->getBody();
            throw new MockServerException("Clear failed (HTTP {$status}): {$responseBody}");
        }
    }

    /**
     * Clear by expectation ID.
     *
     * @param string $expectationId The ID of the expectation to clear
     * @param string|null $type "EXPECTATIONS", "LOG", or "ALL"
     */
    public function clearById(string $expectationId, ?string $type = null): void
    {
        $path = '/mockserver/clear';
        if ($type !== null) {
            $path .= '?type=' . urlencode($type);
        }

        $body = json_encode(['id' => $expectationId], JSON_THROW_ON_ERROR);
        $response = $this->put($path, $body);

        $status = $response->getStatusCode();
        if ($status >= 400) {
            $responseBody = (string) $response->getBody();
            throw new MockServerException("Clear by ID failed (HTTP {$status}): {$responseBody}");
        }
    }

    /**
     * Clear only the expectations that match both the request matcher and the
     * filter, e.g. by metadata or because they have never matched a request.
     * The event log is left untouched.
     *
     * @param HttpRequest|null $request Matcher (null = every expectation)
     * @param ExpectationFilter $filter Metadata and/or unmatched selection (no paging)
     * @throws \InvalidArgumentException If the filter selects nothing or pages;
     *         nothing is sent, because a filter that selects nothing would clear
     *         every expectation
     */
    public function clearWithFilter(?HttpRequest $request, ExpectationFilter $filter): void
    {
        if (!$filter->hasSelection()) {
            throw new \InvalidArgumentException(
                'clear with a filter requires a metadata or unmatched selection; use clear() to clear without one'
            );
        }
        if ($filter->hasPaging()) {
            throw new \InvalidArgumentException('limit and offset are not supported for clear');
        }

        $path = '/mockserver/clear?type=expectations&' . $filter->toQueryString();
        $body = $request !== null ? json_encode($request->toArray() ?: new \stdClass(), JSON_THROW_ON_ERROR) : '';
        $response = $this->put($path, $body);

        $status = $response->getStatusCode();
        if ($status >= 400) {
            $responseBody = (string) $response->getBody();
            throw new MockServerException("Clear failed (HTTP {$status}): {$responseBody}");
        }
    }

    /**
     * Clear the expectations whose metadata has {@code $key} with a value
     * equal to {@code $value} (see {@see ExpectationFilter::withMetadata()}).
     *
     * @throws \InvalidArgumentException If the key is blank or the value is null
     */
    public function clearByMetadata(string $key, mixed $value): void
    {
        $this->clearWithFilter(null, ExpectationFilter::expectationFilter()->withMetadata($key, $value));
    }

    /**
     * Clear the expectations whose metadata has {@code $key}, with any value.
     *
     * @throws \InvalidArgumentException If the key is blank
     */
    public function clearByMetadataKey(string $key): void
    {
        $this->clearWithFilter(null, ExpectationFilter::expectationFilter()->withMetadataKey($key));
    }

    /**
     * Clear the expectations that have never matched a request.
     */
    public function clearUnmatchedExpectations(): void
    {
        $this->clearWithFilter(null, ExpectationFilter::expectationFilter()->withUnmatched(true));
    }

    /**
     * Reset MockServer: remove all expectations and clear all recorded requests.
     */
    public function reset(): void
    {
        $response = $this->put('/mockserver/reset', '');

        $status = $response->getStatusCode();
        if ($status >= 400) {
            $responseBody = (string) $response->getBody();
            throw new MockServerException("Reset failed (HTTP {$status}): {$responseBody}");
        }
    }

    /**
     * Retrieve recorded requests matching the given filter.
     *
     * @param HttpRequest|null $request Filter (null = all)
     * @return array List of recorded request arrays
     */
    public function retrieveRecordedRequests(?HttpRequest $request = null): array
    {
        return $this->retrieve($request, 'REQUESTS', 'JSON');
    }

    /**
     * Retrieve active expectations matching the given filter.
     *
     * @param HttpRequest|null $request Filter (null = all)
     * @return array List of expectation arrays
     */
    public function retrieveActiveExpectations(?HttpRequest $request = null): array
    {
        return $this->retrieve($request, 'ACTIVE_EXPECTATIONS', 'JSON');
    }

    /**
     * Retrieve recorded expectations matching the given filter.
     *
     * @param HttpRequest|null $request Filter (null = all)
     * @return array List of expectation arrays
     */
    public function retrieveRecordedExpectations(?HttpRequest $request = null): array
    {
        return $this->retrieve($request, 'RECORDED_EXPECTATIONS', 'JSON');
    }

    /**
     * Retrieve the active expectations matching the request matcher and the
     * filter: by metadata, only those never matched, and/or one page
     * ({@code limit} / {@code offset}).
     *
     * Each metadata object is returned exactly as sent (empty or list-like JSON
     * objects as {@code \stdClass}), so passing an entry to
     * {@see Expectation::fromArray()} re-submits it unchanged.
     *
     * @param HttpRequest|null $request Matcher (null = all)
     * @param ExpectationFilter $filter The selection and paging
     * @return array List of expectation arrays
     */
    public function retrieveActiveExpectationsWithFilter(?HttpRequest $request, ExpectationFilter $filter): array
    {
        return $this->retrieveFiltered($request, 'ACTIVE_EXPECTATIONS', $filter);
    }

    /**
     * Retrieve the active expectations whose metadata has {@code $key} with a
     * value equal to {@code $value} (see {@see ExpectationFilter::withMetadata()}).
     *
     * @throws \InvalidArgumentException If the key is blank or the value is null
     */
    public function retrieveActiveExpectationsByMetadata(string $key, mixed $value): array
    {
        return $this->retrieveActiveExpectationsWithFilter(
            null,
            ExpectationFilter::expectationFilter()->withMetadata($key, $value),
        );
    }

    /**
     * Retrieve the active expectations whose metadata has {@code $key}, with any value.
     *
     * @throws \InvalidArgumentException If the key is blank
     */
    public function retrieveActiveExpectationsByMetadataKey(string $key): array
    {
        return $this->retrieveActiveExpectationsWithFilter(
            null,
            ExpectationFilter::expectationFilter()->withMetadataKey($key),
        );
    }

    /**
     * Retrieve the active expectations that have never matched a request.
     */
    public function retrieveUnmatchedExpectations(): array
    {
        return $this->retrieveActiveExpectationsWithFilter(
            null,
            ExpectationFilter::expectationFilter()->withUnmatched(true),
        );
    }

    /**
     * Retrieve one page of the recorded expectations matching the request
     * matcher, in the order the requests were received.
     *
     * @param HttpRequest|null $request Matcher (null = all)
     * @param int $offset Number of recorded expectations to skip, zero or more
     * @param int $limit Maximum number to return, zero or more
     * @throws \InvalidArgumentException If the offset or limit is negative
     */
    public function retrieveRecordedExpectationsPage(?HttpRequest $request, int $offset, int $limit): array
    {
        return $this->retrieveFiltered(
            $request,
            'RECORDED_EXPECTATIONS',
            ExpectationFilter::expectationFilter()->withOffset($offset)->withLimit($limit),
        );
    }

    /**
     * Retrieve the active expectations as MockServer SDK setup code (the builder
     * code that recreates the expectations) in the requested language.
     *
     * @param string $format One of java, javascript, python, go, csharp, ruby,
     *                        rust or php (case-insensitive)
     * @param HttpRequest|null $request Filter (null = all)
     * @return string The generated code
     */
    public function retrieveExpectationsAsCode(string $format = 'java', ?HttpRequest $request = null): string
    {
        return $this->retrieveRaw($request, 'ACTIVE_EXPECTATIONS', strtoupper($format));
    }

    /**
     * Retrieve the recorded (proxied) request/response pairs as MockServer SDK
     * setup code in the requested language.
     *
     * @param string $format One of java, javascript, python, go, csharp, ruby,
     *                        rust or php (case-insensitive)
     * @param HttpRequest|null $request Filter (null = all)
     * @return string The generated code
     */
    public function retrieveRecordedExpectationsAsCode(string $format = 'java', ?HttpRequest $request = null): string
    {
        return $this->retrieveRaw($request, 'RECORDED_EXPECTATIONS', strtoupper($format));
    }

    /**
     * Retrieve log messages matching the given filter.
     *
     * @param HttpRequest|null $request Filter (null = all)
     * @return array List of log entry strings/arrays
     */
    public function retrieveLogMessages(?HttpRequest $request = null): array
    {
        return $this->retrieve($request, 'LOGS', 'JSON');
    }

    /**
     * Retrieve the recorded requests matching the request of the expectation with the given ID.
     *
     * @param string $expectationId The ID of the expectation to filter by
     * @return array List of recorded request arrays
     */
    public function retrieveRecordedRequestsById(string $expectationId): array
    {
        return $this->retrieveById($expectationId, 'REQUESTS', 'JSON');
    }

    /**
     * Retrieve the active expectation with the given ID.
     *
     * Unlike retrieveActiveExpectations() this returns only the expectation with that ID,
     * never other expectations whose matcher happens to match the same request.
     *
     * @param string $expectationId The ID of the expectation to retrieve
     * @return array List containing the expectation with that ID
     */
    public function retrieveActiveExpectationsById(string $expectationId): array
    {
        return $this->retrieveById($expectationId, 'ACTIVE_EXPECTATIONS', 'JSON');
    }

    /**
     * Retrieve the recorded expectations matching the request of the expectation with the given ID.
     *
     * @param string $expectationId The ID of the expectation to filter by
     * @return array List of expectation arrays
     */
    public function retrieveRecordedExpectationsById(string $expectationId): array
    {
        return $this->retrieveById($expectationId, 'RECORDED_EXPECTATIONS', 'JSON');
    }

    /**
     * Retrieve the recorded request/response pairs matching the request of the expectation
     * with the given ID.
     *
     * @param string $expectationId The ID of the expectation to filter by
     * @return array List of request/response arrays
     */
    public function retrieveRecordedRequestsAndResponsesById(string $expectationId): array
    {
        return $this->retrieveById($expectationId, 'REQUEST_RESPONSES', 'JSON');
    }

    /**
     * Retrieve the log messages matching the request of the expectation with the given ID.
     *
     * @param string $expectationId The ID of the expectation to filter by
     * @return array List of log entry strings/arrays
     */
    public function retrieveLogMessagesById(string $expectationId): array
    {
        return $this->retrieveById($expectationId, 'LOGS', 'JSON');
    }

    // -----------------------------------------------------------------
    // Match diagnostics
    // -----------------------------------------------------------------

    /**
     * Explain how a recorded request would be served now.
     *
     * Re-checks the request against the current expectations, in the order
     * MockServer evaluates them, and returns the expectation that would serve
     * it, why each expectation ahead of that one was passed over, and which
     * lower-priority expectations it shadows. Read-only: it consumes no Times
     * and moves no scenario.
     *
     * @param string $correlationId The correlation ID of the request's log entries.
     * @return RequestExplanation The explanation.
     * @throws \InvalidArgumentException If the correlation ID is blank.
     * @throws InvalidRequestException If no request is recorded with this correlation ID
     *         (never received, cleared or evicted; HTTP 404) or it is not an HTTP request (HTTP 400).
     * @throws MockServerException On other server errors or an unreadable response.
     */
    public function explain(string $correlationId): RequestExplanation
    {
        if (trim($correlationId) === '') {
            throw new \InvalidArgumentException('correlationId is required');
        }
        $response = $this->put('/mockserver/explain?correlationId=' . rawurlencode($correlationId), '');

        return RequestExplanation::fromArray(
            $this->decodeDiagnosticResponse($response, "explain request '{$correlationId}'")
        );
    }

    /**
     * Compare a request with every active expectation and return, for each,
     * whether it matches and which fields differ, plus the closest match.
     * Nothing is recorded or consumed.
     *
     * @param HttpRequest $request The request to compare with the active expectations.
     * @return MismatchDebugResult The per-expectation comparison.
     * @throws InvalidRequestException If the server rejects the request (HTTP 400).
     * @throws MockServerException On other server errors or an unreadable response.
     */
    public function debugMismatch(HttpRequest $request): MismatchDebugResult
    {
        $body = json_encode($request->toArray() ?: new \stdClass(), JSON_THROW_ON_ERROR);
        $response = $this->put('/mockserver/debugMismatch', $body);

        return MismatchDebugResult::fromArray($this->decodeDiagnosticResponse($response, 'debug mismatch'));
    }

    /**
     * Explain why recent requests matched no expectation: for each recorded
     * unmatched request, the evaluated expectations ranked closest first, with
     * their differences and remediation hints.
     *
     * @param int|null $limit How many recent unmatched requests to explain; the
     *        server default (10) when null.
     * @param bool $suggestFixes Also suggest, for each of the five closest
     *        non-matching expectations, a JSON patch that would make it match.
     *        Nothing is applied.
     * @return UnmatchedExplanation The unmatched requests and their closest expectations.
     * @throws \InvalidArgumentException If the limit is less than 1.
     * @throws MockServerException On a server error or an unreadable response.
     */
    public function explainUnmatched(?int $limit = null, bool $suggestFixes = false): UnmatchedExplanation
    {
        if ($limit !== null && $limit < 1) {
            throw new \InvalidArgumentException('limit must be at least 1');
        }
        $payload = [];
        if ($limit !== null) {
            $payload['limit'] = $limit;
        }
        if ($suggestFixes) {
            $payload['suggestFixes'] = true;
        }
        $response = $this->put('/mockserver/explainUnmatched', json_encode((object) $payload, JSON_THROW_ON_ERROR));

        return UnmatchedExplanation::fromArray(
            $this->decodeDiagnosticResponse($response, 'explain unmatched requests')
        );
    }

    /**
     * Check the server status (bound ports).
     *
     * @return array{ports: list<int>} Status response
     */
    public function status(): array
    {
        $response = $this->put('/mockserver/status', '');

        $status = $response->getStatusCode();
        $responseBody = (string) $response->getBody();

        if ($status >= 400) {
            throw new MockServerException("Status request failed (HTTP {$status}): {$responseBody}");
        }

        if ($responseBody !== '') {
            $parsed = json_decode($responseBody, true);
            if (is_array($parsed)) {
                return $parsed;
            }
        }

        return ['ports' => []];
    }

    /**
     * Bind additional ports.
     *
     * @param int ...$ports Ports to bind
     * @return array{ports: list<int>} Bound ports response
     */
    public function bind(int ...$ports): array
    {
        $body = json_encode(['ports' => array_values($ports)], JSON_THROW_ON_ERROR);
        $response = $this->put('/mockserver/bind', $body);

        $status = $response->getStatusCode();
        $responseBody = (string) $response->getBody();

        if ($status >= 400) {
            throw new MockServerException("Bind failed (HTTP {$status}): {$responseBody}");
        }

        if ($responseBody !== '') {
            $parsed = json_decode($responseBody, true);
            if (is_array($parsed)) {
                return $parsed;
            }
        }

        return ['ports' => []];
    }

    // -----------------------------------------------------------------
    // Stateful scenarios
    // -----------------------------------------------------------------

    /**
     * Obtain a typed handle for a named scenario state machine.
     *
     * @param string $name The scenario name.
     * @return ScenarioHandle A handle exposing state()/set()/trigger().
     */
    public function scenario(string $name): ScenarioHandle
    {
        return new ScenarioHandle($this, $name);
    }

    /**
     * List all known scenarios and their current states.
     *
     * @return array<string, mixed> {@code {"scenarios":[{"scenarioName","currentState"}]}}.
     * @throws MockServerException On communication errors.
     */
    public function scenarios(): array
    {
        $response = $this->get('/mockserver/scenario');

        return $this->decodeScenarioResponse($response, 'list scenarios');
    }

    /**
     * Get the current state of a single scenario.
     *
     * @internal Prefer {@see MockServerClient::scenario()}->state().
     * @param string $name The scenario name.
     * @return array<string, mixed> {@code {"scenarioName","currentState"}}.
     * @throws MockServerException On communication errors.
     */
    public function getScenario(string $name): array
    {
        $response = $this->get('/mockserver/scenario/' . rawurlencode($name));

        return $this->decodeScenarioResponse($response, 'get scenario');
    }

    /**
     * Set a scenario's state, optionally scheduling a timed transition.
     *
     * @internal Prefer {@see MockServerClient::scenario()}->set().
     * @param string $name The scenario name.
     * @param string $state The state to set immediately.
     * @param int|null $transitionAfterMs Milliseconds after which to transition.
     * @param string|null $nextState The state to transition to.
     * @return array<string, mixed> {@code {"scenarioName","currentState",...}}.
     * @throws InvalidRequestException If the request is rejected (HTTP 400).
     * @throws MockServerException On communication errors.
     */
    public function setScenario(
        string $name,
        string $state,
        ?int $transitionAfterMs = null,
        ?string $nextState = null,
    ): array {
        $payload = ['state' => $state];
        if ($transitionAfterMs !== null) {
            $payload['transitionAfterMs'] = $transitionAfterMs;
        }
        if ($nextState !== null) {
            $payload['nextState'] = $nextState;
        }

        $body = json_encode($payload, JSON_THROW_ON_ERROR);
        $response = $this->put('/mockserver/scenario/' . rawurlencode($name), $body);

        return $this->decodeScenarioResponse($response, 'set scenario');
    }

    /**
     * Trigger an immediate transition of a scenario to a new state.
     *
     * @internal Prefer {@see MockServerClient::scenario()}->trigger().
     * @param string $name The scenario name.
     * @param string $newState The state to transition to.
     * @return array<string, mixed> {@code {"scenarioName","currentState"}}.
     * @throws InvalidRequestException If the request is rejected (HTTP 400).
     * @throws MockServerException On communication errors.
     */
    public function triggerScenario(string $name, string $newState): array
    {
        $body = json_encode(['newState' => $newState], JSON_THROW_ON_ERROR);
        $response = $this->put('/mockserver/scenario/' . rawurlencode($name) . '/trigger', $body);

        return $this->decodeScenarioResponse($response, 'trigger scenario');
    }

    // -----------------------------------------------------------------
    // OpenAPI import
    // -----------------------------------------------------------------

    /**
     * Import expectations from an OpenAPI / Swagger specification.
     *
     * The spec may be supplied as a URL, a file/classpath path, or inline
     * (JSON or YAML) via {@see OpenAPIExpectation::openAPI()}.
     *
     * @param OpenAPIExpectation $expectation The OpenAPI definition to import
     * @throws InvalidRequestException If the spec is rejected by the server
     * @throws MockServerException On communication errors
     */
    public function openApiExpectation(OpenAPIExpectation $expectation): void
    {
        $body = json_encode($expectation->toArray(), JSON_THROW_ON_ERROR);
        $response = $this->put('/mockserver/openapi', $body);

        $status = $response->getStatusCode();
        $responseBody = (string) $response->getBody();

        if ($status === 400 || $status === 406) {
            throw new InvalidRequestException("Invalid OpenAPI expectation: {$responseBody}");
        }
        if ($status >= 400) {
            throw new MockServerException("Failed to create OpenAPI expectation (HTTP {$status}): {$responseBody}");
        }
    }

    // -----------------------------------------------------------------
    // gRPC descriptor management
    // -----------------------------------------------------------------

    /**
     * Upload a compiled protobuf descriptor set so gRPC requests can be matched.
     *
     * The bytes must be the raw contents of a {@code FileDescriptorSet} (e.g. the
     * output of {@code protoc --descriptor_set_out=... --include_imports}). They
     * are sent verbatim as {@code application/octet-stream} (NOT base64-encoded).
     *
     * @param string $descriptorBytes Raw descriptor set bytes (binary string)
     * @throws \InvalidArgumentException If the descriptor bytes are empty
     * @throws InvalidRequestException If the descriptor set is rejected
     * @throws MockServerException On communication errors
     */
    public function uploadGrpcDescriptor(string $descriptorBytes): void
    {
        if ($descriptorBytes === '') {
            throw new \InvalidArgumentException('descriptor bytes must not be empty');
        }

        $response = $this->put(
            '/mockserver/grpc/descriptors',
            $descriptorBytes,
            'application/octet-stream',
        );

        $status = $response->getStatusCode();
        $responseBody = (string) $response->getBody();

        if ($status === 400) {
            throw new InvalidRequestException("Invalid gRPC descriptor: {$responseBody}");
        }
        if ($status >= 400) {
            throw new MockServerException("Failed to upload gRPC descriptor (HTTP {$status}): {$responseBody}");
        }
    }

    /**
     * Retrieve the gRPC services registered from uploaded descriptor sets.
     *
     * Each entry has a "name" and a list of "methods", where each method has
     * "name", "inputType", "outputType", "clientStreaming", "serverStreaming".
     *
     * @return array<int, array<string, mixed>> List of service descriptors
     * @throws MockServerException On communication errors
     */
    public function retrieveGrpcServices(): array
    {
        $response = $this->put('/mockserver/grpc/services', '');

        $status = $response->getStatusCode();
        $responseBody = (string) $response->getBody();

        if ($status >= 400) {
            throw new MockServerException("Failed to retrieve gRPC services (HTTP {$status}): {$responseBody}");
        }

        if ($responseBody !== '') {
            $parsed = json_decode($responseBody, true);
            if (is_array($parsed)) {
                return $parsed;
            }
        }

        return [];
    }

    /**
     * Clear all uploaded gRPC descriptor sets and registered services.
     *
     * @throws MockServerException On communication errors
     */
    public function clearGrpcDescriptors(): void
    {
        $response = $this->put('/mockserver/grpc/clear', '');

        $status = $response->getStatusCode();
        if ($status >= 400) {
            $responseBody = (string) $response->getBody();
            throw new MockServerException("Failed to clear gRPC descriptors (HTTP {$status}): {$responseBody}");
        }
    }

    /**
     * Check if MockServer has started (polls with retries).
     *
     * @param int $attempts Number of attempts
     * @param float $timeout Seconds to wait between attempts
     * @return bool True if server is reachable
     */
    public function hasStarted(int $attempts = 10, float $timeout = 0.5): bool
    {
        for ($i = 0; $i < $attempts; $i++) {
            try {
                $response = $this->put('/mockserver/status', '');
                if ($response->getStatusCode() === 200) {
                    return true;
                }
            } catch (ConnectionException) {
                // Not yet started, retry
            }

            if ($i < $attempts - 1) {
                usleep((int) ($timeout * 1_000_000));
            }
        }

        return false;
    }

    /**
     * Get the base URI of this client.
     */
    public function getBaseUri(): string
    {
        return $this->baseUri;
    }

    // -----------------------------------------------------------------
    // SRE control-plane: load generation
    // -----------------------------------------------------------------

    /**
     * Register (load) an API load scenario in the server's registry.
     *
     * A scenario is an ordered list of templated request steps fired at a target
     * load described by a staged {@see LoadProfile}. Each scenario carries a
     * unique {@code name} the registry keys it by. Registration only loads the
     * scenario; it does not drive any load — call {@see startLoadScenarios()} to
     * run it. Registration is allowed even when load generation is disabled.
     *
     * @param LoadScenario|array<string, mixed> $scenario The scenario, as a
     *        {@see LoadScenario} value object or an equivalent camelCase array.
     * @return array<string, mixed> The registered reference ({name, state}).
     * @throws InvalidRequestException If the scenario is rejected (HTTP 400).
     * @throws MockServerException On communication errors.
     */
    public function loadScenario(LoadScenario|array $scenario): array
    {
        $body = json_encode(self::toPayload($scenario), JSON_THROW_ON_ERROR);
        $response = $this->put('/mockserver/loadScenario', $body);

        return $this->decodeSreResponse($response, 'register load scenario');
    }

    /**
     * List all registered load scenarios with their current state.
     *
     * Each entry echoes the scenario's {@code name}, {@code state}, full
     * {@code definition} (a valid {@code LoadScenario} body that can be
     * re-submitted) and, for running/completed scenarios, an optional live
     * {@code status}.
     *
     * @return array<string, mixed> The scenario list ({scenarios: [...]}).
     * @throws MockServerException On communication errors.
     */
    public function loadScenarios(): array
    {
        $response = $this->get('/mockserver/loadScenario');

        return $this->decodeSreResponse($response, 'list load scenarios');
    }

    /**
     * Retrieve a single registered load scenario by name.
     *
     * @param string $name The unique scenario name.
     * @return array<string, mixed> The scenario entry ({name, state, definition, status?}).
     * @throws InvalidRequestException If no scenario with that name exists (HTTP 404).
     * @throws MockServerException On communication errors.
     */
    public function getLoadScenario(string $name): array
    {
        $response = $this->get('/mockserver/loadScenario/' . rawurlencode($name));

        return $this->decodeSreResponse($response, "get load scenario '{$name}'");
    }

    /**
     * Remove a single registered load scenario by name.
     *
     * @param string $name The unique scenario name.
     * @return array<string, mixed> The server response.
     * @throws InvalidRequestException If no scenario with that name exists (HTTP 404).
     * @throws MockServerException On communication errors.
     */
    public function deleteLoadScenario(string $name): array
    {
        $response = $this->delete('/mockserver/loadScenario/' . rawurlencode($name));

        return $this->decodeSreResponse($response, "delete load scenario '{$name}'");
    }

    /**
     * Remove all registered load scenarios. Idempotent.
     *
     * @return array<string, mixed> The server response.
     * @throws MockServerException On communication errors.
     */
    public function clearLoadScenarios(): array
    {
        $response = $this->delete('/mockserver/loadScenario');

        return $this->decodeSreResponse($response, 'clear load scenarios');
    }

    /**
     * Start one or more previously-registered load scenarios.
     *
     * Drives the load for each named scenario, honouring its
     * {@code startDelayMillis}. Off by default — the server returns 403 until
     * started with {@code loadGenerationEnabled=true}.
     *
     * @param string|array<int, string> $names A single scenario name or a list of names.
     * @return array<string, mixed> The started status ({started: [{name, state}], status}).
     * @throws FeatureNotEnabledException If load generation is disabled (HTTP 403).
     * @throws InvalidRequestException If a named scenario is unknown (HTTP 404)
     *         or the request is invalid (HTTP 400).
     * @throws MockServerException On communication errors.
     */
    public function startLoadScenarios(string|array $names): array
    {
        $body = json_encode(['names' => self::toNameList($names)], JSON_THROW_ON_ERROR);
        $response = $this->put('/mockserver/loadScenario/start', $body);

        return $this->decodeSreResponse(
            $response,
            'start load scenarios',
            featureFlag: 'loadGenerationEnabled',
        );
    }

    /**
     * Stop running load scenarios.
     *
     * Pass a single name or a list of names to stop those scenarios, or null
     * (the default) to stop all running scenarios. Idempotent.
     *
     * @param string|array<int, string>|null $names A scenario name, a list of
     *        names, or null to stop all.
     * @return array<string, mixed> The stopped status ({stopped: [...], status}).
     * @throws MockServerException On communication errors.
     */
    public function stopLoadScenarios(string|array|null $names = null): array
    {
        if ($names === null) {
            $body = '';
        } else {
            $body = json_encode(['names' => self::toNameList($names)], JSON_THROW_ON_ERROR);
        }
        $response = $this->put('/mockserver/loadScenario/stop', $body);

        return $this->decodeSreResponse($response, 'stop load scenarios');
    }

    /**
     * Convenience: register a load scenario and immediately start it.
     *
     * Equivalent to {@see loadScenario()} followed by {@see startLoadScenarios()}
     * for the scenario's name.
     *
     * @param LoadScenario|array<string, mixed> $scenario The scenario to register and run.
     * @return array<string, mixed> The started status ({started: [{name, state}], status}).
     * @throws FeatureNotEnabledException If load generation is disabled (HTTP 403).
     * @throws InvalidRequestException If the scenario is rejected (HTTP 400).
     * @throws MockServerException On communication errors.
     */
    public function runLoadScenario(LoadScenario|array $scenario): array
    {
        $this->loadScenario($scenario);

        return $this->startLoadScenarios(self::scenarioName($scenario));
    }

    /**
     * Retrieve the end-of-run summary report for a load scenario run.
     *
     * With the default JSON form (omit {@code $format}) the report is returned
     * as a decoded associative array carrying counts, latency percentiles, the
     * threshold verdict and per-threshold results. With {@code $format='junit'}
     * the same data is returned as a raw JUnit-XML {@code <testsuite>} string so
     * a load run becomes a first-class CI test artifact.
     *
     * @param string $name The load scenario name.
     * @param string|null $format Report format. Omit (or any value other than
     *        "junit") for the JSON report; "junit" returns the JUnit-XML string.
     * @return array<string, mixed>|string The decoded JSON report, or the raw
     *         JUnit-XML string when {@code $format='junit'}.
     * @throws InvalidRequestException If the scenario never ran (HTTP 404).
     * @throws MockServerException On communication errors.
     */
    public function getLoadScenarioReport(string $name, ?string $format = null): array|string
    {
        $path = '/mockserver/loadScenario/' . rawurlencode($name) . '/report';
        if ($format !== null) {
            $path .= '?format=' . rawurlencode($format);
        }
        $response = $this->get($path);

        $status = $response->getStatusCode();
        $responseBody = (string) $response->getBody();

        if ($status === 404) {
            throw new InvalidRequestException(
                "Failed to get load scenario report '{$name}': not found (HTTP 404): {$responseBody}"
            );
        }
        if ($status >= 400) {
            throw new MockServerException(
                "Failed to get load scenario report '{$name}' (HTTP {$status}): {$responseBody}"
            );
        }

        // The JUnit form is returned as raw XML; hand it back verbatim.
        if ($format === 'junit') {
            return $responseBody;
        }

        if ($responseBody !== '') {
            $parsed = json_decode($responseBody, true);
            if (is_array($parsed)) {
                return $parsed;
            }
        }

        return [];
    }

    /**
     * Generate (and load) an editable load scenario from an OpenAPI spec.
     *
     * One step is produced per OpenAPI operation, each with the operation's
     * method, server-prefixed path, a representative request-body example and a
     * Content-Type header. The generated scenario is loaded (registered) in the
     * LOADED state — it generates no traffic and is allowed even when load
     * generation is disabled — and returned so it can be shown and edited before
     * triggering a run.
     *
     * @param string $name The generated scenario name (the unique registry key).
     * @param string $specUrlOrPayload The OpenAPI spec as an inline JSON/YAML
     *        payload, a URL, or a file/classpath reference.
     * @param array<string, mixed>|null $target Optional explicit network target
     *        for every generated step ({host, port, scheme}); overrides the
     *        spec's servers[0].
     * @param LoadProfile|array<string, mixed>|null $profile Optional load
     *        profile; when omitted a conservative default is applied.
     * @return array<string, mixed> The server response ({status, name, state, scenario}).
     * @throws InvalidRequestException If the name/spec is missing or the spec or
     *         generated scenario is invalid (HTTP 400).
     * @throws MockServerException On communication errors.
     */
    public function generateLoadScenarioFromOpenAPI(
        string $name,
        string $specUrlOrPayload,
        ?array $target = null,
        LoadProfile|array|null $profile = null,
    ): array {
        $payload = [
            'name' => $name,
            'specUrlOrPayload' => $specUrlOrPayload,
        ];
        if ($target !== null) {
            $payload['target'] = $target;
        }
        if ($profile !== null) {
            $payload['profile'] = self::toPayload($profile);
        }

        $body = json_encode($payload, JSON_THROW_ON_ERROR);
        $response = $this->put('/mockserver/loadScenario/generateFromOpenAPI', $body);

        return $this->decodeSreResponse($response, 'generate load scenario from OpenAPI');
    }

    /**
     * Generate (and load) an editable load scenario from recorded proxy traffic.
     *
     * VERBATIM (the default) emits one step per recorded request, in recorded
     * order, optionally capped by {@code $maxSteps}. TEMPLATIZED deduplicates
     * recorded requests by (method, templatised-path), keeping one representative
     * per unique route ordered by descending hit frequency. The generated
     * scenario is loaded (registered) in the LOADED state and returned so it can
     * be shown and edited before triggering a run.
     *
     * @param string $name The generated scenario name (the unique registry key).
     * @param string|null $mode VERBATIM (default) or TEMPLATIZED.
     * @param HttpRequest|array<string, mixed>|null $requestFilter Optional matcher
     *        selecting which recorded requests to include; absent means all.
     * @param array<string, mixed>|null $target Optional explicit network target
     *        applied to every generated step ({host, port, scheme}).
     * @param int|null $maxSteps Optional cap on the number of VERBATIM steps.
     * @param LoadProfile|array<string, mixed>|null $profile Optional load
     *        profile; when omitted a conservative default is applied.
     * @return array<string, mixed> The server response ({status, name, state, scenario}).
     * @throws InvalidRequestException If the name is missing, there are no
     *         recorded requests, the mode is invalid, or the generated scenario
     *         is invalid (HTTP 400).
     * @throws MockServerException On communication errors.
     */
    public function generateLoadScenarioFromRecording(
        string $name,
        ?string $mode = null,
        HttpRequest|array|null $requestFilter = null,
        ?array $target = null,
        ?int $maxSteps = null,
        LoadProfile|array|null $profile = null,
    ): array {
        $payload = ['name' => $name];
        if ($mode !== null) {
            $payload['mode'] = strtoupper($mode);
        }
        if ($requestFilter !== null) {
            $payload['requestFilter'] = $requestFilter instanceof HttpRequest
                ? $requestFilter->toArray()
                : $requestFilter;
        }
        if ($target !== null) {
            $payload['target'] = $target;
        }
        if ($maxSteps !== null) {
            $payload['maxSteps'] = $maxSteps;
        }
        if ($profile !== null) {
            $payload['profile'] = self::toPayload($profile);
        }

        $body = json_encode($payload, JSON_THROW_ON_ERROR);
        $response = $this->put('/mockserver/loadScenario/generateFromRecording', $body);

        return $this->decodeSreResponse($response, 'generate load scenario from recording');
    }

    // -----------------------------------------------------------------
    // SRE control-plane: service-scoped HTTP chaos
    // -----------------------------------------------------------------

    /**
     * Register a service-scoped HTTP chaos profile for a downstream host.
     *
     * The profile is applied to forward expectations targeting {@code $host} that
     * do not define their own chaos. The host is matched case-insensitively,
     * ignoring any {@code :port}. When {@code $ttlMillis} is supplied the chaos
     * auto-reverts after that many milliseconds (a dead-man's switch).
     *
     * @param string $host The downstream host the chaos applies to.
     * @param array<string, mixed> $profile The chaos profile (errorStatus,
     *        errorProbability, latency{value,timeUnit}, dropConnectionProbability,
     *        seed). Note dropConnectionProbability is a 0.0-1.0 probability, not a
     *        boolean; the previously documented `connectionDrop` key does not exist
     *        server-side and was silently ignored.
     * @param int|null $ttlMillis Optional time-to-live in milliseconds.
     * @return array<string, mixed> The server response.
     * @throws InvalidRequestException If the chaos profile is invalid (HTTP 400).
     * @throws MockServerException On communication errors.
     */
    public function setServiceChaos(string $host, array $profile, ?int $ttlMillis = null): array
    {
        $payload = ['host' => $host, 'chaos' => $profile];
        if ($ttlMillis !== null) {
            $payload['ttlMillis'] = $ttlMillis;
        }

        $body = json_encode($payload, JSON_THROW_ON_ERROR);
        $response = $this->put('/mockserver/serviceChaos', $body);

        return $this->decodeSreResponse($response, 'set service chaos');
    }

    // -----------------------------------------------------------------
    // SRE control-plane: SLO verdicts
    // -----------------------------------------------------------------

    /**
     * Verify a service-level objective over a window of recorded SLI samples.
     *
     * Returns the verdict with a {@code result} of PASS, FAIL or INCONCLUSIVE.
     * A FAIL verdict (HTTP 406) raises {@see VerificationException} so a CI or
     * chaos gate can assert on it directly; PASS and INCONCLUSIVE return the
     * verdict array.
     *
     * Off by default — the server returns 400 until started with
     * {@code sloTrackingEnabled=true}.
     *
     * @param array<string, mixed> $criteria The SLO criteria (name, window,
     *        minimumSampleCount, upstreamHosts, objectives[{sli,comparator,threshold,scope}]).
     * @return array<string, mixed> The SLO verdict (result PASS or INCONCLUSIVE).
     * @throws VerificationException If the verdict is FAIL (HTTP 406).
     * @throws InvalidRequestException If criteria are malformed or SLO tracking
     *         is disabled (HTTP 400).
     * @throws MockServerException On communication errors.
     */
    public function verifySlo(array $criteria): array
    {
        $body = json_encode($criteria, JSON_THROW_ON_ERROR);
        $response = $this->put('/mockserver/verifySLO', $body);

        $status = $response->getStatusCode();
        $responseBody = (string) $response->getBody();

        if ($status === 406) {
            throw new VerificationException($responseBody ?: 'SLO verdict: FAIL');
        }
        if ($status === 403) {
            throw new FeatureNotEnabledException(
                "SLO tracking is disabled (start MockServer with sloTrackingEnabled=true) (HTTP 403): {$responseBody}"
            );
        }
        if ($status === 400) {
            throw new InvalidRequestException("Invalid SLO criteria (HTTP 400): {$responseBody}");
        }
        if ($status >= 400) {
            throw new MockServerException("Verify SLO failed (HTTP {$status}): {$responseBody}");
        }

        if ($responseBody !== '') {
            $parsed = json_decode($responseBody, true);
            if (is_array($parsed)) {
                return $parsed;
            }
        }

        return [];
    }

    // -----------------------------------------------------------------
    // SRE control-plane: preemption (cordon / drain)
    // -----------------------------------------------------------------

    /**
     * Cordon and drain the server (preemption simulation).
     *
     * Simulates a connection-lifecycle preemption (e.g. a Kubernetes node drain
     * or spot reclamation). While cordoned, new exchanges are signalled to back
     * off while in-flight requests drain. All fields are optional; an empty array
     * uses server defaults (mode "both", drain from {@code stopDrainMillis}, no TTL).
     *
     * @param array<string, mixed> $request Preemption parameters
     *        (mode, drainMillis, ttlMillis, lastStreamId). Defaults to empty.
     * @return array<string, mixed> The preemption status.
     * @throws InvalidRequestException If the request is invalid (HTTP 400).
     * @throws MockServerException On communication errors.
     */
    public function setPreemption(array $request = []): array
    {
        $body = json_encode((object) $request, JSON_THROW_ON_ERROR);
        $response = $this->put('/mockserver/preemption', $body);

        return $this->decodeSreResponse($response, 'set preemption');
    }

    /**
     * Retrieve the current preemption (cordon/drain) status.
     *
     * @return array<string, mixed> The preemption status
     *         (state, inFlight, drainRemainingMillis, mode).
     * @throws MockServerException On communication errors.
     */
    public function preemptionStatus(): array
    {
        $response = $this->get('/mockserver/preemption');

        return $this->decodeSreResponse($response, 'get preemption status');
    }

    /**
     * Uncordon the server (clear any active preemption simulation). Idempotent.
     *
     * @return array<string, mixed> The cleared status ({state: "inactive"}).
     * @throws MockServerException On communication errors.
     */
    public function clearPreemption(): array
    {
        $response = $this->delete('/mockserver/preemption');

        return $this->decodeSreResponse($response, 'clear preemption');
    }

    // -----------------------------------------------------------------
    // SRE control-plane: scheduled chaos experiments
    // -----------------------------------------------------------------

    /**
     * Start a scheduled multi-stage chaos experiment.
     *
     * The experiment is an ordered sequence of stages, each applying
     * service-scoped chaos profiles to one or more hosts for a duration; stages
     * progress automatically. Only one experiment may be active at a time;
     * starting a new one stops the previous one.
     *
     * @param array<string, mixed> $experiment The experiment definition
     *        (name, loop, stages[{durationMillis, profiles{host: profile}}]).
     * @return array<string, mixed> The started status ({status: "started", name}).
     * @throws InvalidRequestException If the experiment definition is invalid (HTTP 400).
     * @throws MockServerException On communication errors.
     */
    public function startChaosExperiment(array $experiment): array
    {
        $body = json_encode($experiment, JSON_THROW_ON_ERROR);
        $response = $this->put('/mockserver/chaosExperiment', $body);

        return $this->decodeSreResponse($response, 'start chaos experiment');
    }

    // -----------------------------------------------------------------
    // Clock control (freeze / advance / reset / status)
    // -----------------------------------------------------------------

    /**
     * Freeze the MockServer clock.
     *
     * With no argument the clock freezes at the current time. Supplying an
     * ISO-8601 instant string (e.g. {@code "2024-01-01T00:00:00Z"}) freezes the
     * clock at that instant instead. Wraps {@code PUT /mockserver/clock} with
     * body {@code {"action":"freeze"[,"instant":"<instant>"]}}.
     *
     * @param string|null $instant Optional ISO-8601 instant to freeze at; null
     *        (the default) freezes at the current time.
     * @return $this
     * @throws InvalidRequestException If the instant is rejected (HTTP 400).
     * @throws MockServerException On communication errors.
     */
    public function freezeClock(?string $instant = null): self
    {
        $payload = ['action' => 'freeze'];
        if ($instant !== null) {
            $payload['instant'] = $instant;
        }
        $body = json_encode($payload, JSON_THROW_ON_ERROR);
        $response = $this->put('/mockserver/clock', $body);

        $this->expectClockSuccess($response, 'freeze clock');

        return $this;
    }

    /**
     * Advance the (frozen) MockServer clock by a number of milliseconds.
     *
     * Wraps {@code PUT /mockserver/clock} with body
     * {@code {"action":"advance","durationMillis":<millis>}}. The duration must
     * be positive; a non-positive value is rejected by the server (HTTP 400).
     *
     * @param int $durationMillis Positive number of milliseconds to advance by.
     * @return $this
     * @throws InvalidRequestException If the duration is rejected (HTTP 400).
     * @throws MockServerException On communication errors.
     */
    public function advanceClock(int $durationMillis): self
    {
        $body = json_encode(
            ['action' => 'advance', 'durationMillis' => $durationMillis],
            JSON_THROW_ON_ERROR,
        );
        $response = $this->put('/mockserver/clock', $body);

        $this->expectClockSuccess($response, 'advance clock');

        return $this;
    }

    /**
     * Reset the MockServer clock to real wall-clock time.
     *
     * Wraps {@code PUT /mockserver/clock} with body {@code {"action":"reset"}}.
     *
     * @return $this
     * @throws MockServerException On communication errors.
     */
    public function resetClock(): self
    {
        $body = json_encode(['action' => 'reset'], JSON_THROW_ON_ERROR);
        $response = $this->put('/mockserver/clock', $body);

        $this->expectClockSuccess($response, 'reset clock');

        return $this;
    }

    /**
     * Retrieve the current clock status.
     *
     * Wraps {@code GET /mockserver/clock}; returns the response body verbatim,
     * a JSON object with fields {@code currentInstant}, {@code currentEpochMillis}
     * and {@code frozen}.
     *
     * @return string The clock status JSON document.
     * @throws MockServerException On communication errors.
     */
    public function clockStatus(): string
    {
        $response = $this->get('/mockserver/clock');

        $status = $response->getStatusCode();
        $responseBody = (string) $response->getBody();

        if ($status >= 400) {
            throw new MockServerException("Failed to get clock status (HTTP {$status}): {$responseBody}");
        }

        return $responseBody;
    }

    // -----------------------------------------------------------------
    // Metrics
    // -----------------------------------------------------------------

    /**
     * Retrieve the JSON counter snapshot of server metrics.
     *
     * Wraps {@code PUT /mockserver/retrieve?type=METRICS}; returns the response
     * body verbatim, a flat JSON object mapping each metric name to its long
     * value (or {@code {}} when metrics are disabled). This is distinct from
     * {@see scrapeMetrics()} (the Prometheus exposition text).
     *
     * @return string The metrics snapshot JSON document.
     * @throws MockServerException On communication errors.
     */
    public function retrieveMetrics(): string
    {
        $response = $this->put('/mockserver/retrieve?type=METRICS', '');

        $status = $response->getStatusCode();
        $responseBody = (string) $response->getBody();

        if ($status >= 400) {
            throw new MockServerException("Failed to retrieve metrics (HTTP {$status}): {$responseBody}");
        }

        return $responseBody;
    }

    /**
     * Scrape the Prometheus exposition-format metrics text.
     *
     * Wraps {@code GET /mockserver/metrics}; returns the exposition text body.
     * When metrics are disabled the server replies {@code 404} — surfaced as a
     * {@see FeatureNotEnabledException}. This is distinct from
     * {@see retrieveMetrics()} (the JSON counter snapshot).
     *
     * @return string The Prometheus exposition text.
     * @throws FeatureNotEnabledException If metrics are disabled (HTTP 404).
     * @throws MockServerException On communication errors.
     */
    public function scrapeMetrics(): string
    {
        $response = $this->get('/mockserver/metrics');

        $status = $response->getStatusCode();
        $responseBody = (string) $response->getBody();

        if ($status === 404) {
            throw new FeatureNotEnabledException(
                'Failed to scrape metrics: metrics are disabled '
                . '(start MockServer with metricsEnabled=true to enable them) (HTTP 404)'
            );
        }
        if ($status >= 400) {
            throw new MockServerException("Failed to scrape metrics (HTTP {$status}): {$responseBody}");
        }

        return $responseBody;
    }

    // -----------------------------------------------------------------
    // Configuration (read / update)
    // -----------------------------------------------------------------

    /**
     * Retrieve the effective live server configuration.
     *
     * Wraps {@code GET /mockserver/configuration}; returns the serialized
     * {@code Configuration} JSON document verbatim.
     *
     * @return string The configuration JSON document.
     * @throws MockServerException On communication errors.
     */
    public function retrieveConfiguration(): string
    {
        $response = $this->get('/mockserver/configuration');

        $status = $response->getStatusCode();
        $responseBody = (string) $response->getBody();

        if ($status >= 400) {
            throw new MockServerException("Failed to retrieve configuration (HTTP {$status}): {$responseBody}");
        }

        return $responseBody;
    }

    /**
     * Update the live server configuration (partial update).
     *
     * Wraps {@code PUT /mockserver/configuration} with a {@code ConfigurationDTO}
     * JSON document; only the fields present are applied. Returns the serialized
     * updated configuration JSON document verbatim.
     *
     * @param string $configJson The {@code ConfigurationDTO} JSON document.
     * @return string The updated configuration JSON document.
     * @throws InvalidRequestException If the configuration JSON is rejected (HTTP 400).
     * @throws MockServerException On communication errors.
     */
    public function updateConfiguration(string $configJson): string
    {
        $response = $this->put('/mockserver/configuration', $configJson);

        $status = $response->getStatusCode();
        $responseBody = (string) $response->getBody();

        if ($status === 400) {
            throw new InvalidRequestException("Invalid configuration JSON: {$responseBody}");
        }
        if ($status >= 400) {
            throw new MockServerException("Failed to update configuration (HTTP {$status}): {$responseBody}");
        }

        return $responseBody;
    }

    // -----------------------------------------------------------------
    // Drift detection
    // -----------------------------------------------------------------

    /**
     * Retrieve the recorded mock drift report.
     *
     * Wraps {@code GET /mockserver/drift}; returns the serialized report JSON
     * document verbatim, of the form
     * {@code {"count": <n>, "drifts": [ ... ]}}, where each entry describes a
     * difference detected between a mock's configured response and the live
     * upstream response for the same request.
     *
     * @return string The drift report JSON document.
     * @throws MockServerException On communication errors.
     */
    public function retrieveDrift(): string
    {
        $response = $this->get('/mockserver/drift');

        $status = $response->getStatusCode();
        $responseBody = (string) $response->getBody();

        if ($status >= 400) {
            throw new MockServerException("Failed to retrieve drift (HTTP {$status}): {$responseBody}");
        }

        return $responseBody;
    }

    /**
     * Clear all recorded mock drift.
     *
     * Wraps {@code PUT /mockserver/drift/clear}.
     *
     * @return void
     * @throws MockServerException On communication errors.
     */
    public function clearDrift(): void
    {
        $response = $this->put('/mockserver/drift/clear', '');

        $status = $response->getStatusCode();
        if ($status >= 400) {
            $responseBody = (string) $response->getBody();
            throw new MockServerException("Failed to clear drift (HTTP {$status}): {$responseBody}");
        }
    }

    // -----------------------------------------------------------------
    // Pact (import / export / verify)
    // -----------------------------------------------------------------

    /**
     * Import a Pact v3 consumer contract as expectations.
     *
     * Wraps {@code PUT /mockserver/pact/import}; returns the upserted
     * expectations as a JSON array string (as returned by the server).
     *
     * @param string $json The Pact v3 contract JSON document.
     * @return string The upserted expectations JSON array document.
     * @throws \InvalidArgumentException If the contract JSON is blank.
     * @throws InvalidRequestException If the contract is rejected (HTTP 400).
     * @throws MockServerException On communication errors.
     */
    public function pactImport(string $json): string
    {
        if (trim($json) === '') {
            throw new \InvalidArgumentException('pactImport requires a non-empty pact contract JSON');
        }

        $response = $this->put('/mockserver/pact/import', $json);

        $status = $response->getStatusCode();
        $responseBody = (string) $response->getBody();

        if ($status === 400) {
            throw new InvalidRequestException("Invalid pact contract: {$responseBody}");
        }
        if ($status >= 400) {
            throw new MockServerException("Failed to import pact (HTTP {$status}): {$responseBody}");
        }

        return $responseBody;
    }

    /**
     * Export the active expectations as a Pact v3 consumer contract.
     *
     * Wraps {@code PUT /mockserver/pact?consumer=&provider=}; a blank consumer or
     * provider is omitted so the server default is used. Returns the generated
     * Pact v3 contract JSON document.
     *
     * @param string $consumer The consumer name (blank uses the server default).
     * @param string $provider The provider name (blank uses the server default).
     * @return string The Pact v3 contract JSON document.
     * @throws MockServerException On communication errors.
     */
    public function pactExport(string $consumer, string $provider): string
    {
        $query = [];
        if (trim($consumer) !== '') {
            $query[] = 'consumer=' . urlencode($consumer);
        }
        if (trim($provider) !== '') {
            $query[] = 'provider=' . urlencode($provider);
        }
        $path = '/mockserver/pact';
        if ($query !== []) {
            $path .= '?' . implode('&', $query);
        }

        $response = $this->put($path, '');

        $status = $response->getStatusCode();
        $responseBody = (string) $response->getBody();

        if ($status >= 400) {
            throw new MockServerException("Failed to export pact (HTTP {$status}): {$responseBody}");
        }

        return $responseBody;
    }

    /**
     * Verify a Pact v3 consumer contract against the active expectations.
     *
     * Wraps {@code PUT /mockserver/pact/verify}. The server replies
     * {@code 202 ACCEPTED} when every interaction matches an expectation (returns
     * {@code true}) and {@code 406 NOT_ACCEPTABLE} otherwise (returns
     * {@code false}); the verification report body is discarded in both cases —
     * use the boolean verdict. A malformed contract is surfaced as an exception.
     *
     * @param string $json The Pact v3 contract JSON document.
     * @return bool True when the contract verified (HTTP 202), false on a FAIL
     *         verdict (HTTP 406).
     * @throws \InvalidArgumentException If the contract JSON is blank.
     * @throws InvalidRequestException If the contract is malformed (HTTP 400).
     * @throws MockServerException On communication errors.
     */
    public function pactVerify(string $json): bool
    {
        if (trim($json) === '') {
            throw new \InvalidArgumentException('pactVerify requires a non-empty pact contract JSON');
        }

        $response = $this->put('/mockserver/pact/verify', $json);

        $status = $response->getStatusCode();
        $responseBody = (string) $response->getBody();

        if ($status === 202) {
            return true;
        }
        if ($status === 406) {
            return false;
        }
        if ($status === 400) {
            throw new InvalidRequestException("Invalid pact contract: {$responseBody}");
        }
        if ($status >= 400) {
            throw new MockServerException("Failed to verify pact (HTTP {$status}): {$responseBody}");
        }

        // Any other 2xx (unexpected) is treated as a pass.
        return true;
    }

    // -----------------------------------------------------------------
    // File store (store / retrieve / list / delete)
    // -----------------------------------------------------------------

    /**
     * Store a named file (UTF-8 text content) in the server's in-memory file store.
     *
     * Wraps {@code PUT /mockserver/files/store} with body
     * {@code {"name":"<name>","content":"<content>"}}.
     *
     * @param string $name The file name/key.
     * @param string $content The UTF-8 file content.
     * @return $this
     * @throws InvalidRequestException If name/content are missing (HTTP 400).
     * @throws MockServerException On communication errors.
     */
    public function storeFile(string $name, string $content): self
    {
        $body = json_encode(
            ['name' => $name, 'content' => $content],
            JSON_THROW_ON_ERROR,
        );
        $response = $this->put('/mockserver/files/store', $body);

        $status = $response->getStatusCode();
        $responseBody = (string) $response->getBody();

        if ($status === 400) {
            throw new InvalidRequestException("Failed to store file: {$responseBody}");
        }
        if ($status >= 400) {
            throw new MockServerException("Failed to store file (HTTP {$status}): {$responseBody}");
        }

        return $this;
    }

    /**
     * Retrieve a named file's content from the server's in-memory file store.
     *
     * Wraps {@code PUT /mockserver/files/retrieve} with body
     * {@code {"name":"<name>"}}; returns the raw {@code 200} response body. A
     * missing file (HTTP 404) is signalled as a clear not-found exception rather
     * than returning the server's "file not found" text as if it were content —
     * this matches the Go/.NET/Ruby/Rust clients, which all throw on a 404 here.
     *
     * @param string $name The file name/key.
     * @return string The raw file content.
     * @throws InvalidRequestException If the file does not exist (HTTP 404) or
     *         the name is blank (HTTP 400).
     * @throws MockServerException On communication errors.
     */
    public function retrieveFile(string $name): string
    {
        $body = json_encode(['name' => $name], JSON_THROW_ON_ERROR);
        $response = $this->put('/mockserver/files/retrieve', $body);

        $status = $response->getStatusCode();
        $responseBody = (string) $response->getBody();

        if ($status === 404) {
            throw new InvalidRequestException("file not found: {$name}");
        }
        if ($status === 400) {
            throw new InvalidRequestException("Failed to retrieve file (HTTP 400): {$responseBody}");
        }
        if ($status >= 400) {
            throw new MockServerException("Failed to retrieve file (HTTP {$status}): {$responseBody}");
        }

        return $responseBody;
    }

    /**
     * List the names of all files in the server's in-memory file store.
     *
     * Wraps {@code PUT /mockserver/files/list}; returns the decoded JSON array of
     * file-name strings.
     *
     * @return array<int, string> The file names.
     * @throws MockServerException On communication errors.
     */
    public function listFiles(): array
    {
        $response = $this->put('/mockserver/files/list', '');

        $status = $response->getStatusCode();
        $responseBody = (string) $response->getBody();

        if ($status >= 400) {
            throw new MockServerException("Failed to list files (HTTP {$status}): {$responseBody}");
        }

        if ($responseBody !== '') {
            $parsed = json_decode($responseBody, true);
            if (is_array($parsed)) {
                return array_values($parsed);
            }
        }

        return [];
    }

    /**
     * Delete a named file from the server's in-memory file store.
     *
     * Wraps {@code PUT /mockserver/files/delete} with body
     * {@code {"name":"<name>"}}.
     *
     * @param string $name The file name/key.
     * @return $this
     * @throws InvalidRequestException If the file is unknown (HTTP 404) or the
     *         name is blank (HTTP 400).
     * @throws MockServerException On communication errors.
     */
    public function deleteFile(string $name): self
    {
        $body = json_encode(['name' => $name], JSON_THROW_ON_ERROR);
        $response = $this->put('/mockserver/files/delete', $body);

        $status = $response->getStatusCode();
        $responseBody = (string) $response->getBody();

        if ($status === 400 || $status === 404) {
            throw new InvalidRequestException("Failed to delete file (HTTP {$status}): {$responseBody}");
        }
        if ($status >= 400) {
            throw new MockServerException("Failed to delete file (HTTP {$status}): {$responseBody}");
        }

        return $this;
    }

    // -----------------------------------------------------------------
    // Import (HAR / Postman)
    // -----------------------------------------------------------------

    /**
     * Import a HAR (HTTP Archive) document as expectations.
     *
     * Wraps {@code PUT /mockserver/import?format=har}; returns the decoded JSON
     * array of upserted expectations.
     *
     * @param string $harJson The HAR JSON document.
     * @return array<int, array<string, mixed>> The created/updated expectations.
     * @throws InvalidRequestException If the document is rejected (HTTP 400).
     * @throws MockServerException On communication errors.
     */
    public function importHar(string $harJson): array
    {
        return $this->importDocument($harJson, 'har', 'import HAR');
    }

    /**
     * Import a Postman collection document as expectations.
     *
     * Wraps {@code PUT /mockserver/import?format=postman}; returns the decoded
     * JSON array of upserted expectations.
     *
     * @param string $collectionJson The Postman collection JSON document.
     * @return array<int, array<string, mixed>> The created/updated expectations.
     * @throws InvalidRequestException If the document is rejected (HTTP 400).
     * @throws MockServerException On communication errors.
     */
    public function importPostmanCollection(string $collectionJson): array
    {
        return $this->importDocument($collectionJson, 'postman', 'import Postman collection');
    }

    // -----------------------------------------------------------------
    // Operating mode (high-level proxy/record mode)
    // -----------------------------------------------------------------

    /**
     * Set the server's high-level operating mode.
     *
     * Wraps {@code PUT /mockserver/mode?mode=<MODE>}. Use the {@code MODE_*}
     * constants — {@see MODE_SIMULATE}, {@see MODE_SPY}, {@see MODE_CAPTURE}
     * (case-insensitive on the wire).
     *
     * @param string $mode One of {@code SIMULATE}, {@code SPY}, {@code CAPTURE}.
     * @return $this
     * @throws InvalidRequestException If the mode is invalid/blank (HTTP 400).
     * @throws MockServerException On communication errors.
     */
    public function setMode(string $mode): self
    {
        $response = $this->put('/mockserver/mode?mode=' . urlencode($mode), '');

        $status = $response->getStatusCode();
        $responseBody = (string) $response->getBody();

        if ($status === 400) {
            throw new InvalidRequestException("Invalid operating mode: {$responseBody}");
        }
        if ($status >= 400) {
            throw new MockServerException("Failed to set operating mode (HTTP {$status}): {$responseBody}");
        }

        return $this;
    }

    /**
     * Retrieve the server's current high-level operating mode.
     *
     * Wraps {@code GET /mockserver/mode}; returns the decoded JSON object
     * {@code {"mode":"<MODE>","proxyUnmatchedRequests":<boolean>}}.
     *
     * @return array<string, mixed> The current mode status.
     * @throws MockServerException On communication errors.
     */
    public function retrieveMode(): array
    {
        $response = $this->get('/mockserver/mode');

        $status = $response->getStatusCode();
        $responseBody = (string) $response->getBody();

        if ($status >= 400) {
            throw new MockServerException("Failed to retrieve operating mode (HTTP {$status}): {$responseBody}");
        }

        if ($responseBody !== '') {
            $parsed = json_decode($responseBody, true);
            if (is_array($parsed)) {
                return $parsed;
            }
        }

        return [];
    }

    // -----------------------------------------------------------------
    // WSDL -> expectations
    // -----------------------------------------------------------------

    /**
     * Generate expectations from a WSDL document.
     *
     * Wraps {@code PUT /mockserver/wsdl} with the raw WSDL XML as the request
     * body ({@code text/xml}); returns the decoded JSON array of the generated
     * (upserted) expectations.
     *
     * @param string $wsdl The WSDL document (XML).
     * @return array<int, array<string, mixed>> The generated expectations.
     * @throws \InvalidArgumentException If the WSDL is blank.
     * @throws InvalidRequestException If the WSDL is invalid (HTTP 400).
     * @throws MockServerException On communication errors.
     */
    public function wsdlExpectation(string $wsdl): array
    {
        if (trim($wsdl) === '') {
            throw new \InvalidArgumentException('wsdlExpectation requires a non-empty WSDL document');
        }

        $response = $this->put('/mockserver/wsdl', $wsdl, 'text/xml');

        $status = $response->getStatusCode();
        $responseBody = (string) $response->getBody();

        if ($status === 400) {
            throw new InvalidRequestException("Invalid WSDL: {$responseBody}");
        }
        if ($status >= 400) {
            throw new MockServerException("Failed to generate WSDL expectations (HTTP {$status}): {$responseBody}");
        }

        if ($responseBody !== '') {
            $parsed = json_decode($responseBody, true);
            if (is_array($parsed)) {
                return $parsed;
            }
        }

        return [];
    }

    // -----------------------------------------------------------------
    // Internal helpers
    // -----------------------------------------------------------------

    /**
     * Assert a clock-control PUT succeeded, mapping the documented error codes to
     * typed exceptions.
     *
     * @param \Psr\Http\Message\ResponseInterface $response
     * @param string $action Human-readable action used in error messages.
     * @throws InvalidRequestException If the request was rejected (HTTP 400).
     * @throws MockServerException On other error statuses.
     */
    private function expectClockSuccess(
        \Psr\Http\Message\ResponseInterface $response,
        string $action,
    ): void {
        $status = $response->getStatusCode();
        $responseBody = (string) $response->getBody();

        if ($status === 400) {
            throw new InvalidRequestException("Failed to {$action} (HTTP 400): {$responseBody}");
        }
        if ($status >= 400) {
            throw new MockServerException("Failed to {$action} (HTTP {$status}): {$responseBody}");
        }
    }

    /**
     * Shared HAR/Postman import: PUT /mockserver/import?format=<format>, decode
     * the upserted-expectations JSON array.
     *
     * @param string $json The document to import.
     * @param string $format The {@code ?format=} value (har or postman).
     * @param string $action Human-readable action used in error messages.
     * @return array<int, array<string, mixed>> The created/updated expectations.
     * @throws InvalidRequestException If the document is rejected (HTTP 400).
     * @throws MockServerException On communication errors.
     */
    private function importDocument(string $json, string $format, string $action): array
    {
        $response = $this->put('/mockserver/import?format=' . urlencode($format), $json);

        $status = $response->getStatusCode();
        $responseBody = (string) $response->getBody();

        if ($status === 400) {
            throw new InvalidRequestException("Failed to {$action} (HTTP 400): {$responseBody}");
        }
        if ($status >= 400) {
            throw new MockServerException("Failed to {$action} (HTTP {$status}): {$responseBody}");
        }

        if ($responseBody !== '') {
            $parsed = json_decode($responseBody, true);
            if (is_array($parsed)) {
                return $parsed;
            }
        }

        return [];
    }

    /**
     * Coerce a value-object-or-array argument into a JSON-encodable array.
     *
     * @param object|array<string, mixed> $value
     * @return array<string, mixed>|object
     */
    private static function toPayload(object|array $value): array|object
    {
        if (is_array($value)) {
            return $value;
        }
        if (method_exists($value, 'toArray')) {
            return $value->toArray();
        }
        if ($value instanceof \JsonSerializable) {
            $serialized = $value->jsonSerialize();
            if (is_array($serialized)) {
                return $serialized;
            }
        }

        return $value;
    }

    /**
     * Normalise a single name or list of names into a list of strings.
     *
     * @param string|array<int, string> $names
     * @return array<int, string>
     */
    private static function toNameList(string|array $names): array
    {
        return is_array($names) ? array_values($names) : [$names];
    }

    /**
     * Extract the unique {@code name} of a scenario value object or array.
     *
     * @param LoadScenario|array<string, mixed> $scenario
     * @throws InvalidRequestException If the scenario has no name.
     */
    private static function scenarioName(LoadScenario|array $scenario): string
    {
        $name = $scenario instanceof LoadScenario
            ? $scenario->getName()
            : ($scenario['name'] ?? null);

        if (!is_string($name) || $name === '') {
            throw new InvalidRequestException('Load scenario must have a non-empty "name" to run.');
        }

        return $name;
    }

    /**
     * Decode a control-plane (SRE) JSON response, surfacing the common error
     * statuses with clear, typed exceptions.
     *
     * @param \Psr\Http\Message\ResponseInterface $response
     * @param string $action Human-readable action used in error messages.
     * @param string|null $featureFlag When set, a 403 is surfaced as a
     *        {@see FeatureNotEnabledException} naming the server flag to enable.
     * @return array<string, mixed>
     * @throws FeatureNotEnabledException
     * @throws InvalidRequestException
     * @throws MockServerException
     */
    private function decodeSreResponse(
        \Psr\Http\Message\ResponseInterface $response,
        string $action,
        ?string $featureFlag = null,
    ): array {
        $status = $response->getStatusCode();
        $responseBody = (string) $response->getBody();

        if ($status === 403) {
            $hint = $featureFlag !== null
                ? " (start MockServer with {$featureFlag}=true to enable it)"
                : '';
            throw new FeatureNotEnabledException(
                "Failed to {$action}: feature is disabled{$hint} (HTTP 403): {$responseBody}"
            );
        }
        if ($status === 400) {
            throw new InvalidRequestException("Failed to {$action} (HTTP 400): {$responseBody}");
        }
        if ($status === 404) {
            throw new InvalidRequestException("Failed to {$action}: not found (HTTP 404): {$responseBody}");
        }
        if ($status >= 400) {
            throw new MockServerException("Failed to {$action} (HTTP {$status}): {$responseBody}");
        }

        if ($responseBody !== '') {
            $parsed = json_decode($responseBody, true);
            if (is_array($parsed)) {
                return $parsed;
            }
        }

        return [];
    }

    /**
     * Decode a match-diagnostic JSON response, reporting the server's
     * {@code error} message on failure.
     *
     * @param \Psr\Http\Message\ResponseInterface $response
     * @param string $action Human-readable action used in error messages.
     * @return array<string, mixed>
     * @throws InvalidRequestException On HTTP 400 or 404.
     * @throws MockServerException On other errors, or a body that is not a JSON object.
     */
    private function decodeDiagnosticResponse(
        \Psr\Http\Message\ResponseInterface $response,
        string $action,
    ): array {
        $status = $response->getStatusCode();
        $responseBody = (string) $response->getBody();
        $parsed = $responseBody !== '' ? json_decode($responseBody, true) : [];

        if ($status >= 400) {
            $reason = is_array($parsed) && is_string($parsed['error'] ?? null) ? $parsed['error'] : $responseBody;
            $message = "Failed to {$action} (HTTP {$status}): {$reason}";
            throw $status === 400 || $status === 404
                ? new InvalidRequestException($message)
                : new MockServerException($message);
        }
        if (!is_array($parsed) || (array_is_list($parsed) && $parsed !== [])) {
            throw new MockServerException("Unable to parse {$action} response: {$responseBody}");
        }

        return $parsed;
    }

    /**
     * Decode a scenario control-plane JSON response, surfacing the common error
     * statuses with clear, typed exceptions.
     *
     * @param \Psr\Http\Message\ResponseInterface $response
     * @param string $action Human-readable action used in error messages.
     * @return array<string, mixed>
     * @throws InvalidRequestException
     * @throws MockServerException
     */
    private function decodeScenarioResponse(
        \Psr\Http\Message\ResponseInterface $response,
        string $action,
    ): array {
        $status = $response->getStatusCode();
        $responseBody = (string) $response->getBody();

        if ($status === 400 || $status === 406) {
            throw new InvalidRequestException("Failed to {$action} (HTTP {$status}): {$responseBody}");
        }
        if ($status >= 400) {
            throw new MockServerException("Failed to {$action} (HTTP {$status}): {$responseBody}");
        }

        if ($responseBody !== '') {
            $parsed = json_decode($responseBody, true);
            if (is_array($parsed)) {
                return $parsed;
            }
        }

        return [];
    }

    /**
     * Generic retrieve method.
     *
     * @param HttpRequest|null $request Filter
     * @param string $type REQUESTS, ACTIVE_EXPECTATIONS, RECORDED_EXPECTATIONS, LOGS
     * @param string $format JSON or LOG_ENTRIES
     * @return array
     */
    private function retrieve(?HttpRequest $request, string $type, string $format): array
    {
        $path = '/mockserver/retrieve?type=' . urlencode($type) . '&format=' . urlencode($format);
        $body = $request !== null ? json_encode($request->toArray() ?: new \stdClass(), JSON_THROW_ON_ERROR) : '';

        $response = $this->put($path, $body);

        $status = $response->getStatusCode();
        $responseBody = (string) $response->getBody();

        if ($status >= 400) {
            throw new MockServerException("Retrieve ({$type}) failed (HTTP {$status}): {$responseBody}");
        }

        if ($responseBody !== '') {
            $parsed = json_decode($responseBody, true);
            if (is_array($parsed)) {
                return $parsed;
            }
        }

        return [];
    }

    /**
     * Retrieve expectations with an {@see ExpectationFilter} applied as query
     * string parameters, preserving each expectation's metadata JSON shape.
     */
    private function retrieveFiltered(?HttpRequest $request, string $type, ExpectationFilter $filter): array
    {
        $path = '/mockserver/retrieve?type=' . urlencode($type) . '&format=JSON';
        $query = $filter->toQueryString();
        if ($query !== '') {
            $path .= '&' . $query;
        }
        $body = $request !== null ? json_encode($request->toArray() ?: new \stdClass(), JSON_THROW_ON_ERROR) : '';

        $response = $this->put($path, $body);

        $status = $response->getStatusCode();
        $responseBody = (string) $response->getBody();

        if ($status >= 400) {
            throw new MockServerException("Retrieve ({$type}) failed (HTTP {$status}): {$responseBody}");
        }

        return $responseBody !== '' ? self::decodeExpectations($responseBody) : [];
    }

    /**
     * Decode an expectation list as {@code json_decode(..., true)} does, except
     * that each {@code metadata} keeps its JSON shape: an associative decode
     * turns {@code {}} into {@code []}, which would re-encode as a JSON array.
     */
    private static function decodeExpectations(string $json): array
    {
        $assoc = json_decode($json, true);
        if (!is_array($assoc)) {
            return [];
        }
        $objects = json_decode($json, false);
        if (!is_array($objects) || !array_is_list($assoc)) {
            return $assoc;
        }
        foreach ($objects as $index => $expectation) {
            if ($expectation instanceof \stdClass && property_exists($expectation, 'metadata')
                && is_array($assoc[$index] ?? null)) {
                $assoc[$index]['metadata'] = self::preserveJsonShape($expectation->metadata);
            }
        }
        return $assoc;
    }

    /**
     * Convert an object-decoded JSON value to arrays, keeping a JSON object as
     * {@code \stdClass} only where an array would re-encode as a JSON list.
     */
    private static function preserveJsonShape(mixed $value): mixed
    {
        if (is_array($value)) {
            return array_map(static fn(mixed $item): mixed => self::preserveJsonShape($item), $value);
        }
        if ($value instanceof \stdClass) {
            $converted = array_map(
                static fn(mixed $item): mixed => self::preserveJsonShape($item),
                get_object_vars($value),
            );
            return array_is_list($converted) ? (object) $converted : $converted;
        }
        return $value;
    }

    /**
     * Retrieve filtered by expectation ID rather than by a request matcher.
     *
     * For ACTIVE_EXPECTATIONS the server returns only the expectation with that ID; for
     * every other type it returns the entries matching that expectation's request, exactly
     * as verify by expectation ID matches them. An unknown ID is rejected with a 400.
     *
     * @param string $expectationId The ID of the expectation to filter by
     * @param string $type REQUESTS, REQUEST_RESPONSES, RECORDED_EXPECTATIONS, ACTIVE_EXPECTATIONS or LOGS
     * @param string $format JSON
     * @return array
     */
    private function retrieveById(string $expectationId, string $type, string $format): array
    {
        $path = '/mockserver/retrieve?type=' . urlencode($type) . '&format=' . urlencode($format);
        $body = json_encode(['id' => $expectationId], JSON_THROW_ON_ERROR);

        $response = $this->put($path, $body);

        $status = $response->getStatusCode();
        $responseBody = (string) $response->getBody();

        if ($status >= 400) {
            throw new MockServerException("Retrieve by ID ({$type}) failed (HTTP {$status}): {$responseBody}");
        }

        if ($responseBody !== '') {
            $parsed = json_decode($responseBody, true);
            if (is_array($parsed)) {
                return $parsed;
            }
        }

        return [];
    }

    /**
     * Retrieve raw (non-JSON) response body, e.g. generated SDK code.
     *
     * @param HttpRequest|null $request Filter
     * @param string $type ACTIVE_EXPECTATIONS or RECORDED_EXPECTATIONS
     * @param string $format Code-generation format (JAVA, PYTHON, GO, ...)
     * @return string
     */
    private function retrieveRaw(?HttpRequest $request, string $type, string $format): string
    {
        $path = '/mockserver/retrieve?type=' . urlencode($type) . '&format=' . urlencode($format);
        $body = $request !== null ? json_encode($request->toArray() ?: new \stdClass(), JSON_THROW_ON_ERROR) : '';

        $response = $this->put($path, $body);

        $status = $response->getStatusCode();
        $responseBody = (string) $response->getBody();

        if ($status >= 400) {
            throw new MockServerException("Retrieve ({$type}) failed (HTTP {$status}): {$responseBody}");
        }

        return $responseBody;
    }

    /**
     * Send a PUT request to MockServer.
     *
     * @param string $path Request path (including query string if any)
     * @param string $body Request body (JSON by default, or raw bytes)
     * @param string|null $contentType Override the request Content-Type (e.g. application/octet-stream)
     * @return \Psr\Http\Message\ResponseInterface
     * @throws ConnectionException
     */
    private function put(
        string $path,
        string $body,
        ?string $contentType = null,
    ): \Psr\Http\Message\ResponseInterface {
        $options = [
            RequestOptions::BODY => $body,
        ];
        if ($contentType !== null) {
            // Per-request Content-Type override (replaces the client default).
            $options[RequestOptions::HEADERS] = ['Content-Type' => $contentType];
        }

        $options = $this->withControlPlaneOptions($options);

        try {
            return $this->httpClient->request('PUT', $path, $options);
        } catch (ConnectException $e) {
            throw new ConnectionException(
                "Failed to connect to MockServer at {$this->baseUri}: {$e->getMessage()}",
                (int) $e->getCode(),
                $e
            );
        } catch (GuzzleException $e) {
            throw new ConnectionException(
                "Request to MockServer at {$this->baseUri} failed: {$e->getMessage()}",
                (int) $e->getCode(),
                $e
            );
        }
    }

    /**
     * Send a GET request to MockServer (used by control-plane status endpoints).
     *
     * @param string $path Request path (including query string if any)
     * @return \Psr\Http\Message\ResponseInterface
     * @throws ConnectionException
     */
    private function get(string $path): \Psr\Http\Message\ResponseInterface
    {
        return $this->send('GET', $path);
    }

    /**
     * Send a DELETE request to MockServer (used by control-plane stop/clear endpoints).
     *
     * @param string $path Request path (including query string if any)
     * @return \Psr\Http\Message\ResponseInterface
     * @throws ConnectionException
     */
    private function delete(string $path): \Psr\Http\Message\ResponseInterface
    {
        return $this->send('DELETE', $path);
    }

    /**
     * Send a body-less request (GET/DELETE) to MockServer.
     *
     * @param string $method HTTP method
     * @param string $path Request path (including query string if any)
     * @return \Psr\Http\Message\ResponseInterface
     * @throws ConnectionException
     */
    private function send(string $method, string $path): \Psr\Http\Message\ResponseInterface
    {
        $options = $this->withControlPlaneOptions([]);

        try {
            return $this->httpClient->request($method, $path, $options);
        } catch (ConnectException $e) {
            throw new ConnectionException(
                "Failed to connect to MockServer at {$this->baseUri}: {$e->getMessage()}",
                (int) $e->getCode(),
                $e
            );
        } catch (GuzzleException $e) {
            throw new ConnectionException(
                "Request to MockServer at {$this->baseUri} failed: {$e->getMessage()}",
                (int) $e->getCode(),
                $e
            );
        }
    }
}
