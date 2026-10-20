//! Fluent builder for mocking an MCP (Model Context Protocol) server.
//!
//! This is an idiomatic Rust port of the Java/Node/Python client
//! `McpMockBuilder` (`org.mockserver.client.McpMockBuilder`). It produces the
//! same wire-level expectation JSON: a set of HTTP expectations that emulate a
//! Streamable-HTTP MCP server speaking JSON-RPC 2.0.
//!
//! Each generated expectation matches a JSON-RPC method (`initialize`, `ping`,
//! `notifications/initialized`, `tools/list`, `tools/call`, `resources/list`,
//! `resources/read`, `prompts/list`, `prompts/get`) on `POST <path>` and
//! responds with a Velocity template that echoes back the incoming JSON-RPC id
//! via `$!{request.jsonRpcRawId}`.
//!
//! # Example
//!
//! ```
//! use mockserver_client::mcp::mcp_mock;
//!
//! let expectations = mcp_mock("/mcp")
//!     .with_tool("get_weather")
//!         .with_description("Get the weather for a city")
//!         .with_input_schema("{\"type\":\"object\"}")
//!         .responding_with("sunny", false)
//!         .and()
//!     .build();
//!
//! // initialize, ping, notifications/initialized, tools/list, tools/call
//! assert_eq!(expectations.len(), 5);
//! ```

use serde_json::{json, Value};

use crate::error::Result;
use crate::MockServerClient;

// ---------------------------------------------------------------------------
// Escaping helpers — ported 1:1 from the Java/Node/Python builders so the
// produced template strings are byte-identical.
// ---------------------------------------------------------------------------

/// JSON-escape a string for inlining inside a JSON string literal, returning the
/// contents WITHOUT the surrounding quotes. Mirrors Java's
/// `OBJECT_MAPPER.writeValueAsString(value)` then stripping the outer quotes.
pub fn escape_json(value: &str) -> String {
    let quoted = serde_json::to_string(value).unwrap_or_else(|_| "\"\"".to_string());
    // Strip the surrounding quotes.
    quoted[1..quoted.len() - 1].to_string()
}

/// Escape Velocity meta-characters so literal `$` / `#` in mock content are not
/// interpreted as Velocity references/directives. Velocity consumes backslashes
/// written before a reference or directive, so a run of backslashes followed by
/// `$`, `#` or the end of the value is escaped too; other backslashes are left
/// as-is. Mirrors Java's `escapeVelocity`.
pub fn escape_velocity(value: &str) -> String {
    let bytes = value.as_bytes();
    let mut out = String::with_capacity(value.len());
    let mut segment_start = 0;
    let mut i = 0;
    while i < bytes.len() {
        match bytes[i] {
            b'\\' => {
                let mut run_end = i;
                while run_end < bytes.len() && bytes[run_end] == b'\\' {
                    run_end += 1;
                }
                if run_end == bytes.len() || bytes[run_end] == b'$' || bytes[run_end] == b'#' {
                    out.push_str(&value[segment_start..i]);
                    out.push_str(&"${esc.b}".repeat(run_end - i));
                    segment_start = run_end;
                }
                i = run_end;
            }
            b'$' => {
                out.push_str(&value[segment_start..i]);
                out.push_str("${esc.d}");
                i += 1;
                segment_start = i;
            }
            b'#' => {
                out.push_str(&value[segment_start..i]);
                out.push_str("${esc.h}");
                i += 1;
                segment_start = i;
            }
            _ => i += 1,
        }
    }
    out.push_str(&value[segment_start..]);
    out
}

/// Escape backslashes, then single quotes, for safe inclusion inside a
/// single-quoted JSONPath string literal.
pub fn escape_json_path(value: &str) -> String {
    value.replace('\\', "\\\\").replace('\'', "\\'")
}

/// Validate that the supplied string is valid JSON and return it re-serialised
/// in compact form. Mirrors Java's `validateAndSerializeJson`.
///
/// # Errors
/// Returns [`crate::Error`] if `raw` is not valid JSON.
pub fn validate_and_serialize_json(raw: &str) -> Result<String> {
    let parsed: Value = serde_json::from_str(raw)?;
    Ok(serde_json::to_string(&parsed)?)
}

/// Like [`escape_velocity`] composed with [`escape_json`] — applied to inlined
/// string values exactly as in the reference clients.
fn ev_ej(value: &str) -> String {
    escape_velocity(&escape_json(value))
}

