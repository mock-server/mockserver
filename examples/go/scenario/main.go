// Demonstrates the MockServer stateful-scenario features from the Go client by
// running the five canonical scenarios in sequence against a live MockServer and
// asserting the outcome of each. It exits 0 only if every scenario passes.
//
// The five scenarios are:
//  1. state_machine      - a login flow driven by scenarioState/newScenarioState
//  2. sequential_cycling - one expectation, multiple responses, SEQUENTIAL mode
//  3. timed_transition   - the scenario REST helper with a timed auto-transition
//  4. external_trigger   - the scenario REST helper driven by an external trigger
//  5. cross_protocol     - crossProtocolScenarios advancing a scenario on an event
//
// MockServer location is read from MOCKSERVER_HOST (default localhost) and
// MOCKSERVER_PORT (default 1080). The server is reset before each scenario so the
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
	"strings"
	"time"

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

// get performs a data-plane GET and returns the status code and body.
func get(path string) (int, string, error) {
	resp, err := http.Get(baseURL() + path)
	if err != nil {
		return 0, "", err
	}
	defer resp.Body.Close()
	body, err := io.ReadAll(resp.Body)
	if err != nil {
		return resp.StatusCode, "", err
	}
	return resp.StatusCode, string(body), nil
}

// post performs a data-plane POST and returns the status code and body.
func post(path string) (int, string, error) {
	resp, err := http.Post(baseURL()+path, "application/json", nil)
	if err != nil {
		return 0, "", err
	}
	defer resp.Body.Close()
	body, err := io.ReadAll(resp.Body)
	if err != nil {
		return resp.StatusCode, "", err
	}
	return resp.StatusCode, string(body), nil
}

// scenarioFunc runs one scenario and returns an error if any assertion fails.
type scenarioFunc func(c *mockserver.Client) error

// ---------------------------------------------------------------------------
// 1. state_machine -- login flow
// ---------------------------------------------------------------------------
func stateMachine(c *mockserver.Client) error {
	// POST /login (Started -> LoggedIn), once -> 200 {"token":"abc123"}
	// GET /profile while LoggedIn -> 200 {"name":"Alice"}
	// GET /profile while Started  -> 401 {"error":"Not authenticated"}
	login := mockserver.Response().StatusCode(200).JSONBody(`{"token":"abc123"}`).Build()
	profileOK := mockserver.Response().StatusCode(200).JSONBody(`{"name":"Alice"}`).Build()
	profile401 := mockserver.Response().StatusCode(401).JSONBody(`{"error":"Not authenticated"}`).Build()

	loginReq := mockserver.Request().Method("POST").Path("/login").Build()
	profileReq := mockserver.Request().Method("GET").Path("/profile").Build()

	if _, err := c.Upsert(
		mockserver.Expectation{
			HttpRequest:      &loginReq,
			HttpResponse:     &login,
			ScenarioName:     "LoginFlow",
			ScenarioState:    "Started",
			NewScenarioState: "LoggedIn",
			Times:            mockserver.Once(),
		},
		mockserver.Expectation{
			HttpRequest:   &profileReq,
			HttpResponse:  &profileOK,
			ScenarioName:  "LoginFlow",
			ScenarioState: "LoggedIn",
		},
		mockserver.Expectation{
			HttpRequest:   &profileReq,
			HttpResponse:  &profile401,
			ScenarioName:  "LoginFlow",
			ScenarioState: "Started",
		},
	); err != nil {
		return fmt.Errorf("upsert expectations: %w", err)
	}

	// GET /profile before login -> 401
	if status, body, err := get("/profile"); err != nil {
		return err
	} else if status != 401 {
		return fmt.Errorf("GET /profile (pre-login): expected 401, got %d (%s)", status, body)
	}

	// POST /login -> 200 with token
	if status, body, err := post("/login"); err != nil {
		return err
	} else if status != 200 {
		return fmt.Errorf("POST /login: expected 200, got %d (%s)", status, body)
	} else if !strings.Contains(body, "abc123") {
		return fmt.Errorf("POST /login: expected token abc123, got %s", body)
	}

	// GET /profile after login -> 200 name=Alice
	if status, body, err := get("/profile"); err != nil {
		return err
	} else if status != 200 {
		return fmt.Errorf("GET /profile (post-login): expected 200, got %d (%s)", status, body)
	} else if !strings.Contains(body, "Alice") {
		return fmt.Errorf("GET /profile (post-login): expected name Alice, got %s", body)
	}

	return nil
}

