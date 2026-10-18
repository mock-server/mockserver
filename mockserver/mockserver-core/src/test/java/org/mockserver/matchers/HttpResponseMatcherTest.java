package org.mockserver.matchers;

import org.junit.Test;
import org.mockserver.codec.JsonSchemaBodyDecoder;
import org.mockserver.configuration.Configuration;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.Header;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.MultipartBody;
import org.mockserver.model.Parameter;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockserver.configuration.Configuration.configuration;
import static org.mockserver.model.BinaryBody.binary;
import static org.mockserver.model.Cookie.cookie;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.JsonBody.json;
import static org.mockserver.model.JsonPathBody.jsonPath;
import static org.mockserver.model.NottableString.not;
import static org.mockserver.model.NottableString.string;
import static org.mockserver.model.StringBody.exact;
import static org.mockserver.model.XmlBody.xml;

/**
 * Tests for {@link HttpResponseMatcher}.
 */
public class HttpResponseMatcherTest {

    private final Configuration configuration = configuration();
    private final MockServerLogger mockServerLogger = new MockServerLogger(configuration, HttpResponseMatcherTest.class);

    @Test
    public void shouldMatchWhenTemplateIsNull() {
        // null template matches anything
        HttpResponseMatcher matcher = new HttpResponseMatcher(configuration, mockServerLogger, null);
        assertThat(matcher.matches(response().withStatusCode(200)), is(true));
        assertThat(matcher.matches(response().withStatusCode(500)), is(true));
        assertThat(matcher.matches(null), is(true));
    }

    @Test
    public void shouldMatchWhenTemplateIsEmpty() {
        // empty template matches anything
        HttpResponseMatcher matcher = new HttpResponseMatcher(configuration, mockServerLogger, response());
        assertThat(matcher.matches(response().withStatusCode(200)), is(true));
        assertThat(matcher.matches(response().withStatusCode(500)), is(true));
    }

    @Test
    public void shouldMatchStatusCode() {
        HttpResponseMatcher matcher = new HttpResponseMatcher(configuration, mockServerLogger,
            response().withStatusCode(200));
        assertThat(matcher.matches(response().withStatusCode(200)), is(true));
        assertThat(matcher.matches(response().withStatusCode(404)), is(false));
    }

    @Test
    public void shouldMatchStatusCodeClassRange() {
        HttpResponseMatcher matcher = new HttpResponseMatcher(configuration, mockServerLogger,
            response().withStatusCodeRange("2XX"));
        assertThat(matcher.matches(response().withStatusCode(200)), is(true));
        assertThat(matcher.matches(response().withStatusCode(201)), is(true));
        assertThat(matcher.matches(response().withStatusCode(299)), is(true));
        assertThat(matcher.matches(response().withStatusCode(300)), is(false));
        assertThat(matcher.matches(response().withStatusCode(404)), is(false));
    }

    @Test
    public void shouldMatchStatusCodeNumericOperator() {
        HttpResponseMatcher matcher = new HttpResponseMatcher(configuration, mockServerLogger,
            response().withStatusCodeRange(">= 400"));
        assertThat(matcher.matches(response().withStatusCode(404)), is(true));
        assertThat(matcher.matches(response().withStatusCode(500)), is(true));
        assertThat(matcher.matches(response().withStatusCode(200)), is(false));
    }

    @Test
    public void shouldNotMatchNullActualStatusWhenTemplateHasStatusCodeRange() {
        HttpResponseMatcher matcher = new HttpResponseMatcher(configuration, mockServerLogger,
            response().withStatusCodeRange("2XX"));
        // actual response with no status code does not satisfy a range constraint
        assertThat(matcher.matches(response()), is(false));
    }

    @Test
    public void shouldNotMatchWhenStatusCodeRangeIsUnparseable() {
        HttpResponseMatcher matcher = new HttpResponseMatcher(configuration, mockServerLogger,
            response().withStatusCodeRange("garbage"));
        assertThat(matcher.matches(response().withStatusCode(200)), is(false));
    }

