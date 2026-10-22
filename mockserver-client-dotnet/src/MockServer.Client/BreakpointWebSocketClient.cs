using System.Collections.Concurrent;
using System.Net.WebSockets;
using System.Text;
using System.Text.Json;
using System.Text.Json.Nodes;
using System.Text.Json.Serialization;
using MockServer.Client.Models;

namespace MockServer.Client;

/// <summary>
/// Handler for REQUEST phase breakpoints.
/// Receives the paused request; return a modified request (continue/modify) or
/// a response object with a "statusCode" key (abort). Return null to auto-continue.
/// </summary>
public delegate JsonObject? BreakpointRequestHandler(JsonObject request);

/// <summary>
/// Handler for RESPONSE phase breakpoints.
/// Receives the paused request and response; return a response.
/// Return null to auto-continue with the original response.
/// </summary>
public delegate JsonObject? BreakpointResponseHandler(JsonObject request, JsonObject response);

/// <summary>
/// Handler for stream frame breakpoints (RESPONSE_STREAM / INBOUND_STREAM).
/// Receives the paused frame; return a StreamFrameDecision.
/// Return null to auto-continue.
/// </summary>
public delegate StreamFrameDecision? BreakpointStreamFrameHandler(PausedStreamFrame frame);

/// <summary>
/// Handler for an object (closure) response callback.
/// Receives the matched request; returns the response to send back.
/// </summary>
public delegate JsonObject ObjectResponseCallbackHandler(JsonObject request);

/// <summary>
/// Handler for an object (closure) forward callback.
/// Receives the matched request; returns the (possibly modified) request to forward.
/// </summary>
public delegate JsonObject ObjectForwardCallbackHandler(JsonObject request);

/// <summary>
/// Internal WebSocket client for breakpoint callback resolution.
/// Connects to <c>/_mockserver_callback_websocket</c> and dispatches
/// paused items to per-breakpoint-id handlers.
/// </summary>
internal sealed class BreakpointWebSocketClient : IDisposable
{
    private ClientWebSocket? _ws;
    private CancellationTokenSource? _cts;
    private Task? _readLoopTask;
    private readonly SemaphoreSlim _sendLock = new(1, 1);
    private volatile bool _disposed;
    private volatile bool _dead; // set when the read loop exits

    internal string? ClientId { get; private set; }

    /// <summary>
    /// True when the read loop has exited (connection no longer usable).
    /// Allows the client to transparently re-establish the WS on the next call.
    /// </summary>
    internal bool IsDead => _dead;

    private readonly ConcurrentDictionary<string, BreakpointRequestHandler> _requestHandlers = new();
    private readonly ConcurrentDictionary<string, BreakpointResponseHandler> _responseHandlers = new();
    private readonly ConcurrentDictionary<string, BreakpointStreamFrameHandler> _streamFrameHandlers = new();

    // Object/closure callbacks. A request frame without an X-MockServer-BreakpointId
    // header is an object-callback dispatch (not a breakpoint) and is routed here.
    private volatile ObjectResponseCallbackHandler? _objectResponseHandler;
    private volatile ObjectForwardCallbackHandler? _objectForwardHandler;

    private static readonly JsonSerializerOptions JsonOptions = new()
    {
        DefaultIgnoreCondition = System.Text.Json.Serialization.JsonIgnoreCondition.WhenWritingNull,
        PropertyNamingPolicy = JsonNamingPolicy.CamelCase,
        // Decode/encode paused-exchange requests whose matcher values use the nottable object form. See
        // HttpRequestConverter.
        Converters = { new Models.HttpRequestConverter() }
    };

