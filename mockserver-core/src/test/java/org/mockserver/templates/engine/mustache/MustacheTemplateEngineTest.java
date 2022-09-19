package org.mockserver.templates.engine.mustache;

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
import org.mockserver.templates.engine.mustache.MustacheTemplateEngine;
import org.mockserver.time.EpochService;
import org.mockserver.time.TimeService;
import org.mockserver.uuid.UUIDService;
import org.slf4j.event.Level;

import java.nio.charset.StandardCharsets;
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
public class MustacheTemplateEngineTest {

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
    public void shouldHandleHttpRequestsWithMustacheResponseTemplateWithMethodPathAndHeader() throws JsonProcessingException {
        // given
        String template = "{" + NEW_LINE +
            "    'statusCode': 200," + NEW_LINE +
            "    'body': \"{'method': '{{ request.method }}', 'path': '{{ request.path }}', 'headers': '{{ request.headers.host.0 }}'}\"" + NEW_LINE +
            "}";
        HttpRequest request = request()
            .withPath("/somePath")
            .withMethod("POST")
            .withHeader(HOST.toString(), "mock-server.com")
            .withBody("some_body");

        // when
        HttpResponse actualHttpResponse = new MustacheTemplateEngine(mockServerLogger, configuration).executeTemplate(template, request, HttpResponseDTO.class);

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
                        "}" + NEW_LINE),
                    template,
                    request
                )
        );
    }

    @Test
    public void shouldHandleHttpRequestsWithMustacheResponseTemplateWithLoopOverEntrySet() throws JsonProcessingException {
        // given
        String template = "{" + NEW_LINE +
            "    'statusCode': 200," + NEW_LINE +
            "    'body': \"{'headers': [{{#request.headers.entrySet}}{{^-first}}, {{/-first}}'{{ key }}={{ value.0 }}'{{/request.headers.entrySet}}]}\"" + NEW_LINE +
            "}";
        HttpRequest request = request()
            .withPath("/somePath")
            .withMethod("POST")
            .withHeader(HOST.toString(), "mock-server.com")
            .withHeader(CONTENT_TYPE.toString(), "plain/text")
            .withBody("some_body");

        // when
        HttpResponse actualHttpResponse = new MustacheTemplateEngine(mockServerLogger, configuration).executeTemplate(template, request, HttpResponseDTO.class);

        // then
        assertThat(actualHttpResponse, is(
            response()
                .withStatusCode(200)
                .withBody("{'headers': ['host=mock-server.com', 'content-type=plain/text']}")
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
                        "    'body': \"{'headers': ['host=mock-server.com', 'content-type=plain/text']}\"" + NEW_LINE +
                        "}" + NEW_LINE),
                    template,
                    request
                )
        );
    }

    @Test
    public void shouldHandleHttpRequestsWithMustacheResponseTemplateWithLoopOverValuesUsingThis() throws JsonProcessingException {
        // given
        String template = "{" + NEW_LINE +
            "    'statusCode': 200," + NEW_LINE +
            "    'body': \"{'headers': [{{#request.headers.values}}{{^-first}}, {{/-first}}'{{ this.0 }}'{{/request.headers.values}}]}\"" + NEW_LINE +
            "}";
        HttpRequest request = request()
            .withPath("/somePath")
            .withMethod("POST")
            .withHeader(HOST.toString(), "mock-server.com")
            .withHeader(CONTENT_TYPE.toString(), "plain/text")
            .withBody("some_body");

        // when
        HttpResponse actualHttpResponse = new MustacheTemplateEngine(mockServerLogger, configuration).executeTemplate(template, request, HttpResponseDTO.class);

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
    public void shouldHandleHttpRequestsWithMustacheResponseTemplateWithLoopOverKeysUsingThis() throws JsonProcessingException {
        // given
        String template = "{" + NEW_LINE +
            "    'statusCode': 200," + NEW_LINE +
            "    'body': \"{'headers': [{{#request.headers.keySet}}{{^-first}}, {{/-first}}'{{ -index }}:{{ this }}'{{/request.headers.keySet}}]}\"" + NEW_LINE +
            "}";
        HttpRequest request = request()
            .withPath("/somePath")
            .withMethod("POST")
            .withHeader(HOST.toString(), "mock-server.com")
            .withHeader(CONTENT_TYPE.toString(), "plain/text")
            .withBody("some_body");

        // when
        HttpResponse actualHttpResponse = new MustacheTemplateEngine(mockServerLogger, configuration).executeTemplate(template, request, HttpResponseDTO.class);

        // then
        assertThat(actualHttpResponse, is(
            response()
                .withStatusCode(200)
                .withBody("{'headers': ['1:host', '2:content-type']}")
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
                        "    'body': \"{'headers': ['1:host', '2:content-type']}\"" + NEW_LINE +
                        "}" + NEW_LINE),
                    template,
                    request
                )
        );
    }

    @Test
    public void shouldHandleHttpRequestsWithMustacheResponseTemplateWithIf() {
        // given
        String template = "{" + NEW_LINE +
            "    'statusCode': 200," + NEW_LINE +
            "    'body': \"{{#request.body}}inside_if_{{ this }}{{/request.body}}\"" + NEW_LINE +
            "}";
        HttpRequest nonEmptyBodyRequest = request()
            .withPath("/somePath")
            .withMethod("POST")
            .withHeader(HOST.toString(), "mock-server.com")
            .withHeader(CONTENT_TYPE.toString(), "plain/text")
            .withBody("some_body");

        // when
        HttpResponse nonEmptyBodyActualHttpResponse = new MustacheTemplateEngine(mockServerLogger, configuration).executeTemplate(template, nonEmptyBodyRequest, HttpResponseDTO.class);

        // then
        assertThat(nonEmptyBodyActualHttpResponse, is(
            response()
                .withStatusCode(200)
                .withBody("inside_if_some_body")
        ));

        // then
        HttpRequest emptyBodyRequest = request()
            .withPath("/somePath")
            .withMethod("POST")
            .withHeader(HOST.toString(), "mock-server.com")
            .withHeader(CONTENT_TYPE.toString(), "plain/text");

        // when
        HttpResponse emptyBodyActualHttpResponse = new MustacheTemplateEngine(mockServerLogger, configuration).executeTemplate(template, emptyBodyRequest, HttpResponseDTO.class);

        // then
        assertThat(emptyBodyActualHttpResponse, is(
            response()
                .withStatusCode(200)
                .withBody("")
        ));
    }

    @Test
    public void shouldHandleHttpRequestsWithMustacheResponseTemplateWithParametersCookiesAndBody() throws JsonProcessingException {
        // given
        String template = "{" + NEW_LINE +
            "    'statusCode': 200," + NEW_LINE +
            "    'body': \"{'queryStringParameters': '{{ request.queryStringParameters.nameOne.0 }},{{ request.queryStringParameters.nameTwo.0 }},{{ request.queryStringParameters.nameTwo.1 }}'," +
            " 'pathParameters': '{{ request.pathParameters.nameOne.0 }},{{ request.pathParameters.nameTwo.0 }},{{ request.pathParameters.nameTwo.1 }}'," +
            " 'cookies': '{{ request.cookies.session }}'," +
            " 'body': '{{ request.body }}'}\"" + NEW_LINE +
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
        HttpResponse actualHttpResponse = new MustacheTemplateEngine(mockServerLogger, configuration).executeTemplate(template, request, HttpResponseDTO.class);

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
    public void shouldHandleHttpRequestsWithMustacheResponseTemplateWithDynamicValuesDateAndUUID() throws JsonProcessingException {
        boolean originalFixedUUID = UUIDService.fixedUUID;
        boolean originalFixedTime = TimeService.fixedTime;
        try {
            // given
            UUIDService.fixedUUID = true;
            TimeService.fixedTime = true;
            String template = "{" + NEW_LINE +
                "    'statusCode': 200," + NEW_LINE +
                "    'body': \"{'date': '{{ now }}', 'date_epoch': '{{ now_epoch }}', 'date_iso_8601': '{{ now_iso_8601 }}', 'date_rfc_1123': '{{ now_rfc_1123 }}', 'uuids': ['{{ uuid }}', '{{ uuid }}'] }\"" + NEW_LINE +
                "}";
            HttpRequest request = request()
                .withPath("/somePath")
                .withQueryStringParameter("nameOne", "valueOne")
                .withQueryStringParameter("nameTwo", "valueTwoOne", "valueTwoTwo")
                .withMethod("POST")
                .withCookie("session", "some_session_id")
                .withBody("some_body");

            // when
            HttpResponse actualHttpResponse = new MustacheTemplateEngine(mockServerLogger, configuration).executeTemplate(template, request, HttpResponseDTO.class);

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
    public void shouldHandleHttpRequestsWithMustacheResponseTemplateWithDynamicValuesRandom() {
        shouldPopulateRandomValue("{{ rand_int }}", equalTo(1));
        shouldPopulateRandomValue("{{ rand_int_10 }}", allOf(greaterThan(0), lessThan(3)));
        shouldPopulateRandomValue("{{ rand_int_100 }}", allOf(greaterThan(0), lessThan(4)));
        shouldPopulateRandomValue("{{ rand_bytes }}", allOf(greaterThan(20), lessThan(50)));
        shouldPopulateRandomValue("{{ rand_bytes_16 }}", allOf(greaterThan(20), lessThan(50)));
        shouldPopulateRandomValue("{{ rand_bytes_32 }}", allOf(greaterThan(40), lessThan(60)));
        shouldPopulateRandomValue("{{ rand_bytes_64 }}", allOf(greaterThan(80), lessThan(120)));
        shouldPopulateRandomValue("{{ rand_bytes_128 }}", allOf(greaterThan(160), lessThan(300)));
    }

    private void shouldPopulateRandomValue(String function, Matcher<Integer> matcher) {
        // given
        String template = "{ 'body': '" + function + "' }";
        HttpRequest request = request()
            .withPath("/somePath")
            .withBody("some_body");

        // when
        HttpResponse actualHttpResponse = new MustacheTemplateEngine(mockServerLogger, configuration).executeTemplate(template, request, HttpResponseDTO.class);

        // then
        assertThat(actualHttpResponse.getBodyAsString(), not(equalTo("")));
        assertThat(actualHttpResponse.getBodyAsString().length(), matcher);
    }

    @Test
    public void shouldHandleHttpRequestsWithMustacheResponseTemplateWithXPath() throws JsonProcessingException {
        // given
        String template = "{" + NEW_LINE +
            "    'statusCode': 200," + NEW_LINE +
            "    'body': \"{'key': '{{#xPath}}/element/key{{/xPath}}', 'value': '{{#xPath}}/element/value{{/xPath}}'}\"" + NEW_LINE +
            "}";
        HttpRequest request = request()
            .withPath("/somePath")
            .withBody("<element>" + NEW_LINE +
                "   <key>some_key</key>" + NEW_LINE +
                "   <value>some_value</value>" + NEW_LINE +
                "</element>");

        // when
        HttpResponse actualHttpResponse = new MustacheTemplateEngine(mockServerLogger, configuration).executeTemplate(template, request, HttpResponseDTO.class);

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
    public void shouldHandleHttpRequestsWithMustacheResponseTemplateWithXPathNode() throws JsonProcessingException {
        // given
        String template = "{" + NEW_LINE +
            "    'statusCode': 200," + NEW_LINE +
            "    'body': \"{'titles': ['{{#xPath}}/store/book/title{{/xPath}}', '{{#xPath}}//book[2]/title{{/xPath}}'], 'bikeColor': '{{#xPath}}//bicycle/color{{/xPath}}'}\"" + NEW_LINE +
            "}";
        HttpRequest request = request()
            .withPath("/somePath")
            .withBody("<?xml version=\"1.0\" encoding=\"UTF-8\" ?>" + NEW_LINE +
                "<store>" + NEW_LINE +
                "  <book>" + NEW_LINE +
                "    <category>reference</category>" + NEW_LINE +
                "    <author>Nigel Rees</author>" + NEW_LINE +
                "    <title>Sayings of the Century</title>" + NEW_LINE +
                "    <price>18.95</price>" + NEW_LINE +
                "  </book>" + NEW_LINE +
                "  <book>" + NEW_LINE +
                "    <category>fiction</category>" + NEW_LINE +
                "    <author>Herman Melville</author>" + NEW_LINE +
                "    <title>Moby Dick</title>" + NEW_LINE +
                "    <isbn>0-553-21311-3</isbn>" + NEW_LINE +
                "    <price>8.99</price>" + NEW_LINE +
                "  </book>" + NEW_LINE +
                "  <bicycle>" + NEW_LINE +
                "    <color>red</color>" + NEW_LINE +
                "    <price>19.95</price>" + NEW_LINE +
                "  </bicycle>" + NEW_LINE +
                "  <expensive>10</expensive>" + NEW_LINE +
                "</store>");

        // when
        HttpResponse actualHttpResponse = new MustacheTemplateEngine(mockServerLogger, configuration).executeTemplate(template, request, HttpResponseDTO.class);

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
    public void shouldHandleHttpRequestsWithMustacheResponseTemplateWithXPathWithJsonBody() throws JsonProcessingException {
        // given
        String template = "{" + NEW_LINE +
            "    'statusCode': 200," + NEW_LINE +
            "    'body': \"{'key': '{{#xPath}}/element/key{{/xPath}}', 'value': '{{#xPath}}/element/value{{/xPath}}'}\"" + NEW_LINE +
            "}";
        HttpRequest request = request()
            .withPath("/somePath")
            .withBody("{" + NEW_LINE +
                "   \"element\": {" + NEW_LINE +
                "      \"key\": \"some_key\"," + NEW_LINE +
                "      \"value\": \"some_value\"" + NEW_LINE +
                "   }" + NEW_LINE +
                "}");

        // when
        HttpResponse actualHttpResponse = new MustacheTemplateEngine(mockServerLogger, configuration).executeTemplate(template, request, HttpResponseDTO.class);

        // then
        assertThat(actualHttpResponse, is(
            response()
                .withStatusCode(200)
                .withBody("{'key': '', 'value': ''}")
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
                        "    'body': \"{'key': '', 'value': ''}\"" + NEW_LINE +
                        "}" + NEW_LINE),
                    template,
                    request
                )
        );
    }

    @Test
    public void shouldHandleHttpRequestsWithMustacheResponseTemplateWithXPathWithStringBody() throws JsonProcessingException {
        // given
        String template = "{" + NEW_LINE +
            "    'statusCode': 200," + NEW_LINE +
            "    'body': \"{'key': '{{#xPath}}/element/key{{/xPath}}', 'value': '{{#xPath}}/element/value{{/xPath}}'}\"" + NEW_LINE +
            "}";
        HttpRequest request = request()
            .withPath("/somePath")
            .withBody("some_string_body");

        // when
        HttpResponse actualHttpResponse = new MustacheTemplateEngine(mockServerLogger, configuration).executeTemplate(template, request, HttpResponseDTO.class);

        // then
        assertThat(actualHttpResponse, is(
            response()
                .withStatusCode(200)
                .withBody("{'key': '', 'value': ''}")
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
                        "    'body': \"{'key': '', 'value': ''}\"" + NEW_LINE +
                        "}" + NEW_LINE),
                    template,
                    request
                )
        );
    }

    @Test
    public void shouldHandleHttpRequestsWithMustacheResponseTemplateWithJsonPath() throws JsonProcessingException {
        // given
        String template = "{" + NEW_LINE +
            "    'statusCode': 200," + NEW_LINE +
            "    'body': \"{'titles': {{#jsonPath}}$.store.book{{/jsonPath}}[{{#jsonPathResult}}{{^-first}}, {{/-first}}'{{title}}'{{/jsonPathResult}}], 'bikeColor': '{{#jsonPath}}$.store.bicycle.color{{/jsonPath}}{{jsonPathResult}}'}\"" + NEW_LINE +
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
        HttpResponse actualHttpResponse = new MustacheTemplateEngine(mockServerLogger, configuration).executeTemplate(template, request, HttpResponseDTO.class);

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
    public void shouldHandleHttpRequestsWithMustacheResponseTemplateWithJsonPathWithXmlBody() throws JsonProcessingException {
        // given
        String template = "{" + NEW_LINE +
            "    'statusCode': 200," + NEW_LINE +
            "    'body': \"{'key': '{{#jsonPath}}$.store.book[0].title{{/jsonPath}}', 'value': '{{#jsonPath}}$.store.bicycle.color{{/jsonPath}}'}\"" + NEW_LINE +
            "}";
        HttpRequest request = request()
            .withPath("/somePath")
            .withBody("<?xml version=\"1.0\" encoding=\"UTF-8\" ?>" + NEW_LINE +
                "<root>" + NEW_LINE +
                "  <store>" + NEW_LINE +
                "    <book>" + NEW_LINE +
                "      <category>reference</category>" + NEW_LINE +
                "      <author>Nigel Rees</author>" + NEW_LINE +
                "      <title>Sayings of the Century</title>" + NEW_LINE +
                "      <price>18.95</price>" + NEW_LINE +
                "    </book>" + NEW_LINE +
                "    <book>" + NEW_LINE +
                "      <category>fiction</category>" + NEW_LINE +
                "      <author>Herman Melville</author>" + NEW_LINE +
                "      <title>Moby Dick</title>" + NEW_LINE +
                "      <isbn>0-553-21311-3</isbn>" + NEW_LINE +
                "      <price>8.99</price>" + NEW_LINE +
                "    </book>" + NEW_LINE +
                "    <bicycle>" + NEW_LINE +
                "      <color>red</color>" + NEW_LINE +
                "      <price>19.95</price>" + NEW_LINE +
                "    </bicycle>" + NEW_LINE +
                "  </store>" + NEW_LINE +
                "  <expensive>10</expensive>" + NEW_LINE +
                "</root>");

        // when
        HttpResponse actualHttpResponse = new MustacheTemplateEngine(mockServerLogger, configuration).executeTemplate(template, request, HttpResponseDTO.class);

        // then
        assertThat(actualHttpResponse, is(
            response()
                .withStatusCode(200)
                .withBody("{'key': '', 'value': ''}")
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
                        "    'body': \"{'key': '', 'value': ''}\"" + NEW_LINE +
                        "}" + NEW_LINE),
                    template,
                    request
                )
        );
    }

    @Test
    public void shouldHandleHttpRequestsWithMustacheResponseTemplateWithJsonPathWithStringBody() throws JsonProcessingException {
        // given
        String template = "{" + NEW_LINE +
            "    'statusCode': 200," + NEW_LINE +
            "    'body': \"{'key': '{{#jsonPath}}$.store.book[0].title{{/jsonPath}}', 'value': '{{#jsonPath}}$.store.bicycle.color{{/jsonPath}}'}\"" + NEW_LINE +
            "}";
        HttpRequest request = request()
            .withPath("/somePath")
            .withBody("some_string_body");

        // when
        HttpResponse actualHttpResponse = new MustacheTemplateEngine(mockServerLogger, configuration).executeTemplate(template, request, HttpResponseDTO.class);

        // then
        assertThat(actualHttpResponse, is(
            response()
                .withStatusCode(200)
                .withBody("{'key': '', 'value': ''}")
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
                        "    'body': \"{'key': '', 'value': ''}\"" + NEW_LINE +
                        "}" + NEW_LINE),
                    template,
                    request
                )
        );
    }

    @Test
    public void shouldHandleHttpRequestsWithMustacheResponseTemplateBinaryBody() throws JsonProcessingException {
        // given
        String template = "{" + NEW_LINE +
            "    'statusCode': 200," + NEW_LINE +
            "    'body': \"{'body': '{{ request.body }}'}\"" + NEW_LINE +
            "}";
        HttpRequest request = request()
            .withPath("/somePath")
            .withMethod("POST")
            .withHeader(HOST.toString(), "mock-server.com")
            .withBody("some_body".getBytes(StandardCharsets.UTF_8));

        // when
        HttpResponse actualHttpResponse = new MustacheTemplateEngine(mockServerLogger, configuration).executeTemplate(template, request, HttpResponseDTO.class);

        // then
        assertThat(actualHttpResponse, is(
            response()
                .withStatusCode(200)
                .withBody("{'body': 'c29tZV9ib2R5'}")
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
                        "    'body': \"{'body': 'c29tZV9ib2R5'}\"" + NEW_LINE +
                        "}" + NEW_LINE),
                    template,
                    request
                )
        );
    }

    @Test
    public void shouldHandleHttpRequestsWithMustacheForwardTemplateWithPathBodyParametersAndCookies() throws JsonProcessingException {
        // given
        String template = "{" + NEW_LINE +
            "    'path': '{{ request.path }}'," + NEW_LINE +
            "    'body': \"{'queryStringParameters': '{{ request.queryStringParameters.nameOne.0 }},{{ request.queryStringParameters.nameTwo.0 }},{{ request.queryStringParameters.nameTwo.1 }}'," +
            " 'pathParameters': '{{ request.pathParameters.nameOne.0 }},{{ request.pathParameters.nameTwo.0 }},{{ request.pathParameters.nameTwo.1 }}'," +
            " 'cookies': '{{ request.cookies.session }}'," +
            " 'body': '{{ request.body }}'}\"" + NEW_LINE +
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
        HttpRequest actualHttpRequest = new MustacheTemplateEngine(mockServerLogger, configuration).executeTemplate(template, request, HttpRequestDTO.class);

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
    public void shouldHandleHttpRequestsWithMustacheTemplateWithDisallowedText() {
        String originalMustacheDisallowedText = configuration.mustacheDisallowedText();

        try {
            // given
            String template = "{" + NEW_LINE +
                "    'statusCode': 200," + NEW_LINE +
                "    'body': \"{'method': '{{ request.method }}', 'path': '{{ request.path }}', 'headers': '{{ request.headers.host.0 }}'}\"" + NEW_LINE +
                "}";
            HttpRequest request = request()
                .withPath("/somePath")
                .withMethod("POST")
                .withHeader(HOST.toString(), "mock-server.com")
                .withBody("some_body");

            // when
            HttpResponse actualHttpResponse = new MustacheTemplateEngine(mockServerLogger, configuration).executeTemplate(template, request, HttpResponseDTO.class);

            // then
            assertThat(actualHttpResponse, is(
                response()
                    .withStatusCode(200)
                    .withBody("{'method': 'POST', 'path': '/somePath', 'headers': 'mock-server.com'}")
            ));

            // when
            configuration.mustacheDisallowedText("request.method");

            // then
            Exception exception = assertThrows(RuntimeException.class, () -> new MustacheTemplateEngine(mockServerLogger, configuration).executeTemplate(template, request, HttpResponseDTO.class));
            assertThat(exception.getMessage(), containsString("Found disallowed string \"request.method\" in template:"));
        } finally {
            configuration.mustacheDisallowedText(originalMustacheDisallowedText);
        }
    }

    @Test
    public void shouldHandleHttpRequestsWithMustacheTemplateWithDisallowedTextList() {
        String originalMustacheDisallowedText = configuration.mustacheDisallowedText();

        try {
            // given
            String template = "{" + NEW_LINE +
                "    'statusCode': 200," + NEW_LINE +
                "    'body': \"{'method': '{{ request.method }}', 'path': '{{ request.path }}', 'headers': '{{ request.headers.host.0 }}'}\"" + NEW_LINE +
                "}";
            HttpRequest request = request()
                .withPath("/somePath")
                .withMethod("POST")
                .withHeader(HOST.toString(), "mock-server.com")
                .withBody("some_body");
            configuration.mustacheDisallowedText("request.method , request.path");

            // then
            Exception exception = assertThrows(RuntimeException.class, () -> new MustacheTemplateEngine(mockServerLogger, configuration).executeTemplate(template, request, HttpResponseDTO.class));
            assertThat(exception.getMessage(), containsString("Found disallowed string \"request.method\" in template:"));

            // when
            configuration.mustacheDisallowedText(" request.path   ,request.method");

            // then
            exception = assertThrows(RuntimeException.class, () -> new MustacheTemplateEngine(mockServerLogger, configuration).executeTemplate(template, request, HttpResponseDTO.class));
            assertThat(exception.getMessage(), containsString("Found disallowed string \"request.path\" in template:"));
        } finally {
            configuration.mustacheDisallowedText(originalMustacheDisallowedText);
        }
    }

    @Test
    public void shouldHandleHttpRequestsWithMustacheResponseTemplateInvalidFields() throws JsonProcessingException {
        // given
        String template = "{" + NEW_LINE +
            "    'statusCode': 200," + NEW_LINE +
            "    'body': \"{'method': '{{ request.method.invalid }}', 'path': '{{ request.invalid }}', 'headers': '{{ invalid.headers.host.0 }}'}\"" + NEW_LINE +
            "}";
        HttpRequest request = request()
            .withPath("/somePath")
            .withMethod("POST")
            .withHeader(HOST.toString(), "mock-server.com")
            .withBody("some_body".getBytes(StandardCharsets.UTF_8));

        // when
        HttpResponse actualHttpResponse = new MustacheTemplateEngine(mockServerLogger, configuration).executeTemplate(template, request, HttpResponseDTO.class);

        // then
        assertThat(actualHttpResponse, is(
            response()
                .withStatusCode(200)
                .withBody("{'method': '', 'path': '', 'headers': ''}")
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
                        "    'body': \"{'method': '', 'path': '', 'headers': ''}\"" + NEW_LINE +
                        "}" + NEW_LINE),
                    template,
                    request
                )
        );
    }

    @Test
    public void shouldHandleInvalidMustacheTemplate() {
        // given
        String template = "{{ {{ }} }}";

        // when
        RuntimeException runtimeException = assertThrows(RuntimeException.class, () -> new MustacheTemplateEngine(mockServerLogger, configuration).executeTemplate(template, request()
                .withPath("/someOtherPath")
                .withQueryStringParameter("queryParameter", "someValue")
                .withBody("some_body"),
            HttpRequestDTO.class
        ));

        // then
        assertThat(runtimeException.getMessage(), is("Exception:" + NEW_LINE +
            "" + NEW_LINE +
            "  String index out of range: -1" + NEW_LINE +
            "" + NEW_LINE +
            " transforming template:" + NEW_LINE +
            "" + NEW_LINE +
            "  {{ {{ }} }}" + NEW_LINE +
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
    public void shouldHandleMultipleHttpRequestsWithMustacheResponseTemplateInParallel()
        throws InterruptedException, ExecutionException {
        // given
        String template = "{" + NEW_LINE +
            "    'statusCode': 200," + NEW_LINE +
            "    'body': \"{'method': '{{ request.method }}', 'path': '{{ request.path }}', 'headers': '{{ request.headers.host.0 }}'}\"" + NEW_LINE +
            "}";
        HttpRequest request = request()
            .withPath("/somePath")
            .withMethod("POST")
            .withHeader(HOST.toString(), "mock-server.com")
            .withBody("some_body");

        // when
        MustacheTemplateEngine mustacheTemplateEngine = new MustacheTemplateEngine(mockServerLogger, configuration);

        ExecutorService newFixedThreadPool = Executors.newFixedThreadPool(30);

        List<Future<Boolean>> futures = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            futures.add(newFixedThreadPool.submit(() -> {
                assertThat(mustacheTemplateEngine.executeTemplate(template, request, HttpResponseDTO.class), is(
                    response()
                        .withStatusCode(200)
                        .withBody("{'method': 'POST', 'path': '/somePath', 'headers': 'mock-server.com'}")
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
