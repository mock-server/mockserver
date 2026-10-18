using System.Net;
using FluentAssertions;
using MockServer.Client.Models;
using Llm = MockServer.Client.Llm;
using Xunit;

namespace MockServer.Client.IntegrationTests;

/// <summary>
/// Integration tests that prove the SERVER actually honours the actions and matchers the .NET
/// client sends over the wire — not merely that the client can create the expectation.
///
/// <para>Covered behaviours:</para>
/// <list type="bullet">
///   <item><description>NottableString negation (a header matcher value of <c>!foo</c>): the client
///   must send the negation marker and the server must reject a request whose header equals
///   <c>foo</c> while matching one whose header does not — and the escaped literal <c>!foo</c>
///   (via <see cref="MatcherValue.Literal"/>) must match a header whose value really is
///   <c>!foo</c>.</description></item>
///   <item><description>Response actions (forward / error): the client registers a FORWARD and an
///   ERROR action and a real request proves the server actually forwards (the response body comes
///   from the forwarded-to expectation) and actually errors (the connection is dropped) —
///   not just that the expectation was created.</description></item>
///   <item><description>Advanced response actions (template, BINARY body, SSE, LLM): a real request
///   proves the server renders the template, serves the exact bytes, streams the registered events
///   and encodes the provider's response envelope.</description></item>
///   <item><description>Protocol and callback actions (WebSocket, DNS, gRPC, class and object callbacks), in
///   <c>WireBehaviorTests.ProtocolActions.cs</c>: the frames, answer records, messages and trailers, and
///   callback-built responses the server actually sends.</description></item>
/// </list>
///
/// <para>Reaches a running MockServer via the same harness the other integration tests use: the
/// <c>MOCKSERVER_URL</c> environment variable (for example <c>http://localhost:1080</c>). When it
/// is unset the tests skip, unless <c>MOCKSERVER_REQUIRE_SERVER=true</c> (set in CI) forces a loud
/// failure so a missing server can never be a silent green.</para>
/// </summary>
[Collection("Integration")]
public partial class WireBehaviorTests : IDisposable
{
    private readonly MockServerClient? _client;
    private readonly string? _baseUrl;
    private readonly string? _selfForwardHost;
    private readonly int _selfForwardPort;

    public WireBehaviorTests()
    {
        var url = Environment.GetEnvironmentVariable("MOCKSERVER_URL");
        if (string.IsNullOrEmpty(url))
        {
            _client = null;
            return;
        }

        _baseUrl = url.TrimEnd('/');
        var uri = new Uri(url);
        _client = new MockServerClient(uri.Host, uri.Port, secure: uri.Scheme == "https");
        // The server can always reach itself on its own loopback and listening port. This holds both
        // for a host-run jar (localhost:<port>) and for a containerised server (localhost:1080 inside
        // its own container), so a self-forward needs no knowledge of external networking.
        _selfForwardHost = "localhost";
        _selfForwardPort = uri.Port;
    }

    public void Dispose()
    {
        _client?.Dispose();
    }

    private void SkipIfNoServer()
    {
        if (_client == null
            && Environment.GetEnvironmentVariable("MOCKSERVER_REQUIRE_SERVER") == "true")
        {
            throw new InvalidOperationException(
                "MOCKSERVER_REQUIRE_SERVER=true but MOCKSERVER_URL is not set — refusing to skip.");
        }

        Skip.If(_client == null, "MOCKSERVER_URL environment variable not set; skipping integration test.");
    }

    private static HttpResponseMessage Get(string url, string? headerName = null, string? headerValue = null)
    {
        using var httpClient = new HttpClient();
        using var request = new HttpRequestMessage(HttpMethod.Get, url);
        if (headerName != null && headerValue != null)
        {
            request.Headers.TryAddWithoutValidation(headerName, headerValue);
        }
        return httpClient.Send(request);
    }

    // ── NottableString negation over the wire ──────────────────────────────────────────────

