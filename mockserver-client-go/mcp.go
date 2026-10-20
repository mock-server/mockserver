package mockserver

import (
	"encoding/json"
	"fmt"
	"strings"
)

// MCP (Model Context Protocol) mock builder for the MockServer Go client.
//
// This is an idiomatic Go port of the Java client McpMockBuilder
// (org.mockserver.client.McpMockBuilder) and its Node/Python equivalents. It
// builds the set of MockServer expectations needed to mock an MCP server
// speaking JSON-RPC 2.0 over the Streamable HTTP transport. The produced
// expectation JSON is wire-identical to the other clients so all of them drive
// the same server behaviour.
//
// Usage:
//
//	mockserver.McpMock("/mcp").
//	    WithServerName("MyServer").
//	    WithTool("get_weather", func(t *McpToolBuilder) {
//	        t.WithDescription("Get the weather for a city").
//	            WithInputSchema(`{"type":"object"}`).
//	            RespondingWith("sunny", false)
//	    }).
//	    ApplyTo(client)

// ---------------------------------------------------------------------------
// Escaping helpers — ported 1:1 from the Java/Node/Python builders so the
// produced template strings are byte-identical.
// ---------------------------------------------------------------------------

// escapeJson JSON-escapes a string for inlining inside a JSON string literal,
// returning the contents WITHOUT the surrounding quotes. Mirrors Jackson's
// writeValueAsString(value) then stripping the outer quotes.
func escapeJson(value string) string {
	if value == "" {
		return ""
	}
	quoted, err := json.Marshal(value)
	if err != nil {
		// json.Marshal of a string never fails in practice.
		return value
	}
	return string(quoted[1 : len(quoted)-1])
}

// escapeVelocity escapes Velocity meta-characters so literal '$' and '#' in
// mock content are not interpreted as Velocity references/directives. Velocity
// consumes backslashes written before a reference or directive, so a run of
// backslashes followed by '$', '#' or the end of the value is escaped too;
// other backslashes are literal to Velocity and left as-is.
func escapeVelocity(value string) string {
	var sb strings.Builder
	for i := 0; i < len(value); {
		switch value[i] {
		case '\\':
			runEnd := i
			for runEnd < len(value) && value[runEnd] == '\\' {
				runEnd++
			}
			if runEnd == len(value) || value[runEnd] == '$' || value[runEnd] == '#' {
				sb.WriteString(strings.Repeat("${esc.b}", runEnd-i))
			} else {
				sb.WriteString(value[i:runEnd])
			}
			i = runEnd
			continue
		case '$':
			sb.WriteString("${esc.d}")
		case '#':
			sb.WriteString("${esc.h}")
		default:
			sb.WriteByte(value[i])
		}
		i++
	}
	return sb.String()
}

// escapeJsonPath escapes backslashes and single quotes for safe inclusion
// inside a single-quoted JSONPath string literal. Backslashes must be escaped
// first so the backslash added before a quote is not itself doubled.
func escapeJsonPath(value string) string {
	return strings.ReplaceAll(strings.ReplaceAll(value, "\\", "\\\\"), "'", "\\'")
}

// validateAndSerializeJson parses + re-serializes JSON (compact) to validate
// it. It panics on invalid JSON, mirroring the throwing Java/Node/Python
// builders.
func validateAndSerializeJson(jsonStr string) string {
	var parsed interface{}
	if err := json.Unmarshal([]byte(jsonStr), &parsed); err != nil {
		panic(fmt.Sprintf("mockserver: invalid JSON for inputSchema: %v", err))
	}
	compact, err := json.Marshal(parsed)
	if err != nil {
		panic(fmt.Sprintf("mockserver: invalid JSON for inputSchema: %v", err))
	}
	return string(compact)
}

func velocityJsonRpcResponse(resultJson string) string {
	return `{"statusCode": 200, ` +
		`"headers": [{"name": "Content-Type", "values": ["application/json"]}], ` +
		`"body": {"jsonrpc": "2.0", "result": ` + resultJson + `, "id": $!{request.jsonRpcRawId}}}`
}

