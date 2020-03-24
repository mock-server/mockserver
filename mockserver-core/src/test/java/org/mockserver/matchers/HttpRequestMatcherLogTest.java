package org.mockserver.matchers;

import org.apache.commons.text.StringEscapeUtils;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockserver.log.TimeService;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.mock.Expectation;
import org.mockserver.mock.HttpStateHandler;
import org.mockserver.model.*;
import org.mockserver.scheduler.Scheduler;
import org.mockserver.uuid.UUIDService;
import org.slf4j.event.Level;

import java.util.Date;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockserver.character.Character.NEW_LINE;
import static org.mockserver.configuration.ConfigurationProperties.logLevel;
import static org.mockserver.configuration.ConfigurationProperties.matchersFailFast;
import static org.mockserver.log.model.LogEntry.LOG_DATE_FORMAT;
import static org.mockserver.model.BinaryBody.binary;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.JsonBody.json;
import static org.mockserver.model.JsonPathBody.jsonPath;
import static org.mockserver.model.JsonSchemaBody.jsonSchema;
import static org.mockserver.model.StringBody.exact;
import static org.mockserver.model.XPathBody.xpath;
import static org.mockserver.model.XmlBody.xml;
import static org.mockserver.model.XmlSchemaBody.xmlSchema;

/**
 * @author jamesdbloom
 */
public class HttpRequestMatcherLogTest {

    private final MockServerLogger mockServerLogger = new MockServerLogger(HttpRequestMatcherLogTest.class);
    private final HttpStateHandler httpStateHandler = new HttpStateHandler(mockServerLogger, new Scheduler(mockServerLogger));
    private static Level originalLevel;

    @BeforeClass
    public static void fixTimeAndLogs() {
        TimeService.fixedTime = true;
        UUIDService.fixedUUID = true;
        originalLevel = logLevel();
        logLevel("INFO");
    }

    @AfterClass
    public static void resetTimeAndLogs() {
        TimeService.fixedTime = false;
        UUIDService.fixedUUID = false;
        logLevel(originalLevel.name());
    }

    private boolean match(HttpRequest matcher, HttpRequest matched) {
        return new HttpRequestMatcher(mockServerLogger, matcher).withControlPlaneMatcher(false).matches(new MatchDifference(matched), matched);
    }

    private boolean match(Expectation matcher, HttpRequest matched) {
        return new HttpRequestMatcher(mockServerLogger, matcher).matches(new MatchDifference(matched), matched);
    }

    @Test
    public void matchesMatchingMethod() {
        // given
        assertTrue(match(request().withMethod("HEAD"), request().withMethod("HEAD")));

        // then - no match failure log entry
        HttpResponse response = httpStateHandler
            .retrieve(
                request()
                    .withQueryStringParameter("type", "logs")
            );
        assertThat(response.getBodyAsString(), is(
            LOG_DATE_FORMAT.format(new Date(TimeService.currentTimeMillis())) + " - request:" + NEW_LINE +
                NEW_LINE +
                "  {" + NEW_LINE +
                "    \"method\" : \"HEAD\"" + NEW_LINE +
                "  }" + NEW_LINE +
                NEW_LINE +
                " matched request:" + NEW_LINE +
                NEW_LINE +
                "  {" + NEW_LINE +
                "    \"method\" : \"HEAD\"" + NEW_LINE +
                "  }" + NEW_LINE +
                NEW_LINE +
                "------------------------------------" + NEW_LINE +
                LOG_DATE_FORMAT.format(new Date(TimeService.currentTimeMillis())) + " - retrieving logs that match:" + NEW_LINE +
                NEW_LINE +
                "  { }" + NEW_LINE +
                NEW_LINE
        ));
    }

    @Test
    public void doesNotMatchMultipleIncorrectFields() {
        // given
        assertFalse(match(
            request()
                .withPath("some_path")
                .withMethod("GET")
                .withBody("some_body")
                .withKeepAlive(true)
                .withSecure(true),
            request()
                .withPath("some_other_path")
                .withMethod("POST")
                .withBody("some_other_body")
                .withKeepAlive(false)
                .withSecure(false)
        ));

        // then
        HttpResponse response = httpStateHandler
            .retrieve(
                request()
                    .withQueryStringParameter("type", "logs")
            );
        assertThat(response.getBodyAsString(), is(
            LOG_DATE_FORMAT.format(new Date(TimeService.currentTimeMillis())) + " - request:" + NEW_LINE +
                NEW_LINE +
                "  {" + NEW_LINE +
                "    \"method\" : \"POST\"," + NEW_LINE +
                "    \"path\" : \"some_other_path\"," + NEW_LINE +
                "    \"keepAlive\" : false," + NEW_LINE +
                "    \"secure\" : false," + NEW_LINE +
                "    \"body\" : \"some_other_body\"" + NEW_LINE +
                "  }" + NEW_LINE +
                NEW_LINE +
                " didn't match request matcher:" + NEW_LINE +
                NEW_LINE +
                "  {" + NEW_LINE +
                "    \"method\" : \"GET\"," + NEW_LINE +
                "    \"path\" : \"some_path\"," + NEW_LINE +
                "    \"keepAlive\" : true," + NEW_LINE +
                "    \"secure\" : true," + NEW_LINE +
                "    \"body\" : \"some_body\"" + NEW_LINE +
                "  }" + NEW_LINE +
                NEW_LINE +
                " because:" + NEW_LINE +
                NEW_LINE +
                "  method didn't match: " + NEW_LINE +
                "  " + NEW_LINE +
                "    string or regex match failed expected:" + NEW_LINE +
                "  " + NEW_LINE +
                "      GET" + NEW_LINE +
                "  " + NEW_LINE +
                "     found:" + NEW_LINE +
                "  " + NEW_LINE +
                "      POST" + NEW_LINE +
                NEW_LINE +
                NEW_LINE +
                "------------------------------------" + NEW_LINE +
                LOG_DATE_FORMAT.format(new Date(TimeService.currentTimeMillis())) + " - retrieving logs that match:" + NEW_LINE +
                NEW_LINE +
                "  { }" + NEW_LINE +
                NEW_LINE
        ));
    }

    @Test
    public void doesNotMatchMultipleIncorrectFieldsInExpectation() {
        // given
        assertFalse(match(
            new Expectation(
                request()
                    .withPath("some_path")
                    .withMethod("GET")
                    .withBody("some_body")
                    .withKeepAlive(true)
                    .withSecure(true)
            ),
            request()
                .withPath("some_other_path")
                .withMethod("POST")
                .withBody("some_other_body")
                .withKeepAlive(false)
                .withSecure(false)
        ));

        // then
        HttpResponse response = httpStateHandler
            .retrieve(
                request()
                    .withQueryStringParameter("type", "logs")
            );
        assertThat(response.getBodyAsString(), is(
            LOG_DATE_FORMAT.format(new Date(TimeService.currentTimeMillis())) + " - request:" + NEW_LINE +
                NEW_LINE +
                "  {" + NEW_LINE +
                "    \"method\" : \"POST\"," + NEW_LINE +
                "    \"path\" : \"some_other_path\"," + NEW_LINE +
                "    \"keepAlive\" : false," + NEW_LINE +
                "    \"secure\" : false," + NEW_LINE +
                "    \"body\" : \"some_other_body\"" + NEW_LINE +
                "  }" + NEW_LINE +
                NEW_LINE +
                " didn't match expectation:" + NEW_LINE +
                NEW_LINE +
                "  {" + NEW_LINE +
                "    \"id\" : \"" + UUIDService.getUUID() + "\"," + NEW_LINE +
                "    \"priority\" : 0," + NEW_LINE +
                "    \"httpRequest\" : {" + NEW_LINE +
                "      \"method\" : \"GET\"," + NEW_LINE +
                "      \"path\" : \"some_path\"," + NEW_LINE +
                "      \"keepAlive\" : true," + NEW_LINE +
                "      \"secure\" : true," + NEW_LINE +
                "      \"body\" : \"some_body\"" + NEW_LINE +
                "    }," + NEW_LINE +
                "    \"times\" : {" + NEW_LINE +
                "      \"unlimited\" : true" + NEW_LINE +
                "    }," + NEW_LINE +
                "    \"timeToLive\" : {" + NEW_LINE +
                "      \"unlimited\" : true" + NEW_LINE +
                "    }" + NEW_LINE +
                "  }" + NEW_LINE +
                NEW_LINE +
                " because:" + NEW_LINE +
                NEW_LINE +
                "  method didn't match: " + NEW_LINE +
                "  " + NEW_LINE +
                "    string or regex match failed expected:" + NEW_LINE +
                "  " + NEW_LINE +
                "      GET" + NEW_LINE +
                "  " + NEW_LINE +
                "     found:" + NEW_LINE +
                "  " + NEW_LINE +
                "      POST" + NEW_LINE +
                NEW_LINE +
                NEW_LINE +
                "------------------------------------" + NEW_LINE +
                LOG_DATE_FORMAT.format(new Date(TimeService.currentTimeMillis())) + " - retrieving logs that match:" + NEW_LINE +
                NEW_LINE +
                "  { }" + NEW_LINE +
                NEW_LINE
        ));
    }

    @Test
    public void doesNotMatchMultipleIncorrectFieldsWithoutFailFast() {
        boolean originalMatchersFailFast = matchersFailFast();
        try {
            // given
            matchersFailFast(false);
            assertFalse(match(
                request()
                    .withPath("some_path")
                    .withMethod("GET")
                    .withBody("some_body")
                    .withKeepAlive(true)
                    .withSecure(true),
                request()
                    .withPath("some_other_path")
                    .withMethod("POST")
                    .withBody("some_other_body")
                    .withKeepAlive(false)
                    .withSecure(false)
            ));

            // then
            HttpResponse response = httpStateHandler
                .retrieve(
                    request()
                        .withQueryStringParameter("type", "logs")
                );
            assertThat(response.getBodyAsString(), is(
                LOG_DATE_FORMAT.format(new Date(TimeService.currentTimeMillis())) + " - request:" + NEW_LINE +
                    NEW_LINE +
                    "  {" + NEW_LINE +
                    "    \"method\" : \"POST\"," + NEW_LINE +
                    "    \"path\" : \"some_other_path\"," + NEW_LINE +
                    "    \"keepAlive\" : false," + NEW_LINE +
                    "    \"secure\" : false," + NEW_LINE +
                    "    \"body\" : \"some_other_body\"" + NEW_LINE +
                    "  }" + NEW_LINE +
                    NEW_LINE +
                    " didn't match request matcher:" + NEW_LINE +
                    NEW_LINE +
                    "  {" + NEW_LINE +
                    "    \"method\" : \"GET\"," + NEW_LINE +
                    "    \"path\" : \"some_path\"," + NEW_LINE +
                    "    \"keepAlive\" : true," + NEW_LINE +
                    "    \"secure\" : true," + NEW_LINE +
                    "    \"body\" : \"some_body\"" + NEW_LINE +
                    "  }" + NEW_LINE +
                    NEW_LINE +
                    " because:" + NEW_LINE +
                    NEW_LINE +
                    "  method didn't match: " + NEW_LINE +
                    "  " + NEW_LINE +
                    "    string or regex match failed expected:" + NEW_LINE +
                    "  " + NEW_LINE +
                    "      GET" + NEW_LINE +
                    "  " + NEW_LINE +
                    "     found:" + NEW_LINE +
                    "  " + NEW_LINE +
                    "      POST" + NEW_LINE +
                    "  " + NEW_LINE +
                    "  path didn't match: " + NEW_LINE +
                    "  " + NEW_LINE +
                    "    string or regex match failed expected:" + NEW_LINE +
                    "  " + NEW_LINE +
                    "      some_path" + NEW_LINE +
                    "  " + NEW_LINE +
                    "     found:" + NEW_LINE +
                    "  " + NEW_LINE +
                    "      some_other_path" + NEW_LINE +
                    "  " + NEW_LINE +
                    "  body didn't match: " + NEW_LINE +
                    "  " + NEW_LINE +
                    "    exact string match failed expected:" + NEW_LINE +
                    "  " + NEW_LINE +
                    "      some_body" + NEW_LINE +
                    "  " + NEW_LINE +
                    "     found:" + NEW_LINE +
                    "  " + NEW_LINE +
                    "      some_other_body" + NEW_LINE +
                    "  " + NEW_LINE +
                    "  headers matched" + NEW_LINE +
                    "  cookies matched" + NEW_LINE +
                    "  query matched" + NEW_LINE +
                    "  keep-alive didn't match: " + NEW_LINE +
                    "  " + NEW_LINE +
                    "    boolean match failed expected:" + NEW_LINE +
                    "  " + NEW_LINE +
                    "      true" + NEW_LINE +
                    "  " + NEW_LINE +
                    "     found:" + NEW_LINE +
                    "  " + NEW_LINE +
                    "      false" + NEW_LINE +
                    "  " + NEW_LINE +
                    "  sslMatches didn't match: " + NEW_LINE +
                    "  " + NEW_LINE +
                    "    boolean match failed expected:" + NEW_LINE +
                    "  " + NEW_LINE +
                    "      true" + NEW_LINE +
                    "  " + NEW_LINE +
                    "     found:" + NEW_LINE +
                    "  " + NEW_LINE +
                    "      false" + NEW_LINE +
                    NEW_LINE +
                    NEW_LINE +
                    "------------------------------------" + NEW_LINE +
                    LOG_DATE_FORMAT.format(new Date(TimeService.currentTimeMillis())) + " - retrieving logs that match:" + NEW_LINE +
                    NEW_LINE +
                    "  { }" + NEW_LINE +
                    NEW_LINE
            ));
        } finally {
            matchersFailFast(originalMatchersFailFast);
        }
    }

