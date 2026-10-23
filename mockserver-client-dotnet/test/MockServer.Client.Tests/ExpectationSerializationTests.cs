using System.Text.Json;
using FluentAssertions;
using MockServer.Client.Models;
using Xunit;

namespace MockServer.Client.Tests;

public class ExpectationSerializationTests
{
    private static readonly JsonSerializerOptions JsonOptions = new()
    {
        DefaultIgnoreCondition = System.Text.Json.Serialization.JsonIgnoreCondition.WhenWritingNull,
        PropertyNamingPolicy = JsonNamingPolicy.CamelCase
    };

    [Fact]
    public void Expectation_FullShape_SerializesCorrectly()
    {
        var expectation = new Expectation
        {
            Id = "test-id",
            Priority = 5,
            HttpRequest = HttpRequest.Request()
                .WithMethod("GET")
                .WithPath("/hello")
                .WithQueryStringParameter("q", "x")
                .WithHeader("H", "v")
                .Build(),
            HttpResponse = HttpResponse.Response()
                .WithStatusCode(200)
                .WithHeader("Content-Type", "application/json")
                .WithBody("{\"k\":1}")
                .WithDelay(TimeUnit.MILLISECONDS, 0)
                .Build(),
            Times = Times.Exactly(1),
            TimeToLive = TimeToLive.UnlimitedTtl()
        };

        var json = JsonSerializer.Serialize(expectation, JsonOptions);
        var doc = JsonDocument.Parse(json);

        doc.RootElement.GetProperty("id").GetString().Should().Be("test-id");
        doc.RootElement.GetProperty("priority").GetInt32().Should().Be(5);

        var req = doc.RootElement.GetProperty("httpRequest");
        req.GetProperty("method").GetString().Should().Be("GET");
        req.GetProperty("path").GetString().Should().Be("/hello");
        req.GetProperty("queryStringParameters").GetProperty("q")[0].GetString().Should().Be("x");
        req.GetProperty("headers").GetProperty("H")[0].GetString().Should().Be("v");

        var resp = doc.RootElement.GetProperty("httpResponse");
        resp.GetProperty("statusCode").GetInt32().Should().Be(200);
        resp.GetProperty("headers").GetProperty("Content-Type")[0].GetString().Should().Be("application/json");
        resp.GetProperty("body").GetString().Should().Be("{\"k\":1}");

        var times = doc.RootElement.GetProperty("times");
        times.GetProperty("remainingTimes").GetInt32().Should().Be(1);
        times.GetProperty("unlimited").GetBoolean().Should().BeFalse();

        var ttl = doc.RootElement.GetProperty("timeToLive");
        ttl.GetProperty("unlimited").GetBoolean().Should().BeTrue();
    }

    [Fact]
    public void Expectation_WithForward_SerializesCorrectly()
    {
        var expectation = new Expectation
        {
            HttpRequest = HttpRequest.Request().WithPath("/proxy").Build(),
            HttpForward = HttpForward.Forward()
                .WithHost("backend.example.com")
                .WithPort(443)
                .WithScheme("HTTPS")
                .Build()
        };

        var json = JsonSerializer.Serialize(expectation, JsonOptions);
        var doc = JsonDocument.Parse(json);

        doc.RootElement.TryGetProperty("httpResponse", out _).Should().BeFalse();
        var fwd = doc.RootElement.GetProperty("httpForward");
        fwd.GetProperty("host").GetString().Should().Be("backend.example.com");
        fwd.GetProperty("port").GetInt32().Should().Be(443);
        fwd.GetProperty("scheme").GetString().Should().Be("HTTPS");
    }