    @Test
    public void shouldNotMatchNullActualWhenTemplateHasStatusCode() {
        HttpResponseMatcher matcher = new HttpResponseMatcher(configuration, mockServerLogger,
            response().withStatusCode(200));
        assertThat(matcher.matches(null), is(false));
    }

    @Test
    public void shouldMatchReasonPhrase() {
        HttpResponseMatcher matcher = new HttpResponseMatcher(configuration, mockServerLogger,
            response().withReasonPhrase("OK"));
        assertThat(matcher.matches(response().withReasonPhrase("OK")), is(true));
        assertThat(matcher.matches(response().withReasonPhrase("Not Found")), is(false));
    }

    @Test
    public void shouldMatchReasonPhraseRegex() {
        HttpResponseMatcher matcher = new HttpResponseMatcher(configuration, mockServerLogger,
            response().withReasonPhrase(".*Found.*"));
        assertThat(matcher.matches(response().withReasonPhrase("Not Found")), is(true));
        assertThat(matcher.matches(response().withReasonPhrase("OK")), is(false));
    }

    @Test
    public void shouldMatchHeaders() {
        HttpResponseMatcher matcher = new HttpResponseMatcher(configuration, mockServerLogger,
            response().withHeaders(new Header("Content-Type", "application/json")));
        assertThat(matcher.matches(
            response().withHeaders(
                new Header("Content-Type", "application/json"),
                new Header("X-Extra", "value")
            )), is(true));
        assertThat(matcher.matches(
            response().withHeaders(
                new Header("Content-Type", "text/html")
            )), is(false));
    }

    @Test
    public void shouldMatchJsonBody() {
        HttpResponseMatcher matcher = new HttpResponseMatcher(configuration, mockServerLogger,
            response().withBody(json("{\"status\": \"ok\"}")));
        assertThat(matcher.matches(
            response().withBody("{\"status\": \"ok\"}")), is(true));
        assertThat(matcher.matches(
            response().withBody("{\"status\": \"error\"}")), is(false));
    }

    @Test
    public void shouldMatchJsonBodyWithExtraFields() {
        // JSON matching with ONLY_MATCHING_FIELDS (default) tolerates extra fields in actual
        HttpResponseMatcher matcher = new HttpResponseMatcher(configuration, mockServerLogger,
            response().withBody(json("{\"status\": \"ok\"}")));
        assertThat(matcher.matches(
            response().withBody("{\"status\": \"ok\", \"extra\": 1}")), is(true));
    }

    @Test
    public void shouldMatchStringBodyOnResponse() {
        HttpResponseMatcher matcher = new HttpResponseMatcher(configuration, mockServerLogger,
            response().withBody("some response text"));
        assertThat(matcher.matches(
            response().withBody("some response text")), is(true));
        assertThat(matcher.matches(
            response().withBody("different text")), is(false));
    }

    @Test
    public void shouldMatchExactStringBody() {
        HttpResponseMatcher matcher = new HttpResponseMatcher(configuration, mockServerLogger,
            response().withBody(exact("hello world")));
        assertThat(matcher.matches(
            response().withBody("hello world")), is(true));
        assertThat(matcher.matches(
            response().withBody("goodbye")), is(false));
    }

    @Test
    public void shouldMatchStatusCodeAndHeaders() {
        HttpResponseMatcher matcher = new HttpResponseMatcher(configuration, mockServerLogger,
            response()
                .withStatusCode(200)
                .withHeaders(new Header("Content-Type", "application/json")));
        assertThat(matcher.matches(
            response()
                .withStatusCode(200)
                .withHeaders(new Header("Content-Type", "application/json"))),
            is(true));
        assertThat(matcher.matches(
            response()
                .withStatusCode(404)
                .withHeaders(new Header("Content-Type", "application/json"))),
            is(false));
        assertThat(matcher.matches(
            response()
                .withStatusCode(200)
                .withHeaders(new Header("Content-Type", "text/html"))),
            is(false));
    }

