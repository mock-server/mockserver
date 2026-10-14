package mockserver_test

import (
	"encoding/json"
	"fmt"
	"testing"

	mockserver "github.com/mock-server/mockserver-monorepo/mockserver-client-go/v9"
)

// A value that round-trips through the plain form must STAY a plain string, so
// existing expectations are byte-identical on the wire.
func TestMatcherValue_UnambiguousStaysPlainString(t *testing.T) {
	for _, tc := range []struct {
		value mockserver.MatcherValue
		want  string
	}{
		{mockserver.Literal("foo"), `"foo"`},
		{mockserver.NotLiteral("foo"), `"!foo"`},
		{mockserver.OptionalLiteral("foo"), `"?foo"`},
		{mockserver.Literal(""), `""`},
		{mockserver.Literal("foo!bar"), `"foo!bar"`}, // marker not at position 0
		{mockserver.NotLiteral("!foo"), `"!!foo"`},   // re-parses to itself, so no escape needed
	} {
		got, err := json.Marshal(tc.value)
		if err != nil {
			t.Fatalf("marshal %+v: %v", tc.value, err)
		}
		if string(got) != tc.want {
			t.Errorf("Marshal(%+v) = %s, want %s", tc.value, got, tc.want)
		}
	}
}

// A value the plain form would misread must use the object form, and must carry
// "not" explicitly even when false so the intent is unmistakable.
func TestMatcherValue_AmbiguousUsesObjectFormWithExplicitNot(t *testing.T) {
	for _, tc := range []struct {
		value mockserver.MatcherValue
		want  string
	}{
		{mockserver.Literal("!foo"), `{"not":false,"value":"!foo"}`},
		{mockserver.Literal("?foo"), `{"not":false,"value":"?foo"}`},
		{mockserver.Literal("?!foo"), `{"not":false,"value":"?!foo"}`},
		{mockserver.Literal("!?foo"), `{"not":false,"value":"!?foo"}`},
		// NOT deliberately absent: NotLiteral("!foo") serialises to "!!foo",
		// which re-parses to exactly itself, so it is NOT ambiguous and stays a
		// plain string. Verified against a live server: "!!foo" and
		// {"not":true,"value":"!foo"} match identically. A naive startsWith("!")
		// port would escape this unnecessarily and diverge from the Java client.
		{mockserver.OptionalLiteral("!foo"), `{"not":false,"optional":true,"value":"!foo"}`},
	} {
		got, err := json.Marshal(tc.value)
		if err != nil {
			t.Fatalf("marshal %+v: %v", tc.value, err)
		}
		if string(got) != tc.want {
			t.Errorf("Marshal(%+v) = %s, want %s", tc.value, got, tc.want)
		}
	}
}

// A blank value must NOT use the object form: the server's object-form reader
// ignores a blank "value", so escaping one would silently drop the matcher.
func TestMatcherValue_BlankNeverUsesObjectForm(t *testing.T) {
	for _, v := range []mockserver.MatcherValue{
		mockserver.Literal(""),
		mockserver.Literal("   "),
		mockserver.NotLiteral(""),
	} {
		got, err := json.Marshal(v)
		if err != nil {
			t.Fatalf("marshal %+v: %v", v, err)
		}
		if got[0] != '"' {
			t.Errorf("Marshal(%+v) = %s, want a plain string (object form drops a blank value)", v, got)
		}
	}
}

func TestMatcherValue_RoundTripsBothWireForms(t *testing.T) {
	for _, wire := range []string{`"foo"`, `"!foo"`, `{"not":false,"value":"!foo"}`, `{"not":true,"value":"!foo"}`} {
		var m mockserver.MatcherValue
		if err := json.Unmarshal([]byte(wire), &m); err != nil {
			t.Fatalf("unmarshal %s: %v", wire, err)
		}
		out, err := json.Marshal(m)
		if err != nil {
			t.Fatalf("remarshal %s: %v", wire, err)
		}
		var a, b mockserver.MatcherValue
		_ = json.Unmarshal([]byte(wire), &a)
		_ = json.Unmarshal(out, &b)
		if a != b {
			t.Errorf("%s round-tripped to %s (%+v != %+v)", wire, out, a, b)
		}
	}
}

