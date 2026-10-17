using System.Linq;
using System.Text.Json;
using FluentAssertions;
using MockServer.Client.A2a;
using MockServer.Client.Models;
using Xunit;

namespace MockServer.Client.Tests;

/// <summary>
/// Unit tests for the fluent A2A mock builder. Asserts the generated expectation set:
/// the GET agent-card document, the JSON-RPC tasks/send|get|cancel handlers, optional
/// SSE streaming, custom message handlers, and push-notification config + delivery.
/// </summary>
public class A2aBuilderTests
{
    private static readonly JsonSerializerOptions JsonOptions = new()
    {
        DefaultIgnoreCondition = System.Text.Json.Serialization.JsonIgnoreCondition.WhenWritingNull,
        PropertyNamingPolicy = JsonNamingPolicy.CamelCase
    };

    private static JsonElement Serialize(object value)
        => JsonDocument.Parse(JsonSerializer.Serialize(value, JsonOptions)).RootElement;

    private static JsonElement FindByJsonRpcMethod(List<Expectation> expectations, string method)
        => expectations
            .Select(Serialize)
            .First(e => e.GetProperty("httpRequest").TryGetProperty("body", out var body)
                        && body.TryGetProperty("type", out var t) && t.GetString() == "JSON_RPC"
                        && body.GetProperty("method").GetString() == method);

    private static JsonElement FindByJsonPathContaining(List<Expectation> expectations, string fragment)
        => expectations
            .Select(Serialize)
            .First(e => e.GetProperty("httpRequest").TryGetProperty("body", out var body)
                        && body.TryGetProperty("type", out var t) && t.GetString() == "JSON_PATH"
                        && body.GetProperty("jsonPath").GetString()!.Contains(fragment));

    private static JsonElement FindAgentCard(List<Expectation> expectations)
        => expectations
            .Select(Serialize)
            .First(e => e.GetProperty("httpRequest").GetProperty("method").GetString() == "GET");

    [Fact]
    public void A2a_EmptyAgent_ProducesCoreExpectations()
    {
        var expectations = A2aMockBuilder.A2aMock().Build();

        // agent card, tasks/send, tasks/get, tasks/cancel — and nothing else by default.
        expectations.Should().HaveCount(4);

        var card = FindAgentCard(expectations);
        card.GetProperty("httpRequest").GetProperty("path").GetString().Should().Be("/.well-known/agent.json");
        card.GetProperty("httpResponse").GetProperty("statusCode").GetInt32().Should().Be(200);
        var cardBody = card.GetProperty("httpResponse").GetProperty("body").GetString()!;
        cardBody.Should().Contain("\"name\": \"MockAgent\"");
        cardBody.Should().Contain("\"description\": \"A mock A2A agent\"");
        cardBody.Should().Contain("\"version\": \"1.0.0\"");
        cardBody.Should().Contain("\"url\": \"http://localhost/a2a\"");
        cardBody.Should().Contain("\"streaming\": false");
        cardBody.Should().Contain("\"pushNotifications\": false");
        cardBody.Should().Contain("\"skills\": []");

        // tasks/send and tasks/get target the configured path with a JSON-RPC method matcher.
        var send = FindByJsonRpcMethod(expectations, "tasks/send");
        send.GetProperty("httpRequest").GetProperty("method").GetString().Should().Be("POST");
        send.GetProperty("httpRequest").GetProperty("path").GetString().Should().Be("/a2a");
        var sendTemplate = send.GetProperty("httpResponseTemplate").GetProperty("template").GetString()!;
        sendTemplate.Should().Contain("\"jsonrpc\": \"2.0\"");
        sendTemplate.Should().Contain("$!{request.jsonRpcRawId}");
        sendTemplate.Should().Contain("\"state\": \"completed\"");
        sendTemplate.Should().Contain("Task completed successfully");

        FindByJsonRpcMethod(expectations, "tasks/get").ValueKind.Should().Be(JsonValueKind.Object);

        var cancelTemplate = FindByJsonRpcMethod(expectations, "tasks/cancel")
            .GetProperty("httpResponseTemplate").GetProperty("template").GetString()!;
        cancelTemplate.Should().Contain("\"state\": \"canceled\"");
    }

