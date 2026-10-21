/**
 * Go client-library emitter. Emits idiomatic, fully-typed construction code for
 * the MockServer Go client (github.com/mock-server/mockserver-monorepo/
 * mockserver-client-go): the request matcher is built with the fluent
 * `mockserver.Request()` builder and the typed body constructors
 * (StringBody / JSONMatchBody / XPathBody / AllOf / …); every action, chaos
 * profile, LLM response, response sequence, step, capture rule, scenario binding
 * and lifecycle control is emitted as a typed struct literal on a
 * `mockserver.Expectation`, which is registered with `client.Upsert(...)`.
 *
 * No `json.Unmarshal` of an embedded JSON blob is emitted — the generated code
 * constructs the same expectation the JSON tab shows using the client's own
 * types, so it reads like hand-written client code the website examples show.
 *
 * The emitter walks the wire JSON produced by {@link buildExpectationJson} (so it
 * automatically covers every feature that builder emits, including edit-overlay
 * passthrough fields such as a preserved `httpLlmResponse` or `httpResponses`
 * sequence). Each wire key is mapped to its Go struct field via the STRUCTS
 * registry below; the field names and JSON keys are pinned to the Go client's
 * model files (expectation.go / request.go / response.go / forward.go /
 * template.go / callback.go / streaming_response.go / expectation_model.go /
 * llm.go). Raw-JSON passthrough (a Go `map[string]interface{}` literal) is used
 * only for genuinely interface-typed fields (HttpLlmResponse.chaos) or a fragment
 * the Go model cannot carry.
 */
import { buildExpectationJson, type StandardMatcher, type StandardActionPayload } from '../standardCodegen.ts';
import { clientHostPort } from './shared.ts';

// ---------------------------------------------------------------------------
// Struct field registry — maps each Go struct type to its [wireKey, goField,
// kind] field specs. `kind` selects how the child value is rendered. Field names
// are pinned to the Go client model source.
// ---------------------------------------------------------------------------

type FieldSpec = [wireKey: string, goField: string, kind: string];