    [SkippableFact]
    public void HeaderNegationMatcher_IsSentAndEnforced_OverWire()
    {
        SkipIfNoServer();
        _client!.Reset();

        // "X-Tag is NOT foo" — MatcherValue.NotLiteral serialises to the plain marker string "!foo",
        // which the server reads back as a negation.
        _client.When(
            HttpRequest.Request().WithMethod("GET").WithPath("/neg-header")
                .WithHeaderMatcher("X-Tag", MatcherValue.NotLiteral("foo"))
        ).Respond(
            HttpResponse.Response().WithStatusCode(200).WithBody("MATCHED-NOT-FOO")
        );

        // A non-foo header satisfies "not foo" → the expectation matches.
        var matched = Get($"{_baseUrl}/neg-header", "X-Tag", "bar");
        matched.StatusCode.Should().Be(HttpStatusCode.OK);
        matched.Content.ReadAsStringAsync().Result.Should().Be("MATCHED-NOT-FOO");

        // The excluded value "foo" must NOT match → no expectation → 404. This is the discrimination
        // that proves the "!" negation actually crossed the wire and the server enforced it; a client
        // that dropped the "!" would send a plain equality on "foo", making this request match (200)
        // and the bar request 404 — the exact inversion the positive control demonstrates.
        var excluded = Get($"{_baseUrl}/neg-header", "X-Tag", "foo");
        excluded.StatusCode.Should().Be(HttpStatusCode.NotFound);

        _client.Reset();
    }

    [SkippableFact]
    public void LiteralBangHeaderMatcher_IsSentAndEnforced_OverWire()
    {
        SkipIfNoServer();
        _client!.Reset();

        // Escaped literal: "X-Tag is exactly the string !foo" — MatcherValue.Literal escapes to the
        // object form {"not":false,"value":"!foo"} so the leading "!" is data, not a negation marker.
        _client.When(
            HttpRequest.Request().WithMethod("GET").WithPath("/lit-header")
                .WithHeaderMatcher("X-Tag", MatcherValue.Literal("!foo"))
        ).Respond(
            HttpResponse.Response().WithStatusCode(200).WithBody("MATCHED-LITERAL-BANG")
        );

        // Only a header whose value really is "!foo" matches.
        var literal = Get($"{_baseUrl}/lit-header", "X-Tag", "!foo");
        literal.StatusCode.Should().Be(HttpStatusCode.OK);
        literal.Content.ReadAsStringAsync().Result.Should().Be("MATCHED-LITERAL-BANG");

        // "foo" must NOT match a literal-"!foo" matcher (if the escape had been dropped and read as a
        // negation, "foo" would be excluded and any other value would match — a different behaviour).
        var plainFoo = Get($"{_baseUrl}/lit-header", "X-Tag", "foo");
        plainFoo.StatusCode.Should().Be(HttpStatusCode.NotFound);

        // An unrelated value must NOT match either (it would, had the escape collapsed to "not foo").
        var other = Get($"{_baseUrl}/lit-header", "X-Tag", "bar");
        other.StatusCode.Should().Be(HttpStatusCode.NotFound);

        _client.Reset();
    }

    // ── server-echoed NottableString negation/escape is DECODED by the client ──────────────

