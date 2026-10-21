using System.Net;
using System.Net.Http;
using System.Text;
using System.Text.Json;
using System.Text.Json.Nodes;
using FluentAssertions;
using MockServer.Client.Models;
using Xunit;

namespace MockServer.Client.Tests;

/// <summary>
/// Expectation metadata, retrieve / clear by metadata and unmatched, and paging: the model round trip and the
/// exact query string sent, including the fail-closed rejections that must send nothing.
/// </summary>
public class ExpectationMetadataFilterTests
{
    private const string ClearPath = "/mockserver/clear?type=expectations";
    private const string ActivePath = "/mockserver/retrieve?type=ACTIVE_EXPECTATIONS&format=JSON";

    private static readonly JsonSerializerOptions JsonOptions = new()
    {
        DefaultIgnoreCondition = System.Text.Json.Serialization.JsonIgnoreCondition.WhenWritingNull,
        PropertyNamingPolicy = JsonNamingPolicy.CamelCase,
        Converters = { new HttpRequestConverter() }
    };

    private sealed class RecordingHandler : HttpMessageHandler
    {
        public List<(HttpMethod Method, string PathAndQuery, string Body)> Requests { get; } = new();
        public string ResponseBody { get; set; } = "[]";

        protected override async Task<HttpResponseMessage> SendAsync(HttpRequestMessage request, CancellationToken cancellationToken)
        {
            var body = request.Content != null ? await request.Content.ReadAsStringAsync(cancellationToken) : "";
            Requests.Add((request.Method, request.RequestUri!.PathAndQuery, body));
            return new HttpResponseMessage(HttpStatusCode.OK)
            {
                Content = new StringContent(ResponseBody, Encoding.UTF8, "application/json")
            };
        }
    }

    private static (MockServerClient Client, RecordingHandler Handler) CreateClient()
    {
        var handler = new RecordingHandler();
        return (new MockServerClient("http://localhost:1080", new HttpClient(handler)), handler);
    }

    private static string FixtureJson =>
        File.ReadAllText(Path.Combine(AppContext.BaseDirectory, "fixtures", "top_metadata.json"));

    // ---------------------------------------------------------------- model

    [Fact]
    public void Metadata_SharedFixture_RoundTripsExactly()
    {
        var expectation = JsonSerializer.Deserialize<Expectation>(FixtureJson, JsonOptions)!;
        var reserialized = JsonSerializer.Serialize(expectation, JsonOptions);

        JsonNode.DeepEquals(JsonNode.Parse(FixtureJson), JsonNode.Parse(reserialized))
            .Should().BeTrue($"metadata must round-trip exactly; got {reserialized}");
        var metadata = JsonNode.Parse(reserialized)!["metadata"]!.AsObject();
        metadata.ContainsKey("note").Should().BeTrue("a JSON null value must be kept, not dropped");
        metadata["note"].Should().BeNull();
        metadata["ticket"]!.GetValueKind().Should().Be(JsonValueKind.Number);
        metadata["ticket"]!.ToJsonString().Should().Be("4211");
        metadata["ratio"]!.ToJsonString().Should().Be("0.5");
        metadata["flaky"]!.GetValueKind().Should().Be(JsonValueKind.False);
    }

    [Fact]
    public void Metadata_OmittedWhenNotSet()
    {
        var json = JsonSerializer.Serialize(new Expectation { Id = "x" }, JsonOptions);
        json.Should().NotContain("metadata");
    }

    [Fact]
    public void Metadata_FluentBuilder_IsSentOnUpsert()
    {
        var (client, handler) = CreateClient();
        client.When(HttpRequest.Request().WithPath("/a").Build())
            .WithMetadata("owner", "team-a")
            .WithMetadata("ticket", 42)
            .WithMetadata("note", null)
            .WithMetadata("tags", new[] { "smoke" })
            .Respond(HttpResponse.Response().WithStatusCode(200).Build());

        var sent = JsonNode.Parse(handler.Requests.Single().Body)!.AsArray()[0]!["metadata"]!.AsObject();
        JsonNode.DeepEquals(sent, JsonNode.Parse("{\"owner\":\"team-a\",\"ticket\":42,\"note\":null,\"tags\":[\"smoke\"]}"))
            .Should().BeTrue($"got {sent.ToJsonString()}");
    }