const STRUCTS: Record<string, FieldSpec[]> = {
  HttpRequest: [
    ['method', 'Method', 'string'],
    ['path', 'Path', 'string'],
    ['queryStringParameters', 'QueryStringParams', 'headers'],
    ['headers', 'Headers', 'headers'],
    ['cookies', 'Cookies', 'cookies'],
    ['pathParameters', 'PathParametersList', 'headers'],
    ['body', 'Body', 'body'],
    ['jwt', 'JWT', 'struct:Jwt'],
    ['secure', 'Secure', 'boolPtr'],
    ['keepAlive', 'KeepAlive', 'boolPtr'],
    ['not', 'Not', 'boolPtr'],
    ['respondBeforeBody', 'RespondBeforeBody', 'boolPtr'],
    ['protocol', 'Protocol', 'string'],
    ['socketAddress', 'SocketAddress', 'struct:SocketAddress'],
    ['dnsName', 'DnsName', 'string'],
    ['dnsType', 'DnsType', 'string'],
    ['dnsClass', 'DnsClass', 'string'],
    ['specUrlOrPayload', 'SpecUrlOrPayload', 'raw'],
    ['operationId', 'OperationId', 'string'],
    ['contextPathPrefix', 'ContextPathPrefix', 'string'],
  ],
  SocketAddress: [
    ['host', 'Host', 'string'],
    ['port', 'Port', 'int'],
    ['scheme', 'Scheme', 'string'],
  ],
  Jwt: [
    ['claims', 'Claims', 'cookies'],
    ['issuer', 'Issuer', 'string'],
    ['audience', 'Audience', 'string'],
    ['algorithm', 'Algorithm', 'string'],
    ['header', 'Header', 'string'],
    ['scheme', 'Scheme', 'string'],
  ],
  HttpResponse: [
    ['statusCode', 'StatusCode', 'int'],
    ['reasonPhrase', 'ReasonPhrase', 'string'],
    ['headers', 'Headers', 'headers'],
    ['cookies', 'Cookies', 'cookies'],
    ['body', 'Body', 'body'],
    ['trailers', 'Trailers', 'headers'],
    ['statusCodeRange', 'StatusCodeRange', 'string'],
    ['recoverAfter', 'RecoverAfter', 'struct:RecoverAfter'],
    ['connectionOptions', 'ConnectionOptions', 'struct:ConnectionOptions'],
    ['delay', 'Delay', 'struct:Delay'],
    ['primary', 'Primary', 'boolPtr'],
  ],
  RecoverAfter: [
    ['failTimes', 'FailTimes', 'intPtr'],
    ['failResponse', 'FailResponse', 'struct:HttpResponse'],
    ['idempotencyHeader', 'IdempotencyHeader', 'string'],
  ],
  ConnectionOptions: [
    ['suppressContentLengthHeader', 'SuppressContentLengthHeader', 'boolPtr'],
    ['contentLengthHeaderOverride', 'ContentLengthHeaderOverride', 'intPtr'],
    ['suppressConnectionHeader', 'SuppressConnectionHeader', 'boolPtr'],
    ['chunkSize', 'ChunkSize', 'intPtr'],
    ['keepAliveOverride', 'KeepAliveOverride', 'boolPtr'],
    ['closeSocket', 'CloseSocket', 'boolPtr'],
    ['closeSocketDelay', 'CloseSocketDelay', 'struct:Delay'],
  ],
  Delay: [
    ['timeUnit', 'TimeUnit', 'string'],
    ['value', 'Value', 'int'],
    ['template', 'Template', 'string'],
    ['templateType', 'TemplateType', 'string'],
    ['distribution', 'Distribution', 'struct:DelayDistribution'],
  ],
  DelayDistribution: [
    ['type', 'Type', 'string'],
    ['min', 'Min', 'intPtr'],
    ['max', 'Max', 'intPtr'],
    ['median', 'Median', 'intPtr'],
    ['p99', 'P99', 'intPtr'],
    ['mean', 'Mean', 'intPtr'],
    ['stdDev', 'StdDev', 'intPtr'],
  ],
  HttpForward: [
    ['host', 'Host', 'string'],
    ['port', 'Port', 'int'],
    ['scheme', 'Scheme', 'string'],
    ['delay', 'Delay', 'struct:Delay'],
    ['primary', 'Primary', 'boolPtr'],
  ],
  HttpOverrideForwardedRequest: [
    ['requestOverride', 'RequestOverride', 'httpRequestPtr'],
    ['requestModifier', 'RequestModifier', 'struct:RequestModifier'],
    ['responseOverride', 'ResponseOverride', 'struct:HttpResponse'],
    ['responseModifier', 'ResponseModifier', 'struct:ResponseModifier'],
    ['responseTemplate', 'ResponseTemplate', 'struct:HttpTemplate'],
    ['httpRequest', 'HttpRequest', 'httpRequestPtr'],
    ['httpResponse', 'HttpResponse', 'struct:HttpResponse'],
    ['delay', 'Delay', 'struct:Delay'],
    ['primary', 'Primary', 'boolPtr'],
  ],
  RequestModifier: [
    ['path', 'Path', 'struct:PathModifier'],
    ['queryStringParameters', 'QueryStringParameters', 'struct:QueryParametersModifier'],
    ['headers', 'Headers', 'struct:HeadersModifier'],
    ['cookies', 'Cookies', 'struct:CookiesModifier'],
  ],
  ResponseModifier: [
    ['headers', 'Headers', 'struct:HeadersModifier'],
    ['cookies', 'Cookies', 'struct:CookiesModifier'],
  ],
  PathModifier: [
    ['regex', 'Regex', 'string'],
    ['substitution', 'Substitution', 'string'],
  ],
  HeadersModifier: [
    ['add', 'Add', 'headers'],
    ['replace', 'Replace', 'headers'],
    ['remove', 'Remove', 'stringSlice'],
  ],
  QueryParametersModifier: [
    ['add', 'Add', 'headers'],
    ['replace', 'Replace', 'headers'],
    ['remove', 'Remove', 'stringSlice'],
  ],
  CookiesModifier: [
    ['add', 'Add', 'cookies'],
    ['replace', 'Replace', 'cookies'],
    ['remove', 'Remove', 'stringSlice'],
  ],
  HttpTemplate: [
    ['templateType', 'TemplateType', 'string'],
    ['template', 'Template', 'string'],
    ['templateFile', 'TemplateFile', 'string'],
    ['delay', 'Delay', 'struct:Delay'],
    ['primary', 'Primary', 'boolPtr'],
    ['responseOverride', 'ResponseOverride', 'struct:HttpResponse'],
    ['responseModifier', 'ResponseModifier', 'struct:ResponseModifier'],
  ],
  HttpError: [
    ['dropConnection', 'DropConnection', 'boolPtr'],
    ['responseBytes', 'ResponseBytes', 'string'],
    ['streamError', 'StreamError', 'int64Ptr'],
    ['delay', 'Delay', 'struct:Delay'],
    ['primary', 'Primary', 'boolPtr'],
  ],
  HttpClassCallback: [
    ['callbackClass', 'CallbackClass', 'string'],
    ['delay', 'Delay', 'struct:Delay'],
    ['primary', 'Primary', 'boolPtr'],
  ],
  HttpObjectCallback: [
    ['clientId', 'ClientId', 'string'],
    ['responseCallback', 'ResponseCallback', 'boolPtr'],
    ['delay', 'Delay', 'struct:Delay'],
    ['primary', 'Primary', 'boolPtr'],
  ],
  HttpForwardWithFallback: [
    ['httpForward', 'HttpForward', 'struct:HttpForward'],
    ['fallbackResponse', 'FallbackResponse', 'struct:HttpResponse'],
    ['fallbackOnStatusCodes', 'FallbackOnStatusCodes', 'intSlice'],
    ['fallbackOnTimeout', 'FallbackOnTimeout', 'boolPtr'],
    ['delay', 'Delay', 'struct:Delay'],
    ['primary', 'Primary', 'boolPtr'],
  ],
  HttpForwardValidateAction: [
    ['specUrlOrPayload', 'SpecUrlOrPayload', 'string'],
    ['host', 'Host', 'string'],
    ['port', 'Port', 'int'],
    ['scheme', 'Scheme', 'string'],
    ['validateRequest', 'ValidateRequest', 'boolPtr'],
    ['validateResponse', 'ValidateResponse', 'boolPtr'],
    ['validationMode', 'ValidationMode', 'string'],
    ['delay', 'Delay', 'struct:Delay'],
    ['primary', 'Primary', 'boolPtr'],
  ],
  HttpWebSocketResponse: [
    ['subprotocol', 'Subprotocol', 'string'],
    ['messages', 'Messages', 'structSlice:WebSocketMessage'],
    ['matchers', 'Matchers', 'structSlice:WebSocketMatcher'],
    ['closeConnection', 'CloseConnection', 'boolPtr'],
    ['delay', 'Delay', 'struct:Delay'],
    ['primary', 'Primary', 'boolPtr'],
  ],
  WebSocketMessage: [
    ['text', 'Text', 'string'],
    ['binary', 'Binary', 'string'],
    ['delay', 'Delay', 'struct:Delay'],
  ],
  WebSocketMatcher: [
    ['frameType', 'FrameType', 'string'],
    ['textMatcher', 'TextMatcher', 'string'],
    ['responses', 'Responses', 'structSlice:WebSocketMessage'],
  ],
  HttpSseResponse: [
    ['statusCode', 'StatusCode', 'int'],
    ['headers', 'Headers', 'headers'],
    ['events', 'Events', 'structSlice:SseEvent'],
    ['closeConnection', 'CloseConnection', 'boolPtr'],
    ['delay', 'Delay', 'struct:Delay'],
    ['primary', 'Primary', 'boolPtr'],
  ],
  SseEvent: [
    ['event', 'Event', 'string'],
    ['data', 'Data', 'string'],
    ['id', 'ID', 'string'],
    ['retry', 'Retry', 'int'],
    ['delay', 'Delay', 'struct:Delay'],
  ],
  BinaryResponse: [
    ['binaryData', 'BinaryData', 'string'],
    ['delay', 'Delay', 'struct:Delay'],
    ['primary', 'Primary', 'boolPtr'],
    ['upstream', 'Upstream', 'string'],
  ],
  DnsResponse: [
    ['responseCode', 'ResponseCode', 'string'],
    ['answerRecords', 'AnswerRecords', 'structSlice:DnsRecord'],
    ['authorityRecords', 'AuthorityRecords', 'structSlice:DnsRecord'],
    ['additionalRecords', 'AdditionalRecords', 'structSlice:DnsRecord'],
    ['delay', 'Delay', 'struct:Delay'],
    ['primary', 'Primary', 'boolPtr'],
  ],
  DnsRecord: [
    ['name', 'Name', 'string'],
    ['type', 'Type', 'string'],
    ['dnsClass', 'DnsClass', 'string'],
    ['ttl', 'TTL', 'int'],
    ['value', 'Value', 'string'],
    ['priority', 'Priority', 'int'],
    ['weight', 'Weight', 'int'],
    ['port', 'Port', 'int'],
  ],
  GrpcStreamResponse: [
    ['statusName', 'StatusName', 'string'],
    ['statusMessage', 'StatusMessage', 'string'],
    ['headers', 'Headers', 'headers'],
    ['messages', 'Messages', 'structSlice:GrpcStreamMessage'],
    ['closeConnection', 'CloseConnection', 'boolPtr'],
    ['delay', 'Delay', 'struct:Delay'],
    ['primary', 'Primary', 'boolPtr'],
  ],
  GrpcStreamMessage: [
    ['json', 'JSON', 'string'],
    ['templateType', 'TemplateType', 'string'],
    ['delay', 'Delay', 'struct:Delay'],
  ],
  GrpcBidiResponse: [
    ['statusName', 'StatusName', 'string'],
    ['statusMessage', 'StatusMessage', 'string'],
    ['headers', 'Headers', 'headers'],
    ['messages', 'Messages', 'structSlice:GrpcStreamMessage'],
    ['rules', 'Rules', 'structSlice:GrpcBidiRule'],
    ['closeConnection', 'CloseConnection', 'boolPtr'],
    ['delay', 'Delay', 'struct:Delay'],
    ['primary', 'Primary', 'boolPtr'],
  ],
  GrpcBidiRule: [
    ['matchJson', 'MatchJson', 'string'],
    ['responses', 'Responses', 'structSlice:GrpcStreamMessage'],
  ],
  HttpChaosProfile: [
    ['errorStatus', 'ErrorStatus', 'int'],
    ['retryAfter', 'RetryAfter', 'string'],
    ['errorProbability', 'ErrorProbability', 'floatPtr'],
    ['dropConnectionProbability', 'DropConnectionProbability', 'floatPtr'],
    ['latency', 'Latency', 'struct:Delay'],
    ['seed', 'Seed', 'int64Ptr'],
    ['succeedFirst', 'SucceedFirst', 'intPtr'],
    ['failRequestCount', 'FailRequestCount', 'intPtr'],
    ['outageAfterMillis', 'OutageAfterMillis', 'int64Ptr'],
    ['outageDurationMillis', 'OutageDurationMillis', 'int64Ptr'],
    ['truncateBodyAtFraction', 'TruncateBodyAtFraction', 'floatPtr'],
    ['malformedBody', 'MalformedBody', 'boolPtr'],
    ['slowResponseChunkSize', 'SlowResponseChunkSize', 'int'],
    ['slowResponseChunkDelay', 'SlowResponseChunkDelay', 'struct:Delay'],
    ['quotaName', 'QuotaName', 'string'],
    ['quotaLimit', 'QuotaLimit', 'int'],
    ['quotaWindowMillis', 'QuotaWindowMillis', 'int64Ptr'],
    ['quotaErrorStatus', 'QuotaErrorStatus', 'int'],
    ['degradationRampMillis', 'DegradationRampMillis', 'int64Ptr'],
    ['graphqlErrors', 'GraphqlErrors', 'boolPtr'],
    ['graphqlErrorMessage', 'GraphqlErrorMessage', 'string'],
    ['graphqlErrorCode', 'GraphqlErrorCode', 'string'],
    ['graphqlNullifyData', 'GraphqlNullifyData', 'boolPtr'],
  ],
  RateLimit: [
    ['name', 'Name', 'string'],
    ['algorithm', 'Algorithm', 'string'],
    ['limit', 'Limit', 'int'],
    ['windowMillis', 'WindowMillis', 'int'],
    ['burst', 'Burst', 'int'],
    ['refillPerSecond', 'RefillPerSecond', 'floatPtr'],
    ['errorStatus', 'ErrorStatus', 'int'],
    ['retryAfter', 'RetryAfter', 'string'],
  ],
  AfterAction: [
    ['httpRequest', 'HttpRequest', 'httpRequestPtr'],
    ['httpRequestTemplate', 'HttpRequestTemplate', 'struct:HttpTemplate'],
    ['httpClassCallback', 'HttpClassCallback', 'struct:HttpClassCallback'],
    ['httpObjectCallback', 'HttpObjectCallback', 'struct:HttpObjectCallback'],
    ['delay', 'Delay', 'struct:Delay'],
    ['blocking', 'Blocking', 'boolPtr'],
    ['timeout', 'Timeout', 'struct:Delay'],
    ['failurePolicy', 'FailurePolicy', 'string'],
  ],
  ExpectationStep: [
    ['httpRequest', 'HttpRequest', 'httpRequestPtr'],
    ['httpClassCallback', 'HttpClassCallback', 'struct:HttpClassCallback'],
    ['httpObjectCallback', 'HttpObjectCallback', 'struct:HttpObjectCallback'],
    ['httpForward', 'HttpForward', 'struct:HttpForward'],
    ['httpOverrideForwardedRequest', 'HttpOverrideForwardedRequest', 'struct:HttpOverrideForwardedRequest'],
    ['httpResponse', 'HttpResponse', 'struct:HttpResponse'],
    ['httpError', 'HttpError', 'struct:HttpError'],
    ['responder', 'Responder', 'boolPtr'],
    ['delay', 'Delay', 'struct:Delay'],
    ['blocking', 'Blocking', 'boolPtr'],
    ['timeout', 'Timeout', 'struct:Delay'],
    ['failurePolicy', 'FailurePolicy', 'string'],
  ],
  CaptureRule: [
    ['source', 'Source', 'string'],
    ['expression', 'Expression', 'string'],
    ['into', 'Into', 'string'],
  ],
  CrossProtocolScenario: [
    ['trigger', 'Trigger', 'string'],
    ['matchPattern', 'MatchPattern', 'string'],
    ['scenarioName', 'ScenarioName', 'string'],
    ['targetState', 'TargetState', 'string'],
  ],
  Times: [
    ['remainingTimes', 'RemainingTimes', 'int'],
    ['unlimited', 'Unlimited', 'bool'],
  ],
  TimeToLive: [
    ['timeUnit', 'TimeUnit', 'string'],
    ['timeToLive', 'TimeToLive', 'int'],
    ['unlimited', 'Unlimited', 'bool'],
  ],
  HttpLlmResponse: [
    ['provider', 'Provider', 'string'],
    ['model', 'Model', 'string'],
    ['completion', 'Completion', 'struct:Completion'],
    ['embedding', 'Embedding', 'struct:EmbeddingResponse'],
    ['rerank', 'Rerank', 'struct:RerankResponse'],
    ['moderation', 'Moderation', 'struct:ModerationResponse'],
    ['contentFilter', 'ContentFilter', 'struct:ContentFilterResponse'],
    ['conversationPredicates', 'ConversationPredicates', 'struct:ConversationPredicates'],
    ['chaos', 'Chaos', 'raw'],
    ['delay', 'Delay', 'struct:Delay'],
    ['primary', 'Primary', 'boolPtr'],
  ],
  Completion: [
    ['text', 'Text', 'string'],
    ['toolCalls', 'ToolCalls', 'structPtrSlice:ToolUse'],
    ['stopReason', 'StopReason', 'string'],
    ['usage', 'Usage', 'struct:Usage'],
    ['streaming', 'Streaming', 'boolPtr'],
    ['streamingPhysics', 'StreamingPhysics', 'struct:StreamingPhysics'],
    ['outputSchema', 'OutputSchema', 'string'],
    ['enforceOutputSchema', 'EnforceOutputSchema', 'boolPtr'],
    ['toolChoice', 'ToolChoice', 'string'],
    ['reasoningText', 'ReasoningText', 'string'],
    ['reasoningSignature', 'ReasoningSignature', 'string'],
    ['model', 'Model', 'string'],
  ],
  ToolUse: [
    ['id', 'ID', 'string'],
    ['name', 'Name', 'string'],
    ['arguments', 'Arguments', 'string'],
  ],
  Usage: [
    ['inputTokens', 'InputTokens', 'intPtr'],
    ['outputTokens', 'OutputTokens', 'intPtr'],
    ['cachedInputTokens', 'CachedInputTokens', 'intPtr'],
    ['cacheCreationTokens', 'CacheCreationTokens', 'intPtr'],
    ['reasoningTokens', 'ReasoningTokens', 'intPtr'],
  ],
  StreamingPhysics: [
    ['timeToFirstToken', 'TimeToFirstToken', 'struct:Delay'],
    ['tokensPerSecond', 'TokensPerSecond', 'intPtr'],
    ['jitter', 'Jitter', 'floatPtr'],
    ['seed', 'Seed', 'int64Ptr'],
    ['subwordStreaming', 'SubwordStreaming', 'boolPtr'],
  ],
  EmbeddingResponse: [
    ['dimensions', 'Dimensions', 'intPtr'],
    ['deterministicFromInput', 'DeterministicFromInput', 'boolPtr'],
    ['seed', 'Seed', 'int64Ptr'],
  ],
  RerankResponse: [
    ['topN', 'TopN', 'intPtr'],
    ['deterministicFromInput', 'DeterministicFromInput', 'boolPtr'],
    ['seed', 'Seed', 'int64Ptr'],
  ],
  ModerationResponse: [
    ['flaggedCategories', 'FlaggedCategories', 'stringSlice'],
    ['model', 'Model', 'string'],
  ],
  ContentFilterResponse: [
    ['hate', 'Hate', 'string'],
    ['sexual', 'Sexual', 'string'],
    ['violence', 'Violence', 'string'],
    ['selfHarm', 'SelfHarm', 'string'],
  ],
  ConversationPredicates: [
    ['turnIndex', 'TurnIndex', 'intPtr'],
    ['latestMessageContains', 'LatestMessageContains', 'string'],
    ['latestMessageMatches', 'LatestMessageMatches', 'string'],
    ['latestMessageRole', 'LatestMessageRole', 'string'],
    ['containsToolResultFor', 'ContainsToolResultFor', 'string'],
    ['semanticMatchAgainst', 'SemanticMatchAgainst', 'string'],
    ['normalization', 'Normalization', 'struct:NormalizationOptions'],
  ],
  NormalizationOptions: [
    ['collapseWhitespace', 'CollapseWhitespace', 'boolPtr'],
    ['lowercase', 'Lowercase', 'boolPtr'],
    ['sortJsonKeys', 'SortJSONKeys', 'boolPtr'],
    ['dropBuiltInVolatileFields', 'DropBuiltInVolatileFields', 'boolPtr'],
    ['dropVolatileFields', 'DropVolatileFields', 'stringSlice'],
  ],
};

