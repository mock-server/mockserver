//! The MockServer client and its builder.

use base64::{engine::general_purpose::STANDARD as BASE64, Engine};
use percent_encoding::{utf8_percent_encode, AsciiSet, CONTROLS};
use reqwest::blocking::Client;
use serde::Serialize;
use serde_json::Value;

/// Characters percent-encoded when interpolating a scenario name into a path
/// segment — everything unsafe for a single segment (matches the other clients'
/// path-escaping). Unreserved characters (`-` `_` `.` `~`) are left intact.
const SCENARIO_NAME: &AsciiSet = &CONTROLS
    .add(b' ')
    .add(b'/')
    .add(b'?')
    .add(b'#')
    .add(b'%')
    .add(b'[')
    .add(b']')
    .add(b'{')
    .add(b'}')
    .add(b'|')
    .add(b'\\')
    .add(b'^')
    .add(b'"')
    .add(b'<')
    .add(b'>')
    .add(b'`');

/// Build the control-plane path for a named scenario with the name
/// percent-encoded as a single path segment (matching the other MockServer clients).
fn scenario_path(name: &str) -> String {
    format!(
        "/mockserver/scenario/{}",
        utf8_percent_encode(name, SCENARIO_NAME)
    )
}

use crate::breakpoint::{
    BreakpointMatcherList, BreakpointMatcherRegistration, BreakpointMatcherResponse,
    BreakpointRequestHandler, BreakpointResponseHandler, BreakpointStreamFrameHandler,
    BreakpointWebSocketClient,
};
use crate::diagnostics::{
    ExplainUnmatchedOptions, MismatchReport, RequestExplanation, UnmatchedExplanation,
};
use crate::error::{Error, Result};
use crate::model::*;

// ---------------------------------------------------------------------------
// ClientBuilder
// ---------------------------------------------------------------------------

/// Builder for constructing a [`MockServerClient`].
///
/// # Example
/// ```no_run
/// use mockserver_client::ClientBuilder;
///
/// let client = ClientBuilder::new("localhost", 1080)
///     .context_path("/api")
///     .secure(true)
///     .build()
///     .unwrap();
/// ```
pub struct ClientBuilder {
    host: String,
    port: u16,
    context_path: String,
    secure: bool,
    tls_verify: bool,
    control_plane_bearer_token: Option<String>,
    ca_cert_pem: Option<Vec<u8>>,
    /// Client identity for mTLS as `(certificate PEM, private key PEM)`.
    client_identity_pem: Option<(Vec<u8>, Vec<u8>)>,
}

impl ClientBuilder {
    /// Create a new builder targeting the given host and port.
    pub fn new(host: impl Into<String>, port: u16) -> Self {
        Self {
            host: host.into(),
            port,
            context_path: String::new(),
            secure: false,
            tls_verify: true,
            control_plane_bearer_token: None,
            ca_cert_pem: None,
            client_identity_pem: None,
        }
    }

    /// Set a context path prefix (e.g., "/mockserver" if deployed behind a reverse proxy).
    pub fn context_path(mut self, path: impl Into<String>) -> Self {
        self.context_path = path.into();
        self
    }

    /// Use HTTPS instead of HTTP.
    pub fn secure(mut self, secure: bool) -> Self {
        self.secure = secure;
        self
    }

    /// Whether to verify TLS certificates (default: true).
    pub fn tls_verify(mut self, verify: bool) -> Self {
        self.tls_verify = verify;
        self
    }

    /// Attach an `Authorization: Bearer <token>` header to **every control-plane
    /// request** the built client sends, including the callback WebSocket upgrade
    /// used by breakpoints and callbacks.
    ///
    /// Use this when the server requires a JWT on the control plane
    /// (`mockserver.controlPlaneJWTAuthenticationRequired=true`). The client does
    /// not generate the token — supply the JWT string here. The header is only
    /// sent on control-plane (`/mockserver/*`) requests issued by this client; it
    /// is not added to any proxied/data-plane traffic.
    ///
    /// # Example
    /// ```no_run
    /// use mockserver_client::ClientBuilder;
    ///
    /// let client = ClientBuilder::new("localhost", 1080)
    ///     .secure(true)
    ///     .control_plane_bearer_token("eyJhbGciOi...")
    ///     .build()
    ///     .unwrap();
    /// ```
    pub fn control_plane_bearer_token(mut self, token: impl Into<String>) -> Self {
        self.control_plane_bearer_token = Some(token.into());
        self
    }

    /// Trust the given CA certificate (PEM file path) when connecting over HTTPS.
    ///
    /// Reads the PEM file and adds it as an additional trusted root so a
    /// MockServer HTTPS certificate issued by that CA validates. Compose with
    /// [`secure(true)`](Self::secure). The CA is added to — not a replacement for
    /// — the platform's default trust store.
    ///
    /// Errors from reading the file surface when [`build`](Self::build) is called.
    pub fn ca_cert_pem_path(mut self, path: impl AsRef<std::path::Path>) -> Self {
        self.ca_cert_pem = std::fs::read(path.as_ref()).ok();
        // Defer error reporting to build(); but if the read failed we still want
        // build() to fail loudly rather than silently ignore the CA, so record a
        // sentinel empty Vec which Certificate::from_pem will reject.
        if self.ca_cert_pem.is_none() {
            self.ca_cert_pem = Some(Vec::new());
        }
        self
    }

    /// Trust the given CA certificate (PEM bytes) when connecting over HTTPS.
    ///
    /// In-memory counterpart to [`ca_cert_pem_path`](Self::ca_cert_pem_path).
    pub fn ca_cert_pem(mut self, bytes: impl Into<Vec<u8>>) -> Self {
        self.ca_cert_pem = Some(bytes.into());
        self
    }

    /// Present a client certificate + private key (PEM) for mutual TLS (mTLS).
    ///
    /// Reads the certificate and PKCS#8 private key PEM files and configures them
    /// as the client identity used in the TLS handshake — required when the
    /// server enforces `mockserver.controlPlaneTLSMutualAuthenticationRequired`.
    ///
    /// Errors from reading either file, or from building the identity, surface
    /// when [`build`](Self::build) is called.
    pub fn client_cert_pem(
        mut self,
        cert_path: impl AsRef<std::path::Path>,
        key_path: impl AsRef<std::path::Path>,
    ) -> Self {
        match (
            std::fs::read(cert_path.as_ref()),
            std::fs::read(key_path.as_ref()),
        ) {
            (Ok(cert), Ok(key)) => self.client_identity_pem = Some((cert, key)),
            // Record a sentinel empty buffer so build() fails loudly rather than
            // silently dropping the requested client certificate.
            _ => self.client_identity_pem = Some((Vec::new(), Vec::new())),
        }
        self
    }

    /// Build the client.
    pub fn build(self) -> Result<MockServerClient> {
        let scheme = if self.secure { "https" } else { "http" };
        let ctx = if self.context_path.is_empty() {
            String::new()
        } else if self.context_path.starts_with('/') {
            self.context_path
        } else {
            format!("/{}", self.context_path)
        };
        let base_url = format!("{scheme}://{}:{}{ctx}", self.host, self.port);

        let mut builder = Client::builder().danger_accept_invalid_certs(!self.tls_verify);

        // Attach the control-plane bearer token as a default header so it rides
        // on every control-plane request this client issues (this client only
        // ever talks to the `/mockserver/*` control plane).
        if let Some(token) = &self.control_plane_bearer_token {
            let mut headers = reqwest::header::HeaderMap::new();
            let mut value = reqwest::header::HeaderValue::from_str(&format!("Bearer {token}"))
                .map_err(|e| Error::InvalidRequest(format!("invalid bearer token: {e}")))?;
            value.set_sensitive(true);
            headers.insert(reqwest::header::AUTHORIZATION, value);
            builder = builder.default_headers(headers);
        }

        if let Some(ca) = &self.ca_cert_pem {
            let cert = reqwest::Certificate::from_pem(ca)?;
            builder = builder.add_root_certificate(cert);
        }

        if let Some((cert_pem, key_pem)) = &self.client_identity_pem {
            let identity = reqwest::Identity::from_pkcs8_pem(cert_pem, key_pem)?;
            builder = builder.identity(identity);
        }

        let http_client = builder.build()?;
        let ws_tls = if self.secure {
            Some(ws_tls_connector(
                self.tls_verify,
                self.ca_cert_pem.as_deref(),
                self.client_identity_pem.as_ref(),
            )?)
        } else {
            None
        };

        Ok(MockServerClient {
            base_url,
            http: http_client,
            control_plane_bearer_token: self.control_plane_bearer_token,
            ws_tls,
            breakpoint_ws: std::sync::Mutex::new(None),
        })
    }
}

/// The callback WebSocket's TLS connector, configured like the reqwest HTTPS client.
fn ws_tls_connector(
    tls_verify: bool,
    ca_cert_pem: Option<&[u8]>,
    client_identity_pem: Option<&(Vec<u8>, Vec<u8>)>,
) -> Result<native_tls::TlsConnector> {
    let tls_error = |e: native_tls::Error| Error::InvalidRequest(format!("WebSocket TLS setup failed: {e}"));
    let mut builder = native_tls::TlsConnector::builder();
    builder.danger_accept_invalid_certs(!tls_verify);
    if let Some(ca) = ca_cert_pem {
        builder.add_root_certificate(native_tls::Certificate::from_pem(ca).map_err(tls_error)?);
    }
    if let Some((cert_pem, key_pem)) = client_identity_pem {
        builder.identity(native_tls::Identity::from_pkcs8(cert_pem, key_pem).map_err(tls_error)?);
    }
    builder.build().map_err(tls_error)
}

// ---------------------------------------------------------------------------
// MockServerClient
// ---------------------------------------------------------------------------

/// A blocking client for the MockServer control-plane REST API.
///
/// Created via [`ClientBuilder`]. All methods are synchronous.
pub struct MockServerClient {
    pub(crate) base_url: String,
    http: Client,
    control_plane_bearer_token: Option<String>,
    ws_tls: Option<native_tls::TlsConnector>,
    breakpoint_ws: std::sync::Mutex<Option<BreakpointWebSocketClient>>,
}

impl MockServerClient {
    // ------------------------------------------------------------------
    // Expectation creation
    // ------------------------------------------------------------------

    /// Create one or more expectations on the server.
    ///
    /// Returns the created expectations as echoed by the server.
    pub fn upsert(&self, expectations: &[Expectation]) -> Result<Vec<Expectation>> {
        let body = serde_json::to_value(expectations)?;
        let resp = self
            .http
            .put(self.url("/mockserver/expectation"))
            .json(&body)
            .send()?;

        let status = resp.status().as_u16();
        match status {
            200 | 201 => {
                let text = resp.text()?;
                if text.is_empty() {
                    Ok(expectations.to_vec())
                } else {
                    Ok(serde_json::from_str(&text)?)
                }
            }
            400 => Err(Error::InvalidRequest(resp.text()?)),
            _ => Err(Error::UnexpectedStatus {
                status,
                body: resp.text().unwrap_or_default(),
            }),
        }
    }

    /// Create one or more expectations from raw JSON values.
    ///
    /// This is the lower-level counterpart to [`upsert`](Self::upsert) for
    /// expectation shapes that the typed [`Expectation`] model does not (yet)
    /// cover — notably the `httpLlmResponse` action and conversation scenario
    /// fields produced by the [`crate::llm`] builders, and the Velocity/JSON-RPC
    /// expectations produced by the [`crate::mcp`] builder.
    ///
    /// The `expectations` value should be a JSON object (single expectation) or
    /// a JSON array of expectation objects. Returns the raw JSON the server
    /// echoes back (or the submitted value if the server returns an empty body).
    pub fn upsert_raw(&self, expectations: Value) -> Result<Value> {
        let resp = self
            .http
            .put(self.url("/mockserver/expectation"))
            .json(&expectations)
            .send()?;

        let status = resp.status().as_u16();
        match status {
            200 | 201 => {
                let text = resp.text()?;
                if text.is_empty() {
                    Ok(expectations)
                } else {
                    Ok(serde_json::from_str(&text)?)
                }
            }
            400 => Err(Error::InvalidRequest(resp.text()?)),
            _ => Err(Error::UnexpectedStatus {
                status,
                body: resp.text().unwrap_or_default(),
            }),
        }
    }