    [Fact]
    public void A2a_CustomPathAndAgentMetadata_ReflectedInCard()
    {
        var expectations = A2aMockBuilder.A2aMock("/agent")
            .WithAgentName("WeatherAgent")
            .WithAgentDescription("Knows the weather")
            .WithAgentVersion("2.1.0")
            .WithAgentUrl("https://example.com/agent")
            .WithAgentCardPath("/.well-known/custom.json")
            .Build();

        var card = FindAgentCard(expectations);
        card.GetProperty("httpRequest").GetProperty("path").GetString().Should().Be("/.well-known/custom.json");
        var cardBody = card.GetProperty("httpResponse").GetProperty("body").GetString()!;
        cardBody.Should().Contain("\"name\": \"WeatherAgent\"");
        cardBody.Should().Contain("\"version\": \"2.1.0\"");
        cardBody.Should().Contain("\"url\": \"https://example.com/agent\"");

        FindByJsonRpcMethod(expectations, "tasks/send")
            .GetProperty("httpRequest").GetProperty("path").GetString().Should().Be("/agent");
    }

    [Fact]
    public void A2a_WithSkill_AppearsOnAgentCard()
    {
        var expectations = A2aMockBuilder.A2aMock()
            .WithSkill("get_weather")
                .WithName("Get Weather")
                .WithDescription("Returns the weather")
                .WithTag("weather")
                .WithExample("What is the weather in London?")
            .And()
            .Build();

        var cardBody = FindAgentCard(expectations).GetProperty("httpResponse").GetProperty("body").GetString()!;
        cardBody.Should().Contain("\"id\": \"get_weather\"");
        cardBody.Should().Contain("\"name\": \"Get Weather\"");
        cardBody.Should().Contain("\"description\": \"Returns the weather\"");
        cardBody.Should().Contain("\"tags\": [\"weather\"]");
        cardBody.Should().Contain("\"examples\": [\"What is the weather in London?\"]");
    }

    [Fact]
    public void A2a_WithStreaming_AdvertisesAndEmitsSseExpectation()
    {
        var expectations = A2aMockBuilder.A2aMock()
            .WithStreaming()
            .Build();

        // agent card, tasks/send, tasks/get, tasks/cancel, streaming.
        expectations.Should().HaveCount(5);

        var cardBody = FindAgentCard(expectations).GetProperty("httpResponse").GetProperty("body").GetString()!;
        cardBody.Should().Contain("\"streaming\": true");

        var streaming = FindByJsonRpcMethod(expectations, "message/stream");
        var sse = streaming.GetProperty("httpSseResponse");
        sse.GetProperty("statusCode").GetInt32().Should().Be(200);
        sse.GetProperty("closeConnection").GetBoolean().Should().BeTrue();
        var events = sse.GetProperty("events");
        events.GetArrayLength().Should().Be(3);
        events[0].GetProperty("data").GetString().Should().Contain("\"kind\": \"status-update\"");
        events[0].GetProperty("data").GetString().Should().Contain("\"state\": \"working\"");
        events[1].GetProperty("data").GetString().Should().Contain("\"kind\": \"artifact-update\"");
        events[2].GetProperty("data").GetString().Should().Contain("\"final\": true");
    }

    [Fact]
    public void A2a_WithStreamingMethod_OverridesMatcherAndImpliesStreaming()
    {
        var expectations = A2aMockBuilder.A2aMock()
            .WithStreamingMethod("tasks/sendSubscribe")
            .Build();

        FindAgentCard(expectations).GetProperty("httpResponse").GetProperty("body").GetString()!
            .Should().Contain("\"streaming\": true");
        FindByJsonRpcMethod(expectations, "tasks/sendSubscribe")
            .TryGetProperty("httpSseResponse", out _).Should().BeTrue();
    }

