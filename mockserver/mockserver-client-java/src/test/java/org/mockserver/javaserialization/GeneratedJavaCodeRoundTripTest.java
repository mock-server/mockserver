package org.mockserver.javaserialization;

import org.junit.Test;
import org.mockserver.client.MockServerClient;
import org.mockserver.llm.ParsedMessage;
import org.mockserver.mock.Expectation;
import org.mockserver.model.*;
import org.mockserver.serialization.java.ExpectationToJavaSerializer;

import javax.tools.*;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.mockserver.model.HttpClassCallback.callback;
import static org.mockserver.model.HttpForward.forward;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

/**
 * The Java generated for an expectation ({@code format=JAVA}) compiles and, run against a client, submits an
 * expectation whose JSON is the original's, for each kind of action, for before and after actions and for the
 * expectation-level fields such as id, chaos, rate limit, steps and capture rules.
 */
public class GeneratedJavaCodeRoundTripTest {

    private static final String CLIENT_CONSTRUCTION = "new MockServerClient(\"localhost\", 1080)";
    private static final CapturingClient CLIENT = new CapturingClient();

    @Test
    public void shouldRecreateSseResponse() throws Exception {
        assertRecreated(new Expectation(request().withPath("/sse"))
            .thenRespondWithSse(HttpSseResponse.sseResponse()
                .withStatusCode(201)
                .withHeader("X-One", "a", "b")
                .withHeader("X-Two", "c")
                .withEvents(
                    SseEvent.sseEvent().withEvent("update").withData("{\"n\": \"1\"}\nline two").withId("1").withRetry(500).withDelay(TimeUnit.MILLISECONDS, 20),
                    SseEvent.sseEvent().withData("é ünïcode")
                )
                .withCloseConnection(true)
                .withTemplateType(HttpTemplate.TemplateType.VELOCITY)
                .withDelay(TimeUnit.SECONDS, 2)));
    }

    @Test
    public void shouldRecreateLlmCompletionResponse() throws Exception {
        assertRecreated(new Expectation(request().withPath("/v1/chat/completions"))
            .thenRespondWithLlm(HttpLlmResponse.llmResponse()
                .withProvider(Provider.OPENAI)
                .withModel("gpt-test")
                .withCompletion(Completion.completion()
                    .withText("hello \"world\"")
                    .withToolCalls(
                        ToolUse.toolUse("get_weather").withId("call_1").withArguments("{\"city\":\"London\"}"),
                        ToolUse.toolUse("noop")
                    )
                    .withStopReason("tool_calls")
                    .withUsage(Usage.usage().withInputTokens(10).withOutputTokens(20).withCachedInputTokens(3).withCacheCreationTokens(4).withReasoningTokens(5))
                    .withStreaming(true)
                    .withStreamingPhysics(StreamingPhysics.streamingPhysics()
                        .withTimeToFirstToken(new Delay(TimeUnit.MILLISECONDS, 150))
                        .withTokensPerSecond(40)
                        .withJitter(0.25)
                        .withSeed(1234567890123L)
                        .withSubwordStreaming(false))
                    .withOutputSchema("{\"type\":\"object\"}")
                    .withEnforceOutputSchema(true)
                    .withModel("gpt-completion")
                    .withToolChoice("auto")
                    .withReasoningText("thinking")
                    .withReasoningSignature("sig"))
                .withContentFilter(LlmContentFilter.llmContentFilter().withHate("low").withSexual("safe").withViolence("medium").withSelfHarm("high"))
                .withConversationPredicates(ConversationPredicates.conversationPredicates()
                    .withTurnIndex(2)
                    .withLatestMessageContains("weather")
                    .withLatestMessageMatches("^what.*\\?$")
                    .withLatestMessageRole(ParsedMessage.Role.USER)
                    .withContainsToolResultFor("get_weather")
                    .withSemanticMatchAgainst("asks about weather")
                    .withNormalization(NormalizationOptions.normalizationOptions()
                        .withCollapseWhitespace(true)
                        .withLowercase(false)
                        .withSortJsonKeys(true)
                        .withDropBuiltInVolatileFields(true)
                        .withDropVolatileFields(Arrays.asList("id", "created"))))
                .withChaos(LlmChaosProfile.llmChaosProfile()
                    .withErrorStatus(429)
                    .withRetryAfter("5")
                    .withErrorProbability(0.1)
                    .withTruncateMode(LlmChaosProfile.TruncateMode.values()[0])
                    .withTruncateAtFraction(0.5)
                    .withMalformedSse(true)
                    .withSeed(42L)
                    .withQuotaName("quota")
                    .withQuotaLimit(100)
                    .withQuotaWindowMillis(60000L)
                    .withQuotaErrorStatus(503)
                    .withTokenQuotaLimit(5000L)
                    .withTokenQuotaWindowMillis(120000L)
                    .withErrorKind("RATE_LIMIT")
                    .withContentFilterBlockProbability(0.05))
                .withDelay(TimeUnit.MILLISECONDS, 10)));
    }

