using System.Net.Http;
using System.Text;
using System.Text.Json;
using MockServer.Client.Exceptions;
using MockServer.Client.Models;

namespace MockServer.Client;

/// <summary>
/// Synchronous and asynchronous client for the MockServer control-plane REST API.
/// Uses HttpClient and System.Text.Json internally.
/// </summary>
/// <example>
/// <code>
/// using var client = new MockServerClient("localhost", 1080);
/// client.When(
///     HttpRequest.Request().WithMethod("GET").WithPath("/hello")
/// ).Respond(
///     HttpResponse.Response().WithStatusCode(200).WithBody("world")
/// );
/// client.Verify(HttpRequest.Request().WithPath("/hello"), VerificationTimes.AtLeastTimes(1));
/// client.Reset();
/// </code>
/// </example>
public sealed class MockServerClient : IDisposable
{
    private readonly HttpClient _httpClient;
    private readonly string _baseUrl;
    private readonly bool _ownsHttpClient;
    private Func<string>? _controlPlaneBearerTokenSupplier;
    private string? _controlPlaneBasicAuthorization;
    private readonly MockServerTlsOptions? _tlsOptions;

    private static readonly JsonSerializerOptions JsonOptions = new()
    {
        DefaultIgnoreCondition = System.Text.Json.Serialization.JsonIgnoreCondition.WhenWritingNull,
        PropertyNamingPolicy = JsonNamingPolicy.CamelCase,
        // Lets a header / query / cookie / path-parameter matcher value that starts with '!' or '?' be
        // sent verbatim (object form) rather than being read by the server as a negation / optional
        // marker; also decodes both wire forms on retrieve. See HttpRequestConverter.
        Converters = { new Models.HttpRequestConverter() }
    };

    /// <summary>
    /// Creates a new MockServer client.
    /// </summary>
    /// <param name="host">MockServer host (e.g., "localhost").</param>
    /// <param name="port">MockServer port (default 1080).</param>
    /// <param name="contextPath">Optional context path (e.g., "/mockserver-proxy").</param>
    /// <param name="secure">Use HTTPS if true.</param>
    public MockServerClient(string host, int port = 1080, string contextPath = "", bool secure = false)
    {
        var scheme = secure ? "https" : "http";
        var ctxPath = string.IsNullOrEmpty(contextPath)
            ? ""
            : (contextPath.StartsWith("/") ? contextPath : "/" + contextPath);
        _baseUrl = $"{scheme}://{host}:{port}{ctxPath}";
        _httpClient = new HttpClient { Timeout = TimeSpan.FromSeconds(60) };
        _ownsHttpClient = true;
    }

    /// <summary>
    /// Creates a new MockServer client with a pre-configured HttpClient.
    /// </summary>
    /// <param name="baseUrl">The base URL of the MockServer (e.g., "http://localhost:1080").</param>
    /// <param name="httpClient">A pre-configured HttpClient instance.</param>
    public MockServerClient(string baseUrl, HttpClient httpClient)
    {
        _baseUrl = baseUrl.TrimEnd('/');
        _httpClient = httpClient;
        _ownsHttpClient = false;
    }

    /// <summary>
    /// Internal constructor used by <see cref="MockServerClientBuilder"/> to construct a
    /// client around a pre-built (possibly TLS-configured) HttpClient with a bearer-token supplier.
    /// </summary>
    private MockServerClient(
        string baseUrl, HttpClient httpClient, bool ownsHttpClient, Func<string>? controlPlaneBearerTokenSupplier,
        MockServerTlsOptions? tlsOptions, string? controlPlaneBasicAuthorization = null)
    {
        _baseUrl = baseUrl.TrimEnd('/');
        _httpClient = httpClient;
        _ownsHttpClient = ownsHttpClient;
        _controlPlaneBearerTokenSupplier = controlPlaneBearerTokenSupplier;
        _controlPlaneBasicAuthorization = controlPlaneBasicAuthorization;
        _tlsOptions = tlsOptions;
        WarnIfBasicWithoutTls();
    }

    /// <summary>
    /// Begin building a client with control-plane authentication and/or custom TLS.
    /// </summary>
    /// <param name="host">MockServer host (e.g., "localhost").</param>
    /// <param name="port">MockServer port (default 1080).</param>
    public static MockServerClientBuilder Builder(string host, int port = 1080)
        => new MockServerClientBuilder(host, port);

    internal static MockServerClient CreateConfigured(
        string host, int port, string contextPath, bool secure,
        HttpClient httpClient, bool ownsHttpClient, Func<string>? controlPlaneBearerTokenSupplier,
        MockServerTlsOptions? tlsOptions = null, string? controlPlaneBasicAuthorization = null)
    {
        var scheme = secure ? "https" : "http";
        var ctxPath = string.IsNullOrEmpty(contextPath)
            ? ""
            : (contextPath.StartsWith("/") ? contextPath : "/" + contextPath);
        var baseUrl = $"{scheme}://{host}:{port}{ctxPath}";
        return new MockServerClient(baseUrl, httpClient, ownsHttpClient, controlPlaneBearerTokenSupplier, tlsOptions, controlPlaneBasicAuthorization);
    }

    // -------------------------------------------------------------------
    // Control-plane authentication
    // -------------------------------------------------------------------

    /// <summary>
    /// Attach <c>Authorization: Bearer &lt;token&gt;</c> to every control-plane request this
    /// client sends. The client only ever issues control-plane requests, so all of its
    /// requests carry the header. Returns this client for chaining.
    /// </summary>
    public MockServerClient WithControlPlaneBearerToken(string token)
    {
        if (string.IsNullOrEmpty(token))
            throw new ArgumentException("token is required", nameof(token));
        _controlPlaneBearerTokenSupplier = () => token;
        return this;
    }

    /// <summary>
    /// Attach <c>Authorization: Bearer &lt;token&gt;</c> to every control-plane request,
    /// evaluating <paramref name="tokenSupplier"/> per request (e.g., for rotating tokens).
    /// Returns this client for chaining.
    /// </summary>
    public MockServerClient WithControlPlaneBearerToken(Func<string> tokenSupplier)
    {
        _controlPlaneBearerTokenSupplier = tokenSupplier ?? throw new ArgumentNullException(nameof(tokenSupplier));
        return this;
    }

    /// <summary>
    /// Attach <c>Authorization: Basic ...</c> with <paramref name="username"/> and
    /// <paramref name="password"/> to every control-plane request and to the callback/breakpoint
    /// WebSocket upgrade. MockServer refuses Basic credentials without TLS, so use a secure client.
    /// A bearer token, when also configured, is sent instead. Returns this client for chaining.
    /// </summary>
    public MockServerClient WithControlPlaneBasicAuthentication(string username, string password)
    {
        _controlPlaneBasicAuthorization = BasicAuthorization(username, password);
        WarnIfBasicWithoutTls();
        return this;
    }

    private void WarnIfBasicWithoutTls()
    {
        if (_controlPlaneBasicAuthorization != null && _baseUrl.StartsWith("http://", StringComparison.OrdinalIgnoreCase))
        {
            System.Diagnostics.Trace.TraceWarning(
                "MockServer client: control-plane Basic credentials are configured without TLS, so the password can be read on the network, " +
                "and MockServer refuses them unless controlPlaneBasicAuthenticationAllowInsecure is set (tests only); use a secure client");
        }
    }

    internal static string BasicAuthorization(string username, string password)
    {
        if (string.IsNullOrEmpty(username) || username.IndexOf(':') >= 0)
            throw new ArgumentException("username is required and must not contain ':'", nameof(username));
        if (password == null)
            throw new ArgumentNullException(nameof(password));
        return "Basic " + Convert.ToBase64String(System.Text.Encoding.UTF8.GetBytes(username + ":" + password));
    }

    /// <summary>
    /// Apply the configured control-plane bearer token, else the Basic credentials, to an
    /// outgoing request. Evaluated per request so a supplier can return a rotating token.
    /// </summary>
    private void ApplyControlPlaneAuth(HttpRequestMessage request)
    {
        var token = _controlPlaneBearerTokenSupplier?.Invoke();
        if (!string.IsNullOrEmpty(token))
        {
            request.Headers.Authorization =
                new System.Net.Http.Headers.AuthenticationHeaderValue("Bearer", token);
            return;
        }
        var basic = _controlPlaneBasicAuthorization;
        if (basic != null)
        {
            request.Headers.Authorization =
                new System.Net.Http.Headers.AuthenticationHeaderValue("Basic", basic.Substring("Basic ".Length));
        }
    }

    // -------------------------------------------------------------------
    // Fluent API
    // -------------------------------------------------------------------

    /// <summary>
    /// Begin building an expectation with the fluent When/Respond API.
    /// </summary>
    public ForwardChainExpectation When(HttpRequest request, Times? times = null, TimeToLive? timeToLive = null, int? priority = null)
    {
        var expectation = new Expectation
        {
            HttpRequest = request,
            Times = times,
            TimeToLive = timeToLive,
            Priority = priority
        };
        return new ForwardChainExpectation(this, expectation);
    }

    // -------------------------------------------------------------------
    // Core API methods
    // -------------------------------------------------------------------

    /// <summary>
    /// Create or update one or more expectations.
    /// </summary>
    public List<Expectation> Upsert(params Expectation[] expectations)
        => UpsertAsync(expectations).GetAwaiter().GetResult();

    /// <summary>
    /// Create or update one or more expectations (async).
    /// </summary>
    public async Task<List<Expectation>> UpsertAsync(params Expectation[] expectations)
    {
        var json = JsonSerializer.Serialize(expectations, JsonOptions);
        var (statusCode, body) = await PutAsync("/mockserver/expectation", json).ConfigureAwait(false);

        if (statusCode == 400)
            throw new MockServerClientException($"Invalid expectation: {body}");
        if (statusCode >= 400)
            throw new MockServerClientException($"Failed to upsert expectations (HTTP {statusCode}): {body}");

        if (!string.IsNullOrEmpty(body))
        {
            var result = JsonSerializer.Deserialize<List<Expectation>>(body, JsonOptions);
            if (result != null) return result;
        }
        return new List<Expectation>(expectations);
    }

    /// <summary>
    /// Register expectations from an OpenAPI v3 specification.
    /// </summary>
    /// <param name="expectation">The OpenAPI spec (URL/classpath/inline payload) and optional operation-to-response map.</param>
    public void OpenApiExpectation(OpenApiExpectation expectation)
        => OpenApiExpectationAsync(expectation).GetAwaiter().GetResult();

    /// <summary>
    /// Register expectations from an OpenAPI v3 specification (async).
    /// </summary>
    public async Task OpenApiExpectationAsync(OpenApiExpectation expectation)
    {
        if (expectation == null) throw new ArgumentNullException(nameof(expectation));
        var json = JsonSerializer.Serialize(expectation, JsonOptions);
        var (statusCode, body) = await PutAsync("/mockserver/openapi", json).ConfigureAwait(false);

        if (statusCode == 400)
            throw new MockServerClientException($"Invalid OpenAPI expectation: {body}");
        if (statusCode >= 400)
            throw new MockServerClientException($"Failed to create OpenAPI expectation (HTTP {statusCode}): {body}");
    }

    /// <summary>
    /// Register expectations from an OpenAPI v3 spec given by URL, classpath, or inline payload.
    /// </summary>
    public void OpenApiExpectation(string specUrlOrPayload)
        => OpenApiExpectation(Models.OpenApiExpectation.Of(specUrlOrPayload));

    /// <summary>
    /// Register expectations from an OpenAPI v3 spec given by URL, classpath, or inline payload (async).
    /// </summary>
    public Task OpenApiExpectationAsync(string specUrlOrPayload)
        => OpenApiExpectationAsync(Models.OpenApiExpectation.Of(specUrlOrPayload));

    /// <summary>
    /// Verify that a request has been received a specific number of times.
    /// </summary>
    /// <exception cref="VerificationException">If the verification fails (HTTP 406).</exception>
    public void Verify(HttpRequest request, VerificationTimes? times = null)
        => VerifyAsync(request, times).GetAwaiter().GetResult();

    /// <summary>
    /// Verify that a request has been received a specific number of times (async).
    /// </summary>
    public async Task VerifyAsync(HttpRequest request, VerificationTimes? times = null)
    {
        var verification = new Verification { HttpRequest = request, Times = times };
        var json = JsonSerializer.Serialize(verification, JsonOptions);
        var (statusCode, body) = await PutAsync("/mockserver/verify", json).ConfigureAwait(false);

        if (statusCode == 406)
            throw new VerificationException(body ?? "Verification failed");
        if (statusCode >= 400)
            throw new MockServerClientException($"Failed to verify (HTTP {statusCode}): {body}");
    }

    /// <summary>
    /// Verify that a request-response pair was recorded (proxied/forwarded).
    /// When <paramref name="response"/> is provided, verification switches from
    /// "request received" to "response received" mode.
    /// </summary>
    /// <param name="request">Optional request matcher. Null to verify response only.</param>
    /// <param name="response">Response matcher to verify against recorded responses.</param>
    /// <param name="times">Optional number of times the response should have been recorded.</param>
    /// <exception cref="VerificationException">If the verification fails (HTTP 406).</exception>
    public void Verify(HttpRequest? request, HttpResponse response, VerificationTimes? times = null)
        => VerifyAsync(request, response, times).GetAwaiter().GetResult();

    /// <summary>
    /// Verify that a request-response pair was recorded (async).
    /// </summary>
    public async Task VerifyAsync(HttpRequest? request, HttpResponse response, VerificationTimes? times = null)
    {
        var verification = new Verification { HttpRequest = request, HttpResponse = response, Times = times };
        var json = JsonSerializer.Serialize(verification, JsonOptions);
        var (statusCode, body) = await PutAsync("/mockserver/verify", json).ConfigureAwait(false);

        if (statusCode == 406)
            throw new VerificationException(body ?? "Verification failed");
        if (statusCode >= 400)
            throw new MockServerClientException($"Failed to verify (HTTP {statusCode}): {body}");
    }

    /// <summary>
    /// Verify that MockServer received no requests at all.
    /// </summary>
    /// <exception cref="VerificationException">If any request was received (HTTP 406).</exception>
    public void VerifyZeroInteractions()
        => VerifyZeroInteractionsAsync().GetAwaiter().GetResult();

    /// <summary>
    /// Verify that MockServer received no requests at all (async).
    /// </summary>
    public Task VerifyZeroInteractionsAsync()
        => VerifyAsync(HttpRequest.Request().Build(), VerificationTimes.AtMostTimes(0));

    /// <summary>
    /// Verify that requests were received in a specific sequence.
    /// </summary>
    /// <exception cref="VerificationException">If the verification fails (HTTP 406).</exception>
    public void VerifySequence(params HttpRequest[] requests)
        => VerifySequenceAsync(requests).GetAwaiter().GetResult();

