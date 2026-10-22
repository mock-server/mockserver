using System.Net.Http;
using System.Text;
using System.Text.Json;
using System.Text.Json.Nodes;
using FluentAssertions;
using MockServer.Client.Models;
using Xunit;

namespace MockServer.Client.Tests;

/// <summary>
/// The anyOf (logical OR) request matcher carried on <see cref="HttpRequest.AnyOf"/>: wire form, round-trip
/// through every branch kind, and the diagnostics that name the closest branch.
/// </summary>
public class AnyOfRequestMatcherTests
{
    private sealed class RecordingHandler : HttpMessageHandler
    {
        public string? LastBody { get; private set; }
        public Func<string, string> ResponseFor { get; set; } = _ => "{}";

        protected override async Task<HttpResponseMessage> SendAsync(HttpRequestMessage request, CancellationToken cancellationToken)
        {
            LastBody = request.Content != null ? await request.Content.ReadAsStringAsync(cancellationToken) : null;
            return new HttpResponseMessage(System.Net.HttpStatusCode.OK)
            {
                Content = new StringContent(ResponseFor(request.RequestUri!.AbsolutePath), Encoding.UTF8, "application/json")
            };
        }
    }

    private static readonly JsonSerializerOptions ClientOptions = new()
    {
        DefaultIgnoreCondition = System.Text.Json.Serialization.JsonIgnoreCondition.WhenWritingNull,
        PropertyNamingPolicy = JsonNamingPolicy.CamelCase,
        Converters = { new HttpRequestConverter() }
    };

    private static (MockServerClient Client, RecordingHandler Handler) CreateClient()
    {
        var handler = new RecordingHandler();
        return (new MockServerClient("http://localhost:1080", new HttpClient(handler)), handler);
    }

    private static void AssertJsonEqual(string actual, string expected)
        => JsonNode.DeepEquals(JsonNode.Parse(actual), JsonNode.Parse(expected))
            .Should().BeTrue($"expected {expected}\nbut got {actual}");

    [Fact]
    public void FactoryAndBuilder_WriteTheSameAnyOfArray()
    {
        const string expected = """{"anyOf":[{"path":"/a"},{"method":"POST","path":"/b"}]}""";

        AssertJsonEqual(JsonSerializer.Serialize(HttpRequest.RequestAnyOf(
            HttpRequest.Request().WithPath("/a").Build(),
            HttpRequest.Request().WithMethod("POST").WithPath("/b").Build()), ClientOptions), expected);
        AssertJsonEqual(JsonSerializer.Serialize(HttpRequest.Request()
            .WithAnyOf(HttpRequest.Request().WithPath("/a").Build())
            .WithAnyOf(HttpRequest.Request().WithMethod("POST").WithPath("/b").Build())
            .Build(), ClientOptions), expected);
    }

    [Fact]
    public void EmptyAnyOf_IsSentForTheServerToReject_NotDroppedToMatchAll()
    {
        // {} would match every request; "anyOf": [] is rejected by the server
        AssertJsonEqual(JsonSerializer.Serialize(HttpRequest.RequestAnyOf(), ClientOptions), """{"anyOf":[]}""");
        AssertJsonEqual(JsonSerializer.Serialize(HttpRequest.Request().WithAnyOf().Build(), ClientOptions), """{"anyOf":[]}""");
        AssertJsonEqual(JsonSerializer.Serialize(new Expectation { HttpRequest = HttpRequest.RequestAnyOf() }, ClientOptions),
            """{"httpRequest":{"anyOf":[]}}""");
        JsonSerializer.Deserialize<HttpRequest>("""{"anyOf":[]}""", ClientOptions)!.AnyOf.Should().BeEmpty().And.NotBeNull();
    }

    [Fact]
    public void PlainRequest_HasNoAnyOf()
    {
        JsonSerializer.Serialize(HttpRequest.Request().WithPath("/x").Build(), ClientOptions).Should().NotContain("anyOf");
    }

    [Fact]
    public void AnyOf_JsonToTypedToJson_IsIdentical()
    {
        const string json = """
            {
              "httpRequest": {
                "not": true,
                "anyOf": [
                  { "method": "GET", "path": "/pets", "headers": { "X-Flag": [ { "not": false, "value": "!on" } ] } },
                  { "specUrlOrPayload": "https://example.com/spec.json", "operationId": "listPets" },
                  { "if": { "method": "POST" }, "then": { "path": "/orders" } },
                  { "not": true, "anyOf": [ { "path": "/admin/.*" }, { "path": "/internal/.*" } ] }
                ]
              },
              "httpResponse": { "statusCode": 200 }
            }
            """;

        var expectation = JsonSerializer.Deserialize<Expectation>(json, ClientOptions)!;
        var request = expectation.HttpRequest!;

        request.Not.Should().BeTrue();
        request.AnyOf.Should().HaveCount(4);
        request.AnyOf![0].HeaderMatchers!["X-Flag"].Should().ContainSingle();
        request.AnyOf[1].OperationId.Should().Be("listPets");
        request.AnyOf[2].If!.Method.Should().Be("POST");
        request.AnyOf[3].AnyOf![1].Path.Should().Be("/internal/.*");

        AssertJsonEqual(JsonSerializer.Serialize(expectation, ClientOptions), json);
    }

    [Fact]
    public void AnyOf_ReadFromServerUpsertResponse()
    {
        var (client, handler) = CreateClient();
        handler.ResponseFor = _ =>
            """[{"id":"a1","httpRequest":{"anyOf":[{"path":"/a"},{"path":"/b"}]},"httpResponse":{"statusCode":200}}]""";

        var created = client.When(HttpRequest.RequestAnyOf(HttpRequest.Request().WithPath("/a").Build(), HttpRequest.Request().WithPath("/b").Build()))
            .Respond(HttpResponse.Response().WithStatusCode(200));

        AssertJsonEqual(JsonNode.Parse(handler.LastBody!)!.AsArray()[0]!["httpRequest"]!.ToJsonString(),
            """{"anyOf":[{"path":"/a"},{"path":"/b"}]}""");
        created.Should().ContainSingle();
        created[0].HttpRequest!.AnyOf!.Select(branch => branch.Path).Should().Equal("/a", "/b");
    }

    [Fact]
    public void DebugMismatch_ReadsTheClosestAnyOfBranch()
    {
        var (client, handler) = CreateClient();
        handler.ResponseFor = _ => """
            {
              "results" : [ {
                "expectationId" : "any-of", "matches" : false,
                "differences" : { "headers" : [ "header did not match" ] },
                "anyOf" : { "closestBranch" : 1, "branches" : [
                  { "branch" : 0, "matches" : false, "differingFieldCount" : 2, "differingFields" : [ "method", "path" ] },
                  { "branch" : 1, "matches" : false, "differingFieldCount" : 1, "differingFields" : [ "headers" ] } ] }
              }, { "expectationId" : "plain", "matches" : true } ]
            }
            """;

        var result = client.DebugMismatch(HttpRequest.Request().WithPath("/orders"));

        var anyOf = result.Results[0].AnyOf!;
        anyOf.ClosestBranch.Should().Be(1);
        anyOf.Branches.Should().HaveCount(2);
        anyOf.Branches[1].DifferingFields.Should().Equal("headers");
        result.Results[1].AnyOf.Should().BeNull();
    }
}
