//! Integration tests that require a running MockServer.
//!
//! These tests are marked `#[ignore]` so they are skipped by default.
//! Run them with:
//!
//! ```sh
//! MOCKSERVER_URL=http://localhost:1080 cargo test -- --ignored
//! ```
//!
//! If `MOCKSERVER_URL` is not set, the tests will panic with a clear message.

use mockserver_client::*;
use std::io::{Read, Write};
use std::net::TcpStream;
use std::time::Duration;

fn get_client() -> MockServerClient {
    let url = std::env::var("MOCKSERVER_URL")
        .expect("MOCKSERVER_URL must be set (e.g., http://localhost:1080)");

    // Parse host and port from URL
    let url = url.trim_end_matches('/');
    let without_scheme = url
        .strip_prefix("http://")
        .or_else(|| url.strip_prefix("https://"))
        .unwrap_or(url);

    let (host, port_str) = without_scheme
        .rsplit_once(':')
        .expect("MOCKSERVER_URL must include port (e.g., http://localhost:1080)");
    let port: u16 = port_str.parse().expect("Port must be a valid u16");
    let secure = url.starts_with("https://");

    ClientBuilder::new(host, port)
        .secure(secure)
        .build()
        .expect("Failed to build client")
}

/// The MockServer host and port for **data-plane** requests, parsed from
/// `MOCKSERVER_URL` (the same address the control-plane client targets).
fn data_host_port() -> (String, u16) {
    let url = std::env::var("MOCKSERVER_URL")
        .expect("MOCKSERVER_URL must be set (e.g., http://localhost:1080)");
    let url = url.trim_end_matches('/');
    let without_scheme = url
        .strip_prefix("http://")
        .or_else(|| url.strip_prefix("https://"))
        .unwrap_or(url);
    let (host, port_str) = without_scheme
        .rsplit_once(':')
        .expect("MOCKSERVER_URL must include port (e.g., http://localhost:1080)");
    (host.to_string(), port_str.parse().expect("Port must be a valid u16"))
}

/// A minimal, dependency-free blocking HTTP/1.1 `GET` over a raw socket that
/// returns `(status_code, body)`.
///
/// The control-plane [`MockServerClient`] deliberately does not send data-plane
/// requests, so these wire tests drive the server directly. The response is read
/// until it is complete (see [`response_complete`]) rather than to EOF, so it
/// works whether the server closes the socket or keeps it alive after replying.
/// `extra_headers` are `(name, value)` pairs sent verbatim.
fn http_get(path: &str, extra_headers: &[(&str, &str)]) -> (u16, String) {
    let (host, port) = data_host_port();
    let mut stream =
        TcpStream::connect((host.as_str(), port)).expect("connect to MockServer data plane");
    stream
        .set_read_timeout(Some(Duration::from_secs(20)))
        .expect("set read timeout");
    stream
        .set_write_timeout(Some(Duration::from_secs(20)))
        .expect("set write timeout");

    let mut req = format!("GET {path} HTTP/1.1\r\nHost: {host}:{port}\r\nConnection: close\r\n");
    for (name, value) in extra_headers {
        req.push_str(name);
        req.push_str(": ");
        req.push_str(value);
        req.push_str("\r\n");
    }
    req.push_str("\r\n");

    stream.write_all(req.as_bytes()).expect("write request");
    stream.flush().ok();

    // Read until the response is complete rather than to EOF: some actions (e.g.
    // the error action's raw bytes) advertise a Content-Length but leave the
    // socket open, so a blind read-to-EOF would block until the timeout.
    let mut raw = Vec::new();
    let mut chunk = [0u8; 4096];
    loop {
        match stream.read(&mut chunk) {
            Ok(0) => break, // EOF — server closed
            Ok(n) => {
                raw.extend_from_slice(&chunk[..n]);
                if response_complete(&raw) {
                    break;
                }
            }
            // A read timeout is a fallback terminator: use whatever we have.
            Err(e)
                if e.kind() == std::io::ErrorKind::WouldBlock
                    || e.kind() == std::io::ErrorKind::TimedOut =>
            {
                break
            }
            Err(e) => panic!("read response failed: {e}"),
        }
    }
    let text = String::from_utf8_lossy(&raw);

    let status = text
        .split_whitespace()
        .nth(1)
        .and_then(|s| s.parse::<u16>().ok())
        .unwrap_or_else(|| panic!("could not parse status line from response: {text:?}"));
    let body = text
        .split_once("\r\n\r\n")
        .map_or(String::new(), |(_, b)| b.to_string());
    (status, body)
}

/// Whether `raw` holds a complete HTTP/1.1 response: headers terminated by a
/// blank line and, when a `Content-Length` is present, that many body bytes
/// received. Without a `Content-Length` we cannot tell, so we defer to EOF.
fn response_complete(raw: &[u8]) -> bool {
    let text = String::from_utf8_lossy(raw);
    let Some((headers, _)) = text.split_once("\r\n\r\n") else {
        return false;
    };
    let body_start = headers.len() + 4; // ASCII headers; the "\r\n\r\n" separator is 4 bytes
    for line in headers.split("\r\n") {
        let lower = line.to_ascii_lowercase();
        if let Some(rest) = lower.strip_prefix("content-length:") {
            if let Ok(len) = rest.trim().parse::<usize>() {
                return raw.len() >= body_start + len;
            }
        }
    }
    false
}

#[test]
#[ignore]
fn test_create_expectation_and_verify() {
    let client = get_client();
    client.reset().expect("reset failed");

    // Create an expectation
    client
        .when(HttpRequest::new().method("GET").path("/integration-test"))
        .respond(HttpResponse::new().status_code(200).body("integration OK"))
        .expect("creating expectation failed");

    // Retrieve active expectations
    let expectations = client
        .retrieve_active_expectations(None)
        .expect("retrieve failed");
    assert!(
        !expectations.is_empty(),
        "Should have at least one active expectation"
    );

    // Clean up
    client.reset().expect("reset failed");
}

#[test]
#[ignore]
fn test_status() {
    let client = get_client();
    let ports = client.status().expect("status failed");
    assert!(!ports.ports.is_empty(), "Server should report at least one port");
}

#[test]
#[ignore]
fn test_clear_and_reset() {
    let client = get_client();

    // Create an expectation
    client
        .when(HttpRequest::new().method("POST").path("/to-clear"))
        .respond(HttpResponse::new().status_code(201))
        .expect("creating expectation failed");

    // Clear it
    client
        .clear(
            Some(&HttpRequest::new().path("/to-clear")),
            Some(ClearType::Expectations),
        )
        .expect("clear failed");

    // Reset everything
    client.reset().expect("reset failed");
}

#[test]
#[ignore]
fn test_verify_failure() {
    let client = get_client();
    client.reset().expect("reset failed");

    // Verify something that never happened — should fail
    let result = client.verify(
        HttpRequest::new().method("DELETE").path("/never-called"),
        VerificationTimes::at_least(1),
    );

    assert!(
        result.is_err(),
        "Verification should fail for a request that was never received"
    );
    match result.unwrap_err() {
        Error::VerificationFailure(_) => {} // expected
        other => panic!("Expected VerificationFailure, got: {other}"),
    }
}

#[test]
#[ignore]
fn test_forward_expectation() {
    let client = get_client();
    client.reset().expect("reset failed");

    // Create a forward expectation (won't actually forward, just tests the API)
    client
        .when(HttpRequest::new().method("GET").path("/forward-test"))
        .forward(HttpForward::new("httpbin.org", 80).scheme("HTTP"))
        .expect("creating forward expectation failed");

    let expectations = client
        .retrieve_active_expectations(None)
        .expect("retrieve failed");
    assert!(!expectations.is_empty());

    client.reset().expect("reset failed");
}