    [Fact]
    public void A2a_WithCustomTaskHandler_EmitsJsonPathRegexMatcher()
    {
        var expectations = A2aMockBuilder.A2aMock()
            .OnTaskSend()
                .MatchingMessage("hello.*")
                .RespondingWith("Hi there")
            .And()
            .Build();

        var handler = FindByJsonPathContaining(expectations, "=~");
        var jsonPath = handler.GetProperty("httpRequest").GetProperty("body").GetProperty("jsonPath").GetString()!;
        jsonPath.Should().Be("$[?(@.method == 'tasks/send' && @.params.message.parts[0].text =~ /hello.*/)]");
        var template = handler.GetProperty("httpResponseTemplate").GetProperty("template").GetString()!;
        template.Should().Contain("Hi there");
        template.Should().Contain("\"state\": \"completed\"");
    }

    // --- security: message-pattern escaping (CodeQL incomplete-sanitization) ---------

    [Fact]
    public void EscapeMessagePattern_PreservesSingleBackslashRegexEscapes()
    {
        // A regex escape such as \d+ must survive verbatim — the backslash must NOT be doubled,
        // otherwise the intended regex meaning is corrupted.
        A2aMockBuilder.EscapeMessagePattern(@"\d+").Should().Be(@"\d+");
    }

    [Fact]
    public void EscapeMessagePattern_PreservesAlreadyEscapedSlash()
    {
        // An author-supplied escaped slash a\/b must stay a\/b, not become a\\/b (which would
        // turn the slash literal into an escaped backslash followed by an unescaped delimiter).
        A2aMockBuilder.EscapeMessagePattern(@"a\/b").Should().Be(@"a\/b");
    }

    [Fact]
    public void EscapeMessagePattern_TrailingLoneBackslash_CannotEscapeClosingDelimiter()
    {
        // A pattern ENDING in a single backslash is the breakout vector: a lone trailing '\'
        // would escape the closing '/' delimiter. It must be doubled to a literal "\\".
        var pattern = "abc\\"; // three chars then a single trailing backslash
        A2aMockBuilder.EscapeMessagePattern(pattern).Should().Be("abc\\\\");

        // And the full jsonPath produced through the builder must still terminate with the
        // UNESCAPED closing "/)]" — proving the regex literal was not broken out of.
        var expectations = A2aMockBuilder.A2aMock()
            .OnTaskSend()
                .MatchingMessage(pattern)
                .RespondingWith("ok")
            .And()
            .Build();

        var jsonPath = FindByJsonPathContaining(expectations, "=~")
            .GetProperty("httpRequest").GetProperty("body").GetProperty("jsonPath").GetString()!;
        jsonPath.Should().Be("$[?(@.method == 'tasks/send' && @.params.message.parts[0].text =~ /abc\\\\/)]");
        jsonPath.Should().EndWith("/)]");
    }

    [Fact]
    public void EscapeMessagePattern_BareSlashAndControlChars_StillNeutralised()
    {
        // Existing behaviour is preserved: bare '/' is escaped, newline/CR converted, NUL stripped.
        A2aMockBuilder.EscapeMessagePattern("a/b").Should().Be("a\\/b");
        A2aMockBuilder.EscapeMessagePattern("a\nb").Should().Be("a\\nb");
        A2aMockBuilder.EscapeMessagePattern("a\rb").Should().Be("a\\rb");
        A2aMockBuilder.EscapeMessagePattern("a\0b").Should().Be("ab");
    }

    [Fact]
    public void A2a_TaskHandlerError_SetsFailedState()
    {
        var expectations = A2aMockBuilder.A2aMock()
            .OnTaskSend().MatchingMessage("boom").RespondingWith("kaboom", isError: true).And()
            .Build();

        var template = FindByJsonPathContaining(expectations, "boom")
            .GetProperty("httpResponseTemplate").GetProperty("template").GetString()!;
        template.Should().Contain("\"state\": \"failed\"");
        template.Should().Contain("kaboom");
    }