    @Test
    public void shouldRecreateLlmEmbeddingRerankAndModerationResponse() throws Exception {
        assertRecreated(new Expectation(request().withPath("/v1/embeddings"))
            .thenRespondWithLlm(HttpLlmResponse.llmResponse()
                .withProvider(Provider.COHERE)
                .withEmbedding(EmbeddingResponse.embedding().withDimensions(8).withDeterministicFromInput(true).withSeed(7L))
                .withRerank(RerankResponse.rerank().withTopN(3).withDeterministicFromInput(false).withSeed(9L))
                .withModeration(ModerationResponse.moderationResponse().withFlaggedCategories(Arrays.asList("hate", "violence")).withModel("omni"))));
    }

    @Test
    public void shouldRecreateClientIpMatcher() throws Exception {
        assertRecreated(new Expectation(request().withPath("/internal").withClientIp("10.0.0.0/8")).thenRespond(response().withStatusCode(200)));
        assertRecreated(new Expectation(request().withPath("/external").withClientIp("!2001:db8::/32")).thenRespond(response().withStatusCode(403)));
    }

    @Test
    public void shouldRecreateWebSocketResponse() throws Exception {
        assertRecreated(new Expectation(request().withPath("/ws"))
            .thenRespondWithWebSocket(HttpWebSocketResponse.webSocketResponse()
                .withSubprotocol("graphql-ws")
                .withMessages(
                    WebSocketMessage.webSocketMessage("hello").withDelay(TimeUnit.MILLISECONDS, 5),
                    WebSocketMessage.webSocketMessage().withBinary(new byte[]{0, 1, 2, (byte) 255})
                )
                .withMatchers(
                    WebSocketMessageMatcher.webSocketMessageMatcher().withTextMatcher(NottableString.string("!literal", false)).withResponse(WebSocketMessage.webSocketMessage("one")),
                    WebSocketMessageMatcher.webSocketMessageMatcher().withTextMatcher(NottableString.not("other")).withFrameType(WebSocketFrameType.ANY),
                    WebSocketMessageMatcher.webSocketMessageMatcher().withFrameType(WebSocketFrameType.BINARY)
                )
                .withCloseConnection(false)
                .withGraphqlSubscriptionFilter(new GraphQLBody("subscription { a }", "Op", "{\"type\":\"object\"}")
                    .withSelectionSetMatchType(SelectionSetMatchType.values()[0])
                    .withFields("a", "b")
                    .withSchema("type Query { a: String }"))
                .withTemplateType(HttpTemplate.TemplateType.MUSTACHE)));
    }

    @Test
    public void shouldRecreateGrpcStreamResponse() throws Exception {
        assertRecreated(new Expectation(request().withPath("/svc.Greeter/Stream"))
            .thenRespondWithGrpcStream(GrpcStreamResponse.grpcStreamResponse()
                .withStatusName("OK")
                .withStatusMessage("done")
                .withHeader("x-meta", "1")
                .withMessages(
                    GrpcStreamMessage.grpcStreamMessage("{\"m\":1}").withDelay(TimeUnit.MILLISECONDS, 30),
                    GrpcStreamMessage.grpcStreamMessage("{\"m\":\"$!request.path\"}").withTemplateType(HttpTemplate.TemplateType.VELOCITY)
                )
                .withCloseConnection(true)));
    }