    /// <summary>
    /// Verify that requests were received in a specific sequence (async).
    /// </summary>
    public async Task VerifySequenceAsync(params HttpRequest[] requests)
    {
        var verification = new VerificationSequence { HttpRequests = new List<HttpRequest>(requests) };
        var json = JsonSerializer.Serialize(verification, JsonOptions);
        var (statusCode, body) = await PutAsync("/mockserver/verifySequence", json).ConfigureAwait(false);

        if (statusCode == 406)
            throw new VerificationException(body ?? "Verification sequence failed");
        if (statusCode >= 400)
            throw new MockServerClientException($"Failed to verify sequence (HTTP {statusCode}): {body}");
    }

    /// <summary>
    /// Verify that request-response pairs were recorded in a specific sequence.
    /// Requests and responses are index-aligned — the request at position i is
    /// paired with the response at position i.
    /// </summary>
    /// <param name="requests">Request matchers for the sequence.</param>
    /// <param name="responses">Response matchers index-aligned with <paramref name="requests"/>.</param>
    /// <exception cref="VerificationException">If the verification fails (HTTP 406).</exception>
    public void VerifySequence(IList<HttpRequest> requests, IList<HttpResponse> responses)
        => VerifySequenceAsync(requests, responses).GetAwaiter().GetResult();

    /// <summary>
    /// Verify that request-response pairs were recorded in a specific sequence (async).
    /// </summary>
    public async Task VerifySequenceAsync(IList<HttpRequest> requests, IList<HttpResponse> responses)
    {
        var verification = new VerificationSequence
        {
            HttpRequests = new List<HttpRequest>(requests),
            HttpResponses = new List<HttpResponse>(responses)
        };
        var json = JsonSerializer.Serialize(verification, JsonOptions);
        var (statusCode, body) = await PutAsync("/mockserver/verifySequence", json).ConfigureAwait(false);

        if (statusCode == 406)
            throw new VerificationException(body ?? "Verification sequence failed");
        if (statusCode >= 400)
            throw new MockServerClientException($"Failed to verify sequence (HTTP {statusCode}): {body}");
    }

    /// <summary>
    /// Clear expectations and/or logs matching a request.
    /// </summary>
    /// <param name="request">Optional request matcher to clear. Null clears all.</param>
    /// <param name="type">Type to clear: "all", "log", or "expectations".</param>
    public void Clear(HttpRequest? request = null, string? type = null)
        => ClearAsync(request, type).GetAwaiter().GetResult();

    /// <summary>
    /// Clear expectations and/or logs matching a request (async).
    /// </summary>
    public async Task ClearAsync(HttpRequest? request = null, string? type = null)
    {
        var path = "/mockserver/clear";
        if (!string.IsNullOrEmpty(type))
            path += $"?type={Uri.EscapeDataString(type)}";

        var json = request != null ? JsonSerializer.Serialize(request, JsonOptions) : "";
        var (statusCode, body) = await PutAsync(path, json).ConfigureAwait(false);

        if (statusCode >= 400)
            throw new MockServerClientException($"Failed to clear (HTTP {statusCode}): {body}");
    }

    /// <summary>
    /// Clear by expectation ID.
    /// </summary>
    public void ClearById(string expectationId, string? type = null)
        => ClearByIdAsync(expectationId, type).GetAwaiter().GetResult();

    /// <summary>
    /// Clear by expectation ID (async).
    /// </summary>
    public async Task ClearByIdAsync(string expectationId, string? type = null)
    {
        var path = "/mockserver/clear";
        if (!string.IsNullOrEmpty(type))
            path += $"?type={Uri.EscapeDataString(type)}";

        var json = JsonSerializer.Serialize(new { id = expectationId }, JsonOptions);
        var (statusCode, body) = await PutAsync(path, json).ConfigureAwait(false);

        if (statusCode >= 400)
            throw new MockServerClientException($"Failed to clear by id (HTTP {statusCode}): {body}");
    }

    /// <summary>
    /// Clear only the expectations that match both the optional request matcher and the filter, for example by
    /// metadata or because they have never matched a request. The event log is left untouched.
    /// </summary>
    /// <param name="request">Optional request matcher; null for all expectations.</param>
    /// <param name="filter">The metadata and/or unmatched selection (offset and limit are not supported for clear).</param>
    /// <exception cref="ArgumentException">the filter is null, selects nothing, or pages; nothing is sent.</exception>
    public void ClearByFilter(HttpRequest? request, ExpectationFilter filter)
        => ClearByFilterAsync(request, filter).GetAwaiter().GetResult();

    /// <summary>
    /// Clear the expectations matching the request matcher and the filter (async). See <see cref="ClearByFilter"/>.
    /// </summary>
    public async Task ClearByFilterAsync(HttpRequest? request, ExpectationFilter filter)
    {
        // fail closed: a filter selecting nothing would otherwise be sent as a clear of every expectation
        if (filter is null || !filter.HasSelection)
            throw new ArgumentException(
                "clear with a filter requires a metadata or unmatched selection; use Clear(request, \"expectations\") to clear without one",
                nameof(filter));
        if (filter.HasPaging)
            throw new ArgumentException("offset and limit are not supported for clear", nameof(filter));

        var json = request != null ? JsonSerializer.Serialize(request, JsonOptions) : "";
        var (statusCode, body) = await PutAsync("/mockserver/clear?type=expectations" + filter.ToQueryString(), json)
            .ConfigureAwait(false);

        if (statusCode >= 400)
            throw new MockServerClientException($"Failed to clear by filter (HTTP {statusCode}): {body}");
    }

    /// <summary>
    /// Clear the expectations whose metadata has <paramref name="key"/> with a value equal to
    /// <paramref name="value"/> (a string is compared as text, any other value as JSON). The event log is untouched.
    /// </summary>
    /// <exception cref="ArgumentException">the key is blank or the value is null (use <see cref="ClearByMetadataKey"/>
    /// for any value, or <see cref="ExpectationFilter.JsonNull"/> for a JSON null); nothing is sent.</exception>
    public void ClearByMetadata(string key, object value)
        => ClearByMetadataAsync(key, value).GetAwaiter().GetResult();

    /// <summary>Clear the expectations by metadata key and value (async). See <see cref="ClearByMetadata"/>.</summary>
    public Task ClearByMetadataAsync(string key, object value)
        => ClearByFilterAsync(null, ExpectationFilter.Filter().WithMetadata(key, value));

    /// <summary>Clear the expectations whose metadata has <paramref name="key"/>, whatever its value.</summary>
    public void ClearByMetadataKey(string key)
        => ClearByMetadataKeyAsync(key).GetAwaiter().GetResult();

    /// <summary>Clear the expectations by metadata key (async). See <see cref="ClearByMetadataKey"/>.</summary>
    public Task ClearByMetadataKeyAsync(string key)
        => ClearByFilterAsync(null, ExpectationFilter.Filter().WithMetadataKey(key));

    /// <summary>Clear the active expectations that have never matched a request. The event log is untouched.</summary>
    public void ClearUnmatchedExpectations()
        => ClearUnmatchedExpectationsAsync().GetAwaiter().GetResult();

    /// <summary>Clear the active expectations that have never matched a request (async).</summary>
    public Task ClearUnmatchedExpectationsAsync()
        => ClearByFilterAsync(null, ExpectationFilter.Filter().WithUnmatched(true));

    /// <summary>
    /// Reset all expectations and recorded requests.
    /// </summary>
    public void Reset() => ResetAsync().GetAwaiter().GetResult();

    /// <summary>
    /// Reset all expectations and recorded requests (async).
    /// </summary>
    public async Task ResetAsync()
    {
        var (statusCode, body) = await PutAsync("/mockserver/reset", "").ConfigureAwait(false);

        if (statusCode >= 400)
            throw new MockServerClientException($"Failed to reset (HTTP {statusCode}): {body}");
    }

    /// <summary>
    /// Retrieve recorded requests matching an optional filter.
    /// </summary>
    public List<HttpRequest> RetrieveRecordedRequests(HttpRequest? filter = null)
        => RetrieveRecordedRequestsAsync(filter).GetAwaiter().GetResult();

    /// <summary>
    /// Retrieve recorded requests matching an optional filter (async).
    /// </summary>
    public async Task<List<HttpRequest>> RetrieveRecordedRequestsAsync(HttpRequest? filter = null)
    {
        var json = filter != null ? JsonSerializer.Serialize(filter, JsonOptions) : "";
        var (statusCode, body) = await PutAsync("/mockserver/retrieve?type=requests&format=json", json).ConfigureAwait(false);

        if (statusCode >= 400)
            throw new MockServerClientException($"Failed to retrieve recorded requests (HTTP {statusCode}): {body}");

        if (!string.IsNullOrEmpty(body))
        {
            var result = JsonSerializer.Deserialize<List<HttpRequest>>(body, JsonOptions);
            if (result != null) return result;
        }
        return new List<HttpRequest>();
    }

    /// <summary>
    /// Retrieve active expectations matching an optional filter.
    /// </summary>
    public List<Expectation> RetrieveActiveExpectations(HttpRequest? filter = null)
        => RetrieveActiveExpectationsAsync(filter).GetAwaiter().GetResult();

    /// <summary>
    /// Retrieve active expectations matching an optional filter (async).
    /// </summary>
    public async Task<List<Expectation>> RetrieveActiveExpectationsAsync(HttpRequest? filter = null)
    {
        var json = filter != null ? JsonSerializer.Serialize(filter, JsonOptions) : "";
        var (statusCode, body) = await PutAsync("/mockserver/retrieve?type=active_expectations&format=json", json).ConfigureAwait(false);

        if (statusCode >= 400)
            throw new MockServerClientException($"Failed to retrieve active expectations (HTTP {statusCode}): {body}");

        if (!string.IsNullOrEmpty(body))
        {
            var result = JsonSerializer.Deserialize<List<Expectation>>(body, JsonOptions);
            if (result != null) return result;
        }
        return new List<Expectation>();
    }

    /// <summary>
    /// Retrieve recorded expectations matching an optional filter.
    /// </summary>
    public List<Expectation> RetrieveRecordedExpectations(HttpRequest? filter = null)
        => RetrieveRecordedExpectationsAsync(filter).GetAwaiter().GetResult();

    /// <summary>
    /// Retrieve recorded expectations matching an optional filter (async).
    /// </summary>
    public async Task<List<Expectation>> RetrieveRecordedExpectationsAsync(HttpRequest? filter = null)
    {
        var json = filter != null ? JsonSerializer.Serialize(filter, JsonOptions) : "";
        var (statusCode, body) = await PutAsync("/mockserver/retrieve?type=recorded_expectations&format=json", json).ConfigureAwait(false);

        if (statusCode >= 400)
            throw new MockServerClientException($"Failed to retrieve recorded expectations (HTTP {statusCode}): {body}");

        if (!string.IsNullOrEmpty(body))
        {
            var result = JsonSerializer.Deserialize<List<Expectation>>(body, JsonOptions);
            if (result != null) return result;
        }
        return new List<Expectation>();
    }

    /// <summary>
    /// Retrieve one page of the recorded expectations matching the optional request matcher, in the order the
    /// requests were received.
    /// </summary>
    /// <exception cref="ArgumentOutOfRangeException">offset or limit is negative; nothing is sent.</exception>
    public List<Expectation> RetrieveRecordedExpectations(HttpRequest? request, int offset, int limit)
        => RetrieveRecordedExpectationsAsync(request, offset, limit).GetAwaiter().GetResult();

    /// <summary>Retrieve one page of the recorded expectations (async).</summary>
    public Task<List<Expectation>> RetrieveRecordedExpectationsAsync(HttpRequest? request, int offset, int limit)
        => RetrieveExpectationsWithFilterAsync("RECORDED_EXPECTATIONS", request,
            ExpectationFilter.Filter().WithOffset(offset).WithLimit(limit), "recorded expectations");

    /// <summary>
    /// Retrieve the recorded expectations matching the optional request matcher, shaped by the options, for example
    /// replaying a request recorded with different responses in order, keeping only some request headers, or moving
    /// large bodies into files.
    /// </summary>
    /// <param name="request">Optional request matcher; null for all recorded expectations.</param>
    /// <param name="options">The options to apply; null for none.</param>
    public List<Expectation> RetrieveRecordedExpectations(HttpRequest? request, RecordedExpectationsOptions? options)
        => RetrieveRecordedExpectationsAsync(request, options).GetAwaiter().GetResult();

    /// <summary>Retrieve the recorded expectations shaped by the options (async).</summary>
    public Task<List<Expectation>> RetrieveRecordedExpectationsAsync(HttpRequest? request, RecordedExpectationsOptions? options)
        => RetrieveExpectationsWithQueryAsync("RECORDED_EXPECTATIONS", request, options?.ToQueryString() ?? "",
            "recorded expectations");

    /// <summary>
    /// Retrieve the active expectations matching both the optional request matcher and the filter, for example by
    /// metadata, because they have never matched a request, or one page of them (offset and limit). Pages are
    /// taken in match order (highest priority first, then oldest first).
    /// </summary>
    /// <param name="request">Optional request matcher; null for all expectations.</param>
    /// <param name="filter">The metadata, unmatched and paging options; null for no filter.</param>
    public List<Expectation> RetrieveActiveExpectations(HttpRequest? request, ExpectationFilter? filter)
        => RetrieveActiveExpectationsAsync(request, filter).GetAwaiter().GetResult();

    /// <summary>Retrieve the active expectations matching the request matcher and the filter (async).</summary>
    public Task<List<Expectation>> RetrieveActiveExpectationsAsync(HttpRequest? request, ExpectationFilter? filter)
        => RetrieveExpectationsWithFilterAsync("ACTIVE_EXPECTATIONS", request, filter, "active expectations");

    /// <summary>
    /// Retrieve the active expectations whose metadata has <paramref name="key"/> with a value equal to
    /// <paramref name="value"/> (a string is compared as text, any other value as JSON).
    /// </summary>
    public List<Expectation> RetrieveActiveExpectationsByMetadata(string key, object value)
        => RetrieveActiveExpectationsByMetadataAsync(key, value).GetAwaiter().GetResult();

    /// <summary>Retrieve the active expectations by metadata key and value (async).</summary>
    public Task<List<Expectation>> RetrieveActiveExpectationsByMetadataAsync(string key, object value)
        => RetrieveActiveExpectationsAsync(null, ExpectationFilter.Filter().WithMetadata(key, value));

    /// <summary>Retrieve the active expectations whose metadata has <paramref name="key"/>, whatever its value.</summary>
    public List<Expectation> RetrieveActiveExpectationsByMetadataKey(string key)
        => RetrieveActiveExpectationsByMetadataKeyAsync(key).GetAwaiter().GetResult();

    /// <summary>Retrieve the active expectations by metadata key (async).</summary>
    public Task<List<Expectation>> RetrieveActiveExpectationsByMetadataKeyAsync(string key)
        => RetrieveActiveExpectationsAsync(null, ExpectationFilter.Filter().WithMetadataKey(key));

