package mockserver

// This file completes the typed Expectation model so a Go client can round-trip
// every MockServer expectation feature the server can emit, without silently
// dropping fields. The JSON wire keys mirror the MockServer serialization model
// (mockserver-core ExpectationDTO and the referenced model classes / OpenAPI
// component schemas in mock-server-openapi-embedded-model.yaml).

// --- Chaos / fault-injection profile (Expectation.chaos) ---

// HttpChaosProfile is a declarative HTTP chaos / fault-injection profile applied
// to a mocked response (OpenAPI schema HttpChaosProfile; Expectation.chaos).
// Probability fields are 0.0–1.0. Numeric fields whose zero value is meaningful
// (probabilities, counts with a 0 minimum, seed) are pointers so an explicit
// zero round-trips rather than being dropped by omitempty.
type HttpChaosProfile struct {
	ErrorStatus               int      `json:"errorStatus,omitempty"`
	RetryAfter                string   `json:"retryAfter,omitempty"`
	ErrorProbability          *float64 `json:"errorProbability,omitempty"`
	DropConnectionProbability *float64 `json:"dropConnectionProbability,omitempty"`
	Latency                   *Delay   `json:"latency,omitempty"`
	Seed                      *int64   `json:"seed,omitempty"`
	SucceedFirst              *int     `json:"succeedFirst,omitempty"`
	FailRequestCount          *int     `json:"failRequestCount,omitempty"`
	OutageAfterMillis         *int64   `json:"outageAfterMillis,omitempty"`
	OutageDurationMillis      *int64   `json:"outageDurationMillis,omitempty"`
	TruncateBodyAtFraction    *float64 `json:"truncateBodyAtFraction,omitempty"`
	MalformedBody             *bool    `json:"malformedBody,omitempty"`
	SlowResponseChunkSize     int      `json:"slowResponseChunkSize,omitempty"`
	SlowResponseChunkDelay    *Delay   `json:"slowResponseChunkDelay,omitempty"`
	QuotaName                 string   `json:"quotaName,omitempty"`
	QuotaLimit                int      `json:"quotaLimit,omitempty"`
	QuotaWindowMillis         *int64   `json:"quotaWindowMillis,omitempty"`
	QuotaErrorStatus          int      `json:"quotaErrorStatus,omitempty"`
	DegradationRampMillis     *int64   `json:"degradationRampMillis,omitempty"`
	// GraphQL error-envelope injection: GraphqlErrors rewrites the response as a
	// GraphQL error envelope (HTTP 200, {"data":null,"errors":[...]});
	// GraphqlErrorMessage / GraphqlErrorCode populate errors[0].message and
	// errors[0].extensions.code; GraphqlNullifyData (server default true) nulls
	// the data field when true — the pointers preserve an explicit false.
	GraphqlErrors       *bool  `json:"graphqlErrors,omitempty"`
	GraphqlErrorMessage string `json:"graphqlErrorMessage,omitempty"`
	GraphqlErrorCode    string `json:"graphqlErrorCode,omitempty"`
	GraphqlNullifyData  *bool  `json:"graphqlNullifyData,omitempty"`
}

// --- Rate limit / quota (Expectation.rateLimit) ---

// RateLimitAlgorithm selects the RateLimit algorithm.
type RateLimitAlgorithm = string

const (
	// RateLimitFixedWindow is the default fixed-window algorithm (limit / windowMillis).
	RateLimitFixedWindow RateLimitAlgorithm = "fixed_window"
	// RateLimitTokenBucket is the token-bucket algorithm (burst / refillPerSecond).
	RateLimitTokenBucket RateLimitAlgorithm = "token_bucket"
)

// RateLimit is a declarative, protocol-agnostic rate limit / quota applied to an
// expectation (OpenAPI schema RateLimit; Expectation.rateLimit).
type RateLimit struct {
	Name            string   `json:"name,omitempty"`
	Algorithm       string   `json:"algorithm,omitempty"`
	Limit           int      `json:"limit,omitempty"`
	WindowMillis    int      `json:"windowMillis,omitempty"`
	Burst           int      `json:"burst,omitempty"`
	RefillPerSecond *float64 `json:"refillPerSecond,omitempty"`
	ErrorStatus     int      `json:"errorStatus,omitempty"`
	RetryAfter      string   `json:"retryAfter,omitempty"`
}

// --- Forward-with-fallback action (Expectation.httpForwardWithFallback) ---

// HttpForwardWithFallback forwards to HttpForward and falls back to
// FallbackResponse when the upstream returns one of FallbackOnStatusCodes or
// (when FallbackOnTimeout) times out (mockserver-core HttpForwardWithFallback).
type HttpForwardWithFallback struct {
	HttpForward           *HttpForward  `json:"httpForward,omitempty"`
	FallbackResponse      *HttpResponse `json:"fallbackResponse,omitempty"`
	FallbackOnStatusCodes []int         `json:"fallbackOnStatusCodes,omitempty"`
	FallbackOnTimeout     *bool         `json:"fallbackOnTimeout,omitempty"`
	Delay                 *Delay        `json:"delay,omitempty"`
	Primary               *bool         `json:"primary,omitempty"`
}

