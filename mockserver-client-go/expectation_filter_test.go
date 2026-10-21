package mockserver

import (
	"bytes"
	"encoding/json"
	"io"
	"net/http"
	"net/http/httptest"
	"os"
	"reflect"
	"strings"
	"sync"
	"testing"
)

type capturedRequest struct {
	method   string
	path     string
	rawQuery string
	body     string
}

// captureServer records every request and answers with responseBody.
func captureServer(t *testing.T, responseBody string) (*Client, *[]capturedRequest) {
	t.Helper()
	var mu sync.Mutex
	captured := &[]capturedRequest{}
	ts := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		body, _ := io.ReadAll(r.Body)
		mu.Lock()
		*captured = append(*captured, capturedRequest{r.Method, r.URL.Path, r.URL.RawQuery, string(body)})
		mu.Unlock()
		w.WriteHeader(200)
		_, _ = w.Write([]byte(responseBody))
	}))
	t.Cleanup(ts.Close)
	return NewFromURL(ts.URL), captured
}

func onlyRequest(t *testing.T, captured *[]capturedRequest) capturedRequest {
	t.Helper()
	if len(*captured) != 1 {
		t.Fatalf("expected exactly 1 request, got %d", len(*captured))
	}
	return (*captured)[0]
}

func decodeWithNumbers(t *testing.T, data []byte) any {
	t.Helper()
	dec := json.NewDecoder(bytes.NewReader(data))
	dec.UseNumber()
	var v any
	if err := dec.Decode(&v); err != nil {
		t.Fatalf("decode %s: %v", data, err)
	}
	return v
}

// --- metadata model round trip ---

func TestMetadata_SharedFixtureRoundTripsExactly(t *testing.T) {
	data, err := os.ReadFile("../test-fixtures/expectations/top_metadata.json")
	if err != nil {
		t.Fatal(err)
	}
	var exp Expectation
	if err := json.Unmarshal(data, &exp); err != nil {
		t.Fatal(err)
	}
	out, err := json.Marshal(exp)
	if err != nil {
		t.Fatal(err)
	}
	in := decodeWithNumbers(t, data).(map[string]any)["metadata"]
	got := decodeWithNumbers(t, out).(map[string]any)["metadata"]
	if !reflect.DeepEqual(in, got) {
		t.Fatalf("metadata did not round-trip:\n in: %#v\nout: %#v", in, got)
	}
	gotMap := got.(map[string]any)
	if v, present := gotMap["note"]; !present || v != nil {
		t.Errorf("explicit null must survive, got present=%v value=%#v", present, v)
	}
	if gotMap["ticket"] != json.Number("4211") || gotMap["ratio"] != json.Number("0.5") {
		t.Errorf("numbers must keep their form, got ticket=%#v ratio=%#v", gotMap["ticket"], gotMap["ratio"])
	}
	if exp.Metadata["literal"] != "!not?optional 100%+a b" {
		t.Errorf("literal string changed: %#v", exp.Metadata["literal"])
	}
}

func TestMetadata_LargeNumbersKeepPrecision(t *testing.T) {
	in := `{"metadata":{"big":12345678901234567890,"exp":1.5e300,"neg":-0.000001}}`
	var exp Expectation
	if err := json.Unmarshal([]byte(in), &exp); err != nil {
		t.Fatal(err)
	}
	out, _ := json.Marshal(exp)
	if string(out) != `{"metadata":{"big":12345678901234567890,"exp":1.5e300,"neg":-0.000001}}` {
		t.Errorf("unexpected re-encoding: %s", out)
	}
}

func TestMetadata_OmittedWhenEmpty(t *testing.T) {
	for _, exp := range []Expectation{{ID: "a"}, {ID: "a", Metadata: Metadata{}}} {
		out, _ := json.Marshal(exp)
		if strings.Contains(string(out), "metadata") {
			t.Errorf("metadata must be omitted when empty, got %s", out)
		}
	}
}

func TestMetadata_FluentSettersSendMetadata(t *testing.T) {
	client, captured := captureServer(t, "[]")
	_, err := client.When(Request().Path("/a")).
		WithMetadataEntry("owner", "team-a").
		WithMetadataEntry("ticket", 42).
		WithMetadataEntry("note", nil).
		Respond(Response().StatusCode(200))
	if err != nil {
		t.Fatal(err)
	}
	_, err = client.When(Request().Path("/b"), WithExpectationMetadata(map[string]any{"tags": []string{"smoke"}})).
		Respond(Response().StatusCode(200))
	if err != nil {
		t.Fatal(err)
	}
	_, err = client.When(Request().Path("/c")).WithMetadata(map[string]any{"flaky": false}).
		Respond(Response().StatusCode(200))
	if err != nil {
		t.Fatal(err)
	}
	wants := []string{
		`"metadata":{"note":null,"owner":"team-a","ticket":42}`,
		`"metadata":{"tags":["smoke"]}`,
		`"metadata":{"flaky":false}`,
	}
	if len(*captured) != len(wants) {
		t.Fatalf("expected %d requests, got %d", len(wants), len(*captured))
	}
	for i, want := range wants {
		if !strings.Contains((*captured)[i].body, want) {
			t.Errorf("request %d body %s does not contain %s", i, (*captured)[i].body, want)
		}
	}
}

