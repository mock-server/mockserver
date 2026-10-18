using System.Buffers.Binary;
using System.Net;
using System.Net.Sockets;
using System.Net.WebSockets;
using System.Text;
using System.Text.Json;
using FluentAssertions;
using MockServer.Client.Models;
using Xunit;

namespace MockServer.Client.IntegrationTests;

/// <summary>
/// Drives the WebSocket, DNS, gRPC and callback response actions through a real MockServer and
/// asserts what the server actually sends back: the WebSocket frames, the DNS answer records, the
/// gRPC messages and trailers, and the callback-produced HTTP response.
/// </summary>
public partial class WireBehaviorTests
{
    private static readonly TimeSpan WireTimeout = TimeSpan.FromSeconds(10);

    // ── WebSocket ──────────────────────────────────────────────────────────────────────────

    private static Uri WebSocketUri(string baseUrl, string path)
    {
        var uri = new Uri(baseUrl);
        return new UriBuilder(uri) { Scheme = uri.Scheme == "https" ? "wss" : "ws", Path = path }.Uri;
    }

    private static async Task<(WebSocketMessageType Type, byte[] Payload)> ReceiveMessageAsync(
        ClientWebSocket socket, CancellationToken token)
    {
        using var buffer = new MemoryStream();
        var chunk = new byte[4096];
        WebSocketReceiveResult result;
        do
        {
            result = await socket.ReceiveAsync(chunk, token);
            buffer.Write(chunk, 0, result.Count);
        } while (!result.EndOfMessage);
        return (result.MessageType, buffer.ToArray());
    }

    [SkippableFact]
    public async Task WebSocketResponse_SendsFramesAndAnswersMatchedFrame_OverWire()
    {
        SkipIfNoServer();
        _client!.Reset();

        var binaryPayload = new byte[] { 0x00, 0x7F, 0x80, 0xFF, 0x10 };
        var webSocketResponse = HttpWebSocketResponse.Response()
            .WithSubprotocol("dotnet-wire.v1")
            .WithTextMessage("hello-ws-dotnet")
            .WithBinaryMessage(binaryPayload)
            .Build();
        webSocketResponse.Matchers = new List<WebSocketFrameMatcher>
        {
            new()
            {
                FrameType = "TEXT",
                TextMatcher = "ping-dotnet",
                Responses = new List<WebSocketMessage> { WebSocketMessage.OfText("pong-dotnet") }
            }
        };
        _client.When(
            HttpRequest.Request().WithMethod("GET").WithPath("/ws-wire")
        ).RespondWithWebSocket(webSocketResponse);

        using var cts = new CancellationTokenSource(WireTimeout);
        using var socket = new ClientWebSocket();
        socket.Options.AddSubProtocol("dotnet-wire.v1");
        await socket.ConnectAsync(WebSocketUri(_baseUrl!, "/ws-wire"), cts.Token);

        socket.SubProtocol.Should().Be("dotnet-wire.v1", "the server must agree the registered subprotocol");

        var text = await ReceiveMessageAsync(socket, cts.Token);
        text.Type.Should().Be(WebSocketMessageType.Text);
        Encoding.UTF8.GetString(text.Payload).Should().Be("hello-ws-dotnet");

        var binary = await ReceiveMessageAsync(socket, cts.Token);
        binary.Type.Should().Be(WebSocketMessageType.Binary);
        binary.Payload.Should().Equal(binaryPayload);

        await socket.SendAsync(Encoding.UTF8.GetBytes("ping-dotnet"), WebSocketMessageType.Text, true, cts.Token);
        var reply = await ReceiveMessageAsync(socket, cts.Token);
        reply.Type.Should().Be(WebSocketMessageType.Text);
        Encoding.UTF8.GetString(reply.Payload).Should().Be("pong-dotnet",
            "a TEXT frame matching the registered matcher must be answered with its response");

        await socket.CloseAsync(WebSocketCloseStatus.NormalClosure, "done", cts.Token);
        _client.Reset();
    }

