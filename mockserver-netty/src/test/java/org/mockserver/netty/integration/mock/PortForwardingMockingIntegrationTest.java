package org.mockserver.netty.integration.mock;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockserver.client.MockServerClient;
import org.mockserver.configuration.ConfigurationProperties;
import org.mockserver.mock.Expectation;
import org.mockserver.model.*;
import org.mockserver.netty.MockServer;
import org.mockserver.testing.integration.mock.AbstractBasicMockingSameJVMIntegrationTest;
import org.mockserver.uuid.UUIDService;
import org.slf4j.event.Level;

import java.util.Arrays;

import static junit.framework.TestCase.assertEquals;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.endsWith;
import static org.mockserver.character.Character.NEW_LINE;
import static org.mockserver.matchers.Times.exactly;
import static org.mockserver.mock.OpenAPIExpectation.openAPIExpectation;
import static org.mockserver.model.Cookie.cookie;
import static org.mockserver.model.Cookie.schemaCookie;
import static org.mockserver.model.Header.header;
import static org.mockserver.model.Header.schemaHeader;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.notFoundResponse;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.HttpStatusCode.*;
import static org.mockserver.model.HttpStatusCode.NOT_FOUND_404;
import static org.mockserver.model.JsonBody.json;
import static org.mockserver.model.Parameter.param;
import static org.mockserver.model.Parameter.schemaParam;
import static org.mockserver.model.RegexBody.regex;
import static org.mockserver.model.StringBody.exact;
import static org.mockserver.stop.Stop.stopQuietly;

/**
 * @author jamesdbloom
 */
public class PortForwardingMockingIntegrationTest extends AbstractBasicMockingSameJVMIntegrationTest {

    private static int mockServerPort;

    @BeforeClass
    public static void startServer() {
        mockServerPort = new MockServer(insecureEchoServer.getPort(), "localhost", 0).getLocalPort();

        mockServerClient = new MockServerClient("localhost", mockServerPort);
    }

    @AfterClass
    public static void stopServer() {
        stopQuietly(mockServerClient);
    }

    @Override
    public int getServerPort() {
        return mockServerPort;
    }

    @Override
    protected HttpResponse localNotFoundResponse() {
        return response()
            .withStatusCode(OK_200.code())
            .withReasonPhrase(OK_200.reasonPhrase());
    }

