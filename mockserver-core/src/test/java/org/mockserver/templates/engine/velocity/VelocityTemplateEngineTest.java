package org.mockserver.templates.engine.velocity;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.hamcrest.Matcher;
import org.junit.*;
import org.junit.rules.ExpectedException;
import org.mockito.Mock;
import org.mockserver.configuration.Configuration;
import org.mockserver.configuration.ConfigurationProperties;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.serialization.ObjectMapperFactory;
import org.mockserver.serialization.model.HttpRequestDTO;
import org.mockserver.serialization.model.HttpResponseDTO;
import org.mockserver.time.EpochService;
import org.mockserver.time.TimeService;
import org.mockserver.uuid.UUIDService;
import org.slf4j.event.Level;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.io.File;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_TYPE;
import static io.netty.handler.codec.http.HttpHeaderNames.HOST;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.MockitoAnnotations.openMocks;
import static org.mockserver.character.Character.NEW_LINE;
import static org.mockserver.configuration.Configuration.configuration;
import static org.mockserver.log.model.LogEntry.LogMessageType.TEMPLATE_GENERATED;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.JsonBody.json;
import static org.slf4j.event.Level.INFO;

/**
 * @author jamesdbloom
 */
public class VelocityTemplateEngineTest {

    private static final ObjectMapper OBJECT_MAPPER = ObjectMapperFactory.createObjectMapper();
    private static boolean originalFixedTime;
    private static final Configuration configuration = configuration();

    @Rule
    public final ExpectedException exception = ExpectedException.none();

    @Mock
    private MockServerLogger mockServerLogger;

    @BeforeClass
    public static void fixTime() {
        originalFixedTime = EpochService.fixedTime;
        EpochService.fixedTime = true;
    }

