/*
 * mockserver
 * http://mock-server.com
 *
 * Copyright (c) 2014 James Bloom
 * Licensed under the Apache License, Version 2.0
 *
 * Compile-level test: asserts the `Expectation` type (and the request-matcher
 * union) covers every MockServer expectation feature by assigning kitchen-sink
 * literals exercising every member. This file is type-checked by `tsc`
 * (see tsconfig.json `include`) and produces no runtime output.
 */

import {
    BinaryRequestDefinition,
    Body,
    BodyWithContentType,
    CaptureRule,
    ConditionalRequestDefinition,
    DnsRequestDefinition,
    Expectation,
    GrpcStreamResponse,
    HttpChaosProfile,
    HttpForwardValidateAction,
    HttpForwardWithFallback,
    HttpLlmResponse,
    HttpRequest,
    HttpResponse,
    HttpWebSocketResponse,
    LlmChaosProfile,
    LlmCompletion,
    LlmConversationPredicates,
    LlmNormalizationOptions,
    RateLimit,
    RequestDefinition
} from '../mockServer';

// --- request-matcher union: DNS / binary / conditional variants ------------

const dnsRequest: DnsRequestDefinition = {
    not: false,
    dnsName: 'example.com',
    dnsType: 'AAAA',
    dnsClass: 'IN'
};

const binaryRequest: BinaryRequestDefinition = {
    binaryData: 'AQIDBA==',
    socketAddress: {host: 'upstream', port: 443, scheme: 'HTTPS'}
};

const conditionalRequest: ConditionalRequestDefinition = {
    if: {method: 'GET', path: '/a'},
    then: {method: 'GET', path: '/b'},
    else: dnsRequest
};

const requestDefinitions: RequestDefinition[] = [
    {method: 'GET', path: '/http'},
    {specUrlOrPayload: 'https://example.com/openapi.json', operationId: 'listPets'},
    dnsRequest,
    binaryRequest,
    conditionalRequest
];

// --- forward-with-validation and forward-with-fallback actions -------------

const forwardValidate: HttpForwardValidateAction = {
    delay: {timeUnit: 'MILLISECONDS', value: 10},
    primary: true,
    specUrlOrPayload: 'https://example.com/openapi.json',
    host: 'upstream',
    port: 8443,
    scheme: 'HTTPS',
    validateRequest: true,
    validateResponse: false,
    validationMode: 'LOG_ONLY'
};

const forwardWithFallback: HttpForwardWithFallback = {
    delay: {timeUnit: 'SECONDS', value: 1},
    primary: false,
    httpForward: {host: 'upstream', port: 80, scheme: 'HTTP'},
    fallbackResponse: {statusCode: 503, body: 'upstream down'},
    fallbackOnStatusCodes: [500, 502, 503, 504],
    fallbackOnTimeout: true
};

// --- expectation-level capture and rate limiting ---------------------------

const captureRules: CaptureRule[] = [
    {source: 'jsonPath', expression: '$.id', into: 'orderId'},
    {source: 'header', expression: 'X-Trace', into: 'trace'},
    {source: 'pathParameter', expression: 'petId', into: 'petId'}
];

const fixedWindowLimit: RateLimit = {
    name: 'per-tenant',
    algorithm: 'fixed_window',
    limit: 100,
    windowMillis: 60000,
    errorStatus: 429,
    retryAfter: '60'
};

const tokenBucketLimit: RateLimit = {
    algorithm: 'token_bucket',
    burst: 20,
    refillPerSecond: 5.5
};

// --- LLM response action, with completion / predicates / normalization -----

const normalization: LlmNormalizationOptions = {
    collapseWhitespace: true,
    lowercase: true,
    sortJsonKeys: true,
    dropBuiltInVolatileFields: true,
    dropVolatileFields: ['requestId', 'created']
};

const conversationPredicates: LlmConversationPredicates = {
    turnIndex: 2,
    latestMessageContains: 'weather',
    latestMessageMatches: '.*forecast.*',
    latestMessageRole: 'USER',
    containsToolResultFor: 'get_weather',
    semanticMatchAgainst: 'the user is asking about the weather',
    normalization
};

const llmChaos: LlmChaosProfile = {
    errorStatus: 529,
    retryAfter: '30',
    errorProbability: 0.1,
    truncateMode: 'MID_STREAM',
    truncateAtFraction: 0.5,
    malformedSse: true,
    seed: 42,
    quotaName: 'rpm',
    quotaLimit: 60,
    quotaWindowMillis: 60000,
    quotaErrorStatus: 429,
    tokenQuotaLimit: 100000,
    tokenQuotaWindowMillis: 86400000,
    errorKind: 'RATE_LIMIT',
    contentFilterBlockProbability: 0.05
};

const completion: LlmCompletion = {
    text: 'It is sunny.',
    toolCalls: [{id: 'call_1', name: 'get_weather', arguments: '{"city":"London"}'}],
    stopReason: 'end_turn',
    usage: {
        inputTokens: 12,
        outputTokens: 8,
        cachedInputTokens: 4,
        cacheCreationTokens: 2,
        reasoningTokens: 3
    },
    streaming: true,
    streamingPhysics: {
        timeToFirstToken: {timeUnit: 'MILLISECONDS', value: 120},
        tokensPerSecond: 40,
        jitter: 0.2,
        seed: 7,
        subwordStreaming: true
    },
    outputSchema: '{"type":"object"}',
    enforceOutputSchema: true,
    model: 'claude-sonnet',
    toolChoice: 'auto',
    reasoningText: 'thinking...',
    reasoningSignature: 'sig'
};

