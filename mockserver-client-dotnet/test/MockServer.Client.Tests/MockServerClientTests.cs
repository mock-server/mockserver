using System.Net;
using System.Net.Http;
using System.Text;
using System.Text.Json;
using FluentAssertions;
using MockServer.Client.Exceptions;
using MockServer.Client.Models;
using Xunit;

namespace MockServer.Client.Tests;

/// <summary>
/// Unit tests for MockServerClient that use a fake HttpMessageHandler to avoid needing a real server.
/// </summary>
public class MockServerClientTests
{
    private static readonly JsonSerializerOptions JsonOptions = new()
    {
        DefaultIgnoreCondition = System.Text.Json.Serialization.JsonIgnoreCondition.WhenWritingNull,
        PropertyNamingPolicy = JsonNamingPolicy.CamelCase
    };

    /// <summary>
    /// A simple message handler that captures requests and returns pre-configured responses.
    /// </summary>
    private sealed class FakeHandler : HttpMessageHandler
    {
        public HttpRequestMessage? LastRequest { get; private set; }
        public string? LastRequestBody { get; private set; }
        public HttpStatusCode ResponseStatusCode { get; set; } = HttpStatusCode.OK;
        public string ResponseBody { get; set; } = "";

        protected override async Task<HttpResponseMessage> SendAsync(HttpRequestMessage request, CancellationToken cancellationToken)
        {
            LastRequest = request;
            if (request.Content != null)
                LastRequestBody = await request.Content.ReadAsStringAsync(cancellationToken);
            else
                LastRequestBody = null;

            return new HttpResponseMessage(ResponseStatusCode)
            {
                Content = new StringContent(ResponseBody, Encoding.UTF8, "application/json")
            };
        }
    }

    private static (MockServerClient Client, FakeHandler Handler) CreateClient()
    {
        var handler = new FakeHandler();
        var httpClient = new HttpClient(handler);
        var client = new MockServerClient("http://localhost:1080", httpClient);
        return (client, handler);
    }

    [Fact]
    public void Upsert_SendsCorrectRequest()
    {
        var (client, handler) = CreateClient();
        handler.ResponseStatusCode = HttpStatusCode.Created;
        handler.ResponseBody = "[]";

        var expectation = new Expectation
        {
            HttpRequest = HttpRequest.Request().WithPath("/test").Build(),
            HttpResponse = HttpResponse.Response().WithStatusCode(200).Build()
        };

        client.Upsert(expectation);

        handler.LastRequest!.Method.Should().Be(HttpMethod.Put);
        handler.LastRequest!.RequestUri!.PathAndQuery.Should().Be("/mockserver/expectation");
        handler.LastRequestBody.Should().Contain("/test");
    }

    [Fact]
    public void Upsert_ThrowsOnBadRequest()
    {
        var (client, handler) = CreateClient();
        handler.ResponseStatusCode = HttpStatusCode.BadRequest;
        handler.ResponseBody = "Invalid expectation format";

        var expectation = new Expectation
        {
            HttpRequest = HttpRequest.Request().WithPath("/test").Build()
        };

        var act = () => client.Upsert(expectation);
        act.Should().Throw<MockServerClientException>().WithMessage("*Invalid expectation*");
    }

    [Fact]
    public void When_Respond_CreatesExpectation()
    {
        var (client, handler) = CreateClient();
        handler.ResponseStatusCode = HttpStatusCode.Created;
        handler.ResponseBody = "[]";

        client.When(
            HttpRequest.Request().WithMethod("GET").WithPath("/hello")
        ).Respond(
            HttpResponse.Response().WithStatusCode(200).WithBody("world")
        );

        handler.LastRequest!.RequestUri!.PathAndQuery.Should().Be("/mockserver/expectation");
        handler.LastRequestBody.Should().Contain("\"method\":\"GET\"");
        handler.LastRequestBody.Should().Contain("\"path\":\"/hello\"");
        handler.LastRequestBody.Should().Contain("\"statusCode\":200");
        handler.LastRequestBody.Should().Contain("\"body\":\"world\"");
    }

