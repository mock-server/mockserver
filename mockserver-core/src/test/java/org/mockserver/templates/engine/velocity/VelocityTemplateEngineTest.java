package org.mockserver.templates.engine.velocity;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.*;
import org.junit.rules.ExpectedException;
import org.mockito.Mock;
import org.mockserver.configuration.ConfigurationProperties;
import org.mockserver.log.TimeService;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.serialization.ObjectMapperFactory;
import org.mockserver.serialization.model.HttpRequestDTO;
import org.mockserver.serialization.model.HttpResponseDTO;
import org.mockserver.uuid.UUIDService;
import org.slf4j.event.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

import static io.netty.handler.codec.http.HttpHeaderNames.HOST;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.StringEndsWith.endsWith;
import static org.hamcrest.core.StringStartsWith.startsWith;
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.MockitoAnnotations.openMocks;
import static org.mockserver.character.Character.NEW_LINE;
import static org.mockserver.log.model.LogEntry.LogMessageType.TEMPLATE_GENERATED;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.slf4j.event.Level.INFO;

/**
 * @author jamesdbloom
 */
public class VelocityTemplateEngineTest {

    private static final ObjectMapper OBJECT_MAPPER = ObjectMapperFactory.createObjectMapper();
    private static boolean originalFixedTime;

    @Rule
    public final ExpectedException exception = ExpectedException.none();

    @Mock
    private MockServerLogger mockServerLogger;

    @BeforeClass
    public static void fixTime() {
        originalFixedTime = TimeService.fixedTime;
        TimeService.fixedTime = true;
    }

    @AfterClass
    public static void fixTimeReset() {
        TimeService.fixedTime = originalFixedTime;
    }

    @Before
    public void setupTestFixture() {
        openMocks(this);
    }

    private Level originalLogLevel;

    @Before
    public void setLogLevel() {
        originalLogLevel = ConfigurationProperties.logLevel();
        ConfigurationProperties.logLevel("INFO");
    }

    @After
    public void resetLogLevel() {
        ConfigurationProperties.logLevel(originalLogLevel.name());
    }

    @Test
    public void shouldHandleHttpRequestsWithVelocityResponseTemplateWithMethodPathAndHeader() throws JsonProcessingException {
        // given
        String template = "{" + NEW_LINE +
            "    'statusCode': 200," + NEW_LINE +
            "    'body': \"{'method': '$request.method', 'path': '$request.path', 'headers': '$request.headers.host[0]'}\"" + NEW_LINE +
            "}";
        HttpRequest request = request()
            .withPath("/somePath")
            .withMethod("POST")
            .withHeader(HOST.toString(), "mock-server.com")
            .withBody("some_body");

        // when
        HttpResponse actualHttpResponse = new VelocityTemplateEngine(mockServerLogger).executeTemplate(template, request, HttpResponseDTO.class);

        // then
        assertThat(actualHttpResponse, is(
            response()
                .withStatusCode(200)
                .withBody("{'method': 'POST', 'path': '/somePath', 'headers': 'mock-server.com'}")
        ));
        verify(mockServerLogger).logEvent(
            new LogEntry()
                .setType(TEMPLATE_GENERATED)
                .setLogLevel(INFO)
                .setHttpRequest(request)
                .setMessageFormat("generated output:{}from template:{}for request:{}")
                .setArguments(OBJECT_MAPPER.readTree("" +
                        "    {" + NEW_LINE +
                        "        'statusCode': 200," + NEW_LINE +
                        "        'body': \"{'method': 'POST', 'path': '/somePath', 'headers': 'mock-server.com'}\"" + NEW_LINE +
                        "    }" + NEW_LINE),
                    template,
                    request
                )
        );
    }