// --- Forward-and-validate action (Expectation.httpForwardValidateAction) ---

// ValidationMode selects how HttpForwardValidateAction reacts to a validation
// failure: STRICT (fail the exchange) or LOG_ONLY (record but pass through).
type ValidationMode = string

const (
	ValidationModeStrict  ValidationMode = "STRICT"
	ValidationModeLogOnly ValidationMode = "LOG_ONLY"
)

// HttpForwardValidateAction forwards the request upstream and validates the
// request and/or response against an OpenAPI spec
// (mockserver-core HttpForwardValidateAction; Expectation.httpForwardValidateAction).
type HttpForwardValidateAction struct {
	SpecUrlOrPayload string `json:"specUrlOrPayload,omitempty"`
	Host             string `json:"host,omitempty"`
	Port             int    `json:"port,omitempty"`
	Scheme           string `json:"scheme,omitempty"`
	ValidateRequest  *bool  `json:"validateRequest,omitempty"`
	ValidateResponse *bool  `json:"validateResponse,omitempty"`
	ValidationMode   string `json:"validationMode,omitempty"`
	Delay            *Delay `json:"delay,omitempty"`
	Primary          *bool  `json:"primary,omitempty"`
}

// --- gRPC bidirectional-streaming response (Expectation.grpcBidiResponse) ---

// GrpcBidiRule matches an inbound gRPC stream message (MatchJson) and replies
// with Responses (mockserver-core GrpcBidiRule).
type GrpcBidiRule struct {
	MatchJson string              `json:"matchJson,omitempty"`
	Responses []GrpcStreamMessage `json:"responses,omitempty"`
}

// GrpcBidiResponse is a gRPC bidirectional-streaming response action: it may emit
// unconditional Messages and/or per-inbound-message Rules
// (mockserver-core GrpcBidiResponse; Expectation.grpcBidiResponse).
type GrpcBidiResponse struct {
	StatusName      string              `json:"statusName,omitempty"`
	StatusMessage   string              `json:"statusMessage,omitempty"`
	Headers         map[string][]string `json:"headers,omitempty"`
	Messages        []GrpcStreamMessage `json:"messages,omitempty"`
	Rules           []GrpcBidiRule      `json:"rules,omitempty"`
	CloseConnection *bool               `json:"closeConnection,omitempty"`
	Delay           *Delay              `json:"delay,omitempty"`
	Primary         *bool               `json:"primary,omitempty"`
}

// --- Before / after actions (Expectation.beforeActions / afterActions) ---

// FailurePolicy controls how a before/after action failure is handled:
// FAIL_FAST (abort) or BEST_EFFORT (continue).
type FailurePolicy = string

const (
	FailurePolicyFailFast   FailurePolicy = "FAIL_FAST"
	FailurePolicyBestEffort FailurePolicy = "BEST_EFFORT"
)

// AfterAction is a side-effect action run before (beforeActions) or after
// (afterActions) the primary action — e.g. fire a webhook via HttpRequest or a
// callback (mockserver-core AfterAction). HttpRequestTemplate is a webhook whose
// request a template renders from the triggering request; escape request values
// and keep them out of its destination. Blocking runs it synchronously;
// Timeout bounds a blocking action; FailurePolicy selects error handling.
type AfterAction struct {
	HttpRequest         *HttpRequest        `json:"httpRequest,omitempty"`
	HttpRequestTemplate *HttpTemplate       `json:"httpRequestTemplate,omitempty"`
	HttpClassCallback   *HttpClassCallback  `json:"httpClassCallback,omitempty"`
	HttpObjectCallback  *HttpObjectCallback `json:"httpObjectCallback,omitempty"`
	Delay               *Delay              `json:"delay,omitempty"`
	Blocking            *bool               `json:"blocking,omitempty"`
	Timeout             *Delay              `json:"timeout,omitempty"`
	FailurePolicy       string              `json:"failurePolicy,omitempty"`
}

// --- Multi-step expectations (Expectation.steps) ---

// ExpectationStep is one step of a multi-step expectation: exactly one target
// action (response / error / forward / override / callback) plus scheduling
// controls (mockserver-core ExpectationStep). Responder marks the step whose
// action is returned to the caller.
type ExpectationStep struct {
	HttpRequest                  *HttpRequest                  `json:"httpRequest,omitempty"`
	HttpClassCallback            *HttpClassCallback            `json:"httpClassCallback,omitempty"`
	HttpObjectCallback           *HttpObjectCallback           `json:"httpObjectCallback,omitempty"`
	HttpForward                  *HttpForward                  `json:"httpForward,omitempty"`
	HttpOverrideForwardedRequest *HttpOverrideForwardedRequest `json:"httpOverrideForwardedRequest,omitempty"`
	HttpResponse                 *HttpResponse                 `json:"httpResponse,omitempty"`
	HttpError                    *HttpError                    `json:"httpError,omitempty"`
	Responder                    *bool                         `json:"responder,omitempty"`
	Delay                        *Delay                        `json:"delay,omitempty"`
	Blocking                     *bool                         `json:"blocking,omitempty"`
	Timeout                      *Delay                        `json:"timeout,omitempty"`
	FailurePolicy                string                        `json:"failurePolicy,omitempty"`
}

