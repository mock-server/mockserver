package mockserver_test

import (
	"encoding/json"
	"os"
	"reflect"
	"testing"

	mockserver "github.com/mock-server/mockserver-monorepo/mockserver-client-go/v9"
)

// A JSONPath or XPath body carries the same date-time object form as a header
// value, under "dateTime", and nothing extra without one.
func TestTypedBody_DateTimeMarshalsTheObjectForm(t *testing.T) {
	for _, tc := range []struct {
		name string
		body *mockserver.TypedBody
		want string
	}{
		{"json path", mockserver.JSONPathBody("$.order.created").WithDateTime(mockserver.After("now -1 days").WithZone("Europe/London")),
			`{"type":"JSON_PATH","jsonPath":"$.order.created","dateTime":{"after":"now -1 days","zone":"Europe/London"}}`},
		{"xpath", mockserver.XPathBody("/order/@expires").WithDateTime(mockserver.Before("2026-01-01").WithNot(true)),
			`{"type":"XPATH","xpath":"/order/@expires","dateTime":{"not":true,"before":"2026-01-01"}}`},
		{"json path without", mockserver.JSONPathBody("$.a"), `{"type":"JSON_PATH","jsonPath":"$.a"}`},
		{"xpath without", mockserver.XPathBody("/a"), `{"type":"XPATH","xpath":"/a"}`},
	} {
		t.Run(tc.name, func(t *testing.T) {
			got, err := json.Marshal(tc.body)
			if err != nil {
				t.Fatalf("marshal: %v", err)
			}
			if string(got) != tc.want {
				t.Errorf("got %s, want %s", got, tc.want)
			}
		})
	}
}

// The shared fixtures decode into a typed body whose dateTime is a date-time
// MatcherValue, and re-encode to the same JSON.
func TestTypedBody_SharedFixturesDecodeTyped(t *testing.T) {
	for _, name := range []string{"matcher_body_json_path_date_time.json", "matcher_body_xpath_date_time.json"} {
		t.Run(name, func(t *testing.T) {
			data, err := os.ReadFile("../test-fixtures/expectations/" + name)
			if err != nil {
				t.Fatalf("read fixture: %v", err)
			}
			var fixture struct {
				HttpRequest struct {
					Body json.RawMessage `json:"body"`
				} `json:"httpRequest"`
			}
			if err := json.Unmarshal(data, &fixture); err != nil {
				t.Fatalf("unmarshal: %v", err)
			}
			var body mockserver.TypedBody
			if err := json.Unmarshal(fixture.HttpRequest.Body, &body); err != nil {
				t.Fatalf("unmarshal body: %v", err)
			}
			if body.DateTime == nil || body.DateTime.DateTime == nil {
				t.Fatalf("dateTime decoded as %+v", body.DateTime)
			}
			rewire, err := json.Marshal(body)
			if err != nil {
				t.Fatalf("re-marshal: %v", err)
			}
			var a, b interface{}
			_ = json.Unmarshal(fixture.HttpRequest.Body, &a)
			_ = json.Unmarshal(rewire, &b)
			if !reflect.DeepEqual(a, b) {
				t.Errorf("round trip changed the body:\n  before %s\n  after  %s", fixture.HttpRequest.Body, rewire)
			}
		})
	}
}