// Top-level Expectation fields, in emission order. httpRequest is handled
// specially (fluent builder) — the rest map onto the Expectation struct.
const EXPECTATION_SPEC: FieldSpec[] = [
  ['id', 'ID', 'string'],
  ['priority', 'Priority', 'int'],
  ['percentage', 'Percentage', 'int'],
  ['chaos', 'Chaos', 'struct:HttpChaosProfile'],
  ['rateLimit', 'RateLimit', 'struct:RateLimit'],
  ['httpResponse', 'HttpResponse', 'struct:HttpResponse'],
  ['httpResponses', 'HttpResponses', 'structPtrSlice:HttpResponse'],
  ['httpResponseTemplate', 'HttpResponseTemplate', 'struct:HttpTemplate'],
  ['httpForward', 'HttpForward', 'struct:HttpForward'],
  ['httpForwardTemplate', 'HttpForwardTemplate', 'struct:HttpTemplate'],
  ['httpOverrideForwardedRequest', 'HttpOverrideForwardedRequest', 'struct:HttpOverrideForwardedRequest'],
  ['httpForwardWithFallback', 'HttpForwardWithFallback', 'struct:HttpForwardWithFallback'],
  ['httpForwardValidateAction', 'HttpForwardValidateAction', 'struct:HttpForwardValidateAction'],
  ['httpError', 'HttpError', 'struct:HttpError'],
  ['httpResponseClassCallback', 'HttpResponseClassCallback', 'struct:HttpClassCallback'],
  ['httpForwardClassCallback', 'HttpForwardClassCallback', 'struct:HttpClassCallback'],
  ['httpResponseObjectCallback', 'HttpResponseObjectCallback', 'struct:HttpObjectCallback'],
  ['httpForwardObjectCallback', 'HttpForwardObjectCallback', 'struct:HttpObjectCallback'],
  ['httpSseResponse', 'HttpSseResponse', 'struct:HttpSseResponse'],
  ['httpWebSocketResponse', 'HttpWebSocketResponse', 'struct:HttpWebSocketResponse'],
  ['grpcStreamResponse', 'GrpcStreamResponse', 'struct:GrpcStreamResponse'],
  ['grpcBidiResponse', 'GrpcBidiResponse', 'struct:GrpcBidiResponse'],
  ['binaryResponse', 'BinaryResponse', 'struct:BinaryResponse'],
  ['dnsResponse', 'DnsResponse', 'struct:DnsResponse'],
  ['httpLlmResponse', 'HttpLlmResponse', 'struct:HttpLlmResponse'],
  ['scenarioName', 'ScenarioName', 'string'],
  ['scenarioState', 'ScenarioState', 'string'],
  ['newScenarioState', 'NewScenarioState', 'string'],
  ['responseMode', 'ResponseMode', 'string'],
  ['responseWeights', 'ResponseWeights', 'intSlice'],
  ['switchAfter', 'SwitchAfter', 'intPtr'],
  ['crossProtocolScenarios', 'CrossProtocolScenarios', 'structSlice:CrossProtocolScenario'],
  ['beforeActions', 'BeforeActions', 'structSlice:AfterAction'],
  ['afterActions', 'AfterActions', 'structSlice:AfterAction'],
  ['steps', 'Steps', 'structSlice:ExpectationStep'],
  ['capture', 'Capture', 'structSlice:CaptureRule'],
  ['namespace', 'Namespace', 'string'],
  ['times', 'Times', 'struct:Times'],
  ['timeToLive', 'TimeToLive', 'struct:TimeToLive'],
  ['timestamp', 'Timestamp', 'string'],
];