    [SkippableFact]
    public void NegationAndEscapeMatchers_AreDecoded_WhenRetrieved()
    {
        SkipIfNoServer();
        _client!.Reset();

        // The tests above prove the SERVER acts on the negation the client sends. This proves the
        // reverse direction: that the client correctly DECODES a server-echoed NottableString when the
        // expectation is read back with RetrieveActiveExpectations — the "!" (and its escape) survives
        // the server echo → client decode intact. Without this, a decode that dropped the negation flag
        // or mis-read the escape would go unnoticed because nothing asserted on the value read back.

        // (a) A negation: NotLiteral("foo") is sent as the bare "!foo" marker and the server echoes it
        // back as the plain string "!foo".
        _client.When(
            HttpRequest.Request().WithMethod("GET").WithPath("/neg-decode")
                .WithHeaderMatcher("X-Tag", MatcherValue.NotLiteral("foo"))
        ).Respond(
            HttpResponse.Response().WithStatusCode(200)
        );

        // (c) An ESCAPED literal "!foo": the leading "!" is DATA, not a negation, so the client sends
        // the object form and the server echoes the object back.
        _client.When(
            HttpRequest.Request().WithMethod("GET").WithPath("/esc-decode")
                .WithHeaderMatcher("X-Lit", MatcherValue.Literal("!foo"))
        ).Respond(
            HttpResponse.Response().WithStatusCode(200)
        );

        var expectations = _client.RetrieveActiveExpectations();

        // The negation must decode back to Not=true / Value="foo": the "!" survived the server echo and
        // the client read it as a negation, not as literal data.
        var neg = DecodedHeader(expectations, "/neg-decode", "X-Tag");
        neg.Not.Should().BeTrue("the '!' negation flag must survive the server echo → client decode");
        neg.Should().Be(MatcherValue.NotLiteral("foo"),
            "server-echoed negation must decode to Not=true, Value=foo");

        // The escaped literal must decode back to Not=false / Value="!foo": the "!" is preserved as data
        // and was NOT misread as a negation.
        var esc = DecodedHeader(expectations, "/esc-decode", "X-Lit");
        esc.Not.Should().BeFalse("an escaped '!foo' must NOT be decoded as a negation");
        esc.Value.Should().Be("!foo", "the escaped '!' must survive the round-trip as literal data");
        esc.Should().Be(MatcherValue.Literal("!foo"),
            "server-echoed escaped literal must decode to Not=false, Value=!foo");

        _client.Reset();
    }

    /// <summary>
    /// Finds the single decoded header matcher for <paramref name="key"/> on the retrieved expectation
    /// whose request path is <paramref name="path"/>, as a <see cref="MatcherValue"/> so the caller can
    /// assert on the decoded negation/escape. It looks in BOTH decoded homes: a value that round-trips
    /// through the plain string form lands in the plain <c>Headers</c> map (a bare <c>"!foo"</c>), while
    /// a value that needs the object form to stay unambiguous (an escaped <c>"!foo"</c>) lands in
    /// <c>HeaderMatchers</c>.
    /// </summary>
    private static MatcherValue DecodedHeader(IEnumerable<Expectation> expectations, string path, string key)
    {
        var request = expectations
            .Select(e => e.HttpRequest)
            .FirstOrDefault(r => r?.Path == path)
            ?? throw new InvalidOperationException($"no expectation retrieved for path {path}");

        // Object (nottable) form is decoded straight into HeaderMatchers.
        if (request.HeaderMatchers != null
            && request.HeaderMatchers.TryGetValue(key, out var matchers)
            && matchers.Count > 0)
        {
            return matchers[0];
        }

        // Plain-string form: decode it the way the server reads a NottableString.
        if (request.Headers != null
            && request.Headers.TryGetValue(key, out var values)
            && values.Count > 0)
        {
            return MatcherValue.ParsePlain(values[0]);
        }

        throw new InvalidOperationException($"header {key} not present on expectation for {path}");
    }

    // ── response actions actually performed over the wire ──────────────────────────────────

    [SkippableFact]
    public void ForwardAction_IsActuallyForwarded_OverWire()
    {
        SkipIfNoServer();
        _client!.Reset();

        // Forward the request to the server itself (once). The forwarded request re-enters on the same
        // path; because the forward expectation is consumed (Times.Once) and lower-priority than the
        // response below is not needed — after consumption only the response expectation remains — the
        // second pass is served by the plain response, whose distinctive body proves the round trip.
        _client.When(
            HttpRequest.Request().WithMethod("GET").WithPath("/self-forward"),
            Times.Once(),
            timeToLive: null,
            priority: 10
        ).Forward(
            HttpForward.Forward().WithHost(_selfForwardHost!).WithPort(_selfForwardPort).WithScheme("HTTP")
        );

        _client.When(
            HttpRequest.Request().WithMethod("GET").WithPath("/self-forward")
        ).Respond(
            HttpResponse.Response().WithStatusCode(200).WithBody("FORWARDED-OK")
        );

        var response = Get($"{_baseUrl}/self-forward");
        response.StatusCode.Should().Be(HttpStatusCode.OK);
        response.Content.ReadAsStringAsync().Result.Should().Be("FORWARDED-OK");

        // The body alone is not proof: the response expectation matches /self-forward directly, so a
        // server that ignored the forward could still return "FORWARDED-OK". The decisive signal is
        // that the path was RECEIVED TWICE — once from the test client and once when the forward
        // re-entered the server — which only an actually-performed forward produces. A server that
        // dropped the forward would log exactly one receipt and this verification would fail.
        _client.Verify(
            HttpRequest.Request().WithMethod("GET").WithPath("/self-forward"),
            VerificationTimes.AtLeastTimes(2)
        );

        _client.Reset();
    }

