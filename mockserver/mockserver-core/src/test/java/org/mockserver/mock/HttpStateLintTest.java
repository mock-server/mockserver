package org.mockserver.mock;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockserver.configuration.Configuration;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.matchers.MatchType;
import org.mockserver.matchers.TimeToLive;
import org.mockserver.matchers.Times;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.JsonBody;
import org.mockserver.responsewriter.ResponseWriter;
import org.mockserver.scheduler.Scheduler;
import org.mockserver.serialization.ExpectationSerializer;
import org.mockserver.serialization.ObjectMapperFactory;
import org.slf4j.event.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.lessThan;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.core.Is.is;
import static org.mockserver.configuration.Configuration.configuration;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

/**
 * Expectation lint: {@code PUT /mockserver/lint} and the advisory warnings on {@code PUT /mockserver/expectation}.
 * Each "never matches" finding is checked against the real matcher too, so the advice cannot drift from serving.
 */
public class HttpStateLintTest {

    private final ObjectMapper objectMapper = ObjectMapperFactory.createObjectMapper();
    private final List<LogEntry> logged = new CopyOnWriteArrayList<>();
    private ExpectationSerializer expectationSerializer;
    private HttpState httpState;

    @Before
    public void setUp() {
        Configuration configuration = configuration().logLevel(Level.INFO);
        MockServerLogger logger = new MockServerLogger(configuration, HttpStateLintTest.class) {
            @Override
            public void logEvent(LogEntry logEntry) {
                logged.add(logEntry);
            }
        };
        Scheduler scheduler = new Scheduler(configuration, logger, true);
        httpState = new HttpState(configuration, logger, scheduler);
        expectationSerializer = new ExpectationSerializer(new MockServerLogger());
    }

    @After
    public void stopHttpState() {
        if (httpState != null) {
            httpState.stop();
        }
    }

    // ---- single-expectation rules ----

    @Test
    public void shouldFlagAPathWithoutALeadingSlashThatTheMatcherNeverMatches() throws Exception {
        Expectation expectation = new Expectation(request().withMethod("GET").withPath("users/1")).thenRespond(response());

        assertThat(rules(lint(expectation)), hasItem("PATH_MISSING_LEADING_SLASH"));
        assertNeverMatches(expectation, request().withMethod("GET").withPath("/users/1"));
    }

    @Test
    public void shouldNotFlagAHostAndPortPathOrAConnectRequest() throws Exception {
        assertThat(rules(lint(new Expectation(request().withMethod("CONNECT").withPath("example.com")).thenRespond(response()))), not(hasItem("PATH_MISSING_LEADING_SLASH")));
        assertThat(rules(lint(new Expectation(request().withPath("example.com:443")).thenRespond(response()))), not(hasItem("PATH_MISSING_LEADING_SLASH")));
        assertThat(rules(lint(new Expectation(request().withPath(".*/users")).thenRespond(response()))), not(hasItem("PATH_MISSING_LEADING_SLASH")));
    }

    @Test
    public void shouldFlagAFullUrlInThePath() throws Exception {
        Expectation expectation = new Expectation(request().withPath("https://api.example.com/users")).thenRespond(response());

        JsonNode result = lint(expectation);

        assertThat(rules(result), hasItem("PATH_INCLUDES_SCHEME_AND_HOST"));
        assertThat(rules(result), not(hasItem("PATH_MISSING_LEADING_SLASH")));
        assertNeverMatches(expectation, request().withPath("/users").withHeader("Host", "api.example.com"));
    }

    @Test
    public void shouldFlagAQueryStringInThePath() throws Exception {
        Expectation expectation = new Expectation(request().withPath("/search?q=shoes")).thenRespond(response());

        assertThat(rules(lint(expectation)), hasItem("PATH_INCLUDES_QUERY_STRING"));
        assertNeverMatches(expectation, request().withPath("/search").withQueryStringParameter("q", "shoes"));
        assertThat(rules(lint(new Expectation(request().withPath("/users/?")).thenRespond(response()))), not(hasItem("PATH_INCLUDES_QUERY_STRING")));
    }

