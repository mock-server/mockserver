using System.Text;
using System.Text.Json;
using System.Text.Json.Serialization;
using MockServer.Client.Models;

namespace MockServer.Client.Mcp;

/// <summary>
/// A JSON-RPC method body matcher (<c>type=JSON_RPC</c>).
/// </summary>
internal sealed class JsonRpcBody
{
    [JsonPropertyName("type")]
    public string Type => "JSON_RPC";

    [JsonPropertyName("method")]
    public string Method { get; set; } = "";
}

/// <summary>
/// A JSONPath body matcher (<c>type=JSON_PATH</c>).
/// </summary>
internal sealed class JsonPathBody
{
    [JsonPropertyName("type")]
    public string Type => "JSON_PATH";

    [JsonPropertyName("jsonPath")]
    public string JsonPath { get; set; } = "";
}

/// <summary>
/// Fluent builder for mocking an MCP (Model Context Protocol) server
/// (mirrors <c>org.mockserver.client.McpMockBuilder</c>). Produces the same
/// wire-level expectation JSON: a set of HTTP expectations that emulate a
/// Streamable HTTP MCP server speaking JSON-RPC 2.0.
/// </summary>
public sealed class McpMockBuilder
{
    private readonly string _path;
    private string _serverName = "MockMCPServer";
    private string _serverVersion = "1.0.0";
    private string _protocolVersion = "2025-03-26";
    private bool _toolsCapability;
    private bool _resourcesCapability;
    private bool _promptsCapability;
    private readonly List<McpToolDefinition> _tools = new();
    private readonly List<McpResourceDefinition> _resources = new();
    private readonly List<McpPromptDefinition> _prompts = new();

    public McpMockBuilder(string path = "/mcp")
    {
        _path = path;
    }

    /// <summary>Create a new MCP mock builder. <paramref name="path"/> defaults to <c>/mcp</c>.</summary>
    public static McpMockBuilder McpMock(string path = "/mcp") => new(path);

    // --- top-level configuration -------------------------------------------

    public McpMockBuilder WithServerName(string name)
    {
        _serverName = name;
        return this;
    }

    public McpMockBuilder WithServerVersion(string version)
    {
        _serverVersion = version;
        return this;
    }

    public McpMockBuilder WithProtocolVersion(string version)
    {
        _protocolVersion = version;
        return this;
    }

    public McpMockBuilder WithToolsCapability()
    {
        _toolsCapability = true;
        return this;
    }

    public McpMockBuilder WithResourcesCapability()
    {
        _resourcesCapability = true;
        return this;
    }

    public McpMockBuilder WithPromptsCapability()
    {
        _promptsCapability = true;
        return this;
    }

    public McpToolBuilder WithTool(string name) => new(this, name);

    public McpResourceBuilder WithResource(string uri) => new(this, uri);

    public McpPromptBuilder WithPrompt(string name) => new(this, name);

    // --- internal registration (called by the sub-builders' And()) ---------

    internal void AddTool(McpToolDefinition tool)
    {
        _tools.Add(tool);
        _toolsCapability = true;
    }

    internal void AddResource(McpResourceDefinition resource)
    {
        _resources.Add(resource);
        _resourcesCapability = true;
    }

    internal void AddPrompt(McpPromptDefinition prompt)
    {
        _prompts.Add(prompt);
        _promptsCapability = true;
    }

    // --- terminal operations -----------------------------------------------

    public List<Expectation> ApplyTo(MockServerClient client) => client.Upsert(Build().ToArray());

