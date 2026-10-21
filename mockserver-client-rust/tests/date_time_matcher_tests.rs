//! Pure (de)serialization tests for the date-time matcher object form
//! (`{"after":"now -1 hours"}`) on header, query-string, cookie and path
//! parameter values.

use mockserver_client::{HttpRequest, MatcherValue};
use serde_json::{json, Value};

#[test]
fn builders_emit_the_object_form() {
    let cases: Vec<(MatcherValue, Value)> = vec![
        (
            MatcherValue::after("now -1 hours"),
            json!({"after": "now -1 hours"}),
        ),
        (
            MatcherValue::before("2026-10-21T00:00:00Z").with_actual_format("rfc1123"),
            json!({"before": "2026-10-21T00:00:00Z", "actualFormat": "rfc1123"}),
        ),
        (
            MatcherValue::equal_to_date_time("now +1 days")
                .with_not(true)
                .with_truncate_expected("first day of month")
                .with_truncate_actual("startOfDay")
                .with_apply_truncation_last(true)
                .with_zone("Europe/London"),
            json!({
                "not": true,
                "equalToDateTime": "now +1 days",
                "truncateExpected": "first day of month",
                "truncateActual": "startOfDay",
                "applyTruncationLast": true,
                "zone": "Europe/London"
            }),
        ),
    ];
    for (value, want) in cases {
        assert!(value.is_date_time(), "{value:?}");
        assert_eq!(serde_json::to_value(&value).unwrap(), want, "{value:?}");
    }
}

// Like the Java serializer: "not" first and only when true, then the operator,
// then only the options that are set — never null.
#[test]
fn writes_only_set_fields_in_java_order() {
    let value = MatcherValue::after("now")
        .with_zone("UTC")
        .with_actual_format("epoch")
        .with_not(true);
    assert_eq!(
        serde_json::to_string(&value).unwrap(),
        r#"{"not":true,"after":"now","actualFormat":"epoch","zone":"UTC"}"#
    );
    assert_eq!(
        serde_json::to_string(&MatcherValue::after("now").with_not(false)).unwrap(),
        r#"{"after":"now"}"#
    );
    assert_eq!(
        serde_json::to_string(&MatcherValue::before("now").with_apply_truncation_last(false))
            .unwrap(),
        r#"{"before":"now","applyTruncationLast":false}"#
    );
}

#[test]
fn builder_to_json_to_model_and_back() {
    let built = MatcherValue::before("2026-10-21T00:00:00Z")
        .with_actual_format("rfc1123")
        .with_truncate_actual("startOfDay")
        .with_not(true);
    let wire = serde_json::to_value(&built).unwrap();
    let decoded: MatcherValue = serde_json::from_value(wire.clone()).unwrap();
    assert_eq!(decoded, built);
    assert!(decoded.not);
    assert_eq!(serde_json::to_value(&decoded).unwrap(), wire);
}

#[test]
fn option_methods_return_new_values() {
    let base = MatcherValue::after("now");
    let zoned = base.clone().with_zone("UTC");
    assert_ne!(base, zoned);
    assert_eq!(
        serde_json::to_value(&base).unwrap(),
        json!({"after": "now"})
    );
    assert_eq!(
        serde_json::to_value(&zoned).unwrap(),
        json!({"after": "now", "zone": "UTC"})
    );
}

// A plain string that looks like an operator is a literal, not a date-time matcher.
#[test]
fn plain_before_string_stays_a_plain_string() {
    let value = MatcherValue::from("before:2026-01-01");
    assert!(!value.is_date_time());
    assert_eq!(value, MatcherValue::literal("before:2026-01-01"));
    assert_eq!(
        serde_json::to_value(&value).unwrap(),
        json!("before:2026-01-01")
    );

    let decoded: MatcherValue = serde_json::from_value(json!("before:2026-01-01")).unwrap();
    assert!(!decoded.is_date_time());

    let req = HttpRequest::new().header("X-Plain", "before:2026-01-01");
    let wire = serde_json::to_value(&req).unwrap();
    assert_eq!(wire["headers"]["X-Plain"], json!(["before:2026-01-01"]));
}

// The date-time options mean nothing on a literal, so they leave it unchanged;
// `with_not` negates any value.
#[test]
fn with_not_applies_to_a_literal() {
    assert_eq!(
        MatcherValue::literal("foo").with_not(true),
        MatcherValue::not_literal("foo")
    );
}