    [Fact]
    public void Expectation_WithError_SerializesCorrectly()
    {
        var expectation = new Expectation
        {
            HttpRequest = HttpRequest.Request().WithPath("/error").Build(),
            HttpError = HttpError.Error().WithDropConnection(true).Build()
        };

        var json = JsonSerializer.Serialize(expectation, JsonOptions);
        var doc = JsonDocument.Parse(json);

        doc.RootElement.TryGetProperty("httpResponse", out _).Should().BeFalse();
        doc.RootElement.TryGetProperty("httpForward", out _).Should().BeFalse();
        var err = doc.RootElement.GetProperty("httpError");
        err.GetProperty("dropConnection").GetBoolean().Should().BeTrue();
    }

    [Fact]
    public void Expectation_WithResetConnectionError_RoundTrips()
    {
        var expectation = new Expectation
        {
            HttpRequest = HttpRequest.Request().WithPath("/reset").Build(),
            HttpError = HttpError.Error().WithResetConnection().Build()
        };

        var json = JsonSerializer.Serialize(expectation, JsonOptions);
        var err = JsonDocument.Parse(json).RootElement.GetProperty("httpError");

        err.GetProperty("resetConnection").GetBoolean().Should().BeTrue();
        err.TryGetProperty("dropConnection", out _).Should().BeFalse();
        JsonSerializer.Deserialize<Expectation>(json, JsonOptions)!.HttpError!.ResetConnection.Should().BeTrue();
    }

    [Fact]
    public void Expectation_Array_SerializesCorrectly()
    {
        var expectations = new[]
        {
            new Expectation
            {
                HttpRequest = HttpRequest.Request().WithPath("/one").Build(),
                HttpResponse = HttpResponse.Response().WithStatusCode(200).Build()
            },
            new Expectation
            {
                HttpRequest = HttpRequest.Request().WithPath("/two").Build(),
                HttpResponse = HttpResponse.Response().WithStatusCode(201).Build()
            }
        };

        var json = JsonSerializer.Serialize(expectations, JsonOptions);
        var doc = JsonDocument.Parse(json);

        doc.RootElement.GetArrayLength().Should().Be(2);
        doc.RootElement[0].GetProperty("httpRequest").GetProperty("path").GetString().Should().Be("/one");
        doc.RootElement[1].GetProperty("httpRequest").GetProperty("path").GetString().Should().Be("/two");
    }

    [Fact]
    public void Times_Helpers_Work()
    {
        var once = Times.Once();
        once.RemainingTimes.Should().Be(1);
        once.IsUnlimited.Should().BeFalse();

        var unlimited = Times.Unlimited();
        unlimited.IsUnlimited.Should().BeTrue();

        var exactly5 = Times.Exactly(5);
        exactly5.RemainingTimes.Should().Be(5);
        exactly5.IsUnlimited.Should().BeFalse();
    }

    [Fact]
    public void TimeToLive_Helpers_Work()
    {
        var unlimited = TimeToLive.UnlimitedTtl();
        unlimited.Unlimited.Should().BeTrue();

        var exactly = TimeToLive.ExactlyTtl(TimeUnit.SECONDS, 30);
        exactly.TimeUnit.Should().Be(TimeUnit.SECONDS);
        exactly.Value.Should().Be(30);
        exactly.Unlimited.Should().BeFalse();
    }

    [Fact]
    public void Expectation_WithResponseTemplate_SerializesCorrectly()
    {
        var expectation = new Expectation
        {
            HttpRequest = HttpRequest.Request().WithPath("/template").Build(),
            HttpResponseTemplate = HttpTemplate.OfType(TemplateType.VELOCITY)
                .WithTemplate("{ \"statusCode\": 200, \"body\": \"hello $!request.path\" }")
                .Build()
        };

        var json = JsonSerializer.Serialize(expectation, JsonOptions);
        var doc = JsonDocument.Parse(json);

        doc.RootElement.TryGetProperty("httpResponse", out _).Should().BeFalse();
        var tmpl = doc.RootElement.GetProperty("httpResponseTemplate");
        tmpl.GetProperty("templateType").GetString().Should().Be("VELOCITY");
        tmpl.GetProperty("template").GetString().Should().Contain("$!request.path");
        tmpl.TryGetProperty("templateFile", out _).Should().BeFalse();
    }

