//! Expectation metadata, and retrieve / clear by metadata, unmatched and paging.
//!
//! No running MockServer required: model round-trip tests plus `tiny_http` stub
//! tests that assert the exact query string each method sends, and that every
//! fail-closed rejection returns an error without sending anything.

use std::sync::mpsc;
use std::thread;
use std::time::Duration;

use mockserver_client::*;
use serde_json::{json, Value};

struct Captured {
    method: String,
    url: String,
    body: Vec<u8>,
}

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

const RETRIEVE_ACTIVE: &str = "/mockserver/retrieve?type=ACTIVE_EXPECTATIONS&format=JSON";
const CLEAR: &str = "/mockserver/clear?type=EXPECTATIONS";

fn retrieved_url(filter: ExpectationFilter) -> String {
    let (client, rx) = stub(200, "[]");
    client
        .retrieve_active_expectations_with_filter(None, &filter)
        .unwrap();
    rx.recv().unwrap().url
}

fn assert_rejected_and_nothing_sent<T: std::fmt::Debug>(
    rx: &mpsc::Receiver<Captured>,
    result: Result<T>,
) {
    match result {
        Err(Error::InvalidRequest(_)) => {}
        other => panic!("expected a client-side InvalidRequest, got {other:?}"),
    }
    assert!(
        rx.recv_timeout(Duration::from_millis(200)).is_err(),
        "a rejected call must not send a request"
    );
}

// ---------------------------------------------------------------------------
// Model: metadata
// ---------------------------------------------------------------------------

#[test]
fn shared_metadata_fixture_round_trips_exactly() {
    let path = concat!(
        env!("CARGO_MANIFEST_DIR"),
        "/../test-fixtures/expectations/top_metadata.json"
    );
    let input: Value = serde_json::from_str(&std::fs::read_to_string(path).unwrap()).unwrap();
    let expectation: Expectation = serde_json::from_value(input.clone()).unwrap();
    let metadata = expectation.metadata_map().expect("metadata parsed");
    assert_eq!(metadata["note"], Value::Null);
    assert_eq!(metadata["ticket"], json!(4211));
    assert!(metadata["ticket"].is_u64());
    assert_eq!(metadata["ratio"], json!(0.5));
    assert_eq!(serde_json::to_value(&expectation).unwrap(), input);
}

#[test]
fn metadata_builder_preserves_null_and_nested_values() {
    let expectation = Expectation::new(HttpRequest::new().path("/x"))
        .metadata_entry("owner", "team-a")
        .metadata_entry("note", Value::Null)
        .metadata_entry("tags", json!(["smoke", {"deep": [1, null]}]))
        .metadata_entry("ticket", 42);
    let wire = serde_json::to_value(&expectation).unwrap();
    assert_eq!(
        wire["metadata"],
        json!({"owner": "team-a", "note": null, "tags": ["smoke", {"deep": [1, null]}], "ticket": 42})
    );
    let back: Expectation = serde_json::from_value(wire).unwrap();
    assert_eq!(back, expectation);
}

#[test]
fn metadata_is_omitted_when_absent_or_empty() {
    let plain = serde_json::to_value(Expectation::new(HttpRequest::new())).unwrap();
    assert!(plain.get("metadata").is_none());
    let emptied = Expectation::new(HttpRequest::new())
        .metadata_entry("a", 1)
        .metadata(serde_json::Map::new());
    assert!(emptied.metadata_map().is_none());
    assert!(serde_json::to_value(emptied).unwrap().get("metadata").is_none());
}

#[test]
fn retrieved_expectations_carry_metadata() {
    let (client, rx) = stub(
        200,
        r#"[{"id":"m1","httpRequest":{"path":"/m"},"metadata":{"owner":"team-a","note":null}}]"#,
    );
    let found = client
        .retrieve_active_expectations_by_metadata("owner", "team-a")
        .unwrap();
    rx.recv().unwrap();
    assert_eq!(found.len(), 1);
    assert_eq!(
        Value::Object(found[0].metadata_map().unwrap().clone()),
        json!({"owner": "team-a", "note": null})
    );
}

// ---------------------------------------------------------------------------
// Retrieve: exact query strings
// ---------------------------------------------------------------------------

#[test]
fn metadata_values_are_sent_literally_and_percent_encoded() {
    let cases = [
        ("!team-a", "%21team-a"),
        ("?x", "%3Fx"),
        ("100%", "100%25"),
        ("a+b", "a%2Bb"),
        ("a b", "a+b"),
        ("x&y#z=1", "x%26y%23z%3D1"),
    ];
    for (value, encoded) in cases {
        assert_eq!(
            retrieved_url(ExpectationFilter::new().metadata("owner", value)),
            format!("{RETRIEVE_ACTIVE}&metadataKey=owner&metadataValue={encoded}"),
            "value {value:?}"
        );
    }
}