    [SkippableFact]
    public async Task WebSocketResponse_ClosesAfterMessages_OverWire()
    {
        SkipIfNoServer();
        _client!.Reset();

        _client.When(
            HttpRequest.Request().WithMethod("GET").WithPath("/ws-close-wire")
        ).RespondWithWebSocket(
            HttpWebSocketResponse.Response().WithTextMessage("only-frame").WithCloseConnection()
        );

        using var cts = new CancellationTokenSource(WireTimeout);
        using var socket = new ClientWebSocket();
        await socket.ConnectAsync(WebSocketUri(_baseUrl!, "/ws-close-wire"), cts.Token);

        var only = await ReceiveMessageAsync(socket, cts.Token);
        only.Type.Should().Be(WebSocketMessageType.Text);
        Encoding.UTF8.GetString(only.Payload).Should().Be("only-frame");

        var close = await socket.ReceiveAsync(new byte[16], cts.Token);
        close.MessageType.Should().Be(WebSocketMessageType.Close);
        socket.CloseStatus.Should().Be(WebSocketCloseStatus.NormalClosure);

        _client.Reset();
    }

    // ── DNS ────────────────────────────────────────────────────────────────────────────────

    private const ushort DnsTypeA = 1;
    private const ushort DnsTypeMx = 15;
    private const ushort DnsClassIn = 1;

    private sealed record DnsAnswer(string Name, ushort Type, ushort Class, uint Ttl, byte[] Data, byte[] Message, int DataOffset);

    private sealed record DnsReply(ushort Id, ushort Flags, ushort QuestionCount, List<DnsAnswer> Answers);

    /// <summary>
    /// The server's DNS listener port, read from <c>/mockserver/status</c> so the test follows whatever the
    /// server bound. A server started without DNS fails the test under <c>MOCKSERVER_REQUIRE_SERVER=true</c>.
    /// </summary>
    private int DnsPortOrSkip()
    {
        using var httpClient = new HttpClient();
        using var status = httpClient.Send(new HttpRequestMessage(HttpMethod.Put, $"{_baseUrl}/mockserver/status"));
        using var doc = JsonDocument.Parse(status.Content.ReadAsStringAsync().Result);
        if (doc.RootElement.TryGetProperty("dnsPort", out var port) && port.ValueKind == JsonValueKind.Number)
        {
            return port.GetInt32();
        }
        if (Environment.GetEnvironmentVariable("MOCKSERVER_REQUIRE_SERVER") == "true")
        {
            throw new InvalidOperationException(
                "MOCKSERVER_REQUIRE_SERVER=true but MockServer reports no dnsPort; start it with MOCKSERVER_DNS_ENABLED=true.");
        }
        Skip.If(true, "MockServer was started without DNS (MOCKSERVER_DNS_ENABLED); skipping DNS wire test.");
        return 0;
    }

    private static byte[] DnsQuery(ushort id, string name, ushort type)
    {
        using var query = new MemoryStream();
        var header = new byte[12];
        BinaryPrimitives.WriteUInt16BigEndian(header.AsSpan(0), id);
        BinaryPrimitives.WriteUInt16BigEndian(header.AsSpan(2), 0x0100);
        BinaryPrimitives.WriteUInt16BigEndian(header.AsSpan(4), 1);
        query.Write(header);
        foreach (var label in name.Split('.'))
        {
            var bytes = Encoding.ASCII.GetBytes(label);
            query.WriteByte((byte)bytes.Length);
            query.Write(bytes);
        }
        query.WriteByte(0);
        var tail = new byte[4];
        BinaryPrimitives.WriteUInt16BigEndian(tail.AsSpan(0), type);
        BinaryPrimitives.WriteUInt16BigEndian(tail.AsSpan(2), DnsClassIn);
        query.Write(tail);
        return query.ToArray();
    }

    private static string ReadDnsName(byte[] message, ref int offset)
    {
        var labels = new List<string>();
        var position = offset;
        var jumped = false;
        while (true)
        {
            int length = message[position];
            if (length == 0)
            {
                position++;
                break;
            }
            if ((length & 0xC0) == 0xC0)
            {
                var pointer = ((length & 0x3F) << 8) | message[position + 1];
                if (!jumped)
                {
                    offset = position + 2;
                }
                jumped = true;
                position = pointer;
                continue;
            }
            labels.Add(Encoding.ASCII.GetString(message, position + 1, length));
            position += 1 + length;
        }
        if (!jumped)
        {
            offset = position;
        }
        return string.Join('.', labels);
    }