    [Fact]
    public void Expectation_WithResponseTemplate_TemplateFile_SerializesCorrectly()
    {
        var expectation = new Expectation
        {
            HttpRequest = HttpRequest.Request().WithPath("/template-file").Build(),
            HttpResponseTemplate = HttpTemplate.OfType(TemplateType.MUSTACHE)
                .WithTemplateFile("/templates/response.mustache")
                .Build()
        };

        var json = JsonSerializer.Serialize(expectation, JsonOptions);
        var doc = JsonDocument.Parse(json);

        var tmpl = doc.RootElement.GetProperty("httpResponseTemplate");
        tmpl.GetProperty("templateType").GetString().Should().Be("MUSTACHE");
        tmpl.GetProperty("templateFile").GetString().Should().Be("/templates/response.mustache");
        tmpl.TryGetProperty("template", out _).Should().BeFalse();
    }

    [Fact]
    public void Expectation_WithForwardTemplate_SerializesCorrectly()
    {
        var expectation = new Expectation
        {
            HttpRequest = HttpRequest.Request().WithPath("/forward-template").Build(),
            HttpForwardTemplate = HttpTemplate.OfType(TemplateType.JAVASCRIPT)
                .WithTemplate("return { 'httpRequest': { 'path': '/forwarded' } };")
                .Build()
        };

        var json = JsonSerializer.Serialize(expectation, JsonOptions);
        var doc = JsonDocument.Parse(json);

        doc.RootElement.TryGetProperty("httpForward", out _).Should().BeFalse();
        var tmpl = doc.RootElement.GetProperty("httpForwardTemplate");
        tmpl.GetProperty("templateType").GetString().Should().Be("JAVASCRIPT");
        tmpl.GetProperty("template").GetString().Should().Contain("/forwarded");
    }

    [Fact]
    public void HttpTemplate_RoundTrips_ThroughJson()
    {
        var original = HttpTemplate.OfType(TemplateType.VELOCITY)
            .WithTemplate("inline template")
            .WithTemplateFile("fallback.vm")
            .Build();

        var json = JsonSerializer.Serialize(original, JsonOptions);
        var deserialized = JsonSerializer.Deserialize<HttpTemplate>(json, JsonOptions);

        deserialized.Should().NotBeNull();
        deserialized!.TemplateType.Should().Be(TemplateType.VELOCITY);
        deserialized.Template.Should().Be("inline template");
        deserialized.TemplateFile.Should().Be("fallback.vm");
    }

    [Fact]
    public void HttpTemplate_Parameters_RoundTrip_ThroughJson()
    {
        var original = HttpTemplate.OfType(TemplateType.MUSTACHE)
            .WithTemplate("{{ parameters.greeting }}")
            .WithParameter("greeting", "hello")
            .WithParameter("names", new[] { "ann", "bob" })
            .WithParameters(new Dictionary<string, object?> { ["limits"] = new Dictionary<string, object?> { ["max"] = 5, ["none"] = null }, ["empty"] = "" })
            .Build();

        var json = JsonSerializer.Serialize(original, JsonOptions);
        var doc = JsonDocument.Parse(json);
        var parameters = doc.RootElement.GetProperty("parameters");
        parameters.GetProperty("greeting").GetString().Should().Be("hello");
        parameters.GetProperty("names")[1].GetString().Should().Be("bob");
        parameters.GetProperty("limits").GetProperty("max").GetInt32().Should().Be(5);
        parameters.GetProperty("limits").GetProperty("none").ValueKind.Should().Be(JsonValueKind.Null);
        parameters.GetProperty("empty").GetString().Should().Be("");

        var deserialized = JsonSerializer.Deserialize<HttpTemplate>(json, JsonOptions)!;
        JsonSerializer.Serialize(deserialized, JsonOptions).Should().Be(json);
    }