    @Test
    @Override
    public void shouldRetrieveRecordedLogMessages() {
        Level originalLevel = ConfigurationProperties.logLevel();
        try {
            // given
            ConfigurationProperties.logLevel("INFO");
            UUIDService.fixedUUID = true;

            // when
            mockServerClient.reset();
            mockServerClient.when(request().withPath(calculatePath("some_path.*")), exactly(4)).respond(response().withBody("some_body"));
            assertEquals(
                response("some_body"),
                makeRequest(
                    request().withPath(calculatePath("some_path_one")),
                    getHeadersToRemove()
                )
            );
            assertEquals(
                notFoundResponse(),
                makeRequest(
                    request().withPath(calculatePath("not_found")),
                    getHeadersToRemove()
                )
            );
            assertEquals(
                response("some_body"),
                makeRequest(
                    request().withPath(calculatePath("some_path_three")),
                    getHeadersToRemove()
                )
            );

            // then
            String[] actualLogMessages = mockServerClient.retrieveLogMessagesArray(request().withPath(calculatePath(".*")));

            Object[] expectedLogMessages = new Object[]{
                "resetting all expectations and request logs",  // 0
                "creating expectation:" + NEW_LINE +  // 1
                    NEW_LINE +
                    "  {" + NEW_LINE +
                    "    \"httpRequest\" : {" + NEW_LINE +
                    "      \"path\" : \"/some_path.*\"" + NEW_LINE +
                    "    }," + NEW_LINE +
                    "    \"httpResponse\" : {" + NEW_LINE +
                    "      \"body\" : \"some_body\"" + NEW_LINE +
                    "    }," + NEW_LINE +
                    "    \"id\" : \"" + UUIDService.getUUID() + "\"," + NEW_LINE +
                    "    \"priority\" : 0," + NEW_LINE +
                    "    \"timeToLive\" : {" + NEW_LINE +
                    "      \"unlimited\" : true" + NEW_LINE +
                    "    }," + NEW_LINE +
                    "    \"times\" : {" + NEW_LINE +
                    "      \"remainingTimes\" : 4" + NEW_LINE +
                    "    }" + NEW_LINE +
                    "  }" + NEW_LINE +
                    NEW_LINE +
                    " with id:" + NEW_LINE +
                    NEW_LINE +
                    "  " + UUIDService.getUUID() + NEW_LINE,
                new String[]{  // 2
                    "received request:" + NEW_LINE +
                        NEW_LINE +
                        "  {" + NEW_LINE +
                        "    \"method\" : \"GET\"," + NEW_LINE +
                        "    \"path\" : \"/some_path_one\"," + NEW_LINE +
                        "    \"headers\" : {"
                },
                new String[]{  // 3
                    "request:" + NEW_LINE +
                        NEW_LINE +
                        "  {" + NEW_LINE +
                        "    \"method\" : \"GET\"," + NEW_LINE +
                        "    \"path\" : \"/some_path_one\",",
                    " matched expectation:" + NEW_LINE +
                        NEW_LINE +
                        "  {" + NEW_LINE +
                        "    \"httpRequest\" : {" + NEW_LINE +
                        "      \"path\" : \"/some_path.*\"" + NEW_LINE +
                        "    }," + NEW_LINE +
                        "    \"httpResponse\" : {" + NEW_LINE +
                        "      \"body\" : \"some_body\"" + NEW_LINE +
                        "    }," + NEW_LINE +
                        "    \"id\" : \"" + UUIDService.getUUID() + "\"," + NEW_LINE +
                        "    \"priority\" : 0," + NEW_LINE +
                        "    \"timeToLive\" : {" + NEW_LINE +
                        "      \"unlimited\" : true" + NEW_LINE +
                        "    }," + NEW_LINE +
                        "    \"times\" : {" + NEW_LINE +
                        "      \"remainingTimes\" : 4" + NEW_LINE +
                        "    }" + NEW_LINE +
                        "  }"
                },
                new String[]{ // 4
                    "returning response:" + NEW_LINE +
                        NEW_LINE +
                        "  {" + NEW_LINE +
                        "    \"body\" : \"some_body\"" + NEW_LINE +
                        "  }" + NEW_LINE +
                        NEW_LINE +
                        " for request:" + NEW_LINE +
                        NEW_LINE +
                        "  {" + NEW_LINE +
                        "    \"method\" : \"GET\"," + NEW_LINE +
                        "    \"path\" : \"/some_path_one\",",
                    " for action:" + NEW_LINE +
                        NEW_LINE +
                        "  {" + NEW_LINE +
                        "    \"body\" : \"some_body\"" + NEW_LINE +
                        "  }" + NEW_LINE
                },
                new String[]{ // 5
                    "received request:" + NEW_LINE +
                        NEW_LINE +
                        "  {" + NEW_LINE +
                        "    \"method\" : \"GET\"," + NEW_LINE +
                        "    \"path\" : \"/not_found\"," + NEW_LINE +
                        "    \"headers\" : {"
                },
                new String[]{ // 6
                    "request:" + NEW_LINE +
                        NEW_LINE +
                        "  {" + NEW_LINE +
                        "    \"method\" : \"GET\"," + NEW_LINE +
                        "    \"path\" : \"/not_found\",",
                    " didn't match expectation:" + NEW_LINE +
                        NEW_LINE +
                        "  {" + NEW_LINE +
                        "    \"httpRequest\" : {" + NEW_LINE +
                        "      \"path\" : \"/some_path.*\"" + NEW_LINE +
                        "    }," + NEW_LINE +
                        "    \"httpResponse\" : {" + NEW_LINE +
                        "      \"body\" : \"some_body\"" + NEW_LINE +
                        "    }," + NEW_LINE +
                        "    \"id\" : \"" + UUIDService.getUUID() + "\"," + NEW_LINE +
                        "    \"priority\" : 0," + NEW_LINE +
                        "    \"timeToLive\" : {" + NEW_LINE +
                        "      \"unlimited\" : true" + NEW_LINE +
                        "    }," + NEW_LINE +
                        "    \"times\" : {" + NEW_LINE +
                        "      \"remainingTimes\" : 3" + NEW_LINE +
                        "    }" + NEW_LINE +
                        "  }" + NEW_LINE +
                        NEW_LINE +
                        " because:" + NEW_LINE +
                        NEW_LINE +
                        "  method matched" + NEW_LINE +
                        "  path didn't match" + NEW_LINE
                },
                new String[]{ // 7
                    "no expectation for:" + NEW_LINE +
                        NEW_LINE +
                        "  {" + NEW_LINE +
                        "    \"method\" : \"GET\"," + NEW_LINE +
                        "    \"path\" : \"/not_found\"",
                    " returning response:" + NEW_LINE +
                        NEW_LINE +
                        "  {" + NEW_LINE +
                        "    \"statusCode\" : 404," + NEW_LINE +
                        "    \"reasonPhrase\" : \"Not Found\""
                },
                new String[]{ // 8
                    "received request:" + NEW_LINE +
                        NEW_LINE +
                        "  {" + NEW_LINE +
                        "    \"method\" : \"GET\"," + NEW_LINE +
                        "    \"path\" : \"/some_path_three\"," + NEW_LINE +
                        "    \"headers\" : {"
                },
                new String[]{ // 9
                    "request:" + NEW_LINE +
                        NEW_LINE +
                        "  {" + NEW_LINE +
                        "    \"method\" : \"GET\"," + NEW_LINE +
                        "    \"path\" : \"/some_path_three\",",
                    " matched expectation:" + NEW_LINE +
                        NEW_LINE +
                        "  {" + NEW_LINE +
                        "    \"httpRequest\" : {" + NEW_LINE +
                        "      \"path\" : \"/some_path.*\"" + NEW_LINE +
                        "    }," + NEW_LINE +
                        "    \"httpResponse\" : {" + NEW_LINE +
                        "      \"body\" : \"some_body\"" + NEW_LINE +
                        "    }," + NEW_LINE +
                        "    \"id\" : \"" + UUIDService.getUUID() + "\"," + NEW_LINE +
                        "    \"priority\" : 0," + NEW_LINE +
                        "    \"timeToLive\" : {" + NEW_LINE +
                        "      \"unlimited\" : true" + NEW_LINE +
                        "    }," + NEW_LINE +
                        "    \"times\" : {" + NEW_LINE +
                        "      \"remainingTimes\" : 3" + NEW_LINE +
                        "    }" + NEW_LINE +
                        "  }"
                },
                new String[]{ // 10
                    "returning response:" + NEW_LINE +
                        NEW_LINE +
                        "  {" + NEW_LINE +
                        "    \"body\" : \"some_body\"" + NEW_LINE +
                        "  }" + NEW_LINE +
                        NEW_LINE +
                        " for request:" + NEW_LINE +
                        NEW_LINE +
                        "  {" + NEW_LINE +
                        "    \"method\" : \"GET\"," + NEW_LINE +
                        "    \"path\" : \"/some_path_three\",",
                    " for action:" + NEW_LINE +
                        NEW_LINE +
                        "  {" + NEW_LINE +
                        "    \"body\" : \"some_body\"" + NEW_LINE +
                        "  }" + NEW_LINE
                }
            };

            for (int i = 0; i < expectedLogMessages.length; i++) {
                if (expectedLogMessages[i] instanceof String) {
                    assertThat("matching log message " + i + "\nActual:" + NEW_LINE + Arrays.toString(actualLogMessages), actualLogMessages[i], endsWith((String) expectedLogMessages[i]));
                } else if (expectedLogMessages[i] instanceof String[]) {
                    String[] expectedLogMessage = (String[]) expectedLogMessages[i];
                    for (int j = 0; j < expectedLogMessage.length; j++) {
                        assertThat("matching log message " + i + "-" + j + "\nActual:" + NEW_LINE + Arrays.toString(actualLogMessages), actualLogMessages[i], containsString(expectedLogMessage[j]));
                    }
                }
            }
        } finally {
            UUIDService.fixedUUID = false;
            ConfigurationProperties.logLevel(originalLevel.name());
        }
    }

