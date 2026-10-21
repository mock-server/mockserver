package mockserver_test

import (
	"encoding/json"
	"os"
	"reflect"
	"testing"

	mockserver "github.com/mock-server/mockserver-monorepo/mockserver-client-go/v9"
)

// The builders must produce exactly the object form the Java serialiser writes:
// "not" only when true, options only when set, never null.
func TestDateTimeMatcher_BuilderToJSONAndBack(t *testing.T) {
	for _, tc := range []struct {
		name  string
		value mockserver.MatcherValue
		want  string
	}{
		{"after", mockserver.After("now -1 hours"), `{"after":"now -1 hours"}`},
		{
			"before with format",
			mockserver.Before("2026-10-21T00:00:00Z").WithActualFormat("rfc1123"),
			`{"before":"2026-10-21T00:00:00Z","actualFormat":"rfc1123"}`,
		},
		{
			"every option",
			mockserver.EqualToDateTime("now +1 days").
				WithTruncateExpected("first day of month").
				WithTruncateActual("startOfDay").
				WithApplyTruncationLast(true).
				WithZone("Europe/London").
				WithNot(true),
			`{"not":true,"equalToDateTime":"now +1 days","truncateExpected":"first day of month","truncateActual":"startOfDay","applyTruncationLast":true,"zone":"Europe/London"}`,
		},
		{
			"explicit false applyTruncationLast is kept",
			mockserver.After("now").WithApplyTruncationLast(false),
			`{"after":"now","applyTruncationLast":false}`,
		},
		{"not false is omitted", mockserver.After("now").WithNot(false), `{"after":"now"}`},
	} {
		t.Run(tc.name, func(t *testing.T) {
			got, err := json.Marshal(tc.value)
			if err != nil {
				t.Fatalf("marshal: %v", err)
			}
			if string(got) != tc.want {
				t.Fatalf("Marshal = %s, want %s", got, tc.want)
			}
			var decoded mockserver.MatcherValue
			if err := json.Unmarshal(got, &decoded); err != nil {
				t.Fatalf("unmarshal %s: %v", got, err)
			}
			if !reflect.DeepEqual(decoded, tc.value) {
				t.Errorf("decoded %+v (%+v), want %+v (%+v)", decoded, decoded.DateTime, tc.value, tc.value.DateTime)
			}
			again, err := json.Marshal(decoded)
			if err != nil {
				t.Fatalf("re-marshal: %v", err)
			}
			if string(again) != tc.want {
				t.Errorf("re-marshal = %s, want %s", again, tc.want)
			}
			if decoded.String() != tc.want {
				t.Errorf("String() = %s, want %s", decoded.String(), tc.want)
			}
		})
	}
}

// Each option must return a new value, leaving the receiver unchanged even
// though the date-time half is held through a pointer.
func TestDateTimeMatcher_OptionsAreImmutable(t *testing.T) {
	base := mockserver.After("now")
	withZone := base.WithZone("Europe/London")
	withFormat := withZone.WithActualFormat("iso")
	_ = base.WithNot(true).WithApplyTruncationLast(true)

	if base.DateTime.Zone != "" || base.Not || base.DateTime.ApplyTruncationLast != nil {
		t.Errorf("base changed by its options: %+v %+v", base, base.DateTime)
	}
	if withZone.DateTime.ActualFormat != "" {
		t.Errorf("WithActualFormat changed its receiver: %+v", withZone.DateTime)
	}
	if withFormat.DateTime.Zone != "Europe/London" || withFormat.DateTime.ActualFormat != "iso" {
		t.Errorf("options not carried forward: %+v", withFormat.DateTime)
	}
}

// A plain string that happens to look like an operator is a literal, not a
// date-time matcher, and must stay byte-identical on the wire.
func TestDateTimeMatcher_PlainOperatorTextStaysPlainString(t *testing.T) {
	var m mockserver.MatcherValue
	if err := json.Unmarshal([]byte(`"before:2026-01-01"`), &m); err != nil {
		t.Fatalf("unmarshal: %v", err)
	}
	if m.DateTime != nil || m.Value != "before:2026-01-01" {
		t.Fatalf("decoded as %+v (%+v), want the plain literal", m, m.DateTime)
	}
	if got, _ := json.Marshal(m); string(got) != `"before:2026-01-01"` {
		t.Errorf("re-marshalled as %s", got)
	}

	wire := []byte(`{"headers":{"X-Plain":["before:2026-01-01"]}}`)
	var req mockserver.HttpRequest
	if err := json.Unmarshal(wire, &req); err != nil {
		t.Fatalf("unmarshal request: %v", err)
	}
	if req.HeaderMatchers != nil {
		t.Errorf("plain value populated HeaderMatchers: %+v", req.HeaderMatchers)
	}
	if got, _ := json.Marshal(req); string(got) != string(wire) {
		t.Errorf("request re-marshalled as %s, want %s", got, wire)
	}
}