#[test]
#[ignore]
fn test_respond_with_advanced_response_builders() {
    // Exercises the `respond_with_*` fluent aliases (cross-client naming
    // parity with the Python/PHP/.NET clients) end-to-end against a live
    // server. Each registers an expectation with one advanced response action.
    let client = get_client();
    client.reset().expect("reset failed");

    client
        .when(HttpRequest::new().method("GET").path("/sse"))
        .respond_with_sse(HttpSseResponse::new().event(SseEvent::new().data("tick")))
        .expect("respond_with_sse failed");

    client
        .when(HttpRequest::new().method("GET").path("/ws"))
        .respond_with_web_socket(
            HttpWebSocketResponse::new().message(WebSocketMessage::text("hi")),
        )
        .expect("respond_with_web_socket failed");

    client
        .when(HttpRequest::new().path("/dns"))
        .respond_with_dns(DnsResponse::new().answer_record(DnsRecord::a("host", "10.0.0.1")))
        .expect("respond_with_dns failed");

    client
        .when(HttpRequest::new().path("/raw"))
        .respond_with_binary(BinaryResponse::from_bytes([0x00, 0xFF]))
        .expect("respond_with_binary failed");

    client
        .when(HttpRequest::new().path("/grpc"))
        .respond_with_grpc_stream(
            GrpcStreamResponse::new().message(GrpcStreamMessage::json("{}")),
        )
        .expect("respond_with_grpc_stream failed");

    let expectations = client
        .retrieve_active_expectations(None)
        .expect("retrieve failed");
    assert_eq!(
        expectations.len(),
        5,
        "all five advanced-response expectations should be registered"
    );

    client.reset().expect("reset failed");
}

#[test]
#[ignore]
fn test_verify_sequence() {
    let client = get_client();
    client.reset().expect("reset failed");

    // Verify a sequence that did not happen — should fail
    let result = client.verify_sequence(vec![
        HttpRequest::new().path("/seq-1"),
        HttpRequest::new().path("/seq-2"),
    ]);

    assert!(result.is_err());
    client.reset().expect("reset failed");
}

/// Gap #43 — prove a `NottableString` **negation** is sent over the wire as a
/// negation and that the SERVER enforces it (match / no-match discrimination),
/// covering all three client forms: a bare `"!foo"`, an explicit
/// [`MatcherValue::not_literal`], and an ESCAPED literal `"!foo"` that must NOT
/// be read as a negation. Prior coverage (`matcher_value_tests.rs`) only asserted
/// JSON serialization; nothing proved the running server acts on it.
#[test]
#[ignore]
fn test_negation_matcher_enforced_over_wire() {
    let client = get_client();
    client.reset().expect("reset failed");

    // (a) Bare "!foo" header value — the client sends it as a NottableString
    // negation ("anything but foo"); the server must EXCLUDE exactly "foo".
    client
        .when(
            HttpRequest::new()
                .method("GET")
                .path("/neg-header")
                .header("X-Neg", "!foo"),
        )
        .respond(HttpResponse::new().status_code(200).body("NEG_MATCH"))
        .expect("creating bare-negation expectation failed");

    let (status_bar, body_bar) = http_get("/neg-header", &[("X-Neg", "bar")]);
    assert_eq!(
        status_bar, 200,
        "a value that is NOT 'foo' must satisfy the negation and match"
    );
    assert_eq!(body_bar, "NEG_MATCH");

    let (status_foo, _) = http_get("/neg-header", &[("X-Neg", "foo")]);
    assert_eq!(
        status_foo, 404,
        "the excluded value 'foo' must NOT match — negation enforced server-side"
    );

    client.reset().expect("reset failed");

    // (b) Explicit MatcherValue::not_literal — the same negation via the typed
    // matcher API (serialised as the bare "!foo" marker).
    client
        .when(
            HttpRequest::new()
                .method("GET")
                .path("/neg-typed")
                .header_matcher("X-Tag", MatcherValue::not_literal("foo")),
        )
        .respond(HttpResponse::new().status_code(200).body("NOT_LITERAL"))
        .expect("creating not_literal expectation failed");

    assert_eq!(
        http_get("/neg-typed", &[("X-Tag", "other")]).0,
        200,
        "not_literal('foo') must match any value other than 'foo'"
    );
    assert_eq!(
        http_get("/neg-typed", &[("X-Tag", "foo")]).0,
        404,
        "not_literal('foo') must reject 'foo'"
    );

    client.reset().expect("reset failed");

    // (c) ESCAPED literal "!foo" via MatcherValue::literal — the leading '!' is
    // sent in object form ({"not":false,"value":"!foo"}) so the server matches a
    // header whose value LITERALLY starts with '!' rather than treating it as a
    // negation. So "!foo" matches and "foo" does NOT.
    client
        .when(
            HttpRequest::new()
                .method("GET")
                .path("/neg-escaped")
                .header_matcher("X-Lit", MatcherValue::literal("!foo")),
        )
        .respond(HttpResponse::new().status_code(200).body("LITERAL_BANG"))
        .expect("creating literal-'!foo' expectation failed");

    assert_eq!(
        http_get("/neg-escaped", &[("X-Lit", "!foo")]).0,
        200,
        "an escaped literal '!foo' matcher must match the value '!foo' itself"
    );
    assert_eq!(
        http_get("/neg-escaped", &[("X-Lit", "foo")]).0,
        404,
        "'foo' must NOT match a literal '!foo' matcher — the '!' is not a negation"
    );

    client.reset().expect("reset failed");
}

/// Find the single decoded header matcher for `key` on the retrieved expectation
/// whose request path is `path`, as a [`MatcherValue`] so the caller can assert
/// on the decoded negation/escape. It looks in BOTH decoded homes: a value that
/// round-trips through the plain string form lands in the plain `headers` map (a
/// bare `"!foo"`), while a value that needs the object form to stay unambiguous
/// (an escaped `"!foo"`) lands in `header_matchers`.
fn decoded_header(expectations: &[Expectation], path: &str, key: &str) -> MatcherValue {
    let request = expectations
        .iter()
        .filter_map(|e| e.http_request.as_ref())
        .find(|r| r.path.as_deref() == Some(path))
        .unwrap_or_else(|| panic!("no expectation retrieved for path {path}"));

    // Object (nottable) form is decoded straight into `header_matchers`.
    if let Some(matcher) = request
        .header_matchers
        .as_ref()
        .and_then(|m| m.get(key))
        .and_then(|vs| vs.first())
    {
        return matcher.clone();
    }
    // Plain-string form: decode it the way the server reads a NottableString.
    let raw = request
        .headers
        .as_ref()
        .and_then(|m| m.get(key))
        .and_then(|vs| vs.first())
        .unwrap_or_else(|| panic!("header {key} not present on expectation for {path}"));
    MatcherValue::from(raw.clone())
}

