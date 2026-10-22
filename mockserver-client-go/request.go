package mockserver

import (
	"encoding/json"
	"errors"
	"strings"
)

// HttpRequest represents an HTTP request matcher for MockServer.
type HttpRequest struct {
	Method             string              `json:"method,omitempty"`
	Path               string              `json:"path,omitempty"`
	QueryStringParams  map[string][]string `json:"queryStringParameters,omitempty"`
	Headers            map[string][]string `json:"headers,omitempty"`
	Cookies            map[string]string   `json:"cookies,omitempty"`
	Body               interface{}         `json:"body,omitempty"`
	Secure             *bool               `json:"secure,omitempty"`
	KeepAlive          *bool               `json:"keepAlive,omitempty"`
	SocketAddress      *SocketAddress      `json:"socketAddress,omitempty"`
	PathParametersList map[string][]string `json:"pathParameters,omitempty"`
	JWT                *Jwt                `json:"jwt,omitempty"`
	// HeaderMatchers, QueryStringParameterMatchers, CookieMatchers and
	// PathParameterMatchers express matcher values that the plain string maps
	// above cannot. A value whose first character is '!' or '?' is a marker to
	// the server and is stripped when read, so `Headers["X"] = []string{"!foo"}`
	// asks for "X is anything but foo", not "X is !foo". A MatcherValue keeps
	// the text and the flags apart and escapes to the object form only when the
	// plain form would be misread -- see MatcherValue. They also carry date-time
	// matchers (Before, After, EqualToDateTime), which have no plain form.
	//
	// When one of these is non-nil it REPLACES the corresponding plain map on
	// the wire. They are additive so existing code using the plain maps keeps
	// compiling and keeps its current meaning.
	HeaderMatchers               map[string][]MatcherValue `json:"-"`
	QueryStringParameterMatchers map[string][]MatcherValue `json:"-"`
	CookieMatchers               map[string]MatcherValue   `json:"-"`
	PathParameterMatchers        map[string][]MatcherValue `json:"-"`

	// Not negates the whole request matcher (match everything except this).
	Not *bool `json:"not,omitempty"`
	// Protocol constrains the matcher to a wire protocol: "HTTP_1_1",
	// "HTTP_2" or "HTTP_3".
	Protocol string `json:"protocol,omitempty"`
	// RespondBeforeBody makes MockServer respond before consuming the request
	// body (requires no body matcher and a RESPONSE or ERROR action).
	RespondBeforeBody *bool `json:"respondBeforeBody,omitempty"`
	// ClientIP matches the client address the request came from: an IP
	// address or CIDR range, IPv4 or IPv6 (e.g. "10.0.0.0/8"); prefix with "!"
	// to match any other address. It is the TCP peer, not X-Forwarded-For.
	ClientIP string `json:"clientIp,omitempty"`

	// --- DNS request definition (RequestDefinition: DnsRequestDefinition) ---
	// When set, this expectation matches a DNS query rather than an HTTP
	// request. DnsName is the queried name; DnsType (e.g. "A", "AAAA", "MX")
	// and DnsClass (e.g. "IN") are optional constraints.
	DnsName  string `json:"dnsName,omitempty"`
	DnsType  string `json:"dnsType,omitempty"`
	DnsClass string `json:"dnsClass,omitempty"`

	// --- OpenAPI request definition (RequestDefinition: OpenAPIDefinition) ---
	// When set, the request is matched against an OpenAPI/Swagger operation.
	SpecUrlOrPayload  interface{} `json:"specUrlOrPayload,omitempty"`
	OperationId       string      `json:"operationId,omitempty"`
	ContextPathPrefix string      `json:"contextPathPrefix,omitempty"`

	// --- Conditional request definition (ConditionalRequestDefinition) ---
	// When If is set, the request is matched with if/then/else logic: if If
	// matches, Then must match too; otherwise Else must (when Else is nil the
	// matcher matches whenever If does not). Not inverts the result. Each
	// branch is an HTTP, OpenAPI or nested conditional matcher. Build one with
	// ConditionalRequest or RequestBuilder.If/Then/Else.
	If   *HttpRequest `json:"if,omitempty"`
	Then *HttpRequest `json:"then,omitempty"`
	Else *HttpRequest `json:"else,omitempty"`

	// --- AnyOf request definition (AnyOfRequestDefinition) ---
	// When AnyOf is set, the request matches when any branch matches it,
	// trying them in order; Not inverts the result, so it then matches when no
	// branch does. Each branch is an HTTP, OpenAPI, conditional or nested anyOf
	// matcher. Build one with AnyOfRequest or RequestBuilder.AnyOf. A non-nil
	// empty AnyOf is still sent, as "anyOf": [], so the server rejects it
	// rather than reading the request as {}, which matches every request.
	AnyOf []*HttpRequest `json:"anyOf,omitempty"`
}

