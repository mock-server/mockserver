package org.mockserver.testing.integration.mock;

import com.google.common.util.concurrent.Uninterruptibles;
import org.apache.commons.io.IOUtils;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockserver.log.TimeService;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.matchers.MatchType;
import org.mockserver.matchers.TimeToLive;
import org.mockserver.mock.Expectation;
import org.mockserver.model.*;
import org.mockserver.serialization.ExpectationSerializer;
import org.mockserver.serialization.HttpRequestSerializer;
import org.mockserver.serialization.LogEntrySerializer;
import org.mockserver.serialization.java.ExpectationToJavaSerializer;
import org.mockserver.verify.VerificationTimes;
import org.slf4j.event.Level;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static io.netty.handler.codec.http.HttpHeaderNames.*;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.StringStartsWith.startsWith;
import static org.hamcrest.number.OrderingComparison.greaterThan;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockserver.character.Character.NEW_LINE;
import static org.mockserver.configuration.ConfigurationProperties.maxFutureTimeout;
import static org.mockserver.log.model.LogEntry.LogMessageType.RECEIVED_REQUEST;
import static org.mockserver.matchers.Times.exactly;
import static org.mockserver.matchers.Times.unlimited;
import static org.mockserver.model.BinaryBody.binary;
import static org.mockserver.model.Cookie.cookie;
import static org.mockserver.model.Header.header;
import static org.mockserver.model.HttpForward.forward;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.notFoundResponse;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.HttpStatusCode.*;
import static org.mockserver.model.JsonBody.json;
import static org.mockserver.model.JsonPathBody.jsonPath;
import static org.mockserver.model.JsonSchemaBody.jsonSchema;
import static org.mockserver.model.NottableString.not;
import static org.mockserver.model.NottableString.string;
import static org.mockserver.model.Parameter.param;
import static org.mockserver.model.ParameterBody.params;
import static org.mockserver.model.RegexBody.regex;
import static org.mockserver.model.StringBody.exact;
import static org.mockserver.model.StringBody.subString;
import static org.mockserver.model.XPathBody.xpath;
import static org.mockserver.model.XmlBody.xml;
import static org.mockserver.model.XmlSchemaBody.xmlSchema;
import static org.mockserver.model.XmlSchemaBody.xmlSchemaFromResource;

/**
 * @author jamesdbloom
 */
public abstract class AbstractExtendedMockingIntegrationTest extends AbstractBasicMockingIntegrationTest {

    @BeforeClass
    public static void fixTime() {
        TimeService.fixedTime = true;
    }

