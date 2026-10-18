package mockserver_test

import (
	"bytes"
	"crypto/tls"
	"crypto/x509"
	"encoding/base64"
	"encoding/binary"
	"errors"
	"fmt"
	"io"
	"net"
	"net/http"
	"os"
	"strings"
	"testing"
	"time"

	"github.com/gorilla/websocket"

	mockserver "github.com/mock-server/mockserver-monorepo/mockserver-client-go/v9"
)

// These tests prove WebSocket, DNS and gRPC actions registered via the client are served with the right bytes.

// mockServerCAPath is the CA MockServer signs its TLS certificates with unless configured otherwise.
const mockServerCAPath = "../mockserver/mockserver-core/src/main/resources/org/mockserver/socket/CertificateAuthorityCertificate.pem"

// greetingDescriptorPath is the shared gRPC fixture (see its README for the .proto it was compiled from).
const greetingDescriptorPath = "../mockserver/mockserver-core/src/test/resources/grpc/greeting.dsc"

func TestIntegration_WebSocketResponseFramesOverWire(t *testing.T) {
	client := skipIfNoServer(t)
	mustReset(t, client)

	binaryPayload := []byte{0x00, 0x01, 0x7f, 0xfe, 0xff}
	if _, err := client.When(
		mockserver.Request().Method("GET").Path("/gowire-ws"),
	).RespondWebSocket(
		mockserver.WebSocketResponse().
			Subprotocol("gowire.v1").
			TextMessage("hello-ws-go").
			BinaryMessage(base64.StdEncoding.EncodeToString(binaryPayload)).
			CloseConnection(true),
	); err != nil {
		t.Fatalf("registering WebSocket expectation: %v", err)
	}

	conn := dialWebSocket(t, "/gowire-ws", "gowire.v1")
	if got := conn.Subprotocol(); got != "gowire.v1" {
		t.Errorf("negotiated subprotocol = %q, want gowire.v1", got)
	}

	wantFrames := []struct {
		kind int
		data []byte
	}{
		{websocket.TextMessage, []byte("hello-ws-go")},
		{websocket.BinaryMessage, binaryPayload},
	}
	for i, want := range wantFrames {
		kind, data, err := conn.ReadMessage()
		if err != nil {
			t.Fatalf("reading frame %d: %v", i, err)
		}
		if kind != want.kind || !bytes.Equal(data, want.data) {
			t.Errorf("frame %d = type %d %q, want type %d %q", i, kind, data, want.kind, want.data)
		}
	}
	if kind, data, err := conn.ReadMessage(); !websocket.IsCloseError(err, websocket.CloseNormalClosure) {
		t.Errorf("after the registered frames got type %d %q (err %v), want a normal close frame", kind, data, err)
	}
}

func TestIntegration_WebSocketMatcherRepliesToInboundFrameOverWire(t *testing.T) {
	client := skipIfNoServer(t)
	mustReset(t, client)

	if _, err := client.Upsert(mockserver.Expectation{
		HttpRequest: mockserver.Request().Method("GET").Path("/gowire-ws-match").BuildPtr(),
		HttpWebSocketResponse: &mockserver.HttpWebSocketResponse{
			Messages: []mockserver.WebSocketMessage{{Text: "ready-go"}},
			Matchers: []mockserver.WebSocketMatcher{{
				FrameType:   "TEXT",
				TextMatcher: "ping-go",
				Responses:   []mockserver.WebSocketMessage{{Text: "pong-go"}},
			}},
		},
	}); err != nil {
		t.Fatalf("registering WebSocket matcher expectation: %v", err)
	}

	conn := dialWebSocket(t, "/gowire-ws-match", "")
	expectTextFrame(t, conn, "ready-go")
	if err := conn.WriteMessage(websocket.TextMessage, []byte("ping-go")); err != nil {
		t.Fatalf("sending ping-go: %v", err)
	}
	expectTextFrame(t, conn, "pong-go")
}