    @Test
    public void doesNotMatchMultipleIncorrectFieldsInExpectationWithoutFailFast() {
        boolean originalMatchersFailFast = matchersFailFast();
        try {
            // given
            matchersFailFast(false);
            assertFalse(match(
                new Expectation(
                    request()
                        .withPath("some_path")
                        .withMethod("GET")
                        .withBody("some_body")
                        .withKeepAlive(true)
                        .withSecure(true)
                ),
                request()
                    .withPath("some_other_path")
                    .withMethod("POST")
                    .withBody("some_other_body")
                    .withKeepAlive(false)
                    .withSecure(false)
            ));

            // then
            HttpResponse response = httpStateHandler
                .retrieve(
                    request()
                        .withQueryStringParameter("type", "logs")
                );
            assertThat(response.getBodyAsString(), is(
                LOG_DATE_FORMAT.format(new Date(TimeService.currentTimeMillis())) + " - request:" + NEW_LINE +
                    NEW_LINE +
                    "  {" + NEW_LINE +
                    "    \"method\" : \"POST\"," + NEW_LINE +
                    "    \"path\" : \"some_other_path\"," + NEW_LINE +
                    "    \"keepAlive\" : false," + NEW_LINE +
                    "    \"secure\" : false," + NEW_LINE +
                    "    \"body\" : \"some_other_body\"" + NEW_LINE +
                    "  }" + NEW_LINE +
                    NEW_LINE +
                    " didn't match expectation:" + NEW_LINE +
                    NEW_LINE +
                    "  {" + NEW_LINE +
                    "    \"id\" : \"" + UUIDService.getUUID() + "\"," + NEW_LINE +
                    "    \"priority\" : 0," + NEW_LINE +
                    "    \"httpRequest\" : {" + NEW_LINE +
                    "      \"method\" : \"GET\"," + NEW_LINE +
                    "      \"path\" : \"some_path\"," + NEW_LINE +
                    "      \"keepAlive\" : true," + NEW_LINE +
                    "      \"secure\" : true," + NEW_LINE +
                    "      \"body\" : \"some_body\"" + NEW_LINE +
                    "    }," + NEW_LINE +
                    "    \"times\" : {" + NEW_LINE +
                    "      \"unlimited\" : true" + NEW_LINE +
                    "    }," + NEW_LINE +
                    "    \"timeToLive\" : {" + NEW_LINE +
                    "      \"unlimited\" : true" + NEW_LINE +
                    "    }" + NEW_LINE +
                    "  }" + NEW_LINE +
                    NEW_LINE +
                    " because:" + NEW_LINE +
                    NEW_LINE +
                    "  method didn't match: " + NEW_LINE +
                    "  " + NEW_LINE +
                    "    string or regex match failed expected:" + NEW_LINE +
                    "  " + NEW_LINE +
                    "      GET" + NEW_LINE +
                    "  " + NEW_LINE +
                    "     found:" + NEW_LINE +
                    "  " + NEW_LINE +
                    "      POST" + NEW_LINE +
                    "  " + NEW_LINE +
                    "  path didn't match: " + NEW_LINE +
                    "  " + NEW_LINE +
                    "    string or regex match failed expected:" + NEW_LINE +
                    "  " + NEW_LINE +
                    "      some_path" + NEW_LINE +
                    "  " + NEW_LINE +
                    "     found:" + NEW_LINE +
                    "  " + NEW_LINE +
                    "      some_other_path" + NEW_LINE +
                    "  " + NEW_LINE +
                    "  body didn't match: " + NEW_LINE +
                    "  " + NEW_LINE +
                    "    exact string match failed expected:" + NEW_LINE +
                    "  " + NEW_LINE +
                    "      some_body" + NEW_LINE +
                    "  " + NEW_LINE +
                    "     found:" + NEW_LINE +
                    "  " + NEW_LINE +
                    "      some_other_body" + NEW_LINE +
                    "  " + NEW_LINE +
                    "  headers matched" + NEW_LINE +
                    "  cookies matched" + NEW_LINE +
                    "  query matched" + NEW_LINE +
                    "  keep-alive didn't match: " + NEW_LINE +
                    "  " + NEW_LINE +
                    "    boolean match failed expected:" + NEW_LINE +
                    "  " + NEW_LINE +
                    "      true" + NEW_LINE +
                    "  " + NEW_LINE +
                    "     found:" + NEW_LINE +
                    "  " + NEW_LINE +
                    "      false" + NEW_LINE +
                    "  " + NEW_LINE +
                    "  sslMatches didn't match: " + NEW_LINE +
                    "  " + NEW_LINE +
                    "    boolean match failed expected:" + NEW_LINE +
                    "  " + NEW_LINE +
                    "      true" + NEW_LINE +
                    "  " + NEW_LINE +
                    "     found:" + NEW_LINE +
                    "  " + NEW_LINE +
                    "      false" + NEW_LINE +
                    NEW_LINE +
                    NEW_LINE +
                    "------------------------------------" + NEW_LINE +
                    LOG_DATE_FORMAT.format(new Date(TimeService.currentTimeMillis())) + " - retrieving logs that match:" + NEW_LINE +
                    NEW_LINE +
                    "  { }" + NEW_LINE +
                    NEW_LINE
            ));
        } finally {
            matchersFailFast(originalMatchersFailFast);
        }
    }

    @Test
    public void doesNotMatchIncorrectKeepAlive() {
        // given
        assertFalse(match(request().withKeepAlive(true), request().withKeepAlive(false)));
        assertFalse(match(request().withKeepAlive(false), request().withKeepAlive(null)));

        // then
        HttpResponse response = httpStateHandler
            .retrieve(
                request()
                    .withQueryStringParameter("type", "logs")
            );
        assertThat(response.getBodyAsString(), is(
            LOG_DATE_FORMAT.format(new Date(TimeService.currentTimeMillis())) + " - request:" + NEW_LINE +
                NEW_LINE +
                "  {" + NEW_LINE +
                "    \"keepAlive\" : false" + NEW_LINE +
                "  }" + NEW_LINE +
                NEW_LINE +
                " didn't match request matcher:" + NEW_LINE +
                NEW_LINE +
                "  {" + NEW_LINE +
                "    \"keepAlive\" : true" + NEW_LINE +
                "  }" + NEW_LINE +
                NEW_LINE +
                " because:" + NEW_LINE +
                NEW_LINE +
                "  method matched" + NEW_LINE +
                "  path matched" + NEW_LINE +
                "  body matched" + NEW_LINE +
                "  headers matched" + NEW_LINE +
                "  cookies matched" + NEW_LINE +
                "  query matched" + NEW_LINE +
                "  keep-alive didn't match: " + NEW_LINE +
                "  " + NEW_LINE +
                "    boolean match failed expected:" + NEW_LINE +
                "  " + NEW_LINE +
                "      true" + NEW_LINE +
                "  " + NEW_LINE +
                "     found:" + NEW_LINE +
                "  " + NEW_LINE +
                "      false" + NEW_LINE +
                NEW_LINE +
                NEW_LINE +
                "------------------------------------" + NEW_LINE +
                LOG_DATE_FORMAT.format(new Date(TimeService.currentTimeMillis())) + " - request:" + NEW_LINE +
                NEW_LINE +
                "  { }" + NEW_LINE +
                NEW_LINE +
                " didn't match request matcher:" + NEW_LINE +
                NEW_LINE +
                "  {" + NEW_LINE +
                "    \"keepAlive\" : false" + NEW_LINE +
                "  }" + NEW_LINE +
                NEW_LINE +
                " because:" + NEW_LINE +
                NEW_LINE +
                "  method matched" + NEW_LINE +
                "  path matched" + NEW_LINE +
                "  body matched" + NEW_LINE +
                "  headers matched" + NEW_LINE +
                "  cookies matched" + NEW_LINE +
                "  query matched" + NEW_LINE +
                "  keep-alive didn't match: " + NEW_LINE +
                "  " + NEW_LINE +
                "    boolean match failed expected:" + NEW_LINE +
                "  " + NEW_LINE +
                "      false" + NEW_LINE +
                "  " + NEW_LINE +
                "     found:" + NEW_LINE +
                "  " + NEW_LINE +
                "      null" + NEW_LINE +
                NEW_LINE +
                NEW_LINE +
                "------------------------------------" + NEW_LINE +
                LOG_DATE_FORMAT.format(new Date(TimeService.currentTimeMillis())) + " - retrieving logs that match:" + NEW_LINE +
                NEW_LINE +
                "  { }" + NEW_LINE +
                NEW_LINE
        ));
    }

    @Test
    public void doesNotMatchIncorrectKeepAliveWithoutFailFast() {
        boolean originalMatchersFailFast = matchersFailFast();
        try {
            // given
            matchersFailFast(false);
            assertFalse(match(request().withKeepAlive(true), request().withKeepAlive(false)));
            assertFalse(match(request().withKeepAlive(false), request().withKeepAlive(null)));

            // then
            HttpResponse response = httpStateHandler
                .retrieve(
                    request()
                        .withQueryStringParameter("type", "logs")
                );
            assertThat(response.getBodyAsString(), is(
                LOG_DATE_FORMAT.format(new Date(TimeService.currentTimeMillis())) + " - request:" + NEW_LINE +
                    NEW_LINE +
                    "  {" + NEW_LINE +
                    "    \"keepAlive\" : false" + NEW_LINE +
                    "  }" + NEW_LINE +
                    NEW_LINE +
                    " didn't match request matcher:" + NEW_LINE +
                    NEW_LINE +
                    "  {" + NEW_LINE +
                    "    \"keepAlive\" : true" + NEW_LINE +
                    "  }" + NEW_LINE +
                    NEW_LINE +
                    " because:" + NEW_LINE +
                    NEW_LINE +
                    "  method matched" + NEW_LINE +
                    "  path matched" + NEW_LINE +
                    "  body matched" + NEW_LINE +
                    "  headers matched" + NEW_LINE +
                    "  cookies matched" + NEW_LINE +
                    "  query matched" + NEW_LINE +
                    "  keep-alive didn't match: " + NEW_LINE +
                    "  " + NEW_LINE +
                    "    boolean match failed expected:" + NEW_LINE +
                    "  " + NEW_LINE +
                    "      true" + NEW_LINE +
                    "  " + NEW_LINE +
                    "     found:" + NEW_LINE +
                    "  " + NEW_LINE +
                    "      false" + NEW_LINE +
                    "  " + NEW_LINE +
                    "  sslMatches matched" + NEW_LINE +
                    NEW_LINE +
                    "------------------------------------" + NEW_LINE +
                    LOG_DATE_FORMAT.format(new Date(TimeService.currentTimeMillis())) + " - request:" + NEW_LINE +
                    NEW_LINE +
                    "  { }" + NEW_LINE +
                    NEW_LINE +
                    " didn't match request matcher:" + NEW_LINE +
                    NEW_LINE +
                    "  {" + NEW_LINE +
                    "    \"keepAlive\" : false" + NEW_LINE +
                    "  }" + NEW_LINE +
                    NEW_LINE +
                    " because:" + NEW_LINE +
                    NEW_LINE +
                    "  method matched" + NEW_LINE +
                    "  path matched" + NEW_LINE +
                    "  body matched" + NEW_LINE +
                    "  headers matched" + NEW_LINE +
                    "  cookies matched" + NEW_LINE +
                    "  query matched" + NEW_LINE +
                    "  keep-alive didn't match: " + NEW_LINE +
                    "  " + NEW_LINE +
                    "    boolean match failed expected:" + NEW_LINE +
                    "  " + NEW_LINE +
                    "      false" + NEW_LINE +
                    "  " + NEW_LINE +
                    "     found:" + NEW_LINE +
                    "  " + NEW_LINE +
                    "      null" + NEW_LINE +
                    "  " + NEW_LINE +
                    "  sslMatches matched" + NEW_LINE +
                    NEW_LINE +
                    "------------------------------------" + NEW_LINE +
                    LOG_DATE_FORMAT.format(new Date(TimeService.currentTimeMillis())) + " - retrieving logs that match:" + NEW_LINE +
                    NEW_LINE +
                    "  { }" + NEW_LINE +
                    NEW_LINE
            ));
        } finally {
            matchersFailFast(originalMatchersFailFast);
        }
    }