    // ------------------------------------------------------------------
    // OpenAPI import
    // ------------------------------------------------------------------

    /// Register expectations from an OpenAPI/Swagger specification.
    ///
    /// Sends a `PUT /mockserver/openapi` with the given [`OpenApiExpectation`].
    /// MockServer parses the spec and creates request matchers and example
    /// responses for the selected operations (or every operation when none are
    /// specified). Returns the created expectations as echoed by the server.
    ///
    /// # Example
    /// ```no_run
    /// use mockserver_client::{ClientBuilder, OpenApiExpectation};
    ///
    /// let client = ClientBuilder::new("localhost", 1080).build().unwrap();
    /// client.openapi(
    ///     &OpenApiExpectation::new("https://example.com/petstore.yaml")
    ///         .operation("listPets", "200"),
    /// ).unwrap();
    /// ```
    pub fn openapi(&self, expectation: &OpenApiExpectation) -> Result<Vec<Expectation>> {
        let resp = self
            .http
            .put(self.url("/mockserver/openapi"))
            .json(expectation)
            .send()?;

        let status = resp.status().as_u16();
        match status {
            200 | 201 => {
                let text = resp.text()?;
                if text.is_empty() {
                    Ok(vec![])
                } else {
                    Ok(serde_json::from_str(&text)?)
                }
            }
            400 => Err(Error::InvalidRequest(resp.text()?)),
            _ => Err(Error::UnexpectedStatus {
                status,
                body: resp.text().unwrap_or_default(),
            }),
        }
    }

    // ------------------------------------------------------------------
    // Fluent API entry point
    // ------------------------------------------------------------------

