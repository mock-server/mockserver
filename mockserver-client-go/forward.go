package mockserver

// HttpForward represents an HTTP forward action for MockServer.
type HttpForward struct {
	Host   string `json:"host"`
	Port   int    `json:"port,omitempty"`
	Scheme string `json:"scheme,omitempty"`
	Delay  *Delay `json:"delay,omitempty"`
	// Primary marks this as the primary action when multiple are present.
	Primary *bool `json:"primary,omitempty"`
}

// HttpOverrideForwardedRequest forwards the matched request to an overridden
// target while shaping the caller's response. The server serialization is a
// oneOf of two wire forms, both represented here (a value only ever populates
// one form):
//
//   - the modifier form — RequestOverride replaces the outbound (forwarded)
//     request; ResponseOverride or ResponseTemplate shape the response returned
//     to the caller; RequestModifier / ResponseModifier declaratively rewrite
//     them;
//   - the modern form — HttpRequest / HttpResponse carry the override request
//     and response directly under those keys.
//
// The wire field names mirror the MockServer HttpOverrideForwardedRequestDTO.
type HttpOverrideForwardedRequest struct {
	RequestOverride  *HttpRequest      `json:"requestOverride,omitempty"`
	RequestModifier  *RequestModifier  `json:"requestModifier,omitempty"`
	ResponseOverride *HttpResponse     `json:"responseOverride,omitempty"`
	ResponseModifier *ResponseModifier `json:"responseModifier,omitempty"`
	ResponseTemplate *HttpTemplate     `json:"responseTemplate,omitempty"`
	// HttpRequest / HttpResponse are the modern oneOf form of the override
	// (the request/response carried directly under these keys).
	HttpRequest  *HttpRequest  `json:"httpRequest,omitempty"`
	HttpResponse *HttpResponse `json:"httpResponse,omitempty"`
	Delay        *Delay        `json:"delay,omitempty"`
	// Primary marks this as the primary action when multiple are present.
	Primary *bool `json:"primary,omitempty"`
}

// HttpError represents an HTTP error action for MockServer.
type HttpError struct {
	DropConnection *bool  `json:"dropConnection,omitempty"`
	ResponseBytes  string `json:"responseBytes,omitempty"`
	// StreamError resets the matched request stream with this error code
	// (HTTP/2 RST_STREAM / HTTP/3 RESET_STREAM) instead of returning a
	// response; it takes precedence over DropConnection.
	StreamError *int64 `json:"streamError,omitempty"`
	Delay       *Delay `json:"delay,omitempty"`
	// Primary marks this as the primary action when multiple are present.
	Primary *bool `json:"primary,omitempty"`
	// ResetConnection resets the connection with a TCP RST, so the client sees
	// "connection reset by peer"; it takes precedence over StreamError and DropConnection.
	ResetConnection *bool `json:"resetConnection,omitempty"`
}

// ForwardBuilder provides a fluent API for building HttpForward actions.
type ForwardBuilder struct {
	forward HttpForward
}

// Forward creates a new ForwardBuilder.
func Forward() *ForwardBuilder {
	return &ForwardBuilder{}
}

// Host sets the forward destination host.
func (b *ForwardBuilder) Host(host string) *ForwardBuilder {
	b.forward.Host = host
	return b
}

// Port sets the forward destination port.
func (b *ForwardBuilder) Port(port int) *ForwardBuilder {
	b.forward.Port = port
	return b
}

// Scheme sets the forward scheme (HTTP or HTTPS).
func (b *ForwardBuilder) Scheme(scheme string) *ForwardBuilder {
	b.forward.Scheme = scheme
	return b
}

// WithDelay sets the forward delay.
func (b *ForwardBuilder) WithDelay(timeUnit string, value int) *ForwardBuilder {
	b.forward.Delay = &Delay{TimeUnit: timeUnit, Value: value}
	return b
}

// Build returns the constructed HttpForward.
func (b *ForwardBuilder) Build() HttpForward {
	return b.forward
}

// ErrorBuilder provides a fluent API for building HttpError actions.
type ErrorBuilder struct {
	httpError HttpError
}

// Error creates a new ErrorBuilder.
func Error() *ErrorBuilder {
	return &ErrorBuilder{}
}

// DropConnection sets whether to drop the connection.
func (b *ErrorBuilder) DropConnection(drop bool) *ErrorBuilder {
	b.httpError.DropConnection = &drop
	return b
}

// ResetConnection sets whether to reset the connection with a TCP RST.
func (b *ErrorBuilder) ResetConnection(reset bool) *ErrorBuilder {
	b.httpError.ResetConnection = &reset
	return b
}

// ResponseBytes sets the raw bytes to send before dropping.
func (b *ErrorBuilder) ResponseBytes(bytes string) *ErrorBuilder {
	b.httpError.ResponseBytes = bytes
	return b
}

// WithDelay sets the error delay.
func (b *ErrorBuilder) WithDelay(timeUnit string, value int) *ErrorBuilder {
	b.httpError.Delay = &Delay{TimeUnit: timeUnit, Value: value}
	return b
}

// Build returns the constructed HttpError.
func (b *ErrorBuilder) Build() HttpError {
	return b.httpError
}
