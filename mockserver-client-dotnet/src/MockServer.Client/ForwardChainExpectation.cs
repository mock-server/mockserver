using MockServer.Client.Models;

namespace MockServer.Client;

/// <summary>
/// Fluent chain: after When(...), call Respond(...), Forward(...), or Error(...) to complete the expectation.
/// </summary>
public sealed class ForwardChainExpectation
{
    private readonly MockServerClient _client;
    private readonly Expectation _expectation;

    internal ForwardChainExpectation(MockServerClient client, Expectation expectation)
    {
        _client = client;
        _expectation = expectation;
    }

    /// <summary>
    /// Set the expectation ID.
    /// </summary>
    public ForwardChainExpectation WithId(string id)
    {
        _expectation.Id = id;
        return this;
    }

    /// <summary>
    /// Set the expectation priority.
    /// </summary>
    public ForwardChainExpectation WithPriority(int priority)
    {
        _expectation.Priority = priority;
        return this;
    }

    /// <summary>
    /// Set the expectation's metadata (string keys, any JSON value), replacing any set before.
    /// </summary>
    public ForwardChainExpectation WithMetadata(IDictionary<string, object?> metadata)
    {
        _expectation.Metadata = new Dictionary<string, object?>(metadata);
        return this;
    }

    /// <summary>
    /// Add one metadata entry to the expectation.
    /// </summary>
    public ForwardChainExpectation WithMetadata(string key, object? value)
    {
        (_expectation.Metadata ??= new Dictionary<string, object?>())[key] = value;
        return this;
    }

    /// <summary>
    /// Set the scenario name this expectation participates in.
    /// </summary>
    public ForwardChainExpectation WithScenarioName(string scenarioName)
    {
        _expectation.ScenarioName = scenarioName;
        return this;
    }

    /// <summary>
    /// Require the scenario to be in <paramref name="scenarioState"/> for this expectation to match.
    /// </summary>
    public ForwardChainExpectation WithScenarioState(string scenarioState)
    {
        _expectation.ScenarioState = scenarioState;
        return this;
    }

    /// <summary>
    /// Transition the scenario to <paramref name="newScenarioState"/> after this expectation matches.
    /// </summary>
    public ForwardChainExpectation WithNewScenarioState(string newScenarioState)
    {
        _expectation.NewScenarioState = newScenarioState;
        return this;
    }

    /// <summary>
    /// Set the response-selection mode (used with <see cref="WithHttpResponses"/>).
    /// </summary>
    public ForwardChainExpectation WithResponseMode(ResponseMode responseMode)
    {
        _expectation.ResponseMode = responseMode;
        return this;
    }

    /// <summary>
    /// Set the relative weights index-aligned with the responses (WEIGHTED mode).
    /// </summary>
    public ForwardChainExpectation WithResponseWeights(IEnumerable<int> responseWeights)
    {
        _expectation.ResponseWeights = new List<int>(responseWeights);
        return this;
    }

    /// <summary>
    /// Set the number of requests served per response block before advancing (SWITCH mode).
    /// </summary>
    public ForwardChainExpectation WithSwitchAfter(int switchAfter)
    {
        _expectation.SwitchAfter = switchAfter;
        return this;
    }

    /// <summary>
    /// Add a single cross-protocol trigger that advances a named scenario.
    /// </summary>
    public ForwardChainExpectation WithCrossProtocolScenario(CrossProtocolScenario crossProtocolScenario)
    {
        (_expectation.CrossProtocolScenarios ??= new List<CrossProtocolScenario>()).Add(crossProtocolScenario);
        return this;
    }

    /// <summary>
    /// Set the cross-protocol triggers that advance named scenarios.
    /// </summary>
    public ForwardChainExpectation WithCrossProtocolScenarios(IEnumerable<CrossProtocolScenario> crossProtocolScenarios)
    {
        _expectation.CrossProtocolScenarios = new List<CrossProtocolScenario>(crossProtocolScenarios);
        return this;
    }

    /// <summary>
    /// Complete the expectation with multiple responses served per <see cref="WithResponseMode"/>.
    /// </summary>
    public List<Expectation> Respond(IEnumerable<HttpResponse> responses)
    {
        _expectation.HttpResponses = new List<HttpResponse>(responses);
        return _client.UpsertExpectation(_expectation);
    }

    /// <summary>
    /// Complete the expectation with multiple responses (async).
    /// </summary>
    public Task<List<Expectation>> RespondAsync(IEnumerable<HttpResponse> responses)
    {
        _expectation.HttpResponses = new List<HttpResponse>(responses);
        return _client.UpsertExpectationAsync(_expectation);
    }