/// Gap #46 — prove the Rust client correctly DECODES a server-echoed
/// `NottableString` negation/escape when an expectation is read back via
/// [`MockServerClient::retrieve_active_expectations`]. The enforcement test
/// above proves the server ACTS on the negation the client sends; this proves
/// the reverse direction — the `!` (and its escape) survives the server echo →
/// client decode intact, so a round-tripped matcher keeps its meaning. Without
/// this, a decode that dropped the negation flag or mis-read the escape would go
/// unnoticed because nothing asserted on the value read back from the server.
#[test]
#[ignore]
fn test_negation_matcher_decoded_from_server() {
    let client = get_client();
    client.reset().expect("reset failed");

    // (a) A negation: not_literal("foo") is sent as the bare "!foo" marker and
    // the server echoes it back as the plain string "!foo".
    client
        .when(
            HttpRequest::new()
                .method("GET")
                .path("/neg-decode")
                .header_matcher("X-Tag", MatcherValue::not_literal("foo")),
        )
        .respond(HttpResponse::new().status_code(200))
        .expect("creating not_literal expectation failed");

    // (c) An ESCAPED literal "!foo": the leading '!' is DATA, not a negation, so
    // the client sends the object form and the server echoes the object back.
    client
        .when(
            HttpRequest::new()
                .method("GET")
                .path("/esc-decode")
                .header_matcher("X-Lit", MatcherValue::literal("!foo")),
        )
        .respond(HttpResponse::new().status_code(200))
        .expect("creating escaped-literal expectation failed");

    let expectations = client
        .retrieve_active_expectations(None)
        .expect("retrieve failed");

    // The negation must decode back to not=true / value="foo": the '!' survived
    // the server echo and the client read it as a negation, not as literal data.
    let neg = decoded_header(&expectations, "/neg-decode", "X-Tag");
    assert!(
        neg.not,
        "the '!' negation flag must survive the server echo → client decode (got {neg:?})"
    );
    assert_eq!(
        neg,
        MatcherValue::not_literal("foo"),
        "server-echoed negation must decode to not=true, value=foo (got {neg:?})"
    );

    // The escaped literal must decode back to not=false / value="!foo": the '!'
    // is preserved as data and was NOT misread as a negation.
    let esc = decoded_header(&expectations, "/esc-decode", "X-Lit");
    assert!(
        !esc.not,
        "an escaped '!foo' must NOT be decoded as a negation (got {esc:?})"
    );
    assert_eq!(
        esc.value, "!foo",
        "the escaped '!' must survive the round-trip as literal data (got {esc:?})"
    );
    assert_eq!(
        esc,
        MatcherValue::literal("!foo"),
        "server-echoed escaped literal must decode to not=false, value=!foo (got {esc:?})"
    );

    client.reset().expect("reset failed");
}

/// Gap #46 — prove a FORWARD action registered via the Rust client is ACTUALLY
/// performed by the server, with no external upstream so it is topology-independent
/// (works whether the test runs on the host or in a sibling container). The prior
/// `test_forward_expectation` only registered the expectation ("won't actually forward").
///
/// The upstream is the server's OWN loopback. Two expectations are registered on
/// the same path:
///   * a higher-priority, run-once FORWARD to `127.0.0.1:<server-internal-port>`, and
///   * a lower-priority RESPOND returning a distinctive body.
///
/// The first request matches the forward, which loops the request back to the
/// same server; by then the run-once forward is consumed, so the looped-back
/// request falls through to the RESPOND and returns the distinctive body. The
/// caller therefore sees that body only if the forward genuinely executed —
/// confirmed by the positive control (a dead forward port yields 502, not the body).
#[test]
#[ignore]
fn test_forward_action_actually_forwards() {
    let client = get_client();
    client.reset().expect("reset failed");

    // The server-internal port to loop back to. Taken from the server's own
    // status so it is the port MockServer is bound to inside its network
    // namespace, independent of any host port mapping.
    let internal_port = *client
        .status()
        .expect("status failed")
        .ports
        .first()
        .expect("server should report at least one bound port");

    // Higher-priority, run-once forward that loops back to this same server.
    client
        .when(HttpRequest::new().method("GET").path("/forward-loop"))
        .priority(10)
        .times(Times::once())
        .forward(HttpForward::new("127.0.0.1", internal_port).scheme("HTTP"))
        .expect("creating loopback forward expectation failed");

    // Lower-priority fall-through the looped-back request lands on.
    client
        .when(HttpRequest::new().method("GET").path("/forward-loop"))
        .priority(0)
        .respond(HttpResponse::new().status_code(200).body("UPSTREAM_REACHED"))
        .expect("creating fall-through respond expectation failed");

    let (status, body) = http_get("/forward-loop", &[]);
    assert_eq!(status, 200, "looped-back forward response status");
    assert_eq!(
        body, "UPSTREAM_REACHED",
        "body must come from the fall-through reached VIA the loopback forward, \
         proving the server actually forwarded the request"
    );

    client.reset().expect("reset failed");
}

/// Gap #46 — a fully self-contained proof (no upstream) that an ERROR action
/// registered via the Rust client is ACTUALLY performed: the server writes the
/// configured raw bytes back to the client. Portable to any topology.
#[test]
#[ignore]
fn test_error_action_actually_returns_raw_bytes() {
    let client = get_client();
    client.reset().expect("reset failed");

    // base64 of the raw HTTP/1.1 response bytes:
    //   "HTTP/1.1 418 Teapot\r\nContent-Length: 8\r\nConnection: close\r\n\r\nTEAPOT!!"
    const TEAPOT_RESPONSE_B64: &str =
        "SFRUUC8xLjEgNDE4IFRlYXBvdA0KQ29udGVudC1MZW5ndGg6IDgNCkNvbm5lY3Rpb246IGNsb3NlDQoNClRFQVBPVCEh";

    client
        .when(HttpRequest::new().method("GET").path("/error-drive"))
        .error(HttpError::new().response_bytes(TEAPOT_RESPONSE_B64))
        .expect("creating error expectation failed");

    let (status, body) = http_get("/error-drive", &[]);
    assert_eq!(
        status, 418,
        "server must actually perform the error action and write the raw bytes back"
    );
    assert_eq!(body, "TEAPOT!!");

    client.reset().expect("reset failed");
}

/// Gap #47 — DRIVE a request against a registered `httpResponseTemplate` and
/// assert the RENDERED body served over the wire, not merely that the template
/// expectation was accepted (which `test_respond_with_advanced_response_builders`
/// already covers by create+retrieve). A VELOCITY template that echoes
/// `$!{request.path}` into the body proves two things a schema round-trip cannot:
/// the server actually ran the template engine, and the template had live access
/// to the matched request. A client that mis-encoded the template action, or a
/// server that ignored it, would serve a 404 or an unrendered body — reddening
/// the exact-string assertion below.
#[test]
#[ignore]
fn test_response_template_rendered_over_wire() {
    let client = get_client();
    client.reset().expect("reset failed");

    // The template renders a JSON object describing the response; the server
    // maps it to an HttpResponse. `$!{request.path}` is substituted from the
    // matched request, so the served body is a function of the wire request.
    let template =
        r#"{"statusCode": 200, "body": "TEMPLATED path=$!{request.path}"}"#;
    let expectation = Expectation::new(HttpRequest::new().method("GET").path("/tmpl-wire"))
        .respond_template(HttpTemplate::new("VELOCITY", template));
    client
        .upsert(&[expectation])
        .expect("registering response-template expectation failed");

    let (status, body) = http_get("/tmpl-wire", &[]);
    assert_eq!(
        status, 200,
        "the response template must be executed and produce a 200"
    );
    assert_eq!(
        body, "TEMPLATED path=/tmpl-wire",
        "the served body must be the VELOCITY-rendered template with the request path \
         substituted — proving the server ran the template engine over the live request"
    );

    client.reset().expect("reset failed");
}

/// Gap #47 — DRIVE a request against a registered `httpResponseTemplate` using a
/// second, distinct engine (MUSTACHE) and assert the RENDERED body over the
/// wire. Pairing this with the VELOCITY test above proves the client encodes the
/// template action for BOTH engines the server exposes and that each engine
/// actually runs against the live request — `{{request.method}}` is substituted
/// from the matched request, so a client that mis-set the `templateType`, or a
/// server that skipped the engine, would serve a 404 or unrendered body.
#[test]
#[ignore]
fn test_mustache_response_template_rendered_over_wire() {
    let client = get_client();
    client.reset().expect("reset failed");

    let template =
        r#"{ "statusCode": 200, "body": "MUSTACHE method={{request.method}}" }"#;
    let expectation = Expectation::new(HttpRequest::new().method("GET").path("/mustache-wire"))
        .respond_template(HttpTemplate::new("MUSTACHE", template));
    client
        .upsert(&[expectation])
        .expect("registering mustache response-template expectation failed");

    let (status, body) = http_get("/mustache-wire", &[]);
    assert_eq!(
        status, 200,
        "the MUSTACHE response template must be executed and produce a 200"
    );
    assert_eq!(
        body, "MUSTACHE method=GET",
        "the served body must be the MUSTACHE-rendered template with the request method \
         substituted — proving the server ran the MUSTACHE engine over the live request"
    );

    client.reset().expect("reset failed");
}