    /// <summary>
    /// Connect and register. A non-empty token from <paramref name="bearerTokenSupplier"/> is
    /// sent as <c>Authorization: Bearer &lt;token&gt;</c> on the upgrade request, otherwise
    /// <paramref name="basicAuthorization"/> (a whole <c>Basic ...</c> value) when given, and
    /// <paramref name="tlsOptions"/> supplies the client certificate and CA trust for a wss connection.
    /// </summary>
    internal async Task ConnectAsync(string baseUrl, Func<string>? bearerTokenSupplier = null, MockServerTlsOptions? tlsOptions = null, string? basicAuthorization = null)
    {
        var uri = new Uri(baseUrl);
        var scheme = uri.Scheme == "https" ? "wss" : "ws";
        var path = uri.AbsolutePath.TrimEnd('/');
        var wsUri = new Uri($"{scheme}://{uri.Host}:{uri.Port}{path}/_mockserver_callback_websocket");

        _ws = new ClientWebSocket();
        tlsOptions?.ApplyTo(_ws.Options);
        var token = bearerTokenSupplier?.Invoke();
        if (!string.IsNullOrEmpty(token))
            _ws.Options.SetRequestHeader("Authorization", "Bearer " + token);
        else if (basicAuthorization != null)
            _ws.Options.SetRequestHeader("Authorization", basicAuthorization);
        _cts = new CancellationTokenSource();

        // Apply a 30-second handshake timeout
        using var connectCts = CancellationTokenSource.CreateLinkedTokenSource(_cts.Token);
        connectCts.CancelAfter(TimeSpan.FromSeconds(30));
        await _ws.ConnectAsync(wsUri, connectCts.Token).ConfigureAwait(false);

        // Read registration reply
        var buffer = new byte[64 * 1024];
        var result = await _ws.ReceiveAsync(new ArraySegment<byte>(buffer), _cts.Token).ConfigureAwait(false);
        var regMessage = Encoding.UTF8.GetString(buffer, 0, result.Count);

        var envelope = JsonSerializer.Deserialize<WsEnvelope>(regMessage, JsonOptions);
        if (envelope?.Type == "org.mockserver.serialization.model.WebSocketClientIdDTO" && envelope.Value != null)
        {
            var regDTO = JsonSerializer.Deserialize<WsClientIdDTO>(envelope.Value, JsonOptions);
            ClientId = regDTO?.ClientId;
        }

        if (string.IsNullOrEmpty(ClientId))
            throw new InvalidOperationException("Failed to obtain clientId from breakpoint WebSocket registration");

        // Start read loop
        _readLoopTask = Task.Run(() => ReadLoopAsync(_cts.Token));
    }

    private async Task ReadLoopAsync(CancellationToken ct)
    {
        var buffer = new byte[256 * 1024];
        try
        {
            while (!ct.IsCancellationRequested && _ws?.State == WebSocketState.Open)
            {
                WebSocketReceiveResult result;
                int totalBytes = 0;
                // Read a complete message (may be fragmented)
                using var ms = new System.IO.MemoryStream();
                do
                {
                    result = await _ws.ReceiveAsync(new ArraySegment<byte>(buffer), ct).ConfigureAwait(false);
                    ms.Write(buffer, 0, result.Count);
                    totalBytes += result.Count;
                } while (!result.EndOfMessage);

                if (result.MessageType == WebSocketMessageType.Close)
                    break;

                var messageText = Encoding.UTF8.GetString(ms.GetBuffer(), 0, totalBytes);
                ProcessMessage(messageText);
            }
        }
        catch (OperationCanceledException)
        {
            // Normal shutdown
        }
        catch (WebSocketException ex)
        {
            System.Diagnostics.Trace.TraceWarning(
                "mockserver: breakpoint ws read loop terminated unexpectedly: {0}", ex.Message);
        }
        finally
        {
            _dead = true;
        }
    }

    internal void ProcessMessage(string messageText)
    {
        WsEnvelope? envelope;
        try
        {
            envelope = JsonSerializer.Deserialize<WsEnvelope>(messageText, JsonOptions);
        }
        catch
        {
            return;
        }

        if (envelope?.Type == null || envelope.Value == null) return;

        switch (envelope.Type)
        {
            case "org.mockserver.model.HttpRequest":
                HandleRequest(envelope.Value);
                break;
            case "org.mockserver.model.HttpRequestAndHttpResponse":
                HandleResponse(envelope.Value);
                break;
            case "org.mockserver.serialization.model.PausedStreamFrameDTO":
                HandleStreamFrame(envelope.Value);
                break;
            case "org.mockserver.serialization.model.WebSocketClientIdDTO":
                // Already handled during connect
                break;
        }
    }

    private void HandleRequest(string valueJson)
    {
        JsonObject? request;
        try { request = JsonNode.Parse(valueJson)?.AsObject(); }
        catch { return; }
        if (request == null) return;

        var correlationId = ExtractHeader(request, "WebSocketCorrelationId");
        var breakpointId = ExtractHeader(request, "X-MockServer-BreakpointId");

        // A request frame WITHOUT a breakpoint id is an object-callback dispatch.
        if (string.IsNullOrEmpty(breakpointId))
        {
            HandleObjectCallback(request, correlationId);
            return;
        }

        JsonObject? result = null;
        if (_requestHandlers.TryGetValue(breakpointId!, out var handler))
        {
            try { result = handler(request); }
            catch { result = null; }
        }

        // Auto-continue with original request
        if (result == null) result = request;

        // Determine type: if it has statusCode, it's a response (abort)
        var typeName = result.ContainsKey("statusCode")
            ? "org.mockserver.model.HttpResponse"
            : "org.mockserver.model.HttpRequest";

        SetHeader(result, "WebSocketCorrelationId", correlationId);
        SendEnvelope(typeName, result.ToJsonString());
    }