    @Test
    public void doesNotMatchIncorrectKeepAliveWithExpectationAndWithoutFailFast() {
        boolean originalMatchersFailFast = matchersFailFast();
        try {
            // given
            matchersFailFast(false);
            assertFalse(match(new Expectation(request().withKeepAlive(true)), request().withKeepAlive(false)));
            assertFalse(match(new Expectation(request().withKeepAlive(false)), request().withKeepAlive(null)));

            // then
            HttpResponse response = httpStateHandler
                .retrieve(
                    request()
                        .withQueryStringParameter("type", "logs")
                );
            assertThat(response.getBodyAsString(), is(
                LOG_DATE_FORMAT.format(new Date(TimeService.currentTimeMillis())) + " - request:" + NEW_LINE +
                    NEW_LINE +
                    "  {" + NEW_LINE +
                    "    \"keepAlive\" : false" + NEW_LINE +
                    "  }" + NEW_LINE +
                    NEW_LINE +
                    " didn't match expectation:" + NEW_LINE +
                    NEW_LINE +
                    "  {" + NEW_LINE +
                    "    \"id\" : \"" + UUIDService.getUUID() + "\"," + NEW_LINE +
                    "    \"priority\" : 0," + NEW_LINE +
                    "    \"httpRequest\" : {" + NEW_LINE +
                    "      \"keepAlive\" : true" + NEW_LINE +
                    "    }," + NEW_LINE +
                    "    \"times\" : {" + NEW_LINE +
                    "      \"unlimited\" : true" + NEW_LINE +
                    "    }," + NEW_LINE +
                    "    \"timeToLive\" : {" + NEW_LINE +
                    "      \"unlimited\" : true" + NEW_LINE +
                    "    }" + NEW_LINE +
                    "  }" + NEW_LINE +
                    NEW_LINE +
                    " because:" + NEW_LINE +
                    NEW_LINE +
                    "  method matched" + NEW_LINE +
                    "  path matched" + NEW_LINE +
                    "  body matched" + NEW_LINE +
                    "  headers matched" + NEW_LINE +
                    "  cookies matched" + NEW_LINE +
                    "  query matched" + NEW_LINE +
                    "  keep-alive didn't match: " + NEW_LINE +
                    "  " + NEW_LINE +
                    "    boolean match failed expected:" + NEW_LINE +
                    "  " + NEW_LINE +
                    "      true" + NEW_LINE +
                    "  " + NEW_LINE +
                    "     found:" + NEW_LINE +
                    "  " + NEW_LINE +
                    "      false" + NEW_LINE +
                    "  " + NEW_LINE +
                    "  sslMatches matched" + NEW_LINE +
                    NEW_LINE +
                    "------------------------------------" + NEW_LINE +
                    LOG_DATE_FORMAT.format(new Date(TimeService.currentTimeMillis())) + " - request:" + NEW_LINE +
                    NEW_LINE +
                    "  { }" + NEW_LINE +
                    NEW_LINE +
                    " didn't match expectation:" + NEW_LINE +
                    NEW_LINE +
                    "  {" + NEW_LINE +
                    "    \"id\" : \"" + UUIDService.getUUID() + "\"," + NEW_LINE +
                    "    \"priority\" : 0," + NEW_LINE +
                    "    \"httpRequest\" : {" + NEW_LINE +
                    "      \"keepAlive\" : false" + NEW_LINE +
                    "    }," + NEW_LINE +
                    "    \"times\" : {" + NEW_LINE +
                    "      \"unlimited\" : true" + NEW_LINE +
                    "    }," + NEW_LINE +
                    "    \"timeToLive\" : {" + NEW_LINE +
                    "      \"unlimited\" : true" + NEW_LINE +
                    "    }" + NEW_LINE +
                    "  }" + NEW_LINE +
                    NEW_LINE +
                    " because:" + NEW_LINE +
                    NEW_LINE +
                    "  method matched" + NEW_LINE +
                    "  path matched" + NEW_LINE +
                    "  body matched" + NEW_LINE +
                    "  headers matched" + NEW_LINE +
                    "  cookies matched" + NEW_LINE +
                    "  query matched" + NEW_LINE +
                    "  keep-alive didn't match: " + NEW_LINE +
                    "  " + NEW_LINE +
                    "    boolean match failed expected:" + NEW_LINE +
                    "  " + NEW_LINE +
                    "      false" + NEW_LINE +
                    "  " + NEW_LINE +
                    "     found:" + NEW_LINE +
                    "  " + NEW_LINE +
                    "      null" + NEW_LINE +
                    "  " + NEW_LINE +
                    "  sslMatches matched" + NEW_LINE +
                    NEW_LINE +
                    "------------------------------------" + NEW_LINE +
                    LOG_DATE_FORMAT.format(new Date(TimeService.currentTimeMillis())) + " - retrieving logs that match:" + NEW_LINE +
                    NEW_LINE +
                    "  { }" + NEW_LINE +
                    NEW_LINE
            ));
        } finally {
            matchersFailFast(originalMatchersFailFast);
        }
    }

    @Test
    public void doesNotMatchIncorrectSsl() {
        boolean originalMatchersFailFast = matchersFailFast();
        try {
            // given
            assertFalse(match(request().withSecure(true), request().withSecure(false)));
            assertFalse(match(request().withSecure(true), request().withSecure(null)));

            // then
            HttpResponse response = httpStateHandler
                .retrieve(
                    request()
                        .withQueryStringParameter("type", "logs")
                );
            assertThat(response.getBodyAsString(), is(
                LOG_DATE_FORMAT.format(new Date(TimeService.currentTimeMillis())) + " - request:" + NEW_LINE +
                    NEW_LINE +
                    "  {" + NEW_LINE +
                    "    \"secure\" : false" + NEW_LINE +
                    "  }" + NEW_LINE +
                    NEW_LINE +
                    " didn't match request matcher:" + NEW_LINE +
                    NEW_LINE +
                    "  {" + NEW_LINE +
                    "    \"secure\" : true" + NEW_LINE +
                    "  }" + NEW_LINE +
                    NEW_LINE +
                    " because:" + NEW_LINE +
                    NEW_LINE +
                    "  method matched" + NEW_LINE +
                    "  path matched" + NEW_LINE +
                    "  body matched" + NEW_LINE +
                    "  headers matched" + NEW_LINE +
                    "  cookies matched" + NEW_LINE +
                    "  query matched" + NEW_LINE +
                    "  keep-alive matched" + NEW_LINE +
                    "  sslMatches didn't match: " + NEW_LINE +
                    "  " + NEW_LINE +
                    "    boolean match failed expected:" + NEW_LINE +
                    "  " + NEW_LINE +
                    "      true" + NEW_LINE +
                    "  " + NEW_LINE +
                    "     found:" + NEW_LINE +
                    "  " + NEW_LINE +
                    "      false" + NEW_LINE +
                    NEW_LINE +
                    NEW_LINE +
                    "------------------------------------" + NEW_LINE +
                    LOG_DATE_FORMAT.format(new Date(TimeService.currentTimeMillis())) + " - request:" + NEW_LINE +
                    NEW_LINE +
                    "  { }" + NEW_LINE +
                    NEW_LINE +
                    " didn't match request matcher:" + NEW_LINE +
                    NEW_LINE +
                    "  {" + NEW_LINE +
                    "    \"secure\" : true" + NEW_LINE +
                    "  }" + NEW_LINE +
                    NEW_LINE +
                    " because:" + NEW_LINE +
                    NEW_LINE +
                    "  method matched" + NEW_LINE +
                    "  path matched" + NEW_LINE +
                    "  body matched" + NEW_LINE +
                    "  headers matched" + NEW_LINE +
                    "  cookies matched" + NEW_LINE +
                    "  query matched" + NEW_LINE +
                    "  keep-alive matched" + NEW_LINE +
                    "  sslMatches didn't match: " + NEW_LINE +
                    "  " + NEW_LINE +
                    "    boolean match failed expected:" + NEW_LINE +
                    "  " + NEW_LINE +
                    "      true" + NEW_LINE +
                    "  " + NEW_LINE +
                    "     found:" + NEW_LINE +
                    "  " + NEW_LINE +
                    "      null" + NEW_LINE +
                    NEW_LINE +
                    NEW_LINE +
                    "------------------------------------" + NEW_LINE +
                    LOG_DATE_FORMAT.format(new Date(TimeService.currentTimeMillis())) + " - retrieving logs that match:" + NEW_LINE +
                    NEW_LINE +
                    "  { }" + NEW_LINE +
                    NEW_LINE
            ));
        } finally {
            matchersFailFast(originalMatchersFailFast);
        }
    }

    @Test
    public void doesNotMatchIncorrectMethod() {
        boolean originalMatchersFailFast = matchersFailFast();
        try {
            // given
            assertFalse(match(request().withMethod("HEAD"), request().withMethod("OPTIONS")));

            // then
            HttpResponse response = httpStateHandler
                .retrieve(
                    request()
                        .withQueryStringParameter("type", "logs")
                );
            assertThat(response.getBodyAsString(), is(
                LOG_DATE_FORMAT.format(new Date(TimeService.currentTimeMillis())) + " - request:" + NEW_LINE +
                    NEW_LINE +
                    "  {" + NEW_LINE +
                    "    \"method\" : \"OPTIONS\"" + NEW_LINE +
                    "  }" + NEW_LINE +
                    NEW_LINE +
                    " didn't match request matcher:" + NEW_LINE +
                    NEW_LINE +
                    "  {" + NEW_LINE +
                    "    \"method\" : \"HEAD\"" + NEW_LINE +
                    "  }" + NEW_LINE +
                    NEW_LINE +
                    " because:" + NEW_LINE +
                    NEW_LINE +
                    "  method didn't match: " + NEW_LINE +
                    "  " + NEW_LINE +
                    "    string or regex match failed expected:" + NEW_LINE +
                    "  " + NEW_LINE +
                    "      HEAD" + NEW_LINE +
                    "  " + NEW_LINE +
                    "     found:" + NEW_LINE +
                    "  " + NEW_LINE +
                    "      OPTIONS" + NEW_LINE +
                    NEW_LINE +
                    NEW_LINE +
                    "------------------------------------" + NEW_LINE +
                    LOG_DATE_FORMAT.format(new Date(TimeService.currentTimeMillis())) + " - retrieving logs that match:" + NEW_LINE +
                    NEW_LINE +
                    "  { }" + NEW_LINE +
                    NEW_LINE
            ));
        } finally {
            matchersFailFast(originalMatchersFailFast);
        }
    }

    @Test
    public void doesNotMatchIncorrectMethodRegex() {
        boolean originalMatchersFailFast = matchersFailFast();
        try {
            // given
            assertFalse(match(request().withMethod("P[A-Z]{2}"), request().withMethod("POST")));

            // then
            HttpResponse response = httpStateHandler
                .retrieve(
                    request()
                        .withQueryStringParameter("type", "logs")
                );
            assertThat(response.getBodyAsString(), is(
                LOG_DATE_FORMAT.format(new Date(TimeService.currentTimeMillis())) + " - request:" + NEW_LINE +
                    NEW_LINE +
                    "  {" + NEW_LINE +
                    "    \"method\" : \"POST\"" + NEW_LINE +
                    "  }" + NEW_LINE +
                    NEW_LINE +
                    " didn't match request matcher:" + NEW_LINE +
                    NEW_LINE +
                    "  {" + NEW_LINE +
                    "    \"method\" : \"P[A-Z]{2}\"" + NEW_LINE +
                    "  }" + NEW_LINE +
                    NEW_LINE +
                    " because:" + NEW_LINE +
                    NEW_LINE +
                    "  method didn't match: " + NEW_LINE +
                    "  " + NEW_LINE +
                    "    string or regex match failed expected:" + NEW_LINE +
                    "  " + NEW_LINE +
                    "      P[A-Z]{2}" + NEW_LINE +
                    "  " + NEW_LINE +
                    "     found:" + NEW_LINE +
                    "  " + NEW_LINE +
                    "      POST" + NEW_LINE +
                    NEW_LINE +
                    NEW_LINE +
                    "------------------------------------" + NEW_LINE +
                    LOG_DATE_FORMAT.format(new Date(TimeService.currentTimeMillis())) + " - retrieving logs that match:" + NEW_LINE +
                    NEW_LINE +
                    "  { }" + NEW_LINE +
                    NEW_LINE
            ));
        } finally {
            matchersFailFast(originalMatchersFailFast);
        }
    }

    @Test
    public void doesNotMatchEncodedMatcherPath() {
        boolean originalMatchersFailFast = matchersFailFast();
        try {
            // given
            assertFalse(match(request().withPath("/dWM%2FdWM+ZA=="), request().withPath("/dWM/dWM+ZA==")));

            // then
            HttpResponse response = httpStateHandler
                .retrieve(
                    request()
                        .withQueryStringParameter("type", "logs")
                );
            assertThat(response.getBodyAsString(), is(
                LOG_DATE_FORMAT.format(new Date(TimeService.currentTimeMillis())) + " - request:" + NEW_LINE +
                    NEW_LINE +
                    "  {" + NEW_LINE +
                    "    \"path\" : \"/dWM/dWM+ZA==\"" + NEW_LINE +
                    "  }" + NEW_LINE +
                    NEW_LINE +
                    " didn't match request matcher:" + NEW_LINE +
                    NEW_LINE +
                    "  {" + NEW_LINE +
                    "    \"path\" : \"/dWM%2FdWM+ZA==\"" + NEW_LINE +
                    "  }" + NEW_LINE +
                    NEW_LINE +
                    " because:" + NEW_LINE +
                    NEW_LINE +
                    "  method matched" + NEW_LINE +
                    "  path didn't match: " + NEW_LINE +
                    "  " + NEW_LINE +
                    "    string or regex match failed expected:" + NEW_LINE +
                    "  " + NEW_LINE +
                    "      /dWM%2FdWM+ZA==" + NEW_LINE +
                    "  " + NEW_LINE +
                    "     found:" + NEW_LINE +
                    "  " + NEW_LINE +
                    "      /dWM/dWM+ZA==" + NEW_LINE +
                    NEW_LINE +
                    NEW_LINE +
                    "------------------------------------" + NEW_LINE +
                    LOG_DATE_FORMAT.format(new Date(TimeService.currentTimeMillis())) + " - retrieving logs that match:" + NEW_LINE +
                    NEW_LINE +
                    "  { }" + NEW_LINE +
                    NEW_LINE
            ));
        } finally {
            matchersFailFast(originalMatchersFailFast);
        }
    }