    [Fact]
    public void HttpTemplate_WithoutParameters_OmitsTheField()
    {
        var json = JsonSerializer.Serialize(HttpTemplate.OfType(TemplateType.VELOCITY).WithTemplate("x").Build(), JsonOptions);
        JsonDocument.Parse(json).RootElement.TryGetProperty("parameters", out _).Should().BeFalse();
    }

    [Fact]
    public void FileBody_WithTemplateType_SerializesCorrectly()
    {
        var expectation = new Expectation
        {
            HttpRequest = HttpRequest.Request().WithPath("/file").Build(),
            HttpResponse = HttpResponse.Response()
                .WithStatusCode(200)
                .WithFileBody("/data/response.vm", "application/json", FileTemplateType.VELOCITY)
                .Build()
        };

        var json = JsonSerializer.Serialize(expectation, JsonOptions);
        var doc = JsonDocument.Parse(json);

        var body = doc.RootElement.GetProperty("httpResponse").GetProperty("body");
        body.GetProperty("type").GetString().Should().Be("FILE");
        body.GetProperty("filePath").GetString().Should().Be("/data/response.vm");
        body.GetProperty("contentType").GetString().Should().Be("application/json");
        body.GetProperty("templateType").GetString().Should().Be("VELOCITY");
    }

    [Fact]
    public void FileBody_WithoutTemplateType_OmitsTemplateType()
    {
        var expectation = new Expectation
        {
            HttpRequest = HttpRequest.Request().WithPath("/file-plain").Build(),
            HttpResponse = HttpResponse.Response()
                .WithStatusCode(200)
                .WithFileBody("/data/response.json", "application/json")
                .Build()
        };

        var json = JsonSerializer.Serialize(expectation, JsonOptions);
        var doc = JsonDocument.Parse(json);

        var body = doc.RootElement.GetProperty("httpResponse").GetProperty("body");
        body.GetProperty("type").GetString().Should().Be("FILE");
        body.GetProperty("filePath").GetString().Should().Be("/data/response.json");
        body.GetProperty("contentType").GetString().Should().Be("application/json");
        body.TryGetProperty("templateType", out _).Should().BeFalse();
    }

    [Fact]
    public void FileBody_RoundTrips_ThroughJson()
    {
        var original = new FileBody
        {
            FilePath = "/templates/body.mustache",
            ContentType = "text/html",
            TemplateType = FileTemplateType.MUSTACHE
        };

        var json = JsonSerializer.Serialize(original, JsonOptions);
        var deserialized = JsonSerializer.Deserialize<FileBody>(json, JsonOptions);

        deserialized.Should().NotBeNull();
        deserialized!.Type.Should().Be("FILE");
        deserialized.FilePath.Should().Be("/templates/body.mustache");
        deserialized.ContentType.Should().Be("text/html");
        deserialized.TemplateType.Should().Be(FileTemplateType.MUSTACHE);
        deserialized.TemplatedFilePath.Should().BeFalse();
        json.Should().NotContain("templatedFilePath");
    }

    [Fact]
    public void FileBody_WithTemplatedFilePath_RoundTrips()
    {
        var response = HttpResponse.Response()
            .WithStatusCode(200)
            .WithTemplatedFileBody("responses/{{ request.pathParameters.name.0 }}.json", FileTemplateType.MUSTACHE, "application/json")
            .Build();

        var json = JsonSerializer.Serialize(response.Body, JsonOptions);
        var body = JsonDocument.Parse(json).RootElement;
        body.GetProperty("type").GetString().Should().Be("FILE");
        body.GetProperty("templateType").GetString().Should().Be("MUSTACHE");
        body.GetProperty("templatedFilePath").GetBoolean().Should().BeTrue();

        var deserialized = JsonSerializer.Deserialize<FileBody>(json, JsonOptions);
        deserialized!.TemplatedFilePath.Should().BeTrue();
        JsonSerializer.Serialize(deserialized, JsonOptions).Should().Be(json);
    }
}
