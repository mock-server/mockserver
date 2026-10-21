<?php

declare(strict_types=1);

namespace MockServer;

use MockServer\Llm\HttpLlmResponse;

/**
 * Represents a MockServer expectation (request matcher + action).
 */
class Expectation implements \JsonSerializable
{
    /**
     * Raw expectation data captured by {@see Expectation::fromArray()}.
     *
     * When set (i.e. the expectation was reconstructed from a decoded JSON
     * payload such as the output of MockServer's {@code retrieve?format=php}
     * code generator), {@see Expectation::toArray()} returns this verbatim so
     * the round-trip is byte-faithful regardless of which expectation fields
     * are present. The typed builder path leaves this {@code null}.
     *
     * @var array<string, mixed>|null
     */
    private ?array $rawData = null;

    private ?string $id = null;
    private ?int $priority = null;
    private ?HttpRequest $httpRequest = null;
    private ?HttpResponse $httpResponse = null;
    private ?HttpForward $httpForward = null;
    private ?HttpTemplate $httpResponseTemplate = null;
    private ?HttpTemplate $httpForwardTemplate = null;
    private ?HttpForwardValidateAction $httpForwardValidateAction = null;
    private ?HttpForwardWithFallback $httpForwardWithFallback = null;
    private ?HttpClassCallback $httpResponseClassCallback = null;
    private ?HttpClassCallback $httpForwardClassCallback = null;
    private ?HttpError $httpError = null;
    private ?HttpSseResponse $httpSseResponse = null;
    private ?HttpWebSocketResponse $httpWebSocketResponse = null;
    private ?GrpcStreamResponse $grpcStreamResponse = null;
    private ?GrpcBidiResponse $grpcBidiResponse = null;
    private ?BinaryResponse $binaryResponse = null;
    private ?DnsResponse $dnsResponse = null;
    private ?HttpLlmResponse $httpLlmResponse = null;
    private ?string $scenarioName = null;
    private ?string $scenarioState = null;
    private ?string $newScenarioState = null;
    /** @var list<HttpResponse> */
    private array $httpResponses = [];
    private ?string $responseMode = null;
    /** @var list<int> */
    private array $responseWeights = [];
    private ?int $switchAfter = null;
    /** @var list<CrossProtocolScenario> */
    private array $crossProtocolScenarios = [];
    private ?Times $times = null;
    private ?TimeToLive $timeToLive = null;
    /** @var array<string, mixed> */
    private array $metadata = [];

    public function id(string $id): self
    {
        $this->id = $id;
        return $this;
    }

    public function priority(int $priority): self
    {
        $this->priority = $priority;
        return $this;
    }

    public function httpRequest(HttpRequest $request): self
    {
        $this->httpRequest = $request;
        return $this;
    }

    public function httpResponse(HttpResponse $response): self
    {
        $this->httpResponse = $response;
        return $this;
    }

    public function httpForward(HttpForward $forward): self
    {
        $this->httpForward = $forward;
        return $this;
    }

    /**
     * Respond with a rendered template (serialises to {@code httpResponseTemplate}).
     */
    public function httpResponseTemplate(HttpTemplate $httpResponseTemplate): self
    {
        $this->httpResponseTemplate = $httpResponseTemplate;
        return $this;
    }

    /**
     * Forward a request built from a rendered template (serialises to
     * {@code httpForwardTemplate}).
     */
    public function httpForwardTemplate(HttpTemplate $httpForwardTemplate): self
    {
        $this->httpForwardTemplate = $httpForwardTemplate;
        return $this;
    }

    /**
     * Forward and validate the exchange against an OpenAPI spec (serialises to
     * {@code httpForwardValidateAction}).
     */
    public function httpForwardValidateAction(HttpForwardValidateAction $httpForwardValidateAction): self
    {
        $this->httpForwardValidateAction = $httpForwardValidateAction;
        return $this;
    }

    /**
     * Forward with a fallback response on upstream failure (serialises to
     * {@code httpForwardWithFallback}).
     */
    public function httpForwardWithFallback(HttpForwardWithFallback $httpForwardWithFallback): self
    {
        $this->httpForwardWithFallback = $httpForwardWithFallback;
        return $this;
    }

