package mockserver

import (
	"encoding/json"
	"strings"
	"testing"
)

// findByTemplate returns the first expectation whose response-template body
// contains the given substring.
func findByTemplate(exps []Expectation, substr string) *Expectation {
	for i := range exps {
		if exps[i].HttpResponseTemplate != nil && strings.Contains(exps[i].HttpResponseTemplate.Template, substr) {
			return &exps[i]
		}
	}
	return nil
}

func TestMcpMock_DefaultsAndStructure(t *testing.T) {
	exps := McpMock("").Build() // empty path -> default "/mcp"

	// initialize + ping + notifications/initialized, no capabilities by default.
	if len(exps) != 3 {
		t.Fatalf("expected 3 expectations with no capabilities, got %d", len(exps))
	}

	// initialize: velocity template with server defaults + protocol version.
	init := exps[0]
	if init.HttpRequest == nil || init.HttpRequest.Path != "/mcp" {
		t.Fatalf("initialize path = %#v, want /mcp", init.HttpRequest)
	}
	rpc, ok := init.HttpRequest.Body.(jsonRpcBody)
	if !ok || rpc.Type != "JSON_RPC" || rpc.Method != "initialize" {
		t.Errorf("initialize body = %#v, want JSON_RPC initialize", init.HttpRequest.Body)
	}
	if init.HttpResponseTemplate == nil || init.HttpResponseTemplate.TemplateType != "VELOCITY" {
		t.Fatalf("initialize template missing or not VELOCITY: %#v", init.HttpResponseTemplate)
	}
	tmpl := init.HttpResponseTemplate.Template
	if !strings.Contains(tmpl, `"protocolVersion": "2025-03-26"`) {
		t.Errorf("initialize template missing default protocol version: %q", tmpl)
	}
	if !strings.Contains(tmpl, `"name": "MockMCPServer"`) || !strings.Contains(tmpl, `"version": "1.0.0"`) {
		t.Errorf("initialize template missing server defaults: %q", tmpl)
	}
	if !strings.Contains(tmpl, `"id": $!{request.jsonRpcRawId}`) {
		t.Errorf("initialize template missing jsonRpcRawId echo: %q", tmpl)
	}
	if !strings.Contains(tmpl, `"capabilities": {}`) {
		t.Errorf("initialize template should have empty capabilities by default: %q", tmpl)
	}

	// notifications/initialized -> plain 200 httpResponse, body {}.
	notif := exps[2]
	if notif.HttpResponse == nil || notif.HttpResponse.StatusCode != 200 || notif.HttpResponse.Body != "{}" {
		t.Errorf("notifications/initialized = %#v, want 200 / {}", notif.HttpResponse)
	}
}

func TestMcpMock_ToolWiring(t *testing.T) {
	exps := McpMock("/mcp").
		WithServerName("WeatherServer").
		WithTool("get_weather", func(tb *McpToolBuilder) {
			tb.WithDescription("Get weather for a city").
				WithInputSchema(`{"type":"object","properties":{"city":{"type":"string"}}}`).
				RespondingWith("72F and sunny", false)
		}).
		Build()

	// initialize, ping, notifications, tools/list, tools/call = 5.
	if len(exps) != 5 {
		t.Fatalf("expected 5 expectations, got %d", len(exps))
	}

	// initialize advertises tools capability.
	if !strings.Contains(exps[0].HttpResponseTemplate.Template, `"tools": {"listChanged": false}`) {
		t.Errorf("initialize should advertise tools capability: %q", exps[0].HttpResponseTemplate.Template)
	}

	// tools/list contains the validated + compacted input schema.
	list := findByTemplate(exps, `"tools": [`)
	if list == nil {
		t.Fatalf("tools/list expectation not found")
	}
	if !strings.Contains(list.HttpResponseTemplate.Template, `"name": "get_weather"`) {
		t.Errorf("tools/list missing tool name: %q", list.HttpResponseTemplate.Template)
	}
	// Compacted schema (no spaces) proves validateAndSerializeJson ran. Go's
	// encoding/json sorts object keys, so assert on the compacted form it emits.
	if !strings.Contains(list.HttpResponseTemplate.Template, `"inputSchema": {"properties":{"city":{"type":"string"}},"type":"object"}`) {
		t.Errorf("tools/list missing compacted inputSchema: %q", list.HttpResponseTemplate.Template)
	}

	// tools/call uses a JSON_PATH matcher and returns the content.
	var call *Expectation
	for i := range exps {
		if jp, ok := exps[i].HttpRequest.Body.(jsonPathBody); ok && strings.Contains(jp.JSONPath, "tools/call") {
			call = &exps[i]
			break
		}
	}
	if call == nil {
		t.Fatalf("tools/call expectation not found")
	}
	jp := call.HttpRequest.Body.(jsonPathBody)
	if jp.Type != "JSON_PATH" {
		t.Errorf("tools/call body type = %q, want JSON_PATH", jp.Type)
	}
	wantPath := "$[?(@.method == 'tools/call' && @.params.name == 'get_weather')]"
	if jp.JSONPath != wantPath {
		t.Errorf("tools/call jsonPath = %q, want %q", jp.JSONPath, wantPath)
	}
	if !strings.Contains(call.HttpResponseTemplate.Template, `"text": "72F and sunny"`) {
		t.Errorf("tools/call missing response content: %q", call.HttpResponseTemplate.Template)
	}
	if !strings.Contains(call.HttpResponseTemplate.Template, `"isError": false`) {
		t.Errorf("tools/call missing isError flag: %q", call.HttpResponseTemplate.Template)
	}
}