// Request-matcher wire keys the fluent Request() builder can express directly.
const BUILDER_REQUEST_KEYS = new Set([
  'method', 'path', 'headers', 'queryStringParameters', 'cookies',
  'pathParameters', 'body', 'jwt', 'secure', 'keepAlive',
]);

// ---------------------------------------------------------------------------
// Emission context / low-level renderers
// ---------------------------------------------------------------------------

interface Ctx {
  /** True once a `ptr(...)` helper call has been emitted, so the generic pointer
   *  helper `func ptr[T any](v T) *T` is appended to the file. */
  usesPtr: boolean;
}

const tab = (n: number): string => '\t'.repeat(n);
const isObj = (v: unknown): v is Record<string, unknown> =>
  !!v && typeof v === 'object' && !Array.isArray(v);

/** A valid Go interpreted (double-quoted) string literal. JSON string escaping
 *  is a subset of Go's, so JSON.stringify produces a valid Go string. */
const goStr = (s: string): string => JSON.stringify(s);
const intLit = (v: unknown): string => String(Math.trunc(Number(v)));
const floatLit = (v: unknown): string => {
  const n = Number(v);
  return Number.isInteger(n) ? `${n}.0` : String(n);
};
/** Compact JSON string for the value carried in a typed body constructor
 *  (JSONMatchBody / JSONSchemaBody take a Go string). */