    public List<Expectation> Build()
    {
        var expectations = new List<Expectation>
        {
            BuildInitializeExpectation(),
            BuildPingExpectation(),
            BuildNotificationsInitializedExpectation()
        };

        if (_toolsCapability || _tools.Count > 0)
            expectations.Add(BuildToolsListExpectation());
        foreach (var tool in _tools)
            expectations.Add(BuildToolsCallExpectation(tool));

        if (_resourcesCapability || _resources.Count > 0)
            expectations.Add(BuildResourcesListExpectation());
        foreach (var resource in _resources)
            expectations.Add(BuildResourcesReadExpectation(resource));

        if (_promptsCapability || _prompts.Count > 0)
            expectations.Add(BuildPromptsListExpectation());
        foreach (var prompt in _prompts)
            expectations.Add(BuildPromptsGetExpectation(prompt));

        return expectations;
    }

    // --- expectation builders ----------------------------------------------

    private HttpRequest JsonRpcRequest(string method) => new()
    {
        Method = "POST",
        Path = _path,
        Body = new JsonRpcBody { Method = method }
    };

    private HttpRequest JsonPathRequest(string jsonPath) => new()
    {
        Method = "POST",
        Path = _path,
        Body = new JsonPathBody { JsonPath = jsonPath }
    };

    private static Expectation VelocityResponse(HttpRequest request, string resultJson) => new()
    {
        HttpRequest = request,
        HttpResponseTemplate = new HttpTemplate
        {
            TemplateType = TemplateType.VELOCITY,
            Template = VelocityJsonRpcResponse(resultJson)
        }
    };

    private Expectation BuildInitializeExpectation()
    {
        var capsParts = new List<string>();
        if (_toolsCapability || _tools.Count > 0)
            capsParts.Add("\"tools\": {\"listChanged\": false}");
        if (_resourcesCapability || _resources.Count > 0)
            capsParts.Add("\"resources\": {\"subscribe\": false, \"listChanged\": false}");
        if (_promptsCapability || _prompts.Count > 0)
            capsParts.Add("\"prompts\": {\"listChanged\": false}");
        var caps = "{" + string.Join(", ", capsParts) + "}";

        var resultJson =
            "{\"protocolVersion\": \"" + EscapeVelocity(EscapeJson(_protocolVersion)) + "\", " +
            "\"capabilities\": " + caps + ", " +
            "\"serverInfo\": {\"name\": \"" + EscapeVelocity(EscapeJson(_serverName)) +
            "\", \"version\": \"" + EscapeVelocity(EscapeJson(_serverVersion)) + "\"}}";

        return VelocityResponse(JsonRpcRequest("initialize"), resultJson);
    }

    private Expectation BuildPingExpectation()
        => VelocityResponse(JsonRpcRequest("ping"), "{}");

    private Expectation BuildNotificationsInitializedExpectation() => new()
    {
        HttpRequest = JsonRpcRequest("notifications/initialized"),
        HttpResponse = new HttpResponse
        {
            StatusCode = 200,
            Headers = new Dictionary<string, List<string>>
            {
                ["Content-Type"] = new() { "application/json" }
            },
            Body = "{}"
        }
    };

    private Expectation BuildToolsListExpectation()
    {
        var items = new List<string>();
        foreach (var tool in _tools)
        {
            var sb = new StringBuilder();
            sb.Append("{\"name\": \"").Append(EscapeVelocity(EscapeJson(tool.Name))).Append('"');
            if (tool.Description != null)
                sb.Append(", \"description\": \"").Append(EscapeVelocity(EscapeJson(tool.Description))).Append('"');
            if (tool.InputSchema != null)
                sb.Append(", \"inputSchema\": ").Append(EscapeVelocity(ValidateAndSerializeJson(tool.InputSchema)));
            sb.Append('}');
            items.Add(sb.ToString());
        }
        var toolsJson = "[" + string.Join(", ", items) + "]";
        return VelocityResponse(JsonRpcRequest("tools/list"), "{\"tools\": " + toolsJson + "}");
    }