func TestRetrieveActiveExpectationsByMetadata_DecodesMetadata(t *testing.T) {
	client, _ := captureServer(t, `[{"id":"x","httpRequest":{"path":"/m"},"metadata":{"owner":"team-a","ticket":7,"note":null}}]`)
	exps, err := client.RetrieveActiveExpectationsByMetadata("owner", "team-a")
	if err != nil {
		t.Fatal(err)
	}
	if len(exps) != 1 {
		t.Fatalf("expected 1 expectation, got %d", len(exps))
	}
	want := Metadata{"owner": "team-a", "ticket": json.Number("7"), "note": nil}
	if !reflect.DeepEqual(exps[0].Metadata, want) {
		t.Errorf("got metadata %#v, want %#v", exps[0].Metadata, want)
	}
}

// --- wire contract ---

func TestRetrieveActiveExpectationsByMetadata_EncodesValueLiterally(t *testing.T) {
	cases := map[string]string{
		"!team-a": "%21team-a",
		"?x":      "%3Fx",
		"100%":    "100%25",
		"a+b":     "a%2Bb",
		"a b":     "a+b",
		"a&b":     "a%26b",
		"a#b":     "a%23b",
		"a=b":     "a%3Db",
	}
	for value, encoded := range cases {
		t.Run(value, func(t *testing.T) {
			client, captured := captureServer(t, "[]")
			if _, err := client.RetrieveActiveExpectationsByMetadata("owner", value); err != nil {
				t.Fatal(err)
			}
			req := onlyRequest(t, captured)
			want := "format=json&metadataKey=owner&metadataValue=" + encoded + "&type=active_expectations"
			if req.method != "PUT" || req.path != "/mockserver/retrieve" || req.rawQuery != want {
				t.Errorf("got %s %s?%s, want PUT /mockserver/retrieve?%s", req.method, req.path, req.rawQuery, want)
			}
			if req.body != "" {
				t.Errorf("expected empty body, got %q", req.body)
			}
		})
	}
}

func TestClearByMetadata_EncodesValueLiterally(t *testing.T) {
	cases := map[string]string{
		"!team-a": "%21team-a",
		"?x":      "%3Fx",
		"100%":    "100%25",
		"a+b":     "a%2Bb",
		"a b":     "a+b",
	}
	for value, encoded := range cases {
		t.Run(value, func(t *testing.T) {
			client, captured := captureServer(t, "")
			if err := client.ClearByMetadata("owner", value); err != nil {
				t.Fatal(err)
			}
			req := onlyRequest(t, captured)
			want := "metadataKey=owner&metadataValue=" + encoded + "&type=expectations"
			if req.method != "PUT" || req.path != "/mockserver/clear" || req.rawQuery != want {
				t.Errorf("got %s %s?%s, want PUT /mockserver/clear?%s", req.method, req.path, req.rawQuery, want)
			}
		})
	}
}

func TestMetadataValue_NonStringValuesSentAsCompactJSON(t *testing.T) {
	cases := []struct {
		value   any
		encoded string
	}{
		{42, "42"},
		{0.5, "0.5"},
		{true, "true"},
		{json.Number("12345678901234567890"), "12345678901234567890"},
		{[]string{"a", "b"}, "%5B%22a%22%2C%22b%22%5D"},
		{map[string]any{"team": "<a&b>"}, "%7B%22team%22%3A%22%3Ca%26b%3E%22%7D"},
		{MetadataJSONNull, "null"},
		{json.RawMessage(` { "a" : 1 } `), "%7B%22a%22%3A1%7D"},
	}
	for _, tc := range cases {
		client, captured := captureServer(t, "[]")
		if _, err := client.RetrieveActiveExpectationsByMetadata("k", tc.value); err != nil {
			t.Fatalf("%#v: %v", tc.value, err)
		}
		want := "format=json&metadataKey=k&metadataValue=" + tc.encoded + "&type=active_expectations"
		if got := onlyRequest(t, captured).rawQuery; got != want {
			t.Errorf("%#v: got %s, want %s", tc.value, got, want)
		}
	}
}