    [Fact]
    public void When_Forward_CreatesExpectation()
    {
        var (client, handler) = CreateClient();
        handler.ResponseStatusCode = HttpStatusCode.Created;
        handler.ResponseBody = "[]";

        client.When(
            HttpRequest.Request().WithPath("/proxy")
        ).Forward(
            HttpForward.Forward().WithHost("backend.example.com").WithPort(443)
        );

        handler.LastRequestBody.Should().Contain("\"httpForward\"");
        handler.LastRequestBody.Should().Contain("\"host\":\"backend.example.com\"");
        handler.LastRequestBody.Should().Contain("\"port\":443");
    }

    [Fact]
    public void When_Error_CreatesExpectation()
    {
        var (client, handler) = CreateClient();
        handler.ResponseStatusCode = HttpStatusCode.Created;
        handler.ResponseBody = "[]";

        client.When(
            HttpRequest.Request().WithPath("/error")
        ).Error(
            HttpError.Error().WithDropConnection(true)
        );

        handler.LastRequestBody.Should().Contain("\"httpError\"");
        handler.LastRequestBody.Should().Contain("\"dropConnection\":true");
    }

    [Fact]
    public void Verify_SendsCorrectRequest()
    {
        var (client, handler) = CreateClient();
        handler.ResponseStatusCode = HttpStatusCode.Accepted;

        client.Verify(
            HttpRequest.Request().WithPath("/hello"),
            VerificationTimes.AtLeastTimes(1)
        );

        handler.LastRequest!.RequestUri!.PathAndQuery.Should().Be("/mockserver/verify");
        handler.LastRequestBody.Should().Contain("\"path\":\"/hello\"");
        handler.LastRequestBody.Should().Contain("\"atLeast\":1");
    }

    [Fact]
    public void Verify_ThrowsVerificationException_On406()
    {
        var (client, handler) = CreateClient();
        handler.ResponseStatusCode = HttpStatusCode.NotAcceptable;
        handler.ResponseBody = "Expected at least 1 but received 0";

        var act = () => client.Verify(
            HttpRequest.Request().WithPath("/hello"),
            VerificationTimes.AtLeastTimes(1)
        );

        act.Should().Throw<VerificationException>()
            .WithMessage("*Expected at least 1*");
    }

    [Fact]
    public void VerifySequence_SendsCorrectRequest()
    {
        var (client, handler) = CreateClient();
        handler.ResponseStatusCode = HttpStatusCode.Accepted;

        client.VerifySequence(
            HttpRequest.Request().WithPath("/first"),
            HttpRequest.Request().WithPath("/second")
        );

        handler.LastRequest!.RequestUri!.PathAndQuery.Should().Be("/mockserver/verifySequence");
        handler.LastRequestBody.Should().Contain("\"httpRequests\"");
        handler.LastRequestBody.Should().Contain("/first");
        handler.LastRequestBody.Should().Contain("/second");
    }

    [Fact]
    public void VerifySequence_ThrowsVerificationException_On406()
    {
        var (client, handler) = CreateClient();
        handler.ResponseStatusCode = HttpStatusCode.NotAcceptable;
        handler.ResponseBody = "Request sequence not found";

        var act = () => client.VerifySequence(
            HttpRequest.Request().WithPath("/first")
        );

        act.Should().Throw<VerificationException>();
    }

    // --- Response Verification Tests ---

    [Fact]
    public void Verify_WithResponse_SendsCorrectRequest()
    {
        var (client, handler) = CreateClient();
        handler.ResponseStatusCode = HttpStatusCode.Accepted;

        client.Verify(
            HttpRequest.Request().WithPath("/api"),
            HttpResponse.Response().WithStatusCode(200).Build(),
            VerificationTimes.AtLeastTimes(1)
        );

        handler.LastRequest!.RequestUri!.PathAndQuery.Should().Be("/mockserver/verify");
        handler.LastRequestBody.Should().Contain("\"httpRequest\"");
        handler.LastRequestBody.Should().Contain("\"path\":\"/api\"");
        handler.LastRequestBody.Should().Contain("\"httpResponse\"");
        handler.LastRequestBody.Should().Contain("\"statusCode\":200");
        handler.LastRequestBody.Should().Contain("\"atLeast\":1");
    }