    /// Begin building an expectation with the fluent `when(...).respond(...)` API.
    ///
    /// # Example
    /// ```no_run
    /// use mockserver_client::{ClientBuilder, HttpRequest, HttpResponse};
    ///
    /// let client = ClientBuilder::new("localhost", 1080).build().unwrap();
    /// client.when(HttpRequest::new().method("GET").path("/foo"))
    ///     .respond(HttpResponse::new().status_code(200).body("bar"))
    ///     .unwrap();
    /// ```
    pub fn when(&self, request: HttpRequest) -> ForwardChainExpectation<'_> {
        ForwardChainExpectation {
            client: self,
            request,
            times: None,
            time_to_live: None,
            priority: None,
            id: None,
        }
    }

    // ------------------------------------------------------------------
    // Verify
    // ------------------------------------------------------------------

    /// Verify that a request was received the specified number of times.
    ///
    /// Returns `Ok(())` if verification passes, or
    /// `Err(Error::VerificationFailure)` with the server's failure message.
    pub fn verify(&self, request: HttpRequest, times: VerificationTimes) -> Result<()> {
        let verification = Verification {
            http_request: Some(request),
            http_response: None,
            times: Some(times),
            maximum_number_of_request_to_return_in_verification_failure: None,
        };
        self.do_verify(&verification)
    }

    /// Verify that a request/response pair was received the specified number of times.
    ///
    /// Both the request matcher and the response matcher must match for a
    /// recorded exchange to count. The response matcher uses the same
    /// [`HttpResponse`] type as expectations — the server matches against the
    /// recorded response's status code, headers, and body.
    pub fn verify_request_and_response(
        &self,
        request: HttpRequest,
        response: HttpResponse,
        times: VerificationTimes,
    ) -> Result<()> {
        let verification = Verification {
            http_request: Some(request),
            http_response: Some(response),
            times: Some(times),
            maximum_number_of_request_to_return_in_verification_failure: None,
        };
        self.do_verify(&verification)
    }

    /// Verify that a response (regardless of request) was returned the
    /// specified number of times.
    ///
    /// The `httpRequest` field is omitted from the JSON so the server matches
    /// any request.
    pub fn verify_response(&self, response: HttpResponse, times: VerificationTimes) -> Result<()> {
        let verification = Verification {
            http_request: None,
            http_response: Some(response),
            times: Some(times),
            maximum_number_of_request_to_return_in_verification_failure: None,
        };
        self.do_verify(&verification)
    }

    /// Verify that no requests at all were received by the server.
    ///
    /// Thin wrapper over [`verify`](Self::verify): matches any request (an empty
    /// matcher) with `exactly(0)` times. Returns `Ok(())` if the server received
    /// no requests, or `Err(Error::VerificationFailure)` otherwise.
    pub fn verify_zero_interactions(&self) -> Result<()> {
        self.verify(HttpRequest::new(), VerificationTimes::exactly(0))
    }

    /// Send a fully constructed [`Verification`] to the server.
    ///
    /// This is the most flexible form — callers can set every field,
    /// including `maximum_number_of_request_to_return_in_verification_failure`.
    pub fn verify_raw(&self, verification: &Verification) -> Result<()> {
        self.do_verify(verification)
    }

    /// Verify that requests were received in the given order.
    pub fn verify_sequence(&self, requests: Vec<HttpRequest>) -> Result<()> {
        let verification = VerificationSequence {
            http_requests: Some(requests),
            http_responses: None,
        };
        self.do_verify_sequence(&verification)
    }

    /// Verify that request/response pairs were received in the given order.
    ///
    /// `responses` is index-aligned with `requests` — each entry constrains
    /// the response that must have been returned for the corresponding request.
    pub fn verify_sequence_with_responses(
        &self,
        requests: Vec<HttpRequest>,
        responses: Vec<HttpResponse>,
    ) -> Result<()> {
        let verification = VerificationSequence {
            http_requests: Some(requests),
            http_responses: Some(responses),
        };
        self.do_verify_sequence(&verification)
    }

    /// Send a fully constructed [`VerificationSequence`] to the server.
    pub fn verify_sequence_raw(&self, verification: &VerificationSequence) -> Result<()> {
        self.do_verify_sequence(verification)
    }

    // ------------------------------------------------------------------
    // Clear / Reset
    // ------------------------------------------------------------------

    /// Clear expectations and/or logs matching the given request.
    ///
    /// If `request` is `None`, clears everything of the specified type.
    pub fn clear(
        &self,
        request: Option<&HttpRequest>,
        clear_type: Option<ClearType>,
    ) -> Result<()> {
        let mut url = self.url("/mockserver/clear");
        if let Some(ct) = clear_type {
            url = format!("{url}?type={}", ct.as_str());
        }

        let mut builder = self.http.put(&url);
        builder = builder.header("Content-Type", "application/json");
        if let Some(req) = request {
            builder = builder.json(req);
        } else {
            builder = builder.body("");
        }

        let resp = builder.send()?;
        let status = resp.status().as_u16();
        match status {
            200 => Ok(()),
            400 => Err(Error::InvalidRequest(resp.text()?)),
            _ => Err(Error::UnexpectedStatus {
                status,
                body: resp.text().unwrap_or_default(),
            }),
        }
    }

    /// Clear expectations by expectation ID.
    pub fn clear_by_id(
        &self,
        expectation_id: impl Into<String>,
        clear_type: Option<ClearType>,
    ) -> Result<()> {
        let mut url = self.url("/mockserver/clear");
        if let Some(ct) = clear_type {
            url = format!("{url}?type={}", ct.as_str());
        }

        let body = serde_json::json!({ "id": expectation_id.into() });
        let resp = self.http.put(&url).json(&body).send()?;

        let status = resp.status().as_u16();
        match status {
            200 => Ok(()),
            400 => Err(Error::InvalidRequest(resp.text()?)),
            _ => Err(Error::UnexpectedStatus {
                status,
                body: resp.text().unwrap_or_default(),
            }),
        }
    }

    /// Reset all expectations and recorded requests.
    pub fn reset(&self) -> Result<()> {
        let resp = self
            .http
            .put(self.url("/mockserver/reset"))
            .header("Content-Type", "application/json")
            .body("")
            .send()?;

        let status = resp.status().as_u16();
        match status {
            200 => Ok(()),
            _ => Err(Error::UnexpectedStatus {
                status,
                body: resp.text().unwrap_or_default(),
            }),
        }
    }

    // ------------------------------------------------------------------
    // Retrieve
    // ------------------------------------------------------------------

    /// Retrieve recorded requests matching the optional filter.
    pub fn retrieve_recorded_requests(
        &self,
        request: Option<&HttpRequest>,
    ) -> Result<Vec<HttpRequest>> {
        let text = self.do_retrieve(request, RetrieveType::Requests, RetrieveFormat::Json)?;
        if text.is_empty() {
            return Ok(vec![]);
        }
        Ok(serde_json::from_str(&text)?)
    }

    /// Retrieve active expectations matching the optional filter.
    pub fn retrieve_active_expectations(
        &self,
        request: Option<&HttpRequest>,
    ) -> Result<Vec<Expectation>> {
        let text = self.do_retrieve(
            request,
            RetrieveType::ActiveExpectations,
            RetrieveFormat::Json,
        )?;
        if text.is_empty() {
            return Ok(vec![]);
        }
        Ok(serde_json::from_str(&text)?)
    }

    /// Retrieve recorded expectations matching the optional filter.
    pub fn retrieve_recorded_expectations(
        &self,
        request: Option<&HttpRequest>,
    ) -> Result<Vec<Expectation>> {
        let text = self.do_retrieve(
            request,
            RetrieveType::RecordedExpectations,
            RetrieveFormat::Json,
        )?;
        if text.is_empty() {
            return Ok(vec![]);
        }
        Ok(serde_json::from_str(&text)?)
    }

    /// Retrieve the active expectations as MockServer SDK setup code (the
    /// builder code that recreates the expectations) in the requested language.
    ///
    /// `format` must be one of the code-generation variants of
    /// [`RetrieveFormat`] (e.g. [`RetrieveFormat::Java`],
    /// [`RetrieveFormat::Rust`]). The generated code is returned as a string.
    pub fn retrieve_expectations_as_code(
        &self,
        format: RetrieveFormat,
        request: Option<&HttpRequest>,
    ) -> Result<String> {
        self.do_retrieve(request, RetrieveType::ActiveExpectations, format)
    }

    /// Retrieve the recorded (proxied) request/response pairs as MockServer SDK
    /// setup code in the requested language.
    ///
    /// `format` must be one of the code-generation variants of
    /// [`RetrieveFormat`]. The generated code is returned as a string.
    pub fn retrieve_recorded_expectations_as_code(
        &self,
        format: RetrieveFormat,
        request: Option<&HttpRequest>,
    ) -> Result<String> {
        self.do_retrieve(request, RetrieveType::RecordedExpectations, format)
    }

    /// Retrieve log messages matching the optional filter.
    pub fn retrieve_log_messages(&self, request: Option<&HttpRequest>) -> Result<Vec<String>> {
        let text = self.do_retrieve(request, RetrieveType::Logs, RetrieveFormat::LogEntries)?;
        if text.is_empty() {
            return Ok(vec![]);
        }
        // Log messages may be returned as a JSON array of strings or as a
        // separator-delimited block. Try JSON first.
        if let Ok(arr) = serde_json::from_str::<Vec<String>>(&text) {
            return Ok(arr);
        }
        // Fall back to splitting on the separator used by MockServer.
        Ok(text
            .split("------------------------------------\n")
            .map(|s| s.to_string())
            .filter(|s| !s.is_empty())
            .collect())
    }

    /// Retrieve recorded request/response pairs.
    pub fn retrieve_request_responses(&self, request: Option<&HttpRequest>) -> Result<Vec<Value>> {
        let text = self.do_retrieve(
            request,
            RetrieveType::RequestResponses,
            RetrieveFormat::Json,
        )?;
        if text.is_empty() {
            return Ok(vec![]);
        }
        Ok(serde_json::from_str(&text)?)
    }

    /// Retrieve the active expectation with the given expectation id.
    ///
    /// Unlike [`Self::retrieve_active_expectations`] this returns only the expectation
    /// with that id, never other expectations whose matcher happens to match the same
    /// request. An unknown id is rejected with [`Error::InvalidRequest`].
    pub fn retrieve_active_expectations_by_id(
        &self,
        expectation_id: &str,
    ) -> Result<Vec<Expectation>> {
        let text = self.do_retrieve_by_id(
            expectation_id,
            RetrieveType::ActiveExpectations,
            RetrieveFormat::Json,
        )?;
        if text.is_empty() {
            return Ok(vec![]);
        }
        Ok(serde_json::from_str(&text)?)
    }

    /// Retrieve the recorded requests matching the request of the expectation with the
    /// given expectation id.
    pub fn retrieve_recorded_requests_by_id(
        &self,
        expectation_id: &str,
    ) -> Result<Vec<HttpRequest>> {
        let text = self.do_retrieve_by_id(
            expectation_id,
            RetrieveType::Requests,
            RetrieveFormat::Json,
        )?;
        if text.is_empty() {
            return Ok(vec![]);
        }
        Ok(serde_json::from_str(&text)?)
    }

    /// Retrieve the recorded expectations matching the request of the expectation with
    /// the given expectation id.
    pub fn retrieve_recorded_expectations_by_id(
        &self,
        expectation_id: &str,
    ) -> Result<Vec<Expectation>> {
        let text = self.do_retrieve_by_id(
            expectation_id,
            RetrieveType::RecordedExpectations,
            RetrieveFormat::Json,
        )?;
        if text.is_empty() {
            return Ok(vec![]);
        }
        Ok(serde_json::from_str(&text)?)
    }

    /// Retrieve the recorded request/response pairs matching the request of the
    /// expectation with the given expectation id.
    pub fn retrieve_request_responses_by_id(&self, expectation_id: &str) -> Result<Vec<Value>> {
        let text = self.do_retrieve_by_id(
            expectation_id,
            RetrieveType::RequestResponses,
            RetrieveFormat::Json,
        )?;
        if text.is_empty() {
            return Ok(vec![]);
        }
        Ok(serde_json::from_str(&text)?)
    }

    /// Retrieve the log messages matching the request of the expectation with the given
    /// expectation id.
    pub fn retrieve_log_messages_by_id(&self, expectation_id: &str) -> Result<Vec<String>> {
        let text = self.do_retrieve_by_id(
            expectation_id,
            RetrieveType::Logs,
            RetrieveFormat::LogEntries,
        )?;
        if text.is_empty() {
            return Ok(vec![]);
        }
        if let Ok(arr) = serde_json::from_str::<Vec<String>>(&text) {
            return Ok(arr);
        }
        Ok(text
            .split("------------------------------------\n")
            .map(|s| s.to_string())
            .filter(|s| !s.is_empty())
            .collect())
    }

    // ------------------------------------------------------------------
    // Status / Bind
    // ------------------------------------------------------------------

    /// Query the server's listening ports.
    pub fn status(&self) -> Result<Ports> {
        let resp = self
            .http
            .put(self.url("/mockserver/status"))
            .header("Content-Type", "application/json")
            .body("")
            .send()?;

        let status = resp.status().as_u16();
        match status {
            200 => {
                let text = resp.text()?;
                Ok(serde_json::from_str(&text)?)
            }
            _ => Err(Error::UnexpectedStatus {
                status,
                body: resp.text().unwrap_or_default(),
            }),
        }
    }

    /// Bind additional listening ports.
    pub fn bind(&self, ports: &[u16]) -> Result<Ports> {
        let body = Ports {
            ports: ports.to_vec(),
        };
        let resp = self
            .http
            .put(self.url("/mockserver/bind"))
            .json(&body)
            .send()?;

        let status = resp.status().as_u16();
        match status {
            200 => {
                let text = resp.text()?;
                Ok(serde_json::from_str(&text)?)
            }
            400 => Err(Error::InvalidRequest(resp.text()?)),
            406 => Err(Error::VerificationFailure(resp.text()?)),
            _ => Err(Error::UnexpectedStatus {
                status,
                body: resp.text().unwrap_or_default(),
            }),
        }
    }

    /// Check if the MockServer has started (polls with retries).
    pub fn has_started(&self, attempts: u32, timeout_ms: u64) -> bool {
        for i in 0..attempts {
            match self.status() {
                Ok(_) => return true,
                Err(_) => {
                    if i < attempts - 1 {
                        std::thread::sleep(std::time::Duration::from_millis(timeout_ms));
                    }
                }
            }
        }
        false
    }

    // ------------------------------------------------------------------
    // Matching diagnostics
    // ------------------------------------------------------------------

    /// Explain how a recorded request would be served now.
    ///
    /// Re-checks the request whose log entries carry `correlation_id` against
    /// the current expectations, in the order MockServer evaluates them, and
    /// returns the expectation that would serve it, why each one ahead of it
    /// was passed over, and which lower-priority ones it shadows. Sends
    /// `PUT /mockserver/explain?correlationId=<id>`; read-only on the server.
    ///
    /// A blank id is rejected with [`Error::InvalidRequest`] before anything
    /// is sent. An id that is not in the event log (never received, cleared or
    /// evicted) is [`Error::NotFound`]; a recorded request that is not an HTTP
    /// request is [`Error::InvalidRequest`].
    ///
    /// ```no_run
    /// # let client = mockserver_client::ClientBuilder::new("localhost", 1080).build().unwrap();
    /// let explanation = client.explain("abc-123-def-456").unwrap();
    /// for skipped in &explanation.skipped_expectations {
    ///     println!("{:?} {:?}: {:?}", skipped.expectation_id, skipped.reason, skipped.explanation);
    /// }
    /// ```
    pub fn explain(&self, correlation_id: &str) -> Result<RequestExplanation> {
        if correlation_id.trim().is_empty() {
            return Err(Error::InvalidRequest(
                "explain requires a non-blank correlation id".into(),
            ));
        }
        let resp = self
            .http
            .put(self.url("/mockserver/explain"))
            .query(&[("correlationId", correlation_id)])
            .send()?;
        self.diagnostic_response(resp)
    }

    /// Evaluate a request against every active expectation and report, for
    /// each, whether it matched and which fields differ, plus the closest
    /// match. Sends `PUT /mockserver/debugMismatch` with the request as the
    /// body.
    pub fn debug_mismatch(&self, request: HttpRequest) -> Result<MismatchReport> {
        let resp = self
            .http
            .put(self.url("/mockserver/debugMismatch"))
            .json(&request)
            .send()?;
        self.diagnostic_response(resp)
    }

    /// Re-check recent requests that matched no expectation against the
    /// current expectations and rank the closest ones, optionally with a
    /// suggested JSON patch for each. Sends `PUT /mockserver/explainUnmatched`;
    /// `None` sends no body (server defaults).
    pub fn explain_unmatched(
        &self,
        options: Option<&ExplainUnmatchedOptions>,
    ) -> Result<UnmatchedExplanation> {
        let builder = self.http.put(self.url("/mockserver/explainUnmatched"));
        let resp = match options {
            Some(options) => builder.json(options),
            None => builder,
        }
        .send()?;
        self.diagnostic_response(resp)
    }

    fn diagnostic_response<T: serde::de::DeserializeOwned>(
        &self,
        resp: reqwest::blocking::Response,
    ) -> Result<T> {
        let status = resp.status().as_u16();
        match status {
            200 => Ok(serde_json::from_str(&resp.text()?)?),
            400 => Err(Error::InvalidRequest(resp.text()?)),
            404 => Err(Error::NotFound(resp.text()?)),
            _ => Err(Error::UnexpectedStatus {
                status,
                body: resp.text().unwrap_or_default(),
            }),
        }
    }

    // ------------------------------------------------------------------
    // Breakpoints
    // ------------------------------------------------------------------

    /// Ensure the breakpoint WebSocket client is connected and return the clientId.
    /// If the existing connection's read loop has exited, it is replaced transparently.
    fn ensure_breakpoint_ws(&self) -> Result<String> {
        let mut guard = self.breakpoint_ws.lock().unwrap();
        let needs_connect = match guard.as_ref() {
            None => true,
            Some(ws) => ws.is_dead(),
        };
        if needs_connect {
            // Close the old dead connection if present
            if let Some(old) = guard.take() {
                old.close();
            }
            let ws = BreakpointWebSocketClient::connect(
                &self.base_url,
                self.control_plane_bearer_token.as_deref(),
                self.ws_tls.as_ref(),
            )?;
            *guard = Some(ws);
        }
        Ok(guard.as_ref().unwrap().client_id.clone())
    }

    /// Register a breakpoint matcher with the given phases and handlers.
    /// Returns the server-assigned breakpoint id.
    pub fn add_breakpoint(
        &self,
        matcher: HttpRequest,
        phases: &[&str],
        request_handler: Option<BreakpointRequestHandler>,
        response_handler: Option<BreakpointResponseHandler>,
        stream_frame_handler: Option<BreakpointStreamFrameHandler>,
    ) -> Result<String> {
        if phases.is_empty() {
            return Err(Error::InvalidRequest(
                "At least one phase is required".into(),
            ));
        }

        let client_id = self.ensure_breakpoint_ws()?;

        let reg = BreakpointMatcherRegistration {
            http_request: matcher,
            phases: phases.iter().map(|s| s.to_string()).collect(),
            client_id: Some(client_id),
        };

        let resp = self
            .http
            .put(self.url("/mockserver/breakpoint/matcher"))
            .json(&reg)
            .send()?;

        let status = resp.status().as_u16();
        let text = resp.text()?;
        if status >= 400 {
            return Err(Error::UnexpectedStatus { status, body: text });
        }

        let result: BreakpointMatcherResponse = serde_json::from_str(&text)?;
        let id = result.id.clone();

        // Register handlers
        let guard = self.breakpoint_ws.lock().unwrap();
        if let Some(ws) = guard.as_ref() {
            if let Some(h) = request_handler {
                ws.set_request_handler(&id, h);
            }
            if let Some(h) = response_handler {
                ws.set_response_handler(&id, h);
            }
            if let Some(h) = stream_frame_handler {
                ws.set_stream_frame_handler(&id, h);
            }
        }

        Ok(id)
    }

    /// Convenience: register a REQUEST-only breakpoint.
    pub fn add_request_breakpoint(
        &self,
        matcher: HttpRequest,
        handler: BreakpointRequestHandler,
    ) -> Result<String> {
        self.add_breakpoint(
            matcher,
            &[crate::breakpoint::phase::REQUEST],
            Some(handler),
            None,
            None,
        )
    }

    /// Convenience: register a REQUEST + RESPONSE breakpoint.
    pub fn add_request_response_breakpoint(
        &self,
        matcher: HttpRequest,
        request_handler: BreakpointRequestHandler,
        response_handler: BreakpointResponseHandler,
    ) -> Result<String> {
        self.add_breakpoint(
            matcher,
            &[
                crate::breakpoint::phase::REQUEST,
                crate::breakpoint::phase::RESPONSE,
            ],
            Some(request_handler),
            Some(response_handler),
            None,
        )
    }

    /// Convenience: register a streaming-phase breakpoint.
    pub fn add_stream_breakpoint(
        &self,
        matcher: HttpRequest,
        phases: &[&str],
        handler: BreakpointStreamFrameHandler,
    ) -> Result<String> {
        self.add_breakpoint(matcher, phases, None, None, Some(handler))
    }

    /// List all registered breakpoint matchers.
    pub fn list_breakpoint_matchers(&self) -> Result<BreakpointMatcherList> {
        let resp = self
            .http
            .get(self.url("/mockserver/breakpoint/matchers"))
            .send()?;

        let status = resp.status().as_u16();
        let text = resp.text()?;
        if status >= 400 {
            return Err(Error::UnexpectedStatus { status, body: text });
        }

        Ok(serde_json::from_str(&text)?)
    }

    /// Remove a breakpoint matcher by id.
    pub fn remove_breakpoint_matcher(&self, id: impl Into<String>) -> Result<()> {
        let id = id.into();
        let body = serde_json::json!({ "id": &id });
        let resp = self
            .http
            .put(self.url("/mockserver/breakpoint/matcher/remove"))
            .json(&body)
            .send()?;

        let status = resp.status().as_u16();
        match status {
            200 => {
                let guard = self.breakpoint_ws.lock().unwrap();
                if let Some(ws) = guard.as_ref() {
                    ws.remove_handlers(&id);
                }
                Ok(())
            }
            404 => Err(Error::InvalidRequest(format!(
                "Breakpoint matcher not found: {id}"
            ))),
            _ => Err(Error::UnexpectedStatus {
                status,
                body: resp.text().unwrap_or_default(),
            }),
        }
    }

    /// Remove all registered breakpoint matchers.
    pub fn clear_breakpoint_matchers(&self) -> Result<()> {
        let resp = self
            .http
            .put(self.url("/mockserver/breakpoint/matcher/clear"))
            .header("Content-Type", "application/json")
            .body("")
            .send()?;

        let status = resp.status().as_u16();
        if status >= 400 {
            return Err(Error::UnexpectedStatus {
                status,
                body: resp.text().unwrap_or_default(),
            });
        }

        let guard = self.breakpoint_ws.lock().unwrap();
        if let Some(ws) = guard.as_ref() {
            ws.clear_handlers();
        }
        Ok(())
    }

    /// Close the breakpoint callback WebSocket connection.
    pub fn close_breakpoint_websocket(&self) {
        let mut guard = self.breakpoint_ws.lock().unwrap();
        if let Some(ws) = guard.take() {
            ws.close();
        }
    }

    // ------------------------------------------------------------------
    // Object (closure) callbacks
    // ------------------------------------------------------------------

    /// Register an expectation whose response is produced by a Rust closure
    /// invoked over the callback WebSocket (an `httpResponseObjectCallback`).
    ///
    /// When a request matches `matcher`, MockServer pushes it to this client over
    /// the shared callback WebSocket; `handler` receives the [`HttpRequest`] and
    /// returns the [`HttpResponse`] to send back. The closure runs on the client's
    /// background WebSocket-read thread, so it must be `Send + 'static`.
    ///
    /// The callback WebSocket is shared with breakpoints — only one socket is
    /// opened per client. There is a single object-response handler per client;
    /// calling this again replaces it. Narrow which requests reach the closure
    /// with the `matcher`.
    ///
    /// # Example
    /// ```no_run
    /// use mockserver_client::{ClientBuilder, HttpRequest, HttpResponse};
    ///
    /// let client = ClientBuilder::new("localhost", 1080).build().unwrap();
    /// client.mock_with_callback(
    ///     HttpRequest::new().method("GET").path("/echo"),
    ///     |req| {
    ///         HttpResponse::new()
    ///             .status_code(200)
    ///             .body(format!("you asked for {}", req.path.unwrap_or_default()))
    ///     },
    /// ).unwrap();
    /// ```
    pub fn mock_with_callback<F>(
        &self,
        matcher: HttpRequest,
        handler: F,
    ) -> Result<Vec<Expectation>>
    where
        F: Fn(HttpRequest) -> HttpResponse + Send + 'static,
    {
        // Ensure the shared callback WebSocket is connected and learn its clientId.
        let client_id = self.ensure_breakpoint_ws()?;

        // Adapt the typed closure to the JSON-level ObjectResponseHandler the WS
        // read loop drives. The reply must echo the WebSocketCorrelationId header,
        // which route_object_callback re-applies after the closure returns.
        let object_handler: crate::breakpoint::ObjectResponseHandler =
            Box::new(move |request_json: Value| {
                let request: HttpRequest =
                    serde_json::from_value(request_json).unwrap_or_default();
                let response = handler(request);
                serde_json::to_value(&response).unwrap_or_else(|_| serde_json::json!({}))
            });

        {
            let guard = self.breakpoint_ws.lock().unwrap();
            if let Some(ws) = guard.as_ref() {
                ws.set_object_response_handler(object_handler);
            }
        }

        let expectation = Expectation::new(matcher)
            .respond_object_callback(HttpObjectCallback::new(client_id));
        self.upsert(&[expectation])
    }

    // ------------------------------------------------------------------
    // gRPC descriptor management
    // ------------------------------------------------------------------

    /// Upload a compiled protobuf descriptor set so gRPC requests can be matched.
    ///
    /// `descriptor` must be the raw bytes of a `FileDescriptorSet` (e.g. the
    /// output of `protoc --descriptor_set_out=... --include_imports`). The bytes
    /// are sent verbatim as `application/octet-stream` — they are **not**
    /// base64-encoded. Sends a `PUT /mockserver/grpc/descriptors`; the server
    /// responds `201 Created` on success.
    ///
    /// # Example
    /// ```no_run
    /// use mockserver_client::ClientBuilder;
    ///
    /// let client = ClientBuilder::new("localhost", 1080).build().unwrap();
    /// let descriptor_set: Vec<u8> = std::fs::read("greeter.desc").unwrap();
    /// client.upload_grpc_descriptor(&descriptor_set).unwrap();
    /// ```
    pub fn upload_grpc_descriptor(&self, descriptor: &[u8]) -> Result<()> {
        if descriptor.is_empty() {
            return Err(Error::InvalidRequest(
                "descriptor set bytes must not be empty".into(),
            ));
        }
        let resp = self
            .http
            .put(self.url("/mockserver/grpc/descriptors"))
            .header("Content-Type", "application/octet-stream")
            .body(descriptor.to_vec())
            .send()?;

        let status = resp.status().as_u16();
        match status {
            200 | 201 => Ok(()),
            400 => Err(Error::InvalidRequest(resp.text()?)),
            _ => Err(Error::UnexpectedStatus {
                status,
                body: resp.text().unwrap_or_default(),
            }),
        }
    }

    /// Retrieve the gRPC services registered from uploaded descriptor sets.
    ///
    /// Sends a `PUT /mockserver/grpc/services` and returns the parsed list of
    /// [`GrpcService`]s, each with its [`GrpcMethod`]s.
    pub fn retrieve_grpc_services(&self) -> Result<Vec<GrpcService>> {
        let resp = self
            .http
            .put(self.url("/mockserver/grpc/services"))
            .header("Content-Type", "application/json")
            .body("")
            .send()?;

        let status = resp.status().as_u16();
        match status {
            200 => {
                let text = resp.text()?;
                if text.is_empty() {
                    Ok(vec![])
                } else {
                    Ok(serde_json::from_str(&text)?)
                }
            }
            400 => Err(Error::InvalidRequest(resp.text()?)),
            _ => Err(Error::UnexpectedStatus {
                status,
                body: resp.text().unwrap_or_default(),
            }),
        }
    }

    /// Clear all uploaded gRPC descriptor sets and registered services.
    ///
    /// Sends a `PUT /mockserver/grpc/clear`; the server responds `200 OK`.
    pub fn clear_grpc_descriptors(&self) -> Result<()> {
        let resp = self
            .http
            .put(self.url("/mockserver/grpc/clear"))
            .header("Content-Type", "application/json")
            .body("")
            .send()?;

        let status = resp.status().as_u16();
        match status {
            200 => Ok(()),
            400 => Err(Error::InvalidRequest(resp.text()?)),
            _ => Err(Error::UnexpectedStatus {
                status,
                body: resp.text().unwrap_or_default(),
            }),
        }
    }

    // ------------------------------------------------------------------
    // Stateful scenarios
    // ------------------------------------------------------------------

    /// Obtain a handle for inspecting and driving a named scenario state-machine.
    ///
    /// The returned [`Scenario`] borrows the client and issues control-plane
    /// requests against `/mockserver/scenario/{name}`.
    ///
    /// # Example
    /// ```no_run
    /// use mockserver_client::ClientBuilder;
    ///
    /// let client = ClientBuilder::new("localhost", 1080).build().unwrap();
    /// client.scenario("Deploy").set("Deploying").unwrap();
    /// client.scenario("Deploy").set_timed("Deploying", 5000, "Deployed").unwrap();
    /// client.scenario("Deploy").trigger("Failed").unwrap();
    /// let state = client.scenario("Deploy").state().unwrap();
    /// assert_eq!(state, "Failed");
    /// ```
    pub fn scenario(&self, name: &str) -> Scenario<'_> {
        Scenario {
            client: self,
            name: name.to_string(),
        }
    }

    /// List every known scenario and its current state.
    ///
    /// Sends a `GET /mockserver/scenario` and returns the parsed list of
    /// [`ScenarioState`]s.
    pub fn scenarios(&self) -> Result<Vec<ScenarioState>> {
        let resp = self.http.get(self.url("/mockserver/scenario")).send()?;
        let status = resp.status().as_u16();
        match status {
            200 => {
                let text = resp.text()?;
                if text.is_empty() {
                    Ok(vec![])
                } else {
                    let list: ScenarioList = serde_json::from_str(&text)?;
                    Ok(list.scenarios)
                }
            }
            400 => Err(Error::InvalidRequest(resp.text()?)),
            _ => Err(Error::UnexpectedStatus {
                status,
                body: resp.text().unwrap_or_default(),
            }),
        }
    }

    /// Get the current state of a named scenario.
    fn scenario_state(&self, name: &str) -> Result<String> {
        let resp = self
            .http
            .get(self.url(&scenario_path(name)))
            .send()?;
        let status = resp.status().as_u16();
        match status {
            200 => {
                let text = resp.text()?;
                let state: ScenarioState = serde_json::from_str(&text)?;
                Ok(state.current_state)
            }
            400 => Err(Error::InvalidRequest(resp.text()?)),
            _ => Err(Error::UnexpectedStatus {
                status,
                body: resp.text().unwrap_or_default(),
            }),
        }
    }

    /// Set a scenario's state, optionally scheduling a timed transition.
    fn scenario_set(
        &self,
        name: &str,
        state: &str,
        transition_after_ms: Option<u64>,
        next_state: Option<&str>,
    ) -> Result<()> {
        let mut body = serde_json::json!({ "state": state });
        if let Some(ms) = transition_after_ms {
            body["transitionAfterMs"] = serde_json::json!(ms);
        }
        if let Some(next) = next_state {
            body["nextState"] = serde_json::json!(next);
        }
        let resp = self
            .http
            .put(self.url(&scenario_path(name)))
            .json(&body)
            .send()?;
        self.scenario_ok(resp)
    }

    /// Externally trigger a scenario state transition.
    fn scenario_trigger(&self, name: &str, new_state: &str) -> Result<()> {
        let body = serde_json::json!({ "newState": new_state });
        let resp = self
            .http
            .put(self.url(&format!("{}/trigger", scenario_path(name))))
            .json(&body)
            .send()?;
        self.scenario_ok(resp)
    }

    /// Map a scenario REST response to `Ok(())` on `200`, surfacing the server's
    /// error body on `400` and any other status as [`Error::UnexpectedStatus`].
    fn scenario_ok(&self, resp: reqwest::blocking::Response) -> Result<()> {
        let status = resp.status().as_u16();
        match status {
            200 => Ok(()),
            400 => Err(Error::InvalidRequest(resp.text()?)),
            _ => Err(Error::UnexpectedStatus {
                status,
                body: resp.text().unwrap_or_default(),
            }),
        }
    }

    // ------------------------------------------------------------------
    // SRE control plane — load scenario registry
    // ------------------------------------------------------------------

    /// Register (load) a load scenario in the registry without running it.
    ///
    /// Sends a `PUT /mockserver/loadScenario` with the given [`LoadScenario`].
    /// The scenario's [`name`](LoadScenario::name) is the unique registry key
    /// used later by [`start_load_scenarios`](Self::start_load_scenarios) /
    /// [`stop_load_scenarios`](Self::stop_load_scenarios) and the per-scenario
    /// fetch/delete endpoints.
    ///
    /// Registering is always allowed — even when load generation is disabled on
    /// the server — so this does not surface [`Error::FeatureDisabled`]. Returns
    /// the raw JSON the server echoes (`{"name":..,"state":"LOADED"}`).
    pub fn load_scenario(&self, scenario: &LoadScenario) -> Result<Value> {
        let body = serde_json::to_value(scenario)?;
        let resp = self
            .http
            .put(self.url("/mockserver/loadScenario"))
            .json(&body)
            .send()?;
        self.load_scenario_json(resp)
    }

    /// List every registered load scenario.
    ///
    /// Sends a `GET /mockserver/loadScenario` and returns the raw JSON
    /// (`{"scenarios":[{"name":..,"state":..,"definition":..,"status":..?}]}`).
    pub fn load_scenarios(&self) -> Result<Value> {
        let resp = self.http.get(self.url("/mockserver/loadScenario")).send()?;
        self.load_scenario_json(resp)
    }

    /// Fetch a single registered load scenario by name.
    ///
    /// Sends a `GET /mockserver/loadScenario/{name}`. Returns
    /// [`Error::NotFound`] when no scenario with that name is registered.
    pub fn get_load_scenario(&self, name: impl AsRef<str>) -> Result<Value> {
        let resp = self
            .http
            .get(self.url(&format!("/mockserver/loadScenario/{}", name.as_ref())))
            .send()?;
        self.load_scenario_json(resp)
    }

    /// Remove a single registered load scenario by name.
    ///
    /// Sends a `DELETE /mockserver/loadScenario/{name}`. Returns
    /// [`Error::NotFound`] when no scenario with that name is registered.
    pub fn delete_load_scenario(&self, name: impl AsRef<str>) -> Result<Value> {
        let resp = self
            .http
            .delete(self.url(&format!("/mockserver/loadScenario/{}", name.as_ref())))
            .send()?;
        self.load_scenario_json(resp)
    }

    /// Clear all registered load scenarios.
    ///
    /// Sends a `DELETE /mockserver/loadScenario`. Idempotent.
    pub fn clear_load_scenarios(&self) -> Result<Value> {
        let resp = self
            .http
            .delete(self.url("/mockserver/loadScenario"))
            .send()?;
        self.load_scenario_json(resp)
    }

    /// Start one or more registered load scenarios by name.
    ///
    /// Sends a `PUT /mockserver/loadScenario/start` with `{"names":[...]}`.
    /// Requires load generation to be enabled on the server — returns
    /// [`Error::FeatureDisabled`] on `403` (`loadGenerationEnabled=false`) — and
    /// [`Error::NotFound`] when a name is not registered. Honours each
    /// scenario's `startDelayMillis`. Returns the raw JSON
    /// (`{"started":[{"name":..,"state":..}],"status":..}`).
    pub fn start_load_scenarios<S: AsRef<str>>(&self, names: &[S]) -> Result<Value> {
        let names: Vec<&str> = names.iter().map(|n| n.as_ref()).collect();
        let body = serde_json::json!({ "names": names });
        let resp = self
            .http
            .put(self.url("/mockserver/loadScenario/start"))
            .json(&body)
            .send()?;
        self.load_scenario_json(resp)
    }

    /// Stop running load scenarios.
    ///
    /// Sends a `PUT /mockserver/loadScenario/stop`. When `names` is non-empty the
    /// body is `{"names":[...]}`; when it is empty (or `&[]`) the body is empty,
    /// which the server treats as "stop all". Returns the raw JSON
    /// (`{"stopped":[..],"status":..}`).
    pub fn stop_load_scenarios<S: AsRef<str>>(&self, names: &[S]) -> Result<Value> {
        let mut req = self.http.put(self.url("/mockserver/loadScenario/stop"));
        if names.is_empty() {
            req = req.header("Content-Type", "application/json").body("");
        } else {
            let names: Vec<&str> = names.iter().map(|n| n.as_ref()).collect();
            req = req.json(&serde_json::json!({ "names": names }));
        }
        let resp = req.send()?;
        self.load_scenario_json(resp)
    }

    /// Convenience: register `scenario` then immediately start it by name.
    ///
    /// Equivalent to [`load_scenario`](Self::load_scenario) followed by
    /// [`start_load_scenarios`](Self::start_load_scenarios) with the scenario's
    /// own name. Returns the JSON from the `start` call. Surfaces
    /// [`Error::FeatureDisabled`] from `start` when load generation is disabled.
    pub fn run_load_scenario(&self, scenario: &LoadScenario) -> Result<Value> {
        self.load_scenario(scenario)?;
        self.start_load_scenarios(&[scenario.name.as_str()])
    }

    /// Fetch the end-of-run summary report for a load scenario run.
    ///
    /// Sends a `GET /mockserver/loadScenario/{name}/report`. When `format` is
    /// `Some("junit")` a `?format=junit` query is appended and the server returns
    /// a JUnit-XML `<testsuite>` document; omit `format` (`None`) for the JSON
    /// report. The raw response body is returned as a string so either form (JSON
    /// or XML) passes through verbatim. Returns [`Error::NotFound`] when the
    /// scenario never ran.
    pub fn get_load_scenario_report(
        &self,
        name: impl AsRef<str>,
        format: Option<&str>,
    ) -> Result<String> {
        let mut path = format!("/mockserver/loadScenario/{}/report", name.as_ref());
        if let Some(format) = format {
            path.push_str("?format=");
            path.push_str(format);
        }
        let resp = self.http.get(self.url(&path)).send()?;
        let status = resp.status().as_u16();
        match status {
            200 | 201 => Ok(resp.text()?),
            400 => Err(Error::InvalidRequest(resp.text()?)),
            403 => Err(Error::FeatureDisabled(resp.text()?)),
            404 => Err(Error::NotFound(resp.text()?)),
            _ => Err(Error::UnexpectedStatus {
                status,
                body: resp.text().unwrap_or_default(),
            }),
        }
    }

    /// Generate (and register) an editable load scenario from an OpenAPI spec.
    ///
    /// Sends a `PUT /mockserver/loadScenario/generateFromOpenAPI`. The `body`
    /// carries the generated scenario `name`, the `specUrlOrPayload`, and an
    /// optional `target` and `profile`. Like [`load_scenario`](Self::load_scenario)
    /// this only registers (LOADED) the scenario — it generates no traffic and is
    /// allowed even when load generation is disabled. Returns the raw JSON
    /// (`{"status":"loaded","name":..,"state":..,"scenario":..}`).
    pub fn generate_load_scenario_from_openapi<T: Serialize + ?Sized>(
        &self,
        body: &T,
    ) -> Result<Value> {
        let body = serde_json::to_value(body)?;
        let resp = self
            .http
            .put(self.url("/mockserver/loadScenario/generateFromOpenAPI"))
            .json(&body)
            .send()?;
        self.load_scenario_json(resp)
    }

    /// Generate (and register) an editable load scenario from recorded proxy
    /// traffic.
    ///
    /// Sends a `PUT /mockserver/loadScenario/generateFromRecording`. The `body`
    /// carries the generated scenario `name` and the recording selection/options.
    /// Like [`load_scenario`](Self::load_scenario) this only registers (LOADED)
    /// the scenario — it generates no traffic. Returns the raw JSON
    /// (`{"status":"loaded","name":..,"state":..,"scenario":..}`).
    pub fn generate_load_scenario_from_recording<T: Serialize + ?Sized>(
        &self,
        body: &T,
    ) -> Result<Value> {
        let body = serde_json::to_value(body)?;
        let resp = self
            .http
            .put(self.url("/mockserver/loadScenario/generateFromRecording"))
            .json(&body)
            .send()?;
        self.load_scenario_json(resp)
    }

    // ------------------------------------------------------------------
    // SRE control plane — service chaos
    // ------------------------------------------------------------------

    /// Register a service-scoped HTTP chaos profile for a downstream host.
    ///
    /// Sends a `PUT /mockserver/serviceChaos`. `ttl_millis`, when supplied, sets
    /// an optional time-to-live after which the registration auto-reverts.
    /// Returns the raw JSON the server echoes.
    pub fn set_service_chaos(
        &self,
        host: impl Into<String>,
        profile: &HttpChaosProfile,
        ttl_millis: Option<u64>,
    ) -> Result<Value> {
        let mut body = serde_json::json!({
            "host": host.into(),
            "chaos": profile,
        });
        if let Some(ttl) = ttl_millis {
            body["ttlMillis"] = serde_json::json!(ttl);
        }
        let resp = self
            .http
            .put(self.url("/mockserver/serviceChaos"))
            .json(&body)
            .send()?;
        self.json_or_feature_error(resp)
    }

    /// Remove a single host's service-scoped chaos profile.
    ///
    /// Sends a `PUT /mockserver/serviceChaos` with `{"host":..,"remove":true}`.
    pub fn remove_service_chaos(&self, host: impl Into<String>) -> Result<Value> {
        let body = serde_json::json!({ "host": host.into(), "remove": true });
        let resp = self
            .http
            .put(self.url("/mockserver/serviceChaos"))
            .json(&body)
            .send()?;
        self.json_or_feature_error(resp)
    }

    /// Clear all service-scoped chaos.
    ///
    /// Sends a `PUT /mockserver/serviceChaos` with `{"clear":true}`.
    pub fn clear_service_chaos(&self) -> Result<Value> {
        let body = serde_json::json!({ "clear": true });
        let resp = self
            .http
            .put(self.url("/mockserver/serviceChaos"))
            .json(&body)
            .send()?;
        self.json_or_feature_error(resp)
    }

    // ------------------------------------------------------------------
    // SRE control plane — SLO verdicts
    // ------------------------------------------------------------------

    /// Verify a set of service-level objectives over a window.
    ///
    /// Sends a `PUT /mockserver/verifySLO`. The HTTP status encodes the verdict:
    /// `200` for PASS or INCONCLUSIVE, `406` for FAIL — both deserialize into a
    /// [`SloVerdict`] (inspect [`SloVerdict::result`]). A `400` (malformed
    /// criteria, or SLO tracking disabled) surfaces as [`Error::FeatureDisabled`].
    ///
    /// Returns `Ok(SloVerdict)` for both PASS/INCONCLUSIVE (200) and FAIL (406)
    /// so callers can branch on the verdict; transport/parse failures and `400`
    /// are returned as `Err`.
    pub fn verify_slo(&self, criteria: &SloCriteria) -> Result<SloVerdict> {
        let resp = self
            .http
            .put(self.url("/mockserver/verifySLO"))
            .json(criteria)
            .send()?;

        let status = resp.status().as_u16();
        match status {
            // PASS / INCONCLUSIVE (200) and FAIL (406) both carry a SloVerdict.
            200 | 406 => {
                let text = resp.text()?;
                Ok(serde_json::from_str(&text)?)
            }
            // 403 is "SLO tracking is off"; 400 is "these criteria are malformed". They used to
            // be the same status, so a malformed criteria was misreported as a disabled feature.
            403 => Err(Error::FeatureDisabled(resp.text()?)),
            400 => Err(Error::InvalidRequest(resp.text()?)),
            _ => Err(Error::UnexpectedStatus {
                status,
                body: resp.text().unwrap_or_default(),
            }),
        }
    }

    // ------------------------------------------------------------------
    // SRE control plane — preemption
    // ------------------------------------------------------------------

    /// Cordon and drain the server (preemption simulation).
    ///
    /// Sends a `PUT /mockserver/preemption` with the given [`PreemptionRequest`]
    /// and returns the resulting [`PreemptionStatus`].
    pub fn set_preemption(&self, request: &PreemptionRequest) -> Result<PreemptionStatus> {
        let resp = self
            .http
            .put(self.url("/mockserver/preemption"))
            .json(request)
            .send()?;

        let status = resp.status().as_u16();
        match status {
            200 => {
                let text = resp.text()?;
                Ok(serde_json::from_str(&text)?)
            }
            400 => Err(Error::InvalidRequest(resp.text()?)),
            403 => Err(Error::FeatureDisabled(resp.text()?)),
            _ => Err(Error::UnexpectedStatus {
                status,
                body: resp.text().unwrap_or_default(),
            }),
        }
    }

    /// Retrieve the current preemption status.
    ///
    /// Sends a `GET /mockserver/preemption` and returns the [`PreemptionStatus`].
    pub fn preemption_status(&self) -> Result<PreemptionStatus> {
        let resp = self
            .http
            .get(self.url("/mockserver/preemption"))
            .send()?;

        let status = resp.status().as_u16();
        match status {
            200 => {
                let text = resp.text()?;
                Ok(serde_json::from_str(&text)?)
            }
            403 => Err(Error::FeatureDisabled(resp.text()?)),
            _ => Err(Error::UnexpectedStatus {
                status,
                body: resp.text().unwrap_or_default(),
            }),
        }
    }

    /// Uncordon the server (clear any active preemption simulation).
    ///
    /// Sends a `DELETE /mockserver/preemption`. Idempotent — succeeds whether or
    /// not a simulation was active. Returns the raw JSON status.
    pub fn clear_preemption(&self) -> Result<Value> {
        let resp = self
            .http
            .delete(self.url("/mockserver/preemption"))
            .send()?;
        self.json_or_feature_error(resp)
    }

    // ------------------------------------------------------------------
    // SRE control plane — chaos experiments
    // ------------------------------------------------------------------

    /// Start a scheduled multi-stage chaos experiment.
    ///
    /// Sends a `PUT /mockserver/chaosExperiment` with the given
    /// [`ChaosExperiment`]. Only one experiment may be active at a time; starting
    /// a new one stops the previous one. Returns the raw JSON status
    /// (`{"status":"started","name":..}`).
    pub fn start_chaos_experiment(&self, experiment: &ChaosExperiment) -> Result<Value> {
        let resp = self
            .http
            .put(self.url("/mockserver/chaosExperiment"))
            .json(experiment)
            .send()?;
        self.json_or_feature_error(resp)
    }

    // ------------------------------------------------------------------
    // Internal helpers
    // ------------------------------------------------------------------

    /// Common handler for SRE endpoints returning JSON on `200`, mapping `403`
    /// to [`Error::FeatureDisabled`] (the feature is disabled on the server),
    /// `400` to [`Error::InvalidRequest`], and any other status to
    /// [`Error::UnexpectedStatus`]. An empty `200` body deserializes to JSON
    /// `null`.
    fn json_or_feature_error(&self, resp: reqwest::blocking::Response) -> Result<Value> {
        let status = resp.status().as_u16();
        match status {
            200 | 201 => {
                let text = resp.text()?;
                if text.is_empty() {
                    Ok(Value::Null)
                } else {
                    Ok(serde_json::from_str(&text)?)
                }
            }
            400 => Err(Error::InvalidRequest(resp.text()?)),
            403 => Err(Error::FeatureDisabled(resp.text()?)),
            _ => Err(Error::UnexpectedStatus {
                status,
                body: resp.text().unwrap_or_default(),
            }),
        }
    }

    /// Handler for load-scenario registry endpoints. Like
    /// [`json_or_feature_error`](Self::json_or_feature_error) but additionally
    /// maps `404` to [`Error::NotFound`] (an unknown scenario name).
    fn load_scenario_json(&self, resp: reqwest::blocking::Response) -> Result<Value> {
        let status = resp.status().as_u16();
        match status {
            200 | 201 => {
                let text = resp.text()?;
                if text.is_empty() {
                    Ok(Value::Null)
                } else {
                    Ok(serde_json::from_str(&text)?)
                }
            }
            400 => Err(Error::InvalidRequest(resp.text()?)),
            403 => Err(Error::FeatureDisabled(resp.text()?)),
            404 => Err(Error::NotFound(resp.text()?)),
            _ => Err(Error::UnexpectedStatus {
                status,
                body: resp.text().unwrap_or_default(),
            }),
        }
    }

    // ------------------------------------------------------------------
    // Clock control
    // ------------------------------------------------------------------

    /// Freeze the simulated clock (`PUT /mockserver/clock`, `action=freeze`).
    ///
    /// Pass `Some(instant)` — an ISO-8601 instant string such as
    /// `"2024-01-01T00:00:00Z"` — to freeze at a specific time, or `None` to
    /// freeze at the current time. Returns the clock-status JSON the server
    /// echoes back.
    pub fn freeze_clock(&self, instant: Option<&str>) -> Result<String> {
        let body = match instant {
            Some(i) => serde_json::json!({ "action": "freeze", "instant": i }),
            None => serde_json::json!({ "action": "freeze" }),
        };
        self.clock_put(&body)
    }

    /// Advance the simulated clock by `duration_millis` (`PUT /mockserver/clock`,
    /// `action=advance`). The value must be positive — the server returns `400`
    /// for `<= 0`. Returns the clock-status JSON the server echoes back.
    pub fn advance_clock(&self, duration_millis: i64) -> Result<String> {
        let body = serde_json::json!({ "action": "advance", "durationMillis": duration_millis });
        self.clock_put(&body)
    }

    /// Reset the simulated clock back to the real system clock
    /// (`PUT /mockserver/clock`, `action=reset`). Returns the clock-status JSON.
    pub fn reset_clock(&self) -> Result<String> {
        let body = serde_json::json!({ "action": "reset" });
        self.clock_put(&body)
    }

    /// Read the current clock status (`GET /mockserver/clock`). Returns the JSON
    /// body verbatim, e.g.
    /// `{"currentInstant":"...","currentEpochMillis":...,"frozen":true}`.
    pub fn clock_status(&self) -> Result<String> {
        let resp = self.http.get(self.url("/mockserver/clock")).send()?;
        let status = resp.status().as_u16();
        match status {
            200 => Ok(resp.text()?),
            400 => Err(Error::InvalidRequest(resp.text()?)),
            _ => Err(Error::UnexpectedStatus {
                status,
                body: resp.text().unwrap_or_default(),
            }),
        }
    }

    fn clock_put(&self, body: &Value) -> Result<String> {
        let resp = self
            .http
            .put(self.url("/mockserver/clock"))
            .json(body)
            .send()?;
        let status = resp.status().as_u16();
        match status {
            200 => Ok(resp.text()?),
            400 => Err(Error::InvalidRequest(resp.text()?)),
            _ => Err(Error::UnexpectedStatus {
                status,
                body: resp.text().unwrap_or_default(),
            }),
        }
    }

    // ------------------------------------------------------------------
    // Metrics
    // ------------------------------------------------------------------

    /// Retrieve the JSON metrics counter snapshot
    /// (`PUT /mockserver/retrieve?type=METRICS`). Returns a flat JSON object
    /// mapping each metric name to its long value (`{}` when metrics are
    /// disabled).
    pub fn retrieve_metrics(&self) -> Result<String> {
        let url = format!(
            "{}?type=METRICS",
            self.url("/mockserver/retrieve")
        );
        let resp = self
            .http
            .put(&url)
            .header("Content-Type", "application/json")
            .body("")
            .send()?;
        let status = resp.status().as_u16();
        match status {
            200 => Ok(resp.text()?),
            400 => Err(Error::InvalidRequest(resp.text()?)),
            _ => Err(Error::UnexpectedStatus {
                status,
                body: resp.text().unwrap_or_default(),
            }),
        }
    }

    /// Scrape the Prometheus exposition metrics (`GET /mockserver/metrics`).
    /// Returns the exposition text. When metrics are disabled the server replies
    /// `404`, surfaced as [`Error::NotFound`].
    pub fn scrape_metrics(&self) -> Result<String> {
        let resp = self.http.get(self.url("/mockserver/metrics")).send()?;
        let status = resp.status().as_u16();
        match status {
            200 => Ok(resp.text()?),
            404 => Err(Error::NotFound(resp.text().unwrap_or_default())),
            _ => Err(Error::UnexpectedStatus {
                status,
                body: resp.text().unwrap_or_default(),
            }),
        }
    }

    // ------------------------------------------------------------------
    // Configuration
    // ------------------------------------------------------------------

    /// Read the effective live configuration (`GET /mockserver/configuration`).
    /// Returns the serialized `Configuration` JSON.
    pub fn retrieve_configuration(&self) -> Result<String> {
        let resp = self
            .http
            .get(self.url("/mockserver/configuration"))
            .send()?;
        let status = resp.status().as_u16();
        match status {
            200 => Ok(resp.text()?),
            400 => Err(Error::InvalidRequest(resp.text()?)),
            _ => Err(Error::UnexpectedStatus {
                status,
                body: resp.text().unwrap_or_default(),
            }),
        }
    }

    /// Update the live configuration (`PUT /mockserver/configuration`).
    ///
    /// `config_json` is a `ConfigurationDTO` JSON document — only the fields
    /// present are applied (partial update). Returns the serialized *updated*
    /// configuration JSON.
    pub fn update_configuration(&self, config_json: &str) -> Result<String> {
        let resp = self
            .http
            .put(self.url("/mockserver/configuration"))
            .header("Content-Type", "application/json")
            .body(config_json.to_string())
            .send()?;
        let status = resp.status().as_u16();
        match status {
            200 => Ok(resp.text()?),
            400 => Err(Error::InvalidRequest(resp.text()?)),
            _ => Err(Error::UnexpectedStatus {
                status,
                body: resp.text().unwrap_or_default(),
            }),
        }
    }

    // ------------------------------------------------------------------
    // Drift detection
    // ------------------------------------------------------------------

    /// Retrieve the recorded mock drift report (`GET /mockserver/drift`).
    ///
    /// Returns the serialized report JSON, of the form
    /// `{"count": <n>, "drifts": [ ... ]}`, where each entry describes a
    /// difference detected between a mock's configured response and the live
    /// upstream response for the same request.
    pub fn retrieve_drift(&self) -> Result<String> {
        let resp = self.http.get(self.url("/mockserver/drift")).send()?;
        let status = resp.status().as_u16();
        match status {
            200 => Ok(resp.text()?),
            _ => Err(Error::UnexpectedStatus {
                status,
                body: resp.text().unwrap_or_default(),
            }),
        }
    }

    /// Clear all recorded mock drift (`PUT /mockserver/drift/clear`).
    pub fn clear_drift(&self) -> Result<()> {
        let resp = self
            .http
            .put(self.url("/mockserver/drift/clear"))
            .header("Content-Type", "application/json")
            .body("")
            .send()?;

        let status = resp.status().as_u16();
        match status {
            200 => Ok(()),
            _ => Err(Error::UnexpectedStatus {
                status,
                body: resp.text().unwrap_or_default(),
            }),
        }
    }

    // ------------------------------------------------------------------
    // Pact (import / export / verify)
    // ------------------------------------------------------------------

    /// Import a Pact v3 contract (`PUT /mockserver/pact/import`).
    ///
    /// Returns the JSON array of upserted expectations the server creates.
    pub fn pact_import(&self, json: &str) -> Result<Vec<Expectation>> {
        let resp = self
            .http
            .put(self.url("/mockserver/pact/import"))
            .header("Content-Type", "application/json")
            .body(json.to_string())
            .send()?;
        self.expectations_response(resp)
    }

    /// Export the active expectations as a Pact v3 contract
    /// (`PUT /mockserver/pact?consumer=&provider=`).
    ///
    /// A query parameter is only added when the corresponding value is non-blank
    /// (matching the Java client); blank values fall back to the server defaults.
    /// Returns the generated Pact JSON.
    pub fn pact_export(&self, consumer: &str, provider: &str) -> Result<String> {
        let mut params: Vec<(&str, &str)> = Vec::new();
        if !consumer.trim().is_empty() {
            params.push(("consumer", consumer));
        }
        if !provider.trim().is_empty() {
            params.push(("provider", provider));
        }
        let mut builder = self
            .http
            .put(self.url("/mockserver/pact"))
            .header("Content-Type", "application/json")
            .body("");
        if !params.is_empty() {
            builder = builder.query(&params);
        }
        let resp = builder.send()?;
        let status = resp.status().as_u16();
        match status {
            200 => Ok(resp.text()?),
            400 => Err(Error::InvalidRequest(resp.text()?)),
            _ => Err(Error::UnexpectedStatus {
                status,
                body: resp.text().unwrap_or_default(),
            }),
        }
    }

    /// Verify a Pact v3 contract against the active expectations
    /// (`PUT /mockserver/pact/verify`).
    ///
    /// The verification *outcome* is returned in the [`Ok`] value rather than as
    /// an error: `202 ACCEPTED` maps to [`PactVerification`] with `passed = true`
    /// and `406 NOT_ACCEPTABLE` to `passed = false`. Both carry the server's
    /// verification report. A `400` (bad input) is surfaced as
    /// [`Error::InvalidRequest`].
    pub fn pact_verify(&self, json: &str) -> Result<PactVerification> {
        let resp = self
            .http
            .put(self.url("/mockserver/pact/verify"))
            .header("Content-Type", "application/json")
            .body(json.to_string())
            .send()?;
        let status = resp.status().as_u16();
        match status {
            202 => Ok(PactVerification {
                passed: true,
                report: resp.text()?,
            }),
            406 => Ok(PactVerification {
                passed: false,
                report: resp.text()?,
            }),
            400 => Err(Error::InvalidRequest(resp.text()?)),
            _ => Err(Error::UnexpectedStatus {
                status,
                body: resp.text().unwrap_or_default(),
            }),
        }
    }

    // ------------------------------------------------------------------
    // File store
    // ------------------------------------------------------------------

    /// Store a file in the in-memory file store (`PUT /mockserver/files/store`).
    ///
    /// `content` is sent base64-encoded (with `"base64": true`) so arbitrary
    /// binary data round-trips intact. Returns the `{"name":..,"size":..}` JSON.
    pub fn store_file(&self, name: &str, content: &[u8]) -> Result<String> {
        let body = serde_json::json!({
            "name": name,
            "content": BASE64.encode(content),
            "base64": true,
        });
        let resp = self
            .http
            .put(self.url("/mockserver/files/store"))
            .json(&body)
            .send()?;
        let status = resp.status().as_u16();
        match status {
            200 | 201 => Ok(resp.text()?),
            400 => Err(Error::InvalidRequest(resp.text()?)),
            _ => Err(Error::UnexpectedStatus {
                status,
                body: resp.text().unwrap_or_default(),
            }),
        }
    }

    /// Retrieve a file's raw bytes (`PUT /mockserver/files/retrieve`).
    ///
    /// Returns the raw `200` body bytes. An unknown file (`404`) is surfaced as
    /// [`Error::NotFound`] (mirroring the crate's status-to-error mapping).
    pub fn retrieve_file(&self, name: &str) -> Result<Vec<u8>> {
        let body = serde_json::json!({ "name": name });
        let resp = self
            .http
            .put(self.url("/mockserver/files/retrieve"))
            .json(&body)
            .send()?;
        let status = resp.status().as_u16();
        match status {
            200 => Ok(resp.bytes()?.to_vec()),
            400 => Err(Error::InvalidRequest(resp.text()?)),
            404 => Err(Error::NotFound(resp.text().unwrap_or_default())),
            _ => Err(Error::UnexpectedStatus {
                status,
                body: resp.text().unwrap_or_default(),
            }),
        }
    }

    /// List the names of all stored files (`PUT /mockserver/files/list`).
    pub fn list_files(&self) -> Result<Vec<String>> {
        let resp = self
            .http
            .put(self.url("/mockserver/files/list"))
            .header("Content-Type", "application/json")
            .body("")
            .send()?;
        let status = resp.status().as_u16();
        match status {
            200 => {
                let text = resp.text()?;
                if text.trim().is_empty() {
                    Ok(vec![])
                } else {
                    Ok(serde_json::from_str(&text)?)
                }
            }
            400 => Err(Error::InvalidRequest(resp.text()?)),
            _ => Err(Error::UnexpectedStatus {
                status,
                body: resp.text().unwrap_or_default(),
            }),
        }
    }

    /// Delete a stored file (`PUT /mockserver/files/delete`). An unknown file
    /// (`404`) is surfaced as [`Error::NotFound`].
    pub fn delete_file(&self, name: &str) -> Result<()> {
        let body = serde_json::json!({ "name": name });
        let resp = self
            .http
            .put(self.url("/mockserver/files/delete"))
            .json(&body)
            .send()?;
        let status = resp.status().as_u16();
        match status {
            200 => Ok(()),
            400 => Err(Error::InvalidRequest(resp.text()?)),
            404 => Err(Error::NotFound(resp.text().unwrap_or_default())),
            _ => Err(Error::UnexpectedStatus {
                status,
                body: resp.text().unwrap_or_default(),
            }),
        }
    }

    // ------------------------------------------------------------------
    // Import (HAR / Postman)
    // ------------------------------------------------------------------

    /// Import a HAR document (`PUT /mockserver/import?format=har`). Returns the
    /// upserted expectations.
    pub fn import_har(&self, har_json: &str) -> Result<Vec<Expectation>> {
        self.import_document(har_json, "har")
    }

    /// Import a Postman collection
    /// (`PUT /mockserver/import?format=postman`). Returns the upserted
    /// expectations.
    pub fn import_postman_collection(&self, collection_json: &str) -> Result<Vec<Expectation>> {
        self.import_document(collection_json, "postman")
    }

    fn import_document(&self, json: &str, format: &str) -> Result<Vec<Expectation>> {
        let url = format!("{}?format={format}", self.url("/mockserver/import"));
        let resp = self
            .http
            .put(&url)
            .header("Content-Type", "application/json")
            .body(json.to_string())
            .send()?;
        self.expectations_response(resp)
    }

    // ------------------------------------------------------------------
    // Operating mode
    // ------------------------------------------------------------------

    /// Set the high-level operating mode (`PUT /mockserver/mode?mode=<MODE>`).
    /// Returns the `{"mode":..,"proxyUnmatchedRequests":..}` JSON.
    pub fn set_mode(&self, mode: MockMode) -> Result<String> {
        let url = format!("{}?mode={}", self.url("/mockserver/mode"), mode.as_str());
        let resp = self
            .http
            .put(&url)
            .header("Content-Type", "application/json")
            .body("")
            .send()?;
        let status = resp.status().as_u16();
        match status {
            200 => Ok(resp.text()?),
            400 => Err(Error::InvalidRequest(resp.text()?)),
            _ => Err(Error::UnexpectedStatus {
                status,
                body: resp.text().unwrap_or_default(),
            }),
        }
    }

    /// Read the current operating mode (`GET /mockserver/mode`). Returns the
    /// `{"mode":..,"proxyUnmatchedRequests":..}` JSON.
    pub fn retrieve_mode(&self) -> Result<String> {
        let resp = self.http.get(self.url("/mockserver/mode")).send()?;
        let status = resp.status().as_u16();
        match status {
            200 => Ok(resp.text()?),
            400 => Err(Error::InvalidRequest(resp.text()?)),
            _ => Err(Error::UnexpectedStatus {
                status,
                body: resp.text().unwrap_or_default(),
            }),
        }
    }

    // ------------------------------------------------------------------
    // WSDL
    // ------------------------------------------------------------------

    /// Generate expectations from a WSDL document (`PUT /mockserver/wsdl`).
    ///
    /// The raw WSDL XML is sent as the request body (Content-Type `text/xml`).
    /// Returns the generated (upserted) expectations.
    pub fn wsdl_expectation(&self, wsdl: &str) -> Result<Vec<Expectation>> {
        let resp = self
            .http
            .put(self.url("/mockserver/wsdl"))
            .header("Content-Type", "text/xml")
            .body(wsdl.to_string())
            .send()?;
        self.expectations_response(resp)
    }

    /// Map a `201`/`200` JSON-array-of-expectations response (used by import,
    /// pact-import and WSDL) to `Vec<Expectation>`, applying the crate's status
    /// conventions.
    fn expectations_response(
        &self,
        resp: reqwest::blocking::Response,
    ) -> Result<Vec<Expectation>> {
        let status = resp.status().as_u16();
        match status {
            200 | 201 => {
                let text = resp.text()?;
                if text.trim().is_empty() {
                    Ok(vec![])
                } else {
                    Ok(serde_json::from_str(&text)?)
                }
            }
            400 => Err(Error::InvalidRequest(resp.text()?)),
            _ => Err(Error::UnexpectedStatus {
                status,
                body: resp.text().unwrap_or_default(),
            }),
        }
    }

    fn do_verify(&self, verification: &Verification) -> Result<()> {
        let resp = self
            .http
            .put(self.url("/mockserver/verify"))
            .json(verification)
            .send()?;

        let status = resp.status().as_u16();
        match status {
            200 | 202 => Ok(()),
            406 => Err(Error::VerificationFailure(resp.text()?)),
            400 => Err(Error::InvalidRequest(resp.text()?)),
            _ => Err(Error::UnexpectedStatus {
                status,
                body: resp.text().unwrap_or_default(),
            }),
        }
    }

    fn do_verify_sequence(&self, verification: &VerificationSequence) -> Result<()> {
        let resp = self
            .http
            .put(self.url("/mockserver/verifySequence"))
            .json(verification)
            .send()?;

        let status = resp.status().as_u16();
        match status {
            200 | 202 => Ok(()),
            406 => Err(Error::VerificationFailure(resp.text()?)),
            400 => Err(Error::InvalidRequest(resp.text()?)),
            _ => Err(Error::UnexpectedStatus {
                status,
                body: resp.text().unwrap_or_default(),
            }),
        }
    }

    fn url(&self, path: &str) -> String {
        format!("{}{path}", self.base_url)
    }

    /// Send a retrieve request filtered by expectation id rather than by a request
    /// matcher.
    ///
    /// For [`RetrieveType::ActiveExpectations`] the server returns only the expectation
    /// with that id; for every other type it returns the entries matching that
    /// expectation's request, exactly as verify by expectation id matches them. An
    /// unknown id is rejected with a 400 ([`Error::InvalidRequest`]).
    fn do_retrieve_by_id(
        &self,
        expectation_id: &str,
        retrieve_type: RetrieveType,
        format: RetrieveFormat,
    ) -> Result<String> {
        let url = format!(
            "{}?type={}&format={}",
            self.url("/mockserver/retrieve"),
            retrieve_type.as_str(),
            format.as_str(),
        );

        let body = serde_json::json!({ "id": expectation_id });
        let resp = self.http.put(&url).json(&body).send()?;

        let status = resp.status().as_u16();
        match status {
            200 => Ok(resp.text()?),
            400 => Err(Error::InvalidRequest(resp.text()?)),
            _ => Err(Error::UnexpectedStatus {
                status,
                body: resp.text().unwrap_or_default(),
            }),
        }
    }

    fn do_retrieve(
        &self,
        request: Option<&HttpRequest>,
        retrieve_type: RetrieveType,
        format: RetrieveFormat,
    ) -> Result<String> {
        let url = format!(
            "{}?type={}&format={}",
            self.url("/mockserver/retrieve"),
            retrieve_type.as_str(),
            format.as_str(),
        );

        let mut builder = self.http.put(&url);
        builder = builder.header("Content-Type", "application/json");
        if let Some(req) = request {
            builder = builder.json(req);
        } else {
            builder = builder.body("");
        }

        let resp = builder.send()?;
        let status = resp.status().as_u16();
        match status {
            200 => Ok(resp.text()?),
            400 => Err(Error::InvalidRequest(resp.text()?)),
            _ => Err(Error::UnexpectedStatus {
                status,
                body: resp.text().unwrap_or_default(),
            }),
        }
    }
}