func TestIntegration_DnsResponseAnswersQueryOverWire(t *testing.T) {
	client := skipIfNoServer(t)
	dnsAddress := dnsAddressOrSkip(t)
	mustReset(t, client)

	const name = "gowire.example.test"
	aRecord := mockserver.ARecord(name, "10.20.30.40")
	aRecord.TTL = 321
	mx := mockserver.MXRecord(name, 0, "mail.gowire.example.test")
	if _, err := client.Upsert(
		mockserver.Expectation{
			HttpRequest: &mockserver.HttpRequest{DnsName: name, DnsType: "A"},
			DnsResponse: ptrTo(mockserver.Dns().Answer(aRecord).Build()),
		},
		mockserver.Expectation{
			HttpRequest: &mockserver.HttpRequest{DnsName: name, DnsType: "MX"},
			DnsResponse: ptrTo(mockserver.Dns().Answer(mx).Build()),
		},
		mockserver.Expectation{
			HttpRequest: &mockserver.HttpRequest{DnsName: "refused." + name, DnsType: "A"},
			DnsResponse: ptrTo(mockserver.Dns().ResponseCode("REFUSED").Build()),
		},
	); err != nil {
		t.Fatalf("registering DNS expectations: %v", err)
	}

	rcode, answers := queryDns(t, dnsAddress, name, dnsTypeA)
	if rcode != 0 || len(answers) != 1 {
		t.Fatalf("A query: rcode %d with %d answers %+v, want NOERROR with 1 answer", rcode, len(answers), answers)
	}
	want := dnsAnswer{Name: name, Type: dnsTypeA, Class: 1, TTL: 321, Value: "10.20.30.40"}
	if answers[0] != want {
		t.Errorf("A answer = %+v, want %+v", answers[0], want)
	}

	rcode, answers = queryDns(t, dnsAddress, name, dnsTypeMX)
	if rcode != 0 || len(answers) != 1 {
		t.Fatalf("MX query: rcode %d with %d answers %+v, want NOERROR with 1 answer", rcode, len(answers), answers)
	}
	want = dnsAnswer{Name: name, Type: dnsTypeMX, Class: 1, TTL: 300, Value: "0 mail.gowire.example.test"}
	if answers[0] != want {
		t.Errorf("MX answer = %+v, want %+v", answers[0], want)
	}

	if rcode, answers = queryDns(t, dnsAddress, "refused."+name, dnsTypeA); rcode != 5 || len(answers) != 0 {
		t.Errorf("refused query: rcode %d with answers %+v, want REFUSED (5) and none", rcode, answers)
	}
}

func TestIntegration_GrpcStreamResponseOverWire(t *testing.T) {
	client := skipIfNoServer(t)
	mustReset(t, client)
	uploadGreetingDescriptor(t, client)

	const listPath = "/com.example.grpc.GreetingService/ListGreetings"
	if _, err := client.When(
		mockserver.Request().Method("POST").Path(listPath),
	).RespondGrpcStream(
		mockserver.GrpcStream().
			Header("x-gowire", "grpc-go").
			Message(`{"greeting":"hello one"}`).
			Message(`{"greeting":"hello two"}`).
			StatusName("OK"),
	); err != nil {
		t.Fatalf("registering gRPC stream expectation: %v", err)
	}

	resp, messages := callGrpc(t, listPath, "go")
	if ct := resp.Header.Get("Content-Type"); !strings.HasPrefix(ct, "application/grpc") {
		t.Errorf("gRPC Content-Type = %q, want application/grpc", ct)
	}
	if got := resp.Header.Get("x-gowire"); got != "grpc-go" {
		t.Errorf("x-gowire header = %q, want grpc-go", got)
	}
	want := []string{"hello one", "hello two"}
	if len(messages) != len(want) {
		t.Fatalf("gRPC stream had %d messages %q, want %q", len(messages), messages, want)
	}
	for i := range want {
		if messages[i] != want[i] {
			t.Errorf("message %d greeting = %q, want %q", i, messages[i], want[i])
		}
	}
	if status := resp.Trailer.Get("grpc-status"); status != "0" {
		t.Errorf("grpc-status trailer = %q, want 0 (OK)", status)
	}
}