    /**
     * Respond using a server-side callback class (class callback).
     *
     * The argument is either the fully-qualified name of a class on the
     * MockServer's classpath that implements {@code ExpectationResponseCallback},
     * or a pre-built {@see HttpClassCallback} (to attach a delay or the
     * {@code primary} flag). Serialises to {@code httpResponseClassCallback}.
     *
     * Note: object/closure callbacks (running the callback in *this* PHP
     * process) require a callback WebSocket that the REST-only PHP client does
     * not implement, so they are unavailable in PHP — use a class callback.
     *
     * @param string|HttpClassCallback $callback class name, or a built callback
     */
    public function httpResponseClassCallback(string|HttpClassCallback $callback): self
    {
        $this->httpResponseClassCallback = is_string($callback)
            ? new HttpClassCallback($callback)
            : $callback;
        return $this;
    }

    /**
     * Forward using a server-side callback class (class callback).
     *
     * The argument is either the fully-qualified name of a class on the
     * MockServer's classpath that implements {@code ExpectationForwardCallback},
     * or a pre-built {@see HttpClassCallback}. Serialises to
     * {@code httpForwardClassCallback}.
     *
     * Note: object/closure callbacks are unavailable in the REST-only PHP
     * client (they need a callback WebSocket) — use a class callback.
     *
     * @param string|HttpClassCallback $callback class name, or a built callback
     */
    public function httpForwardClassCallback(string|HttpClassCallback $callback): self
    {
        $this->httpForwardClassCallback = is_string($callback)
            ? new HttpClassCallback($callback)
            : $callback;
        return $this;
    }

    public function httpError(HttpError $error): self
    {
        $this->httpError = $error;
        return $this;
    }

    public function httpSseResponse(HttpSseResponse $sseResponse): self
    {
        $this->httpSseResponse = $sseResponse;
        return $this;
    }

    public function httpWebSocketResponse(HttpWebSocketResponse $webSocketResponse): self
    {
        $this->httpWebSocketResponse = $webSocketResponse;
        return $this;
    }

    public function grpcStreamResponse(GrpcStreamResponse $grpcStreamResponse): self
    {
        $this->grpcStreamResponse = $grpcStreamResponse;
        return $this;
    }

    public function grpcBidiResponse(GrpcBidiResponse $grpcBidiResponse): self
    {
        $this->grpcBidiResponse = $grpcBidiResponse;
        return $this;
    }

    public function binaryResponse(BinaryResponse $binaryResponse): self
    {
        $this->binaryResponse = $binaryResponse;
        return $this;
    }

    public function dnsResponse(DnsResponse $dnsResponse): self
    {
        $this->dnsResponse = $dnsResponse;
        return $this;
    }

    public function httpLlmResponse(HttpLlmResponse $httpLlmResponse): self
    {
        $this->httpLlmResponse = $httpLlmResponse;
        return $this;
    }

    public function scenarioName(string $scenarioName): self
    {
        $this->scenarioName = $scenarioName;
        return $this;
    }

    public function scenarioState(string $scenarioState): self
    {
        $this->scenarioState = $scenarioState;
        return $this;
    }

    public function newScenarioState(string $newScenarioState): self
    {
        $this->newScenarioState = $newScenarioState;
        return $this;
    }

    /**
     * Set the list of responses to serve (replaces any previously set list).
     *
     * Takes priority over the singular {@see Expectation::httpResponse()} when
     * present. Combine with {@see Expectation::responseMode()} to control how a
     * response is selected per request.
     *
     * @param HttpResponse ...$httpResponses
     */
    public function httpResponses(HttpResponse ...$httpResponses): self
    {
        $this->httpResponses = array_values($httpResponses);
        return $this;
    }

    /**
     * Add a single response to the {@code httpResponses} list.
     */
    public function addHttpResponse(HttpResponse $httpResponse): self
    {
        $this->httpResponses[] = $httpResponse;
        return $this;
    }

    /**
     * Set the response-selection mode. Use a {@see ResponseMode} constant
     * (SEQUENTIAL, RANDOM, WEIGHTED, SWITCH).
     */
    public function responseMode(string $responseMode): self
    {
        $this->responseMode = $responseMode;
        return $this;
    }

    /**
     * Set the relative weights index-aligned with {@code httpResponses}
     * (used when {@code responseMode} is WEIGHTED).
     *
     * @param int ...$responseWeights
     */
    public function responseWeights(int ...$responseWeights): self
    {
        $this->responseWeights = array_values($responseWeights);
        return $this;
    }

    /**
     * Set the number of requests served per response block before advancing
     * (used when {@code responseMode} is SWITCH).
     */
    public function switchAfter(int $switchAfter): self
    {
        $this->switchAfter = $switchAfter;
        return $this;
    }