    private Expectation BuildToolsCallExpectation(McpToolDefinition tool)
    {
        var jsonPath = "$[?(@.method == 'tools/call' && @.params.name == '" + EscapeJsonPath(tool.Name) + "')]";
        var content = tool.ResponseContent != null ? EscapeVelocity(EscapeJson(tool.ResponseContent)) : "";
        var isError = tool.ResponseIsError ? "true" : "false";
        var resultJson = "{\"content\": [{\"type\": \"text\", \"text\": \"" + content + "\"}], \"isError\": " + isError + "}";
        return VelocityResponse(JsonPathRequest(jsonPath), resultJson);
    }

    private Expectation BuildResourcesListExpectation()
    {
        var items = new List<string>();
        foreach (var resource in _resources)
        {
            var sb = new StringBuilder();
            sb.Append("{\"uri\": \"").Append(EscapeVelocity(EscapeJson(resource.Uri))).Append('"');
            if (resource.Name != null)
                sb.Append(", \"name\": \"").Append(EscapeVelocity(EscapeJson(resource.Name))).Append('"');
            if (resource.Description != null)
                sb.Append(", \"description\": \"").Append(EscapeVelocity(EscapeJson(resource.Description))).Append('"');
            if (resource.MimeType != null)
                sb.Append(", \"mimeType\": \"").Append(EscapeVelocity(EscapeJson(resource.MimeType))).Append('"');
            sb.Append('}');
            items.Add(sb.ToString());
        }
        var resourcesJson = "[" + string.Join(", ", items) + "]";
        return VelocityResponse(JsonRpcRequest("resources/list"), "{\"resources\": " + resourcesJson + "}");
    }

    private Expectation BuildResourcesReadExpectation(McpResourceDefinition resource)
    {
        var jsonPath = "$[?(@.method == 'resources/read' && @.params.uri == '" + EscapeJsonPath(resource.Uri) + "')]";
        var content = resource.Content != null ? EscapeVelocity(EscapeJson(resource.Content)) : "";
        var mimeType = resource.MimeType ?? "application/json";
        var resultJson =
            "{\"contents\": [{\"uri\": \"" + EscapeVelocity(EscapeJson(resource.Uri)) + "\", " +
            "\"mimeType\": \"" + EscapeVelocity(EscapeJson(mimeType)) + "\", " +
            "\"text\": \"" + content + "\"}]}";
        return VelocityResponse(JsonPathRequest(jsonPath), resultJson);
    }

    private Expectation BuildPromptsListExpectation()
    {
        var items = new List<string>();
        foreach (var prompt in _prompts)
        {
            var sb = new StringBuilder();
            sb.Append("{\"name\": \"").Append(EscapeVelocity(EscapeJson(prompt.Name))).Append('"');
            if (prompt.Description != null)
                sb.Append(", \"description\": \"").Append(EscapeVelocity(EscapeJson(prompt.Description))).Append('"');
            if (prompt.Arguments.Count > 0)
            {
                var argItems = new List<string>();
                foreach (var arg in prompt.Arguments)
                {
                    var argSb = new StringBuilder();
                    argSb.Append("{\"name\": \"").Append(EscapeVelocity(EscapeJson(arg.Name))).Append('"');
                    if (arg.Description != null)
                        argSb.Append(", \"description\": \"").Append(EscapeVelocity(EscapeJson(arg.Description))).Append('"');
                    argSb.Append(", \"required\": ").Append(arg.Required ? "true" : "false");
                    argSb.Append('}');
                    argItems.Add(argSb.ToString());
                }
                sb.Append(", \"arguments\": [").Append(string.Join(", ", argItems)).Append(']');
            }
            sb.Append('}');
            items.Add(sb.ToString());
        }
        var promptsJson = "[" + string.Join(", ", items) + "]";
        return VelocityResponse(JsonRpcRequest("prompts/list"), "{\"prompts\": " + promptsJson + "}");
    }