func TestIntegration_GrpcStreamErrorStatusOverWire(t *testing.T) {
	client := skipIfNoServer(t)
	mustReset(t, client)
	uploadGreetingDescriptor(t, client)

	const listPath = "/com.example.grpc.GreetingService/ListGreetings"
	if _, err := client.When(
		mockserver.Request().Method("POST").Path(listPath),
	).RespondGrpcStream(
		mockserver.GrpcStream().
			Message(`{"greeting":"before the failure"}`).
			StatusName("NOT_FOUND").
			StatusMessage("no greeting for go"),
	); err != nil {
		t.Fatalf("registering gRPC error expectation: %v", err)
	}

	resp, messages := callGrpc(t, listPath, "go")
	if len(messages) != 1 || messages[0] != "before the failure" {
		t.Errorf("gRPC stream messages = %q, want [\"before the failure\"]", messages)
	}
	if status := resp.Trailer.Get("grpc-status"); status != "5" {
		t.Errorf("grpc-status trailer = %q, want 5 (NOT_FOUND)", status)
	}
	if msg := resp.Trailer.Get("grpc-message"); msg != "no greeting for go" {
		t.Errorf("grpc-message trailer = %q, want %q", msg, "no greeting for go")
	}
}

func ptrTo[T any](v T) *T { return &v }

// --- WebSocket helpers ---

func dialWebSocket(t *testing.T, path, subprotocol string) *websocket.Conn {
	t.Helper()
	wsURL := "ws" + strings.TrimPrefix(os.Getenv("MOCKSERVER_URL"), "http") + path
	dialer := websocket.Dialer{HandshakeTimeout: 10 * time.Second}
	if subprotocol != "" {
		dialer.Subprotocols = []string{subprotocol}
	}
	conn, resp, err := dialer.Dial(wsURL, nil)
	if err != nil {
		status := 0
		if resp != nil {
			status = resp.StatusCode
		}
		t.Fatalf("WebSocket handshake with %s failed (HTTP %d): %v", wsURL, status, err)
	}
	t.Cleanup(func() { _ = conn.Close() })
	_ = conn.SetReadDeadline(time.Now().Add(10 * time.Second))
	return conn
}

func expectTextFrame(t *testing.T, conn *websocket.Conn, want string) {
	t.Helper()
	kind, data, err := conn.ReadMessage()
	if err != nil {
		t.Fatalf("waiting for text frame %q: %v", want, err)
	}
	if kind != websocket.TextMessage || string(data) != want {
		t.Errorf("frame = type %d %q, want text %q", kind, data, want)
	}
}

// --- DNS helpers ---

const (
	dnsTypeA  = 1
	dnsTypeMX = 15
)

// dnsAnswer is one decoded answer resource record; Value is the dotted IPv4
// address for A and "<preference> <exchange>" for MX.
type dnsAnswer struct {
	Name  string
	Type  uint16
	Class uint16
	TTL   uint32
	Value string
}

// dnsAddressOrSkip returns MOCKSERVER_DNS_ADDRESS (host:port of MockServer's
// DNS listener), skipping like skipIfNoServer when it is unset.
func dnsAddressOrSkip(t *testing.T) string {
	t.Helper()
	address := os.Getenv("MOCKSERVER_DNS_ADDRESS")
	if address == "" {
		if os.Getenv("MOCKSERVER_REQUIRE_SERVER") == "true" {
			t.Fatalf("MOCKSERVER_REQUIRE_SERVER=true, refusing to skip: MOCKSERVER_DNS_ADDRESS not set")
		}
		t.Skip("MOCKSERVER_DNS_ADDRESS not set; skipping DNS integration test")
	}
	return address
}