    @Test
    public void shouldRecreateGrpcBidiResponse() throws Exception {
        assertRecreated(new Expectation(request().withPath("/svc.Chat/Talk"))
            .thenRespondWithGrpcBidi(GrpcBidiResponse.grpcBidiResponse()
                .withStatusName("UNAVAILABLE")
                .withStatusMessage("gone")
                .withHeader("x-meta", "2")
                .withMessage("{\"hello\":true}")
                .withRules(
                    GrpcBidiRule.grpcBidiRule("{\"q\":\"a\"}").withResponse("{\"r\":\"A\"}", new Delay(TimeUnit.MILLISECONDS, 3)),
                    GrpcBidiRule.grpcBidiRule().withMatchJson(NottableString.not("{\"q\":\"b\"}")).withResponses(GrpcStreamMessage.grpcStreamMessage("{\"r\":\"B\"}"))
                )
                .withCloseConnection(false)));
    }

    @Test
    public void shouldRecreateBinaryResponse() throws Exception {
        assertRecreated(new Expectation(request().withPath("/binary"))
            .thenRespondWithBinary(BinaryResponse.binaryResponse("binary \u0000 payload".getBytes(StandardCharsets.UTF_8)).withDelay(TimeUnit.MILLISECONDS, 1)));
    }

    @Test
    public void shouldRecreateDnsResponse() throws Exception {
        assertRecreated(new Expectation(request().withPath("/dns"))
            .thenRespondWithDns(DnsResponse.dnsResponse()
                .withAnswerRecords(DnsRecord.aRecord("example.com", "1.2.3.4").withTtl(60), DnsRecord.srvRecord("_sip._tcp.example.com", 10, 5, 5060, "sip.example.com"))
                .withAuthorityRecords(DnsRecord.dnsRecord().withName("example.com").withType(DnsRecordType.values()[0]).withDnsClass(DnsRecordClass.values()[0]).withValue("ns1.example.com"))
                .withAdditionalRecords(DnsRecord.mxRecord("example.com", 10, "mail.example.com"))
                .withResponseCode(DnsResponseCode.values()[DnsResponseCode.values().length - 1])));
    }

    @Test
    public void shouldRecreateForwardValidate() throws Exception {
        assertRecreated(new Expectation(request().withPath("/validated"))
            .thenForwardValidate(HttpForwardValidateAction.forwardValidate()
                .withSpecUrlOrPayload("https://example.com/openapi.json")
                .withHost("upstream.example.com")
                .withPort(8443)
                .withScheme(HttpForward.Scheme.HTTPS)
                .withValidateRequest(false)
                .withValidateResponse(true)
                .withValidationMode(HttpForwardValidateAction.ValidationMode.values()[HttpForwardValidateAction.ValidationMode.values().length - 1])
                .withDelay(TimeUnit.MILLISECONDS, 2)));
    }

    @Test
    public void shouldRecreateForwardWithFallback() throws Exception {
        assertRecreated(new Expectation(request().withPath("/fallback"))
            .thenForwardWithFallback(HttpForwardWithFallback.forwardWithFallback()
                .withForward(forward().withHost("upstream.example.com").withPort(8080).withScheme(HttpForward.Scheme.HTTP))
                .withFallback(response().withStatusCode(503).withBody("fallback body"))
                .withFallbackOnStatusCodes(500, 502)
                .withFallbackOnTimeout(true)));
    }

    @Test
    public void shouldRecreateBeforeAndAfterActions() throws Exception {
        assertRecreated(new Expectation(request().withPath("/hooks"))
            .withBeforeActions(
                AfterAction.beforeAction()
                    .withHttpRequest(request().withMethod("POST").withPath("/audit").withBody("before"))
                    .withDelay(new Delay(TimeUnit.MILLISECONDS, 5))
                    .withBlocking(true)
                    .withTimeout(new Delay(TimeUnit.SECONDS, 2))
                    .withFailurePolicy(FailurePolicy.FAIL_FAST),
                AfterAction.beforeAction().withHttpClassCallback(callback().withCallbackClass("org.example.Before"))
            )
            .withAfterActions(AfterAction.afterAction().withHttpRequest(request().withPath("/after")).withFailurePolicy(FailurePolicy.BEST_EFFORT))
            .thenRespond(response().withStatusCode(204)));
    }

