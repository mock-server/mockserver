package mockserver

import (
	"bytes"
	"encoding/json"
	"errors"
	"fmt"
	"net/url"
	"strconv"
	"strings"
)

// Metadata is free-form information attached to an expectation (owner, ticket,
// tags, ...). Values are any JSON value; numbers decode as json.Number so they
// re-encode exactly as the server sent them.
type Metadata map[string]any

// UnmarshalJSON decodes metadata keeping numbers as json.Number.
func (m *Metadata) UnmarshalJSON(data []byte) error {
	dec := json.NewDecoder(bytes.NewReader(data))
	dec.UseNumber()
	var decoded map[string]any
	if err := dec.Decode(&decoded); err != nil {
		return err
	}
	*m = decoded
	return nil
}

// MetadataJSONNull selects a metadata value that is an explicit JSON null in
// ExpectationFilter.WithMetadata (a Go nil is rejected, never read as "any value").
var MetadataJSONNull = json.RawMessage("null")

// Query string parameter names understood by PUT /mockserver/retrieve and
// PUT /mockserver/clear.
const (
	metadataKeyParameter      = "metadataKey"
	metadataValueParameter    = "metadataValue"
	metadataJSONPathParameter = "metadataJsonPath"
	unmatchedParameter        = "unmatched"
	limitParameter            = "limit"
	offsetParameter           = "offset"
)

// ExpectationFilter selects active expectations by metadata, by never having
// matched a request (unmatched), or by page (offset/limit, retrieve only). The
// builder methods record the first invalid argument; it is returned by the
// client method the filter is passed to, before any request is sent.
type ExpectationFilter struct {
	metadataKey      string
	metadataValue    *string
	metadataJSONPath string
	unmatched        bool
	limit            *int
	offset           *int
	err              error
}

// NewExpectationFilter returns an empty filter.
func NewExpectationFilter() *ExpectationFilter {
	return &ExpectationFilter{}
}

func (f *ExpectationFilter) fail(err error) *ExpectationFilter {
	if f.err == nil {
		f.err = err
	}
	return f
}

// WithMetadataKey selects expectations whose metadata has key, with any value.
func (f *ExpectationFilter) WithMetadataKey(key string) *ExpectationFilter {
	if strings.TrimSpace(key) == "" {
		return f.fail(errors.New("mockserver: the metadata key must not be blank"))
	}
	f.metadataKey = key
	f.metadataValue = nil
	return f
}

// WithMetadata selects expectations whose metadata has key with a value equal
// to value. A string is sent as its text; any other value is sent as its
// compact JSON. A nil value is rejected: use WithMetadataKey for any value, or
// MetadataJSONNull for a JSON null.
func (f *ExpectationFilter) WithMetadata(key string, value any) *ExpectationFilter {
	if strings.TrimSpace(key) == "" {
		return f.fail(errors.New("mockserver: the metadata key must not be blank"))
	}
	encoded, err := encodeMetadataValue(value)
	if err != nil {
		return f.fail(err)
	}
	f.metadataKey = key
	f.metadataValue = &encoded
	return f
}

// WithMetadataJSONPath selects expectations whose metadata object the JSONPath
// selects at least one value from.
func (f *ExpectationFilter) WithMetadataJSONPath(jsonPath string) *ExpectationFilter {
	if strings.TrimSpace(jsonPath) == "" {
		return f.fail(errors.New("mockserver: the metadata JSONPath must not be blank"))
	}
	f.metadataJSONPath = jsonPath
	return f
}

// WithUnmatched selects only expectations that have not matched any request.
func (f *ExpectationFilter) WithUnmatched(unmatched bool) *ExpectationFilter {
	f.unmatched = unmatched
	return f
}

// WithLimit sets the maximum number of expectations returned (retrieve only).
func (f *ExpectationFilter) WithLimit(limit int) *ExpectationFilter {
	if limit < 0 {
		return f.fail(fmt.Errorf("mockserver: limit must be zero or more, got %d", limit))
	}
	f.limit = &limit
	return f
}

// WithOffset sets the number of expectations skipped (retrieve only).
func (f *ExpectationFilter) WithOffset(offset int) *ExpectationFilter {
	if offset < 0 {
		return f.fail(fmt.Errorf("mockserver: offset must be zero or more, got %d", offset))
	}
	f.offset = &offset
	return f
}