/// Retrieve filtered by expectation id (GitHub issue #2591).
///
/// Two expectations match the same request, so this also pins the distinction that
/// matters: `retrieve_active_expectations_by_id` returns only the expectation with that
/// id, while `retrieve_active_expectations` with the same request matcher returns both.
#[test]
#[ignore]
fn test_retrieve_by_expectation_id() {
    let client = get_client();
    client.reset().expect("reset failed");

    client
        .upsert(&[Expectation::new(HttpRequest::new().path("/retrieve-by-id"))
            .id("retrieve-by-id-one")
            .respond(HttpResponse::new().status_code(200).body("one"))])
        .expect("creating expectation one failed");
    client
        .upsert(&[Expectation::new(HttpRequest::new().path("/retrieve-by-id"))
            .id("retrieve-by-id-two")
            .respond(HttpResponse::new().status_code(200).body("two"))])
        .expect("creating expectation two failed");

    let by_id = client
        .retrieve_active_expectations_by_id("retrieve-by-id-one")
        .expect("retrieve by id failed");
    assert_eq!(by_id.len(), 1, "expected only the expectation with that id");
    assert_eq!(by_id[0].id.as_deref(), Some("retrieve-by-id-one"));

    let by_matcher = client
        .retrieve_active_expectations(Some(&HttpRequest::new().path("/retrieve-by-id")))
        .expect("retrieve by matcher failed");
    assert_eq!(
        by_matcher.len(),
        2,
        "the request matcher matches both expectations"
    );

    // an unknown id is rejected rather than silently matching everything
    assert!(
        client
            .retrieve_active_expectations_by_id("no-such-expectation-id")
            .is_err(),
        "an unknown expectation id should be rejected"
    );

    client.reset().expect("reset failed");
}

/// The correlation id of the received `GET <path>`, read from the server's log entries.
fn correlation_id_of(path: &str) -> String {
    let url = std::env::var("MOCKSERVER_URL").expect("MOCKSERVER_URL must be set");
    let entries: serde_json::Value = reqwest::blocking::Client::new()
        .put(format!(
            "{}/mockserver/retrieve?type=LOGS&format=LOG_ENTRIES",
            url.trim_end_matches('/')
        ))
        .send()
        .expect("retrieve log entries")
        .json()
        .expect("log entries are JSON");
    entries
        .as_array()
        .expect("log entries are an array")
        .iter()
        .find(|e| {
            e["type"] == "RECEIVED_REQUEST"
                && e["httpRequest"]["method"] == "GET"
                && e["httpRequest"]["path"] == path
        })
        .and_then(|e| e["correlationId"].as_str())
        .unwrap_or_else(|| panic!("no received GET {path} in the log"))
        .to_string()
}

#[test]
#[ignore]
fn test_matching_diagnostics() {
    let client = get_client();
    client.reset().expect("reset failed");

    client
        .upsert(&[
            Expectation::new(
                HttpRequest::new()
                    .method("GET")
                    .path("/diag-orders")
                    .header("X-Tenant", "gold"),
            )
            .id("diag-gold")
            .priority(10)
            .respond(HttpResponse::new().status_code(200)),
            Expectation::new(HttpRequest::new().method("GET").path("/diag-orders"))
                .id("diag-catch-all")
                .respond(HttpResponse::new().status_code(200)),
        ])
        .expect("creating expectations failed");
    assert_eq!(http_get("/diag-orders", &[("X-Tenant", "silver")]).0, 200);
    assert_eq!(http_get("/diag-order", &[]).0, 404);

    let explanation = client
        .explain(&correlation_id_of("/diag-orders"))
        .expect("explain failed");
    assert_eq!(
        explanation
            .matched_expectation
            .as_ref()
            .and_then(|m| m.expectation_id.as_deref()),
        Some("diag-catch-all")
    );
    assert_eq!(explanation.skipped_expectations.len(), 1);
    let skipped = &explanation.skipped_expectations[0];
    assert_eq!(skipped.expectation_id.as_deref(), Some("diag-gold"));
    assert_eq!(skipped.reason, Some(ExplainReason::FieldsDiffer));
    assert!(skipped.differences.contains_key("headers"));
    assert_eq!(
        explanation.at_arrival.and_then(|a| a.outcome),
        Some(ArrivalOutcome::Matched)
    );
    assert!(matches!(
        client.explain("no-such-correlation-id"),
        Err(Error::NotFound(_))
    ));

    let report = client
        .debug_mismatch(
            HttpRequest::new()
                .method("GET")
                .path("/diag-orders")
                .header("X-Tenant", "silver"),
        )
        .expect("debug_mismatch failed");
    assert_eq!(report.total_expectations, 2);
    assert_eq!(report.results.len(), 2);
    assert_eq!(
        report
            .closest_match
            .and_then(|c| c.expectation_id)
            .as_deref(),
        Some("diag-gold")
    );

    let unmatched = client
        .explain_unmatched(Some(
            &ExplainUnmatchedOptions::new().limit(5).suggest_fixes(true),
        ))
        .expect("explain_unmatched failed");
    assert_eq!(unmatched.unmatched_requests.len(), 1);
    let request = &unmatched.unmatched_requests[0];
    assert_eq!(request.path.as_deref(), Some("/diag-order"));
    assert_eq!(request.closest_expectations.len(), 2);
    let fix = request.closest_expectations[0]
        .suggested_fix
        .as_ref()
        .expect("the closest expectation has a suggested fix");
    assert!(!fix.patch.is_empty());

    client.reset().expect("reset failed");
}

// ---------------------------------------------------------------------------
// SSE, LLM and binary response actions driven over the wire
// ---------------------------------------------------------------------------

/// A raw HTTP/1.1 exchange returning `(status, lower-cased header block, body)`.
/// Unlike [`http_get`] it keeps the body as bytes, sends an optional body, and
/// de-chunks a `Transfer-Encoding: chunked` response (SSE responses are chunked).
fn http_exchange(method: &str, path: &str, body: Option<&str>) -> (u16, String, Vec<u8>) {
    let (host, port) = data_host_port();
    let mut stream =
        TcpStream::connect((host.as_str(), port)).expect("connect to MockServer data plane");
    stream
        .set_read_timeout(Some(Duration::from_secs(20)))
        .expect("set read timeout");

    let mut req =
        format!("{method} {path} HTTP/1.1\r\nHost: {host}:{port}\r\nConnection: close\r\n");
    if let Some(b) = body {
        req.push_str(&format!(
            "Content-Type: application/json\r\nContent-Length: {}\r\n",
            b.len()
        ));
    }
    req.push_str("\r\n");
    req.push_str(body.unwrap_or(""));
    stream.write_all(req.as_bytes()).expect("write request");

    let mut raw = Vec::new();
    let mut chunk = [0u8; 4096];
    loop {
        match stream.read(&mut chunk) {
            Ok(0) => break,
            Ok(n) => {
                raw.extend_from_slice(&chunk[..n]);
                if response_complete(&raw) || raw.ends_with(b"\r\n0\r\n\r\n") {
                    break;
                }
            }
            Err(e)
                if e.kind() == std::io::ErrorKind::WouldBlock
                    || e.kind() == std::io::ErrorKind::TimedOut =>
            {
                panic!(
                    "timed out before the response completed; partial response: {:?}",
                    String::from_utf8_lossy(&raw)
                )
            }
            Err(e) => panic!("read response failed: {e}"),
        }
    }

    let split = raw
        .windows(4)
        .position(|w| w == b"\r\n\r\n")
        .unwrap_or_else(|| {
            panic!(
                "no header terminator in {:?}",
                String::from_utf8_lossy(&raw)
            )
        });
    let headers = String::from_utf8_lossy(&raw[..split]).to_ascii_lowercase();
    let status = headers
        .split_whitespace()
        .nth(1)
        .and_then(|s| s.parse::<u16>().ok())
        .unwrap_or_else(|| panic!("could not parse status line: {headers:?}"));
    let mut body_bytes = raw[split + 4..].to_vec();
    if headers.contains("transfer-encoding: chunked") {
        body_bytes = dechunk(&body_bytes);
    }
    (status, headers, body_bytes)
}