    private static DnsReply ParseDnsReply(byte[] message)
    {
        var id = BinaryPrimitives.ReadUInt16BigEndian(message.AsSpan(0));
        var flags = BinaryPrimitives.ReadUInt16BigEndian(message.AsSpan(2));
        var questions = BinaryPrimitives.ReadUInt16BigEndian(message.AsSpan(4));
        var answerCount = BinaryPrimitives.ReadUInt16BigEndian(message.AsSpan(6));
        var offset = 12;
        for (var i = 0; i < questions; i++)
        {
            ReadDnsName(message, ref offset);
            offset += 4;
        }
        var answers = new List<DnsAnswer>();
        for (var i = 0; i < answerCount; i++)
        {
            var name = ReadDnsName(message, ref offset);
            var type = BinaryPrimitives.ReadUInt16BigEndian(message.AsSpan(offset));
            var dnsClass = BinaryPrimitives.ReadUInt16BigEndian(message.AsSpan(offset + 2));
            var ttl = BinaryPrimitives.ReadUInt32BigEndian(message.AsSpan(offset + 4));
            var length = BinaryPrimitives.ReadUInt16BigEndian(message.AsSpan(offset + 8));
            var dataOffset = offset + 10;
            answers.Add(new DnsAnswer(name, type, dnsClass, ttl, message[dataOffset..(dataOffset + length)], message, dataOffset));
            offset = dataOffset + length;
        }
        return new DnsReply(id, flags, questions, answers);
    }

    private DnsReply SendDnsQuery(int dnsPort, ushort id, string name, ushort type)
    {
        var host = new Uri(_baseUrl!).Host;
        var address = Dns.GetHostAddresses(host).First(a => a.AddressFamily == AddressFamily.InterNetwork);
        using var udp = new UdpClient(AddressFamily.InterNetwork);
        udp.Client.ReceiveTimeout = 2000;
        var query = DnsQuery(id, name, type);
        // UDP may drop a datagram, so resend on a receive timeout; any other error fails at once.
        const int attempts = 3;
        for (var attempt = 1; ; attempt++)
        {
            udp.Send(query, query.Length, new IPEndPoint(address, dnsPort));
            var from = new IPEndPoint(IPAddress.Any, 0);
            try
            {
                return ParseDnsReply(udp.Receive(ref from));
            }
            catch (SocketException e) when (e.SocketErrorCode == SocketError.TimedOut && attempt < attempts)
            {
            }
        }
    }

