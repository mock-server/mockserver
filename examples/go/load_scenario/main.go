// Demonstrates MockServer's Load Scenario registry from the Go client.
//
// A "load scenario" is a named, server-side traffic generator: you register it
// once (its profile of ramp/hold/pause stages and the request steps it drives),
// then start/stop it by name. While running it generates synthetic traffic
// against the data plane and reports live throughput/latency status. This is the
// registry workflow exercised with the typed Go client:
//
//	c.LoadScenario(scenario)        register/upsert (PUT /mockserver/loadScenario)
//	c.StartLoadScenarios(names...)  start one/many (PUT .../start)
//	c.LoadScenarios()               list all (GET /mockserver/loadScenario)
//	c.GetLoadScenario(name)         one scenario + live status (GET .../{name})
//	c.StopLoadScenarios(names...)   stop one/many; no args = stop all (PUT .../stop)
//	c.RunLoadScenario(scenario)     register + start in one call
//	c.DeleteLoadScenario(name)      delete one (DELETE .../{name})
//	c.ClearLoadScenarios()          clear the registry (DELETE /mockserver/loadScenario)
//
// IMPORTANT: the server must be started with load generation enabled, otherwise
// starting returns HTTP 403:
//
//	java -Dmockserver.loadGenerationEnabled=true -jar mockserver-netty-...-jar-with-dependencies.jar -serverPort 1080
//	(or env MOCKSERVER_LOAD_GENERATION_ENABLED=true). Registering is always allowed.
//
// Prints "PASS" and exits 0 on success; exits non-zero on the first failure.
//
// MockServer location is read from MOCKSERVER_HOST (default localhost) and
// MOCKSERVER_PORT (default 1080).
package main

import (
	"fmt"
	"os"
	"strconv"
	"time"

	mockserver "github.com/mock-server/mockserver-monorepo/mockserver-client-go/v9"
)

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

// buildScenario builds a realistic multi-stage scenario: a linear RATE ramp
// (5 -> 50 req/s, capped at 50 VUs), then a 25-VU hold, then a PAUSE. Two
// Velocity-templated steps drive each iteration ($!iteration.index varies the
// request). StartDelayMillis defers load for half a second after start. Stage
// VUs are kept within the default safety cap of 50 (loadGenerationMaxVirtualUsers).
func buildScenario() mockserver.LoadScenario {
	browse := mockserver.Request().Method("GET").Path("/products/$!iteration.index").Build()
	checkout := mockserver.Request().Method("POST").Path("/cart/checkout").
		Body(`{"item":"$!iteration.index","qty":1}`).Build()

	// RATE ramp capped at 50 VUs (MaxVus is an optional *int field on the stage).
	rampStage := mockserver.RampRateStage(5, 50, 30000, mockserver.RampLinear)
	maxVus := 50
	rampStage.MaxVus = &maxVus

	return mockserver.LoadScenario{
		Name:             "checkout-load",
		TemplateType:     "VELOCITY",
		MaxRequests:      100000,
		StartDelayMillis: 500,
		Labels:           map[string]string{"team": "payments", "env": "staging"},
		Profile: &mockserver.LoadProfile{
			Stages: []mockserver.LoadStage{
				rampStage,
				mockserver.ConstantVusStage(25, 60000),
				mockserver.PauseStage(10000),
			},
		},
		Steps: []mockserver.LoadStep{
			{
				Name:      "browse",
				Request:   &browse,
				ThinkTime: &mockserver.Delay{TimeUnit: "MILLISECONDS", Value: 500},
			},
			{
				Name:    "checkout",
				Request: &checkout,
				Labels:  map[string]string{"critical": "true"},
			},
		},
	}
}

func main() {
	client := newClient()
	scenario := buildScenario()

	if err := run(client, scenario); err != nil {
		fmt.Printf("FAIL: %v\n", err)
		// Best-effort cleanup.
		_, _ = client.StopLoadScenarios()
		_ = client.ClearLoadScenarios()
		os.Exit(1)
	}

	fmt.Println("PASS")
	os.Exit(0)
}

func run(client *mockserver.Client, scenario mockserver.LoadScenario) error {
	// A catch-all target expectation so generated traffic gets a 200 to measure.
	if _, err := client.
		When(mockserver.Request().Path("/.*")).
		Respond(mockserver.Response().StatusCode(200).Body("ok")); err != nil {
		return fmt.Errorf("register target expectation: %w", err)
	}

	// 1. Register (does NOT start it yet).
	if _, err := client.LoadScenario(scenario); err != nil {
		return fmt.Errorf("register load scenario: %w", err)
	}
	fmt.Println(`registered "checkout-load"`)

	// 2. Start it (StartLoadScenarios is variadic — pass one or many names).
	if _, err := client.StartLoadScenarios("checkout-load"); err != nil {
		return fmt.Errorf("start load scenario (is loadGenerationEnabled=true?): %w", err)
	}
	fmt.Println(`started "checkout-load"`)
	time.Sleep(1500 * time.Millisecond)

	// 3. List all registered scenarios.
	list, err := client.LoadScenarios()
	if err != nil {
		return fmt.Errorf("list load scenarios: %w", err)
	}
	running := false
	for _, s := range list.Scenarios {
		if s.Name == "checkout-load" && s.State == mockserver.LoadScenarioStateRunning {
			running = true
		}
		fmt.Printf("listed: %s=%s\n", s.Name, s.State)
	}
	if !running {
		return fmt.Errorf("checkout-load is not RUNNING in the list (is loadGenerationEnabled=true?)")
	}

	// One scenario's live status (throughput/latency, current stage, ...).
	entry, err := client.GetLoadScenario("checkout-load")
	if err != nil {
		return fmt.Errorf("get load scenario: %w", err)
	}
	fmt.Printf("status: state=%s stageType=%s currentTarget=%v requestsSent=%d\n",
		entry.State, entry.StageType, entry.CurrentTarget, entry.RequestsSent)

	// 4. Stop it (StopLoadScenarios with no args stops ALL running scenarios).
	if _, err := client.StopLoadScenarios("checkout-load"); err != nil {
		return fmt.Errorf("stop load scenario: %w", err)
	}
	fmt.Println(`stopped "checkout-load"`)

	// Tidy up the registry.
	if err := client.ClearLoadScenarios(); err != nil {
		return fmt.Errorf("clear load scenarios: %w", err)
	}
	return nil
}