    @Test
    public void doesNotMatchIncorrectPath() {
        boolean originalMatchersFailFast = matchersFailFast();
        try {
            // given
            assertFalse(match(request().withPath("somepath"), request().withPath("pathsome")));

            // then
            HttpResponse response = httpStateHandler
                .retrieve(
                    request()
                        .withQueryStringParameter("type", "logs")
                );
            assertThat(response.getBodyAsString(), is(
                LOG_DATE_FORMAT.format(new Date(TimeService.currentTimeMillis())) + " - request:" + NEW_LINE +
                    NEW_LINE +
                    "  {" + NEW_LINE +
                    "    \"path\" : \"pathsome\"" + NEW_LINE +
                    "  }" + NEW_LINE +
                    NEW_LINE +
                    " didn't match request matcher:" + NEW_LINE +
                    NEW_LINE +
                    "  {" + NEW_LINE +
                    "    \"path\" : \"somepath\"" + NEW_LINE +
                    "  }" + NEW_LINE +
                    NEW_LINE +
                    " because:" + NEW_LINE +
                    NEW_LINE +
                    "  method matched" + NEW_LINE +
                    "  path didn't match: " + NEW_LINE +
                    "  " + NEW_LINE +
                    "    string or regex match failed expected:" + NEW_LINE +
                    "  " + NEW_LINE +
                    "      somepath" + NEW_LINE +
                    "  " + NEW_LINE +
                    "     found:" + NEW_LINE +
                    "  " + NEW_LINE +
                    "      pathsome" + NEW_LINE +
                    NEW_LINE +
                    NEW_LINE +
                    "------------------------------------" + NEW_LINE +
                    LOG_DATE_FORMAT.format(new Date(TimeService.currentTimeMillis())) + " - retrieving logs that match:" + NEW_LINE +
                    NEW_LINE +
                    "  { }" + NEW_LINE +
                    NEW_LINE
            ));
        } finally {
            matchersFailFast(originalMatchersFailFast);
        }
    }

    @Test
    public void doesNotMatchIncorrectPathRegex() {
        boolean originalMatchersFailFast = matchersFailFast();
        try {
            // given
            assertFalse(match(request().withPath("someP[a-z]{2}"), request().withPath("somePath")));

            // then
            HttpResponse response = httpStateHandler
                .retrieve(
                    request()
                        .withQueryStringParameter("type", "logs")
                );
            assertThat(response.getBodyAsString(), is(
                LOG_DATE_FORMAT.format(new Date(TimeService.currentTimeMillis())) + " - request:" + NEW_LINE +
                    NEW_LINE +
                    "  {" + NEW_LINE +
                    "    \"path\" : \"somePath\"" + NEW_LINE +
                    "  }" + NEW_LINE +
                    NEW_LINE +
                    " didn't match request matcher:" + NEW_LINE +
                    NEW_LINE +
                    "  {" + NEW_LINE +
                    "    \"path\" : \"someP[a-z]{2}\"" + NEW_LINE +
                    "  }" + NEW_LINE +
                    NEW_LINE +
                    " because:" + NEW_LINE +
                    NEW_LINE +
                    "  method matched" + NEW_LINE +
                    "  path didn't match: " + NEW_LINE +
                    "  " + NEW_LINE +
                    "    string or regex match failed expected:" + NEW_LINE +
                    "  " + NEW_LINE +
                    "      someP[a-z]{2}" + NEW_LINE +
                    "  " + NEW_LINE +
                    "     found:" + NEW_LINE +
                    "  " + NEW_LINE +
                    "      somePath" + NEW_LINE +
                    NEW_LINE +
                    NEW_LINE +
                    "------------------------------------" + NEW_LINE +
                    LOG_DATE_FORMAT.format(new Date(TimeService.currentTimeMillis())) + " - retrieving logs that match:" + NEW_LINE +
                    NEW_LINE +
                    "  { }" + NEW_LINE +
                    NEW_LINE
            ));
        } finally {
            matchersFailFast(originalMatchersFailFast);
        }
    }

    @Test
    public void doesNotMatchIncorrectQueryStringName() {
        boolean originalMatchersFailFast = matchersFailFast();
        try {
            // given
            assertFalse(match(request().withQueryStringParameters(new Parameter("someKey", "someValue")), request().withQueryStringParameter(new Parameter("someOtherKey", "someValue"))));

            // then
            HttpResponse response = httpStateHandler
                .retrieve(
                    request()
                        .withQueryStringParameter("type", "logs")
                );
            assertThat(response.getBodyAsString(), is(
                LOG_DATE_FORMAT.format(new Date(TimeService.currentTimeMillis())) + " - request:" + NEW_LINE +
                    NEW_LINE +
                    "  {" + NEW_LINE +
                    "    \"queryStringParameters\" : {" + NEW_LINE +
                    "      \"someOtherKey\" : [ \"someValue\" ]" + NEW_LINE +
                    "    }" + NEW_LINE +
                    "  }" + NEW_LINE +
                    NEW_LINE +
                    " didn't match request matcher:" + NEW_LINE +
                    NEW_LINE +
                    "  {" + NEW_LINE +
                    "    \"queryStringParameters\" : {" + NEW_LINE +
                    "      \"someKey\" : [ \"someValue\" ]" + NEW_LINE +
                    "    }" + NEW_LINE +
                    "  }" + NEW_LINE +
                    NEW_LINE +
                    " because:" + NEW_LINE +
                    NEW_LINE +
                    "  method matched" + NEW_LINE +
                    "  path matched" + NEW_LINE +
                    "  body matched" + NEW_LINE +
                    "  headers matched" + NEW_LINE +
                    "  cookies matched" + NEW_LINE +
                    "  query didn't match: " + NEW_LINE +
                    "  " + NEW_LINE +
                    "    multimap subset match failed expected:" + NEW_LINE +
                    "  " + NEW_LINE +
                    "      {" + NEW_LINE +
                    "        \"someKey\" : [ \"someValue\" ]" + NEW_LINE +
                    "      }" + NEW_LINE +
                    "  " + NEW_LINE +
                    "     found:" + NEW_LINE +
                    "  " + NEW_LINE +
                    "      {" + NEW_LINE +
                    "        \"someOtherKey\" : [ \"someValue\" ]" + NEW_LINE +
                    "      }" + NEW_LINE +
                    "  " + NEW_LINE +
                    "     failed because:" + NEW_LINE +
                    "  " + NEW_LINE +
                    "      multimap is not a subset" + NEW_LINE +
                    NEW_LINE +
                    NEW_LINE +
                    "------------------------------------" + NEW_LINE +
                    LOG_DATE_FORMAT.format(new Date(TimeService.currentTimeMillis())) + " - retrieving logs that match:" + NEW_LINE +
                    NEW_LINE +
                    "  { }" + NEW_LINE +
                    NEW_LINE
            ));
        } finally {
            matchersFailFast(originalMatchersFailFast);
        }
    }

    @Test
    public void doesNotMatchIncorrectQueryStringValue() {
        boolean originalMatchersFailFast = matchersFailFast();
        try {
            // given
            assertFalse(match(request().withQueryStringParameters(new Parameter("someKey", "someValue")), request().withQueryStringParameter(new Parameter("someKey", "someOtherValue"))));

            // then
            HttpResponse response = httpStateHandler
                .retrieve(
                    request()
                        .withQueryStringParameter("type", "logs")
                );
            assertThat(response.getBodyAsString(), is(
                LOG_DATE_FORMAT.format(new Date(TimeService.currentTimeMillis())) + " - request:" + NEW_LINE +
                    NEW_LINE +
                    "  {" + NEW_LINE +
                    "    \"queryStringParameters\" : {" + NEW_LINE +
                    "      \"someKey\" : [ \"someOtherValue\" ]" + NEW_LINE +
                    "    }" + NEW_LINE +
                    "  }" + NEW_LINE +
                    NEW_LINE +
                    " didn't match request matcher:" + NEW_LINE +
                    NEW_LINE +
                    "  {" + NEW_LINE +
                    "    \"queryStringParameters\" : {" + NEW_LINE +
                    "      \"someKey\" : [ \"someValue\" ]" + NEW_LINE +
                    "    }" + NEW_LINE +
                    "  }" + NEW_LINE +
                    NEW_LINE +
                    " because:" + NEW_LINE +
                    NEW_LINE +
                    "  method matched" + NEW_LINE +
                    "  path matched" + NEW_LINE +
                    "  body matched" + NEW_LINE +
                    "  headers matched" + NEW_LINE +
                    "  cookies matched" + NEW_LINE +
                    "  query didn't match: " + NEW_LINE +
                    "  " + NEW_LINE +
                    "    multimap subset match failed expected:" + NEW_LINE +
                    "  " + NEW_LINE +
                    "      {" + NEW_LINE +
                    "        \"someKey\" : [ \"someValue\" ]" + NEW_LINE +
                    "      }" + NEW_LINE +
                    "  " + NEW_LINE +
                    "     found:" + NEW_LINE +
                    "  " + NEW_LINE +
                    "      {" + NEW_LINE +
                    "        \"someKey\" : [ \"someOtherValue\" ]" + NEW_LINE +
                    "      }" + NEW_LINE +
                    "  " + NEW_LINE +
                    "     failed because:" + NEW_LINE +
                    "  " + NEW_LINE +
                    "      multimap is not a subset" + NEW_LINE +
                    NEW_LINE +
                    NEW_LINE +
                    "------------------------------------" + NEW_LINE +
                    LOG_DATE_FORMAT.format(new Date(TimeService.currentTimeMillis())) + " - retrieving logs that match:" + NEW_LINE +
                    NEW_LINE +
                    "  { }" + NEW_LINE +
                    NEW_LINE
            ));
        } finally {
            matchersFailFast(originalMatchersFailFast);
        }
    }

    @Test
    public void doesNotMatchIncorrectParameterName() {
        boolean originalMatchersFailFast = matchersFailFast();
        try {
            // given
            assertFalse(match(request().withBody(new ParameterBody(new Parameter("name", "value"))), request().withBody(new ParameterBody(new Parameter("name1", "value")))));

            // then
            HttpResponse response = httpStateHandler
                .retrieve(
                    request()
                        .withQueryStringParameter("type", "logs")
                );
            assertThat(response.getBodyAsString(), is(
                LOG_DATE_FORMAT.format(new Date(TimeService.currentTimeMillis())) + " - request:" + NEW_LINE +
                    NEW_LINE +
                    "  {" + NEW_LINE +
                    "    \"body\" : {" + NEW_LINE +
                    "      \"type\" : \"PARAMETERS\"," + NEW_LINE +
                    "      \"value\" : {" + NEW_LINE +
                    "        \"name1\" : [ \"value\" ]" + NEW_LINE +
                    "      }" + NEW_LINE +
                    "    }" + NEW_LINE +
                    "  }" + NEW_LINE +
                    NEW_LINE +
                    " didn't match request matcher:" + NEW_LINE +
                    NEW_LINE +
                    "  {" + NEW_LINE +
                    "    \"body\" : {" + NEW_LINE +
                    "      \"type\" : \"PARAMETERS\"," + NEW_LINE +
                    "      \"value\" : {" + NEW_LINE +
                    "        \"name\" : [ \"value\" ]" + NEW_LINE +
                    "      }" + NEW_LINE +
                    "    }" + NEW_LINE +
                    "  }" + NEW_LINE +
                    NEW_LINE +
                    " because:" + NEW_LINE +
                    NEW_LINE +
                    "  method matched" + NEW_LINE +
                    "  path matched" + NEW_LINE +
                    "  body didn't match: " + NEW_LINE +
                    "  " + NEW_LINE +
                    "    multimap subset match failed expected:" + NEW_LINE +
                    "  " + NEW_LINE +
                    "      {" + NEW_LINE +
                    "        \"name\" : [ \"value\" ]" + NEW_LINE +
                    "      }" + NEW_LINE +
                    "  " + NEW_LINE +
                    "     found:" + NEW_LINE +
                    "  " + NEW_LINE +
                    "      {" + NEW_LINE +
                    "        \"name1\" : [ \"value\" ]" + NEW_LINE +
                    "      }" + NEW_LINE +
                    "  " + NEW_LINE +
                    "     failed because:" + NEW_LINE +
                    "  " + NEW_LINE +
                    "      multimap is not a subset" + NEW_LINE +
                    NEW_LINE +
                    NEW_LINE +
                    "------------------------------------" + NEW_LINE +
                    LOG_DATE_FORMAT.format(new Date(TimeService.currentTimeMillis())) + " - retrieving logs that match:" + NEW_LINE +
                    NEW_LINE +
                    "  { }" + NEW_LINE +
                    NEW_LINE
            ));
        } finally {
            matchersFailFast(originalMatchersFailFast);
        }
    }