    @Test
    public void shouldRecreateTemplatedBeforeAndAfterActions() throws Exception {
        assertRecreated(new Expectation(request().withPath("/templated-hooks"))
            .withBeforeActions(AfterAction.beforeAction()
                .withHttpRequestTemplate(HttpTemplate.template(HttpTemplate.TemplateType.MUSTACHE, "{ \"path\": \"/before{{ request.path }}\" }"))
                .withFailurePolicy(FailurePolicy.FAIL_FAST))
            .withAfterActions(AfterAction.afterAction()
                .withHttpRequestTemplate(HttpTemplate.template(HttpTemplate.TemplateType.VELOCITY, "{ \"path\": \"/after$!request.path\" }"))
                .withDelay(new Delay(TimeUnit.MILLISECONDS, 5)))
            .thenRespond(response().withStatusCode(204)));
    }

    @Test
    public void shouldRecreateExpectationWhosePrimaryActionIsANewActionTypeAmongOthers() throws Exception {
        assertRecreated(new Expectation(request().withPath("/several"))
            .thenRespondWithSse(HttpSseResponse.sseResponse().withEvent(SseEvent.sseEvent().withData("primary")).withPrimary(true))
            .thenForwardWithFallback(HttpForwardWithFallback.forwardWithFallback().withForward(forward().withHost("a.example.com").withPort(80)))
            .thenRespondWithDns(DnsResponse.dnsResponse().withAnswerRecord(DnsRecord.aRecord("a.example.com", "10.0.0.1"))));
    }

    @Test
    public void shouldRecreateIdChaosCaptureAndCrossProtocolScenarios() throws Exception {
        assertRecreated(new Expectation(request().withPath("/chaos"))
            .withId("expectation-one")
            .withChaos(chaosProfile())
            .withCapture(
                CaptureRule.capture(CaptureRule.Source.jsonPath, "$.user.id", "userId"),
                CaptureRule.captureRule().withSource(CaptureRule.Source.header).withExpression("x-\"trace\"").withInto("trace")
            )
            .withCrossProtocolScenarios(Arrays.asList(
                CrossProtocolScenario.onDnsQuery("api.example.com", "flow", "DnsSeen"),
                CrossProtocolScenario.crossProtocolScenario().withTrigger(CrossProtocolTrigger.WEBSOCKET_CONNECT).withScenarioName("flow").withTargetState("Connected")
            ))
            .thenRespond(response().withStatusCode(200).withBody("ok")));
    }

    @Test
    public void shouldRecreateRateLimit() throws Exception {
        assertRecreated(new Expectation(request().withPath("/limited"))
            .withId("expectation-two")
            .withRateLimit(RateLimit.rateLimit()
                .withName("shared")
                .withAlgorithm(RateLimit.Algorithm.TOKEN_BUCKET)
                .withLimit(5)
                .withWindowMillis(1000L)
                .withBurst(10L)
                .withRefillPerSecond(2.5)
                .withErrorStatus(503)
                .withRetryAfter("3"))
            .thenRespond(response().withStatusCode(201)));
    }

    @Test
    public void shouldRecreateSteps() throws Exception {
        assertRecreated(new Expectation(request().withPath("/steps"))
            .withSteps(
                ExpectationStep.step()
                    .withHttpRequest(request().withMethod("POST").withPath("/audit"))
                    .withDelay(new Delay(TimeUnit.MILLISECONDS, 5))
                    .withBlocking(true)
                    .withTimeout(new Delay(TimeUnit.SECONDS, 1))
                    .withFailurePolicy(FailurePolicy.FAIL_FAST),
                ExpectationStep.step().withHttpClassCallback(callback().withCallbackClass("org.example.Step")).withBlocking(false),
                ExpectationStep.step().withHttpResponse(response().withStatusCode(202).withBody("accepted")).withResponder(true),
                ExpectationStep.step().withHttpForward(forward().withHost("audit.example.com").withPort(8080)),
                ExpectationStep.step().withHttpOverrideForwardedRequest(HttpOverrideForwardedRequest.forwardOverriddenRequest(request().withPath("/copy"))),
                ExpectationStep.step().withHttpError(HttpError.error().withDropConnection(true))
            )
            .withAfterActions(AfterAction.afterAction().withHttpRequest(request().withPath("/after"))));
    }