// ---------------------------------------------------------------------------
// Request matcher bodies (JSON_RPC / JSON_PATH)
// ---------------------------------------------------------------------------

type jsonRpcBody struct {
	Type   string `json:"type"`
	Method string `json:"method"`
}

type jsonPathBody struct {
	Type     string `json:"type"`
	JSONPath string `json:"jsonPath"`
}

func jsonRpcRequest(path, method string) *HttpRequest {
	return &HttpRequest{
		Method: "POST",
		Path:   path,
		Body:   jsonRpcBody{Type: "JSON_RPC", Method: method},
	}
}

func jsonPathRequest(path, jsonPath string) *HttpRequest {
	return &HttpRequest{
		Method: "POST",
		Path:   path,
		Body:   jsonPathBody{Type: "JSON_PATH", JSONPath: jsonPath},
	}
}

func velocityTemplateExpectation(request *HttpRequest, resultJson string) Expectation {
	return Expectation{
		HttpRequest: request,
		HttpResponseTemplate: &HttpTemplate{
			TemplateType: "VELOCITY",
			Template:     velocityJsonRpcResponse(resultJson),
		},
	}
}

// ---------------------------------------------------------------------------
// Internal definitions
// ---------------------------------------------------------------------------

type mcpTool struct {
	name            string
	description     string
	hasDescription  bool
	inputSchema     string
	hasInputSchema  bool
	responseContent string
	responseIsError bool
}

type mcpResource struct {
	uri            string
	name           string
	hasName        bool
	description    string
	hasDescription bool
	mimeType       string
	content        string
}

type mcpPromptArgument struct {
	name           string
	description    string
	hasDescription bool
	required       bool
}

type mcpPromptMessage struct {
	role string
	text string
}

type mcpPrompt struct {
	name           string
	description    string
	hasDescription bool
	arguments      []mcpPromptArgument
	messages       []mcpPromptMessage
}

// ---------------------------------------------------------------------------
// McpMockBuilder
// ---------------------------------------------------------------------------

// McpMockBuilder builds the expectations that emulate an MCP server.
type McpMockBuilder struct {
	path                string
	serverName          string
	serverVersion       string
	protocolVersion     string
	toolsCapability     bool
	resourcesCapability bool
	promptsCapability   bool
	tools               []mcpTool
	resources           []mcpResource
	prompts             []mcpPrompt
}

// McpMock creates a new MCP mock builder. path defaults to "/mcp" when empty.
func McpMock(path string) *McpMockBuilder {
	if path == "" {
		path = "/mcp"
	}
	return &McpMockBuilder{
		path:            path,
		serverName:      "MockMCPServer",
		serverVersion:   "1.0.0",
		protocolVersion: "2025-03-26",
	}
}

// WithServerName sets the MCP server name reported by initialize.
func (b *McpMockBuilder) WithServerName(name string) *McpMockBuilder {
	b.serverName = name
	return b
}

// WithServerVersion sets the MCP server version reported by initialize.
func (b *McpMockBuilder) WithServerVersion(version string) *McpMockBuilder {
	b.serverVersion = version
	return b
}

// WithProtocolVersion sets the MCP protocol version reported by initialize.
func (b *McpMockBuilder) WithProtocolVersion(version string) *McpMockBuilder {
	b.protocolVersion = version
	return b
}

// WithToolsCapability advertises the tools capability even without tools.
func (b *McpMockBuilder) WithToolsCapability() *McpMockBuilder {
	b.toolsCapability = true
	return b
}

// WithResourcesCapability advertises the resources capability even without resources.
func (b *McpMockBuilder) WithResourcesCapability() *McpMockBuilder {
	b.resourcesCapability = true
	return b
}

// WithPromptsCapability advertises the prompts capability even without prompts.
func (b *McpMockBuilder) WithPromptsCapability() *McpMockBuilder {
	b.promptsCapability = true
	return b
}

// McpToolBuilder configures a single MCP tool.
type McpToolBuilder struct {
	tool *mcpTool
}

