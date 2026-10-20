using System.Linq;
using System.Text.Json;
using FluentAssertions;
using MockServer.Client.Mcp;
using MockServer.Client.Models;
using Xunit;

namespace MockServer.Client.Tests;

/// <summary>
/// Unit tests for the fluent MCP mock builder. Asserts the generated JSON-RPC
/// expectation set: the initialize Velocity template, tools/list, tools/call
/// JSONPath matchers, and the per-method response wrapping.
/// </summary>
public class McpBuilderTests
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

    [Fact]
    public void Mcp_EmptyServer_ProducesCoreExpectations()
    {
        var expectations = McpMockBuilder.McpMock().Build();

        // initialize, ping, notifications/initialized — and nothing else by default.
        expectations.Should().HaveCount(3);

        var initialize = FindByJsonRpcMethod(expectations, "initialize");
        initialize.GetProperty("httpRequest").GetProperty("method").GetString().Should().Be("POST");
        initialize.GetProperty("httpRequest").GetProperty("path").GetString().Should().Be("/mcp");

        var tmpl = initialize.GetProperty("httpResponseTemplate");
        tmpl.GetProperty("templateType").GetString().Should().Be("VELOCITY");
        var template = tmpl.GetProperty("template").GetString()!;
        template.Should().Contain("\"jsonrpc\": \"2.0\"");
        template.Should().Contain("$!{request.jsonRpcRawId}");
        template.Should().Contain("\"protocolVersion\": \"2025-03-26\"");
        template.Should().Contain("\"name\": \"MockMCPServer\"");
        template.Should().Contain("\"version\": \"1.0.0\"");

        var ping = FindByJsonRpcMethod(expectations, "ping");
        ping.GetProperty("httpResponseTemplate").GetProperty("template").GetString()
            .Should().Contain("\"result\": {}");

        var notif = FindByJsonRpcMethod(expectations, "notifications/initialized");
        notif.GetProperty("httpResponse").GetProperty("statusCode").GetInt32().Should().Be(200);
        notif.GetProperty("httpResponse").GetProperty("body").GetString().Should().Be("{}");
    }

    [Fact]
    public void Mcp_WithTool_EmitsToolsCapabilityListAndCall()
    {
        var expectations = McpMockBuilder.McpMock()
            .WithServerName("WeatherServer")
            .WithTool("get_weather")
                .WithDescription("Get the weather for a city")
                .WithInputSchema("{\"type\":\"object\",\"properties\":{\"city\":{\"type\":\"string\"}}}")
                .RespondingWith("72F and sunny")
            .And()
            .Build();

        // initialize, ping, notifications, tools/list, tools/call(get_weather).
        expectations.Should().HaveCount(5);

        // initialize advertises the tools capability.
        var initTemplate = FindByJsonRpcMethod(expectations, "initialize")
            .GetProperty("httpResponseTemplate").GetProperty("template").GetString()!;
        initTemplate.Should().Contain("\"tools\": {\"listChanged\": false}");
        initTemplate.Should().Contain("\"name\": \"WeatherServer\"");

        // tools/list contains the tool with a compacted, validated input schema.
        var toolsList = FindByJsonRpcMethod(expectations, "tools/list")
            .GetProperty("httpResponseTemplate").GetProperty("template").GetString()!;
        toolsList.Should().Contain("\"name\": \"get_weather\"");
        toolsList.Should().Contain("\"description\": \"Get the weather for a city\"");
        toolsList.Should().Contain("\"inputSchema\": {\"type\":\"object\"");

        // tools/call uses a JSONPath matcher scoped to the tool name.
        var toolsCall = FindByJsonPathContaining(expectations, "tools/call");
        var jsonPath = toolsCall.GetProperty("httpRequest").GetProperty("body").GetProperty("jsonPath").GetString()!;
        jsonPath.Should().Be("$[?(@.method == 'tools/call' && @.params.name == 'get_weather')]");
        var callTemplate = toolsCall.GetProperty("httpResponseTemplate").GetProperty("template").GetString()!;
        callTemplate.Should().Contain("\"text\": \"72F and sunny\"");
        callTemplate.Should().Contain("\"isError\": false");
    }

    [Fact]
    public void Mcp_ToolError_SetsIsErrorTrue()
    {
        var expectations = McpMockBuilder.McpMock()
            .WithTool("explode").RespondingWith("boom", isError: true).And()
            .Build();

        var callTemplate = FindByJsonPathContaining(expectations, "tools/call")
            .GetProperty("httpResponseTemplate").GetProperty("template").GetString()!;
        callTemplate.Should().Contain("\"isError\": true");
    }

    [Fact]
    public void Mcp_WithResource_EmitsListAndRead()
    {
        var expectations = McpMockBuilder.McpMock()
            .WithResource("file:///config.json")
                .WithName("config")
                .WithMimeType("application/json")
                .WithContent("{\"debug\":true}")
            .And()
            .Build();

        var resourcesList = FindByJsonRpcMethod(expectations, "resources/list")
            .GetProperty("httpResponseTemplate").GetProperty("template").GetString()!;
        resourcesList.Should().Contain("\"uri\": \"file:///config.json\"");
        resourcesList.Should().Contain("\"name\": \"config\"");

        var read = FindByJsonPathContaining(expectations, "resources/read");
        var jsonPath = read.GetProperty("httpRequest").GetProperty("body").GetProperty("jsonPath").GetString()!;
        jsonPath.Should().Be("$[?(@.method == 'resources/read' && @.params.uri == 'file:///config.json')]");
        var readTemplate = read.GetProperty("httpResponseTemplate").GetProperty("template").GetString()!;
        readTemplate.Should().Contain("\"mimeType\": \"application/json\"");
        // The JSON content's quotes are escaped inside the inlined JSON string literal,
        // so when re-read the template still round-trips to the original content.
        readTemplate.Should().Contain("debug");
        readTemplate.Should().Contain(":true}");
    }

    [Fact]
    public void Mcp_WithPrompt_EmitsListWithArgumentsAndGet()
    {
        var expectations = McpMockBuilder.McpMock()
            .WithPrompt("greeting")
                .WithDescription("A greeting prompt")
                .WithArgument("name", "the name to greet", required: true)
                .RespondingWith("user", "Hello!")
            .And()
            .Build();

        var promptsList = FindByJsonRpcMethod(expectations, "prompts/list")
            .GetProperty("httpResponseTemplate").GetProperty("template").GetString()!;
        promptsList.Should().Contain("\"name\": \"greeting\"");
        promptsList.Should().Contain("\"arguments\": [");
        promptsList.Should().Contain("\"required\": true");

        var get = FindByJsonPathContaining(expectations, "prompts/get");
        var jsonPath = get.GetProperty("httpRequest").GetProperty("body").GetProperty("jsonPath").GetString()!;
        jsonPath.Should().Be("$[?(@.method == 'prompts/get' && @.params.name == 'greeting')]");
        var getTemplate = get.GetProperty("httpResponseTemplate").GetProperty("template").GetString()!;
        getTemplate.Should().Contain("\"role\": \"user\"");
        getTemplate.Should().Contain("\"text\": \"Hello!\"");
    }

    [Fact]
    public void Mcp_EscapesVelocityMetacharacters()
    {
        var expectations = McpMockBuilder.McpMock()
            .WithTool("price").RespondingWith("costs $5 #1").And()
            .Build();

        var callTemplate = FindByJsonPathContaining(expectations, "tools/call")
            .GetProperty("httpResponseTemplate").GetProperty("template").GetString()!;
        // '$' and '#' in mock content are escaped so Velocity doesn't interpret them.
        callTemplate.Should().Contain("costs ${esc.d}5 ${esc.h}1");
    }

    // Shared cross-client table: backslash runs before $, # or the end of the value are
    // escaped; other backslashes are left as-is.
    [Theory]
    [InlineData(@"a\$b", @"a${esc.b}${esc.d}b")]
    [InlineData(@"a\#b", @"a${esc.b}${esc.h}b")]
    [InlineData(@"end\", @"end${esc.b}")]
    [InlineData(@"\\$", @"${esc.b}${esc.b}${esc.d}")]
    [InlineData(@"${esc.b}", @"${esc.d}{esc.b}")]
    [InlineData(@"é\$ü", @"é${esc.b}${esc.d}ü")]
    [InlineData(@"a\nb \""q\""", @"a\nb \""q\""")]
    [InlineData(@"\\x", @"\\x")]
    [InlineData("$#", "${esc.d}${esc.h}")]
    [InlineData("", "")]
    public void EscapeVelocity_EscapesBackslashesBeforeMetacharacters(string input, string expected)
    {
        McpMockBuilder.EscapeVelocity(input).Should().Be(expected);
        MockServer.Client.A2a.A2aMockBuilder.EscapeVelocity(input).Should().Be(expected);
    }

    [Theory]
    [InlineData("plain", "plain")]
    [InlineData("o'brien", @"o\'brien")]
    [InlineData(@"x\", @"x\\")]
    [InlineData(@"a\b", @"a\\b")]
    [InlineData(@"a\'b", @"a\\\'b")]
    public void EscapeJsonPath_EscapesBackslashesBeforeQuotes(string input, string expected)
    {
        McpMockBuilder.EscapeJsonPath(input).Should().Be(expected);
    }

    [Fact]
    public void Mcp_EscapesTrailingBackslashInJsonPath()
    {
        var expectations = McpMockBuilder.McpMock()
            .WithTool(@"tool\").RespondingWith("x").And()
            .Build();

        var jsonPath = FindByJsonPathContaining(expectations, "tools/call")
            .GetProperty("httpRequest").GetProperty("body").GetProperty("jsonPath").GetString();
        jsonPath.Should().Be(@"$[?(@.method == 'tools/call' && @.params.name == 'tool\\')]");
    }

    [Fact]
    public void Mcp_EscapesBackslashBeforeVelocityMetacharacter()
    {
        var expectations = McpMockBuilder.McpMock()
            .WithTool("price").RespondingWith(@"a\$b").And()
            .Build();

        var callTemplate = FindByJsonPathContaining(expectations, "tools/call")
            .GetProperty("httpResponseTemplate").GetProperty("template").GetString()!;
        // JSON-escaped "a\\$b": both backslashes precede '$', so all are escaped
        callTemplate.Should().Contain("a${esc.b}${esc.b}${esc.d}b");
    }

    [Fact]
    public void Mcp_InvalidInputSchema_Throws()
    {
        var act = () => McpMockBuilder.McpMock()
            .WithTool("bad").WithInputSchema("{not valid json").And()
            .Build();
        act.Should().Throw<ArgumentException>();
    }
}