fn dechunk(mut data: &[u8]) -> Vec<u8> {
    let mut out = Vec::new();
    while let Some(eol) = data.windows(2).position(|w| w == b"\r\n") {
        let size_field = String::from_utf8_lossy(&data[..eol]);
        let size = usize::from_str_radix(size_field.split(';').next().unwrap().trim(), 16)
            .unwrap_or_else(|_| panic!("bad chunk size {size_field:?}"));
        if size == 0 {
            break;
        }
        let start = eol + 2;
        assert!(
            start + size <= data.len(),
            "truncated chunk: size {size} but only {} bytes remain: {:?}",
            data.len() - start,
            String::from_utf8_lossy(&data[start..])
        );
        out.extend_from_slice(&data[start..start + size]);
        data = &data[(start + size + 2).min(data.len())..];
    }
    out
}

/// Parses a `text/event-stream` body into `(id, event, data, retry)` frames.
fn parse_sse(body: &str) -> Vec<(String, String, String, String)> {
    body.replace("\r\n", "\n")
        .split("\n\n")
        .filter(|frame| !frame.trim().is_empty())
        .map(|frame| {
            let (mut id, mut event, mut data, mut retry) =
                (String::new(), String::new(), String::new(), String::new());
            for line in frame.lines() {
                if let Some((name, value)) = line.split_once(':') {
                    let value = value.strip_prefix(' ').unwrap_or(value).to_string();
                    match name {
                        "id" => id = value,
                        "event" => event = value,
                        "data" => data = value,
                        "retry" => retry = value,
                        _ => {}
                    }
                }
            }
            (id, event, data, retry)
        })
        .collect()
}

#[test]
#[ignore]
fn test_sse_response_streamed_over_wire() {
    let client = get_client();
    client.reset().expect("reset failed");

    client
        .when(HttpRequest::new().method("GET").path("/rustwire-sse"))
        .respond_with_sse(
            HttpSseResponse::new()
                .status_code(200)
                .event(SseEvent::new().id("1").event("greet").data("hello-rust"))
                .event(
                    SseEvent::new()
                        .id("42")
                        .event("update")
                        .data("world-rust")
                        .retry(3000),
                )
                .close_connection(true),
        )
        .expect("registering SSE expectation failed");

    let (status, headers, body) = http_exchange("GET", "/rustwire-sse", None);
    assert_eq!(status, 200);
    assert!(
        headers.contains("content-type: text/event-stream"),
        "SSE must be served as text/event-stream, headers: {headers}"
    );
    let s = |v: &str| v.to_string();
    assert_eq!(
        parse_sse(&String::from_utf8_lossy(&body)),
        vec![
            (s("1"), s("greet"), s("hello-rust"), s("")),
            (s("42"), s("update"), s("world-rust"), s("3000")),
        ],
        "the server must stream exactly the registered SSE events"
    );

    client.reset().expect("reset failed");
}