// ConditionalRequest builds a conditional (if/then/else) request matcher.
// thenReq and elseReq may be nil.
func ConditionalRequest(ifReq, thenReq, elseReq *HttpRequest) *HttpRequest {
	return &HttpRequest{If: ifReq, Then: thenReq, Else: elseReq}
}

// AnyOfRequest builds a logical OR request matcher: it matches a request when
// any of branches matches it.
func AnyOfRequest(branches ...*HttpRequest) *HttpRequest {
	return &HttpRequest{AnyOf: append([]*HttpRequest{}, branches...)}
}

// SocketAddress represents a socket address constraint.
type SocketAddress struct {
	Host   string `json:"host,omitempty"`
	Port   int    `json:"port,omitempty"`
	Scheme string `json:"scheme,omitempty"`
}

// RequestBuilder provides a fluent API for building HttpRequest matchers.
type RequestBuilder struct {
	request HttpRequest
}

// Request creates a new RequestBuilder.
func Request() *RequestBuilder {
	return &RequestBuilder{}
}

// Method sets the HTTP method matcher.
func (b *RequestBuilder) Method(method string) *RequestBuilder {
	b.request.Method = method
	return b
}

// Path sets the path matcher.
func (b *RequestBuilder) Path(path string) *RequestBuilder {
	b.request.Path = path
	return b
}

// QueryStringParameter adds a query string parameter matcher.
func (b *RequestBuilder) QueryStringParameter(name string, values ...string) *RequestBuilder {
	if b.request.QueryStringParams == nil {
		b.request.QueryStringParams = make(map[string][]string)
	}
	b.request.QueryStringParams[name] = values
	return b
}

// Header adds a header matcher.
func (b *RequestBuilder) Header(name string, values ...string) *RequestBuilder {
	if b.request.Headers == nil {
		b.request.Headers = make(map[string][]string)
	}
	b.request.Headers[name] = values
	return b
}

// Cookie adds a cookie matcher.
func (b *RequestBuilder) Cookie(name, value string) *RequestBuilder {
	if b.request.Cookies == nil {
		b.request.Cookies = make(map[string]string)
	}
	b.request.Cookies[name] = value
	return b
}

// Body sets the request body matcher as a plain string.
func (b *RequestBuilder) Body(body string) *RequestBuilder {
	b.request.Body = body
	return b
}

// JSONBody sets the request body matcher as a JSON body type.
func (b *RequestBuilder) JSONBody(json string) *RequestBuilder {
	b.request.Body = &TypedBody{Type: "JSON", JSON: json}
	return b
}

// AllOfBody sets the request body matcher to a composite ALL_OF matcher that
// matches only when every supplied body matcher matches. Each element may be any
// body matcher value (a *TypedBody, a plain string, or a nested *AllOfBody).
func (b *RequestBuilder) AllOfBody(bodies ...interface{}) *RequestBuilder {
	b.request.Body = AllOf(bodies...)
	return b
}

// Jwt sets a JWT (JSON Web Token) request matcher. MockServer decodes the bearer
// token and matches the supplied claims/issuer/audience/algorithm.
func (b *RequestBuilder) Jwt(jwt *Jwt) *RequestBuilder {
	b.request.JWT = jwt
	return b
}

// PathParameter adds a path parameter matcher.
func (b *RequestBuilder) PathParameter(name string, values ...string) *RequestBuilder {
	if b.request.PathParametersList == nil {
		b.request.PathParametersList = make(map[string][]string)
	}
	b.request.PathParametersList[name] = values
	return b
}

// Secure sets whether the request must be secure (HTTPS).
func (b *RequestBuilder) Secure(secure bool) *RequestBuilder {
	b.request.Secure = &secure
	return b
}

// KeepAlive sets whether the request must be keep-alive.
func (b *RequestBuilder) KeepAlive(keepAlive bool) *RequestBuilder {
	b.request.KeepAlive = &keepAlive
	return b
}