#[test]
#[should_panic(expected = "with_zone applies only to a date-time matcher")]
fn a_date_time_option_on_a_literal_panics() {
    let _ = MatcherValue::literal("foo").with_zone("UTC");
}

#[test]
fn every_date_time_option_on_a_literal_panics() {
    let options: [fn(MatcherValue) -> MatcherValue; 5] = [
        |m| m.with_actual_format("rfc1123"),
        |m| m.with_truncate_expected("startOfDay"),
        |m| m.with_truncate_actual("startOfDay"),
        |m| m.with_apply_truncation_last(true),
        |m| m.with_zone("UTC"),
    ];
    for option in options {
        let outcome = std::panic::catch_unwind(|| option(MatcherValue::literal("foo")));
        assert!(
            outcome.is_err(),
            "a date-time option on a literal must panic"
        );
    }
}

#[test]
fn request_builders_carry_date_time_matchers() {
    let req = HttpRequest::new()
        .path("/date-time/{id}")
        .header_matcher("X-Issued-At", MatcherValue::after("now -1 hours"))
        .header_matcher("X-Issued-At", MatcherValue::literal("plain"))
        .query_param_matcher(
            "from",
            MatcherValue::after("2026-01-01").with_zone("Europe/London"),
        )
        .cookie_matcher(
            "seen",
            MatcherValue::equal_to_date_time("now").with_not(true),
        )
        .path_param_matcher("id", MatcherValue::before("now"));
    let wire = serde_json::to_value(&req).unwrap();

    assert_eq!(
        wire["headers"]["X-Issued-At"],
        json!([{"after": "now -1 hours"}, "plain"])
    );
    assert_eq!(
        wire["queryStringParameters"]["from"],
        json!([{"after": "2026-01-01", "zone": "Europe/London"}])
    );
    assert_eq!(
        wire["cookies"]["seen"],
        json!({"not": true, "equalToDateTime": "now"})
    );
    assert_eq!(wire["pathParameters"]["id"], json!([{"before": "now"}]));

    let decoded: HttpRequest = serde_json::from_value(wire.clone()).unwrap();
    assert_eq!(
        decoded.header_matchers.as_ref().unwrap()["X-Issued-At"],
        vec![
            MatcherValue::after("now -1 hours"),
            MatcherValue::literal("plain")
        ]
    );
    assert!(decoded.headers.is_none());
    assert_eq!(
        decoded.cookie_matchers.as_ref().unwrap()["seen"],
        MatcherValue::equal_to_date_time("now").with_not(true)
    );
    assert!(decoded.cookies.is_none());
    assert_eq!(serde_json::to_value(&decoded).unwrap(), wire);
}

// Same shape as test-fixtures/expectations/matcher_request_date_time.json.
#[test]
fn fixture_shaped_request_round_trips_exactly() {
    let wire = json!({
        "method": "GET",
        "path": "/date-time",
        "headers": {
            "X-Issued-At": [{"after": "now -1 hours"}],
            "If-Modified-Since": [{"before": "2026-10-21T00:00:00Z", "actualFormat": "rfc1123"}],
            "X-Plain": ["before:2026-01-01"]
        },
        "queryStringParameters": {
            "from": [{"after": "2026-01-01", "actualFormat": "dd/MM/yyyy", "zone": "Europe/London"}]
        },
        "cookies": {
            "seen": {
                "not": true,
                "equalToDateTime": "now +1 days",
                "truncateExpected": "first day of month",
                "truncateActual": "startOfDay",
                "applyTruncationLast": true
            }
        }
    });
    let decoded: HttpRequest = serde_json::from_value(wire.clone()).unwrap();
    let headers = decoded.header_matchers.as_ref().unwrap();
    assert!(headers["X-Issued-At"][0].is_date_time());
    assert!(!headers["X-Plain"][0].is_date_time());
    assert_eq!(serde_json::to_value(&decoded).unwrap(), wire);
}

#[test]
fn decode_rejects_an_invalid_date_time_shape() {
    for wire in [
        json!({"before": "now", "after": "now"}),
        json!({"after": "now", "value": "x"}),
    ] {
        assert!(
            serde_json::from_value::<MatcherValue>(wire.clone()).is_err(),
            "{wire} should not decode"
        );
    }
}

#[test]
fn display_shows_the_object_form() {
    assert_eq!(
        MatcherValue::after("now").with_zone("UTC").to_string(),
        r#"{"after":"now","zone":"UTC"}"#
    );
    assert_eq!(MatcherValue::not_literal("dev").to_string(), "!dev");
}