    [Fact]
    public void Verify_ResponseOnly_OmitsHttpRequest()
    {
        var (client, handler) = CreateClient();
        handler.ResponseStatusCode = HttpStatusCode.Accepted;

        client.Verify(
            null,
            HttpResponse.Response().WithStatusCode(500).Build()
        );

        handler.LastRequest!.RequestUri!.PathAndQuery.Should().Be("/mockserver/verify");
        handler.LastRequestBody.Should().NotContain("\"httpRequest\"");
        handler.LastRequestBody.Should().Contain("\"httpResponse\"");
        handler.LastRequestBody.Should().Contain("\"statusCode\":500");
    }

    [Fact]
    public void Verify_WithResponse_ThrowsVerificationException_On406()
    {
        var (client, handler) = CreateClient();
        handler.ResponseStatusCode = HttpStatusCode.NotAcceptable;
        handler.ResponseBody = "Response not found at least 1 times";

        var act = () => client.Verify(
            HttpRequest.Request().WithPath("/missing"),
            HttpResponse.Response().WithStatusCode(200).Build(),
            VerificationTimes.AtLeastTimes(1)
        );

        act.Should().Throw<VerificationException>()
            .WithMessage("*Response not found*");
    }

    [Fact]
    public void Verify_WithResponseHeaders_SerializesCorrectly()
    {
        var (client, handler) = CreateClient();
        handler.ResponseStatusCode = HttpStatusCode.Accepted;

        client.Verify(
            HttpRequest.Request().WithPath("/api"),
            HttpResponse.Response()
                .WithStatusCode(200)
                .WithHeader("Content-Type", "application/json")
                .Build()
        );

        handler.LastRequestBody.Should().Contain("\"httpResponse\"");
        handler.LastRequestBody.Should().Contain("\"Content-Type\"");
        handler.LastRequestBody.Should().Contain("\"application/json\"");
    }

    [Fact]
    public void VerifySequence_WithResponses_SendsCorrectRequest()
    {
        var (client, handler) = CreateClient();
        handler.ResponseStatusCode = HttpStatusCode.Accepted;

        client.VerifySequence(
            new List<HttpRequest>
            {
                HttpRequest.Request().WithPath("/first"),
                HttpRequest.Request().WithPath("/second")
            },
            new List<HttpResponse>
            {
                HttpResponse.Response().WithStatusCode(200).Build(),
                HttpResponse.Response().WithStatusCode(201).Build()
            }
        );

        handler.LastRequest!.RequestUri!.PathAndQuery.Should().Be("/mockserver/verifySequence");
        handler.LastRequestBody.Should().Contain("\"httpRequests\"");
        handler.LastRequestBody.Should().Contain("/first");
        handler.LastRequestBody.Should().Contain("/second");
        handler.LastRequestBody.Should().Contain("\"httpResponses\"");
        handler.LastRequestBody.Should().Contain("\"statusCode\":200");
        handler.LastRequestBody.Should().Contain("\"statusCode\":201");
    }

    [Fact]
    public void VerifySequence_WithResponses_ThrowsVerificationException_On406()
    {
        var (client, handler) = CreateClient();
        handler.ResponseStatusCode = HttpStatusCode.NotAcceptable;
        handler.ResponseBody = "Response sequence not found";

        var act = () => client.VerifySequence(
            new List<HttpRequest> { HttpRequest.Request().WithPath("/first") },
            new List<HttpResponse> { HttpResponse.Response().WithStatusCode(200).Build() }
        );

        act.Should().Throw<VerificationException>();
    }

