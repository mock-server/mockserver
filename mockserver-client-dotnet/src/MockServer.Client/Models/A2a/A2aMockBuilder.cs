using System;
using System.Collections.Generic;
using System.Text;
using System.Text.Json;
using System.Text.Json.Serialization;
using MockServer.Client.Models;

namespace MockServer.Client.A2a;

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
/// Fluent builder for mocking an A2A (Agent2Agent) server
/// (mirrors <c>org.mockserver.client.A2aMockBuilder</c>). Produces the same
/// wire-level expectation set: a GET agent-card document, JSON-RPC 2.0
/// <c>tasks/send</c> / <c>tasks/get</c> / <c>tasks/cancel</c> handlers, optional
/// SSE streaming, and optional push-notification config + delivery.
/// </summary>
public sealed class A2aMockBuilder
{
    private string _path = "/a2a";
    private string _agentCardPath = "/.well-known/agent.json";
    private string _agentName = "MockAgent";
    private string _agentDescription = "A mock A2A agent";
    private string _agentVersion = "1.0.0";
    private string? _agentUrl;
    private readonly List<A2aSkillDefinition> _skills = new();
    private readonly List<A2aTaskHandler> _taskHandlers = new();
    private string _defaultTaskResponse = "Task completed successfully";
    private bool _streaming;
    private string _streamingMethod = "message/stream";
    private string? _pushNotificationUrl;

    private A2aMockBuilder()
    {
    }

    /// <summary>Create a new A2A mock builder. <paramref name="path"/> defaults to <c>/a2a</c>.</summary>
    public static A2aMockBuilder A2aMock(string path = "/a2a")
    {
        var builder = new A2aMockBuilder();
        builder._path = path;
        return builder;
    }

    // --- top-level configuration -------------------------------------------

    public A2aMockBuilder WithAgentName(string name)
    {
        _agentName = name;
        return this;
    }

    public A2aMockBuilder WithAgentDescription(string description)
    {
        _agentDescription = description;
        return this;
    }

    public A2aMockBuilder WithAgentVersion(string version)
    {
        _agentVersion = version;
        return this;
    }

    public A2aMockBuilder WithAgentUrl(string url)
    {
        _agentUrl = url;
        return this;
    }

    public A2aMockBuilder WithAgentCardPath(string path)
    {
        _agentCardPath = path;
        return this;
    }

    public A2aMockBuilder WithDefaultTaskResponse(string response)
    {
        _defaultTaskResponse = response;
        return this;
    }

    /// <summary>
    /// Advertise and mock the A2A streaming capability. When enabled the agent card reports
    /// <c>capabilities.streaming: true</c> and the streaming JSON-RPC method (default
    /// <c>message/stream</c>, see <see cref="WithStreamingMethod"/>) returns an SSE stream of
    /// <c>status-update</c> and <c>artifact-update</c> chunks, each wrapped in a JSON-RPC 2.0 envelope.
    /// </summary>
    public A2aMockBuilder WithStreaming()
    {
        _streaming = true;
        return this;
    }

    /// <summary>
    /// Override the JSON-RPC method that triggers the streaming response. The A2A specification
    /// uses <c>message/stream</c>; the legacy method name is <c>tasks/sendSubscribe</c>.
    /// Implies <see cref="WithStreaming"/>.
    /// </summary>
    public A2aMockBuilder WithStreamingMethod(string method)
    {
        _streamingMethod = method;
        _streaming = true;
        return this;
    }

    /// <summary>
    /// Advertise and mock A2A push notifications. When configured the agent card reports
    /// <c>capabilities.pushNotifications: true</c>, the <c>tasks/pushNotificationConfig/set</c>
    /// method echoes the registered config, and each <c>tasks/send</c> additionally POSTs the
    /// completed task to <paramref name="webhookUrl"/> while still returning the JSON-RPC task
    /// response to the caller.
    /// </summary>
    /// <param name="webhookUrl">absolute webhook URL (e.g. <c>http://localhost:1234/a2a/callback</c>)</param>
    public A2aMockBuilder WithPushNotifications(string webhookUrl)
    {
        _pushNotificationUrl = webhookUrl;
        return this;
    }

    public A2aSkillBuilder WithSkill(string id) => new(this, id);

    public A2aTaskHandlerBuilder OnTaskSend() => new(this);