    @Test
    public void doesNotMatchIncorrectParameterValueRegex() {
        boolean originalMatchersFailFast = matchersFailFast();
        try {
            // given
            assertFalse(match(request().withBody(new ParameterBody(new Parameter("name", "va[0-9]{1}ue"))), request().withBody(new ParameterBody(new Parameter("name", "value1")))));

            // then
            HttpResponse response = httpStateHandler
                .retrieve(
                    request()
                        .withQueryStringParameter("type", "logs")
                );
            assertThat(response.getBodyAsString(), is(
                LOG_DATE_FORMAT.format(new Date(TimeService.currentTimeMillis())) + " - request:" + NEW_LINE +
                    NEW_LINE +
                    "  {" + NEW_LINE +
                    "    \"body\" : {" + NEW_LINE +
                    "      \"type\" : \"PARAMETERS\"," + NEW_LINE +
                    "      \"value\" : {" + NEW_LINE +
                    "        \"name\" : [ \"value1\" ]" + NEW_LINE +
                    "      }" + NEW_LINE +
                    "    }" + NEW_LINE +
                    "  }" + NEW_LINE +
                    NEW_LINE +
                    " didn't match request matcher:" + NEW_LINE +
                    NEW_LINE +
                    "  {" + NEW_LINE +
                    "    \"body\" : {" + NEW_LINE +
                    "      \"type\" : \"PARAMETERS\"," + NEW_LINE +
                    "      \"value\" : {" + NEW_LINE +
                    "        \"name\" : [ \"va[0-9]{1}ue\" ]" + NEW_LINE +
                    "      }" + NEW_LINE +
                    "    }" + NEW_LINE +
                    "  }" + NEW_LINE +
                    NEW_LINE +
                    " because:" + NEW_LINE +
                    NEW_LINE +
                    "  method matched" + NEW_LINE +
                    "  path matched" + NEW_LINE +
                    "  body didn't match: " + NEW_LINE +
                    "  " + NEW_LINE +
                    "    multimap subset match failed expected:" + NEW_LINE +
                    "  " + NEW_LINE +
                    "      {" + NEW_LINE +
                    "        \"name\" : [ \"va[0-9]{1}ue\" ]" + NEW_LINE +
                    "      }" + NEW_LINE +
                    "  " + NEW_LINE +
                    "     found:" + NEW_LINE +
                    "  " + NEW_LINE +
                    "      {" + NEW_LINE +
                    "        \"name\" : [ \"value1\" ]" + NEW_LINE +
                    "      }" + NEW_LINE +
                    "  " + NEW_LINE +
                    "     failed because:" + NEW_LINE +
                    "  " + NEW_LINE +
                    "      multimap is not a subset" + NEW_LINE +
                    NEW_LINE +
                    NEW_LINE +
                    "------------------------------------" + NEW_LINE +
                    LOG_DATE_FORMAT.format(new Date(TimeService.currentTimeMillis())) + " - retrieving logs that match:" + NEW_LINE +
                    NEW_LINE +
                    "  { }" + NEW_LINE +
                    NEW_LINE
            ));
        } finally {
            matchersFailFast(originalMatchersFailFast);
        }
    }

    @Test
    public void doesNotMatchIncorrectBody() {
        boolean originalMatchersFailFast = matchersFailFast();
        try {
            // given
            assertFalse(match(request().withBody(exact("somebody")), request().withBody("bodysome")));

            // then
            HttpResponse response = httpStateHandler
                .retrieve(
                    request()
                        .withQueryStringParameter("type", "logs")
                );
            assertThat(response.getBodyAsString(), is(
                LOG_DATE_FORMAT.format(new Date(TimeService.currentTimeMillis())) + " - request:" + NEW_LINE +
                    NEW_LINE +
                    "  {" + NEW_LINE +
                    "    \"body\" : \"bodysome\"" + NEW_LINE +
                    "  }" + NEW_LINE +
                    NEW_LINE +
                    " didn't match request matcher:" + NEW_LINE +
                    NEW_LINE +
                    "  {" + NEW_LINE +
                    "    \"body\" : \"somebody\"" + NEW_LINE +
                    "  }" + NEW_LINE +
                    NEW_LINE +
                    " because:" + NEW_LINE +
                    NEW_LINE +
                    "  method matched" + NEW_LINE +
                    "  path matched" + NEW_LINE +
                    "  body didn't match: " + NEW_LINE +
                    "  " + NEW_LINE +
                    "    exact string match failed expected:" + NEW_LINE +
                    "  " + NEW_LINE +
                    "      somebody" + NEW_LINE +
                    "  " + NEW_LINE +
                    "     found:" + NEW_LINE +
                    "  " + NEW_LINE +
                    "      bodysome" + NEW_LINE +
                    NEW_LINE +
                    NEW_LINE +
                    "------------------------------------" + NEW_LINE +
                    LOG_DATE_FORMAT.format(new Date(TimeService.currentTimeMillis())) + " - retrieving logs that match:" + NEW_LINE +
                    NEW_LINE +
                    "  { }" + NEW_LINE +
                    NEW_LINE
            ));
        } finally {
            matchersFailFast(originalMatchersFailFast);
        }
    }

    @Test
    public void doesNotMatchEmptyBodyAgainstMatcherWithStringBody() {
        boolean originalMatchersFailFast = matchersFailFast();
        try {
            // given
            assertFalse(match(request().withBody(exact("somebody")), request()));

            // then
            HttpResponse response = httpStateHandler
                .retrieve(
                    request()
                        .withQueryStringParameter("type", "logs")
                );
            assertThat(response.getBodyAsString(), is(
                LOG_DATE_FORMAT.format(new Date(TimeService.currentTimeMillis())) + " - request:" + NEW_LINE +
                    NEW_LINE +
                    "  { }" + NEW_LINE +
                    NEW_LINE +
                    " didn't match request matcher:" + NEW_LINE +
                    NEW_LINE +
                    "  {" + NEW_LINE +
                    "    \"body\" : \"somebody\"" + NEW_LINE +
                    "  }" + NEW_LINE +
                    NEW_LINE +
                    " because:" + NEW_LINE +
                    NEW_LINE +
                    "  method matched" + NEW_LINE +
                    "  path matched" + NEW_LINE +
                    "  body didn't match: " + NEW_LINE +
                    "  " + NEW_LINE +
                    "    exact string match failed expected:" + NEW_LINE +
                    "  " + NEW_LINE +
                    "      somebody" + NEW_LINE +
                    "  " + NEW_LINE +
                    "     found:" + NEW_LINE +
                    "  " + NEW_LINE +
                    "  " + NEW_LINE +
                    NEW_LINE +
                    NEW_LINE +
                    "------------------------------------" + NEW_LINE +
                    LOG_DATE_FORMAT.format(new Date(TimeService.currentTimeMillis())) + " - retrieving logs that match:" + NEW_LINE +
                    NEW_LINE +
                    "  { }" + NEW_LINE +
                    NEW_LINE
            ));
        } finally {
            matchersFailFast(originalMatchersFailFast);
        }
    }

    @Test
    public void doesNotMatchIncorrectBodyXPath() {
        boolean originalMatchersFailFast = matchersFailFast();
        try {
            // given
            String matched = "" +
                "<element>" + NEW_LINE +
                "   <key>some_key</key>" + NEW_LINE +
                "</element>";
            assertFalse(match(request().withBody(xpath("/element[key = 'some_key' and value = 'some_value']")), request().withBody(matched)));

            // then
            HttpResponse response = httpStateHandler
                .retrieve(
                    request()
                        .withQueryStringParameter("type", "logs")
                );
            assertThat(response.getBodyAsString(), is(
                LOG_DATE_FORMAT.format(new Date(TimeService.currentTimeMillis())) + " - request:" + NEW_LINE +
                    NEW_LINE +
                    "  {" + NEW_LINE +
                    "    \"body\" : \"<element>\\n   <key>some_key</key>\\n</element>\"" + NEW_LINE +
                    "  }" + NEW_LINE +
                    NEW_LINE +
                    " didn't match request matcher:" + NEW_LINE +
                    NEW_LINE +
                    "  {" + NEW_LINE +
                    "    \"body\" : {" + NEW_LINE +
                    "      \"type\" : \"XPATH\"," + NEW_LINE +
                    "      \"xpath\" : \"/element[key = 'some_key' and value = 'some_value']\"" + NEW_LINE +
                    "    }" + NEW_LINE +
                    "  }" + NEW_LINE +
                    NEW_LINE +
                    " because:" + NEW_LINE +
                    NEW_LINE +
                    "  method matched" + NEW_LINE +
                    "  path matched" + NEW_LINE +
                    "  body didn't match: " + NEW_LINE +
                    "  " + NEW_LINE +
                    "    xpath match failed expected:" + NEW_LINE +
                    "  " + NEW_LINE +
                    "      /element[key = 'some_key' and value = 'some_value']" + NEW_LINE +
                    "  " + NEW_LINE +
                    "     found:" + NEW_LINE +
                    "  " + NEW_LINE +
                    "      <element>" + NEW_LINE +
                    "         <key>some_key</key>" + NEW_LINE +
                    "      </element>" + NEW_LINE +
                    "  " + NEW_LINE +
                    "     failed because:" + NEW_LINE +
                    "  " + NEW_LINE +
                    "      xpath did not evaluate to truthy" + NEW_LINE +
                    NEW_LINE +
                    NEW_LINE +
                    "------------------------------------" + NEW_LINE +
                    LOG_DATE_FORMAT.format(new Date(TimeService.currentTimeMillis())) + " - retrieving logs that match:" + NEW_LINE +
                    NEW_LINE +
                    "  { }" + NEW_LINE +
                    NEW_LINE
            ));
        } finally {
            matchersFailFast(originalMatchersFailFast);
        }
    }

    @Test
    public void doesNotMatchIncorrectBodyXml() {
        boolean originalMatchersFailFast = matchersFailFast();
        try {
            // given
            String matched = "" +
                "<element>" + NEW_LINE +
                "   <key>some_key</key>" + NEW_LINE +
                "</element>";
            assertFalse(match(request().withBody(xml("" +
                "<element>" + NEW_LINE +
                "   <key>some_key</key>" + NEW_LINE +
                "   <value>some_value</value>" + NEW_LINE +
                "</element>")), request().withBody(matched)));

            // then
            HttpResponse response = httpStateHandler
                .retrieve(
                    request()
                        .withQueryStringParameter("type", "logs")
                );
            assertThat(response.getBodyAsString(), is(
                LOG_DATE_FORMAT.format(new Date(TimeService.currentTimeMillis())) + " - request:" + NEW_LINE +
                    NEW_LINE +
                    "  {" + NEW_LINE +
                    "    \"body\" : \"<element>\\n   <key>some_key</key>\\n</element>\"" + NEW_LINE +
                    "  }" + NEW_LINE +
                    NEW_LINE +
                    " didn't match request matcher:" + NEW_LINE +
                    NEW_LINE +
                    "  {" + NEW_LINE +
                    "    \"body\" : {" + NEW_LINE +
                    "      \"type\" : \"XML\"," + NEW_LINE +
                    "      \"xml\" : \"<element>\\n   <key>some_key</key>\\n   <value>some_value</value>\\n</element>\"" + NEW_LINE +
                    "    }" + NEW_LINE +
                    "  }" + NEW_LINE +
                    NEW_LINE +
                    " because:" + NEW_LINE +
                    NEW_LINE +
                    "  method matched" + NEW_LINE +
                    "  path matched" + NEW_LINE +
                    "  body didn't match: " + NEW_LINE +
                    "  " + NEW_LINE +
                    "    xml match failed expected:" + NEW_LINE +
                    "  " + NEW_LINE +
                    "      <element>" + NEW_LINE +
                    "         <key>some_key</key>" + NEW_LINE +
                    "         <value>some_value</value>" + NEW_LINE +
                    "      </element>" + NEW_LINE +
                    "  " + NEW_LINE +
                    "     found:" + NEW_LINE +
                    "  " + NEW_LINE +
                    "      <element>" + NEW_LINE +
                    "         <key>some_key</key>" + NEW_LINE +
                    "      </element>" + NEW_LINE +
                    "  " + NEW_LINE +
                    "     failed because:" + NEW_LINE +
                    "  " + NEW_LINE +
                    "      Expected child nodelist length '2' but was '1' - comparing <element...> at /element[1] to <element...> at /element[1]" + NEW_LINE +
                    NEW_LINE +
                    NEW_LINE +
                    "------------------------------------" + NEW_LINE +
                    LOG_DATE_FORMAT.format(new Date(TimeService.currentTimeMillis())) + " - retrieving logs that match:" + NEW_LINE +
                    NEW_LINE +
                    "  { }" + NEW_LINE +
                    NEW_LINE
            ));
        } finally {
            matchersFailFast(originalMatchersFailFast);
        }
    }