const jsonArg = (v: unknown): string => (typeof v === 'string' ? v : JSON.stringify(v));

function ptrExpr(ctx: Ctx, inner: string): string {
  ctx.usesPtr = true;
  return `ptr(${inner})`;
}

function renderHeaders(obj: Record<string, unknown>, d: number): string {
  const entries = Object.entries(obj);
  if (entries.length === 0) return 'map[string][]string{}';
  const lines = entries.map(([k, v]) => {
    const vals = Array.isArray(v) ? v : [v];
    return `${tab(d + 1)}${goStr(k)}: {${vals.map((x) => goStr(String(x))).join(', ')}},`;
  });
  return `map[string][]string{\n${lines.join('\n')}\n${tab(d)}}`;
}

function renderCookies(obj: Record<string, unknown>, d: number): string {
  const entries = Object.entries(obj);
  if (entries.length === 0) return 'map[string]string{}';
  const lines = entries.map(([k, v]) => `${tab(d + 1)}${goStr(k)}: ${goStr(String(v))},`);
  return `map[string]string{\n${lines.join('\n')}\n${tab(d)}}`;
}

function renderStringSlice(arr: unknown[]): string {
  return `[]string{${arr.map((x) => goStr(String(x))).join(', ')}}`;
}
function renderIntSlice(arr: unknown[]): string {
  return `[]int{${arr.map(intLit).join(', ')}}`;
}

