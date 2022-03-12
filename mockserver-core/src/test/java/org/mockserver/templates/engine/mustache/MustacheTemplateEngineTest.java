package org.mockserver.templates.engine.mustache;

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

import javax.script.ScriptException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

import static io.netty.handler.codec.http.HttpHeaderNames.HOST;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.Is.isA;
import static org.hamcrest.core.StringEndsWith.endsWith;
import static org.hamcrest.core.StringStartsWith.startsWith;
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.MockitoAnnotations.openMocks;
import static org.mockserver.character.Character.NEW_LINE;
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

    @Rule
    public final ExpectedException exception = ExpectedException.none();

    @Mock
    private MockServerLogger logFormatter;

    @BeforeClass
    public static void fixTime() {
        TimeService.fixedTime = true;
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
    public void shouldHandleHttpRequestsWithMustacheResponseTemplateMethodPathAndHeader() throws JsonProcessingException {
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
        HttpResponse actualHttpResponse = new MustacheTemplateEngine(logFormatter).executeTemplate(template, request, HttpResponseDTO.class);

        // then
        assertThat(actualHttpResponse, is(
            response()
                .withStatusCode(200)
                .withBody("{'method': 'POST', 'path': '/somePath', 'headers': 'mock-server.com'}")
        ));
        verify(logFormatter).logEvent(
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
    public void shouldHandleHttpRequestsWithMustacheResponseTemplateParametersCookiesAndBody() throws JsonProcessingException {
        // given
        String template = "{" + NEW_LINE +
            "    'statusCode': 200," + NEW_LINE +
            "    'body': \"{'queryStringParameters': '{{ request.queryStringParameters.nameOne.0 }},{{ request.queryStringParameters.nameTwo.0 }},{{ request.queryStringParameters.nameTwo.1 }}'," +
            " 'pathParameters': '{{ request.pathParameters.nameOne.0 }},{{ request.pathParameters.nameTwo.0 }},{{ request.pathParameters.nameTwo.1 }}'," +
            " 'cookies': '{{ request.cookies.session }}," +
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
        HttpResponse actualHttpResponse = new MustacheTemplateEngine(logFormatter).executeTemplate(template, request, HttpResponseDTO.class);

        // then
        assertThat(actualHttpResponse, is(
            response()
                .withStatusCode(200)
                .withBody("{'queryStringParameters': 'queryValueOne,queryValueTwoOne,queryValueTwoTwo', 'pathParameters': 'pathValueOne,pathValueTwoOne,pathValueTwoTwo', 'cookies': 'some_session_id, 'body': 'some_body'}")
        ));
        verify(logFormatter).logEvent(
            new LogEntry()
                .setType(TEMPLATE_GENERATED)
                .setLogLevel(INFO)
                .setHttpRequest(request)
                .setMessageFormat("generated output:{}from template:{}for request:{}")
                .setArguments(OBJECT_MAPPER.readTree("" +
                        "    {" + NEW_LINE +
                        "        'statusCode': 200," + NEW_LINE +
                        "        'body': \"{'queryStringParameters': 'queryValueOne,queryValueTwoOne,queryValueTwoTwo', 'pathParameters': 'pathValueOne,pathValueTwoOne,pathValueTwoTwo', 'cookies': 'some_session_id, 'body': 'some_body'}\"" + NEW_LINE +
                        "    }" + NEW_LINE),
                    template,
                    request
                )
        );
    }

    @Test
    public void shouldHandleHttpRequestsWithMustacheResponseTemplateDynamicValuesDateAndUUID() throws InterruptedException {
        boolean originalFixedUUID = UUIDService.fixedUUID;
        try {
            // given
            UUIDService.fixedUUID = true;
            String template = "{" + NEW_LINE +
                "    'statusCode': 200," + NEW_LINE +
                "    'body': \"{'date': '{{ now }}', 'date_epoch': '{{ now_epoch }}', 'date_iso-8601': '{{ now_iso-8601 }}', 'date_rfc_1123': '{{ now_rfc_1123 }}', 'uuids': ['{{ uuid }}', '{{ uuid }}'] }\"" + NEW_LINE +
                "}";
            HttpRequest request = request()
                .withPath("/somePath")
                .withQueryStringParameter("nameOne", "valueOne")
                .withQueryStringParameter("nameTwo", "valueTwoOne", "valueTwoTwo")
                .withMethod("POST")
                .withCookie("session", "some_session_id")
                .withBody("some_body");

            // when
            HttpResponse firstActualHttpResponse = new MustacheTemplateEngine(logFormatter).executeTemplate(template, request, HttpResponseDTO.class);

            // then
            assertThat(firstActualHttpResponse.getBodyAsString(), allOf(startsWith("{'date': '20"), endsWith("', 'uuids': ['" + UUIDService.getUUID() + "', '" + UUIDService.getUUID() + "'] }")));

            // given
            TimeUnit.SECONDS.sleep(1);

            // when
            HttpResponse secondActualHttpResponse = new MustacheTemplateEngine(logFormatter).executeTemplate(template, request, HttpResponseDTO.class);

            // then
            assertThat(secondActualHttpResponse.getBodyAsString(), allOf(startsWith("{'date': '20"), endsWith("', 'uuids': ['" + UUIDService.getUUID() + "', '" + UUIDService.getUUID() + "'] }")));
            // date should now be different
            assertThat(secondActualHttpResponse.getBodyAsString(), not(is(firstActualHttpResponse.getBodyAsString())));

        } finally {
            UUIDService.fixedUUID = originalFixedUUID;
        }
    }

    @Test
    public void shouldHandleHttpRequestsWithMustacheResponseTemplateDynamicValuesRandom() throws InterruptedException {
        boolean originalFixedUUID = UUIDService.fixedUUID;
        try {
            // given
            UUIDService.fixedUUID = true;
            String template = "{" + NEW_LINE +
                "    'statusCode': 200," + NEW_LINE +
                "    'body': \"{'rand_int': '{{ rand_int }}', 'rand_int_10': '{{ rand_int_10 }}', 'rand_int_100': '{{ rand_int_100 }}', 'rand_bytes': ['{{ rand_bytes }}','{{ rand_bytes_16 }}','{{ rand_bytes_32 }}','{{ rand_bytes_64 }}','{{ rand_bytes_128 }}'], 'end': 'end' }\"" + NEW_LINE +
                "}";
            HttpRequest request = request()
                .withPath("/somePath")
                .withQueryStringParameter("nameOne", "valueOne")
                .withQueryStringParameter("nameTwo", "valueTwoOne", "valueTwoTwo")
                .withMethod("POST")
                .withCookie("session", "some_session_id")
                .withBody("some_body");

            // when
            HttpResponse firstActualHttpResponse = new MustacheTemplateEngine(logFormatter).executeTemplate(template, request, HttpResponseDTO.class);

            // then
            assertThat(firstActualHttpResponse.getBodyAsString(), allOf(startsWith("{'rand_int': '"), endsWith("'], 'end': 'end' }")));

            // given
            TimeUnit.SECONDS.sleep(1);

            // when
            HttpResponse secondActualHttpResponse = new MustacheTemplateEngine(logFormatter).executeTemplate(template, request, HttpResponseDTO.class);

            // then
            assertThat(firstActualHttpResponse.getBodyAsString(), allOf(startsWith("{'rand_int': '"), endsWith("'], 'end': 'end' }")));
            // should now be different
            assertThat(secondActualHttpResponse.getBodyAsString(), not(is(firstActualHttpResponse.getBodyAsString())));

        } finally {
            UUIDService.fixedUUID = originalFixedUUID;
        }
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
        HttpResponse actualHttpResponse = new MustacheTemplateEngine(logFormatter).executeTemplate(template, request, HttpResponseDTO.class);

        // then
        assertThat(actualHttpResponse, is(
            response()
                .withStatusCode(200)
                .withBody("{'key': 'some_key', 'value': 'some_value'}")
        ));
        verify(logFormatter).logEvent(
            new LogEntry()
                .setType(TEMPLATE_GENERATED)
                .setLogLevel(INFO)
                .setHttpRequest(request)
                .setMessageFormat("generated output:{}from template:{}for request:{}")
                .setArguments(OBJECT_MAPPER.readTree("" +
                        "    {" + NEW_LINE +
                        "        'statusCode': 200," + NEW_LINE +
                        "        'body': \"{'key': 'some_key', 'value': 'some_value'}\"" + NEW_LINE +
                        "    }" + NEW_LINE),
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
        HttpResponse actualHttpResponse = new MustacheTemplateEngine(logFormatter).executeTemplate(template, request, HttpResponseDTO.class);

        // then
        assertThat(actualHttpResponse, is(
            response()
                .withStatusCode(200)
                .withBody("{'key': '', 'value': ''}")
        ));
        verify(logFormatter).logEvent(
            new LogEntry()
                .setType(TEMPLATE_GENERATED)
                .setLogLevel(INFO)
                .setHttpRequest(request)
                .setMessageFormat("generated output:{}from template:{}for request:{}")
                .setArguments(OBJECT_MAPPER.readTree("" +
                        "    {" + NEW_LINE +
                        "        'statusCode': 200," + NEW_LINE +
                        "        'body': \"{'key': '', 'value': ''}\"" + NEW_LINE +
                        "    }" + NEW_LINE),
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
        HttpResponse actualHttpResponse = new MustacheTemplateEngine(logFormatter).executeTemplate(template, request, HttpResponseDTO.class);

        // then
        assertThat(actualHttpResponse, is(
            response()
                .withStatusCode(200)
                .withBody("{'key': '', 'value': ''}")
        ));
        verify(logFormatter).logEvent(
            new LogEntry()
                .setType(TEMPLATE_GENERATED)
                .setLogLevel(INFO)
                .setHttpRequest(request)
                .setMessageFormat("generated output:{}from template:{}for request:{}")
                .setArguments(OBJECT_MAPPER.readTree("" +
                        "    {" + NEW_LINE +
                        "        'statusCode': 200," + NEW_LINE +
                        "        'body': \"{'key': '', 'value': ''}\"" + NEW_LINE +
                        "    }" + NEW_LINE),
                    template,
                    request
                )
        );
    }

    @Test
    public void shouldHandleHttpRequestsWithMustacheResponseTemplateWithJsonPath() throws JsonProcessingException {
        // given
        ConfigurationProperties.logLevel("TRACE");
        String template = "{" + NEW_LINE +
            "    'statusCode': 200," + NEW_LINE +
            "    'body': \"{'key': '{{#jsonPath}}$.store.book[0].title{{/jsonPath}}', 'value': '{{#jsonPath}}$.store.bicycle.color{{/jsonPath}}'}\"" + NEW_LINE +
            "}";
        HttpRequest request = request()
            .withPath("/somePath")
            .withBody("{" + NEW_LINE +
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
                "}");

        // when
        HttpResponse actualHttpResponse = new MustacheTemplateEngine(logFormatter).executeTemplate(template, request, HttpResponseDTO.class);

        // then
        assertThat(actualHttpResponse, is(
            response()
                .withStatusCode(200)
                .withBody("{'key': 'Sayings of the Century', 'value': 'red'}")
        ));
        verify(logFormatter).logEvent(
            new LogEntry()
                .setType(TEMPLATE_GENERATED)
                .setLogLevel(INFO)
                .setHttpRequest(request)
                .setMessageFormat("generated output:{}from template:{}for request:{}")
                .setArguments(OBJECT_MAPPER.readTree("" +
                        "    {" + NEW_LINE +
                        "        'statusCode': 200," + NEW_LINE +
                        "        'body': \"{'key': 'Sayings of the Century', 'value': 'red'}\"" + NEW_LINE +
                        "    }" + NEW_LINE),
                    template,
                    request
                )
        );
    }

    @Test
    public void shouldHandleHttpRequestsWithMustacheResponseTemplateWithJsonPathWithXmlBody() throws JsonProcessingException {
        // given
        ConfigurationProperties.logLevel("TRACE");
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
        HttpResponse actualHttpResponse = new MustacheTemplateEngine(logFormatter).executeTemplate(template, request, HttpResponseDTO.class);

        // then
        assertThat(actualHttpResponse, is(
            response()
                .withStatusCode(200)
                .withBody("{'key': '', 'value': ''}")
        ));
        verify(logFormatter).logEvent(
            new LogEntry()
                .setType(TEMPLATE_GENERATED)
                .setLogLevel(INFO)
                .setHttpRequest(request)
                .setMessageFormat("generated output:{}from template:{}for request:{}")
                .setArguments(OBJECT_MAPPER.readTree("" +
                        "    {" + NEW_LINE +
                        "        'statusCode': 200," + NEW_LINE +
                        "        'body': \"{'key': '', 'value': ''}\"" + NEW_LINE +
                        "    }" + NEW_LINE),
                    template,
                    request
                )
        );
    }

    @Test
    public void shouldHandleHttpRequestsWithMustacheResponseTemplateWithJsonPathWithStringBody() throws JsonProcessingException {
        // given
        ConfigurationProperties.logLevel("TRACE");
        String template = "{" + NEW_LINE +
            "    'statusCode': 200," + NEW_LINE +
            "    'body': \"{'key': '{{#jsonPath}}$.store.book[0].title{{/jsonPath}}', 'value': '{{#jsonPath}}$.store.bicycle.color{{/jsonPath}}'}\"" + NEW_LINE +
            "}";
        HttpRequest request = request()
            .withPath("/somePath")
            .withBody("some_string_body");

        // when
        HttpResponse actualHttpResponse = new MustacheTemplateEngine(logFormatter).executeTemplate(template, request, HttpResponseDTO.class);

        // then
        assertThat(actualHttpResponse, is(
            response()
                .withStatusCode(200)
                .withBody("{'key': '', 'value': ''}")
        ));
        verify(logFormatter).logEvent(
            new LogEntry()
                .setType(TEMPLATE_GENERATED)
                .setLogLevel(INFO)
                .setHttpRequest(request)
                .setMessageFormat("generated output:{}from template:{}for request:{}")
                .setArguments(OBJECT_MAPPER.readTree("" +
                        "    {" + NEW_LINE +
                        "        'statusCode': 200," + NEW_LINE +
                        "        'body': \"{'key': '', 'value': ''}\"" + NEW_LINE +
                        "    }" + NEW_LINE),
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
        HttpResponse actualHttpResponse = new MustacheTemplateEngine(logFormatter).executeTemplate(template, request, HttpResponseDTO.class);

        // then
        assertThat(actualHttpResponse, is(
            response()
                .withStatusCode(200)
                .withBody("{'body': 'c29tZV9ib2R5'}")
        ));
        verify(logFormatter).logEvent(
            new LogEntry()
                .setType(TEMPLATE_GENERATED)
                .setLogLevel(INFO)
                .setHttpRequest(request)
                .setMessageFormat("generated output:{}from template:{}for request:{}")
                .setArguments(OBJECT_MAPPER.readTree("" +
                        "    {" + NEW_LINE +
                        "        'statusCode': 200," + NEW_LINE +
                        "        'body': \"{'body': 'c29tZV9ib2R5'}\"" + NEW_LINE +
                        "    }" + NEW_LINE),
                    template,
                    request
                )
        );
    }

    @Test
    public void shouldHandleHttpRequestsWithMustacheResponseTemplateInvalidFields() {
        // given
        String template = "{" + NEW_LINE +
            "    'statusCode': 200," + NEW_LINE +
            "    'body': \"{'method': '{{ request.method.invalid }}', 'path': '{{ request.invalid }}', 'headers': '{{ invalid.headers.host.0 }}'}\"" + NEW_LINE +
            "}";

        // when
        RuntimeException runtimeException = assertThrows(RuntimeException.class, () -> new MustacheTemplateEngine(logFormatter).executeTemplate(template, request()
                .withPath("/somePath")
                .withHeader(HOST.toString(), "mock-server.com")
                .withBody("some_body"),
            HttpRequestDTO.class
        ));

        // then
        assertThat(runtimeException.getMessage(), is("Exception:" + NEW_LINE +
            "" + NEW_LINE +
            "  No method or field with name 'request.method.invalid' on line 3" + NEW_LINE +
            "" + NEW_LINE +
            " transforming template:" + NEW_LINE +
            "" + NEW_LINE +
            "  {" + NEW_LINE +
            "      'statusCode': 200," + NEW_LINE +
            "      'body': \"{'method': '{{ request.method.invalid }}', 'path': '{{ request.invalid }}', 'headers': '{{ invalid.headers.host.0 }}'}\"" + NEW_LINE +
            "  }" + NEW_LINE +
            "" + NEW_LINE +
            " for request:" + NEW_LINE +
            "" + NEW_LINE +
            "  {" + NEW_LINE +
            "    \"path\" : \"/somePath\"," + NEW_LINE +
            "    \"headers\" : {" + NEW_LINE +
            "      \"host\" : [ \"mock-server.com\" ]" + NEW_LINE +
            "    }," + NEW_LINE +
            "    \"body\" : \"some_body\"" + NEW_LINE +
            "  }" + NEW_LINE));
    }

    @Test
    public void shouldHandleInvalidMustacheTemplate() {
        // given
        String template = "{{ {{ }} }}";

        // when
        RuntimeException runtimeException = assertThrows(RuntimeException.class, () -> new MustacheTemplateEngine(logFormatter).executeTemplate(template, request()
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
        MustacheTemplateEngine mustacheTemplateEngine = new MustacheTemplateEngine(logFormatter);

        ExecutorService newFixedThreadPool = Executors.newFixedThreadPool(30);

        List<Future<Boolean>> futures = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            futures.add(newFixedThreadPool.submit(() -> {
                assertThat(mustacheTemplateEngine.executeTemplate(template, request, HttpResponseDTO.class), is(
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