    @Test
    public void shouldIgnoreUnsetTemplateFields() {
        // template only specifies statusCode -- headers and body should not constrain
        HttpResponseMatcher matcher = new HttpResponseMatcher(configuration, mockServerLogger,
            response().withStatusCode(200));
        assertThat(matcher.matches(
            response()
                .withStatusCode(200)
                .withHeaders(new Header("X-Any", "value"))
                .withBody("any body content")),
            is(true));
    }

    // ---------------------------------------------------------------------------------------------
    // Parity with request body matching: the shared BodyMatching dispatch gives a response body
    // matcher the JSON/XML/form conversion, optional-body short-circuit, multipart decode, binary
    // dual-match and null-safety that the request body matcher already had.
    // ---------------------------------------------------------------------------------------------

    @Test
    public void shouldMatchJsonMatcherAgainstXmlResponseBodyViaConversion() {
        // a JSON matcher against an XML actual response body: the actual XML is converted to JSON
        // (driven by the Content-Type header) before matching — request-side behaviour, now on
        // responses
        // root element wraps the field so the XML converts to a JSON object {"name":"value"}
        // (a bare <name>value</name> would convert to the scalar string "value")
        HttpResponseMatcher matcher = new HttpResponseMatcher(configuration, mockServerLogger,
            response().withBody(json("{ \"name\": \"value\" }")));
        assertThat(matcher.matches(
            response()
                .withHeaders(new Header("Content-Type", "application/xml"))
                .withBody("<root><name>value</name></root>")), is(true));
        assertThat(matcher.matches(
            response()
                .withHeaders(new Header("Content-Type", "application/xml"))
                .withBody("<root><name>other</name></root>")), is(false));
    }

    @Test
    public void shouldMatchJsonMatcherAgainstFormResponseBodyViaConversion() {
        // a JSON matcher against an application/x-www-form-urlencoded actual response body: the form
        // body is converted to a JSON object before matching
        HttpResponseMatcher matcher = new HttpResponseMatcher(configuration, mockServerLogger,
            response().withBody(json("{ \"name\": \"value\" }")));
        assertThat(matcher.matches(
            response()
                .withHeaders(new Header("Content-Type", "application/x-www-form-urlencoded"))
                .withBody("name=value")), is(true));
        assertThat(matcher.matches(
            response()
                .withHeaders(new Header("Content-Type", "application/x-www-form-urlencoded"))
                .withBody("name=other")), is(false));
    }

    @Test
    public void shouldMatchXmlResponseBody() {
        HttpResponseMatcher matcher = new HttpResponseMatcher(configuration, mockServerLogger,
            response().withBody(xml("<name>value</name>")));
        assertThat(matcher.matches(
            response().withBody("<name>value</name>")), is(true));
        assertThat(matcher.matches(
            response().withBody("<name>other</name>")), is(false));
    }

    @Test
    public void shouldMatchJsonPathResponseBodyViaSharedDispatch() {
        // JsonPathBody extends Body (not BodyWithContentType) so it cannot be carried by an
        // HttpResponse template through withBody; exercise the shared BodyMatching dispatch directly
        // (the same code the response matcher invokes) to prove the JSON family is handled on the
        // response BodySource
        BodyMatcher bodyMatcher = BodyMatcherBuilder.buildBodyMatcher(configuration, mockServerLogger, jsonPath("$.status"), false);
        JsonSchemaBodyDecoder parser = new JsonSchemaBodyDecoder(configuration, mockServerLogger, null, null);
        assertThat(BodyMatching.bodyMatches(bodyMatcher, null,
            BodyMatching.of(response().withBody("{ \"status\": \"ok\" }")), null, parser, mockServerLogger), is(true));
        assertThat(BodyMatching.bodyMatches(bodyMatcher, null,
            BodyMatching.of(response().withBody("{ \"other\": \"ok\" }")), null, parser, mockServerLogger), is(false));
    }