    /// <summary>
    /// Dispatch an object-callback request frame (no breakpoint id) to the registered
    /// response or forward closure and reply echoing the WebSocketCorrelationId header.
    /// A response handler takes precedence over a forward handler if both are set.
    /// </summary>
    private void HandleObjectCallback(JsonObject request, string? correlationId)
    {
        var responseHandler = _objectResponseHandler;
        if (responseHandler != null)
        {
            JsonObject? response = null;
            try { response = responseHandler(request); }
            catch { response = null; }
            // On handler failure, fall back to a minimal 200 so the request is not left hanging.
            response ??= new JsonObject { ["statusCode"] = 200 };
            SetHeader(response, "WebSocketCorrelationId", correlationId);
            SendEnvelope("org.mockserver.model.HttpResponse", response.ToJsonString());
            return;
        }

        var forwardHandler = _objectForwardHandler;
        if (forwardHandler != null)
        {
            JsonObject? forward = null;
            try { forward = forwardHandler(request); }
            catch { forward = null; }
            // On handler failure, forward the request unchanged.
            forward ??= request;
            SetHeader(forward, "WebSocketCorrelationId", correlationId);
            SendEnvelope("org.mockserver.model.HttpRequest", forward.ToJsonString());
            return;
        }

        // No object-callback handler registered: echo the request back unchanged.
        SetHeader(request, "WebSocketCorrelationId", correlationId);
        SendEnvelope("org.mockserver.model.HttpRequest", request.ToJsonString());
    }

    private void HandleResponse(string valueJson)
    {
        JsonObject? reqAndResp;
        try { reqAndResp = JsonNode.Parse(valueJson)?.AsObject(); }
        catch { return; }
        if (reqAndResp == null) return;

        var httpRequest = reqAndResp["httpRequest"]?.AsObject();
        var httpResponse = reqAndResp["httpResponse"]?.AsObject();

        var correlationId = httpRequest != null ? ExtractHeader(httpRequest, "WebSocketCorrelationId") : null;
        var breakpointId = httpRequest != null ? ExtractHeader(httpRequest, "X-MockServer-BreakpointId") : null;

        JsonObject? result = null;
        if (!string.IsNullOrEmpty(breakpointId) && _responseHandlers.TryGetValue(breakpointId!, out var handler))
        {
            try { result = handler(httpRequest!, httpResponse ?? new JsonObject()); }
            catch { result = null; }
        }

        if (result == null)
            result = httpResponse ?? new JsonObject();

        SetHeader(result, "WebSocketCorrelationId", correlationId);
        SendEnvelope("org.mockserver.model.HttpResponse", result.ToJsonString());
    }

    private void HandleStreamFrame(string valueJson)
    {
        PausedStreamFrame? frame;
        try { frame = JsonSerializer.Deserialize<PausedStreamFrame>(valueJson, JsonOptions); }
        catch { return; }
        if (frame == null) return;

        StreamFrameDecision? decision = null;
        if (!string.IsNullOrEmpty(frame.BreakpointId) && _streamFrameHandlers.TryGetValue(frame.BreakpointId!, out var handler))
        {
            try { decision = handler(frame); }
            catch { decision = null; }
        }

        if (decision == null)
            decision = StreamFrameDecision.Continue(frame.CorrelationId!);
        else
            decision.CorrelationId = frame.CorrelationId;

        var decisionJson = JsonSerializer.Serialize(decision, JsonOptions);
        SendEnvelope("org.mockserver.serialization.model.StreamFrameDecisionDTO", decisionJson);
    }

    internal void SetRequestHandler(string breakpointId, BreakpointRequestHandler handler)
        => _requestHandlers[breakpointId] = handler;

    internal void SetResponseHandler(string breakpointId, BreakpointResponseHandler handler)
        => _responseHandlers[breakpointId] = handler;

    internal void SetStreamFrameHandler(string breakpointId, BreakpointStreamFrameHandler handler)
        => _streamFrameHandlers[breakpointId] = handler;

    internal void SetObjectResponseHandler(ObjectResponseCallbackHandler handler)
        => _objectResponseHandler = handler;

    internal void SetObjectForwardHandler(ObjectForwardCallbackHandler handler)
        => _objectForwardHandler = handler;