    @Test
    public void shouldReturnResponseByMatchingSchemaHeaderCookieAndParameter() {
        // when
        mockServerClient
            .when(
                request()
                    .withHeader(schemaHeader(
                        "headerName", "{" + NEW_LINE +
                            "   \"type\": \"string\"," + NEW_LINE +
                            "   \"pattern\": \"^headerVal[a-z]{2}$\"" + NEW_LINE +
                            "}"
                    ))
                    .withQueryStringParameter(schemaParam(
                        "parameterName", "{" + NEW_LINE +
                            "   \"type\": \"string\"," + NEW_LINE +
                            "   \"pattern\": \"^parameterVal[a-z]{2}$\"" + NEW_LINE +
                            "}"
                    ))
                    .withCookie(schemaCookie(
                        "cookieName", "{" + NEW_LINE +
                            "   \"type\": \"string\"," + NEW_LINE +
                            "   \"pattern\": \"^cookieVal[a-z]{2}$\"" + NEW_LINE +
                            "}"
                    ))
            )
            .respond(
                response()
                    .withStatusCode(200)
            );

        // then
        assertEquals(
            response()
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase()),
            makeRequest(
                request()
                    .withPath(calculatePath("some_path?parameterName=parameterValue"))
                    .withHeader("headerName", "headerValue")
                    .withCookie("cookieName", "cookieValue"),
                getHeadersToRemove()
            )
        );