func TestDateTimeMatcher_RejectsInvalidShapes(t *testing.T) {
	for _, wire := range []string{
		`{"before":"now","after":"now"}`,
		`{"actualFormat":"iso"}`,
	} {
		var m mockserver.MatcherValue
		if err := json.Unmarshal([]byte(wire), &m); err == nil {
			t.Errorf("decoding %s succeeded as %+v (%+v), want an error", wire, m, m.DateTime)
		}
	}
	for name, v := range map[string]mockserver.MatcherValue{
		"option on a literal": mockserver.Literal("x").WithZone("UTC"),
		"with a value":        {Value: "x", DateTime: mockserver.After("now").DateTime},
		"optional":            {Optional: true, DateTime: mockserver.After("now").DateTime},
	} {
		if got, err := json.Marshal(v); err == nil {
			t.Errorf("%s: marshalled as %s, want an error", name, got)
		}
	}
}

// Date-time values must work through every request matcher map, and survive a
// decode back into the typed form.
func TestHttpRequest_DateTimeMatchersRoundTrip(t *testing.T) {
	original := mockserver.Request().Path("/x/{issued}").
		HeaderMatcher("X-Issued-At", mockserver.After("now -1 hours")).
		QueryStringParameterMatcher("from", mockserver.After("2026-01-01").WithZone("Europe/London")).
		CookieMatcher("seen", mockserver.EqualToDateTime("now").WithNot(true)).
		PathParameterMatcher("issued", mockserver.Before("now").WithActualFormat("epochSeconds")).
		Build()

	wire, err := json.Marshal(original)
	if err != nil {
		t.Fatalf("marshal: %v", err)
	}
	var decoded mockserver.HttpRequest
	if err := json.Unmarshal(wire, &decoded); err != nil {
		t.Fatalf("unmarshal %s: %v", wire, err)
	}
	if !reflect.DeepEqual(decoded.HeaderMatchers, original.HeaderMatchers) ||
		!reflect.DeepEqual(decoded.QueryStringParameterMatchers, original.QueryStringParameterMatchers) ||
		!reflect.DeepEqual(decoded.CookieMatchers, original.CookieMatchers) ||
		!reflect.DeepEqual(decoded.PathParameterMatchers, original.PathParameterMatchers) {
		t.Errorf("matchers changed by a round trip through %s", wire)
	}
	rewire, err := json.Marshal(decoded)
	if err != nil {
		t.Fatalf("re-marshal: %v", err)
	}
	var a, b interface{}
	_ = json.Unmarshal(wire, &a)
	_ = json.Unmarshal(rewire, &b)
	if !reflect.DeepEqual(a, b) {
		t.Errorf("round trip changed the wire form:\n  before %s\n  after  %s", wire, rewire)
	}
}

// The shared fixture must decode into the typed date-time form, not merely
// survive the generic round trip.
func TestDateTimeMatcher_SharedFixtureDecodesTyped(t *testing.T) {
	data, err := os.ReadFile("../test-fixtures/expectations/matcher_request_date_time.json")
	if err != nil {
		t.Fatalf("read fixture: %v", err)
	}
	var e mockserver.Expectation
	if err := json.Unmarshal(data, &e); err != nil {
		t.Fatalf("unmarshal: %v", err)
	}
	req := e.HttpRequest
	if req == nil {
		t.Fatalf("httpRequest missing")
	}
	issued := req.HeaderMatchers["X-Issued-At"]
	if len(issued) != 1 || issued[0].DateTime == nil ||
		issued[0].DateTime.Operator != mockserver.DateTimeAfter || issued[0].DateTime.Expected != "now -1 hours" {
		t.Errorf("X-Issued-At decoded as %+v", issued)
	}
	plain := req.HeaderMatchers["X-Plain"]
	if len(plain) != 1 || plain[0].DateTime != nil || plain[0].Value != "before:2026-01-01" {
		t.Errorf("X-Plain decoded as %+v, want the plain literal", plain)
	}
	from := req.QueryStringParameterMatchers["from"]
	if len(from) != 1 || from[0].DateTime == nil || from[0].DateTime.Zone != "Europe/London" ||
		from[0].DateTime.ActualFormat != "dd/MM/yyyy" {
		t.Errorf("from decoded as %+v", from)
	}
	seen := req.CookieMatchers["seen"]
	if seen.DateTime == nil || !seen.Not || seen.DateTime.Operator != mockserver.DateTimeEqualTo ||
		seen.DateTime.ApplyTruncationLast == nil || !*seen.DateTime.ApplyTruncationLast ||
		seen.DateTime.TruncateActual != "startOfDay" || seen.DateTime.TruncateExpected != "first day of month" {
		t.Errorf("seen cookie decoded as %+v (%+v)", seen, seen.DateTime)
	}
}