/// Build the Velocity template string that renders a JSON-RPC 2.0 success
/// response wrapping the supplied result JSON. Mirrors `velocityJsonRpcResponse`.
fn velocity_json_rpc_response(result_json: &str) -> String {
    format!(
        "{{\"statusCode\": 200, \
\"headers\": [{{\"name\": \"Content-Type\", \"values\": [\"application/json\"]}}], \
\"body\": {{\"jsonrpc\": \"2.0\", \"result\": {result_json}, \"id\": $!{{request.jsonRpcRawId}}}}}}"
    )
}

// ---------------------------------------------------------------------------
// Expectation fragment helpers
// ---------------------------------------------------------------------------

fn json_rpc_request(path: &str, method: &str) -> Value {
    json!({
        "method": "POST",
        "path": path,
        "body": { "type": "JSON_RPC", "method": method }
    })
}

fn json_path_request(path: &str, json_path: &str) -> Value {
    json!({
        "method": "POST",
        "path": path,
        "body": { "type": "JSON_PATH", "jsonPath": json_path }
    })
}

fn velocity_template_expectation(http_request: Value, result_json: &str) -> Value {
    json!({
        "httpRequest": http_request,
        "httpResponseTemplate": {
            "template": velocity_json_rpc_response(result_json),
            "templateType": "VELOCITY"
        }
    })
}

// ---------------------------------------------------------------------------
// Internal definitions
// ---------------------------------------------------------------------------

#[derive(Debug, Clone)]
struct ToolDef {
    name: String,
    description: Option<String>,
    input_schema: Option<String>,
    response_content: Option<String>,
    response_is_error: bool,
}

#[derive(Debug, Clone)]
struct ResourceDef {
    uri: String,
    name: Option<String>,
    description: Option<String>,
    mime_type: String,
    content: Option<String>,
}

#[derive(Debug, Clone)]
struct PromptArgument {
    name: String,
    description: Option<String>,
    required: bool,
}

#[derive(Debug, Clone)]
struct PromptMessage {
    role: String,
    text: String,
}

#[derive(Debug, Clone)]
struct PromptDef {
    name: String,
    description: Option<String>,
    arguments: Vec<PromptArgument>,
    messages: Vec<PromptMessage>,
}

// ---------------------------------------------------------------------------
// McpMockBuilder
// ---------------------------------------------------------------------------

/// Fluent builder producing the expectations for a mocked MCP server.
#[derive(Debug, Clone)]
pub struct McpMockBuilder {
    path: String,
    server_name: String,
    server_version: String,
    protocol_version: String,
    tools_capability: bool,
    resources_capability: bool,
    prompts_capability: bool,
    tools: Vec<ToolDef>,
    resources: Vec<ResourceDef>,
    prompts: Vec<PromptDef>,
}

impl McpMockBuilder {
    fn new(path: impl Into<String>) -> Self {
        Self {
            path: path.into(),
            server_name: "MockMCPServer".to_string(),
            server_version: "1.0.0".to_string(),
            protocol_version: "2025-03-26".to_string(),
            tools_capability: false,
            resources_capability: false,
            prompts_capability: false,
            tools: Vec::new(),
            resources: Vec::new(),
            prompts: Vec::new(),
        }
    }

    /// Set the advertised server name (default `"MockMCPServer"`).
    pub fn with_server_name(mut self, name: impl Into<String>) -> Self {
        self.server_name = name.into();
        self
    }

    /// Set the advertised server version (default `"1.0.0"`).
    pub fn with_server_version(mut self, version: impl Into<String>) -> Self {
        self.server_version = version.into();
        self
    }

    /// Set the advertised protocol version (default `"2025-03-26"`).
    pub fn with_protocol_version(mut self, version: impl Into<String>) -> Self {
        self.protocol_version = version.into();
        self
    }

    /// Advertise the tools capability even with no tools defined.
    pub fn with_tools_capability(mut self) -> Self {
        self.tools_capability = true;
        self
    }

    /// Advertise the resources capability even with no resources defined.
    pub fn with_resources_capability(mut self) -> Self {
        self.resources_capability = true;
        self
    }

    /// Advertise the prompts capability even with no prompts defined.
    pub fn with_prompts_capability(mut self) -> Self {
        self.prompts_capability = true;
        self
    }