    [SkippableFact]
    public void DnsResponse_AnswersQueryWithRegisteredRecords_OverWire()
    {
        SkipIfNoServer();
        var dnsPort = DnsPortOrSkip();
        _client!.Reset();

        _client.When(
            new HttpRequest { DnsName = "wire.dotnet.example", DnsType = "A" }
        ).RespondWithDns(
            DnsResponse.Response()
                .WithAnswerRecord(new DnsRecord { Name = "wire.dotnet.example", Type = "A", Value = "10.20.30.40", Ttl = 123 })
                .WithAnswerRecord(DnsRecord.ARecord("wire.dotnet.example", "10.20.30.41"))
        );
        _client.When(
            new HttpRequest { DnsName = "mail.dotnet.example", DnsType = "MX" }
        ).RespondWithDns(
            DnsResponse.Response().WithAnswerRecord(DnsRecord.MxRecord("mail.dotnet.example", 7, "mx1.dotnet.example"))
        );

        var a = SendDnsQuery(dnsPort, 0x1234, "wire.dotnet.example", DnsTypeA);
        a.Id.Should().Be(0x1234, "the reply must echo the query id");
        (a.Flags & 0x8000).Should().NotBe(0, "QR must mark the message as a response");
        (a.Flags & 0x000F).Should().Be(0, "RCODE must be NOERROR");
        a.Answers.Should().HaveCount(2);
        a.Answers[0].Name.Should().Be("wire.dotnet.example");
        a.Answers[0].Type.Should().Be(DnsTypeA);
        a.Answers[0].Class.Should().Be(DnsClassIn);
        a.Answers[0].Ttl.Should().Be(123u, "the registered TTL must reach the wire");
        new IPAddress(a.Answers[0].Data).ToString().Should().Be("10.20.30.40");
        new IPAddress(a.Answers[1].Data).ToString().Should().Be("10.20.30.41");

        var mx = SendDnsQuery(dnsPort, 0x4321, "mail.dotnet.example", DnsTypeMx);
        (mx.Flags & 0x000F).Should().Be(0, "RCODE must be NOERROR");
        mx.Answers.Should().ContainSingle();
        mx.Answers[0].Type.Should().Be(DnsTypeMx);
        BinaryPrimitives.ReadUInt16BigEndian(mx.Answers[0].Data).Should().Be(7, "the registered MX priority must reach the wire");
        var exchangeOffset = mx.Answers[0].DataOffset + 2;
        ReadDnsName(mx.Answers[0].Message, ref exchangeOffset).Should().Be("mx1.dotnet.example");

        var unmatched = SendDnsQuery(dnsPort, 0x0042, "other.dotnet.example", DnsTypeA);
        (unmatched.Flags & 0x000F).Should().Be(3, "a name with no expectation must get NXDOMAIN");
        unmatched.Answers.Should().BeEmpty();

        _client.Reset();
    }

    // ── gRPC ───────────────────────────────────────────────────────────────────────────────

    private static byte[] Varint(ulong value)
    {
        var bytes = new List<byte>();
        while (value >= 0x80)
        {
            bytes.Add((byte)(value | 0x80));
            value >>= 7;
        }
        bytes.Add((byte)value);
        return bytes.ToArray();
    }

    private static byte[] ProtoBytes(int field, byte[] value) =>
        Varint((ulong)(field << 3 | 2)).Concat(Varint((ulong)value.Length)).Concat(value).ToArray();

    private static byte[] ProtoString(int field, string value) => ProtoBytes(field, Encoding.UTF8.GetBytes(value));

    private static byte[] ProtoVarint(int field, ulong value) => Varint((ulong)(field << 3)).Concat(Varint(value)).ToArray();

    private static byte[] ProtoMessage(params byte[][] fields) => fields.SelectMany(f => f).ToArray();

    /// <summary>
    /// A <c>FileDescriptorSet</c> for:
    /// <code>
    /// syntax = "proto3";
    /// package mockserver.dotnet.wire;
    /// message HelloRequest { string name = 1; }
    /// message HelloReply { string greeting = 1; int32 count = 2; }
    /// service Greeter { rpc StreamGreetings (HelloRequest) returns (stream HelloReply); }
    /// </code>
    /// </summary>
    private static byte[] GreeterDescriptorSet()
    {
        static byte[] Field(string name, int number, int type) => ProtoMessage(
            ProtoString(1, name), ProtoVarint(3, (ulong)number), ProtoVarint(4, 1), ProtoVarint(5, (ulong)type),
            ProtoString(10, name));

        const int typeString = 9;
        const int typeInt32 = 5;
        var helloRequest = ProtoMessage(ProtoString(1, "HelloRequest"), ProtoBytes(2, Field("name", 1, typeString)));
        var helloReply = ProtoMessage(ProtoString(1, "HelloReply"),
            ProtoBytes(2, Field("greeting", 1, typeString)), ProtoBytes(2, Field("count", 2, typeInt32)));
        var method = ProtoMessage(ProtoString(1, "StreamGreetings"),
            ProtoString(2, ".mockserver.dotnet.wire.HelloRequest"), ProtoString(3, ".mockserver.dotnet.wire.HelloReply"),
            ProtoVarint(6, 1));
        var service = ProtoMessage(ProtoString(1, "Greeter"), ProtoBytes(2, method));
        var file = ProtoMessage(ProtoString(1, "mockserver_dotnet_wire.proto"), ProtoString(2, "mockserver.dotnet.wire"),
            ProtoBytes(4, helloRequest), ProtoBytes(4, helloReply), ProtoBytes(6, service), ProtoString(12, "proto3"));
        return ProtoBytes(1, file);
    }