// ---------------------------------------------------------------------------
// ForwardChainExpectation (fluent builder)
// ---------------------------------------------------------------------------

/// Fluent builder for creating an expectation via `client.when(...).respond(...)`.
pub struct ForwardChainExpectation<'a> {
    client: &'a MockServerClient,
    request: HttpRequest,
    times: Option<Times>,
    time_to_live: Option<TimeToLive>,
    priority: Option<i32>,
    id: Option<String>,
}

impl<'a> ForwardChainExpectation<'a> {
    /// Set how many times this expectation should match.
    pub fn times(mut self, times: Times) -> Self {
        self.times = Some(times);
        self
    }

    /// Set the time-to-live for this expectation.
    pub fn time_to_live(mut self, ttl: TimeToLive) -> Self {
        self.time_to_live = Some(ttl);
        self
    }

    /// Set the priority for this expectation.
    pub fn priority(mut self, priority: i32) -> Self {
        self.priority = Some(priority);
        self
    }

    /// Set the expectation ID (for upsert semantics).
    pub fn with_id(mut self, id: impl Into<String>) -> Self {
        self.id = Some(id.into());
        self
    }

    /// Complete the expectation with a response action.
    pub fn respond(self, response: HttpResponse) -> Result<Vec<Expectation>> {
        let (client, expectation) = self.into_parts();
        let expectation = expectation.respond(response);
        client.upsert(&[expectation])
    }

