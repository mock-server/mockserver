/**
 * Universal anti-drop tripwire.
 *
 * For every corpus combo × every language emitter, this asserts that NO top-level
 * wire key of buildExpectationJson(combo) is ABSENT from the emitted source. This
 * is the guard whose absence let the reported regression ship: two emitters
 * (python, ruby) silently DROPPED a preserved action, and others degraded it to a
 * JSON blob — none of which a per-language golden could catch, because the
 * corpus never exercised the preserved-action keys.
 *
 * Contract encoded per emitter:
 *  - the six client-library emitters (node/python/go/csharp/ruby/rust) MUST render
 *    every key TYPED — a declared, low-false-positive marker (the typed construct
 *    the key maps to) must appear in the output;
 *  - the Java tab (standardToJava) MUST render every key typed OR name it in an
 *    explicit NOTE / whole-action notice — never a silent drop. (Its compile
 *    correctness is separately guarded by the .buildkite javac gate.)
 *
 * A combo top-level key with no declared marker for a strict language FAILS the
 * test — forcing a deliberate marker declaration when the corpus grows, which is
 * exactly the tripwire that did not exist before.
 */
import { describe, it, expect } from 'vitest';
import { buildExpectationJson } from '../standardCodegen';
import { standardToNode } from './node';
import { standardToPython } from './python';
import { standardToGo } from './go';
import { standardToCsharp } from './csharp';
import { standardToRuby } from './ruby';
import { standardToRust } from './rust';
import { standardToJava, type StandardMatcher, type StandardActionPayload } from '../standardCodegen';
import { combos } from './extractParityCases';

type Lang = 'node' | 'python' | 'go' | 'csharp' | 'ruby' | 'rust' | 'java';
type Marker = string | string[];

/**
 * key → per-language marker(s). A marker is a substring the emitted source MUST
 * contain when the key is present; an array means "any one of these". Node keys
 * are the verbatim JSON property (`"key":`) so they are derived, not listed.
 */