    @Test
    public void doesNotMatchIncorrectBodyByXmlSchema() {
        boolean originalMatchersFailFast = matchersFailFast();
        try {
            // given
            String matcher = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" + NEW_LINE +
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
                "</xs:schema>";
            assertFalse(match(request().withBody(xmlSchema(matcher)), request().withBody("<?xml version=\"1.0\" encoding=\"utf-8\"?>" + NEW_LINE +
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
                "</notes>")));

            // then
            HttpResponse response = httpStateHandler
                .retrieve(
                    request()
                        .withQueryStringParameter("type", "logs")
                );
            assertThat(response.getBodyAsString(), is(
                LOG_DATE_FORMAT.format(new Date(TimeService.currentTimeMillis())) + " - request:" + NEW_LINE +
                    NEW_LINE +
                    "  {" + NEW_LINE +
                    "    \"body\" : \"<?xml version=\\\"1.0\\\" encoding=\\\"utf-8\\\"?>" + StringEscapeUtils.escapeJava(NEW_LINE) +
                    "<notes>" + StringEscapeUtils.escapeJava(NEW_LINE) +
                    "    <note>" + StringEscapeUtils.escapeJava(NEW_LINE) +
                    "        <to>Bob</to>" + StringEscapeUtils.escapeJava(NEW_LINE) +
                    "        <heading>Reminder</heading>" + StringEscapeUtils.escapeJava(NEW_LINE) +
                    "        <body>Buy Bread</body>" + StringEscapeUtils.escapeJava(NEW_LINE) +
                    "    </note>" + StringEscapeUtils.escapeJava(NEW_LINE) +
                    "    <note>" + StringEscapeUtils.escapeJava(NEW_LINE) +
                    "        <to>Jack</to>" + StringEscapeUtils.escapeJava(NEW_LINE) +
                    "        <from>Jill</from>" + StringEscapeUtils.escapeJava(NEW_LINE) +
                    "        <heading>Reminder</heading>" + StringEscapeUtils.escapeJava(NEW_LINE) +
                    "        <body>Wash Shirts</body>" + StringEscapeUtils.escapeJava(NEW_LINE) +
                    "    </note>" + StringEscapeUtils.escapeJava(NEW_LINE) +
                    "</notes>\"" + NEW_LINE +
                    "  }" + NEW_LINE +
                    NEW_LINE +
                    " didn't match request matcher:" + NEW_LINE +
                    NEW_LINE +
                    "  {" + NEW_LINE +
                    "    \"body\" : {" + NEW_LINE +
                    "      \"type\" : \"XML_SCHEMA\"," + NEW_LINE +
                    "      \"xmlSchema\" : \"<?xml version=\\\"1.0\\\" encoding=\\\"UTF-8\\\"?>" + StringEscapeUtils.escapeJava(NEW_LINE) +
                    "<xs:schema xmlns:xs=\\\"http://www.w3.org/2001/XMLSchema\\\" elementFormDefault=\\\"qualified\\\" attributeFormDefault=\\\"unqualified\\\">" + StringEscapeUtils.escapeJava(NEW_LINE) +
                    "    <!-- XML Schema Generated from XML Document on Wed Jun 28 2017 21:52:45 GMT+0100 (BST) -->" + StringEscapeUtils.escapeJava(NEW_LINE) +
                    "    <!-- with XmlGrid.net Free Online Service http://xmlgrid.net -->" + StringEscapeUtils.escapeJava(NEW_LINE) +
                    "    <xs:element name=\\\"notes\\\">" + StringEscapeUtils.escapeJava(NEW_LINE) +
                    "        <xs:complexType>" + StringEscapeUtils.escapeJava(NEW_LINE) +
                    "            <xs:sequence>" + StringEscapeUtils.escapeJava(NEW_LINE) +
                    "                <xs:element name=\\\"note\\\" maxOccurs=\\\"unbounded\\\">" + StringEscapeUtils.escapeJava(NEW_LINE) +
                    "                    <xs:complexType>" + StringEscapeUtils.escapeJava(NEW_LINE) +
                    "                        <xs:sequence>" + StringEscapeUtils.escapeJava(NEW_LINE) +
                    "                            <xs:element name=\\\"to\\\" minOccurs=\\\"1\\\" maxOccurs=\\\"1\\\" type=\\\"xs:string\\\"></xs:element>" + StringEscapeUtils.escapeJava(NEW_LINE) +
                    "                            <xs:element name=\\\"from\\\" minOccurs=\\\"1\\\" maxOccurs=\\\"1\\\" type=\\\"xs:string\\\"></xs:element>" + StringEscapeUtils.escapeJava(NEW_LINE) +
                    "                            <xs:element name=\\\"heading\\\" minOccurs=\\\"1\\\" maxOccurs=\\\"1\\\" type=\\\"xs:string\\\"></xs:element>" + StringEscapeUtils.escapeJava(NEW_LINE) +
                    "                            <xs:element name=\\\"body\\\" minOccurs=\\\"1\\\" maxOccurs=\\\"1\\\" type=\\\"xs:string\\\"></xs:element>" + StringEscapeUtils.escapeJava(NEW_LINE) +
                    "                        </xs:sequence>" + StringEscapeUtils.escapeJava(NEW_LINE) +
                    "                    </xs:complexType>" + StringEscapeUtils.escapeJava(NEW_LINE) +
                    "                </xs:element>" + StringEscapeUtils.escapeJava(NEW_LINE) +
                    "            </xs:sequence>" + StringEscapeUtils.escapeJava(NEW_LINE) +
                    "        </xs:complexType>" + StringEscapeUtils.escapeJava(NEW_LINE) +
                    "    </xs:element>" + StringEscapeUtils.escapeJava(NEW_LINE) +
                    "</xs:schema>\"" + NEW_LINE +
                    "    }" + NEW_LINE +
                    "  }" + NEW_LINE +
                    NEW_LINE +
                    " because:" + NEW_LINE +
                    NEW_LINE +
                    "  method matched" + NEW_LINE +
                    "  path matched" + NEW_LINE +
                    "  body didn't match: " + NEW_LINE +
                    "  " + NEW_LINE +
                    "    xml schema match failed expected:" + NEW_LINE +
                    "  " + NEW_LINE +
                    "      <?xml version=\"1.0\" encoding=\"UTF-8\"?>" + NEW_LINE +
                    "      <xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\" elementFormDefault=\"qualified\" attributeFormDefault=\"unqualified\">" + NEW_LINE +
                    "          <!-- XML Schema Generated from XML Document on Wed Jun 28 2017 21:52:45 GMT+0100 (BST) -->" + NEW_LINE +
                    "          <!-- with XmlGrid.net Free Online Service http://xmlgrid.net -->" + NEW_LINE +
                    "          <xs:element name=\"notes\">" + NEW_LINE +
                    "              <xs:complexType>" + NEW_LINE +
                    "                  <xs:sequence>" + NEW_LINE +
                    "                      <xs:element name=\"note\" maxOccurs=\"unbounded\">" + NEW_LINE +
                    "                          <xs:complexType>" + NEW_LINE +
                    "                              <xs:sequence>" + NEW_LINE +
                    "                                  <xs:element name=\"to\" minOccurs=\"1\" maxOccurs=\"1\" type=\"xs:string\"></xs:element>" + NEW_LINE +
                    "                                  <xs:element name=\"from\" minOccurs=\"1\" maxOccurs=\"1\" type=\"xs:string\"></xs:element>" + NEW_LINE +
                    "                                  <xs:element name=\"heading\" minOccurs=\"1\" maxOccurs=\"1\" type=\"xs:string\"></xs:element>" + NEW_LINE +
                    "                                  <xs:element name=\"body\" minOccurs=\"1\" maxOccurs=\"1\" type=\"xs:string\"></xs:element>" + NEW_LINE +
                    "                              </xs:sequence>" + NEW_LINE +
                    "                          </xs:complexType>" + NEW_LINE +
                    "                      </xs:element>" + NEW_LINE +
                    "                  </xs:sequence>" + NEW_LINE +
                    "              </xs:complexType>" + NEW_LINE +
                    "          </xs:element>" + NEW_LINE +
                    "      </xs:schema>" + NEW_LINE +
                    "  " + NEW_LINE +
                    "     found:" + NEW_LINE +
                    "  " + NEW_LINE +
                    "      <?xml version=\"1.0\" encoding=\"utf-8\"?>" + NEW_LINE +
                    "      <notes>" + NEW_LINE +
                    "          <note>" + NEW_LINE +
                    "              <to>Bob</to>" + NEW_LINE +
                    "              <heading>Reminder</heading>" + NEW_LINE +
                    "              <body>Buy Bread</body>" + NEW_LINE +
                    "          </note>" + NEW_LINE +
                    "          <note>" + NEW_LINE +
                    "              <to>Jack</to>" + NEW_LINE +
                    "              <from>Jill</from>" + NEW_LINE +
                    "              <heading>Reminder</heading>" + NEW_LINE +
                    "              <body>Wash Shirts</body>" + NEW_LINE +
                    "          </note>" + NEW_LINE +
                    "      </notes>" + NEW_LINE +
                    "  " + NEW_LINE +
                    "     failed because:" + NEW_LINE +
                    "  " + NEW_LINE +
                    "      cvc-complex-type.2.4.a: Invalid content was found starting with element 'heading'. One of '{from}' is expected." + NEW_LINE +
                    NEW_LINE +
                    NEW_LINE +
                    "------------------------------------" + NEW_LINE +
                    LOG_DATE_FORMAT.format(new Date(TimeService.currentTimeMillis())) + " - retrieving logs that match:" + NEW_LINE +
                    NEW_LINE +
                    "  { }" + NEW_LINE +
                    NEW_LINE
            ));
        } finally {
            matchersFailFast(originalMatchersFailFast);
        }
    }

    @Test
    public void doesNotMatchIncorrectJSONBody() {
        boolean originalMatchersFailFast = matchersFailFast();
        try {
            // given
            String matched = "" +
                "{" + NEW_LINE +
                "   \"some_incorrect_field\": \"some_value\"," + NEW_LINE +
                "   \"some_other_field\": \"some_other_value\"" + NEW_LINE +
                "}";
            assertFalse(match(request().withBody(json("{ \"some_field\": \"some_value\" }")), request().withBody(matched)));

            // then
            HttpResponse response = httpStateHandler
                .retrieve(
                    request()
                        .withQueryStringParameter("type", "logs")
                );
            assertThat(response.getBodyAsString(), is(
                LOG_DATE_FORMAT.format(new Date(TimeService.currentTimeMillis())) + " - request:" + NEW_LINE +
                    NEW_LINE +
                    "  {" + NEW_LINE +
                    "    \"body\" : \"{" + StringEscapeUtils.escapeJava(NEW_LINE) +
                    "   \\\"some_incorrect_field\\\": \\\"some_value\\\"," + StringEscapeUtils.escapeJava(NEW_LINE) +
                    "   \\\"some_other_field\\\": \\\"some_other_value\\\"" + StringEscapeUtils.escapeJava(NEW_LINE) +
                    "}\"" + NEW_LINE +
                    "  }" + NEW_LINE +
                    NEW_LINE +
                    " didn't match request matcher:" + NEW_LINE +
                    NEW_LINE +
                    "  {" + NEW_LINE +
                    "    \"body\" : {" + NEW_LINE +
                    "      \"type\" : \"JSON\"," + NEW_LINE +
                    "      \"json\" : \"{ \\\"some_field\\\": \\\"some_value\\\" }\"" + NEW_LINE +
                    "    }" + NEW_LINE +
                    "  }" + NEW_LINE +
                    NEW_LINE +
                    " because:" + NEW_LINE +
                    NEW_LINE +
                    "  method matched" + NEW_LINE +
                    "  path matched" + NEW_LINE +
                    "  body didn't match: " + NEW_LINE +
                    "  " + NEW_LINE +
                    "    json match failed expected:" + NEW_LINE +
                    "  " + NEW_LINE +
                    "      { \"some_field\": \"some_value\" }" + NEW_LINE +
                    "  " + NEW_LINE +
                    "     found:" + NEW_LINE +
                    "  " + NEW_LINE +
                    "      {" + NEW_LINE +
                    "         \"some_incorrect_field\": \"some_value\"," + NEW_LINE +
                    "         \"some_other_field\": \"some_other_value\"" + NEW_LINE +
                    "      }" + NEW_LINE +
                    "  " + NEW_LINE +
                    "     failed because:" + NEW_LINE +
                    "  " + NEW_LINE +
                    "      missing element at \"some_field\"" + NEW_LINE +
                    NEW_LINE +
                    NEW_LINE +
                    "------------------------------------" + NEW_LINE +
                    LOG_DATE_FORMAT.format(new Date(TimeService.currentTimeMillis())) + " - retrieving logs that match:" + NEW_LINE +
                    NEW_LINE +
                    "  { }" + NEW_LINE +
                    NEW_LINE
            ));
        } finally {
            matchersFailFast(originalMatchersFailFast);
        }
    }

