//! Control-plane parity tests (clock, metrics, configuration, pact, file store,
//! import, operating mode, WSDL).
//!
//! Two kinds of tests, no running MockServer required:
//!  * pure model tests (the `MockMode` enum), and
//!  * stub-server tests using `tiny_http` that assert the exact HTTP method,
//!    path, query and request body each client method sends, and that the
//!    documented status codes map to the right `Result`.

use std::str::FromStr;
use std::sync::mpsc;
use std::thread;

use mockserver_client::*;

// ---------------------------------------------------------------------------
// Stub HTTP server harness (mirrors tests/sre_tests.rs)
// ---------------------------------------------------------------------------

/// What the stub captured about the single request it served.
struct Captured {
    method: String,
    url: String,
    body: Vec<u8>,
}

/// Start a one-shot stub server that replies with `status`/`resp_body` to the
/// first request, capturing its method, url and body. Returns a client pointed
/// at the stub and a receiver that yields the captured request.
fn stub(status: u16, resp_body: &'static str) -> (MockServerClient, mpsc::Receiver<Captured>) {
    let server = tiny_http::Server::http("127.0.0.1:0").expect("bind stub server");
    let port = match server.server_addr() {
        tiny_http::ListenAddr::IP(addr) => addr.port(),
        #[allow(unreachable_patterns)]
        _ => panic!("expected IP listen address"),
    };
    let (tx, rx) = mpsc::channel();

    thread::spawn(move || {
        let mut request = server.recv().expect("stub recv");
        let method = request.method().as_str().to_string();
        let url = request.url().to_string();
        let mut body = Vec::new();
        request.as_reader().read_to_end(&mut body).ok();
        tx.send(Captured { method, url, body }).ok();

        let response = tiny_http::Response::from_string(resp_body)
            .with_status_code(tiny_http::StatusCode(status));
        request.respond(response).ok();
    });

    let client = ClientBuilder::new("127.0.0.1", port)
        .build()
        .expect("build client");
    (client, rx)
}

fn body_string(cap: &Captured) -> String {
    String::from_utf8_lossy(&cap.body).to_string()
}

fn body_json(cap: &Captured) -> serde_json::Value {
    serde_json::from_slice(&cap.body).expect("request body is JSON")
}

// ---------------------------------------------------------------------------
// MockMode enum (pure model)
// ---------------------------------------------------------------------------

#[test]
fn test_mock_mode_as_str_and_display() {
    assert_eq!(MockMode::Simulate.as_str(), "SIMULATE");
    assert_eq!(MockMode::Spy.as_str(), "SPY");
    assert_eq!(MockMode::Capture.as_str(), "CAPTURE");
    assert_eq!(MockMode::Spy.to_string(), "SPY");
}

#[test]
fn test_mock_mode_proxy_unmatched_requests() {
    assert!(!MockMode::Simulate.proxy_unmatched_requests());
    assert!(MockMode::Spy.proxy_unmatched_requests());
    assert!(MockMode::Capture.proxy_unmatched_requests());
}

#[test]
fn test_mock_mode_parse_case_insensitive() {
    assert_eq!(MockMode::from_str("simulate").unwrap(), MockMode::Simulate);
    assert_eq!(MockMode::from_str("  Spy ").unwrap(), MockMode::Spy);
    assert_eq!(MockMode::from_str("CAPTURE").unwrap(), MockMode::Capture);
    assert!(MockMode::from_str("").is_err());
    assert!(MockMode::from_str("forward").is_err());
}

// ---------------------------------------------------------------------------
// Clock
// ---------------------------------------------------------------------------

