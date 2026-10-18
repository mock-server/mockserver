package mockserver_test

import (
	"fmt"
	"io"
	"net/http"
	"os"
	"testing"
	"time"

	mockserver "github.com/mock-server/mockserver-monorepo/mockserver-client-go/v9"
)

// These tests prove class and closure callbacks registered via the client are invoked by the server.

// logoutCallbackClass ships in every MockServer build and answers purely from
// the request: a post_logout_redirect_uri query parameter becomes a 302 to that
// URI with state appended, otherwise a fixed 200 page.
const logoutCallbackClass = "org.mockserver.oidc.OidcLogoutCallback"

func TestIntegration_ClassCallbackInvokedOverWire(t *testing.T) {
	client := skipIfNoServer(t)
	mustReset(t, client)

	if _, err := client.When(
		mockserver.Request().Method("GET").Path("/gowire-class-callback"),
	).RespondWithClassCallback(logoutCallbackClass); err != nil {
		t.Fatalf("registering class callback expectation: %v", err)
	}

	status, headers, _ := getNoRedirect(t, "/gowire-class-callback?post_logout_redirect_uri=http://after.example/done&state=go-state")
	if status != 302 {
		t.Fatalf("class callback returned %d, want 302 from %s", status, logoutCallbackClass)
	}
	if loc := headers.Get("Location"); loc != "http://after.example/done?state=go-state" {
		t.Errorf("class callback Location = %q, want http://after.example/done?state=go-state", loc)
	}

	status, headers, body := getNoRedirect(t, "/gowire-class-callback")
	if status != 200 || string(body) != "<html><body>Logged out</body></html>" {
		t.Errorf("class callback without a redirect = %d %q, want 200 with the logged-out page", status, body)
	}
	if ct := headers.Get("Content-Type"); ct != "text/html; charset=utf-8" {
		t.Errorf("class callback Content-Type = %q, want text/html; charset=utf-8", ct)
	}
}

func TestIntegration_ObjectResponseCallbackInvokedOverWire(t *testing.T) {
	client := skipIfNoServer(t)
	mustReset(t, client)
	t.Cleanup(client.CloseBreakpointWebSocket)

	if _, err := client.MockWithCallback(
		mockserver.Request().Method("GET").Path("/gowire-obj-callback"),
		func(req *mockserver.HttpRequest) *mockserver.HttpResponse {
			return mockserver.Response().
				StatusCode(233).
				Header("x-callback", "go-closure").
				Body(fmt.Sprintf("closure saw %s %s tag=%v", req.Method, req.Path, req.Headers["X-Tag"])).
				BuildPtr()
		},
	); err != nil {
		t.Fatalf("registering object callback expectation: %v", err)
	}

	req := newReq(t, "/gowire-obj-callback")
	req.Header.Set("x-tag", "t-42")
	resp, err := http.DefaultClient.Do(req)
	if err != nil {
		t.Fatalf("calling object callback endpoint: %v", err)
	}
	defer resp.Body.Close()
	body, _ := io.ReadAll(resp.Body)
	if resp.StatusCode != 233 {
		t.Fatalf("object callback returned %d %q, want 233 from the Go closure", resp.StatusCode, body)
	}
	if got := resp.Header.Get("x-callback"); got != "go-closure" {
		t.Errorf("x-callback header = %q, want go-closure", got)
	}
	if want := "closure saw GET /gowire-obj-callback tag=[t-42]"; string(body) != want {
		t.Errorf("object callback body = %q, want %q", body, want)
	}
}

func TestIntegration_ObjectForwardCallbackInvokedOverWire(t *testing.T) {
	client := skipIfNoServer(t)
	mustReset(t, client)
	t.Cleanup(client.CloseBreakpointWebSocket)

	if _, err := client.When(
		mockserver.Request().Method("GET").Path("/gowire-fwd-callback-target"),
	).Respond(
		mockserver.Response().StatusCode(200).Body(forwardUpstreamBody),
	); err != nil {
		t.Fatalf("registering upstream expectation: %v", err)
	}
	if _, err := client.MockWithForwardCallback(
		mockserver.Request().Method("GET").Path("/gowire-fwd-callback"),
		func(req *mockserver.HttpRequest) *mockserver.HttpRequest {
			address := fmt.Sprintf("localhost:%d", mockServerInternalPort)
			return &mockserver.HttpRequest{
				Method:        req.Method,
				Path:          "/gowire-fwd-callback-target",
				Headers:       map[string][]string{"Host": {address}},
				SocketAddress: &mockserver.SocketAddress{Host: "localhost", Port: mockServerInternalPort, Scheme: "HTTP"},
			}
		},
	); err != nil {
		t.Fatalf("registering forward callback expectation: %v", err)
	}

	status, body := getWithBody(t, "/gowire-fwd-callback")
	if status != 200 || body != forwardUpstreamBody {
		t.Errorf("forward callback returned %d %q, want 200 %q from the path the Go closure rewrote to", status, body, forwardUpstreamBody)
	}
}

func getNoRedirect(t *testing.T, path string) (int, http.Header, []byte) {
	t.Helper()
	client := &http.Client{
		Timeout:       10 * time.Second,
		CheckRedirect: func(*http.Request, []*http.Request) error { return http.ErrUseLastResponse },
	}
	resp, err := client.Get(os.Getenv("MOCKSERVER_URL") + path)
	if err != nil {
		t.Fatalf("GET %s failed: %v", path, err)
	}
	defer resp.Body.Close()
	body, err := io.ReadAll(resp.Body)
	if err != nil {
		t.Fatalf("reading %s body: %v", path, err)
	}
	return resp.StatusCode, resp.Header, body
}