    [Fact]
    public void Clear_SendsCorrectRequest()
    {
        var (client, handler) = CreateClient();
        handler.ResponseStatusCode = HttpStatusCode.OK;

        client.Clear(HttpRequest.Request().WithPath("/hello"), "expectations");

        handler.LastRequest!.RequestUri!.PathAndQuery.Should().Contain("/mockserver/clear");
        handler.LastRequest!.RequestUri!.Query.Should().Contain("type=expectations");
        handler.LastRequestBody.Should().Contain("/hello");
    }

    [Fact]
    public void Clear_WithNoArgs_SendsEmptyBody()
    {
        var (client, handler) = CreateClient();
        handler.ResponseStatusCode = HttpStatusCode.OK;

        client.Clear();

        handler.LastRequest!.RequestUri!.PathAndQuery.Should().Be("/mockserver/clear");
        handler.LastRequestBody.Should().BeEmpty();
    }

    [Fact]
    public void ClearById_SendsCorrectRequest()
    {
        var (client, handler) = CreateClient();
        handler.ResponseStatusCode = HttpStatusCode.OK;

        client.ClearById("my-expectation-id");

        handler.LastRequest!.RequestUri!.PathAndQuery.Should().Be("/mockserver/clear");
        handler.LastRequestBody.Should().Contain("\"id\":\"my-expectation-id\"");
    }

    [Fact]
    public void Reset_SendsCorrectRequest()
    {
        var (client, handler) = CreateClient();
        handler.ResponseStatusCode = HttpStatusCode.OK;

        client.Reset();

        handler.LastRequest!.Method.Should().Be(HttpMethod.Put);
        handler.LastRequest!.RequestUri!.PathAndQuery.Should().Be("/mockserver/reset");
    }

    [Fact]
    public void RetrieveRecordedRequests_SendsCorrectRequest()
    {
        var (client, handler) = CreateClient();
        handler.ResponseStatusCode = HttpStatusCode.OK;
        handler.ResponseBody = "[{\"path\":\"/recorded\"}]";

        var results = client.RetrieveRecordedRequests(HttpRequest.Request().WithPath("/test"));

        handler.LastRequest!.RequestUri!.PathAndQuery.Should().Contain("type=requests");
        handler.LastRequest!.RequestUri!.PathAndQuery.Should().Contain("format=json");
        results.Should().HaveCount(1);
        results[0].Path.Should().Be("/recorded");
    }

    [Fact]
    public void RetrieveRecordedRequests_WithDnsFilter_ReturnsDnsRequests()
    {
        var (client, handler) = CreateClient();
        handler.ResponseStatusCode = HttpStatusCode.OK;
        handler.ResponseBody = "[{\"dnsName\":\"api.example.com\",\"dnsType\":\"A\",\"dnsClass\":\"IN\"}]";

        var results = client.RetrieveRecordedRequests(new HttpRequest { DnsName = "api.example.com", DnsType = "A" });

        handler.LastRequest!.RequestUri!.PathAndQuery.Should().Contain("type=requests");
        handler.LastRequestBody.Should().Contain("\"dnsName\":\"api.example.com\"");
        handler.LastRequestBody.Should().Contain("\"dnsType\":\"A\"");
        results.Should().HaveCount(1);
        results[0].DnsName.Should().Be("api.example.com");
        results[0].DnsType.Should().Be("A");
        results[0].DnsClass.Should().Be("IN");
    }

    [Fact]
    public void RetrieveActiveExpectations_SendsCorrectRequest()
    {
        var (client, handler) = CreateClient();
        handler.ResponseStatusCode = HttpStatusCode.OK;
        handler.ResponseBody = "[{\"httpRequest\":{\"path\":\"/active\"}}]";

        var results = client.RetrieveActiveExpectations();

        handler.LastRequest!.RequestUri!.PathAndQuery.Should().Contain("type=active_expectations");
        results.Should().HaveCount(1);
    }