    private Expectation BuildPromptsGetExpectation(McpPromptDefinition prompt)
    {
        var jsonPath = "$[?(@.method == 'prompts/get' && @.params.name == '" + EscapeJsonPath(prompt.Name) + "')]";
        var msgItems = new List<string>();
        foreach (var msg in prompt.Messages)
        {
            msgItems.Add(
                "{\"role\": \"" + EscapeVelocity(EscapeJson(msg.Role)) + "\", " +
                "\"content\": {\"type\": \"text\", \"text\": \"" + EscapeVelocity(EscapeJson(msg.Text)) + "\"}}");
        }
        var messagesJson = "[" + string.Join(", ", msgItems) + "]";
        var resultJson = "{\"messages\": " + messagesJson + "}";
        return VelocityResponse(JsonPathRequest(jsonPath), resultJson);
    }

    // --- escaping helpers (ported 1:1 for byte-identical templates) --------

    /// <summary>
    /// JSON-escape a string for inlining inside a JSON string literal, returning
    /// the contents WITHOUT the surrounding quotes. Mirrors Jackson's
    /// <c>writeValueAsString</c> then stripping the outer quotes.
    /// </summary>
    internal static string EscapeJson(string? value)
    {
        if (value == null)
            return "";
        var quoted = JsonSerializer.Serialize(value);
        return quoted.Substring(1, quoted.Length - 2);
    }

    /// <summary>
    /// Escape Velocity meta-characters so literal <c>$</c> and <c>#</c> are not
    /// interpreted as Velocity references/directives. Velocity consumes backslashes
    /// written before a reference or directive, so a run of backslashes followed by
    /// <c>$</c>, <c>#</c> or the end of the value is escaped too; other backslashes
    /// are left as-is.
    /// </summary>
    internal static string? EscapeVelocity(string? value)
    {
        if (value == null)
            return null;
        var sb = new StringBuilder(value.Length);
        var i = 0;
        while (i < value.Length)
        {
            var c = value[i];
            if (c == '\\')
            {
                var runEnd = i;
                while (runEnd < value.Length && value[runEnd] == '\\')
                    runEnd++;
                var beforeMetacharacter = runEnd == value.Length || value[runEnd] == '$' || value[runEnd] == '#';
                for (var j = i; j < runEnd; j++)
                    sb.Append(beforeMetacharacter ? "${esc.b}" : "\\");
                i = runEnd;
                continue;
            }
            if (c == '$')
                sb.Append("${esc.d}");
            else if (c == '#')
                sb.Append("${esc.h}");
            else
                sb.Append(c);
            i++;
        }
        return sb.ToString();
    }

    /// <summary>Escape backslashes, then single quotes, for safe inclusion inside a single-quoted JSONPath string literal.</summary>
    internal static string EscapeJsonPath(string? value)
    {
        if (value == null)
            return "";
        return value.Replace("\\", "\\\\").Replace("'", "\\'");
    }

    /// <summary>
    /// Validate that the supplied string is valid JSON and return it re-serialised
    /// in compact form. Throws for invalid JSON.
    /// </summary>
    internal static string ValidateAndSerializeJson(string json)
    {
        try
        {
            using var doc = JsonDocument.Parse(json);
            return JsonSerializer.Serialize(doc.RootElement);
        }
        catch (JsonException e)
        {
            throw new ArgumentException("Invalid JSON for inputSchema: " + e.Message, nameof(json), e);
        }
    }

    private static string VelocityJsonRpcResponse(string resultJson) =>
        "{\"statusCode\": 200, " +
        "\"headers\": [{\"name\": \"Content-Type\", \"values\": [\"application/json\"]}], " +
        "\"body\": {\"jsonrpc\": \"2.0\", \"result\": " + resultJson + ", \"id\": $!{request.jsonRpcRawId}}}";
}

// ---------------------------------------------------------------------------
// Internal definition holders
// ---------------------------------------------------------------------------

internal sealed class McpToolDefinition
{
    public string Name { get; set; } = "";
    public string? Description { get; set; }
    public string? InputSchema { get; set; }
    public string? ResponseContent { get; set; }
    public bool ResponseIsError { get; set; }
}