    @Test
    public void shouldMatchOptionalResponseBodyWhenResponseHasNoBody() {
        // an optional template body matches a response with no body at all
        org.mockserver.model.JsonBody optionalJsonBody = json("{ \"status\": \"ok\" }");
        optionalJsonBody.withOptional(true);
        HttpResponseMatcher matcher = new HttpResponseMatcher(configuration, mockServerLogger,
            response().withBody(optionalJsonBody));
        assertThat(matcher.matches(response().withStatusCode(204)), is(true));
        // ...but a present, non-matching body is still rejected
        assertThat(matcher.matches(response().withBody("{ \"status\": \"error\" }")), is(false));
        // ...and a present, matching body still matches
        assertThat(matcher.matches(response().withBody("{ \"status\": \"ok\" }")), is(true));
    }

    @Test
    public void shouldMatchMultipartResponseBodyViaSharedDispatch() {
        // MultipartBody extends Body (not BodyWithContentType) so it cannot be carried by an
        // HttpResponse template through withBody; exercise the shared BodyMatching dispatch directly
        // to prove the multipart branch decodes the response's raw bytes using the Content-Type
        // boundary (the branch the old response matcher mis-dispatched to the generic string else)
        String boundary = "----MockServerBoundaryResp";
        String body =
            "--" + boundary + "\r\n" +
                "Content-Disposition: form-data; name=\"field\"\r\n" +
                "\r\n" +
                "fieldValue\r\n" +
                "--" + boundary + "--\r\n";
        BodyMatcher bodyMatcher = BodyMatcherBuilder.buildBodyMatcher(configuration, mockServerLogger,
            new MultipartBody(new Parameter("field", "fieldValue")), false);
        JsonSchemaBodyDecoder parser = new JsonSchemaBodyDecoder(configuration, mockServerLogger, null, null);
        assertThat(bodyMatcher instanceof MultipartMatcher, is(true));
        assertThat(BodyMatching.bodyMatches(bodyMatcher, null, BodyMatching.of(
            response()
                .withHeaders(new Header("Content-Type", "multipart/form-data; boundary=" + boundary))
                .withBody(body.getBytes(StandardCharsets.UTF_8))), null, parser, mockServerLogger), is(true));

        String otherBody =
            "--" + boundary + "\r\n" +
                "Content-Disposition: form-data; name=\"field\"\r\n" +
                "\r\n" +
                "otherValue\r\n" +
                "--" + boundary + "--\r\n";
        assertThat(BodyMatching.bodyMatches(bodyMatcher, null, BodyMatching.of(
            response()
                .withHeaders(new Header("Content-Type", "multipart/form-data; boundary=" + boundary))
                .withBody(otherBody.getBytes(StandardCharsets.UTF_8))), null, parser, mockServerLogger), is(false));
    }

    @Test
    public void shouldMatchBinaryResponseBody() {
        byte[] bytes = {1, 2, 3, 4, 5};
        HttpResponseMatcher matcher = new HttpResponseMatcher(configuration, mockServerLogger,
            response().withBody(binary(bytes)));
        assertThat(matcher.matches(
            response().withBody(binary(new byte[]{1, 2, 3, 4, 5}))), is(true));
        assertThat(matcher.matches(
            response().withBody(binary(new byte[]{9, 9, 9}))), is(false));
    }

    @Test
    public void shouldCleanlyNonMatchJsonMatcherAgainstResponseWithNoBody() {
        // a JSON matcher against a response with NO body must be a clean non-match, NOT an internal
        // NPE swallowed into a silent non-match — i.e. the matcher returns false without throwing
        HttpResponseMatcher matcher = new HttpResponseMatcher(configuration, mockServerLogger,
            response().withBody(json("{ \"status\": \"ok\" }")));
        assertThat(matcher.matches(response().withStatusCode(204)), is(false));
    }