    @Test
    public void shouldFlagAGlobWildcardInThePath() throws Exception {
        Expectation expectation = new Expectation(request().withPath("/api/*")).thenRespond(response());

        assertThat(issue(lint(expectation), "PATH_GLOB_WILDCARD").path("message").asText(), containsString("matches any number of slashes, including none, and nothing else"));
        assertNeverMatches(expectation, request().withPath("/api/users"));
        assertThat("as the message says, the regex does match the path without the slash", matches(expectation, request().withPath("/api")), is(true));
        assertThat(rules(lint(new Expectation(request().withPath("/api/.*")).thenRespond(response()))), not(hasItem("PATH_GLOB_WILDCARD")));
        assertThat("OPTIONS * uses the asterisk-form target", rules(lint(new Expectation(request().withMethod("OPTIONS").withPath("*")).thenRespond(response()))), not(hasItem("PATH_GLOB_WILDCARD")));
        assertThat(rules(lint(new Expectation(request().withPath("*/users")).thenRespond(response()))), hasItem("PATH_GLOB_WILDCARD"));
    }

    @Test
    public void shouldFlagAPathTemplateWithoutPathParameters() throws Exception {
        Expectation expectation = new Expectation(request().withPath("/users/{id}")).thenRespond(response());

        assertThat(rules(lint(expectation)), hasItem("PATH_TEMPLATE_WITHOUT_PATH_PARAMETERS"));
        assertNeverMatches(expectation, request().withPath("/users/1"));

        Expectation withParameters = new Expectation(request().withPath("/users/{id}").withPathParameter("id", "[0-9]+")).thenRespond(response());
        assertThat(rules(lint(withParameters)), not(hasItem("PATH_TEMPLATE_WITHOUT_PATH_PARAMETERS")));
        assertThat(rules(lint(new Expectation(request().withPath("/users/[0-9]{3}")).thenRespond(response()))), not(hasItem("PATH_TEMPLATE_WITHOUT_PATH_PARAMETERS")));
    }

    @Test
    public void shouldSuggestTheMethodAMisspeltMethodWasMeantToBe() throws Exception {
        JsonNode result = lint(new Expectation(request().withMethod("GTE").withPath("/users")).thenRespond(response()));

        assertThat(issue(result, "METHOD_TYPO").path("message").asText(), containsString("did you mean GET?"));
        assertThat(rules(lint(new Expectation(request().withMethod("PURGE").withPath("/users")).thenRespond(response()))), not(hasItem("METHOD_TYPO")));
        assertThat(rules(lint(new Expectation(request().withMethod("get").withPath("/users")).thenRespond(response()))), not(hasItem("METHOD_TYPO")));
    }

    @Test
    public void shouldSuggestTheHeaderAMisspeltHeaderNameWasMeantToBe() throws Exception {
        JsonNode result = lint(new Expectation(request().withPath("/users")
            .withHeader("Authorisation", "Bearer x")
            .withHeader("content_type", "application/json")
            .withHeader("access_control_request_method", "GET")
            .withHeader("X-Tenant", "gold")
            .withHeader("Accept", "application/json")).thenRespond(response()));

        List<String> messages = messages(result, "HEADER_NAME_TYPO");
        assertThat(messages, hasSize(3));
        assertThat(messages, hasItem("header \"Authorisation\" is not a standard header name; did you mean Authorization?"));
        assertThat(messages, hasItem("header \"content_type\" is not a standard header name; did you mean Content-Type?"));
        assertThat("underscores for hyphens, too far apart for an edit-distance match", messages, hasItem("header \"access_control_request_method\" is not a standard header name; did you mean Access-Control-Request-Method?"));
        assertThat("never quotes the header value", result.toString(), not(containsString("Bearer x")));
    }