    [SkippableFact]
    public void ErrorAction_ActuallyDropsConnection_OverWire()
    {
        SkipIfNoServer();
        _client!.Reset();

        _client.When(
            HttpRequest.Request().WithMethod("GET").WithPath("/drop-conn")
        ).Error(
            HttpError.Error().WithDropConnection(true)
        );

        // The server drops the connection instead of responding, so the client sees a transport
        // failure rather than any HTTP status. If the action had NOT been performed the request would
        // return 404 (no response expectation) and this assertion would fail — the positive control.
        var act = () => Get($"{_baseUrl}/drop-conn");
        act.Should().Throw<HttpRequestException>();

        _client.Reset();
    }

    // ── advanced response actions actually rendered/served over the wire ───────────────────

    [SkippableFact]
    public void ResponseTemplate_IsRendered_OverWire()
    {
        SkipIfNoServer();
        _client!.Reset();

        // A VELOCITY response template that renders a JSON object describing the response and echoes
        // $!{request.path} into the body. The server maps the rendered JSON to an HttpResponse, so the
        // served body is a function of the matched request — something a create+retrieve (schema-only)
        // test can never prove.
        _client.When(
            HttpRequest.Request().WithMethod("GET").WithPath("/tmpl-wire")
        ).RespondWithTemplate(
            HttpTemplate.OfType(TemplateType.VELOCITY)
                .WithTemplate("{\"statusCode\": 200, \"body\": \"TEMPLATED path=$!{request.path}\"}")
        );

        var response = Get($"{_baseUrl}/tmpl-wire");
        response.StatusCode.Should().Be(HttpStatusCode.OK,
            "the response template must be executed and produce a 200");
        response.Content.ReadAsStringAsync().Result.Should().Be("TEMPLATED path=/tmpl-wire",
            "the served body must be the VELOCITY-rendered template with the request path substituted — "
            + "proving the server ran the template engine over the live request, not merely accepted the schema");

        _client.Reset();
    }

    [SkippableFact]
    public void BinaryResponseBody_IsServed_OverWire()
    {
        SkipIfNoServer();
        _client!.Reset();

        // A BINARY response body carrying bytes that are NOT valid UTF-8 (a NUL 0x00 and 0xFF). Only a
        // server that decoded the base64 and served the raw octets verbatim yields the exact bytes; a
        // path that mangled them as text would fail the byte-exact comparison.
        var payload = new byte[] { 0x4D, 0x53, 0x00, 0x01, 0xFF, 0x7A };
        var response = HttpResponse.Response().WithStatusCode(200).Build();
        response.Body = Body.OfBinary(Convert.ToBase64String(payload), "application/octet-stream");

        _client.When(
            HttpRequest.Request().WithMethod("GET").WithPath("/bin-wire")
        ).Respond(response);

        var served = Get($"{_baseUrl}/bin-wire");
        served.StatusCode.Should().Be(HttpStatusCode.OK, "the binary-body response must serve a 200");
        served.Content.ReadAsByteArrayAsync().Result.Should().Equal(payload,
            "the server must serve the EXACT bytes of the registered BINARY body");
        served.Content.Headers.ContentType?.MediaType.Should().Be("application/octet-stream",
            "the registered content type must be served on the wire");

        _client.Reset();
    }

    // ── SSE and LLM response actions actually streamed/encoded over the wire ───────────────

    private static HttpResponseMessage PostJson(string url, string json)
    {
        using var httpClient = new HttpClient();
        using var request = new HttpRequestMessage(HttpMethod.Post, url)
        {
            Content = new StringContent(json, System.Text.Encoding.UTF8, "application/json")
        };
        return httpClient.Send(request);
    }