    /// <summary>
    /// Retrieve the active expectations that have never matched a request, for example to find mocks a test
    /// suite no longer uses. The count is per MockServer instance and resets when an expectation is changed.
    /// </summary>
    public List<Expectation> RetrieveUnmatchedExpectations()
        => RetrieveUnmatchedExpectationsAsync().GetAwaiter().GetResult();

    /// <summary>Retrieve the active expectations that have never matched a request (async).</summary>
    public Task<List<Expectation>> RetrieveUnmatchedExpectationsAsync()
        => RetrieveActiveExpectationsAsync(null, ExpectationFilter.Filter().WithUnmatched(true));

    private Task<List<Expectation>> RetrieveExpectationsWithFilterAsync(
        string type, HttpRequest? request, ExpectationFilter? filter, string description)
        => RetrieveExpectationsWithQueryAsync(type, request, filter?.ToQueryString() ?? "", description);

    private async Task<List<Expectation>> RetrieveExpectationsWithQueryAsync(
        string type, HttpRequest? request, string query, string description)
    {
        var json = request != null ? JsonSerializer.Serialize(request, JsonOptions) : "";
        var path = $"/mockserver/retrieve?type={type}&format=JSON" + query;
        var (statusCode, body) = await PutAsync(path, json).ConfigureAwait(false);

        if (statusCode >= 400)
            throw new MockServerClientException($"Failed to retrieve {description} (HTTP {statusCode}): {body}");

        if (!string.IsNullOrEmpty(body))
        {
            var result = JsonSerializer.Deserialize<List<Expectation>>(body, JsonOptions);
            if (result != null) return result;
        }
        return new List<Expectation>();
    }

    /// <summary>
    /// Retrieve filtered by expectation ID rather than by a request matcher (async).
    /// For type "active_expectations" only the expectation with that ID is returned; for
    /// every other type the entries matching that expectation's request are returned,
    /// exactly as verify by expectation ID matches them. An unknown ID is rejected with a
    /// 400.
    /// </summary>
    private async Task<string> RetrieveByIdAsync(string expectationId, string typeAndFormat)
    {
        var json = JsonSerializer.Serialize(new { id = expectationId }, JsonOptions);
        var (statusCode, body) = await PutAsync("/mockserver/retrieve?" + typeAndFormat, json).ConfigureAwait(false);

        if (statusCode >= 400)
            throw new MockServerClientException($"Failed to retrieve by id (HTTP {statusCode}): {body}");

        return body ?? "";
    }

    /// <summary>
    /// Retrieve the recorded requests matching the request of the expectation with the given ID.
    /// </summary>
    public List<HttpRequest> RetrieveRecordedRequestsById(string expectationId)
        => RetrieveRecordedRequestsByIdAsync(expectationId).GetAwaiter().GetResult();

    /// <summary>
    /// Retrieve the recorded requests matching the request of the expectation with the given ID (async).
    /// </summary>
    public async Task<List<HttpRequest>> RetrieveRecordedRequestsByIdAsync(string expectationId)
    {
        var body = await RetrieveByIdAsync(expectationId, "type=requests&format=json").ConfigureAwait(false);
        if (!string.IsNullOrEmpty(body))
        {
            var result = JsonSerializer.Deserialize<List<HttpRequest>>(body, JsonOptions);
            if (result != null) return result;
        }
        return new List<HttpRequest>();
    }

    /// <summary>
    /// Retrieve the active expectation with the given ID. Unlike
    /// <see cref="RetrieveActiveExpectations"/> this returns only the expectation with that
    /// ID, never other expectations whose matcher happens to match the same request.
    /// </summary>
    public List<Expectation> RetrieveActiveExpectationsById(string expectationId)
        => RetrieveActiveExpectationsByIdAsync(expectationId).GetAwaiter().GetResult();

    /// <summary>
    /// Retrieve the active expectation with the given ID (async).
    /// </summary>
    public async Task<List<Expectation>> RetrieveActiveExpectationsByIdAsync(string expectationId)
    {
        var body = await RetrieveByIdAsync(expectationId, "type=active_expectations&format=json").ConfigureAwait(false);
        if (!string.IsNullOrEmpty(body))
        {
            var result = JsonSerializer.Deserialize<List<Expectation>>(body, JsonOptions);
            if (result != null) return result;
        }
        return new List<Expectation>();
    }

    /// <summary>
    /// Retrieve the recorded expectations matching the request of the expectation with the given ID.
    /// </summary>
    public List<Expectation> RetrieveRecordedExpectationsById(string expectationId)
        => RetrieveRecordedExpectationsByIdAsync(expectationId).GetAwaiter().GetResult();

    /// <summary>
    /// Retrieve the recorded expectations matching the request of the expectation with the given ID (async).
    /// </summary>
    public async Task<List<Expectation>> RetrieveRecordedExpectationsByIdAsync(string expectationId)
    {
        var body = await RetrieveByIdAsync(expectationId, "type=recorded_expectations&format=json").ConfigureAwait(false);
        if (!string.IsNullOrEmpty(body))
        {
            var result = JsonSerializer.Deserialize<List<Expectation>>(body, JsonOptions);
            if (result != null) return result;
        }
        return new List<Expectation>();
    }

    /// <summary>
    /// Retrieve the log messages matching the request of the expectation with the given ID.
    /// </summary>
    public List<string> RetrieveLogMessagesById(string expectationId)
        => RetrieveLogMessagesByIdAsync(expectationId).GetAwaiter().GetResult();

    /// <summary>
    /// Retrieve the log messages matching the request of the expectation with the given ID (async).
    /// </summary>
    public async Task<List<string>> RetrieveLogMessagesByIdAsync(string expectationId)
    {
        var body = await RetrieveByIdAsync(expectationId, "type=logs").ConfigureAwait(false);
        if (!string.IsNullOrEmpty(body))
        {
            try
            {
                var result = JsonSerializer.Deserialize<List<string>>(body, JsonOptions);
                if (result != null) return result;
            }
            catch (JsonException)
            {
                // Log messages may come as separator-delimited text
                return new List<string>(body.Split(new[] { "------------------------------------\n" }, StringSplitOptions.RemoveEmptyEntries));
            }
        }
        return new List<string>();
    }

    /// <summary>
    /// Retrieve the active expectations as MockServer SDK setup code (the builder
    /// code that recreates the expectations) in the requested language.
    /// </summary>
    /// <param name="format">the code-generation language, one of "java", "javascript",
    /// "python", "go", "csharp", "ruby", "rust" or "php" (case-insensitive)</param>
    /// <param name="filter">an optional request filter</param>
    public string RetrieveExpectationsAsCode(string format = "java", HttpRequest? filter = null)
        => RetrieveExpectationsAsCodeAsync(format, filter).GetAwaiter().GetResult();

    /// <summary>
    /// Retrieve the active expectations as MockServer SDK setup code in the
    /// requested language (async).
    /// </summary>
    public async Task<string> RetrieveExpectationsAsCodeAsync(string format = "java", HttpRequest? filter = null)
    {
        var json = filter != null ? JsonSerializer.Serialize(filter, JsonOptions) : "";
        var (statusCode, body) = await PutAsync(
            "/mockserver/retrieve?type=active_expectations&format=" + Uri.EscapeDataString(format.ToUpperInvariant()),
            json).ConfigureAwait(false);

        if (statusCode >= 400)
            throw new MockServerClientException($"Failed to retrieve expectations as code (HTTP {statusCode}): {body}");

        return body ?? "";
    }

    /// <summary>
    /// Retrieve the recorded (proxied) request/response pairs as MockServer SDK
    /// setup code (the builder code that recreates the expectations) in the
    /// requested language.
    /// </summary>
    /// <param name="format">the code-generation language, one of "java", "javascript",
    /// "python", "go", "csharp", "ruby", "rust" or "php" (case-insensitive)</param>
    /// <param name="filter">an optional request filter</param>
    public string RetrieveRecordedExpectationsAsCode(string format = "java", HttpRequest? filter = null)
        => RetrieveRecordedExpectationsAsCodeAsync(format, filter).GetAwaiter().GetResult();

    /// <summary>
    /// Retrieve the recorded request/response pairs as MockServer SDK setup code
    /// in the requested language (async).
    /// </summary>
    public async Task<string> RetrieveRecordedExpectationsAsCodeAsync(string format = "java", HttpRequest? filter = null)
    {
        var json = filter != null ? JsonSerializer.Serialize(filter, JsonOptions) : "";
        var (statusCode, body) = await PutAsync(
            "/mockserver/retrieve?type=recorded_expectations&format=" + Uri.EscapeDataString(format.ToUpperInvariant()),
            json).ConfigureAwait(false);

        if (statusCode >= 400)
            throw new MockServerClientException($"Failed to retrieve recorded expectations as code (HTTP {statusCode}): {body}");

        return body ?? "";
    }

    /// <summary>
    /// Retrieve log messages matching an optional filter.
    /// </summary>
    public List<string> RetrieveLogMessages(HttpRequest? filter = null)
        => RetrieveLogMessagesAsync(filter).GetAwaiter().GetResult();

    /// <summary>
    /// Retrieve log messages matching an optional filter (async).
    /// </summary>
    public async Task<List<string>> RetrieveLogMessagesAsync(HttpRequest? filter = null)
    {
        var json = filter != null ? JsonSerializer.Serialize(filter, JsonOptions) : "";
        var (statusCode, body) = await PutAsync("/mockserver/retrieve?type=logs", json).ConfigureAwait(false);

        if (statusCode >= 400)
            throw new MockServerClientException($"Failed to retrieve log messages (HTTP {statusCode}): {body}");

        if (!string.IsNullOrEmpty(body))
        {
            try
            {
                var result = JsonSerializer.Deserialize<List<string>>(body, JsonOptions);
                if (result != null) return result;
            }
            catch (JsonException)
            {
                // Log messages may come as separator-delimited text
                return new List<string>(body.Split(new[] { "------------------------------------\n" }, StringSplitOptions.RemoveEmptyEntries));
            }
        }
        return new List<string>();
    }

    // -------------------------------------------------------------------
    // Match diagnostics
    // -------------------------------------------------------------------

    /// <summary>
    /// Explain how a recorded request would be served now (PUT /mockserver/explain): re-checks it against the
    /// current expectations, in the order MockServer evaluates them, and returns the expectation that would
    /// serve it, why each expectation ahead of that one was passed over, and which lower-priority expectations
    /// it shadows. Read-only: it consumes no Times and moves no scenario.
    /// </summary>
    /// <param name="correlationId">The correlation ID of the request's log entries.</param>
    /// <exception cref="ArgumentException">If <paramref name="correlationId"/> is null or blank.</exception>
    /// <exception cref="MockServerClientException">If no request is recorded with this correlation ID (never
    /// received, cleared or evicted from the event log), the recorded request is not an HTTP request, or the
    /// server fails.</exception>
    public RequestExplanation Explain(string correlationId)
        => ExplainAsync(correlationId).GetAwaiter().GetResult();

    /// <summary>
    /// Explain how a recorded request would be served now (async). See <see cref="Explain"/>.
    /// </summary>
    public async Task<RequestExplanation> ExplainAsync(string correlationId)
    {
        if (string.IsNullOrWhiteSpace(correlationId))
            throw new ArgumentException("correlationId is required", nameof(correlationId));

        var request = new HttpRequestMessage(HttpMethod.Put,
            _baseUrl + "/mockserver/explain?correlationId=" + Uri.EscapeDataString(correlationId));
        var (statusCode, body) = await SendAsync(request).ConfigureAwait(false);

        if (statusCode >= 400)
            throw new MockServerClientException(
                $"Failed to explain request '{correlationId}' (HTTP {statusCode}): {ErrorMessage(body)}");
        return DeserializeDiagnostic<RequestExplanation>(body, "explain");
    }

    /// <summary>
    /// Compare a request with every active expectation (PUT /mockserver/debugMismatch) and return, for each,
    /// whether it matches and which fields differ, plus the closest match. Nothing is recorded or consumed.
    /// </summary>
    /// <param name="request">The request to compare with the active expectations.</param>
    /// <exception cref="ArgumentNullException">If <paramref name="request"/> is null.</exception>
    /// <exception cref="MockServerClientException">If the server rejects the request or fails.</exception>
    public MismatchDebugResult DebugMismatch(HttpRequest request)
        => DebugMismatchAsync(request).GetAwaiter().GetResult();

    /// <summary>
    /// Compare a request with every active expectation (async). See <see cref="DebugMismatch"/>.
    /// </summary>
    public async Task<MismatchDebugResult> DebugMismatchAsync(HttpRequest request)
    {
        if (request == null) throw new ArgumentNullException(nameof(request));

        var json = JsonSerializer.Serialize(request, JsonOptions);
        var (statusCode, body) = await PutAsync("/mockserver/debugMismatch", json).ConfigureAwait(false);

        if (statusCode >= 400)
            throw new MockServerClientException($"Failed to debug mismatch (HTTP {statusCode}): {ErrorMessage(body)}");
        return DeserializeDiagnostic<MismatchDebugResult>(body, "debugMismatch");
    }

    /// <summary>
    /// Explain why recent requests matched no expectation (PUT /mockserver/explainUnmatched): for each recorded
    /// unmatched request, the evaluated expectations ranked closest first, with their differences and
    /// remediation hints.
    /// </summary>
    /// <param name="limit">How many recent unmatched requests to explain; the server default (10) when null.</param>
    /// <param name="suggestFixes">Also suggest, for each of the five closest non-matching expectations, a JSON
    /// patch that would make it match. Nothing is applied.</param>
    /// <exception cref="ArgumentOutOfRangeException">If <paramref name="limit"/> is less than 1.</exception>
    /// <exception cref="MockServerClientException">If the server fails.</exception>
    public UnmatchedExplanation ExplainUnmatched(int? limit = null, bool suggestFixes = false)
        => ExplainUnmatchedAsync(limit, suggestFixes).GetAwaiter().GetResult();

    /// <summary>
    /// Explain why recent requests matched no expectation (async). See <see cref="ExplainUnmatched"/>.
    /// </summary>
    public async Task<UnmatchedExplanation> ExplainUnmatchedAsync(int? limit = null, bool suggestFixes = false)
    {
        if (limit < 1)
            throw new ArgumentOutOfRangeException(nameof(limit), limit, "limit must be at least 1");

        var payload = new Dictionary<string, object>();
        if (limit.HasValue) payload["limit"] = limit.Value;
        if (suggestFixes) payload["suggestFixes"] = true;
        var json = JsonSerializer.Serialize(payload, JsonOptions);
        var (statusCode, body) = await PutAsync("/mockserver/explainUnmatched", json).ConfigureAwait(false);

        if (statusCode >= 400)
            throw new MockServerClientException($"Failed to explain unmatched requests (HTTP {statusCode}): {ErrorMessage(body)}");
        return DeserializeDiagnostic<UnmatchedExplanation>(body, "explainUnmatched");
    }