        // then
        assertEquals(
            response()
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase())
                .withHeader("headerName", "headerValue")
                .withHeader("cookie", "cookieName=cookieValue")
                .withHeader("set-cookie", "cookieName=cookieValue")
                .withCookie("cookieName", "cookieValue"),
            makeRequest(
                request()
                    .withPath(calculatePath("some_path?parameterName=parameterOtherValue"))
                    .withHeader("headerName", "headerValue")
                    .withCookie("cookieName", "cookieValue"),
                getHeadersToRemove()
            )
        );

        // then
        assertEquals(
            response()
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase())
                .withHeader("headerName", "headerOtherValue")
                .withHeader("cookie", "cookieName=cookieValue")
                .withHeader("set-cookie", "cookieName=cookieValue")
                .withCookie("cookieName", "cookieValue"),
            makeRequest(
                request()
                    .withPath(calculatePath("some_path?parameterName=parameterValue"))
                    .withHeader("headerName", "headerOtherValue")
                    .withCookie("cookieName", "cookieValue"),
                getHeadersToRemove()
            )
        );

        // then
        assertEquals(
            response()
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase())
                .withHeader("headerName", "headerValue")
                .withHeader("cookie", "cookieName=cookieOtherValue")
                .withHeader("set-cookie", "cookieName=cookieOtherValue")
                .withCookie("cookieName", "cookieOtherValue"),
            makeRequest(
                request()
                    .withPath(calculatePath("some_path?parameterName=parameterValue"))
                    .withHeader("headerName", "headerValue")
                    .withCookie("cookieName", "cookieOtherValue")
                ,
                getHeadersToRemove()
            )
        );
    }

    @Test
    @Override
    public void shouldReturnResponseByMatchingNotBody() {
        // when
        mockServerClient
            .when(
                request()
                    .withPath(calculatePath("some_path"))
                    .withBody(Not.not(regex(".+")))
            )
            .respond(
                response()
                    .withBody("some_response_body")
            );

        // then
        assertEquals(
            response()
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase())
                .withBody("some_response_body"),
            makeRequest(
                request()
                    .withMethod("POST")
                    .withPath(calculatePath("some_path")),
                getHeadersToRemove()
            )
        );
        assertEquals(
            response()
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase())
                .withBody(exact("some_random_body")),
            makeRequest(
                request()
                    .withMethod("POST")
                    .withPath(calculatePath("some_path"))
                    .withBody("some_random_body"),
                getHeadersToRemove()
            )
        );
    }

    @Test
    @Override
    public void shouldReturnResponseByMatchingOpenAPIExpectationWithContentTypeWithSpecialCharacters() throws JsonProcessingException {
        // when
        Expectation[] upsertedExpectations = mockServerClient
            .upsert(
                openAPIExpectation("{" + NEW_LINE +
                    "    \"openapi\": \"3.0.3\", " + NEW_LINE +
                    "    \"info\": {" + NEW_LINE +
                    "        \"title\": \"OAS 3.0.3 sample\", " + NEW_LINE +
                    "        \"version\": \"0.1.0\"" + NEW_LINE +
                    "    }, " + NEW_LINE +
                    "    \"paths\": {" + NEW_LINE +
                    "        \"/test\": {" + NEW_LINE +
                    "            \"post\": {" + NEW_LINE +
                    "                \"requestBody\": {" + NEW_LINE +
                    "                    \"$ref\": \"#/components/requestBodies/testRequest\"" + NEW_LINE +
                    "                }, " + NEW_LINE +
                    "                \"responses\": {" + NEW_LINE +
                    "                    \"200\": {" + NEW_LINE +
                    "                        \"description\": \"some response\"" + NEW_LINE +
                    "                    }" + NEW_LINE +
                    "                }" + NEW_LINE +
                    "            }" + NEW_LINE +
                    "        }" + NEW_LINE +
                    "    }, " + NEW_LINE +
                    "    \"components\": {" + NEW_LINE +
                    "        \"requestBodies\": {" + NEW_LINE +
                    "            \"testRequest\": {" + NEW_LINE +
                    "                \"required\": true, " + NEW_LINE +
                    "                \"content\": {" + NEW_LINE +
                    "                    \"application/vnd.api+json\": {" + NEW_LINE +
                    "                        \"schema\": {" + NEW_LINE +
                    "                            \"type\": \"object\"" + NEW_LINE +
                    "                        }" + NEW_LINE +
                    "                    }" + NEW_LINE +
                    "                }" + NEW_LINE +
                    "            }" + NEW_LINE +
                    "        }" + NEW_LINE +
                    "    }" + NEW_LINE +
                    "}")
            );

        // then
        assertEquals(
            response()
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase()),
            makeRequest(
                request()
                    .withMethod("POST")
                    .withPath("/test")
                    .withHeader("content-type", "application/vnd.api+json")
                    .withBody(json("{}")),
                getHeadersToRemove()
            )
        );
        assertEquals(
            response()
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase()),
            makeRequest(
                request()
                    .withMethod("POST")
                    .withPath("/test")
                    .withHeader("content-type", "application/vnd.api+json; charset=utf8")
                    .withBody(json("{}")),
                getHeadersToRemove()
            )
        );

        // and - dot only matches a dot
        assertEquals(
            response()
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase())
                .withHeader("content-type", "application/vndXapi+json")
                .withBody(json("{}", MediaType.parse("application/vndXapi+json"))),
            makeRequest(
                request()
                    .withMethod("POST")
                    .withPath("/test")
                    .withHeader("content-type", "application/vndXapi+json")
                    .withBody(json("{}")),
                getHeadersToRemove()
            )
        );
    }

    @Test
    @Override
    public void shouldNotReturnResponseForNonMatchingBody() {
        // when
        mockServerClient
            .when(
                request()
                    .withMethod("GET")
                    .withPath(calculatePath("some_path"))
                    .withQueryStringParameters(
                        param("queryStringParameterOneName", "queryStringParameterOneValue"),
                        param("queryStringParameterTwoName", "queryStringParameterTwoValue")
                    )
                    .withBody(exact("some_body"))
                    .withHeaders(header("headerName", "headerValue"))
                    .withCookies(cookie("cookieName", "cookieValue"))
            )
            .respond(
                response()
                    .withStatusCode(HttpStatusCode.ACCEPTED_202.code())
                    .withReasonPhrase(HttpStatusCode.ACCEPTED_202.reasonPhrase())
                    .withBody("some_body")
                    .withHeaders(header("headerName", "headerValue"))
                    .withCookies(cookie("cookieName", "cookieValue"))
            );

        // then
        // - in http
        assertEquals(
            response()
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase())
                .withBody(exact("some_other_body"))
                .withHeader(header("set-cookie", "cookieName=cookieValue"))
                .withHeader(header("headerName", "headerValue"))
                .withHeader(header("cookie", "cookieName=cookieValue"))
                .withCookies(cookie("cookieName", "cookieValue")),
            makeRequest(
                request()
                    .withMethod("GET")
                    .withPath(calculatePath("some_path"))
                    .withQueryStringParameters(
                        param("queryStringParameterOneName", "queryStringParameterOneValue"),
                        param("queryStringParameterTwoName", "queryStringParameterTwoValue")
                    )
                    .withBody(exact("some_other_body"))
                    .withHeaders(header("headerName", "headerValue"))
                    .withCookies(cookie("cookieName", "cookieValue")),
                getHeadersToRemove()
            )
        );
    }

    @Test
    @Override
    public void shouldNotReturnResponseForNonMatchingPath() {
        // when
        mockServerClient
            .when(
                request()
                    .withMethod("GET")
                    .withPath(calculatePath("some_path"))
                    .withQueryStringParameters(
                        param("queryStringParameterOneName", "queryStringParameterOneValue"),
                        param("queryStringParameterTwoName", "queryStringParameterTwoValue")
                    )
                    .withBody(exact("some_body"))
                    .withHeaders(header("headerName", "headerValue"))
                    .withCookies(cookie("cookieName", "cookieValue"))
            )
            .respond(
                response()
                    .withStatusCode(HttpStatusCode.ACCEPTED_202.code())
                    .withReasonPhrase(HttpStatusCode.ACCEPTED_202.reasonPhrase())
                    .withBody("some_body")
                    .withHeaders(header("headerName", "headerValue"))
                    .withCookies(cookie("cookieName", "cookieValue"))
            );

        // then
        // - in http
        assertEquals(
            response()
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase())
                .withBody(exact("some_body"))
                .withHeader(header("set-cookie", "cookieName=cookieValue"))
                .withHeader(header("headerName", "headerValue"))
                .withHeader(header("cookie", "cookieName=cookieValue"))
                .withCookies(cookie("cookieName", "cookieValue")),
            makeRequest(
                request()
                    .withMethod("GET")
                    .withPath(calculatePath("some_other_path"))
                    .withQueryStringParameters(
                        param("queryStringParameterOneName", "queryStringParameterOneValue"),
                        param("queryStringParameterTwoName", "queryStringParameterTwoValue")
                    )
                    .withBody(exact("some_body"))
                    .withHeaders(header("headerName", "headerValue"))
                    .withCookies(cookie("cookieName", "cookieValue")),
                getHeadersToRemove()
            )
        );
    }

    @Test
    public void shouldNotReturnResponseForNottedHeader() {
        // when
        mockServerClient
            .when(
                request()
                    .withMethod("GET")
                    .withHeaders(new Headers(header(NottableString.not("headerName"))))
            )
            .respond(
                response()
                    .withStatusCode(ACCEPTED_202.code())
                    .withReasonPhrase(ACCEPTED_202.reasonPhrase())
                    .withBody("some_body")
            );

        // then
        assertEquals(
            response()
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase())
                .withHeaders(header("headerName", "headerValue")),
            makeRequest(
                request()
                    .withMethod("GET")
                    .withHeaders(header("headerName", "headerValue")),
                getHeadersToRemove()
            )
        );

        // then
        assertEquals(
            response()
                .withStatusCode(ACCEPTED_202.code())
                .withReasonPhrase(ACCEPTED_202.reasonPhrase())
                .withBody("some_body"),
            makeRequest(
                request()
                    .withMethod("GET")
                    .withHeaders(header("otherHeaderName", "headerValue")),
                getHeadersToRemove()
            )
        );
    }

}
