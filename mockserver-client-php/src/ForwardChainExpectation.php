<?php

declare(strict_types=1);

namespace MockServer;

/**
 * Fluent chain returned by MockServerClient::when() to attach an action.
 *
 * @example
 *   $client->when(HttpRequest::request()->path('/hello'))
 *       ->respond(HttpResponse::response()->statusCode(200)->body('world'));
 */
class ForwardChainExpectation
{
    private MockServerClient $client;
    private Expectation $expectation;

    public function __construct(MockServerClient $client, Expectation $expectation)
    {
        $this->client = $client;
        $this->expectation = $expectation;
    }

    /**
     * Set an explicit expectation ID.
     */
    public function withId(string $id): self
    {
        $this->expectation->id($id);
        return $this;
    }

    /**
     * Set the expectation priority.
     */
    public function withPriority(int $priority): self
    {
        $this->expectation->priority($priority);
        return $this;
    }

    /**
     * Set the expectation metadata (see {@see Expectation::metadata()}).
     *
     * @param array<string, mixed> $metadata
     */
    public function withMetadata(array $metadata): self
    {
        $this->expectation->metadata($metadata);
        return $this;
    }

    /**
     * Respond with the given HTTP response.
     *
     * @return array The created expectation(s) as returned by the server.
     */
    public function respond(HttpResponse $response): array
    {
        $this->expectation->httpResponse($response);
        return $this->client->upsertExpectation($this->expectation);
    }

    /**
     * Forward to the given destination.
     *
     * @return array The created expectation(s) as returned by the server.
     */
    public function forward(HttpForward $forward): array
    {
        $this->expectation->httpForward($forward);
        return $this->client->upsertExpectation($this->expectation);
    }

    /**
     * Respond with a rendered template ({@code httpResponseTemplate}).
     *
     * @return array The created expectation(s) as returned by the server.
     */
    public function respondWithTemplate(HttpTemplate $httpTemplate): array
    {
        $this->expectation->httpResponseTemplate($httpTemplate);
        return $this->client->upsertExpectation($this->expectation);
    }

    /**
     * Forward a request built from a rendered template ({@code httpForwardTemplate}).
     *
     * @return array The created expectation(s) as returned by the server.
     */
    public function forwardWithTemplate(HttpTemplate $httpTemplate): array
    {
        $this->expectation->httpForwardTemplate($httpTemplate);
        return $this->client->upsertExpectation($this->expectation);
    }

    /**
     * Forward and validate the exchange against an OpenAPI spec
     * ({@code httpForwardValidateAction}).
     *
     * @return array The created expectation(s) as returned by the server.
     */
    public function forwardWithValidation(HttpForwardValidateAction $validateAction): array
    {
        $this->expectation->httpForwardValidateAction($validateAction);
        return $this->client->upsertExpectation($this->expectation);
    }

    /**
     * Forward with a fallback response on upstream failure
     * ({@code httpForwardWithFallback}).
     *
     * @return array The created expectation(s) as returned by the server.
     */
    public function forwardWithFallback(HttpForwardWithFallback $forwardWithFallback): array
    {
        $this->expectation->httpForwardWithFallback($forwardWithFallback);
        return $this->client->upsertExpectation($this->expectation);
    }

    /**
     * Return an error.
     *
     * @return array The created expectation(s) as returned by the server.
     */
    public function error(HttpError $error): array
    {
        $this->expectation->httpError($error);
        return $this->client->upsertExpectation($this->expectation);
    }

    /**
     * Respond with a Server-Sent Events (SSE) stream.
     *
     * @return array The created expectation(s) as returned by the server.
     */
    public function respondWithSse(HttpSseResponse $sseResponse): array
    {
        $this->expectation->httpSseResponse($sseResponse);
        return $this->client->upsertExpectation($this->expectation);
    }

    /**
     * Respond by upgrading to a WebSocket and sending the given messages.
     *
     * @return array The created expectation(s) as returned by the server.
     */
    public function respondWithWebSocket(HttpWebSocketResponse $webSocketResponse): array
    {
        $this->expectation->httpWebSocketResponse($webSocketResponse);
        return $this->client->upsertExpectation($this->expectation);
    }

    /**
     * Respond with a gRPC server-stream.
     *
     * @return array The created expectation(s) as returned by the server.
     */
    public function respondWithGrpcStream(GrpcStreamResponse $grpcStreamResponse): array
    {
        $this->expectation->grpcStreamResponse($grpcStreamResponse);
        return $this->client->upsertExpectation($this->expectation);
    }

    /**
     * Respond with a gRPC bidirectional stream.
     *
     * @return array The created expectation(s) as returned by the server.
     */
    public function respondWithGrpcBidi(GrpcBidiResponse $grpcBidiResponse): array
    {
        $this->expectation->grpcBidiResponse($grpcBidiResponse);
        return $this->client->upsertExpectation($this->expectation);
    }

    /**
     * Respond with a raw binary payload.
     *
     * @return array The created expectation(s) as returned by the server.
     */
    public function respondWithBinary(BinaryResponse $binaryResponse): array
    {
        $this->expectation->binaryResponse($binaryResponse);
        return $this->client->upsertExpectation($this->expectation);
    }

    /**
     * Respond with a DNS answer.
     *
     * @return array The created expectation(s) as returned by the server.
     */
    public function respondWithDns(DnsResponse $dnsResponse): array
    {
        $this->expectation->dnsResponse($dnsResponse);
        return $this->client->upsertExpectation($this->expectation);
    }
}