    private static T DeserializeDiagnostic<T>(string body, string action) where T : new()
    {
        if (string.IsNullOrWhiteSpace(body)) return new T();
        try
        {
            return JsonSerializer.Deserialize<T>(body, JsonOptions) ?? new T();
        }
        catch (JsonException e)
        {
            throw new MockServerClientException($"Unable to parse {action} response: {body}", e);
        }
    }

    /// <summary>The <c>error</c> field of a JSON error body, or the body itself.</summary>
    private static string ErrorMessage(string body)
    {
        try
        {
            using var doc = JsonDocument.Parse(body);
            if (doc.RootElement.ValueKind == JsonValueKind.Object
                && doc.RootElement.TryGetProperty("error", out var error)
                && error.ValueKind == JsonValueKind.String)
                return error.GetString() ?? body;
        }
        catch (JsonException)
        {
            // not JSON: report the body as sent
        }
        return body;
    }

    /// <summary>
    /// Check MockServer status (returns bound ports).
    /// </summary>
    public Ports Status() => StatusAsync().GetAwaiter().GetResult();

    /// <summary>
    /// Check MockServer status (async).
    /// </summary>
    public async Task<Ports> StatusAsync()
    {
        var (statusCode, body) = await PutAsync("/mockserver/status", "").ConfigureAwait(false);

        if (statusCode >= 400)
            throw new MockServerClientException($"Failed to get status (HTTP {statusCode}): {body}");

        if (!string.IsNullOrEmpty(body))
        {
            var result = JsonSerializer.Deserialize<Ports>(body, JsonOptions);
            if (result != null) return result;
        }
        return new Ports();
    }

    /// <summary>
    /// Bind additional ports to the MockServer.
    /// </summary>
    public Ports Bind(params int[] ports) => BindAsync(ports).GetAwaiter().GetResult();

    /// <summary>
    /// Bind additional ports (async).
    /// </summary>
    public async Task<Ports> BindAsync(params int[] ports)
    {
        var payload = new Ports { PortList = new List<int>(ports) };
        var json = JsonSerializer.Serialize(payload, JsonOptions);
        var (statusCode, body) = await PutAsync("/mockserver/bind", json).ConfigureAwait(false);

        if (statusCode >= 400)
            throw new MockServerClientException($"Failed to bind ports (HTTP {statusCode}): {body}");

        if (!string.IsNullOrEmpty(body))
        {
            var result = JsonSerializer.Deserialize<Ports>(body, JsonOptions);
            if (result != null) return result;
        }
        return new Ports();
    }

    /// <summary>
    /// Check if MockServer has started (poll with retries).
    /// </summary>
    public bool HasStarted(int attempts = 10, TimeSpan? delay = null)
        => HasStartedAsync(attempts, delay).GetAwaiter().GetResult();

    /// <summary>
    /// Check if MockServer has started (async, with retries).
    /// </summary>
    public async Task<bool> HasStartedAsync(int attempts = 10, TimeSpan? delay = null)
    {
        var wait = delay ?? TimeSpan.FromMilliseconds(500);
        for (int i = 0; i < attempts; i++)
        {
            try
            {
                var (statusCode, _) = await PutAsync("/mockserver/status", "").ConfigureAwait(false);
                if (statusCode == 200) return true;
            }
            catch (HttpRequestException)
            {
                // Server not yet available
            }
            catch (TaskCanceledException)
            {
                // Timeout
            }

            if (i < attempts - 1)
                await Task.Delay(wait).ConfigureAwait(false);
        }
        return false;
    }

    // -------------------------------------------------------------------
    // Breakpoints
    // -------------------------------------------------------------------

    private BreakpointWebSocketClient? _breakpointWs;
    private readonly SemaphoreSlim _breakpointWsLock = new(1, 1);

    /// <summary>
    /// Register a breakpoint matcher with the given phases and handlers.
    /// Returns the server-assigned breakpoint id.
    /// </summary>
    public string AddBreakpoint(
        HttpRequest matcher,
        IEnumerable<string> phases,
        BreakpointRequestHandler? requestHandler = null,
        BreakpointResponseHandler? responseHandler = null,
        BreakpointStreamFrameHandler? streamFrameHandler = null)
        => AddBreakpointAsync(matcher, phases, requestHandler, responseHandler, streamFrameHandler)
            .GetAwaiter().GetResult();

    /// <summary>
    /// Register a breakpoint matcher (async).
    /// </summary>
    public async Task<string> AddBreakpointAsync(
        HttpRequest matcher,
        IEnumerable<string> phases,
        BreakpointRequestHandler? requestHandler = null,
        BreakpointResponseHandler? responseHandler = null,
        BreakpointStreamFrameHandler? streamFrameHandler = null)
    {
        if (matcher == null) throw new ArgumentNullException(nameof(matcher));
        var phaseList = phases?.ToList() ?? throw new ArgumentNullException(nameof(phases));
        if (phaseList.Count == 0) throw new ArgumentException("At least one phase is required", nameof(phases));

        await EnsureBreakpointWsAsync().ConfigureAwait(false);

        var reg = new BreakpointMatcherRegistration
        {
            HttpRequest = matcher,
            Phases = phaseList,
            ClientId = _breakpointWs!.ClientId
        };

        var json = JsonSerializer.Serialize(reg, JsonOptions);
        var (statusCode, body) = await PutAsync("/mockserver/breakpoint/matcher", json).ConfigureAwait(false);

        if (statusCode >= 400)
            throw new Exceptions.MockServerClientException($"Failed to register breakpoint (HTTP {statusCode}): {body}");

        var result = JsonSerializer.Deserialize<BreakpointMatcherResponse>(body, JsonOptions);
        var id = result?.Id ?? throw new Exceptions.MockServerClientException("No breakpoint id in response");

        if (requestHandler != null) _breakpointWs.SetRequestHandler(id, requestHandler);
        if (responseHandler != null) _breakpointWs.SetResponseHandler(id, responseHandler);
        if (streamFrameHandler != null) _breakpointWs.SetStreamFrameHandler(id, streamFrameHandler);

        return id;
    }

    /// <summary>
    /// Convenience: register a REQUEST-only breakpoint.
    /// </summary>
    public string AddRequestBreakpoint(HttpRequest matcher, BreakpointRequestHandler handler)
        => AddBreakpoint(matcher, new[] { BreakpointPhase.Request }, requestHandler: handler);

    /// <summary>
    /// Convenience: register a REQUEST + RESPONSE breakpoint.
    /// </summary>
    public string AddRequestResponseBreakpoint(
        HttpRequest matcher,
        BreakpointRequestHandler requestHandler,
        BreakpointResponseHandler responseHandler)
        => AddBreakpoint(matcher, new[] { BreakpointPhase.Request, BreakpointPhase.Response },
            requestHandler: requestHandler, responseHandler: responseHandler);

    /// <summary>
    /// Convenience: register a streaming-phase breakpoint.
    /// </summary>
    public string AddStreamBreakpoint(HttpRequest matcher, IEnumerable<string> phases, BreakpointStreamFrameHandler handler)
        => AddBreakpoint(matcher, phases, streamFrameHandler: handler);

    /// <summary>
    /// List all registered breakpoint matchers.
    /// </summary>
    public BreakpointMatcherList ListBreakpointMatchers()
        => ListBreakpointMatchersAsync().GetAwaiter().GetResult();

    /// <summary>
    /// List all registered breakpoint matchers (async).
    /// </summary>
    public async Task<BreakpointMatcherList> ListBreakpointMatchersAsync()
    {
        var (statusCode, body) = await GetAsync("/mockserver/breakpoint/matchers").ConfigureAwait(false);
        if (statusCode >= 400)
            throw new Exceptions.MockServerClientException($"Failed to list breakpoint matchers (HTTP {statusCode}): {body}");

        return JsonSerializer.Deserialize<BreakpointMatcherList>(body, JsonOptions) ?? new BreakpointMatcherList();
    }

    /// <summary>
    /// Remove a breakpoint matcher by id.
    /// </summary>
    public void RemoveBreakpointMatcher(string id) => RemoveBreakpointMatcherAsync(id).GetAwaiter().GetResult();

    /// <summary>
    /// Remove a breakpoint matcher by id (async).
    /// </summary>
    public async Task RemoveBreakpointMatcherAsync(string id)
    {
        var json = JsonSerializer.Serialize(new { id }, JsonOptions);
        var (statusCode, body) = await PutAsync("/mockserver/breakpoint/matcher/remove", json).ConfigureAwait(false);

        if (statusCode == 404)
            throw new Exceptions.MockServerClientException($"Breakpoint matcher not found: {id}");
        if (statusCode >= 400)
            throw new Exceptions.MockServerClientException($"Failed to remove breakpoint matcher (HTTP {statusCode}): {body}");

        _breakpointWs?.RemoveHandlers(id);
    }

    /// <summary>
    /// Remove all registered breakpoint matchers.
    /// </summary>
    public void ClearBreakpointMatchers() => ClearBreakpointMatchersAsync().GetAwaiter().GetResult();

    /// <summary>
    /// Remove all registered breakpoint matchers (async).
    /// </summary>
    public async Task ClearBreakpointMatchersAsync()
    {
        var (statusCode, body) = await PutAsync("/mockserver/breakpoint/matcher/clear", "").ConfigureAwait(false);
        if (statusCode >= 400)
            throw new Exceptions.MockServerClientException($"Failed to clear breakpoint matchers (HTTP {statusCode}): {body}");

        _breakpointWs?.ClearHandlers();
    }

    internal async Task EnsureBreakpointWsAsync()
    {
        if (_breakpointWs != null && !_breakpointWs.IsDead) return;
        await _breakpointWsLock.WaitAsync().ConfigureAwait(false);
        try
        {
            if (_breakpointWs != null && !_breakpointWs.IsDead) return;
            // Dispose old dead connection if present
            _breakpointWs?.Dispose();
            var ws = new BreakpointWebSocketClient();
            await ws.ConnectAsync(_baseUrl, _controlPlaneBearerTokenSupplier, _tlsOptions, _controlPlaneBasicAuthorization).ConfigureAwait(false);
            _breakpointWs = ws;
        }
        finally
        {
            _breakpointWsLock.Release();
        }
    }

    // -------------------------------------------------------------------
    // Object (closure) callbacks
    //
    // Reuses the single callback WebSocket shared with breakpoints (one socket
    // per client). On a match the server dispatches the request over the WS; the
    // registered closure produces the response (or request to forward).
    // -------------------------------------------------------------------

    /// <summary>
    /// Register a request matcher whose response is produced by a closure (object callback).
    /// Opens (or reuses) the shared callback WebSocket, registers <paramref name="handler"/>,
    /// then creates an expectation with an <c>httpResponseObjectCallback</c> bound to this
    /// client's WebSocket id. On each match the server sends the request over the WebSocket
    /// and replies with the closure's response.
    /// </summary>
    /// <param name="request">The request matcher.</param>
    /// <param name="handler">A closure mapping the matched request to a response.</param>
    /// <param name="times">Optional number of times the expectation should match.</param>
    /// <param name="timeToLive">Optional time-to-live for the expectation.</param>
    /// <returns>The created expectation(s).</returns>
    public List<Expectation> MockWithCallback(
        HttpRequest request, Func<HttpRequest, HttpResponse> handler, Times? times = null, TimeToLive? timeToLive = null)
        => MockWithCallbackAsync(request, handler, times, timeToLive).GetAwaiter().GetResult();

    /// <summary>
    /// Register a request matcher whose response is produced by a closure (async).
    /// See <see cref="MockWithCallback(HttpRequest, Func{HttpRequest, HttpResponse}, Times, TimeToLive)"/>.
    /// </summary>
    public async Task<List<Expectation>> MockWithCallbackAsync(
        HttpRequest request, Func<HttpRequest, HttpResponse> handler, Times? times = null, TimeToLive? timeToLive = null)
    {
        if (request == null) throw new ArgumentNullException(nameof(request));
        if (handler == null) throw new ArgumentNullException(nameof(handler));

        await EnsureBreakpointWsAsync().ConfigureAwait(false);
        var clientId = _breakpointWs!.ClientId
            ?? throw new MockServerClientException("Callback WebSocket has no clientId");

        _breakpointWs.SetObjectResponseHandler(requestNode =>
        {
            var req = DeserializeNode<HttpRequest>(requestNode) ?? new HttpRequest();
            var resp = handler(req) ?? new HttpResponse { StatusCode = 200 };
            return SerializeToNode(resp);
        });

        var expectation = new Expectation
        {
            HttpRequest = request,
            HttpResponseObjectCallback = new HttpObjectCallback { ClientId = clientId },
            Times = times,
            TimeToLive = timeToLive
        };
        return await UpsertAsync(expectation).ConfigureAwait(false);
    }

    /// <summary>
    /// Register a request matcher whose request-to-forward is produced by a closure
    /// (forward object callback). Mirrors <see cref="MockWithCallback(HttpRequest, Func{HttpRequest, HttpResponse}, Times, TimeToLive)"/>
    /// but the closure returns the (possibly modified) <see cref="HttpRequest"/> to forward.
    /// </summary>
    public List<Expectation> MockWithForwardCallback(
        HttpRequest request, Func<HttpRequest, HttpRequest> handler, Times? times = null, TimeToLive? timeToLive = null)
        => MockWithForwardCallbackAsync(request, handler, times, timeToLive).GetAwaiter().GetResult();

    /// <summary>
    /// Register a forward object callback (async). See <see cref="MockWithForwardCallback"/>.
    /// </summary>
    public async Task<List<Expectation>> MockWithForwardCallbackAsync(
        HttpRequest request, Func<HttpRequest, HttpRequest> handler, Times? times = null, TimeToLive? timeToLive = null)
    {
        if (request == null) throw new ArgumentNullException(nameof(request));
        if (handler == null) throw new ArgumentNullException(nameof(handler));

        await EnsureBreakpointWsAsync().ConfigureAwait(false);
        var clientId = _breakpointWs!.ClientId
            ?? throw new MockServerClientException("Callback WebSocket has no clientId");

        _breakpointWs.SetObjectForwardHandler(requestNode =>
        {
            var req = DeserializeNode<HttpRequest>(requestNode) ?? new HttpRequest();
            var forward = handler(req) ?? req;
            return SerializeToNode(forward);
        });

        var expectation = new Expectation
        {
            HttpRequest = request,
            HttpForwardObjectCallback = new HttpObjectCallback { ClientId = clientId },
            Times = times,
            TimeToLive = timeToLive
        };
        return await UpsertAsync(expectation).ConfigureAwait(false);
    }

    private static System.Text.Json.Nodes.JsonObject SerializeToNode<T>(T value)
    {
        var json = JsonSerializer.Serialize(value, JsonOptions);
        return System.Text.Json.Nodes.JsonNode.Parse(json)!.AsObject();
    }