func TestKeyOnlyFormsCarryNoValue(t *testing.T) {
	client, captured := captureServer(t, "[]")
	if _, err := client.RetrieveActiveExpectationsByMetadataKey("!owner"); err != nil {
		t.Fatal(err)
	}
	if err := client.ClearByMetadataKey("owner"); err != nil {
		t.Fatal(err)
	}
	// switching a filter from key+value to key-only drops the value
	filter := NewExpectationFilter().WithMetadata("owner", "team-a").WithMetadataKey("owner")
	if _, err := client.RetrieveActiveExpectationsWithFilter(nil, filter); err != nil {
		t.Fatal(err)
	}
	wants := []string{
		"format=json&metadataKey=%21owner&type=active_expectations",
		"metadataKey=owner&type=expectations",
		"format=json&metadataKey=owner&type=active_expectations",
	}
	for i, want := range wants {
		if got := (*captured)[i].rawQuery; got != want {
			t.Errorf("request %d: got %s, want %s", i, got, want)
		}
	}
}

func TestRetrieveUnmatchedAndClearUnmatched(t *testing.T) {
	client, captured := captureServer(t, "[]")
	if _, err := client.RetrieveUnmatchedExpectations(); err != nil {
		t.Fatal(err)
	}
	if err := client.ClearUnmatchedExpectations(); err != nil {
		t.Fatal(err)
	}
	if got := (*captured)[0]; got.path != "/mockserver/retrieve" || got.rawQuery != "format=json&type=active_expectations&unmatched=true" {
		t.Errorf("retrieve unmatched: got %s?%s", got.path, got.rawQuery)
	}
	if got := (*captured)[1]; got.path != "/mockserver/clear" || got.rawQuery != "type=expectations&unmatched=true" {
		t.Errorf("clear unmatched: got %s?%s", got.path, got.rawQuery)
	}
}

func TestRetrieveActiveExpectationsWithFilter_AllParameters(t *testing.T) {
	client, captured := captureServer(t, "[]")
	filter := NewExpectationFilter().
		WithMetadataJSONPath("$.tags[?(@ == 'smoke')]").
		WithUnmatched(true).
		WithOffset(5).
		WithLimit(10)
	if _, err := client.RetrieveActiveExpectationsWithFilter(Request().Path("/api"), filter); err != nil {
		t.Fatal(err)
	}
	req := onlyRequest(t, captured)
	want := "format=json&limit=10&metadataJsonPath=%24.tags%5B%3F%28%40+%3D%3D+%27smoke%27%29%5D&offset=5&type=active_expectations&unmatched=true"
	if req.rawQuery != want {
		t.Errorf("got %s, want %s", req.rawQuery, want)
	}
	if req.body != `{"path":"/api"}` {
		t.Errorf("expected request matcher body, got %s", req.body)
	}
}

func TestRetrieveActiveExpectationsWithFilter_NilFilterAndZeroPaging(t *testing.T) {
	client, captured := captureServer(t, "[]")
	if _, err := client.RetrieveActiveExpectationsWithFilter(nil, nil); err != nil {
		t.Fatal(err)
	}
	if _, err := client.RetrieveActiveExpectationsWithFilter(nil, NewExpectationFilter().WithOffset(0).WithLimit(0)); err != nil {
		t.Fatal(err)
	}
	if got := (*captured)[0].rawQuery; got != "format=json&type=active_expectations" {
		t.Errorf("nil filter: got %s", got)
	}
	if got := (*captured)[1].rawQuery; got != "format=json&limit=0&offset=0&type=active_expectations" {
		t.Errorf("zero paging: got %s", got)
	}
}

func TestRetrieveRecordedExpectationsPage(t *testing.T) {
	client, captured := captureServer(t, `[{"httpRequest":{"path":"/r"}}]`)
	exps, err := client.RetrieveRecordedExpectationsPage(Request().Path("/r"), 3, 2)
	if err != nil {
		t.Fatal(err)
	}
	if len(exps) != 1 {
		t.Errorf("expected 1 expectation, got %d", len(exps))
	}
	req := onlyRequest(t, captured)
	if req.rawQuery != "format=json&limit=2&offset=3&type=recorded_expectations" || req.body != `{"path":"/r"}` {
		t.Errorf("got %s body %s", req.rawQuery, req.body)
	}
}

