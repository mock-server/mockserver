//! Crate-wide policy for public enums, so a newer MockServer cannot break an
//! older client:
//!  * every public enum is `#[non_exhaustive]`, so adding a variant is not a
//!    breaking change for callers that `match` on it;
//!  * every serde wire enum has an `Unknown` variant marked
//!    `#[serde(other, skip_serializing)]`, so a value this client does not know
//!    reads as `Unknown` instead of failing the whole response, and sending
//!    `Unknown` back is an error instead of a value the server would reject.
//!
//! No running MockServer is required.

use std::path::PathBuf;
use std::sync::mpsc;
use std::thread;
use std::time::Duration;

use mockserver_client::*;
use serde::de::DeserializeOwned;
use serde::Serialize;
use serde_json::{json, Value};

const NEWER_VALUE: &str = "A_VALUE_FROM_A_NEWER_SERVER";

fn assert_policy<T>(known: &[(T, &str)], unknown: T)
where
    T: Serialize + DeserializeOwned + PartialEq + std::fmt::Debug + Copy,
{
    for (variant, wire) in known {
        assert_eq!(
            serde_json::to_value(variant).unwrap(),
            json!(wire),
            "{variant:?} must serialise to its wire value"
        );
        let read: T = serde_json::from_value(json!(wire)).unwrap();
        assert_eq!(read, *variant, "{wire} must read back as {variant:?}");
    }

    let read: T = serde_json::from_value(json!(NEWER_VALUE))
        .expect("an unknown wire value must read as Unknown, not fail");
    assert_eq!(read, unknown);

    let err = serde_json::to_value(unknown)
        .expect_err("serialising Unknown must fail rather than emit a value");
    assert!(
        err.to_string().contains("Unknown"),
        "the error must name the Unknown variant, got: {err}"
    );
}

#[test]
fn binary_upstream() {
    assert_policy(
        &[
            (BinaryUpstream::AnswerOnly, "ANSWER_ONLY"),
            (BinaryUpstream::AnswerAndForward, "ANSWER_AND_FORWARD"),
            (BinaryUpstream::ForwardAndReplace, "FORWARD_AND_REPLACE"),
        ],
        BinaryUpstream::Unknown,
    );
}

#[test]
fn response_mode() {
    assert_policy(
        &[
            (ResponseMode::Sequential, "SEQUENTIAL"),
            (ResponseMode::Random, "RANDOM"),
            (ResponseMode::Weighted, "WEIGHTED"),
            (ResponseMode::Switch, "SWITCH"),
        ],
        ResponseMode::Unknown,
    );
}

#[test]
fn cross_protocol_trigger() {
    assert_policy(
        &[
            (CrossProtocolTrigger::DnsQuery, "DNS_QUERY"),
            (CrossProtocolTrigger::WebsocketConnect, "WEBSOCKET_CONNECT"),
            (CrossProtocolTrigger::GrpcRequest, "GRPC_REQUEST"),
            (CrossProtocolTrigger::HttpRequest, "HTTP_REQUEST"),
        ],
        CrossProtocolTrigger::Unknown,
    );
}

#[test]
fn ramp_curve() {
    assert_policy(
        &[
            (RampCurve::Linear, "LINEAR"),
            (RampCurve::Quadratic, "QUADRATIC"),
            (RampCurve::Exponential, "EXPONENTIAL"),
        ],
        RampCurve::Unknown,
    );
}

#[test]
fn load_stage_type() {
    assert_policy(
        &[
            (LoadStageType::Vu, "VU"),
            (LoadStageType::Rate, "RATE"),
            (LoadStageType::Pause, "PAUSE"),
        ],
        LoadStageType::Unknown,
    );
}

#[test]
fn load_shape_type() {
    assert_policy(
        &[
            (LoadShapeType::Spike, "SPIKE"),
            (LoadShapeType::Stairs, "STAIRS"),
            (LoadShapeType::RampHold, "RAMP_HOLD"),
        ],
        LoadShapeType::Unknown,
    );
}

#[test]
fn load_shape_metric() {
    assert_policy(
        &[(LoadShapeMetric::Vu, "VU"), (LoadShapeMetric::Rate, "RATE")],
        LoadShapeMetric::Unknown,
    );
}