    @Test
    public void shouldHandleHttpRequestsWithVelocityResponseTemplateWithParametersCookiesAndBody() throws JsonProcessingException {
        // given
        String template = "{" + NEW_LINE +
            "    'statusCode': 200," + NEW_LINE +
            "    'body': \"{'queryStringParameters': '$request.queryStringParameters.nameOne[0],$request.queryStringParameters.nameTwo[0],$request.queryStringParameters.nameTwo[1]'," +
            " 'pathParameters': '$request.pathParameters.nameOne[0],$request.pathParameters.nameTwo[0],$request.pathParameters.nameTwo[1]'," +
            " 'cookies': '$request.cookies.session'," +
            " 'body': '$request.body'}\"" + NEW_LINE +
            "}";
        HttpRequest request = request()
            .withPath("/somePath")
            .withQueryStringParameter("nameOne", "queryValueOne")
            .withQueryStringParameter("nameTwo", "queryValueTwoOne", "queryValueTwoTwo")
            .withPathParameter("nameOne", "pathValueOne")
            .withPathParameter("nameTwo", "pathValueTwoOne", "pathValueTwoTwo")
            .withMethod("POST")
            .withCookie("session", "some_session_id")
            .withBody("some_body");

        // when
        HttpResponse actualHttpResponse = new VelocityTemplateEngine(mockServerLogger).executeTemplate(template, request, HttpResponseDTO.class);

        // then
        assertThat(actualHttpResponse, is(
            response()
                .withStatusCode(200)
                .withBody("{'queryStringParameters': 'queryValueOne,queryValueTwoOne,queryValueTwoTwo', 'pathParameters': 'pathValueOne,pathValueTwoOne,pathValueTwoTwo', 'cookies': 'some_session_id', 'body': 'some_body'}")
        ));
        verify(mockServerLogger).logEvent(
            new LogEntry()
                .setType(TEMPLATE_GENERATED)
                .setLogLevel(INFO)
                .setHttpRequest(request)
                .setMessageFormat("generated output:{}from template:{}for request:{}")
                .setArguments(OBJECT_MAPPER.readTree("" +
                        "    {" + NEW_LINE +
                        "        'statusCode': 200," + NEW_LINE +
                        "        'body': \"{'queryStringParameters': 'queryValueOne,queryValueTwoOne,queryValueTwoTwo', 'pathParameters': 'pathValueOne,pathValueTwoOne,pathValueTwoTwo', 'cookies': 'some_session_id', 'body': 'some_body'}\"" + NEW_LINE +
                        "    }" + NEW_LINE),
                    template,
                    request
                )
        );
    }

    @Test
    public void shouldHandleHttpRequestsWithVelocityResponseTemplateWithDynamicValuesDateAndUUID() throws InterruptedException {
        boolean originalFixedUUID = UUIDService.fixedUUID;
        try {
            // given
            UUIDService.fixedUUID = true;
            String template = "{" + NEW_LINE +
                "    'statusCode': 200," + NEW_LINE +
                "    'body': \"{'date': '$now', 'date_epoch': '$now_epoch', 'date_iso-8601': '$now_iso_8601', 'date_rfc_1123': '$now_rfc_1123', 'uuids': ['$uuid', '$uuid'] }\"" + NEW_LINE +
                "}";
            HttpRequest request = request()
                .withPath("/somePath")
                .withQueryStringParameter("nameOne", "valueOne")
                .withQueryStringParameter("nameTwo", "valueTwoOne", "valueTwoTwo")
                .withMethod("POST")
                .withCookie("session", "some_session_id")
                .withBody("some_body");

            // when
            HttpResponse firstActualHttpResponse = new VelocityTemplateEngine(mockServerLogger).executeTemplate(template, request, HttpResponseDTO.class);

            // then
            assertThat(firstActualHttpResponse.getBodyAsString(), allOf(startsWith("{'date': '20"), endsWith("', 'uuids': ['" + UUIDService.getUUID() + "', '" + UUIDService.getUUID() + "'] }")));

            // given
            TimeUnit.SECONDS.sleep(1);

            // when
            HttpResponse secondActualHttpResponse = new VelocityTemplateEngine(mockServerLogger).executeTemplate(template, request, HttpResponseDTO.class);

            // then
            assertThat(secondActualHttpResponse.getBodyAsString(), allOf(startsWith("{'date': '20"), endsWith("', 'uuids': ['" + UUIDService.getUUID() + "', '" + UUIDService.getUUID() + "'] }")));
            // date should now be different
            assertThat(secondActualHttpResponse.getBodyAsString(), not(is(firstActualHttpResponse.getBodyAsString())));

        } finally {
            UUIDService.fixedUUID = originalFixedUUID;
        }
    }

    @Test
    public void shouldHandleHttpRequestsWithVelocityResponseTemplateWithDynamicValuesRandom() throws InterruptedException {
        // given
        String template = "{" + NEW_LINE +
            "    'statusCode': 200," + NEW_LINE +
            "    'body': \"{'rand_int': '$rand_int', 'rand_int_10': '$rand_int_10', 'rand_int_100': '$rand_int_100', 'rand_bytes': ['$rand_bytes','$rand_bytes_16','$rand_bytes_32','$rand_bytes_64','$rand_bytes_128'], 'end': 'end' }\"" + NEW_LINE +
            "}";
        HttpRequest request = request()
            .withPath("/somePath")
            .withQueryStringParameter("nameOne", "valueOne")
            .withQueryStringParameter("nameTwo", "valueTwoOne", "valueTwoTwo")
            .withMethod("POST")
            .withCookie("session", "some_session_id")
            .withBody("some_body");

        // when
        HttpResponse firstActualHttpResponse = new VelocityTemplateEngine(mockServerLogger).executeTemplate(template, request, HttpResponseDTO.class);

        // then
        assertThat(firstActualHttpResponse.getBodyAsString(), allOf(startsWith("{'rand_int': '"), endsWith("'], 'end': 'end' }")));

        // given
        TimeUnit.SECONDS.sleep(1);

        // when
        HttpResponse secondActualHttpResponse = new VelocityTemplateEngine(mockServerLogger).executeTemplate(template, request, HttpResponseDTO.class);

        // then
        assertThat(firstActualHttpResponse.getBodyAsString(), allOf(startsWith("{'rand_int': '"), endsWith("'], 'end': 'end' }")));
        // should now be different
        assertThat(secondActualHttpResponse.getBodyAsString(), not(is(firstActualHttpResponse.getBodyAsString())));
    }

