using System.Diagnostics;
using System.Net;
using System.Net.Sockets;
using System.Security.Cryptography;
using System.Text;
using System.Text.Json;
using System.Text.Json.Nodes;
using FluentAssertions;
using Xunit;

namespace MockServer.Client.Tests;

/// <summary>
/// Disposing the callback WebSocket client is bounded even when the peer never answers the close handshake.
/// </summary>
public class CallbackWebSocketDisposeTests
{
    /// <summary>Completes the upgrade and registration, then never reads, replies to a close, or disconnects.</summary>
    private sealed class SilentPeerServer : IDisposable
    {
        private const string WebSocketGuid = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";
        private readonly TcpListener _listener = new(IPAddress.Loopback, 0);
        private readonly TaskCompletionSource<NetworkStream> _registered =
            new(TaskCreationOptions.RunContinuationsAsynchronously);
        private TcpClient? _connection;

        public SilentPeerServer()
        {
            _listener.Start();
            _ = Task.Run(AcceptAsync);
        }

        public int Port => ((IPEndPoint)_listener.LocalEndpoint).Port;

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
                var key = request.ToString().Split("\r\n")
                    .First(line => line.StartsWith("Sec-WebSocket-Key:", StringComparison.OrdinalIgnoreCase))
                    .Split(':', 2)[1].Trim();
                var accept = Convert.ToBase64String(SHA1.HashData(Encoding.ASCII.GetBytes(key + WebSocketGuid)));
                await stream.WriteAsync(Encoding.ASCII.GetBytes(
                    "HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\nConnection: Upgrade\r\n" +
                    $"Sec-WebSocket-Accept: {accept}\r\n\r\n")).ConfigureAwait(false);
                await WriteTextFrameAsync(stream,
                    "org.mockserver.serialization.model.WebSocketClientIdDTO", "{\"clientId\":\"silent-client\"}");
                _registered.TrySetResult(stream);
            }
            catch (Exception e)
            {
                _registered.TrySetException(e);
            }
        }

        public async Task SendCallbackRequestAsync()
        {
            var stream = await _registered.Task.WaitAsync(TimeSpan.FromSeconds(5));
            await WriteTextFrameAsync(stream, "org.mockserver.model.HttpRequest", "{\"method\":\"GET\",\"path\":\"/slow\"}");
        }

        private static async Task WriteTextFrameAsync(NetworkStream stream, string type, string value)
        {
            var payload = Encoding.UTF8.GetBytes(JsonSerializer.Serialize(new { type, value }));
            var frame = payload.Length < 126
                ? new List<byte> { 0x81, (byte)payload.Length }
                : new List<byte> { 0x81, 126, (byte)(payload.Length >> 8), (byte)payload.Length };
            frame.AddRange(payload);
            await stream.WriteAsync(frame.ToArray()).ConfigureAwait(false);
        }

        public void Dispose()
        {
            _connection?.Dispose();
            _listener.Stop();
        }
    }

    [Fact]
    public async Task Dispose_Returns_Promptly_When_The_Peer_Never_Answers_The_Close()
    {
        using var server = new SilentPeerServer();
        var ws = new BreakpointWebSocketClient { CloseTimeout = TimeSpan.FromMilliseconds(500) };
        await ws.ConnectAsync($"http://127.0.0.1:{server.Port}");
        ws.ClientId.Should().Be("silent-client");

        // A running handler keeps the read loop out of ReceiveAsync, so cancelling it cannot abort the socket.
        var handlerEntered = new SemaphoreSlim(0);
        var releaseHandler = new ManualResetEventSlim(false);
        ws.SetObjectResponseHandler(_ =>
        {
            handlerEntered.Release();
            releaseHandler.Wait(TimeSpan.FromSeconds(30));
            return new JsonObject { ["statusCode"] = 200 };
        });
        try
        {
            await server.SendCallbackRequestAsync();
            (await handlerEntered.WaitAsync(TimeSpan.FromSeconds(5))).Should().BeTrue("the callback handler never ran");

            var stopwatch = Stopwatch.StartNew();
            var dispose = Task.Run(ws.Dispose);
            var finished = await Task.WhenAny(dispose, Task.Delay(TimeSpan.FromSeconds(5)));

            finished.Should().BeSameAs(dispose, "Dispose waited on a close handshake the peer never answers");
            await dispose;
            stopwatch.Elapsed.Should().BeLessThan(TimeSpan.FromSeconds(3));
        }
        finally
        {
            releaseHandler.Set();
        }
    }
}