    internal void RemoveHandlers(string breakpointId)
    {
        _requestHandlers.TryRemove(breakpointId, out _);
        _responseHandlers.TryRemove(breakpointId, out _);
        _streamFrameHandlers.TryRemove(breakpointId, out _);
    }

    internal void ClearHandlers()
    {
        _requestHandlers.Clear();
        _responseHandlers.Clear();
        _streamFrameHandlers.Clear();
        _objectResponseHandler = null;
        _objectForwardHandler = null;
    }

    /// <summary>
    /// Test-only hook: when set, every outgoing envelope is delivered here instead of
    /// being written to the socket, so unit tests can assert the reply (type, value JSON).
    /// </summary>
    private Action<string, string>? _sendInterceptor;

    internal void SetSendInterceptorForTest(Action<string, string> interceptor)
        => _sendInterceptor = interceptor;

    private void SendEnvelope(string type, string valueJson)
    {
        var interceptor = _sendInterceptor;
        if (interceptor != null)
        {
            interceptor(type, valueJson);
            return;
        }

        if (_disposed || _ws?.State != WebSocketState.Open) return;

        var envelope = new WsEnvelope { Type = type, Value = valueJson };
        var json = JsonSerializer.Serialize(envelope, JsonOptions);
        var bytes = Encoding.UTF8.GetBytes(json);

        _sendLock.Wait();
        try
        {
            if (_disposed || _ws?.State != WebSocketState.Open) return;
            var token = _cts?.Token ?? CancellationToken.None;
            _ws!.SendAsync(new ArraySegment<byte>(bytes), WebSocketMessageType.Text, true, token)
                .ConfigureAwait(false).GetAwaiter().GetResult();
        }
        catch (ObjectDisposedException)
        {
            // Socket disposed concurrently — ignore
        }
        catch (OperationCanceledException)
        {
            // Cancellation during dispose — ignore
        }
        catch
        {
            // Best effort; auto-continue on failure
        }
        finally
        {
            _sendLock.Release();
        }
    }

    private static string? ExtractHeader(JsonObject obj, string name)
    {
        if (!obj.ContainsKey("headers")) return null;
        var headers = obj["headers"]?.AsObject();
        if (headers == null) return null;

        foreach (var kvp in headers)
        {
            if (string.Equals(kvp.Key, name, StringComparison.OrdinalIgnoreCase))
            {
                if (kvp.Value is JsonArray arr && arr.Count > 0)
                    return arr[0]?.GetValue<string>();
                if (kvp.Value is JsonValue jv)
                    return jv.GetValue<string>();
            }
        }
        return null;
    }

    private static void SetHeader(JsonObject obj, string name, string? value)
    {
        if (string.IsNullOrEmpty(value)) return;

        if (!obj.ContainsKey("headers"))
            obj["headers"] = new JsonObject();

        var headers = obj["headers"]!.AsObject();
        // Remove existing (case-insensitive)
        string? existingKey = null;
        foreach (var kvp in headers)
        {
            if (string.Equals(kvp.Key, name, StringComparison.OrdinalIgnoreCase))
            {
                existingKey = kvp.Key;
                break;
            }
        }
        if (existingKey != null) headers.Remove(existingKey);
        headers[name] = new JsonArray(JsonValue.Create(value));
    }

    /// <summary>
    /// How long <see cref="Dispose"/> waits for in-flight sends and for the peer to answer
    /// the close handshake before aborting the socket.
    /// </summary>
    internal TimeSpan CloseTimeout { get; set; } = TimeSpan.FromSeconds(5);

    public void Dispose()
    {
        if (_disposed) return;
        _disposed = true;
        _cts?.Cancel();

        // Serialize close with any in-flight sends
        var locked = _sendLock.Wait(CloseTimeout);
        try
        {
            if (locked && _ws?.State == WebSocketState.Open)
            {
                using var closeCts = new CancellationTokenSource(CloseTimeout);
                _ws.CloseAsync(WebSocketCloseStatus.NormalClosure, "", closeCts.Token).Wait(CloseTimeout);
            }
        }
        catch { }
        finally
        {
            if (locked) _sendLock.Release();
        }

        _ws?.Abort();
        _ws?.Dispose();
        _cts?.Dispose();
        // a sender still holding the lock releases it on its way out
        if (locked) _sendLock.Dispose();
    }

    // --- Internal DTOs ---

    internal sealed class WsEnvelope
    {
        [JsonPropertyName("type")]
        public string? Type { get; set; }

        [JsonPropertyName("value")]
        public string? Value { get; set; }
    }

    internal sealed class WsClientIdDTO
    {
        [JsonPropertyName("clientId")]
        public string? ClientId { get; set; }
    }
}