    [Fact]
    public void RetrieveActiveExpectationsByMetadata_ReturnsTypedMetadata()
    {
        var (client, handler) = CreateClient();
        handler.ResponseBody = "[" + FixtureJson + "]";

        var result = client.RetrieveActiveExpectationsByMetadata("owner", "team-a");

        result.Should().HaveCount(1);
        result[0].Metadata!["owner"].Should().BeOfType<JsonElement>()
            .Which.GetString().Should().Be("team-a");
        result[0].Metadata!["note"].Should().BeNull();
        ((JsonElement)result[0].Metadata!["ticket"]!).GetInt32().Should().Be(4211);
    }

    // ---------------------------------------------------------------- wire: value encoding

    [Theory]
    [InlineData("!team-a", "%21team-a")]
    [InlineData("?x", "%3Fx")]
    [InlineData("100%", "100%25")]
    [InlineData("a+b", "a%2Bb")]
    [InlineData("a b", "a%20b")]
    [InlineData("a&b=c#d", "a%26b%3Dc%23d")]
    public void ClearByMetadata_SendsValueLiterallyEncoded(string value, string encoded)
    {
        var (client, handler) = CreateClient();

        client.ClearByMetadata("owner", value);

        var request = handler.Requests.Single();
        request.Method.Should().Be(HttpMethod.Put);
        request.PathAndQuery.Should().Be(ClearPath + "&metadataKey=owner&metadataValue=" + encoded);
        request.Body.Should().BeEmpty();
    }

    [Theory]
    [InlineData("!team-a", "%21team-a")]
    [InlineData("?x", "%3Fx")]
    [InlineData("100%", "100%25")]
    [InlineData("a+b", "a%2Bb")]
    [InlineData("a b", "a%20b")]
    public async Task RetrieveActiveExpectationsByMetadataAsync_SendsValueLiterallyEncoded(string value, string encoded)
    {
        var (client, handler) = CreateClient();

        await client.RetrieveActiveExpectationsByMetadataAsync("owner", value);

        handler.Requests.Single().PathAndQuery.Should().Be(ActivePath + "&metadataKey=owner&metadataValue=" + encoded);
    }

    [Fact]
    public void MetadataKey_IsEncodedToo()
    {
        var (client, handler) = CreateClient();

        client.ClearByMetadataKey("!k?&");

        handler.Requests.Single().PathAndQuery.Should().Be(ClearPath + "&metadataKey=%21k%3F%26");
    }

    [Fact]
    public void NonStringValues_AreSentAsCompactJson()
    {
        var (client, handler) = CreateClient();

        client.ClearByMetadata("k", 42);
        client.ClearByMetadata("k", true);
        client.ClearByMetadata("k", new List<string> { "a", "b" });
        client.ClearByMetadata("k", new Dictionary<string, object> { ["x"] = 1 });
        client.ClearByMetadata("k", ExpectationFilter.JsonNull);
        client.ClearByMetadata("k", JsonDocument.Parse("{ \"y\" : [ 1 , 2 ] }").RootElement);

        handler.Requests.Select(r => r.PathAndQuery).Should().Equal(
            ClearPath + "&metadataKey=k&metadataValue=42",
            ClearPath + "&metadataKey=k&metadataValue=true",
            ClearPath + "&metadataKey=k&metadataValue=%5B%22a%22%2C%22b%22%5D",
            ClearPath + "&metadataKey=k&metadataValue=%7B%22x%22%3A1%7D",
            ClearPath + "&metadataKey=k&metadataValue=null",
            ClearPath + "&metadataKey=k&metadataValue=" + Uri.EscapeDataString("{ \"y\" : [ 1 , 2 ] }"));
    }

