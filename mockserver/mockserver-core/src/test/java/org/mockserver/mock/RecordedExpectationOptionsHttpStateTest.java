package org.mockserver.mock;

import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockserver.authentication.AuthenticationHandler;
import org.mockserver.authentication.AuthenticationResult;
import org.mockserver.authentication.authorization.ControlPlaneRole;
import org.mockserver.configuration.Configuration;
import org.mockserver.fixture.FixtureRedactor;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.matchers.TimeToLive;
import org.mockserver.matchers.Times;
import org.mockserver.model.FileBody;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.RetrieveType;
import org.mockserver.responsewriter.ResponseWriter;
import org.mockserver.scheduler.Scheduler;
import org.mockserver.serialization.ExpectationSerializer;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.mock;
import static org.mockserver.configuration.Configuration.configuration;
import static org.mockserver.log.model.LogEntry.LogMessageType.FORWARDED_REQUEST;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

/**
 * The recording options on {@code PUT /mockserver/retrieve?type=RECORDED_EXPECTATIONS} as the control plane serves
 * them: writing body files needs a role that may change state, and secrets are redacted before bodies are written.
 */
public class RecordedExpectationOptionsHttpStateTest {

    private static final String LARGE_BODY = "{\"report\":\"" + "x".repeat(200) + "\"}";

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    private final List<HttpState> httpStates = new ArrayList<>();
    private final List<Scheduler> schedulers = new ArrayList<>();
    private final ExpectationSerializer expectationSerializer = new ExpectationSerializer(new MockServerLogger());

    private static class CapturingResponseWriter extends ResponseWriter {
        final List<HttpResponse> responses = new CopyOnWriteArrayList<>();
        final List<Thread> writingThreads = new CopyOnWriteArrayList<>();

        CapturingResponseWriter() {
            super(configuration(), new MockServerLogger());
        }

        @Override
        public void sendResponse(HttpRequest request, HttpResponse response) {
            writingThreads.add(Thread.currentThread());
            responses.add(response);
        }

        HttpResponse awaitResponse() throws InterruptedException {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
            while (responses.isEmpty() && System.nanoTime() < deadline) {
                TimeUnit.MILLISECONDS.sleep(10);
            }
            assertThat("a response was written", responses.isEmpty(), is(false));
            return responses.get(0);
        }
    }

    @After
    public void stopHttpStates() {
        httpStates.forEach(HttpState::stop);
        schedulers.forEach(Scheduler::shutdown);
    }

    private HttpState newHttpState(Configuration configuration) {
        return newHttpState(configuration, mock(Scheduler.class));
    }

    private HttpState newHttpState(Configuration configuration, Scheduler scheduler) {
        HttpState httpState = new HttpState(configuration, new MockServerLogger(configuration, MockServerLogger.class), scheduler);
        httpStates.add(httpState);
        HttpRequest recordedRequest = request("/report").withMethod("GET");
        HttpResponse recordedResponse = response(LARGE_BODY).withHeader("Set-Cookie", "session=SECRET-COOKIE");
        httpState.log(
            new LogEntry()
                .setType(FORWARDED_REQUEST)
                .setHttpRequest(recordedRequest)
                .setHttpResponse(recordedResponse)
                .setExpectation(new Expectation(recordedRequest, Times.once(), TimeToLive.unlimited(), 0).thenRespond(recordedResponse))
        );
        return httpState;
    }

    private static HttpRequest retrieve(boolean extractBodies) {
        HttpRequest retrieve = request("/mockserver/retrieve")
            .withMethod("PUT")
            .withQueryStringParameter("type", RetrieveType.RECORDED_EXPECTATIONS.name());
        return extractBodies ? retrieve.withQueryStringParameter("extractBodiesLargerThan", "10") : retrieve;
    }

