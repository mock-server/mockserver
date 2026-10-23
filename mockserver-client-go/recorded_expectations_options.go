package mockserver

import (
	"fmt"
	"net/url"
	"strconv"
	"strings"
)

// RecordedExpectationsOptions shapes the expectations returned by
// RetrieveRecordedExpectationsWithOptions. Each option is off unless set. The
// builder methods record the first invalid argument; it is returned by the
// client method the options are passed to, before any request is sent.
type RecordedExpectationsOptions struct {
	consolidate             bool
	parameterize            bool
	repeatsAsScenarios      bool
	captureHeaders          []string
	extractBodiesLargerThan *int64
	err                     error
}

// NewRecordedExpectationsOptions returns options with every option off.
func NewRecordedExpectationsOptions() *RecordedExpectationsOptions {
	return &RecordedExpectationsOptions{}
}

func (o *RecordedExpectationsOptions) fail(err error) *RecordedExpectationsOptions {
	if o.err == nil {
		o.err = err
	}
	return o
}

// WithConsolidate collapses recorded exchanges that share a request shape into
// one reusable, unlimited-times expectation.
func (o *RecordedExpectationsOptions) WithConsolidate(consolidate bool) *RecordedExpectationsOptions {
	o.consolidate = consolidate
	return o
}

// WithParameterize also generalises volatile path, query, header and body
// values into matchers (implies consolidate).
func (o *RecordedExpectationsOptions) WithParameterize(parameterize bool) *RecordedExpectationsOptions {
	o.parameterize = parameterize
	return o
}

// WithRepeatsAsScenarios replays a request recorded several times with
// different responses in the recorded order, then keeps returning the last one.
func (o *RecordedExpectationsOptions) WithRepeatsAsScenarios(repeatsAsScenarios bool) *RecordedExpectationsOptions {
	o.repeatsAsScenarios = repeatsAsScenarios
	return o
}

// WithCaptureHeaders keeps only these request headers (case-insensitive) in
// each recorded matcher; no names keeps none.
func (o *RecordedExpectationsOptions) WithCaptureHeaders(headerNames ...string) *RecordedExpectationsOptions {
	for _, name := range headerNames {
		if strings.TrimSpace(name) == "" || strings.Contains(name, ",") {
			return o.fail(fmt.Errorf("mockserver: each capture header must be a non-blank header name without a comma, got %q", name))
		}
	}
	o.captureHeaders = append([]string{}, headerNames...)
	return o
}

// WithExtractBodiesLargerThan writes each response body larger than this many
// bytes to the server's recordedBodyFilesDirectory and returns a FILE body that
// points at it.
func (o *RecordedExpectationsOptions) WithExtractBodiesLargerThan(thresholdBytes int64) *RecordedExpectationsOptions {
	if thresholdBytes < 0 {
		return o.fail(fmt.Errorf("mockserver: extractBodiesLargerThan must be zero or more, got %d", thresholdBytes))
	}
	o.extractBodiesLargerThan = &thresholdBytes
	return o
}

func (o *RecordedExpectationsOptions) applyTo(params url.Values) {
	if o.consolidate {
		params.Set("consolidate", "true")
	}
	if o.parameterize {
		params.Set("parameterize", "true")
	}
	if o.repeatsAsScenarios {
		params.Set("repeatsAsScenarios", "true")
	}
	if o.captureHeaders != nil {
		params.Set("captureHeaders", strings.Join(o.captureHeaders, ","))
	}
	if o.extractBodiesLargerThan != nil {
		params.Set("extractBodiesLargerThan", strconv.FormatInt(*o.extractBodiesLargerThan, 10))
	}
}

// RetrieveRecordedExpectationsWithOptions retrieves the recorded expectations
// matching the optional request matcher, shaped by options, for example
// replaying a request recorded with different responses in order. A nil
// options retrieves as RetrieveRecordedExpectations does.
func (c *Client) RetrieveRecordedExpectationsWithOptions(rb *RequestBuilder, options *RecordedExpectationsOptions) ([]Expectation, error) {
	params := make(url.Values)
	if options != nil {
		if options.err != nil {
			return nil, options.err
		}
		options.applyTo(params)
	}
	return c.retrieveExpectationsWithParams(rb, RetrieveRecordedExpectations, params, "recorded expectations")
}