    /// Complete the expectation with a forward action.
    pub fn forward(self, forward: HttpForward) -> Result<Vec<Expectation>> {
        let (client, expectation) = self.into_parts();
        let expectation = expectation.forward(forward);
        client.upsert(&[expectation])
    }

    /// Complete the expectation with an error action.
    pub fn error(self, error: HttpError) -> Result<Vec<Expectation>> {
        let (client, expectation) = self.into_parts();
        let expectation = expectation.error(error);
        client.upsert(&[expectation])
    }

    /// Complete the expectation with a Server-Sent Events (SSE) response action.
    pub fn respond_sse(self, sse: HttpSseResponse) -> Result<Vec<Expectation>> {
        let (client, expectation) = self.into_parts();
        let expectation = expectation.respond_sse(sse);
        client.upsert(&[expectation])
    }

    /// Complete the expectation with a WebSocket response action.
    pub fn respond_web_socket(self, ws: HttpWebSocketResponse) -> Result<Vec<Expectation>> {
        let (client, expectation) = self.into_parts();
        let expectation = expectation.respond_web_socket(ws);
        client.upsert(&[expectation])
    }

    /// Complete the expectation with a DNS response action.
    pub fn respond_dns(self, dns: DnsResponse) -> Result<Vec<Expectation>> {
        let (client, expectation) = self.into_parts();
        let expectation = expectation.respond_dns(dns);
        client.upsert(&[expectation])
    }

