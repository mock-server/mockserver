//! Fluent builder for mocking an A2A (Agent-to-Agent) protocol server.
//!
//! This is an idiomatic Rust port of the Java client `A2aMockBuilder`
//! (`org.mockserver.client.A2aMockBuilder`). It produces the same wire-level
//! expectation JSON: a set of HTTP expectations that emulate an A2A agent —
//! a discoverable agent card plus a JSON-RPC 2.0 task endpoint.
//!
//! Each generated expectation either:
//!
//! - serves the agent card on `GET <agentCardPath>` (a static JSON response), or
//! - matches a JSON-RPC method (`tasks/send`, `tasks/get`, `tasks/cancel`,
//!   `tasks/pushNotificationConfig/set`, or the streaming method) on
//!   `POST <path>` and responds with a Velocity template that echoes the
//!   incoming JSON-RPC id via `$!{request.jsonRpcRawId}`.
//!
//! Optional capabilities mirror the Java builder exactly:
//!
//! - **streaming** — advertises `capabilities.streaming: true` and adds an SSE
//!   expectation for the streaming method (default `message/stream`) that emits
//!   a `working` status-update, an artifact-update, then a `completed`
//!   (`final: true`) status-update, each wrapped in a JSON-RPC 2.0 envelope.
//! - **push notifications** — advertises `capabilities.pushNotifications: true`,
//!   echoes the registered config from `tasks/pushNotificationConfig/set`, and
//!   replaces the plain `tasks/send` expectation with an
//!   override-forwarded-request that POSTs the completed task to the configured
//!   webhook while still returning the JSON-RPC task response to the caller.
//!
//! # Example
//!
//! ```
//! use mockserver_client::a2a::a2a_mock_default;
//!
//! let expectations = a2a_mock_default()
//!     .with_agent_name("TestAgent")
//!     .with_skill("translate")
//!         .with_name("Translation")
//!         .with_description("Translates text")
//!         .with_tag("i18n")
//!         .and()
//!     .on_task_send()
//!         .matching_message("translate.*")
//!         .responding_with("Bonjour", false)
//!         .and()
//!     .build();
//!
//! // agent card + custom handler + tasks/send + tasks/get + tasks/cancel
//! assert_eq!(expectations.len(), 5);
//! ```

use serde_json::{json, Value};

use crate::error::Result;
use crate::mcp::{escape_json, escape_velocity};
use crate::MockServerClient;

// ---------------------------------------------------------------------------
// Escaping / template helpers — ported 1:1 from the Java builder so the
// produced template strings are byte-identical to the other clients.
// ---------------------------------------------------------------------------

/// Like [`escape_velocity`] composed with [`escape_json`] — applied to inlined
/// string values destined for a Velocity-templated body, exactly as in the
/// reference clients (`escapeVelocity(escapeJson(value))`).
fn ev_ej(value: &str) -> String {
    escape_velocity(&escape_json(value))
}

/// Build the Velocity template string that renders a JSON-RPC 2.0 success
/// response wrapping the supplied result JSON. Mirrors Java's
/// `velocityJsonRpcResponse`.
fn velocity_json_rpc_response(result_json: &str) -> String {
    format!(
        "{{\"statusCode\": 200, \
\"headers\": [{{\"name\": \"Content-Type\", \"values\": [\"application/json\"]}}], \
\"body\": {{\"jsonrpc\": \"2.0\", \"result\": {result_json}, \"id\": $!{{request.jsonRpcRawId}}}}}}"
    )
}

/// The task `result` JSON, with `escaped_text` already escaped appropriately for
/// the destination context. Mirrors Java's `taskResultJson`.
fn task_result_json(escaped_text: &str, is_error: bool) -> String {
    let state = if is_error { "failed" } else { "completed" };
    format!(
        "{{\"id\": \"mock-task-id\", \
\"status\": {{\"state\": \"{state}\"}}, \
\"artifacts\": [{{\"parts\": [{{\"type\": \"text\", \"text\": \"{escaped_text}\"}}]}}]}}"
    )
}

/// Task result for Velocity-templated bodies — the text must survive the
/// Velocity engine, so both JSON and Velocity escaping are applied. Mirrors
/// Java's `buildTaskResultJson`.
fn build_task_result_json(response_text: &str, is_error: bool) -> String {
    task_result_json(&ev_ej(response_text), is_error)
}