const MARKERS: Record<string, Partial<Record<Lang, Marker>>> = {
  httpRequest: { python: 'http_request=', go: 'HttpRequest:', csharp: 'HttpRequest =', ruby: 'http_request:', rust: 'Expectation::new(', java: ['request()', 'dnsRequest()'] },
  httpResponse: { python: 'http_response=', go: 'HttpResponse:', csharp: 'HttpResponse =', ruby: 'http_response:', rust: '.respond(', java: '.respond(' },
  httpResponseTemplate: { python: 'http_response_template=', go: 'HttpResponseTemplate:', csharp: 'HttpResponseTemplate =', ruby: 'http_response_template:', rust: '.respond_template(', java: 'template(TemplateType' },
  httpResponseClassCallback: { python: 'http_response_class_callback=', go: 'HttpResponseClassCallback:', csharp: 'HttpResponseClassCallback =', ruby: 'http_response_class_callback:', rust: ['.respond_with_class_callback(', '.respond_class_callback('], java: 'callback()' },
  httpForward: { python: 'http_forward=', go: 'HttpForward:', csharp: 'HttpForward =', ruby: 'http_forward:', rust: '.forward(', java: 'forward()' },
  httpForwardWithFallback: { python: 'http_forward_with_fallback=', go: 'HttpForwardWithFallback:', csharp: 'HttpForwardWithFallback =', ruby: 'http_forward_with_fallback:', rust: '.forward_with_fallback(', java: 'forwardWithFallback(' },
  httpOverrideForwardedRequest: { python: 'http_override_forwarded_request=', go: 'HttpOverrideForwardedRequest:', csharp: 'HttpOverrideForwardedRequest =', ruby: 'http_override_forwarded_request:', rust: '.override_forwarded_request(', java: 'forwardOverriddenRequest(' },
  httpError: { python: 'http_error=', go: 'HttpError:', csharp: 'HttpError =', ruby: 'http_error:', rust: '.error(', java: 'error()' },
  httpWebSocketResponse: { python: 'http_websocket_response=', go: 'HttpWebSocketResponse:', csharp: 'HttpWebSocketResponse =', ruby: 'http_websocket_response:', rust: '.respond_web_socket(', java: 'webSocketResponse()' },
  httpSseResponse: { python: 'http_sse_response=', go: 'HttpSseResponse:', csharp: 'HttpSseResponse =', ruby: 'http_sse_response:', rust: '.respond_sse(', java: 'sseResponse()' },
  binaryResponse: { python: 'binary_response=', go: 'BinaryResponse:', csharp: 'BinaryResponse =', ruby: 'binary_response:', rust: '.respond_binary(', java: 'binaryResponse()' },
  dnsResponse: { python: 'dns_response=', go: 'DnsResponse:', csharp: 'DnsResponse =', ruby: 'dns_response:', rust: ['.respond_dns(', 'dnsResponse'], java: 'dnsResponse()' },
  grpcStreamResponse: { python: 'grpc_stream_response=', go: 'GrpcStreamResponse:', csharp: 'GrpcStreamResponse =', ruby: 'grpc_stream_response:', rust: '.respond_grpc_stream(', java: 'grpcStreamResponse()' },
  steps: { python: 'steps=', go: 'Steps:', csharp: 'Steps =', ruby: 'steps:', rust: '.steps(', java: '.withSteps(' },
  afterActions: { python: 'after_actions=', go: 'AfterActions:', csharp: 'AfterActions =', ruby: 'after_actions:', rust: '.after_action(', java: '.withAfterAction(' },
  capture: { python: 'capture=', go: 'Capture:', csharp: 'Capture =', ruby: 'capture:', rust: '.capture_rule(', java: '.withCapture(' },
  scenarioName: { python: 'scenario_name=', go: 'ScenarioName:', csharp: 'ScenarioName =', ruby: 'scenario_name:', rust: '.scenario_name(', java: '.withScenarioName(' },
  scenarioState: { python: 'scenario_state=', go: 'ScenarioState:', csharp: 'ScenarioState =', ruby: 'scenario_state:', rust: '.scenario_state(', java: '.withScenarioState(' },
  newScenarioState: { python: 'new_scenario_state=', go: 'NewScenarioState:', csharp: 'NewScenarioState =', ruby: 'new_scenario_state:', rust: '.new_scenario_state(', java: '.withNewScenarioState(' },
  priority: { python: 'priority=', go: 'Priority:', csharp: 'Priority =', ruby: 'priority:', rust: '.priority(', java: 'Times.exactly' /* 4-arg when(...) present alongside priority */ },
  times: { python: 'times=', go: 'Times:', csharp: 'Times =', ruby: 'times:', rust: '.times(', java: 'Times.exactly' },
  timeToLive: { python: 'time_to_live=', go: 'TimeToLive:', csharp: 'TimeToLive =', ruby: 'time_to_live:', rust: '.time_to_live(', java: 'TimeToLive.exactly' },
  namespace: { python: 'namespace=', go: 'Namespace:', csharp: 'Namespace =', ruby: 'namespace:', rust: '.namespace(', java: '.withNamespace(' },
  metadata: { python: 'metadata=', go: 'Metadata:', csharp: 'Metadata =', ruby: 'metadata:', rust: '.metadata_entry(', java: '.withMetadata(' },
  // --- edit-preserved actions / siblings (the regression surface) ---
  httpLlmResponse: { python: 'http_llm_response=', go: 'HttpLlmResponse:', csharp: 'HttpLlmResponse =', ruby: 'http_llm_response:', rust: '.respond_llm(', java: '.respondWithLlm(' },
  httpResponses: { python: 'http_responses=', go: 'HttpResponses:', csharp: 'HttpResponses =', ruby: 'http_responses:', rust: '.http_responses(', java: '.respond(Arrays.asList(' },
  responseMode: { python: 'response_mode=', go: 'ResponseMode:', csharp: 'ResponseMode =', ruby: 'response_mode:', rust: '.response_mode(', java: '.withResponseMode(' },
  responseWeights: { python: 'response_weights=', go: 'ResponseWeights:', csharp: 'ResponseWeights =', ruby: 'response_weights:', rust: '.response_weights(', java: '.withResponseWeights(' },
  switchAfter: { python: 'switch_after=', go: 'SwitchAfter:', csharp: 'SwitchAfter =', ruby: 'switch_after:', rust: '.switch_after(', java: '.withSwitchAfter(' },
  httpResponseObjectCallback: { python: 'http_response_object_callback=', go: 'HttpResponseObjectCallback:', csharp: 'HttpResponseObjectCallback =', ruby: 'http_response_object_callback:', rust: '.respond_object_callback(', java: 'httpResponseObjectCallback' },
  httpForwardObjectCallback: { python: 'http_forward_object_callback=', go: 'HttpForwardObjectCallback:', csharp: 'HttpForwardObjectCallback =', ruby: 'http_forward_object_callback:', rust: '.forward_object_callback(', java: 'httpForwardObjectCallback' },
  httpForwardValidateAction: { python: 'http_forward_validate_action=', go: 'HttpForwardValidateAction:', csharp: 'HttpForwardValidateAction =', ruby: 'http_forward_validate_action:', rust: '.forward_validate(', java: 'httpForwardValidateAction' },
  grpcBidiResponse: { python: 'grpc_bidi_response=', go: 'GrpcBidiResponse:', csharp: 'GrpcBidiResponse =', ruby: 'grpc_bidi_response:', rust: '.respond_grpc_bidi(', java: 'grpcBidiResponse' },
  rateLimit: { python: 'rate_limit=', go: 'RateLimit:', csharp: 'RateLimit =', ruby: 'rate_limit:', rust: '.rate_limit(', java: 'rateLimit' },
  crossProtocolScenarios: { python: 'cross_protocol_scenarios=', go: 'CrossProtocolScenarios:', csharp: 'CrossProtocolScenarios =', ruby: 'cross_protocol_scenarios:', rust: '.cross_protocol_scenarios(', java: '.withCrossProtocolScenario(' },
  percentage: { python: 'percentage=', go: 'Percentage:', csharp: 'Percentage =', ruby: 'percentage:', rust: '.percentage(', java: '.withPercentage(' },
  timestamp: { python: 'timestamp=', go: 'Timestamp:', csharp: 'Timestamp =', ruby: 'timestamp:', rust: 'expectation.timestamp =', java: 'timestamp' },
};

