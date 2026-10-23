package mockserver

import (
	"testing"
)

func TestRetrieveRecordedExpectationsWithOptions_SendsEveryOption(t *testing.T) {
	client, captured := captureServer(t, `[{"httpRequest":{"method":"GET","path":"/jobs/1"},"httpResponses":[{"statusCode":200,"body":"pending"},{"statusCode":200,"body":"done"}],"responseMode":"SWITCH","times":{"unlimited":true}}]`)
	options := NewRecordedExpectationsOptions().
		WithConsolidate(true).
		WithParameterize(true).
		WithRepeatsAsScenarios(true).
		WithCaptureHeaders("X-Tenant", "Accept").
		WithExtractBodiesLargerThan(1024)

	exps, err := client.RetrieveRecordedExpectationsWithOptions(Request().Path("/jobs/.*"), options)
	if err != nil {
		t.Fatal(err)
	}

	req := onlyRequest(t, captured)
	want := "captureHeaders=X-Tenant%2CAccept&consolidate=true&extractBodiesLargerThan=1024&format=json&parameterize=true&repeatsAsScenarios=true&type=recorded_expectations"
	if req.method != "PUT" || req.path != "/mockserver/retrieve" || req.rawQuery != want || req.body != `{"path":"/jobs/.*"}` {
		t.Errorf("got %s %s?%s body %s", req.method, req.path, req.rawQuery, req.body)
	}
	if len(exps) != 1 || exps[0].ResponseMode != "SWITCH" || len(exps[0].HttpResponses) != 2 {
		t.Errorf("expected the sequenced expectation, got %+v", exps)
	}
}

func TestRetrieveRecordedExpectationsWithOptions_QueryForEachOption(t *testing.T) {
	cases := map[string]struct {
		options *RecordedExpectationsOptions
		want    string
	}{
		"nil options":          {nil, "format=json&type=recorded_expectations"},
		"empty options":        {NewRecordedExpectationsOptions(), "format=json&type=recorded_expectations"},
		"false is not sent":    {NewRecordedExpectationsOptions().WithRepeatsAsScenarios(false), "format=json&type=recorded_expectations"},
		"empty captureHeaders": {NewRecordedExpectationsOptions().WithCaptureHeaders(), "captureHeaders=&format=json&type=recorded_expectations"},
		"zero threshold":       {NewRecordedExpectationsOptions().WithExtractBodiesLargerThan(0), "extractBodiesLargerThan=0&format=json&type=recorded_expectations"},
	}
	for name, tc := range cases {
		t.Run(name, func(t *testing.T) {
			client, captured := captureServer(t, "[]")
			if _, err := client.RetrieveRecordedExpectationsWithOptions(nil, tc.options); err != nil {
				t.Fatal(err)
			}
			if got := onlyRequest(t, captured).rawQuery; got != tc.want {
				t.Errorf("got %s, want %s", got, tc.want)
			}
		})
	}
}

func TestRetrieveRecordedExpectationsWithOptions_RejectsInvalidOptionsSendingNothing(t *testing.T) {
	cases := map[string]*RecordedExpectationsOptions{
		"blank header":         NewRecordedExpectationsOptions().WithCaptureHeaders("X-Tenant", " "),
		"header with a comma":  NewRecordedExpectationsOptions().WithCaptureHeaders("X-Tenant,Accept"),
		"negative threshold":   NewRecordedExpectationsOptions().WithExtractBodiesLargerThan(-1),
		"first error is kept":  NewRecordedExpectationsOptions().WithExtractBodiesLargerThan(-1).WithExtractBodiesLargerThan(10),
		"error survives reset": NewRecordedExpectationsOptions().WithCaptureHeaders("").WithCaptureHeaders("X-Tenant"),
	}
	for name, options := range cases {
		t.Run(name, func(t *testing.T) {
			client, captured := captureServer(t, "[]")
			if _, err := client.RetrieveRecordedExpectationsWithOptions(nil, options); err == nil {
				t.Fatal("expected an error")
			}
			if len(*captured) != 0 {
				t.Errorf("expected nothing sent, got %d requests", len(*captured))
			}
		})
	}
}