    /// Complete the expectation with a raw binary response action.
    pub fn respond_binary(self, binary: BinaryResponse) -> Result<Vec<Expectation>> {
        let (client, expectation) = self.into_parts();
        let expectation = expectation.respond_binary(binary);
        client.upsert(&[expectation])
    }

    /// Complete the expectation with a gRPC streaming response action.
    pub fn respond_grpc_stream(self, grpc: GrpcStreamResponse) -> Result<Vec<Expectation>> {
        let (client, expectation) = self.into_parts();
        let expectation = expectation.respond_grpc_stream(grpc);
        client.upsert(&[expectation])
    }

    // ------------------------------------------------------------------
    // `respond_with_*` aliases (cross-client naming parity)
    //
    // The Python, PHP, and .NET clients expose these advanced response
    // builders under `respond_with_*` names. These aliases give the Rust
    // fluent chain the same surface so examples translate verbatim across
    // clients; each simply delegates to its idiomatic Rust counterpart.
    // ------------------------------------------------------------------

    /// Alias of [`respond_sse`](Self::respond_sse) for cross-client naming parity.
    pub fn respond_with_sse(self, sse: HttpSseResponse) -> Result<Vec<Expectation>> {
        self.respond_sse(sse)
    }

    /// Alias of [`respond_web_socket`](Self::respond_web_socket) for cross-client naming parity.
    pub fn respond_with_web_socket(self, ws: HttpWebSocketResponse) -> Result<Vec<Expectation>> {
        self.respond_web_socket(ws)
    }