    @Test
    public void shouldNotTakeACustomHeaderBuiltFromAKnownNameForATypo() {
        for (String custom : new String[]{"X-Authorization", "X-Origin", "X-Cookie", "X-Accept", "Request-Id", "Correlation-Id", "Content-Id", "Content-Hash",
            "X-Api-Version", "X-Tenant-Id", "Idempotency-Key", "Accept-Version", "X-Forwarded-Prefix", "Sec-Fetch-Mode", "Baggage", "X-Amz-Date"}) {
            assertThat(custom, ExpectationLinter.suggestHeaderName(custom), nullValue());
        }
        assertThat(ExpectationLinter.suggestHeaderName("Referrer"), is("Referer"));
    }

    @Test
    public void shouldNotFlagRegisteredOrCommonExtensionMethods() throws Exception {
        for (String method : new String[]{"UNLINK", "LINK", "BATCH", "FETCH", "PURGE", "SEARCH", "REPORT", "MERGE"}) {
            assertThat(method, rules(lint(new Expectation(request().withMethod(method).withPath("/x")).thenRespond(response()))), not(hasItem("METHOD_TYPO")));
        }
        assertThat(rules(lint(new Expectation(request().withMethod("DELET").withPath("/x")).thenRespond(response()))), hasItem("METHOD_TYPO"));
    }

    @Test
    public void shouldNotFlagRegexConstructsAsPathMistakes() throws Exception {
        assertThat(rules(lint(new Expectation(request().withPath("users|/accounts")).thenRespond(response()))), not(hasItem("PATH_MISSING_LEADING_SLASH")));
        assertThat(rules(lint(new Expectation(request().withPath("/a(?=b)")).thenRespond(response()))), not(hasItem("PATH_INCLUDES_QUERY_STRING")));
        assertThat(rules(lint(new Expectation(request().withPath("/a(?<=b)c")).thenRespond(response()))), not(hasItem("PATH_INCLUDES_QUERY_STRING")));
    }

    @Test
    public void shouldFlagTimesThatAllowNoMatches() throws Exception {
        Expectation expectation = new Expectation(request().withPath("/once"), Times.exactly(0), TimeToLive.unlimited(), 0).thenRespond(response());

        assertThat(rules(lint(expectation)), hasItem("TIMES_ZERO"));
        assertNeverMatches(expectation, request().withPath("/once"));
    }

    @Test
    public void shouldFlagAZeroTimeToLiveAndAZeroPercentage() throws Exception {
        assertThat(rules(lint(new Expectation(request().withPath("/ttl"), Times.unlimited(), TimeToLive.exactly(TimeUnit.SECONDS, 0L), 0).thenRespond(response()))), hasItem("TIME_TO_LIVE_ZERO"));
        assertThat(rules(lint(new Expectation(request().withPath("/never")).withPercentage(0).thenRespond(response()))), hasItem("PERCENTAGE_ZERO"));
        assertThat(rules(lint(new Expectation(request().withPath("/half")).withPercentage(50).thenRespond(response()))), not(hasItem("PERCENTAGE_ZERO")));
    }

    @Test
    public void shouldFlagSequentialResponsesThatTimesNeverReaches() throws Exception {
        Expectation expectation = new Expectation(request().withPath("/sequence"), Times.exactly(2), TimeToLive.unlimited(), 0)
            .thenRespond(java.util.Arrays.asList(response().withBody("one"), response().withBody("two"), response().withBody("three")));

        JsonNode found = issue(lint(expectation), "UNREACHABLE_RESPONSES");

        assertThat(found.path("message").asText(), containsString("only the first 2 of its 3 responses can be served"));
        httpState.add(expectation);
        assertThat(servedBody("/sequence"), is("one"));
        assertThat(servedBody("/sequence"), is("two"));
        assertThat("the third response is never served", httpState.firstMatchingExpectation(request().withPath("/sequence")), nullValue());
    }