// ClientIP matches only requests from this client IP address or CIDR range
// (e.g. "10.0.0.0/8"); prefix with "!" to match any other address.
func (b *RequestBuilder) ClientIP(clientIP string) *RequestBuilder {
	b.request.ClientIP = clientIP
	return b
}

// If makes this a conditional (if/then/else) matcher whose guard is req. See
// HttpRequest.If.
func (b *RequestBuilder) If(req *RequestBuilder) *RequestBuilder {
	b.request.If = req.BuildPtr()
	return b
}

// Then sets the branch that must match when the If guard matches.
func (b *RequestBuilder) Then(req *RequestBuilder) *RequestBuilder {
	b.request.Then = req.BuildPtr()
	return b
}

// Else sets the branch that must match when the If guard does not.
func (b *RequestBuilder) Else(req *RequestBuilder) *RequestBuilder {
	b.request.Else = req.BuildPtr()
	return b
}

// AnyOf makes this a logical OR matcher of the given branches. See
// HttpRequest.AnyOf.
func (b *RequestBuilder) AnyOf(branches ...*RequestBuilder) *RequestBuilder {
	if b.request.AnyOf == nil {
		b.request.AnyOf = []*HttpRequest{}
	}
	for _, branch := range branches {
		b.request.AnyOf = append(b.request.AnyOf, branch.BuildPtr())
	}
	return b
}

// Build returns the constructed HttpRequest.
func (b *RequestBuilder) Build() HttpRequest {
	return b.request
}

// BuildPtr returns a pointer to the constructed HttpRequest. It is a convenience
// for object forward-callback handlers (see Client.MockWithForwardCallback) which
// return a *HttpRequest.
func (b *RequestBuilder) BuildPtr() *HttpRequest {
	req := b.request
	return &req
}

// TypedBody represents a typed body matcher. A single struct carries every
// MockServer body-matcher variant (selected by Type); only the fields relevant
// to that Type are populated. Type is one of: STRING, JSON, JSON_SCHEMA,
// JSON_PATH, XML, XML_SCHEMA, XPATH, REGEX, PARAMETERS, BINARY, MULTIPART,
// GRAPHQL, JSON_RPC or WASM (see the MockServer Body schema). Not negates the
// matcher and Optional makes it optional.
type TypedBody struct {
	Type     string `json:"type"`
	Not      *bool  `json:"not,omitempty"`
	Optional *bool  `json:"optional,omitempty"`

	// STRING
	String    string `json:"string,omitempty"`
	SubString *bool  `json:"subString,omitempty"`

	// JSON
	JSON                  string `json:"json,omitempty"`
	MatchType             string `json:"matchType,omitempty"`
	MatchNumbersAsStrings *bool  `json:"matchNumbersAsStrings,omitempty"`

	// JSON_SCHEMA — the schema may be a JSON string or an embedded object.
	JSONSchema interface{} `json:"jsonSchema,omitempty"`

	// JSON_PATH
	JSONPath string `json:"jsonPath,omitempty"`

	// XML
	XML string `json:"xml,omitempty"`

	// XML_SCHEMA
	XMLSchema string `json:"xmlSchema,omitempty"`

	// XPATH
	XPath string `json:"xpath,omitempty"`

	// REGEX
	Regex string `json:"regex,omitempty"`

	// PARAMETERS
	Parameters map[string][]string `json:"parameters,omitempty"`

	// BINARY
	Base64Bytes string `json:"base64Bytes,omitempty"`

	// MULTIPART
	Fields           map[string][]string `json:"fields,omitempty"`
	Filenames        map[string][]string `json:"filenames,omitempty"`
	PartContentTypes map[string][]string `json:"partContentTypes,omitempty"`

	// GRAPHQL. Note: the GraphQL selection-set field list also serializes to the
	// JSON key "fields", which the MULTIPART Fields matcher above already owns.
	// A body is only ever one Type, but a single Go struct cannot expose the same
	// JSON key twice, so the (advanced, rarely client-set) GraphQL field list is
	// not a typed field here — it still round-trips losslessly via the generic
	// HttpRequest.Body interface. SelectionSetMatchType and Schema do not collide.
	Query                 string `json:"query,omitempty"`
	OperationName         string `json:"operationName,omitempty"`
	VariablesSchema       string `json:"variablesSchema,omitempty"`
	SelectionSetMatchType string `json:"selectionSetMatchType,omitempty"`
	Schema                string `json:"schema,omitempty"`

	// JSON_RPC
	Method       string `json:"method,omitempty"`
	ParamsSchema string `json:"paramsSchema,omitempty"`

	// WASM
	ModuleName string `json:"moduleName,omitempty"`

	// ContentType applies to the STRING, JSON, XML and BINARY variants.
	ContentType string `json:"contentType,omitempty"`
}

