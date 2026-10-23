//! Retrieving recorded expectations with `RecordedExpectationsOptions`.
//!
//! No running MockServer required: `tiny_http` stub tests that assert the exact
//! query string sent, and that every rejected option sends nothing.

use std::sync::mpsc;
use std::thread;
use std::time::Duration;

use mockserver_client::*;
use serde_json::Value;

struct Captured {
    method: String,
    url: String,
    body: Vec<u8>,
}

fn stub(resp_body: &'static str) -> (MockServerClient, mpsc::Receiver<Captured>) {
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
        request
            .respond(tiny_http::Response::from_string(resp_body))
            .ok();
    });
    let client = ClientBuilder::new("127.0.0.1", port)
        .build()
        .expect("build client");
    (client, rx)
}

const RECORDED: &str = "/mockserver/retrieve?type=RECORDED_EXPECTATIONS&format=JSON";

fn sent_url(options: RecordedExpectationsOptions) -> String {
    let (client, rx) = stub("[]");
    client
        .retrieve_recorded_expectations_with_options(None, &options)
        .unwrap();
    rx.recv().unwrap().url
}

#[test]
fn sends_every_option_with_a_request_matcher_and_returns_the_sequence() {
    let (client, rx) = stub(
        r#"[{"httpRequest":{"method":"GET","path":"/jobs/1"},
            "httpResponses":[{"statusCode":200,"body":"pending"},{"statusCode":200,"body":"done"}],
            "responseMode":"SWITCH","times":{"unlimited":true}}]"#,
    );
    let options = RecordedExpectationsOptions::new()
        .consolidate(true)
        .parameterize(true)
        .repeats_as_scenarios(true)
        .capture_headers(["X-Tenant", "Accept"])
        .extract_bodies_larger_than(1024);

    let recorded = client
        .retrieve_recorded_expectations_with_options(
            Some(&HttpRequest::new().path("/jobs/.*")),
            &options,
        )
        .unwrap();

    let sent = rx.recv().unwrap();
    assert_eq!(sent.method, "PUT");
    assert_eq!(
        sent.url,
        format!(
            "{RECORDED}&consolidate=true&parameterize=true&repeatsAsScenarios=true\
             &captureHeaders=X-Tenant%2CAccept&extractBodiesLargerThan=1024"
        )
    );
    let body: Value = serde_json::from_slice(&sent.body).unwrap();
    assert_eq!(body["path"], "/jobs/.*");
    assert_eq!(recorded.len(), 1);
    assert_eq!(recorded[0].response_mode, Some(ResponseMode::Switch));
    assert_eq!(recorded[0].http_responses.as_ref().map(Vec::len), Some(2));
}

#[test]
fn sends_the_query_for_each_option() {
    assert_eq!(sent_url(RecordedExpectationsOptions::new()), RECORDED);
    assert_eq!(
        sent_url(RecordedExpectationsOptions::new().repeats_as_scenarios(false)),
        RECORDED
    );
    assert_eq!(
        sent_url(RecordedExpectationsOptions::new().capture_headers(Vec::<String>::new())),
        format!("{RECORDED}&captureHeaders=")
    );
    assert_eq!(
        sent_url(RecordedExpectationsOptions::new().extract_bodies_larger_than(0)),
        format!("{RECORDED}&extractBodiesLargerThan=0")
    );
}

#[test]
fn rejects_invalid_options_sending_nothing() {
    for options in [
        RecordedExpectationsOptions::new().capture_headers(["X-Tenant", " "]),
        RecordedExpectationsOptions::new().capture_headers(["X-Tenant,Accept"]),
        RecordedExpectationsOptions::new().extract_bodies_larger_than(i64::MAX as u64 + 1),
    ] {
        let (client, rx) = stub("[]");
        match client.retrieve_recorded_expectations_with_options(None, &options) {
            Err(Error::InvalidRequest(_)) => {}
            other => panic!("expected a client-side InvalidRequest for {options:?}, got {other:?}"),
        }
        assert!(
            rx.recv_timeout(Duration::from_millis(200)).is_err(),
            "a rejected call must not send a request"
        );
    }
}