    @Test
    public void shouldReturnResponseForRequestInSsl() {
        // when
        mockServerClient.when(request().withSecure(true)).respond(response().withBody("some_body"));

        // then
        // - in http
        assertEquals(
            response()
                .withStatusCode(HttpStatusCode.NOT_FOUND_404.code())
                .withReasonPhrase(HttpStatusCode.NOT_FOUND_404.reasonPhrase()),
            makeRequest(
                request()
                    .withPath(calculatePath("")),
                headersToIgnore)
        );
        // - in https
        assertEquals(
            response()
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase())
                .withBody("some_body"),
            makeRequest(
                request()
                    .withSecure(true)
                    .withPath(calculatePath("")),
                headersToIgnore)
        );
    }

    @Test
    public void shouldReturnResponseForRequestNotInSsl() {
        // when
        mockServerClient.when(request().withSecure(false)).respond(response().withBody("some_body"));

        // then
        // - in http
        assertEquals(
            response()
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase())
                .withBody("some_body"),
            makeRequest(
                request()
                    .withPath(calculatePath("")),
                headersToIgnore)
        );
        // - in https
        assertEquals(
            response()
                .withStatusCode(HttpStatusCode.NOT_FOUND_404.code())
                .withReasonPhrase(HttpStatusCode.NOT_FOUND_404.reasonPhrase()),
            makeRequest(
                request()
                    .withSecure(true)
                    .withPath(calculatePath("")),
                headersToIgnore)
        );
    }

    @Test
    public void shouldReturnResponseByMatchingPath() {
        // when
        mockServerClient
            .when(
                request()
                    .withPath(calculatePath("some_path1"))
            )
            .respond(
                response()
                    .withBody("some_body1")
            );
        mockServerClient
            .when(
                request()
                    .withPath(calculatePath("some_path2"))
            )
            .respond(
                response()
                    .withBody("some_body2")
            );

        // then
        assertEquals(
            response()
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase())
                .withBody("some_body2"),
            makeRequest(
                request()
                    .withPath(calculatePath("some_path2")),
                headersToIgnore)
        );
        assertEquals(
            response()
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase())
                .withBody("some_body1"),
            makeRequest(
                request()
                    .withPath(calculatePath("some_path1")),
                headersToIgnore)
        );
    }

    @Test
    public void shouldReturnResponseByMatchingPathExactTimes() {
        // when
        mockServerClient
            .when(
                request()
                    .withPath(calculatePath("some_path")),
                exactly(2)
            )
            .respond(
                response()
                    .withBody("some_body")
            );

        // then
        assertEquals(
            response()
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase())
                .withBody("some_body"),
            makeRequest(
                request()
                    .withPath(calculatePath("some_path")),
                headersToIgnore)
        );
        assertEquals(
            response()
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase())
                .withBody("some_body"),
            makeRequest(
                request()
                    .withSecure(true)
                    .withPath(calculatePath("some_path")),
                headersToIgnore)
        );
        assertEquals(
            response()
                .withStatusCode(HttpStatusCode.NOT_FOUND_404.code())
                .withReasonPhrase(HttpStatusCode.NOT_FOUND_404.reasonPhrase()),
            makeRequest(
                request()
                    .withPath(calculatePath("some_path")),
                headersToIgnore)
        );
    }

    @Test
    public void shouldReturnResponseByMatchingPathInOrderOfCreationExactTimes() {
        // when
        mockServerClient
            .when(
                request()
                    .withPath(calculatePath("some_path")),
                exactly(1)
            )
            .respond(
                response()
                    .withBody("some_body_one")
            );
        mockServerClient
            .when(
                request()
                    .withPath(calculatePath("some_path")),
                exactly(1)
            )
            .respond(
                response()
                    .withBody("some_body_two")
            );

        // then
        assertEquals(
            response()
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase())
                .withBody("some_body_one"),
            makeRequest(
                request()
                    .withPath(calculatePath("some_path")),
                headersToIgnore)
        );
        assertEquals(
            response()
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase())
                .withBody("some_body_two"),
            makeRequest(
                request()
                    .withSecure(true)
                    .withPath(calculatePath("some_path")),
                headersToIgnore)
        );
        assertEquals(
            response()
                .withStatusCode(HttpStatusCode.NOT_FOUND_404.code())
                .withReasonPhrase(HttpStatusCode.NOT_FOUND_404.reasonPhrase()),
            makeRequest(
                request()
                    .withPath(calculatePath("some_path")),
                headersToIgnore)
        );
    }

    @Test
    public void shouldReturnResponseByMatchingPathInOrderOfCreationBeforeExpiry() throws InterruptedException {
        // when
        mockServerClient
            .when(
                request()
                    .withPath(calculatePath("some_path")),
                unlimited(),
                TimeToLive.exactly(SECONDS, 2L)
            )
            .respond(
                response()
                    .withBody("some_body_one")
            );
        mockServerClient
            .when(
                request()
                    .withPath(calculatePath("some_path")),
                unlimited(),
                TimeToLive.exactly(SECONDS, 4L)
            )
            .respond(
                response()
                    .withBody("some_body_two")
            );

        // then
        assertEquals(
            response()
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase())
                .withBody("some_body_one"),
            makeRequest(
                request()
                    .withPath(calculatePath("some_path")),
                headersToIgnore)
        );
        MILLISECONDS.sleep(2500);
        assertEquals(
            response()
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase())
                .withBody("some_body_two"),
            makeRequest(
                request()
                    .withSecure(true)
                    .withPath(calculatePath("some_path")),
                headersToIgnore)
        );
        MILLISECONDS.sleep(2250);
        assertEquals(
            response()
                .withStatusCode(HttpStatusCode.NOT_FOUND_404.code())
                .withReasonPhrase(HttpStatusCode.NOT_FOUND_404.reasonPhrase()),
            makeRequest(
                request()
                    .withPath(calculatePath("some_path")),
                headersToIgnore)
        );
    }

    @Test
    public void shouldReturnResponseByMatchingPathInOrderOfPriorityExactTimes() {
        // when
        mockServerClient
            .when(
                request()
                    .withPath(calculatePath("some_path")),
                exactly(1),
                TimeToLive.unlimited(),
                0
            )
            .respond(
                response()
                    .withBody("some_body_one")
            );
        mockServerClient
            .when(
                request()
                    .withPath(calculatePath("some_path")),
                exactly(1),
                TimeToLive.unlimited(),
                10
            )
            .respond(
                response()
                    .withBody("some_body_two")
            );

        // then
        assertEquals(
            response()
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase())
                .withBody("some_body_two"),
            makeRequest(
                request()
                    .withPath(calculatePath("some_path")),
                headersToIgnore)
        );
        assertEquals(
            response()
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase())
                .withBody("some_body_one"),
            makeRequest(
                request()
                    .withSecure(true)
                    .withPath(calculatePath("some_path")),
                headersToIgnore)
        );
        assertEquals(
            response()
                .withStatusCode(HttpStatusCode.NOT_FOUND_404.code())
                .withReasonPhrase(HttpStatusCode.NOT_FOUND_404.reasonPhrase()),
            makeRequest(
                request()
                    .withPath(calculatePath("some_path")),
                headersToIgnore)
        );
    }

    @Test
    public void shouldReturnResponseByMatchingPathInOrderOfPriorityWithNegativePriorities() {
        // when
        mockServerClient
            .when(
                request()
                    .withPath(calculatePath("some_path")),
                exactly(1),
                TimeToLive.unlimited(),
                -10
            )
            .respond(
                response()
                    .withBody("some_body_one")
            );
        mockServerClient
            .when(
                request()
                    .withPath(calculatePath("some_path")),
                exactly(1),
                TimeToLive.unlimited(),
                0
            )
            .respond(
                response()
                    .withBody("some_body_two")
            );

        // then
        assertEquals(
            response()
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase())
                .withBody("some_body_two"),
            makeRequest(
                request()
                    .withPath(calculatePath("some_path")),
                headersToIgnore)
        );
        assertEquals(
            response()
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase())
                .withBody("some_body_one"),
            makeRequest(
                request()
                    .withSecure(true)
                    .withPath(calculatePath("some_path")),
                headersToIgnore)
        );
        assertEquals(
            response()
                .withStatusCode(HttpStatusCode.NOT_FOUND_404.code())
                .withReasonPhrase(HttpStatusCode.NOT_FOUND_404.reasonPhrase()),
            makeRequest(
                request()
                    .withPath(calculatePath("some_path")),
                headersToIgnore)
        );
    }

    @Test
    public void shouldReturnResponseByMatchingPathInOrderOfPriorityWithPriorityUpdate() {
        // when
        Expectation expectationOne = new Expectation(request().withPath(calculatePath("some_path")), unlimited(), TimeToLive.unlimited(), 0)
            .thenRespond(
                response()
                    .withBody("some_body_one")
            )
            .withId("one");
        Expectation expectationTwo = new Expectation(request().withPath(calculatePath("some_path")), unlimited(), TimeToLive.unlimited(), 10)
            .thenRespond(
                response()
                    .withBody("some_body_two")
            )
            .withId("two");
        Expectation[] upsertedExpectations = mockServerClient
            .upsert(
                expectationOne,
                expectationTwo
            );

        // then
        assertThat(upsertedExpectations.length, is(2));
        assertThat(upsertedExpectations[0], is(expectationOne));
        assertThat(upsertedExpectations[1], is(expectationTwo));
        assertEquals(
            response()
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase())
                .withBody("some_body_two"),
            makeRequest(
                request()
                    .withPath(calculatePath("some_path")),
                headersToIgnore)
        );

        // when
        Expectation expectationOneWithHigherPriority = new Expectation(request().withPath(calculatePath("some_path")), unlimited(), TimeToLive.unlimited(), 15)
            .thenRespond(
                response()
                    .withBody("some_body_one")
            )
            .withId("one");
        upsertedExpectations = mockServerClient
            .upsert(
                expectationOneWithHigherPriority
            );

        // then
        assertThat(upsertedExpectations.length, is(1));
        assertThat(upsertedExpectations[0], is(expectationOneWithHigherPriority));
        assertEquals(
            response()
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase())
                .withBody("some_body_one"),
            makeRequest(
                request()
                    .withSecure(true)
                    .withPath(calculatePath("some_path")),
                headersToIgnore)
        );
    }

    @Test
    public void shouldReturnResponseByMatchingPathInOrderOfPriorityWithPriorityUpdateAndExactTimes() {
        // when
        Expectation expectationOne = new Expectation(request().withPath(calculatePath("some_path")), exactly(1), TimeToLive.unlimited(), 0)
            .thenRespond(
                response()
                    .withBody("some_body_one")
            );
        Expectation expectationTwo = new Expectation(request().withPath(calculatePath("some_path")), exactly(1), TimeToLive.unlimited(), 10)
            .thenRespond(
                response()
                    .withBody("some_body_two")
            );
        Expectation[] upsertedExpectations = mockServerClient
            .upsert(
                expectationOne,
                expectationTwo
            );

        // then
        assertThat(upsertedExpectations.length, is(2));
        assertThat(upsertedExpectations[0], is(expectationOne));
        assertThat(upsertedExpectations[1], is(expectationTwo));
        assertEquals(
            response()
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase())
                .withBody("some_body_two"),
            makeRequest(
                request()
                    .withPath(calculatePath("some_path")),
                headersToIgnore)
        );

        // when
        Expectation expectationOneWithHigherPriority = new Expectation(request().withPath(calculatePath("some_path")), exactly(1), TimeToLive.unlimited(), 15)
            .withId(upsertedExpectations[0].getId())
            .thenRespond(
                response()
                    .withBody("some_body_one")
            );
        upsertedExpectations = mockServerClient
            .upsert(
                expectationOneWithHigherPriority,
                expectationTwo
            );

        // then
        assertThat(upsertedExpectations.length, is(2));
        assertThat(upsertedExpectations[0], is(expectationOneWithHigherPriority));
        assertEquals(
            response()
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase())
                .withBody("some_body_one"),
            makeRequest(
                request()
                    .withSecure(true)
                    .withPath(calculatePath("some_path")),
                headersToIgnore)
        );
        assertEquals(
            response()
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase())
                .withBody("some_body_two"),
            makeRequest(
                request()
                    .withPath(calculatePath("some_path")),
                headersToIgnore)
        );
        assertEquals(
            response()
                .withStatusCode(HttpStatusCode.NOT_FOUND_404.code())
                .withReasonPhrase(HttpStatusCode.NOT_FOUND_404.reasonPhrase()),
            makeRequest(
                request()
                    .withPath(calculatePath("some_path")),
                headersToIgnore)
        );
    }

    @Test
    public void shouldUpdateExistingExpectation() {
        // when
        Expectation expectationOne = new Expectation(request().withPath(calculatePath("some_path_one")))
            .thenRespond(
                response()
                    .withBody("some_body_one")
            );
        Expectation expectationTwo = new Expectation(request().withPath(calculatePath("some_path_two")))
            .thenRespond(
                response()
                    .withBody("some_body_two")
            );
        mockServerClient
            .upsert(
                expectationOne,
                expectationTwo
            );

        // then
        assertEquals(
            response()
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase())
                .withBody("some_body_one"),
            makeRequest(
                request()
                    .withPath(calculatePath("some_path_one")),
                headersToIgnore)
        );
        assertEquals(
            response()
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase())
                .withBody("some_body_two"),
            makeRequest(
                request()
                    .withPath(calculatePath("some_path_two")),
                headersToIgnore)
        );

        // when
        Expectation expectationOneUpdated = new Expectation(request().withPath(calculatePath("some_path_updated")))
            .thenRespond(
                response()
                    .withBody("some_body_one_updated")
            );
        mockServerClient
            .upsert(
                expectationOneUpdated
            );
        assertEquals(
            response()
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase())
                .withBody("some_body_one_updated"),
            makeRequest(
                request()
                    .withSecure(true)
                    .withPath(calculatePath("some_path_updated")),
                headersToIgnore)
        );
        assertEquals(
            response()
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase())
                .withBody("some_body_two"),
            makeRequest(
                request()
                    .withPath(calculatePath("some_path_two")),
                headersToIgnore)
        );
    }

    @Test
    public void shouldReturnResponseWhenTimeToLiveHasNotExpired() {
        // when
        mockServerClient
            .when(
                request().withPath(calculatePath("some_path")),
                exactly(1),
                TimeToLive.exactly(TimeUnit.HOURS, 1L)
            )
            .respond(
                response().withBody("some_body")
            );

        // then
        assertEquals(
            response()
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase())
                .withBody("some_body"),
            makeRequest(
                request()
                    .withPath(calculatePath("some_path")),
                headersToIgnore)
        );
        assertEquals(
            response()
                .withStatusCode(HttpStatusCode.NOT_FOUND_404.code())
                .withReasonPhrase(HttpStatusCode.NOT_FOUND_404.reasonPhrase()),
            makeRequest(
                request()
                    .withPath(calculatePath("some_path")),
                headersToIgnore)
        );
    }

    @Test
    public void shouldReturnMatchRequestWithBodyInUTF16() {
        // when
        String body = "我说中国话";
        mockServerClient
            .when(
                request()
                    .withBody(body, StandardCharsets.UTF_16)
            )
            .respond(
                response()
                    .withBody(body, StandardCharsets.UTF_8)
            );

        // then
        assertEquals(
            response()
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase())
                .withHeader(CONTENT_TYPE.toString(), MediaType.PLAIN_TEXT_UTF_8.toString())
                .withBody(body, MediaType.PLAIN_TEXT_UTF_8),
            makeRequest(
                request()
                    .withPath(calculatePath(""))
                    .withBody(body, StandardCharsets.UTF_16),
                headersToIgnore)
        );
    }

    @Test
    public void shouldReturnMatchRequestWithBodyInUTF8WithContentTypeHeader() {
        // when
        String body = "我说中国话";
        mockServerClient
            .when(
                request()
                    .withHeader(CONTENT_TYPE.toString(), MediaType.PLAIN_TEXT_UTF_8.toString())
                    .withBody(body)
            )
            .respond(
                response()
                    .withBody(body, StandardCharsets.UTF_8)
            );

        // then
        assertEquals(
            response()
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase())
                .withHeader(CONTENT_TYPE.toString(), MediaType.PLAIN_TEXT_UTF_8.toString())
                .withBody(body, MediaType.PLAIN_TEXT_UTF_8),
            makeRequest(
                request()
                    .withHeader(CONTENT_TYPE.toString(), MediaType.PLAIN_TEXT_UTF_8.toString())
                    .withPath(calculatePath(""))
                    .withBody(body, StandardCharsets.UTF_8),
                headersToIgnore)
        );
    }

    @Test
    public void shouldReturnResponseWithBodyInUTF16() {
        // when
        String body = "我说中国话";
        mockServerClient
            .when(
                request()
                    .withHeader(CONTENT_TYPE.toString(), MediaType.create("text", "plain").withCharset(StandardCharsets.UTF_16).toString())
                    .withBody(body)
            )
            .respond(
                response()
                    .withHeader(CONTENT_TYPE.toString(), MediaType.create("text", "plain").withCharset(StandardCharsets.UTF_16).toString())
                    .withBody(body, StandardCharsets.UTF_16)
            );

        // then
        assertEquals(
            response()
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase())
                .withHeader(CONTENT_TYPE.toString(), MediaType.create("text", "plain").withCharset(StandardCharsets.UTF_16).toString())
                .withBody(body, StandardCharsets.UTF_16),
            makeRequest(
                request()
                    .withHeader(CONTENT_TYPE.toString(), MediaType.create("text", "plain").withCharset(StandardCharsets.UTF_16).toString())
                    .withPath(calculatePath(""))
                    .withBody(body),
                headersToIgnore)
        );
    }

    @Test
    public void shouldReturnResponseWithBodyInUTF8WithContentTypeHeader() {
        // when
        String body = "我说中国话";
        mockServerClient
            .when(
                request()
            )
            .respond(
                response()
                    .withHeader(CONTENT_TYPE.toString(), MediaType.PLAIN_TEXT_UTF_8.toString())
                    .withBody(body)
            );

        // then
        assertEquals(
            response()
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase())
                .withHeader(CONTENT_TYPE.toString(), MediaType.PLAIN_TEXT_UTF_8.toString())
                .withBody(body, MediaType.PLAIN_TEXT_UTF_8),
            makeRequest(
                request()
                    .withPath(calculatePath("")),
                headersToIgnore)
        );
    }

    @Test
    public void shouldReturnResponseWithBodyInUTF8WithNoContentTypeHeader() {
        // when
        String body = "我说中国话";
        mockServerClient
            .when(
                request()
            )
            .respond(
                response()
                    .withBody(body, StandardCharsets.UTF_8)
            );

        // then
        assertEquals(
            response()
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase())
                .withHeader(CONTENT_TYPE.toString(), MediaType.PLAIN_TEXT_UTF_8.toString())
                .withBody(body, StandardCharsets.UTF_8),
            makeRequest(
                request()
                    .withPath(calculatePath("")),
                headersToIgnore)
        );
    }

    @Test
    public void shouldReturnResponseByMatchingSubStringBody() {
        // when
        mockServerClient
            .when(
                request()
                    .withBody(
                        subString("random")
                    ),
                exactly(2)
            )
            .respond(
                response()
                    .withBody("some_sub_string_body_response")
            );

        // then
        assertEquals(
            response()
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase())
                .withBody("some_sub_string_body_response"),
            makeRequest(
                request()
                    .withMethod("POST")
                    .withPath(calculatePath("some_path"))
                    .withBody("some_random_body"),
                headersToIgnore)
        );
    }

    @Test
    public void shouldReturnResponseByMatchingNotRegexBody() {
        // when
        mockServerClient
            .when(
                request()
                    .withMethod("POST")
                    .withBody(Body.not(regex("10\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}")))
            )
            .respond(
                response()
                    .withBody("some_not_regex_body_response")
            );

        // then
        // should not match (because body matches regex)
        assertEquals(
            response()
                .withStatusCode(NOT_FOUND_404.code())
                .withReasonPhrase(NOT_FOUND_404.reasonPhrase()),
            makeRequest(
                request()
                    .withMethod("POST")
                    .withBody("10.2.3.123"),
                headersToIgnore)
        );
        // should match (because body doesn't matches regex)
        assertEquals(
            response()
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase())
                .withBody("some_not_regex_body_response"),
            makeRequest(
                request()
                    .withMethod("POST")
                    .withBody("10.2.3.1234"),
                headersToIgnore)
        );
    }

    @Test
    public void shouldReturnResponseByMatchingNotSubStringBody() {
        // when
        mockServerClient
            .when(
                request()
                    .withMethod("POST")
                    .withBody(Body.not(subString("some_body")))
            )
            .respond(
                response()
                    .withBody("some_not_regex_body_response")
            );

        // then
        // should not match (because body matches regex)
        assertEquals(
            response()
                .withStatusCode(NOT_FOUND_404.code())
                .withReasonPhrase(NOT_FOUND_404.reasonPhrase()),
            makeRequest(
                request()
                    .withMethod("POST")
                    .withBody("some_body_full_string"),
                headersToIgnore)
        );
        // should match (because body doesn't matches regex)
        assertEquals(
            response()
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase())
                .withBody("some_not_regex_body_response"),
            makeRequest(
                request()
                    .withMethod("POST")
                    .withBody("some_other_body_full_string"),
                headersToIgnore)
        );
    }

    @Test
    public void shouldReturnResponseByMatchingNotExactBody() {
        // when
        mockServerClient
            .when(
                request()
                    .withMethod("POST")
                    .withBody(Body.not(exact("some_body")))
            )
            .respond(
                response()
                    .withBody("some_not_regex_body_response")
            );

        // then
        // should not match (because body matches regex)
        assertEquals(
            response()
                .withStatusCode(NOT_FOUND_404.code())
                .withReasonPhrase(NOT_FOUND_404.reasonPhrase()),
            makeRequest(
                request()
                    .withMethod("POST")
                    .withBody("some_body"),
                headersToIgnore)
        );
        // should match (because body doesn't matches regex)
        assertEquals(
            response()
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase())
                .withBody("some_not_regex_body_response"),
            makeRequest(
                request()
                    .withMethod("POST")
                    .withBody("some_other_body"),
                headersToIgnore)
        );
    }

    @Test
    public void shouldReturnResponseByMatchingBodyWithXPath() {
        // when
        mockServerClient.when(request().withBody(xpath("/bookstore/book[price>30]/price")), exactly(2)).respond(response().withBody("some_body"));

        // then
        assertEquals(
            response()
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase())
                .withBody("some_body"),
            makeRequest(
                request()
                    .withPath(calculatePath("some_path"))
                    .withMethod("POST")
                    .withBody(new StringBody("" +
                        "<?xml version=\"1.0\" encoding=\"ISO-8859-1\"?>" + NEW_LINE +
                        "<bookstore>" + NEW_LINE +
                        "  <book category=\"COOKING\">" + NEW_LINE +
                        "    <title lang=\"en\">Everyday Italian</title>" + NEW_LINE +
                        "    <author>Giada De Laurentiis</author>" + NEW_LINE +
                        "    <year>2005</year>" + NEW_LINE +
                        "    <price>30.00</price>" + NEW_LINE +
                        "  </book>" + NEW_LINE +
                        "  <book category=\"CHILDREN\">" + NEW_LINE +
                        "    <title lang=\"en\">Harry Potter</title>" + NEW_LINE +
                        "    <author>J K. Rowling</author>" + NEW_LINE +
                        "    <year>2005</year>" + NEW_LINE +
                        "    <price>29.99</price>" + NEW_LINE +
                        "  </book>" + NEW_LINE +
                        "  <book category=\"WEB\">" + NEW_LINE +
                        "    <title lang=\"en\">Learning XML</title>" + NEW_LINE +
                        "    <author>Erik T. Ray</author>" + NEW_LINE +
                        "    <year>2003</year>" + NEW_LINE +
                        "    <price>31.95</price>" + NEW_LINE +
                        "  </book>" + NEW_LINE +
                        "</bookstore>")),
                headersToIgnore)
        );
    }

    @Test
    public void shouldReturnResponseByMatchingBodyWithXmlSchema() {
        // when
        mockServerClient.when(request()
            .withBody(
                xmlSchema("<?xml version=\"1.0\" encoding=\"UTF-8\"?>" + NEW_LINE +
                    "<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\" elementFormDefault=\"qualified\" attributeFormDefault=\"unqualified\">" + NEW_LINE +
                    "    <!-- XML Schema Generated from XML Document on Wed Jun 28 2017 21:52:45 GMT+0100 (BST) -->" + NEW_LINE +
                    "    <!-- with XmlGrid.net Free Online Service http://xmlgrid.net -->" + NEW_LINE +
                    "    <xs:element name=\"notes\">" + NEW_LINE +
                    "        <xs:complexType>" + NEW_LINE +
                    "            <xs:sequence>" + NEW_LINE +
                    "                <xs:element name=\"note\" maxOccurs=\"unbounded\">" + NEW_LINE +
                    "                    <xs:complexType>" + NEW_LINE +
                    "                        <xs:sequence>" + NEW_LINE +
                    "                            <xs:element name=\"to\" minOccurs=\"1\" maxOccurs=\"1\" type=\"xs:string\"></xs:element>" + NEW_LINE +
                    "                            <xs:element name=\"from\" minOccurs=\"1\" maxOccurs=\"1\" type=\"xs:string\"></xs:element>" + NEW_LINE +
                    "                            <xs:element name=\"heading\" minOccurs=\"1\" maxOccurs=\"1\" type=\"xs:string\"></xs:element>" + NEW_LINE +
                    "                            <xs:element name=\"body\" minOccurs=\"1\" maxOccurs=\"1\" type=\"xs:string\"></xs:element>" + NEW_LINE +
                    "                        </xs:sequence>" + NEW_LINE +
                    "                    </xs:complexType>" + NEW_LINE +
                    "                </xs:element>" + NEW_LINE +
                    "            </xs:sequence>" + NEW_LINE +
                    "        </xs:complexType>" + NEW_LINE +
                    "    </xs:element>" + NEW_LINE +
                    "</xs:schema>")), exactly(2)
        )
            .respond(
                response()
                    .withBody("some_body")
            );

        // then
        assertEquals(
            response()
                .withStatusCode(NOT_FOUND_404.code())
                .withReasonPhrase(NOT_FOUND_404.reasonPhrase()),
            makeRequest(
                request()
                    .withMethod("POST")
                    .withBody("<?xml version=\"1.0\" encoding=\"utf-8\"?>" + NEW_LINE +
                        "<notes>" + NEW_LINE +
                        "    <note>" + NEW_LINE +
                        "        <to>Bob</to>" + NEW_LINE +
                        "        <heading>Reminder</heading>" + NEW_LINE +
                        "        <body>Buy Bread</body>" + NEW_LINE +
                        "    </note>" + NEW_LINE +
                        "    <note>" + NEW_LINE +
                        "        <to>Jack</to>" + NEW_LINE +
                        "        <from>Jill</from>" + NEW_LINE +
                        "        <heading>Reminder</heading>" + NEW_LINE +
                        "        <body>Wash Shirts</body>" + NEW_LINE +
                        "    </note>" + NEW_LINE +
                        "</notes>"),
                headersToIgnore)
        );
        assertEquals(
            response()
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase())
                .withBody("some_body"),
            makeRequest(
                request()
                    .withPath(calculatePath("some_path"))
                    .withMethod("POST")
                    .withBody("<?xml version=\"1.0\" encoding=\"utf-8\"?>" + NEW_LINE +
                        "<notes>" + NEW_LINE +
                        "    <note>" + NEW_LINE +
                        "        <to>Bob</to>" + NEW_LINE +
                        "        <from>Bill</from>" + NEW_LINE +
                        "        <heading>Reminder</heading>" + NEW_LINE +
                        "        <body>Buy Bread</body>" + NEW_LINE +
                        "    </note>" + NEW_LINE +
                        "    <note>" + NEW_LINE +
                        "        <to>Jack</to>" + NEW_LINE +
                        "        <from>Jill</from>" + NEW_LINE +
                        "        <heading>Reminder</heading>" + NEW_LINE +
                        "        <body>Wash Shirts</body>" + NEW_LINE +
                        "    </note>" + NEW_LINE +
                        "</notes>"),
                headersToIgnore)
        );
    }

    @Test
    public void shouldReturnResponseByMatchingBodyWithXmlSchemaByClasspath() {
        // when
        mockServerClient.when(request()
            .withBody(
                xmlSchemaFromResource("org/mockserver/model/testXmlSchema.xsd")), exactly(2)
        )
            .respond(
                response()
                    .withBody("some_body")
            );

        // then
        assertEquals(
            response()
                .withStatusCode(NOT_FOUND_404.code())
                .withReasonPhrase(NOT_FOUND_404.reasonPhrase()),
            makeRequest(
                request()
                    .withMethod("POST")
                    .withBody("<?xml version=\"1.0\" encoding=\"utf-8\"?>" + NEW_LINE +
                        "<notes>" + NEW_LINE +
                        "    <note>" + NEW_LINE +
                        "        <to>Bob</to>" + NEW_LINE +
                        "        <heading>Reminder</heading>" + NEW_LINE +
                        "        <body>Buy Bread</body>" + NEW_LINE +
                        "    </note>" + NEW_LINE +
                        "    <note>" + NEW_LINE +
                        "        <to>Jack</to>" + NEW_LINE +
                        "        <from>Jill</from>" + NEW_LINE +
                        "        <heading>Reminder</heading>" + NEW_LINE +
                        "        <body>Wash Shirts</body>" + NEW_LINE +
                        "    </note>" + NEW_LINE +
                        "</notes>"),
                headersToIgnore)
        );
        assertEquals(
            response()
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase())
                .withBody("some_body"),
            makeRequest(
                request()
                    .withPath(calculatePath("some_path"))
                    .withMethod("POST")
                    .withBody("<?xml version=\"1.0\" encoding=\"utf-8\"?>" + NEW_LINE +
                        "<notes>" + NEW_LINE +
                        "    <note>" + NEW_LINE +
                        "        <to>Bob</to>" + NEW_LINE +
                        "        <from>Bill</from>" + NEW_LINE +
                        "        <heading>Reminder</heading>" + NEW_LINE +
                        "        <body>Buy Bread</body>" + NEW_LINE +
                        "    </note>" + NEW_LINE +
                        "    <note>" + NEW_LINE +
                        "        <to>Jack</to>" + NEW_LINE +
                        "        <from>Jill</from>" + NEW_LINE +
                        "        <heading>Reminder</heading>" + NEW_LINE +
                        "        <body>Wash Shirts</body>" + NEW_LINE +
                        "    </note>" + NEW_LINE +
                        "</notes>"),
                headersToIgnore)
        );
    }

    @Test
    public void shouldReturnResponseByMatchingBodyWithXmlWithSpecialCharactersDefaultingToUTF8() {
        // when
        mockServerClient.when(request().withBody(xml("" +
            "<bookstore>" + NEW_LINE +
            "  <book nationality=\"ITALIAN\" category=\"COOKING\"><title lang=\"en\">Everyday Italian</title><author>ÄÑçîüÏ</author><year>2005</year><price>30.00</price></book>" + NEW_LINE +
            "</bookstore>")), exactly(2)).respond(response().withBody("some_body"));

        // then
        assertEquals(
            response()
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase())
                .withBody("some_body"),
            makeRequest(
                request()
                    .withPath(calculatePath("some_path"))
                    .withMethod("POST")
                    .withBody(new StringBody("" +
                        "<?xml version=\"1.0\" encoding=\"ISO-8859-1\"?>" + NEW_LINE +
                        "<bookstore>" + NEW_LINE +
                        "  <book category=\"COOKING\" nationality=\"ITALIAN\">" + NEW_LINE +
                        "    <title lang=\"en\">Everyday Italian</title>" + NEW_LINE +
                        "    <author>ÄÑçîüÏ</author>" + NEW_LINE +
                        "    <year>2005</year>" + NEW_LINE +
                        "    <price>30.00</price>" + NEW_LINE +
                        "  </book>" + NEW_LINE +
                        "</bookstore>")),
                headersToIgnore)
        );
    }

    @Test
    public void shouldReturnResponseByMatchingBodyWithXmlWithSpecialCharactersAndCharset() {

        // when
        mockServerClient
            .when(
                request()
                    .withBody(
                        xml(
                            "" +
                                "<bookstore>" + NEW_LINE +
                                "  <book nationality=\"ITALIAN\" category=\"COOKING\"><title>Everyday Italian</title><author>我说中国话</author></book>" + NEW_LINE +
                                "</bookstore>",
                            StandardCharsets.UTF_8
                        )
                    ),
                exactly(2)
            )
            .respond(
                response()
                    .withBody("some_body")
            );

        // then
        assertEquals(
            response()
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase())
                .withBody("some_body"),
            makeRequest(
                request()
                    .withPath(calculatePath("some_path"))
                    .withMethod("POST")
                    .withHeader("Content-Type", "application/xml; charset=utf-8")
                    .withBody(new StringBody("" +
                        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" + NEW_LINE +
                        "<bookstore>" + NEW_LINE +
                        "  <book category=\"COOKING\" nationality=\"ITALIAN\">" + NEW_LINE +
                        "    <title>Everyday Italian</title>" + NEW_LINE +
                        "    <author>我说中国话</author>" + NEW_LINE +
                        "  </book>" + NEW_LINE +
                        "</bookstore>")),
                headersToIgnore)
        );
    }

    @Test
    public void shouldReturnResponseByMatchingBodyWithXmlWithSpecialCharactersClientCharsetDifferent() {

        // when
        mockServerClient
            .when(
                request()
                    .withBody(
                        xml(
                            "" +
                                "<bookstore>" + NEW_LINE +
                                "  <book nationality=\"ITALIAN\" category=\"COOKING\"><title>Everyday Italian</title><author>ÄÑçîüÏ</author></book>" + NEW_LINE +
                                "</bookstore>",
                            StandardCharsets.UTF_8
                        )
                    ),
                exactly(2)
            )
            .respond(
                response()
                    .withBody("some_body")
            );

        // then
        assertEquals(
            response()
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase())
                .withBody("some_body"),
            makeRequest(
                request()
                    .withPath(calculatePath("some_path"))
                    .withMethod("POST")
                    .withHeader("Content-Type", "application/xml; charset=" + StandardCharsets.ISO_8859_1.name())
                    .withBody(binary(("" +
                        "<?xml version=\"1.0\" encoding=\"ISO-8859-1\"?>" + NEW_LINE +
                        "<bookstore>" + NEW_LINE +
                        "  <book category=\"COOKING\" nationality=\"ITALIAN\">" + NEW_LINE +
                        "    <title>Everyday Italian</title>" + NEW_LINE +
                        "    <author>ÄÑçîüÏ</author>" + NEW_LINE +
                        "  </book>" + NEW_LINE +
                        "</bookstore>").getBytes(StandardCharsets.ISO_8859_1))),
                headersToIgnore)
        );
    }

    @Test
    public void shouldReturnXmlResponseWithUTF8() {
        // when
        mockServerClient
            .when(
                request()
                    .withBody("some_body"),
                exactly(2)
            )
            .respond(
                response()
                    .withStatusCode(ACCEPTED_202.code())
                    .withReasonPhrase(ACCEPTED_202.reasonPhrase())
                    .withBody(xml("" +
                        "<?xml version=\"1.0\" encoding=\"ISO-8859-1\"?>" + NEW_LINE +
                        "<bookstore>" + NEW_LINE +
                        "  <book category=\"COOKING\" nationality=\"ITALIAN\">" + NEW_LINE +
                        "    <title>Everyday Italian</title>" + NEW_LINE +
                        "    <author>ÄÑçîüÏ</author>" + NEW_LINE +
                        "  </book>" + NEW_LINE +
                        "</bookstore>")
                    )
            );

        // then
        assertEquals(
            response()
                .withStatusCode(ACCEPTED_202.code())
                .withReasonPhrase(ACCEPTED_202.reasonPhrase())
                .withHeader("content-type", "application/xml; charset=utf-8")
                .withBody(xml("" +
                    "<?xml version=\"1.0\" encoding=\"ISO-8859-1\"?>" + NEW_LINE +
                    "<bookstore>" + NEW_LINE +
                    "  <book category=\"COOKING\" nationality=\"ITALIAN\">" + NEW_LINE +
                    "    <title>Everyday Italian</title>" + NEW_LINE +
                    "    <author>ÄÑçîüÏ</author>" + NEW_LINE +
                    "  </book>" + NEW_LINE +
                    "</bookstore>")
                ),
            makeRequest(
                request()
                    .withPath(calculatePath("some_path"))
                    .withMethod("POST")
                    .withBody("some_body"),
                headersToIgnore)
        );
    }

    @Test
    public void shouldReturnResponseByMatchingBodyWithJsonWithSpecialCharactersDefaultingToUTF8() {
        // when
        mockServerClient
            .when(
                request()
                    .withBody(json("{" + NEW_LINE +
                        "    \"id\": 1," + NEW_LINE +
                        "    \"name\": \"A σπίτι door\"," + NEW_LINE +
                        "    \"price\": 12.50," + NEW_LINE +
                        "    \"tags\": [\"home\", \"green\"]" + NEW_LINE +
                        "}")),
                exactly(2)
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
                .withStatusCode(ACCEPTED_202.code())
                .withReasonPhrase(ACCEPTED_202.reasonPhrase())
                .withBody("some_body"),
            makeRequest(
                request()
                    .withPath(calculatePath("some_path"))
                    .withMethod("POST")
                    .withBody(json("{" + NEW_LINE +
                        "    \"id\": 1," + NEW_LINE +
                        "    \"extra ignored field\": \"some value\"," + NEW_LINE +
                        "    \"name\": \"A σπίτι door\"," + NEW_LINE +
                        "    \"price\": 12.50," + NEW_LINE +
                        "    \"tags\": [\"home\", \"green\"]" + NEW_LINE +
                        "}")),
                headersToIgnore)
        );
    }

    @Test
    public void shouldReturnResponseByMatchingBodyWithJsonAsRawBody() {
        // when
        makeRequest(
            request()
                .withPath(calculatePath("mockserver/expectation"))
                .withMethod("PUT")
                .withBody("{" + NEW_LINE +
                    "  \"httpRequest\" : {" + NEW_LINE +
                    "    \"body\" : {" + NEW_LINE +
                    "        \"id\": 1," + NEW_LINE +
                    "        \"name\": \"A green door\"," + NEW_LINE +
                    "        \"price\": 12.50," + NEW_LINE +
                    "        \"tags\": [\"home\", \"green\"]" + NEW_LINE +
                    "    }" + NEW_LINE +
                    "  }," + NEW_LINE +
                    "  \"httpResponse\" : {" + NEW_LINE +
                    "    \"body\" : {" + NEW_LINE +
                    "        \"id\": 1," + NEW_LINE +
                    "        \"name\": \"A green door\"," + NEW_LINE +
                    "        \"price\": 12.50," + NEW_LINE +
                    "        \"tags\": [\"home\", \"green\"]" + NEW_LINE +
                    "    }" + NEW_LINE +
                    "  }" + NEW_LINE +
                    "}"),
            headersToIgnore);

        // then
        assertEquals(
            response()
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase())
                .withHeader(CONTENT_TYPE.toString(), MediaType.APPLICATION_JSON_UTF_8.toString())
                .withBody(json("{" + NEW_LINE +
                    "  \"id\" : 1," + NEW_LINE +
                    "  \"name\" : \"A green door\"," + NEW_LINE +
                    "  \"price\" : 12.5," + NEW_LINE +
                    "  \"tags\" : [ \"home\", \"green\" ]" + NEW_LINE +
                    "}")),
            makeRequest(
                request()
                    .withPath(calculatePath("some_path"))
                    .withMethod("POST")
                    .withBody("{" + NEW_LINE +
                        "  \"id\" : 1," + NEW_LINE +
                        "  \"name\" : \"A green door\"," + NEW_LINE +
                        "  \"price\" : 12.5," + NEW_LINE +
                        "  \"tags\" : [ \"home\", \"green\" ]" + NEW_LINE +
                        "}"),
                headersToIgnore)
        );
    }

    @Test
    public void shouldReturnResponseByMatchingBodyWithJsonWithBlankFields() {
        // when
        makeRequest(
            request()
                .withPath(calculatePath("mockserver/expectation"))
                .withMethod("PUT")
                .withBody("{" + NEW_LINE +
                    "  \"httpRequest\" : {" + NEW_LINE +
                    "    \"body\" : {" + NEW_LINE +
                    "        \"id\": 1," + NEW_LINE +
                    "        \"name\": \"\"," + NEW_LINE +
                    "        \"price\": 0," + NEW_LINE +
                    "        \"null\": null," + NEW_LINE +
                    "        \"tags\": []" + NEW_LINE +
                    "    }" + NEW_LINE +
                    "  }," + NEW_LINE +
                    "  \"httpResponse\" : {" + NEW_LINE +
                    "    \"body\" : {" + NEW_LINE +
                    "        \"id\": 1," + NEW_LINE +
                    "        \"name\": \"\"," + NEW_LINE +
                    "        \"price\": 0," + NEW_LINE +
                    "        \"null\": null," + NEW_LINE +
                    "        \"tags\": []" + NEW_LINE +
                    "    }" + NEW_LINE +
                    "  }" + NEW_LINE +
                    "}"),
            headersToIgnore);

        // then
        assertEquals(
            response()
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase())
                .withHeader(CONTENT_TYPE.toString(), MediaType.APPLICATION_JSON_UTF_8.toString())
                .withBody(json("{" + NEW_LINE +
                    "  \"id\" : 1," + NEW_LINE +
                    "  \"name\" : \"\"," + NEW_LINE +
                    "  \"price\" : 0," + NEW_LINE +
                    "  \"null\" : null," + NEW_LINE +
                    "  \"tags\" : [ ]" + NEW_LINE +
                    "}")),
            makeRequest(
                request()
                    .withPath(calculatePath("some_path"))
                    .withMethod("POST")
                    .withBody(json("{" + NEW_LINE +
                        "  \"id\" : 1," + NEW_LINE +
                        "  \"name\" : \"\"," + NEW_LINE +
                        "  \"price\" : 0," + NEW_LINE +
                        "  \"null\" : null," + NEW_LINE +
                        "  \"tags\" : [ ]" + NEW_LINE +
                        "}")),
                headersToIgnore)
        );
    }

    @Test
    public void shouldReturnResponseByMatchingBodyWithJsonWithCharsetUTF16() {
        // when
        mockServerClient
            .when(
                request()
                    .withBody(json("{" + NEW_LINE +
                        "    \"ταυτότητα\": 1," + NEW_LINE +
                        "    \"όνομα\": \"μια πράσινη πόρτα\"," + NEW_LINE +
                        "    \"τιμή\": 12.50," + NEW_LINE +
                        "    \"ετικέτες\": [\"σπίτι\", \"πράσινος\"]" + NEW_LINE +
                        "}", StandardCharsets.UTF_16, MatchType.ONLY_MATCHING_FIELDS)),
                exactly(2)
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
                .withStatusCode(ACCEPTED_202.code())
                .withReasonPhrase(ACCEPTED_202.reasonPhrase())
                .withBody("some_body"),
            makeRequest(
                request()
                    .withPath(calculatePath("some_path"))
                    .withMethod("POST")
                    .withBody(json("{" + NEW_LINE +
                        "    \"ταυτότητα\": 1," + NEW_LINE +
                        "    \"επιπλέον αγνοούνται τομέα\": \"κάποια αξία\"," + NEW_LINE +
                        "    \"όνομα\": \"μια πράσινη πόρτα\"," + NEW_LINE +
                        "    \"τιμή\": 12.50," + NEW_LINE +
                        "    \"ετικέτες\": [\"σπίτι\", \"πράσινος\"]" + NEW_LINE +
                        "}", StandardCharsets.UTF_16, MatchType.ONLY_MATCHING_FIELDS)),
                headersToIgnore)
        );
    }

    @Test
    public void shouldReturnResponseByMatchingBodyWithJsonWithContentTypeHeaderAndCharsetUTF16() {
        // when
        mockServerClient
            .when(
                request()
                    .withBody(json("{" + NEW_LINE +
                        "    \"ταυτότητα\": 1," + NEW_LINE +
                        "    \"όνομα\": \"μια πράσινη πόρτα\"," + NEW_LINE +
                        "    \"τιμή\": 12.50," + NEW_LINE +
                        "    \"ετικέτες\": [\"σπίτι\", \"πράσινος\"]" + NEW_LINE +
                        "}", StandardCharsets.UTF_16, MatchType.ONLY_MATCHING_FIELDS)),
                exactly(2)
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
                .withStatusCode(ACCEPTED_202.code())
                .withReasonPhrase(ACCEPTED_202.reasonPhrase())
                .withBody("some_body"),
            makeRequest(
                request()
                    .withHeader(CONTENT_TYPE.toString(), MediaType.create("text", "plain").withCharset(StandardCharsets.UTF_16).toString())
                    .withPath(calculatePath("some_path"))
                    .withMethod("POST")
                    .withBody(json("{" + NEW_LINE +
                        "    \"ταυτότητα\": 1," + NEW_LINE +
                        "    \"επιπλέον αγνοούνται τομέα\": \"κάποια αξία\"," + NEW_LINE +
                        "    \"όνομα\": \"μια πράσινη πόρτα\"," + NEW_LINE +
                        "    \"τιμή\": 12.50," + NEW_LINE +
                        "    \"ετικέτες\": [\"σπίτι\", \"πράσινος\"]" + NEW_LINE +
                        "}", StandardCharsets.UTF_16)),
                headersToIgnore)
        );
    }

    @Test
    public void shouldReturnResponseByMatchingBodyWithJsonWithUTF8() {
        // when
        mockServerClient
            .when(
                request()
                    .withBody(json("{" + NEW_LINE +
                        "    \"ταυτότητα\": 1," + NEW_LINE +
                        "    \"όνομα\": \"μια πράσινη πόρτα\"," + NEW_LINE +
                        "    \"τιμή\": 12.50," + NEW_LINE +
                        "    \"ετικέτες\": [\"σπίτι\", \"πράσινος\"]" + NEW_LINE +
                        "}", MatchType.ONLY_MATCHING_FIELDS)),
                exactly(2)
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
                .withStatusCode(ACCEPTED_202.code())
                .withReasonPhrase(ACCEPTED_202.reasonPhrase())
                .withBody("some_body"),
            makeRequest(
                request()
                    .withPath(calculatePath("some_path"))
                    .withMethod("POST")
                    .withBody(json("{" + NEW_LINE +
                        "    \"ταυτότητα\": 1," + NEW_LINE +
                        "    \"επιπλέον αγνοούνται τομέα\": \"κάποια αξία\"," + NEW_LINE +
                        "    \"όνομα\": \"μια πράσινη πόρτα\"," + NEW_LINE +
                        "    \"τιμή\": 12.50," + NEW_LINE +
                        "    \"ετικέτες\": [\"σπίτι\", \"πράσινος\"]" + NEW_LINE +
                        "}", StandardCharsets.UTF_8)),
                headersToIgnore)
        );
    }

    @Test
    public void shouldReturnResponseByMatchingBodyWithJsonWithNoCharset() {
        // when
        mockServerClient
            .when(
                request()
                    .withBody(json("{" + NEW_LINE +
                        "    \"ταυτότητα\": 1," + NEW_LINE +
                        "    \"όνομα\": \"μια πράσινη πόρτα\"," + NEW_LINE +
                        "    \"τιμή\": 12.50," + NEW_LINE +
                        "    \"ετικέτες\": [\"σπίτι\", \"πράσινος\"]" + NEW_LINE +
                        "}", MatchType.ONLY_MATCHING_FIELDS)),
                exactly(2)
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
                .withStatusCode(ACCEPTED_202.code())
                .withReasonPhrase(ACCEPTED_202.reasonPhrase())
                .withBody("some_body"),
            makeRequest(
                request()
                    .withPath(calculatePath("some_path"))
                    .withMethod("POST")
                    .withBody(json("{" + NEW_LINE +
                        "    \"ταυτότητα\": 1," + NEW_LINE +
                        "    \"επιπλέον αγνοούνται τομέα\": \"κάποια αξία\"," + NEW_LINE +
                        "    \"όνομα\": \"μια πράσινη πόρτα\"," + NEW_LINE +
                        "    \"τιμή\": 12.50," + NEW_LINE +
                        "    \"ετικέτες\": [\"σπίτι\", \"πράσινος\"]" + NEW_LINE +
                        "}")),
                headersToIgnore)
        );
    }

    @Test
    public void shouldReturnJsonResponseWithJsonWithUTF8() {
        // when
        mockServerClient
            .when(
                request()
                    .withBody("some_body"),
                exactly(2)
            )
            .respond(
                response()
                    .withStatusCode(ACCEPTED_202.code())
                    .withReasonPhrase(ACCEPTED_202.reasonPhrase())
                    .withBody(json("{" + NEW_LINE +
                        "  \"ταυτότητα\": 1," + NEW_LINE +
                        "  \"όνομα\": \"μια πράσινη πόρτα\"," + NEW_LINE +
                        "  \"τιμή\": 12.50," + NEW_LINE +
                        "  \"ετικέτες\": [\"σπίτι\", \"πράσινος\"]" + NEW_LINE +
                        "}")
                    )
            );

        // then
        assertEquals(
            response()
                .withStatusCode(ACCEPTED_202.code())
                .withReasonPhrase(ACCEPTED_202.reasonPhrase())
                .withHeader("content-type", "application/json; charset=utf-8")
                .withBody(json("{" + NEW_LINE +
                    "  \"ταυτότητα\" : 1," + NEW_LINE +
                    "  \"όνομα\" : \"μια πράσινη πόρτα\"," + NEW_LINE +
                    "  \"τιμή\" : 12.5," + NEW_LINE +
                    "  \"ετικέτες\" : [ \"σπίτι\", \"πράσινος\" ]" + NEW_LINE +
                    "}")),
            makeRequest(
                request()
                    .withPath(calculatePath("some_path"))
                    .withMethod("POST")
                    .withBody("some_body"),
                headersToIgnore)
        );
    }

    @Test
    public void shouldReturnResponseByMatchingBodyWithJsonWithMatchType() {
        // when
        mockServerClient
            .when(
                request()
                    .withBody(json("{" + NEW_LINE +
                        "    \"id\": 1," + NEW_LINE +
                        "    \"name\": \"A green door\"," + NEW_LINE +
                        "    \"price\": 12.50," + NEW_LINE +
                        "    \"tags\": [\"home\", \"green\"]" + NEW_LINE +
                        "}", MatchType.ONLY_MATCHING_FIELDS)),
                exactly(2)
            )
            .respond(
                response()
                    .withBody("some_body")
            );

        // then
        assertEquals(
            response("some_body"),
            makeRequest(
                request()
                    .withPath(calculatePath("some_path"))
                    .withMethod("POST")
                    .withBody("{" + NEW_LINE +
                        "    \"id\": 1," + NEW_LINE +
                        "    \"extra field\": \"some value\"," + NEW_LINE +
                        "    \"name\": \"A green door\"," + NEW_LINE +
                        "    \"price\": 12.50," + NEW_LINE +
                        "    \"tags\": [\"home\", \"green\"]" + NEW_LINE +
                        "}"),
                headersToIgnore)
        );
    }

    @Test
    public void shouldReturnResponseByMatchingBodyWithJsonSchema() {
        // when
        mockServerClient.when(request().withBody(jsonSchema("{" + NEW_LINE +
            "    \"$schema\": \"http://json-schema.org/draft-04/schema#\"," + NEW_LINE +
            "    \"title\": \"Product\"," + NEW_LINE +
            "    \"description\": \"A product from Acme's catalog\"," + NEW_LINE +
            "    \"type\": \"object\"," + NEW_LINE +
            "    \"properties\": {" + NEW_LINE +
            "        \"id\": {" + NEW_LINE +
            "            \"description\": \"The unique identifier for a product\"," + NEW_LINE +
            "            \"type\": \"integer\"" + NEW_LINE +
            "        }," + NEW_LINE +
            "        \"name\": {" + NEW_LINE +
            "            \"description\": \"Name of the product\"," + NEW_LINE +
            "            \"type\": \"string\"" + NEW_LINE +
            "        }," + NEW_LINE +
            "        \"price\": {" + NEW_LINE +
            "            \"type\": \"number\"," + NEW_LINE +
            "            \"minimum\": 0," + NEW_LINE +
            "            \"exclusiveMinimum\": true" + NEW_LINE +
            "        }," + NEW_LINE +
            "        \"tags\": {" + NEW_LINE +
            "            \"type\": \"array\"," + NEW_LINE +
            "            \"items\": {" + NEW_LINE +
            "                \"type\": \"string\"" + NEW_LINE +
            "            }," + NEW_LINE +
            "            \"minItems\": 1," + NEW_LINE +
            "            \"uniqueItems\": true" + NEW_LINE +
            "        }" + NEW_LINE +
            "    }," + NEW_LINE +
            "    \"required\": [\"id\", \"name\", \"price\"]" + NEW_LINE +
            "}")), exactly(2)).respond(response().withBody("some_body"));

        // then
        assertEquals(
            response()
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase())
                .withBody("some_body"),
            makeRequest(
                request()
                    .withPath(calculatePath("some_path"))
                    .withMethod("POST")
                    .withBody("{" + NEW_LINE +
                        "    \"id\": 1," + NEW_LINE +
                        "    \"name\": \"A green door\"," + NEW_LINE +
                        "    \"price\": 12.50," + NEW_LINE +
                        "    \"tags\": [\"home\", \"green\"]" + NEW_LINE +
                        "}"),
                headersToIgnore)
        );
    }

    @Test
    public void shouldReturnResponseByMatchingBodyWithJsonPath() {
        // when
        mockServerClient.when(request().withBody(jsonPath("$..book[?(@.price <= $['expensive'])]")), exactly(2)).respond(response().withBody("some_body"));

        // then
        assertEquals(
            response()
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase())
                .withBody("some_body"),
            makeRequest(
                request()
                    .withPath(calculatePath("some_path"))
                    .withMethod("POST")
                    .withBody(new StringBody("" +
                        "{" + NEW_LINE +
                        "    \"store\": {" + NEW_LINE +
                        "        \"book\": [" + NEW_LINE +
                        "            {" + NEW_LINE +
                        "                \"category\": \"reference\"," + NEW_LINE +
                        "                \"author\": \"Nigel Rees\"," + NEW_LINE +
                        "                \"title\": \"Sayings of the Century\"," + NEW_LINE +
                        "                \"price\": 8.95" + NEW_LINE +
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
                        "}")),
                headersToIgnore)
        );
    }

    @Test
    @SuppressWarnings("ConstantConditions")
    public void shouldReturnPDFResponseByMatchingPath() throws IOException {
        // when
        byte[] pdfBytes = IOUtils.toByteArray(getClass().getClassLoader().getResourceAsStream("test.pdf"));
        mockServerClient
            .when(
                request()
                    .withPath(calculatePath("ws/rest/user/[0-9]+/document/[0-9]+\\.pdf"))
            )
            .respond(
                response()
                    .withStatusCode(OK_200.code())
                    .withReasonPhrase(OK_200.reasonPhrase())
                    .withHeaders(
                        header(CONTENT_TYPE.toString(), MediaType.PDF.toString()),
                        header(CONTENT_DISPOSITION.toString(), "form-data; name=\"test.pdf\"; filename=\"test.pdf\""),
                        header(CACHE_CONTROL.toString(), "must-revalidate, post-check=0, pre-check=0")
                    )
                    .withBody(binary(pdfBytes))
            );

        // then
        assertEquals(
            response()
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase())
                .withHeaders(
                    header(CONTENT_DISPOSITION.toString(), "form-data; name=\"test.pdf\"; filename=\"test.pdf\""),
                    header(CACHE_CONTROL.toString(), "must-revalidate, post-check=0, pre-check=0"),
                    header(CONTENT_TYPE.toString(), MediaType.PDF.toString())
                )
                .withBody(binary(pdfBytes, MediaType.PDF)),
            makeRequest(
                request()
                    .withPath(calculatePath("ws/rest/user/1/document/2.pdf"))
                    .withMethod("GET"),
                headersToIgnore)
        );
    }

    @Test
    @SuppressWarnings("ConstantConditions")
    public void shouldReturnPNGResponseByMatchingPath() throws IOException {
        // when
        byte[] pngBytes = IOUtils.toByteArray(getClass().getClassLoader().getResourceAsStream("test.png"));
        mockServerClient
            .when(
                request()
                    .withPath(calculatePath("ws/rest/user/[0-9]+/icon/[0-9]+\\.png"))
            )
            .respond(
                response()
                    .withStatusCode(OK_200.code())
                    .withReasonPhrase(OK_200.reasonPhrase())
                    .withHeaders(
                        header(CONTENT_TYPE.toString(), MediaType.PNG.toString()),
                        header(CONTENT_DISPOSITION.toString(), "form-data; name=\"test.png\"; filename=\"test.png\"")
                    )
                    .withBody(binary(pngBytes))
            );

        // then
        assertEquals(
            response()
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase())
                .withHeaders(
                    header(CONTENT_DISPOSITION.toString(), "form-data; name=\"test.png\"; filename=\"test.png\""),
                    header(CONTENT_TYPE.toString(), MediaType.PNG.toString())
                )
                .withBody(binary(pngBytes, MediaType.PNG)),
            makeRequest(
                request()
                    .withPath(calculatePath("ws/rest/user/1/icon/1.png"))
                    .withMethod("GET"),
                headersToIgnore)
        );
    }

    @Test
    @SuppressWarnings("ConstantConditions")
    public void shouldReturnPDFResponseByMatchingBinaryPDFBody() throws IOException {
        // when
        byte[] pdfBytes = IOUtils.toByteArray(getClass().getClassLoader().getResourceAsStream("test.pdf"));
        mockServerClient
            .when(
                request()
                    .withBody(binary(pdfBytes, MediaType.PDF))
            )
            .respond(
                response()
                    .withStatusCode(OK_200.code())
                    .withReasonPhrase(OK_200.reasonPhrase())
                    .withHeaders(
                        header(CONTENT_TYPE.toString(), MediaType.PDF.toString()),
                        header(CONTENT_DISPOSITION.toString(), "form-data; name=\"test.pdf\"; filename=\"test.pdf\""),
                        header(CACHE_CONTROL.toString(), "must-revalidate, post-check=0, pre-check=0")
                    )
                    .withBody(binary(pdfBytes))
            );

        // then
        assertEquals(
            response()
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase())
                .withHeaders(
                    header(CONTENT_DISPOSITION.toString(), "form-data; name=\"test.pdf\"; filename=\"test.pdf\""),
                    header(CACHE_CONTROL.toString(), "must-revalidate, post-check=0, pre-check=0"),
                    header(CONTENT_TYPE.toString(), MediaType.PDF.toString())
                )
                .withBody(binary(pdfBytes, MediaType.PDF)),
            makeRequest(
                request()
                    .withPath(calculatePath("ws/rest/user/1/document/2.pdf"))
                    .withBody(binary(pdfBytes, MediaType.PDF))
                    .withMethod("POST"),
                headersToIgnore)
        );
    }

    @Test
    @SuppressWarnings("ConstantConditions")
    public void shouldReturnPNGResponseByMatchingBinaryPNGBody() throws IOException {
        // when
        byte[] pngBytes = IOUtils.toByteArray(getClass().getClassLoader().getResourceAsStream("test.png"));
        mockServerClient
            .when(
                request()
                    .withBody(binary(pngBytes, MediaType.ANY_IMAGE_TYPE))
            )
            .respond(
                response()
                    .withStatusCode(OK_200.code())
                    .withReasonPhrase(OK_200.reasonPhrase())
                    .withHeaders(
                        header(CONTENT_TYPE.toString(), MediaType.PNG.toString()),
                        header(CONTENT_DISPOSITION.toString(), "form-data; name=\"test.png\"; filename=\"test.png\"")
                    )
                    .withBody(binary(pngBytes))
            );

        // then
        assertEquals(
            response()
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase())
                .withHeaders(
                    header(CONTENT_DISPOSITION.toString(), "form-data; name=\"test.png\"; filename=\"test.png\""),
                    header(CONTENT_TYPE.toString(), MediaType.PNG.toString())
                )
                .withBody(binary(pngBytes, MediaType.PNG)),
            makeRequest(
                request()
                    .withPath(calculatePath("ws/rest/user/1/icon/1.png"))
                    .withBody(binary(pngBytes, MediaType.ANY_IMAGE_TYPE))
                    .withMethod("POST"),
                headersToIgnore)
        );
    }

    @Test
    public void shouldReturnResponseByNotMatchingPathWithNotOperator() {
        // when
        mockServerClient
            .when(
                request()
                    .withPath(not(calculatePath("some_path")))
            )
            .respond(
                response()
                    .withStatusCode(ACCEPTED_202.code())
                    .withReasonPhrase(ACCEPTED_202.reasonPhrase())
                    .withBody("some_body_response")
            );

        // then
        assertEquals(
            response()
                .withStatusCode(ACCEPTED_202.code())
                .withReasonPhrase(ACCEPTED_202.reasonPhrase())
                .withBody("some_body_response"),
            makeRequest(
                request()
                    .withPath(calculatePath("some_other_path")),
                headersToIgnore)
        );
    }

    @Test
    public void shouldReturnResponseByNotMatchingMethodWithNotOperator() {
        // when
        mockServerClient
            .when(
                request()
                    .withMethod(not("GET"))
            )
            .respond(
                response()
                    .withStatusCode(ACCEPTED_202.code())
                    .withReasonPhrase(ACCEPTED_202.reasonPhrase())
                    .withBody("some_body_response")
            );

        // then
        assertEquals(
            response()
                .withStatusCode(ACCEPTED_202.code())
                .withReasonPhrase(ACCEPTED_202.reasonPhrase())
                .withBody("some_body_response"),
            makeRequest(
                request()
                    .withMethod("POST"),
                headersToIgnore)
        );
    }

    @Test
    public void shouldReturnResponseByMatchingPathAndMethodAndBody() {
        // when
        mockServerClient
            .when(
                request()
                    .withMethod("POST")
                    .withPath(calculatePath("some_pathRequest"))
                    .withBody("some_bodyRequest")
            )
            .respond(
                response()
                    .withStatusCode(ACCEPTED_202.code())
                    .withReasonPhrase(ACCEPTED_202.reasonPhrase())
                    .withBody("some_body_response")
                    .withHeaders(header("headerNameResponse", "headerValueResponse"))
            );

        // then
        assertEquals(
            response()
                .withStatusCode(ACCEPTED_202.code())
                .withReasonPhrase(ACCEPTED_202.reasonPhrase())
                .withBody("some_body_response")
                .withHeaders(
                    header("headerNameResponse", "headerValueResponse")
                ),
            makeRequest(
                request()
                    .withMethod("POST")
                    .withPath(calculatePath("some_pathRequest"))
                    .withQueryStringParameters(
                        param("queryStringParameterOneName", "queryStringParameterOneValue"),
                        param("queryStringParameterTwoName", "queryStringParameterTwoValue")
                    )
                    .withBody("some_bodyRequest")
                    .withHeaders(header("headerNameRequest", "headerValueRequest"))
                    .withCookies(cookie("cookieNameRequest", "cookieValueRequest")),
                headersToIgnore)
        );
    }

    @Test
    public void shouldReturnResponseByMatchingPathAndMethodAndQueryStringParameters() {
        // when
        mockServerClient
            .when(
                request()
                    .withMethod("GET")
                    .withPath(calculatePath("some_pathRequest"))
                    .withQueryStringParameters(
                        param("queryStringParameterOneName", "queryStringParameterOneValue"),
                        param("queryStringParameterTwoName", "queryStringParameterTwoValue")
                    )
            )
            .respond(
                response()
                    .withStatusCode(ACCEPTED_202.code())
                    .withReasonPhrase(ACCEPTED_202.reasonPhrase())
                    .withBody("some_body_response")
                    .withCookies(cookie("cookieNameResponse", "cookieValueResponse"))
            );

        // then
        assertEquals(
            response()
                .withStatusCode(ACCEPTED_202.code())
                .withReasonPhrase(ACCEPTED_202.reasonPhrase())
                .withBody("some_body_response")
                .withCookies(cookie("cookieNameResponse", "cookieValueResponse"))
                .withHeaders(
                    header(SET_COOKIE.toString(), "cookieNameResponse=cookieValueResponse")
                ),
            makeRequest(
                request()
                    .withMethod("GET")
                    .withPath(calculatePath("some_pathRequest"))
                    .withQueryStringParameters(
                        param("queryStringParameterOneName", "queryStringParameterOneValue"),
                        param("queryStringParameterTwoName", "queryStringParameterTwoValue")
                    )
                    .withHeaders(header("headerNameRequest", "headerValueRequest"))
                    .withCookies(cookie("cookieNameRequest", "cookieValueRequest")),
                headersToIgnore)
        );
    }

    @Test
    public void shouldReturnResponseByMatchingPathAndMethodAndHeaders() {
        // when
        mockServerClient
            .when(
                request()
                    .withMethod("GET")
                    .withPath(calculatePath("some_pathRequest"))
                    .withHeaders(
                        header("requestHeaderNameOne", "requestHeaderValueOne_One", "requestHeaderValueOne_Two"),
                        header("requestHeaderNameTwo", "requestHeaderValueTwo")
                    )
            )
            .respond(
                response()
                    .withStatusCode(ACCEPTED_202.code())
                    .withReasonPhrase(ACCEPTED_202.reasonPhrase())
                    .withBody("some_body_response")
                    .withCookies(cookie("cookieNameResponse", "cookieValueResponse"))
            );

        // then
        assertEquals(
            response()
                .withStatusCode(ACCEPTED_202.code())
                .withReasonPhrase(ACCEPTED_202.reasonPhrase())
                .withBody("some_body_response")
                .withCookies(cookie("cookieNameResponse", "cookieValueResponse"))
                .withHeaders(
                    header(SET_COOKIE.toString(), "cookieNameResponse=cookieValueResponse")
                ),
            makeRequest(
                request()
                    .withMethod("GET")
                    .withPath(calculatePath("some_pathRequest"))
                    .withHeaders(
                        header("requestHeaderNameOne", "requestHeaderValueOne_One", "requestHeaderValueOne_Two"),
                        header("requestHeaderNameTwo", "requestHeaderValueTwo")
                    )
                    .withCookies(cookie("cookieNameRequest", "cookieValueRequest")),
                headersToIgnore)
        );
    }

    @Test
    public void shouldReturnResponseByMatchingPathAndMethodAndCookies() {
        // when
        mockServerClient
            .when(
                request()
                    .withMethod("GET")
                    .withPath(calculatePath("some_pathRequest"))
                    .withCookies(
                        cookie("requestCookieNameOne", "requestCookieValueOne"),
                        cookie("requestCookieNameTwo", "requestCookieValueTwo")
                    )
            )
            .respond(
                response()
                    .withStatusCode(ACCEPTED_202.code())
                    .withReasonPhrase(ACCEPTED_202.reasonPhrase())
                    .withBody("some_body_response")
                    .withCookies(
                        cookie("responseCookieNameOne", "responseCookieValueOne"),
                        cookie("responseCookieNameTwo", "responseCookieValueTwo")
                    )
            );

        // then
        // - cookie objects
        assertEquals(
            response()
                .withStatusCode(ACCEPTED_202.code())
                .withReasonPhrase(ACCEPTED_202.reasonPhrase())
                .withBody("some_body_response")
                .withCookies(
                    cookie("responseCookieNameOne", "responseCookieValueOne"),
                    cookie("responseCookieNameTwo", "responseCookieValueTwo")
                )
                .withHeaders(
                    header(SET_COOKIE.toString(), "responseCookieNameOne=responseCookieValueOne", "responseCookieNameTwo=responseCookieValueTwo")
                ),
            makeRequest(
                request()
                    .withMethod("GET")
                    .withPath(calculatePath("some_pathRequest"))
                    .withHeaders(
                        header("headerNameRequest", "headerValueRequest"),
                        header(CONTENT_TYPE.toString(), MediaType.create("text", "plain").toString())
                    )
                    .withCookies(
                        cookie("requestCookieNameOne", "requestCookieValueOne"),
                        cookie("requestCookieNameTwo", "requestCookieValueTwo")
                    ),
                headersToIgnore)
        );
        // - cookie header
        assertEquals(
            response()
                .withStatusCode(ACCEPTED_202.code())
                .withReasonPhrase(ACCEPTED_202.reasonPhrase())
                .withBody("some_body_response")
                .withCookies(
                    cookie("responseCookieNameOne", "responseCookieValueOne"),
                    cookie("responseCookieNameTwo", "responseCookieValueTwo")
                )
                .withHeaders(
                    header(SET_COOKIE.toString(), "responseCookieNameOne=responseCookieValueOne", "responseCookieNameTwo=responseCookieValueTwo")
                ),
            makeRequest(
                request()
                    .withMethod("GET")
                    .withPath(calculatePath("some_pathRequest"))
                    .withHeaders(
                        header("headerNameRequest", "headerValueRequest"),
                        header("Cookie", "requestCookieNameOne=requestCookieValueOne; requestCookieNameTwo=requestCookieValueTwo")
                    ),
                headersToIgnore)
        );
    }

    @Test
    public void shouldReturnResponseByMatchingPathAndMethodAndQueryStringParametersAndBodyParameters() {
        // when
        mockServerClient
            .when(
                request()
                    .withMethod("POST")
                    .withPath(calculatePath("some_pathRequest"))
                    .withQueryStringParameters(
                        param("queryStringParameterOneName", "queryStringParameterOneValueOne", "queryStringParameterOneValueTwo"),
                        param("queryStringParameterTwoName", "queryStringParameterTwoValue")
                    )
                    .withBody(params(param("bodyParameterName", "bodyParameterValue")))
            )
            .respond(
                response()
                    .withStatusCode(ACCEPTED_202.code())
                    .withReasonPhrase(ACCEPTED_202.reasonPhrase())
                    .withBody("some_body")
            );

        // then
        // - in http - url query string
        assertEquals(
            response()
                .withStatusCode(ACCEPTED_202.code())
                .withReasonPhrase(ACCEPTED_202.reasonPhrase())
                .withBody("some_body"),
            makeRequest(
                request()
                    .withMethod("POST")
                    .withPath(calculatePath("some_pathRequest"))
                    .withQueryStringParameters(
                        param("queryStringParameterOneName", "queryStringParameterOneValueOne", "queryStringParameterOneValueTwo"),
                        param("queryStringParameterTwoName", "queryStringParameterTwoValue")
                    )
                    .withBody(params(param("bodyParameterName", "bodyParameterValue"))),
                headersToIgnore)
        );
        // - in https - query string parameter objects
        assertEquals(
            response()
                .withStatusCode(ACCEPTED_202.code())
                .withReasonPhrase(ACCEPTED_202.reasonPhrase())
                .withBody("some_body"),
            makeRequest(
                request()
                    .withSecure(true)
                    .withMethod("POST")
                    .withPath(calculatePath("some_pathRequest"))
                    .withQueryStringParameters(
                        param("queryStringParameterOneName", "queryStringParameterOneValueOne", "queryStringParameterOneValueTwo"),
                        param("queryStringParameterTwoName", "queryStringParameterTwoValue")
                    )
                    .withBody(params(param("bodyParameterName", "bodyParameterValue"))),
                headersToIgnore)
        );
    }

    @Test
    public void shouldReturnResponseByMatchingPathAndMethodAndQueryStringParametersAndBody() {
        // when
        mockServerClient
            .when(
                request()
                    .withMethod("POST")
                    .withPath(calculatePath("some_pathRequest"))
                    .withQueryStringParameters(
                        param("queryStringParameterOneName", "queryStringParameterOneValueOne", "queryStringParameterOneValueTwo"),
                        param("queryStringParameterTwoName", "queryStringParameterTwoValue")
                    )
                    .withBody("some_bodyRequest")
            )
            .respond(
                response()
                    .withStatusCode(ACCEPTED_202.code())
                    .withReasonPhrase(ACCEPTED_202.reasonPhrase())
                    .withBody("some_body_response")
                    .withHeaders(header("headerNameResponse", "headerValueResponse"))
                    .withCookies(cookie("cookieNameResponse", "cookieValueResponse"))
            );

        // then
        // - in http - url query string
        assertEquals(
            response()
                .withStatusCode(ACCEPTED_202.code())
                .withReasonPhrase(ACCEPTED_202.reasonPhrase())
                .withBody("some_body_response")
                .withCookies(cookie("cookieNameResponse", "cookieValueResponse"))
                .withHeaders(
                    header("headerNameResponse", "headerValueResponse"),
                    header(SET_COOKIE.toString(), "cookieNameResponse=cookieValueResponse")
                ),
            makeRequest(
                request()
                    .withMethod("POST")
                    .withPath(calculatePath("some_pathRequest"))
                    .withQueryStringParameters(
                        param("queryStringParameterOneName", "queryStringParameterOneValueOne", "queryStringParameterOneValueTwo"),
                        param("queryStringParameterTwoName", "queryStringParameterTwoValue")
                    )
                    .withBody("some_bodyRequest")
                    .withHeaders(header("headerNameRequest", "headerValueRequest"))
                    .withCookies(cookie("cookieNameRequest", "cookieValueRequest")),
                headersToIgnore)
        );
        // - in http - query string parameter objects
        assertEquals(
            response()
                .withStatusCode(ACCEPTED_202.code())
                .withReasonPhrase(ACCEPTED_202.reasonPhrase())
                .withBody("some_body_response")
                .withCookies(cookie("cookieNameResponse", "cookieValueResponse"))
                .withHeaders(
                    header("headerNameResponse", "headerValueResponse"),
                    header(SET_COOKIE.toString(), "cookieNameResponse=cookieValueResponse")
                ),
            makeRequest(
                request()
                    .withMethod("POST")
                    .withPath(calculatePath("some_pathRequest"))
                    .withQueryStringParameters(
                        param("queryStringParameterOneName", "queryStringParameterOneValueOne", "queryStringParameterOneValueTwo"),
                        param("queryStringParameterTwoName", "queryStringParameterTwoValue")
                    )
                    .withBody("some_bodyRequest")
                    .withHeaders(header("headerNameRequest", "headerValueRequest"))
                    .withCookies(cookie("cookieNameRequest", "cookieValueRequest")),
                headersToIgnore)
        );
        // - in https - url string and query string parameter objects
        assertEquals(
            response()
                .withStatusCode(ACCEPTED_202.code())
                .withReasonPhrase(ACCEPTED_202.reasonPhrase())
                .withBody("some_body_response")
                .withCookies(cookie("cookieNameResponse", "cookieValueResponse"))
                .withHeaders(
                    header("headerNameResponse", "headerValueResponse"),
                    header(SET_COOKIE.toString(), "cookieNameResponse=cookieValueResponse")
                ),
            makeRequest(
                request()
                    .withMethod("POST")
                    .withPath(calculatePath("some_pathRequest"))
                    .withQueryStringParameters(
                        param("queryStringParameterOneName", "queryStringParameterOneValueOne", "queryStringParameterOneValueTwo"),
                        param("queryStringParameterTwoName", "queryStringParameterTwoValue")
                    )
                    .withBody("some_bodyRequest")
                    .withHeaders(header("headerNameRequest", "headerValueRequest"))
                    .withCookies(cookie("cookieNameRequest", "cookieValueRequest")),
                headersToIgnore)
        );
    }

    @Test
    public void shouldReturnResponseByMatchingPathAndMethodAndBodyParameters() {
        // when
        mockServerClient
            .when(
                request()
                    .withMethod("POST")
                    .withPath(calculatePath("some_pathRequest"))
                    .withBody(params(
                        param("bodyParameterOneName", "Parameter One Value One", "Parameter One Value Two"),
                        param("bodyParameterTwoName", "Parameter Two")
                    ))
            )
            .respond(
                response()
                    .withStatusCode(ACCEPTED_202.code())
                    .withReasonPhrase(ACCEPTED_202.reasonPhrase())
                    .withBody("some_body_response")
                    .withHeaders(header("headerNameResponse", "headerValueResponse"))
                    .withCookies(cookie("cookieNameResponse", "cookieValueResponse"))
            );

        // then
        // - in http - body string
        assertEquals(
            response()
                .withStatusCode(ACCEPTED_202.code())
                .withReasonPhrase(ACCEPTED_202.reasonPhrase())
                .withBody("some_body_response")
                .withCookies(cookie("cookieNameResponse", "cookieValueResponse"))
                .withHeaders(
                    header("headerNameResponse", "headerValueResponse"),
                    header(SET_COOKIE.toString(), "cookieNameResponse=cookieValueResponse")
                ),
            makeRequest(
                request()
                    .withMethod("POST")
                    .withPath(calculatePath("some_pathRequest"))
                    .withBody(new StringBody("bodyParameterOneName=Parameter+One+Value+One" +
                        "&bodyParameterOneName=Parameter+One+Value+Two" +
                        "&bodyParameterTwoName=Parameter+Two"))
                    .withHeaders(header("headerNameRequest", "headerValueRequest"))
                    .withCookies(cookie("cookieNameRequest", "cookieValueRequest")),
                headersToIgnore)
        );
        // - in http - body parameter objects
        assertEquals(
            response()
                .withStatusCode(ACCEPTED_202.code())
                .withReasonPhrase(ACCEPTED_202.reasonPhrase())
                .withBody("some_body_response")
                .withCookies(cookie("cookieNameResponse", "cookieValueResponse"))
                .withHeaders(
                    header("headerNameResponse", "headerValueResponse"),
                    header(SET_COOKIE.toString(), "cookieNameResponse=cookieValueResponse")
                ),
            makeRequest(
                request()
                    .withMethod("POST")
                    .withPath(calculatePath("some_pathRequest"))
                    .withBody(params(
                        param("bodyParameterOneName", "Parameter One Value One", "Parameter One Value Two"),
                        param("bodyParameterTwoName", "Parameter Two")
                    ))
                    .withHeaders(header("headerNameRequest", "headerValueRequest"))
                    .withCookies(cookie("cookieNameRequest", "cookieValueRequest")),
                headersToIgnore)
        );
        // - in https - url string and query string parameter objects
        assertEquals(
            response()
                .withStatusCode(ACCEPTED_202.code())
                .withReasonPhrase(ACCEPTED_202.reasonPhrase())
                .withBody("some_body_response")
                .withCookies(cookie("cookieNameResponse", "cookieValueResponse"))
                .withHeaders(
                    header("headerNameResponse", "headerValueResponse"),
                    header(SET_COOKIE.toString(), "cookieNameResponse=cookieValueResponse")
                ),
            makeRequest(
                request()
                    .withMethod("POST")
                    .withPath(calculatePath("some_pathRequest"))
                    .withBody(params(
                        param("bodyParameterOneName", "Parameter One Value One", "Parameter One Value Two"),
                        param("bodyParameterTwoName", "Parameter Two")
                    ))
                    .withHeaders(header("headerNameRequest", "headerValueRequest"))
                    .withCookies(cookie("cookieNameRequest", "cookieValueRequest")),
                headersToIgnore)
        );
    }

    @Test
    public void shouldReturnResponseByMatchingPathAndMethodAndParametersAndHeadersAndCookies() {
        // when
        mockServerClient
            .when(
                request()
                    .withMethod("PUT")
                    .withPath(calculatePath("some_pathRequest"))
                    .withBody(new StringBody("bodyParameterOneName=Parameter+One+Value+One" +
                        "&bodyParameterOneName=Parameter+One+Value+Two" +
                        "&bodyParameterTwoName=Parameter+Two"))
                    .withHeaders(header("headerNameRequest", "headerValueRequest"))
                    .withCookies(cookie("cookieNameRequest", "cookieValueRequest"))
            )
            .respond(
                response()
                    .withStatusCode(ACCEPTED_202.code())
                    .withReasonPhrase(ACCEPTED_202.reasonPhrase())
                    .withBody("some_body_response")
                    .withHeaders(header("headerNameResponse", "headerValueResponse"))
                    .withCookies(cookie("cookieNameResponse", "cookieValueResponse"))
            );

        // then
        // - body string
        assertEquals(
            response()
                .withStatusCode(ACCEPTED_202.code())
                .withReasonPhrase(ACCEPTED_202.reasonPhrase())
                .withBody("some_body_response")
                .withCookies(cookie("cookieNameResponse", "cookieValueResponse"))
                .withHeaders(
                    header("headerNameResponse", "headerValueResponse"),
                    header(SET_COOKIE.toString(), "cookieNameResponse=cookieValueResponse")
                ),
            makeRequest(
                request()
                    .withMethod("PUT")
                    .withPath(calculatePath("some_pathRequest"))
                    .withBody(new StringBody("bodyParameterOneName=Parameter+One+Value+One" +
                        "&bodyParameterOneName=Parameter+One+Value+Two" +
                        "&bodyParameterTwoName=Parameter+Two"))
                    .withHeaders(
                        header("headerNameRequest", "headerValueRequest"),
                        header("Cookie", "cookieNameRequest=cookieValueRequest")
                    ),
                headersToIgnore)
        );
        // - body parameter objects
        assertEquals(
            response()
                .withStatusCode(ACCEPTED_202.code())
                .withReasonPhrase(ACCEPTED_202.reasonPhrase())
                .withBody("some_body_response")
                .withCookies(cookie("cookieNameResponse", "cookieValueResponse"))
                .withHeaders(
                    header("headerNameResponse", "headerValueResponse"),
                    header(SET_COOKIE.toString(), "cookieNameResponse=cookieValueResponse")
                ),
            makeRequest(
                request()
                    .withMethod("PUT")
                    .withPath(calculatePath("some_pathRequest"))
                    .withBody(params(
                        param("bodyParameterOneName", "Parameter One Value One", "Parameter One Value Two"),
                        param("bodyParameterTwoName", "Parameter Two")
                    ))
                    .withHeaders(header("headerNameRequest", "headerValueRequest"))
                    .withCookies(cookie("cookieNameRequest", "cookieValueRequest")),
                headersToIgnore)
        );
    }

    @Test
    public void shouldReturnResponseByNotMatchingBodyParameterWithNotOperatorForNameAndValue() {
        // when
        mockServerClient
            .when(
                request()
                    .withMethod("POST")
                    .withPath(calculatePath("some_pathRequest"))
                    .withBody(params(
                        param(not("bodyParameterOneName"), not("Parameter One Value One"), not("Parameter One Value Two")),
                        param("bodyParameterTwoName", "Parameter Two")
                    ))
            )
            .respond(
                response()
                    .withStatusCode(ACCEPTED_202.code())
                    .withReasonPhrase(ACCEPTED_202.reasonPhrase())
                    .withBody("some_body_response")
            );

        // then
        // wrong query string parameter name
        assertEquals(
            response()
                .withStatusCode(ACCEPTED_202.code())
                .withReasonPhrase(ACCEPTED_202.reasonPhrase())
                .withBody("some_body_response"),
            makeRequest(
                request()
                    .withMethod("POST")
                    .withPath(calculatePath("some_pathRequest"))
                    .withBody(params(
                        param("OTHERBodyParameterOneName", "Parameter One Value One", "Parameter One Value Two"),
                        param("bodyParameterTwoName", "Parameter Two")
                    ))
                    .withHeaders(header("headerNameRequest", "headerValueRequest"))
                    .withCookies(cookie("cookieNameRequest", "cookieValueRequest")),
                headersToIgnore)
        );
        // wrong query string parameter name
        assertEquals(
            response()
                .withStatusCode(ACCEPTED_202.code())
                .withReasonPhrase(ACCEPTED_202.reasonPhrase())
                .withBody("some_body_response"),
            makeRequest(
                request()
                    .withMethod("POST")
                    .withPath(calculatePath("some_pathRequest"))
                    .withBody(new StringBody("OTHERBodyParameterOneName=Parameter+One+Value+One" +
                        "&OTHERBodyParameterOneName=Parameter+One+Value+Two" +
                        "&bodyParameterTwoName=Parameter+Two"))
                    .withHeaders(header("headerNameRequest", "headerValueRequest"))
                    .withCookies(cookie("cookieNameRequest", "cookieValueRequest")),
                headersToIgnore)
        );
        // wrong query string parameter value
        assertEquals(
            response()
                .withStatusCode(ACCEPTED_202.code())
                .withReasonPhrase(ACCEPTED_202.reasonPhrase())
                .withBody("some_body_response"),
            makeRequest(
                request()
                    .withMethod("POST")
                    .withPath(calculatePath("some_pathRequest"))
                    .withBody(params(
                        param("bodyParameterOneName", "OTHER Parameter One Value One", "OTHER Parameter One Value Two"),
                        param("bodyParameterTwoName", "Parameter Two")
                    ))
                    .withHeaders(header("headerNameRequest", "headerValueRequest"))
                    .withCookies(cookie("cookieNameRequest", "cookieValueRequest")),
                headersToIgnore)
        );
        // wrong query string parameter value
        assertEquals(
            response()
                .withStatusCode(ACCEPTED_202.code())
                .withReasonPhrase(ACCEPTED_202.reasonPhrase())
                .withBody("some_body_response"),
            makeRequest(
                request()
                    .withMethod("POST")
                    .withPath(calculatePath("some_pathRequest"))
                    .withBody(new StringBody("bodyParameterOneName=OTHER Parameter+One+Value+One" +
                        "&bodyParameterOneName=OTHER Parameter+One+Value+Two" +
                        "&bodyParameterTwoName=Parameter+Two"))
                    .withHeaders(header("headerNameRequest", "headerValueRequest"))
                    .withCookies(cookie("cookieNameRequest", "cookieValueRequest")),
                headersToIgnore)
        );
    }

    @Test
    public void shouldReturnResponseByNotMatchingBodyParameterWithNotOperatorForName() {
        // when
        mockServerClient
            .when(
                request()
                    .withMethod("POST")
                    .withPath(calculatePath("some_pathRequest"))
                    .withBody(params(
                        param(not("bodyParameterOneName"), string("Parameter One Value One"), string("Parameter One Value Two")),
                        param("bodyParameterTwoName", "Parameter Two")
                    ))
            )
            .respond(
                response()
                    .withStatusCode(ACCEPTED_202.code())
                    .withReasonPhrase(ACCEPTED_202.reasonPhrase())
                    .withBody("some_body_response")
            );

        // then
        // wrong query string parameter name
        assertEquals(
            response()
                .withStatusCode(ACCEPTED_202.code())
                .withReasonPhrase(ACCEPTED_202.reasonPhrase())
                .withBody("some_body_response"),
            makeRequest(
                request()
                    .withMethod("POST")
                    .withPath(calculatePath("some_pathRequest"))
                    .withBody(params(
                        param("OTHERBodyParameterOneName", "Parameter One Value One", "Parameter One Value Two"),
                        param("bodyParameterTwoName", "Parameter Two")
                    ))
                    .withHeaders(header("headerNameRequest", "headerValueRequest"))
                    .withCookies(cookie("cookieNameRequest", "cookieValueRequest")),
                headersToIgnore)
        );
        // wrong query string parameter name
        assertEquals(
            response()
                .withStatusCode(ACCEPTED_202.code())
                .withReasonPhrase(ACCEPTED_202.reasonPhrase())
                .withBody("some_body_response"),
            makeRequest(
                request()
                    .withMethod("POST")
                    .withPath(calculatePath("some_pathRequest"))
                    .withBody(new StringBody("OTHERBodyParameterOneName=Parameter+One+Value+One" +
                        "&OTHERBodyParameterOneName=Parameter+One+Value+Two" +
                        "&bodyParameterTwoName=Parameter+Two"))
                    .withHeaders(header("headerNameRequest", "headerValueRequest"))
                    .withCookies(cookie("cookieNameRequest", "cookieValueRequest")),
                headersToIgnore)
        );
    }

    @Test
    public void shouldReturnResponseByNotMatchingBodyParameterWithNotOperatorForValue() {
        // when
        mockServerClient
            .when(
                request()
                    .withMethod("POST")
                    .withPath(calculatePath("some_pathRequest"))
                    .withBody(params(
                        param(string("bodyParameterOneName"), not("Parameter One Value One"), not("Parameter One Value Two")),
                        param("bodyParameterTwoName", "Parameter Two")
                    ))
            )
            .respond(
                response()
                    .withStatusCode(ACCEPTED_202.code())
                    .withReasonPhrase(ACCEPTED_202.reasonPhrase())
                    .withBody("some_body_response")
            );

        // then
        // wrong query string parameter value
        assertEquals(
            response()
                .withStatusCode(ACCEPTED_202.code())
                .withReasonPhrase(ACCEPTED_202.reasonPhrase())
                .withBody("some_body_response"),
            makeRequest(
                request()
                    .withMethod("POST")
                    .withPath(calculatePath("some_pathRequest"))
                    .withBody(params(
                        param("bodyParameterOneName", "OTHER Parameter One Value One", "OTHER Parameter One Value Two"),
                        param("bodyParameterTwoName", "Parameter Two")
                    ))
                    .withHeaders(header("headerNameRequest", "headerValueRequest"))
                    .withCookies(cookie("cookieNameRequest", "cookieValueRequest")),
                headersToIgnore)
        );
        // wrong query string parameter value
        assertEquals(
            response()
                .withStatusCode(ACCEPTED_202.code())
                .withReasonPhrase(ACCEPTED_202.reasonPhrase())
                .withBody("some_body_response"),
            makeRequest(
                request()
                    .withMethod("POST")
                    .withPath(calculatePath("some_pathRequest"))
                    .withBody(new StringBody("bodyParameterOneName=OTHER Parameter+One+Value+One" +
                        "&bodyParameterOneName=OTHER Parameter+One+Value+Two" +
                        "&bodyParameterTwoName=Parameter+Two"))
                    .withHeaders(header("headerNameRequest", "headerValueRequest"))
                    .withCookies(cookie("cookieNameRequest", "cookieValueRequest")),
                headersToIgnore)
        );
    }

    @Test
    public void shouldReturnResponseByNotMatchingQueryStringParameterWithNotOperatorForNameAndValue() {
        // when
        mockServerClient
            .when(
                request()
                    .withMethod("POST")
                    .withPath(calculatePath("some_pathRequest"))
                    .withQueryStringParameters(
                        param(not("queryStringParameterOneName"), not("Parameter One Value One"), not("Parameter One Value Two")),
                        param("queryStringParameterTwoName", "Parameter Two")
                    )
            )
            .respond(
                response()
                    .withStatusCode(ACCEPTED_202.code())
                    .withReasonPhrase(ACCEPTED_202.reasonPhrase())
                    .withBody("some_body_response")
            );

        // then
        // wrong query string parameter name
        assertEquals(
            response()
                .withStatusCode(ACCEPTED_202.code())
                .withReasonPhrase(ACCEPTED_202.reasonPhrase())
                .withBody("some_body_response"),
            makeRequest(
                request()
                    .withMethod("POST")
                    .withPath(calculatePath("some_pathRequest"))
                    .withQueryStringParameters(
                        param("OTHERQueryStringParameterOneName", "Parameter One Value One", "Parameter One Value Two"),
                        param("queryStringParameterTwoName", "Parameter Two")
                    ),
                headersToIgnore)
        );
        // wrong query string parameter value
        assertEquals(
            response()
                .withStatusCode(ACCEPTED_202.code())
                .withReasonPhrase(ACCEPTED_202.reasonPhrase())
                .withBody("some_body_response"),
            makeRequest(
                request()
                    .withMethod("POST")
                    .withPath(calculatePath("some_pathRequest"))
                    .withQueryStringParameters(
                        param("queryStringParameterOneName", "OTHER Parameter One Value One", "OTHER Parameter One Value Two"),
                        param("queryStringParameterTwoName", "Parameter Two")
                    ),
                headersToIgnore)
        );
    }

    @Test
    public void shouldReturnResponseByNotMatchingQueryStringParameterWithNotOperatorForName() {
        // when
        mockServerClient
            .when(
                request()
                    .withMethod("POST")
                    .withPath(calculatePath("some_pathRequest"))
                    .withQueryStringParameters(
                        param(not("queryStringParameterOneName"), string("Parameter One Value One"), string("Parameter One Value Two")),
                        param("queryStringParameterTwoName", "Parameter Two")
                    )
            )
            .respond(
                response()
                    .withStatusCode(ACCEPTED_202.code())
                    .withReasonPhrase(ACCEPTED_202.reasonPhrase())
                    .withBody("some_body_response")
            );

        // then
        // wrong query string parameter name
        assertEquals(
            response()
                .withStatusCode(ACCEPTED_202.code())
                .withReasonPhrase(ACCEPTED_202.reasonPhrase())
                .withBody("some_body_response"),
            makeRequest(
                request()
                    .withMethod("POST")
                    .withPath(calculatePath("some_pathRequest"))
                    .withQueryStringParameters(
                        param("OTHERQueryStringParameterOneName", "Parameter One Value One", "Parameter One Value Two"),
                        param("queryStringParameterTwoName", "Parameter Two")
                    ),
                headersToIgnore)
        );
    }

    @Test
    public void shouldReturnResponseByNotMatchingQueryStringParameterWithNotOperatorForValue() {
        // when
        mockServerClient
            .when(
                request()
                    .withMethod("POST")
                    .withPath(calculatePath("some_pathRequest"))
                    .withQueryStringParameters(
                        param(string("queryStringParameterOneName"), not("Parameter One Value One"), not("Parameter One Value Two")),
                        param("queryStringParameterTwoName", "Parameter Two")
                    )
            )
            .respond(
                response()
                    .withStatusCode(ACCEPTED_202.code())
                    .withReasonPhrase(ACCEPTED_202.reasonPhrase())
                    .withBody("some_body_response")
            );

        // then
        // wrong query string parameter value
        assertEquals(
            response()
                .withStatusCode(ACCEPTED_202.code())
                .withReasonPhrase(ACCEPTED_202.reasonPhrase())
                .withBody("some_body_response"),
            makeRequest(
                request()
                    .withMethod("POST")
                    .withPath(calculatePath("some_pathRequest"))
                    .withQueryStringParameters(
                        param("queryStringParameterOneName", "OTHER Parameter One Value One", "OTHER Parameter One Value Two"),
                        param("queryStringParameterTwoName", "Parameter Two")
                    ),
                headersToIgnore)
        );
    }

    @Test
    public void shouldReturnResponseByNotMatchingCookieWithNotOperatorForNameAndValue() {
        // when
        mockServerClient
            .when(
                request()
                    .withMethod("GET")
                    .withPath(calculatePath("some_pathRequest"))
                    .withCookies(
                        cookie(not("requestCookieNameOne"), not("requestCookieValueOne")),
                        cookie("requestCookieNameTwo", "requestCookieValueTwo")
                    )
            )
            .respond(
                response()
                    .withStatusCode(ACCEPTED_202.code())
                    .withReasonPhrase(ACCEPTED_202.reasonPhrase())
                    .withBody("some_body_response")
            );

        // then
        // wrong query string cookie name
        assertEquals(
            response()
                .withStatusCode(ACCEPTED_202.code())
                .withReasonPhrase(ACCEPTED_202.reasonPhrase())
                .withBody("some_body_response"),
            makeRequest(
                request()
                    .withMethod("GET")
                    .withPath(calculatePath("some_pathRequest"))
                    .withCookies(
                        cookie("OTHERrequestCookieNameOne", "requestCookieValueOne"),
                        cookie("requestCookieNameTwo", "requestCookieValueTwo")
                    ),
                headersToIgnore)
        );
        // wrong query string cookie value
        assertEquals(
            response()
                .withStatusCode(ACCEPTED_202.code())
                .withReasonPhrase(ACCEPTED_202.reasonPhrase())
                .withBody("some_body_response"),
            makeRequest(
                request()
                    .withMethod("GET")
                    .withPath(calculatePath("some_pathRequest"))
                    .withCookies(
                        cookie("requestCookieNameOne", "OTHERrequestCookieValueOne"),
                        cookie("requestCookieNameTwo", "requestCookieValueTwo")
                    ),
                headersToIgnore)
        );
    }

    @Test
    public void shouldReturnResponseByNotMatchingCookieWithNotOperatorForName() {
        // when
        mockServerClient
            .when(
                request()
                    .withMethod("GET")
                    .withPath(calculatePath("some_pathRequest"))
                    .withCookies(
                        cookie(not("requestCookieNameOne"), string("requestCookieValueOne")),
                        cookie("requestCookieNameTwo", "requestCookieValueTwo")
                    )
            )
            .respond(
                response()
                    .withStatusCode(ACCEPTED_202.code())
                    .withReasonPhrase(ACCEPTED_202.reasonPhrase())
                    .withBody("some_body_response")
            );

        // then
        // wrong query string cookie name
        assertEquals(
            response()
                .withStatusCode(ACCEPTED_202.code())
                .withReasonPhrase(ACCEPTED_202.reasonPhrase())
                .withBody("some_body_response"),
            makeRequest(
                request()
                    .withMethod("GET")
                    .withPath(calculatePath("some_pathRequest"))
                    .withCookies(
                        cookie("OTHERrequestCookieNameOne", "requestCookieValueOne"),
                        cookie("requestCookieNameTwo", "requestCookieValueTwo")
                    ),
                headersToIgnore)
        );
    }

    @Test
    public void shouldReturnResponseByNotMatchingCookieWithNotOperatorForValue() {
        // when
        mockServerClient
            .when(
                request()
                    .withMethod("GET")
                    .withPath(calculatePath("some_pathRequest"))
                    .withCookies(
                        cookie(string("requestCookieNameOne"), not("requestCookieValueOne")),
                        cookie("requestCookieNameTwo", "requestCookieValueTwo")
                    )
            )
            .respond(
                response()
                    .withStatusCode(ACCEPTED_202.code())
                    .withReasonPhrase(ACCEPTED_202.reasonPhrase())
                    .withBody("some_body_response")
            );

        // then
        // wrong query string cookie value
        assertEquals(
            response()
                .withStatusCode(ACCEPTED_202.code())
                .withReasonPhrase(ACCEPTED_202.reasonPhrase())
                .withBody("some_body_response"),
            makeRequest(
                request()
                    .withMethod("GET")
                    .withPath(calculatePath("some_pathRequest"))
                    .withCookies(
                        cookie("requestCookieNameOne", "OTHERrequestCookieValueOne"),
                        cookie("requestCookieNameTwo", "requestCookieValueTwo")
                    ),
                headersToIgnore)
        );
    }

    @Test
    public void shouldReturnResponseByNotMatchingHeaderWithNotOperatorForNameAndValue() {
        // when
        mockServerClient
            .when(
                request()
                    .withMethod("GET")
                    .withPath(calculatePath("some_pathRequest"))
                    .withHeaders(
                        header(not("requestHeaderNameOne"), not("requestHeaderValueOne")),
                        header("requestHeaderNameTwo", "requestHeaderValueTwo")
                    )
            )
            .respond(
                response()
                    .withStatusCode(ACCEPTED_202.code())
                    .withReasonPhrase(ACCEPTED_202.reasonPhrase())
                    .withBody("some_body_response")
            );

        // then
        // wrong query string header name
        assertEquals(
            response()
                .withStatusCode(ACCEPTED_202.code())
                .withReasonPhrase(ACCEPTED_202.reasonPhrase())
                .withBody("some_body_response"),
            makeRequest(
                request()
                    .withMethod("GET")
                    .withPath(calculatePath("some_pathRequest"))
                    .withHeaders(
                        header("OTHERrequestHeaderNameOne", "requestHeaderValueOne"),
                        header("requestHeaderNameTwo", "requestHeaderValueTwo")
                    ),
                headersToIgnore)
        );
        // wrong query string header value
        assertEquals(
            response()
                .withStatusCode(ACCEPTED_202.code())
                .withReasonPhrase(ACCEPTED_202.reasonPhrase())
                .withBody("some_body_response"),
            makeRequest(
                request()
                    .withMethod("GET")
                    .withPath(calculatePath("some_pathRequest"))
                    .withHeaders(
                        header("requestHeaderNameOne", "OTHERrequestHeaderValueOne"),
                        header("requestHeaderNameTwo", "requestHeaderValueTwo")
                    ),
                headersToIgnore)
        );
    }

    @Test
    public void shouldReturnResponseByNotMatchingHeaderWithNotOperatorForName() {
        // when
        mockServerClient
            .when(
                request()
                    .withMethod("GET")
                    .withPath(calculatePath("some_pathRequest"))
                    .withHeaders(
                        header(not("requestHeaderNameOne"), string("requestHeaderValueOne")),
                        header("requestHeaderNameTwo", "requestHeaderValueTwo")
                    )
            )
            .respond(
                response()
                    .withStatusCode(ACCEPTED_202.code())
                    .withReasonPhrase(ACCEPTED_202.reasonPhrase())
                    .withBody("some_body_response")
            );

        // then
        // wrong query string header name
        assertEquals(
            response()
                .withStatusCode(ACCEPTED_202.code())
                .withReasonPhrase(ACCEPTED_202.reasonPhrase())
                .withBody("some_body_response"),
            makeRequest(
                request()
                    .withMethod("GET")
                    .withPath(calculatePath("some_pathRequest"))
                    .withHeaders(
                        header("OTHERrequestHeaderNameOne", "requestHeaderValueOne"),
                        header("requestHeaderNameTwo", "requestHeaderValueTwo")
                    ),
                headersToIgnore)
        );
    }

    @Test
    public void shouldReturnResponseByNotMatchingHeaderWithNotOperatorForValue() {
        // when
        mockServerClient
            .when(
                request()
                    .withMethod("GET")
                    .withPath(calculatePath("some_pathRequest"))
                    .withHeaders(
                        header(string("requestHeaderNameOne"), not("requestHeaderValueOne")),
                        header("requestHeaderNameTwo", "requestHeaderValueTwo")
                    )
            )
            .respond(
                response()
                    .withStatusCode(ACCEPTED_202.code())
                    .withReasonPhrase(ACCEPTED_202.reasonPhrase())
                    .withBody("some_body_response")
            );

        // then
        // wrong query string header value
        assertEquals(
            response()
                .withStatusCode(ACCEPTED_202.code())
                .withReasonPhrase(ACCEPTED_202.reasonPhrase())
                .withBody("some_body_response"),
            makeRequest(
                request()
                    .withMethod("GET")
                    .withPath(calculatePath("some_pathRequest"))
                    .withHeaders(
                        header("requestHeaderNameOne", "OTHERrequestHeaderValueOne"),
                        header("requestHeaderNameTwo", "requestHeaderValueTwo")
                    ),
                headersToIgnore)
        );
    }

    @Test
    public void shouldNotReturnResponseForWhenTimeToLiveExpired() {
        // when
        mockServerClient
            .when(
                request().withPath(calculatePath("some_path")),
                exactly(2),
                TimeToLive.exactly(SECONDS, 3L)
            )
            .respond(
                response().withBody("some_body").withDelay(SECONDS, 3L)
            );

        // then
        assertEquals(
            response()
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase())
                .withBody("some_body"),
            makeRequest(
                request()
                    .withPath(calculatePath("some_path")),
                headersToIgnore)
        );
        assertEquals(
            response()
                .withStatusCode(HttpStatusCode.NOT_FOUND_404.code())
                .withReasonPhrase(HttpStatusCode.NOT_FOUND_404.reasonPhrase()),
            makeRequest(
                request()
                    .withPath(calculatePath("some_path")),
                headersToIgnore)
        );
    }

    @Test
    public void shouldNotReturnResponseForMatchingBodyWithNotOperator() {
        // when
        mockServerClient
            .when(
                request()
                    .withBody(Not.not(json("{" + NEW_LINE +
                        "    \"id\": 1," + NEW_LINE +
                        "    \"name\": \"A green door\"," + NEW_LINE +
                        "    \"price\": 12.50," + NEW_LINE +
                        "    \"tags\": [\"home\", \"green\"]" + NEW_LINE +
                        "}"))),
                exactly(2)
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
                .withStatusCode(HttpStatusCode.NOT_FOUND_404.code())
                .withReasonPhrase(HttpStatusCode.NOT_FOUND_404.reasonPhrase()),
            makeRequest(
                request()
                    .withPath(calculatePath("some_path"))
                    .withMethod("POST")
                    .withBody("{" + NEW_LINE +
                        "    \"id\": 1," + NEW_LINE +
                        "    \"extra_ignored_field\": \"some value\"," + NEW_LINE +
                        "    \"name\": \"A green door\"," + NEW_LINE +
                        "    \"price\": 12.50," + NEW_LINE +
                        "    \"tags\": [\"home\", \"green\"]" + NEW_LINE +
                        "}"),
                headersToIgnore)
        );
    }

    @Test
    public void shouldNotReturnResponseForNonMatchingXPathBody() {
        // when
        mockServerClient.when(request().withBody(new XPathBody("/bookstore/book[price>35]/price")), exactly(2)).respond(response().withBody("some_body"));

        // then
        assertEquals(
            response()
                .withStatusCode(HttpStatusCode.NOT_FOUND_404.code())
                .withReasonPhrase(HttpStatusCode.NOT_FOUND_404.reasonPhrase()),
            makeRequest(
                request()
                    .withSecure(true)
                    .withPath(calculatePath("some_path"))
                    .withMethod("POST")
                    .withBody(new StringBody("" +
                        "<?xml version=\"1.0\" encoding=\"ISO-8859-1\"?>" + NEW_LINE +
                        "<bookstore>" + NEW_LINE +
                        "  <book category=\"COOKING\">" + NEW_LINE +
                        "    <title lang=\"en\">Everyday Italian</title>" + NEW_LINE +
                        "    <author>Giada De Laurentiis</author>" + NEW_LINE +
                        "    <year>2005</year>" + NEW_LINE +
                        "    <price>30.00</price>" + NEW_LINE +
                        "  </book>" + NEW_LINE +
                        "  <book category=\"CHILDREN\">" + NEW_LINE +
                        "    <title lang=\"en\">Harry Potter</title>" + NEW_LINE +
                        "    <author>J K. Rowling</author>" + NEW_LINE +
                        "    <year>2005</year>" + NEW_LINE +
                        "    <price>29.99</price>" + NEW_LINE +
                        "  </book>" + NEW_LINE +
                        "  <book category=\"WEB\">" + NEW_LINE +
                        "    <title lang=\"en\">Learning XML</title>" + NEW_LINE +
                        "    <author>Erik T. Ray</author>" + NEW_LINE +
                        "    <year>2003</year>" + NEW_LINE +
                        "    <price>31.95</price>" + NEW_LINE +
                        "  </book>" + NEW_LINE +
                        "</bookstore>")),
                headersToIgnore)
        );
    }

    @Test
    public void shouldNotReturnResponseForNonMatchingXmlBody() {
        // when
        mockServerClient.when(request().withBody(xml("" +
            "<?xml version=\"1.0\" encoding=\"ISO-8859-1\"?>" + NEW_LINE +
            "<bookstore>" + NEW_LINE +
            "  <book category=\"COOKING\" nationality=\"ITALIAN\">" + NEW_LINE +
            "    <title lang=\"en\">Everyday Italian</title>" + NEW_LINE +
            "    <author>Giada De Laurentiis</author>" + NEW_LINE +
            "    <year>2005</year>" + NEW_LINE +
            "    <price>30.00</price>" + NEW_LINE +
            "  </book>" + NEW_LINE +
            "</bookstore>")), exactly(2)).respond(response().withBody("some_body"));

        // then
        assertEquals(
            response()
                .withStatusCode(HttpStatusCode.NOT_FOUND_404.code())
                .withReasonPhrase(HttpStatusCode.NOT_FOUND_404.reasonPhrase()),
            makeRequest(
                request()
                    .withSecure(true)
                    .withPath(calculatePath("some_path"))
                    .withMethod("POST")
                    .withBody(new StringBody("" +
                        "<?xml version=\"1.0\" encoding=\"ISO-8859-1\"?>" + NEW_LINE +
                        "<bookstore>" + NEW_LINE +
                        "  <book category=\"COOKING\">" + NEW_LINE +
                        "    <title lang=\"en\">Everyday Italian</title>" + NEW_LINE +
                        "    <author>Giada De Laurentiis</author>" + NEW_LINE +
                        "    <year>2005</year>" + NEW_LINE +
                        "    <price>30.00</price>" + NEW_LINE +
                        "  </book>" + NEW_LINE +
                        "</bookstore>")),
                headersToIgnore)
        );
    }

    @Test
    public void shouldNotReturnResponseForNonMatchingJsonBody() {
        // when
        mockServerClient.when(request().withBody(json("{" + NEW_LINE +
            "    \"id\": 1," + NEW_LINE +
            "    \"name\": \"A green door\"," + NEW_LINE +
            "    \"price\": 12.50," + NEW_LINE +
            "    \"tags\": [\"home\", \"green\"]" + NEW_LINE +
            "}")), exactly(2)).respond(response().withBody("some_body"));

        // then
        assertEquals(
            response()
                .withStatusCode(HttpStatusCode.NOT_FOUND_404.code())
                .withReasonPhrase(HttpStatusCode.NOT_FOUND_404.reasonPhrase()),
            makeRequest(
                request()
                    .withPath(calculatePath("some_path"))
                    .withMethod("POST")
                    .withBody("{" + NEW_LINE +
                        "    \"id\": 1," + NEW_LINE +
                        "    \"name\": \"---- XXXX WRONG VALUE XXXX ----\"," + NEW_LINE +
                        "    \"price\": 12.50," + NEW_LINE +
                        "    \"tags\": [\"home\", \"green\"]" + NEW_LINE +
                        "}"),
                headersToIgnore)
        );
    }

    @Test
    public void shouldNotReturnResponseForNonMatchingJsonBodyWithMatchType() {
        // when
        mockServerClient
            .when(
                request()
                    .withBody(json("{" + NEW_LINE +
                        "    \"id\": 1," + NEW_LINE +
                        "    \"name\": \"A green door\"," + NEW_LINE +
                        "    \"price\": 12.50," + NEW_LINE +
                        "    \"tags\": [\"home\", \"green\"]" + NEW_LINE +
                        "}", MatchType.STRICT)),
                exactly(2))
            .respond(
                response()
                    .withBody("some_body")
            );

        // then
        assertEquals(
            response()
                .withStatusCode(HttpStatusCode.NOT_FOUND_404.code())
                .withReasonPhrase(HttpStatusCode.NOT_FOUND_404.reasonPhrase()),
            makeRequest(
                request()
                    .withPath(calculatePath("some_path"))
                    .withMethod("POST")
                    .withBody("{" + NEW_LINE +
                        "    \"id\": 1," + NEW_LINE +
                        "    \"extra field\": \"some value\"," + NEW_LINE +
                        "    \"name\": \"A green door\"," + NEW_LINE +
                        "    \"price\": 12.50," + NEW_LINE +
                        "    \"tags\": [\"home\", \"green\"]" + NEW_LINE +
                        "}"),
                headersToIgnore)
        );
    }

    @Test
    public void shouldNotReturnResponseForNonMatchingJsonSchema() {
        // when
        mockServerClient.when(request().withBody(jsonSchema("{" + NEW_LINE +
            "    \"$schema\": \"http://json-schema.org/draft-04/schema#\"," + NEW_LINE +
            "    \"title\": \"Product\"," + NEW_LINE +
            "    \"description\": \"A product from Acme's catalog\"," + NEW_LINE +
            "    \"type\": \"object\"," + NEW_LINE +
            "    \"properties\": {" + NEW_LINE +
            "        \"id\": {" + NEW_LINE +
            "            \"description\": \"The unique identifier for a product\"," + NEW_LINE +
            "            \"type\": \"integer\"" + NEW_LINE +
            "        }," + NEW_LINE +
            "        \"name\": {" + NEW_LINE +
            "            \"description\": \"Name of the product\"," + NEW_LINE +
            "            \"type\": \"string\"" + NEW_LINE +
            "        }," + NEW_LINE +
            "        \"price\": {" + NEW_LINE +
            "            \"type\": \"number\"," + NEW_LINE +
            "            \"minimum\": 0," + NEW_LINE +
            "            \"exclusiveMinimum\": true" + NEW_LINE +
            "        }," + NEW_LINE +
            "        \"tags\": {" + NEW_LINE +
            "            \"type\": \"array\"," + NEW_LINE +
            "            \"items\": {" + NEW_LINE +
            "                \"type\": \"string\"" + NEW_LINE +
            "            }," + NEW_LINE +
            "            \"minItems\": 1," + NEW_LINE +
            "            \"uniqueItems\": true" + NEW_LINE +
            "        }" + NEW_LINE +
            "    }," + NEW_LINE +
            "    \"required\": [\"id\", \"name\", \"price\"]" + NEW_LINE +
            "}")), exactly(2)).respond(response().withBody("some_body"));

        // then
        assertEquals(
            response()
                .withStatusCode(HttpStatusCode.NOT_FOUND_404.code())
                .withReasonPhrase(HttpStatusCode.NOT_FOUND_404.reasonPhrase()),
            makeRequest(
                request()
                    .withPath(calculatePath("some_path"))
                    .withMethod("POST")
                    .withBody("{" + NEW_LINE +
                        "    \"id\": 1," + NEW_LINE +
                        "    \"wrong field name\": \"A green door\"," + NEW_LINE +
                        "    \"price\": 12.50," + NEW_LINE +
                        "    \"tags\": [\"home\", \"green\"]" + NEW_LINE +
                        "}"),
                headersToIgnore)
        );
    }

    @Test
    public void shouldNotReturnResponseForNonMatchingJsonPathBody() {
        // when
        mockServerClient.when(request().withBody(new JsonPathBody("$..book[?(@.price > $['expensive'])]")), exactly(2)).respond(response().withBody("some_body"));

        // then
        assertEquals(
            response()
                .withStatusCode(HttpStatusCode.NOT_FOUND_404.code())
                .withReasonPhrase(HttpStatusCode.NOT_FOUND_404.reasonPhrase()),
            makeRequest(
                request()
                    .withSecure(true)
                    .withPath(calculatePath("some_path"))
                    .withMethod("POST")
                    .withBody(new StringBody("" +
                        "{" + NEW_LINE +
                        "    \"store\": {" + NEW_LINE +
                        "        \"book\": [" + NEW_LINE +
                        "            {" + NEW_LINE +
                        "                \"category\": \"reference\"," + NEW_LINE +
                        "                \"author\": \"Nigel Rees\"," + NEW_LINE +
                        "                \"title\": \"Sayings of the Century\"," + NEW_LINE +
                        "                \"price\": 8.95" + NEW_LINE +
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
                        "}")),
                headersToIgnore)
        );
    }

    @Test
    public void shouldNotReturnResponseForMatchingPathWithNotOperator() {
        // when
        mockServerClient
            .when(
                request()
                    .withPath(not(calculatePath("some_path")))
            )
            .respond(
                response()
                    .withStatusCode(ACCEPTED_202.code())
                    .withReasonPhrase(ACCEPTED_202.reasonPhrase())
                    .withBody("some_body_response")
            );

        // then
        assertEquals(
            response()
                .withStatusCode(HttpStatusCode.NOT_FOUND_404.code())
                .withReasonPhrase(HttpStatusCode.NOT_FOUND_404.reasonPhrase()),
            makeRequest(
                request()
                    .withPath(calculatePath("some_path")),
                headersToIgnore)
        );
    }

    @Test
    public void shouldNotReturnResponseForMatchingMethodWithNotOperator() {
        // when
        mockServerClient
            .when(
                request()
                    .withMethod(not("GET"))
            )
            .respond(
                response()
                    .withStatusCode(ACCEPTED_202.code())
                    .withReasonPhrase(ACCEPTED_202.reasonPhrase())
                    .withBody("some_body_response")
            );

        // then
        assertEquals(
            response()
                .withStatusCode(HttpStatusCode.NOT_FOUND_404.code())
                .withReasonPhrase(HttpStatusCode.NOT_FOUND_404.reasonPhrase()),
            makeRequest(
                request()
                    .withMethod("GET"),
                headersToIgnore)
        );
    }

    @Test
    public void shouldNotReturnResponseForNonMatchingBodyParameterName() {
        // when
        mockServerClient
            .when(
                request()
                    .withMethod("POST")
                    .withPath(calculatePath("some_pathRequest"))
                    .withBody(params(
                        param("bodyParameterOneName", "Parameter One Value One", "Parameter One Value Two"),
                        param("bodyParameterTwoName", "Parameter Two")
                    ))
            )
            .respond(
                response()
                    .withStatusCode(ACCEPTED_202.code())
                    .withReasonPhrase(ACCEPTED_202.reasonPhrase())
                    .withBody("some_body_response")
                    .withHeaders(header("headerNameResponse", "headerValueResponse"))
                    .withCookies(cookie("cookieNameResponse", "cookieValueResponse"))
            );

        // then
        // wrong query string parameter name
        assertEquals(
            response()
                .withStatusCode(HttpStatusCode.NOT_FOUND_404.code())
                .withReasonPhrase(HttpStatusCode.NOT_FOUND_404.reasonPhrase()),
            makeRequest(
                request()
                    .withMethod("POST")
                    .withPath(calculatePath("some_pathRequest"))
                    .withBody(params(
                        param("OTHERBodyParameterOneName", "Parameter One Value One", "Parameter One Value Two"),
                        param("bodyParameterTwoName", "Parameter Two")
                    ))
                    .withHeaders(header("headerNameRequest", "headerValueRequest"))
                    .withCookies(cookie("cookieNameRequest", "cookieValueRequest")),
                headersToIgnore)
        );
        // wrong query string parameter name
        assertEquals(
            response()
                .withStatusCode(HttpStatusCode.NOT_FOUND_404.code())
                .withReasonPhrase(HttpStatusCode.NOT_FOUND_404.reasonPhrase()),
            makeRequest(
                request()
                    .withMethod("POST")
                    .withPath(calculatePath("some_pathRequest"))
                    .withBody(new StringBody("OTHERBodyParameterOneName=Parameter+One+Value+One" +
                        "&bodyParameterOneName=Parameter+One+Value+Two" +
                        "&bodyParameterTwoName=Parameter+Two"))
                    .withHeaders(header("headerNameRequest", "headerValueRequest"))
                    .withCookies(cookie("cookieNameRequest", "cookieValueRequest")),
                headersToIgnore)
        );
    }

    @Test
    public void shouldNotReturnResponseForMatchingBodyParameterWithNotOperator() {
        // when
        mockServerClient
            .when(
                request()
                    .withMethod("POST")
                    .withPath(calculatePath("some_pathRequest"))
                    .withBody(params(
                        param(not("bodyParameterOneName"), not("Parameter One Value One"), not("Parameter One Value Two")),
                        param("bodyParameterTwoName", "Parameter Two")
                    ))
            )
            .respond(
                response()
                    .withStatusCode(ACCEPTED_202.code())
                    .withReasonPhrase(ACCEPTED_202.reasonPhrase())
                    .withBody("some_body_response")
            );

        // then
        // wrong query string parameter name
        assertEquals(
            response()
                .withStatusCode(HttpStatusCode.NOT_FOUND_404.code())
                .withReasonPhrase(HttpStatusCode.NOT_FOUND_404.reasonPhrase()),
            makeRequest(
                request()
                    .withMethod("POST")
                    .withPath(calculatePath("some_pathRequest"))
                    .withBody(params(
                        param("bodyParameterOneName", "Parameter One Value One", "Parameter One Value Two"),
                        param("bodyParameterTwoName", "Parameter Two")
                    ))
                    .withHeaders(header("headerNameRequest", "headerValueRequest"))
                    .withCookies(cookie("cookieNameRequest", "cookieValueRequest")),
                headersToIgnore)
        );
        // wrong query string parameter name
        assertEquals(
            response()
                .withStatusCode(HttpStatusCode.NOT_FOUND_404.code())
                .withReasonPhrase(HttpStatusCode.NOT_FOUND_404.reasonPhrase()),
            makeRequest(
                request()
                    .withMethod("POST")
                    .withPath(calculatePath("some_pathRequest"))
                    .withBody(new StringBody("bodyParameterOneName=Other Parameter+One+Value+One" +
                        "&bodyParameterOneName=Parameter+One+Value+Two" +
                        "&bodyParameterTwoName=Parameter+Two"))
                    .withHeaders(header("headerNameRequest", "headerValueRequest"))
                    .withCookies(cookie("cookieNameRequest", "cookieValueRequest")),
                headersToIgnore)
        );
    }

    @Test
    public void shouldNotReturnResponseForNonMatchingBodyParameterValue() {
        // when
        mockServerClient
            .when(
                request()
                    .withMethod("POST")
                    .withPath(calculatePath("some_pathRequest"))
                    .withBody(params(
                        param("bodyParameterOneName", "Parameter One Value One", "Parameter One Value Two"),
                        param("bodyParameterTwoName", "Parameter Two")
                    ))
            )
            .respond(
                response()
                    .withStatusCode(ACCEPTED_202.code())
                    .withReasonPhrase(ACCEPTED_202.reasonPhrase())
                    .withBody("some_body_response")
                    .withHeaders(header("headerNameResponse", "headerValueResponse"))
                    .withCookies(cookie("cookieNameResponse", "cookieValueResponse"))
            );

        // then
        // wrong body parameter value
        assertEquals(
            response()
                .withStatusCode(HttpStatusCode.NOT_FOUND_404.code())
                .withReasonPhrase(HttpStatusCode.NOT_FOUND_404.reasonPhrase()),
            makeRequest(
                request()
                    .withMethod("POST")
                    .withPath(calculatePath("some_pathRequest"))
                    .withBody(params(
                        param("bodyParameterOneName", "Other Parameter One Value One", "Parameter One Value Two"),
                        param("bodyParameterTwoName", "Parameter Two")
                    ))
                    .withHeaders(header("headerNameRequest", "headerValueRequest"))
                    .withCookies(cookie("cookieNameRequest", "cookieValueRequest")),
                headersToIgnore)
        );
        // wrong body parameter value
        assertEquals(
            response()
                .withStatusCode(HttpStatusCode.NOT_FOUND_404.code())
                .withReasonPhrase(HttpStatusCode.NOT_FOUND_404.reasonPhrase()),
            makeRequest(
                request()
                    .withMethod("POST")
                    .withPath(calculatePath("some_pathRequest"))
                    .withBody(new StringBody("bodyParameterOneName=Other Parameter+One+Value+One" +
                        "&bodyParameterOneName=Parameter+One+Value+Two" +
                        "&bodyParameterTwoName=Parameter+Two"))
                    .withHeaders(header("headerNameRequest", "headerValueRequest"))
                    .withCookies(cookie("cookieNameRequest", "cookieValueRequest")),
                headersToIgnore)
        );
    }

    @Test
    public void shouldNotReturnResponseForNonMatchingQueryStringParameterName() {
        // when
        mockServerClient
            .when(
                request()
                    .withMethod("POST")
                    .withPath(calculatePath("some_pathRequest"))
                    .withQueryStringParameters(
                        param("queryStringParameterOneName", "queryStringParameterOneValueOne", "queryStringParameterOneValueTwo"),
                        param("queryStringParameterTwoName", "queryStringParameterTwoValue")
                    )
                    .withBody("some_bodyRequest")
            )
            .respond(
                response()
                    .withStatusCode(ACCEPTED_202.code())
                    .withReasonPhrase(ACCEPTED_202.reasonPhrase())
                    .withBody("some_body_response")
                    .withHeaders(header("headerNameResponse", "headerValueResponse"))
                    .withCookies(cookie("cookieNameResponse", "cookieValueResponse"))
            );

        // then
        // wrong query string parameter name
        assertEquals(
            response()
                .withStatusCode(HttpStatusCode.NOT_FOUND_404.code())
                .withReasonPhrase(HttpStatusCode.NOT_FOUND_404.reasonPhrase()),
            makeRequest(
                request()
                    .withMethod("POST")
                    .withPath(calculatePath("some_pathRequest"))
                    .withQueryStringParameters(
                        param("OTHERQueryStringParameterOneName", "queryStringParameterOneValueOne", "queryStringParameterOneValueTwo"),
                        param("queryStringParameterTwoName", "queryStringParameterTwoValue")
                    )
                    .withBody("some_bodyRequest")
                    .withHeaders(
                        header("headerNameRequest", "headerValueRequest")
                    )
                    .withCookies(cookie("cookieNameRequest", "cookieValueRequest")),
                headersToIgnore)
        );
    }

    @Test
    public void shouldNotReturnResponseForNonMatchingQueryStringParameterValue() {
        // when
        mockServerClient
            .when(
                request()
                    .withMethod("POST")
                    .withPath(calculatePath("some_pathRequest"))
                    .withQueryStringParameters(
                        param("queryStringParameterOneName", "queryStringParameterOneValueOne", "queryStringParameterOneValueTwo"),
                        param("queryStringParameterTwoName", "queryStringParameterTwoValue")
                    )
                    .withBody("some_bodyRequest")
            )
            .respond(
                response()
                    .withStatusCode(ACCEPTED_202.code())
                    .withReasonPhrase(ACCEPTED_202.reasonPhrase())
                    .withBody("some_body_response")
                    .withHeaders(header("headerNameResponse", "headerValueResponse"))
                    .withCookies(cookie("cookieNameResponse", "cookieValueResponse"))
            );

        // then
        // wrong query string parameter value
        assertEquals(
            response()
                .withStatusCode(HttpStatusCode.NOT_FOUND_404.code())
                .withReasonPhrase(HttpStatusCode.NOT_FOUND_404.reasonPhrase()),
            makeRequest(
                request()
                    .withMethod("POST")
                    .withPath(calculatePath("some_pathRequest"))
                    .withQueryStringParameters(
                        param("queryStringParameterOneName", "OTHERqueryStringParameterOneValueOne", "queryStringParameterOneValueTwo"),
                        param("queryStringParameterTwoName", "queryStringParameterTwoValue")
                    )
                    .withBody("some_bodyRequest")
                    .withHeaders(header("headerNameRequest", "headerValueRequest"))
                    .withCookies(cookie("cookieNameRequest", "cookieValueRequest")),
                headersToIgnore)
        );
    }

    @Test
    public void shouldNotReturnResponseForMatchingQueryStringParameterWithNotOperator() {
        // when
        mockServerClient
            .when(
                request()
                    .withMethod("POST")
                    .withPath(calculatePath("some_pathRequest"))
                    .withQueryStringParameters(
                        param(not("queryStringParameterOneName"), not("Parameter One Value One"), not("Parameter One Value Two")),
                        param("queryStringParameterTwoName", "Parameter Two")
                    )
            )
            .respond(
                response()
                    .withStatusCode(ACCEPTED_202.code())
                    .withReasonPhrase(ACCEPTED_202.reasonPhrase())
                    .withBody("some_body_response")
            );

        // then
        // wrong query string parameter name
        assertEquals(
            response()
                .withStatusCode(HttpStatusCode.NOT_FOUND_404.code())
                .withReasonPhrase(HttpStatusCode.NOT_FOUND_404.reasonPhrase()),
            makeRequest(
                request()
                    .withMethod("POST")
                    .withPath(calculatePath("some_pathRequest"))
                    .withQueryStringParameters(
                        param("queryStringParameterOneName", "Parameter One Value One", "Parameter One Value Two"),
                        param("queryStringParameterTwoName", "Parameter Two")
                    ),
                headersToIgnore)
        );
        // wrong query string parameter name
        assertEquals(
            response()
                .withStatusCode(HttpStatusCode.NOT_FOUND_404.code())
                .withReasonPhrase(HttpStatusCode.NOT_FOUND_404.reasonPhrase()),
            makeRequest(
                request()
                    .withMethod("POST")
                    .withPath(calculatePath("some_pathRequest"))
                    .withQueryStringParameters(
                        param("queryStringParameterOneName", "Parameter One Value One", "Parameter One Value Two"),
                        param("queryStringParameterTwoName", "Parameter Two")
                    ),
                headersToIgnore)
        );
    }

    @Test
    public void shouldNotReturnResponseForNonMatchingCookieName() {
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
                    .withStatusCode(ACCEPTED_202.code())
                    .withReasonPhrase(ACCEPTED_202.reasonPhrase())
                    .withBody("some_body")
                    .withHeaders(header("headerName", "headerValue"))
                    .withCookies(cookie("cookieName", "cookieValue"))
            );

        // then
        assertEquals(
            response()
                .withStatusCode(HttpStatusCode.NOT_FOUND_404.code())
                .withReasonPhrase(HttpStatusCode.NOT_FOUND_404.reasonPhrase()),
            makeRequest(
                request()
                    .withMethod("GET")
                    .withPath(calculatePath("some_path"))
                    .withQueryStringParameters(
                        param("queryStringParameterOneName", "queryStringParameterOneValue"),
                        param("queryStringParameterTwoName", "queryStringParameterTwoValue")
                    )
                    .withBody(exact("some_body"))
                    .withHeaders(header("headerName", "headerValue"))
                    .withCookies(cookie("cookieOtherName", "cookieValue")),
                headersToIgnore)
        );
    }

    @Test
    public void shouldNotReturnResponseForNonMatchingCookieValue() {
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
                    .withStatusCode(ACCEPTED_202.code())
                    .withReasonPhrase(ACCEPTED_202.reasonPhrase())
                    .withBody("some_body")
                    .withHeaders(header("headerName", "headerValue"))
                    .withCookies(cookie("cookieName", "cookieValue"))
            );

        // then
        assertEquals(
            response()
                .withStatusCode(HttpStatusCode.NOT_FOUND_404.code())
                .withReasonPhrase(HttpStatusCode.NOT_FOUND_404.reasonPhrase()),
            makeRequest(
                request()
                    .withMethod("GET")
                    .withPath(calculatePath("some_path"))
                    .withQueryStringParameters(
                        param("queryStringParameterOneName", "queryStringParameterOneValue"),
                        param("queryStringParameterTwoName", "queryStringParameterTwoValue")
                    )
                    .withBody(exact("some_body"))
                    .withHeaders(header("headerName", "headerValue"))
                    .withCookies(cookie("cookieName", "cookieOtherValue")),
                headersToIgnore)
        );
    }

    @Test
    public void shouldNotReturnResponseForMatchingCookieWithNotOperator() {
        // when
        mockServerClient
            .when(
                request()
                    .withMethod("GET")
                    .withPath(calculatePath("some_pathRequest"))
                    .withCookies(
                        cookie(not("requestCookieNameOne"), not("requestCookieValueOne")),
                        cookie("requestCookieNameTwo", "requestCookieValueTwo")
                    )
            )
            .respond(
                response()
                    .withStatusCode(ACCEPTED_202.code())
                    .withReasonPhrase(ACCEPTED_202.reasonPhrase())
                    .withBody("some_body_response")
            );

        // then
        // wrong query string parameter name
        assertEquals(
            response()
                .withStatusCode(HttpStatusCode.NOT_FOUND_404.code())
                .withReasonPhrase(HttpStatusCode.NOT_FOUND_404.reasonPhrase()),
            makeRequest(
                request()
                    .withMethod("GET")
                    .withPath(calculatePath("some_pathRequest"))
                    .withCookies(
                        cookie("requestCookieNameOne", "requestCookieValueOne"),
                        cookie("requestCookieNameTwo", "requestCookieValueTwo")
                    ),
                headersToIgnore)
        );
        // wrong query string parameter name
        assertEquals(
            response()
                .withStatusCode(HttpStatusCode.NOT_FOUND_404.code())
                .withReasonPhrase(HttpStatusCode.NOT_FOUND_404.reasonPhrase()),
            makeRequest(
                request()
                    .withMethod("GET")
                    .withPath(calculatePath("some_pathRequest"))
                    .withCookies(
                        cookie("requestCookieNameOne", "requestCookieValueOne"),
                        cookie("requestCookieNameTwo", "requestCookieValueTwo")
                    ),
                headersToIgnore)
        );
    }

    @Test
    public void shouldNotReturnResponseForNonMatchingHeaderName() {
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
                    .withStatusCode(ACCEPTED_202.code())
                    .withReasonPhrase(ACCEPTED_202.reasonPhrase())
                    .withBody("some_body")
                    .withHeaders(header("headerName", "headerValue"))
                    .withCookies(cookie("cookieName", "cookieValue"))
            );

        // then
        assertEquals(
            response()
                .withStatusCode(HttpStatusCode.NOT_FOUND_404.code())
                .withReasonPhrase(HttpStatusCode.NOT_FOUND_404.reasonPhrase()),
            makeRequest(
                request()
                    .withMethod("GET")
                    .withPath(calculatePath("some_path"))
                    .withQueryStringParameters(
                        param("queryStringParameterOneName", "queryStringParameterOneValue"),
                        param("queryStringParameterTwoName", "queryStringParameterTwoValue")
                    )
                    .withBody(exact("some_body"))
                    .withHeaders(header("headerOtherName", "headerValue"))
                    .withCookies(cookie("cookieName", "cookieValue")),
                headersToIgnore)
        );
    }

    @Test
    public void shouldNotReturnResponseForNonMatchingHeaderValue() {
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
                    .withStatusCode(ACCEPTED_202.code())
                    .withReasonPhrase(ACCEPTED_202.reasonPhrase())
                    .withBody("some_body")
                    .withHeaders(header("headerName", "headerValue"))
                    .withCookies(cookie("cookieName", "cookieValue"))
            );

        // then
        assertEquals(
            response()
                .withStatusCode(HttpStatusCode.NOT_FOUND_404.code())
                .withReasonPhrase(HttpStatusCode.NOT_FOUND_404.reasonPhrase()),
            makeRequest(
                request()
                    .withMethod("GET")
                    .withPath(calculatePath("some_path"))
                    .withQueryStringParameters(
                        param("queryStringParameterOneName", "queryStringParameterOneValue"),
                        param("queryStringParameterTwoName", "queryStringParameterTwoValue")
                    )
                    .withBody(exact("some_body"))
                    .withHeaders(header("headerName", "headerOtherValue"))
                    .withCookies(cookie("cookieName", "cookieValue")),
                headersToIgnore)
        );
    }

    @Test
    public void shouldNotReturnResponseForMatchingHeaderWithNotOperator() {
        // when
        mockServerClient
            .when(
                request()
                    .withMethod("GET")
                    .withPath(calculatePath("some_pathRequest"))
                    .withHeaders(
                        header(not("requestHeaderNameOne"), not("requestHeaderValueOne")),
                        header("requestHeaderNameTwo", "requestHeaderValueTwo")
                    )
            )
            .respond(
                response()
                    .withStatusCode(ACCEPTED_202.code())
                    .withReasonPhrase(ACCEPTED_202.reasonPhrase())
                    .withBody("some_body_response")
            );

        // then
        // wrong query string parameter name
        assertEquals(
            response()
                .withStatusCode(HttpStatusCode.NOT_FOUND_404.code())
                .withReasonPhrase(HttpStatusCode.NOT_FOUND_404.reasonPhrase()),
            makeRequest(
                request()
                    .withMethod("GET")
                    .withPath(calculatePath("some_pathRequest"))
                    .withHeaders(
                        header("requestHeaderNameOne", "requestHeaderValueOne"),
                        header("requestHeaderNameTwo", "requestHeaderValueTwo")
                    ),
                headersToIgnore)
        );
        // wrong query string parameter name
        assertEquals(
            response()
                .withStatusCode(HttpStatusCode.NOT_FOUND_404.code())
                .withReasonPhrase(HttpStatusCode.NOT_FOUND_404.reasonPhrase()),
            makeRequest(
                request()
                    .withMethod("GET")
                    .withPath(calculatePath("some_pathRequest"))
                    .withHeaders(
                        header("requestHeaderNameOne", "requestHeaderValueOne"),
                        header("requestHeaderNameTwo", "requestHeaderValueTwo")
                    ),
                headersToIgnore)
        );
    }

    @Test
    public void shouldVerifyReceivedRequestInSsl() {
        // when
        mockServerClient
            .when(
                request()
                    .withPath(calculatePath("some.*path")), exactly(2)
            )
            .respond(
                response()
                    .withBody("some_body")
            );

        // then
        // - in http
        assertEquals(
            response()
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase())
                .withBody("some_body"),
            makeRequest(
                request()
                    .withPath(calculatePath("some_path")),
                headersToIgnore)
        );
        mockServerClient.verify(request()
            .withPath(calculatePath("some_path"))
            .withSecure(false));
        mockServerClient.verify(request()
            .withPath(calculatePath("some_path"))
            .withSecure(false), VerificationTimes.exactly(1));

        // - in https
        assertEquals(
            response()
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase())
                .withBody("some_body"),
            makeRequest(
                request()
                    .withPath(calculatePath("some_secure_path"))
                    .withSecure(true),
                headersToIgnore)
        );
        mockServerClient.verify(request()
            .withPath(calculatePath("some_secure_path"))
            .withSecure(true));
        mockServerClient.verify(request()
            .withPath(calculatePath("some_secure_path"))
            .withSecure(true), VerificationTimes.exactly(1));
    }

    @Test
    public void shouldVerifyReceivedRequestsWithRegexBody() {
        // when
        mockServerClient
            .when(
                request()
                    .withMethod("POST")
                    .withPath(calculatePath("some_path"))
                    .withBody("{type: 'some_random_type', value: 'some_random_value'}"),
                exactly(2)
            )
            .respond(
                response()
                    .withBody("some_response")
            );

        // then
        assertEquals(
            response()
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase())
                .withBody("some_response"),
            makeRequest(
                request()
                    .withMethod("POST")
                    .withPath(calculatePath("some_path"))
                    .withBody("{type: 'some_random_type', value: 'some_random_value'}"),
                headersToIgnore)
        );
        mockServerClient.verify(
            request()
                .withBody(regex("\\{type\\: \\'some_random_type\\'\\, value\\: \\'some_random_value\\'\\}"))
        );
        mockServerClient.verify(
            request()
                .withBody(regex("\\{type\\: \\'some_random_type\\'\\, value\\: \\'some_random_value\\'\\}")),
            VerificationTimes.exactly(1)
        );
    }

    @Test
    public void shouldVerifyReceivedRequestsWithNoBody() {
        // when
        mockServerClient.when(request().withPath(calculatePath("some_path")), exactly(2)).respond(response());

        // then
        assertEquals(
            response()
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase()),
            makeRequest(
                request()
                    .withPath(calculatePath("some_path")),
                headersToIgnore)
        );
        mockServerClient.verify(request()
            .withPath(calculatePath("some_path")));
        mockServerClient.verify(request()
            .withPath(calculatePath("some_path")), VerificationTimes.exactly(1));
    }

    @Test
    public void shouldVerifyReceivedRequestsWithNoMatchingExpectation() {
        // when
        makeRequest(
            request()
                .withPath(calculatePath("some_path")),
            headersToIgnore);

        mockServerClient.verify(request()
            .withPath(calculatePath("some_path")));
        mockServerClient.verify(request()
            .withPath(calculatePath("some_path")), VerificationTimes.exactly(1));
        mockServerClient.verify(request()
            .withPath(calculatePath("some_path")), VerificationTimes.once());
    }

    @Test
    public void shouldVerifyTooManyRequestsReceived() {
        // when
        mockServerClient.when(request().withPath(calculatePath("some_path")), exactly(2)).respond(response().withBody("some_body"));

        // then
        assertEquals(
            response()
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase())
                .withBody("some_body"),
            makeRequest(
                request()
                    .withPath(calculatePath("some_path")),
                headersToIgnore)
        );
        try {
            mockServerClient.verify(request()
                .withPath(calculatePath("some_path")), VerificationTimes.exactly(0));
            fail("expected exception to be thrown");
        } catch (AssertionError ae) {
            assertThat(ae.getMessage(), startsWith("Request not found exactly 0 times, expected:<{" + NEW_LINE +
                "  \"path\" : \"" + calculatePath("some_path") + "\"" + NEW_LINE +
                "}> but was:<{" + NEW_LINE +
                "  \"method\" : \"GET\"," + NEW_LINE +
                "  \"path\" : \"" + calculatePath("some_path") + "\"," + NEW_LINE));
        }
    }

    @Test
    public void shouldVerifyNoMatchingRequestsReceived() {
        // when
        mockServerClient.when(request().withPath(calculatePath("some_path")), exactly(2)).respond(response().withBody("some_body"));

        // then
        assertEquals(
            response()
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase())
                .withBody("some_body"),
            makeRequest(
                request()
                    .withPath(calculatePath("some_path")),
                headersToIgnore)
        );
        try {
            mockServerClient.verify(request()
                .withPath(calculatePath("some_other_path")), VerificationTimes.exactly(2));
            fail("expected exception to be thrown");
        } catch (AssertionError ae) {
            assertThat(ae.getMessage(), startsWith("Request not found exactly 2 times, expected:<{" + NEW_LINE +
                "  \"path\" : \"" + calculatePath("some_other_path") + "\"" + NEW_LINE +
                "}> but was:<{" + NEW_LINE +
                "  \"method\" : \"GET\"," + NEW_LINE +
                "  \"path\" : \"" + calculatePath("some_path") + "\"," + NEW_LINE));
        }
    }

    @Test
    public void shouldNotVerifyNoRequestsReceived() {
        // when
        mockServerClient.when(request().withPath(calculatePath("some_path")), exactly(2)).respond(response().withBody("some_body"));

        // then
        assertEquals(
            response()
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase())
                .withBody("some_body"),
            makeRequest(
                request()
                    .withPath(calculatePath("some_path")),
                headersToIgnore)
        );
        try {
            mockServerClient.verifyZeroInteractions();
            fail("expected exception to be thrown");
        } catch (AssertionError ae) {
            assertThat(ae.getMessage(), startsWith("Request not found exactly 0 times, expected:<{ }> but was:<{" + NEW_LINE +
                "  \"method\" : \"GET\"," + NEW_LINE +
                "  \"path\" : \"" + calculatePath("some_path") + "\"," + NEW_LINE));
        }
    }

    @Test
    public void shouldVerifyNoMatchingRequestsReceivedInSsl() {
        // when
        mockServerClient.when(request().withPath(calculatePath("some.*path")), exactly(2)).respond(response().withBody("some_body"));

        // then
        assertEquals(
            response()
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase())
                .withBody("some_body"),
            makeRequest(
                request()
                    .withPath(calculatePath("some_path")),
                headersToIgnore)
        );
        try {
            mockServerClient.verify(
                request()
                    .withPath(calculatePath("some_path"))
                    .withSecure(true),
                VerificationTimes.atLeast(1)
            );
            fail("expected exception to be thrown");
        } catch (AssertionError ae) {
            assertThat(ae.getMessage(), startsWith("Request not found at least once, expected:<{" + NEW_LINE +
                "  \"path\" : \"" + calculatePath("some_path") + "\"," + NEW_LINE +
                "  \"secure\" : true" + NEW_LINE +
                "}> but was:<{" + NEW_LINE +
                "  \"method\" : \"GET\"," + NEW_LINE +
                "  \"path\" : \"" + calculatePath("some_path") + "\"," + NEW_LINE));
        }
    }

    @Test
    public void shouldVerifySequenceOfRequestsReceivedIncludingThoseNotMatchingAnException() {
        // when
        mockServerClient.when(request().withPath(calculatePath("some_path.*")), exactly(4)).respond(response().withBody("some_body"));

        // then
        assertEquals(
            response("some_body"),
            makeRequest(
                request().withPath(calculatePath("some_path_one")),
                headersToIgnore)
        );
        assertEquals(
            notFoundResponse(),
            makeRequest(
                request().withPath(calculatePath("not_found")),
                headersToIgnore)
        );
        assertEquals(
            response("some_body"),
            makeRequest(
                request().withPath(calculatePath("some_path_three")),
                headersToIgnore)
        );
        mockServerClient.verify(request(calculatePath("some_path_one")), request(calculatePath("some_path_three")));
        mockServerClient.verify(request(calculatePath("some_path_one")), request(calculatePath("not_found")));
        mockServerClient.verify(request(calculatePath("some_path_one")), request(calculatePath("not_found")), request(calculatePath("some_path_three")));
        mockServerClient.verify(request(calculatePath("some_path_one")), request(calculatePath("not_found")), request(calculatePath("some_path_three")));
    }

    @Test
    public void shouldVerifySequenceOfRequestsNotReceived() {
        // when
        mockServerClient.when(request().withPath(calculatePath("some_path.*")), exactly(6)).respond(response().withBody("some_body"));

        // then
        assertEquals(
            response("some_body"),
            makeRequest(
                request().withPath(calculatePath("some_path_one")),
                headersToIgnore)
        );
        assertEquals(
            response("some_body"),
            makeRequest(
                request().withPath(calculatePath("some_path_two")),
                headersToIgnore)
        );
        assertEquals(
            response("some_body"),
            makeRequest(
                request().withPath(calculatePath("some_path_three")),
                headersToIgnore)
        );
        try {
            mockServerClient.verify(request(calculatePath("some_path_two")), request(calculatePath("some_path_one")));
            fail("expected exception to be thrown");
        } catch (AssertionError ae) {
            assertThat(ae.getMessage(), startsWith("Request sequence not found, expected:<[ {" + NEW_LINE +
                "  \"path\" : \"" + calculatePath("some_path_two") + "\"" + NEW_LINE +
                "}, {" + NEW_LINE +
                "  \"path\" : \"" + calculatePath("some_path_one") + "\"" + NEW_LINE +
                "} ]> but was:<[ {" + NEW_LINE +
                "  \"method\" : \"GET\"," + NEW_LINE +
                "  \"path\" : \"" + calculatePath("some_path_one") + "\"," + NEW_LINE));
        }
        try {
            mockServerClient.verify(request(calculatePath("some_path_three")), request(calculatePath("some_path_two")));
            fail("expected exception to be thrown");
        } catch (AssertionError ae) {
            assertThat(ae.getMessage(), startsWith("Request sequence not found, expected:<[ {" + NEW_LINE +
                "  \"path\" : \"" + calculatePath("some_path_three") + "\"" + NEW_LINE +
                "}, {" + NEW_LINE +
                "  \"path\" : \"" + calculatePath("some_path_two") + "\"" + NEW_LINE +
                "} ]> but was:<[ {" + NEW_LINE +
                "  \"method\" : \"GET\"," + NEW_LINE +
                "  \"path\" : \"" + calculatePath("some_path_one") + "\"," + NEW_LINE));
        }
        try {
            mockServerClient.verify(request(calculatePath("some_path_four")));
            fail("expected exception to be thrown");
        } catch (AssertionError ae) {
            assertThat(ae.getMessage(), startsWith("Request sequence not found, expected:<[ {" + NEW_LINE +
                "  \"path\" : \"" + calculatePath("some_path_four") + "\"" + NEW_LINE +
                "} ]> but was:<[ {" + NEW_LINE +
                "  \"method\" : \"GET\"," + NEW_LINE +
                "  \"path\" : \"" + calculatePath("some_path_one") + "\"," + NEW_LINE));
        }
    }

    @Test
    public void shouldRetrieveRecordedRequestsAsJson() {
        // when
        mockServerClient.when(request().withPath(calculatePath("some_path.*")), exactly(4)).respond(response().withBody("some_body"));
        assertEquals(
            response("some_body"),
            makeRequest(
                request().withPath(calculatePath("some_path_one")),
                headersToIgnore)
        );
        assertEquals(
            notFoundResponse(),
            makeRequest(
                request().withPath(calculatePath("not_found")),
                headersToIgnore)
        );
        assertEquals(
            response("some_body"),
            makeRequest(
                request().withPath(calculatePath("some_path_three")),
                headersToIgnore)
        );

        // then
        verifyRequestsMatches(
            new HttpRequestSerializer(new MockServerLogger()).deserializeArray(mockServerClient.retrieveRecordedRequests(request().withPath(calculatePath("some_path.*")), Format.JSON)),
            request(calculatePath("some_path_one")),
            request(calculatePath("some_path_three"))
        );

        verifyRequestsMatches(
            new HttpRequestSerializer(new MockServerLogger()).deserializeArray(mockServerClient.retrieveRecordedRequests(request(), Format.JSON)),
            request(calculatePath("some_path_one")),
            request(calculatePath("not_found")),
            request(calculatePath("some_path_three"))
        );

        verifyRequestsMatches(
            new HttpRequestSerializer(new MockServerLogger()).deserializeArray(mockServerClient.retrieveRecordedRequests(null, Format.JSON)),
            request(calculatePath("some_path_one")),
            request(calculatePath("not_found")),
            request(calculatePath("some_path_three"))
        );
    }

    @Test
    public void shouldRetrieveRecordedRequestsAsLogEntries() {
        // given
        mockServerClient.when(request().withPath(calculatePath("some_path.*")), exactly(4)).respond(response().withBody("some_body"));
        assertEquals(
            response("some_body"),
            makeRequest(
                request().withPath(calculatePath("some_path_one")),
                headersToIgnore)
        );
        assertEquals(
            notFoundResponse(),
            makeRequest(
                request().withPath(calculatePath("not_found")),
                headersToIgnore)
        );
        assertEquals(
            response("some_body"),
            makeRequest(
                request().withPath(calculatePath("some_path_three")),
                headersToIgnore)
        );

        // when
        String logEntriesActual = mockServerClient.retrieveRecordedRequests(request().withPath(calculatePath("some_path.*")), Format.LOG_ENTRIES);
        HttpRequest requestOne = request("/some_path_one")
            .withMethod("GET")
            .withHeader("host", "localhost:" + this.getServerPort())
            .withHeader("accept-encoding", "gzip,deflate")
            .withHeader("content-length", "0")
            .withHeader("connection", "keep-alive")
            .withKeepAlive(true)
            .withSecure(false);
        HttpRequest requestTwo = request("/some_path_three")
            .withMethod("GET")
            .withHeader("host", "localhost:" + this.getServerPort())
            .withHeader("accept-encoding", "gzip,deflate")
            .withHeader("content-length", "0")
            .withHeader("connection", "keep-alive")
            .withKeepAlive(true)
            .withSecure(false);
        List<LogEntry> logEntriesExpected = Arrays.asList(
            new LogEntry()
                .setType(RECEIVED_REQUEST)
                .setLogLevel(Level.INFO)
                .setHttpRequest(requestOne)
                .setMessageFormat("received request:{}")
                .setArguments(requestOne),
            new LogEntry()
                .setType(RECEIVED_REQUEST)
                .setLogLevel(Level.INFO)
                .setHttpRequest(requestTwo)
                .setMessageFormat("received request:{}")
                .setArguments(requestTwo)
        );

        // then
        assertThat(logEntriesActual, is(new LogEntrySerializer(new MockServerLogger()).serialize(logEntriesExpected)));
    }

    @Test
    public void shouldRetrieveActiveExpectationsAsJson() {
        // when
        mockServerClient.when(request().withPath(calculatePath("some_path.*")), exactly(4))
            .respond(response().withBody("some_body"));
        mockServerClient.when(request().withPath(calculatePath("some_path.*")))
            .respond(response().withBody("some_body"));
        mockServerClient.when(request().withPath(calculatePath("some_other_path")))
            .respond(response().withBody("some_other_body"));
        mockServerClient.when(request().withPath(calculatePath("some_forward_path")))
            .forward(forward());

        // then
        assertThat(
            new ExpectationSerializer(new MockServerLogger())
                .deserializeArray(
                    mockServerClient
                        .retrieveActiveExpectations(request().withPath(calculatePath("some_path.*")), Format.JSON),
                    false
                ),
            arrayContaining(
                new Expectation(request().withPath(calculatePath("some_path.*")), exactly(4), TimeToLive.unlimited(), 0)
                    .thenRespond(response().withBody("some_body")),
                new Expectation(request().withPath(calculatePath("some_path.*")))
                    .thenRespond(response().withBody("some_body"))
            )
        );

        assertThat(
            new ExpectationSerializer(new MockServerLogger())
                .deserializeArray(
                    mockServerClient
                        .retrieveActiveExpectations(null, Format.JSON),
                    false
                ),
            arrayContaining(
                new Expectation(request().withPath(calculatePath("some_path.*")), exactly(4), TimeToLive.unlimited(), 0)
                    .thenRespond(response().withBody("some_body")),
                new Expectation(request().withPath(calculatePath("some_path.*")))
                    .thenRespond(response().withBody("some_body")),
                new Expectation(request().withPath(calculatePath("some_other_path")))
                    .thenRespond(response().withBody("some_other_body")),
                new Expectation(request().withPath(calculatePath("some_forward_path")))
                    .thenForward(forward())
            )
        );

        assertThat(
            new ExpectationSerializer(new MockServerLogger())
                .deserializeArray(
                    mockServerClient
                        .retrieveActiveExpectations(request(), Format.JSON),
                    false
                ),
            arrayContaining(
                new Expectation(request().withPath(calculatePath("some_path.*")), exactly(4), TimeToLive.unlimited(), 0)
                    .thenRespond(response().withBody("some_body")),
                new Expectation(request().withPath(calculatePath("some_path.*")))
                    .thenRespond(response().withBody("some_body")),
                new Expectation(request().withPath(calculatePath("some_other_path")))
                    .thenRespond(response().withBody("some_other_body")),
                new Expectation(request().withPath(calculatePath("some_forward_path")))
                    .thenForward(forward())
            )
        );
    }

    @Test
    public void shouldRetrieveActiveExpectationsAsJava() {
        // when
        mockServerClient.when(request().withPath(calculatePath("some_path.*")), exactly(4))
            .respond(response().withBody("some_body"));
        mockServerClient.when(request().withPath(calculatePath("some_path.*")))
            .respond(response().withBody("some_body"));
        mockServerClient.when(request().withPath(calculatePath("some_other_path")))
            .respond(response().withBody("some_other_body"));
        mockServerClient.when(request().withPath(calculatePath("some_forward_path")))
            .forward(forward());

        // then
        assertThat(
            mockServerClient.retrieveActiveExpectations(request().withPath(calculatePath("some_path.*")), Format.JAVA),
            is(new ExpectationToJavaSerializer().serialize(Arrays.asList(
                new Expectation(request().withPath(calculatePath("some_path.*")), exactly(4), TimeToLive.unlimited(), 0)
                    .thenRespond(response().withBody("some_body")),
                new Expectation(request().withPath(calculatePath("some_path.*")))
                    .thenRespond(response().withBody("some_body"))
            )))
        );

        assertThat(
            mockServerClient.retrieveActiveExpectations(null, Format.JAVA),
            is(new ExpectationToJavaSerializer().serialize(Arrays.asList(
                new Expectation(request().withPath(calculatePath("some_path.*")), exactly(4), TimeToLive.unlimited(), 0)
                    .thenRespond(response().withBody("some_body")),
                new Expectation(request().withPath(calculatePath("some_path.*")))
                    .thenRespond(response().withBody("some_body")),
                new Expectation(request().withPath(calculatePath("some_other_path")))
                    .thenRespond(response().withBody("some_other_body")),
                new Expectation(request().withPath(calculatePath("some_forward_path")))
                    .thenForward(forward())
            )))
        );

        assertThat(
            mockServerClient.retrieveActiveExpectations(request(), Format.JAVA),
            is(new ExpectationToJavaSerializer().serialize(Arrays.asList(
                new Expectation(request().withPath(calculatePath("some_path.*")), exactly(4), TimeToLive.unlimited(), 0)
                    .thenRespond(response().withBody("some_body")),
                new Expectation(request().withPath(calculatePath("some_path.*")))
                    .thenRespond(response().withBody("some_body")),
                new Expectation(request().withPath(calculatePath("some_other_path")))
                    .thenRespond(response().withBody("some_other_body")),
                new Expectation(request().withPath(calculatePath("some_forward_path")))
                    .thenForward(forward())
            )))
        );
    }

    @Test
    public void shouldRetrieveRecordedExpectationsAsJson() {
        // when
        mockServerClient.when(request().withPath(calculatePath("some_path.*")), exactly(4)).forward(
            forward()
                .withHost("127.0.0.1")
                .withPort(insecureEchoServer.getPort())
        );
        assertEquals(
            response("some_body_one"),
            makeRequest(
                request().withPath(calculatePath("some_path_one")).withBody("some_body_one"),
                headersToIgnore
            )
        );
        assertEquals(
            response("some_body_three"),
            makeRequest(
                request().withPath(calculatePath("some_path_three")).withBody("some_body_three"),
                headersToIgnore
            )
        );

        // then
        Expectation[] recordedExpectations = new ExpectationSerializer(new MockServerLogger()).deserializeArray(
            mockServerClient.retrieveRecordedExpectations(request().withPath(calculatePath("some_path_one")), Format.JSON),
            false
        );
        assertThat(recordedExpectations.length, is(1));
        verifyRequestsMatches(
            new RequestDefinition[]{
                recordedExpectations[0].getHttpRequest()
            },
            request(calculatePath("some_path_one")).withBody("some_body_one")
        );
        assertThat(recordedExpectations[0].getHttpResponse().getBodyAsString(), is("some_body_one"));
        // and
        recordedExpectations = new ExpectationSerializer(new MockServerLogger()).deserializeArray(
            mockServerClient.retrieveRecordedExpectations(request(), Format.JSON),
            false
        );
        assertThat(recordedExpectations.length, is(2));
        verifyRequestsMatches(
            new RequestDefinition[]{
                recordedExpectations[0].getHttpRequest(),
                recordedExpectations[1].getHttpRequest()
            },
            request(calculatePath("some_path_one")).withBody("some_body_one"),
            request(calculatePath("some_path_three")).withBody("some_body_three")
        );
        assertThat(recordedExpectations[0].getHttpResponse().getBodyAsString(), is("some_body_one"));
        assertThat(recordedExpectations[1].getHttpResponse().getBodyAsString(), is("some_body_three"));
        // and
        recordedExpectations = new ExpectationSerializer(new MockServerLogger()).deserializeArray(
            mockServerClient.retrieveRecordedExpectations(null, Format.JSON),
            false
        );
        assertThat(recordedExpectations.length, is(2));
        verifyRequestsMatches(
            new RequestDefinition[]{
                recordedExpectations[0].getHttpRequest(),
                recordedExpectations[1].getHttpRequest()
            },
            request(calculatePath("some_path_one")).withBody("some_body_one"),
            request(calculatePath("some_path_three")).withBody("some_body_three")
        );
        assertThat(recordedExpectations[0].getHttpResponse().getBodyAsString(), is("some_body_one"));
        assertThat(recordedExpectations[1].getHttpResponse().getBodyAsString(), is("some_body_three"));
    }

    @Test
    public void shouldClearExpectationsOnly() {
        // given - some expectations
        mockServerClient
            .when(
                request()
                    .withPath(calculatePath("some_path1"))
            )
            .respond(
                response()
                    .withBody("some_body1")
            );
        mockServerClient
            .when(
                request()
                    .withPath(calculatePath("some_path2"))
            )
            .respond(
                response()
                    .withBody("some_body2")
            );

        // and - some matching requests
        assertEquals(
            response()
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase())
                .withBody("some_body1"),
            makeRequest(
                request()
                    .withPath(calculatePath("some_path1")),
                headersToIgnore)
        );
        assertEquals(
            response()
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase())
                .withBody("some_body2"),
            makeRequest(
                request()
                    .withPath(calculatePath("some_path2")),
                headersToIgnore)
        );

        // when
        mockServerClient
            .clear(
                request()
                    .withPath(calculatePath("some_path1")),
                ClearType.EXPECTATIONS
            );

        // then - expectations cleared
        assertThat(
            mockServerClient.retrieveActiveExpectations(null),
            arrayContaining(
                new Expectation(request()
                    .withPath(calculatePath("some_path2")))
                    .thenRespond(
                        response()
                            .withBody("some_body2")
                    )
            )
        );

        // and then - request log not cleared
        verifyRequestsMatches(
            mockServerClient.retrieveRecordedRequests(null),
            request(calculatePath("some_path1")),
            request(calculatePath("some_path2"))
        );
    }

    @Test
    public void shouldClearLogsOnly() {
        // given - some expectations
        mockServerClient
            .when(
                request()
                    .withPath(calculatePath("some_path1"))
            )
            .respond(
                response()
                    .withBody("some_body1")
            );
        mockServerClient
            .when(
                request()
                    .withPath(calculatePath("some_path2"))
            )
            .respond(
                response()
                    .withBody("some_body2")
            );

        // and - some matching requests
        assertEquals(
            response()
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase())
                .withBody("some_body1"),
            makeRequest(
                request()
                    .withPath(calculatePath("some_path1")),
                headersToIgnore)
        );
        assertEquals(
            response()
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase())
                .withBody("some_body2"),
            makeRequest(
                request()
                    .withPath(calculatePath("some_path2")),
                headersToIgnore)
        );

        // when
        mockServerClient
            .clear(
                request()
                    .withPath(calculatePath("some_path1")),
                ClearType.LOG
            );

        // then - expectations cleared
        assertThat(
            mockServerClient.retrieveActiveExpectations(null),
            arrayContaining(
                new Expectation(request()
                    .withPath(calculatePath("some_path1")))
                    .thenRespond(
                        response()
                            .withBody("some_body1")
                    ),
                new Expectation(request()
                    .withPath(calculatePath("some_path2")))
                    .thenRespond(
                        response()
                            .withBody("some_body2")
                    )
            )
        );

        // and then - request log cleared
        verifyRequestsMatches(
            mockServerClient.retrieveRecordedRequests(null),
            request(calculatePath("some_path2"))
        );
    }

    @Test
    public void shouldClearAllExpectationsWithNull() {
        // given
        mockServerClient
            .when(
                request()
                    .withPath(calculatePath("some_path1"))
            )
            .respond(
                response()
                    .withBody("some_body1")
            );
        mockServerClient
            .when(
                request()
                    .withPath(calculatePath("some_path2"))
            )
            .respond(
                response()
                    .withBody("some_body2")
            );
        assertEquals(
            response()
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase())
                .withBody("some_body2"),
            makeRequest(
                request()
                    .withPath(calculatePath("some_path2")),
                headersToIgnore)
        );

        // when
        mockServerClient.clear(null);

        // then
        assertThat(mockServerClient.retrieveActiveExpectations(null), emptyArray());
        assertThat(mockServerClient.retrieveRecordedRequests(null), emptyArray());
    }

    @Test
    public void shouldClearAllExpectationsWithEmptyRequest() {
        // given
        mockServerClient
            .when(
                request()
                    .withPath(calculatePath("some_path1"))
            )
            .respond(
                response()
                    .withBody("some_body1")
            );
        mockServerClient
            .when(
                request()
                    .withPath(calculatePath("some_path2"))
            )
            .respond(
                response()
                    .withBody("some_body2")
            );
        assertEquals(
            response()
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase())
                .withBody("some_body2"),
            makeRequest(
                request()
                    .withPath(calculatePath("some_path2")),
                headersToIgnore)
        );

        // when
        mockServerClient.clear(request());

        // then
        assertThat(mockServerClient.retrieveActiveExpectations(null), emptyArray());
        assertThat(mockServerClient.retrieveRecordedRequests(null), emptyArray());
    }

    @Test
    public void shouldClearExpectationsWithXPathBody() {
        // given
        mockServerClient
            .when(
                request()
                    .withBody(xpath("/bookstore/book[year=2005]/price"))
            )
            .respond(
                response()
                    .withBody("some_body1")
            );
        mockServerClient
            .when(
                request()
                    .withBody(xpath("/bookstore/book[year=2006]/price"))
            )
            .respond(
                response()
                    .withBody("some_body2")
            );

        // and
        StringBody xmlBody = new StringBody("" +
            "<?xml version=\"1.0\" encoding=\"ISO-8859-1\"?>" + NEW_LINE +
            "<bookstore>" + NEW_LINE +
            "  <book category=\"COOKING\">" + NEW_LINE +
            "    <title lang=\"en\">Everyday Italian</title>" + NEW_LINE +
            "    <author>Giada De Laurentiis</author>" + NEW_LINE +
            "    <year>2005</year>" + NEW_LINE +
            "    <price>30.00</price>" + NEW_LINE +
            "  </book>" + NEW_LINE +
            "  <book category=\"CHILDREN\">" + NEW_LINE +
            "    <title lang=\"en\">Harry Potter</title>" + NEW_LINE +
            "    <author>J K. Rowling</author>" + NEW_LINE +
            "    <year>2006</year>" + NEW_LINE +
            "    <price>29.99</price>" + NEW_LINE +
            "  </book>" + NEW_LINE +
            "  <book category=\"WEB\">" + NEW_LINE +
            "    <title lang=\"en\">Learning XML</title>" + NEW_LINE +
            "    <author>Erik T. Ray</author>" + NEW_LINE +
            "    <year>2003</year>" + NEW_LINE +
            "    <price>31.95</price>" + NEW_LINE +
            "  </book>" + NEW_LINE +
            "</bookstore>");

        // then
        assertEquals(
            response()
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase())
                .withBody("some_body1"),
            makeRequest(
                request()
                    .withBody(xmlBody),
                headersToIgnore)
        );

        // when
        mockServerClient
            .clear(
                request()
                    .withBody(xpath("/bookstore/book[year=2005]/price"))
            );

        // then
        assertThat(
            mockServerClient.retrieveActiveExpectations(null),
            arrayContaining(
                new Expectation(request()
                    .withBody(xpath("/bookstore/book[year=2006]/price")))
                    .thenRespond(
                        response()
                            .withBody("some_body2")
                    )
            )
        );
        assertEquals(
            response()
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase())
                .withBody("some_body2"),
            makeRequest(
                request()
                    .withBody(xmlBody),
                headersToIgnore)
        );
    }

    @Test
    public void shouldClearExpectationsWithJsonSchemaBody() {
        // given
        JsonSchemaBody jsonSchemaBodyOne = jsonSchema("{" + NEW_LINE +
            "    \"$schema\": \"http://json-schema.org/draft-04/schema#\"," + NEW_LINE +
            "    \"title\": \"Product\"," + NEW_LINE +
            "    \"description\": \"A product from Acme's catalog\"," + NEW_LINE +
            "    \"type\": \"object\"," + NEW_LINE +
            "    \"properties\": {" + NEW_LINE +
            "        \"id\": {" + NEW_LINE +
            "            \"description\": \"The unique identifier for a product\"," + NEW_LINE +
            "            \"type\": \"integer\"" + NEW_LINE +
            "        }," + NEW_LINE +
            "        \"name\": {" + NEW_LINE +
            "            \"description\": \"Name of the product\"," + NEW_LINE +
            "            \"type\": \"string\"" + NEW_LINE +
            "        }," + NEW_LINE +
            "        \"price\": {" + NEW_LINE +
            "            \"type\": \"number\"," + NEW_LINE +
            "            \"minimum\": 0," + NEW_LINE +
            "            \"exclusiveMinimum\": true" + NEW_LINE +
            "        }," + NEW_LINE +
            "        \"tags\": {" + NEW_LINE +
            "            \"type\": \"array\"," + NEW_LINE +
            "            \"items\": {" + NEW_LINE +
            "                \"type\": \"string\"" + NEW_LINE +
            "            }," + NEW_LINE +
            "            \"minItems\": 1," + NEW_LINE +
            "            \"uniqueItems\": true" + NEW_LINE +
            "        }" + NEW_LINE +
            "    }," + NEW_LINE +
            "    \"required\": [\"id\", \"name\", \"price\"]" + NEW_LINE +
            "}");
        JsonSchemaBody jsonSchemaBodyTwo = jsonSchema("{" + NEW_LINE +
            "  \"$schema\" : \"http://json-schema.org/draft-04/schema#\"," + NEW_LINE +
            "  \"title\" : \"Product\"," + NEW_LINE +
            "  \"description\" : \"A product from Acme's catalog\"," + NEW_LINE +
            "  \"type\" : \"object\"," + NEW_LINE +
            "  \"properties\" : {" + NEW_LINE +
            "    \"id\" : {" + NEW_LINE +
            "      \"description\" : \"The unique identifier for a product\"," + NEW_LINE +
            "      \"type\" : \"integer\"" + NEW_LINE +
            "    }," + NEW_LINE +
            "    \"name\" : {" + NEW_LINE +
            "      \"description\" : \"Name of the product\"," + NEW_LINE +
            "      \"type\" : \"string\"" + NEW_LINE +
            "    }," + NEW_LINE +
            "    \"price\" : {" + NEW_LINE +
            "      \"type\" : \"number\"," + NEW_LINE +
            "      \"minimum\" : 10," + NEW_LINE +
            "      \"exclusiveMinimum\" : true" + NEW_LINE +
            "    }," + NEW_LINE +
            "    \"tags\" : {" + NEW_LINE +
            "      \"type\" : \"array\"," + NEW_LINE +
            "      \"items\" : {" + NEW_LINE +
            "        \"type\" : \"string\"" + NEW_LINE +
            "      }," + NEW_LINE +
            "      \"minItems\" : 1," + NEW_LINE +
            "      \"uniqueItems\" : true" + NEW_LINE +
            "    }" + NEW_LINE +
            "  }," + NEW_LINE +
            "  \"required\" : [ \"id\", \"name\", \"price\" ]" + NEW_LINE +
            "}");
        mockServerClient
            .when(
                request()
                    .withBody(jsonSchemaBodyOne)
            )
            .respond(
                response()
                    .withBody("some_body1")
            );
        mockServerClient
            .when(
                request()
                    .withBody(jsonSchemaBodyTwo)
            )
            .respond(
                response()
                    .withBody("some_body2")
            );

        // then
        assertEquals(
            response()
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase())
                .withBody("some_body1"),
            makeRequest(
                request()
                    .withBody("{" + NEW_LINE +
                        "    \"id\": 1," + NEW_LINE +
                        "    \"name\": \"A green door\"," + NEW_LINE +
                        "    \"price\": 12.50," + NEW_LINE +
                        "    \"tags\": [\"home\", \"green\"]" + NEW_LINE +
                        "}"),
                headersToIgnore)
        );

        // when
        mockServerClient
            .clear(
                request()
                    .withBody(jsonSchemaBodyOne)
            );

        // then
        assertThat(
            mockServerClient.retrieveActiveExpectations(null),
            arrayContaining(
                new Expectation(request()
                    .withBody(jsonSchemaBodyTwo))
                    .thenRespond(
                        response()
                            .withBody("some_body2")
                    )
            )
        );
        assertEquals(
            response()
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase())
                .withBody("some_body2"),
            makeRequest(
                request()
                    .withBody("{" + NEW_LINE +
                        "    \"id\": 1," + NEW_LINE +
                        "    \"name\": \"A green door\"," + NEW_LINE +
                        "    \"price\": 12.50," + NEW_LINE +
                        "    \"tags\": [\"home\", \"green\"]" + NEW_LINE +
                        "}"),
                headersToIgnore)
        );
    }

    @Test
    public void shouldClearExpectationsWithParameterBody() {
        // given
        mockServerClient
            .when(
                request()
                    .withBody(params(param("bodyParameterNameOne", "bodyParameterValueOne")))
            )
            .respond(
                response()
                    .withBody("some_body1")
            );
        mockServerClient
            .when(
                request()
                    .withBody(params(param("bodyParameterNameTwo", "bodyParameterValueTwo")))
            )
            .respond(
                response()
                    .withBody("some_body2")
            );

        // then
        assertEquals(
            response()
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase())
                .withBody("some_body1"),
            makeRequest(
                request()
                    .withBody(params(param("bodyParameterNameOne", "bodyParameterValueOne"))),
                headersToIgnore)
        );

        // when
        mockServerClient
            .clear(
                request()
                    .withBody(params(param("bodyParameterNameOne", "bodyParameterValueOne")))
            );

        // then
        assertThat(
            mockServerClient.retrieveActiveExpectations(null),
            arrayContaining(
                new Expectation(request()
                    .withBody(params(param("bodyParameterNameTwo", "bodyParameterValueTwo"))))
                    .thenRespond(
                        response()
                            .withBody("some_body2")
                    )
            )
        );
        assertEquals(
            response()
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase())
                .withBody("some_body2"),
            makeRequest(
                request()
                    .withBody(params(param("bodyParameterNameTwo", "bodyParameterValueTwo"))),
                headersToIgnore)
        );
    }

    @Test
    public void shouldEnsureThatInterruptedRequestsAreVerifiable() {
        mockServerClient
            .when(
                request(calculatePath("delayed"))
            )
            .respond(
                response("delayed data")
                    .withDelay(new Delay(SECONDS, 3))
            );

        Future<HttpResponse> delayedFuture = Executors.newSingleThreadExecutor().submit(() -> httpClient.sendRequest(
            request(addContextToPath(calculatePath("delayed")))
                .withHeader(HOST.toString(), "localhost:" + getServerPort())
        ).get(10, SECONDS));

        Uninterruptibles.sleepUninterruptibly(1, SECONDS); // Let request reach server

        delayedFuture.cancel(true); // Then interrupt requesting thread

        mockServerClient.verify(request(calculatePath("delayed"))); // We should be able to verify request that reached server even though its later interrupted
    }

    @Test
    public void shouldEnsureThatRequestDelaysDoNotAffectOtherRequests() throws Exception {
        mockServerClient
            .when(
                request("/slow")
            )
            .respond(
                response("super slow")
                    .withDelay(new Delay(SECONDS, 5))
            );
        mockServerClient
            .when(
                request("/fast")
            )
            .respond(
                response("quite fast")
            );

        ExecutorService executorService = Executors.newFixedThreadPool(2);

        Future<Long> slowFuture = executorService.submit(() -> {
            long start = System.currentTimeMillis();
            makeRequest(request("/slow"), Collections.emptySet());
            return System.currentTimeMillis() - start;
        });

        // Let fast request come to the server slightly after slow request
        Uninterruptibles.sleepUninterruptibly(1, SECONDS);

        Future<Long> fastFuture = executorService.submit(() -> {
            long start = System.currentTimeMillis();
            makeRequest(request("/fast"), Collections.emptySet());
            return System.currentTimeMillis() - start;

        });

        Long slowRequestElapsedMillis = slowFuture.get(maxFutureTimeout(), MILLISECONDS);
        Long fastRequestElapsedMillis = fastFuture.get(maxFutureTimeout(), MILLISECONDS);

        assertThat("Slow request takes less than expected", slowRequestElapsedMillis, is(greaterThan(4 * 1000L)));
        assertThat("Fast request takes longer than expected", fastRequestElapsedMillis, is(lessThan(1000L)));
    }

}