#[test]
fn test_freeze_clock_with_instant() {
    let (client, rx) = stub(200, r#"{"status":"freeze","currentEpochMillis":0}"#);
    client.freeze_clock(Some("2024-01-01T00:00:00Z")).unwrap();
    let cap = rx.recv().unwrap();
    assert_eq!(cap.method, "PUT");
    assert_eq!(cap.url, "/mockserver/clock");
    let sent = body_json(&cap);
    assert_eq!(sent["action"], "freeze");
    assert_eq!(sent["instant"], "2024-01-01T00:00:00Z");
}

#[test]
fn test_freeze_clock_without_instant() {
    let (client, rx) = stub(200, r#"{"status":"freeze"}"#);
    client.freeze_clock(None).unwrap();
    let cap = rx.recv().unwrap();
    let sent = body_json(&cap);
    assert_eq!(sent["action"], "freeze");
    assert!(sent.get("instant").is_none());
}

#[test]
fn test_advance_clock() {
    let (client, rx) = stub(200, r#"{"status":"advance"}"#);
    client.advance_clock(5000).unwrap();
    let cap = rx.recv().unwrap();
    assert_eq!(cap.method, "PUT");
    assert_eq!(cap.url, "/mockserver/clock");
    let sent = body_json(&cap);
    assert_eq!(sent["action"], "advance");
    assert_eq!(sent["durationMillis"], 5000);
}

#[test]
fn test_advance_clock_non_positive_maps_400() {
    let (client, _rx) = stub(400, r#"{"error":"durationMillis must be positive"}"#);
    let err = client.advance_clock(0).unwrap_err();
    assert!(matches!(err, Error::InvalidRequest(_)));
}

#[test]
fn test_reset_clock() {
    let (client, rx) = stub(200, r#"{"status":"reset"}"#);
    client.reset_clock().unwrap();
    let cap = rx.recv().unwrap();
    assert_eq!(cap.method, "PUT");
    assert_eq!(cap.url, "/mockserver/clock");
    assert_eq!(body_json(&cap)["action"], "reset");
}

#[test]
fn test_clock_status() {
    let (client, rx) = stub(200, r#"{"frozen":true,"currentEpochMillis":42}"#);
    let out = client.clock_status().unwrap();
    let cap = rx.recv().unwrap();
    assert_eq!(cap.method, "GET");
    assert_eq!(cap.url, "/mockserver/clock");
    assert!(out.contains("\"frozen\":true"));
}

// ---------------------------------------------------------------------------
// Metrics
// ---------------------------------------------------------------------------

#[test]
fn test_retrieve_metrics() {
    let (client, rx) = stub(200, r#"{"REQUESTS_RECEIVED":3}"#);
    let out = client.retrieve_metrics().unwrap();
    let cap = rx.recv().unwrap();
    assert_eq!(cap.method, "PUT");
    assert_eq!(cap.url, "/mockserver/retrieve?type=METRICS");
    assert!(out.contains("REQUESTS_RECEIVED"));
}

#[test]
fn test_scrape_metrics() {
    let (client, rx) = stub(200, "# HELP foo\nfoo 1\n");
    let out = client.scrape_metrics().unwrap();
    let cap = rx.recv().unwrap();
    assert_eq!(cap.method, "GET");
    assert_eq!(cap.url, "/mockserver/metrics");
    assert!(out.contains("foo 1"));
}

#[test]
fn test_scrape_metrics_disabled_maps_404() {
    let (client, _rx) = stub(404, "");
    let err = client.scrape_metrics().unwrap_err();
    assert!(matches!(err, Error::NotFound(_)));
}

// ---------------------------------------------------------------------------
// Configuration
// ---------------------------------------------------------------------------

#[test]
fn test_retrieve_configuration() {
    let (client, rx) = stub(200, r#"{"logLevel":"INFO"}"#);
    let out = client.retrieve_configuration().unwrap();
    let cap = rx.recv().unwrap();
    assert_eq!(cap.method, "GET");
    assert_eq!(cap.url, "/mockserver/configuration");
    assert!(out.contains("logLevel"));
}

#[test]
fn test_update_configuration() {
    let (client, rx) = stub(200, r#"{"logLevel":"DEBUG"}"#);
    let out = client
        .update_configuration(r#"{"logLevel":"DEBUG"}"#)
        .unwrap();
    let cap = rx.recv().unwrap();
    assert_eq!(cap.method, "PUT");
    assert_eq!(cap.url, "/mockserver/configuration");
    assert_eq!(body_json(&cap)["logLevel"], "DEBUG");
    assert!(out.contains("DEBUG"));
}

// ---------------------------------------------------------------------------
// Drift detection
// ---------------------------------------------------------------------------

#[test]
fn test_retrieve_drift() {
    let (client, rx) = stub(200, r#"{"count":1,"drifts":[{"path":"/foo"}]}"#);
    let out = client.retrieve_drift().unwrap();
    let cap = rx.recv().unwrap();
    assert_eq!(cap.method, "GET");
    assert_eq!(cap.url, "/mockserver/drift");
    assert!(out.contains("\"count\":1"));
    assert!(out.contains("drifts"));
}

#[test]
fn test_clear_drift() {
    let (client, rx) = stub(200, r#"{"status":"cleared"}"#);
    client.clear_drift().unwrap();
    let cap = rx.recv().unwrap();
    assert_eq!(cap.method, "PUT");
    assert_eq!(cap.url, "/mockserver/drift/clear");
}

// ---------------------------------------------------------------------------
// Pact
// ---------------------------------------------------------------------------

#[test]
fn test_pact_import() {
    let (client, rx) = stub(201, r#"[{"httpRequest":{"path":"/a"},"httpResponse":{"statusCode":200}}]"#);
    let created = client.pact_import(r#"{"interactions":[]}"#).unwrap();
    let cap = rx.recv().unwrap();
    assert_eq!(cap.method, "PUT");
    assert_eq!(cap.url, "/mockserver/pact/import");
    assert_eq!(body_json(&cap)["interactions"], serde_json::json!([]));
    assert_eq!(created.len(), 1);
}

#[test]
fn test_pact_export_with_params() {
    let (client, rx) = stub(200, r#"{"interactions":[]}"#);
    let out = client.pact_export("my-consumer", "my-provider").unwrap();
    let cap = rx.recv().unwrap();
    assert_eq!(cap.method, "PUT");
    assert!(
        cap.url == "/mockserver/pact?consumer=my-consumer&provider=my-provider"
            || cap.url == "/mockserver/pact?provider=my-provider&consumer=my-consumer",
        "unexpected url {}",
        cap.url
    );
    assert!(out.contains("interactions"));
}

#[test]
fn test_pact_export_blank_params_omitted() {
    let (client, rx) = stub(200, r#"{"interactions":[]}"#);
    client.pact_export("", "").unwrap();
    let cap = rx.recv().unwrap();
    assert_eq!(cap.url, "/mockserver/pact");
}

#[test]
fn test_pact_verify_pass_202() {
    let (client, rx) = stub(202, r#"{"valid":true}"#);
    let result = client.pact_verify(r#"{"interactions":[]}"#).unwrap();
    let cap = rx.recv().unwrap();
    assert_eq!(cap.method, "PUT");
    assert_eq!(cap.url, "/mockserver/pact/verify");
    assert!(result.passed);
    assert!(result.report.contains("valid"));
}

#[test]
fn test_pact_verify_fail_406() {
    let (client, _rx) = stub(406, r#"{"valid":false,"reason":"mismatch"}"#);
    let result = client.pact_verify(r#"{"interactions":[]}"#).unwrap();
    assert!(!result.passed);
    assert!(result.report.contains("mismatch"));
}

#[test]
fn test_pact_verify_bad_input_400() {
    let (client, _rx) = stub(400, r#"{"error":"Pact contract JSON must not be empty"}"#);
    let err = client.pact_verify("").unwrap_err();
    assert!(matches!(err, Error::InvalidRequest(_)));
}

// ---------------------------------------------------------------------------
// File store
// ---------------------------------------------------------------------------

#[test]
fn test_store_file_base64_encodes() {
    let (client, rx) = stub(201, r#"{"name":"data.bin","size":3}"#);
    let out = client.store_file("data.bin", &[1, 2, 3]).unwrap();
    let cap = rx.recv().unwrap();
    assert_eq!(cap.method, "PUT");
    assert_eq!(cap.url, "/mockserver/files/store");
    let sent = body_json(&cap);
    assert_eq!(sent["name"], "data.bin");
    assert_eq!(sent["base64"], true);
    // base64 of bytes [1,2,3]
    assert_eq!(sent["content"], "AQID");
    assert!(out.contains("data.bin"));
}

#[test]
fn test_retrieve_file_returns_raw_bytes() {
    let (client, rx) = stub(200, "raw-bytes-here");
    let bytes = client.retrieve_file("data.bin").unwrap();
    let cap = rx.recv().unwrap();
    assert_eq!(cap.method, "PUT");
    assert_eq!(cap.url, "/mockserver/files/retrieve");
    assert_eq!(body_json(&cap)["name"], "data.bin");
    assert_eq!(bytes, b"raw-bytes-here");
}

#[test]
fn test_retrieve_file_unknown_maps_404() {
    let (client, _rx) = stub(404, "file not found: nope");
    let err = client.retrieve_file("nope").unwrap_err();
    assert!(matches!(err, Error::NotFound(_)));
}

#[test]
fn test_list_files() {
    let (client, rx) = stub(200, r#"["a.txt","b.txt"]"#);
    let files = client.list_files().unwrap();
    let cap = rx.recv().unwrap();
    assert_eq!(cap.method, "PUT");
    assert_eq!(cap.url, "/mockserver/files/list");
    assert_eq!(files, vec!["a.txt".to_string(), "b.txt".to_string()]);
}

#[test]
fn test_delete_file() {
    let (client, rx) = stub(200, "");
    client.delete_file("a.txt").unwrap();
    let cap = rx.recv().unwrap();
    assert_eq!(cap.method, "PUT");
    assert_eq!(cap.url, "/mockserver/files/delete");
    assert_eq!(body_json(&cap)["name"], "a.txt");
}

#[test]
fn test_delete_file_unknown_maps_404() {
    let (client, _rx) = stub(404, "file not found: nope");
    let err = client.delete_file("nope").unwrap_err();
    assert!(matches!(err, Error::NotFound(_)));
}

// ---------------------------------------------------------------------------
// Import (HAR / Postman)
// ---------------------------------------------------------------------------

#[test]
fn test_import_har() {
    let (client, rx) = stub(201, r#"[{"httpRequest":{"path":"/a"}}]"#);
    let created = client.import_har(r#"{"log":{"entries":[]}}"#).unwrap();
    let cap = rx.recv().unwrap();
    assert_eq!(cap.method, "PUT");
    assert_eq!(cap.url, "/mockserver/import?format=har");
    assert!(body_string(&cap).contains("entries"));
    assert_eq!(created.len(), 1);
}

#[test]
fn test_import_postman_collection() {
    let (client, rx) = stub(201, r#"[]"#);
    let created = client
        .import_postman_collection(r#"{"info":{},"item":[]}"#)
        .unwrap();
    let cap = rx.recv().unwrap();
    assert_eq!(cap.method, "PUT");
    assert_eq!(cap.url, "/mockserver/import?format=postman");
    assert!(created.is_empty());
}

// ---------------------------------------------------------------------------
// Operating mode
// ---------------------------------------------------------------------------

#[test]
fn test_set_mode() {
    let (client, rx) = stub(200, r#"{"mode":"SPY","proxyUnmatchedRequests":true}"#);
    let out = client.set_mode(MockMode::Spy).unwrap();
    let cap = rx.recv().unwrap();
    assert_eq!(cap.method, "PUT");
    assert_eq!(cap.url, "/mockserver/mode?mode=SPY");
    assert!(out.contains("SPY"));
}

#[test]
fn test_retrieve_mode() {
    let (client, rx) = stub(200, r#"{"mode":"SIMULATE","proxyUnmatchedRequests":false}"#);
    let out = client.retrieve_mode().unwrap();
    let cap = rx.recv().unwrap();
    assert_eq!(cap.method, "GET");
    assert_eq!(cap.url, "/mockserver/mode");
    assert!(out.contains("SIMULATE"));
}

#[test]
fn test_set_mode_invalid_maps_400() {
    let (client, _rx) = stub(400, "unknown mode");
    let err = client.set_mode(MockMode::Capture).unwrap_err();
    assert!(matches!(err, Error::InvalidRequest(_)));
}

// ---------------------------------------------------------------------------
// WSDL
// ---------------------------------------------------------------------------

#[test]
fn test_wsdl_expectation() {
    let (client, rx) = stub(201, r#"[{"httpRequest":{"path":"/svc"}}]"#);
    let wsdl = "<definitions xmlns=\"http://schemas.xmlsoap.org/wsdl/\"></definitions>";
    let created = client.wsdl_expectation(wsdl).unwrap();
    let cap = rx.recv().unwrap();
    assert_eq!(cap.method, "PUT");
    assert_eq!(cap.url, "/mockserver/wsdl");
    assert_eq!(body_string(&cap), wsdl);
    assert_eq!(created.len(), 1);
}

#[test]
fn test_wsdl_invalid_maps_400() {
    let (client, _rx) = stub(400, "invalid wsdl");
    let err = client.wsdl_expectation("<bad/>").unwrap_err();
    assert!(matches!(err, Error::InvalidRequest(_)));
}

// ---------------------------------------------------------------------------
// Retrieve recorded requests with a DNS filter
// ---------------------------------------------------------------------------

#[test]
fn test_retrieve_recorded_requests_with_dns_filter() {
    let (client, rx) = stub(
        200,
        r#"[{"dnsName":"api.example.com","dnsType":"A","dnsClass":"IN"}]"#,
    );
    // HttpRequest has no DNS fields; a DNS matcher travels in the `extra` catch-all.
    let mut filter = HttpRequest::new();
    filter
        .extra
        .insert("dnsName".into(), "api.example.com".into());
    filter.extra.insert("dnsType".into(), "A".into());

    let recorded = client.retrieve_recorded_requests(Some(&filter)).unwrap();

    let cap = rx.recv().unwrap();
    assert_eq!(cap.method, "PUT");
    assert!(
        cap.url.starts_with("/mockserver/retrieve?type=REQUESTS"),
        "{}",
        cap.url
    );
    assert_eq!(
        body_json(&cap),
        serde_json::json!({"dnsName": "api.example.com", "dnsType": "A"})
    );
    assert_eq!(recorded.len(), 1);
    assert_eq!(recorded[0].extra["dnsName"], "api.example.com");
    assert_eq!(recorded[0].extra["dnsType"], "A");
    assert_eq!(recorded[0].extra["dnsClass"], "IN");
}