#[test]
fn non_string_metadata_values_are_sent_as_compact_json() {
    let cases: Vec<(ExpectationFilter, &str)> = vec![
        (ExpectationFilter::new().metadata("k", 42), "42"),
        (ExpectationFilter::new().metadata("k", true), "true"),
        (ExpectationFilter::new().metadata("k", vec!["a", "b"]), "%5B%22a%22%2C%22b%22%5D"),
        (ExpectationFilter::new().metadata("k", Value::Null), "null"),
        (ExpectationFilter::new().metadata("k", json!({"a": 1})), "%7B%22a%22%3A1%7D"),
    ];
    for (filter, encoded) in cases {
        assert_eq!(
            retrieved_url(filter),
            format!("{RETRIEVE_ACTIVE}&metadataKey=k&metadataValue={encoded}")
        );
    }
}

#[test]
fn key_only_form_carries_no_value() {
    let (client, rx) = stub(200, "[]");
    client
        .retrieve_active_expectations_by_metadata_key("!owner")
        .unwrap();
    assert_eq!(rx.recv().unwrap().url, format!("{RETRIEVE_ACTIVE}&metadataKey=%21owner"));
    assert_eq!(
        retrieved_url(ExpectationFilter::new().metadata("owner", "x").metadata_key("owner")),
        format!("{RETRIEVE_ACTIVE}&metadataKey=owner")
    );
}

#[test]
fn json_path_unmatched_and_paging_params() {
    assert_eq!(
        retrieved_url(ExpectationFilter::new().metadata_json_path("$[?(@.owner == 'team-a')]")),
        format!("{RETRIEVE_ACTIVE}&metadataJsonPath=%24%5B%3F%28%40.owner+%3D%3D+%27team-a%27%29%5D")
    );
    assert_eq!(
        retrieved_url(ExpectationFilter::new().unmatched(true).offset(5).limit(10)),
        format!("{RETRIEVE_ACTIVE}&unmatched=true&limit=10&offset=5")
    );
    assert_eq!(
        retrieved_url(ExpectationFilter::new().unmatched(false)),
        RETRIEVE_ACTIVE.to_string()
    );
}

#[test]
fn retrieve_unmatched_sends_unmatched_true_and_the_request_matcher_body() {
    let (client, rx) = stub(200, "");
    assert!(client.retrieve_unmatched_expectations().unwrap().is_empty());
    let cap = rx.recv().unwrap();
    assert_eq!(cap.method, "PUT");
    assert_eq!(cap.url, format!("{RETRIEVE_ACTIVE}&unmatched=true"));
    assert!(cap.body.is_empty());

    let (client, rx) = stub(200, "[]");
    client
        .retrieve_active_expectations_with_filter(
            Some(&HttpRequest::new().path("/a")),
            &ExpectationFilter::new().metadata_key("owner"),
        )
        .unwrap();
    let body: Value = serde_json::from_slice(&rx.recv().unwrap().body).unwrap();
    assert_eq!(body, json!({"path": "/a"}));
}

#[test]
fn recorded_expectations_page_sends_offset_and_limit() {
    let (client, rx) = stub(200, "[]");
    client
        .retrieve_recorded_expectations_page(None, 0, 25)
        .unwrap();
    assert_eq!(
        rx.recv().unwrap().url,
        "/mockserver/retrieve?type=RECORDED_EXPECTATIONS&format=JSON&limit=25&offset=0"
    );
}

// ---------------------------------------------------------------------------
// Clear: exact query strings
// ---------------------------------------------------------------------------