// StringBody builds a STRING body matcher (exact-string match) for the given value.
func StringBody(value string) *TypedBody {
	return &TypedBody{Type: "STRING", String: value}
}

// SubStringBody builds a STRING body matcher that matches when the request body
// contains the given value as a substring.
func SubStringBody(value string) *TypedBody {
	subString := true
	return &TypedBody{Type: "STRING", String: value, SubString: &subString}
}

// JSONMatchBody builds a JSON body matcher. matchType selects the comparison
// strictness: "STRICT" or "ONLY_MATCHING_FIELDS" (empty defaults to the server
// default). Use RequestBuilder.JSONBody for the simple case.
func JSONMatchBody(json, matchType string) *TypedBody {
	b := &TypedBody{Type: "JSON", JSON: json}
	if matchType != "" {
		b.MatchType = matchType
	}
	return b
}

// JSONSchemaBody builds a JSON_SCHEMA body matcher validating the request body
// against the given JSON schema (a schema string or a map/struct).
func JSONSchemaBody(schema interface{}) *TypedBody {
	return &TypedBody{Type: "JSON_SCHEMA", JSONSchema: schema}
}

// JSONPathBody builds a JSON_PATH body matcher that matches when the given
// JSONPath expression selects a value in the request body.
func JSONPathBody(jsonPath string) *TypedBody {
	return &TypedBody{Type: "JSON_PATH", JSONPath: jsonPath}
}

// XMLBody builds an XML body matcher (canonical XML comparison).
func XMLBody(xml string) *TypedBody {
	return &TypedBody{Type: "XML", XML: xml}
}

// XMLSchemaBody builds an XML_SCHEMA (XSD) body matcher.
func XMLSchemaBody(xmlSchema string) *TypedBody {
	return &TypedBody{Type: "XML_SCHEMA", XMLSchema: xmlSchema}
}

// XPathBody builds an XPATH body matcher that matches when the given XPath
// expression selects a node in the request body.
func XPathBody(xpath string) *TypedBody {
	return &TypedBody{Type: "XPATH", XPath: xpath}
}

// RegexBody builds a REGEX body matcher that matches when the request body
// matches the given regular expression.
func RegexBody(regex string) *TypedBody {
	return &TypedBody{Type: "REGEX", Regex: regex}
}

// ParameterBody builds a PARAMETERS body matcher over form-url-encoded body
// parameters.
func ParameterBody(parameters map[string][]string) *TypedBody {
	return &TypedBody{Type: "PARAMETERS", Parameters: parameters}
}

// BinaryBody builds a BINARY body matcher for the given base64-encoded bytes.
func BinaryBody(base64Bytes string) *TypedBody {
	return &TypedBody{Type: "BINARY", Base64Bytes: base64Bytes}
}

// MultipartBody builds a MULTIPART (form-data) body matcher. Any of fields,
// filenames or partContentTypes may be nil.
func MultipartBody(fields, filenames, partContentTypes map[string][]string) *TypedBody {
	return &TypedBody{Type: "MULTIPART", Fields: fields, Filenames: filenames, PartContentTypes: partContentTypes}
}

// GraphQLBody builds a GRAPHQL body matcher for the given query (operationName
// optional).
func GraphQLBody(query, operationName string) *TypedBody {
	return &TypedBody{Type: "GRAPHQL", Query: query, OperationName: operationName}
}

// WasmBody builds a WASM body matcher delegating to the named WASM module.
func WasmBody(moduleName string) *TypedBody {
	return &TypedBody{Type: "WASM", ModuleName: moduleName}
}

// AllOfBody is a composite body matcher (wire type "ALL_OF") that matches only
// when every nested body matcher matches.
type AllOfBody struct {
	Type      string        `json:"type"`
	BodyAllOf []interface{} `json:"bodyAllOf"`
}

// AllOf builds a composite body matcher that matches only when every supplied
// body matcher matches. Each element may be any body matcher value (a *TypedBody,
// a plain string, or a nested *AllOfBody).
func AllOf(bodies ...interface{}) *AllOfBody {
	return &AllOfBody{Type: "ALL_OF", BodyAllOf: bodies}
}