    [Fact]
    public void RetrieveRecordedExpectations_SendsCorrectRequest()
    {
        var (client, handler) = CreateClient();
        handler.ResponseStatusCode = HttpStatusCode.OK;
        handler.ResponseBody = "[]";

        client.RetrieveRecordedExpectations();

        handler.LastRequest!.RequestUri!.PathAndQuery.Should().Contain("type=recorded_expectations");
    }

    [Fact]
    public void RetrieveActiveExpectationsById_SendsIdBody()
    {
        var (client, handler) = CreateClient();
        handler.ResponseStatusCode = HttpStatusCode.OK;
        handler.ResponseBody = "[{\"id\":\"my-expectation-id\",\"httpRequest\":{\"path\":\"/active\"}}]";

        var results = client.RetrieveActiveExpectationsById("my-expectation-id");

        handler.LastRequest!.RequestUri!.PathAndQuery.Should().Contain("type=active_expectations");
        handler.LastRequestBody.Should().Contain("\"id\":\"my-expectation-id\"");
        results.Should().HaveCount(1);
    }

    [Fact]
    public void RetrieveRecordedRequestsById_SendsIdBody()
    {
        var (client, handler) = CreateClient();
        handler.ResponseStatusCode = HttpStatusCode.OK;
        handler.ResponseBody = "[{\"path\":\"/recorded\"}]";

        var results = client.RetrieveRecordedRequestsById("my-expectation-id");

        handler.LastRequest!.RequestUri!.PathAndQuery.Should().Contain("type=requests");
        handler.LastRequestBody.Should().Contain("\"id\":\"my-expectation-id\"");
        results.Should().HaveCount(1);
        results[0].Path.Should().Be("/recorded");
    }

    [Fact]
    public void RetrieveRecordedExpectationsById_SendsIdBody()
    {
        var (client, handler) = CreateClient();
        handler.ResponseStatusCode = HttpStatusCode.OK;
        handler.ResponseBody = "[]";

        client.RetrieveRecordedExpectationsById("my-expectation-id");

        handler.LastRequest!.RequestUri!.PathAndQuery.Should().Contain("type=recorded_expectations");
        handler.LastRequestBody.Should().Contain("\"id\":\"my-expectation-id\"");
    }

    [Fact]
    public void RetrieveLogMessagesById_SendsIdBody()
    {
        var (client, handler) = CreateClient();
        handler.ResponseStatusCode = HttpStatusCode.OK;
        handler.ResponseBody = "[]";

        client.RetrieveLogMessagesById("my-expectation-id");

        handler.LastRequest!.RequestUri!.PathAndQuery.Should().Contain("type=logs");
        handler.LastRequestBody.Should().Contain("\"id\":\"my-expectation-id\"");
    }

    [Fact]
    public void RetrieveById_ThrowsForUnknownId()
    {
        var (client, handler) = CreateClient();
        handler.ResponseStatusCode = HttpStatusCode.BadRequest;
        handler.ResponseBody = "No expectation found with id missing";

        var act = () => client.RetrieveActiveExpectationsById("missing");

        act.Should().Throw<MockServerClientException>()
            .WithMessage("*No expectation found with id missing*");
    }

    [Fact]
    public void RetrieveExpectationsAsCode_SendsCorrectRequestAndReturnsCode()
    {
        var (client, handler) = CreateClient();
        handler.ResponseStatusCode = HttpStatusCode.OK;
        handler.ResponseBody = "new MockServerClient().When(request().WithPath(\"/code\"));";

        var code = client.RetrieveExpectationsAsCode("java");

        handler.LastRequest!.RequestUri!.PathAndQuery.Should().Contain("type=active_expectations");
        handler.LastRequest!.RequestUri!.PathAndQuery.Should().Contain("format=JAVA");
        code.Should().Contain("/code");
    }