const STRICT_LANGS: Lang[] = ['node', 'python', 'go', 'csharp', 'ruby', 'rust'];

function contains(src: string, marker: Marker): boolean {
  return Array.isArray(marker) ? marker.some((m) => src.includes(m)) : src.includes(marker);
}

function emit(lang: Lang, combo: (typeof combos)[number]): string {
  const m: StandardMatcher = combo.matcher;
  const a: StandardActionPayload = combo.action;
  switch (lang) {
    case 'node': return standardToNode(m, a, combo.baseUrl);
    case 'python': return standardToPython(m, a, combo.baseUrl);
    case 'go': return standardToGo(m, a, combo.baseUrl);
    case 'csharp': return standardToCsharp(m, a, combo.baseUrl);
    case 'ruby': return standardToRuby(m, a, combo.baseUrl);
    case 'rust': return standardToRust(m, a, combo.baseUrl);
    case 'java': return standardToJava(m, a);
  }
}

describe('universal anti-drop guard: every top-level wire key is present in every emitter', () => {
  for (const combo of combos) {
    const keys = Object.keys(buildExpectationJson(combo.matcher, combo.action));
    const sources: Record<Lang, string> = {
      node: emit('node', combo), python: emit('python', combo), go: emit('go', combo),
      csharp: emit('csharp', combo), ruby: emit('ruby', combo), rust: emit('rust', combo), java: emit('java', combo),
    };

    it(`${combo.name}: strict-typed emitters carry every key`, () => {
      for (const key of keys) {
        const markers = MARKERS[key];
        expect(markers, `no declared marker table for wire key "${key}" — add one to keep the tripwire honest`).toBeDefined();
        for (const lang of STRICT_LANGS) {
          // Node emits the verbatim JSON literal, so the key name is exact.
          const marker: Marker = lang === 'node' ? `"${key}":` : (markers![lang] as Marker);
          expect(marker, `no ${lang} marker for wire key "${key}"`).toBeDefined();
          expect(
            contains(sources[lang], marker),
            `${lang} emitter DROPPED wire key "${key}" (expected marker ${JSON.stringify(marker)}) for combo "${combo.name}"`,
          ).toBe(true);
        }
      }
    });

    it(`${combo.name}: Java carries every key typed or explicitly noticed`, () => {
      const src = sources.java;
      const punted = src.includes('cannot represent'); // honest whole-action notice
      for (const key of keys) {
        const marker = MARKERS[key]?.java;
        const ok = punted || (marker !== undefined && contains(src, marker)) || src.includes(key);
        expect(ok, `Java emitter silently dropped wire key "${key}" for combo "${combo.name}"`).toBe(true);
      }
    });
  }
});