    private static T? DeserializeNode<T>(System.Text.Json.Nodes.JsonObject node)
        => JsonSerializer.Deserialize<T>(node.ToJsonString(), JsonOptions);

    // -------------------------------------------------------------------
    // gRPC descriptor management
    // -------------------------------------------------------------------

    /// <summary>
    /// Upload a compiled protobuf descriptor set so gRPC requests can be matched.
    /// </summary>
    /// <param name="descriptor">
    /// The raw bytes of a <c>FileDescriptorSet</c> (e.g. the output of
    /// <c>protoc --descriptor_set_out=... --include_imports</c>). The bytes are
    /// sent verbatim as <c>application/octet-stream</c> — not base64-encoded.
    /// </param>
    public void UploadGrpcDescriptor(byte[] descriptor)
        => UploadGrpcDescriptorAsync(descriptor).GetAwaiter().GetResult();

    /// <summary>
    /// Upload a compiled protobuf descriptor set (async). See <see cref="UploadGrpcDescriptor"/>.
    /// </summary>
    public async Task UploadGrpcDescriptorAsync(byte[] descriptor)
    {
        if (descriptor == null) throw new ArgumentNullException(nameof(descriptor));
        if (descriptor.Length == 0)
            throw new ArgumentException("Descriptor set bytes must not be empty", nameof(descriptor));

        var (statusCode, body) = await PutBytesAsync("/mockserver/grpc/descriptors", descriptor).ConfigureAwait(false);

        if (statusCode >= 400)
            throw new MockServerClientException($"Failed to upload gRPC descriptor (HTTP {statusCode}): {body}");
    }

    /// <summary>
    /// Retrieve the gRPC services registered from uploaded descriptor sets.
    /// </summary>
    public GrpcService[] RetrieveGrpcServices()
        => RetrieveGrpcServicesAsync().GetAwaiter().GetResult();

    /// <summary>
    /// Retrieve the gRPC services registered from uploaded descriptor sets (async).
    /// </summary>
    public async Task<GrpcService[]> RetrieveGrpcServicesAsync()
    {
        var (statusCode, body) = await PutAsync("/mockserver/grpc/services", "").ConfigureAwait(false);

        if (statusCode >= 400)
            throw new MockServerClientException($"Failed to retrieve gRPC services (HTTP {statusCode}): {body}");

        if (!string.IsNullOrEmpty(body))
        {
            var result = JsonSerializer.Deserialize<GrpcService[]>(body, JsonOptions);
            if (result != null) return result;
        }
        return Array.Empty<GrpcService>();
    }

    /// <summary>
    /// Clear all uploaded gRPC descriptor sets and registered services.
    /// </summary>
    public void ClearGrpcDescriptors()
        => ClearGrpcDescriptorsAsync().GetAwaiter().GetResult();

    /// <summary>
    /// Clear all uploaded gRPC descriptor sets and registered services (async).
    /// </summary>
    public async Task ClearGrpcDescriptorsAsync()
    {
        var (statusCode, body) = await PutAsync("/mockserver/grpc/clear", "").ConfigureAwait(false);

        if (statusCode >= 400)
            throw new MockServerClientException($"Failed to clear gRPC descriptors (HTTP {statusCode}): {body}");
    }

    // -------------------------------------------------------------------
    // SRE control plane: load-scenario registry
    //
    // Scenarios are registered (loaded) under a unique name without running,
    // then started and stopped by name. Registration is always allowed;
    // only starting requires the server's loadGenerationEnabled=true.
    // -------------------------------------------------------------------

    /// <summary>
    /// Register (load) a load scenario under its unique name (PUT /mockserver/loadScenario).
    /// Registration does not start the scenario and is allowed even when load generation is
    /// disabled; start it later with <see cref="StartLoadScenarios"/>.
    /// </summary>
    /// <exception cref="MockServerClientException">If the scenario is invalid (HTTP 400).</exception>
    public LoadScenarioRef LoadScenario(LoadScenario scenario)
        => LoadScenarioAsync(scenario).GetAwaiter().GetResult();

    /// <summary>
    /// Register (load) a load scenario (async). See <see cref="LoadScenario(MockServer.Client.Models.LoadScenario)"/>.
    /// </summary>
    public async Task<LoadScenarioRef> LoadScenarioAsync(LoadScenario scenario)
    {
        if (scenario == null) throw new ArgumentNullException(nameof(scenario));
        var json = JsonSerializer.Serialize(scenario, JsonOptions);
        var (statusCode, body) = await PutAsync("/mockserver/loadScenario", json).ConfigureAwait(false);

        if (statusCode == 400)
            throw new MockServerClientException($"Invalid load scenario: {body}");
        if (statusCode >= 400)
            throw new MockServerClientException($"Failed to register load scenario (HTTP {statusCode}): {body}");

        if (!string.IsNullOrEmpty(body))
        {
            var result = JsonSerializer.Deserialize<LoadScenarioRef>(body, JsonOptions);
            if (result != null) return result;
        }
        return new LoadScenarioRef { Name = scenario.Name };
    }

    /// <summary>
    /// List all registered load scenarios (GET /mockserver/loadScenario).
    /// </summary>
    public LoadScenarioList LoadScenarios()
        => LoadScenariosAsync().GetAwaiter().GetResult();

    /// <summary>
    /// List all registered load scenarios (async). See <see cref="LoadScenarios"/>.
    /// </summary>
    public async Task<LoadScenarioList> LoadScenariosAsync()
    {
        var (statusCode, body) = await GetAsync("/mockserver/loadScenario").ConfigureAwait(false);

        if (statusCode >= 400)
            throw new MockServerClientException($"Failed to list load scenarios (HTTP {statusCode}): {body}");

        if (!string.IsNullOrEmpty(body))
        {
            var result = JsonSerializer.Deserialize<LoadScenarioList>(body, JsonOptions);
            if (result != null) return result;
        }
        return new LoadScenarioList();
    }

    /// <summary>
    /// Fetch a single registered load scenario by name (GET /mockserver/loadScenario/{name}).
    /// </summary>
    /// <exception cref="MockServerClientException">If no scenario is registered under that name (HTTP 404).</exception>
    public LoadScenarioEntry GetLoadScenario(string name)
        => GetLoadScenarioAsync(name).GetAwaiter().GetResult();

    /// <summary>
    /// Fetch a single registered load scenario by name (async). See <see cref="GetLoadScenario"/>.
    /// </summary>
    public async Task<LoadScenarioEntry> GetLoadScenarioAsync(string name)
    {
        if (name == null) throw new ArgumentNullException(nameof(name));
        var (statusCode, body) = await GetAsync($"/mockserver/loadScenario/{Uri.EscapeDataString(name)}").ConfigureAwait(false);

        if (statusCode == 404)
            throw new MockServerClientException($"No load scenario registered with name '{name}' (HTTP 404): {body}");
        if (statusCode >= 400)
            throw new MockServerClientException($"Failed to get load scenario '{name}' (HTTP {statusCode}): {body}");

        if (!string.IsNullOrEmpty(body))
        {
            var result = JsonSerializer.Deserialize<LoadScenarioEntry>(body, JsonOptions);
            if (result != null) return result;
        }
        return new LoadScenarioEntry { Name = name };
    }

    /// <summary>
    /// Remove a single registered load scenario by name (DELETE /mockserver/loadScenario/{name}).
    /// </summary>
    public void DeleteLoadScenario(string name)
        => DeleteLoadScenarioAsync(name).GetAwaiter().GetResult();

    /// <summary>
    /// Remove a single registered load scenario by name (async). See <see cref="DeleteLoadScenario"/>.
    /// </summary>
    public async Task DeleteLoadScenarioAsync(string name)
    {
        if (name == null) throw new ArgumentNullException(nameof(name));
        var (statusCode, body) = await DeleteAsync($"/mockserver/loadScenario/{Uri.EscapeDataString(name)}").ConfigureAwait(false);

        if (statusCode >= 400)
            throw new MockServerClientException($"Failed to delete load scenario '{name}' (HTTP {statusCode}): {body}");
    }

    /// <summary>
    /// Clear all registered load scenarios (DELETE /mockserver/loadScenario). Idempotent.
    /// </summary>
    public void ClearLoadScenarios()
        => ClearLoadScenariosAsync().GetAwaiter().GetResult();

    /// <summary>
    /// Clear all registered load scenarios (async). See <see cref="ClearLoadScenarios"/>.
    /// </summary>
    public async Task ClearLoadScenariosAsync()
    {
        var (statusCode, body) = await DeleteAsync("/mockserver/loadScenario").ConfigureAwait(false);

        if (statusCode >= 400)
            throw new MockServerClientException($"Failed to clear load scenarios (HTTP {statusCode}): {body}");
    }

    /// <summary>
    /// Start one or more registered load scenarios by name (PUT /mockserver/loadScenario/start).
    /// Honours each scenario's startDelayMillis. Requires the server to be started with
    /// loadGenerationEnabled=true.
    /// </summary>
    /// <exception cref="MockServerClientException">
    /// If load generation is disabled (HTTP 403 — start MockServer with loadGenerationEnabled=true)
    /// or a named scenario is not registered (HTTP 404).
    /// </exception>
    public LoadScenarioStartResult StartLoadScenarios(params string[] names)
        => StartLoadScenariosAsync(names).GetAwaiter().GetResult();

    /// <summary>
    /// Start one or more registered load scenarios by name (async). See <see cref="StartLoadScenarios"/>.
    /// </summary>
    public async Task<LoadScenarioStartResult> StartLoadScenariosAsync(params string[] names)
    {
        if (names == null || names.Length == 0)
            throw new ArgumentException("At least one scenario name must be provided to start.", nameof(names));

        var json = JsonSerializer.Serialize(new Dictionary<string, object> { ["names"] = names }, JsonOptions);
        var (statusCode, body) = await PutAsync("/mockserver/loadScenario/start", json).ConfigureAwait(false);

        if (statusCode == 403)
            throw new MockServerClientException(
                "Failed to start load scenario: load generation is disabled " +
                $"(start MockServer with loadGenerationEnabled=true) (HTTP 403): {body}");
        if (statusCode == 404)
            throw new MockServerClientException($"Failed to start load scenario: unknown scenario name (HTTP 404): {body}");
        if (statusCode >= 400)
            throw new MockServerClientException($"Failed to start load scenario (HTTP {statusCode}): {body}");

        if (!string.IsNullOrEmpty(body))
        {
            var result = JsonSerializer.Deserialize<LoadScenarioStartResult>(body, JsonOptions);
            if (result != null) return result;
        }
        return new LoadScenarioStartResult();
    }

    /// <summary>
    /// Stop running load scenarios (PUT /mockserver/loadScenario/stop). Pass one or more names to
    /// stop those scenarios, or pass no names to stop every running scenario. Idempotent.
    /// </summary>
    public LoadScenarioStopResult StopLoadScenarios(params string[] names)
        => StopLoadScenariosAsync(names).GetAwaiter().GetResult();

    /// <summary>
    /// Stop running load scenarios (async). See <see cref="StopLoadScenarios"/>.
    /// </summary>
    public async Task<LoadScenarioStopResult> StopLoadScenariosAsync(params string[] names)
    {
        // No names => stop all (empty body); named => {"names":[...]}.
        var json = names == null || names.Length == 0
            ? ""
            : JsonSerializer.Serialize(new Dictionary<string, object> { ["names"] = names }, JsonOptions);
        var (statusCode, body) = await PutAsync("/mockserver/loadScenario/stop", json).ConfigureAwait(false);

        if (statusCode >= 400)
            throw new MockServerClientException($"Failed to stop load scenario (HTTP {statusCode}): {body}");

        if (!string.IsNullOrEmpty(body))
        {
            var result = JsonSerializer.Deserialize<LoadScenarioStopResult>(body, JsonOptions);
            if (result != null) return result;
        }
        return new LoadScenarioStopResult();
    }

    /// <summary>
    /// Convenience helper: register a load scenario and immediately start it. Equivalent to
    /// <see cref="LoadScenario(MockServer.Client.Models.LoadScenario)"/> followed by
    /// <see cref="StartLoadScenarios"/> for that scenario's name. Requires the server to be started
    /// with loadGenerationEnabled=true.
    /// </summary>
    public LoadScenarioStartResult RunLoadScenario(LoadScenario scenario)
        => RunLoadScenarioAsync(scenario).GetAwaiter().GetResult();

    /// <summary>
    /// Register a load scenario and immediately start it (async). See <see cref="RunLoadScenario"/>.
    /// </summary>
    public async Task<LoadScenarioStartResult> RunLoadScenarioAsync(LoadScenario scenario)
    {
        if (scenario == null) throw new ArgumentNullException(nameof(scenario));
        var registered = await LoadScenarioAsync(scenario).ConfigureAwait(false);
        var name = registered.Name ?? scenario.Name
            ?? throw new MockServerClientException("Cannot start load scenario: the scenario has no name.");
        return await StartLoadScenariosAsync(name).ConfigureAwait(false);
    }

    /// <summary>
    /// Fetch the end-of-run summary report for a load scenario run
    /// (GET /mockserver/loadScenario/{name}/report). Returns the raw report body: the JSON report by
    /// default, or a JUnit-XML &lt;testsuite&gt; document when <paramref name="format"/> is "junit".
    /// </summary>
    /// <param name="name">The load scenario name.</param>
    /// <param name="format">Optional report format; pass "junit" for the JUnit-XML rendering, otherwise the JSON report.</param>
    /// <exception cref="MockServerClientException">If the scenario never ran (HTTP 404).</exception>
    public string GetLoadScenarioReport(string name, string? format = null)
        => GetLoadScenarioReportAsync(name, format).GetAwaiter().GetResult();

    /// <summary>
    /// Fetch the end-of-run summary report for a load scenario run (async). See <see cref="GetLoadScenarioReport"/>.
    /// </summary>
    public async Task<string> GetLoadScenarioReportAsync(string name, string? format = null)
    {
        if (name == null) throw new ArgumentNullException(nameof(name));
        var path = $"/mockserver/loadScenario/{Uri.EscapeDataString(name)}/report";
        if (!string.IsNullOrEmpty(format))
            path += $"?format={Uri.EscapeDataString(format)}";
        var (statusCode, body) = await GetAsync(path).ConfigureAwait(false);

        if (statusCode == 404)
            throw new MockServerClientException($"No load scenario run for name '{name}' (HTTP 404): {body}");
        if (statusCode >= 400)
            throw new MockServerClientException($"Failed to get load scenario report '{name}' (HTTP {statusCode}): {body}");

        return body;
    }