/// Task result for literal (non-templated) bodies (e.g. the webhook POST
/// payload), where no Velocity engine runs — only JSON escaping is applied, as
/// Velocity escaping would corrupt any `$` / `#`. Mirrors Java's
/// `buildTaskResultJsonRaw`.
fn build_task_result_json_raw(response_text: &str, is_error: bool) -> String {
    task_result_json(&escape_json(response_text), is_error)
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
struct SkillDef {
    id: String,
    name: Option<String>,
    description: Option<String>,
    tags: Vec<String>,
    examples: Vec<String>,
}

#[derive(Debug, Clone)]
struct TaskHandler {
    message_pattern: String,
    response_text: String,
    is_error: bool,
}

/// A parsed push-notification webhook target — host, port, scheme, and path.
/// Mirrors Java's `WebhookTarget`.
#[derive(Debug, Clone)]
struct WebhookTarget {
    host: String,
    port: u16,
    secure: bool,
    path: String,
}

impl WebhookTarget {
    fn host_header(&self) -> String {
        format!("{}:{}", self.host, self.port)
    }

    /// Parse an absolute webhook URL into its components, applying default ports
    /// (80 for http, 443 for https) and a default path of `/`.
    ///
    /// # Errors
    /// Returns [`crate::Error`] if the URL has no host or an unparseable port.
    fn parse(url: &str) -> Result<Self> {
        // Minimal URL parsing sufficient for absolute http(s) webhook URLs,
        // mirroring the subset of java.net.URI behaviour the Java builder relies
        // on. Avoids pulling in a URL-parsing dependency.
        let (scheme, rest) = url
            .split_once("://")
            .ok_or_else(|| crate::Error::InvalidRequest(format!(
                "Invalid push-notification webhook URL (no scheme): {url}"
            )))?;
        let secure = scheme.eq_ignore_ascii_case("https");

        // Authority is everything up to the first '/', '?' or '#'.
        let authority_end = rest
            .find(['/', '?', '#'])
            .unwrap_or(rest.len());
        let authority = &rest[..authority_end];
        let path_and_rest = &rest[authority_end..];

        // Strip userinfo if present.
        let host_port = authority.rsplit_once('@').map_or(authority, |(_, hp)| hp);

        let (host, port) = match host_port.rsplit_once(':') {
            // Guard against IPv6-style hosts that contain ':' but no port — the
            // webhook URLs in practice are host[:port]; treat a non-numeric tail
            // as part of the host.
            Some((h, p)) if !p.is_empty() && p.chars().all(|c| c.is_ascii_digit()) => {
                let port = p.parse::<u16>().map_err(|_| {
                    crate::Error::InvalidRequest(format!(
                        "Invalid push-notification webhook URL (bad port): {url}"
                    ))
                })?;
                (h.to_string(), port)
            }
            _ => (
                host_port.to_string(),
                if secure { 443 } else { 80 },
            ),
        };

        if host.is_empty() {
            return Err(crate::Error::InvalidRequest(format!(
                "Invalid push-notification webhook URL (no host): {url}"
            )));
        }

        // Path excludes any query/fragment; default to "/" when empty.
        let path = path_and_rest
            .split(['?', '#'])
            .next()
            .unwrap_or("");
        let path = if path.is_empty() { "/".to_string() } else { path.to_string() };

        Ok(Self { host, port, secure, path })
    }
}

// ---------------------------------------------------------------------------
// A2aMockBuilder
// ---------------------------------------------------------------------------

/// Fluent builder producing the expectations for a mocked A2A agent.
#[derive(Debug, Clone)]
pub struct A2aMockBuilder {
    path: String,
    agent_card_path: String,
    agent_name: String,
    agent_description: String,
    agent_version: String,
    agent_url: Option<String>,
    skills: Vec<SkillDef>,
    task_handlers: Vec<TaskHandler>,
    default_task_response: String,
    streaming: bool,
    streaming_method: String,
    push_notification_url: Option<String>,
}

impl A2aMockBuilder {
    fn new(path: impl Into<String>) -> Self {
        Self {
            path: path.into(),
            agent_card_path: "/.well-known/agent.json".to_string(),
            agent_name: "MockAgent".to_string(),
            agent_description: "A mock A2A agent".to_string(),
            agent_version: "1.0.0".to_string(),
            agent_url: None,
            skills: Vec::new(),
            task_handlers: Vec::new(),
            default_task_response: "Task completed successfully".to_string(),
            streaming: false,
            streaming_method: "message/stream".to_string(),
            push_notification_url: None,
        }
    }

    /// Set the advertised agent name (default `"MockAgent"`).
    pub fn with_agent_name(mut self, name: impl Into<String>) -> Self {
        self.agent_name = name.into();
        self
    }

    /// Set the advertised agent description (default `"A mock A2A agent"`).
    pub fn with_agent_description(mut self, description: impl Into<String>) -> Self {
        self.agent_description = description.into();
        self
    }

    /// Set the advertised agent version (default `"1.0.0"`).
    pub fn with_agent_version(mut self, version: impl Into<String>) -> Self {
        self.agent_version = version.into();
        self
    }

    /// Set the advertised agent URL. Defaults to `http://localhost<path>`.
    pub fn with_agent_url(mut self, url: impl Into<String>) -> Self {
        self.agent_url = Some(url.into());
        self
    }

    /// Set the agent-card discovery path (default `/.well-known/agent.json`).
    pub fn with_agent_card_path(mut self, path: impl Into<String>) -> Self {
        self.agent_card_path = path.into();
        self
    }

    /// Set the default task response text used by `tasks/send` / `tasks/get`.
    pub fn with_default_task_response(mut self, response: impl Into<String>) -> Self {
        self.default_task_response = response.into();
        self
    }

    /// Advertise and mock the A2A streaming capability. The agent card reports
    /// `capabilities.streaming: true` and the streaming method (default
    /// `message/stream`) returns an SSE stream of status/artifact update events.
    pub fn with_streaming(mut self) -> Self {
        self.streaming = true;
        self
    }

    /// Override the JSON-RPC method that triggers the streaming response (the
    /// A2A spec uses `message/stream`; the legacy name is
    /// `tasks/sendSubscribe`). Implies [`with_streaming`](Self::with_streaming).
    pub fn with_streaming_method(mut self, method: impl Into<String>) -> Self {
        self.streaming_method = method.into();
        self.streaming = true;
        self
    }

    /// Advertise and mock A2A push notifications. The agent card reports
    /// `capabilities.pushNotifications: true`, `tasks/pushNotificationConfig/set`
    /// echoes the registered config, and each `tasks/send` additionally POSTs
    /// the completed task to the supplied webhook URL while still returning the
    /// JSON-RPC task response to the caller.
    pub fn with_push_notifications(mut self, webhook_url: impl Into<String>) -> Self {
        self.push_notification_url = Some(webhook_url.into());
        self
    }

    /// Start defining a skill. Finish with [`A2aSkillBuilder::and`].
    pub fn with_skill(self, id: impl Into<String>) -> A2aSkillBuilder {
        A2aSkillBuilder {
            parent: self,
            skill: SkillDef {
                id: id.into(),
                name: None,
                description: None,
                tags: Vec::new(),
                examples: Vec::new(),
            },
        }
    }

    /// Start defining a custom `tasks/send` handler. Finish with
    /// [`A2aTaskHandlerBuilder::and`].
    pub fn on_task_send(self) -> A2aTaskHandlerBuilder {
        A2aTaskHandlerBuilder {
            parent: self,
            message_pattern: ".*".to_string(),
            response_text: "Task completed".to_string(),
            is_error: false,
        }
    }

    /// Build the full ordered list of A2A expectations.
    ///
    /// # Panics
    /// Panics if [`with_push_notifications`](Self::with_push_notifications) was
    /// given a webhook URL that cannot be parsed. Use [`try_build`](Self::try_build)
    /// for a fallible variant.
    pub fn build(&self) -> Vec<Value> {
        self.try_build().expect("invalid A2A mock configuration")
    }

    /// Fallible variant of [`build`](Self::build). Returns an error only when a
    /// configured push-notification webhook URL cannot be parsed.
    pub fn try_build(&self) -> Result<Vec<Value>> {
        let mut expectations = vec![self.build_agent_card()];

        for handler in &self.task_handlers {
            expectations.push(self.build_custom_task_handler(handler));
        }

        if self.streaming {
            expectations.push(self.build_streaming());
        }

        if let Some(url) = &self.push_notification_url {
            expectations.push(self.build_push_notification_config(url));
            expectations.push(self.build_push_notification_delivery(url)?);
        } else {
            expectations.push(self.build_tasks_send());
        }
        expectations.push(self.build_tasks_get());
        expectations.push(self.build_tasks_cancel());

        Ok(expectations)
    }

    /// Build and register the expectations on the given client.
    pub fn apply_to(&self, client: &MockServerClient) -> Result<Value> {
        client.upsert_raw(Value::Array(self.try_build()?))
    }

    // --- individual expectation builders -------------------------------

    fn build_agent_card(&self) -> Value {
        let mut skills_json = String::from("[");
        for (i, skill) in self.skills.iter().enumerate() {
            if i > 0 {
                skills_json.push_str(", ");
            }
            skills_json.push('{');
            skills_json.push_str(&format!("\"id\": \"{}\"", escape_json(&skill.id)));
            let name = skill.name.as_deref().unwrap_or(&skill.id);
            skills_json.push_str(&format!(", \"name\": \"{}\"", escape_json(name)));
            if let Some(description) = &skill.description {
                skills_json.push_str(&format!(", \"description\": \"{}\"", escape_json(description)));
            }
            if !skill.tags.is_empty() {
                skills_json.push_str(", \"tags\": [");
                for (j, tag) in skill.tags.iter().enumerate() {
                    if j > 0 {
                        skills_json.push_str(", ");
                    }
                    skills_json.push_str(&format!("\"{}\"", escape_json(tag)));
                }
                skills_json.push(']');
            }
            if !skill.examples.is_empty() {
                skills_json.push_str(", \"examples\": [");
                for (j, example) in skill.examples.iter().enumerate() {
                    if j > 0 {
                        skills_json.push_str(", ");
                    }
                    skills_json.push_str(&format!("\"{}\"", escape_json(example)));
                }
                skills_json.push(']');
            }
            skills_json.push('}');
        }
        skills_json.push(']');

        let default_url = format!("http://localhost{}", self.path);
        let url = self.agent_url.as_deref().unwrap_or(&default_url);

        let agent_card_json = format!(
            "{{\"name\": \"{}\", \"description\": \"{}\", \"version\": \"{}\", \"url\": \"{}\", \
\"capabilities\": {{\"streaming\": {}, \"pushNotifications\": {}, \"stateTransitionHistory\": false}}, \
\"skills\": {}}}",
            escape_json(&self.agent_name),
            escape_json(&self.agent_description),
            escape_json(&self.agent_version),
            escape_json(url),
            self.streaming,
            self.push_notification_url.is_some(),
            skills_json,
        );

        json!({
            "httpRequest": { "method": "GET", "path": self.agent_card_path },
            "httpResponse": {
                "statusCode": 200,
                "headers": [{ "name": "Content-Type", "values": ["application/json"] }],
                "body": agent_card_json
            }
        })
    }

    fn build_tasks_send(&self) -> Value {
        let result_json = build_task_result_json(&self.default_task_response, false);
        velocity_template_expectation(json_rpc_request(&self.path, "tasks/send"), &result_json)
    }

    fn build_tasks_get(&self) -> Value {
        let result_json = build_task_result_json(&self.default_task_response, false);
        velocity_template_expectation(json_rpc_request(&self.path, "tasks/get"), &result_json)
    }

    fn build_tasks_cancel(&self) -> Value {
        let result_json = "{\"id\": \"mock-task-id\", \"status\": {\"state\": \"canceled\"}}";
        velocity_template_expectation(json_rpc_request(&self.path, "tasks/cancel"), result_json)
    }

    fn build_streaming(&self) -> Value {
        let text = escape_json(&self.default_task_response);
        let task_id = "mock-task-id";

        // A2A streaming: each SSE event data is a JSON-RPC 2.0 response envelope
        // wrapping a TaskStatusUpdateEvent or TaskArtifactUpdateEvent. The
        // JSON-RPC id is not known at build time, so a stable placeholder ("1")
        // is used (streaming clients correlate by stream).
        let status_working = format!(
            "{{\"jsonrpc\": \"2.0\", \"id\": \"1\", \"result\": \
{{\"taskId\": \"{task_id}\", \"kind\": \"status-update\", \
\"status\": {{\"state\": \"working\"}}, \"final\": false}}}}"
        );
        let artifact_update = format!(
            "{{\"jsonrpc\": \"2.0\", \"id\": \"1\", \"result\": \
{{\"taskId\": \"{task_id}\", \"kind\": \"artifact-update\", \
\"artifact\": {{\"parts\": [{{\"type\": \"text\", \"text\": \"{text}\"}}]}}}}}}"
        );
        let status_completed = format!(
            "{{\"jsonrpc\": \"2.0\", \"id\": \"1\", \"result\": \
{{\"taskId\": \"{task_id}\", \"kind\": \"status-update\", \
\"status\": {{\"state\": \"completed\"}}, \"final\": true}}}}"
        );

        json!({
            "httpRequest": json_rpc_request(&self.path, &self.streaming_method),
            "httpSseResponse": {
                "statusCode": 200,
                "events": [
                    { "event": "message", "data": status_working },
                    { "event": "message", "data": artifact_update },
                    { "event": "message", "data": status_completed }
                ],
                "closeConnection": true
            }
        })
    }

    fn build_push_notification_config(&self, url: &str) -> Value {
        // Echo the registered push-notification config back as the JSON-RPC
        // result. The url is destined for a Velocity-templated body.
        let result_json = format!("{{\"url\": \"{}\"}}", ev_ej(url));
        velocity_template_expectation(
            json_rpc_request(&self.path, "tasks/pushNotificationConfig/set"),
            &result_json,
        )
    }

    fn build_push_notification_delivery(&self, url: &str) -> Result<Value> {
        // When push notifications are configured, a tasks/send both returns the
        // JSON-RPC task response to the caller AND POSTs the completed task to
        // the configured webhook URL. Modelled with an override-forwarded
        // request: the request override targets the webhook (literal body), and
        // a Velocity response *template* produces the caller's JSON-RPC
        // response so the request's id is echoed back.
        let target = WebhookTarget::parse(url)?;

        // Literal webhook POST body — no Velocity engine runs over a request
        // override, so only JSON escaping is applied. The push payload carries
        // no JSON-RPC id (server-initiated).
        let push_body = format!(
            "{{\"jsonrpc\": \"2.0\", \"result\": {}}}",
            build_task_result_json_raw(&self.default_task_response, false)
        );

        let webhook_request = json!({
            "method": "POST",
            "path": target.path,
            "secure": target.secure,
            "socketAddress": {
                "host": target.host,
                "port": target.port,
                "scheme": if target.secure { "HTTPS" } else { "HTTP" }
            },
            "headers": {
                "Host": [target.host_header()],
                "Content-Type": ["application/json"]
            },
            "body": push_body
        });

        // Caller response — a Velocity template so $!{request.jsonRpcRawId}
        // echoes the request id, matching the non-push tasks/send contract.
        let client_response_template = velocity_json_rpc_response(&build_task_result_json(
            &self.default_task_response,
            false,
        ));

        Ok(json!({
            "httpRequest": json_rpc_request(&self.path, "tasks/send"),
            "httpOverrideForwardedRequest": {
                "requestOverride": webhook_request,
                "responseTemplate": {
                    "template": client_response_template,
                    "templateType": "VELOCITY"
                }
            }
        }))
    }

    fn build_custom_task_handler(&self, handler: &TaskHandler) -> Value {
        let escaped_pattern = escape_message_pattern(&handler.message_pattern);
        let json_path = format!(
            "$[?(@.method == 'tasks/send' && @.params.message.parts[0].text =~ /{escaped_pattern}/)]"
        );
        let result_json = build_task_result_json(&handler.response_text, handler.is_error);
        velocity_template_expectation(json_path_request(&self.path, &json_path), &result_json)
    }
}

/// Escape a user-supplied message regular expression so it can be embedded as a
/// `/.../`-delimited regex literal inside the JsonPath filter without allowing a
/// breakout of the trailing `/` delimiter.
///
/// The pattern is documented as a regular expression, so existing escape
/// sequences (e.g. `\d`, `\/`, `\\`) are preserved verbatim. Only delimiter
/// breakout and control characters are neutralised:
/// - `\` followed by another char: emitted as-is (`\` + next char), preserving
///   the author's escape; a lone trailing `\` is doubled to `\\` so it cannot
///   escape the closing delimiter.
/// - bare `/`: escaped to `\/`.
/// - newline / carriage return: escaped to `\n` / `\r`.
/// - NUL: stripped.
/// - any other char: emitted verbatim (UTF-8 preserved).
fn escape_message_pattern(pattern: &str) -> String {
    let chars: Vec<char> = pattern.chars().collect();
    let mut out = String::with_capacity(pattern.len());
    let mut i = 0;
    while i < chars.len() {
        match chars[i] {
            '\\' => {
                if i + 1 < chars.len() {
                    out.push('\\');
                    out.push(chars[i + 1]);
                    i += 1;
                } else {
                    out.push_str("\\\\");
                }
            }
            '/' => out.push_str("\\/"),
            '\n' => out.push_str("\\n"),
            '\r' => out.push_str("\\r"),
            '\0' => {} // strip NUL
            c => out.push(c),
        }
        i += 1;
    }
    out
}

// ---------------------------------------------------------------------------
// Nested sub-builders
// ---------------------------------------------------------------------------

/// Sub-builder for a single A2A skill. Call [`and`](Self::and) to return to the
/// parent [`A2aMockBuilder`].
pub struct A2aSkillBuilder {
    parent: A2aMockBuilder,
    skill: SkillDef,
}

impl A2aSkillBuilder {
    /// Set the skill display name (defaults to the skill id).
    pub fn with_name(mut self, name: impl Into<String>) -> Self {
        self.skill.name = Some(name.into());
        self
    }

    /// Set the skill description.
    pub fn with_description(mut self, description: impl Into<String>) -> Self {
        self.skill.description = Some(description.into());
        self
    }

    /// Add a tag to the skill.
    pub fn with_tag(mut self, tag: impl Into<String>) -> Self {
        self.skill.tags.push(tag.into());
        self
    }

    /// Add an example to the skill.
    pub fn with_example(mut self, example: impl Into<String>) -> Self {
        self.skill.examples.push(example.into());
        self
    }

    /// Finish this skill and return to the parent builder.
    pub fn and(mut self) -> A2aMockBuilder {
        self.parent.skills.push(self.skill);
        self.parent
    }
}

/// Sub-builder for a single custom `tasks/send` handler. Call [`and`](Self::and)
/// to return to the parent [`A2aMockBuilder`].
pub struct A2aTaskHandlerBuilder {
    parent: A2aMockBuilder,
    message_pattern: String,
    response_text: String,
    is_error: bool,
}

impl A2aTaskHandlerBuilder {
    /// Set the regular expression matched against the first message part's text.
    pub fn matching_message(mut self, pattern: impl Into<String>) -> Self {
        self.message_pattern = pattern.into();
        self
    }

    /// Set the response text and whether this handler models a failed task.
    pub fn responding_with(mut self, text: impl Into<String>, is_error: bool) -> Self {
        self.response_text = text.into();
        self.is_error = is_error;
        self
    }

    /// Finish this handler and return to the parent builder.
    pub fn and(mut self) -> A2aMockBuilder {
        self.parent.task_handlers.push(TaskHandler {
            message_pattern: self.message_pattern,
            response_text: self.response_text,
            is_error: self.is_error,
        });
        self.parent
    }
}

/// Create a new A2A mock builder targeting `path` for the JSON-RPC task
/// endpoint.
pub fn a2a_mock(path: impl Into<String>) -> A2aMockBuilder {
    A2aMockBuilder::new(path)
}

/// Create a new A2A mock builder targeting the default `/a2a` path.
pub fn a2a_mock_default() -> A2aMockBuilder {
    A2aMockBuilder::new("/a2a")
}

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

#[cfg(test)]
mod tests {
    use super::*;

    fn agent_card_body(expectations: &[Value]) -> String {
        expectations[0]["httpResponse"]["body"]
            .as_str()
            .expect("agent card body string")
            .to_string()
    }

    #[test]
    fn minimal_mock_has_four_expectations() {
        // agent card + tasks/send + tasks/get + tasks/cancel
        let expectations = a2a_mock_default().build();
        assert_eq!(expectations.len(), 4);
    }

    #[test]
    fn custom_path_is_used_on_task_endpoint() {
        let expectations = a2a_mock("/custom/a2a").build();
        assert_eq!(expectations.len(), 4);
        // tasks/send is expectations[1] in the minimal layout.
        assert_eq!(expectations[1]["httpRequest"]["path"], "/custom/a2a");
        assert_eq!(
            expectations[1]["httpRequest"]["body"]["method"],
            "tasks/send"
        );
    }

    #[test]
    fn agent_card_is_static_response() {
        let expectations = a2a_mock_default()
            .with_agent_name("TestAgent")
            .with_agent_version("2.0.0")
            .build();
        let card = &expectations[0];
        assert_eq!(card["httpRequest"]["method"], "GET");
        assert_eq!(card["httpRequest"]["path"], "/.well-known/agent.json");
        assert_eq!(card["httpResponse"]["statusCode"], 200);
        let body = agent_card_body(&expectations);
        assert!(body.contains("TestAgent"), "{body}");
        assert!(body.contains("2.0.0"), "{body}");
    }

    #[test]
    fn custom_agent_card_path() {
        let expectations = a2a_mock_default().with_agent_card_path("/agent-card").build();
        assert_eq!(expectations[0]["httpRequest"]["path"], "/agent-card");
    }

    #[test]
    fn skills_appear_in_agent_card() {
        let expectations = a2a_mock_default()
            .with_skill("skill1")
            .with_name("Skill One")
            .with_description("First skill")
            .with_tag("test")
            .with_example("Do it")
            .and()
            .build();
        // skills do not add expectations
        assert_eq!(expectations.len(), 4);
        let body = agent_card_body(&expectations);
        assert!(body.contains("skill1"), "{body}");
        assert!(body.contains("Skill One"), "{body}");
        assert!(body.contains("First skill"), "{body}");
        assert!(body.contains("\"tags\": [\"test\"]"), "{body}");
        assert!(body.contains("\"examples\": [\"Do it\"]"), "{body}");
    }

    #[test]
    fn skill_name_defaults_to_id() {
        let expectations = a2a_mock_default().with_skill("only_id").and().build();
        let body = agent_card_body(&expectations);
        assert!(body.contains("\"id\": \"only_id\", \"name\": \"only_id\""), "{body}");
    }

    #[test]
    fn task_handlers_add_two_expectations_each_before_defaults() {
        let expectations = a2a_mock_default()
            .on_task_send()
            .matching_message("translate.*")
            .responding_with("Translation: Hola", false)
            .and()
            .on_task_send()
            .matching_message("summarize.*")
            .responding_with("Summary: Brief text", false)
            .and()
            .build();
        // agent card + 2 handlers + tasks/send + tasks/get + tasks/cancel
        assert_eq!(expectations.len(), 6);
        // custom handlers placed before the default tasks/send
        assert!(expectations[1]["httpRequest"]["body"]["jsonPath"]
            .as_str()
            .unwrap()
            .contains("translate.*"));
        assert!(expectations[1]["httpResponseTemplate"]["template"]
            .as_str()
            .unwrap()
            .contains("Translation: Hola"));
    }

    #[test]
    fn task_responses_use_velocity_templates() {
        let expectations = a2a_mock_default().build();
        let tasks_send = &expectations[1];
        assert_eq!(
            tasks_send["httpResponseTemplate"]["templateType"],
            "VELOCITY"
        );
        assert!(tasks_send["httpResponseTemplate"]["template"]
            .as_str()
            .unwrap()
            .contains("$!{request.jsonRpcRawId}"));
    }

    #[test]
    fn error_task_handler_uses_failed_state() {
        let expectations = a2a_mock_default()
            .on_task_send()
            .matching_message("bad_request.*")
            .responding_with("Error occurred", true)
            .and()
            .build();
        let template = expectations[1]["httpResponseTemplate"]["template"]
            .as_str()
            .unwrap();
        assert!(template.contains("failed"), "{template}");
    }

    #[test]
    fn default_task_response_appears_in_template() {
        let expectations = a2a_mock_default()
            .with_default_task_response("Custom default response")
            .build();
        let template = expectations[1]["httpResponseTemplate"]["template"]
            .as_str()
            .unwrap();
        assert!(template.contains("Custom default response"), "{template}");
    }

    #[test]
    fn velocity_metacharacters_escaped_in_default_response() {
        let expectations = a2a_mock_default()
            .with_default_task_response("$100 off #sale")
            .build();
        let template = expectations[1]["httpResponseTemplate"]["template"]
            .as_str()
            .unwrap();
        assert!(template.contains("${esc.d}100 off ${esc.h}sale"), "{template}");
    }

    #[test]
    fn backslash_before_hash_escaped_in_default_response() {
        let expectations = a2a_mock_default()
            .with_default_task_response(r"a\#b")
            .build();
        let template = expectations[1]["httpResponseTemplate"]["template"]
            .as_str()
            .unwrap();
        // JSON-escaped "a\\#b": both backslashes precede '#', so all are escaped
        assert!(template.contains("a${esc.b}${esc.b}${esc.h}b"), "{template}");
    }

    #[test]
    fn velocity_metacharacters_escaped_in_custom_handler() {
        let expectations = a2a_mock_default()
            .on_task_send()
            .matching_message("test.*")
            .responding_with("Price is $50 #discount", false)
            .and()
            .build();
        let template = expectations[1]["httpResponseTemplate"]["template"]
            .as_str()
            .unwrap();
        assert!(template.contains("${esc.d}50 ${esc.h}discount"), "{template}");
    }

    #[test]
    fn slash_escaped_in_handler_message_pattern() {
        let expectations = a2a_mock_default()
            .on_task_send()
            .matching_message("path/to/resource")
            .responding_with("found", false)
            .and()
            .build();
        let json_path = expectations[1]["httpRequest"]["body"]["jsonPath"]
            .as_str()
            .unwrap();
        assert!(json_path.contains("path\\/to\\/resource"), "{json_path}");
    }

    #[test]
    fn backslash_newline_and_cr_escaped_in_pattern() {
        let expectations = a2a_mock_default()
            .on_task_send()
            .matching_message("line1\nline2\\d+")
            .responding_with("found", false)
            .and()
            .build();
        let json_path = expectations[1]["httpRequest"]["body"]["jsonPath"]
            .as_str()
            .unwrap();
        assert!(json_path.contains("line1\\nline2\\d+"), "{json_path}");
        assert!(!json_path.contains('\n'), "must not contain literal newline");
    }

    #[test]
    fn null_byte_stripped_in_pattern() {
        let expectations = a2a_mock_default()
            .on_task_send()
            .matching_message("before\0after")
            .responding_with("found", false)
            .and()
            .build();
        let json_path = expectations[1]["httpRequest"]["body"]["jsonPath"]
            .as_str()
            .unwrap();
        assert!(json_path.contains("beforeafter"), "{json_path}");
        assert!(!json_path.contains('\0'));
    }

    fn handler_json_path(message_pattern: &str) -> String {
        let expectations = a2a_mock_default()
            .on_task_send()
            .matching_message(message_pattern)
            .responding_with("found", false)
            .and()
            .build();
        expectations[1]["httpRequest"]["body"]["jsonPath"]
            .as_str()
            .unwrap()
            .to_string()
    }

    #[test]
    fn regex_escape_sequence_is_preserved_not_doubled() {
        // `\d+` must stay `\d+` (single backslash) — preserving the author's
        // regex escape rather than doubling it to `\\d+`.
        assert_eq!(escape_message_pattern(r"\d+"), r"\d+");
        let json_path = handler_json_path(r"\d+");
        assert!(json_path.contains(r"\d+"), "{json_path}");
        assert!(!json_path.contains(r"\\d+"), "{json_path}");
    }

    #[test]
    fn already_escaped_slash_is_not_double_escaped() {
        // `a\/b` (an already-escaped slash) must stay `a\/b`, NOT become `a\\/b`.
        assert_eq!(escape_message_pattern(r"a\/b"), r"a\/b");
        let json_path = handler_json_path(r"a\/b");
        assert!(json_path.contains(r"a\/b"), "{json_path}");
        assert!(!json_path.contains(r"a\\/b"), "{json_path}");
    }

    #[test]
    fn trailing_backslash_cannot_break_out_of_regex_delimiter() {
        // A lone trailing backslash must be doubled so it escapes itself, NOT
        // the closing `/` delimiter. The produced jsonPath must still terminate
        // with an UNESCAPED `/)]`.
        assert_eq!(escape_message_pattern(r"abc\"), r"abc\\");
        let json_path = handler_json_path(r"abc\");
        assert!(json_path.contains(r"abc\\/)]"), "{json_path}");
        // Security assertion: the regex literal is properly closed — the
        // backslash is doubled and the delimiter `/)]` remains intact.
        assert!(json_path.ends_with(r"abc\\/)]"), "{json_path}");
    }

    #[test]
    fn normal_slash_pattern_still_escaped_as_before() {
        // Regression: a plain slash pattern keeps the existing behaviour.
        assert_eq!(
            escape_message_pattern("path/to/resource"),
            "path\\/to\\/resource"
        );
        let json_path = handler_json_path("path/to/resource");
        assert!(json_path.contains("path\\/to\\/resource"), "{json_path}");
    }

    #[test]
    fn streaming_advertised_and_generates_sse() {
        let expectations = a2a_mock_default()
            .with_streaming()
            .with_default_task_response("streamed result")
            .build();
        // agent card + tasks/send + streaming + tasks/get + tasks/cancel
        assert_eq!(expectations.len(), 5);

        let card = agent_card_body(&expectations);
        assert!(card.contains("\"streaming\": true"), "{card}");
        assert!(card.contains("\"pushNotifications\": false"), "{card}");

        let streaming = expectations
            .iter()
            .find(|e| e.get("httpSseResponse").is_some())
            .expect("expected an SSE expectation");
        let events = streaming["httpSseResponse"]["events"].as_array().unwrap();
        assert_eq!(events.len(), 3);
        assert_eq!(streaming["httpSseResponse"]["closeConnection"], true);

        let all_data: String = events
            .iter()
            .map(|e| e["data"].as_str().unwrap())
            .collect();
        assert!(all_data.contains("status-update"));
        assert!(all_data.contains("artifact-update"));
        assert!(all_data.contains("\"state\": \"working\""));
        assert!(all_data.contains("\"state\": \"completed\""));
        assert!(all_data.contains("\"final\": true"));
        assert!(all_data.contains("streamed result"));

        // streaming request matches the default streaming method
        assert_eq!(
            streaming["httpRequest"]["body"]["method"],
            "message/stream"
        );
    }

    #[test]
    fn custom_streaming_method() {
        let expectations = a2a_mock_default()
            .with_streaming_method("tasks/sendSubscribe")
            .build();
        let streaming = expectations
            .iter()
            .find(|e| e.get("httpSseResponse").is_some())
            .expect("expected an SSE expectation");
        assert_eq!(
            streaming["httpRequest"]["body"]["method"],
            "tasks/sendSubscribe"
        );
    }

    #[test]
    fn push_notifications_generate_config_and_delivery() {
        let expectations = a2a_mock_default()
            .with_push_notifications("http://localhost:1234/callback")
            .build();
        // agent card + pushConfig + tasks/send delivery + tasks/get + tasks/cancel
        assert_eq!(expectations.len(), 5);

        let card = agent_card_body(&expectations);
        assert!(card.contains("\"pushNotifications\": true"), "{card}");

        let mut has_config_echo = false;
        let mut has_forward_delivery = false;
        for expectation in &expectations {
            let method = expectation["httpRequest"]["body"]["method"]
                .as_str()
                .unwrap_or("");
            if method == "tasks/pushNotificationConfig/set" {
                has_config_echo = true;
            }
            if method == "tasks/send" && expectation.get("httpOverrideForwardedRequest").is_some() {
                has_forward_delivery = true;
                let webhook = &expectation["httpOverrideForwardedRequest"]["requestOverride"];
                assert_eq!(webhook["method"], "POST");
                assert_eq!(webhook["path"], "/callback");
                assert_eq!(webhook["socketAddress"]["host"], "localhost");
                assert_eq!(webhook["socketAddress"]["port"], 1234);
                assert_eq!(webhook["socketAddress"]["scheme"], "HTTP");
                assert_eq!(webhook["secure"], false);

                let response_template =
                    &expectation["httpOverrideForwardedRequest"]["responseTemplate"];
                assert_eq!(response_template["templateType"], "VELOCITY");
                assert!(response_template["template"]
                    .as_str()
                    .unwrap()
                    .contains("$!{request.jsonRpcRawId}"));
            }
        }
        assert!(has_config_echo, "expected push-notification config echo");
        assert!(has_forward_delivery, "expected push-notification delivery");
    }

    #[test]
    fn literal_webhook_push_body_not_velocity_escaped() {
        let expectations = a2a_mock_default()
            .with_push_notifications("http://localhost:1234/callback")
            .with_default_task_response("$100 off #sale")
            .build();
        let webhook = expectations
            .iter()
            .find_map(|e| e.get("httpOverrideForwardedRequest"))
            .map(|o| &o["requestOverride"])
            .expect("expected a webhook request override");
        let push_body = webhook["body"].as_str().unwrap();
        assert!(push_body.contains("$100 off #sale"), "{push_body}");
        assert!(!push_body.contains("esc.d"), "{push_body}");
        assert!(!push_body.contains("esc.h"), "{push_body}");
    }

    #[test]
    fn https_webhook_default_port() {
        let expectations = a2a_mock_default()
            .with_push_notifications("https://example.com/a2a/push")
            .build();
        let webhook = expectations
            .iter()
            .find_map(|e| e.get("httpOverrideForwardedRequest"))
            .map(|o| &o["requestOverride"])
            .expect("expected a webhook request override");
        assert_eq!(webhook["socketAddress"]["host"], "example.com");
        assert_eq!(webhook["socketAddress"]["port"], 443);
        assert_eq!(webhook["socketAddress"]["scheme"], "HTTPS");
        assert_eq!(webhook["secure"], true);
        assert_eq!(webhook["path"], "/a2a/push");
    }

    #[test]
    fn full_mock_layout() {
        let expectations = a2a_mock("/agent")
            .with_agent_name("FullAgent")
            .with_agent_description("A complete mock agent")
            .with_agent_version("3.0.0")
            .with_agent_url("http://localhost:8080/agent")
            .with_skill("translate")
            .with_name("Translation")
            .with_description("Translates text")
            .with_tag("i18n")
            .with_example("Translate hello to French")
            .and()
            .with_default_task_response("Default done")
            .on_task_send()
            .matching_message("translate.*")
            .responding_with("Bonjour", false)
            .and()
            .build();
        // agent card + 1 handler + tasks/send + tasks/get + tasks/cancel
        assert_eq!(expectations.len(), 5);
        let card = agent_card_body(&expectations);
        assert!(card.contains("FullAgent"));
        assert!(card.contains("http://localhost:8080/agent"));
        assert!(card.contains("translate"));
    }

    #[test]
    fn agent_card_body_is_valid_json() {
        let expectations = a2a_mock_default()
            .with_agent_name("Quote\"Agent")
            .with_skill("s1")
            .with_name("Name/with\\chars")
            .and()
            .build();
        let body = agent_card_body(&expectations);
        let parsed: Value = serde_json::from_str(&body).expect("agent card must be valid JSON");
        assert_eq!(parsed["name"], "Quote\"Agent");
        assert_eq!(parsed["capabilities"]["stateTransitionHistory"], false);
    }
}
