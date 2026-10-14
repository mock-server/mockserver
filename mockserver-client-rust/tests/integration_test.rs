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