    /// <summary>Parses a text/event-stream body into (id, event, data, retry) tuples, one per frame.</summary>
    private static List<(string? Id, string? Event, string? Data, string? Retry)> ParseSse(string body)
    {
        var events = new List<(string?, string?, string?, string?)>();
        foreach (var frame in body.Replace("\r\n", "\n").Split("\n\n"))
        {
            if (string.IsNullOrWhiteSpace(frame)) continue;
            string? id = null, evt = null, data = null, retry = null;
            foreach (var line in frame.Split('\n'))
            {
                var colon = line.IndexOf(':');
                if (colon < 0) continue;
                var value = line[(colon + 1)..].TrimStart(' ');
                switch (line[..colon])
                {
                    case "id": id = value; break;
                    case "event": evt = value; break;
                    case "data": data = value; break;
                    case "retry": retry = value; break;
                }
            }
            events.Add((id, evt, data, retry));
        }
        return events;
    }

    [SkippableFact]
    public void SseResponse_IsStreamed_OverWire()
    {
        SkipIfNoServer();
        _client!.Reset();

        _client.When(
            HttpRequest.Request().WithMethod("GET").WithPath("/sse-wire")
        ).RespondWithSse(
            new SseResponseBuilder()
                .WithStatusCode(200)
                .WithEvent("hello-dotnet", eventName: "greet", id: "1")
                .WithEvent("world-dotnet", eventName: "update", id: "42", retry: 3000)
                .WithCloseConnection()
        );

        var response = Get($"{_baseUrl}/sse-wire");
        response.StatusCode.Should().Be(HttpStatusCode.OK);
        response.Content.Headers.ContentType?.MediaType.Should().Be("text/event-stream");

        var events = ParseSse(response.Content.ReadAsStringAsync().Result);
        events.Should().Equal(
            ("1", "greet", "hello-dotnet", (string?)null),
            ("42", "update", "world-dotnet", "3000"));

        _client.Reset();
    }

    [SkippableFact]
    public void LlmResponse_ServesAnthropicEnvelope_OverWire()
    {
        SkipIfNoServer();
        _client!.Reset();

        Llm.LlmMockBuilder.LlmMock("/llm-wire/v1/messages")
            .WithProvider(Llm.Provider.ANTHROPIC)
            .WithModel("claude-dotnet-wire")
            .RespondingWith(Llm.Completion.Create()
                .WithText("Hello from .NET")
                .WithUsage(Llm.Usage.Create().WithInputTokens(13).WithOutputTokens(4)))
            .ApplyTo(_client);

        var response = PostJson($"{_baseUrl}/llm-wire/v1/messages",
            "{\"model\":\"claude-dotnet-wire\",\"max_tokens\":64,\"messages\":[{\"role\":\"user\",\"content\":\"hi\"}]}");
        response.StatusCode.Should().Be(HttpStatusCode.OK);
        response.Content.Headers.ContentType?.MediaType.Should().Be("application/json");

        using var doc = System.Text.Json.JsonDocument.Parse(response.Content.ReadAsStringAsync().Result);
        var root = doc.RootElement;
        root.GetProperty("type").GetString().Should().Be("message", "an Anthropic Messages envelope");
        root.GetProperty("id").GetString().Should().StartWith("msg_");
        root.GetProperty("role").GetString().Should().Be("assistant");
        root.GetProperty("model").GetString().Should().Be("claude-dotnet-wire");
        root.GetProperty("stop_reason").GetString().Should().Be("end_turn");
        var content = root.GetProperty("content");
        content.GetArrayLength().Should().Be(1);
        content[0].GetProperty("type").GetString().Should().Be("text");
        content[0].GetProperty("text").GetString().Should().Be("Hello from .NET");
        root.GetProperty("usage").GetProperty("input_tokens").GetInt32().Should().Be(13);
        root.GetProperty("usage").GetProperty("output_tokens").GetInt32().Should().Be(4);

        _client.Reset();
    }
}