    @Test
    public void shouldRecreateExpectationLevelFieldsOfAnExpectationWithSeveralActions() throws Exception {
        assertRecreated(new Expectation(request().withPath("/several-with-fields"))
            .withId("expectation-three")
            .withChaos(HttpChaosProfile.httpChaosProfile().withErrorStatus(500).withErrorProbability(1.0))
            .withRateLimit(RateLimit.rateLimit().withLimit(1).withWindowMillis(60000L))
            .withCapture(CaptureRule.capture(CaptureRule.Source.queryStringParameter, "id", "id"))
            .withCrossProtocolScenario(CrossProtocolScenario.onHttpPath("/next.*", "flow", "Next"))
            .thenRespond(response().withStatusCode(200))
            .thenRespondWithBinary(BinaryResponse.binaryResponse(new byte[]{1, 2})));
    }

    @Test
    public void shouldRecreateGraphQLBodyWithVariablesSchemaButNoOperationNameAndWithSchema() throws Exception {
        assertRecreated(new Expectation(request().withPath("/graphql").withBody(
            GraphQLBody.graphQL("query { a(id: $id) }", null, "{\"type\":\"object\",\"required\":[\"id\"]}")
                .withSelectionSetMatchType(SelectionSetMatchType.values()[0])
                .withFields("a")
                .withSchema("type Query { a(id: ID): String }")))
            .thenRespond(response().withBody("{\"data\":{\"a\":\"x\"}}")));
    }

    private static HttpChaosProfile chaosProfile() {
        return HttpChaosProfile.httpChaosProfile()
            .withErrorStatus(503)
            .withRetryAfter("7")
            .withErrorProbability(0.25)
            .withDropConnectionProbability(0.1)
            .withLatency(new Delay(TimeUnit.MILLISECONDS, 40))
            .withSeed(99L)
            .withSucceedFirst(2)
            .withFailRequestCount(3)
            .withOutageAfterMillis(1000L)
            .withOutageDurationMillis(5000L)
            .withTruncateBodyAtFraction(0.5)
            .withMalformedBody(true)
            .withSlowResponseChunkSize(16)
            .withSlowResponseChunkDelay(new Delay(TimeUnit.MILLISECONDS, 3))
            .withQuotaName("quota")
            .withQuotaLimit(10)
            .withQuotaWindowMillis(60000L)
            .withQuotaErrorStatus(429)
            .withDegradationRampMillis(2000L)
            .withGraphqlErrors(true)
            .withGraphqlErrorMessage("broken \"field\"")
            .withGraphqlErrorCode("INTERNAL")
            .withGraphqlNullifyData(false);
    }

    @Test
    public void shouldRecreateAnyOfRequestMatcherWithEveryBranchKind() throws Exception {
        assertRecreated(new Expectation(AnyOfRequestDefinition.requestAnyOf(
            request().withMethod("GET").withPath("/pets"),
            ConditionalRequestDefinition.requestIf(request().withMethod("POST"), request().withPath("/orders"), request().withPath("/other")),
            AnyOfRequestDefinition.requestAnyOf(request().withPath("/admin/.*")).withNot(true)
        )).thenRespond(response().withBody("either")));
    }

    @Test
    public void shouldRecreateConditionalRequestMatcherWithAnAbsentThenBranch() throws Exception {
        assertRecreated(new Expectation(Not.not(ConditionalRequestDefinition.requestIf(
            request().withMethod("GET"),
            null,
            AnyOfRequestDefinition.requestAnyOf(request().withPath("/a"), request().withPath("/b"))
        ))).thenRespond(response().withBody("conditional")));
    }

    private static void assertRecreated(Expectation original) throws Exception {
        boolean idSet = original.hasId();
        String generatedCode = new ExpectationToJavaSerializer().serialize(2, original);
        assertThat(generatedCode, containsString(CLIENT_CONSTRUCTION));
        assertThat(generatedCode, not(containsString("NOT POSSIBLE")));

        CLIENT.upserted.clear();
        run(generatedCode.replace(CLIENT_CONSTRUCTION, "client"));

        assertThat(CLIENT.upserted, hasSize(1));
        if (idSet) {
            assertThat("generated code:\n" + generatedCode, CLIENT.upserted.get(0).toString(), is(original.toString()));
        } else {
            assertThat("generated code:\n" + generatedCode, withoutId(CLIENT.upserted.get(0)), is(withoutId(original)));
        }
    }