#[test]
fn load_threshold_metric() {
    assert_policy(
        &[
            (LoadThresholdMetric::LatencyP50, "LATENCY_P50"),
            (LoadThresholdMetric::LatencyP95, "LATENCY_P95"),
            (LoadThresholdMetric::LatencyP99, "LATENCY_P99"),
            (LoadThresholdMetric::LatencyP999, "LATENCY_P999"),
            (LoadThresholdMetric::ErrorRate, "ERROR_RATE"),
            (LoadThresholdMetric::ThroughputRps, "THROUGHPUT_RPS"),
            (LoadThresholdMetric::CheckFailureRate, "CHECK_FAILURE_RATE"),
        ],
        LoadThresholdMetric::Unknown,
    );
}

#[test]
fn load_comparator() {
    assert_policy(
        &[
            (LoadComparator::LessThan, "LESS_THAN"),
            (LoadComparator::LessThanOrEqual, "LESS_THAN_OR_EQUAL"),
            (LoadComparator::GreaterThan, "GREATER_THAN"),
            (LoadComparator::GreaterThanOrEqual, "GREATER_THAN_OR_EQUAL"),
        ],
        LoadComparator::Unknown,
    );
}

#[test]
fn load_pacing_mode() {
    assert_policy(
        &[
            (LoadPacingMode::None, "NONE"),
            (LoadPacingMode::ConstantPacing, "CONSTANT_PACING"),
            (LoadPacingMode::ConstantThroughput, "CONSTANT_THROUGHPUT"),
        ],
        LoadPacingMode::Unknown,
    );
}

#[test]
fn load_feeder_format() {
    assert_policy(
        &[
            (LoadFeederFormat::Csv, "CSV"),
            (LoadFeederFormat::Json, "JSON"),
        ],
        LoadFeederFormat::Unknown,
    );
}

#[test]
fn load_feeder_strategy() {
    assert_policy(
        &[
            (LoadFeederStrategy::Circular, "CIRCULAR"),
            (LoadFeederStrategy::Random, "RANDOM"),
            (LoadFeederStrategy::Sequential, "SEQUENTIAL"),
        ],
        LoadFeederStrategy::Unknown,
    );
}

#[test]
fn load_capture_source() {
    assert_policy(
        &[
            (LoadCaptureSource::BodyJsonpath, "BODY_JSONPATH"),
            (LoadCaptureSource::Header, "HEADER"),
            (LoadCaptureSource::BodyRegex, "BODY_REGEX"),
        ],
        LoadCaptureSource::Unknown,
    );
}

#[test]
fn load_check_source() {
    assert_policy(
        &[
            (LoadCheckSource::Status, "STATUS"),
            (LoadCheckSource::Header, "HEADER"),
            (LoadCheckSource::BodyJsonpath, "BODY_JSONPATH"),
        ],
        LoadCheckSource::Unknown,
    );
}

#[test]
fn load_check_comparator() {
    assert_policy(
        &[
            (LoadCheckComparator::Equals, "EQUALS"),
            (LoadCheckComparator::NotEquals, "NOT_EQUALS"),
            (LoadCheckComparator::Contains, "CONTAINS"),
            (LoadCheckComparator::Matches, "MATCHES"),
            (LoadCheckComparator::Gt, "GT"),
            (LoadCheckComparator::Lt, "LT"),
            (LoadCheckComparator::Gte, "GTE"),
            (LoadCheckComparator::Lte, "LTE"),
        ],
        LoadCheckComparator::Unknown,
    );
}

#[test]
fn load_step_selection() {
    assert_policy(
        &[
            (LoadStepSelection::Sequential, "SEQUENTIAL"),
            (LoadStepSelection::Weighted, "WEIGHTED"),
        ],
        LoadStepSelection::Unknown,
    );
}

#[test]
fn explain_reason() {
    assert_policy(
        &[
            (ExplainReason::Namespace, "NAMESPACE"),
            (ExplainReason::Inactive, "INACTIVE"),
            (ExplainReason::FieldsDiffer, "FIELDS_DIFFER"),
            (ExplainReason::NotOperator, "NOT_OPERATOR"),
            (ExplainReason::Conversation, "CONVERSATION"),
            (ExplainReason::ScenarioState, "SCENARIO_STATE"),
            (ExplainReason::Percentage, "PERCENTAGE"),
            (ExplainReason::LowerPriority, "LOWER_PRIORITY"),
        ],
        ExplainReason::Unknown,
    );
}