    @Test
    public void shouldHandleHttpRequestsWithVelocityResponseTemplateWithIfElse() throws JsonProcessingException {
        // given
        String template = "#if ( $request.method == 'POST' && $request.path == '/somePath' )" + NEW_LINE +
            "    {" + NEW_LINE +
            "        'statusCode': 200," + NEW_LINE +
            "        'body': \"{'name': 'value'}\"" + NEW_LINE +
            "    }" + NEW_LINE +
            "#else" + NEW_LINE +
            "    {" + NEW_LINE +
            "        'statusCode': 406," + NEW_LINE +
            "        'body': \"$!request.body\"" + NEW_LINE +
            "    }" + NEW_LINE +
            "#end";
        HttpRequest request = request()
            .withPath("/somePath")
            .withMethod("POST")
            .withBody("some_body");

        // when
        HttpResponse actualHttpResponse = new VelocityTemplateEngine(mockServerLogger).executeTemplate(template, request, HttpResponseDTO.class);

        // then
        assertThat(actualHttpResponse, is(
            response()
                .withStatusCode(200)
                .withBody("{'name': 'value'}")
        ));
        verify(mockServerLogger).logEvent(
            new LogEntry()
                .setType(TEMPLATE_GENERATED)
                .setLogLevel(INFO)
                .setHttpRequest(request)
                .setMessageFormat("generated output:{}from template:{}for request:{}")
                .setArguments(OBJECT_MAPPER.readTree("" +
                        "    {" + NEW_LINE +
                        "        'statusCode': 200," + NEW_LINE +
                        "        'body': \"{'name': 'value'}\"" + NEW_LINE +
                        "    }" + NEW_LINE),
                    template,
                    request
                )
        );
    }

    @Test
    public void shouldHandleHttpRequestsWithVelocityForwardTemplateWithPathBodyParametersAndCookies() throws JsonProcessingException {
        // given
        String template = "{" + NEW_LINE +
            "    'path': '$request.path'," + NEW_LINE +
            "    'body': \"{'queryStringParameters': '$request.queryStringParameters.nameOne[0],$request.queryStringParameters.nameTwo[0],$request.queryStringParameters.nameTwo[1]'," +
            " 'pathParameters': '$request.pathParameters.nameOne[0],$request.pathParameters.nameTwo[0],$request.pathParameters.nameTwo[1]'," +
            " 'cookies': '$request.cookies.session'," +
            " 'body': '$request.body'}\"" + NEW_LINE +
            "}";
        HttpRequest request = request()
            .withPath("/somePath")
            .withQueryStringParameter("nameOne", "queryValueOne")
            .withQueryStringParameter("nameTwo", "queryValueTwoOne", "queryValueTwoTwo")
            .withPathParameter("nameOne", "pathValueOne")
            .withPathParameter("nameTwo", "pathValueTwoOne", "pathValueTwoTwo")
            .withMethod("POST")
            .withCookie("session", "some_session_id")
            .withBody("some_body");

        // when
        HttpRequest actualHttpRequest = new VelocityTemplateEngine(mockServerLogger).executeTemplate(template, request, HttpRequestDTO.class);

        // then
        assertThat(actualHttpRequest, is(
            request()
                .withPath("/somePath")
                .withBody("{'queryStringParameters': 'queryValueOne,queryValueTwoOne,queryValueTwoTwo', 'pathParameters': 'pathValueOne,pathValueTwoOne,pathValueTwoTwo', 'cookies': 'some_session_id', 'body': 'some_body'}")
        ));
        verify(mockServerLogger).logEvent(
            new LogEntry()
                .setType(TEMPLATE_GENERATED)
                .setLogLevel(INFO)
                .setHttpRequest(request)
                .setMessageFormat("generated output:{}from template:{}for request:{}")
                .setArguments(OBJECT_MAPPER.readTree("" +
                        "{" + NEW_LINE +
                        "    'path' : \"/somePath\"," + NEW_LINE +
                        "    'body': \"{'queryStringParameters': 'queryValueOne,queryValueTwoOne,queryValueTwoTwo', 'pathParameters': 'pathValueOne,pathValueTwoOne,pathValueTwoTwo', 'cookies': 'some_session_id', 'body': 'some_body'}\"" + NEW_LINE +
                        "}" + NEW_LINE),
                    template,
                    request
                )
        );
    }

