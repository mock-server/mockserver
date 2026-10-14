package mockserver_test

import (
	"fmt"
	"log"
	"net/http"
	"net/http/httptest"

	mockserver "github.com/mock-server/mockserver-monorepo/mockserver-client-go/v9"
)

// This example demonstrates creating a basic expectation using the fluent API.
func Example() {
	// Use a test server to demonstrate (in real usage, point to a MockServer instance)
	ts := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(201)
		w.Write([]byte("[]"))
	}))
	defer ts.Close()

	client := mockserver.NewFromURL(ts.URL)

	// Create an expectation: when GET /hello is received, respond with 200 "world"
	_, err := client.When(
		mockserver.Request().Method("GET").Path("/hello"),
	).Respond(
		mockserver.Response().StatusCode(200).Body("world"),
	)
	if err != nil {
		log.Fatal(err)
	}

	fmt.Println("Expectation created successfully")
	// Output: Expectation created successfully
}

// This example demonstrates verifying a request was received.
func ExampleClient_Verify() {
	ts := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(202)
	}))
	defer ts.Close()

	client := mockserver.NewFromURL(ts.URL)

	err := client.Verify(
		mockserver.Request().Method("GET").Path("/hello"),
		mockserver.AtLeast(1),
	)
	if err != nil {
		log.Fatal(err)
	}

	fmt.Println("Verification passed")
	// Output: Verification passed
}

// This example demonstrates the When/Respond fluent chain with options.
func ExampleClient_When() {
	ts := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(201)
		w.Write([]byte("[]"))
	}))
	defer ts.Close()

	client := mockserver.NewFromURL(ts.URL)

	// Create an expectation that matches only once
	_, err := client.When(
		mockserver.Request().Method("POST").Path("/api/users").
			Header("Content-Type", "application/json"),
		mockserver.WithTimes(mockserver.Once()),
		mockserver.WithTimeToLive(mockserver.TTL("SECONDS", 60)),
	).Respond(
		mockserver.Response().
			StatusCode(201).
			JSONBody(`{"id": "123", "name": "Alice"}`).
			WithDelay("MILLISECONDS", 100),
	)
	if err != nil {
		log.Fatal(err)
	}

	fmt.Println("Expectation with options created")
	// Output: Expectation with options created
}

// This example demonstrates forwarding requests.
func ExampleClient_When_forward() {
	ts := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(201)
		w.Write([]byte("[]"))
	}))
	defer ts.Close()

	client := mockserver.NewFromURL(ts.URL)

	_, err := client.When(
		mockserver.Request().Path("/proxy/.*"),
	).Forward(
		mockserver.Forward().Host("backend.example.com").Port(443).Scheme("HTTPS"),
	)
	if err != nil {
		log.Fatal(err)
	}

	fmt.Println("Forward expectation created")
	// Output: Forward expectation created
}