// Jwt is a JWT (JSON Web Token) request matcher. MockServer decodes the bearer
// token and matches the supplied claims, issuer, audience, and algorithm. Claim
// and string values follow MockServer's string-matcher semantics: an exact
// value, a regular expression, or a "!"-prefixed negated match. Header and
// Scheme select where the token is read from (defaulting to the Authorization
// header with the "Bearer" scheme).
type Jwt struct {
	Claims    map[string]string `json:"claims,omitempty"`
	Issuer    string            `json:"issuer,omitempty"`
	Audience  string            `json:"audience,omitempty"`
	Algorithm string            `json:"algorithm,omitempty"`
	Header    string            `json:"header,omitempty"`
	Scheme    string            `json:"scheme,omitempty"`
}

// NewJwt creates an empty JWT matcher to be populated via its fluent methods.
func NewJwt() *Jwt { return &Jwt{} }

// Claim adds a claim matcher. The value matches exactly, as a regular
// expression, or — when prefixed with "!" — as a negated match.
func (j *Jwt) Claim(name, value string) *Jwt {
	if j.Claims == nil {
		j.Claims = make(map[string]string)
	}
	j.Claims[name] = value
	return j
}

// WithIssuer sets the "iss" (issuer) claim matcher.
func (j *Jwt) WithIssuer(issuer string) *Jwt {
	j.Issuer = issuer
	return j
}

// WithAudience sets the "aud" (audience) claim matcher.
func (j *Jwt) WithAudience(audience string) *Jwt {
	j.Audience = audience
	return j
}

// WithAlgorithm sets the JWT signing algorithm matcher (e.g. "HS256", "RS256").
func (j *Jwt) WithAlgorithm(algorithm string) *Jwt {
	j.Algorithm = algorithm
	return j
}

// WithHeader sets the request header the token is read from (defaults to
// "Authorization").
func (j *Jwt) WithHeader(header string) *Jwt {
	j.Header = header
	return j
}

// WithScheme sets the authorization scheme prefix stripped from the header value
// before decoding the token (defaults to "Bearer").
func (j *Jwt) WithScheme(scheme string) *Jwt {
	j.Scheme = scheme
	return j
}

// MarshalJSON serialises the request, letting the *Matchers maps stand in for
// their plain-string counterparts when set. Without this the matcher maps would
// be invisible on the wire (they carry `json:"-"`), and a value beginning with a
// marker character could not be expressed at all.
func (r HttpRequest) MarshalJSON() ([]byte, error) {
	// a distinct type with no methods, so encoding/json does not recurse
	type plain HttpRequest
	aux := struct {
		plain
		Headers           interface{} `json:"headers,omitempty"`
		QueryStringParams interface{} `json:"queryStringParameters,omitempty"`
		Cookies           interface{} `json:"cookies,omitempty"`
		PathParameters    interface{} `json:"pathParameters,omitempty"`
		AnyOf             interface{} `json:"anyOf,omitempty"`
	}{plain: plain(r)}

	// the embedded plain still carries the original maps; blank them there and
	// choose the effective value for each field explicitly
	aux.plain.Headers = nil
	aux.plain.QueryStringParams = nil
	aux.plain.Cookies = nil
	aux.plain.PathParametersList = nil
	aux.plain.AnyOf = nil
	// nil, not len: an empty but set AnyOf must reach the server, which rejects it
	if r.AnyOf != nil {
		aux.AnyOf = r.AnyOf
	}

	// len, not nil: aux.Headers is an interface{}, and `omitempty` drops only a
	// NIL interface -- one holding an empty map still emits `{}`, which the
	// plain fields never did.
	if len(r.HeaderMatchers) > 0 {
		aux.Headers = r.HeaderMatchers
	} else if len(r.Headers) > 0 {
		aux.Headers = r.Headers
	}
	// len, not nil: aux.QueryStringParams is an interface{}, and `omitempty` drops only a
	// NIL interface -- one holding an empty map still emits `{}`, which the
	// plain fields never did.
	if len(r.QueryStringParameterMatchers) > 0 {
		aux.QueryStringParams = r.QueryStringParameterMatchers
	} else if len(r.QueryStringParams) > 0 {
		aux.QueryStringParams = r.QueryStringParams
	}
	// len, not nil: aux.Cookies is an interface{}, and `omitempty` drops only a
	// NIL interface -- one holding an empty map still emits `{}`, which the
	// plain fields never did.
	if len(r.CookieMatchers) > 0 {
		aux.Cookies = r.CookieMatchers
	} else if len(r.Cookies) > 0 {
		aux.Cookies = r.Cookies
	}
	// len, not nil: aux.PathParameters is an interface{}, and `omitempty` drops only a
	// NIL interface -- one holding an empty map still emits `{}`, which the
	// plain fields never did.
	if len(r.PathParameterMatchers) > 0 {
		aux.PathParameters = r.PathParameterMatchers
	} else if len(r.PathParametersList) > 0 {
		aux.PathParameters = r.PathParametersList
	}

	return json.Marshal(aux)
}