    @Test
    public void shouldHandleInvalidVelocityTemplate() {
        // given
        String template = "#if {" + NEW_LINE +
            "    'path' : \"/somePath\"," + NEW_LINE +
            "    'queryStringParameters' : [ {" + NEW_LINE +
            "        'name' : \"queryParameter\"," + NEW_LINE +
            "        'values' : [ \"$!request.queryStringParameters['queryParameter'][0]\" ]" + NEW_LINE +
            "    } ]," + NEW_LINE +
            "    'headers' : [ {" + NEW_LINE +
            "        'name' : \"Host\"," + NEW_LINE +
            "        'values' : [ \"localhost:1090\" ]" + NEW_LINE +
            "    } ]," + NEW_LINE +
            "    'body': \"{'name': 'value'}\"" + NEW_LINE +
            "}";

        // when
        RuntimeException runtimeException = assertThrows(RuntimeException.class, () -> new VelocityTemplateEngine(mockServerLogger).executeTemplate(template, request()
                .withPath("/someOtherPath")
                .withQueryStringParameter("queryParameter", "someValue")
                .withBody("some_body"),
            HttpRequestDTO.class
        ));

        // then
        assertThat(runtimeException.getMessage(), is("Exception:" + NEW_LINE +
            "" + NEW_LINE +
            "  org.apache.velocity.exception.ParseErrorException: Encountered \"{\" at <unknown>[line 1, column 5]" + NEW_LINE +
            "  Was expecting one of:" + NEW_LINE +
            "      \"(\" ..." + NEW_LINE +
            "      <WHITESPACE> ..." + NEW_LINE +
            "      <NEWLINE> ..." + NEW_LINE +
            "      " + NEW_LINE +
            "" + NEW_LINE +
            " transforming template:" + NEW_LINE +
            "" + NEW_LINE +
            "  #if {" + NEW_LINE +
            "      'path' : \"/somePath\"," + NEW_LINE +
            "      'queryStringParameters' : [ {" + NEW_LINE +
            "          'name' : \"queryParameter\"," + NEW_LINE +
            "          'values' : [ \"$!request.queryStringParameters['queryParameter'][0]\" ]" + NEW_LINE +
            "      } ]," + NEW_LINE +
            "      'headers' : [ {" + NEW_LINE +
            "          'name' : \"Host\"," + NEW_LINE +
            "          'values' : [ \"localhost:1090\" ]" + NEW_LINE +
            "      } ]," + NEW_LINE +
            "      'body': \"{'name': 'value'}\"" + NEW_LINE +
            "  }" + NEW_LINE +
            "" + NEW_LINE +
            " for request:" + NEW_LINE +
            "" + NEW_LINE +
            "  {" + NEW_LINE +
            "    \"path\" : \"/someOtherPath\"," + NEW_LINE +
            "    \"queryStringParameters\" : {" + NEW_LINE +
            "      \"queryParameter\" : [ \"someValue\" ]" + NEW_LINE +
            "    }," + NEW_LINE +
            "    \"body\" : \"some_body\"" + NEW_LINE +
            "  }" + NEW_LINE));
    }

    @Test
    public void shouldHandleMultipleHttpRequestsWithVelocityResponseTemplateInParallel()
        throws InterruptedException, ExecutionException {
        // given
        String template = "#if ( $request.method == 'POST' && $request.path == '/somePath' )" + NEW_LINE +
            "    {" + NEW_LINE +
            "        'statusCode': 200," + NEW_LINE +
            "        'body': \"{'name': 'value'}\"" + NEW_LINE +
            "    }" + NEW_LINE +
            "#else" + NEW_LINE +
            "    {" + NEW_LINE +
            "        'statusCode': 406," + NEW_LINE +
            "        'body': \"$!request.body\"" + NEW_LINE +
            "    }" + NEW_LINE +
            "#end";

        HttpRequest request = request()
            .withPath("/somePath")
            .withMethod("POST")
            .withBody("some_body");

        // when
        VelocityTemplateEngine velocityTemplateEngine = new VelocityTemplateEngine(mockServerLogger);

        ExecutorService newFixedThreadPool = Executors.newFixedThreadPool(30);

        List<Future<Boolean>> futures = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            futures.add(newFixedThreadPool.submit(() -> {
                assertThat(velocityTemplateEngine.executeTemplate(template, request, HttpResponseDTO.class), is(
                    response()
                        .withStatusCode(200)
                        .withBody("{'name': 'value'}")
                ));
                return true;
            }));

        }

        for (Future<Boolean> future : futures) {
            future.get();
        }
        newFixedThreadPool.shutdown();
    }

}
