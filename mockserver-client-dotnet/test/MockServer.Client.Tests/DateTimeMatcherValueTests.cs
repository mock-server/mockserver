using System.Text.Json;
using FluentAssertions;
using MockServer.Client.Models;
using Xunit;

namespace MockServer.Client.Tests;

/// <summary>
/// Unit tests for the date-time object form of a <see cref="MatcherValue"/>
/// (<c>{"after":"now -1 hours","zone":"UTC"}</c>) on header, query-string parameter, cookie and path
/// parameter values: the builders, the exact wire shape, the read half, and that a plain string such
/// as <c>"before:2026-01-01"</c> is never mistaken for a date-time matcher.
/// </summary>
public class DateTimeMatcherValueTests
{
    private static readonly JsonSerializerOptions JsonOptions = new()
    {
        DefaultIgnoreCondition = System.Text.Json.Serialization.JsonIgnoreCondition.WhenWritingNull,
        PropertyNamingPolicy = JsonNamingPolicy.CamelCase,
        // keeps '+' (as in "now +1 days") unescaped so the expected wire text reads as written
        Encoder = System.Text.Encodings.Web.JavaScriptEncoder.UnsafeRelaxedJsonEscaping,
        Converters = { new HttpRequestConverter() }
    };

    public static IEnumerable<object[]> BuilderCases()
    {
        yield return new object[]
        {
            MatcherValue.After("now -1 hours"),
            "{\"after\":\"now -1 hours\"}"
        };
        yield return new object[]
        {
            MatcherValue.Before("2026-10-21T00:00:00Z").WithActualFormat("rfc1123"),
            "{\"before\":\"2026-10-21T00:00:00Z\",\"actualFormat\":\"rfc1123\"}"
        };
        yield return new object[]
        {
            MatcherValue.EqualToDateTime("now +1 days")
                .WithNot(true)
                .WithTruncateExpected("first day of month")
                .WithTruncateActual("startOfDay")
                .WithApplyTruncationLast(true)
                .WithZone("Europe/London"),
            "{\"not\":true,\"equalToDateTime\":\"now +1 days\",\"truncateExpected\":\"first day of month\"," +
            "\"truncateActual\":\"startOfDay\",\"applyTruncationLast\":true,\"zone\":\"Europe/London\"}"
        };
        yield return new object[]
        {
            MatcherValue.After("2026-01-01").WithApplyTruncationLast(false).WithNot(false),
            "{\"after\":\"2026-01-01\",\"applyTruncationLast\":false}"
        };
    }

    [Theory]
    [MemberData(nameof(BuilderCases))]
    public void BuilderWritesOnlySetFieldsInServerOrder(MatcherValue matcher, string want)
    {
        JsonSerializer.Serialize(matcher, JsonOptions).Should().Be(want);
    }

    [Theory]
    [MemberData(nameof(BuilderCases))]
    public void BuilderOutputRoundTripsThroughJsonToAnEqualModel(MatcherValue matcher, string wire)
    {
        var decoded = JsonSerializer.Deserialize<MatcherValue>(wire);

        decoded.Should().Be(matcher);
        decoded.IsDateTime.Should().BeTrue();
        JsonSerializer.Serialize(decoded, JsonOptions).Should().Be(wire);
    }

    [Fact]
    public void DecodedFieldsAreExposedOnTheModel()
    {
        var matcher = JsonSerializer.Deserialize<MatcherValue>(
            "{\"not\":true,\"equalToDateTime\":\"now +1 days\",\"actualFormat\":\"dd/MM/yyyy\"," +
            "\"truncateExpected\":\"first day of month\",\"truncateActual\":\"startOfDay\"," +
            "\"applyTruncationLast\":true,\"zone\":\"Europe/London\"}");

        matcher.DateTimeOperator.Should().Be(DateTimeMatchOperator.EqualToDateTime);
        matcher.DateTimeExpected.Should().Be("now +1 days");
        matcher.Not.Should().BeTrue();
        matcher.ActualFormat.Should().Be("dd/MM/yyyy");
        matcher.TruncateExpected.Should().Be("first day of month");
        matcher.TruncateActual.Should().Be("startOfDay");
        matcher.ApplyTruncationLast.Should().BeTrue();
        matcher.Zone.Should().Be("Europe/London");
        matcher.Optional.Should().BeFalse();
    }