    /// Start defining a tool. Finish with [`McpToolBuilder::and`].
    pub fn with_tool(self, name: impl Into<String>) -> McpToolBuilder {
        McpToolBuilder {
            parent: self,
            tool: ToolDef {
                name: name.into(),
                description: None,
                input_schema: None,
                response_content: None,
                response_is_error: false,
            },
        }
    }

    /// Start defining a resource. Finish with [`McpResourceBuilder::and`].
    pub fn with_resource(self, uri: impl Into<String>) -> McpResourceBuilder {
        McpResourceBuilder {
            parent: self,
            resource: ResourceDef {
                uri: uri.into(),
                name: None,
                description: None,
                mime_type: "application/json".to_string(),
                content: None,
            },
        }
    }

    /// Start defining a prompt. Finish with [`McpPromptBuilder::and`].
    pub fn with_prompt(self, name: impl Into<String>) -> McpPromptBuilder {
        McpPromptBuilder {
            parent: self,
            prompt: PromptDef {
                name: name.into(),
                description: None,
                arguments: Vec::new(),
                messages: Vec::new(),
            },
        }
    }

    fn has_tools(&self) -> bool {
        self.tools_capability || !self.tools.is_empty()
    }

    fn has_resources(&self) -> bool {
        self.resources_capability || !self.resources.is_empty()
    }

    fn has_prompts(&self) -> bool {
        self.prompts_capability || !self.prompts.is_empty()
    }

    /// Build the full ordered list of MCP expectations.
    pub fn build(&self) -> Vec<Value> {
        let mut expectations = vec![
            self.build_initialize(),
            self.build_ping(),
            self.build_notifications_initialized(),
        ];

        if self.has_tools() {
            expectations.push(self.build_tools_list());
        }
        for tool in &self.tools {
            expectations.push(self.build_tools_call(tool));
        }

        if self.has_resources() {
            expectations.push(self.build_resources_list());
        }
        for resource in &self.resources {
            expectations.push(self.build_resources_read(resource));
        }

        if self.has_prompts() {
            expectations.push(self.build_prompts_list());
        }
        for prompt in &self.prompts {
            expectations.push(self.build_prompts_get(prompt));
        }

        expectations
    }

    /// Build and register the expectations on the given client.
    pub fn apply_to(&self, client: &MockServerClient) -> Result<Value> {
        client.upsert_raw(Value::Array(self.build()))
    }

    // --- individual expectation builders -------------------------------

    fn build_initialize(&self) -> Value {
        let mut caps_parts: Vec<&str> = Vec::new();
        if self.has_tools() {
            caps_parts.push("\"tools\": {\"listChanged\": false}");
        }
        if self.has_resources() {
            caps_parts.push("\"resources\": {\"subscribe\": false, \"listChanged\": false}");
        }
        if self.has_prompts() {
            caps_parts.push("\"prompts\": {\"listChanged\": false}");
        }
        let caps = format!("{{{}}}", caps_parts.join(", "));

        let result_json = format!(
            "{{\"protocolVersion\": \"{}\", \"capabilities\": {}, \"serverInfo\": {{\"name\": \"{}\", \"version\": \"{}\"}}}}",
            ev_ej(&self.protocol_version),
            caps,
            ev_ej(&self.server_name),
            ev_ej(&self.server_version),
        );

        velocity_template_expectation(json_rpc_request(&self.path, "initialize"), &result_json)
    }

    fn build_ping(&self) -> Value {
        velocity_template_expectation(json_rpc_request(&self.path, "ping"), "{}")
    }

    fn build_notifications_initialized(&self) -> Value {
        json!({
            "httpRequest": json_rpc_request(&self.path, "notifications/initialized"),
            "httpResponse": {
                "statusCode": 200,
                "headers": [{ "name": "Content-Type", "values": ["application/json"] }],
                "body": "{}"
            }
        })
    }

    fn build_tools_list(&self) -> Value {
        let items: Vec<String> = self
            .tools
            .iter()
            .map(|tool| {
                let mut s = format!("{{\"name\": \"{}\"", ev_ej(&tool.name));
                if let Some(description) = &tool.description {
                    s.push_str(&format!(", \"description\": \"{}\"", ev_ej(description)));
                }
                if let Some(input_schema) = &tool.input_schema {
                    let compacted = validate_and_serialize_json(input_schema)
                        .expect("inputSchema must be valid JSON");
                    s.push_str(&format!(", \"inputSchema\": {}", escape_velocity(&compacted)));
                }
                s.push('}');
                s
            })
            .collect();
        let tools_json = format!("[{}]", items.join(", "));
        velocity_template_expectation(
            json_rpc_request(&self.path, "tools/list"),
            &format!("{{\"tools\": {tools_json}}}"),
        )
    }

