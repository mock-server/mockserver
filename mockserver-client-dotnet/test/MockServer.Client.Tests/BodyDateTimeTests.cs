using System.Text.Json;
using System.Text.Json.Nodes;
using FluentAssertions;
using MockServer.Client.Models;
using Xunit;

namespace MockServer.Client.Tests;

/// <summary>
/// A <c>JSON_PATH</c> or <c>XPATH</c> <see cref="Body"/> can compare the value it selects as a date-time,
/// carried under <c>dateTime</c> in the same object form as a date-time header value.
/// </summary>
public class BodyDateTimeTests
{
    private static readonly JsonSerializerOptions JsonOptions = new()
    {
        DefaultIgnoreCondition = System.Text.Json.Serialization.JsonIgnoreCondition.WhenWritingNull,
        PropertyNamingPolicy = JsonNamingPolicy.CamelCase,
        Encoder = System.Text.Encodings.Web.JavaScriptEncoder.UnsafeRelaxedJsonEscaping
    };

    [Fact]
    public void WritesTheDateTimeObjectFormOnAJsonPathBody()
    {
        var body = Body.OfJsonPath("$.order.created").WithDateTime(MatcherValue.After("now -1 days").WithZone("Europe/London"));

        JsonSerializer.Serialize(body, JsonOptions).Should().Be(
            "{\"type\":\"JSON_PATH\",\"jsonPath\":\"$.order.created\",\"dateTime\":{\"after\":\"now -1 days\",\"zone\":\"Europe/London\"}}");
    }

    [Fact]
    public void WritesTheDateTimeObjectFormOnAnXPathBody()
    {
        var body = Body.OfXPath("/order/@expires").WithDateTime(MatcherValue.Before("2026-01-01").WithNot(true));

        JsonSerializer.Serialize(body, JsonOptions).Should().Be(
            "{\"type\":\"XPATH\",\"xpath\":\"/order/@expires\",\"dateTime\":{\"not\":true,\"before\":\"2026-01-01\"}}");
    }

    [Fact]
    public void WritesNothingExtraWithoutADateTime()
    {
        JsonSerializer.Serialize(Body.OfJsonPath("$.a"), JsonOptions).Should().Be("{\"type\":\"JSON_PATH\",\"jsonPath\":\"$.a\"}");
    }

    [Fact]
    public void RejectsAValueThatIsNotADateTimeMatcher()
    {
        var act = () => Body.OfJsonPath("$.a").WithDateTime(MatcherValue.Literal("now"));

        act.Should().Throw<ArgumentException>().WithMessage("*date-time matcher*");
    }

    [Theory]
    [InlineData("matcher_body_json_path_date_time.json")]
    [InlineData("matcher_body_xpath_date_time.json")]
    public void SharedFixtureBodyDecodesTypedAndRoundTrips(string fixtureName)
    {
        var fixture = JsonNode.Parse(File.ReadAllText(Path.Combine(AppContext.BaseDirectory, "fixtures", fixtureName)))!;
        var source = fixture["httpRequest"]!["body"]!.ToJsonString();

        var body = JsonSerializer.Deserialize<Body>(source, JsonOptions)!;

        body.DateTime.Should().NotBeNull();
        body.DateTime!.Value.IsDateTime.Should().BeTrue();
        body.AdditionalProperties.Should().BeNull();
        JsonNode.DeepEquals(JsonNode.Parse(JsonSerializer.Serialize(body, JsonOptions)), JsonNode.Parse(source)).Should().BeTrue();
    }
}