    @Test
    public void shouldCleanlyNonMatchXmlMatcherAgainstResponseWithNoBody() {
        HttpResponseMatcher matcher = new HttpResponseMatcher(configuration, mockServerLogger,
            response().withBody(xml("<name>value</name>")));
        assertThat(matcher.matches(response().withStatusCode(204)), is(false));
    }

    @Test
    public void shouldFallbackToBodyStringWhenXmlConversionFailsOnResponse() {
        // a JSON matcher against a malformed XML response body: the XML→JSON conversion fails and
        // falls back to matching the raw body string — a clean non-match, never a surfaced exception
        HttpResponseMatcher matcher = new HttpResponseMatcher(configuration, mockServerLogger,
            response().withBody(json("{ \"name\": \"value\" }")));
        assertThat(matcher.matches(
            response()
                .withHeaders(new Header("Content-Type", "application/xml"))
                .withBody("<root><name>value</name>")), is(false));
    }

    // ---------------------------------------------------------------------------------------------
    // Reason-phrase honours matchExactCase (parity with the response body, which already
    // honours it via BodyMatcherBuilder). A per-instance Configuration is used so these tests stay
    // in the parallel Surefire phase (no global singleton mutation).
    // ---------------------------------------------------------------------------------------------

    @Test
    public void shouldMatchReasonPhraseCaseInsensitiveByDefault() {
        // default matchExactCase=false: "OK" template matches lowercase "ok" actual
        HttpResponseMatcher matcher = new HttpResponseMatcher(configuration, mockServerLogger,
            response().withReasonPhrase("OK"));
        assertThat(matcher.matches(response().withReasonPhrase("ok")), is(true));
    }

    @Test
    public void shouldNotMatchWhenActualReasonPhraseIsNull() {
        // a template reason-phrase against an actual response with no reason phrase is a clean non-match
        HttpResponseMatcher matcher = new HttpResponseMatcher(configuration, mockServerLogger,
            response().withReasonPhrase("OK"));
        assertThat(matcher.matches(response().withStatusCode(204)), is(false));
    }

    @Test
    public void shouldNotMatchReasonPhraseCaseMismatchWhenMatchExactCase() {
        Configuration exactCaseConfiguration = configuration().matchExactCase(true);
        HttpResponseMatcher matcher = new HttpResponseMatcher(exactCaseConfiguration, mockServerLogger,
            response().withReasonPhrase("OK"));
        // exact case enabled: "OK" must NOT match "ok"
        assertThat(matcher.matches(response().withReasonPhrase("ok")), is(false));
        // ...but identical case still matches
        assertThat(matcher.matches(response().withReasonPhrase("OK")), is(true));
    }

    // ---------------------------------------------------------------------------------------------
    // Cookie matching mirrors the request matcher (HashMapMatcher): sub-set semantics,
    // missing required cookies fail, notted values behave correctly.
    // ---------------------------------------------------------------------------------------------

    @Test
    public void shouldMatchCookie() {
        HttpResponseMatcher matcher = new HttpResponseMatcher(configuration, mockServerLogger,
            response().withCookie("session", "abc123"));
        assertThat(matcher.matches(response().withCookie("session", "abc123")), is(true));
    }

    @Test
    public void shouldNotMatchMissingRequiredCookie() {
        HttpResponseMatcher matcher = new HttpResponseMatcher(configuration, mockServerLogger,
            response().withCookie("session", "abc123"));
        // actual response has no cookies -> required cookie absent -> non-match
        assertThat(matcher.matches(response().withStatusCode(200)), is(false));
        // actual response has a different cookie value -> non-match
        assertThat(matcher.matches(response().withCookie("session", "other")), is(false));
    }

    @Test
    public void shouldMatchCookieSubSetAllowingExtraResponseCookies() {
        // sub-set semantics: an extra cookie in the actual response does not break the match
        HttpResponseMatcher matcher = new HttpResponseMatcher(configuration, mockServerLogger,
            response().withCookie("session", "abc123"));
        assertThat(matcher.matches(
            response()
                .withCookie("session", "abc123")
                .withCookie("tracking", "xyz")), is(true));
    }

