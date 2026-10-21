using System.Text.Json;
using System.Text.Json.Nodes;
using FluentAssertions;
using MockServer.Client.Models;
using Xunit;

namespace MockServer.Client.Tests;

/// <summary>
/// Round-trip tests for the typed <see cref="Body"/> model and its <see cref="BodyJsonConverter"/>.
/// Each case proves both directions: JSON -> typed -> JSON is semantically identical
/// (<see cref="JsonNode.DeepEquals(JsonNode, JsonNode)"/>), and typed construction emits the exact
/// wire shape the server expects.
/// </summary>
public class BodyModelTests
{
    private static readonly JsonSerializerOptions JsonOptions = new()
    {
        DefaultIgnoreCondition = System.Text.Json.Serialization.JsonIgnoreCondition.WhenWritingNull
    };

    private static void AssertBodyRoundTrips(string json)
    {
        var body = JsonSerializer.Deserialize<Body>(json, JsonOptions);
        body.Should().NotBeNull();
        var reserialized = JsonSerializer.Serialize(body, JsonOptions);
        JsonNode.DeepEquals(JsonNode.Parse(json), JsonNode.Parse(reserialized))
            .Should().BeTrue($"body should round-trip losslessly; got: {reserialized}");
    }

    [Theory]
    [InlineData("{\"type\":\"STRING\",\"string\":\"hello\",\"subString\":true,\"contentType\":\"text/plain\"}")]
    [InlineData("{\"type\":\"JSON\",\"json\":\"{\\\"k\\\":1}\",\"matchType\":\"ONLY_MATCHING_FIELDS\",\"contentType\":\"application/json\"}")]
    [InlineData("{\"type\":\"JSON_PATH\",\"jsonPath\":\"$.name\"}")]
    [InlineData("{\"type\":\"JSON_SCHEMA\",\"jsonSchema\":\"{\\\"type\\\":\\\"object\\\"}\"}")]
    [InlineData("{\"type\":\"XML\",\"xml\":\"<a/>\",\"contentType\":\"application/xml\"}")]
    [InlineData("{\"type\":\"XML_SCHEMA\",\"xmlSchema\":\"<xs:schema/>\"}")]
    [InlineData("{\"type\":\"XPATH\",\"xpath\":\"/a/b\"}")]
    [InlineData("{\"type\":\"REGEX\",\"regex\":\"a.*z\"}")]
    [InlineData("{\"type\":\"BINARY\",\"base64Bytes\":\"AAECAw==\",\"contentType\":\"application/octet-stream\"}")]
    [InlineData("{\"type\":\"WASM\",\"moduleName\":\"my-rule\"}")]
    [InlineData("{\"type\":\"JSON_RPC\",\"method\":\"ping\",\"paramsSchema\":\"{}\"}")]
    [InlineData("{\"type\":\"FILE\",\"filePath\":\"/x.vm\",\"templateType\":\"VELOCITY\",\"contentType\":\"application/json\"}")]
    [InlineData("{\"type\":\"FILE\",\"filePath\":\"responses/{{ request.pathParameters.name.0 }}.json\",\"templateType\":\"MUSTACHE\",\"templatedFilePath\":true}")]
    [InlineData("{\"type\":\"FUZZY\",\"fuzzy\":\"colour\",\"threshold\":0.8,\"ignoreCase\":true}")]
    [InlineData("{\"type\":\"JSON\",\"json\":\"1\",\"not\":true,\"optional\":true}")]
    public void SingleVariant_RoundTrips(string json) => AssertBodyRoundTrips(json);

    [Fact]
    public void Parameters_RoundTrips()
    {
        AssertBodyRoundTrips("{\"type\":\"PARAMETERS\",\"parameters\":{\"a\":[\"1\",\"2\"],\"b\":[\"3\"]}}");
    }

    [Fact]
    public void Multipart_RoundTrips()
    {
        AssertBodyRoundTrips(
            "{\"type\":\"MULTIPART\",\"fields\":{\"f\":[\"v\"]},\"filenames\":{\"file\":[\"a.txt\"]},\"partContentTypes\":{\"file\":[\"text/plain\"]}}");
    }