// --- Capture rules (Expectation.capture) ---

// CaptureSource selects where a CaptureRule reads its value from. Wire values
// match the server enum (mockserver-core CaptureRule.Source).
type CaptureSource = string

const (
	CaptureSourceJSONPath             CaptureSource = "jsonPath"
	CaptureSourceXPath                CaptureSource = "xpath"
	CaptureSourceHeader               CaptureSource = "header"
	CaptureSourceQueryStringParameter CaptureSource = "queryStringParameter"
	CaptureSourceCookie               CaptureSource = "cookie"
	CaptureSourcePathParameter        CaptureSource = "pathParameter"
)

// CaptureRule extracts a value from the matched request (via Source + Expression)
// and stores it into the scenario/state key Into for later template reuse
// (mockserver-core CaptureRule; Expectation.capture).
type CaptureRule struct {
	Source     string `json:"source,omitempty"`
	Expression string `json:"expression,omitempty"`
	Into       string `json:"into,omitempty"`
}

// NewCaptureRule builds a CaptureRule for the given source, expression and target key.
func NewCaptureRule(source CaptureSource, expression, into string) CaptureRule {
	return CaptureRule{Source: source, Expression: expression, Into: into}
}

// --- Deterministic retry/backoff recovery (HttpResponse.recoverAfter) ---

// RecoverAfter serves FailResponse for the first FailTimes matches, then serves
// the configured response — a deterministic retry/backoff recovery primitive
// (OpenAPI schema RecoverAfter; HttpResponse.recoverAfter). IdempotencyHeader,
// when set, keys an independent failure window per header value.
type RecoverAfter struct {
	FailTimes         *int          `json:"failTimes,omitempty"`
	FailResponse      *HttpResponse `json:"failResponse,omitempty"`
	IdempotencyHeader string        `json:"idempotencyHeader,omitempty"`
}

// --- Variable-delay distribution (Delay.distribution) ---

// DelayDistribution describes a variable response delay (OpenAPI Delay.distribution):
// UNIFORM requires Min and Max, LOG_NORMAL requires Median and P99, GAUSSIAN
// requires Mean and StdDev.
type DelayDistribution struct {
	Type   string `json:"type,omitempty"`
	Min    *int   `json:"min,omitempty"`
	Max    *int   `json:"max,omitempty"`
	Median *int   `json:"median,omitempty"`
	P99    *int   `json:"p99,omitempty"`
	Mean   *int   `json:"mean,omitempty"`
	StdDev *int   `json:"stdDev,omitempty"`
}

// --- Request / response modifiers (override-forward and template actions) ---

// HeadersModifier / QueryParametersModifier add, replace or remove multi-valued
// keys when overriding a forwarded request or shaping a response.
type HeadersModifier struct {
	Add     map[string][]string `json:"add,omitempty"`
	Replace map[string][]string `json:"replace,omitempty"`
	Remove  []string            `json:"remove,omitempty"`
}

// QueryParametersModifier is the query-string equivalent of HeadersModifier.
type QueryParametersModifier struct {
	Add     map[string][]string `json:"add,omitempty"`
	Replace map[string][]string `json:"replace,omitempty"`
	Remove  []string            `json:"remove,omitempty"`
}

// CookiesModifier adds, replaces or removes single-valued cookies.
type CookiesModifier struct {
	Add     map[string]string `json:"add,omitempty"`
	Replace map[string]string `json:"replace,omitempty"`
	Remove  []string          `json:"remove,omitempty"`
}

// PathModifier rewrites the forwarded request path via a regex substitution.
type PathModifier struct {
	Regex        string `json:"regex,omitempty"`
	Substitution string `json:"substitution,omitempty"`
}

// RequestModifier declaratively rewrites a forwarded request (path, query
// parameters, headers, cookies) — the requestModifier of
// HttpOverrideForwardedRequest.
type RequestModifier struct {
	Path                  *PathModifier            `json:"path,omitempty"`
	QueryStringParameters *QueryParametersModifier `json:"queryStringParameters,omitempty"`
	Headers               *HeadersModifier         `json:"headers,omitempty"`
	Cookies               *CookiesModifier         `json:"cookies,omitempty"`
}

// ResponseModifier declaratively rewrites a response's headers and cookies — the
// responseModifier of HttpOverrideForwardedRequest and HttpTemplate.
type ResponseModifier struct {
	Headers *HeadersModifier `json:"headers,omitempty"`
	Cookies *CookiesModifier `json:"cookies,omitempty"`
}