// ---------------------------------------------------------------------------
// 2. sequential_cycling -- multiple responses, one expectation (no scenario)
// ---------------------------------------------------------------------------
func sequentialCycling(c *mockserver.Client) error {
	// GET /api/status cycles 200 ok, 503 degraded, 200 ok (SEQUENTIAL, default);
	// the 4th call cycles back to the first response.
	if _, err := c.
		When(mockserver.Request().Method("GET").Path("/api/status")).
		WithResponseMode(mockserver.ResponseModeSequential).
		RespondMultiple(
			mockserver.Response().StatusCode(200).JSONBody(`{"status":"ok"}`),
			mockserver.Response().StatusCode(503).JSONBody(`{"status":"degraded"}`),
			mockserver.Response().StatusCode(200).JSONBody(`{"status":"ok"}`),
		); err != nil {
		return fmt.Errorf("upsert expectation: %w", err)
	}

	wantStatuses := []int{200, 503, 200, 200}
	for i, want := range wantStatuses {
		status, body, err := get("/api/status")
		if err != nil {
			return err
		}
		if status != want {
			return fmt.Errorf("GET /api/status call %d: expected %d, got %d (%s)", i+1, want, status, body)
		}
	}
	return nil
}

// ---------------------------------------------------------------------------
// 3. timed_transition -- scenario REST helper, timed auto-transition
// ---------------------------------------------------------------------------
func timedTransition(c *mockserver.Client) error {
	// GET /status while Deploying -> 200 {"status":"deploying"}
	// GET /status while Deployed  -> 200 {"status":"complete"}
	statusReq := mockserver.Request().Method("GET").Path("/status").Build()
	deploying := mockserver.Response().StatusCode(200).JSONBody(`{"status":"deploying"}`).Build()
	complete := mockserver.Response().StatusCode(200).JSONBody(`{"status":"complete"}`).Build()

	if _, err := c.Upsert(
		mockserver.Expectation{
			HttpRequest:   &statusReq,
			HttpResponse:  &deploying,
			ScenarioName:  "DeployFlow",
			ScenarioState: "Deploying",
		},
		mockserver.Expectation{
			HttpRequest:   &statusReq,
			HttpResponse:  &complete,
			ScenarioName:  "DeployFlow",
			ScenarioState: "Deployed",
		},
	); err != nil {
		return fmt.Errorf("upsert expectations: %w", err)
	}

	// Start in Deploying, auto-transition to Deployed after ~1s.
	if err := c.Scenario("DeployFlow").SetTimed("Deploying", 1000, "Deployed"); err != nil {
		return fmt.Errorf("set timed scenario: %w", err)
	}

	// Before the transition.
	if status, body, err := get("/status"); err != nil {
		return err
	} else if status != 200 || !strings.Contains(body, "deploying") {
		return fmt.Errorf("GET /status (before transition): expected deploying, got %d (%s)", status, body)
	}

	// Wait past the transition window.
	time.Sleep(1300 * time.Millisecond)

	// After the transition.
	if status, body, err := get("/status"); err != nil {
		return err
	} else if status != 200 || !strings.Contains(body, "complete") {
		return fmt.Errorf("GET /status (after transition): expected complete, got %d (%s)", status, body)
	}

	return nil
}