    @AfterClass
    public static void fixTimeReset() {
        EpochService.fixedTime = originalFixedTime;
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
        HttpResponse actualHttpResponse = new VelocityTemplateEngine(mockServerLogger, configuration).executeTemplate(template, request, HttpResponseDTO.class);

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
                        "{" + NEW_LINE +
                        "    'statusCode': 200," + NEW_LINE +
                        "    'body': \"{'method': 'POST', 'path': '/somePath', 'headers': 'mock-server.com'}\"" + NEW_LINE +
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
        HttpResponse actualHttpResponse = new VelocityTemplateEngine(mockServerLogger, configuration).executeTemplate(template, request, HttpResponseDTO.class);

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
                        "{" + NEW_LINE +
                        "    'statusCode': 200," + NEW_LINE +
                        "    'body': \"{'queryStringParameters': 'queryValueOne,queryValueTwoOne,queryValueTwoTwo', 'pathParameters': 'pathValueOne,pathValueTwoOne,pathValueTwoTwo', 'cookies': 'some_session_id', 'body': 'some_body'}\"" + NEW_LINE +
                        "}" + NEW_LINE),
                    template,
                    request
                )
        );
    }

    @Test
    public void shouldHandleHttpRequestsWithVelocityResponseTemplateWithDynamicValuesDateAndUUID() throws JsonProcessingException {
        boolean originalFixedUUID = UUIDService.fixedUUID;
        boolean originalFixedTime = TimeService.fixedTime;
        try {
            // given
            UUIDService.fixedUUID = true;
            TimeService.fixedTime = true;
            String template = "{" + NEW_LINE +
                "    'statusCode': 200," + NEW_LINE +
                "    'body': \"{'date': '$now', 'date_epoch': '$now_epoch', 'date_iso_8601': '$now_iso_8601', 'date_rfc_1123': '$now_rfc_1123', 'uuids': ['$uuid', '$uuid'] }\"" + NEW_LINE +
                "}";
            HttpRequest request = request()
                .withPath("/somePath")
                .withQueryStringParameter("nameOne", "valueOne")
                .withQueryStringParameter("nameTwo", "valueTwoOne", "valueTwoTwo")
                .withMethod("POST")
                .withCookie("session", "some_session_id")
                .withBody("some_body");

            // when
            HttpResponse actualHttpResponse = new VelocityTemplateEngine(mockServerLogger, configuration).executeTemplate(template, request, HttpResponseDTO.class);

            // then
            assertThat(actualHttpResponse, is(
                response()
                    .withStatusCode(200)
                    .withBody("{'date': '" + TimeService.now() + "', 'date_epoch': '" + TimeService.now().getEpochSecond() + "', 'date_iso_8601': '" + DateTimeFormatter.ISO_INSTANT.format(TimeService.now()) + "', 'date_rfc_1123': '" + DateTimeFormatter.RFC_1123_DATE_TIME.format(TimeService.offsetNow()) + "', 'uuids': ['" + UUIDService.getUUID() + "', '" + UUIDService.getUUID() + "'] }")
            ));
            verify(mockServerLogger).logEvent(
                new LogEntry()
                    .setType(TEMPLATE_GENERATED)
                    .setLogLevel(INFO)
                    .setHttpRequest(request)
                    .setMessageFormat("generated output:{}from template:{}for request:{}")
                    .setArguments(OBJECT_MAPPER.readTree("" +
                            "{" + NEW_LINE +
                            "    'statusCode': 200," + NEW_LINE +
                            "    'body': \"{'date': '" + TimeService.now() + "', 'date_epoch': '" + TimeService.now().getEpochSecond() + "', 'date_iso_8601': '" + DateTimeFormatter.ISO_INSTANT.format(TimeService.now()) + "', 'date_rfc_1123': '" + DateTimeFormatter.RFC_1123_DATE_TIME.format(TimeService.offsetNow()) + "', 'uuids': ['" + UUIDService.getUUID() + "', '" + UUIDService.getUUID() + "'] }\"" + NEW_LINE +
                            "}" + NEW_LINE),
                        template,
                        request
                    )
            );

        } finally {
            UUIDService.fixedUUID = originalFixedUUID;
            TimeService.fixedTime = originalFixedTime;
        }
    }

    @Test
    public void shouldHandleHttpRequestsWithVelocityResponseTemplateWithDynamicValuesRandom() {
        shouldPopulateRandomValue("$rand_int", equalTo(1));
        shouldPopulateRandomValue("$rand_int_10", allOf(greaterThan(0), lessThan(3)));
        shouldPopulateRandomValue("$rand_int_100", allOf(greaterThan(0), lessThan(4)));
        shouldPopulateRandomValue("$rand_bytes", allOf(greaterThan(20), lessThan(50)));
        shouldPopulateRandomValue("$rand_bytes_16", allOf(greaterThan(20), lessThan(50)));
        shouldPopulateRandomValue("$rand_bytes_32", allOf(greaterThan(40), lessThan(60)));
        shouldPopulateRandomValue("$rand_bytes_64", allOf(greaterThan(80), lessThan(120)));
        shouldPopulateRandomValue("$rand_bytes_128", allOf(greaterThan(160), lessThan(300)));
    }

    private void shouldPopulateRandomValue(String function, Matcher<Integer> matcher) {
        // given
        String template = "{ 'body': '" + function + "' }";
        HttpRequest request = request()
            .withPath("/somePath")
            .withBody("some_body");

        // when
        HttpResponse actualHttpResponse = new VelocityTemplateEngine(mockServerLogger, configuration).executeTemplate(template, request, HttpResponseDTO.class);

        // then
        assertThat(actualHttpResponse.getBodyAsString(), not(equalTo("")));
        assertThat(actualHttpResponse.getBodyAsString().length(), matcher);
    }

    @Test
    public void shouldHandleHttpRequestsWithVelocityResponseTemplateWithLoopOverValues() throws JsonProcessingException {
        // given
        String template = "{" + NEW_LINE +
            "    'statusCode': 200," + NEW_LINE +
            "    'body': \"{'headers': [#foreach( $value in $request.headers.values() )'$value[0]'#if( $foreach.hasNext ), #end#end]}\"" + NEW_LINE +
            "}";


        HttpRequest request = request()
            .withPath("/somePath")
            .withMethod("POST")
            .withHeader(HOST.toString(), "mock-server.com")
            .withHeader(CONTENT_TYPE.toString(), "plain/text")
            .withBody("some_body");

        // when
        HttpResponse actualHttpResponse = new VelocityTemplateEngine(mockServerLogger, configuration).executeTemplate(template, request, HttpResponseDTO.class);

        // then
        assertThat(actualHttpResponse, is(
            response()
                .withStatusCode(200)
                .withBody("{'headers': ['mock-server.com', 'plain/text']}")
        ));
        verify(mockServerLogger).logEvent(
            new LogEntry()
                .setType(TEMPLATE_GENERATED)
                .setLogLevel(INFO)
                .setHttpRequest(request)
                .setMessageFormat("generated output:{}from template:{}for request:{}")
                .setArguments(OBJECT_MAPPER.readTree("" +
                        "{" + NEW_LINE +
                        "    'statusCode': 200," + NEW_LINE +
                        "    'body': \"{'headers': ['mock-server.com', 'plain/text']}\"" + NEW_LINE +
                        "}" + NEW_LINE),
                    template,
                    request
                )
        );
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
        HttpResponse actualHttpResponse = new VelocityTemplateEngine(mockServerLogger, configuration).executeTemplate(template, request, HttpResponseDTO.class);

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
                        "{" + NEW_LINE +
                        "    'statusCode': 200," + NEW_LINE +
                        "    'body': \"{'name': 'value'}\"" + NEW_LINE +
                        "}" + NEW_LINE),
                    template,
                    request
                )
        );
    }

    @Test
    public void shouldHandleHttpRequestsWithVelocityResponseTemplateWithXPath() throws JsonProcessingException {
        // given
        String template = "#set($xmlBody = $xml.parse($!request.body))" + NEW_LINE +
            "{" + NEW_LINE +
            "    'statusCode': 200," + NEW_LINE +
            "    'body': \"{'key': '$xml.find('/element/key/text()')', 'value': '$xml.find('/element/value/text()')'}\"" + NEW_LINE +
            "}";
        HttpRequest request = request()
            .withPath("/somePath")
            .withBody("<element><key>some_key</key><value>some_value</value></element>");

        // when
        HttpResponse actualHttpResponse = new VelocityTemplateEngine(mockServerLogger, configuration).executeTemplate(template, request, HttpResponseDTO.class);

        // then
        assertThat(actualHttpResponse, is(
            response()
                .withStatusCode(200)
                .withBody("{'key': 'some_key', 'value': 'some_value'}")
        ));
        verify(mockServerLogger).logEvent(
            new LogEntry()
                .setType(TEMPLATE_GENERATED)
                .setLogLevel(INFO)
                .setHttpRequest(request)
                .setMessageFormat("generated output:{}from template:{}for request:{}")
                .setArguments(OBJECT_MAPPER.readTree("" +
                        "{" + NEW_LINE +
                        "    'statusCode': 200," + NEW_LINE +
                        "    'body': \"{'key': 'some_key', 'value': 'some_value'}\"" + NEW_LINE +
                        "}" + NEW_LINE),
                    template,
                    request
                )
        );
    }

    @Test
    public void shouldHandleHttpRequestsWithVelocityResponseTemplateWithJsonParsing() throws JsonProcessingException {
        // given
        String template = "#set($jsonBody = $json.parse($!request.body))" + NEW_LINE +
            "{" + NEW_LINE +
            "    'statusCode': 200," + NEW_LINE +
            "    'body': \"{'titles': [#foreach( $book in $jsonBody.store.book )'$book.title'#if( $foreach.hasNext ), #end#end], 'bikeColor': '$jsonBody.store.bicycle.color'}\"" + NEW_LINE +
            "}";
        HttpRequest request = request()
            .withPath("/somePath")
            .withBody(json("{" + NEW_LINE +
                "    \"store\": {" + NEW_LINE +
                "        \"book\": [" + NEW_LINE +
                "            {" + NEW_LINE +
                "                \"category\": \"reference\"," + NEW_LINE +
                "                \"author\": \"Nigel Rees\"," + NEW_LINE +
                "                \"title\": \"Sayings of the Century\"," + NEW_LINE +
                "                \"price\": 18.95" + NEW_LINE +
                "            }," + NEW_LINE +
                "            {" + NEW_LINE +
                "                \"category\": \"fiction\"," + NEW_LINE +
                "                \"author\": \"Herman Melville\"," + NEW_LINE +
                "                \"title\": \"Moby Dick\"," + NEW_LINE +
                "                \"isbn\": \"0-553-21311-3\"," + NEW_LINE +
                "                \"price\": 8.99" + NEW_LINE +
                "            }" + NEW_LINE +
                "        ]," + NEW_LINE +
                "        \"bicycle\": {" + NEW_LINE +
                "            \"color\": \"red\"," + NEW_LINE +
                "            \"price\": 19.95" + NEW_LINE +
                "        }" + NEW_LINE +
                "    }," + NEW_LINE +
                "    \"expensive\": 10" + NEW_LINE +
                "}"));

        // when
        HttpResponse actualHttpResponse = new VelocityTemplateEngine(mockServerLogger, configuration).executeTemplate(template, request, HttpResponseDTO.class);

        // then
        assertThat(actualHttpResponse, is(
            response()
                .withStatusCode(200)
                .withBody("{'titles': ['Sayings of the Century', 'Moby Dick'], 'bikeColor': 'red'}")
        ));
        verify(mockServerLogger).logEvent(
            new LogEntry()
                .setType(TEMPLATE_GENERATED)
                .setLogLevel(INFO)
                .setHttpRequest(request)
                .setMessageFormat("generated output:{}from template:{}for request:{}")
                .setArguments(OBJECT_MAPPER.readTree("" +
                        "{" + NEW_LINE +
                        "    'statusCode': 200," + NEW_LINE +
                        "    'body': \"{'titles': ['Sayings of the Century', 'Moby Dick'], 'bikeColor': 'red'}\"" + NEW_LINE +
                        "}" + NEW_LINE),
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
        HttpRequest actualHttpRequest = new VelocityTemplateEngine(mockServerLogger, configuration).executeTemplate(template, request, HttpRequestDTO.class);

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
    public void shouldHandleHttpRequestsWithVelocityTemplateWithDisallowClassLoading() {
        Boolean originalVelocityDenyClasses = configuration.velocityDisallowClassLoading();

        try {
            // given
            String template = "{" + NEW_LINE +
                "    'statusCode': 200," + NEW_LINE +
                "    'body': \"$!request.class.classLoader.loadClass('java.lang.Runtime').getRuntime().exec(\"does_not_exist.sh\")\"" + NEW_LINE +
                "}";
            HttpRequest request = request()
                .withPath("/somePath")
                .withMethod("POST")
                .withHeader(HOST.toString(), "mock-server.com")
                .withBody("some_body".getBytes(StandardCharsets.UTF_8));

            // then
            Exception exception = assertThrows(RuntimeException.class, () -> new VelocityTemplateEngine(mockServerLogger, configuration).executeTemplate(template, request, HttpResponseDTO.class));
            assertThat(exception.getMessage(), containsString("Cannot run program \"does_not_exist.sh\""));

            // when
            configuration.velocityDisallowClassLoading(true);

            // then - should skip execution of line and not thrown error
            HttpResponse actualHttpResponse = new VelocityTemplateEngine(mockServerLogger, configuration).executeTemplate(template, request, HttpResponseDTO.class);
            assertThat(actualHttpResponse, is(
                response()
                    .withStatusCode(200)
                    .withBody("")
            ));
        } finally {
            configuration.velocityDisallowClassLoading(originalVelocityDenyClasses);
        }
    }

    @Test
    public void shouldHandleHttpRequestsWithVelocityTemplateWithDisallowedText() {
        String originalVelocityDisallowedText = configuration.velocityDisallowedText();

        try {
            // given
            String template = "{" + NEW_LINE +
                "    'statusCode': 200," + NEW_LINE +
                "    'body': \"$!request.class.classLoader.loadClass('java.lang.Runtime').getRuntime().exec(\"does_not_exist.sh\")\"" + NEW_LINE +
                "}";
            HttpRequest request = request()
                .withPath("/somePath")
                .withMethod("POST")
                .withHeader(HOST.toString(), "mock-server.com")
                .withBody("some_body".getBytes(StandardCharsets.UTF_8));

            // then
            Exception exception = assertThrows(RuntimeException.class, () -> new VelocityTemplateEngine(mockServerLogger, configuration).executeTemplate(template, request, HttpResponseDTO.class));
            assertThat(exception.getMessage(), containsString("Cannot run program \"does_not_exist.sh\""));

            // when
            configuration.velocityDisallowedText("request.class");

            // then
            exception = assertThrows(RuntimeException.class, () -> new VelocityTemplateEngine(mockServerLogger, configuration).executeTemplate(template, request, HttpResponseDTO.class));
            assertThat(exception.getMessage(), containsString("Found disallowed string \"request.class\" in template:"));
        } finally {
            configuration.velocityDisallowedText(originalVelocityDisallowedText);
        }
    }

    @Test
    public void shouldHandleHttpRequestsWithVelocityTemplateWithDisallowedTextList() {
        String originalVelocityDisallowedText = configuration.velocityDisallowedText();

        try {
            // given
            String template = "{" + NEW_LINE +
                "    'statusCode': 200," + NEW_LINE +
                "    'body': \"$!request.class.classLoader.loadClass('java.lang.Runtime').getRuntime().exec(\"does_not_exist.sh\")\"" + NEW_LINE +
                "}";
            HttpRequest request = request()
                .withPath("/somePath")
                .withMethod("POST")
                .withHeader(HOST.toString(), "mock-server.com")
                .withBody("some_body".getBytes(StandardCharsets.UTF_8));
            configuration.velocityDisallowedText("request.class,classLoader.loadClass");

            // then
            Exception exception = assertThrows(RuntimeException.class, () -> new VelocityTemplateEngine(mockServerLogger, configuration).executeTemplate(template, request, HttpResponseDTO.class));
            assertThat(exception.getMessage(), containsString("Found disallowed string \"request.class\" in template:"));

            // when
            configuration.velocityDisallowedText("classLoader.loadClass,request.class");

            // then
            exception = assertThrows(RuntimeException.class, () -> new VelocityTemplateEngine(mockServerLogger, configuration).executeTemplate(template, request, HttpResponseDTO.class));
            assertThat(exception.getMessage(), containsString("Found disallowed string \"classLoader.loadClass\" in template:"));
        } finally {
            configuration.velocityDisallowedText(originalVelocityDisallowedText);
        }
    }

    @Test
    public void shouldHandleHttpRequestsWithVelocityTemplateWithImportTool() {
        File testInputFile = new File("testInputFile.txt");
        String testInputFileContent = "This file content will be part of the response body";
        try {
            // given
            String template = "{" + NEW_LINE +
                "    'statusCode': 200," + NEW_LINE +
                "    'body': \"$import.read('testInputFile.txt')\"" + NEW_LINE +
                "}";
            HttpRequest request = request()
                .withPath("/somePath")
                .withMethod("POST")
                .withHeader(HOST.toString(), "mock-server.com")
                .withBody("some_body".getBytes(StandardCharsets.UTF_8));

            try {
                testInputFile = new File("testInputFile.txt");
                testInputFile.delete();
                testInputFile.createNewFile();
                Files.write(Paths.get(testInputFile.getAbsolutePath()), testInputFileContent.getBytes());
            } catch (Throwable t) {
                t.printStackTrace();
            }

            // when
            HttpResponse actualHttpResponse = new VelocityTemplateEngine(mockServerLogger, configuration).executeTemplate(template, request, HttpResponseDTO.class);

            // then
            assertThat(actualHttpResponse, is(
                response()
                    .withStatusCode(200)
                    .withBody(testInputFileContent)
            ));
        } finally {
            if (testInputFile != null) {
                testInputFile.delete();
            }
        }
    }

    @Test
    public void shouldHandleHttpRequestsWithVelocityTemplateWithMathTool() {
        // given
        String template = "{" + NEW_LINE +
            "    'statusCode': 200," + NEW_LINE +
            "    'body': \"$math.sub('5', '3')\"" + NEW_LINE +
            "}";
        HttpRequest request = request()
            .withPath("/somePath")
            .withMethod("POST")
            .withHeader(HOST.toString(), "mock-server.com")
            .withBody("some_body".getBytes(StandardCharsets.UTF_8));

        // when
        HttpResponse actualHttpResponse = new VelocityTemplateEngine(mockServerLogger, configuration).executeTemplate(template, request, HttpResponseDTO.class);

        // then
        assertThat(actualHttpResponse, is(
            response()
                .withStatusCode(200)
                .withBody("2")
        ));
    }

    @Test
    public void shouldHandleHttpRequestsWithVelocityTemplateWithDateTool() {
        // given
        String template = "{" + NEW_LINE +
            "    'statusCode': 200," + NEW_LINE +
            "    'body': \"$date\"" + NEW_LINE +
            "}";
        HttpRequest request = request()
            .withPath("/somePath")
            .withMethod("POST")
            .withHeader(HOST.toString(), "mock-server.com")
            .withBody("some_body".getBytes(StandardCharsets.UTF_8));


        // when
        HttpResponse actualHttpResponse = new VelocityTemplateEngine(mockServerLogger, configuration).executeTemplate(template, request, HttpResponseDTO.class);

        // then
        assertThat(actualHttpResponse, is(
            response()
                .withStatusCode(200)
                .withBody(new java.text.SimpleDateFormat("dd MMM yyyy, HH:mm:ss").format(new java.util.Date()))
        ));
    }

    @Test
    public void shouldHandleHttpRequestsWithVelocityResponseTemplateInvalidFields() throws JsonProcessingException {
        // given
        String template = "{" + NEW_LINE +
            "    'statusCode': 200," + NEW_LINE +
            "    'body': \"{'method': '$!request.method.invalid', 'path': '$request.invalid', 'headers': '$!request.headers.host[0]'}\"" + NEW_LINE +
            "}";
        HttpRequest request = request()
            .withPath("/somePath")
            .withMethod("POST")
            .withHeader(HOST.toString(), "mock-server.com")
            .withBody("some_body".getBytes(StandardCharsets.UTF_8));

        // when
        HttpResponse actualHttpResponse = new VelocityTemplateEngine(mockServerLogger, configuration).executeTemplate(template, request, HttpResponseDTO.class);

        // then
        assertThat(actualHttpResponse, is(
            response()
                .withStatusCode(200)
                .withBody("{'method': '', 'path': '$request.invalid', 'headers': 'mock-server.com'}")
        ));
        verify(mockServerLogger).logEvent(
            new LogEntry()
                .setType(TEMPLATE_GENERATED)
                .setLogLevel(INFO)
                .setHttpRequest(request)
                .setMessageFormat("generated output:{}from template:{}for request:{}")
                .setArguments(OBJECT_MAPPER.readTree("" +
                        "{" + NEW_LINE +
                        "    'statusCode': 200," + NEW_LINE +
                        "    'body': \"{'method': '', 'path': '$request.invalid', 'headers': 'mock-server.com'}\"" + NEW_LINE +
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
        RuntimeException runtimeException = assertThrows(RuntimeException.class, () -> new VelocityTemplateEngine(mockServerLogger, configuration).executeTemplate(template, request()
                .withPath("/someOtherPath")
                .withQueryStringParameter("queryParameter", "someValue")
                .withBody("some_body"),
            HttpRequestDTO.class
        ));

        // then
        assertThat(runtimeException.getMessage(), is("Exception:" + NEW_LINE +
            "" + NEW_LINE +
            "  Encountered \"{\" at VelocityResponseTemplate[line 1, column 5]" + NEW_LINE +
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
        VelocityTemplateEngine velocityTemplateEngine = new VelocityTemplateEngine(mockServerLogger, configuration);

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