    [Fact]
    public void GraphQl_RoundTrips_WithFieldsArray()
    {
        AssertBodyRoundTrips(
            "{\"type\":\"GRAPHQL\",\"query\":\"{ me }\",\"operationName\":\"Q\",\"variablesSchema\":\"{}\",\"selectionSetMatchType\":\"AST_SUBSET\",\"fields\":[\"id\",\"name\"],\"schema\":\"type Query{me:ID}\"}");
    }

    [Fact]
    public void AllOf_RoundTrips_Nested()
    {
        AssertBodyRoundTrips(
            "{\"type\":\"ALL_OF\",\"bodyAllOf\":[{\"type\":\"JSON_PATH\",\"jsonPath\":\"$.a\"},{\"type\":\"REGEX\",\"regex\":\"x\"}]}");
    }

    [Fact]
    public void StringShorthand_RoundTrips_Verbatim()
    {
        AssertBodyRoundTrips("\"just a string body\"");
    }

    [Fact]
    public void BareJsonObjectShorthand_RoundTrips_Verbatim()
    {
        // A body object without a "type" discriminator is a bare-JSON body (additionalProperties:true).
        AssertBodyRoundTrips("{\"name\":\"value\",\"nested\":{\"n\":1},\"arr\":[1,2,3]}");
    }

    [Fact]
    public void BareJsonArrayShorthand_RoundTrips_Verbatim()
    {
        AssertBodyRoundTrips("[1,2,{\"a\":true}]");
    }

    [Fact]
    public void UnknownProperty_OnKnownType_IsPreserved()
    {
        // A future/unknown property on a known type must survive via AdditionalProperties.
        var json = "{\"type\":\"JSON\",\"json\":\"{}\",\"futureField\":{\"deep\":[1]},\"anotherNew\":42}";
        var body = JsonSerializer.Deserialize<Body>(json, JsonOptions)!;
        body.Type.Should().Be("JSON");
        body.AdditionalProperties.Should().ContainKey("futureField").And.ContainKey("anotherNew");
        AssertBodyRoundTrips(json);
    }

    [Fact]
    public void TypedConstruction_EmitsExactWireShape()
    {
        var body = Body.OfJson("{\"k\":1}", matchType: "STRICT").WithContentType("application/json").WithOptional(true);
        var json = JsonSerializer.Serialize(body, JsonOptions);
        var doc = JsonDocument.Parse(json);
        doc.RootElement.GetProperty("type").GetString().Should().Be("JSON");
        doc.RootElement.GetProperty("json").GetString().Should().Be("{\"k\":1}");
        doc.RootElement.GetProperty("matchType").GetString().Should().Be("STRICT");
        doc.RootElement.GetProperty("contentType").GetString().Should().Be("application/json");
        doc.RootElement.GetProperty("optional").GetBoolean().Should().BeTrue();
    }

    [Fact]
    public void TypedBody_AssignedToRequestBody_SerializesViaConverter()
    {
        // The object? Body accessor still works; assigning a typed Body opts into the converter.
        var request = new HttpRequest { Path = "/x", Body = Body.OfRegex("a.*z").WithNot(true) };
        var json = JsonSerializer.Serialize(request, JsonOptions);
        var body = JsonDocument.Parse(json).RootElement.GetProperty("body");
        body.GetProperty("type").GetString().Should().Be("REGEX");
        body.GetProperty("regex").GetString().Should().Be("a.*z");
        body.GetProperty("not").GetBoolean().Should().BeTrue();
    }

    [Fact]
    public void ExistingBodyAccessors_StillWork()
    {
        // Backward-compatibility: raw string, TypedBody and FileBody assignments are unchanged.
        var stringBody = JsonSerializer.Serialize(new HttpRequest { Body = "plain" }, JsonOptions);
        JsonDocument.Parse(stringBody).RootElement.GetProperty("body").GetString().Should().Be("plain");

        var typed = JsonSerializer.Serialize(new HttpRequest { Body = new TypedBody { Json = "{}" } }, JsonOptions);
        JsonDocument.Parse(typed).RootElement.GetProperty("body").GetProperty("type").GetString().Should().Be("JSON");

        var file = JsonSerializer.Serialize(new HttpResponse { Body = new FileBody { FilePath = "/x" } }, JsonOptions);
        JsonDocument.Parse(file).RootElement.GetProperty("body").GetProperty("type").GetString().Should().Be("FILE");
    }
}