    [Fact]
    public void OptionMethodsReturnANewValueAndLeaveTheOriginalUnchanged()
    {
        var original = MatcherValue.After("now");
        var refined = original.WithZone("UTC").WithNot(true);

        original.Zone.Should().BeNull();
        original.Not.Should().BeFalse();
        refined.Zone.Should().Be("UTC");
        refined.Not.Should().BeTrue();
        refined.Should().NotBe(original);
    }

    [Fact]
    public void DateTimeOptionOnANonDateTimeValueIsRejected()
    {
        var act = () => MatcherValue.Literal("x").WithZone("UTC");
        act.Should().Throw<InvalidOperationException>();
    }

    [Fact]
    public void PlainBeforeStringStaysAPlainLiteral()
    {
        var matcher = JsonSerializer.Deserialize<MatcherValue>("\"before:2026-01-01\"");

        matcher.IsDateTime.Should().BeFalse();
        matcher.Value.Should().Be("before:2026-01-01");
        JsonSerializer.Serialize(matcher).Should().Be("\"before:2026-01-01\"");
    }

    [Fact]
    public void PlainBeforeHeaderStaysInThePlainMapByteIdentically()
    {
        const string wire =
            "{\"httpRequest\":{\"path\":\"/x\",\"headers\":{\"X-Plain\":[\"before:2026-01-01\"]}}," +
            "\"httpResponse\":{\"statusCode\":200}}";

        var expectation = JsonSerializer.Deserialize<Expectation>(wire, JsonOptions)!;

        expectation.HttpRequest!.HeaderMatchers.Should().BeNull();
        expectation.HttpRequest.Headers!["X-Plain"].Should().Equal("before:2026-01-01");
        JsonDocument.Parse(JsonSerializer.Serialize(expectation, JsonOptions)).RootElement
            .GetProperty("httpRequest").GetProperty("headers").GetProperty("X-Plain")[0]
            .GetString().Should().Be("before:2026-01-01");
    }

    [Fact]
    public void BuildersPutTheObjectFormOnEveryMatcherField()
    {
        var request = HttpRequest.Request()
            .WithPath("/date-time/{when}")
            .WithHeaderMatcher("X-Issued-At", MatcherValue.After("now -1 hours"))
            .WithQueryStringParameterMatcher("from", MatcherValue.After("2026-01-01").WithZone("Europe/London"))
            .WithCookieMatcher("seen", MatcherValue.EqualToDateTime("now +1 days").WithNot(true))
            .WithPathParameterMatcher("when", MatcherValue.Before("now"))
            .Build();

        var doc = JsonDocument.Parse(JsonSerializer.Serialize(request, JsonOptions)).RootElement;

        doc.GetProperty("headers").GetProperty("X-Issued-At")[0].GetRawText()
            .Should().Be("{\"after\":\"now -1 hours\"}");
        doc.GetProperty("queryStringParameters").GetProperty("from")[0].GetRawText()
            .Should().Be("{\"after\":\"2026-01-01\",\"zone\":\"Europe/London\"}");
        doc.GetProperty("cookies").GetProperty("seen").GetRawText()
            .Should().Be("{\"not\":true,\"equalToDateTime\":\"now +1 days\"}");
        doc.GetProperty("pathParameters").GetProperty("when")[0].GetRawText()
            .Should().Be("{\"before\":\"now\"}");
    }