/** Arbitrary JSON → Go literal, for genuinely interface-typed fields. */
function renderRaw(v: unknown, d: number): string {
  if (v === null || v === undefined) return 'nil';
  if (typeof v === 'string') return goStr(v);
  if (typeof v === 'boolean') return v ? 'true' : 'false';
  if (typeof v === 'number') return Number.isInteger(v) ? String(v) : String(v);
  if (Array.isArray(v)) {
    if (v.length === 0) return '[]interface{}{}';
    const lines = v.map((x) => `${tab(d + 1)}${renderRaw(x, d + 1)},`);
    return `[]interface{}{\n${lines.join('\n')}\n${tab(d)}}`;
  }
  const entries = Object.entries(v as Record<string, unknown>);
  if (entries.length === 0) return 'map[string]interface{}{}';
  const lines = entries.map(([k, val]) => `${tab(d + 1)}${goStr(k)}: ${renderRaw(val, d + 1)},`);
  return `map[string]interface{}{\n${lines.join('\n')}\n${tab(d)}}`;
}

// ---------------------------------------------------------------------------
// Body — typed body constructors (StringBody / JSONMatchBody / XPathBody / …).
// ---------------------------------------------------------------------------

/** A typed request-body matcher wire object → its Go constructor expression. */
function renderBodyMatcher(b: Record<string, unknown>, ctx: Ctx, d: number): string {
  switch (b['type']) {
    case 'STRING':
      return b['subString'] === true
        ? `mockserver.SubStringBody(${goStr(String(b['string'] ?? ''))})`
        : `mockserver.StringBody(${goStr(String(b['string'] ?? ''))})`;
    case 'JSON':
      return `mockserver.JSONMatchBody(${goStr(jsonArg(b['json']))}, ${goStr(String(b['matchType'] ?? ''))})`;
    case 'JSON_SCHEMA':
      return `mockserver.JSONSchemaBody(${goStr(jsonArg(b['jsonSchema']))})`;
    case 'JSON_PATH':
      return `mockserver.JSONPathBody(${goStr(String(b['jsonPath'] ?? ''))})`;
    case 'XML':
      return `mockserver.XMLBody(${goStr(String(b['xml'] ?? ''))})`;
    case 'XML_SCHEMA':
      return `mockserver.XMLSchemaBody(${goStr(String(b['xmlSchema'] ?? ''))})`;
    case 'XPATH':
      return `mockserver.XPathBody(${goStr(String(b['xpath'] ?? ''))})`;
    case 'REGEX':
      return `mockserver.RegexBody(${goStr(String(b['regex'] ?? ''))})`;
    case 'PARAMETERS':
      return `mockserver.ParameterBody(${renderHeaders((b['parameters'] as Record<string, unknown>) ?? {}, d)})`;
    case 'BINARY':
      return `mockserver.BinaryBody(${goStr(String(b['base64Bytes'] ?? ''))})`;
    case 'WASM':
      return `mockserver.WasmBody(${goStr(String(b['moduleName'] ?? ''))})`;
    case 'GRAPHQL': {
      // The typed GraphQLBody constructor covers query + operationName. The
      // advanced selectionSetMatchType / schema round-trip via a TypedBody
      // struct literal; a `fields` list has no typed Go field (it shares the JSON
      // key "fields" with the MULTIPART matcher) so it falls to a struct with a
      // note. Query-only is by far the common case.
      const advanced = b['selectionSetMatchType'] != null || b['schema'] != null || b['fields'] != null;
      if (!advanced) {
        return `mockserver.GraphQLBody(${goStr(String(b['query'] ?? ''))}, ${goStr(String(b['operationName'] ?? ''))})`;
      }
      const parts: string[] = [`${tab(d + 1)}Type: "GRAPHQL",`, `${tab(d + 1)}Query: ${goStr(String(b['query'] ?? ''))},`];
      if (b['operationName'] != null) parts.push(`${tab(d + 1)}OperationName: ${goStr(String(b['operationName']))},`);
      if (b['selectionSetMatchType'] != null) parts.push(`${tab(d + 1)}SelectionSetMatchType: ${goStr(String(b['selectionSetMatchType']))},`);
      if (b['schema'] != null) parts.push(`${tab(d + 1)}Schema: ${goStr(String(b['schema']))},`);
      if (b['fields'] != null) parts.push(`${tab(d + 1)}// NOTE: GraphQL "fields" list omitted — no typed Go field (shares JSON key with MULTIPART)`);
      return `&mockserver.TypedBody{\n${parts.join('\n')}\n${tab(d)}}`;
    }
    case 'ALL_OF': {
      const subs = (b['bodyAllOf'] as unknown[]) ?? [];
      const rendered = subs.map((s) => {
        if (typeof s === 'string') return `${tab(d + 1)}${goStr(s)},`;
        return `${tab(d + 1)}${renderBodyMatcher(s as Record<string, unknown>, ctx, d + 1)},`;
      });
      if (rendered.length === 0) return 'mockserver.AllOf()';
      return `mockserver.AllOf(\n${rendered.join('\n')}\n${tab(d)})`;
    }
    default:
      return renderRaw(b, d);
  }
}

/** A `body` field (interface{}) — plain string, FILE body, typed matcher body or raw. */
function renderBody(v: unknown, ctx: Ctx, d: number): string {
  if (typeof v === 'string') return goStr(v);
  if (isObj(v)) {
    if (v['type'] === 'FILE') return `mockserver.FileBody${renderStructBody('FileBody', v, ctx, d, FILE_BODY_SPEC)}`;
    if (typeof v['type'] === 'string') return renderBodyMatcher(v, ctx, d);
  }
  return renderRaw(v, d);
}