    /// <summary>
    /// Seed an editable load scenario from an OpenAPI specification and load (register) it under
    /// <paramref name="name"/> in the LOADED state (PUT /mockserver/loadScenario/generateFromOpenAPI).
    /// Generates no traffic and is allowed even when load generation is disabled. The generated
    /// scenario is returned so it can be shown and edited before triggering a run.
    /// </summary>
    /// <param name="name">The generated scenario name (the unique registry key).</param>
    /// <param name="specUrlOrPayload">The OpenAPI spec as an inline JSON/YAML payload, a URL, or a file/classpath reference.</param>
    /// <param name="target">Optional explicit network target for every generated step (overrides the spec's servers[0]).</param>
    /// <param name="profile">Optional load profile; when omitted a conservative default is applied.</param>
    /// <exception cref="MockServerClientException">If the request is invalid (HTTP 400).</exception>
    public LoadScenarioGenerateResult GenerateLoadScenarioFromOpenAPI(
        string name, string specUrlOrPayload, LoadGenerateTarget? target = null, LoadProfile? profile = null)
        => GenerateLoadScenarioFromOpenAPIAsync(name, specUrlOrPayload, target, profile).GetAwaiter().GetResult();

    /// <summary>
    /// Seed a load scenario from an OpenAPI specification (async). See <see cref="GenerateLoadScenarioFromOpenAPI"/>.
    /// </summary>
    public async Task<LoadScenarioGenerateResult> GenerateLoadScenarioFromOpenAPIAsync(
        string name, string specUrlOrPayload, LoadGenerateTarget? target = null, LoadProfile? profile = null)
    {
        if (name == null) throw new ArgumentNullException(nameof(name));
        if (specUrlOrPayload == null) throw new ArgumentNullException(nameof(specUrlOrPayload));

        var json = JsonSerializer.Serialize(new GenerateFromOpenApiRequest
        {
            Name = name,
            SpecUrlOrPayload = specUrlOrPayload,
            Target = target,
            Profile = profile
        }, JsonOptions);
        var (statusCode, body) = await PutAsync("/mockserver/loadScenario/generateFromOpenAPI", json).ConfigureAwait(false);

        if (statusCode == 400)
            throw new MockServerClientException($"Invalid generateFromOpenAPI request: {body}");
        if (statusCode >= 400)
            throw new MockServerClientException($"Failed to generate load scenario from OpenAPI (HTTP {statusCode}): {body}");

        if (!string.IsNullOrEmpty(body))
        {
            var result = JsonSerializer.Deserialize<LoadScenarioGenerateResult>(body, JsonOptions);
            if (result != null) return result;
        }
        return new LoadScenarioGenerateResult { Name = name };
    }

    /// <summary>
    /// Seed an editable load scenario from previously recorded proxy traffic and load (register) it
    /// under <paramref name="name"/> in the LOADED state
    /// (PUT /mockserver/loadScenario/generateFromRecording). Generates no traffic and is allowed even
    /// when load generation is disabled. The generated scenario is returned so it can be shown and
    /// edited before triggering a run.
    /// </summary>
    /// <param name="name">The generated scenario name (the unique registry key).</param>
    /// <param name="mode">Optional generation mode: VERBATIM (default) or TEMPLATIZED.</param>
    /// <param name="requestFilter">Optional matcher selecting which recorded requests to include; null means all.</param>
    /// <param name="target">Optional explicit network target applied to every generated step.</param>
    /// <param name="maxSteps">Optional cap on the number of VERBATIM steps (keeps the first N recorded requests).</param>
    /// <param name="profile">Optional load profile; when omitted a conservative default is applied.</param>
    /// <exception cref="MockServerClientException">If the request is invalid or there is nothing to convert (HTTP 400).</exception>
    public LoadScenarioGenerateResult GenerateLoadScenarioFromRecording(
        string name, LoadRecordingMode? mode = null, HttpRequest? requestFilter = null,
        LoadGenerateTarget? target = null, int? maxSteps = null, LoadProfile? profile = null)
        => GenerateLoadScenarioFromRecordingAsync(name, mode, requestFilter, target, maxSteps, profile).GetAwaiter().GetResult();

    /// <summary>
    /// Seed a load scenario from recorded proxy traffic (async). See <see cref="GenerateLoadScenarioFromRecording"/>.
    /// </summary>
    public async Task<LoadScenarioGenerateResult> GenerateLoadScenarioFromRecordingAsync(
        string name, LoadRecordingMode? mode = null, HttpRequest? requestFilter = null,
        LoadGenerateTarget? target = null, int? maxSteps = null, LoadProfile? profile = null)
    {
        if (name == null) throw new ArgumentNullException(nameof(name));

        var json = JsonSerializer.Serialize(new GenerateFromRecordingRequest
        {
            Name = name,
            Mode = mode,
            RequestFilter = requestFilter,
            Target = target,
            MaxSteps = maxSteps,
            Profile = profile
        }, JsonOptions);
        var (statusCode, body) = await PutAsync("/mockserver/loadScenario/generateFromRecording", json).ConfigureAwait(false);

        if (statusCode == 400)
            throw new MockServerClientException($"Invalid generateFromRecording request: {body}");
        if (statusCode >= 400)
            throw new MockServerClientException($"Failed to generate load scenario from recording (HTTP {statusCode}): {body}");

        if (!string.IsNullOrEmpty(body))
        {
            var result = JsonSerializer.Deserialize<LoadScenarioGenerateResult>(body, JsonOptions);
            if (result != null) return result;
        }
        return new LoadScenarioGenerateResult { Name = name };
    }

    // -------------------------------------------------------------------
    // SRE control plane: service chaos
    // -------------------------------------------------------------------

    /// <summary>
    /// Register a service-scoped HTTP chaos profile for a downstream <paramref name="host"/>
    /// (PUT /mockserver/serviceChaos). The profile applies to matched forward expectations to
    /// that host that do not define their own chaos.
    /// </summary>
    /// <param name="host">Downstream host the chaos profile applies to.</param>
    /// <param name="profile">The HTTP chaos profile to register.</param>
    /// <param name="ttlMillis">Optional time-to-live after which the registration auto-reverts (dead-man's switch).</param>
    public void SetServiceChaos(string host, ServiceChaosProfile profile, long? ttlMillis = null)
        => SetServiceChaosAsync(host, profile, ttlMillis).GetAwaiter().GetResult();

    /// <summary>
    /// Register a service-scoped HTTP chaos profile (async). See <see cref="SetServiceChaos"/>.
    /// </summary>
    public async Task SetServiceChaosAsync(string host, ServiceChaosProfile profile, long? ttlMillis = null)
    {
        if (string.IsNullOrEmpty(host)) throw new ArgumentException("host is required", nameof(host));
        if (profile == null) throw new ArgumentNullException(nameof(profile));

        var payload = new Dictionary<string, object?> { ["host"] = host, ["chaos"] = profile };
        if (ttlMillis.HasValue) payload["ttlMillis"] = ttlMillis.Value;

        var json = JsonSerializer.Serialize(payload, JsonOptions);
        var (statusCode, body) = await PutAsync("/mockserver/serviceChaos", json).ConfigureAwait(false);

        if (statusCode == 400)
            throw new MockServerClientException($"Invalid service chaos profile: {body}");
        if (statusCode >= 400)
            throw new MockServerClientException($"Failed to set service chaos (HTTP {statusCode}): {body}");
    }

    /// <summary>
    /// Remove the service-scoped chaos profile registered for <paramref name="host"/>
    /// (PUT /mockserver/serviceChaos with remove:true).
    /// </summary>
    public void RemoveServiceChaos(string host) => RemoveServiceChaosAsync(host).GetAwaiter().GetResult();

    /// <summary>
    /// Remove the service-scoped chaos profile for a host (async).
    /// </summary>
    public async Task RemoveServiceChaosAsync(string host)
    {
        if (string.IsNullOrEmpty(host)) throw new ArgumentException("host is required", nameof(host));
        var json = JsonSerializer.Serialize(new { host, remove = true }, JsonOptions);
        var (statusCode, body) = await PutAsync("/mockserver/serviceChaos", json).ConfigureAwait(false);

        if (statusCode >= 400)
            throw new MockServerClientException($"Failed to remove service chaos (HTTP {statusCode}): {body}");
    }

    /// <summary>
    /// Clear all service-scoped chaos profiles (PUT /mockserver/serviceChaos with clear:true).
    /// </summary>
    public void ClearServiceChaos() => ClearServiceChaosAsync().GetAwaiter().GetResult();

    /// <summary>
    /// Clear all service-scoped chaos profiles (async).
    /// </summary>
    public async Task ClearServiceChaosAsync()
    {
        var json = JsonSerializer.Serialize(new { clear = true }, JsonOptions);
        var (statusCode, body) = await PutAsync("/mockserver/serviceChaos", json).ConfigureAwait(false);

        if (statusCode >= 400)
            throw new MockServerClientException($"Failed to clear service chaos (HTTP {statusCode}): {body}");
    }

    // -------------------------------------------------------------------
    // SRE control plane: SLO verdicts
    // -------------------------------------------------------------------

    /// <summary>
    /// Evaluate a set of service-level objectives over a window and return the verdict
    /// (PUT /mockserver/verifySLO). The HTTP status encodes the verdict: 200 = PASS or
    /// INCONCLUSIVE, 406 = FAIL.
    /// </summary>
    /// <returns>The <see cref="SloVerdict"/> for a PASS, INCONCLUSIVE or FAIL outcome.</returns>
    /// <exception cref="MockServerClientException">
    /// If the criteria are malformed or SLO tracking is disabled (HTTP 400 —
    /// start MockServer with sloTrackingEnabled=true).
    /// </exception>
    public SloVerdict VerifySlo(SloCriteria criteria)
        => VerifySloAsync(criteria).GetAwaiter().GetResult();

    /// <summary>
    /// Evaluate an SLO criteria and return the verdict (async). See <see cref="VerifySlo"/>.
    /// </summary>
    public async Task<SloVerdict> VerifySloAsync(SloCriteria criteria)
    {
        if (criteria == null) throw new ArgumentNullException(nameof(criteria));
        var json = JsonSerializer.Serialize(criteria, JsonOptions);
        var (statusCode, body) = await PutAsync("/mockserver/verifySLO", json).ConfigureAwait(false);

        // 200 = PASS or INCONCLUSIVE, 406 = FAIL — both carry an SloVerdict body.
        if (statusCode == 200 || statusCode == 406)
        {
            if (!string.IsNullOrEmpty(body))
            {
                var verdict = JsonSerializer.Deserialize<SloVerdict>(body, JsonOptions);
                if (verdict != null) return verdict;
            }
            return new SloVerdict();
        }

        // 403 is "SLO tracking is off"; 400 is "these criteria are malformed". They used to be the
        // same status, so a malformed criteria was misreported as a disabled feature and vice versa.
        if (statusCode == 403)
            throw new MockServerClientException(
                "Failed to verify SLO: SLO tracking is disabled " +
                $"(start MockServer with sloTrackingEnabled=true) (HTTP 403): {body}");
        if (statusCode == 400)
            throw new MockServerClientException(
                $"Failed to verify SLO: malformed criteria (HTTP 400): {body}");
        throw new MockServerClientException($"Failed to verify SLO (HTTP {statusCode}): {body}");
    }

    // -------------------------------------------------------------------
    // SRE control plane: preemption simulation
    // -------------------------------------------------------------------

    /// <summary>
    /// Cordon and drain the server (PUT /mockserver/preemption). A null
    /// <paramref name="request"/> uses server defaults (mode "both", drain from stopDrainMillis, no TTL).
    /// </summary>
    /// <returns>The resulting <see cref="PreemptionStatus"/>.</returns>
    public PreemptionStatus SetPreemption(PreemptionRequest? request = null)
        => SetPreemptionAsync(request).GetAwaiter().GetResult();

    /// <summary>
    /// Cordon and drain the server (async). See <see cref="SetPreemption"/>.
    /// </summary>
    public async Task<PreemptionStatus> SetPreemptionAsync(PreemptionRequest? request = null)
    {
        var json = request != null ? JsonSerializer.Serialize(request, JsonOptions) : "";
        var (statusCode, body) = await PutAsync("/mockserver/preemption", json).ConfigureAwait(false);

        if (statusCode == 400)
            throw new MockServerClientException($"Invalid preemption request: {body}");
        if (statusCode >= 400)
            throw new MockServerClientException($"Failed to set preemption (HTTP {statusCode}): {body}");

        return DeserializePreemptionStatus(body);
    }

    /// <summary>
    /// Retrieve the current preemption status (GET /mockserver/preemption).
    /// </summary>
    public PreemptionStatus PreemptionStatus()
        => PreemptionStatusAsync().GetAwaiter().GetResult();

    /// <summary>
    /// Retrieve the current preemption status (async).
    /// </summary>
    public async Task<PreemptionStatus> PreemptionStatusAsync()
    {
        var (statusCode, body) = await GetAsync("/mockserver/preemption").ConfigureAwait(false);

        if (statusCode >= 400)
            throw new MockServerClientException($"Failed to get preemption status (HTTP {statusCode}): {body}");

        return DeserializePreemptionStatus(body);
    }

    /// <summary>
    /// Uncordon the server, clearing any active preemption (DELETE /mockserver/preemption). Idempotent.
    /// </summary>
    public void ClearPreemption() => ClearPreemptionAsync().GetAwaiter().GetResult();

    /// <summary>
    /// Uncordon the server (async).
    /// </summary>
    public async Task ClearPreemptionAsync()
    {
        var (statusCode, body) = await DeleteAsync("/mockserver/preemption").ConfigureAwait(false);

        if (statusCode >= 400)
            throw new MockServerClientException($"Failed to clear preemption (HTTP {statusCode}): {body}");
    }

    private static PreemptionStatus DeserializePreemptionStatus(string body)
    {
        if (!string.IsNullOrEmpty(body))
        {
            var status = JsonSerializer.Deserialize<PreemptionStatus>(body, JsonOptions);
            if (status != null) return status;
        }
        return new PreemptionStatus();
    }

    // -------------------------------------------------------------------
    // SRE control plane: scheduled chaos experiment
    // -------------------------------------------------------------------

    /// <summary>
    /// Start a scheduled multi-stage chaos experiment (PUT /mockserver/chaosExperiment).
    /// Only one experiment may be active at a time; starting a new one stops the previous one.
    /// </summary>
    public void StartChaosExperiment(ChaosExperiment experiment)
        => StartChaosExperimentAsync(experiment).GetAwaiter().GetResult();

    /// <summary>
    /// Start a scheduled chaos experiment (async). See <see cref="StartChaosExperiment"/>.
    /// </summary>
    public async Task StartChaosExperimentAsync(ChaosExperiment experiment)
    {
        if (experiment == null) throw new ArgumentNullException(nameof(experiment));
        var json = JsonSerializer.Serialize(experiment, JsonOptions);
        var (statusCode, body) = await PutAsync("/mockserver/chaosExperiment", json).ConfigureAwait(false);

        if (statusCode == 400)
            throw new MockServerClientException($"Invalid chaos experiment: {body}");
        if (statusCode >= 400)
            throw new MockServerClientException($"Failed to start chaos experiment (HTTP {statusCode}): {body}");
    }