#[test]
#[ignore]
fn test_llm_response_serves_openai_envelope_over_wire() {
    use mockserver_client::llm::{llm_mock, Completion, Provider, Usage};

    let client = get_client();
    client.reset().expect("reset failed");

    llm_mock("/rustwire-llm/v1/chat/completions")
        .with_provider(Provider::OPENAI)
        .with_model("gpt-4o-rustwire")
        .responding_with(
            Completion::new()
                .with_text("Hello from Rust")
                .with_stop_reason("stop")
                .with_usage(Usage::new().with_input_tokens(9).with_output_tokens(3)),
        )
        .apply_to(&client)
        .expect("registering LLM expectation failed");

    let (status, headers, body) = http_exchange(
        "POST",
        "/rustwire-llm/v1/chat/completions",
        Some(r#"{"model":"gpt-4o-rustwire","messages":[{"role":"user","content":"hi"}]}"#),
    );
    assert_eq!(status, 200, "body: {}", String::from_utf8_lossy(&body));
    assert!(
        headers.contains("content-type: application/json"),
        "headers: {headers}"
    );

    let envelope: serde_json::Value =
        serde_json::from_slice(&body).expect("LLM response body must be JSON");
    assert_eq!(
        envelope["object"], "chat.completion",
        "not an OpenAI envelope: {envelope}"
    );
    assert!(
        envelope["id"]
            .as_str()
            .unwrap_or("")
            .starts_with("chatcmpl-"),
        "{envelope}"
    );
    assert_eq!(envelope["model"], "gpt-4o-rustwire");
    let choices = envelope["choices"].as_array().expect("choices array");
    assert_eq!(choices.len(), 1, "{envelope}");
    assert_eq!(choices[0]["message"]["role"], "assistant");
    assert_eq!(choices[0]["message"]["content"], "Hello from Rust");
    assert_eq!(choices[0]["finish_reason"], "stop");
    assert_eq!(envelope["usage"]["prompt_tokens"], 9);
    assert_eq!(envelope["usage"]["completion_tokens"], 3);
    assert_eq!(envelope["usage"]["total_tokens"], 12);

    client.reset().expect("reset failed");
}

/// A `binaryResponse` action answers a raw, non-HTTP message: the server writes
/// the registered octets (including NUL and 0xFF) back on the same socket. The
/// request matcher is a binary one (`httpRequest.binaryData`).
#[test]
#[ignore]
fn test_binary_response_served_over_wire() {
    let client = get_client();
    client.reset().expect("reset failed");

    let request_bytes: &[u8] = b"\x00\x01RUSTPING\xff";
    let response_bytes: &[u8] = b"\x00\x02RUSTPONG\xfe";
    client
        .when(HttpRequest::new().binary_data(request_bytes))
        .respond_with_binary(BinaryResponse::from_bytes(response_bytes))
        .expect("registering binary expectation failed");

    let (host, port) = data_host_port();
    let mut stream = TcpStream::connect((host.as_str(), port)).expect("connect");
    stream
        .set_read_timeout(Some(Duration::from_secs(10)))
        .expect("set read timeout");
    stream
        .write_all(request_bytes)
        .expect("write binary message");
    let mut received = Vec::new();
    let mut chunk = [0u8; 256];
    while received.len() < response_bytes.len() {
        match stream.read(&mut chunk) {
            Ok(0) => break,
            Ok(n) => received.extend_from_slice(&chunk[..n]),
            Err(e) => panic!("reading binary reply failed after {received:?}: {e}"),
        }
    }
    assert_eq!(
        received, response_bytes,
        "the server must write back exactly the registered binaryResponse octets"
    );

    client.reset().expect("reset failed");
}

/// An `httpResponse` with a `BINARY` body: the HTTP response body is exactly the
/// registered bytes (including NUL, 0x7F, 0x80 and 0xFF, which a string body
/// would corrupt), with the registered `Content-Type`.
#[test]
#[ignore]
fn test_http_response_binary_body_served_over_wire() {
    let client = get_client();
    client.reset().expect("reset failed");

    let body_bytes: &[u8] = b"\x00\x01RUST\x7f\x80BIN\xfe\xff\x00";
    client
        .when(
            HttpRequest::new()
                .method("GET")
                .path("/rustwire-binary-body"),
        )
        .respond(
            HttpResponse::new()
                .status_code(200)
                .binary_body(body_bytes, Some("application/octet-stream".to_string())),
        )
        .expect("registering binary-body expectation failed");

    let (status, headers, body) = http_exchange("GET", "/rustwire-binary-body", None);
    assert_eq!(status, 200, "headers: {headers}");
    assert!(
        headers.contains("content-type: application/octet-stream"),
        "headers: {headers}"
    );
    assert_eq!(
        body, body_bytes,
        "the server must send exactly the registered BINARY body bytes"
    );

    let retrieved = client
        .retrieve_active_expectations(Some(
            &HttpRequest::new()
                .method("GET")
                .path("/rustwire-binary-body"),
        ))
        .expect("retrieving the binary-body expectation failed");
    assert_eq!(retrieved.len(), 1);
    let response = retrieved[0]
        .http_response
        .as_ref()
        .expect("the retrieved expectation has an httpResponse action");
    assert_eq!(
        response.typed_body(),
        Some(Body::binary(
            body_bytes,
            Some("application/octet-stream".to_string())
        )),
        "the retrieved expectation must read back the same BINARY body"
    );

    client.reset().expect("reset failed");
}

// ---------------------------------------------------------------------------
// Callback, WebSocket, DNS and gRPC response actions driven over the wire
// ---------------------------------------------------------------------------

/// The MockServer DNS (UDP) port, from `MOCKSERVER_DNS_PORT`. The server must
/// run with `dnsEnabled=true` on that port.
fn dns_port() -> u16 {
    std::env::var("MOCKSERVER_DNS_PORT")
        .expect("MOCKSERVER_DNS_PORT must be set to the server's UDP dnsPort (e.g., 1053)")
        .parse()
        .expect("MOCKSERVER_DNS_PORT must be a valid u16")
}

/// An `httpResponseClassCallback` naming a class on the server's classpath:
/// `OidcDiscoveryCallback` ships in the MockServer jar and builds the OIDC
/// discovery document from the configured provider, so the served JSON proves
/// the server loaded and ran exactly the named class.
#[test]
#[ignore]
fn test_class_callback_invoked_over_wire() {
    let client = get_client();
    client.reset().expect("reset failed");

    let (host, port) = data_host_port();
    let oidc = reqwest::blocking::Client::new()
        .put(format!("http://{host}:{port}/mockserver/oidc"))
        .header("Content-Type", "application/json")
        .body(r#"{"issuer":"https://rustwire.example","jwksPath":"/rustwire/jwks"}"#)
        .send()
        .expect("configuring the OIDC provider failed");
    assert_eq!(oidc.status().as_u16(), 201, "{:?}", oidc.text());

    client
        .upsert(&[Expectation::new(
            HttpRequest::new()
                .method("GET")
                .path("/rustwire-class-callback"),
        )
        .respond_class_callback(HttpClassCallback::new(
            "org.mockserver.oidc.OidcDiscoveryCallback",
        ))])
        .expect("registering class-callback expectation failed");

    let (status, headers, body) = http_exchange("GET", "/rustwire-class-callback", None);
    assert_eq!(status, 200, "body: {}", String::from_utf8_lossy(&body));
    assert!(
        headers.contains("content-type: application/json"),
        "headers: {headers}"
    );
    let discovery: serde_json::Value =
        serde_json::from_slice(&body).expect("class callback response must be JSON");
    assert_eq!(
        discovery["issuer"], "https://rustwire.example",
        "{discovery}"
    );
    assert_eq!(
        discovery["jwks_uri"],
        "https://rustwire.example/rustwire/jwks"
    );
    assert_eq!(
        discovery["token_endpoint"],
        "https://rustwire.example/token"
    );
    assert_eq!(
        discovery["response_types_supported"],
        serde_json::json!(["code"])
    );

    client.reset().expect("reset failed");
}

/// An `httpResponseObjectCallback`: the server pushes the matched request to
/// this client over the callback WebSocket and serves what the closure returns.
#[test]
#[ignore]
fn test_object_callback_invoked_over_wire() {
    let client = get_client();
    client.reset().expect("reset failed");

    client
        .mock_with_callback(
            HttpRequest::new()
                .method("GET")
                .path("/rustwire-object-callback"),
            |req| {
                let probe = req
                    .headers
                    .as_ref()
                    .and_then(|h| {
                        h.iter()
                            .find(|(k, _)| k.eq_ignore_ascii_case("x-probe"))
                            .and_then(|(_, v)| v.first().cloned())
                    })
                    .unwrap_or_else(|| "<missing>".into());
                HttpResponse::new()
                    .status_code(202)
                    .header("x-rustwire-callback", "closure")
                    .body(format!(
                        "{}|{}|{}",
                        req.method.unwrap_or_default(),
                        req.path.unwrap_or_default(),
                        probe
                    ))
            },
        )
        .expect("registering object-callback expectation failed");

    let (status, body) = http_get("/rustwire-object-callback", &[("x-probe", "p-47")]);
    assert_eq!(status, 202, "body: {body}");
    assert_eq!(
        body, "GET|/rustwire-object-callback|p-47",
        "the server must serve the closure's response built from the forwarded request"
    );

    client.close_breakpoint_websocket();
    client.reset().expect("reset failed");
}

fn web_socket_connect(
    path: &str,
    subprotocol: Option<&str>,
) -> (
    tungstenite::WebSocket<tungstenite::stream::MaybeTlsStream<TcpStream>>,
    tungstenite::handshake::client::Response,
) {
    use tungstenite::client::IntoClientRequest;

    let (host, port) = data_host_port();
    let mut request = format!("ws://{host}:{port}{path}")
        .into_client_request()
        .expect("WebSocket request");
    if let Some(protocol) = subprotocol {
        request.headers_mut().insert(
            "Sec-WebSocket-Protocol",
            protocol.parse().expect("subprotocol header"),
        );
    }
    let (socket, response) = tungstenite::connect(request).expect("WebSocket handshake failed");
    if let tungstenite::stream::MaybeTlsStream::Plain(stream) = socket.get_ref() {
        stream
            .set_read_timeout(Some(Duration::from_secs(10)))
            .expect("set read timeout");
    }
    (socket, response)
}

/// An `httpWebSocketResponse` with scripted messages: the server upgrades,
/// agrees the registered subprotocol, sends exactly the text and binary frames
/// registered, then closes with 1000.
#[test]
#[ignore]
fn test_web_socket_messages_streamed_over_wire() {
    use tungstenite::Message;

    let client = get_client();
    client.reset().expect("reset failed");

    client
        .when(HttpRequest::new().method("GET").path("/rustwire-ws"))
        .respond_with_web_socket(
            HttpWebSocketResponse::new()
                .subprotocol("rustwire.v1")
                .message(WebSocketMessage::text("hello-rust"))
                .message(WebSocketMessage::binary([0x00, 0x01, 0xfe, 0xff]))
                .message(WebSocketMessage::text("bye-rust"))
                .close_connection(true),
        )
        .expect("registering WebSocket expectation failed");

    let (mut socket, response) = web_socket_connect("/rustwire-ws", Some("rustwire.v1"));
    assert_eq!(response.status().as_u16(), 101);
    assert_eq!(
        response
            .headers()
            .get("sec-websocket-protocol")
            .map(|v| v.to_str().unwrap_or_default()),
        Some("rustwire.v1"),
        "the server must agree the registered subprotocol"
    );

    let mut frames = Vec::new();
    let close = loop {
        match socket.read() {
            Ok(Message::Close(frame)) => break frame,
            Ok(message @ (Message::Text(_) | Message::Binary(_))) => frames.push(message),
            Ok(_) => {}
            Err(e) => panic!("WebSocket read failed after {frames:?}: {e}"),
        }
    };
    assert_eq!(
        frames,
        vec![
            Message::text("hello-rust"),
            Message::binary(vec![0x00, 0x01, 0xfe, 0xff]),
            Message::text("bye-rust"),
        ],
        "the server must send exactly the registered frames in order"
    );
    assert_eq!(
        close.map(|f| u16::from(f.code)),
        Some(1000),
        "closeConnection must end with a normal closure"
    );

    client.reset().expect("reset failed");
}

/// An `httpWebSocketResponse` with an incoming-frame matcher: a text frame
/// matching the rule gets the rule's reply, and one that does not gets nothing.
#[test]
#[ignore]
fn test_web_socket_matcher_replies_over_wire() {
    use tungstenite::Message;

    let client = get_client();
    client.reset().expect("reset failed");

    client
        .when(
            HttpRequest::new()
                .method("GET")
                .path("/rustwire-ws-matcher"),
        )
        .respond_with_web_socket(
            HttpWebSocketResponse::new().matcher(
                WebSocketMatcher::new()
                    .frame_type("TEXT")
                    .text_matcher("ping-rust")
                    .response(WebSocketMessage::text("pong-rust")),
            ),
        )
        .expect("registering WebSocket matcher expectation failed");

    let (mut socket, _) = web_socket_connect("/rustwire-ws-matcher", None);
    socket
        .send(Message::text("ping-rust"))
        .expect("sending matching frame failed");
    let reply = loop {
        match socket.read().expect("reading the matcher reply failed") {
            message @ (Message::Text(_) | Message::Binary(_)) => break message,
            Message::Close(frame) => panic!("closed before replying: {frame:?}"),
            _ => {}
        }
    };
    assert_eq!(reply, Message::text("pong-rust"));

    if let tungstenite::stream::MaybeTlsStream::Plain(stream) = socket.get_ref() {
        stream
            .set_read_timeout(Some(Duration::from_secs(2)))
            .expect("set read timeout");
    }
    socket
        .send(Message::text("other-rust"))
        .expect("sending non-matching frame failed");
    match socket.read() {
        Err(tungstenite::Error::Io(e))
            if e.kind() == std::io::ErrorKind::WouldBlock
                || e.kind() == std::io::ErrorKind::TimedOut => {}
        other => panic!("a frame matching no rule must get no reply, got {other:?}"),
    }
    socket.close(None).ok();

    client.reset().expect("reset failed");
}

fn dns_encode_name(out: &mut Vec<u8>, name: &str) {
    for label in name.trim_end_matches('.').split('.') {
        out.push(label.len() as u8);
        out.extend_from_slice(label.as_bytes());
    }
    out.push(0);
}

/// Reads a possibly compressed DNS name at `pos`, returning it and the offset
/// just past it in the original record.
fn dns_read_name(msg: &[u8], mut pos: usize) -> (String, usize) {
    let mut labels = Vec::new();
    let mut end = None;
    loop {
        let len = msg[pos] as usize;
        if len & 0xc0 == 0xc0 {
            end.get_or_insert(pos + 2);
            pos = ((len & 0x3f) << 8) | msg[pos + 1] as usize;
        } else if len == 0 {
            return (labels.join("."), end.unwrap_or(pos + 1));
        } else {
            labels.push(String::from_utf8_lossy(&msg[pos + 1..pos + 1 + len]).into_owned());
            pos += 1 + len;
        }
    }
}

/// One answer-section record: `(name, type, class, ttl, rdata)`.
type DnsAnswer = (String, u16, u16, u32, Vec<u8>);

/// Sends one A/IN query over UDP and returns `(rcode, answers)`.
fn dns_query(name: &str, id: u16) -> (u8, Vec<DnsAnswer>) {
    let (host, _) = data_host_port();
    let socket = std::net::UdpSocket::bind("0.0.0.0:0").expect("bind UDP socket");
    socket
        .set_read_timeout(Some(Duration::from_secs(3)))
        .expect("set read timeout");
    socket
        .connect((host.as_str(), dns_port()))
        .expect("connect UDP socket to the DNS port");

    let mut query = Vec::new();
    query.extend_from_slice(&id.to_be_bytes());
    query.extend_from_slice(&[0x01, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00]);
    dns_encode_name(&mut query, name);
    query.extend_from_slice(&[0x00, 0x01, 0x00, 0x01]);

    let mut buf = [0u8; 1500];
    let len = (0..3)
        .find_map(|_| {
            socket.send(&query).expect("send DNS query");
            socket.recv(&mut buf).ok()
        })
        .unwrap_or_else(|| panic!("no DNS answer for {name} after 3 attempts"));
    let msg = &buf[..len];

    assert!(len >= 12, "short DNS message: {msg:?}");
    assert_eq!(
        u16::from_be_bytes([msg[0], msg[1]]),
        id,
        "answer must echo the query id"
    );
    assert_eq!(msg[2] & 0x80, 0x80, "QR must mark a response: {msg:?}");
    let rcode = msg[3] & 0x0f;
    let qdcount = u16::from_be_bytes([msg[4], msg[5]]);
    let ancount = u16::from_be_bytes([msg[6], msg[7]]);

    let mut pos = 12;
    for _ in 0..qdcount {
        pos = dns_read_name(msg, pos).1 + 4;
    }
    let answers = (0..ancount)
        .map(|_| {
            let (record_name, next) = dns_read_name(msg, pos);
            let field = |at: usize| u16::from_be_bytes([msg[at], msg[at + 1]]);
            let ttl =
                u32::from_be_bytes([msg[next + 4], msg[next + 5], msg[next + 6], msg[next + 7]]);
            let rdlength = field(next + 8) as usize;
            let rdata = msg[next + 10..next + 10 + rdlength].to_vec();
            let answer = (record_name, field(next), field(next + 2), ttl, rdata);
            pos = next + 10 + rdlength;
            answer
        })
        .collect();
    (rcode, answers)
}

/// A `dnsResponse` action answering a real UDP DNS query: the answer section
/// holds exactly the registered records (type, class, TTL, address) and the
/// registered response code is the one sent. The DNS request matcher
/// (`dnsName`/`dnsType`) is not modelled by [`HttpRequest`], so it rides in
/// the `extra` catch-all.
#[test]
#[ignore]
fn test_dns_response_answered_over_wire() {
    let client = get_client();
    client.reset().expect("reset failed");

    let dns_matcher = |name: &str| {
        let mut matcher = HttpRequest::new();
        matcher
            .extra
            .insert("dnsName".into(), serde_json::Value::from(name));
        matcher
            .extra
            .insert("dnsType".into(), serde_json::Value::from("A"));
        matcher
    };
    client
        .when(dns_matcher("api.rustwire.example"))
        .respond_with_dns(
            DnsResponse::new()
                .response_code("NOERROR")
                .answer_record(DnsRecord::a("api.rustwire.example", "192.0.2.44").ttl(321))
                .answer_record(
                    DnsRecord::a("api.rustwire.example", "198.51.100.7")
                        .dns_class("IN")
                        .ttl(60),
                ),
        )
        .expect("registering DNS expectation failed");
    client
        .when(dns_matcher("refused.rustwire.example"))
        .respond_with_dns(DnsResponse::new().response_code("REFUSED"))
        .expect("registering REFUSED DNS expectation failed");

    let (rcode, answers) = dns_query("api.rustwire.example", 0x5247);
    assert_eq!(rcode, 0, "NOERROR");
    assert_eq!(
        answers,
        vec![
            (
                "api.rustwire.example".to_string(),
                1,
                1,
                321,
                vec![192, 0, 2, 44]
            ),
            (
                "api.rustwire.example".to_string(),
                1,
                1,
                60,
                vec![198, 51, 100, 7]
            ),
        ],
        "the answer section must hold exactly the registered A records"
    );

    let (rcode, answers) = dns_query("refused.rustwire.example", 0x5248);
    assert_eq!(
        rcode, 5,
        "the registered REFUSED response code must be sent"
    );
    assert!(answers.is_empty(), "{answers:?}");

    client.reset().expect("reset failed");
}

fn proto_varint(out: &mut Vec<u8>, mut value: u64) {
    while value >= 0x80 {
        out.push((value as u8) | 0x80);
        value >>= 7;
    }
    out.push(value as u8);
}

fn proto_bytes(out: &mut Vec<u8>, field: u64, bytes: &[u8]) {
    proto_varint(out, field << 3 | 2);
    proto_varint(out, bytes.len() as u64);
    out.extend_from_slice(bytes);
}

fn proto_int(out: &mut Vec<u8>, field: u64, value: u64) {
    proto_varint(out, field << 3);
    proto_varint(out, value);
}

/// A `FileDescriptorSet` for:
/// `package rustwire; message Ping { string text = 1; }`
/// `message Pong { string text = 1; }`
/// `service Echo { rpc Stream (Ping) returns (stream Pong); }`
fn rustwire_descriptor_set() -> Vec<u8> {
    let message = |name: &str| {
        let mut field = Vec::new();
        proto_bytes(&mut field, 1, b"text");
        proto_int(&mut field, 3, 1);
        proto_int(&mut field, 4, 1);
        proto_int(&mut field, 5, 9);
        proto_bytes(&mut field, 10, b"text");
        let mut message = Vec::new();
        proto_bytes(&mut message, 1, name.as_bytes());
        proto_bytes(&mut message, 2, &field);
        message
    };
    let mut method = Vec::new();
    proto_bytes(&mut method, 1, b"Stream");
    proto_bytes(&mut method, 2, b".rustwire.Ping");
    proto_bytes(&mut method, 3, b".rustwire.Pong");
    proto_int(&mut method, 6, 1);
    let mut service = Vec::new();
    proto_bytes(&mut service, 1, b"Echo");
    proto_bytes(&mut service, 2, &method);

    let mut file = Vec::new();
    proto_bytes(&mut file, 1, b"rustwire.proto");
    proto_bytes(&mut file, 2, b"rustwire");
    proto_bytes(&mut file, 4, &message("Ping"));
    proto_bytes(&mut file, 4, &message("Pong"));
    proto_bytes(&mut file, 6, &service);
    proto_bytes(&mut file, 12, b"proto3");
    let mut set = Vec::new();
    proto_bytes(&mut set, 1, &file);
    set
}

/// Splits a gRPC body into its length-prefixed messages and reads field 1
/// (`text`) of each, failing on compressed frames or any other field.
fn grpc_text_messages(mut body: &[u8]) -> Vec<String> {
    let mut texts = Vec::new();
    while !body.is_empty() {
        assert!(body.len() >= 5, "truncated gRPC frame header: {body:?}");
        assert_eq!(body[0], 0, "gRPC message must not be compressed");
        let len = u32::from_be_bytes([body[1], body[2], body[3], body[4]]) as usize;
        let message = &body[5..5 + len];
        assert_eq!(
            message.first(),
            Some(&0x0a),
            "expected field 1 (text): {message:?}"
        );
        let text_len = message[1] as usize;
        assert_eq!(
            message.len(),
            2 + text_len,
            "unexpected extra fields: {message:?}"
        );
        texts.push(String::from_utf8(message[2..].to_vec()).expect("UTF-8 text"));
        body = &body[5 + len..];
    }
    texts
}

/// A `grpcStreamResponse` action answering a real server-streaming gRPC call
/// over HTTP/2 (h2c): the registered headers arrive as response metadata, each
/// registered JSON message arrives as one protobuf-encoded gRPC message, and
/// the registered status name and message arrive as the trailers.
#[test]
#[ignore]
fn test_grpc_stream_response_served_over_wire() {
    let client = get_client();
    client.reset().expect("reset failed");
    client
        .upload_grpc_descriptor(&rustwire_descriptor_set())
        .expect("uploading the gRPC descriptor failed");

    client
        .when(
            HttpRequest::new()
                .method("POST")
                .path("/rustwire.Echo/Stream"),
        )
        .respond_with_grpc_stream(
            GrpcStreamResponse::new()
                .header("x-rustwire", "grpc-meta")
                .message(GrpcStreamMessage::json(r#"{"text":"pong-1"}"#))
                .message(GrpcStreamMessage::json(r#"{"text":"pong-2"}"#))
                .status_name("RESOURCE_EXHAUSTED")
                .status_message("rustwire-quota"),
        )
        .expect("registering gRPC stream expectation failed");

    let mut ping = Vec::new();
    proto_bytes(&mut ping, 1, b"ping");
    let mut request_body = vec![0u8];
    request_body.extend_from_slice(&(ping.len() as u32).to_be_bytes());
    request_body.extend_from_slice(&ping);

    let (host, port) = data_host_port();
    let runtime = tokio::runtime::Builder::new_current_thread()
        .enable_all()
        .build()
        .expect("tokio runtime");
    let exchange = async move {
        let tcp = tokio::net::TcpStream::connect((host.as_str(), port))
            .await
            .expect("connect to MockServer for h2c");
        let (sender, connection) = h2::client::handshake(tcp).await.expect("h2c handshake");
        tokio::spawn(async move {
            connection.await.ok();
        });
        let mut sender = sender.ready().await.expect("h2 sender ready");
        let request = http::Request::builder()
            .method("POST")
            .uri(format!("http://{host}:{port}/rustwire.Echo/Stream"))
            .header("content-type", "application/grpc")
            .header("te", "trailers")
            .body(())
            .expect("gRPC request");
        let (response, mut send) = sender
            .send_request(request, false)
            .expect("send gRPC request");
        send.send_data(bytes::Bytes::from(request_body), true)
            .expect("send gRPC message");
        let response = response.await.expect("gRPC response headers");
        let headers = response.headers().clone();
        let mut recv = response.into_body();
        let mut body = Vec::new();
        while let Some(chunk) = recv.data().await {
            let chunk = chunk.expect("gRPC DATA frame");
            recv.flow_control().release_capacity(chunk.len()).ok();
            body.extend_from_slice(&chunk);
        }
        let trailers = recv.trailers().await.expect("gRPC trailers");
        (headers, body, trailers)
    };
    let (headers, body, trailers) = runtime
        .block_on(async { tokio::time::timeout(Duration::from_secs(20), exchange).await })
        .expect("timed out before the gRPC stream ended");

    let header = |map: &http::HeaderMap, name: &str| {
        map.get(name)
            .map(|v| v.to_str().unwrap_or_default().to_string())
    };
    assert_eq!(
        header(&headers, "content-type").as_deref(),
        Some("application/grpc")
    );
    assert_eq!(header(&headers, "x-rustwire").as_deref(), Some("grpc-meta"));
    assert_eq!(
        grpc_text_messages(&body),
        vec!["pong-1".to_string(), "pong-2".to_string()],
        "each registered JSON message must arrive as one protobuf Pong"
    );
    let trailers = trailers.expect("a gRPC response must end with trailers");
    assert_eq!(
        header(&trailers, "grpc-status").as_deref(),
        Some("8"),
        "RESOURCE_EXHAUSTED"
    );
    assert_eq!(
        header(&trailers, "grpc-message").as_deref(),
        Some("rustwire-quota")
    );

    client
        .clear_grpc_descriptors()
        .expect("clearing gRPC descriptors failed");
    client.reset().expect("reset failed");
}