    @Test
    public void shouldMatchNottedCookieValue() {
        // a notted cookie value must NOT equal the actual value
        HttpResponseMatcher matcher = new HttpResponseMatcher(configuration, mockServerLogger,
            response().withCookie(cookie(string("session"), not("forbidden"))));
        assertThat(matcher.matches(response().withCookie("session", "allowed")), is(true));
        assertThat(matcher.matches(response().withCookie("session", "forbidden")), is(false));
    }

    @Test
    public void shouldIgnoreCookiesWhenTemplateHasNone() {
        // additive: no cookie template -> cookies place no constraint on the response
        HttpResponseMatcher matcher = new HttpResponseMatcher(configuration, mockServerLogger,
            response().withStatusCode(200));
        assertThat(matcher.matches(
            response()
                .withStatusCode(200)
                .withCookie("any", "value")), is(true));
    }

    // ---------------------------------------------------------------------------------------------
    // matches(MatchDifference, actual) records per-field differences while computing the
    // SAME boolean result as matches(actual). detailedMatchFailures must be true for the
    // MatchDifference to record differences.
    // ---------------------------------------------------------------------------------------------

    @Test
    public void shouldPopulateDifferencesForStatusAndBodyMismatch() {
        HttpResponseMatcher matcher = new HttpResponseMatcher(configuration, mockServerLogger,
            response()
                .withStatusCode(200)
                .withBody(json("{ \"status\": \"ok\" }")));
        HttpResponse actual = response()
            .withStatusCode(500)
            .withBody("{ \"status\": \"error\" }");

        MatchDifference context = new MatchDifference(true, request());
        boolean result = matcher.matches(context, actual);

        assertThat(result, is(false));
        // statusCode mismatch records under OPERATION; body mismatch under BODY
        Map<MatchDifference.Field, ?> differences = context.getAllDifferences();
        assertThat(context.getDifferences(MatchDifference.Field.OPERATION), is(notNullValue()));
        assertThat(context.getDifferences(MatchDifference.Field.BODY), is(notNullValue()));
        // boolean parity with the no-context overload
        assertThat(matcher.matches(actual), is(false));
    }

    @Test
    public void shouldRecordNoDifferencesForMatchingResponse() {
        HttpResponseMatcher matcher = new HttpResponseMatcher(configuration, mockServerLogger,
            response()
                .withStatusCode(200)
                .withBody(json("{ \"status\": \"ok\" }")));
        HttpResponse actual = response()
            .withStatusCode(200)
            .withBody("{ \"status\": \"ok\" }");

        MatchDifference context = new MatchDifference(true, request());
        boolean result = matcher.matches(context, actual);

        assertThat(result, is(true));
        assertThat(context.getAllDifferences().isEmpty(), is(true));
        assertThat(context.getDifferences(MatchDifference.Field.OPERATION), is(nullValue()));
        // boolean parity with the no-context overload
        assertThat(matcher.matches(actual), is(true));
    }

    @Test
    public void shouldComputeSameBooleanWithAndWithoutContext() {
        HttpResponseMatcher matcher = new HttpResponseMatcher(configuration, mockServerLogger,
            response().withStatusCode(200).withHeader("Content-Type", "application/json"));
        HttpResponse matchingActual = response()
            .withStatusCode(200)
            .withHeader("Content-Type", "application/json");
        HttpResponse mismatchingActual = response()
            .withStatusCode(404)
            .withHeader("Content-Type", "application/json");

        assertThat(matcher.matches(new MatchDifference(true, request()), matchingActual),
            is(matcher.matches(matchingActual)));
        assertThat(matcher.matches(new MatchDifference(true, request()), mismatchingActual),
            is(matcher.matches(mismatchingActual)));
    }
}