    [Fact]
    public void RetrieveRecordedExpectationsAsCode_SendsCorrectRequest()
    {
        var (client, handler) = CreateClient();
        handler.ResponseStatusCode = HttpStatusCode.OK;
        handler.ResponseBody = "# generated python";

        var code = client.RetrieveRecordedExpectationsAsCode("python");

        handler.LastRequest!.RequestUri!.PathAndQuery.Should().Contain("type=recorded_expectations");
        handler.LastRequest!.RequestUri!.PathAndQuery.Should().Contain("format=PYTHON");
        code.Should().Be("# generated python");
    }

    [Fact]
    public void RetrieveLogMessages_SendsCorrectRequest()
    {
        var (client, handler) = CreateClient();
        handler.ResponseStatusCode = HttpStatusCode.OK;
        handler.ResponseBody = "[\"message 1\",\"message 2\"]";

        var results = client.RetrieveLogMessages();

        handler.LastRequest!.RequestUri!.PathAndQuery.Should().Contain("type=logs");
        results.Should().HaveCount(2);
        results[0].Should().Be("message 1");
    }

    [Fact]
    public void Status_SendsCorrectRequest()
    {
        var (client, handler) = CreateClient();
        handler.ResponseStatusCode = HttpStatusCode.OK;
        handler.ResponseBody = "{\"ports\":[1080,1081]}";

        var result = client.Status();

        handler.LastRequest!.RequestUri!.PathAndQuery.Should().Be("/mockserver/status");
        result.PortList.Should().BeEquivalentTo(new[] { 1080, 1081 });
    }

    [Fact]
    public void Bind_SendsCorrectRequest()
    {
        var (client, handler) = CreateClient();
        handler.ResponseStatusCode = HttpStatusCode.OK;
        handler.ResponseBody = "{\"ports\":[1080,9090]}";

        var result = client.Bind(9090);

        handler.LastRequest!.RequestUri!.PathAndQuery.Should().Be("/mockserver/bind");
        handler.LastRequestBody.Should().Contain("9090");
        result.PortList.Should().Contain(9090);
    }

    [Fact]
    public void Constructor_WithContextPath_BuildsCorrectUrl()
    {
        var handler = new FakeHandler { ResponseStatusCode = HttpStatusCode.OK };
        var httpClient = new HttpClient(handler);
        var client = new MockServerClient("http://localhost:1080/myapp", httpClient);

        client.Reset();

        handler.LastRequest!.RequestUri!.ToString().Should().Be("http://localhost:1080/myapp/mockserver/reset");
    }

    [Fact]
    public void When_WithTimesAndTtl_SerializesCorrectly()
    {
        var (client, handler) = CreateClient();
        handler.ResponseStatusCode = HttpStatusCode.Created;
        handler.ResponseBody = "[]";

        client.When(
            HttpRequest.Request().WithPath("/limited"),
            times: Times.Exactly(3),
            timeToLive: TimeToLive.ExactlyTtl(TimeUnit.SECONDS, 60)
        ).Respond(
            HttpResponse.Response().WithStatusCode(200)
        );

        handler.LastRequestBody.Should().Contain("\"remainingTimes\":3");
        handler.LastRequestBody.Should().Contain("\"unlimited\":false");
    }

    [Fact]
    public void When_WithId_SerializesCorrectly()
    {
        var (client, handler) = CreateClient();
        handler.ResponseStatusCode = HttpStatusCode.Created;
        handler.ResponseBody = "[]";

        client.When(
            HttpRequest.Request().WithPath("/named")
        ).WithId("my-expectation")
         .Respond(HttpResponse.Response().WithStatusCode(200));

        handler.LastRequestBody.Should().Contain("\"id\":\"my-expectation\"");
    }

    [Fact]
    public void When_WithPriority_SerializesCorrectly()
    {
        var (client, handler) = CreateClient();
        handler.ResponseStatusCode = HttpStatusCode.Created;
        handler.ResponseBody = "[]";

        client.When(
            HttpRequest.Request().WithPath("/priority")
        ).WithPriority(10)
         .Respond(HttpResponse.Response().WithStatusCode(200));

        handler.LastRequestBody.Should().Contain("\"priority\":10");
    }
}