    [Fact]
    public void RetrievedDateTimeValuesRouteToTheMatcherMapsAndRoundTrip()
    {
        const string wire =
            "{\"httpRequest\":{\"path\":\"/x\"," +
            "\"headers\":{\"X-Issued-At\":[{\"after\":\"now -1 hours\"}],\"X-Plain\":[\"before:2026-01-01\"]}," +
            "\"queryStringParameters\":{\"from\":[{\"after\":\"2026-01-01\",\"actualFormat\":\"dd/MM/yyyy\"}]}," +
            "\"cookies\":{\"seen\":{\"not\":true,\"equalToDateTime\":\"now +1 days\",\"applyTruncationLast\":true}}}," +
            "\"httpResponse\":{\"statusCode\":200}}";

        var expectation = JsonSerializer.Deserialize<Expectation>(wire, JsonOptions)!;
        var request = expectation.HttpRequest!;

        request.Headers.Should().BeNull();
        request.HeaderMatchers!["X-Issued-At"][0].Should().Be(MatcherValue.After("now -1 hours"));
        request.HeaderMatchers["X-Plain"][0].Should().Be(MatcherValue.Literal("before:2026-01-01"));
        request.QueryStringParameterMatchers!["from"][0]
            .Should().Be(MatcherValue.After("2026-01-01").WithActualFormat("dd/MM/yyyy"));
        request.CookieMatchers!["seen"]
            .Should().Be(MatcherValue.EqualToDateTime("now +1 days").WithNot(true).WithApplyTruncationLast(true));

        var reread = JsonSerializer.Deserialize<Expectation>(JsonSerializer.Serialize(expectation, JsonOptions), JsonOptions)!;
        reread.HttpRequest!.HeaderMatchers.Should().BeEquivalentTo(request.HeaderMatchers);
        reread.HttpRequest.QueryStringParameterMatchers.Should().BeEquivalentTo(request.QueryStringParameterMatchers);
        reread.HttpRequest.CookieMatchers.Should().BeEquivalentTo(request.CookieMatchers);
    }

    [Fact]
    public void ExistingDateTimePathParameterIsCarriedOverByTheBuilder()
    {
        // Build() returns the builder's live request, so plain path parameters set on it (as on a
        // retrieved expectation) are what WithPathParameterMatcher migrates.
        var builder = HttpRequest.Request().WithPath("/x/{a}/{b}");
        builder.Build().PathParameters = new Dictionary<string, List<JsonElement>>
        {
            ["a"] = new List<JsonElement> { JsonDocument.Parse("{\"after\":\"now\"}").RootElement }
        };

        var request = builder.WithPathParameterMatcher("b", MatcherValue.Literal("x")).Build();

        request.PathParameterMatchers!["a"].Should().Equal(MatcherValue.After("now"));
        request.PathParameterMatchers["b"].Should().Equal(MatcherValue.Literal("x"));
    }

    [Theory]
    [InlineData("{\"before\":\"now\",\"after\":\"now\"}")]
    [InlineData("{\"after\":\"now\",\"value\":\"x\"}")]
    [InlineData("{\"after\":\"now\",\"schema\":{\"type\":\"string\"}}")]
    [InlineData("{\"after\":\"now\",\"optional\":true}")]
    [InlineData("{\"after\":42}")]
    [InlineData("{\"zone\":\"UTC\"}")]
    [InlineData("{\"value\":\"x\",\"truncateActual\":\"startOfDay\"}")]
    [InlineData("{\"not\":true,\"applyTruncationLast\":false}")]
    public void InvalidDateTimeShapesAreRejected(string wire)
    {
        var act = () => JsonSerializer.Deserialize<MatcherValue>(wire);
        act.Should().Throw<JsonException>();
    }

    [Fact]
    public void AnOptionWithoutAnOperatorIsRejectedNamingTheOption()
    {
        var act = () => JsonSerializer.Deserialize<MatcherValue>("{\"value\":\"x\",\"zone\":\"UTC\"}");
        act.Should().Throw<JsonException>().WithMessage("*\"zone\" needs one of before, after or equalToDateTime*");
    }
}