    @Test
    public void shouldReportStrictJsonArraysAsInformationOnly() throws Exception {
        Expectation expectation = new Expectation(request().withPath("/orders").withBody(JsonBody.json("{\"items\":[1,2]}", MatchType.STRICT))).thenRespond(response());

        assertThat(issue(lint(expectation), "STRICT_JSON_BODY_WITH_ARRAYS").path("severity").asText(), is("INFO"));
        assertThat(rules(lint(new Expectation(request().withPath("/orders").withBody(JsonBody.json("{\"items\":[1,2]}"))).thenRespond(response()))), not(hasItem("STRICT_JSON_BODY_WITH_ARRAYS")));
    }

    @Test
    public void shouldReportNothingForAWellFormedExpectation() throws Exception {
        JsonNode result = lint(new Expectation(request().withMethod("POST").withPath("/users/[0-9]+").withHeader("Content-Type", "application/json")).thenRespond(response()));

        assertThat(result.path("issueCount").asInt(), is(0));
        assertThat(result.path("expectationsChecked").asInt(), is(1));
        assertThat(result.path("source").asText(), is("request"));
    }

    // ---- shadowing ----

    @Test
    public void shouldFlagAnExpectationShadowedByAnEarlierCatchAll() throws Exception {
        Expectation catchAll = new Expectation(request().withMethod("GET").withPath("/users/.*")).withId("catch-all").thenRespond(response().withBody("all"));
        Expectation specific = new Expectation(request().withMethod("GET").withPath("/users/1").withHeader("X-Tenant", "gold")).withId("specific").thenRespond(response().withBody("one"));

        JsonNode found = issue(lint(catchAll, specific), "SHADOWED");

        assertThat(found.path("expectationId").asText(), is("specific"));
        assertThat(found.path("relatedExpectationId").asText(), is("catch-all"));
        assertThat(found.path("index").asInt(), is(1));
        assertThat(found.path("relatedIndex").asInt(), is(0));
        assertThat(found.path("message").asText(), containsString("same priority, created earlier"));
        httpState.add(catchAll, specific);
        assertThat(httpState.firstMatchingExpectation(request().withMethod("GET").withPath("/users/1").withHeader("X-Tenant", "gold")).getId(), is("catch-all"));
    }

    @Test
    public void shouldFlagAnExpectationShadowedByALaterHigherPriorityOne() throws Exception {
        Expectation first = new Expectation(request().withMethod("GET").withPath("/orders")).withId("first").thenRespond(response());
        Expectation priority = new Expectation(request().withPath("/orders")).withId("priority").withPriority(5).thenRespond(response());

        JsonNode found = issue(lint(first, priority), "SHADOWED");

        assertThat(found.path("expectationId").asText(), is("first"));
        assertThat(found.path("relatedExpectationId").asText(), is("priority"));
        assertThat(found.path("message").asText(), containsString("(priority 5 > 0)"));
    }

    @Test
    public void shouldFlagAnExactDuplicateMatcherAsShadowed() throws Exception {
        JsonNode result = lint(
            new Expectation(request().withPath("/same").withQueryStringParameter("page", "1")).thenRespond(response()),
            new Expectation(request().withPath("/same").withQueryStringParameter("page", "1")).thenRespond(response())
        );

        assertThat(issue(result, "SHADOWED").path("index").asInt(), is(1));
    }

    @Test
    public void shouldNotFlagAnExpectationServedBeforeTheBodyAheadOfAnEarlierCatchAll() throws Exception {
        Expectation catchAll = new Expectation(request().withPath("/rb/.*")).withId("catch-all").thenRespond(response());
        Expectation early = new Expectation(request().withPath("/rb/1").withRespondBeforeBody(true)).withId("early").thenRespond(response());

        assertThat(rules(lint(catchAll, early)), not(hasItem("SHADOWED")));

        Expectation earlyCatchAll = new Expectation(request().withPath("/rb/.*").withRespondBeforeBody(true)).withId("early-catch-all").thenRespond(response());
        assertThat("both served before the body: the earlier one still wins", issue(lint(earlyCatchAll, early), "SHADOWED").path("expectationId").asText(), is("early"));
    }

