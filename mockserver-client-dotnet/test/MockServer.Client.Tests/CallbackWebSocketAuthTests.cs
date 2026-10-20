using System.Net;
using System.Net.Sockets;
using System.Security.Cryptography;
using System.Text;
using FluentAssertions;
using Xunit;

namespace MockServer.Client.Tests;

/// <summary>
/// The control-plane bearer token rides on the callback/breakpoint WebSocket upgrade,
/// checked against a minimal in-process WebSocket server that records the upgrade request.
/// </summary>
public class CallbackWebSocketAuthTests
{
    private sealed class RecordingCallbackServer : IDisposable
    {
        private const string WebSocketGuid = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";
        private readonly TcpListener _listener = new(IPAddress.Loopback, 0);
        private readonly TaskCompletionSource<Dictionary<string, string>> _upgradeHeaders =
            new(TaskCreationOptions.RunContinuationsAsynchronously);
        private TcpClient? _connection;

        public RecordingCallbackServer()
        {
            _listener.Start();
            _ = Task.Run(AcceptAsync);
        }

        public int Port => ((IPEndPoint)_listener.LocalEndpoint).Port;

        public Task<Dictionary<string, string>> UpgradeHeaders => _upgradeHeaders.Task;

        private async Task AcceptAsync()
        {
            try
            {
                _connection = await _listener.AcceptTcpClientAsync().ConfigureAwait(false);
                var stream = _connection.GetStream();
                var request = new StringBuilder();
                var buffer = new byte[1];
                while (!request.ToString().EndsWith("\r\n\r\n", StringComparison.Ordinal))
                {
                    if (await stream.ReadAsync(buffer, 0, 1).ConfigureAwait(false) == 0) return;
                    request.Append((char)buffer[0]);
                }
                var headers = new Dictionary<string, string>(StringComparer.OrdinalIgnoreCase);
                foreach (var line in request.ToString().Split("\r\n").Skip(1))
                {
                    var colon = line.IndexOf(':');
                    if (colon > 0) headers[line[..colon].Trim()] = line[(colon + 1)..].Trim();
                }
                var accept = Convert.ToBase64String(
                    SHA1.HashData(Encoding.ASCII.GetBytes(headers["Sec-WebSocket-Key"] + WebSocketGuid)));
                var response = Encoding.ASCII.GetBytes(
                    "HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\nConnection: Upgrade\r\n" +
                    $"Sec-WebSocket-Accept: {accept}\r\n\r\n");
                await stream.WriteAsync(response).ConfigureAwait(false);
                var registration = Encoding.UTF8.GetBytes(
                    "{\"type\":\"org.mockserver.serialization.model.WebSocketClientIdDTO\"," +
                    "\"value\":\"{\\\"clientId\\\":\\\"recorded-client\\\"}\"}");
                var frame = registration.Length < 126
                    ? new List<byte> { 0x81, (byte)registration.Length }
                    : new List<byte> { 0x81, 126, (byte)(registration.Length >> 8), (byte)registration.Length };
                frame.AddRange(registration);
                await stream.WriteAsync(frame.ToArray()).ConfigureAwait(false);
                _upgradeHeaders.TrySetResult(headers);
            }
            catch (Exception e)
            {
                _upgradeHeaders.TrySetException(e);
            }
        }

        /// <summary>Closing the TCP connection first keeps the client's close handshake from waiting on a reply.</summary>
        public void Dispose()
        {
            _connection?.Dispose();
            _listener.Stop();
        }
    }

    private static async Task<Dictionary<string, string>> UpgradeHeadersAsync(Func<string>? bearerTokenSupplier)
    {
        using var server = new RecordingCallbackServer();
        using var ws = new BreakpointWebSocketClient();
        await ws.ConnectAsync($"http://127.0.0.1:{server.Port}", bearerTokenSupplier);
        ws.ClientId.Should().Be("recorded-client");
        var headers = await server.UpgradeHeaders.WaitAsync(TimeSpan.FromSeconds(5));
        server.Dispose();
        return headers;
    }

    [Fact]
    public async Task Upgrade_Carries_BearerToken_When_Supplied()
    {
        var headers = await UpgradeHeadersAsync(() => "my-jwt-token");
        headers.Should().ContainKey("Authorization").WhoseValue.Should().Be("Bearer my-jwt-token");
    }

    [Fact]
    public async Task Upgrade_Has_No_Authorization_Without_A_Token()
    {
        (await UpgradeHeadersAsync(null)).Should().NotContainKey("Authorization");
        (await UpgradeHeadersAsync(() => "")).Should().NotContainKey("Authorization");
    }

    [Fact]
    public async Task Client_Sends_Its_ControlPlane_BearerToken_On_The_Callback_WebSocket()
    {
        using var server = new RecordingCallbackServer();
        using var client = new MockServerClient("127.0.0.1", server.Port).WithControlPlaneBearerToken("client-token");
        await client.EnsureBreakpointWsAsync();
        var headers = await server.UpgradeHeaders.WaitAsync(TimeSpan.FromSeconds(5));
        server.Dispose();
        headers.Should().ContainKey("Authorization").WhoseValue.Should().Be("Bearer client-token");
    }

    [Fact]
    public async Task Client_Without_A_Token_Sends_No_Authorization_On_The_Callback_WebSocket()
    {
        using var server = new RecordingCallbackServer();
        using var client = new MockServerClient("127.0.0.1", server.Port);
        await client.EnsureBreakpointWsAsync();
        var headers = await server.UpgradeHeaders.WaitAsync(TimeSpan.FromSeconds(5));
        server.Dispose();
        headers.Should().NotContainKey("Authorization");
    }
}