    fn build_tools_call(&self, tool: &ToolDef) -> Value {
        let json_path = format!(
            "$[?(@.method == 'tools/call' && @.params.name == '{}')]",
            escape_json_path(&tool.name)
        );
        let content = tool
            .response_content
            .as_deref()
            .map(ev_ej)
            .unwrap_or_default();
        let is_error = if tool.response_is_error { "true" } else { "false" };
        let result_json = format!(
            "{{\"content\": [{{\"type\": \"text\", \"text\": \"{content}\"}}], \"isError\": {is_error}}}"
        );
        velocity_template_expectation(json_path_request(&self.path, &json_path), &result_json)
    }

    fn build_resources_list(&self) -> Value {
        let items: Vec<String> = self
            .resources
            .iter()
            .map(|resource| {
                let mut s = format!("{{\"uri\": \"{}\"", ev_ej(&resource.uri));
                if let Some(name) = &resource.name {
                    s.push_str(&format!(", \"name\": \"{}\"", ev_ej(name)));
                }
                if let Some(description) = &resource.description {
                    s.push_str(&format!(", \"description\": \"{}\"", ev_ej(description)));
                }
                s.push_str(&format!(", \"mimeType\": \"{}\"", ev_ej(&resource.mime_type)));
                s.push('}');
                s
            })
            .collect();
        let resources_json = format!("[{}]", items.join(", "));
        velocity_template_expectation(
            json_rpc_request(&self.path, "resources/list"),
            &format!("{{\"resources\": {resources_json}}}"),
        )
    }

    fn build_resources_read(&self, resource: &ResourceDef) -> Value {
        let json_path = format!(
            "$[?(@.method == 'resources/read' && @.params.uri == '{}')]",
            escape_json_path(&resource.uri)
        );
        let content = resource
            .content
            .as_deref()
            .map(ev_ej)
            .unwrap_or_default();
        let result_json = format!(
            "{{\"contents\": [{{\"uri\": \"{}\", \"mimeType\": \"{}\", \"text\": \"{content}\"}}]}}",
            ev_ej(&resource.uri),
            ev_ej(&resource.mime_type),
        );
        velocity_template_expectation(json_path_request(&self.path, &json_path), &result_json)
    }

    fn build_prompts_list(&self) -> Value {
        let items: Vec<String> = self
            .prompts
            .iter()
            .map(|prompt| {
                let mut s = format!("{{\"name\": \"{}\"", ev_ej(&prompt.name));
                if let Some(description) = &prompt.description {
                    s.push_str(&format!(", \"description\": \"{}\"", ev_ej(description)));
                }
                if !prompt.arguments.is_empty() {
                    let args: Vec<String> = prompt
                        .arguments
                        .iter()
                        .map(|arg| {
                            let mut a = format!("{{\"name\": \"{}\"", ev_ej(&arg.name));
                            if let Some(description) = &arg.description {
                                a.push_str(&format!(", \"description\": \"{}\"", ev_ej(description)));
                            }
                            a.push_str(&format!(
                                ", \"required\": {}",
                                if arg.required { "true" } else { "false" }
                            ));
                            a.push('}');
                            a
                        })
                        .collect();
                    s.push_str(&format!(", \"arguments\": [{}]", args.join(", ")));
                }
                s.push('}');
                s
            })
            .collect();
        let prompts_json = format!("[{}]", items.join(", "));
        velocity_template_expectation(
            json_rpc_request(&self.path, "prompts/list"),
            &format!("{{\"prompts\": {prompts_json}}}"),
        )
    }

    fn build_prompts_get(&self, prompt: &PromptDef) -> Value {
        let json_path = format!(
            "$[?(@.method == 'prompts/get' && @.params.name == '{}')]",
            escape_json_path(&prompt.name)
        );
        let messages: Vec<String> = prompt
            .messages
            .iter()
            .map(|msg| {
                format!(
                    "{{\"role\": \"{}\", \"content\": {{\"type\": \"text\", \"text\": \"{}\"}}}}",
                    ev_ej(&msg.role),
                    ev_ej(&msg.text),
                )
            })
            .collect();
        let messages_json = format!("[{}]", messages.join(", "));
        let result_json = format!("{{\"messages\": {messages_json}}}");
        velocity_template_expectation(json_path_request(&self.path, &json_path), &result_json)
    }
}

