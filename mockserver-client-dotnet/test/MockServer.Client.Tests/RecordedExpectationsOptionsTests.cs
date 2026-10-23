using System.Net;
using System.Net.Http;
using System.Text;
using System.Text.Json.Nodes;
using FluentAssertions;
using MockServer.Client.Models;
using Xunit;

namespace MockServer.Client.Tests;

/// <summary>
/// Retrieving recorded expectations with <see cref="RecordedExpectationsOptions"/>: the exact query string sent, and
/// the rejections that must send nothing.
/// </summary>
public class RecordedExpectationsOptionsTests
{
    private const string RecordedPath = "/mockserver/retrieve?type=RECORDED_EXPECTATIONS&format=JSON";

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

    [Fact]
    public void SendsEveryOption_WithRequestMatcher_AndReturnsTheSequencedExpectation()
    {
        var (client, handler) = CreateClient();
        handler.ResponseBody = """
            [{"httpRequest":{"method":"GET","path":"/jobs/1"},
              "httpResponses":[{"statusCode":200,"body":"pending"},{"statusCode":200,"body":"done"}],
              "responseMode":"SWITCH","times":{"unlimited":true}}]
            """;

        var recorded = client.RetrieveRecordedExpectations(
            HttpRequest.Request().WithPath("/jobs/.*").Build(),
            RecordedExpectationsOptions.Options()
                .WithConsolidate()
                .WithParameterize()
                .WithRepeatsAsScenarios()
                .WithCaptureHeaders("X-Tenant", "Accept")
                .WithExtractBodiesLargerThan(1024));

        var request = handler.Requests.Single();
        request.Method.Should().Be(HttpMethod.Put);
        request.PathAndQuery.Should().Be(RecordedPath
            + "&consolidate=true&parameterize=true&repeatsAsScenarios=true&captureHeaders=X-Tenant%2CAccept&extractBodiesLargerThan=1024");
        JsonNode.Parse(request.Body)!["path"]!.GetValue<string>().Should().Be("/jobs/.*");
        recorded.Should().HaveCount(1);
        recorded[0].ResponseMode.Should().Be(ResponseMode.SWITCH);
        recorded[0].HttpResponses.Should().HaveCount(2);
    }

    [Theory]
    [InlineData("empty capture headers keeps none", "&captureHeaders=")]
    [InlineData("zero threshold", "&extractBodiesLargerThan=0")]
    [InlineData("false is not sent", "")]
    [InlineData("no options", "")]
    public async Task SendsTheQueryForEachOption(string name, string expectedQuery)
    {
        var (client, handler) = CreateClient();
        RecordedExpectationsOptions? options = name switch
        {
            "empty capture headers keeps none" => RecordedExpectationsOptions.Options().WithCaptureHeaders(),
            "zero threshold" => RecordedExpectationsOptions.Options().WithExtractBodiesLargerThan(0),
            "false is not sent" => RecordedExpectationsOptions.Options().WithRepeatsAsScenarios(false).WithConsolidate(false),
            _ => null
        };

        await client.RetrieveRecordedExpectationsAsync(null, options);

        handler.Requests.Single().PathAndQuery.Should().Be(RecordedPath + expectedQuery, name);
    }

    [Fact]
    public void ExistingRecordedRetrieveOverloads_AreUnchanged()
    {
        var (client, handler) = CreateClient();

        client.RetrieveRecordedExpectations();
        client.RetrieveRecordedExpectations(null, 0, 25);

        handler.Requests.Select(r => r.PathAndQuery).Should().Equal(
            "/mockserver/retrieve?type=recorded_expectations&format=json",
            RecordedPath + "&limit=25&offset=0");
    }

    [Fact]
    public void InvalidOptions_ThrowWhenSet()
    {
        var options = RecordedExpectationsOptions.Options();

        FluentActions.Invoking(() => options.WithCaptureHeaders("X-Tenant", " ")).Should().Throw<ArgumentException>();
        FluentActions.Invoking(() => options.WithCaptureHeaders("X-Tenant,Accept")).Should().Throw<ArgumentException>();
        FluentActions.Invoking(() => options.WithCaptureHeaders(null!)).Should().Throw<ArgumentNullException>();
        FluentActions.Invoking(() => options.WithCaptureHeaders("X-Tenant", null!)).Should().Throw<ArgumentException>();
        FluentActions.Invoking(() => options.WithExtractBodiesLargerThan(-1)).Should().Throw<ArgumentOutOfRangeException>();
        options.CaptureHeaders.Should().BeNull("a rejected value must not be kept");
        options.ExtractBodiesLargerThan.Should().BeNull("a rejected value must not be kept");
    }
}