const FILE_BODY_SPEC: FieldSpec[] = [
  ['type', 'Type', 'string'],
  ['filePath', 'FilePath', 'string'],
  ['templateType', 'TemplateType', 'string'],
  ['templatedFilePath', 'TemplatedFilePath', 'bool'],
  ['contentType', 'ContentType', 'string'],
];

// ---------------------------------------------------------------------------
// Struct rendering
// ---------------------------------------------------------------------------

/** Render a value for a given kind, positioned on a line at indent depth `d`. */
function renderValue(kind: string, v: unknown, ctx: Ctx, d: number): string {
  if (kind.startsWith('struct:')) return renderStruct(kind.slice(7), v as Record<string, unknown>, ctx, d);
  if (kind.startsWith('structSlice:')) return renderStructSlice(kind.slice(12), v as unknown[], ctx, d, false);
  if (kind.startsWith('structPtrSlice:')) return renderStructSlice(kind.slice(15), v as unknown[], ctx, d, true);
  switch (kind) {
    case 'string': return goStr(String(v));
    case 'int': return intLit(v);
    case 'bool': return v ? 'true' : 'false';
    case 'boolPtr': return ptrExpr(ctx, v ? 'true' : 'false');
    case 'intPtr': return ptrExpr(ctx, intLit(v));
    case 'int64Ptr': return ptrExpr(ctx, `int64(${intLit(v)})`);
    case 'floatPtr': return ptrExpr(ctx, floatLit(v));
    case 'headers': return renderHeaders((v as Record<string, unknown>) ?? {}, d);
    case 'cookies': return renderCookies((v as Record<string, unknown>) ?? {}, d);
    case 'stringSlice': return renderStringSlice((v as unknown[]) ?? []);
    case 'intSlice': return renderIntSlice((v as unknown[]) ?? []);
    case 'body': return renderBody(v, ctx, d);
    case 'raw': return renderRaw(v, d);
    case 'httpRequestPtr': return renderRequestPtr(v as Record<string, unknown>, ctx, d);
    default: return renderRaw(v, d);
  }
}

/** The `{...}` body of a struct literal (fields at d+1, closing brace at d). */
function renderStructBody(
  typeName: string,
  obj: Record<string, unknown>,
  ctx: Ctx,
  d: number,
  specOverride?: FieldSpec[],
): string {
  const spec = specOverride ?? STRUCTS[typeName];
  if (!spec) return renderRaw(obj, d);
  const lines: string[] = [];
  for (const [wireKey, goField, kind] of spec) {
    if (!(wireKey in obj)) continue;
    const val = obj[wireKey];
    if (val === null || val === undefined) continue;
    lines.push(`${tab(d + 1)}${goField}: ${renderValue(kind, val, ctx, d + 1)},`);
  }
  for (const key of Object.keys(obj)) {
    if (!spec.some(([wireKey]) => wireKey === key)) lines.push(`${tab(d + 1)}// NOTE: wire key "${key}" has no typed Go field — omitted`);
  }
  if (lines.length === 0) return '{}';
  return `{\n${lines.join('\n')}\n${tab(d)}}`;
}

function renderStruct(typeName: string, obj: Record<string, unknown>, ctx: Ctx, d: number): string {
  return `&mockserver.${typeName}${renderStructBody(typeName, obj, ctx, d)}`;
}

function renderStructSlice(typeName: string, arr: unknown[], ctx: Ctx, d: number, isPtr: boolean): string {
  const prefix = isPtr ? `[]*mockserver.${typeName}` : `[]mockserver.${typeName}`;
  if (arr.length === 0) return `${prefix}{}`;
  const els = arr.map((el) => `${tab(d + 1)}${renderStructBody(typeName, (el as Record<string, unknown>) ?? {}, ctx, d + 1)},`);
  return `${prefix}{\n${els.join('\n')}\n${tab(d)}}`;
}

// ---------------------------------------------------------------------------
// Request matcher — fluent Request() builder + typed body constructors.
// ---------------------------------------------------------------------------

function pathParametersSimple(pp: unknown): boolean {
  if (!isObj(pp)) return false;
  return Object.values(pp).every((v) => Array.isArray(v) && v.every((x) => typeof x === 'string'));
}

function renderJwtBuilder(jwt: Record<string, unknown>): string {
  let expr = 'mockserver.NewJwt()';
  if (typeof jwt['header'] === 'string') expr += `.WithHeader(${goStr(jwt['header'])})`;
  if (typeof jwt['scheme'] === 'string') expr += `.WithScheme(${goStr(jwt['scheme'])})`;
  const claims = jwt['claims'];
  if (isObj(claims)) {
    for (const [k, v] of Object.entries(claims)) expr += `.Claim(${goStr(k)}, ${goStr(String(v))})`;
  }
  if (typeof jwt['issuer'] === 'string') expr += `.WithIssuer(${goStr(jwt['issuer'])})`;
  if (typeof jwt['audience'] === 'string') expr += `.WithAudience(${goStr(jwt['audience'])})`;
  if (typeof jwt['algorithm'] === 'string') expr += `.WithAlgorithm(${goStr(jwt['algorithm'])})`;
  return expr;
}

/** The fluent-builder method calls (without leading dot) for a request. Body is
 *  included only when it is a plain string (a typed body must be post-assigned). */