    /// <summary>
    /// Set multiple responses (served per <see cref="WithResponseMode"/>) without completing the expectation,
    /// so further chaining (e.g. <see cref="WithResponseWeights"/>) can follow.
    /// </summary>
    public ForwardChainExpectation WithHttpResponses(IEnumerable<HttpResponse> responses)
    {
        _expectation.HttpResponses = new List<HttpResponse>(responses);
        return this;
    }

    /// <summary>
    /// Complete the expectation with a response action.
    /// </summary>
    public List<Expectation> Respond(HttpResponse response)
    {
        _expectation.HttpResponse = response;
        return _client.UpsertExpectation(_expectation);
    }

    /// <summary>
    /// Complete the expectation with a response action (async).
    /// </summary>
    public Task<List<Expectation>> RespondAsync(HttpResponse response)
    {
        _expectation.HttpResponse = response;
        return _client.UpsertExpectationAsync(_expectation);
    }

    /// <summary>
    /// Complete the expectation with a forward action.
    /// </summary>
    public List<Expectation> Forward(HttpForward forward)
    {
        _expectation.HttpForward = forward;
        return _client.UpsertExpectation(_expectation);
    }

    /// <summary>
    /// Complete the expectation with a forward action (async).
    /// </summary>
    public Task<List<Expectation>> ForwardAsync(HttpForward forward)
    {
        _expectation.HttpForward = forward;
        return _client.UpsertExpectationAsync(_expectation);
    }

    /// <summary>
    /// Complete the expectation with a response template action.
    /// </summary>
    public List<Expectation> RespondWithTemplate(HttpTemplate template)
    {
        _expectation.HttpResponseTemplate = template;
        return _client.UpsertExpectation(_expectation);
    }

    /// <summary>
    /// Complete the expectation with a response template action (async).
    /// </summary>
    public Task<List<Expectation>> RespondWithTemplateAsync(HttpTemplate template)
    {
        _expectation.HttpResponseTemplate = template;
        return _client.UpsertExpectationAsync(_expectation);
    }

    /// <summary>
    /// Complete the expectation with a forward template action.
    /// </summary>
    public List<Expectation> ForwardWithTemplate(HttpTemplate template)
    {
        _expectation.HttpForwardTemplate = template;
        return _client.UpsertExpectation(_expectation);
    }

    /// <summary>
    /// Complete the expectation with a forward template action (async).
    /// </summary>
    public Task<List<Expectation>> ForwardWithTemplateAsync(HttpTemplate template)
    {
        _expectation.HttpForwardTemplate = template;
        return _client.UpsertExpectationAsync(_expectation);
    }

    /// <summary>
    /// Complete the expectation with a Server-Sent Events (SSE) response action.
    /// </summary>
    public List<Expectation> RespondWithSse(HttpSseResponse sseResponse)
    {
        _expectation.HttpSseResponse = sseResponse;
        return _client.UpsertExpectation(_expectation);
    }

    /// <summary>
    /// Complete the expectation with a Server-Sent Events (SSE) response action (async).
    /// </summary>
    public Task<List<Expectation>> RespondWithSseAsync(HttpSseResponse sseResponse)
    {
        _expectation.HttpSseResponse = sseResponse;
        return _client.UpsertExpectationAsync(_expectation);
    }

    /// <summary>
    /// Complete the expectation with a WebSocket response action.
    /// </summary>
    public List<Expectation> RespondWithWebSocket(HttpWebSocketResponse webSocketResponse)
    {
        _expectation.HttpWebSocketResponse = webSocketResponse;
        return _client.UpsertExpectation(_expectation);
    }

    /// <summary>
    /// Complete the expectation with a WebSocket response action (async).
    /// </summary>
    public Task<List<Expectation>> RespondWithWebSocketAsync(HttpWebSocketResponse webSocketResponse)
    {
        _expectation.HttpWebSocketResponse = webSocketResponse;
        return _client.UpsertExpectationAsync(_expectation);
    }

    /// <summary>
    /// Complete the expectation with a server-streaming gRPC response action.
    /// </summary>
    public List<Expectation> RespondWithGrpcStream(GrpcStreamResponse grpcStreamResponse)
    {
        _expectation.GrpcStreamResponse = grpcStreamResponse;
        return _client.UpsertExpectation(_expectation);
    }

    /// <summary>
    /// Complete the expectation with a server-streaming gRPC response action (async).
    /// </summary>
    public Task<List<Expectation>> RespondWithGrpcStreamAsync(GrpcStreamResponse grpcStreamResponse)
    {
        _expectation.GrpcStreamResponse = grpcStreamResponse;
        return _client.UpsertExpectationAsync(_expectation);
    }