#[test]
fn arrival_outcome() {
    assert_policy(
        &[
            (ArrivalOutcome::Matched, "MATCHED"),
            (ArrivalOutcome::NoMatch, "NO_MATCH"),
            (
                ArrivalOutcome::ForwardedWithoutExpectation,
                "FORWARDED_WITHOUT_EXPECTATION",
            ),
            (
                ArrivalOutcome::RespondedWithoutExpectation,
                "RESPONDED_WITHOUT_EXPECTATION",
            ),
        ],
        ArrivalOutcome::Unknown,
    );
    let read: ArrivalOutcome = serde_json::from_value(json!("UNKNOWN")).unwrap();
    assert_eq!(read, ArrivalOutcome::Unknown);
}

// ---------------------------------------------------------------------------
// Whole objects: an unknown value must not fail the object that carries it
// ---------------------------------------------------------------------------

fn expectation_with_every_wire_enum() -> Expectation {
    Expectation::new(HttpRequest::new().path("/kept"))
        .response_mode(ResponseMode::Random)
        .respond_binary(BinaryResponse::from_bytes([1, 2]).upstream(BinaryUpstream::AnswerOnly))
        .cross_protocol_scenario(CrossProtocolScenario::new(
            CrossProtocolTrigger::DnsQuery,
            "scenario",
            "state",
        ))
}

fn expectation_json_from_newer_server() -> Value {
    let mut json = serde_json::to_value(expectation_with_every_wire_enum()).unwrap();
    json["responseMode"] = json!(NEWER_VALUE);
    json["binaryResponse"]["upstream"] = json!(NEWER_VALUE);
    json["crossProtocolScenarios"][0]["trigger"] = json!(NEWER_VALUE);
    json
}

#[test]
fn expectation_with_unknown_enum_values_reads_and_keeps_its_other_fields() {
    let json = expectation_json_from_newer_server();

    let read: Expectation = serde_json::from_value(json.clone()).unwrap();

    assert_eq!(read.response_mode, Some(ResponseMode::Unknown));
    assert_eq!(
        read.binary_response.as_ref().unwrap().upstream,
        Some(BinaryUpstream::Unknown)
    );
    assert_eq!(
        read.cross_protocol_scenarios.as_ref().unwrap()[0].trigger,
        CrossProtocolTrigger::Unknown
    );
    assert_eq!(
        read.http_request.as_ref().unwrap().path.as_deref(),
        Some("/kept")
    );
    assert_eq!(
        read.binary_response.as_ref().unwrap().binary_data,
        json["binaryResponse"]["binaryData"]
            .as_str()
            .map(str::to_string)
    );
    assert_eq!(
        read.cross_protocol_scenarios.as_ref().unwrap()[0].target_state,
        "state"
    );
}

#[test]
fn expectation_list_from_newer_server_reads_every_entry() {
    let list = json!([
        expectation_json_from_newer_server(),
        expectation_with_every_wire_enum()
    ]);

    let read: Vec<Expectation> = serde_json::from_value(list).unwrap();

    assert_eq!(read.len(), 2);
    assert_eq!(read[0].response_mode, Some(ResponseMode::Unknown));
    assert_eq!(read[1].response_mode, Some(ResponseMode::Random));
}

#[test]
fn expectation_with_known_enum_values_round_trips_unchanged() {
    let json = serde_json::to_value(expectation_with_every_wire_enum()).unwrap();

    let read: Expectation = serde_json::from_value(json.clone()).unwrap();

    assert_eq!(serde_json::to_value(&read).unwrap(), json);
    assert_eq!(json["responseMode"], "RANDOM");
    assert_eq!(json["binaryResponse"]["upstream"], "ANSWER_ONLY");
    assert_eq!(json["crossProtocolScenarios"][0]["trigger"], "DNS_QUERY");
}

