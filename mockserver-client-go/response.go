package mockserver

// HttpResponse represents an HTTP response action for MockServer.
type HttpResponse struct {
	StatusCode   int                 `json:"statusCode,omitempty"`
	ReasonPhrase string              `json:"reasonPhrase,omitempty"`
	Headers      map[string][]string `json:"headers,omitempty"`
	Cookies      map[string]string   `json:"cookies,omitempty"`
	Body         interface{}         `json:"body,omitempty"`
	Delay        *Delay              `json:"delay,omitempty"`
	// Trailers are HTTP trailing headers sent after the body (HTTP/2, chunked).
	Trailers map[string][]string `json:"trailers,omitempty"`
	// StatusCodeRange serves a status drawn from a range (e.g. "200-299").
	StatusCodeRange string `json:"statusCodeRange,omitempty"`
	// RecoverAfter serves a failure response for the first N matches then this
	// response (a deterministic retry/backoff recovery primitive).
	RecoverAfter      *RecoverAfter      `json:"recoverAfter,omitempty"`
	ConnectionOptions *ConnectionOptions `json:"connectionOptions,omitempty"`
	// Primary marks this as the primary action when multiple are present.
	Primary *bool `json:"primary,omitempty"`
}

// Delay represents a response delay. Value/TimeUnit give a fixed delay;
// Template (with TemplateType) computes the delay from the request; Distribution
// draws a variable delay.
type Delay struct {
	TimeUnit     string             `json:"timeUnit"`
	Value        int                `json:"value"`
	Template     string             `json:"template,omitempty"`
	TemplateType string             `json:"templateType,omitempty"`
	Distribution *DelayDistribution `json:"distribution,omitempty"`
}

// ConnectionOptions represents connection-level response options.
type ConnectionOptions struct {
	SuppressContentLengthHeader *bool  `json:"suppressContentLengthHeader,omitempty"`
	ContentLengthHeaderOverride *int   `json:"contentLengthHeaderOverride,omitempty"`
	SuppressConnectionHeader    *bool  `json:"suppressConnectionHeader,omitempty"`
	ChunkSize                   *int   `json:"chunkSize,omitempty"`
	KeepAliveOverride           *bool  `json:"keepAliveOverride,omitempty"`
	CloseSocket                 *bool  `json:"closeSocket,omitempty"`
	CloseSocketDelay            *Delay `json:"closeSocketDelay,omitempty"`
}

// FileBody represents a FILE response body with optional template processing.
type FileBody struct {
	Type         string `json:"type"`
	FilePath     string `json:"filePath,omitempty"`
	TemplateType string `json:"templateType,omitempty"`
	// TemplatedFilePath, with a TemplateType, renders FilePath itself as a template
	// against the request before the file is read.
	TemplatedFilePath bool   `json:"templatedFilePath,omitempty"`
	ContentType       string `json:"contentType,omitempty"`
}

// ResponseBuilder provides a fluent API for building HttpResponse actions.
type ResponseBuilder struct {
	response HttpResponse
}

// Response creates a new ResponseBuilder.
func Response() *ResponseBuilder {
	return &ResponseBuilder{}
}

// StatusCode sets the HTTP status code.
func (b *ResponseBuilder) StatusCode(code int) *ResponseBuilder {
	b.response.StatusCode = code
	return b
}

// ReasonPhrase sets the HTTP reason phrase.
func (b *ResponseBuilder) ReasonPhrase(phrase string) *ResponseBuilder {
	b.response.ReasonPhrase = phrase
	return b
}

// Header adds a response header.
func (b *ResponseBuilder) Header(name string, values ...string) *ResponseBuilder {
	if b.response.Headers == nil {
		b.response.Headers = make(map[string][]string)
	}
	b.response.Headers[name] = values
	return b
}

// Cookie adds a response cookie.
func (b *ResponseBuilder) Cookie(name, value string) *ResponseBuilder {
	if b.response.Cookies == nil {
		b.response.Cookies = make(map[string]string)
	}
	b.response.Cookies[name] = value
	return b
}

// Body sets the response body as a plain string.
func (b *ResponseBuilder) Body(body string) *ResponseBuilder {
	b.response.Body = body
	return b
}

// JSONBody sets the response body as a JSON string with Content-Type header.
func (b *ResponseBuilder) JSONBody(jsonStr string) *ResponseBuilder {
	b.response.Body = jsonStr
	if b.response.Headers == nil {
		b.response.Headers = make(map[string][]string)
	}
	b.response.Headers["Content-Type"] = []string{"application/json"}
	return b
}

// BodyFromFile sets the response body to a FILE body with optional template type.
// templateType may be empty, "VELOCITY", or "MUSTACHE".
func (b *ResponseBuilder) BodyFromFile(filePath, templateType, contentType string) *ResponseBuilder {
	fb := FileBody{Type: "FILE", FilePath: filePath}
	if templateType != "" {
		fb.TemplateType = templateType
	}
	if contentType != "" {
		fb.ContentType = contentType
	}
	b.response.Body = fb
	return b
}

// BodyFromTemplatedFile is BodyFromFile with the file path itself rendered as a
// template against the request. templateType must be "VELOCITY" or "MUSTACHE".
func (b *ResponseBuilder) BodyFromTemplatedFile(filePath, templateType, contentType string) *ResponseBuilder {
	b.BodyFromFile(filePath, templateType, contentType)
	fb := b.response.Body.(FileBody)
	fb.TemplatedFilePath = true
	b.response.Body = fb
	return b
}

// WithDelay sets the response delay.
func (b *ResponseBuilder) WithDelay(timeUnit string, value int) *ResponseBuilder {
	b.response.Delay = &Delay{TimeUnit: timeUnit, Value: value}
	return b
}

// Build returns the constructed HttpResponse.
func (b *ResponseBuilder) Build() HttpResponse {
	return b.response
}

// BuildPtr returns a pointer to the constructed HttpResponse. It is a
// convenience for object-callback handlers (see Client.MockWithCallback) which
// return a *HttpResponse.
func (b *ResponseBuilder) BuildPtr() *HttpResponse {
	resp := b.response
	return &resp
}