// WithDescription sets the tool description.
func (t *McpToolBuilder) WithDescription(description string) *McpToolBuilder {
	t.tool.description = description
	t.tool.hasDescription = true
	return t
}

// WithInputSchema sets the tool input JSON schema (validated + compacted).
func (t *McpToolBuilder) WithInputSchema(jsonSchema string) *McpToolBuilder {
	t.tool.inputSchema = jsonSchema
	t.tool.hasInputSchema = true
	return t
}

// RespondingWith sets the tools/call response text and error flag.
func (t *McpToolBuilder) RespondingWith(text string, isError bool) *McpToolBuilder {
	t.tool.responseContent = text
	t.tool.responseIsError = isError
	return t
}

// WithTool registers a tool, configured via the supplied closure, and enables
// the tools capability.
func (b *McpMockBuilder) WithTool(name string, configure func(*McpToolBuilder)) *McpMockBuilder {
	tb := &McpToolBuilder{tool: &mcpTool{name: name}}
	if configure != nil {
		configure(tb)
	}
	b.tools = append(b.tools, *tb.tool)
	b.toolsCapability = true
	return b
}

// McpResourceBuilder configures a single MCP resource.
type McpResourceBuilder struct {
	resource *mcpResource
}

// WithName sets the resource name.
func (r *McpResourceBuilder) WithName(name string) *McpResourceBuilder {
	r.resource.name = name
	r.resource.hasName = true
	return r
}

// WithDescription sets the resource description.
func (r *McpResourceBuilder) WithDescription(description string) *McpResourceBuilder {
	r.resource.description = description
	r.resource.hasDescription = true
	return r
}

// WithMimeType sets the resource MIME type (default application/json).
func (r *McpResourceBuilder) WithMimeType(mimeType string) *McpResourceBuilder {
	r.resource.mimeType = mimeType
	return r
}

// WithContent sets the resources/read content.
func (r *McpResourceBuilder) WithContent(content string) *McpResourceBuilder {
	r.resource.content = content
	return r
}

// WithResource registers a resource, configured via the supplied closure, and
// enables the resources capability.
func (b *McpMockBuilder) WithResource(uri string, configure func(*McpResourceBuilder)) *McpMockBuilder {
	rb := &McpResourceBuilder{resource: &mcpResource{uri: uri, mimeType: "application/json"}}
	if configure != nil {
		configure(rb)
	}
	b.resources = append(b.resources, *rb.resource)
	b.resourcesCapability = true
	return b
}

// McpPromptBuilder configures a single MCP prompt.
type McpPromptBuilder struct {
	prompt *mcpPrompt
}

// WithDescription sets the prompt description.
func (p *McpPromptBuilder) WithDescription(description string) *McpPromptBuilder {
	p.prompt.description = description
	p.prompt.hasDescription = true
	return p
}

// WithArgument adds a prompt argument.
func (p *McpPromptBuilder) WithArgument(name, description string, required bool) *McpPromptBuilder {
	p.prompt.arguments = append(p.prompt.arguments, mcpPromptArgument{
		name:           name,
		description:    description,
		hasDescription: description != "",
		required:       required,
	})
	return p
}

// RespondingWith appends a prompts/get message (role + text).
func (p *McpPromptBuilder) RespondingWith(role, text string) *McpPromptBuilder {
	p.prompt.messages = append(p.prompt.messages, mcpPromptMessage{role: role, text: text})
	return p
}

// WithPrompt registers a prompt, configured via the supplied closure, and
// enables the prompts capability.
func (b *McpMockBuilder) WithPrompt(name string, configure func(*McpPromptBuilder)) *McpMockBuilder {
	pb := &McpPromptBuilder{prompt: &mcpPrompt{name: name}}
	if configure != nil {
		configure(pb)
	}
	b.prompts = append(b.prompts, *pb.prompt)
	b.promptsCapability = true
	return b
}

// ---------------------------------------------------------------------------
// Expectation assembly
// ---------------------------------------------------------------------------