    @Test
    public void shouldVisitEachLaterExpectationOnceWhenOneCatchAllShadowsThemAll() {
        List<ExpectationLinter.Subject> subjects = new ArrayList<>();
        subjects.add(new ExpectationLinter.Subject(new Expectation(request()).thenRespond(response()), 0, null, "catch-all"));
        int count = 3_000;
        for (int i = 1; i < count; i++) {
            subjects.add(new ExpectationLinter.Subject(new Expectation(request().withPath("/p" + i + "/.*")).thenRespond(response()), i, null, "e" + i));
        }
        ExpectationLinter.Report report = new ExpectationLinter.Report();

        ExpectationLinter.lint(report, subjects, true);

        assertThat("shadowed expectations are not compared again, so the work is linear here", report.pairComparisons, is((long) count - 1));
        assertThat("every later one is shadowed; only the first MAX_ISSUES are listed", report.issues, hasSize(ExpectationLinter.MAX_ISSUES));
        assertThat(report.issuesTruncated, is(true));
        assertThat(report.shadowCheckTruncated, is(false));
    }

    @Test
    public void shouldNotFlagShadowingThatAGateCanBypass() throws Exception {
        HttpRequest matcher = request().withMethod("GET").withPath("/gated");
        assertThat("earlier one runs out", rules(lint(new Expectation(matcher, Times.once(), TimeToLive.unlimited(), 0).thenRespond(response()), new Expectation(matcher).thenRespond(response()))), not(hasItem("SHADOWED")));
        assertThat("earlier one expires", rules(lint(new Expectation(matcher, Times.unlimited(), TimeToLive.exactly(TimeUnit.MINUTES, 5L), 0).thenRespond(response()), new Expectation(matcher).thenRespond(response()))), not(hasItem("SHADOWED")));
        assertThat("earlier one needs a scenario state", rules(lint(new Expectation(matcher).withScenarioName("checkout").withScenarioState("Paid").thenRespond(response()), new Expectation(matcher).thenRespond(response()))), not(hasItem("SHADOWED")));
        assertThat("earlier one is served for some requests only", rules(lint(new Expectation(matcher).withPercentage(50).thenRespond(response()), new Expectation(matcher).thenRespond(response()))), not(hasItem("SHADOWED")));
        assertThat("different namespaces", rules(lint(new Expectation(matcher).withNamespace("a").thenRespond(response()), new Expectation(matcher).withNamespace("b").thenRespond(response()))), not(hasItem("SHADOWED")));
    }

    @Test
    public void shouldNotFlagShadowingWhenTheEarlierOneIsMoreSpecific() throws Exception {
        assertThat("earlier one needs a header", rules(lint(
            new Expectation(request().withPath("/x").withHeader("X-Tenant", "gold")).thenRespond(response()),
            new Expectation(request().withPath("/x")).thenRespond(response()))), not(hasItem("SHADOWED")));
        assertThat("earlier one needs another header value", rules(lint(
            new Expectation(request().withPath("/x").withHeader("X-Tenant", "gold")).thenRespond(response()),
            new Expectation(request().withPath("/x").withHeader("X-Tenant", "silver")).thenRespond(response()))), not(hasItem("SHADOWED")));
        assertThat("earlier one needs a body", rules(lint(
            new Expectation(request().withPath("/x").withBody("a")).thenRespond(response()),
            new Expectation(request().withPath("/x")).thenRespond(response()))), not(hasItem("SHADOWED")));
        assertThat("earlier path regex does not cover a later regex", rules(lint(
            new Expectation(request().withPath("/x/[0-9]+")).thenRespond(response()),
            new Expectation(request().withPath("/x/.*")).thenRespond(response()))), not(hasItem("SHADOWED")));
        assertThat("earlier path regex does not match the later path", rules(lint(
            new Expectation(request().withPath("/x/[0-9]+")).thenRespond(response()),
            new Expectation(request().withPath("/x/abc")).thenRespond(response()))), not(hasItem("SHADOWED")));
        assertThat("earlier one negates its path", rules(lint(
            new Expectation(request().withPath(org.mockserver.model.NottableString.not("/y"))).thenRespond(response()),
            new Expectation(request().withPath("/x")).thenRespond(response()))), not(hasItem("SHADOWED")));
    }