    /// Alias of [`respond_dns`](Self::respond_dns) for cross-client naming parity.
    pub fn respond_with_dns(self, dns: DnsResponse) -> Result<Vec<Expectation>> {
        self.respond_dns(dns)
    }

    /// Alias of [`respond_binary`](Self::respond_binary) for cross-client naming parity.
    pub fn respond_with_binary(self, binary: BinaryResponse) -> Result<Vec<Expectation>> {
        self.respond_binary(binary)
    }

    /// Alias of [`respond_grpc_stream`](Self::respond_grpc_stream) for cross-client naming parity.
    pub fn respond_with_grpc_stream(self, grpc: GrpcStreamResponse) -> Result<Vec<Expectation>> {
        self.respond_grpc_stream(grpc)
    }

    fn into_parts(self) -> (&'a MockServerClient, Expectation) {
        let ForwardChainExpectation {
            client,
            request,
            times,
            time_to_live,
            priority,
            id,
        } = self;
        let mut exp = Expectation::new(request);
        exp.times = times;
        exp.time_to_live = time_to_live;
        exp.priority = priority;
        exp.id = id;
        (client, exp)
    }
}

// ---------------------------------------------------------------------------
// Scenario (control-plane handle)
// ---------------------------------------------------------------------------

/// A handle for inspecting and driving a named scenario state-machine.
///
/// Obtained via [`MockServerClient::scenario`]. Each method issues a single
/// control-plane request against `/mockserver/scenario/{name}`.
pub struct Scenario<'a> {
    client: &'a MockServerClient,
    name: String,
}