    [Fact]
    public void A2a_WithPushNotifications_EchoesConfigAndForwardsToWebhook()
    {
        var expectations = A2aMockBuilder.A2aMock()
            .WithPushNotifications("http://localhost:1234/a2a/callback")
            .Build();

        // agent card, config/set, push delivery (tasks/send), tasks/get, tasks/cancel.
        expectations.Should().HaveCount(5);

        FindAgentCard(expectations).GetProperty("httpResponse").GetProperty("body").GetString()!
            .Should().Contain("\"pushNotifications\": true");

        var config = FindByJsonRpcMethod(expectations, "tasks/pushNotificationConfig/set");
        config.GetProperty("httpResponseTemplate").GetProperty("template").GetString()!
            .Should().Contain("\"url\": \"http://localhost:1234/a2a/callback\"");

        // tasks/send becomes an override-forwarded-request targeting the webhook socket.
        var delivery = FindByJsonRpcMethod(expectations, "tasks/send");
        var forward = delivery.GetProperty("httpOverrideForwardedRequest");
        var webhook = forward.GetProperty("httpRequest");
        webhook.GetProperty("method").GetString().Should().Be("POST");
        webhook.GetProperty("path").GetString().Should().Be("/a2a/callback");
        var socket = webhook.GetProperty("socketAddress");
        socket.GetProperty("host").GetString().Should().Be("localhost");
        socket.GetProperty("port").GetInt32().Should().Be(1234);
        socket.GetProperty("scheme").GetString().Should().Be("HTTP");
        webhook.GetProperty("headers").GetProperty("Host")[0].GetString().Should().Be("localhost:1234");
        webhook.GetProperty("body").GetString()!.Should().Contain("\"jsonrpc\": \"2.0\"");
        // Literal webhook body: no Velocity escaping, plain JSON.
        webhook.GetProperty("body").GetString()!.Should().NotContain("${esc");

        // Caller still gets a JSON-RPC response template echoing the request id.
        forward.GetProperty("responseTemplate").GetProperty("template").GetString()!
            .Should().Contain("$!{request.jsonRpcRawId}");
    }

    [Fact]
    public void A2a_HttpsPushNotifications_UsesHttpsSchemeAndSecure()
    {
        var expectations = A2aMockBuilder.A2aMock()
            .WithPushNotifications("https://hooks.example.com/notify")
            .Build();

        var webhook = FindByJsonRpcMethod(expectations, "tasks/send")
            .GetProperty("httpOverrideForwardedRequest").GetProperty("httpRequest");
        webhook.GetProperty("secure").GetBoolean().Should().BeTrue();
        var socket = webhook.GetProperty("socketAddress");
        socket.GetProperty("scheme").GetString().Should().Be("HTTPS");
        socket.GetProperty("port").GetInt32().Should().Be(443);
        webhook.GetProperty("path").GetString().Should().Be("/notify");
    }

    [Fact]
    public void A2a_EscapesVelocityMetacharactersInTaskResponse()
    {
        var expectations = A2aMockBuilder.A2aMock()
            .WithDefaultTaskResponse("costs $5 #1")
            .Build();

        var template = FindByJsonRpcMethod(expectations, "tasks/send")
            .GetProperty("httpResponseTemplate").GetProperty("template").GetString()!;
        template.Should().Contain("costs ${esc.d}5 ${esc.h}1");
    }

    [Fact]
    public void A2a_EscapesBackslashBeforeVelocityMetacharacterInTaskResponse()
    {
        var expectations = A2aMockBuilder.A2aMock()
            .WithDefaultTaskResponse(@"a\#b")
            .Build();

        var template = FindByJsonRpcMethod(expectations, "tasks/send")
            .GetProperty("httpResponseTemplate").GetProperty("template").GetString()!;
        // JSON-escaped "a\\#b": both backslashes precede '#', so all are escaped
        template.Should().Contain("a${esc.b}${esc.b}${esc.h}b");
    }
}