    // --- internal registration (called by the sub-builders' And()) ---------

    internal void AddSkill(A2aSkillDefinition skill) => _skills.Add(skill);

    internal void AddTaskHandler(A2aTaskHandler handler) => _taskHandlers.Add(handler);

    // --- terminal operations -----------------------------------------------

    public List<Expectation> ApplyTo(MockServerClient client) => client.Upsert(Build().ToArray());

    public List<Expectation> Build()
    {
        var expectations = new List<Expectation> { BuildAgentCardExpectation() };

        foreach (var handler in _taskHandlers)
            expectations.Add(BuildCustomTaskHandler(handler));

        if (_streaming)
            expectations.Add(BuildStreamingExpectation());

        if (_pushNotificationUrl != null)
        {
            expectations.Add(BuildPushNotificationConfigExpectation());
            expectations.Add(BuildPushNotificationDeliveryExpectation());
        }
        else
        {
            expectations.Add(BuildTasksSendExpectation());
        }
        expectations.Add(BuildTasksGetExpectation());
        expectations.Add(BuildTasksCancelExpectation());

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

    private Expectation BuildAgentCardExpectation()
    {
        var skillsJson = new StringBuilder("[");
        for (var i = 0; i < _skills.Count; i++)
        {
            if (i > 0)
                skillsJson.Append(", ");
            var skill = _skills[i];
            skillsJson.Append('{');
            skillsJson.Append("\"id\": \"").Append(EscapeJson(skill.Id)).Append('"');
            skillsJson.Append(", \"name\": \"").Append(EscapeJson(skill.Name ?? skill.Id)).Append('"');
            if (skill.Description != null)
                skillsJson.Append(", \"description\": \"").Append(EscapeJson(skill.Description)).Append('"');
            if (skill.Tags.Count > 0)
            {
                skillsJson.Append(", \"tags\": [");
                for (var j = 0; j < skill.Tags.Count; j++)
                {
                    if (j > 0)
                        skillsJson.Append(", ");
                    skillsJson.Append('"').Append(EscapeJson(skill.Tags[j])).Append('"');
                }
                skillsJson.Append(']');
            }
            if (skill.Examples.Count > 0)
            {
                skillsJson.Append(", \"examples\": [");
                for (var j = 0; j < skill.Examples.Count; j++)
                {
                    if (j > 0)
                        skillsJson.Append(", ");
                    skillsJson.Append('"').Append(EscapeJson(skill.Examples[j])).Append('"');
                }
                skillsJson.Append(']');
            }
            skillsJson.Append('}');
        }
        skillsJson.Append(']');

        var url = _agentUrl ?? "http://localhost" + _path;

        var agentCardJson = "{" +
            "\"name\": \"" + EscapeJson(_agentName) + "\", " +
            "\"description\": \"" + EscapeJson(_agentDescription) + "\", " +
            "\"version\": \"" + EscapeJson(_agentVersion) + "\", " +
            "\"url\": \"" + EscapeJson(url) + "\", " +
            "\"capabilities\": {\"streaming\": " + Bool(_streaming) +
            ", \"pushNotifications\": " + Bool(_pushNotificationUrl != null) +
            ", \"stateTransitionHistory\": false}, " +
            "\"skills\": " + skillsJson + "}";

        return new Expectation
        {
            HttpRequest = new HttpRequest { Method = "GET", Path = _agentCardPath },
            HttpResponse = new HttpResponse
            {
                StatusCode = 200,
                Headers = new Dictionary<string, List<string>>
                {
                    ["Content-Type"] = new() { "application/json" }
                },
                Body = agentCardJson
            }
        };
    }

    private Expectation BuildTasksSendExpectation()
    {
        var resultJson = BuildTaskResultJson(_defaultTaskResponse, false);
        return VelocityResponse(JsonRpcRequest("tasks/send"), resultJson);
    }

    private Expectation BuildTasksGetExpectation()
    {
        var resultJson = BuildTaskResultJson(_defaultTaskResponse, false);
        return VelocityResponse(JsonRpcRequest("tasks/get"), resultJson);
    }

    private Expectation BuildTasksCancelExpectation()
    {
        const string resultJson = "{\"id\": \"mock-task-id\", \"status\": {\"state\": \"canceled\"}}";
        return VelocityResponse(JsonRpcRequest("tasks/cancel"), resultJson);
    }

    private Expectation BuildStreamingExpectation()
    {
        var text = EscapeJson(_defaultTaskResponse);
        const string taskId = "mock-task-id";

        // A2A streaming: each SSE event data is a JSON-RPC 2.0 response envelope wrapping a
        // status-update or artifact-update. The JSON-RPC id is not known at build time, so a
        // stable placeholder is used (streaming clients correlate by stream).
        var statusWorking = new SseEvent
        {
            Event = "message",
            Data = "{\"jsonrpc\": \"2.0\", \"id\": \"1\", \"result\": " +
                   "{\"taskId\": \"" + taskId + "\", \"kind\": \"status-update\", " +
                   "\"status\": {\"state\": \"working\"}, \"final\": false}}"
        };

        var artifactUpdate = new SseEvent
        {
            Event = "message",
            Data = "{\"jsonrpc\": \"2.0\", \"id\": \"1\", \"result\": " +
                   "{\"taskId\": \"" + taskId + "\", \"kind\": \"artifact-update\", " +
                   "\"artifact\": {\"parts\": [{\"type\": \"text\", \"text\": \"" + text + "\"}]}}}"
        };

        var statusCompleted = new SseEvent
        {
            Event = "message",
            Data = "{\"jsonrpc\": \"2.0\", \"id\": \"1\", \"result\": " +
                   "{\"taskId\": \"" + taskId + "\", \"kind\": \"status-update\", " +
                   "\"status\": {\"state\": \"completed\"}, \"final\": true}}"
        };

        return new Expectation
        {
            HttpRequest = JsonRpcRequest(_streamingMethod),
            HttpSseResponse = new HttpSseResponse
            {
                StatusCode = 200,
                Events = new List<SseEvent> { statusWorking, artifactUpdate, statusCompleted },
                CloseConnection = true
            }
        };
    }

    private Expectation BuildPushNotificationConfigExpectation()
    {
        // Echo the registered push-notification config back as the JSON-RPC result.
        var resultJson = "{\"url\": \"" + EscapeVelocity(EscapeJson(_pushNotificationUrl)) + "\"}";
        return VelocityResponse(JsonRpcRequest("tasks/pushNotificationConfig/set"), resultJson);
    }

    private Expectation BuildPushNotificationDeliveryExpectation()
    {
        // When push notifications are configured, a tasks/send both returns the JSON-RPC task
        // response to the caller AND POSTs the completed task (the push-notification payload) to
        // the configured webhook URL. Modelled with an override-forwarded-request: the request
        // override targets the webhook (literal body), and a Velocity response template produces
        // the caller's JSON-RPC response so the request's id is echoed back.
        var target = WebhookTarget.Parse(_pushNotificationUrl!);

        // Literal webhook POST body — no Velocity engine runs over a request override, so only
        // JSON escaping is applied. The push payload carries no JSON-RPC id (server-initiated).
        var pushBody = "{\"jsonrpc\": \"2.0\", \"result\": " + BuildTaskResultJsonRaw(_defaultTaskResponse, false) + "}";

        var webhookRequest = new HttpRequest
        {
            Method = "POST",
            Path = target.Path,
            SocketAddress = new SocketAddress
            {
                Host = target.Host,
                Port = target.Port,
                Scheme = target.Secure ? SocketScheme.HTTPS : SocketScheme.HTTP
            },
            Secure = target.Secure,
            Headers = new Dictionary<string, List<string>>
            {
                ["Host"] = new() { target.HostHeader() },
                ["Content-Type"] = new() { "application/json" }
            },
            Body = pushBody
        };

        // Caller response — a Velocity template so $!{request.jsonRpcRawId} echoes the request id,
        // matching the non-push tasks/send contract.
        var clientResponseTemplate = new HttpTemplate
        {
            TemplateType = TemplateType.VELOCITY,
            Template = VelocityJsonRpcResponse(BuildTaskResultJson(_defaultTaskResponse, false))
        };

        return new Expectation
        {
            HttpRequest = JsonRpcRequest("tasks/send"),
            HttpOverrideForwardedRequest = new HttpOverrideForwardedRequest
            {
                HttpRequest = webhookRequest,
                ResponseTemplate = clientResponseTemplate
            }
        };
    }

    private Expectation BuildCustomTaskHandler(A2aTaskHandler handler)
    {
        var escapedPattern = EscapeMessagePattern(handler.MessagePattern);
        var jsonPathBody = "$[?(@.method == 'tasks/send' && @.params.message.parts[0].text =~ /" + escapedPattern + "/)]";
        var resultJson = BuildTaskResultJson(handler.ResponseText, handler.IsError);
        return VelocityResponse(JsonPathRequest(jsonPathBody), resultJson);
    }

    /// <summary>
    /// Escape a user-supplied regular expression so it can be inlined between the <c>/</c>
    /// delimiters of a JsonPath regex literal without breaking out of it. Performs a single
    /// left-to-right pass that PRESERVES existing regex escape sequences (so <c>\d</c> stays
    /// <c>\d</c> and <c>\/</c> stays <c>\/</c>) and only neutralises delimiter breakout:
    /// a bare <c>/</c> becomes <c>\/</c>, a trailing lone backslash is doubled so it cannot
    /// escape the closing delimiter, newline/CR are turned into <c>\n</c>/<c>\r</c>, and NUL
    /// is stripped. For every normal pattern the output is byte-identical to the previous
    /// inline escaping — only breakout cases change.
    /// </summary>
    internal static string EscapeMessagePattern(string pattern)
    {
        var sb = new System.Text.StringBuilder(pattern.Length);
        for (int i = 0; i < pattern.Length; i++)
        {
            char c = pattern[i];
            if (c == '\\')
            {
                // Preserve an existing escape sequence verbatim (e.g. \d, \/, \\), but if the
                // backslash is the LAST character double it so it cannot escape the closing '/'.
                if (i + 1 < pattern.Length)
                {
                    sb.Append('\\').Append(pattern[++i]);
                }
                else
                {
                    sb.Append("\\\\");
                }
            }
            else if (c == '/')
            {
                sb.Append("\\/");
            }
            else if (c == '\n')
            {
                sb.Append("\\n");
            }
            else if (c == '\r')
            {
                sb.Append("\\r");
            }
            else if (c == '\0')
            {
                // strip NUL
            }
            else
            {
                sb.Append(c);
            }
        }
        return sb.ToString();
    }

    // --- result JSON helpers -----------------------------------------------

    private static string BuildTaskResultJson(string responseText, bool isError)
        // For Velocity-templated response bodies: the text must survive the Velocity engine, so
        // metacharacters are escaped here and un-escaped by the template engine at response time.
        => TaskResultJson(EscapeVelocity(EscapeJson(responseText))!, isError);

    private static string BuildTaskResultJsonRaw(string responseText, bool isError)
        // For literal (non-templated) response bodies (e.g. the webhook POST payload), where no
        // Velocity engine runs, only JSON escaping is applied — Velocity escaping would corrupt
        // any '$' / '#' into "${esc.d}" / "${esc.h}".
        => TaskResultJson(EscapeJson(responseText), isError);

    private static string TaskResultJson(string escapedText, bool isError)
    {
        var state = isError ? "failed" : "completed";
        return "{\"id\": \"mock-task-id\", " +
               "\"status\": {\"state\": \"" + state + "\"}, " +
               "\"artifacts\": [{\"parts\": [{\"type\": \"text\", \"text\": \"" + escapedText + "\"}]}]}";
    }

    private static string VelocityJsonRpcResponse(string resultJson) =>
        "{\"statusCode\": 200, " +
        "\"headers\": [{\"name\": \"Content-Type\", \"values\": [\"application/json\"]}], " +
        "\"body\": {\"jsonrpc\": \"2.0\", \"result\": " + resultJson + ", \"id\": $!{request.jsonRpcRawId}}}";

    // --- escaping helpers (ported 1:1 for byte-identical templates) --------

    private static string Bool(bool value) => value ? "true" : "false";

    /// <summary>
    /// JSON-escape a string for inlining inside a JSON string literal, returning the contents
    /// WITHOUT the surrounding quotes. Mirrors Jackson's <c>writeValueAsString</c> then stripping
    /// the outer quotes.
    /// </summary>
    internal static string EscapeJson(string? value)
    {
        if (value == null)
            return "";
        var quoted = JsonSerializer.Serialize(value);
        return quoted.Substring(1, quoted.Length - 2);
    }

    /// <summary>
    /// Escape Velocity meta-characters so literal <c>$</c> and <c>#</c> are not interpreted as
    /// Velocity references/directives. Shares the MCP builder's rules, including backslash runs
    /// before <c>$</c>, <c>#</c> or the end of the value.
    /// </summary>
    internal static string? EscapeVelocity(string? value) => MockServer.Client.Mcp.McpMockBuilder.EscapeVelocity(value);

    // --- internal definition holders ---------------------------------------

    internal sealed class A2aSkillDefinition
    {
        public string Id { get; }
        public string? Name { get; set; }
        public string? Description { get; set; }
        public List<string> Tags { get; } = new();
        public List<string> Examples { get; } = new();

        public A2aSkillDefinition(string id) => Id = id;
    }

    internal sealed class A2aTaskHandler
    {
        public string MessagePattern { get; }
        public string ResponseText { get; }
        public bool IsError { get; }

        public A2aTaskHandler(string messagePattern, string responseText, bool isError)
        {
            MessagePattern = messagePattern;
            ResponseText = responseText;
            IsError = isError;
        }
    }

    internal sealed class WebhookTarget
    {
        public string Host { get; }
        public int Port { get; }
        public bool Secure { get; }
        public string Path { get; }

        private WebhookTarget(string host, int port, bool secure, string path)
        {
            Host = host;
            Port = port;
            Secure = secure;
            Path = path;
        }

        public string HostHeader() => Host + ":" + Port;

        public static WebhookTarget Parse(string url)
        {
            var uri = new Uri(url, UriKind.Absolute);
            var secure = string.Equals(uri.Scheme, "https", StringComparison.OrdinalIgnoreCase);
            var host = uri.Host;
            if (string.IsNullOrEmpty(host))
                throw new ArgumentException("Invalid push-notification webhook URL (no host): " + url);
            var port = uri.IsDefaultPort || uri.Port == -1 ? (secure ? 443 : 80) : uri.Port;
            var path = uri.AbsolutePath;
            if (string.IsNullOrEmpty(path))
                path = "/";
            return new WebhookTarget(host, port, secure, path);
        }
    }
}

// ---------------------------------------------------------------------------
// Nested fluent sub-builders
// ---------------------------------------------------------------------------

/// <summary>Sub-builder for an A2A skill advertised on the agent card. Call <see cref="And"/> to register it.</summary>
public sealed class A2aSkillBuilder
{
    private readonly A2aMockBuilder _parent;
    private readonly A2aMockBuilder.A2aSkillDefinition _skill;