func TestClearWithFilter_RequestMatcherAndJSONPath(t *testing.T) {
	client, captured := captureServer(t, "")
	filter := NewExpectationFilter().WithMetadata("owner", "team-a").WithMetadataJSONPath("$.ticket")
	if err := client.ClearWithFilter(Request().Path("/x"), filter); err != nil {
		t.Fatal(err)
	}
	req := onlyRequest(t, captured)
	if req.rawQuery != "metadataJsonPath=%24.ticket&metadataKey=owner&metadataValue=team-a&type=expectations" {
		t.Errorf("got %s", req.rawQuery)
	}
	if req.body != `{"path":"/x"}` {
		t.Errorf("expected request matcher body, got %s", req.body)
	}
}

// --- fail-closed rejections: an error and nothing sent ---

func TestFilterRejectionsSendNothing(t *testing.T) {
	var nilString *string
	var nilSlice []string
	var nilMap map[string]any
	cases := map[string]func(c *Client) error{
		"retrieve blank key":         func(c *Client) error { _, err := c.RetrieveActiveExpectationsByMetadataKey(""); return err },
		"retrieve whitespace key":    func(c *Client) error { _, err := c.RetrieveActiveExpectationsByMetadata(" \t", "v"); return err },
		"retrieve nil value":         func(c *Client) error { _, err := c.RetrieveActiveExpectationsByMetadata("k", nil); return err },
		"retrieve typed nil pointer": func(c *Client) error { _, err := c.RetrieveActiveExpectationsByMetadata("k", nilString); return err },
		"retrieve nil slice":         func(c *Client) error { _, err := c.RetrieveActiveExpectationsByMetadata("k", nilSlice); return err },
		"retrieve nil map":           func(c *Client) error { _, err := c.RetrieveActiveExpectationsByMetadata("k", nilMap); return err },
		"retrieve invalid raw json": func(c *Client) error {
			_, err := c.RetrieveActiveExpectationsByMetadata("k", json.RawMessage("{"))
			return err
		},
		"retrieve nil raw json": func(c *Client) error {
			_, err := c.RetrieveActiveExpectationsByMetadata("k", json.RawMessage(nil))
			return err
		},
		"retrieve blank json path": func(c *Client) error {
			_, err := c.RetrieveActiveExpectationsWithFilter(nil, NewExpectationFilter().WithMetadataJSONPath(" "))
			return err
		},
		"retrieve negative offset": func(c *Client) error {
			_, err := c.RetrieveActiveExpectationsWithFilter(nil, NewExpectationFilter().WithOffset(-1))
			return err
		},
		"retrieve negative limit": func(c *Client) error {
			_, err := c.RetrieveActiveExpectationsWithFilter(nil, NewExpectationFilter().WithLimit(-1))
			return err
		},
		"recorded negative offset": func(c *Client) error { _, err := c.RetrieveRecordedExpectationsPage(nil, -1, 1); return err },
		"recorded negative limit":  func(c *Client) error { _, err := c.RetrieveRecordedExpectationsPage(nil, 0, -1); return err },
		"clear blank key":          func(c *Client) error { return c.ClearByMetadataKey("") },
		"clear blank key value":    func(c *Client) error { return c.ClearByMetadata("", "v") },
		"clear nil value":          func(c *Client) error { return c.ClearByMetadata("k", nil) },
		"clear typed nil pointer":  func(c *Client) error { return c.ClearByMetadata("k", nilString) },
		"clear nil filter":         func(c *Client) error { return c.ClearWithFilter(nil, nil) },
		"clear empty filter":       func(c *Client) error { return c.ClearWithFilter(Request().Path("/x"), NewExpectationFilter()) },
		"clear unmatched false": func(c *Client) error {
			return c.ClearWithFilter(nil, NewExpectationFilter().WithUnmatched(false))
		},
		"clear paging only": func(c *Client) error {
			return c.ClearWithFilter(nil, NewExpectationFilter().WithLimit(1))
		},
		"clear selection with paging": func(c *Client) error {
			return c.ClearWithFilter(nil, NewExpectationFilter().WithMetadataKey("k").WithOffset(0))
		},
		"clear blank json path": func(c *Client) error {
			return c.ClearWithFilter(nil, NewExpectationFilter().WithMetadataJSONPath(""))
		},
		"clear invalid value then valid key": func(c *Client) error {
			return c.ClearWithFilter(nil, NewExpectationFilter().WithMetadata("k", nil).WithMetadataKey("k"))
		},
	}
	for name, call := range cases {
		t.Run(name, func(t *testing.T) {
			client, captured := captureServer(t, "[]")
			if err := call(client); err == nil {
				t.Error("expected an error")
			}
			if len(*captured) != 0 {
				t.Errorf("expected nothing sent, got %d request(s): %+v", len(*captured), *captured)
			}
		})
	}
}