    private static String withoutId(Expectation expectation) {
        return expectation.toString().replaceAll("\"id\" : \"[^\"]*\",?\\s*", "");
    }

    private static void run(String statement) throws Exception {
        String source = "" +
            "import org.mockserver.client.MockServerClient;\n" +
            "import org.mockserver.matchers.Times;\n" +
            "import org.mockserver.matchers.TimeToLive;\n" +
            "import org.mockserver.model.*;\n" +
            "import java.util.concurrent.TimeUnit;\n" +
            "import static org.mockserver.model.HttpClassCallback.callback;\n" +
            "import static org.mockserver.model.HttpError.error;\n" +
            "import static org.mockserver.model.HttpForward.forward;\n" +
            "import static org.mockserver.model.HttpOverrideForwardedRequest.forwardOverriddenRequest;\n" +
            "import static org.mockserver.model.HttpRequest.request;\n" +
            "import static org.mockserver.model.HttpResponse.response;\n" +
            "import static org.mockserver.model.HttpTemplate.template;\n" +
            "\n" +
            "public class GeneratedExpectation implements java.util.function.Consumer<MockServerClient> {\n" +
            "    public void accept(MockServerClient client) {\n" +
            statement + "\n" +
            "    }\n" +
            "}\n";
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        Map<String, ByteArrayOutputStream> classes = new HashMap<>();
        JavaFileManager fileManager = new ForwardingJavaFileManager<StandardJavaFileManager>(compiler.getStandardFileManager(diagnostics, null, null)) {
            @Override
            public JavaFileObject getJavaFileForOutput(Location location, String className, JavaFileObject.Kind kind, FileObject sibling) {
                return new SimpleJavaFileObject(URI.create("mem:///" + className.replace('.', '/') + kind.extension), kind) {
                    @Override
                    public OutputStream openOutputStream() {
                        return classes.computeIfAbsent(className, name -> new ByteArrayOutputStream());
                    }
                };
            }
        };
        JavaFileObject sourceFile = new SimpleJavaFileObject(URI.create("string:///GeneratedExpectation.java"), JavaFileObject.Kind.SOURCE) {
            @Override
            public CharSequence getCharContent(boolean ignoreEncodingErrors) {
                return source;
            }
        };
        List<String> options = List.of("-classpath", System.getProperty("java.class.path"));
        boolean compiled = compiler.getTask(null, fileManager, diagnostics, options, null, Collections.singletonList(sourceFile)).call();
        String errors = diagnostics.getDiagnostics().stream().map(Object::toString).collect(Collectors.joining("\n"));
        assertThat("generated code:\n" + statement + "\ncompiler output:\n" + errors, compiled, is(true));

        ClassLoader classLoader = new ClassLoader(GeneratedJavaCodeRoundTripTest.class.getClassLoader()) {
            @Override
            protected Class<?> findClass(String name) throws ClassNotFoundException {
                ByteArrayOutputStream bytes = classes.get(name);
                if (bytes == null) {
                    throw new ClassNotFoundException(name);
                }
                byte[] classBytes = bytes.toByteArray();
                return defineClass(name, classBytes, 0, classBytes.length);
            }
        };
        @SuppressWarnings("unchecked")
        Consumer<MockServerClient> generated = (Consumer<MockServerClient>) classLoader
            .loadClass("GeneratedExpectation")
            .getDeclaredConstructor()
            .newInstance();
        generated.accept(CLIENT);
    }

    /**
     * Keeps what it is asked to submit instead of sending it.
     */
    private static class CapturingClient extends MockServerClient {

        private final List<Expectation> upserted = new ArrayList<>();

        CapturingClient() {
            super("localhost", 1080);
        }

        @Override
        public Expectation[] upsert(Expectation... expectations) {
            upserted.addAll(Arrays.asList(expectations));
            return expectations;
        }
    }
}