internal sealed class McpResourceDefinition
{
    public string Uri { get; set; } = "";
    public string? Name { get; set; }
    public string? Description { get; set; }
    public string? MimeType { get; set; } = "application/json";
    public string? Content { get; set; }
}

internal sealed class McpPromptArgument
{
    public string Name { get; set; } = "";
    public string? Description { get; set; }
    public bool Required { get; set; }
}

internal sealed class McpPromptMessage
{
    public string Role { get; set; } = "";
    public string Text { get; set; } = "";
}

internal sealed class McpPromptDefinition
{
    public string Name { get; set; } = "";
    public string? Description { get; set; }
    public List<McpPromptArgument> Arguments { get; } = new();
    public List<McpPromptMessage> Messages { get; } = new();
}

// ---------------------------------------------------------------------------
// Nested fluent sub-builders
// ---------------------------------------------------------------------------

/// <summary>Sub-builder for an MCP tool. Call <see cref="And"/> to register it.</summary>
public sealed class McpToolBuilder
{
    private readonly McpMockBuilder _parent;
    private readonly McpToolDefinition _tool;

    internal McpToolBuilder(McpMockBuilder parent, string name)
    {
        _parent = parent;
        _tool = new McpToolDefinition { Name = name };
    }

    public McpToolBuilder WithDescription(string description)
    {
        _tool.Description = description;
        return this;
    }

    public McpToolBuilder WithInputSchema(string jsonSchema)
    {
        _tool.InputSchema = jsonSchema;
        return this;
    }

    public McpToolBuilder RespondingWith(string textContent, bool isError = false)
    {
        _tool.ResponseContent = textContent;
        _tool.ResponseIsError = isError;
        return this;
    }

    public McpMockBuilder And()
    {
        _parent.AddTool(_tool);
        return _parent;
    }
}

/// <summary>Sub-builder for an MCP resource. Call <see cref="And"/> to register it.</summary>
public sealed class McpResourceBuilder
{
    private readonly McpMockBuilder _parent;
    private readonly McpResourceDefinition _resource;

    internal McpResourceBuilder(McpMockBuilder parent, string uri)
    {
        _parent = parent;
        _resource = new McpResourceDefinition { Uri = uri };
    }

    public McpResourceBuilder WithName(string name)
    {
        _resource.Name = name;
        return this;
    }

    public McpResourceBuilder WithDescription(string description)
    {
        _resource.Description = description;
        return this;
    }

    public McpResourceBuilder WithMimeType(string mimeType)
    {
        _resource.MimeType = mimeType;
        return this;
    }

    public McpResourceBuilder WithContent(string content)
    {
        _resource.Content = content;
        return this;
    }

    public McpMockBuilder And()
    {
        _parent.AddResource(_resource);
        return _parent;
    }
}

/// <summary>Sub-builder for an MCP prompt. Call <see cref="And"/> to register it.</summary>
public sealed class McpPromptBuilder
{
    private readonly McpMockBuilder _parent;
    private readonly McpPromptDefinition _prompt;

    internal McpPromptBuilder(McpMockBuilder parent, string name)
    {
        _parent = parent;
        _prompt = new McpPromptDefinition { Name = name };
    }

    public McpPromptBuilder WithDescription(string description)
    {
        _prompt.Description = description;
        return this;
    }

    public McpPromptBuilder WithArgument(string name, string? description, bool required)
    {
        _prompt.Arguments.Add(new McpPromptArgument { Name = name, Description = description, Required = required });
        return this;
    }

    public McpPromptBuilder RespondingWith(string role, string textContent)
    {
        _prompt.Messages.Add(new McpPromptMessage { Role = role, Text = textContent });
        return this;
    }

    public McpMockBuilder And()
    {
        _parent.AddPrompt(_prompt);
        return _parent;
    }
}
