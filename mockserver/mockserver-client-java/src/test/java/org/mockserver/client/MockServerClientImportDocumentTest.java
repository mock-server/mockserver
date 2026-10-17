package org.mockserver.client;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockserver.configuration.ConfigurationProperties;
import org.mockserver.httpclient.NettyHttpClient;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.mock.Expectation;
import org.mockserver.model.HttpRequest;
import org.slf4j.event.Level;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static io.netty.handler.codec.http.HttpResponseStatus.CREATED;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.openMocks;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

/**
 * importDocument returns the upserted expectations from both reply shapes of {@code PUT /mockserver/import}: a plain
 * array, and the {@code {"expectations":[...],"warnings":[...]}} object the wiremock, mountebank and mockoon importers
 * reply with, logging any warnings. Mutates the global log level and log listener, so it relies on the client module's
 * sequential suite.
 */
public class MockServerClientImportDocumentTest {

    @Mock
    private NettyHttpClient mockHttpClient;
    @InjectMocks
    private MockServerClient mockServerClient;

    private final List<LogEntry> logged = new CopyOnWriteArrayList<>();
    private Level originalLogLevel;

    @Before
    public void setupTestFixture() {
        mockServerClient = new MockServerClient("localhost", 1090);
        openMocks(this);
        originalLogLevel = ConfigurationProperties.logLevel();
        ConfigurationProperties.logLevel("WARN");
        MockServerLogger.setGlobalLogEventListener(logEntry -> logged.add(logEntry.clone()));
    }

    @After
    public void restore() {
        MockServerLogger.setGlobalLogEventListener(null);
        ConfigurationProperties.logLevel(originalLogLevel != null ? originalLogLevel.name() : "OFF");
    }

    @Test
    public void shouldReturnTheExpectationsFromAReplyThatCarriesWarningsAndLogTheWarnings() {
        // given
        reply("{" +
            "\"expectations\":[{\"httpRequest\":{\"path\":\"/api/xml\"},\"httpResponse\":{\"statusCode\":200,\"body\":\"xml-ok\"}}]," +
            "\"warnings\":[{\"item\":\"GET /api/xml\",\"construct\":\"response.transformers\",\"detail\":\"response templating is not translated\"}]" +
            "}");

        // when
        Expectation[] result = mockServerClient.importDocument("{\"mappings\":[]}", "wiremock");

        // then
        assertThat(result.length, is(1));
        assertThat(result[0].getHttpRequest(), is(request().withPath("/api/xml")));
        assertThat(result[0].getHttpResponse(), is(response().withStatusCode(200).withBody("xml-ok")));
        assertThat(importWarnings(), hasSize(1));
        assertThat(importWarnings(), hasItem(allOf(
            containsString("wiremock"),
            containsString("GET /api/xml"),
            containsString("response.transformers"),
            containsString("response templating is not translated")
        )));
    }

    @Test
    public void shouldNotLogWhenTheReplyHasNoWarnings() {
        // given
        reply("{\"expectations\":[],\"warnings\":[]}");

        // when
        Expectation[] result = mockServerClient.importDocument("{\"routes\":[]}", "mockoon");

        // then
        assertThat(result.length, is(0));
        assertThat(importWarnings(), is(empty()));
    }

    @Test
    public void shouldReturnTheExpectationsFromAReplyThatIsAnArray() {
        // given
        reply("[{\"httpRequest\":{\"path\":\"/users\"},\"httpResponse\":{\"statusCode\":200}}]");

        // when
        Expectation[] result = mockServerClient.importHar("{\"log\":{\"entries\":[]}}");

        // then
        assertThat(result.length, is(1));
        assertThat(result[0].getHttpRequest(), is(request().withPath("/users")));
        assertThat(importWarnings(), is(empty()));
    }

    @Test
    public void shouldRejectTheRecordingFormatWithoutSendingARequest() {
        for (String format : new String[]{"recording", "Recording", " RECORDING "}) {
            // when
            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> mockServerClient.importDocument("{\"httpRequest\":{}}", format));

            // then
            assertThat(exception.getMessage(), containsString("PUT /mockserver/import?format=recording"));
        }
        verify(mockHttpClient, never()).sendRequest(any(HttpRequest.class), anyLong(), any(TimeUnit.class), anyBoolean());
    }

    @Test
    public void shouldThrowWithTheReplyWhenAnObjectReplyHasNoExpectationsArray() {
        // given - e.g. an error object from a gateway in front of MockServer
        reply("{\"error\":\"upstream unavailable\"}");

        // when
        ClientException exception = assertThrows(ClientException.class, () -> mockServerClient.importDocument("{\"mappings\":[]}", "wiremock"));

        // then
        assertThat(exception.getMessage(), containsString("{\"error\":\"upstream unavailable\"}"));
    }

    private void reply(String body) {
        when(mockHttpClient.sendRequest(any(HttpRequest.class), anyLong(), any(TimeUnit.class), anyBoolean()))
            .thenReturn(response().withStatusCode(CREATED.code()).withBody(body));
    }

    private List<String> importWarnings() {
        return logged.stream()
            .filter(logEntry -> logEntry.getLogLevel() == Level.WARN)
            .map(LogEntry::getMessage)
            .filter(message -> message.contains("import"))
            .collect(Collectors.toList());
    }
}