#[test]
fn load_scenario_with_unknown_enum_values_reads() {
    let scenario = LoadScenario::new(
        "newer",
        LoadProfile::of(vec![LoadStage::vu_ramp(1, 2, 1_000, RampCurve::Linear)]),
        vec![LoadStep::new(HttpRequest::new().path("/kept"))],
    )
    .threshold(LoadThreshold::new(
        LoadThresholdMetric::ErrorRate,
        LoadComparator::LessThan,
        0.1,
    ))
    .pacing(LoadPacing::constant_throughput(2.0))
    .step_selection(LoadStepSelection::Weighted);
    let mut json = serde_json::to_value(&scenario).unwrap();
    json["profile"]["stages"][0]["type"] = json!(NEWER_VALUE);
    json["profile"]["stages"][0]["curve"] = json!(NEWER_VALUE);
    json["thresholds"][0]["metric"] = json!(NEWER_VALUE);
    json["thresholds"][0]["comparator"] = json!(NEWER_VALUE);
    json["pacing"]["mode"] = json!(NEWER_VALUE);
    json["stepSelection"] = json!(NEWER_VALUE);

    let read: LoadScenario = serde_json::from_value(json).unwrap();

    assert_eq!(read.profile.stages[0].stage_type, LoadStageType::Unknown);
    assert_eq!(read.profile.stages[0].curve, Some(RampCurve::Unknown));
    assert_eq!(read.thresholds[0].metric, LoadThresholdMetric::Unknown);
    assert_eq!(read.thresholds[0].comparator, LoadComparator::Unknown);
    assert_eq!(read.pacing.unwrap().mode, LoadPacingMode::Unknown);
    assert_eq!(read.step_selection, Some(LoadStepSelection::Unknown));
    assert_eq!(read.name, "newer");
}

// ---------------------------------------------------------------------------
// Sending Unknown: the client returns an error and sends nothing
// ---------------------------------------------------------------------------

/// A stub that answers every request with `201 []` and reports each one it
/// served, until `quiet_for` passes with no request.
fn counting_stub(quiet_for: Duration) -> (MockServerClient, mpsc::Receiver<String>) {
    let server = tiny_http::Server::http("127.0.0.1:0").expect("bind stub server");
    let port = match server.server_addr() {
        tiny_http::ListenAddr::IP(addr) => addr.port(),
        #[allow(unreachable_patterns)]
        _ => panic!("expected IP listen address"),
    };
    let (tx, rx) = mpsc::channel();
    thread::spawn(move || {
        while let Ok(Some(mut request)) = server.recv_timeout(quiet_for) {
            let mut body = String::new();
            request.as_reader().read_to_string(&mut body).ok();
            tx.send(body).ok();
            let response =
                tiny_http::Response::from_string("[]").with_status_code(tiny_http::StatusCode(201));
            request.respond(response).ok();
        }
    });
    let client = ClientBuilder::new("127.0.0.1", port)
        .build()
        .expect("build client");
    (client, rx)
}

/// Asserts the call failed as a JSON error naming `Unknown`, and that the stub
/// received no request.
fn assert_rejected_before_sending<T: std::fmt::Debug>(
    result: Result<T>,
    served: mpsc::Receiver<String>,
) {
    let err = result.expect_err("sending Unknown must fail");
    assert!(
        matches!(err, Error::Json(_)),
        "expected Error::Json, got: {err:?}"
    );
    assert!(err.to_string().contains("Unknown"), "got: {err}");
    assert!(
        served.recv_timeout(Duration::from_secs(3)).is_err(),
        "no request may reach the server"
    );
}

fn load_scenario_holding_unknown() -> LoadScenario {
    LoadScenario::new(
        "unknown",
        LoadProfile::of(vec![LoadStage::vu_ramp(1, 2, 1_000, RampCurve::Unknown)]),
        vec![LoadStep::new(HttpRequest::new().path("/"))],
    )
}

#[test]
fn upserting_an_expectation_holding_unknown_fails_and_sends_nothing() {
    let (client, served) = counting_stub(Duration::from_secs(2));
    let read: Expectation = serde_json::from_value(expectation_json_from_newer_server()).unwrap();

    assert_rejected_before_sending(client.upsert(&[read]), served);
}

#[test]
fn loading_a_scenario_holding_unknown_fails_and_sends_nothing() {
    let (client, served) = counting_stub(Duration::from_secs(2));

    assert_rejected_before_sending(
        client.load_scenario(&load_scenario_holding_unknown()),
        served,
    );
}