func TestMcpMock_ResourcesAndPrompts(t *testing.T) {
	exps := McpMock("/mcp").
		WithResource("file:///config.json", func(rb *McpResourceBuilder) {
			rb.WithName("config").WithMimeType("application/json").WithContent(`{"debug":true}`)
		}).
		WithPrompt("greeting", func(pb *McpPromptBuilder) {
			pb.WithDescription("A greeting").
				WithArgument("name", "the name", true).
				RespondingWith(RoleUser, "Hello {{name}}")
		}).
		Build()

	// initialize, ping, notifications, resources/list, resources/read,
	// prompts/list, prompts/get = 7.
	if len(exps) != 7 {
		t.Fatalf("expected 7 expectations, got %d", len(exps))
	}

	initTmpl := exps[0].HttpResponseTemplate.Template
	if !strings.Contains(initTmpl, `"resources": {"subscribe": false, "listChanged": false}`) {
		t.Errorf("initialize missing resources capability: %q", initTmpl)
	}
	if !strings.Contains(initTmpl, `"prompts": {"listChanged": false}`) {
		t.Errorf("initialize missing prompts capability: %q", initTmpl)
	}

	promptsList := findByTemplate(exps, `"prompts": [`)
	if promptsList == nil {
		t.Fatalf("prompts/list expectation not found")
	}
	if !strings.Contains(promptsList.HttpResponseTemplate.Template, `"arguments": [{"name": "name", "description": "the name", "required": true}]`) {
		t.Errorf("prompts/list arguments shape wrong: %q", promptsList.HttpResponseTemplate.Template)
	}

	promptsGet := findByTemplate(exps, `"messages": [`)
	if promptsGet == nil {
		t.Fatalf("prompts/get expectation not found")
	}
	if !strings.Contains(promptsGet.HttpResponseTemplate.Template, `"role": "USER"`) {
		t.Errorf("prompts/get missing role: %q", promptsGet.HttpResponseTemplate.Template)
	}
}

func TestMcpMock_CapabilityWithoutItems(t *testing.T) {
	exps := McpMock("/mcp").WithToolsCapability().Build()
	// initialize, ping, notifications, tools/list (no tools/call) = 4.
	if len(exps) != 4 {
		t.Fatalf("expected 4 expectations, got %d", len(exps))
	}
	if findByTemplate(exps, `"tools": []`) == nil {
		t.Errorf("expected an empty tools/list when capability set without tools")
	}
}