// HeaderMatcher adds a header matcher whose values are taken verbatim, so a
// value starting with '!' or '?' means itself rather than being read as a
// negation or optionality marker.
func (b *RequestBuilder) HeaderMatcher(name string, values ...MatcherValue) *RequestBuilder {
	if b.request.HeaderMatchers == nil {
		b.request.HeaderMatchers = make(map[string][]MatcherValue)
		// carry over anything already set through the plain map so the two APIs
		// can be mixed without the plain entries disappearing
		for k, v := range b.request.Headers {
			b.request.HeaderMatchers[k] = literals(v)
		}
	}
	b.request.HeaderMatchers[name] = values
	return b
}

// QueryStringParameterMatcher adds a query-string parameter matcher whose
// values are taken verbatim. See HeaderMatcher.
func (b *RequestBuilder) QueryStringParameterMatcher(name string, values ...MatcherValue) *RequestBuilder {
	if b.request.QueryStringParameterMatchers == nil {
		b.request.QueryStringParameterMatchers = make(map[string][]MatcherValue)
		for k, v := range b.request.QueryStringParams {
			b.request.QueryStringParameterMatchers[k] = literals(v)
		}
	}
	b.request.QueryStringParameterMatchers[name] = values
	return b
}

// CookieMatcher adds a cookie matcher whose value is taken verbatim. See
// HeaderMatcher.
func (b *RequestBuilder) CookieMatcher(name string, value MatcherValue) *RequestBuilder {
	if b.request.CookieMatchers == nil {
		b.request.CookieMatchers = make(map[string]MatcherValue)
		for k, v := range b.request.Cookies {
			b.request.CookieMatchers[k] = parsePlain(v)
		}
	}
	b.request.CookieMatchers[name] = value
	return b
}

// PathParameterMatcher adds a path parameter matcher whose values are taken
// verbatim. See HeaderMatcher.
func (b *RequestBuilder) PathParameterMatcher(name string, values ...MatcherValue) *RequestBuilder {
	if b.request.PathParameterMatchers == nil {
		b.request.PathParameterMatchers = make(map[string][]MatcherValue)
		for k, v := range b.request.PathParametersList {
			b.request.PathParameterMatchers[k] = literals(v)
		}
	}
	b.request.PathParameterMatchers[name] = values
	return b
}