    /**
     * Set the list of cross-protocol scenario transitions (replaces any
     * previously set list).
     *
     * @param CrossProtocolScenario ...$crossProtocolScenarios
     */
    public function crossProtocolScenarios(CrossProtocolScenario ...$crossProtocolScenarios): self
    {
        $this->crossProtocolScenarios = array_values($crossProtocolScenarios);
        return $this;
    }

    /**
     * Add a single cross-protocol scenario transition.
     */
    public function addCrossProtocolScenario(CrossProtocolScenario $crossProtocolScenario): self
    {
        $this->crossProtocolScenarios[] = $crossProtocolScenario;
        return $this;
    }

    public function times(Times $times): self
    {
        $this->times = $times;
        return $this;
    }

    public function timeToLive(TimeToLive $timeToLive): self
    {
        $this->timeToLive = $timeToLive;
        return $this;
    }

    /**
     * Set the expectation's metadata (replaces any previously set metadata).
     *
     * Metadata is free-form data (owner, ticket, tags, ...) stored with the
     * expectation and returned on retrieve; it never affects matching but can
     * select expectations to retrieve or clear (see {@see ExpectationFilter}).
     * Values may be any JSON value, including null. Use {@code new \stdClass()}
     * for a nested empty JSON object, since PHP's {@code []} encodes as a list.
     *
     * @param array<string, mixed> $metadata
     */
    public function metadata(array $metadata): self
    {
        $this->metadata = $metadata;
        return $this;
    }

    /**
     * Add (or replace) a single metadata entry.
     */
    public function addMetadata(string $key, mixed $value): self
    {
        $this->metadata[$key] = $value;
        return $this;
    }

    /**
     * @return array<string, mixed> the metadata (empty when none is set)
     */
    public function getMetadata(): array
    {
        if ($this->rawData !== null) {
            $metadata = $this->rawData['metadata'] ?? [];
            return is_array($metadata) ? $metadata : (array) $metadata;
        }
        return $this->metadata;
    }

    public function getId(): ?string
    {
        return $this->id;
    }

    public function getPriority(): ?int
    {
        return $this->priority;
    }

    public function getHttpRequest(): ?HttpRequest
    {
        return $this->httpRequest;
    }

    public function getHttpResponse(): ?HttpResponse
    {
        return $this->httpResponse;
    }

    public function getHttpForward(): ?HttpForward
    {
        return $this->httpForward;
    }

    public function getHttpResponseClassCallback(): ?HttpClassCallback
    {
        return $this->httpResponseClassCallback;
    }

    public function getHttpForwardClassCallback(): ?HttpClassCallback
    {
        return $this->httpForwardClassCallback;
    }

    public function getHttpError(): ?HttpError
    {
        return $this->httpError;
    }

    public function getTimes(): ?Times
    {
        return $this->times;
    }