function requestBuilderCalls(req: Record<string, unknown>, includePlainBody: boolean): string[] {
  const calls: string[] = [];
  if (typeof req['method'] === 'string' && req['method']) calls.push(`Method(${goStr(req['method'])})`);
  if (typeof req['path'] === 'string' && req['path']) calls.push(`Path(${goStr(req['path'])})`);
  if (isObj(req['headers'])) {
    for (const [k, v] of Object.entries(req['headers'])) {
      const vals = Array.isArray(v) ? v : [v];
      calls.push(`Header(${[goStr(k), ...vals.map((x) => goStr(String(x)))].join(', ')})`);
    }
  }
  if (isObj(req['queryStringParameters'])) {
    for (const [k, v] of Object.entries(req['queryStringParameters'])) {
      const vals = Array.isArray(v) ? v : [v];
      calls.push(`QueryStringParameter(${[goStr(k), ...vals.map((x) => goStr(String(x)))].join(', ')})`);
    }
  }
  if (isObj(req['cookies'])) {
    for (const [k, v] of Object.entries(req['cookies'])) calls.push(`Cookie(${goStr(k)}, ${goStr(String(v))})`);
  }
  if (isObj(req['pathParameters']) && pathParametersSimple(req['pathParameters'])) {
    for (const [k, v] of Object.entries(req['pathParameters'])) {
      const vals = v as string[];
      calls.push(`PathParameter(${[goStr(k), ...vals.map((x) => goStr(String(x)))].join(', ')})`);
    }
  }
  if (isObj(req['jwt'])) calls.push(`Jwt(${renderJwtBuilder(req['jwt'])})`);
  // typeof, not === true: secure:false is an HTTP-only matcher, not an absent field
  if (typeof req['secure'] === 'boolean') calls.push(`Secure(${req['secure']})`);
  if (typeof req['keepAlive'] === 'boolean') calls.push(`KeepAlive(${req['keepAlive']})`);
  if (includePlainBody && typeof req['body'] === 'string') calls.push(`Body(${goStr(req['body'])})`);
  return calls;
}

/** True when the fluent builder can faithfully express the whole request. */
function requestBuilderable(req: Record<string, unknown>): boolean {
  if (typeof req['dnsName'] === 'string' && req['dnsName']) return false;
  for (const key of Object.keys(req)) {
    if (!BUILDER_REQUEST_KEYS.has(key)) return false;
  }
  if ('pathParameters' in req && !pathParametersSimple(req['pathParameters'])) return false;
  return true;
}

/** Format a `mockserver.Request()....BuildPtr()` chain across lines at depth d. */
function formatBuilderChain(calls: string[], d: number): string {
  if (calls.length === 0) return 'mockserver.Request().BuildPtr()';
  const lines = [...calls, 'BuildPtr()'].map((c) => `${tab(d + 1)}${c}`);
  return `mockserver.Request().\n${lines.join('.\n')}`;
}

/** Render a nested *HttpRequest value (request-override / step / before-after
 *  action target). Uses the fluent builder when it can express the request,
 *  otherwise a typed struct literal (covers DNS and edit-overlay passthrough). */
function renderRequestPtr(req: Record<string, unknown>, ctx: Ctx, d: number): string {
  if (requestBuilderable(req) && typeof req['body'] !== 'object') {
    return formatBuilderChain(requestBuilderCalls(req, true), d);
  }
  return renderStruct('HttpRequest', req, ctx, d);
}

// ---------------------------------------------------------------------------
// Expectation assembly
// ---------------------------------------------------------------------------

interface RenderedExpectation {
  preamble: string[];
  literal: string;
}

function renderExpectation(json: Record<string, unknown>, ctx: Ctx): RenderedExpectation {
  const preamble: string[] = [];
  const fields: string[] = [];
  const req = (json['httpRequest'] as Record<string, unknown>) ?? {};

  // Request matcher — fluent builder when possible. A typed (object) body is
  // post-assigned to a hoisted `req` local so the builder chain stays clean and
  // the typed body constructor (JSONMatchBody / XPathBody / AllOf / …) is used.
  if (requestBuilderable(req)) {
    const bodyIsObject = isObj(req['body']);
    if (bodyIsObject) {
      const calls = requestBuilderCalls(req, false);
      preamble.push(`\treq := ${formatBuilderChain(calls, 1)}`);
      preamble.push(`\treq.Body = ${renderBody(req['body'], ctx, 1)}`);
      fields.push(`${tab(2)}HttpRequest: req,`);
    } else {
      fields.push(`${tab(2)}HttpRequest: ${formatBuilderChain(requestBuilderCalls(req, true), 2)},`);
    }
  } else {
    // DNS request or edit-overlay passthrough the builder cannot express.
    fields.push(`${tab(2)}HttpRequest: ${renderStruct('HttpRequest', req, ctx, 2)},`);
  }

  const known = new Set<string>(['httpRequest']);
  for (const [wireKey, goField, kind] of EXPECTATION_SPEC) {
    known.add(wireKey);
    if (!(wireKey in json)) continue;
    const val = json[wireKey];
    if (val === null || val === undefined) continue;
    fields.push(`${tab(2)}${goField}: ${renderValue(kind, val, ctx, 2)},`);
  }
  for (const key of Object.keys(json)) {
    if (!known.has(key)) fields.push(`${tab(2)}// NOTE: wire key "${key}" has no typed Go field — omitted`);
  }

  const literal = `mockserver.Expectation{\n${fields.join('\n')}\n${tab(1)}}`;
  return { preamble, literal };
}

export function standardToGo(matcher: StandardMatcher, action: StandardActionPayload, baseUrl: string): string {
  const { host, port } = clientHostPort(baseUrl);
  const json = buildExpectationJson(matcher, action);
  const ctx: Ctx = { usesPtr: false };
  const { preamble, literal } = renderExpectation(json, ctx);

  const body: string[] = [
    'package main',
    '',
    'import (',
    '\tmockserver "github.com/mock-server/mockserver-monorepo/mockserver-client-go/v9"',
    ')',
    '',
    'func main() {',
    `\tclient := mockserver.New(${goStr(host)}, ${port})`,
    '',
  ];
  if (preamble.length > 0) {
    body.push(...preamble, '');
  }
  body.push(
    `\texpectation := ${literal}`,
    '',
    '\tif _, err := client.Upsert(expectation); err != nil {',
    '\t\tpanic(err)',
    '\t}',
    '}',
  );
  if (ctx.usesPtr) {
    body.push('', 'func ptr[T any](v T) *T { return &v }');
  }
  return body.join('\n');
}