// ---------------------------------------------------------------------------
// 4. external_trigger -- scenario REST helper, external trigger
// ---------------------------------------------------------------------------
func externalTrigger(c *mockserver.Client) error {
	// GET /health while Started -> 200 {"status":"healthy"}
	// GET /health while Down     -> 503 {"status":"down"}
	healthReq := mockserver.Request().Method("GET").Path("/health").Build()
	healthy := mockserver.Response().StatusCode(200).JSONBody(`{"status":"healthy"}`).Build()
	down := mockserver.Response().StatusCode(503).JSONBody(`{"status":"down"}`).Build()

	if _, err := c.Upsert(
		mockserver.Expectation{
			HttpRequest:   &healthReq,
			HttpResponse:  &healthy,
			ScenarioName:  "HealthFlow",
			ScenarioState: "Started",
		},
		mockserver.Expectation{
			HttpRequest:   &healthReq,
			HttpResponse:  &down,
			ScenarioName:  "HealthFlow",
			ScenarioState: "Down",
		},
	); err != nil {
		return fmt.Errorf("upsert expectations: %w", err)
	}

	// Healthy while in the Started state.
	if status, body, err := get("/health"); err != nil {
		return err
	} else if status != 200 || !strings.Contains(body, "healthy") {
		return fmt.Errorf("GET /health (before trigger): expected healthy, got %d (%s)", status, body)
	}

	// Externally trigger the transition to Down.
	if err := c.Scenario("HealthFlow").Trigger("Down"); err != nil {
		return fmt.Errorf("trigger scenario: %w", err)
	}

	// Down after the trigger.
	if status, body, err := get("/health"); err != nil {
		return err
	} else if status != 503 || !strings.Contains(body, "down") {
		return fmt.Errorf("GET /health (after trigger): expected down, got %d (%s)", status, body)
	}

	return nil
}

// ---------------------------------------------------------------------------
// 5. cross_protocol -- crossProtocolScenarios (HTTP_REQUEST trigger)
// ---------------------------------------------------------------------------
func crossProtocol(c *mockserver.Client) error {
	// GET /events advances scenario ConnFlow to Connected (HTTP_REQUEST trigger).
	// GET /api/status while ConnFlow is Connected -> 200 {"status":"connected"}.
	if _, err := c.
		When(mockserver.Request().Method("GET").Path("/events")).
		WithCrossProtocolScenario(mockserver.CrossProtocolScenario{
			Trigger:      mockserver.CrossProtocolTriggerHTTPRequest,
			MatchPattern: "/events",
			ScenarioName: "ConnFlow",
			TargetState:  "Connected",
		}).
		Respond(mockserver.Response().StatusCode(200).JSONBody(`{"status":"listening"}`)); err != nil {
		return fmt.Errorf("upsert /events expectation: %w", err)
	}

	apiStatusReq := mockserver.Request().Method("GET").Path("/api/status").Build()
	connected := mockserver.Response().StatusCode(200).JSONBody(`{"status":"connected"}`).Build()
	if _, err := c.Upsert(mockserver.Expectation{
		HttpRequest:   &apiStatusReq,
		HttpResponse:  &connected,
		ScenarioName:  "ConnFlow",
		ScenarioState: "Connected",
	}); err != nil {
		return fmt.Errorf("upsert /api/status expectation: %w", err)
	}

	// Before the trigger ConnFlow is not Connected, so /api/status is unmatched.
	if status, _, err := get("/api/status"); err != nil {
		return err
	} else if status != 404 {
		return fmt.Errorf("GET /api/status (before /events): expected 404 (unmatched), got %d", status)
	}

	// Fire the cross-protocol trigger via an HTTP request to /events.
	if status, body, err := get("/events"); err != nil {
		return err
	} else if status != 200 {
		return fmt.Errorf("GET /events: expected 200, got %d (%s)", status, body)
	}

	// Now ConnFlow is Connected, so /api/status matches.
	if status, body, err := get("/api/status"); err != nil {
		return err
	} else if status != 200 || !strings.Contains(body, "connected") {
		return fmt.Errorf("GET /api/status (after /events): expected connected, got %d (%s)", status, body)
	}

	return nil
}

func main() {
	client := newClient()

	scenarios := []struct {
		name string
		run  scenarioFunc
	}{
		{"state_machine", stateMachine},
		{"sequential_cycling", sequentialCycling},
		{"timed_transition", timedTransition},
		{"external_trigger", externalTrigger},
		{"cross_protocol", crossProtocol},
	}

	failed := false
	for _, s := range scenarios {
		// Reset before each scenario so it is self-contained and order-independent.
		if err := client.Reset(); err != nil {
			fmt.Printf("FAIL: %s (reset: %v)\n", s.name, err)
			failed = true
			continue
		}
		if err := s.run(client); err != nil {
			fmt.Printf("FAIL: %s (%v)\n", s.name, err)
			failed = true
			continue
		}
		fmt.Printf("PASS: %s\n", s.name)
	}

	// Best-effort cleanup.
	_ = client.Reset()

	if failed {
		os.Exit(1)
	}
	fmt.Println("\nAll scenarios passed.")
	os.Exit(0)
}