// ---------------------------------------------------------------------------
// Nested sub-builders
// ---------------------------------------------------------------------------

/// Sub-builder for a single MCP tool. Call [`and`](Self::and) to return to the
/// parent [`McpMockBuilder`].
pub struct McpToolBuilder {
    parent: McpMockBuilder,
    tool: ToolDef,
}

impl McpToolBuilder {
    /// Set the tool description.
    pub fn with_description(mut self, description: impl Into<String>) -> Self {
        self.tool.description = Some(description.into());
        self
    }

    /// Set the JSON-schema input descriptor (validated + compacted on build).
    pub fn with_input_schema(mut self, json_schema: impl Into<String>) -> Self {
        self.tool.input_schema = Some(json_schema.into());
        self
    }

    /// Set the textual content returned by `tools/call` and whether it is an error.
    pub fn responding_with(mut self, text_content: impl Into<String>, is_error: bool) -> Self {
        self.tool.response_content = Some(text_content.into());
        self.tool.response_is_error = is_error;
        self
    }

    /// Finish this tool and return to the parent builder.
    pub fn and(mut self) -> McpMockBuilder {
        self.parent.tools.push(self.tool);
        self.parent.tools_capability = true;
        self.parent
    }
}

/// Sub-builder for a single MCP resource. Call [`and`](Self::and) to return to
/// the parent [`McpMockBuilder`].
pub struct McpResourceBuilder {
    parent: McpMockBuilder,
    resource: ResourceDef,
}

impl McpResourceBuilder {
    /// Set the resource name.
    pub fn with_name(mut self, name: impl Into<String>) -> Self {
        self.resource.name = Some(name.into());
        self
    }

    /// Set the resource description.
    pub fn with_description(mut self, description: impl Into<String>) -> Self {
        self.resource.description = Some(description.into());
        self
    }

    /// Set the MIME type (default `application/json`).
    pub fn with_mime_type(mut self, mime_type: impl Into<String>) -> Self {
        self.resource.mime_type = mime_type.into();
        self
    }

    /// Set the resource content returned by `resources/read`.
    pub fn with_content(mut self, content: impl Into<String>) -> Self {
        self.resource.content = Some(content.into());
        self
    }

    /// Finish this resource and return to the parent builder.
    pub fn and(mut self) -> McpMockBuilder {
        self.parent.resources.push(self.resource);
        self.parent.resources_capability = true;
        self.parent
    }
}

/// Sub-builder for a single MCP prompt. Call [`and`](Self::and) to return to the
/// parent [`McpMockBuilder`].
pub struct McpPromptBuilder {
    parent: McpMockBuilder,
    prompt: PromptDef,
}

impl McpPromptBuilder {
    /// Set the prompt description.
    pub fn with_description(mut self, description: impl Into<String>) -> Self {
        self.prompt.description = Some(description.into());
        self
    }

    /// Add a prompt argument descriptor.
    pub fn with_argument(
        mut self,
        name: impl Into<String>,
        description: Option<String>,
        required: bool,
    ) -> Self {
        self.prompt.arguments.push(PromptArgument {
            name: name.into(),
            description,
            required,
        });
        self
    }

    /// Add a message returned by `prompts/get` (use a `Role` value for `role`).
    pub fn responding_with(mut self, role: impl Into<String>, text_content: impl Into<String>) -> Self {
        self.prompt.messages.push(PromptMessage {
            role: role.into(),
            text: text_content.into(),
        });
        self
    }

    /// Finish this prompt and return to the parent builder.
    pub fn and(mut self) -> McpMockBuilder {
        self.parent.prompts.push(self.prompt);
        self.parent.prompts_capability = true;
        self.parent
    }
}

/// Create a new MCP mock builder. `path` defaults to `/mcp` if you use
/// [`mcp_mock_default`].
pub fn mcp_mock(path: impl Into<String>) -> McpMockBuilder {
    McpMockBuilder::new(path)
}

/// Create a new MCP mock builder targeting the default `/mcp` path.
pub fn mcp_mock_default() -> McpMockBuilder {
    McpMockBuilder::new("/mcp")
}