#[test]
fn clear_by_metadata_variants_send_the_selection() {
    let (client, rx) = stub(200, "");
    client.clear_by_metadata("owner", "!team-a").unwrap();
    let cap = rx.recv().unwrap();
    assert_eq!(cap.method, "PUT");
    assert_eq!(cap.url, format!("{CLEAR}&metadataKey=owner&metadataValue=%21team-a"));
    assert!(cap.body.is_empty());

    let (client, rx) = stub(200, "");
    client.clear_by_metadata_key("owner").unwrap();
    assert_eq!(rx.recv().unwrap().url, format!("{CLEAR}&metadataKey=owner"));

    let (client, rx) = stub(200, "");
    client.clear_unmatched_expectations().unwrap();
    assert_eq!(rx.recv().unwrap().url, format!("{CLEAR}&unmatched=true"));

    let (client, rx) = stub(200, "");
    client
        .clear_with_filter(
            Some(&HttpRequest::new().path("/a")),
            &ExpectationFilter::new().metadata_json_path("$.tags[?(@ == 'smoke')]"),
        )
        .unwrap();
    let cap = rx.recv().unwrap();
    assert_eq!(
        cap.url,
        format!("{CLEAR}&metadataJsonPath=%24.tags%5B%3F%28%40+%3D%3D+%27smoke%27%29%5D")
    );
    let body: Value = serde_json::from_slice(&cap.body).unwrap();
    assert_eq!(body, json!({"path": "/a"}));
}

#[test]
fn clear_server_rejection_maps_to_invalid_request() {
    let (client, rx) = stub(400, "bad filter");
    match client.clear_by_metadata_key("owner") {
        Err(Error::InvalidRequest(msg)) => assert_eq!(msg, "bad filter"),
        other => panic!("unexpected {other:?}"),
    }
    rx.recv().unwrap();
}

// ---------------------------------------------------------------------------
// Fail-closed rejections: error returned, nothing sent
// ---------------------------------------------------------------------------

#[test]
fn clear_without_a_selection_is_rejected() {
    let (client, rx) = stub(200, "");
    assert_rejected_and_nothing_sent(&rx, client.clear_with_filter(None, &ExpectationFilter::new()));
    assert_rejected_and_nothing_sent(
        &rx,
        client.clear_with_filter(None, &ExpectationFilter::new().unmatched(false)),
    );
}

#[test]
fn clear_with_paging_is_rejected() {
    let (client, rx) = stub(200, "");
    assert_rejected_and_nothing_sent(
        &rx,
        client.clear_with_filter(None, &ExpectationFilter::new().unmatched(true).limit(1)),
    );
    assert_rejected_and_nothing_sent(
        &rx,
        client.clear_with_filter(None, &ExpectationFilter::new().metadata_key("k").offset(0)),
    );
}

#[test]
fn blank_keys_and_json_paths_are_rejected() {
    let (client, rx) = stub(200, "");
    assert_rejected_and_nothing_sent(&rx, client.clear_by_metadata("", "v"));
    assert_rejected_and_nothing_sent(&rx, client.clear_by_metadata_key("  "));
    assert_rejected_and_nothing_sent(
        &rx,
        client.clear_with_filter(None, &ExpectationFilter::new().metadata_json_path(" ")),
    );
    assert_rejected_and_nothing_sent(&rx, client.retrieve_active_expectations_by_metadata("", "v"));
    assert_rejected_and_nothing_sent(&rx, client.retrieve_active_expectations_by_metadata_key(""));
    assert_rejected_and_nothing_sent(
        &rx,
        client.retrieve_active_expectations_with_filter(
            None,
            &ExpectationFilter::new().metadata_json_path(""),
        ),
    );
}

#[test]
fn paging_beyond_the_server_integer_range_is_rejected() {
    let (client, rx) = stub(200, "[]");
    assert_rejected_and_nothing_sent(
        &rx,
        client.retrieve_recorded_expectations_page(None, u32::MAX, 1),
    );
    assert_rejected_and_nothing_sent(
        &rx,
        client.retrieve_active_expectations_with_filter(
            None,
            &ExpectationFilter::new().limit(i32::MAX as u32 + 1),
        ),
    );
}

#[test]
fn non_finite_numbers_are_rejected_instead_of_selecting_null() {
    // serde_json would turn NaN or infinity into null, which selects (and clears) a different expectation
    let (client, rx) = stub(202, "");
    assert_rejected_and_nothing_sent(&rx, client.clear_by_metadata("k", f64::NAN));
    assert_rejected_and_nothing_sent(&rx, client.clear_by_metadata("k", f64::INFINITY));
    assert_rejected_and_nothing_sent(&rx, client.clear_by_metadata("k", vec![1.0, f64::NEG_INFINITY]));
    assert_rejected_and_nothing_sent(&rx, client.retrieve_active_expectations_by_metadata("k", f64::NAN));
    assert_rejected_and_nothing_sent(
        &rx,
        client.clear_with_filter(None, &ExpectationFilter::new().metadata("k", f64::NAN).metadata_key("k")),
    );
    assert!(retrieved_url(ExpectationFilter::new().metadata("k", 1.5)).ends_with("metadataKey=k&metadataValue=1.5"));
}