// queryDns sends one UDP query and returns the response code and decoded answers.
func queryDns(t *testing.T, address, name string, qtype uint16) (int, []dnsAnswer) {
	t.Helper()
	const id = 0x4721
	query := []byte{id >> 8, id & 0xff, 0x01, 0x00, 0, 1, 0, 0, 0, 0, 0, 0}
	for _, label := range strings.Split(name, ".") {
		query = append(query, byte(len(label)))
		query = append(query, label...)
	}
	query = append(query, 0, byte(qtype>>8), byte(qtype), 0, 1)

	conn, err := net.Dial("udp", address)
	if err != nil {
		t.Fatalf("dialling DNS listener %s: %v", address, err)
	}
	defer conn.Close()
	// UDP may drop a datagram, so resend on a read timeout; any other error fails at once.
	const attempts = 3
	msg := make([]byte, 1500)
	var n int
	for attempt := 1; ; attempt++ {
		if _, err := conn.Write(query); err != nil {
			t.Fatalf("sending DNS query: %v", err)
		}
		_ = conn.SetReadDeadline(time.Now().Add(2 * time.Second))
		n, err = conn.Read(msg)
		if err == nil {
			break
		}
		var netErr net.Error
		if !errors.As(err, &netErr) || !netErr.Timeout() || attempt == attempts {
			t.Fatalf("reading DNS response for %s from %s (attempt %d of %d): %v", name, address, attempt, attempts, err)
		}
	}
	msg = msg[:n]

	answers, rcode, err := parseDnsResponse(msg, id)
	if err != nil {
		t.Fatalf("decoding DNS response %x: %v", msg, err)
	}
	return rcode, answers
}

func parseDnsResponse(msg []byte, id uint16) ([]dnsAnswer, int, error) {
	if len(msg) < 12 {
		return nil, 0, fmt.Errorf("response is %d bytes, shorter than a DNS header", len(msg))
	}
	if binary.BigEndian.Uint16(msg) != id || msg[2]&0x80 == 0 {
		return nil, 0, fmt.Errorf("not a response to query id %#x", id)
	}
	rcode := int(msg[3] & 0x0f)
	qdcount, ancount := binary.BigEndian.Uint16(msg[4:]), binary.BigEndian.Uint16(msg[6:])
	off := 12
	for i := 0; i < int(qdcount); i++ {
		_, next, err := readDnsName(msg, off)
		if err != nil {
			return nil, 0, err
		}
		off = next + 4
	}
	var answers []dnsAnswer
	for i := 0; i < int(ancount); i++ {
		name, next, err := readDnsName(msg, off)
		if err != nil {
			return nil, 0, err
		}
		if next+10 > len(msg) {
			return nil, 0, fmt.Errorf("answer %d truncated", i)
		}
		a := dnsAnswer{
			Name:  name,
			Type:  binary.BigEndian.Uint16(msg[next:]),
			Class: binary.BigEndian.Uint16(msg[next+2:]),
			TTL:   binary.BigEndian.Uint32(msg[next+4:]),
		}
		rdStart := next + 10
		rdEnd := rdStart + int(binary.BigEndian.Uint16(msg[next+8:]))
		if rdEnd > len(msg) {
			return nil, 0, fmt.Errorf("answer %d rdata truncated", i)
		}
		rdata := msg[rdStart:rdEnd]
		switch a.Type {
		case dnsTypeA:
			a.Value = net.IP(rdata).String()
		case dnsTypeMX:
			exchange, _, err := readDnsName(msg, rdStart+2)
			if err != nil {
				return nil, 0, err
			}
			a.Value = fmt.Sprintf("%d %s", binary.BigEndian.Uint16(rdata), exchange)
		default:
			a.Value = fmt.Sprintf("%x", rdata)
		}
		answers = append(answers, a)
		off = rdEnd
	}
	return answers, rcode, nil
}

// readDnsName decodes a possibly-compressed name at off and returns it with the offset just past it.
func readDnsName(msg []byte, off int) (string, int, error) {
	var labels []string
	end := -1
	for jumps := 0; ; {
		if off >= len(msg) {
			return "", 0, fmt.Errorf("name runs past the message")
		}
		length := int(msg[off])
		switch {
		case length == 0:
			if end < 0 {
				end = off + 1
			}
			return strings.Join(labels, "."), end, nil
		case length&0xc0 == 0xc0:
			if off+1 >= len(msg) || jumps > 10 {
				return "", 0, fmt.Errorf("bad compression pointer")
			}
			if end < 0 {
				end = off + 2
			}
			off = int(binary.BigEndian.Uint16(msg[off:]) & 0x3fff)
			jumps++
		default:
			if off+1+length > len(msg) {
				return "", 0, fmt.Errorf("label runs past the message")
			}
			labels = append(labels, string(msg[off+1:off+1+length]))
			off += 1 + length
		}
	}
}