    /// <summary>
    /// Complete the expectation with a raw binary response action.
    /// </summary>
    public List<Expectation> RespondWithBinary(BinaryResponse binaryResponse)
    {
        _expectation.BinaryResponse = binaryResponse;
        return _client.UpsertExpectation(_expectation);
    }

    /// <summary>
    /// Complete the expectation with a raw binary response action (async).
    /// </summary>
    public Task<List<Expectation>> RespondWithBinaryAsync(BinaryResponse binaryResponse)
    {
        _expectation.BinaryResponse = binaryResponse;
        return _client.UpsertExpectationAsync(_expectation);
    }

    /// <summary>
    /// Complete the expectation with a DNS response action.
    /// </summary>
    public List<Expectation> RespondWithDns(DnsResponse dnsResponse)
    {
        _expectation.DnsResponse = dnsResponse;
        return _client.UpsertExpectation(_expectation);
    }

    /// <summary>
    /// Complete the expectation with a DNS response action (async).
    /// </summary>
    public Task<List<Expectation>> RespondWithDnsAsync(DnsResponse dnsResponse)
    {
        _expectation.DnsResponse = dnsResponse;
        return _client.UpsertExpectationAsync(_expectation);
    }

    /// <summary>
    /// Complete the expectation with a response class-callback action — a server-side
    /// class (implementing <c>ExpectationResponseCallback</c>) produces the response.
    /// REST-only; the class need not exist when the expectation is registered.
    /// </summary>
    public List<Expectation> RespondWithClassCallback(string callbackClass)
        => RespondWithClassCallback(HttpClassCallback.Of(callbackClass));

    /// <summary>
    /// Complete the expectation with a response class-callback action (async).
    /// </summary>
    public Task<List<Expectation>> RespondWithClassCallbackAsync(string callbackClass)
        => RespondWithClassCallbackAsync(HttpClassCallback.Of(callbackClass));

    /// <summary>
    /// Complete the expectation with a response class-callback action.
    /// </summary>
    public List<Expectation> RespondWithClassCallback(HttpClassCallback classCallback)
    {
        _expectation.HttpResponseClassCallback = classCallback;
        return _client.UpsertExpectation(_expectation);
    }

    /// <summary>
    /// Complete the expectation with a response class-callback action (async).
    /// </summary>
    public Task<List<Expectation>> RespondWithClassCallbackAsync(HttpClassCallback classCallback)
    {
        _expectation.HttpResponseClassCallback = classCallback;
        return _client.UpsertExpectationAsync(_expectation);
    }

    /// <summary>
    /// Complete the expectation with a forward class-callback action — a server-side
    /// class (implementing <c>ExpectationForwardCallback</c> /
    /// <c>ExpectationForwardAndResponseCallback</c>) produces the request to forward.
    /// REST-only; the class need not exist when the expectation is registered.
    /// </summary>
    public List<Expectation> ForwardWithClassCallback(string callbackClass)
        => ForwardWithClassCallback(HttpClassCallback.Of(callbackClass));

    /// <summary>
    /// Complete the expectation with a forward class-callback action (async).
    /// </summary>
    public Task<List<Expectation>> ForwardWithClassCallbackAsync(string callbackClass)
        => ForwardWithClassCallbackAsync(HttpClassCallback.Of(callbackClass));

    /// <summary>
    /// Complete the expectation with a forward class-callback action.
    /// </summary>
    public List<Expectation> ForwardWithClassCallback(HttpClassCallback classCallback)
    {
        _expectation.HttpForwardClassCallback = classCallback;
        return _client.UpsertExpectation(_expectation);
    }

    /// <summary>
    /// Complete the expectation with a forward class-callback action (async).
    /// </summary>
    public Task<List<Expectation>> ForwardWithClassCallbackAsync(HttpClassCallback classCallback)
    {
        _expectation.HttpForwardClassCallback = classCallback;
        return _client.UpsertExpectationAsync(_expectation);
    }

    /// <summary>
    /// Complete the expectation with an error action (drops/corrupts the connection).
    /// </summary>
    public List<Expectation> Error(HttpError error)
    {
        _expectation.HttpError = error;
        return _client.UpsertExpectation(_expectation);
    }

    /// <summary>
    /// Complete the expectation with an error action (async).
    /// </summary>
    public Task<List<Expectation>> ErrorAsync(HttpError error)
    {
        _expectation.HttpError = error;
        return _client.UpsertExpectationAsync(_expectation);
    }
}
