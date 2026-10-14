package mockserver_test

import (
	"net/url"
	"os"
	"testing"

	mockserver "github.com/mock-server/mockserver-monorepo/mockserver-client-go/v9"
)

// skipIfNoServer skips the test if MOCKSERVER_URL is not set or the server
// is unreachable.
//
// Set MOCKSERVER_REQUIRE_SERVER=true to turn every skip into a hard failure.
// CI does this: a skip that reports green is indistinguishable from a pass,
// so on CI a missing or unreachable server must fail the build loudly rather
// than quietly test nothing.
func skipIfNoServer(t *testing.T) *mockserver.Client {
	t.Helper()

	strict := os.Getenv("MOCKSERVER_REQUIRE_SERVER") == "true"
	bail := func(format string, args ...interface{}) {
		t.Helper()
		if strict {
			t.Fatalf("MOCKSERVER_REQUIRE_SERVER=true, refusing to skip: "+format, args...)
		}
		t.Skipf(format+"; skipping integration test", args...)
	}

	msURL := os.Getenv("MOCKSERVER_URL")
	if msURL == "" {
		bail("MOCKSERVER_URL not set")
		return nil
	}

	if _, err := url.Parse(msURL); err != nil {
		bail("MOCKSERVER_URL is not a valid URL: %v", err)
		return nil
	}

	client := mockserver.NewFromURL(msURL)
	if !client.IsRunning() {
		bail("MockServer at %s is not reachable", msURL)
		return nil
	}
	return client
}

func TestIntegration_CreateAndVerify(t *testing.T) {
	client := skipIfNoServer(t)

	// Reset to start clean
	if err := client.Reset(); err != nil {
		t.Fatalf("Reset failed: %v", err)
	}

	// Create an expectation
	_, err := client.When(
		mockserver.Request().Method("GET").Path("/integration-test"),
	).Respond(
		mockserver.Response().StatusCode(200).Body("integration OK"),
	)
	if err != nil {
		t.Fatalf("When/Respond failed: %v", err)
	}

	// Retrieve active expectations
	expectations, err := client.RetrieveActiveExpectations(nil)
	if err != nil {
		t.Fatalf("RetrieveActiveExpectations failed: %v", err)
	}
	if len(expectations) == 0 {
		t.Error("expected at least 1 active expectation")
	}

	// Clear
	if err := client.Clear(mockserver.Request().Path("/integration-test"), mockserver.ClearAll); err != nil {
		t.Fatalf("Clear failed: %v", err)
	}

	// Reset again
	if err := client.Reset(); err != nil {
		t.Fatalf("final Reset failed: %v", err)
	}
}

func TestIntegration_VerifySequence(t *testing.T) {
	client := skipIfNoServer(t)

	if err := client.Reset(); err != nil {
		t.Fatalf("Reset failed: %v", err)
	}

	// Create expectations for two paths
	_, err := client.When(
		mockserver.Request().Method("GET").Path("/seq-a"),
	).Respond(
		mockserver.Response().StatusCode(200),
	)
	if err != nil {
		t.Fatal(err)
	}

	_, err = client.When(
		mockserver.Request().Method("GET").Path("/seq-b"),
	).Respond(
		mockserver.Response().StatusCode(200),
	)
	if err != nil {
		t.Fatal(err)
	}

	// Note: actual verification of sequence requires making real HTTP calls
	// to the mock server, which is outside the scope of this client-only test.
	// We just verify the API call itself works.
	err = client.VerifySequence(
		mockserver.Request().Path("/seq-a"),
		mockserver.Request().Path("/seq-b"),
	)
	// This may return a 406 since no actual requests were made — that's fine
	if err != nil {
		if _, ok := err.(*mockserver.VerificationError); !ok {
			t.Fatalf("unexpected error type: %T: %v", err, err)
		}
	}

	if err := client.Reset(); err != nil {
		t.Fatalf("final Reset failed: %v", err)
	}
}
