// Demonstrates creating a basic MockServer expectation, exercising it with a
// real HTTP request, and verifying that the request was received.
//
// Prerequisites: MockServer running on localhost:1080
//   docker run -d -p 1080:1080 mockserver/mockserver
package main

import (
	"fmt"
	"io"
	"log"
	"net/http"

	mockserver "github.com/mock-server/mockserver-monorepo/mockserver-client-go/v9"
)

func main() {
	client := mockserver.New("localhost", 1080)

	// ---------------------------------------------------------------
	// 1. Create an expectation: GET /hello -> 200 "Hello from Go!"
	// ---------------------------------------------------------------
	_, err := client.When(
		mockserver.Request().Method("GET").Path("/hello"),
	).Respond(
		mockserver.Response().
			StatusCode(200).
			Header("Content-Type", "text/plain").
			Body("Hello from Go!"),
	)
	if err != nil {
		log.Fatalf("Failed to create expectation: %v", err)
	}
	fmt.Println("1. Created expectation: GET /hello -> 200 \"Hello from Go!\"")

	// ---------------------------------------------------------------
	// 2. Send a test request through MockServer
	// ---------------------------------------------------------------
	resp, err := http.Get("http://localhost:1080/hello")
	if err != nil {
		log.Fatalf("Test request failed: %v", err)
	}
	defer resp.Body.Close()
	body, _ := io.ReadAll(resp.Body)
	fmt.Printf("\n--- Test request: GET /hello ---\nStatus: %d\nBody:   %s\n", resp.StatusCode, string(body))

	// ---------------------------------------------------------------
	// 3. Verify the request was received at least once
	// ---------------------------------------------------------------
	err = client.Verify(
		mockserver.Request().Method("GET").Path("/hello"),
		mockserver.AtLeast(1),
	)
	if err != nil {
		log.Fatalf("Verification failed: %v", err)
	}
	fmt.Println("\n2. Verified: GET /hello received at least once")

	// ---------------------------------------------------------------
	// Clean up
	// ---------------------------------------------------------------
	if err := client.Reset(); err != nil {
		log.Fatalf("Reset failed: %v", err)
	}
	fmt.Println("\nAll expectations cleared.")
}