    @Test
    public void doesNotMatchIncorrectJSONSchemaBody() {
        boolean originalMatchersFailFast = matchersFailFast();
        try {
            // given
            String matched = "" +
                "{" + NEW_LINE +
                "    \"id\": 1," + NEW_LINE +
                "    \"name\": \"A green door\"," + NEW_LINE +
                "    \"price\": 12.50," + NEW_LINE +
                "    \"tags\": []" + NEW_LINE +
                "}";
            assertFalse(match(request().withBody(jsonSchema("{" + NEW_LINE +
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
                "}")), request().withBody(matched)));

            // then
            HttpResponse response = httpStateHandler
                .retrieve(
                    request()
                        .withQueryStringParameter("type", "logs")
                );
            assertThat(response.getBodyAsString(), is(
                LOG_DATE_FORMAT.format(new Date(TimeService.currentTimeMillis())) + " - request:" + NEW_LINE +
                    NEW_LINE +
                    "  {" + NEW_LINE +
                    "    \"body\" : \"{" + StringEscapeUtils.escapeJava(NEW_LINE) +
                    "    \\\"id\\\": 1," + StringEscapeUtils.escapeJava(NEW_LINE) +
                    "    \\\"name\\\": \\\"A green door\\\"," + StringEscapeUtils.escapeJava(NEW_LINE) +
                    "    \\\"price\\\": 12.50," + StringEscapeUtils.escapeJava(NEW_LINE) +
                    "    \\\"tags\\\": []" + StringEscapeUtils.escapeJava(NEW_LINE) +
                    "}\"" + NEW_LINE +
                    "  }" + NEW_LINE +
                    NEW_LINE +
                    " didn't match request matcher:" + NEW_LINE +
                    NEW_LINE +
                    "  {" + NEW_LINE +
                    "    \"body\" : {" + NEW_LINE +
                    "      \"type\" : \"JSON_SCHEMA\"," + NEW_LINE +
                    "      \"jsonSchema\" : \"{" + StringEscapeUtils.escapeJava(NEW_LINE) +
                    "    \\\"$schema\\\": \\\"http://json-schema.org/draft-04/schema#\\\"," + StringEscapeUtils.escapeJava(NEW_LINE) +
                    "    \\\"title\\\": \\\"Product\\\"," + StringEscapeUtils.escapeJava(NEW_LINE) +
                    "    \\\"description\\\": \\\"A product from Acme's catalog\\\"," + StringEscapeUtils.escapeJava(NEW_LINE) +
                    "    \\\"type\\\": \\\"object\\\"," + StringEscapeUtils.escapeJava(NEW_LINE) +
                    "    \\\"properties\\\": {" + StringEscapeUtils.escapeJava(NEW_LINE) +
                    "        \\\"id\\\": {" + StringEscapeUtils.escapeJava(NEW_LINE) +
                    "            \\\"description\\\": \\\"The unique identifier for a product\\\"," + StringEscapeUtils.escapeJava(NEW_LINE) +
                    "            \\\"type\\\": \\\"integer\\\"" + StringEscapeUtils.escapeJava(NEW_LINE) +
                    "        }," + StringEscapeUtils.escapeJava(NEW_LINE) +
                    "        \\\"name\\\": {" + StringEscapeUtils.escapeJava(NEW_LINE) +
                    "            \\\"description\\\": \\\"Name of the product\\\"," + StringEscapeUtils.escapeJava(NEW_LINE) +
                    "            \\\"type\\\": \\\"string\\\"" + StringEscapeUtils.escapeJava(NEW_LINE) +
                    "        }," + StringEscapeUtils.escapeJava(NEW_LINE) +
                    "        \\\"price\\\": {" + StringEscapeUtils.escapeJava(NEW_LINE) +
                    "            \\\"type\\\": \\\"number\\\"," + StringEscapeUtils.escapeJava(NEW_LINE) +
                    "            \\\"minimum\\\": 0," + StringEscapeUtils.escapeJava(NEW_LINE) +
                    "            \\\"exclusiveMinimum\\\": true" + StringEscapeUtils.escapeJava(NEW_LINE) +
                    "        }," + StringEscapeUtils.escapeJava(NEW_LINE) +
                    "        \\\"tags\\\": {" + StringEscapeUtils.escapeJava(NEW_LINE) +
                    "            \\\"type\\\": \\\"array\\\"," + StringEscapeUtils.escapeJava(NEW_LINE) +
                    "            \\\"items\\\": {" + StringEscapeUtils.escapeJava(NEW_LINE) +
                    "                \\\"type\\\": \\\"string\\\"" + StringEscapeUtils.escapeJava(NEW_LINE) +
                    "            }," + StringEscapeUtils.escapeJava(NEW_LINE) +
                    "            \\\"minItems\\\": 1," + StringEscapeUtils.escapeJava(NEW_LINE) +
                    "            \\\"uniqueItems\\\": true" + StringEscapeUtils.escapeJava(NEW_LINE) +
                    "        }" + StringEscapeUtils.escapeJava(NEW_LINE) +
                    "    }," + StringEscapeUtils.escapeJava(NEW_LINE) +
                    "    \\\"required\\\": [\\\"id\\\", \\\"name\\\", \\\"price\\\"]" + StringEscapeUtils.escapeJava(NEW_LINE) +
                    "}\"" + NEW_LINE +
                    "    }" + NEW_LINE +
                    "  }" + NEW_LINE +
                    NEW_LINE +
                    " because:" + NEW_LINE +
                    NEW_LINE +
                    "  method matched" + NEW_LINE +
                    "  path matched" + NEW_LINE +
                    "  body didn't match: " + NEW_LINE +
                    "  " + NEW_LINE +
                    "    json schema match failed expected:" + NEW_LINE +
                    "  " + NEW_LINE +
                    "      {" + NEW_LINE +
                    "          \"$schema\": \"http://json-schema.org/draft-04/schema#\"," + NEW_LINE +
                    "          \"title\": \"Product\"," + NEW_LINE +
                    "          \"description\": \"A product from Acme's catalog\"," + NEW_LINE +
                    "          \"type\": \"object\"," + NEW_LINE +
                    "          \"properties\": {" + NEW_LINE +
                    "              \"id\": {" + NEW_LINE +
                    "                  \"description\": \"The unique identifier for a product\"," + NEW_LINE +
                    "                  \"type\": \"integer\"" + NEW_LINE +
                    "              }," + NEW_LINE +
                    "              \"name\": {" + NEW_LINE +
                    "                  \"description\": \"Name of the product\"," + NEW_LINE +
                    "                  \"type\": \"string\"" + NEW_LINE +
                    "              }," + NEW_LINE +
                    "              \"price\": {" + NEW_LINE +
                    "                  \"type\": \"number\"," + NEW_LINE +
                    "                  \"minimum\": 0," + NEW_LINE +
                    "                  \"exclusiveMinimum\": true" + NEW_LINE +
                    "              }," + NEW_LINE +
                    "              \"tags\": {" + NEW_LINE +
                    "                  \"type\": \"array\"," + NEW_LINE +
                    "                  \"items\": {" + NEW_LINE +
                    "                      \"type\": \"string\"" + NEW_LINE +
                    "                  }," + NEW_LINE +
                    "                  \"minItems\": 1," + NEW_LINE +
                    "                  \"uniqueItems\": true" + NEW_LINE +
                    "              }" + NEW_LINE +
                    "          }," + NEW_LINE +
                    "          \"required\": [\"id\", \"name\", \"price\"]" + NEW_LINE +
                    "      }" + NEW_LINE +
                    "  " + NEW_LINE +
                    "     found:" + NEW_LINE +
                    "  " + NEW_LINE +
                    "      {" + NEW_LINE +
                    "          \"id\": 1," + NEW_LINE +
                    "          \"name\": \"A green door\"," + NEW_LINE +
                    "          \"price\": 12.50," + NEW_LINE +
                    "          \"tags\": []" + NEW_LINE +
                    "      }" + NEW_LINE +
                    "  " + NEW_LINE +
                    "     failed because:" + NEW_LINE +
                    "  " + NEW_LINE +
                    "      1 error:" + NEW_LINE +
                    "       - array is too short: must have at least 1 elements but instance has 0 elements for field \"/tags\"" + NEW_LINE +
                    NEW_LINE +
                    NEW_LINE +
                    "------------------------------------" + NEW_LINE +
                    LOG_DATE_FORMAT.format(new Date(TimeService.currentTimeMillis())) + " - retrieving logs that match:" + NEW_LINE +
                    NEW_LINE +
                    "  { }" + NEW_LINE +
                    NEW_LINE
            ));
        } finally {
            matchersFailFast(originalMatchersFailFast);
        }
    }

    @Test
    public void doesNotMatchIncorrectBodyJsonPath() {
        boolean originalMatchersFailFast = matchersFailFast();
        try {
            // given
            String matched = "" +
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
                "}";
            assertFalse(match(request().withBody(jsonPath("$..book[?(@.price > $['expensive'])]")), request().withBody(matched)));

            // then
            HttpResponse response = httpStateHandler
                .retrieve(
                    request()
                        .withQueryStringParameter("type", "logs")
                );
            assertThat(response.getBodyAsString(), is(
                LOG_DATE_FORMAT.format(new Date(TimeService.currentTimeMillis())) + " - request:" + NEW_LINE +
                    NEW_LINE +
                    "  {" + NEW_LINE +
                    "    \"body\" : \"{" + StringEscapeUtils.escapeJava(NEW_LINE) +
                    "    \\\"store\\\": {" + StringEscapeUtils.escapeJava(NEW_LINE) +
                    "        \\\"book\\\": [" + StringEscapeUtils.escapeJava(NEW_LINE) +
                    "            {" + StringEscapeUtils.escapeJava(NEW_LINE) +
                    "                \\\"category\\\": \\\"reference\\\"," + StringEscapeUtils.escapeJava(NEW_LINE) +
                    "                \\\"author\\\": \\\"Nigel Rees\\\"," + StringEscapeUtils.escapeJava(NEW_LINE) +
                    "                \\\"title\\\": \\\"Sayings of the Century\\\"," + StringEscapeUtils.escapeJava(NEW_LINE) +
                    "                \\\"price\\\": 8.95" + StringEscapeUtils.escapeJava(NEW_LINE) +
                    "            }," + StringEscapeUtils.escapeJava(NEW_LINE) +
                    "            {" + StringEscapeUtils.escapeJava(NEW_LINE) +
                    "                \\\"category\\\": \\\"fiction\\\"," + StringEscapeUtils.escapeJava(NEW_LINE) +
                    "                \\\"author\\\": \\\"Herman Melville\\\"," + StringEscapeUtils.escapeJava(NEW_LINE) +
                    "                \\\"title\\\": \\\"Moby Dick\\\"," + StringEscapeUtils.escapeJava(NEW_LINE) +
                    "                \\\"isbn\\\": \\\"0-553-21311-3\\\"," + StringEscapeUtils.escapeJava(NEW_LINE) +
                    "                \\\"price\\\": 8.99" + StringEscapeUtils.escapeJava(NEW_LINE) +
                    "            }" + StringEscapeUtils.escapeJava(NEW_LINE) +
                    "        ]," + StringEscapeUtils.escapeJava(NEW_LINE) +
                    "        \\\"bicycle\\\": {" + StringEscapeUtils.escapeJava(NEW_LINE) +
                    "            \\\"color\\\": \\\"red\\\"," + StringEscapeUtils.escapeJava(NEW_LINE) +
                    "            \\\"price\\\": 19.95" + StringEscapeUtils.escapeJava(NEW_LINE) +
                    "        }" + StringEscapeUtils.escapeJava(NEW_LINE) +
                    "    }," + StringEscapeUtils.escapeJava(NEW_LINE) +
                    "    \\\"expensive\\\": 10" + StringEscapeUtils.escapeJava(NEW_LINE) +
                    "}\"" + NEW_LINE +
                    "  }" + NEW_LINE +
                    NEW_LINE +
                    " didn't match request matcher:" + NEW_LINE +
                    NEW_LINE +
                    "  {" + NEW_LINE +
                    "    \"body\" : {" + NEW_LINE +
                    "      \"type\" : \"JSON_PATH\"," + NEW_LINE +
                    "      \"jsonPath\" : \"$..book[?(@.price > $['expensive'])]\"" + NEW_LINE +
                    "    }" + NEW_LINE +
                    "  }" + NEW_LINE +
                    NEW_LINE +
                    " because:" + NEW_LINE +
                    NEW_LINE +
                    "  method matched" + NEW_LINE +
                    "  path matched" + NEW_LINE +
                    "  body didn't match: " + NEW_LINE +
                    "  " + NEW_LINE +
                    "    json path match failed expected:" + NEW_LINE +
                    "  " + NEW_LINE +
                    "      $..book[?(@.price > $['expensive'])]" + NEW_LINE +
                    "  " + NEW_LINE +
                    "     found:" + NEW_LINE +
                    "  " + NEW_LINE +
                    "      {" + NEW_LINE +
                    "          \"store\": {" + NEW_LINE +
                    "              \"book\": [" + NEW_LINE +
                    "                  {" + NEW_LINE +
                    "                      \"category\": \"reference\"," + NEW_LINE +
                    "                      \"author\": \"Nigel Rees\"," + NEW_LINE +
                    "                      \"title\": \"Sayings of the Century\"," + NEW_LINE +
                    "                      \"price\": 8.95" + NEW_LINE +
                    "                  }," + NEW_LINE +
                    "                  {" + NEW_LINE +
                    "                      \"category\": \"fiction\"," + NEW_LINE +
                    "                      \"author\": \"Herman Melville\"," + NEW_LINE +
                    "                      \"title\": \"Moby Dick\"," + NEW_LINE +
                    "                      \"isbn\": \"0-553-21311-3\"," + NEW_LINE +
                    "                      \"price\": 8.99" + NEW_LINE +
                    "                  }" + NEW_LINE +
                    "              ]," + NEW_LINE +
                    "              \"bicycle\": {" + NEW_LINE +
                    "                  \"color\": \"red\"," + NEW_LINE +
                    "                  \"price\": 19.95" + NEW_LINE +
                    "              }" + NEW_LINE +
                    "          }," + NEW_LINE +
                    "          \"expensive\": 10" + NEW_LINE +
                    "      }" + NEW_LINE +
                    "  " + NEW_LINE +
                    "     failed because:" + NEW_LINE +
                    "  " + NEW_LINE +
                    "      json path did not evaluate to truthy" + NEW_LINE +
                    NEW_LINE +
                    NEW_LINE +
                    "------------------------------------" + NEW_LINE +
                    LOG_DATE_FORMAT.format(new Date(TimeService.currentTimeMillis())) + " - retrieving logs that match:" + NEW_LINE +
                    NEW_LINE +
                    "  { }" + NEW_LINE +
                    NEW_LINE
            ));
        } finally {
            matchersFailFast(originalMatchersFailFast);
        }
    }

