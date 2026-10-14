package mockserver_test

import (
	"net/http"
	"os"
	"testing"

	mockserver "github.com/mock-server/mockserver-monorepo/mockserver-client-go/v9"
)

// A matcher value beginning with '!' is the case the plain-string wire form
// cannot express: the server strips the marker, so "X-Tag is exactly !foo"
// becomes "X-Tag is anything but foo".
//
// The DISCRIMINATING assertion is the control request, not the matching one.
// Under the bug the matcher means "not foo", which matches the literal "!foo"
// too -- so a test that only sends "!foo" and asserts a match passes just as
// happily against the broken behaviour. It is the request carrying an unrelated
// value that must NOT match, and that is what fails without the escape.
const (
	matchedStatus = 222
	literalValue  = "!foo"
	controlValue  = "bar"
)

func mustReset(t *testing.T, client *mockserver.Client) {
	t.Helper()
	if err := client.Reset(); err != nil {
		t.Fatalf("Reset failed: %v", err)
	}
}

// send issues req and returns the status code.
func send(t *testing.T, req *http.Request) int {
	t.Helper()
	resp, err := http.DefaultClient.Do(req)
	if err != nil {
		t.Fatalf("request failed: %v", err)
	}
	defer resp.Body.Close()
	return resp.StatusCode
}

func newReq(t *testing.T, path string) *http.Request {
	t.Helper()
	req, err := http.NewRequest("GET", os.Getenv("MOCKSERVER_URL")+path, nil)
	if err != nil {
		t.Fatalf("building request: %v", err)
	}
	return req
}

// assertLiteralMatch runs both directions for one field.
func assertLiteralMatch(t *testing.T, field string, matching, control *http.Request) {
	t.Helper()
	if got := send(t, matching); got != matchedStatus {
		t.Errorf("%s: request carrying the literal %q returned %d, want %d -- the escape did not reach the server",
			field, literalValue, got, matchedStatus)
	}
	if got := send(t, control); got == matchedStatus {
		t.Errorf("%s: request carrying %q ALSO matched (%d) -- the matcher was read as a negation, "+
			"so it matches everything except %q instead of exactly %q",
			field, controlValue, got, "foo", literalValue)
	}
}

func TestIntegration_LiteralHeaderValueStartingWithNotMarker(t *testing.T) {
	client := skipIfNoServer(t)
	mustReset(t, client)

	if _, err := client.When(
		mockserver.Request().Path("/lit-header").HeaderMatcher("X-Tag", mockserver.Literal(literalValue)),
	).Respond(mockserver.Response().StatusCode(matchedStatus)); err != nil {
		t.Fatalf("creating expectation: %v", err)
	}

	matching := newReq(t, "/lit-header")
	matching.Header.Set("X-Tag", literalValue)
	control := newReq(t, "/lit-header")
	control.Header.Set("X-Tag", controlValue)
	assertLiteralMatch(t, "header", matching, control)
}

func TestIntegration_LiteralQueryParameterStartingWithNotMarker(t *testing.T) {
	client := skipIfNoServer(t)
	mustReset(t, client)

	if _, err := client.When(
		mockserver.Request().Path("/lit-query").QueryStringParameterMatcher("q", mockserver.Literal(literalValue)),
	).Respond(mockserver.Response().StatusCode(matchedStatus)); err != nil {
		t.Fatalf("creating expectation: %v", err)
	}

	matching := newReq(t, "/lit-query")
	qm := matching.URL.Query()
	qm.Set("q", literalValue)
	matching.URL.RawQuery = qm.Encode()

	control := newReq(t, "/lit-query")
	qc := control.URL.Query()
	qc.Set("q", controlValue)
	control.URL.RawQuery = qc.Encode()

	assertLiteralMatch(t, "queryStringParameter", matching, control)
}

func TestIntegration_LiteralCookieValueStartingWithNotMarker(t *testing.T) {
	client := skipIfNoServer(t)
	mustReset(t, client)

	if _, err := client.When(
		mockserver.Request().Path("/lit-cookie").CookieMatcher("ck", mockserver.Literal(literalValue)),
	).Respond(mockserver.Response().StatusCode(matchedStatus)); err != nil {
		t.Fatalf("creating expectation: %v", err)
	}

	matching := newReq(t, "/lit-cookie")
	matching.AddCookie(&http.Cookie{Name: "ck", Value: literalValue})
	control := newReq(t, "/lit-cookie")
	control.AddCookie(&http.Cookie{Name: "ck", Value: controlValue})
	assertLiteralMatch(t, "cookie", matching, control)
}