impl Scenario<'_> {
    /// Get the scenario's current state.
    ///
    /// Sends `GET /mockserver/scenario/{name}`.
    pub fn state(&self) -> Result<String> {
        self.client.scenario_state(&self.name)
    }

    /// Set the scenario's state.
    ///
    /// Sends `PUT /mockserver/scenario/{name}` with `{"state": state}`.
    pub fn set(&self, state: &str) -> Result<()> {
        self.client.scenario_set(&self.name, state, None, None)
    }

    /// Set the scenario's state and schedule an automatic transition to
    /// `next_state` after `transition_after_ms` milliseconds.
    ///
    /// Sends `PUT /mockserver/scenario/{name}` with
    /// `{"state", "transitionAfterMs", "nextState"}`.
    pub fn set_timed(
        &self,
        state: &str,
        transition_after_ms: u64,
        next_state: &str,
    ) -> Result<()> {
        self.client.scenario_set(
            &self.name,
            state,
            Some(transition_after_ms),
            Some(next_state),
        )
    }

    /// Externally trigger a transition to `new_state`.
    ///
    /// Sends `PUT /mockserver/scenario/{name}/trigger` with `{"newState": new_state}`.
    pub fn trigger(&self, new_state: &str) -> Result<()> {
        self.client.scenario_trigger(&self.name, new_state)
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::breakpoint::tests::{
        recording_callback_server, recording_tls_callback_server, self_signed_cert,
        self_signed_localhost_cert,
    };
    use std::time::Duration;

    fn callback_upgrade_authorization(
        builder: impl FnOnce(ClientBuilder) -> ClientBuilder,
    ) -> Option<String> {
        let (port, recorded) = recording_callback_server();
        let client = builder(ClientBuilder::new("127.0.0.1", port))
            .build()
            .expect("build");
        let client_id = client
            .ensure_breakpoint_ws()
            .expect("callback WebSocket connect");
        assert_eq!(client_id, "recorded-client");
        if let Some(ws) = client.breakpoint_ws.lock().unwrap().take() {
            ws.close();
        }
        recorded
            .recv_timeout(Duration::from_secs(3))
            .expect("no upgrade recorded")
    }

    #[test]
    fn callback_websocket_sends_control_plane_bearer_token() {
        assert_eq!(
            callback_upgrade_authorization(|b| b.control_plane_bearer_token("client-token")),
            Some("Bearer client-token".to_string())
        );
    }

    #[test]
    fn callback_websocket_sends_no_authorization_without_a_token() {
        assert_eq!(callback_upgrade_authorization(|b| b), None);
    }

    fn wss_callback_upgrade(
        builder: impl FnOnce(ClientBuilder, &[u8]) -> ClientBuilder,
    ) -> Result<Option<String>> {
        wss_callback_upgrade_with(self_signed_localhost_cert(), builder)
    }

    fn wss_callback_upgrade_with(
        (cert, key): (Vec<u8>, Vec<u8>),
        builder: impl FnOnce(ClientBuilder, &[u8]) -> ClientBuilder,
    ) -> Result<Option<String>> {
        let identity = native_tls::Identity::from_pkcs8(&cert, &key).expect("identity");
        let (port, recorded) = recording_tls_callback_server(identity);
        let client = builder(ClientBuilder::new("127.0.0.1", port).secure(true), &cert)
            .control_plane_bearer_token("client-token")
            .build()
            .expect("build");
        client.ensure_breakpoint_ws()?;
        if let Some(ws) = client.breakpoint_ws.lock().unwrap().take() {
            ws.close();
        }
        Ok(recorded
            .recv_timeout(Duration::from_secs(3))
            .expect("no upgrade recorded"))
    }

    #[test]
    fn secure_client_opens_the_callback_websocket_over_wss_with_its_ca() {
        assert_eq!(
            wss_callback_upgrade(|b, cert| b.ca_cert_pem(cert.to_vec())).expect("wss connect"),
            Some("Bearer client-token".to_string())
        );
    }

    #[test]
    fn secure_client_honours_tls_verify_false_on_the_callback_websocket() {
        assert_eq!(
            wss_callback_upgrade(|b, _| b.tls_verify(false)).expect("wss connect"),
            Some("Bearer client-token".to_string())
        );
    }

    #[test]
    fn secure_client_rejects_an_untrusted_callback_websocket_certificate() {
        assert!(wss_callback_upgrade(|b, _| b).is_err());
    }

    #[test]
    fn secure_client_rejects_a_trusted_callback_websocket_certificate_for_another_host() {
        let wrong_host = || self_signed_cert("wrong-host.example", "DNS:wrong-host.example");
        assert!(
            wss_callback_upgrade_with(wrong_host(), |b, cert| b.ca_cert_pem(cert.to_vec())).is_err(),
            "a trusted certificate for another hostname was accepted with tls_verify on"
        );
        assert_eq!(
            wss_callback_upgrade_with(wrong_host(), |b, _| b.tls_verify(false))
                .expect("wss connect with tls_verify(false)"),
            Some("Bearer client-token".to_string())
        );
    }
}