// The matcher maps must replace their plain counterparts on the wire.
func TestHttpRequest_MatcherMapsReplacePlainMaps(t *testing.T) {
	req := mockserver.Request().
		Path("/x").
		HeaderMatcher("X-Tag", mockserver.Literal("!foo")).
		QueryStringParameterMatcher("q", mockserver.Literal("!foo")).
		CookieMatcher("ck", mockserver.Literal("!foo")).
		PathParameterMatcher("id", mockserver.Literal("!foo")).
		Build()

	got, err := json.Marshal(req)
	if err != nil {
		t.Fatalf("marshal: %v", err)
	}
	var decoded map[string]interface{}
	if err := json.Unmarshal(got, &decoded); err != nil {
		t.Fatalf("unmarshal: %v", err)
	}
	for _, field := range []string{"headers", "queryStringParameters", "cookies", "pathParameters"} {
		if _, ok := decoded[field]; !ok {
			t.Errorf("%q missing from wire form: %s", field, got)
		}
	}
	if n := len(decoded); n == 0 {
		t.Fatalf("empty wire form")
	}
	// the escaped object form must actually be present, not a bare "!foo"
	hdr := decoded["headers"].(map[string]interface{})["X-Tag"].([]interface{})[0]
	if _, isObject := hdr.(map[string]interface{}); !isObject {
		t.Errorf("header value serialised as %#v, want the object form", hdr)
	}
}

// Plain maps alone must serialise exactly as before.
func TestHttpRequest_PlainMapsUnchanged(t *testing.T) {
	req := mockserver.Request().Path("/x").Header("X-Tag", "foo").Build()
	got, err := json.Marshal(req)
	if err != nil {
		t.Fatalf("marshal: %v", err)
	}
	var decoded map[string]interface{}
	_ = json.Unmarshal(got, &decoded)
	hdr := decoded["headers"].(map[string]interface{})["X-Tag"].([]interface{})[0]
	if hdr != "foo" {
		t.Errorf("header serialised as %#v, want plain \"foo\"", hdr)
	}
}

// The escape must survive a decode, not just an encode. Without UnmarshalJSON
// the object form fails to decode into the plain map with "cannot unmarshal
// object into ... of type string", which makes the escape write-only.
func TestHttpRequest_ObjectFormRoundTripsThroughDecode(t *testing.T) {
	original := mockserver.Request().Path("/x").
		HeaderMatcher("X-Tag", mockserver.Literal("!foo")).
		QueryStringParameterMatcher("q", mockserver.Literal("!foo")).
		CookieMatcher("ck", mockserver.Literal("!foo")).
		PathParameterMatcher("id", mockserver.Literal("!foo")).
		Build()

	wire, err := json.Marshal(original)
	if err != nil {
		t.Fatalf("marshal: %v", err)
	}

	var decoded mockserver.HttpRequest
	if err := json.Unmarshal(wire, &decoded); err != nil {
		t.Fatalf("decoding the client's own output failed: %v\nwire: %s", err, wire)
	}

	if got := decoded.HeaderMatchers["X-Tag"]; len(got) != 1 || got[0].Value != "!foo" || got[0].Not {
		t.Errorf("header decoded as %+v, want the literal !foo", got)
	}
	if got := decoded.CookieMatchers["ck"]; got.Value != "!foo" || got.Not {
		t.Errorf("cookie decoded as %+v, want the literal !foo", got)
	}

	// and re-encoding must reproduce the same wire form
	rewire, err := json.Marshal(decoded)
	if err != nil {
		t.Fatalf("re-marshal: %v", err)
	}
	var a, b map[string]interface{}
	_ = json.Unmarshal(wire, &a)
	_ = json.Unmarshal(rewire, &b)
	if fmt.Sprint(a) != fmt.Sprint(b) {
		t.Errorf("round-trip changed the wire form:\n  before %s\n  after  %s", wire, rewire)
	}
}