    public function getTimeToLive(): ?TimeToLive
    {
        return $this->timeToLive;
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
        if ($this->rawData !== null) {
            return $this->rawData;
        }

        $data = [];

        if ($this->id !== null) {
            $data['id'] = $this->id;
        }
        if ($this->priority !== null) {
            $data['priority'] = $this->priority;
        }
        if ($this->httpRequest !== null) {
            // An empty matcher must encode as {} (it matches every request), not [].
            $data['httpRequest'] = $this->httpRequest->toArray() ?: new \stdClass();
        }
        if ($this->httpResponse !== null) {
            $data['httpResponse'] = $this->httpResponse->toArray();
        }
        if ($this->httpForward !== null) {
            $data['httpForward'] = $this->httpForward->toArray();
        }
        if ($this->httpResponseTemplate !== null) {
            $data['httpResponseTemplate'] = $this->httpResponseTemplate->toArray();
        }
        if ($this->httpForwardTemplate !== null) {
            $data['httpForwardTemplate'] = $this->httpForwardTemplate->toArray();
        }
        if ($this->httpForwardValidateAction !== null) {
            $data['httpForwardValidateAction'] = $this->httpForwardValidateAction->toArray();
        }
        if ($this->httpForwardWithFallback !== null) {
            $data['httpForwardWithFallback'] = $this->httpForwardWithFallback->toArray();
        }
        if ($this->httpResponseClassCallback !== null) {
            $data['httpResponseClassCallback'] = $this->httpResponseClassCallback->toArray();
        }
        if ($this->httpForwardClassCallback !== null) {
            $data['httpForwardClassCallback'] = $this->httpForwardClassCallback->toArray();
        }
        if ($this->httpError !== null) {
            $data['httpError'] = $this->httpError->toArray();
        }
        if ($this->httpSseResponse !== null) {
            $data['httpSseResponse'] = $this->httpSseResponse->toArray();
        }
        if ($this->httpWebSocketResponse !== null) {
            $data['httpWebSocketResponse'] = $this->httpWebSocketResponse->toArray();
        }
        if ($this->grpcStreamResponse !== null) {
            $data['grpcStreamResponse'] = $this->grpcStreamResponse->toArray();
        }
        if ($this->grpcBidiResponse !== null) {
            $data['grpcBidiResponse'] = $this->grpcBidiResponse->toArray();
        }
        if ($this->binaryResponse !== null) {
            $data['binaryResponse'] = $this->binaryResponse->toArray();
        }
        if ($this->dnsResponse !== null) {
            $data['dnsResponse'] = $this->dnsResponse->toArray();
        }
        if ($this->httpLlmResponse !== null) {
            $data['httpLlmResponse'] = $this->httpLlmResponse->toArray();
        }
        if ($this->scenarioName !== null) {
            $data['scenarioName'] = $this->scenarioName;
        }
        if ($this->scenarioState !== null) {
            $data['scenarioState'] = $this->scenarioState;
        }
        if ($this->newScenarioState !== null) {
            $data['newScenarioState'] = $this->newScenarioState;
        }
        if ($this->httpResponses !== []) {
            $data['httpResponses'] = array_map(
                static fn(HttpResponse $response): array => $response->toArray(),
                $this->httpResponses,
            );
        }
        if ($this->responseMode !== null) {
            $data['responseMode'] = $this->responseMode;
        }
        if ($this->responseWeights !== []) {
            $data['responseWeights'] = $this->responseWeights;
        }
        if ($this->switchAfter !== null) {
            $data['switchAfter'] = $this->switchAfter;
        }
        if ($this->crossProtocolScenarios !== []) {
            $data['crossProtocolScenarios'] = array_map(
                static fn(CrossProtocolScenario $scenario): array => $scenario->toArray(),
                $this->crossProtocolScenarios,
            );
        }
        if ($this->times !== null) {
            $data['times'] = $this->times->toArray();
        }
        if ($this->timeToLive !== null) {
            $data['timeToLive'] = $this->timeToLive->toArray();
        }
        if ($this->metadata !== []) {
            $data['metadata'] = self::metadataObject($this->metadata);
        }

        return $data;
    }

    /**
     * Reconstruct an Expectation from a decoded JSON array (the inverse of
     * {@see Expectation::toArray()}).
     *
     * This is the factory used by code generated from
     * {@code retrieve?type=ACTIVE_EXPECTATIONS&format=php}: the generated code
     * does {@code Expectation::fromArray(json_decode($json, true))} and passes
     * the result to {@see MockServerClient::upsertExpectation()}.
     *
     * The decoded array is stored verbatim and replayed by {@see toArray()},
     * so every expectation field — including action types not yet modelled by
     * the typed builder — round-trips faithfully without lossy field-by-field
     * reconstruction.
     *
     * The {@code httpRequest} is also read into a typed {@see HttpRequest}
     * (see {@see HttpRequest::fromArray()}), returned by
     * {@see getHttpRequest()}; a conditional matcher keeps its if/then/else
     * branches.
     *
     * @param array<string, mixed> $data
     */
    public static function fromArray(array $data): self
    {
        $expectation = new self();
        if (array_key_exists('metadata', $data) && is_array($data['metadata'])) {
            $data['metadata'] = self::metadataObject($data['metadata']);
        }
        $expectation->rawData = $data;
        if (isset($data['httpRequest']) && is_array($data['httpRequest'])) {
            $expectation->httpRequest = HttpRequest::fromArray($data['httpRequest']);
        }
        return $expectation;
    }

    /**
     * Metadata is always a JSON object, but a PHP list (including an empty
     * array, or {@code {"0": ...}} decoded with {@code json_decode(..., true)})
     * would encode as a JSON array, so encode those as an object.
     *
     * @param array<array-key, mixed> $metadata
     * @return array<array-key, mixed>|\stdClass
     */
    private static function metadataObject(array $metadata): array|\stdClass
    {
        return array_is_list($metadata) ? (object) $metadata : $metadata;
    }
}
