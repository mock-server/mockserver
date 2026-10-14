// Demonstrates the MockServer callback features from the Go client against a live
// MockServer, asserting the outcome of each. It exits 0 only if every check passes.
//
// Two kinds of callback are shown:
//
//  1. object_callback - a Go closure produces the response at request time. The
//     client opens a single callback WebSocket, registers the closure, and creates
//     an expectation carrying httpResponseObjectCallback.clientId. On a match the
//     server sends the request over the WebSocket and the closure returns a
//     response DERIVED from the request (here: body "hello <path>" plus an echoed
//     request header). The example sends a data-plane request and asserts the
//     dynamic response.
//
//  2. class_callback - a declarative, REST-only callback that references a
//     server-side class by name (httpResponseClassCallback.callbackClass). The
//     class need not exist for the wire shape to validate; the example only
//     asserts the server ACCEPTS the expectation (the Upsert succeeds).
//
// MockServer location is read from MOCKSERVER_HOST (default localhost) and
// MOCKSERVER_PORT (default 1080). The server is reset before each check so the
// example is self-contained and order-independent.
//
// Prerequisites: MockServer running (e.g. docker run -d -p 1080:1080 mockserver/mockserver)
package main

import (
	"fmt"
	"io"
	"net/http"
	"os"
	"strconv"

	mockserver "github.com/mock-server/mockserver-monorepo/mockserver-client-go/v9"
)

// baseURL builds the MockServer data-plane base URL from the environment.
func baseURL() string {
	host := os.Getenv("MOCKSERVER_HOST")
	if host == "" {
		host = "localhost"
	}
	port := os.Getenv("MOCKSERVER_PORT")
	if port == "" {
		port = "1080"
	}
	return fmt.Sprintf("http://%s:%s", host, port)
}

// newClient builds a typed control-plane client from the environment.
func newClient() *mockserver.Client {
	host := os.Getenv("MOCKSERVER_HOST")
	if host == "" {
		host = "localhost"
	}
	port := 1080
	if p := os.Getenv("MOCKSERVER_PORT"); p != "" {
		if n, err := strconv.Atoi(p); err == nil {
			port = n
		}
	}
	return mockserver.New(host, port)
}

// getWithHeader performs a data-plane GET with an optional request header and
// returns the status code, body, and the value of the named response header.
func getWithHeader(path, reqHeaderName, reqHeaderValue, respHeaderName string) (int, string, string, error) {
	req, err := http.NewRequest("GET", baseURL()+path, nil)
	if err != nil {
		return 0, "", "", err
	}
	if reqHeaderName != "" {
		req.Header.Set(reqHeaderName, reqHeaderValue)
	}
	resp, err := http.DefaultClient.Do(req)
	if err != nil {
		return 0, "", "", err
	}
	defer resp.Body.Close()
	body, err := io.ReadAll(resp.Body)
	if err != nil {
		return resp.StatusCode, "", "", err
	}
	return resp.StatusCode, string(body), resp.Header.Get(respHeaderName), nil
}

// checkFunc runs one check and returns an error if any assertion fails.
type checkFunc func(c *mockserver.Client) error

// ---------------------------------------------------------------------------
// 1. object_callback -- a Go closure produces a response derived from the request
// ---------------------------------------------------------------------------
func objectCallback(c *mockserver.Client) error {
	// Register an object response callback. The closure derives the response from
	// the incoming request: the body is "hello <path>" and an X-Echo response
	// header echoes the request's X-Caller header.
	if _, err := c.MockWithCallback(
		mockserver.Request().Method("GET").Path("/dynamic"),
		func(req *mockserver.HttpRequest) *mockserver.HttpResponse {
			caller := ""
			if vals, ok := req.Headers["X-Caller"]; ok && len(vals) > 0 {
				caller = vals[0]
			}
			return mockserver.Response().
				StatusCode(200).
				Header("X-Echo", caller).
				Body("hello " + req.Path).
				BuildPtr()
		},
	); err != nil {
		return fmt.Errorf("register object callback: %w", err)
	}

	status, body, echo, err := getWithHeader("/dynamic", "X-Caller", "go-example", "X-Echo")
	if err != nil {
		return err
	}
	if status != 200 {
		return fmt.Errorf("GET /dynamic: expected 200, got %d (%s)", status, body)
	}
	if body != "hello /dynamic" {
		return fmt.Errorf("GET /dynamic: expected body 'hello /dynamic', got %q", body)
	}
	if echo != "go-example" {
		return fmt.Errorf("GET /dynamic: expected X-Echo 'go-example', got %q", echo)
	}
	return nil
}

// ---------------------------------------------------------------------------
// 2. class_callback -- declarative, REST-only class callback
// ---------------------------------------------------------------------------
func classCallback(c *mockserver.Client) error {
	// The server stores the expectation by its wire shape; the named class need
	// not exist for the upsert to be accepted. A non-error return from Upsert
	// means the server accepted it (2xx).
	created, err := c.
		When(mockserver.Request().Method("GET").Path("/class-callback")).
		RespondWithClassCallback("org.mockserver.examples.MyResponseCallback")
	if err != nil {
		return fmt.Errorf("server rejected class-callback expectation: %w", err)
	}
	if len(created) == 0 {
		return fmt.Errorf("class-callback upsert returned no expectations")
	}

	// Re-read the active expectations and confirm the class callback round-trips.
	active, err := c.RetrieveActiveExpectations(mockserver.Request().Method("GET").Path("/class-callback"))
	if err != nil {
		return fmt.Errorf("retrieve active expectations: %w", err)
	}
	found := false
	for _, exp := range active {
		if exp.HttpResponseClassCallback != nil &&
			exp.HttpResponseClassCallback.CallbackClass == "org.mockserver.examples.MyResponseCallback" {
			found = true
			break
		}
	}
	if !found {
		return fmt.Errorf("class-callback expectation not found among active expectations")
	}
	return nil
}

func main() {
	client := newClient()

	checks := []struct {
		name string
		run  checkFunc
	}{
		{"object_callback", objectCallback},
		{"class_callback", classCallback},
	}

	failed := false
	for _, ch := range checks {
		// Reset before each check so it is self-contained and order-independent.
		if err := client.Reset(); err != nil {
			fmt.Printf("FAIL: %s (reset: %v)\n", ch.name, err)
			failed = true
			continue
		}
		if err := ch.run(client); err != nil {
			fmt.Printf("FAIL: %s (%v)\n", ch.name, err)
			failed = true
			continue
		}
		fmt.Printf("PASS: %s\n", ch.name)
	}

	// Best-effort cleanup. Close the callback WebSocket so the program exits.
	client.CloseBreakpointWebSocket()
	_ = client.Reset()

	if failed {
		os.Exit(1)
	}
	fmt.Println("\nAll callback checks passed.")
	os.Exit(0)
}