// Plain-form traffic must decode into the PLAIN maps exactly as before, leaving
// the matcher maps nil, so nothing about existing behaviour changes.
func TestHttpRequest_PlainFormDecodesIntoPlainMaps(t *testing.T) {
	wire := []byte(`{"path":"/x","headers":{"X-Tag":["foo","!bar"]},"cookies":{"ck":"v"}}`)
	var decoded mockserver.HttpRequest
	if err := json.Unmarshal(wire, &decoded); err != nil {
		t.Fatalf("unmarshal: %v", err)
	}
	if decoded.HeaderMatchers != nil {
		t.Errorf("plain input populated HeaderMatchers: %+v", decoded.HeaderMatchers)
	}
	want := []string{"foo", "!bar"}
	if got := decoded.Headers["X-Tag"]; len(got) != 2 || got[0] != want[0] || got[1] != want[1] {
		t.Errorf("headers decoded as %+v, want %+v", got, want)
	}
	if decoded.Cookies["ck"] != "v" {
		t.Errorf("cookies decoded as %+v", decoded.Cookies)
	}
	rewire, _ := json.Marshal(decoded)
	var a, b map[string]interface{}
	_ = json.Unmarshal(wire, &a)
	_ = json.Unmarshal(rewire, &b)
	if fmt.Sprint(a) != fmt.Sprint(b) {
		t.Errorf("plain round-trip changed the wire form:\n  before %s\n  after  %s", wire, rewire)
	}
}

// An empty non-nil map must stay off the wire, as it was before the matcher
// fields existed.
func TestHttpRequest_EmptyMapsAreOmitted(t *testing.T) {
	req := mockserver.HttpRequest{
		Path:               "/x",
		Headers:            map[string][]string{},
		QueryStringParams:  map[string][]string{},
		Cookies:            map[string]string{},
		PathParametersList: map[string][]string{},
	}
	wire, err := json.Marshal(req)
	if err != nil {
		t.Fatalf("marshal: %v", err)
	}
	if string(wire) != `{"path":"/x"}` {
		t.Errorf("empty maps leaked onto the wire: %s, want {\"path\":\"/x\"}", wire)
	}
}

// A type mismatch on `path`/`method` must be tolerated so the parent decode can
// finish, and a mismatch on ANY other field must still be reported.
//
// Tolerating everything would leave a mismatched scalar silently at its zero
// value and lose an error the stdlib used to surface; tolerating nothing would
// abort the enclosing Expectation decode and drop httpResponse. The match is on
// the LAST segment of UnmarshalTypeError.Field because it arrives qualified as
// "plain.path" via the embedded alias -- comparing the whole string to "path"
// never fires, silently restoring the object-wide data loss.
func TestHttpRequest_TypeMismatchToleranceIsNarrow(t *testing.T) {
	for _, tc := range []struct {
		name      string
		wire      string
		wantError bool
	}{
		{"object-form path is tolerated", `{"path":{"not":false,"value":"!p"},"secure":true}`, false},
		{"object-form method is tolerated", `{"method":{"not":false,"value":"!m"},"secure":true}`, false},
		{"mismatched secure is reported", `{"path":"/x","secure":"not-a-bool"}`, true},
		{"mismatched keepAlive is reported", `{"path":"/x","keepAlive":"nope"}`, true},
		{"mismatched headers is reported", `{"path":"/x","headers":5}`, true},
		{"malformed json is reported", `{"path":"/x",`, true},
	} {
		t.Run(tc.name, func(t *testing.T) {
			var r mockserver.HttpRequest
			err := json.Unmarshal([]byte(tc.wire), &r)
			if tc.wantError && err == nil {
				t.Errorf("decoding %s returned no error; the field was silently left at its zero value", tc.wire)
			}
			if !tc.wantError && err != nil {
				t.Errorf("decoding %s returned %v; a tolerated mismatch must not abort the parent decode", tc.wire, err)
			}
		})
	}
}

// The tolerated path mismatch must not cost the sibling fields.
func TestHttpRequest_ToleratedMismatchStillDecodesSiblings(t *testing.T) {
	var r mockserver.HttpRequest
	if err := json.Unmarshal([]byte(`{"path":{"not":false,"value":"!p"},"secure":true,"headers":{"X":["v"]}}`), &r); err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if r.Secure == nil || !*r.Secure {
		t.Errorf("secure lost alongside the tolerated path mismatch: %+v", r.Secure)
	}
	if got := r.Headers["X"]; len(got) != 1 || got[0] != "v" {
		t.Errorf("headers lost alongside the tolerated path mismatch: %+v", r.Headers)
	}
}