    // ---------------------------------------------------------------- wire: other params

    [Fact]
    public void KeyOnly_CarriesNoValue()
    {
        var (client, handler) = CreateClient();

        client.RetrieveActiveExpectationsByMetadataKey("owner");
        client.ClearByMetadataKey("owner");
        client.RetrieveActiveExpectations(null, ExpectationFilter.Filter().WithMetadata("owner", "a").WithMetadataKey("owner"));

        handler.Requests.Select(r => r.PathAndQuery).Should().Equal(
            ActivePath + "&metadataKey=owner",
            ClearPath + "&metadataKey=owner",
            ActivePath + "&metadataKey=owner");
    }

    [Fact]
    public void Unmatched_SendsUnmatchedTrue()
    {
        var (client, handler) = CreateClient();

        client.RetrieveUnmatchedExpectations();
        client.ClearUnmatchedExpectations();

        handler.Requests.Select(r => r.PathAndQuery).Should().Equal(
            ActivePath + "&unmatched=true",
            ClearPath + "&unmatched=true");
    }

    [Fact]
    public void JsonPath_IsEncodedLiterally()
    {
        var (client, handler) = CreateClient();
        var jsonPath = "$[?(@.owner == 'team-a')]";

        client.ClearByFilter(null, ExpectationFilter.Filter().WithMetadataJsonPath(jsonPath));

        handler.Requests.Single().PathAndQuery.Should()
            .Be(ClearPath + "&metadataJsonPath=%24%5B%3F%28%40.owner%20%3D%3D%20%27team-a%27%29%5D");
    }

    [Fact]
    public void RetrieveActive_WithFilterAndPaging_SendsAllParamsAndRequestBody()
    {
        var (client, handler) = CreateClient();

        client.RetrieveActiveExpectations(
            HttpRequest.Request().WithPath("/orders").Build(),
            ExpectationFilter.Filter().WithMetadata("owner", "team-a").WithMetadataJsonPath("$.tags").WithUnmatched()
                .WithOffset(5).WithLimit(10));

        var request = handler.Requests.Single();
        request.PathAndQuery.Should().Be(ActivePath +
            "&metadataKey=owner&metadataValue=team-a&metadataJsonPath=%24.tags&unmatched=true&limit=10&offset=5");
        JsonNode.Parse(request.Body)!["path"]!.GetValue<string>().Should().Be("/orders");
    }

    [Fact]
    public void RetrieveActive_WithNullFilter_SendsNoFilterParams()
    {
        var (client, handler) = CreateClient();

        client.RetrieveActiveExpectations(null, null);

        handler.Requests.Single().PathAndQuery.Should().Be(ActivePath);
    }

    [Fact]
    public void RetrieveRecordedExpectations_Page_SendsOffsetAndLimit()
    {
        var (client, handler) = CreateClient();
        handler.ResponseBody = "[" + FixtureJson + "]";

        var page = client.RetrieveRecordedExpectations(null, 0, 25);

        page.Should().HaveCount(1);
        handler.Requests.Single().PathAndQuery.Should()
            .Be("/mockserver/retrieve?type=RECORDED_EXPECTATIONS&format=JSON&limit=25&offset=0");
    }

    [Fact]
    public async Task ClearByFilterAsync_SendsRequestMatcherBody()
    {
        var (client, handler) = CreateClient();

        await client.ClearByFilterAsync(HttpRequest.Request().WithPath("/x").Build(),
            ExpectationFilter.Filter().WithMetadataKey("owner"));

        var request = handler.Requests.Single();
        request.PathAndQuery.Should().Be(ClearPath + "&metadataKey=owner");
        JsonNode.Parse(request.Body)!["path"]!.GetValue<string>().Should().Be("/x");
    }

    [Fact]
    public void ExistingRetrieveAndClear_AreUnchanged()
    {
        var (client, handler) = CreateClient();

        client.RetrieveActiveExpectations();
        client.Clear(null, "expectations");

        handler.Requests.Select(r => r.PathAndQuery).Should().Equal(
            "/mockserver/retrieve?type=active_expectations&format=json",
            "/mockserver/clear?type=expectations");
    }