    private static (string? Greeting, ulong? Count) DecodeHelloReply(byte[] message)
    {
        string? greeting = null;
        ulong? count = null;
        var offset = 0;
        ulong ReadVarint()
        {
            ulong value = 0;
            var shift = 0;
            byte b;
            do
            {
                b = message[offset++];
                value |= (ulong)(b & 0x7F) << shift;
                shift += 7;
            } while ((b & 0x80) != 0);
            return value;
        }
        while (offset < message.Length)
        {
            var tag = ReadVarint();
            switch (tag)
            {
                case (1 << 3) | 2:
                    var length = (int)ReadVarint();
                    greeting = Encoding.UTF8.GetString(message, offset, length);
                    offset += length;
                    break;
                case 2 << 3:
                    count = ReadVarint();
                    break;
                default:
                    throw new InvalidOperationException($"unexpected protobuf tag {tag} in HelloReply");
            }
        }
        return (greeting, count);
    }

    private static List<byte[]> SplitGrpcFrames(byte[] body)
    {
        var frames = new List<byte[]>();
        var offset = 0;
        while (offset < body.Length)
        {
            body[offset].Should().Be(0, "the server must not mark a message compressed");
            var length = (int)BinaryPrimitives.ReadUInt32BigEndian(body.AsSpan(offset + 1));
            frames.Add(body[(offset + 5)..(offset + 5 + length)]);
            offset += 5 + length;
        }
        return frames;
    }

    [SkippableFact]
    public async Task GrpcStreamResponse_StreamsEncodedMessagesAndStatus_OverWire()
    {
        SkipIfNoServer();
        _client!.Reset();
        _client.UploadGrpcDescriptor(GreeterDescriptorSet());
        try
        {
            _client.When(
                HttpRequest.Request().WithMethod("POST").WithPath("/mockserver.dotnet.wire.Greeter/StreamGreetings")
            ).RespondWithGrpcStream(
                GrpcStreamResponse.Response()
                    .WithHeader("x-dotnet-wire", "grpc")
                    .WithMessage("{\"greeting\": \"hello dotnet\", \"count\": 1}")
                    .WithMessage("{\"greeting\": \"again dotnet\", \"count\": 300}")
                    .WithStatusName("RESOURCE_EXHAUSTED")
                    .WithStatusMessage("quota dotnet")
            );

            var requestMessage = ProtoString(1, "dotnet");
            var requestFrame = new byte[5 + requestMessage.Length];
            BinaryPrimitives.WriteUInt32BigEndian(requestFrame.AsSpan(1), (uint)requestMessage.Length);
            requestMessage.CopyTo(requestFrame, 5);

            using var httpClient = new HttpClient { Timeout = WireTimeout };
            using var request = new HttpRequestMessage(HttpMethod.Post,
                $"{_baseUrl}/mockserver.dotnet.wire.Greeter/StreamGreetings")
            {
                Version = HttpVersion.Version20,
                VersionPolicy = HttpVersionPolicy.RequestVersionExact,
                Content = new ByteArrayContent(requestFrame)
            };
            request.Content.Headers.ContentType = new System.Net.Http.Headers.MediaTypeHeaderValue("application/grpc");
            request.Headers.TryAddWithoutValidation("te", "trailers");

            using var response = await httpClient.SendAsync(request);
            response.Version.Should().Be(HttpVersion.Version20);
            response.StatusCode.Should().Be(HttpStatusCode.OK);
            response.Content.Headers.ContentType?.MediaType.Should().Be("application/grpc");
            response.Headers.GetValues("x-dotnet-wire").Should().Equal("grpc");

            var frames = SplitGrpcFrames(await response.Content.ReadAsByteArrayAsync());
            frames.Select(DecodeHelloReply).Should().Equal(
                ("hello dotnet", (ulong?)1),
                ("again dotnet", (ulong?)300));

            response.TrailingHeaders.GetValues("grpc-status").Should().Equal(new[] { "8" },
                "RESOURCE_EXHAUSTED is gRPC status 8");
            Uri.UnescapeDataString(response.TrailingHeaders.GetValues("grpc-message").Single())
                .Should().Be("quota dotnet");
        }
        finally
        {
            _client.ClearGrpcDescriptors();
            _client.Reset();
        }
    }