func TestIntegration_LiteralPathParameterStartingWithNotMarker(t *testing.T) {
	client := skipIfNoServer(t)
	mustReset(t, client)

	if _, err := client.When(
		mockserver.Request().Path("/lit-pp/{id}").PathParameterMatcher("id", mockserver.Literal(literalValue)),
	).Respond(mockserver.Response().StatusCode(matchedStatus)); err != nil {
		t.Fatalf("creating expectation: %v", err)
	}

	assertLiteralMatch(t, "pathParameter",
		newReq(t, "/lit-pp/"+literalValue),
		newReq(t, "/lit-pp/"+controlValue))
}

// The plain-string API must keep its current meaning: a leading '!' there still
// negates. This is what makes the new matcher API additive rather than a silent
// behaviour change for existing callers.
func TestIntegration_PlainHeaderApiStillNegates(t *testing.T) {
	client := skipIfNoServer(t)
	mustReset(t, client)

	if _, err := client.When(
		mockserver.Request().Path("/plain-header").Header("X-Tag", "!foo"),
	).Respond(mockserver.Response().StatusCode(matchedStatus)); err != nil {
		t.Fatalf("creating expectation: %v", err)
	}

	req := newReq(t, "/plain-header")
	req.Header.Set("X-Tag", "anything-but-foo")
	if got := send(t, req); got != matchedStatus {
		t.Errorf("plain Header(\"X-Tag\", \"!foo\") no longer negates: got %d, want %d", got, matchedStatus)
	}
	req2 := newReq(t, "/plain-header")
	req2.Header.Set("X-Tag", "foo")
	if got := send(t, req2); got == matchedStatus {
		t.Errorf("plain Header(\"X-Tag\", \"!foo\") matched \"foo\" (%d); negation is broken", got)
	}
}

// The escape must survive a RETRIEVE, not just a create. Before UnmarshalJSON
// existed, RetrieveActiveExpectations failed outright with "cannot unmarshal
// object into ... of type string" whenever the stored expectation carried the
// object form -- the escape was write-only, and nothing in the create-and-probe
// tests above could see it because none of them read anything back.
func TestIntegration_LiteralMatcherSurvivesRetrieve(t *testing.T) {
	client := skipIfNoServer(t)
	mustReset(t, client)

	if _, err := client.When(
		mockserver.Request().Path("/lit-retrieve").HeaderMatcher("X-Tag", mockserver.Literal(literalValue)),
	).Respond(mockserver.Response().StatusCode(matchedStatus)); err != nil {
		t.Fatalf("creating expectation: %v", err)
	}

	// The decode itself is the assertion: this is what used to fail.
	active, err := client.RetrieveActiveExpectations(nil)
	if err != nil {
		t.Fatalf("RetrieveActiveExpectations failed to decode the expectation this client just created: %v", err)
	}
	if len(active) == 0 {
		t.Fatalf("no active expectations returned")
	}

	// Whether the retrieved value is the literal or the server's lossy plain-form
	// echo depends on the SERVER version: a server carrying the same escape
	// (current master) echoes the object form, while 7.4.0 and earlier flatten it
	// back to a bare "!foo". Assert only what is true of both -- the request
	// decoded, and the header survived under some representation -- so this test
	// does not become a server-version detector.
	req := active[0].HttpRequest
	_, plain := req.Headers["X-Tag"]
	_, matcher := req.HeaderMatchers["X-Tag"]
	if !plain && !matcher {
		t.Errorf("X-Tag missing from the retrieved expectation entirely: headers=%+v matchers=%+v",
			req.Headers, req.HeaderMatchers)
	}
}

// Recorded requests go through the same decode path.
func TestIntegration_RetrieveRecordedRequestsDecodes(t *testing.T) {
	client := skipIfNoServer(t)
	mustReset(t, client)

	if _, err := client.When(
		mockserver.Request().Path("/lit-recorded").HeaderMatcher("X-Tag", mockserver.Literal(literalValue)),
	).Respond(mockserver.Response().StatusCode(matchedStatus)); err != nil {
		t.Fatalf("creating expectation: %v", err)
	}
	req := newReq(t, "/lit-recorded")
	req.Header.Set("X-Tag", literalValue)
	if got := send(t, req); got != matchedStatus {
		t.Fatalf("setup request did not match: %d", got)
	}

	if _, err := client.RetrieveRecordedRequests(nil); err != nil {
		t.Fatalf("RetrieveRecordedRequests failed to decode: %v", err)
	}
}