    internal A2aSkillBuilder(A2aMockBuilder parent, string id)
    {
        _parent = parent;
        _skill = new A2aMockBuilder.A2aSkillDefinition(id);
    }

    public A2aSkillBuilder WithName(string name)
    {
        _skill.Name = name;
        return this;
    }

    public A2aSkillBuilder WithDescription(string description)
    {
        _skill.Description = description;
        return this;
    }

    public A2aSkillBuilder WithTag(string tag)
    {
        _skill.Tags.Add(tag);
        return this;
    }

    public A2aSkillBuilder WithExample(string example)
    {
        _skill.Examples.Add(example);
        return this;
    }

    public A2aMockBuilder And()
    {
        _parent.AddSkill(_skill);
        return _parent;
    }
}

/// <summary>Sub-builder for a custom <c>tasks/send</c> message-matching handler. Call <see cref="And"/> to register it.</summary>
public sealed class A2aTaskHandlerBuilder
{
    private readonly A2aMockBuilder _parent;
    private string _messagePattern = ".*";
    private string _responseText = "Task completed";
    private bool _isError;

    internal A2aTaskHandlerBuilder(A2aMockBuilder parent) => _parent = parent;

    public A2aTaskHandlerBuilder MatchingMessage(string pattern)
    {
        _messagePattern = pattern;
        return this;
    }

    public A2aTaskHandlerBuilder RespondingWith(string text, bool isError = false)
    {
        _responseText = text;
        _isError = isError;
        return this;
    }

    public A2aMockBuilder And()
    {
        _parent.AddTaskHandler(new A2aMockBuilder.A2aTaskHandler(_messagePattern, _responseText, _isError));
        return _parent;
    }
}