func (f *ExpectationFilter) hasSelection() bool {
	return f.metadataKey != "" || f.metadataJSONPath != "" || f.unmatched
}

func (f *ExpectationFilter) hasPaging() bool {
	return f.limit != nil || f.offset != nil
}

// applyTo adds the filter's parameters to params. url.Values.Encode escapes
// every name and value literally, so a leading '!' or '?' is never stripped.
func (f *ExpectationFilter) applyTo(params url.Values) {
	if f.metadataKey != "" {
		params.Set(metadataKeyParameter, f.metadataKey)
		if f.metadataValue != nil {
			params.Set(metadataValueParameter, *f.metadataValue)
		}
	}
	if f.metadataJSONPath != "" {
		params.Set(metadataJSONPathParameter, f.metadataJSONPath)
	}
	if f.unmatched {
		params.Set(unmatchedParameter, "true")
	}
	if f.limit != nil {
		params.Set(limitParameter, strconv.Itoa(*f.limit))
	}
	if f.offset != nil {
		params.Set(offsetParameter, strconv.Itoa(*f.offset))
	}
}

func encodeMetadataValue(value any) (string, error) {
	switch v := value.(type) {
	case nil:
		return "", errors.New("mockserver: the metadata value must not be nil; use WithMetadataKey(key) to select any value, or MetadataJSONNull to select a JSON null")
	case string:
		return v, nil
	case json.Number:
		return v.String(), nil
	case json.RawMessage:
		if !json.Valid(v) {
			return "", errors.New("mockserver: the metadata value is not valid JSON")
		}
		var buf bytes.Buffer
		if err := json.Compact(&buf, v); err != nil {
			return "", fmt.Errorf("mockserver: compact metadata value: %w", err)
		}
		return buf.String(), nil
	}
	var buf bytes.Buffer
	enc := json.NewEncoder(&buf)
	enc.SetEscapeHTML(false)
	if err := enc.Encode(value); err != nil {
		return "", fmt.Errorf("mockserver: marshal metadata value: %w", err)
	}
	encoded := strings.TrimSuffix(buf.String(), "\n")
	if encoded == "null" {
		// a typed nil (nil pointer, slice or map) must not silently become a JSON-null selection
		return "", errors.New("mockserver: the metadata value must not be nil; use WithMetadataKey(key) to select any value, or MetadataJSONNull to select a JSON null")
	}
	return encoded, nil
}

// requestMatcherBody serialises the optional request matcher exactly as
// Retrieve and Clear do.
func requestMatcherBody(rb *RequestBuilder, what string) ([]byte, error) {
	if rb == nil {
		return nil, nil
	}
	body, err := json.Marshal(rb.Build())
	if err != nil {
		return nil, fmt.Errorf("mockserver: marshal %s: %w", what, err)
	}
	return body, nil
}

func decodeExpectations(data []byte, what string) ([]Expectation, error) {
	if len(data) == 0 {
		return nil, nil
	}
	var result []Expectation
	if err := json.Unmarshal(data, &result); err != nil {
		return nil, fmt.Errorf("mockserver: unmarshal %s: %w", what, err)
	}
	return result, nil
}

func (c *Client) retrieveExpectationsWithParams(rb *RequestBuilder, retrieveType RetrieveType, params url.Values, what string) ([]Expectation, error) {
	body, err := requestMatcherBody(rb, "retrieve filter")
	if err != nil {
		return nil, err
	}
	params.Set("type", string(retrieveType))
	params.Set("format", string(FormatJSON))
	respBody, statusCode, err := c.doRequest("PUT", "/mockserver/retrieve", body, params)
	if err != nil {
		return nil, err
	}
	if statusCode >= 400 {
		return nil, fmt.Errorf("mockserver: retrieve failed (status %d): %s", statusCode, string(respBody))
	}
	return decodeExpectations(respBody, what)
}

