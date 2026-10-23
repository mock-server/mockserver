//! A JSON_PATH or XPATH body can compare the value it selects as a date-time,
//! carried under `dateTime` in the same object form as a header value.

use mockserver_client::{Body, Expectation, MatcherValue};
use serde_json::{json, Value};

#[test]
fn with_date_time_writes_the_object_form() {
    let json_path = Body::json_path("$.order.created")
        .with_date_time(MatcherValue::after("now -1 days").with_zone("Europe/London"));
    assert_eq!(
        serde_json::to_value(&json_path).unwrap(),
        json!({"type": "JSON_PATH", "jsonPath": "$.order.created", "dateTime": {"after": "now -1 days", "zone": "Europe/London"}})
    );
    let xpath = Body::xpath("/order/@expires")
        .with_date_time(MatcherValue::before("2026-01-01").with_not(true));
    assert_eq!(
        serde_json::to_value(&xpath).unwrap(),
        json!({"type": "XPATH", "xpath": "/order/@expires", "dateTime": {"not": true, "before": "2026-01-01"}})
    );
    assert_eq!(
        xpath.date_time(),
        Some(MatcherValue::before("2026-01-01").with_not(true))
    );
}

#[test]
fn without_date_time_nothing_changes() {
    assert_eq!(
        serde_json::to_value(Body::json_path("$.a")).unwrap(),
        json!({"type": "JSON_PATH", "jsonPath": "$.a"})
    );
    assert_eq!(Body::json_path("$.a").date_time(), None);
}

#[test]
#[should_panic(expected = "needs a date-time matcher")]
fn rejects_a_value_that_is_not_a_date_time_matcher() {
    let _ = Body::json_path("$.a").with_date_time(MatcherValue::literal("now"));
}

#[test]
#[should_panic(expected = "applies only to a JSON_PATH or XPATH body")]
fn rejects_a_body_that_is_not_json_path_or_xpath() {
    let _ = Body::regex(".*").with_date_time(MatcherValue::after("now"));
}

#[test]
fn shared_fixtures_round_trip_with_a_typed_date_time() {
    for name in [
        "matcher_body_json_path_date_time.json",
        "matcher_body_xpath_date_time.json",
    ] {
        let path = format!(
            "{}/../test-fixtures/expectations/{}",
            env!("CARGO_MANIFEST_DIR"),
            name
        );
        let source: Value = serde_json::from_str(&std::fs::read_to_string(&path).unwrap()).unwrap();
        let expectation: Expectation = serde_json::from_value(source.clone()).unwrap();
        let body = expectation
            .http_request
            .as_ref()
            .and_then(|request| request.body.as_ref())
            .expect("fixture has a request body");
        assert!(body.date_time().is_some(), "{name}: {body:?}");
        assert_eq!(
            serde_json::to_value(&expectation).unwrap()["httpRequest"]["body"],
            source["httpRequest"]["body"],
            "{name}"
        );
    }
}