    // -------------------------------------------------------------------
    // Stateful scenarios
    // -------------------------------------------------------------------

    /// <summary>
    /// Begin interacting with a named scenario state-machine. The returned handle exposes
    /// <see cref="ScenarioHandle.State"/>, <see cref="ScenarioHandle.Set(string)"/>,
    /// <see cref="ScenarioHandle.Set(string, long, string)"/> and <see cref="ScenarioHandle.Trigger"/>.
    /// </summary>
    /// <param name="name">The scenario name.</param>
    public ScenarioHandle Scenario(string name)
    {
        if (string.IsNullOrEmpty(name)) throw new ArgumentException("name is required", nameof(name));
        return new ScenarioHandle(this, name);
    }

    /// <summary>
    /// List all known scenarios and their current states (GET /mockserver/scenario).
    /// </summary>
    public List<ScenarioState> Scenarios()
        => ScenariosAsync().GetAwaiter().GetResult();

    /// <summary>
    /// List all known scenarios and their current states (async).
    /// </summary>
    public async Task<List<ScenarioState>> ScenariosAsync()
    {
        var (statusCode, body) = await GetAsync("/mockserver/scenario").ConfigureAwait(false);

        if (statusCode >= 400)
            throw new MockServerClientException($"Failed to list scenarios (HTTP {statusCode}): {body}");

        if (!string.IsNullOrEmpty(body))
        {
            var result = JsonSerializer.Deserialize<ScenarioList>(body, JsonOptions);
            if (result?.Scenarios != null) return result.Scenarios;
        }
        return new List<ScenarioState>();
    }

    internal async Task<ScenarioState> ScenarioStateAsync(string name)
    {
        var (statusCode, body) = await GetAsync($"/mockserver/scenario/{Uri.EscapeDataString(name)}").ConfigureAwait(false);

        if (statusCode >= 400)
            throw new MockServerClientException($"Failed to get scenario '{name}' (HTTP {statusCode}): {body}");

        return DeserializeScenarioState(body);
    }

    internal async Task<ScenarioState> ScenarioSetAsync(string name, string state, long? transitionAfterMs, string? nextState)
    {
        var payload = new Dictionary<string, object?> { ["state"] = state };
        if (transitionAfterMs.HasValue) payload["transitionAfterMs"] = transitionAfterMs.Value;
        if (nextState != null) payload["nextState"] = nextState;

        var json = JsonSerializer.Serialize(payload, JsonOptions);
        var (statusCode, body) = await PutAsync($"/mockserver/scenario/{Uri.EscapeDataString(name)}", json).ConfigureAwait(false);

        if (statusCode == 400)
            throw new MockServerClientException($"Invalid scenario state: {body}");
        if (statusCode >= 400)
            throw new MockServerClientException($"Failed to set scenario '{name}' (HTTP {statusCode}): {body}");

        return DeserializeScenarioState(body);
    }

    internal async Task<ScenarioState> ScenarioTriggerAsync(string name, string newState)
    {
        var json = JsonSerializer.Serialize(new { newState }, JsonOptions);
        var (statusCode, body) = await PutAsync($"/mockserver/scenario/{Uri.EscapeDataString(name)}/trigger", json).ConfigureAwait(false);

        if (statusCode == 400)
            throw new MockServerClientException($"Invalid scenario trigger: {body}");
        if (statusCode >= 400)
            throw new MockServerClientException($"Failed to trigger scenario '{name}' (HTTP {statusCode}): {body}");

        return DeserializeScenarioState(body);
    }

    private static ScenarioState DeserializeScenarioState(string body)
    {
        if (!string.IsNullOrEmpty(body))
        {
            var state = JsonSerializer.Deserialize<ScenarioState>(body, JsonOptions);
            if (state != null) return state;
        }
        return new ScenarioState();
    }

    // -------------------------------------------------------------------
    // Clock control (freeze / advance / reset / status)
    // -------------------------------------------------------------------

    /// <summary>
    /// Freeze the simulated clock (PUT /mockserver/clock, action "freeze"). When
    /// <paramref name="instant"/> is supplied it must be an ISO-8601 instant string
    /// (e.g. "2024-01-01T00:00:00Z"); when null the clock freezes at the current time.
    /// Returns this client for chaining.
    /// </summary>
    public MockServerClient FreezeClock(string? instant = null)
        => FreezeClockAsync(instant).GetAwaiter().GetResult();

    /// <summary>
    /// Freeze the simulated clock (async). See <see cref="FreezeClock"/>.
    /// </summary>
    public async Task<MockServerClient> FreezeClockAsync(string? instant = null)
    {
        var payload = new Dictionary<string, object?> { ["action"] = "freeze" };
        if (!string.IsNullOrEmpty(instant)) payload["instant"] = instant;
        var json = JsonSerializer.Serialize(payload, JsonOptions);
        var (statusCode, body) = await PutAsync("/mockserver/clock", json).ConfigureAwait(false);

        if (statusCode >= 400)
            throw new MockServerClientException($"Failed to freeze clock (HTTP {statusCode}): {body}");
        return this;
    }

    /// <summary>
    /// Advance the frozen simulated clock by <paramref name="durationMillis"/> milliseconds
    /// (PUT /mockserver/clock, action "advance"). The duration must be positive.
    /// Returns this client for chaining.
    /// </summary>
    public MockServerClient AdvanceClock(long durationMillis)
        => AdvanceClockAsync(durationMillis).GetAwaiter().GetResult();

    /// <summary>
    /// Advance the frozen simulated clock (async). See <see cref="AdvanceClock"/>.
    /// </summary>
    public async Task<MockServerClient> AdvanceClockAsync(long durationMillis)
    {
        var json = JsonSerializer.Serialize(
            new Dictionary<string, object?> { ["action"] = "advance", ["durationMillis"] = durationMillis }, JsonOptions);
        var (statusCode, body) = await PutAsync("/mockserver/clock", json).ConfigureAwait(false);

        if (statusCode >= 400)
            throw new MockServerClientException($"Failed to advance clock (HTTP {statusCode}): {body}");
        return this;
    }

    /// <summary>
    /// Reset the simulated clock back to the real system clock (PUT /mockserver/clock,
    /// action "reset"). Returns this client for chaining.
    /// </summary>
    public MockServerClient ResetClock()
        => ResetClockAsync().GetAwaiter().GetResult();

    /// <summary>
    /// Reset the simulated clock (async). See <see cref="ResetClock"/>.
    /// </summary>
    public async Task<MockServerClient> ResetClockAsync()
    {
        var json = JsonSerializer.Serialize(new Dictionary<string, object?> { ["action"] = "reset" }, JsonOptions);
        var (statusCode, body) = await PutAsync("/mockserver/clock", json).ConfigureAwait(false);

        if (statusCode >= 400)
            throw new MockServerClientException($"Failed to reset clock (HTTP {statusCode}): {body}");
        return this;
    }

    /// <summary>
    /// Read the current clock status (GET /mockserver/clock). Returns the raw JSON body,
    /// e.g. <c>{"currentInstant":"...","currentEpochMillis":...,"frozen":true}</c>.
    /// </summary>
    public string ClockStatus()
        => ClockStatusAsync().GetAwaiter().GetResult();

    /// <summary>
    /// Read the current clock status (async). See <see cref="ClockStatus"/>.
    /// </summary>
    public async Task<string> ClockStatusAsync()
    {
        var (statusCode, body) = await GetAsync("/mockserver/clock").ConfigureAwait(false);

        if (statusCode >= 400)
            throw new MockServerClientException($"Failed to get clock status (HTTP {statusCode}): {body}");
        return body ?? "";
    }

    // -------------------------------------------------------------------
    // Metrics
    // -------------------------------------------------------------------

    /// <summary>
    /// Retrieve the JSON counter snapshot of internal metrics
    /// (PUT /mockserver/retrieve?type=METRICS). Returns the raw JSON object mapping each
    /// metric name to its long value, or <c>{}</c> when metrics are disabled.
    /// </summary>
    public string RetrieveMetrics()
        => RetrieveMetricsAsync().GetAwaiter().GetResult();

    /// <summary>
    /// Retrieve the JSON metrics snapshot (async). See <see cref="RetrieveMetrics"/>.
    /// </summary>
    public async Task<string> RetrieveMetricsAsync()
    {
        var (statusCode, body) = await PutAsync("/mockserver/retrieve?type=METRICS", "").ConfigureAwait(false);

        if (statusCode >= 400)
            throw new MockServerClientException($"Failed to retrieve metrics (HTTP {statusCode}): {body}");
        return body ?? "";
    }

    /// <summary>
    /// Scrape the Prometheus exposition-format metrics (GET /mockserver/metrics). Returns the
    /// exposition text. Requires the server to be started with metrics enabled; otherwise the
    /// scrape endpoint returns <c>404</c>. This is distinct from <see cref="RetrieveMetrics"/>
    /// (the JSON counter snapshot).
    /// </summary>
    public string ScrapeMetrics()
        => ScrapeMetricsAsync().GetAwaiter().GetResult();

    /// <summary>
    /// Scrape the Prometheus exposition-format metrics (async). See <see cref="ScrapeMetrics"/>.
    /// </summary>
    public async Task<string> ScrapeMetricsAsync()
    {
        var (statusCode, body) = await GetAsync("/mockserver/metrics").ConfigureAwait(false);

        if (statusCode >= 400)
            throw new MockServerClientException($"Failed to scrape metrics (HTTP {statusCode}): {body}");
        return body ?? "";
    }

    // -------------------------------------------------------------------
    // Configuration (read / update)
    // -------------------------------------------------------------------

    /// <summary>
    /// Read the effective live configuration (GET /mockserver/configuration). Returns the
    /// serialized configuration JSON body.
    /// </summary>
    public string RetrieveConfiguration()
        => RetrieveConfigurationAsync().GetAwaiter().GetResult();

    /// <summary>
    /// Read the effective live configuration (async). See <see cref="RetrieveConfiguration"/>.
    /// </summary>
    public async Task<string> RetrieveConfigurationAsync()
    {
        var (statusCode, body) = await GetAsync("/mockserver/configuration").ConfigureAwait(false);

        if (statusCode >= 400)
            throw new MockServerClientException($"Failed to retrieve configuration (HTTP {statusCode}): {body}");
        return body ?? "";
    }

    /// <summary>
    /// Update the live configuration with a partial ConfigurationDTO JSON document
    /// (PUT /mockserver/configuration). Only the fields present are applied. Returns the
    /// serialized updated-configuration JSON body. A null body is sent as an empty document.
    /// Properties fixed at startup, such as forwardProxyBlockPrivateNetworks, forwardProxyAllowedHosts
    /// and forwardProxyDeniedHosts, are ignored and the server logs a warning.
    /// </summary>
    public string UpdateConfiguration(string? configJson)
        => UpdateConfigurationAsync(configJson).GetAwaiter().GetResult();

    /// <summary>
    /// Update the live configuration (async). See <see cref="UpdateConfiguration"/>.
    /// </summary>
    public async Task<string> UpdateConfigurationAsync(string? configJson)
    {
        var (statusCode, body) = await PutAsync("/mockserver/configuration", configJson ?? "").ConfigureAwait(false);

        if (statusCode == 400)
            throw new MockServerClientException($"Invalid configuration: {body}");
        if (statusCode >= 400)
            throw new MockServerClientException($"Failed to update configuration (HTTP {statusCode}): {body}");
        return body ?? "";
    }

    // -------------------------------------------------------------------
    // Drift detection
    // -------------------------------------------------------------------

    /// <summary>
    /// Retrieve the recorded mock drift report (GET /mockserver/drift). Returns the
    /// serialized report JSON body, of the form
    /// <c>{"count": &lt;n&gt;, "drifts": [ ... ]}</c>, where each entry describes a
    /// difference detected between a mock's configured response and the live upstream
    /// response for the same request.
    /// </summary>
    public string RetrieveDrift()
        => RetrieveDriftAsync().GetAwaiter().GetResult();

    /// <summary>
    /// Retrieve the recorded mock drift report (async). See <see cref="RetrieveDrift"/>.
    /// </summary>
    public async Task<string> RetrieveDriftAsync()
    {
        var (statusCode, body) = await GetAsync("/mockserver/drift").ConfigureAwait(false);

        if (statusCode >= 400)
            throw new MockServerClientException($"Failed to retrieve drift (HTTP {statusCode}): {body}");
        return body ?? "";
    }

    /// <summary>
    /// Clear all recorded mock drift (PUT /mockserver/drift/clear).
    /// </summary>
    public void ClearDrift()
        => ClearDriftAsync().GetAwaiter().GetResult();

    /// <summary>
    /// Clear all recorded mock drift (async). See <see cref="ClearDrift"/>.
    /// </summary>
    public async Task ClearDriftAsync()
    {
        var (statusCode, body) = await PutAsync("/mockserver/drift/clear", "").ConfigureAwait(false);

        if (statusCode >= 400)
            throw new MockServerClientException($"Failed to clear drift (HTTP {statusCode}): {body}");
    }

    // -------------------------------------------------------------------
    // Pact (import / export / verify)
    // -------------------------------------------------------------------

    /// <summary>
    /// Import a Pact v3 contract, creating/updating the matching expectations
    /// (PUT /mockserver/pact/import). Returns the serialized JSON array of upserted
    /// expectations.
    /// </summary>
    /// <exception cref="ArgumentException">If <paramref name="json"/> is null or blank.</exception>
    public List<Expectation> PactImport(string json)
        => PactImportAsync(json).GetAwaiter().GetResult();

    /// <summary>
    /// Import a Pact v3 contract (async). See <see cref="PactImport"/>.
    /// </summary>
    public async Task<List<Expectation>> PactImportAsync(string json)
    {
        if (string.IsNullOrWhiteSpace(json))
            throw new ArgumentException("pact contract JSON must not be empty", nameof(json));
        var (statusCode, body) = await PutAsync("/mockserver/pact/import", json).ConfigureAwait(false);

        if (statusCode >= 400)
            throw new MockServerClientException($"Failed to import pact (HTTP {statusCode}): {body}");
        return DeserializeExpectations(body);
    }

    /// <summary>
    /// Export the active expectations as a Pact v3 contract
    /// (PUT /mockserver/pact?consumer=&amp;provider=). Blank consumer/provider use server
    /// defaults. Returns the generated Pact contract JSON.
    /// </summary>
    public string PactExport(string? consumer = null, string? provider = null)
        => PactExportAsync(consumer, provider).GetAwaiter().GetResult();