func (b *McpMockBuilder) buildInitialize() Expectation {
	var caps []string
	if b.toolsCapability || len(b.tools) > 0 {
		caps = append(caps, `"tools": {"listChanged": false}`)
	}
	if b.resourcesCapability || len(b.resources) > 0 {
		caps = append(caps, `"resources": {"subscribe": false, "listChanged": false}`)
	}
	if b.promptsCapability || len(b.prompts) > 0 {
		caps = append(caps, `"prompts": {"listChanged": false}`)
	}
	capsJson := "{" + strings.Join(caps, ", ") + "}"

	resultJson := `{"protocolVersion": "` + escapeVelocity(escapeJson(b.protocolVersion)) + `", ` +
		`"capabilities": ` + capsJson + `, ` +
		`"serverInfo": {"name": "` + escapeVelocity(escapeJson(b.serverName)) + `", "version": "` +
		escapeVelocity(escapeJson(b.serverVersion)) + `"}}`

	return velocityTemplateExpectation(jsonRpcRequest(b.path, "initialize"), resultJson)
}

func (b *McpMockBuilder) buildPing() Expectation {
	return velocityTemplateExpectation(jsonRpcRequest(b.path, "ping"), "{}")
}

func (b *McpMockBuilder) buildNotificationsInitialized() Expectation {
	return Expectation{
		HttpRequest: jsonRpcRequest(b.path, "notifications/initialized"),
		HttpResponse: &HttpResponse{
			StatusCode: 200,
			Headers:    map[string][]string{"Content-Type": {"application/json"}},
			Body:       "{}",
		},
	}
}

func (b *McpMockBuilder) buildToolsList() Expectation {
	items := make([]string, 0, len(b.tools))
	for _, tool := range b.tools {
		var sb strings.Builder
		sb.WriteString(`{"name": "` + escapeVelocity(escapeJson(tool.name)) + `"`)
		if tool.hasDescription {
			sb.WriteString(`, "description": "` + escapeVelocity(escapeJson(tool.description)) + `"`)
		}
		if tool.hasInputSchema {
			sb.WriteString(`, "inputSchema": ` + escapeVelocity(validateAndSerializeJson(tool.inputSchema)))
		}
		sb.WriteString("}")
		items = append(items, sb.String())
	}
	toolsJson := "[" + strings.Join(items, ", ") + "]"
	return velocityTemplateExpectation(jsonRpcRequest(b.path, "tools/list"), `{"tools": `+toolsJson+"}")
}

func (b *McpMockBuilder) buildToolsCall(tool mcpTool) Expectation {
	jsonPath := "$[?(@.method == 'tools/call' && @.params.name == '" + escapeJsonPath(tool.name) + "')]"
	content := escapeVelocity(escapeJson(tool.responseContent))
	isError := "false"
	if tool.responseIsError {
		isError = "true"
	}
	resultJson := `{"content": [{"type": "text", "text": "` + content + `"}], "isError": ` + isError + "}"
	return velocityTemplateExpectation(jsonPathRequest(b.path, jsonPath), resultJson)
}

func (b *McpMockBuilder) buildResourcesList() Expectation {
	items := make([]string, 0, len(b.resources))
	for _, resource := range b.resources {
		var sb strings.Builder
		sb.WriteString(`{"uri": "` + escapeVelocity(escapeJson(resource.uri)) + `"`)
		if resource.hasName {
			sb.WriteString(`, "name": "` + escapeVelocity(escapeJson(resource.name)) + `"`)
		}
		if resource.hasDescription {
			sb.WriteString(`, "description": "` + escapeVelocity(escapeJson(resource.description)) + `"`)
		}
		if resource.mimeType != "" {
			sb.WriteString(`, "mimeType": "` + escapeVelocity(escapeJson(resource.mimeType)) + `"`)
		}
		sb.WriteString("}")
		items = append(items, sb.String())
	}
	resourcesJson := "[" + strings.Join(items, ", ") + "]"
	return velocityTemplateExpectation(jsonRpcRequest(b.path, "resources/list"), `{"resources": `+resourcesJson+"}")
}