    // ── callbacks ──────────────────────────────────────────────────────────────────────────

    /// <summary>
    /// <c>OidcUserinfoCallback</c> ships in the server jar, so a class callback can be driven without deploying
    /// a custom class. With no bearer token it answers a fixed RFC 6750 challenge whatever OIDC state exists.
    /// </summary>
    [SkippableFact]
    public void ClassCallback_InvokesServerSideClass_OverWire()
    {
        SkipIfNoServer();
        _client!.Reset();

        _client.When(
            HttpRequest.Request().WithMethod("GET").WithPath("/class-callback-wire")
        ).RespondWithClassCallback("org.mockserver.oidc.OidcUserinfoCallback");

        var response = Get($"{_baseUrl}/class-callback-wire");
        response.StatusCode.Should().Be(HttpStatusCode.Unauthorized,
            "the named class must be loaded and its handle() result served; an unloadable class yields 404");
        response.Headers.WwwAuthenticate.ToString().Should().Be("Bearer");
        response.Headers.CacheControl?.NoStore.Should().BeTrue();
        response.Content.ReadAsStringAsync().Result.Should().BeEmpty();

        _client.Reset();
    }

    [SkippableFact]
    public void ObjectCallback_ServesClosureResponse_OverWire()
    {
        SkipIfNoServer();
        _client!.Reset();

        _client.MockWithCallback(
            HttpRequest.Request().WithMethod("GET").WithPath("/object-callback-wire"),
            request =>
            {
                var tag = request.Headers?
                    .FirstOrDefault(h => string.Equals(h.Key, "X-Tag", StringComparison.OrdinalIgnoreCase)).Value?
                    .FirstOrDefault();
                return HttpResponse.Response()
                    .WithStatusCode(202)
                    .WithHeader("X-Callback", "dotnet")
                    .WithBody($"closure saw {request.Path} tag={tag}");
            });

        var response = Get($"{_baseUrl}/object-callback-wire", "X-Tag", "wire-42");
        response.StatusCode.Should().Be(HttpStatusCode.Accepted);
        response.Headers.GetValues("X-Callback").Should().Equal("dotnet");
        response.Content.ReadAsStringAsync().Result.Should().Be("closure saw /object-callback-wire tag=wire-42",
            "the body is built by the .NET closure from the request the server sent over the callback WebSocket");

        _client.Reset();
    }

    [SkippableFact]
    public void ObjectForwardCallback_ForwardsClosureRequest_OverWire()
    {
        SkipIfNoServer();
        _client!.Reset();

        _client.When(
            HttpRequest.Request().WithMethod("GET").WithPath("/forward-callback-target").WithHeader("X-Rewritten-By", "dotnet")
        ).Respond(
            HttpResponse.Response().WithStatusCode(200).WithBody("FORWARD-CALLBACK-OK")
        );
        _client.MockWithForwardCallback(
            HttpRequest.Request().WithMethod("GET").WithPath("/forward-callback-wire"),
            request =>
            {
                request.Path = "/forward-callback-target";
                request.Headers ??= new Dictionary<string, List<string>>();
                foreach (var key in request.Headers.Keys.Where(k => string.Equals(k, "Host", StringComparison.OrdinalIgnoreCase)).ToList())
                {
                    request.Headers.Remove(key);
                }
                request.Headers["Host"] = new List<string> { $"{_selfForwardHost}:{_selfForwardPort}" };
                request.Headers["X-Rewritten-By"] = new List<string> { "dotnet" };
                return request;
            });

        var response = Get($"{_baseUrl}/forward-callback-wire");
        response.StatusCode.Should().Be(HttpStatusCode.OK);
        response.Content.ReadAsStringAsync().Result.Should().Be("FORWARD-CALLBACK-OK",
            "only the closure's rewritten path and header reach the target expectation");

        _client.Reset();
    }
}