    /// <summary>
    /// Export the active expectations as a Pact v3 contract (async). See <see cref="PactExport"/>.
    /// </summary>
    public async Task<string> PactExportAsync(string? consumer = null, string? provider = null)
    {
        var query = new List<string>();
        if (!string.IsNullOrEmpty(consumer)) query.Add("consumer=" + Uri.EscapeDataString(consumer));
        if (!string.IsNullOrEmpty(provider)) query.Add("provider=" + Uri.EscapeDataString(provider));
        var path = "/mockserver/pact" + (query.Count > 0 ? "?" + string.Join("&", query) : "");
        var (statusCode, body) = await PutAsync(path, "").ConfigureAwait(false);

        if (statusCode >= 400)
            throw new MockServerClientException($"Failed to export pact (HTTP {statusCode}): {body}");
        return body ?? "";
    }

    /// <summary>
    /// Verify a Pact v3 contract against the active expectations (PUT /mockserver/pact/verify).
    /// Returns <c>true</c> when every interaction matched (HTTP 202), <c>false</c> when
    /// verification failed (HTTP 406). Does not throw for a verification failure.
    /// </summary>
    /// <exception cref="ArgumentException">If <paramref name="json"/> is null or blank.</exception>
    public bool PactVerify(string json)
        => PactVerifyAsync(json).GetAwaiter().GetResult();

    /// <summary>
    /// Verify a Pact v3 contract (async). See <see cref="PactVerify"/>.
    /// </summary>
    public async Task<bool> PactVerifyAsync(string json)
    {
        if (string.IsNullOrWhiteSpace(json))
            throw new ArgumentException("pact contract JSON must not be empty", nameof(json));
        var (statusCode, body) = await PutAsync("/mockserver/pact/verify", json).ConfigureAwait(false);

        if (statusCode == 202) return true;
        if (statusCode == 406) return false;
        throw new MockServerClientException($"Failed to verify pact (HTTP {statusCode}): {body}");
    }

    // -------------------------------------------------------------------
    // File store (store / retrieve / list / delete)
    // -------------------------------------------------------------------

    /// <summary>
    /// Store a UTF-8 text file in the in-memory file store (PUT /mockserver/files/store).
    /// Returns this client for chaining.
    /// </summary>
    public MockServerClient StoreFile(string name, string content)
        => StoreFileAsync(name, content).GetAwaiter().GetResult();

    /// <summary>
    /// Store a text file (async). See <see cref="StoreFile"/>.
    /// </summary>
    public async Task<MockServerClient> StoreFileAsync(string name, string content)
    {
        if (string.IsNullOrEmpty(name)) throw new ArgumentException("name is required", nameof(name));
        if (content == null) throw new ArgumentNullException(nameof(content));
        var json = JsonSerializer.Serialize(
            new Dictionary<string, object?> { ["name"] = name, ["content"] = content }, JsonOptions);
        var (statusCode, body) = await PutAsync("/mockserver/files/store", json).ConfigureAwait(false);

        if (statusCode >= 400)
            throw new MockServerClientException($"Failed to store file '{name}' (HTTP {statusCode}): {body}");
        return this;
    }

    /// <summary>
    /// Retrieve the raw bytes of a stored file (PUT /mockserver/files/retrieve). The server
    /// returns the file content as the raw response body.
    /// </summary>
    /// <exception cref="MockServerClientException">If the file does not exist (HTTP 404).</exception>
    public byte[] RetrieveFile(string name)
        => RetrieveFileAsync(name).GetAwaiter().GetResult();

    /// <summary>
    /// Retrieve the raw bytes of a stored file (async). See <see cref="RetrieveFile"/>.
    /// </summary>
    public async Task<byte[]> RetrieveFileAsync(string name)
    {
        if (string.IsNullOrEmpty(name)) throw new ArgumentException("name is required", nameof(name));
        var json = JsonSerializer.Serialize(new Dictionary<string, object?> { ["name"] = name }, JsonOptions);
        var (statusCode, body) = await PutBytesResponseAsync("/mockserver/files/retrieve", json).ConfigureAwait(false);

        if (statusCode == 404)
            throw new MockServerClientException($"File not found: {name} (HTTP 404)");
        if (statusCode >= 400)
            throw new MockServerClientException(
                $"Failed to retrieve file '{name}' (HTTP {statusCode}): {Encoding.UTF8.GetString(body)}");
        return body;
    }

    /// <summary>
    /// List the names of all files in the in-memory file store (PUT /mockserver/files/list).
    /// </summary>
    public List<string> ListFiles()
        => ListFilesAsync().GetAwaiter().GetResult();

    /// <summary>
    /// List the names of all stored files (async). See <see cref="ListFiles"/>.
    /// </summary>
    public async Task<List<string>> ListFilesAsync()
    {
        var (statusCode, body) = await PutAsync("/mockserver/files/list", "").ConfigureAwait(false);

        if (statusCode >= 400)
            throw new MockServerClientException($"Failed to list files (HTTP {statusCode}): {body}");

        if (!string.IsNullOrEmpty(body))
        {
            var result = JsonSerializer.Deserialize<List<string>>(body, JsonOptions);
            if (result != null) return result;
        }
        return new List<string>();
    }

    /// <summary>
    /// Delete a stored file by name (PUT /mockserver/files/delete). Returns this client for
    /// chaining.
    /// </summary>
    /// <exception cref="MockServerClientException">If the file does not exist (HTTP 404).</exception>
    public MockServerClient DeleteFile(string name)
        => DeleteFileAsync(name).GetAwaiter().GetResult();

    /// <summary>
    /// Delete a stored file by name (async). See <see cref="DeleteFile"/>.
    /// </summary>
    public async Task<MockServerClient> DeleteFileAsync(string name)
    {
        if (string.IsNullOrEmpty(name)) throw new ArgumentException("name is required", nameof(name));
        var json = JsonSerializer.Serialize(new Dictionary<string, object?> { ["name"] = name }, JsonOptions);
        var (statusCode, body) = await PutAsync("/mockserver/files/delete", json).ConfigureAwait(false);

        if (statusCode == 404)
            throw new MockServerClientException($"File not found: {name} (HTTP 404)");
        if (statusCode >= 400)
            throw new MockServerClientException($"Failed to delete file '{name}' (HTTP {statusCode}): {body}");
        return this;
    }

    // -------------------------------------------------------------------
    // Import (HAR / Postman)
    // -------------------------------------------------------------------

    /// <summary>
    /// Import a HAR document, creating/updating the matching expectations
    /// (PUT /mockserver/import?format=har). Returns the serialized JSON array of upserted
    /// expectations.
    /// </summary>
    /// <exception cref="ArgumentException">If <paramref name="harJson"/> is null or blank.</exception>
    public List<Expectation> ImportHar(string harJson)
        => ImportHarAsync(harJson).GetAwaiter().GetResult();

    /// <summary>
    /// Import a HAR document (async). See <see cref="ImportHar"/>.
    /// </summary>
    public Task<List<Expectation>> ImportHarAsync(string harJson)
        => ImportDocumentAsync(harJson, "har");

    /// <summary>
    /// Import a Postman collection, creating/updating the matching expectations
    /// (PUT /mockserver/import?format=postman). Returns the serialized JSON array of upserted
    /// expectations.
    /// </summary>
    /// <exception cref="ArgumentException">If <paramref name="collectionJson"/> is null or blank.</exception>
    public List<Expectation> ImportPostmanCollection(string collectionJson)
        => ImportPostmanCollectionAsync(collectionJson).GetAwaiter().GetResult();

    /// <summary>
    /// Import a Postman collection (async). See <see cref="ImportPostmanCollection"/>.
    /// </summary>
    public Task<List<Expectation>> ImportPostmanCollectionAsync(string collectionJson)
        => ImportDocumentAsync(collectionJson, "postman");

    private async Task<List<Expectation>> ImportDocumentAsync(string json, string format)
    {
        if (string.IsNullOrWhiteSpace(json))
            throw new ArgumentException($"{format} document JSON must not be empty", nameof(json));
        var path = "/mockserver/import?format=" + Uri.EscapeDataString(format);
        var (statusCode, body) = await PutAsync(path, json).ConfigureAwait(false);

        if (statusCode >= 400)
            throw new MockServerClientException($"Failed to import {format} document (HTTP {statusCode}): {body}");
        return DeserializeExpectations(body);
    }

    // -------------------------------------------------------------------
    // Operating mode (SIMULATE / SPY / CAPTURE)
    // -------------------------------------------------------------------

    /// <summary>
    /// Set the high-level operating mode (PUT /mockserver/mode?mode=&lt;MODE&gt;). Returns this
    /// client for chaining.
    /// </summary>
    public MockServerClient SetMode(MockMode mode)
        => SetModeAsync(mode).GetAwaiter().GetResult();

    /// <summary>
    /// Set the operating mode (async). See <see cref="SetMode"/>.
    /// </summary>
    public async Task<MockServerClient> SetModeAsync(MockMode mode)
    {
        var path = "/mockserver/mode?mode=" + mode.ToString().ToUpperInvariant();
        var (statusCode, body) = await PutAsync(path, "").ConfigureAwait(false);

        if (statusCode >= 400)
            throw new MockServerClientException($"Failed to set mode (HTTP {statusCode}): {body}");
        return this;
    }

    /// <summary>
    /// Read the current operating mode (GET /mockserver/mode). Returns the parsed
    /// <see cref="MockMode"/> from the server's <c>{"mode":"..."}</c> response.
    /// </summary>
    public MockMode RetrieveMode()
        => RetrieveModeAsync().GetAwaiter().GetResult();

    /// <summary>
    /// Read the current operating mode (async). See <see cref="RetrieveMode"/>.
    /// </summary>
    public async Task<MockMode> RetrieveModeAsync()
    {
        var (statusCode, body) = await GetAsync("/mockserver/mode").ConfigureAwait(false);

        if (statusCode >= 400)
            throw new MockServerClientException($"Failed to retrieve mode (HTTP {statusCode}): {body}");

        var mode = string.Empty;
        if (!string.IsNullOrEmpty(body))
        {
            using var doc = JsonDocument.Parse(body);
            if (doc.RootElement.TryGetProperty("mode", out var modeElement))
                mode = modeElement.GetString() ?? string.Empty;
        }
        return mode.ToUpperInvariant() switch
        {
            "SPY" => MockMode.Spy,
            "CAPTURE" => MockMode.Capture,
            "SIMULATE" => MockMode.Simulate,
            _ => throw new MockServerClientException($"Unknown operating mode in response: '{mode}'")
        };
    }

    // -------------------------------------------------------------------
    // WSDL -> expectations
    // -------------------------------------------------------------------

    /// <summary>
    /// Generate expectations from a WSDL document (PUT /mockserver/wsdl). The WSDL XML is sent
    /// as the raw request body. Returns the serialized JSON array of generated (upserted)
    /// expectations.
    /// </summary>
    /// <exception cref="ArgumentException">If <paramref name="wsdl"/> is null or blank.</exception>
    public List<Expectation> WsdlExpectation(string wsdl)
        => WsdlExpectationAsync(wsdl).GetAwaiter().GetResult();

    /// <summary>
    /// Generate expectations from a WSDL document (async). See <see cref="WsdlExpectation"/>.
    /// </summary>
    public async Task<List<Expectation>> WsdlExpectationAsync(string wsdl)
    {
        if (string.IsNullOrWhiteSpace(wsdl))
            throw new ArgumentException("WSDL document must not be empty", nameof(wsdl));
        var request = new HttpRequestMessage(HttpMethod.Put, _baseUrl + "/mockserver/wsdl")
        {
            Content = new StringContent(wsdl, Encoding.UTF8, "text/xml")
        };
        var (statusCode, body) = await SendAsync(request).ConfigureAwait(false);

        if (statusCode >= 400)
            throw new MockServerClientException($"Failed to generate WSDL expectations (HTTP {statusCode}): {body}");
        return DeserializeExpectations(body);
    }

    private static List<Expectation> DeserializeExpectations(string body)
    {
        if (!string.IsNullOrEmpty(body))
        {
            var result = JsonSerializer.Deserialize<List<Expectation>>(body, JsonOptions);
            if (result != null) return result;
        }
        return new List<Expectation>();
    }

    // -------------------------------------------------------------------
    // Internal: called by ForwardChainExpectation
    // -------------------------------------------------------------------

    internal List<Expectation> UpsertExpectation(Expectation expectation)
        => Upsert(expectation);

    internal Task<List<Expectation>> UpsertExpectationAsync(Expectation expectation)
        => UpsertAsync(expectation);

    // -------------------------------------------------------------------
    // HTTP transport
    // -------------------------------------------------------------------

    private async Task<(int StatusCode, string Body)> SendAsync(HttpRequestMessage request)
    {
        ApplyControlPlaneAuth(request);
        using (request)
        {
            using var response = await _httpClient.SendAsync(request).ConfigureAwait(false);
            var responseBody = await response.Content.ReadAsStringAsync().ConfigureAwait(false);
            return ((int)response.StatusCode, responseBody);
        }
    }

    private async Task<(int StatusCode, byte[] Body)> SendBytesAsync(HttpRequestMessage request)
    {
        ApplyControlPlaneAuth(request);
        using (request)
        {
            using var response = await _httpClient.SendAsync(request).ConfigureAwait(false);
            var responseBytes = await response.Content.ReadAsByteArrayAsync().ConfigureAwait(false);
            return ((int)response.StatusCode, responseBytes);
        }
    }

    private Task<(int StatusCode, byte[] Body)> PutBytesResponseAsync(string path, string jsonBody)
    {
        var request = new HttpRequestMessage(HttpMethod.Put, _baseUrl + path)
        {
            Content = new StringContent(jsonBody ?? "", Encoding.UTF8, "application/json")
        };
        return SendBytesAsync(request);
    }

    private Task<(int StatusCode, string Body)> PutAsync(string path, string jsonBody)
    {
        var request = new HttpRequestMessage(HttpMethod.Put, _baseUrl + path)
        {
            Content = new StringContent(jsonBody ?? "", Encoding.UTF8, "application/json")
        };
        return SendAsync(request);
    }

    private Task<(int StatusCode, string Body)> PutBytesAsync(string path, byte[] bytes)
    {
        var content = new ByteArrayContent(bytes);
        content.Headers.ContentType = new System.Net.Http.Headers.MediaTypeHeaderValue("application/octet-stream");
        var request = new HttpRequestMessage(HttpMethod.Put, _baseUrl + path) { Content = content };
        return SendAsync(request);
    }

    private Task<(int StatusCode, string Body)> GetAsync(string path)
        => SendAsync(new HttpRequestMessage(HttpMethod.Get, _baseUrl + path));

    private Task<(int StatusCode, string Body)> DeleteAsync(string path)
        => SendAsync(new HttpRequestMessage(HttpMethod.Delete, _baseUrl + path));

    public void Dispose()
    {
        _breakpointWs?.Dispose();
        _breakpointWsLock.Dispose();
        if (_ownsHttpClient)
            _httpClient.Dispose();
    }
}