// UnmarshalJSON is the read half of MarshalJSON.
//
// Without it the escape is write-only: the matcher maps carry `json:"-"`, so a
// response containing the object form (`{"not":false,"value":"!foo"}`) fails to
// decode into the plain `map[string][]string` fields with "cannot unmarshal
// object into ... of type string". That is reachable from
// RetrieveActiveExpectations and RetrieveRecordedRequests, and it is provoked by
// this client's own output -- and by the server's, since a MockServer carrying
// the same escape echoes the object form back.
//
// The four fields are decoded through MatcherValue, which accepts both wire
// forms. A field whose values all round-trip through the plain form is mirrored
// into the plain map and the matcher map is left nil, so plain-form traffic
// decodes exactly as it always did and re-marshals byte-identically. Only a
// field that genuinely needs the object form populates the matcher map.
func (r *HttpRequest) UnmarshalJSON(data []byte) error {
	// a distinct type with no methods, so encoding/json does not recurse
	type plain HttpRequest
	aux := struct {
		*plain
		Headers           json.RawMessage `json:"headers"`
		QueryStringParams json.RawMessage `json:"queryStringParameters"`
		Cookies           json.RawMessage `json:"cookies"`
		PathParameters    json.RawMessage `json:"pathParameters"`
	}{plain: (*plain)(r)}

	// encoding/json reports the FIRST type mismatch but keeps decoding every other
	// field, and callers rely on that. This model still types `path` and `method`
	// as plain strings, so an expectation using their object form raises an
	// UnmarshalTypeError even though the rest of the request decodes fine --
	// and because encoding/json aborts a PARENT decode when a nested
	// UnmarshalJSON returns, propagating it would drop every field decoded after
	// httpRequest, including the enclosing Expectation's httpResponse.
	//
	// So exactly those two fields are tolerated, and NOTHING else: a mismatch on
	// any other field (`secure`, `keepAlive`, ...) still propagates, because
	// swallowing it would leave that field silently at its zero value and lose an
	// error the stdlib used to surface. The two tolerated fields' gap is recorded
	// as `httpRequest.path` and `httpRequest.method` in the round-trip fidelity
	// harness's known-gaps.json, which is where it stays visible.
	var typeErr *json.UnmarshalTypeError
	if err := json.Unmarshal(data, &aux); err != nil {
		if !errors.As(err, &typeErr) || !isToleratedTypeMismatch(typeErr) {
			return err
		}
	}

	if err := decodeMultiValueMatchers(aux.Headers, &r.Headers, &r.HeaderMatchers); err != nil {
		return err
	}
	if err := decodeMultiValueMatchers(aux.QueryStringParams, &r.QueryStringParams, &r.QueryStringParameterMatchers); err != nil {
		return err
	}
	if err := decodeMultiValueMatchers(aux.PathParameters, &r.PathParametersList, &r.PathParameterMatchers); err != nil {
		return err
	}
	// typeErr is a tolerated path/method mismatch (see above) and is deliberately
	// not returned; anything else already returned before reaching here.
	_ = typeErr
	return decodeSingleValueMatchers(aux.Cookies, &r.Cookies, &r.CookieMatchers)
}

// decodeMultiValueMatchers decodes a keyToMultiValue field, mirroring it into
// the plain map when every value survives the plain form.
func decodeMultiValueMatchers(raw json.RawMessage, plainOut *map[string][]string, matcherOut *map[string][]MatcherValue) error {
	if len(raw) == 0 || string(raw) == "null" {
		return nil
	}
	decoded := map[string][]MatcherValue{}
	if err := json.Unmarshal(raw, &decoded); err != nil {
		return err
	}
	needsMatchers := false
	for _, values := range decoded {
		for _, v := range values {
			if v.ambiguous() {
				needsMatchers = true
			}
		}
	}
	if needsMatchers {
		*matcherOut = decoded
		return nil
	}
	mirrored := make(map[string][]string, len(decoded))
	for key, values := range decoded {
		plainValues := make([]string, 0, len(values))
		for _, v := range values {
			plainValues = append(plainValues, v.serialise())
		}
		mirrored[key] = plainValues
	}
	*plainOut = mirrored
	return nil
}

// decodeSingleValueMatchers is decodeMultiValueMatchers for a keyToValue field
// (cookies), which carries one value per name rather than a list.
func decodeSingleValueMatchers(raw json.RawMessage, plainOut *map[string]string, matcherOut *map[string]MatcherValue) error {
	if len(raw) == 0 || string(raw) == "null" {
		return nil
	}
	decoded := map[string]MatcherValue{}
	if err := json.Unmarshal(raw, &decoded); err != nil {
		return err
	}
	for _, v := range decoded {
		if v.ambiguous() {
			*matcherOut = decoded
			return nil
		}
	}
	mirrored := make(map[string]string, len(decoded))
	for key, v := range decoded {
		mirrored[key] = v.serialise()
	}
	*plainOut = mirrored
	return nil
}

// isToleratedTypeMismatch reports whether a type mismatch is on `path` or
// `method` -- the two fields whose object form this model cannot represent, and
// the only two whose error must be swallowed so the parent decode can complete.
//
// The name is compared on its LAST segment because UnmarshalTypeError.Field is
// qualified by the struct it was reached through: decoding via the embedded
// `plain` alias reports "plain.path", not "path". Matching the whole string
// against "path" therefore never fires, which would silently restore the
// object-wide data loss this tolerance exists to prevent.
func isToleratedTypeMismatch(typeErr *json.UnmarshalTypeError) bool {
	field := typeErr.Field
	if i := strings.LastIndex(field, "."); i >= 0 {
		field = field[i+1:]
	}
	return field == "path" || field == "method"
}