const llmResponse: HttpLlmResponse = {
    delay: {timeUnit: 'MILLISECONDS', value: 5},
    primary: true,
    provider: 'ANTHROPIC',
    model: 'claude-sonnet',
    completion,
    embedding: {dimensions: 1536, deterministicFromInput: true, seed: 1},
    rerank: {topN: 3, deterministicFromInput: false, seed: 2},
    moderation: {flaggedCategories: ['hate'], model: 'text-moderation'},
    contentFilter: {hate: 'low', sexual: 'safe', violence: 'medium', selfHarm: 'safe'},
    conversationPredicates,
    chaos: llmChaos
};

// --- protocol matcher, response trailers, chaos GraphQL faults -------------

const httpRequestWithProtocol: HttpRequest = {
    method: 'POST',
    path: '/graphql',
    protocol: 'HTTP_2'
};

const httpResponseWithTrailers: HttpResponse = {
    statusCode: 200,
    body: 'ok',
    headers: {'Content-Type': ['application/grpc']},
    trailers: {'grpc-status': ['0'], 'grpc-message': ['OK']}
};

const graphqlChaos: HttpChaosProfile = {
    errorStatus: 500,
    graphqlErrors: true,
    graphqlErrorMessage: 'simulated GraphQL error',
    graphqlErrorCode: 'INTERNAL_SERVER_ERROR',
    graphqlNullifyData: false
};

// --- gRPC stream templating and WebSocket per-frame matchers ---------------

const grpcStreamResponse: GrpcStreamResponse = {
    statusName: 'OK',
    messages: [
        {json: '{"id":1}'},
        {json: '{"seq":$!counter}', templateType: 'VELOCITY', delay: {timeUnit: 'MILLISECONDS', value: 10}}
    ]
};

const webSocketResponse: HttpWebSocketResponse = {
    subprotocol: 'chat',
    messages: [{text: 'welcome'}],
    matchers: [
        {frameType: 'TEXT', textMatcher: 'ping', responses: [{text: 'pong'}]},
        {frameType: 'ANY', responses: [{binary: 'AQID', delay: {timeUnit: 'MILLISECONDS', value: 5}}]}
    ]
};

// --- FILE response body with a templated path -------------------------------

// BodyWithContentType includes Record<string, any>, which disables excess-property
// checks; keep only explicitly declared members so a dropped field fails tsc.
type ExplicitMember<U> = U extends object ? (string extends keyof U ? never : U) : never;
type FileResponseBody = Extract<ExplicitMember<BodyWithContentType>, {type?: 'FILE'}>;

const templatedFileBody: FileResponseBody = {
    type: 'FILE',
    filePath: 'responses/{{ request.pathParameters.name.0 }}.json',
    contentType: 'application/json',
    templateType: 'MUSTACHE',
    templatedFilePath: true
};

const fileBodyResponse: HttpResponse = {statusCode: 200, body: templatedFileBody};

// the request-matcher schema has no templatedFilePath, so the request FILE member must reject it
type FileRequestBody = Extract<ExplicitMember<Body>, {type?: 'FILE'}>;

const fileRequestBody: FileRequestBody = {
    type: 'FILE',
    filePath: 'requests/expected.json',
    // @ts-expect-error templatedFilePath applies only to response bodies
    templatedFilePath: true
};

// --- kitchen-sink Expectation: every new member present --------------------

const kitchenSink: Expectation = {
    id: 'exp-1',
    priority: 10,
    percentage: 50,
    httpRequest: httpRequestWithProtocol,
    httpResponse: httpResponseWithTrailers,
    httpForwardValidateAction: forwardValidate,
    httpForwardWithFallback: forwardWithFallback,
    httpLlmResponse: llmResponse,
    httpWebSocketResponse: webSocketResponse,
    grpcStreamResponse,
    chaos: graphqlChaos,
    rateLimit: fixedWindowLimit,
    namespace: 'tenant-a',
    capture: captureRules,
    times: {unlimited: true},
    timeToLive: {unlimited: true},
    timestamp: '2026-07-03T12:00:00.000Z'
};

// Reference every binding so `noUnusedLocals`-style checks (if enabled) and the
// linter see them as used; also re-assert assignability at the union level.
export const expectationTypeCoverage: {
    requestDefinitions: RequestDefinition[];
    tokenBucketLimit: RateLimit;
    binaryRequest: BinaryRequestDefinition;
    conditionalRequest: ConditionalRequestDefinition;
    expectation: Expectation;
    fileBodyResponse: HttpResponse;
    fileRequestBody: FileRequestBody;
} = {
    requestDefinitions,
    tokenBucketLimit,
    binaryRequest,
    conditionalRequest,
    expectation: kitchenSink,
    fileBodyResponse,
    fileRequestBody
};