    private int retrieveAs(Set<String> scopes, boolean extractBodies) throws InterruptedException {
        Map<String, ControlPlaneRole> mapping = new LinkedHashMap<>();
        mapping.put("qa-team", ControlPlaneRole.MUTATE);
        mapping.put("viewers", ControlPlaneRole.READ);
        HttpState httpState = newHttpState(configuration()
            .controlPlaneAuthorizationEnabled(true)
            .controlPlaneScopeMapping(mapping)
            .recordedBodyFilesDirectory(new File(temporaryFolder.getRoot(), "bodies").getPath()));
        httpState.setControlPlaneAuthenticationHandler(new AuthenticationHandler() {
            @Override
            public boolean controlPlaneRequestAuthenticated(HttpRequest request) {
                return true;
            }

            @Override
            public AuthenticationResult authenticate(HttpRequest request) {
                return AuthenticationResult.authenticated("tester", "verified-oidc", Map.of("sub", "tester"), scopes);
            }
        });
        CapturingResponseWriter responseWriter = new CapturingResponseWriter();
        httpState.handle(retrieve(extractBodies), responseWriter, false);
        return responseWriter.awaitResponse().getStatusCode();
    }

    @Test
    public void shouldRequireTheMutateRoleToWriteBodyFiles() throws Exception {
        assertThat(retrieveAs(Set.of("viewers"), false), is(200));
        assertThat(retrieveAs(Set.of("viewers"), true), is(403));
        assertThat(new File(temporaryFolder.getRoot(), "bodies").exists(), is(false));
        assertThat(retrieveAs(Set.of("qa-team"), true), is(200));
        assertThat(new File(temporaryFolder.getRoot(), "bodies").list().length, is(1));
    }

    @Test
    public void shouldWriteBodyFilesOffTheCallingThreadAndLeaveOtherRetrievesOnIt() throws Exception {
        Configuration configuration = configuration().recordedBodyFilesDirectory(new File(temporaryFolder.getRoot(), "bodies").getPath());
        Scheduler scheduler = new Scheduler(configuration, new MockServerLogger(configuration, MockServerLogger.class));
        schedulers.add(scheduler);
        HttpState httpState = newHttpState(configuration, scheduler);
        CapturingResponseWriter extracting = new CapturingResponseWriter();
        CapturingResponseWriter plain = new CapturingResponseWriter();

        httpState.handle(retrieve(true), extracting, false);
        httpState.handle(retrieve(false), plain, false);

        assertThat(extracting.awaitResponse().getStatusCode(), is(200));
        assertThat(extracting.writingThreads.get(0) == Thread.currentThread(), is(false));
        assertThat(new File(temporaryFolder.getRoot(), "bodies").list().length, is(1));
        assertThat(plain.awaitResponse().getStatusCode(), is(200));
        assertThat(plain.writingThreads.get(0), is(Thread.currentThread()));
    }

    @Test
    public void shouldRedactTheRecordingBeforeWritingBodyFiles() throws Exception {
        String directory = new File(temporaryFolder.getRoot(), "bodies").getPath();
        HttpState httpState = newHttpState(configuration().redactSecretsInRecordedExpectations(true).recordedBodyFilesDirectory(directory));
        CapturingResponseWriter responseWriter = new CapturingResponseWriter();

        httpState.handle(retrieve(true), responseWriter, false);

        HttpResponse retrieved = responseWriter.awaitResponse();
        assertThat(retrieved.getStatusCode(), is(200));
        Expectation[] expectations = expectationSerializer.deserializeArray(retrieved.getBodyAsString(), true);
        HttpResponse response = expectations[0].getHttpResponse();
        assertThat(response.getFirstHeader("Set-Cookie"), containsString(FixtureRedactor.REDACTED_PLACEHOLDER));
        assertThat(retrieved.getBodyAsString(), not(containsString("SECRET-COOKIE")));
        Path file = Path.of(((FileBody) response.getBody()).getFilePath());
        assertThat(new String(Files.readAllBytes(file), StandardCharsets.UTF_8), is(LARGE_BODY));
    }
}