    @Test
    public void shouldBoundTheShadowingCheckForAPathologicalRegex() throws Exception {
        List<Expectation> expectations = new ArrayList<>();
        expectations.add(new Expectation(request().withPath("((a+)+)+b")).thenRespond(response()));
        for (int i = 0; i < 40; i++) {
            expectations.add(new Expectation(request().withPath("a".repeat(60 + i))).thenRespond(response()));
        }

        long start = System.nanoTime();
        JsonNode result = lint(expectations.toArray(new Expectation[0]));

        assertThat(TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - start), lessThan(20L));
        assertThat(result.path("shadowCheckTruncated").asBoolean(), is(true));
        assertThat(rules(result), not(hasItem("SHADOWED")));
    }

    @Test
    public void shouldStopTheShadowingCheckAtThePairBudgetAndSaySo() throws Exception {
        List<Expectation> expectations = new ArrayList<>();
        for (int i = 0; i < 500; i++) {
            expectations.add(new Expectation(request().withPath("/p" + i + "/.*").withHeader("X-Id", "v" + i)).thenRespond(response()));
        }

        JsonNode result = lint(expectations.toArray(new Expectation[0]));

        assertThat(result.path("shadowCheckTruncated").asBoolean(), is(true));
        assertThat(result.path("pairComparisons").asLong(), is((long) ExpectationLinter.MAX_PAIR_COMPARISONS));
        assertThat(result.path("expectationsChecked").asInt(), is(500));
    }

    // ---- input handling ----

    @Test
    public void shouldReportInvalidEntriesDuplicateIdsAndSkipOpenApiEntries() throws Exception {
        String body = "[" +
            "{\"id\":\"dup\",\"httpRequest\":{\"path\":\"/a\"},\"httpResponse\":{}}," +
            "{\"httpRequest\":{\"path\":\"/b\",\"method\":42,\"unknownField\":true},\"httpResponse\":{}}," +
            "{\"specUrlOrPayload\":\"https://example.com/spec.yaml\"}," +
            "{\"id\":\"dup\",\"httpRequest\":{\"path\":\"/c\"},\"httpResponse\":{}}" +
            "]";

        JsonNode result = lintBody(body);

        JsonNode invalid = issue(result, "INVALID_EXPECTATION");
        assertThat(invalid.path("index").asInt(), is(1));
        assertThat(invalid.path("severity").asText(), is("ERROR"));
        JsonNode duplicate = issue(result, "DUPLICATE_ID");
        assertThat(duplicate.path("index").asInt(), is(0));
        assertThat(duplicate.path("relatedIndex").asInt(), is(3));
        assertThat(result.path("openApiExpectationsSkipped").asInt(), is(1));
        assertThat(result.path("expectationsChecked").asInt(), is(1));
        assertThat(result.path("errorCount").asInt(), is(1));
        assertThat("lint stores nothing", httpState.getRequestMatchers().retrieveActiveExpectations(null), empty());
    }

    @Test
    public void shouldAnswerABodyThatIsNotJsonWithABadRequest() {
        HttpResponse response = handle(request("/mockserver/lint").withMethod("PUT").withBody("{not json"));

        assertThat(response.getStatusCode(), is(400));
        assertThat(response.getBodyAsString(), containsString("failed to lint expectations"));
    }

    @Test
    public void shouldLintTheActiveStoredExpectationsInServingOrderWhenTheBodyIsEmpty() throws Exception {
        httpState.add(
            new Expectation(request().withPath("/stored/.*")).withId("catch-all").thenRespond(response()),
            new Expectation(request().withPath("/stored/1")).withId("shadowed").thenRespond(response()),
            new Expectation(request().withPath("/used-up"), Times.once(), TimeToLive.unlimited(), 0).withId("used-up").thenRespond(response())
        );
        assertThat(httpState.firstMatchingExpectation(request().withPath("/used-up")).getId(), is("used-up"));

        HttpResponse response = handle(request("/mockserver/lint").withMethod("PUT"));

        assertThat(response.getStatusCode(), is(200));
        JsonNode result = objectMapper.readTree(response.getBodyAsString());
        assertThat(result.path("source").asText(), is("stored"));
        assertThat(result.path("expectationsChecked").asInt(), is(2));
        assertThat(result.path("inactiveExpectationsSkipped").asInt(), is(1));
        JsonNode found = issue(result, "SHADOWED");
        assertThat(found.path("expectationId").asText(), is("shadowed"));
        assertThat(found.path("position").asInt(), is(2));
        assertThat(found.path("relatedPosition").asInt(), is(1));
        assertThat("lint changes nothing", httpState.firstMatchingExpectation(request().withPath("/stored/1")).getId(), is("catch-all"));
    }

    @Test
    public void shouldNotFailWhenARegexOverflowsTheStackDuringTheShadowingCheck() throws Throwable {
        // given - a regex that java.util.regex matches recursively, compared with a 12,001 character literal path
        String body = "[{\"id\":\"catch-all\",\"httpRequest\":{\"path\":\"/(a|b)*\"},\"httpResponse\":{}},"
            + "{\"id\":\"long\",\"httpRequest\":{\"path\":\"/" + new String(new char[6_000]).replace("\0", "ab") + "\"},\"httpResponse\":{}}]";
        JsonNode[] result = new JsonNode[1];
        Throwable[] escaped = new Throwable[1];

        // when
        Thread thread = new Thread(null, () -> {
            try {
                result[0] = lintBody(body);
            } catch (Throwable throwable) {
                escaped[0] = throwable;
            }
        }, "small-stack", 512 * 1024);
        thread.start();
        thread.join(TimeUnit.SECONDS.toMillis(120));
        assertThat("finished", thread.isAlive(), is(false));
        assertThat(String.valueOf(escaped[0]), escaped[0] == null, is(true));

        // then - a 200, and the pair counts as not shadowed because it cannot be proved
        assertThat(result[0].toPrettyString(), rules(result[0]), not(hasItem("SHADOWED")));
        assertThat(result[0].path("expectationsChecked").asInt(), is(2));
    }

    // ---- advice on PUT /mockserver/expectation ----

    @Test
    public void shouldWarnButStillStoreAnExpectationThatCanNeverMatch() {
        Expectation expectation = new Expectation(request().withPath("users")).withId("no-slash").thenRespond(response());

        HttpResponse response = handle(request("/mockserver/expectation").withMethod("PUT").withBody(expectationSerializer.serialize(expectation)));

        assertThat(response.getStatusCode(), is(201));
        assertThat(response.getFirstHeader(ExpectationLinter.LINT_WARNINGS_HEADER), is("1"));
        assertThat(httpState.getRequestMatchers().retrieveActiveExpectations(null), hasSize(1));
        List<String> warnings = lintLogMessages();
        assertThat(warnings, hasSize(1));
        assertThat(warnings.get(0), containsString("PATH_MISSING_LEADING_SLASH expectation 'no-slash' (any method users)"));
    }

    @Test
    public void shouldNotWarnOnAWellFormedExpectationOrOnInformationOnlyFindings() {
        Expectation strict = new Expectation(request().withPath("/orders").withBody(JsonBody.json("{\"items\":[1]}", MatchType.STRICT))).thenRespond(response());

        HttpResponse response = handle(request("/mockserver/expectation").withMethod("PUT").withBody(expectationSerializer.serialize(strict)));

        assertThat(response.getStatusCode(), is(201));
        assertThat(response.getFirstHeader(ExpectationLinter.LINT_WARNINGS_HEADER), is(""));
        assertThat(lintLogMessages(), empty());
    }

    @Test
    public void shouldNotCheckShadowingOnPut() {
        Expectation catchAll = new Expectation(request().withPath("/put/.*")).thenRespond(response());
        Expectation specific = new Expectation(request().withPath("/put/1")).thenRespond(response());

        HttpResponse response = handle(request("/mockserver/expectation").withMethod("PUT").withBody(expectationSerializer.serialize(new Expectation[]{catchAll, specific})));

        assertThat(response.getStatusCode(), is(201));
        assertThat(response.getFirstHeader(ExpectationLinter.LINT_WARNINGS_HEADER), is(""));
    }

    // ---- helpers ----

    private JsonNode lint(Expectation... expectations) throws Exception {
        return lintBody(expectationSerializer.serialize(expectations));
    }

    private JsonNode lintBody(String body) throws Exception {
        HttpResponse response = handle(request("/mockserver/lint").withMethod("PUT").withBody(body));
        assertThat(response.getBodyAsString(), response.getStatusCode(), is(200));
        return objectMapper.readTree(response.getBodyAsString());
    }

    private HttpResponse handle(HttpRequest request) {
        CapturingResponseWriter responseWriter = new CapturingResponseWriter();
        assertThat(httpState.handle(request, responseWriter, false), is(true));
        assertThat("a response", responseWriter.response != null, is(true));
        return responseWriter.response;
    }

    private void assertNeverMatches(Expectation expectation, HttpRequest intended) {
        HttpState fresh = new HttpState(configuration(), new MockServerLogger(), new Scheduler(configuration(), new MockServerLogger(), true));
        try {
            fresh.add(expectation);
            assertThat("the real matcher agrees the expectation never matches " + intended.getPath(), fresh.firstMatchingExpectation(intended), nullValue());
        } finally {
            fresh.stop();
        }
    }

    private static boolean matches(Expectation expectation, HttpRequest request) {
        HttpState fresh = new HttpState(configuration(), new MockServerLogger(), new Scheduler(configuration(), new MockServerLogger(), true));
        try {
            fresh.add(expectation);
            return fresh.firstMatchingExpectation(request) != null;
        } finally {
            fresh.stop();
        }
    }

    private String servedBody(String path) {
        Expectation matched = httpState.firstMatchingExpectation(request().withPath(path));
        assertThat("an expectation serves " + path, matched != null, is(true));
        return ((HttpResponse) matched.getAction()).getBodyAsString();
    }

    private List<String> lintLogMessages() {
        return logged.stream()
            .filter(entry -> entry.getLogLevel() == Level.WARN && entry.getMessage().contains("expectation lint"))
            .map(LogEntry::getMessage)
            .collect(Collectors.toList());
    }

    private static List<String> rules(JsonNode result) {
        List<String> rules = new ArrayList<>();
        result.path("issues").forEach(issue -> rules.add(issue.path("rule").asText()));
        return rules;
    }

    private static List<String> messages(JsonNode result, String rule) {
        List<String> messages = new ArrayList<>();
        result.path("issues").forEach(issue -> {
            if (rule.equals(issue.path("rule").asText())) {
                messages.add(issue.path("message").asText());
            }
        });
        return messages;
    }

    private static JsonNode issue(JsonNode result, String rule) {
        for (JsonNode issue : result.path("issues")) {
            if (rule.equals(issue.path("rule").asText())) {
                return issue;
            }
        }
        throw new AssertionError("no " + rule + " issue in " + result.toPrettyString());
    }

    private static class CapturingResponseWriter extends ResponseWriter {
        HttpResponse response;

        CapturingResponseWriter() {
            super(configuration(), new MockServerLogger());
        }

        @Override
        public void sendResponse(HttpRequest request, HttpResponse response) {
            this.response = response;
        }
    }
}