func TestMcpMock_EscapingVelocityAndJsonPath(t *testing.T) {
	exps := McpMock("/mcp").
		WithTool("ca$h#tool", func(tb *McpToolBuilder) {
			tb.RespondingWith("price is $5 #1", false)
		}).
		WithResource("file:///o'brien.json", func(rb *McpResourceBuilder) {
			rb.WithContent("data")
		}).
		Build()

	// Velocity metacharacters in tool name/content must be escaped.
	list := findByTemplate(exps, `"tools": [`)
	if list == nil {
		t.Fatalf("tools/list not found")
	}
	if !strings.Contains(list.HttpResponseTemplate.Template, `ca${esc.d}h${esc.h}tool`) {
		t.Errorf("tool name velocity escaping wrong: %q", list.HttpResponseTemplate.Template)
	}

	// Single quotes in a resources/read JSONPath must be backslash-escaped.
	var read *Expectation
	for i := range exps {
		if jp, ok := exps[i].HttpRequest.Body.(jsonPathBody); ok && strings.Contains(jp.JSONPath, "resources/read") {
			read = &exps[i]
			break
		}
	}
	if read == nil {
		t.Fatalf("resources/read not found")
	}
	jp := read.HttpRequest.Body.(jsonPathBody)
	if !strings.Contains(jp.JSONPath, `o\'brien.json`) {
		t.Errorf("resource uri jsonPath escaping wrong: %q", jp.JSONPath)
	}
}

func TestEscapeJsonPath_EscapesBackslashBeforeQuote(t *testing.T) {
	cases := map[string]string{
		`plain`:   `plain`,
		`o'brien`: `o\'brien`,
		`x\`:      `x\\`,
		`a\b`:     `a\\b`,
		`a\'b`:    `a\\\'b`,
		`\\`:      `\\\\`,
	}
	for in, want := range cases {
		if got := escapeJsonPath(in); got != want {
			t.Errorf("escapeJsonPath(%q) = %q, want %q", in, got, want)
		}
	}
}

func TestMcpMock_JsonPathEscapesTrailingBackslash(t *testing.T) {
	exps := McpMock("/mcp").
		WithTool(`tool\`, func(tb *McpToolBuilder) { tb.RespondingWith("ok", false) }).
		WithResource(`file:///c:\dir\`, func(rb *McpResourceBuilder) { rb.WithContent("data") }).
		WithPrompt(`p\'q`, func(pb *McpPromptBuilder) { pb.RespondingWith("user", "hi") }).
		Build()

	want := map[string]string{
		"tools/call":     `@.params.name == 'tool\\')]`,
		"resources/read": `@.params.uri == 'file:///c:\\dir\\')]`,
		"prompts/get":    `@.params.name == 'p\\\'q')]`,
	}
	for method, suffix := range want {
		found := false
		for i := range exps {
			jp, ok := exps[i].HttpRequest.Body.(jsonPathBody)
			if ok && strings.Contains(jp.JSONPath, "'"+method+"'") {
				found = true
				if !strings.HasSuffix(jp.JSONPath, suffix) {
					t.Errorf("%s jsonPath = %q, want suffix %q", method, jp.JSONPath, suffix)
				}
			}
		}
		if !found {
			t.Errorf("%s expectation not found", method)
		}
	}
}

func TestMcpMock_InvalidInputSchemaPanics(t *testing.T) {
	defer func() {
		if r := recover(); r == nil {
			t.Errorf("expected panic for invalid inputSchema JSON")
		}
	}()
	McpMock("/mcp").
		WithTool("bad", func(tb *McpToolBuilder) {
			tb.WithInputSchema("{not valid json")
		}).
		Build()
}

// TestMcpMock_TemplateIsValidJsonRpcEnvelope sanity-checks that the velocity
// envelope (with the id placeholder substituted) parses as JSON-RPC 2.0.
func TestMcpMock_TemplateIsValidJsonRpcEnvelope(t *testing.T) {
	exps := McpMock("/mcp").WithToolsCapability().Build()
	tmpl := exps[0].HttpResponseTemplate.Template
	rendered := strings.ReplaceAll(tmpl, "$!{request.jsonRpcRawId}", "1")
	var envelope map[string]interface{}
	if err := json.Unmarshal([]byte(rendered), &envelope); err != nil {
		t.Fatalf("rendered template is not valid JSON: %v\n%s", err, rendered)
	}
	body := envelope["body"].(map[string]interface{})
	if body["jsonrpc"] != "2.0" {
		t.Errorf("jsonrpc = %v, want 2.0", body["jsonrpc"])
	}
}