// --- gRPC helpers ---

func uploadGreetingDescriptor(t *testing.T, client *mockserver.Client) {
	t.Helper()
	descriptor, err := os.ReadFile(greetingDescriptorPath)
	if err != nil {
		t.Fatalf("reading gRPC descriptor fixture: %v", err)
	}
	if err := client.UploadGrpcDescriptor(descriptor); err != nil {
		t.Fatalf("uploading gRPC descriptor: %v", err)
	}
	t.Cleanup(func() { _ = client.ClearGrpcDescriptors() })
}

// callGrpc makes an HTTP/2 gRPC call carrying HelloRequest{name} and returns
// the response (body drained, so trailers are populated) and each streamed
// HelloResponse.greeting.
func callGrpc(t *testing.T, path, name string) (*http.Response, []string) {
	t.Helper()
	caPEM, err := os.ReadFile(mockServerCAPath)
	if err != nil {
		t.Fatalf("reading MockServer CA: %v", err)
	}
	roots := x509.NewCertPool()
	if !roots.AppendCertsFromPEM(caPEM) {
		t.Fatalf("no certificate in %s", mockServerCAPath)
	}
	httpClient := &http.Client{
		Timeout: 10 * time.Second,
		Transport: &http.Transport{
			TLSClientConfig:   &tls.Config{RootCAs: roots},
			ForceAttemptHTTP2: true,
		},
	}

	message := append([]byte{0x0a, byte(len(name))}, name...)
	frame := append([]byte{0, 0, 0, 0, byte(len(message))}, message...)
	req, err := http.NewRequest("POST", "https"+strings.TrimPrefix(os.Getenv("MOCKSERVER_URL"), "http")+path, bytes.NewReader(frame))
	if err != nil {
		t.Fatalf("building gRPC request: %v", err)
	}
	req.Header.Set("Content-Type", "application/grpc")
	req.Header.Set("TE", "trailers")

	defer httpClient.CloseIdleConnections()
	resp, err := httpClient.Do(req)
	if err != nil {
		t.Fatalf("gRPC call to %s failed: %v", path, err)
	}
	defer resp.Body.Close()
	if resp.ProtoMajor != 2 {
		t.Fatalf("gRPC call used %s, want HTTP/2", resp.Proto)
	}
	if resp.StatusCode != 200 {
		t.Fatalf("gRPC call returned HTTP %d, want 200", resp.StatusCode)
	}
	body, err := io.ReadAll(resp.Body)
	if err != nil {
		t.Fatalf("reading gRPC body: %v", err)
	}

	var greetings []string
	for len(body) > 0 {
		if len(body) < 5 {
			t.Fatalf("truncated gRPC frame header %x", body)
		}
		size := int(binary.BigEndian.Uint32(body[1:5]))
		if body[0] != 0 || len(body) < 5+size {
			t.Fatalf("bad gRPC frame (compressed flag %d, length %d, %d bytes left)", body[0], size, len(body)-5)
		}
		greetings = append(greetings, decodeGreeting(t, body[5:5+size]))
		body = body[5+size:]
	}
	return resp, greetings
}

// decodeGreeting reads field 1 (string greeting) of a HelloResponse.
func decodeGreeting(t *testing.T, message []byte) string {
	t.Helper()
	if len(message) == 0 {
		return ""
	}
	length, n := binary.Uvarint(message[1:])
	if message[0] != 0x0a || n <= 0 || 1+n+int(length) != len(message) {
		t.Fatalf("HelloResponse %x is not a single greeting field", message)
	}
	return string(message[1+n:])
}