    // ---------------------------------------------------------------- fail closed

    public static IEnumerable<object[]> Rejections()
    {
        yield return Case<ArgumentException>("blank key (clear)", c => c.ClearByMetadata(" ", "v"));
        yield return Case<ArgumentException>("null key (clear)", c => c.ClearByMetadata(null!, "v"));
        yield return Case<ArgumentException>("empty key-only (clear)", c => c.ClearByMetadataKey(""));
        yield return Case<ArgumentException>("blank key (retrieve)", c => c.RetrieveActiveExpectationsByMetadata("", "v"));
        yield return Case<ArgumentException>("null key-only (retrieve)", c => c.RetrieveActiveExpectationsByMetadataKey(null!));
        yield return Case<ArgumentNullException>("null value (clear)", c => c.ClearByMetadata("k", null!));
        yield return Case<ArgumentNullException>("null value (retrieve)", c => c.RetrieveActiveExpectationsByMetadata("k", null!));
        yield return Case<ArgumentException>("null JSONPath",
            c => c.ClearByFilter(null, ExpectationFilter.Filter().WithMetadataJsonPath(null!)));
        yield return Case<ArgumentException>("blank JSONPath",
            c => c.RetrieveActiveExpectations(null, ExpectationFilter.Filter().WithMetadataJsonPath("  ")));
        yield return Case<ArgumentException>("clear with null filter", c => c.ClearByFilter(null, null!));
        yield return Case<ArgumentException>("clear with empty filter", c => c.ClearByFilter(null, ExpectationFilter.Filter()));
        yield return Case<ArgumentException>("clear with unmatched=false only",
            c => c.ClearByFilter(null, ExpectationFilter.Filter().WithUnmatched(false)));
        yield return Case<ArgumentException>("clear with paging only",
            c => c.ClearByFilter(null, ExpectationFilter.Filter().WithLimit(1)));
        yield return Case<ArgumentException>("clear with selection and paging",
            c => c.ClearByFilter(null, ExpectationFilter.Filter().WithMetadataKey("k").WithOffset(0)));
        yield return Case<ArgumentOutOfRangeException>("negative offset",
            c => c.RetrieveActiveExpectations(null, ExpectationFilter.Filter().WithOffset(-1)));
        yield return Case<ArgumentOutOfRangeException>("negative limit",
            c => c.RetrieveActiveExpectations(null, ExpectationFilter.Filter().WithLimit(-1)));
        yield return Case<ArgumentOutOfRangeException>("negative recorded offset", c => c.RetrieveRecordedExpectations(null, -1, 5));
        yield return Case<ArgumentOutOfRangeException>("negative recorded limit", c => c.RetrieveRecordedExpectations(null, 0, -5));
    }

    private static object[] Case<TException>(string name, Action<MockServerClient> act) where TException : Exception
        => new object[] { name, typeof(TException), act };

    [Theory]
    [MemberData(nameof(Rejections))]
    public void InvalidSelection_ThrowsAndSendsNothing(string name, Type expected, Action<MockServerClient> act)
    {
        var (client, handler) = CreateClient();

        var thrown = Record.Exception(() => act(client));

        thrown.Should().NotBeNull(name);
        thrown!.GetType().Should().Be(expected, name);
        handler.Requests.Should().BeEmpty(name + " must send nothing");
    }

    [Fact]
    public async Task InvalidClear_Async_ThrowsAndSendsNothing()
    {
        var (client, handler) = CreateClient();

        await FluentActions.Awaiting(() => client.ClearByFilterAsync(null, ExpectationFilter.Filter()))
            .Should().ThrowAsync<ArgumentException>();
        await FluentActions.Awaiting(() => client.ClearByMetadataAsync("k", null!))
            .Should().ThrowAsync<ArgumentNullException>();

        handler.Requests.Should().BeEmpty();
    }
}