    @Test
    public void doesNotMatchIncorrectBinaryBody() {
        boolean originalMatchersFailFast = matchersFailFast();
        try {
            // given
            byte[] matched = "some other binary value that is much much longer so that the binary data is wrapped".getBytes(UTF_8);
            assertFalse(match(request().withBody(binary("some binary value that is also long and wraps as little".getBytes(UTF_8))), request().withBody(binary(matched))));

            // then
            HttpResponse response = httpStateHandler
                .retrieve(
                    request()
                        .withQueryStringParameter("type", "logs")
                );
            assertThat(response.getBodyAsString(), is(
                LOG_DATE_FORMAT.format(new Date(TimeService.currentTimeMillis())) + " - request:" + NEW_LINE +
                    NEW_LINE +
                    "  {" + NEW_LINE +
                    "    \"body\" : \"c29tZSBvdGhlciBiaW5hcnkgdmFsdWUgdGhhdCBpcyBtdWNoIG11Y2ggbG9uZ2VyIHNvIHRoYXQgdGhlIGJpbmFyeSBkYXRhIGlzIHdyYXBwZWQ=\"" + NEW_LINE +
                    "  }" + NEW_LINE +
                    NEW_LINE +
                    " didn't match request matcher:" + NEW_LINE +
                    NEW_LINE +
                    "  {" + NEW_LINE +
                    "    \"body\" : {" + NEW_LINE +
                    "      \"type\" : \"BINARY\"," + NEW_LINE +
                    "      \"base64Bytes\" : \"c29tZSBiaW5hcnkgdmFsdWUgdGhhdCBpcyBhbHNvIGxvbmcgYW5kIHdyYXBzIGFzIGxpdHRsZQ==\"" + NEW_LINE +
                    "    }" + NEW_LINE +
                    "  }" + NEW_LINE +
                    NEW_LINE +
                    " because:" + NEW_LINE +
                    NEW_LINE +
                    "  method matched" + NEW_LINE +
                    "  path matched" + NEW_LINE +
                    "  body didn't match: " + NEW_LINE +
                    "  " + NEW_LINE +
                    "    binary match failed expected:" + NEW_LINE +
                    "  " + NEW_LINE +
                    "      base64:" + NEW_LINE +
                    "        c29tZSBiaW5hcnkgdmFsdWUgdGhhdCBpcyBhbHNvIGxvbmcgYW5kIHdyYXBzIGFz" + NEW_LINE +
                    "        IGxpdHRsZQ==" + NEW_LINE +
                    "      hex:\n" +
                    "        736f6d652062696e6172792076616c7565207468617420697320616c736f206c" + NEW_LINE +
                    "        6f6e6720616e64207772617073206173206c6974746c65" + NEW_LINE +
                    "  " + NEW_LINE +
                    "     found:" + NEW_LINE +
                    "  " + NEW_LINE +
                    "      base64:" + NEW_LINE +
                    "        c29tZSBvdGhlciBiaW5hcnkgdmFsdWUgdGhhdCBpcyBtdWNoIG11Y2ggbG9uZ2Vy" + NEW_LINE +
                    "        IHNvIHRoYXQgdGhlIGJpbmFyeSBkYXRhIGlzIHdyYXBwZWQ=" + NEW_LINE +
                    "      hex:" + NEW_LINE +
                    "        736f6d65206f746865722062696e6172792076616c7565207468617420697320" + NEW_LINE +
                    "        6d756368206d756368206c6f6e67657220736f2074686174207468652062696e" + NEW_LINE +
                    "        61727920646174612069732077726170706564" + NEW_LINE +
                    NEW_LINE +
                    NEW_LINE +
                    "------------------------------------" + NEW_LINE +
                    LOG_DATE_FORMAT.format(new Date(TimeService.currentTimeMillis())) + " - retrieving logs that match:" + NEW_LINE +
                    NEW_LINE +
                    "  { }" + NEW_LINE +
                    NEW_LINE
            ));
        } finally {
            matchersFailFast(originalMatchersFailFast);
        }
    }

    @Test
    public void doesNotMatchIncorrectHeaderName() {
        boolean originalMatchersFailFast = matchersFailFast();
        try {
            // given
            assertFalse(match(request().withHeaders(new Header("name", "value")), request().withHeaders(new Header("name1", "value"))));

            // then
            HttpResponse response = httpStateHandler
                .retrieve(
                    request()
                        .withQueryStringParameter("type", "logs")
                );
            assertThat(response.getBodyAsString(), is(
                LOG_DATE_FORMAT.format(new Date(TimeService.currentTimeMillis())) + " - request:" + NEW_LINE +
                    NEW_LINE +
                    "  {" + NEW_LINE +
                    "    \"headers\" : {" + NEW_LINE +
                    "      \"name1\" : [ \"value\" ]" + NEW_LINE +
                    "    }" + NEW_LINE +
                    "  }" + NEW_LINE +
                    NEW_LINE +
                    " didn't match request matcher:" + NEW_LINE +
                    NEW_LINE +
                    "  {" + NEW_LINE +
                    "    \"headers\" : {" + NEW_LINE +
                    "      \"name\" : [ \"value\" ]" + NEW_LINE +
                    "    }" + NEW_LINE +
                    "  }" + NEW_LINE +
                    NEW_LINE +
                    " because:" + NEW_LINE +
                    NEW_LINE +
                    "  method matched" + NEW_LINE +
                    "  path matched" + NEW_LINE +
                    "  body matched" + NEW_LINE +
                    "  headers didn't match: " + NEW_LINE +
                    "  " + NEW_LINE +
                    "    multimap subset match failed expected:" + NEW_LINE +
                    "  " + NEW_LINE +
                    "      {" + NEW_LINE +
                    "        \"name\" : [ \"value\" ]" + NEW_LINE +
                    "      }" + NEW_LINE +
                    "  " + NEW_LINE +
                    "     found:" + NEW_LINE +
                    "  " + NEW_LINE +
                    "      {" + NEW_LINE +
                    "        \"name1\" : [ \"value\" ]" + NEW_LINE +
                    "      }" + NEW_LINE +
                    "  " + NEW_LINE +
                    "     failed because:" + NEW_LINE +
                    "  " + NEW_LINE +
                    "      multimap is not a subset" + NEW_LINE +
                    NEW_LINE +
                    NEW_LINE +
                    "------------------------------------" + NEW_LINE +
                    LOG_DATE_FORMAT.format(new Date(TimeService.currentTimeMillis())) + " - retrieving logs that match:" + NEW_LINE +
                    NEW_LINE +
                    "  { }" + NEW_LINE +
                    NEW_LINE
            ));
        } finally {
            matchersFailFast(originalMatchersFailFast);
        }
    }

    @Test
    public void doesNotMatchIncorrectHeaderValueRegex() {
        boolean originalMatchersFailFast = matchersFailFast();
        try {
            // given
            assertFalse(match(request().withHeaders(new Header("name", "[0-9]{0,100}")), request().withHeaders(new Header("name", "value1"))));

            // then
            HttpResponse response = httpStateHandler
                .retrieve(
                    request()
                        .withQueryStringParameter("type", "logs")
                );
            assertThat(response.getBodyAsString(), is(
                LOG_DATE_FORMAT.format(new Date(TimeService.currentTimeMillis())) + " - request:" + NEW_LINE +
                    NEW_LINE +
                    "  {" + NEW_LINE +
                    "    \"headers\" : {" + NEW_LINE +
                    "      \"name\" : [ \"value1\" ]" + NEW_LINE +
                    "    }" + NEW_LINE +
                    "  }" + NEW_LINE +
                    NEW_LINE +
                    " didn't match request matcher:" + NEW_LINE +
                    NEW_LINE +
                    "  {" + NEW_LINE +
                    "    \"headers\" : {" + NEW_LINE +
                    "      \"name\" : [ \"[0-9]{0,100}\" ]" + NEW_LINE +
                    "    }" + NEW_LINE +
                    "  }" + NEW_LINE +
                    NEW_LINE +
                    " because:" + NEW_LINE +
                    NEW_LINE +
                    "  method matched" + NEW_LINE +
                    "  path matched" + NEW_LINE +
                    "  body matched" + NEW_LINE +
                    "  headers didn't match: " + NEW_LINE +
                    "  " + NEW_LINE +
                    "    multimap subset match failed expected:" + NEW_LINE +
                    "  " + NEW_LINE +
                    "      {" + NEW_LINE +
                    "        \"name\" : [ \"[0-9]{0,100}\" ]" + NEW_LINE +
                    "      }" + NEW_LINE +
                    "  " + NEW_LINE +
                    "     found:" + NEW_LINE +
                    "  " + NEW_LINE +
                    "      {" + NEW_LINE +
                    "        \"name\" : [ \"value1\" ]" + NEW_LINE +
                    "      }" + NEW_LINE +
                    "  " + NEW_LINE +
                    "     failed because:" + NEW_LINE +
                    "  " + NEW_LINE +
                    "      multimap is not a subset" + NEW_LINE +
                    NEW_LINE +
                    NEW_LINE +
                    "------------------------------------" + NEW_LINE +
                    LOG_DATE_FORMAT.format(new Date(TimeService.currentTimeMillis())) + " - retrieving logs that match:" + NEW_LINE +
                    NEW_LINE +
                    "  { }" + NEW_LINE +
                    NEW_LINE
            ));
        } finally {
            matchersFailFast(originalMatchersFailFast);
        }
    }

    @Test
    public void doesNotMatchIncorrectCookieValue() {
        boolean originalMatchersFailFast = matchersFailFast();
        try {
            // given
            assertFalse(match(request().withCookies(new Cookie("name", "value")), request().withCookies(new Cookie("name", "value1"))));

            // then
            HttpResponse response = httpStateHandler
                .retrieve(
                    request()
                        .withQueryStringParameter("type", "logs")
                );
            assertThat(response.getBodyAsString(), is(
                LOG_DATE_FORMAT.format(new Date(TimeService.currentTimeMillis())) + " - request:" + NEW_LINE +
                    NEW_LINE +
                    "  {" + NEW_LINE +
                    "    \"cookies\" : {" + NEW_LINE +
                    "      \"name\" : \"value1\"" + NEW_LINE +
                    "    }" + NEW_LINE +
                    "  }" + NEW_LINE +
                    NEW_LINE +
                    " didn't match request matcher:" + NEW_LINE +
                    NEW_LINE +
                    "  {" + NEW_LINE +
                    "    \"cookies\" : {" + NEW_LINE +
                    "      \"name\" : \"value\"" + NEW_LINE +
                    "    }" + NEW_LINE +
                    "  }" + NEW_LINE +
                    NEW_LINE +
                    " because:" + NEW_LINE +
                    NEW_LINE +
                    "  method matched" + NEW_LINE +
                    "  path matched" + NEW_LINE +
                    "  body matched" + NEW_LINE +
                    "  headers matched" + NEW_LINE +
                    "  cookies didn't match: " + NEW_LINE +
                    "  " + NEW_LINE +
                    "    map subset match failed expected:" + NEW_LINE +
                    "  " + NEW_LINE +
                    "      {" + NEW_LINE +
                    "        \"name\" : \"value\"" + NEW_LINE +
                    "      }" + NEW_LINE +
                    "  " + NEW_LINE +
                    "     found:" + NEW_LINE +
                    "  " + NEW_LINE +
                    "      {" + NEW_LINE +
                    "        \"name\" : \"value1\"" + NEW_LINE +
                    "      }" + NEW_LINE +
                    "  " + NEW_LINE +
                    "     failed because:" + NEW_LINE +
                    "  " + NEW_LINE +
                    "      map is not a subset" + NEW_LINE +
                    NEW_LINE +
                    NEW_LINE +
                    "------------------------------------" + NEW_LINE +
                    LOG_DATE_FORMAT.format(new Date(TimeService.currentTimeMillis())) + " - retrieving logs that match:" + NEW_LINE +
                    NEW_LINE +
                    "  { }" + NEW_LINE +
                    NEW_LINE
            ));
        } finally {
            matchersFailFast(originalMatchersFailFast);
        }
    }
}