func (b *McpMockBuilder) buildResourcesRead(resource mcpResource) Expectation {
	jsonPath := "$[?(@.method == 'resources/read' && @.params.uri == '" + escapeJsonPath(resource.uri) + "')]"
	content := escapeVelocity(escapeJson(resource.content))
	mimeType := resource.mimeType
	if mimeType == "" {
		mimeType = "application/json"
	}
	resultJson := `{"contents": [{"uri": "` + escapeVelocity(escapeJson(resource.uri)) + `", ` +
		`"mimeType": "` + escapeVelocity(escapeJson(mimeType)) + `", ` +
		`"text": "` + content + `"}]}`
	return velocityTemplateExpectation(jsonPathRequest(b.path, jsonPath), resultJson)
}

func (b *McpMockBuilder) buildPromptsList() Expectation {
	items := make([]string, 0, len(b.prompts))
	for _, prompt := range b.prompts {
		var sb strings.Builder
		sb.WriteString(`{"name": "` + escapeVelocity(escapeJson(prompt.name)) + `"`)
		if prompt.hasDescription {
			sb.WriteString(`, "description": "` + escapeVelocity(escapeJson(prompt.description)) + `"`)
		}
		if len(prompt.arguments) > 0 {
			argItems := make([]string, 0, len(prompt.arguments))
			for _, arg := range prompt.arguments {
				var ab strings.Builder
				ab.WriteString(`{"name": "` + escapeVelocity(escapeJson(arg.name)) + `"`)
				if arg.hasDescription {
					ab.WriteString(`, "description": "` + escapeVelocity(escapeJson(arg.description)) + `"`)
				}
				required := "false"
				if arg.required {
					required = "true"
				}
				ab.WriteString(`, "required": ` + required)
				ab.WriteString("}")
				argItems = append(argItems, ab.String())
			}
			sb.WriteString(`, "arguments": [` + strings.Join(argItems, ", ") + "]")
		}
		sb.WriteString("}")
		items = append(items, sb.String())
	}
	promptsJson := "[" + strings.Join(items, ", ") + "]"
	return velocityTemplateExpectation(jsonRpcRequest(b.path, "prompts/list"), `{"prompts": `+promptsJson+"}")
}

func (b *McpMockBuilder) buildPromptsGet(prompt mcpPrompt) Expectation {
	jsonPath := "$[?(@.method == 'prompts/get' && @.params.name == '" + escapeJsonPath(prompt.name) + "')]"
	msgItems := make([]string, 0, len(prompt.messages))
	for _, msg := range prompt.messages {
		msgItems = append(msgItems, `{"role": "`+escapeVelocity(escapeJson(msg.role))+`", `+
			`"content": {"type": "text", "text": "`+escapeVelocity(escapeJson(msg.text))+`"}}`)
	}
	messagesJson := "[" + strings.Join(msgItems, ", ") + "]"
	resultJson := `{"messages": ` + messagesJson + "}"
	return velocityTemplateExpectation(jsonPathRequest(b.path, jsonPath), resultJson)
}

// Build returns the ordered list of expectations that emulate the MCP server.
func (b *McpMockBuilder) Build() []Expectation {
	expectations := []Expectation{
		b.buildInitialize(),
		b.buildPing(),
		b.buildNotificationsInitialized(),
	}

	if b.toolsCapability || len(b.tools) > 0 {
		expectations = append(expectations, b.buildToolsList())
	}
	for _, tool := range b.tools {
		expectations = append(expectations, b.buildToolsCall(tool))
	}

	if b.resourcesCapability || len(b.resources) > 0 {
		expectations = append(expectations, b.buildResourcesList())
	}
	for _, resource := range b.resources {
		expectations = append(expectations, b.buildResourcesRead(resource))
	}

	if b.promptsCapability || len(b.prompts) > 0 {
		expectations = append(expectations, b.buildPromptsList())
	}
	for _, prompt := range b.prompts {
		expectations = append(expectations, b.buildPromptsGet(prompt))
	}

	return expectations
}

// ApplyTo registers the MCP expectations with the client via Upsert.
func (b *McpMockBuilder) ApplyTo(c *Client) ([]Expectation, error) {
	return c.Upsert(b.Build()...)
}
