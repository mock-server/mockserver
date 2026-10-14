// Demonstrates registering a RESPONSE-phase breakpoint that modifies a proxied
// response in-flight. This is MockServer's interactive breakpoint feature.
//
// How it works:
//   1. Create a mock "upstream" expectation on /upstream that returns a canned
//      response (simulating a real backend).
//   2. Create a forward expectation that loops requests to /proxy back to the
//      SAME MockServer via httpOverrideForwardedRequest + socketAddress, so the
//      request hits the /upstream expectation.
//   3. Register a RESPONSE-phase breakpoint on /proxy. When the response comes
//      back from the upstream, the handler modifies the body before it reaches
//      the original caller.
//   4. Send a request to /proxy and print the modified response.
//
// Prerequisites:
//   - MockServer running on localhost:1080 WITH breakpoint support enabled
//     (the local build from this repo; the public Docker image may not include
//     breakpoint endpoints)
//   - go run .
package main

import (
	"bytes"
	"encoding/json"
	"fmt"
	"io"
	"log"
	"net/http"
	"time"

	mockserver "github.com/mock-server/mockserver-monorepo/mockserver-client-go/v9"
)

func main() {
	client := mockserver.New("localhost", 1080)

	// ---------------------------------------------------------------
	// 1. Upstream mock: GET /upstream -> 200 {"source":"upstream","modified":false}
	// ---------------------------------------------------------------
	_, err := client.When(
		mockserver.Request().Method("GET").Path("/upstream"),
	).Respond(
		mockserver.Response().
			StatusCode(200).
			Header("Content-Type", "application/json").
			Body(`{"source":"upstream","modified":false}`),
	)
	if err != nil {
		log.Fatalf("Failed to create upstream expectation: %v", err)
	}
	fmt.Println("1. Created upstream expectation: GET /upstream -> 200")

	// ---------------------------------------------------------------
	// 2. Loopback forward: GET /proxy -> forward to localhost:1080/upstream
	//    Uses the raw REST API to set httpOverrideForwardedRequest, which
	//    the fluent client API does not expose directly.
	// ---------------------------------------------------------------
	expMap := map[string]interface{}{
		"httpRequest": map[string]interface{}{
			"method": "GET",
			"path":   "/proxy",
		},
		"httpOverrideForwardedRequest": map[string]interface{}{
			"httpRequest": map[string]interface{}{
				"path": "/upstream",
				"socketAddress": map[string]interface{}{
					"host":   "localhost",
					"port":   1080,
					"scheme": "HTTP",
				},
			},
		},
	}
	expJSON, _ := json.Marshal([]interface{}{expMap})
	if err := rawPut("http://localhost:1080/mockserver/expectation", expJSON); err != nil {
		log.Fatalf("Failed to create forward expectation: %v", err)
	}
	fmt.Println("2. Created loopback forward: GET /proxy -> forward to /upstream (localhost:1080)")

	// ---------------------------------------------------------------
	// 3. Register a RESPONSE-phase breakpoint on /proxy
	//    The handler modifies the response body and adds a custom header.
	// ---------------------------------------------------------------
	bpID, err := client.AddRequestResponseBreakpoint(
		mockserver.Request().Method("GET").Path("/proxy"),
		// REQUEST handler: pass through unchanged
		func(req map[string]interface{}) interface{} {
			return req
		},
		// RESPONSE handler: modify the body
		func(req, resp map[string]interface{}) map[string]interface{} {
			fmt.Println("\n   [breakpoint] RESPONSE phase fired!")
			fmt.Printf("   [breakpoint] Original response body: %v\n", resp["body"])

			// Replace the body
			resp["body"] = `{"source":"upstream","modified":true,"breakpoint":"go-client"}`

			// Add a custom header
			headers, ok := resp["headers"].(map[string]interface{})
			if !ok {
				headers = make(map[string]interface{})
				resp["headers"] = headers
			}
			headers["X-Modified-By"] = []interface{}{"go-breakpoint-example"}

			fmt.Printf("   [breakpoint] Modified response body: %v\n", resp["body"])
			return resp
		},
	)
	if err != nil {
		log.Fatalf("Failed to register breakpoint: %v", err)
	}
	fmt.Printf("3. Registered RESPONSE breakpoint (id=%s) on GET /proxy\n", bpID)

	// ---------------------------------------------------------------
	// 4. Send request to /proxy -- the breakpoint handler will fire
	// ---------------------------------------------------------------
	fmt.Println("\n4. Sending GET /proxy ...")

	// Give the WebSocket a moment to be fully ready
	time.Sleep(200 * time.Millisecond)

	resp, err := http.Get("http://localhost:1080/proxy")
	if err != nil {
		log.Fatalf("Proxy request failed: %v", err)
	}
	defer resp.Body.Close()
	body, _ := io.ReadAll(resp.Body)

	fmt.Printf("\n--- Response from GET /proxy ---\n")
	fmt.Printf("Status:          %d\n", resp.StatusCode)
	fmt.Printf("Body:            %s\n", string(body))
	fmt.Printf("X-Modified-By:   %s\n", resp.Header.Get("X-Modified-By"))

	// ---------------------------------------------------------------
	// Clean up
	// ---------------------------------------------------------------
	client.RemoveBreakpointMatcher(bpID)
	client.CloseBreakpointWebSocket()
	if err := client.Reset(); err != nil {
		log.Fatalf("Reset failed: %v", err)
	}
	fmt.Println("\nAll expectations and breakpoints cleared.")
}

// rawPut sends a PUT request with a JSON body to the given URL.
func rawPut(url string, body []byte) error {
	req, err := http.NewRequest("PUT", url, bytes.NewReader(body))
	if err != nil {
		return err
	}
	req.Header.Set("Content-Type", "application/json; charset=utf-8")
	resp, err := http.DefaultClient.Do(req)
	if err != nil {
		return err
	}
	defer resp.Body.Close()
	if resp.StatusCode >= 400 {
		respBody, _ := io.ReadAll(resp.Body)
		return fmt.Errorf("PUT %s failed (HTTP %d): %s", url, resp.StatusCode, string(respBody))
	}
	return nil
}