// RetrieveActiveExpectationsWithFilter retrieves the active expectations that
// match both the optional request matcher and the filter (metadata, unmatched,
// offset/limit). Pages are taken in match order (highest priority first, then
// oldest first). A nil filter retrieves as RetrieveActiveExpectations does.
func (c *Client) RetrieveActiveExpectationsWithFilter(rb *RequestBuilder, filter *ExpectationFilter) ([]Expectation, error) {
	params := make(url.Values)
	if filter != nil {
		if filter.err != nil {
			return nil, filter.err
		}
		filter.applyTo(params)
	}
	return c.retrieveExpectationsWithParams(rb, RetrieveActiveExpectations, params, "active expectations")
}

// RetrieveActiveExpectationsByMetadata retrieves the active expectations whose
// metadata has key with a value equal to value (see ExpectationFilter.WithMetadata).
func (c *Client) RetrieveActiveExpectationsByMetadata(key string, value any) ([]Expectation, error) {
	return c.RetrieveActiveExpectationsWithFilter(nil, NewExpectationFilter().WithMetadata(key, value))
}

// RetrieveActiveExpectationsByMetadataKey retrieves the active expectations
// whose metadata has key, with any value.
func (c *Client) RetrieveActiveExpectationsByMetadataKey(key string) ([]Expectation, error) {
	return c.RetrieveActiveExpectationsWithFilter(nil, NewExpectationFilter().WithMetadataKey(key))
}

// RetrieveUnmatchedExpectations retrieves the active expectations that have not
// matched any request.
func (c *Client) RetrieveUnmatchedExpectations() ([]Expectation, error) {
	return c.RetrieveActiveExpectationsWithFilter(nil, NewExpectationFilter().WithUnmatched(true))
}

// RetrieveRecordedExpectationsPage retrieves one page of the recorded
// expectations matching the optional request matcher, in the order the
// requests were received. offset and limit must be zero or more.
func (c *Client) RetrieveRecordedExpectationsPage(rb *RequestBuilder, offset, limit int) ([]Expectation, error) {
	filter := NewExpectationFilter().WithOffset(offset).WithLimit(limit)
	if filter.err != nil {
		return nil, filter.err
	}
	params := make(url.Values)
	filter.applyTo(params)
	return c.retrieveExpectationsWithParams(rb, RetrieveRecordedExpectations, params, "recorded expectations")
}

// ClearWithFilter clears only the expectations that match both the optional
// request matcher and the filter; the event log is left untouched. The filter
// must select something (metadata or unmatched) and must not page: a filter
// that selects nothing is rejected rather than sent as a clear of everything.
func (c *Client) ClearWithFilter(rb *RequestBuilder, filter *ExpectationFilter) error {
	if filter == nil {
		return errors.New("mockserver: clear with a filter requires a metadata or unmatched selection; use Clear to clear without one")
	}
	if filter.err != nil {
		return filter.err
	}
	if !filter.hasSelection() {
		return errors.New("mockserver: clear with a filter requires a metadata or unmatched selection; use Clear to clear without one")
	}
	if filter.hasPaging() {
		return errors.New("mockserver: limit and offset are not supported for clear")
	}
	body, err := requestMatcherBody(rb, "clear request")
	if err != nil {
		return err
	}
	params := make(url.Values)
	params.Set("type", string(ClearExpectations))
	filter.applyTo(params)
	_, statusCode, err := c.doRequest("PUT", "/mockserver/clear", body, params)
	if err != nil {
		return err
	}
	if statusCode >= 400 {
		return fmt.Errorf("mockserver: clear failed (status %d)", statusCode)
	}
	return nil
}

// ClearByMetadata clears the expectations whose metadata has key with a value
// equal to value (see ExpectationFilter.WithMetadata).
func (c *Client) ClearByMetadata(key string, value any) error {
	return c.ClearWithFilter(nil, NewExpectationFilter().WithMetadata(key, value))
}

// ClearByMetadataKey clears the expectations whose metadata has key, with any value.
func (c *Client) ClearByMetadataKey(key string) error {
	return c.ClearWithFilter(nil, NewExpectationFilter().WithMetadataKey(key))
}

// ClearUnmatchedExpectations clears the expectations that have not matched any request.
func (c *Client) ClearUnmatchedExpectations() error {
	return c.ClearWithFilter(nil, NewExpectationFilter().WithUnmatched(true))
}