#[test]
fn running_a_scenario_holding_unknown_fails_and_sends_nothing() {
    let (client, served) = counting_stub(Duration::from_secs(2));

    assert_rejected_before_sending(
        client.run_load_scenario(&load_scenario_holding_unknown()),
        served,
    );
}

#[derive(serde::Serialize)]
#[serde(rename_all = "camelCase")]
struct GenerateRequest {
    name: String,
    spec_url_or_payload: String,
    profile: LoadProfile,
}

fn generate_request_holding_unknown() -> GenerateRequest {
    GenerateRequest {
        name: "generated".to_string(),
        spec_url_or_payload: "openapi.json".to_string(),
        profile: load_scenario_holding_unknown().profile,
    }
}

#[test]
fn generating_a_scenario_with_a_typed_body_holding_unknown_fails_and_sends_nothing() {
    let (client, served) = counting_stub(Duration::from_secs(2));
    assert_rejected_before_sending(
        client.generate_load_scenario_from_openapi(&generate_request_holding_unknown()),
        served,
    );

    let (client, served) = counting_stub(Duration::from_secs(2));
    assert_rejected_before_sending(
        client.generate_load_scenario_from_recording(&generate_request_holding_unknown()),
        served,
    );
}

// ---------------------------------------------------------------------------
// Guard: the policy holds for every public enum in the crate's source
// ---------------------------------------------------------------------------

struct EnumDecl {
    location: String,
    attributes: Vec<String>,
    body: Vec<String>,
}

fn public_enums() -> Vec<EnumDecl> {
    let src = PathBuf::from(env!("CARGO_MANIFEST_DIR")).join("src");
    let mut files: Vec<PathBuf> = std::fs::read_dir(&src)
        .unwrap()
        .map(|e| e.unwrap().path())
        .filter(|p| p.extension().is_some_and(|e| e == "rs"))
        .collect();
    files.sort();
    let mut enums = Vec::new();
    for file in files {
        let text = std::fs::read_to_string(&file).unwrap();
        let lines: Vec<&str> = text.lines().collect();
        for (i, line) in lines.iter().enumerate() {
            if !line.trim_start().starts_with("pub enum ") {
                continue;
            }
            let mut attributes = Vec::new();
            let mut j = i;
            while j > 0 {
                let above = lines[j - 1].trim();
                if above.starts_with("#[") || above.starts_with("///") {
                    attributes.push(above.to_string());
                    j -= 1;
                } else {
                    break;
                }
            }
            let mut body = Vec::new();
            for below in &lines[i + 1..] {
                if *below == "}" {
                    break;
                }
                body.push(below.trim().to_string());
            }
            enums.push(EnumDecl {
                location: format!("{}:{} {}", file.display(), i + 1, line.trim()),
                attributes,
                body,
            });
        }
    }
    enums
}

#[test]
fn every_public_enum_is_non_exhaustive() {
    let enums = public_enums();
    assert!(
        enums.len() >= 25,
        "expected the crate's public enums, found {}",
        enums.len()
    );

    let missing: Vec<&str> = enums
        .iter()
        .filter(|e| !e.attributes.iter().any(|a| a == "#[non_exhaustive]"))
        .map(|e| e.location.as_str())
        .collect();

    assert!(
        missing.is_empty(),
        "public enums without #[non_exhaustive]: {missing:#?}"
    );
}

#[test]
fn every_unit_wire_enum_reads_unknown_values_as_unknown() {
    let wire: Vec<EnumDecl> = public_enums()
        .into_iter()
        .filter(|e| {
            e.attributes
                .iter()
                .any(|a| a.starts_with("#[derive(") && a.contains("Deserialize"))
        })
        .filter(|e| !e.attributes.iter().any(|a| a.contains("untagged")))
        .collect();
    assert!(
        wire.len() >= 16,
        "expected the crate's wire enums, found {}",
        wire.len()
    );

    let missing: Vec<&str> = wire
        .iter()
        .filter(|e| {
            !e.body
                .windows(2)
                .any(|w| w[0] == "#[serde(other, skip_serializing)]" && w[1] == "Unknown,")
        })
        .map(|e| e.location.as_str())
        .collect();

    assert!(
        missing.is_empty(),
        "wire enums without `#[serde(other, skip_serializing)] Unknown`: {missing:#?}"
    );
}
