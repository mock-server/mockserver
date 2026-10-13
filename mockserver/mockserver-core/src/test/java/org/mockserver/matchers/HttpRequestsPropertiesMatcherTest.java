package org.mockserver.matchers;

import com.google.common.collect.ImmutableMap;
import org.junit.Test;
import org.mockserver.configuration.Configuration;
import org.mockserver.file.FileReader;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.mock.Expectation;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.OpenAPIDefinition;
import org.mockserver.uuid.UUIDService;

import java.util.ArrayList;
import java.util.UUID;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsNull.nullValue;
import static org.hamcrest.core.StringContains.containsString;
import static org.hamcrest.core.StringStartsWith.startsWith;
import static org.mockserver.character.Character.NEW_LINE;
import static org.mockserver.configuration.Configuration.configuration;
import static org.mockserver.matchers.MatchDifference.Field.*;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.JsonBody.json;
import static org.mockserver.model.StringBody.exact;
import static org.mockserver.model.XmlBody.xml;
import static org.slf4j.event.Level.ERROR;
import static org.slf4j.event.Level.WARN;
import static org.junit.Assert.fail;

/**
 * @author jamesdbloom
 */
public class HttpRequestsPropertiesMatcherTest {

    // Pin matchersFailFast/detailedMatchFailures on this instance (both default true) so the
    // field-difference assertions below do not read the mutable global ConfigurationProperties
    // defaults. Otherwise a concurrently-running test that flips the global matchersFailFast to
    // false makes every field (not just the first mismatch) record a difference, intermittently
    // breaking the "only PATH differs" assertions (parallel-state contamination, see commit log).
    private final Configuration configuration = configuration().matchersFailFast(true).detailedMatchFailures(true);
    private final MockServerLogger mockServerLogger = new MockServerLogger(HttpRequestsPropertiesMatcherTest.class);

    /**
     * Test Pattern For Non Body Fields:
     * - method
     * - basic
     * - path
     * - basic
     * - with path parameter
     * - parameters (see: https://swagger.io/docs/specification/describing-parameters/)
     * - path
     * - query string
     * - header
     * - cookie
     * Then:
     * - required field
     * - allowReserved field
     * - allowEmptyValue field -> nullable: true in json schema
     * - common parameter for all methods of path
     * - common parameter for all paths
     * - override of common all paths at all methods
     * - override of common all paths at operation
     * - override of common all methods at operation
     * - TODO(jamesdbloom) The label and matrix styles are sometimes used with partial path parameters, such as /users{id}, because the parameter values get prefixed.
     * <p>
     * Test Pattern For Body Fields:
     * - requestBody (see: https://swagger.io/docs/specification/describing-request-body/)
     * - json (i.e. application/json)
     * - text (i.e. text/plain)
     * - xml (i.e. application/xml)
     * - form parameters (i.e. application/x-www-form-urlencoded)
     * - multipart form data (i.e. multipart/form-data)
     * Then:
     * - required: false (or missing)
     * - media type range
     * - media type default
     * - required field
     * - encoding (application/x-www-form-urlencoded)
     * <p>
     * Test Pattern For Security Handlers:
     * - basic
     * - bearer
     * - api-key
     * - openIdConnect
     * - oauth2
     * Then:
     * - operation level of spec
     * - multi schemes
     * - override of root level at operation level
     */

    // METHOD
    @Test
    public void shouldMatchByMethod() {
        // given
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(configuration, mockServerLogger);

        // when
        httpRequestsPropertiesMatcher.update(new Expectation(
            new OpenAPIDefinition()
                .withSpecUrlOrPayload("---" + NEW_LINE +
                    "openapi: 3.0.0" + NEW_LINE +
                    "paths:" + NEW_LINE +
                    "  \"/somePath\":" + NEW_LINE +
                    "    get:" + NEW_LINE +
                    "      operationId: someOperation" + NEW_LINE)
        ));

        // then
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withMethod("GET")
        ), is(true));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withMethod("POST")
        ), is(false));
    }

    @Test
    public void shouldMatchByMethodWithOperationId() {
        // given
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(configuration, mockServerLogger);

        // when
        httpRequestsPropertiesMatcher.update(new Expectation(
            new OpenAPIDefinition()
                .withSpecUrlOrPayload("---" + NEW_LINE +
                    "openapi: 3.0.0" + NEW_LINE +
                    "paths:" + NEW_LINE +
                    "  \"/somePath\":" + NEW_LINE +
                    "    get:" + NEW_LINE +
                    "      operationId: someOperation" + NEW_LINE)
                .withOperationId("someOperation")
        ));

        // then
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withMethod("GET")
        ), is(true));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withMethod("POST")
        ), is(false));
    }

    // PATH

    @Test
    public void shouldMatchByPath() {
        // given
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(configuration, mockServerLogger);

        // when
        httpRequestsPropertiesMatcher.update(new Expectation(
            new OpenAPIDefinition()
                .withSpecUrlOrPayload("---" + NEW_LINE +
                    "openapi: 3.0.0" + NEW_LINE +
                    "paths:" + NEW_LINE +
                    "  \"/somePath\":" + NEW_LINE +
                    "    get:" + NEW_LINE +
                    "      operationId: someOperation" + NEW_LINE)
        ));

        // then
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath")
        ), is(true));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/someOtherPath")
        ), is(false));
    }

    @Test
    public void shouldMatchByPathWithOperationId() {
        // given
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(configuration, mockServerLogger);

        // when
        httpRequestsPropertiesMatcher.update(new Expectation(
            new OpenAPIDefinition()
                .withSpecUrlOrPayload("---" + NEW_LINE +
                    "openapi: 3.0.0" + NEW_LINE +
                    "paths:" + NEW_LINE +
                    "  \"/somePath\":" + NEW_LINE +
                    "    get:" + NEW_LINE +
                    "      operationId: someOperation" + NEW_LINE)
                .withOperationId("someOperation")
        ));

        // then
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath")
        ), is(true));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/someOtherPath")
        ), is(false));
    }

    // PARAMETERS

    @Test
    public void shouldThrowExceptionForAllowReserved() {
        // given
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(configuration, mockServerLogger);

        // when
        ArrayList<LogEntry> logEntries = new ArrayList<>();
        OpenAPIDefinition requestDefinition = new OpenAPIDefinition()
            .withSpecUrlOrPayload("---" + NEW_LINE +
                "openapi: 3.0.0" + NEW_LINE +
                "paths:" + NEW_LINE +
                "  \"/somePath/{someParam}\":" + NEW_LINE +
                "    get:" + NEW_LINE +
                "      operationId: someOperation" + NEW_LINE +
                "      parameters:" + NEW_LINE +
                "        - in: path" + NEW_LINE +
                "          name: someParam" + NEW_LINE +
                "          required: true" + NEW_LINE +
                "          allowReserved: true" + NEW_LINE +
                "          schema:" + NEW_LINE +
                "            type: integer" + NEW_LINE +
                "            minimum: 1");
        httpRequestsPropertiesMatcher.apply(requestDefinition, logEntries);

        // then
        assertThat(httpRequestsPropertiesMatcher.getHttpRequestPropertiesMatchers().size(), equalTo(0));
        assertThat(logEntries.size(), equalTo(1));
        assertThat(logEntries.get(0), equalTo(
            new LogEntry()
                .setEpochTime(logEntries.get(0).getEpochTime())
                .setLogLevel(ERROR)
                .setMessageFormat("Unable to load API spec, allowReserved field is not supported on parameters, found on operation: \"someOperation\" method: \"GET\" parameter: \"someParam\" in: \"path\" for open api:{}")
                .setArguments(requestDefinition)
        ));
    }

    // PATH PARAMETERS

    @Test
    public void shouldMatchByPathWithPathParameter() {
        // given
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(configuration, mockServerLogger);

        // when
        httpRequestsPropertiesMatcher.update(new Expectation(
            new OpenAPIDefinition()
                .withSpecUrlOrPayload("---" + NEW_LINE +
                    "openapi: 3.0.0" + NEW_LINE +
                    "paths:" + NEW_LINE +
                    "  \"/somePath/{someParam}\":" + NEW_LINE +
                    "    get:" + NEW_LINE +
                    "      operationId: someOperation" + NEW_LINE +
                    "      parameters:" + NEW_LINE +
                    "        - in: path" + NEW_LINE +
                    "          name: someParam" + NEW_LINE +
                    "          required: true" + NEW_LINE +
                    "          schema:" + NEW_LINE +
                    "            type: integer" + NEW_LINE +
                    "            minimum: 1")
        ));

        // then
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath/1")
        ), is(true));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath/0")
        ), is(false));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath/a")
        ), is(false));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath")
        ), is(false));
    }

    @Test
    public void shouldMatchByPathWithMultiplePathParameter() {
        // given
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(configuration, mockServerLogger);

        // when
        httpRequestsPropertiesMatcher.update(new Expectation(
            new OpenAPIDefinition()
                .withSpecUrlOrPayload("---" + NEW_LINE +
                    "openapi: 3.0.0" + NEW_LINE +
                    "paths:" + NEW_LINE +
                    "  \"/somePath/{someParam}/{someOtherParam}\":" + NEW_LINE +
                    "    get:" + NEW_LINE +
                    "      operationId: someOperation" + NEW_LINE +
                    "      parameters:" + NEW_LINE +
                    "        - in: path" + NEW_LINE +
                    "          name: someParam" + NEW_LINE +
                    "          required: true" + NEW_LINE +
                    "          schema:" + NEW_LINE +
                    "            type: integer" + NEW_LINE +
                    "            minimum: 1" + NEW_LINE +
                    "        - in: path" + NEW_LINE +
                    "          name: someOtherParam" + NEW_LINE +
                    "          required: true" + NEW_LINE +
                    "          schema:" + NEW_LINE +
                    "            type: string" + NEW_LINE +
                    "            minLength: 2" + NEW_LINE +
                    "            maxLength: 3" + NEW_LINE)
        ));

        // then
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath/1/ab")
        ), is(true));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath/0/ab")
        ), is(false));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath/1/a")
        ), is(false));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath/a")
        ), is(false));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath")
        ), is(false));
    }

    @Test
    public void shouldMatchByPathWithMultipleMissingPathParameter() {
        // given
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(configuration, mockServerLogger);

        // when
        httpRequestsPropertiesMatcher.update(new Expectation(
            new OpenAPIDefinition()
                .withSpecUrlOrPayload("---" + NEW_LINE +
                    "openapi: 3.0.0" + NEW_LINE +
                    "paths:" + NEW_LINE +
                    "  \"/somePath\":" + NEW_LINE +
                    "    get:" + NEW_LINE +
                    "      operationId: someOperation" + NEW_LINE +
                    "      parameters:" + NEW_LINE +
                    "        - in: path" + NEW_LINE +
                    "          name: someParam" + NEW_LINE +
                    "          required: true" + NEW_LINE +
                    "          schema:" + NEW_LINE +
                    "            type: integer" + NEW_LINE +
                    "            minimum: 1" + NEW_LINE +
                    "        - in: path" + NEW_LINE +
                    "          name: someOtherParam" + NEW_LINE +
                    "          required: true" + NEW_LINE +
                    "          schema:" + NEW_LINE +
                    "            type: string" + NEW_LINE +
                    "            minLength: 2" + NEW_LINE +
                    "            maxLength: 3" + NEW_LINE)
        ));

        // then
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath")
        ), is(false));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath/0/ab")
        ), is(false));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath/1/a")
        ), is(false));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath/a")
        ), is(false));
    }

    @Test
    public void shouldMatchByPathWithPathParameterAndOperationId() {
        // given
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(configuration, mockServerLogger);

        // when
        httpRequestsPropertiesMatcher.update(new Expectation(
            new OpenAPIDefinition()
                .withSpecUrlOrPayload("---" + NEW_LINE +
                    "openapi: 3.0.0" + NEW_LINE +
                    "paths:" + NEW_LINE +
                    "  \"/somePath/{someParam}\":" + NEW_LINE +
                    "    get:" + NEW_LINE +
                    "      operationId: someOperation" + NEW_LINE +
                    "      parameters:" + NEW_LINE +
                    "        - in: path" + NEW_LINE +
                    "          name: someParam" + NEW_LINE +
                    "          required: true" + NEW_LINE +
                    "          schema:" + NEW_LINE +
                    "            type: integer" + NEW_LINE +
                    "            minimum: 1")
                .withOperationId("someOperation")
        ));

        // then
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath/1")
        ), is(true));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath/0")
        ), is(false));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath/a")
        ), is(false));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath")
        ), is(false));
    }

    @Test
    public void shouldMatchByPathWithOptionalPathParameter() {
        // given
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(configuration, mockServerLogger);

        // when
        httpRequestsPropertiesMatcher.update(new Expectation(
            new OpenAPIDefinition()
                .withSpecUrlOrPayload("---" + NEW_LINE +
                    "openapi: 3.0.0" + NEW_LINE +
                    "paths:" + NEW_LINE +
                    "  \"/somePath/{someParam}\":" + NEW_LINE +
                    "    get:" + NEW_LINE +
                    "      operationId: someOperation" + NEW_LINE +
                    "      parameters:" + NEW_LINE +
                    "        - in: path" + NEW_LINE +
                    "          name: someParam" + NEW_LINE +
                    "          required: false" + NEW_LINE +
                    "          schema:" + NEW_LINE +
                    "            type: integer" + NEW_LINE +
                    "            minimum: 1")
        ));

        // then - required: false not supported for pathParameters
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath/1")
        ), is(true));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath/")
        ), is(false));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath/0")
        ), is(false));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath/a")
        ), is(false));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath")
        ), is(false));
    }

    @Test
    public void shouldMatchByPathWithAllowEmptyPathParameter() {
        // given
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(configuration, mockServerLogger);

        // when
        httpRequestsPropertiesMatcher.update(new Expectation(
            new OpenAPIDefinition()
                .withSpecUrlOrPayload("---" + NEW_LINE +
                    "openapi: 3.0.0" + NEW_LINE +
                    "paths:" + NEW_LINE +
                    "  \"/somePath/{someParam}\":" + NEW_LINE +
                    "    get:" + NEW_LINE +
                    "      operationId: someOperation" + NEW_LINE +
                    "      parameters:" + NEW_LINE +
                    "        - in: path" + NEW_LINE +
                    "          name: someParam" + NEW_LINE +
                    "          required: true" + NEW_LINE +
                    "          allowEmptyValue: true" + NEW_LINE +
                    "          schema:" + NEW_LINE +
                    "            type: integer" + NEW_LINE +
                    "            minimum: 1")
        ));

        // then - allowEmptyValue not supported for pathParameters
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath/1")
        ), is(true));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath/")
        ), is(false));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath/0")
        ), is(false));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath/a")
        ), is(false));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath")
        ), is(false));
    }

    @Test
    public void shouldMatchByPathWithSimplePathParameter() {
        // given
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(configuration, mockServerLogger);

        // when
        httpRequestsPropertiesMatcher.update(new Expectation(
            new OpenAPIDefinition()
                .withSpecUrlOrPayload("---" + NEW_LINE +
                    "openapi: 3.0.0" + NEW_LINE +
                    "paths:" + NEW_LINE +
                    "  \"/somePath/{someParam}\":" + NEW_LINE +
                    "    get:" + NEW_LINE +
                    "      operationId: someOperation" + NEW_LINE +
                    "      parameters:" + NEW_LINE +
                    "        - in: path" + NEW_LINE +
                    "          name: someParam" + NEW_LINE +
                    "          required: true" + NEW_LINE +
                    "          style: simple" + NEW_LINE +
                    "          explode: false" + NEW_LINE +
                    "          schema:" + NEW_LINE +
                    "            type: integer" + NEW_LINE +
                    "            minimum: 1")
        ));

        // then
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath/1,2,3")
        ), is(true));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath/1")
        ), is(true));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath/1,2,3,4")
        ), is(true));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath/0,1,2")
        ), is(false));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath/a,1,2")
        ), is(false));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath")
        ), is(false));
    }

    @Test
    public void shouldMatchByPathWithSimpleExplodedPathParameter() {
        // given
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(configuration, mockServerLogger);

        // when
        httpRequestsPropertiesMatcher.update(new Expectation(
            new OpenAPIDefinition()
                .withSpecUrlOrPayload("---" + NEW_LINE +
                    "openapi: 3.0.0" + NEW_LINE +
                    "paths:" + NEW_LINE +
                    "  \"/somePath/{someParam*}\":" + NEW_LINE +
                    "    get:" + NEW_LINE +
                    "      operationId: someOperation" + NEW_LINE +
                    "      parameters:" + NEW_LINE +
                    "        - in: path" + NEW_LINE +
                    "          name: someParam" + NEW_LINE +
                    "          required: true" + NEW_LINE +
                    "          style: simple" + NEW_LINE +
                    "          explode: true" + NEW_LINE +
                    "          schema:" + NEW_LINE +
                    "            type: integer" + NEW_LINE +
                    "            minimum: 1")
        ));

        // then
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath/1,2,3")
        ), is(true));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath/1")
        ), is(true));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath/1,2,3,4")
        ), is(true));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath/0,1,2")
        ), is(false));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath/a,1,2")
        ), is(false));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath")
        ), is(false));
    }

    @Test
    public void shouldMatchByPathWithLabelPathParameter() {
        // given
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(configuration, mockServerLogger);

        // when
        httpRequestsPropertiesMatcher.update(new Expectation(
            new OpenAPIDefinition()
                .withSpecUrlOrPayload("---" + NEW_LINE +
                    "openapi: 3.0.0" + NEW_LINE +
                    "paths:" + NEW_LINE +
                    "  \"/somePath/{.someParam}\":" + NEW_LINE +
                    "    get:" + NEW_LINE +
                    "      operationId: someOperation" + NEW_LINE +
                    "      parameters:" + NEW_LINE +
                    "        - in: path" + NEW_LINE +
                    "          name: someParam" + NEW_LINE +
                    "          required: true" + NEW_LINE +
                    "          style: label" + NEW_LINE +
                    "          explode: false" + NEW_LINE +
                    "          schema:" + NEW_LINE +
                    "            type: integer" + NEW_LINE +
                    "            minimum: 1")
        ));

        // then
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath/.1,2,3")
        ), is(true));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath/.1")
        ), is(true));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath/.1,2,3,4")
        ), is(true));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath/.0,1,2")
        ), is(false));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath/.a,1,2")
        ), is(false));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath")
        ), is(false));
    }

    @Test
    public void shouldMatchByPathWithLabelExplodedPathParameter() {
        // given
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(configuration, mockServerLogger);

        // when
        httpRequestsPropertiesMatcher.update(new Expectation(
            new OpenAPIDefinition()
                .withSpecUrlOrPayload("---" + NEW_LINE +
                    "openapi: 3.0.0" + NEW_LINE +
                    "paths:" + NEW_LINE +
                    "  \"/somePath/{.someParam}\":" + NEW_LINE +
                    "    get:" + NEW_LINE +
                    "      operationId: someOperation" + NEW_LINE +
                    "      parameters:" + NEW_LINE +
                    "        - in: path" + NEW_LINE +
                    "          name: someParam" + NEW_LINE +
                    "          required: true" + NEW_LINE +
                    "          style: label" + NEW_LINE +
                    "          explode: true" + NEW_LINE +
                    "          schema:" + NEW_LINE +
                    "            type: integer" + NEW_LINE +
                    "            minimum: 1")
        ));

        // then
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath/.1.2.3")
        ), is(true));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath/.1")
        ), is(true));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath/.1.2.3.4")
        ), is(true));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath/.0.1.2")
        ), is(false));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath/.a.1.2")
        ), is(false));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath")
        ), is(false));
    }

    @Test
    public void shouldMatchByPathWithMatrixPathParameter() {
        // given
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(configuration, mockServerLogger);

        // when
        httpRequestsPropertiesMatcher.update(new Expectation(
            new OpenAPIDefinition()
                .withSpecUrlOrPayload("---" + NEW_LINE +
                    "openapi: 3.0.0" + NEW_LINE +
                    "paths:" + NEW_LINE +
                    "  \"/somePath/{;someParam}\":" + NEW_LINE +
                    "    get:" + NEW_LINE +
                    "      operationId: someOperation" + NEW_LINE +
                    "      parameters:" + NEW_LINE +
                    "        - in: path" + NEW_LINE +
                    "          name: someParam" + NEW_LINE +
                    "          required: true" + NEW_LINE +
                    "          style: matrix" + NEW_LINE +
                    "          explode: false" + NEW_LINE +
                    "          schema:" + NEW_LINE +
                    "            type: integer" + NEW_LINE +
                    "            minimum: 1")
        ));

        // then
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath/;someParam=1,2,3")
        ), is(true));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath/;someParam=1")
        ), is(true));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath/;someParam=1,2,3,4")
        ), is(true));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath/;someOtherParam=1,2,3")
        ), is(false));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath/;someParam=0,1,2")
        ), is(false));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath/;someParam=a,1,2")
        ), is(false));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath")
        ), is(false));
    }

    @Test
    public void shouldMatchByPathWithMatrixExplodedPathParameter() {
        // given
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(configuration, mockServerLogger);

        // when
        httpRequestsPropertiesMatcher.update(new Expectation(
            new OpenAPIDefinition()
                .withSpecUrlOrPayload("---" + NEW_LINE +
                    "openapi: 3.0.0" + NEW_LINE +
                    "paths:" + NEW_LINE +
                    "  \"/somePath/{;someParam*}\":" + NEW_LINE +
                    "    get:" + NEW_LINE +
                    "      operationId: someOperation" + NEW_LINE +
                    "      parameters:" + NEW_LINE +
                    "        - in: path" + NEW_LINE +
                    "          name: someParam" + NEW_LINE +
                    "          required: true" + NEW_LINE +
                    "          style: matrix" + NEW_LINE +
                    "          explode: true" + NEW_LINE +
                    "          schema:" + NEW_LINE +
                    "            type: integer" + NEW_LINE +
                    "            minimum: 1")
        ));

        // then
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath/;someParam=1;someParam=2;someParam=3")
        ), is(true));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath/;someParam=1")
        ), is(true));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath/;someParam=1;someParam=2;someParam=3;someParam=4")
        ), is(true));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath/;someOtherParam=1;someOtherParam=2;someOtherParam=3")
        ), is(false));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath/;someParam=0;someParam=1;someParam=2")
        ), is(false));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath/;someParam=a;someParam=1;someParam=2")
        ), is(false));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath")
        ), is(false));
    }

    @Test
    public void shouldMatchByPathWithSimpleAndLabelExplodedPathParameter() {
        // given
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(configuration, mockServerLogger);

        // when
        httpRequestsPropertiesMatcher.update(new Expectation(
            new OpenAPIDefinition()
                .withSpecUrlOrPayload("---" + NEW_LINE +
                    "openapi: 3.0.0" + NEW_LINE +
                    "paths:" + NEW_LINE +
                    "  \"/somePath/{someSimpleParam}/{someLabelParam}\":" + NEW_LINE +
                    "    get:" + NEW_LINE +
                    "      operationId: someOperation" + NEW_LINE +
                    "      parameters:" + NEW_LINE +
                    "        - in: path" + NEW_LINE +
                    "          name: someSimpleParam" + NEW_LINE +
                    "          required: true" + NEW_LINE +
                    "          style: simple" + NEW_LINE +
                    "          explode: false" + NEW_LINE +
                    "          schema:" + NEW_LINE +
                    "            type: integer" + NEW_LINE +
                    "            minimum: 1" + NEW_LINE +
                    "        - in: path" + NEW_LINE +
                    "          name: someLabelParam" + NEW_LINE +
                    "          required: true" + NEW_LINE +
                    "          style: label" + NEW_LINE +
                    "          explode: true" + NEW_LINE +
                    "          schema:" + NEW_LINE +
                    "            type: integer" + NEW_LINE +
                    "            minimum: 1")
        ));

        // then
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath/1,2,3/.1.2.3")
        ), is(true));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath/1/.1.2.3")
        ), is(true));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath/1,2,3/.1")
        ), is(true));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath/1/.1")
        ), is(true));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath/1,2,3/.1.2.3.4")
        ), is(true));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath/1,2,3,4/.1.2.3")
        ), is(true));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath/1,2,3,4/.1.2.3.4")
        ), is(true));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath/1,2,3/.0.1.2")
        ), is(false));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath/0,1,2/.1.2.3")
        ), is(false));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath/0,1,2/.0.1.2")
        ), is(false));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath/1,2,3/.a.1.2")
        ), is(false));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath/a,1,2/.1.2.3")
        ), is(false));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath/a,1,2/.a.1.2")
        ), is(false));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath")
        ), is(false));
    }

    @Test
    public void shouldMatchByPathWithLabelAndMatrixExplodedPathParameter() {
        // given
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(configuration, mockServerLogger);

        // when
        httpRequestsPropertiesMatcher.update(new Expectation(
            new OpenAPIDefinition()
                .withSpecUrlOrPayload("---" + NEW_LINE +
                    "openapi: 3.0.0" + NEW_LINE +
                    "paths:" + NEW_LINE +
                    "  \"/somePath/{.someLabelParam}/{;someMatrixParam*}\":" + NEW_LINE +
                    "    get:" + NEW_LINE +
                    "      operationId: someOperation" + NEW_LINE +
                    "      parameters:" + NEW_LINE +
                    "        - in: path" + NEW_LINE +
                    "          name: someLabelParam" + NEW_LINE +
                    "          required: true" + NEW_LINE +
                    "          style: label" + NEW_LINE +
                    "          explode: false" + NEW_LINE +
                    "          schema:" + NEW_LINE +
                    "            type: integer" + NEW_LINE +
                    "            minimum: 1" + NEW_LINE +
                    "        - in: path" + NEW_LINE +
                    "          name: someMatrixParam" + NEW_LINE +
                    "          required: true" + NEW_LINE +
                    "          style: matrix" + NEW_LINE +
                    "          explode: true" + NEW_LINE +
                    "          schema:" + NEW_LINE +
                    "            type: integer" + NEW_LINE +
                    "            minimum: 1")
        ));

        // then
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath/.1,2,3/;someMatrixParam=1;someMatrixParam=2;someMatrixParam=3")
        ), is(true));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath/.1/;someMatrixParam=1")
        ), is(true));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath/.1,2,3,4/;someMatrixParam=1;someMatrixParam=2;someMatrixParam=3;someMatrixParam=4")
        ), is(true));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath/.1,2,3/;someMatrixParam=0;someMatrixParam=1;someMatrixParam=2")
        ), is(false));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath/.0,1,2/;someMatrixParam=1;someMatrixParam=2;someMatrixParam=3")
        ), is(false));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath/.0,1,2/;someMatrixParam=0;someMatrixParam=1;someMatrixParam=2")
        ), is(false));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath/.1,2,3/;someMatrixParam=a;someMatrixParam=1;someMatrixParam=2")
        ), is(false));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath/.a,1,2/;someMatrixParam=1;someMatrixParam=2;someMatrixParam=3")
        ), is(false));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath/.a,1,2/;someMatrixParam=a;someMatrixParam=1;someMatrixParam=2")
        ), is(false));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath")
        ), is(false));
    }

    // QUERY STRING PARAMETERS

    @Test
    public void shouldMatchByQueryStringParameter() {
        // given
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(configuration, mockServerLogger);

        // when
        httpRequestsPropertiesMatcher.update(new Expectation(
            new OpenAPIDefinition()
                .withSpecUrlOrPayload("---" + NEW_LINE +
                    "openapi: 3.0.0" + NEW_LINE +
                    "paths:" + NEW_LINE +
                    "  \"/somePath\":" + NEW_LINE +
                    "    get:" + NEW_LINE +
                    "      operationId: someOperation" + NEW_LINE +
                    "      parameters:" + NEW_LINE +
                    "        - in: query" + NEW_LINE +
                    "          name: someParam" + NEW_LINE +
                    "          required: true" + NEW_LINE +
                    "          schema:" + NEW_LINE +
                    "            type: integer" + NEW_LINE +
                    "            minimum: 1")
        ));

        // then
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withQueryStringParameter("someParam", "1")
        ), is(true));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withQueryStringParameter("someParam", "0")
        ), is(false));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withQueryStringParameter("someOtherParam", "1")
        ), is(false));
    }

    @Test
    public void shouldMatchByQueryStringParameterWithOperationId() {
        // given
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(configuration, mockServerLogger);

        // when
        httpRequestsPropertiesMatcher.update(new Expectation(
            new OpenAPIDefinition()
                .withSpecUrlOrPayload("---" + NEW_LINE +
                    "openapi: 3.0.0" + NEW_LINE +
                    "paths:" + NEW_LINE +
                    "  \"/somePath\":" + NEW_LINE +
                    "    get:" + NEW_LINE +
                    "      operationId: someOperation" + NEW_LINE +
                    "      parameters:" + NEW_LINE +
                    "        - in: query" + NEW_LINE +
                    "          name: someParam" + NEW_LINE +
                    "          required: true" + NEW_LINE +
                    "          schema:" + NEW_LINE +
                    "            type: integer" + NEW_LINE +
                    "            minimum: 1")
                .withOperationId("someOperation")
        ));

        // then
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withQueryStringParameter("someParam", "1")
        ), is(true));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withQueryStringParameter("someParam", "0")
        ), is(false));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withQueryStringParameter("someOtherParam", "1")
        ), is(false));
    }

    @Test
    public void shouldMatchByOptionalQueryString() {
        // given
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(configuration, mockServerLogger);

        // when
        httpRequestsPropertiesMatcher.update(new Expectation(
            new OpenAPIDefinition()
                .withSpecUrlOrPayload("---" + NEW_LINE +
                    "openapi: 3.0.0" + NEW_LINE +
                    "paths:" + NEW_LINE +
                    "  \"/somePath\":" + NEW_LINE +
                    "    get:" + NEW_LINE +
                    "      operationId: someOperation" + NEW_LINE +
                    "      parameters:" + NEW_LINE +
                    "        - in: query" + NEW_LINE +
                    "          name: someParam" + NEW_LINE +
                    "          required: false" + NEW_LINE +
                    "          schema:" + NEW_LINE +
                    "            type: integer" + NEW_LINE +
                    "            minimum: 1")
        ));

        // then
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withQueryStringParameter("someParam", "1")
        ), is(true));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withQueryStringParameter("someOtherParam", "1")
        ), is(true));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withQueryStringParameter("someOtherParam", "0")
        ), is(true));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withQueryStringParameter("someParam", "0")
        ), is(false));
    }

    @Test
    public void shouldMatchByOptionalNotSpecifiedQueryString() {
        // given
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(configuration, mockServerLogger);

        // when
        httpRequestsPropertiesMatcher.update(new Expectation(
            new OpenAPIDefinition()
                .withSpecUrlOrPayload("---" + NEW_LINE +
                    "openapi: 3.0.0" + NEW_LINE +
                    "paths:" + NEW_LINE +
                    "  \"/somePath\":" + NEW_LINE +
                    "    get:" + NEW_LINE +
                    "      operationId: someOperation" + NEW_LINE +
                    "      parameters:" + NEW_LINE +
                    "        - in: query" + NEW_LINE +
                    "          name: someParam" + NEW_LINE +
                    "          schema:" + NEW_LINE +
                    "            type: integer" + NEW_LINE +
                    "            minimum: 1")
        ));

        // then
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withQueryStringParameter("someParam", "1")
        ), is(true));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withQueryStringParameter("someOtherParam", "1")
        ), is(true));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withQueryStringParameter("someOtherParam", "0")
        ), is(true));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withQueryStringParameter("someParam", "0")
        ), is(false));
    }

    @Test
    public void shouldMatchQueryStringWithAllowEmpty() {
        // given
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(configuration, mockServerLogger);

        // when
        httpRequestsPropertiesMatcher.update(new Expectation(
            new OpenAPIDefinition()
                .withSpecUrlOrPayload("---" + NEW_LINE +
                    "openapi: 3.0.0" + NEW_LINE +
                    "paths:" + NEW_LINE +
                    "  \"/somePath\":" + NEW_LINE +
                    "    get:" + NEW_LINE +
                    "      operationId: someOperation" + NEW_LINE +
                    "      parameters:" + NEW_LINE +
                    "        - in: query" + NEW_LINE +
                    "          name: someParam" + NEW_LINE +
                    "          required: true" + NEW_LINE +
                    "          allowEmptyValue: true" + NEW_LINE +
                    "          schema:" + NEW_LINE +
                    "            type: integer" + NEW_LINE +
                    "            minimum: 1")
        ));

        // then
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withQueryStringParameter("someParam", "1")
        ), is(true));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withQueryStringParameter("someParam")
        ), is(true));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withQueryStringParameter("someOtherParam", "1")
        ), is(false));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withQueryStringParameter("someParam", "0")
        ), is(false));
    }

    @Test
    public void shouldMatchByOptionalQueryStringWithAllowEmpty() {
        // given
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(configuration, mockServerLogger);

        // when
        httpRequestsPropertiesMatcher.update(new Expectation(
            new OpenAPIDefinition()
                .withSpecUrlOrPayload("---" + NEW_LINE +
                    "openapi: 3.0.0" + NEW_LINE +
                    "paths:" + NEW_LINE +
                    "  \"/somePath\":" + NEW_LINE +
                    "    get:" + NEW_LINE +
                    "      operationId: someOperation" + NEW_LINE +
                    "      parameters:" + NEW_LINE +
                    "        - in: query" + NEW_LINE +
                    "          name: someParam" + NEW_LINE +
                    "          required: false" + NEW_LINE +
                    "          allowEmptyValue: true" + NEW_LINE +
                    "          schema:" + NEW_LINE +
                    "            type: integer" + NEW_LINE +
                    "            minimum: 1")
        ));

        // then
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withQueryStringParameter("someParam", "1")
        ), is(true));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withQueryStringParameter("someParam")
        ), is(true));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withQueryStringParameter("someOtherParam", "1")
        ), is(true));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withQueryStringParameter("someOtherParam", "0")
        ), is(true));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withQueryStringParameter("someParam", "0")
        ), is(false));
    }

    @Test
    public void shouldMatchByQueryStringParameterCommonForAllPaths() {
        // given
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(configuration, mockServerLogger);

        // when
        httpRequestsPropertiesMatcher.update(new Expectation(
            new OpenAPIDefinition()
                .withSpecUrlOrPayload("---" + NEW_LINE +
                    "openapi: 3.0.0" + NEW_LINE +
                    "components:" + NEW_LINE +
                    "  parameters:" + NEW_LINE +
                    "    someParam:" + NEW_LINE +
                    "      in: query" + NEW_LINE +
                    "      name: someParam" + NEW_LINE +
                    "      required: true" + NEW_LINE +
                    "      schema:" + NEW_LINE +
                    "        type: integer" + NEW_LINE +
                    "        minimum: 1" + NEW_LINE +
                    "    someOtherParam:" + NEW_LINE +
                    "      in: query" + NEW_LINE +
                    "      name: someOtherParam" + NEW_LINE +
                    "      required: true" + NEW_LINE +
                    "      schema:" + NEW_LINE +
                    "        type: string" + NEW_LINE +
                    "        minLength: 2" + NEW_LINE +
                    "        maxLength: 3" + NEW_LINE +
                    "paths:" + NEW_LINE +
                    "  \"/somePath\":" + NEW_LINE +
                    "    parameters:" + NEW_LINE +
                    "      - $ref: '#/components/parameters/someParam'" + NEW_LINE +
                    "    get:" + NEW_LINE +
                    "      operationId: someOperation" + NEW_LINE +
                    "      parameters:" + NEW_LINE +
                    "        - $ref: '#/components/parameters/someOtherParam'" + NEW_LINE)
        ));

        // then
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withQueryStringParameter("someParam", "1")
                .withQueryStringParameter("someOtherParam", "ab")
        ), is(true));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withQueryStringParameter("someParam", "0")
                .withQueryStringParameter("someOtherParam", "ab")
        ), is(false));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withQueryStringParameter("someParam", "1")
                .withQueryStringParameter("someOtherParam", "a")
        ), is(false));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withQueryStringParameter("someOtherParam", "1")
        ), is(false));
    }

    @Test
    public void shouldMatchByQueryStringParameterCommonForPath() {
        // given
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(configuration, mockServerLogger);

        // when
        httpRequestsPropertiesMatcher.update(new Expectation(
            new OpenAPIDefinition()
                .withSpecUrlOrPayload("---" + NEW_LINE +
                    "openapi: 3.0.0" + NEW_LINE +
                    "paths:" + NEW_LINE +
                    "  \"/somePath\":" + NEW_LINE +
                    "    parameters:" + NEW_LINE +
                    "      - in: query" + NEW_LINE +
                    "        name: someParam" + NEW_LINE +
                    "        required: true" + NEW_LINE +
                    "        schema:" + NEW_LINE +
                    "          type: integer" + NEW_LINE +
                    "          minimum: 1" + NEW_LINE +
                    "    get:" + NEW_LINE +
                    "      operationId: someOperation" + NEW_LINE)
        ));

        // then
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withQueryStringParameter("someParam", "1")
        ), is(true));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withQueryStringParameter("someParam", "0")
        ), is(false));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withQueryStringParameter("someOtherParam", "1")
        ), is(false));
    }

    @Test
    public void shouldMatchByQueryStringParameterCommonForAllPathsOverriddenAtPath() {
        // given
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(configuration, mockServerLogger);

        // when
        httpRequestsPropertiesMatcher.update(new Expectation(
            new OpenAPIDefinition()
                .withSpecUrlOrPayload("---" + NEW_LINE +
                    "openapi: 3.0.0" + NEW_LINE +
                    "components:" + NEW_LINE +
                    "  parameters:" + NEW_LINE +
                    "    someParam:" + NEW_LINE +
                    "      in: query" + NEW_LINE +
                    "      name: someParam" + NEW_LINE +
                    "      required: true" + NEW_LINE +
                    "      schema:" + NEW_LINE +
                    "        type: integer" + NEW_LINE +
                    "        minimum: 1" + NEW_LINE +
                    "    someOtherParam:" + NEW_LINE +
                    "      in: query" + NEW_LINE +
                    "      name: someOtherParam" + NEW_LINE +
                    "      required: true" + NEW_LINE +
                    "      schema:" + NEW_LINE +
                    "        type: string" + NEW_LINE +
                    "        minLength: 2" + NEW_LINE +
                    "        maxLength: 3" + NEW_LINE +
                    "paths:" + NEW_LINE +
                    "  \"/somePath\":" + NEW_LINE +
                    "    parameters:" + NEW_LINE +
                    "      - in: query" + NEW_LINE +
                    "        name: someParam" + NEW_LINE +
                    "        required: true" + NEW_LINE +
                    "        schema:" + NEW_LINE +
                    "          type: integer" + NEW_LINE +
                    "          minimum: 10" + NEW_LINE +
                    "    get:" + NEW_LINE +
                    "      operationId: someOperation" + NEW_LINE +
                    "      parameters:" + NEW_LINE +
                    "        - $ref: '#/components/parameters/someOtherParam'" + NEW_LINE)
        ));

        // then
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withQueryStringParameter("someParam", "10")
                .withQueryStringParameter("someOtherParam", "ab")
        ), is(true));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withQueryStringParameter("someParam", "5")
                .withQueryStringParameter("someOtherParam", "ab")
        ), is(false));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withQueryStringParameter("someParam", "10")
                .withQueryStringParameter("someOtherParam", "a")
        ), is(false));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withQueryStringParameter("someOtherParam", "1")
        ), is(false));
    }

    @Test
    public void shouldMatchByQueryStringParameterCommonForAllPathsOverriddenAtMethod() {
        // given
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(configuration, mockServerLogger);

        // when
        httpRequestsPropertiesMatcher.update(new Expectation(
            new OpenAPIDefinition()
                .withSpecUrlOrPayload("---" + NEW_LINE +
                    "openapi: 3.0.0" + NEW_LINE +
                    "components:" + NEW_LINE +
                    "  parameters:" + NEW_LINE +
                    "    someParam:" + NEW_LINE +
                    "      in: query" + NEW_LINE +
                    "      name: someParam" + NEW_LINE +
                    "      required: true" + NEW_LINE +
                    "      schema:" + NEW_LINE +
                    "        type: integer" + NEW_LINE +
                    "        minimum: 1" + NEW_LINE +
                    "    someOtherParam:" + NEW_LINE +
                    "      in: query" + NEW_LINE +
                    "      name: someOtherParam" + NEW_LINE +
                    "      required: true" + NEW_LINE +
                    "      schema:" + NEW_LINE +
                    "        type: string" + NEW_LINE +
                    "        minLength: 2" + NEW_LINE +
                    "        maxLength: 3" + NEW_LINE +
                    "paths:" + NEW_LINE +
                    "  \"/somePath\":" + NEW_LINE +
                    "    parameters:" + NEW_LINE +
                    "      - in: query" + NEW_LINE +
                    "        name: someParam" + NEW_LINE +
                    "        required: true" + NEW_LINE +
                    "        schema:" + NEW_LINE +
                    "          type: integer" + NEW_LINE +
                    "          minimum: 10" + NEW_LINE +
                    "    get:" + NEW_LINE +
                    "      operationId: someOperation" + NEW_LINE +
                    "      parameters:" + NEW_LINE +
                    "        - in: query" + NEW_LINE +
                    "          name: someParam" + NEW_LINE +
                    "          required: true" + NEW_LINE +
                    "          schema:" + NEW_LINE +
                    "            type: integer" + NEW_LINE +
                    "            minimum: 100" + NEW_LINE +
                    "        - $ref: '#/components/parameters/someOtherParam'" + NEW_LINE)
        ));

        // then
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withQueryStringParameter("someParam", "100")
                .withQueryStringParameter("someOtherParam", "ab")
        ), is(true));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withQueryStringParameter("someParam", "50")
                .withQueryStringParameter("someOtherParam", "ab")
        ), is(false));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withQueryStringParameter("someParam", "100")
                .withQueryStringParameter("someOtherParam", "a")
        ), is(false));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withQueryStringParameter("someOtherParam", "1")
        ), is(false));
    }

    @Test
    public void shouldMatchByPathWithFormQueryStringParameter() {
        // given
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(configuration, mockServerLogger);

        // when
        httpRequestsPropertiesMatcher.update(new Expectation(
            new OpenAPIDefinition()
                .withSpecUrlOrPayload("---" + NEW_LINE +
                    "openapi: 3.0.0" + NEW_LINE +
                    "paths:" + NEW_LINE +
                    "  \"/somePath\":" + NEW_LINE +
                    "    get:" + NEW_LINE +
                    "      operationId: someOperation" + NEW_LINE +
                    "      parameters:" + NEW_LINE +
                    "        - in: query" + NEW_LINE +
                    "          name: someParam" + NEW_LINE +
                    "          required: true" + NEW_LINE +
                    "          style: form" + NEW_LINE +
                    "          explode: false" + NEW_LINE +
                    "          schema:" + NEW_LINE +
                    "            type: integer" + NEW_LINE +
                    "            minimum: 1")
        ));

        // then
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath")
                .withQueryStringParameter("someParam", "1,2,3")
        ), is(true));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath")
                .withQueryStringParameter("someParam", "1")
        ), is(true));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath")
                .withQueryStringParameter("someParam", "1,2,3,4")
        ), is(true));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath")
                .withQueryStringParameter("someParam", "0,1,2")
        ), is(false));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath")
                .withQueryStringParameter("someParam", "a,1,2")
        ), is(false));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath")
        ), is(false));
    }

    @Test
    public void shouldMatchByPathWithFormExplodedQueryStringParameter() {
        // given
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(configuration, mockServerLogger);

        // when
        httpRequestsPropertiesMatcher.update(new Expectation(
            new OpenAPIDefinition()
                .withSpecUrlOrPayload("---" + NEW_LINE +
                    "openapi: 3.0.0" + NEW_LINE +
                    "paths:" + NEW_LINE +
                    "  \"/somePath\":" + NEW_LINE +
                    "    get:" + NEW_LINE +
                    "      operationId: someOperation" + NEW_LINE +
                    "      parameters:" + NEW_LINE +
                    "        - in: query" + NEW_LINE +
                    "          name: someParam" + NEW_LINE +
                    "          required: true" + NEW_LINE +
                    "          style: form" + NEW_LINE +
                    "          explode: true" + NEW_LINE +
                    "          schema:" + NEW_LINE +
                    "            type: integer" + NEW_LINE +
                    "            minimum: 1")
        ));

        // then
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath")
                .withQueryStringParameter("someParam", "1", "2", "3")
        ), is(true));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath")
                .withQueryStringParameter("someParam", "1")
        ), is(true));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath")
                .withQueryStringParameter("someParam", "1", "2", "3", "4")
        ), is(true));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath")
                .withQueryStringParameter("someParam", "0", "1", "2")
        ), is(false));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath")
                .withQueryStringParameter("someParam", "a", "1", "2")
        ), is(false));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath")
        ), is(false));
    }

    @Test
    public void shouldMatchByPathWithSpaceDelimitedQueryStringParameter() {
        // given
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(configuration, mockServerLogger);

        // when
        httpRequestsPropertiesMatcher.update(new Expectation(
            new OpenAPIDefinition()
                .withSpecUrlOrPayload("---" + NEW_LINE +
                    "openapi: 3.0.0" + NEW_LINE +
                    "paths:" + NEW_LINE +
                    "  \"/somePath\":" + NEW_LINE +
                    "    get:" + NEW_LINE +
                    "      operationId: someOperation" + NEW_LINE +
                    "      parameters:" + NEW_LINE +
                    "        - in: query" + NEW_LINE +
                    "          name: someParam" + NEW_LINE +
                    "          required: true" + NEW_LINE +
                    "          style: spaceDelimited" + NEW_LINE +
                    "          explode: false" + NEW_LINE +
                    "          schema:" + NEW_LINE +
                    "            type: integer" + NEW_LINE +
                    "            minimum: 1")
        ));

        // then
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath")
                .withQueryStringParameter("someParam", "1%202%203")
        ), is(true));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath")
                .withQueryStringParameter("someParam", "1 2 3")
        ), is(true));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath")
                .withQueryStringParameter("someParam", "1+2+3")
        ), is(true));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath")
                .withQueryStringParameter("someParam", "1")
        ), is(true));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath")
                .withQueryStringParameter("someParam", "1%202%203%204")
        ), is(true));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath")
                .withQueryStringParameter("someParam", "1 2 3 4")
        ), is(true));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath")
                .withQueryStringParameter("someParam", "1+2+3+4")
        ), is(true));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath")
                .withQueryStringParameter("someParam", "0%201%202")
        ), is(false));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath")
                .withQueryStringParameter("someParam", "0 1 2")
        ), is(false));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath")
                .withQueryStringParameter("someParam", "0+1+2")
        ), is(false));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath")
                .withQueryStringParameter("someParam", "a%202%203")
        ), is(false));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath")
                .withQueryStringParameter("someParam", "a 2 3")
        ), is(false));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath")
                .withQueryStringParameter("someParam", "a+2+3")
        ), is(false));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath")
        ), is(false));
    }

    @Test
    public void shouldMatchByPathWithSpaceDelimitedExplodedQueryStringParameter() {
        // given
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(configuration, mockServerLogger);

        // when
        httpRequestsPropertiesMatcher.update(new Expectation(
            new OpenAPIDefinition()
                .withSpecUrlOrPayload("---" + NEW_LINE +
                    "openapi: 3.0.0" + NEW_LINE +
                    "paths:" + NEW_LINE +
                    "  \"/somePath\":" + NEW_LINE +
                    "    get:" + NEW_LINE +
                    "      operationId: someOperation" + NEW_LINE +
                    "      parameters:" + NEW_LINE +
                    "        - in: query" + NEW_LINE +
                    "          name: someParam" + NEW_LINE +
                    "          required: true" + NEW_LINE +
                    "          style: spaceDelimited" + NEW_LINE +
                    "          explode: true" + NEW_LINE +
                    "          schema:" + NEW_LINE +
                    "            type: integer" + NEW_LINE +
                    "            minimum: 1")
        ));

        // then
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath")
                .withQueryStringParameter("someParam", "1", "2", "3")
        ), is(true));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath")
                .withQueryStringParameter("someParam", "1")
        ), is(true));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath")
                .withQueryStringParameter("someParam", "1", "2", "3", "4")
        ), is(true));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath")
                .withQueryStringParameter("someParam", "0", "1", "2")
        ), is(false));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath")
                .withQueryStringParameter("someParam", "a", "1", "2")
        ), is(false));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath")
        ), is(false));
    }

    @Test
    public void shouldMatchByPathWithPipeDelimitedQueryStringParameter() {
        // given
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(configuration, mockServerLogger);

        // when
        httpRequestsPropertiesMatcher.update(new Expectation(
            new OpenAPIDefinition()
                .withSpecUrlOrPayload("---" + NEW_LINE +
                    "openapi: 3.0.0" + NEW_LINE +
                    "paths:" + NEW_LINE +
                    "  \"/somePath\":" + NEW_LINE +
                    "    get:" + NEW_LINE +
                    "      operationId: someOperation" + NEW_LINE +
                    "      parameters:" + NEW_LINE +
                    "        - in: query" + NEW_LINE +
                    "          name: someParam" + NEW_LINE +
                    "          required: true" + NEW_LINE +
                    "          style: pipeDelimited" + NEW_LINE +
                    "          explode: false" + NEW_LINE +
                    "          schema:" + NEW_LINE +
                    "            type: integer" + NEW_LINE +
                    "            minimum: 1")
        ));

        // then
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath")
                .withQueryStringParameter("someParam", "1|2|3")
        ), is(true));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath")
                .withQueryStringParameter("someParam", "1")
        ), is(true));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath")
                .withQueryStringParameter("someParam", "1|2|3|4")
        ), is(true));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath")
                .withQueryStringParameter("someParam", "0|1|2")
        ), is(false));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath")
                .withQueryStringParameter("someParam", "a|1|2")
        ), is(false));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath")
        ), is(false));
    }

    @Test
    public void shouldMatchByPathWithPipeDelimitedExplodedQueryStringParameter() {
        // given
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(configuration, mockServerLogger);

        // when
        httpRequestsPropertiesMatcher.update(new Expectation(
            new OpenAPIDefinition()
                .withSpecUrlOrPayload("---" + NEW_LINE +
                    "openapi: 3.0.0" + NEW_LINE +
                    "paths:" + NEW_LINE +
                    "  \"/somePath\":" + NEW_LINE +
                    "    get:" + NEW_LINE +
                    "      operationId: someOperation" + NEW_LINE +
                    "      parameters:" + NEW_LINE +
                    "        - in: query" + NEW_LINE +
                    "          name: someParam" + NEW_LINE +
                    "          required: true" + NEW_LINE +
                    "          style: pipeDelimited" + NEW_LINE +
                    "          explode: true" + NEW_LINE +
                    "          schema:" + NEW_LINE +
                    "            type: integer" + NEW_LINE +
                    "            minimum: 1")
        ));

        // then
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath")
                .withQueryStringParameter("someParam", "1", "2", "3")
        ), is(true));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath")
                .withQueryStringParameter("someParam", "1")
        ), is(true));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath")
                .withQueryStringParameter("someParam", "1", "2", "3", "4")
        ), is(true));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath")
                .withQueryStringParameter("someParam", "0", "1", "2")
        ), is(false));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath")
                .withQueryStringParameter("someParam", "a", "1", "2")
        ), is(false));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath")
        ), is(false));
    }

    @Test
    public void shouldMatchByPathWithFormExplodedAndPipeDelimitedQueryStringParameter() {
        // given
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(configuration, mockServerLogger);

        // when
        httpRequestsPropertiesMatcher.update(new Expectation(
            new OpenAPIDefinition()
                .withSpecUrlOrPayload("---" + NEW_LINE +
                    "openapi: 3.0.0" + NEW_LINE +
                    "paths:" + NEW_LINE +
                    "  \"/somePath\":" + NEW_LINE +
                    "    get:" + NEW_LINE +
                    "      operationId: someOperation" + NEW_LINE +
                    "      parameters:" + NEW_LINE +
                    "        - in: query" + NEW_LINE +
                    "          name: someFormParam" + NEW_LINE +
                    "          required: true" + NEW_LINE +
                    "          style: form" + NEW_LINE +
                    "          explode: true" + NEW_LINE +
                    "          schema:" + NEW_LINE +
                    "            type: integer" + NEW_LINE +
                    "            minimum: 1" + NEW_LINE +
                    "        - in: query" + NEW_LINE +
                    "          name: someSpaceDelimitedParam" + NEW_LINE +
                    "          required: true" + NEW_LINE +
                    "          style: pipeDelimited" + NEW_LINE +
                    "          explode: false" + NEW_LINE +
                    "          schema:" + NEW_LINE +
                    "            type: integer" + NEW_LINE +
                    "            minimum: 1")
        ));

        // then
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath")
                .withQueryStringParameter("someFormParam", "1", "2", "3")
                .withQueryStringParameter("someSpaceDelimitedParam", "1|2|3")
        ), is(true));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath")
                .withQueryStringParameter("someFormParam", "1")
                .withQueryStringParameter("someSpaceDelimitedParam", "1")
        ), is(true));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath")
                .withQueryStringParameter("someFormParam", "1", "2", "3", "4")
                .withQueryStringParameter("someSpaceDelimitedParam", "1|2|3|4")
        ), is(true));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath")
                .withQueryStringParameter("someFormParam", "0", "1", "2")
                .withQueryStringParameter("someSpaceDelimitedParam", "1|2|3")
        ), is(false));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath")
                .withQueryStringParameter("someFormParam", "1", "2", "3")
                .withQueryStringParameter("someSpaceDelimitedParam", "0|1|2")
        ), is(false));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath")
                .withQueryStringParameter("someFormParam", "0", "1", "2")
                .withQueryStringParameter("someSpaceDelimitedParam", "0|1|2")
        ), is(false));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath")
                .withQueryStringParameter("someFormParam", "a", "1", "2")
                .withQueryStringParameter("someSpaceDelimitedParam", "1|2|3")
        ), is(false));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath")
                .withQueryStringParameter("someFormParam", "1", "2", "3")
                .withQueryStringParameter("someSpaceDelimitedParam", "a|1|2")
        ), is(false));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath")
                .withQueryStringParameter("someFormParam", "a", "1", "2")
                .withQueryStringParameter("someSpaceDelimitedParam", "a|1|2")
        ), is(false));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath")
        ), is(false));
    }

    @Test
    public void shouldMatchByPathWithSpaceDelimitedAndPipeDelimitedQueryStringParameter() {
        // given
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(configuration, mockServerLogger);

        // when
        httpRequestsPropertiesMatcher.update(new Expectation(
            new OpenAPIDefinition()
                .withSpecUrlOrPayload("---" + NEW_LINE +
                    "openapi: 3.0.0" + NEW_LINE +
                    "paths:" + NEW_LINE +
                    "  \"/somePath\":" + NEW_LINE +
                    "    get:" + NEW_LINE +
                    "      operationId: someOperation" + NEW_LINE +
                    "      parameters:" + NEW_LINE +
                    "        - in: query" + NEW_LINE +
                    "          name: someSpaceDelimitedParam" + NEW_LINE +
                    "          required: true" + NEW_LINE +
                    "          style: spaceDelimited" + NEW_LINE +
                    "          explode: false" + NEW_LINE +
                    "          schema:" + NEW_LINE +
                    "            type: integer" + NEW_LINE +
                    "            minimum: 1" + NEW_LINE +
                    "        - in: query" + NEW_LINE +
                    "          name: somePipeDelimitedParam" + NEW_LINE +
                    "          required: true" + NEW_LINE +
                    "          style: pipeDelimited" + NEW_LINE +
                    "          explode: false" + NEW_LINE +
                    "          schema:" + NEW_LINE +
                    "            type: integer" + NEW_LINE +
                    "            minimum: 1")
        ));

        // then
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath")
                .withQueryStringParameter("someSpaceDelimitedParam", "1%202%203")
                .withQueryStringParameter("somePipeDelimitedParam", "1|2|3")
        ), is(true));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath")
                .withQueryStringParameter("someSpaceDelimitedParam", "1 2 3")
                .withQueryStringParameter("somePipeDelimitedParam", "1|2|3")
        ), is(true));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath")
                .withQueryStringParameter("someSpaceDelimitedParam", "1")
                .withQueryStringParameter("somePipeDelimitedParam", "1")
        ), is(true));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath")
                .withQueryStringParameter("someSpaceDelimitedParam", "1+2+3+4")
                .withQueryStringParameter("somePipeDelimitedParam", "1|2|3|4")
        ), is(true));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath")
                .withQueryStringParameter("someSpaceDelimitedParam", "0+1+2")
                .withQueryStringParameter("somePipeDelimitedParam", "1|2|3")
        ), is(false));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath")
                .withQueryStringParameter("someSpaceDelimitedParam", "1+2+3")
                .withQueryStringParameter("somePipeDelimitedParam", "0|1|2")
        ), is(false));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath")
                .withQueryStringParameter("someSpaceDelimitedParam", "0+1+2")
                .withQueryStringParameter("somePipeDelimitedParam", "0|1|2")
        ), is(false));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath")
                .withQueryStringParameter("someSpaceDelimitedParam", "a%201%202")
                .withQueryStringParameter("somePipeDelimitedParam", "1|2|3")
        ), is(false));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath")
                .withQueryStringParameter("someSpaceDelimitedParam", "1%202%203")
                .withQueryStringParameter("somePipeDelimitedParam", "a|1|2")
        ), is(false));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath")
                .withQueryStringParameter("someSpaceDelimitedParam", "a%201%202")
                .withQueryStringParameter("somePipeDelimitedParam", "a|1|2")
        ), is(false));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath")
        ), is(false));
    }

    // HEADERS

    @Test
    public void shouldMatchByHeader() {
        // given
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(configuration, mockServerLogger);

        // when
        httpRequestsPropertiesMatcher.update(new Expectation(
            new OpenAPIDefinition()
                .withSpecUrlOrPayload("---" + NEW_LINE +
                    "openapi: 3.0.0" + NEW_LINE +
                    "paths:" + NEW_LINE +
                    "  \"/somePath\":" + NEW_LINE +
                    "    get:" + NEW_LINE +
                    "      operationId: someOperation" + NEW_LINE +
                    "      parameters:" + NEW_LINE +
                    "        - in: header" + NEW_LINE +
                    "          name: someParam" + NEW_LINE +
                    "          required: true" + NEW_LINE +
                    "          schema:" + NEW_LINE +
                    "            type: integer" + NEW_LINE +
                    "            minimum: 1")
        ));

        // then
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withHeader("someParam", "1")
        ), is(true));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withHeader("someParam", "0")
        ), is(false));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withHeader("someOtherParam", "1")
        ), is(false));
    }

    @Test
    public void shouldMatchByHeaderWithOperationId() {
        // given
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(configuration, mockServerLogger);

        // when
        httpRequestsPropertiesMatcher.update(new Expectation(
            new OpenAPIDefinition()
                .withSpecUrlOrPayload("---" + NEW_LINE +
                    "openapi: 3.0.0" + NEW_LINE +
                    "paths:" + NEW_LINE +
                    "  \"/somePath\":" + NEW_LINE +
                    "    get:" + NEW_LINE +
                    "      operationId: someOperation" + NEW_LINE +
                    "      parameters:" + NEW_LINE +
                    "        - in: header" + NEW_LINE +
                    "          name: someParam" + NEW_LINE +
                    "          required: true" + NEW_LINE +
                    "          schema:" + NEW_LINE +
                    "            type: integer" + NEW_LINE +
                    "            minimum: 1")
                .withOperationId("someOperation")
        ));

        // then
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withHeader("someParam", "1")
        ), is(true));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withHeader("someParam", "0")
        ), is(false));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withHeader("someOtherParam", "1")
        ), is(false));
    }

    @Test
    public void shouldMatchByOptionalHeader() {
        // given
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(configuration, mockServerLogger);

        // when
        httpRequestsPropertiesMatcher.update(new Expectation(
            new OpenAPIDefinition()
                .withSpecUrlOrPayload("---" + NEW_LINE +
                    "openapi: 3.0.0" + NEW_LINE +
                    "paths:" + NEW_LINE +
                    "  \"/somePath\":" + NEW_LINE +
                    "    get:" + NEW_LINE +
                    "      operationId: someOperation" + NEW_LINE +
                    "      parameters:" + NEW_LINE +
                    "        - in: header" + NEW_LINE +
                    "          name: someParam" + NEW_LINE +
                    "          required: false" + NEW_LINE +
                    "          schema:" + NEW_LINE +
                    "            type: integer" + NEW_LINE +
                    "            minimum: 1")
        ));

        // then
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withHeader("someParam", "1")
        ), is(true));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withHeader("someOtherParam", "1")
        ), is(true));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withHeader("someOtherParam", "0")
        ), is(true));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withHeader("someParam", "0")
        ), is(false));
    }

    @Test
    public void shouldMatchByOptionalNotSpecifiedHeader() {
        // given
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(configuration, mockServerLogger);

        // when
        httpRequestsPropertiesMatcher.update(new Expectation(
            new OpenAPIDefinition()
                .withSpecUrlOrPayload("---" + NEW_LINE +
                    "openapi: 3.0.0" + NEW_LINE +
                    "paths:" + NEW_LINE +
                    "  \"/somePath\":" + NEW_LINE +
                    "    get:" + NEW_LINE +
                    "      operationId: someOperation" + NEW_LINE +
                    "      parameters:" + NEW_LINE +
                    "        - in: header" + NEW_LINE +
                    "          name: someParam" + NEW_LINE +
                    "          schema:" + NEW_LINE +
                    "            type: integer" + NEW_LINE +
                    "            minimum: 1")
        ));

        // then
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withHeader("someParam", "1")
        ), is(true));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withHeader("someOtherParam", "1")
        ), is(true));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withHeader("someOtherParam", "0")
        ), is(true));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withHeader("someParam", "0")
        ), is(false));
    }

    @Test
    public void shouldMatchHeaderWithAllowEmpty() {
        // given
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(configuration, mockServerLogger);

        // when
        httpRequestsPropertiesMatcher.update(new Expectation(
            new OpenAPIDefinition()
                .withSpecUrlOrPayload("---" + NEW_LINE +
                    "openapi: 3.0.0" + NEW_LINE +
                    "paths:" + NEW_LINE +
                    "  \"/somePath\":" + NEW_LINE +
                    "    get:" + NEW_LINE +
                    "      operationId: someOperation" + NEW_LINE +
                    "      parameters:" + NEW_LINE +
                    "        - in: header" + NEW_LINE +
                    "          name: someParam" + NEW_LINE +
                    "          required: true" + NEW_LINE +
                    "          allowEmptyValue: true" + NEW_LINE +
                    "          schema:" + NEW_LINE +
                    "            type: integer" + NEW_LINE +
                    "            minimum: 1")
        ));

        // then - allowEmptyValue not supported for header
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withHeader("someParam", "1")
        ), is(true));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withHeader("someParam", "")
        ), is(false));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withHeader("someOtherParam", "1")
        ), is(false));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withHeader("someParam", "0")
        ), is(false));
    }

    @Test
    public void shouldMatchByHeaderCommonForAllPaths() {
        // given
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(configuration, mockServerLogger);

        // when
        httpRequestsPropertiesMatcher.update(new Expectation(
            new OpenAPIDefinition()
                .withSpecUrlOrPayload("---" + NEW_LINE +
                    "openapi: 3.0.0" + NEW_LINE +
                    "components:" + NEW_LINE +
                    "  parameters:" + NEW_LINE +
                    "    someParam:" + NEW_LINE +
                    "      in: header" + NEW_LINE +
                    "      name: someParam" + NEW_LINE +
                    "      required: true" + NEW_LINE +
                    "      schema:" + NEW_LINE +
                    "        type: integer" + NEW_LINE +
                    "        minimum: 1" + NEW_LINE +
                    "    someOtherParam:" + NEW_LINE +
                    "      in: header" + NEW_LINE +
                    "      name: someOtherParam" + NEW_LINE +
                    "      required: true" + NEW_LINE +
                    "      schema:" + NEW_LINE +
                    "        type: string" + NEW_LINE +
                    "        minLength: 2" + NEW_LINE +
                    "        maxLength: 3" + NEW_LINE +
                    "paths:" + NEW_LINE +
                    "  \"/somePath\":" + NEW_LINE +
                    "    parameters:" + NEW_LINE +
                    "      - $ref: '#/components/parameters/someParam'" + NEW_LINE +
                    "    get:" + NEW_LINE +
                    "      operationId: someOperation" + NEW_LINE +
                    "      parameters:" + NEW_LINE +
                    "        - $ref: '#/components/parameters/someOtherParam'" + NEW_LINE)
        ));

        // then
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withHeader("someParam", "1")
                .withHeader("someOtherParam", "ab")
        ), is(true));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withHeader("someParam", "0")
                .withHeader("someOtherParam", "ab")
        ), is(false));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withHeader("someParam", "1")
                .withHeader("someOtherParam", "a")
        ), is(false));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withHeader("someOtherParam", "1")
        ), is(false));
    }

    @Test
    public void shouldMatchByHeaderCommonForPath() {
        // given
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(configuration, mockServerLogger);

        // when
        httpRequestsPropertiesMatcher.update(new Expectation(
            new OpenAPIDefinition()
                .withSpecUrlOrPayload("---" + NEW_LINE +
                    "openapi: 3.0.0" + NEW_LINE +
                    "paths:" + NEW_LINE +
                    "  \"/somePath\":" + NEW_LINE +
                    "    parameters:" + NEW_LINE +
                    "      - in: header" + NEW_LINE +
                    "        name: someParam" + NEW_LINE +
                    "        required: true" + NEW_LINE +
                    "        schema:" + NEW_LINE +
                    "          type: integer" + NEW_LINE +
                    "          minimum: 1" + NEW_LINE +
                    "    get:" + NEW_LINE +
                    "      operationId: someOperation" + NEW_LINE)
        ));

        // then
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withHeader("someParam", "1")
        ), is(true));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withHeader("someParam", "0")
        ), is(false));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withHeader("someOtherParam", "1")
        ), is(false));
    }

    @Test
    public void shouldMatchByHeaderCommonForAllPathsOverriddenAtMethod() {
        // given
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(configuration, mockServerLogger);

        // when
        httpRequestsPropertiesMatcher.update(new Expectation(
            new OpenAPIDefinition()
                .withSpecUrlOrPayload("---" + NEW_LINE +
                    "openapi: 3.0.0" + NEW_LINE +
                    "components:" + NEW_LINE +
                    "  parameters:" + NEW_LINE +
                    "    someParam:" + NEW_LINE +
                    "      in: header" + NEW_LINE +
                    "      name: someParam" + NEW_LINE +
                    "      required: true" + NEW_LINE +
                    "      schema:" + NEW_LINE +
                    "        type: integer" + NEW_LINE +
                    "        minimum: 1" + NEW_LINE +
                    "    someOtherParam:" + NEW_LINE +
                    "      in: header" + NEW_LINE +
                    "      name: someOtherParam" + NEW_LINE +
                    "      required: true" + NEW_LINE +
                    "      schema:" + NEW_LINE +
                    "        type: string" + NEW_LINE +
                    "        minLength: 2" + NEW_LINE +
                    "        maxLength: 3" + NEW_LINE +
                    "paths:" + NEW_LINE +
                    "  \"/somePath\":" + NEW_LINE +
                    "    parameters:" + NEW_LINE +
                    "      - in: header" + NEW_LINE +
                    "        name: someParam" + NEW_LINE +
                    "        required: true" + NEW_LINE +
                    "        schema:" + NEW_LINE +
                    "          type: integer" + NEW_LINE +
                    "          minimum: 10" + NEW_LINE +
                    "    get:" + NEW_LINE +
                    "      operationId: someOperation" + NEW_LINE +
                    "      parameters:" + NEW_LINE +
                    "        - in: header" + NEW_LINE +
                    "          name: someParam" + NEW_LINE +
                    "          required: true" + NEW_LINE +
                    "          schema:" + NEW_LINE +
                    "            type: integer" + NEW_LINE +
                    "            minimum: 100" + NEW_LINE +
                    "        - $ref: '#/components/parameters/someOtherParam'" + NEW_LINE)
        ));

        // then
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withHeader("someParam", "100")
                .withHeader("someOtherParam", "ab")
        ), is(true));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withHeader("someParam", "50")
                .withHeader("someOtherParam", "ab")
        ), is(false));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withHeader("someParam", "100")
                .withHeader("someOtherParam", "a")
        ), is(false));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withHeader("someOtherParam", "1")
        ), is(false));
    }

    // COOKIES

    @Test
    public void shouldMatchByCookie() {
        // given
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(configuration, mockServerLogger);

        // when
        httpRequestsPropertiesMatcher.update(new Expectation(
            new OpenAPIDefinition()
                .withSpecUrlOrPayload("---" + NEW_LINE +
                    "openapi: 3.0.0" + NEW_LINE +
                    "paths:" + NEW_LINE +
                    "  \"/somePath\":" + NEW_LINE +
                    "    get:" + NEW_LINE +
                    "      operationId: someOperation" + NEW_LINE +
                    "      parameters:" + NEW_LINE +
                    "        - in: cookie" + NEW_LINE +
                    "          name: someParam" + NEW_LINE +
                    "          required: true" + NEW_LINE +
                    "          schema:" + NEW_LINE +
                    "            type: integer" + NEW_LINE +
                    "            minimum: 1")
        ));

        // then
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withCookie("someParam", "1")
        ), is(true));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withCookie("someParam", "0")
        ), is(false));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withCookie("someOtherParam", "1")
        ), is(false));
    }

    @Test
    public void shouldMatchByCookieWithOperationId() {
        // given
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(configuration, mockServerLogger);

        // when
        httpRequestsPropertiesMatcher.update(new Expectation(
            new OpenAPIDefinition()
                .withSpecUrlOrPayload("---" + NEW_LINE +
                    "openapi: 3.0.0" + NEW_LINE +
                    "paths:" + NEW_LINE +
                    "  \"/somePath\":" + NEW_LINE +
                    "    get:" + NEW_LINE +
                    "      operationId: someOperation" + NEW_LINE +
                    "      parameters:" + NEW_LINE +
                    "        - in: cookie" + NEW_LINE +
                    "          name: someParam" + NEW_LINE +
                    "          required: true" + NEW_LINE +
                    "          schema:" + NEW_LINE +
                    "            type: integer" + NEW_LINE +
                    "            minimum: 1")
                .withOperationId("someOperation")
        ));

        // then
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withCookie("someParam", "1")
        ), is(true));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withCookie("someParam", "0")
        ), is(false));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withCookie("someOtherParam", "1")
        ), is(false));
    }

    @Test
    public void shouldMatchByOptionalCookie() {
        // given
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(configuration, mockServerLogger);

        // when
        httpRequestsPropertiesMatcher.update(new Expectation(
            new OpenAPIDefinition()
                .withSpecUrlOrPayload("---" + NEW_LINE +
                    "openapi: 3.0.0" + NEW_LINE +
                    "paths:" + NEW_LINE +
                    "  \"/somePath\":" + NEW_LINE +
                    "    get:" + NEW_LINE +
                    "      operationId: someOperation" + NEW_LINE +
                    "      parameters:" + NEW_LINE +
                    "        - in: cookie" + NEW_LINE +
                    "          name: someParam" + NEW_LINE +
                    "          required: false" + NEW_LINE +
                    "          schema:" + NEW_LINE +
                    "            type: integer" + NEW_LINE +
                    "            minimum: 1")
        ));

        // then
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withCookie("someParam", "1")
        ), is(true));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withCookie("someOtherParam", "1")
        ), is(true));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withCookie("someOtherParam", "0")
        ), is(true));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withCookie("someParam", "0")
        ), is(false));
    }

    @Test
    public void shouldMatchByOptionalNotSpecifiedCookie() {
        // given
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(configuration, mockServerLogger);

        // when
        httpRequestsPropertiesMatcher.update(new Expectation(
            new OpenAPIDefinition()
                .withSpecUrlOrPayload("---" + NEW_LINE +
                    "openapi: 3.0.0" + NEW_LINE +
                    "paths:" + NEW_LINE +
                    "  \"/somePath\":" + NEW_LINE +
                    "    get:" + NEW_LINE +
                    "      operationId: someOperation" + NEW_LINE +
                    "      parameters:" + NEW_LINE +
                    "        - in: cookie" + NEW_LINE +
                    "          name: someParam" + NEW_LINE +
                    "          schema:" + NEW_LINE +
                    "            type: integer" + NEW_LINE +
                    "            minimum: 1")
        ));

        // then
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withCookie("someParam", "1")
        ), is(true));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withCookie("someOtherParam", "1")
        ), is(true));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withCookie("someOtherParam", "0")
        ), is(true));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withCookie("someParam", "0")
        ), is(false));
    }

    @Test
    public void shouldMatchCookieWithAllowEmpty() {
        // given
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(configuration, mockServerLogger);

        // when
        httpRequestsPropertiesMatcher.update(new Expectation(
            new OpenAPIDefinition()
                .withSpecUrlOrPayload("---" + NEW_LINE +
                    "openapi: 3.0.0" + NEW_LINE +
                    "paths:" + NEW_LINE +
                    "  \"/somePath\":" + NEW_LINE +
                    "    get:" + NEW_LINE +
                    "      operationId: someOperation" + NEW_LINE +
                    "      parameters:" + NEW_LINE +
                    "        - in: cookie" + NEW_LINE +
                    "          name: someParam" + NEW_LINE +
                    "          required: true" + NEW_LINE +
                    "          allowEmptyValue: true" + NEW_LINE +
                    "          schema:" + NEW_LINE +
                    "            type: integer" + NEW_LINE +
                    "            minimum: 1")
        ));

        // then - allowEmptyValue not supported for cookie
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withCookie("someParam", "1")
        ), is(true));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withCookie("someParam", "")
        ), is(false));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withCookie("someOtherParam", "1")
        ), is(false));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withCookie("someParam", "0")
        ), is(false));
    }

    @Test
    public void shouldMatchByCookieCommonForPath() {
        // given
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(configuration, mockServerLogger);

        // when
        httpRequestsPropertiesMatcher.update(new Expectation(
            new OpenAPIDefinition()
                .withSpecUrlOrPayload("---" + NEW_LINE +
                    "openapi: 3.0.0" + NEW_LINE +
                    "paths:" + NEW_LINE +
                    "  \"/somePath\":" + NEW_LINE +
                    "    parameters:" + NEW_LINE +
                    "      - in: cookie" + NEW_LINE +
                    "        name: someParam" + NEW_LINE +
                    "        required: true" + NEW_LINE +
                    "        schema:" + NEW_LINE +
                    "          type: integer" + NEW_LINE +
                    "          minimum: 1" + NEW_LINE +
                    "    get:" + NEW_LINE +
                    "      operationId: someOperation" + NEW_LINE)
        ));

        // then
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withCookie("someParam", "1")
        ), is(true));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withCookie("someParam", "0")
        ), is(false));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withCookie("someOtherParam", "1")
        ), is(false));
    }

    @Test
    public void shouldMatchByCookieCommonForAllPaths() {
        // given
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(configuration, mockServerLogger);

        // when
        httpRequestsPropertiesMatcher.update(new Expectation(
            new OpenAPIDefinition()
                .withSpecUrlOrPayload("---" + NEW_LINE +
                    "openapi: 3.0.0" + NEW_LINE +
                    "components:" + NEW_LINE +
                    "  parameters:" + NEW_LINE +
                    "    someParam:" + NEW_LINE +
                    "      in: cookie" + NEW_LINE +
                    "      name: someParam" + NEW_LINE +
                    "      required: true" + NEW_LINE +
                    "      schema:" + NEW_LINE +
                    "        type: integer" + NEW_LINE +
                    "        minimum: 1" + NEW_LINE +
                    "    someOtherParam:" + NEW_LINE +
                    "      in: cookie" + NEW_LINE +
                    "      name: someOtherParam" + NEW_LINE +
                    "      required: true" + NEW_LINE +
                    "      schema:" + NEW_LINE +
                    "        type: string" + NEW_LINE +
                    "        minLength: 2" + NEW_LINE +
                    "        maxLength: 3" + NEW_LINE +
                    "paths:" + NEW_LINE +
                    "  \"/somePath\":" + NEW_LINE +
                    "    parameters:" + NEW_LINE +
                    "      - $ref: '#/components/parameters/someParam'" + NEW_LINE +
                    "    get:" + NEW_LINE +
                    "      operationId: someOperation" + NEW_LINE +
                    "      parameters:" + NEW_LINE +
                    "        - $ref: '#/components/parameters/someOtherParam'" + NEW_LINE)
        ));

        // then
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withCookie("someParam", "1")
                .withCookie("someOtherParam", "ab")
        ), is(true));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withCookie("someParam", "0")
                .withCookie("someOtherParam", "ab")
        ), is(false));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withCookie("someParam", "1")
                .withCookie("someOtherParam", "a")
        ), is(false));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withCookie("someOtherParam", "1")
        ), is(false));
    }

    @Test
    public void shouldMatchByCookieCommonForAllPathsOverriddenAtMethod() {
        // given
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(configuration, mockServerLogger);

        // when
        httpRequestsPropertiesMatcher.update(new Expectation(
            new OpenAPIDefinition()
                .withSpecUrlOrPayload("---" + NEW_LINE +
                    "openapi: 3.0.0" + NEW_LINE +
                    "components:" + NEW_LINE +
                    "  parameters:" + NEW_LINE +
                    "    someParam:" + NEW_LINE +
                    "      in: cookie" + NEW_LINE +
                    "      name: someParam" + NEW_LINE +
                    "      required: true" + NEW_LINE +
                    "      schema:" + NEW_LINE +
                    "        type: integer" + NEW_LINE +
                    "        minimum: 1" + NEW_LINE +
                    "    someOtherParam:" + NEW_LINE +
                    "      in: cookie" + NEW_LINE +
                    "      name: someOtherParam" + NEW_LINE +
                    "      required: true" + NEW_LINE +
                    "      schema:" + NEW_LINE +
                    "        type: string" + NEW_LINE +
                    "        minLength: 2" + NEW_LINE +
                    "        maxLength: 3" + NEW_LINE +
                    "paths:" + NEW_LINE +
                    "  \"/somePath\":" + NEW_LINE +
                    "    parameters:" + NEW_LINE +
                    "      - in: cookie" + NEW_LINE +
                    "        name: someParam" + NEW_LINE +
                    "        required: true" + NEW_LINE +
                    "        schema:" + NEW_LINE +
                    "          type: integer" + NEW_LINE +
                    "          minimum: 10" + NEW_LINE +
                    "    get:" + NEW_LINE +
                    "      operationId: someOperation" + NEW_LINE +
                    "      parameters:" + NEW_LINE +
                    "        - in: cookie" + NEW_LINE +
                    "          name: someParam" + NEW_LINE +
                    "          required: true" + NEW_LINE +
                    "          schema:" + NEW_LINE +
                    "            type: integer" + NEW_LINE +
                    "            minimum: 100" + NEW_LINE +
                    "        - $ref: '#/components/parameters/someOtherParam'" + NEW_LINE)
        ));

        // then
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withCookie("someParam", "100")
                .withCookie("someOtherParam", "ab")
        ), is(true));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withCookie("someParam", "50")
                .withCookie("someOtherParam", "ab")
        ), is(false));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withCookie("someParam", "100")
                .withCookie("someOtherParam", "a")
        ), is(false));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withCookie("someOtherParam", "1")
        ), is(false));
    }

    // BODY

    @Test
    public void shouldBuildMatcherForRequestBodyWithMultipartForm() {
        // given
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(configuration, mockServerLogger);

        // when
        ArrayList<LogEntry> logEntries = new ArrayList<>();
        OpenAPIDefinition requestDefinition = new OpenAPIDefinition()
            .withSpecUrlOrPayload("---" + NEW_LINE +
                "openapi: 3.0.0" + NEW_LINE +
                "paths:" + NEW_LINE +
                "  \"/somePath\":" + NEW_LINE +
                "    post:" + NEW_LINE +
                "      operationId: someOperation" + NEW_LINE +
                "      requestBody:" + NEW_LINE +
                "        required: true" + NEW_LINE +
                "        content:" + NEW_LINE +
                "          multipart/form-data:" + NEW_LINE +
                "            schema:" + NEW_LINE +
                "              type: object" + NEW_LINE +
                "              required:" + NEW_LINE +
                "                - id" + NEW_LINE +
                "                - name" + NEW_LINE +
                "              properties:" + NEW_LINE +
                "                id:" + NEW_LINE +
                "                  type: integer" + NEW_LINE +
                "                  format: int64" + NEW_LINE +
                "                name:" + NEW_LINE +
                "                  type: string" + NEW_LINE +
                "          application/xml:" + NEW_LINE +
                "            schema:" + NEW_LINE +
                "              type: object" + NEW_LINE +
                "              required:" + NEW_LINE +
                "                - id" + NEW_LINE +
                "                - name" + NEW_LINE +
                "              properties:" + NEW_LINE +
                "                id:" + NEW_LINE +
                "                  type: integer" + NEW_LINE +
                "                  format: int64" + NEW_LINE +
                "                name:" + NEW_LINE +
                "                  type: string" + NEW_LINE);
        httpRequestsPropertiesMatcher.apply(requestDefinition, logEntries);

        // then - multipart/form-data now builds a multipart field matcher (no longer unsupported), plus the XML one
        assertThat(httpRequestsPropertiesMatcher.getHttpRequestPropertiesMatchers().size(), equalTo(2));
        assertThat(logEntries.size(), equalTo(0));
    }

    @Test
    public void shouldMatchByPathAndMethodWhenOnlyMultipartFormData() {
        // given
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(configuration, mockServerLogger);

        // when
        httpRequestsPropertiesMatcher.update(new Expectation(
            new OpenAPIDefinition()
                .withSpecUrlOrPayload("---" + NEW_LINE +
                    "openapi: 3.0.0" + NEW_LINE +
                    "paths:" + NEW_LINE +
                    "  \"/somePath\":" + NEW_LINE +
                    "    post:" + NEW_LINE +
                    "      operationId: someOperation" + NEW_LINE +
                    "      requestBody:" + NEW_LINE +
                    "        required: true" + NEW_LINE +
                    "        content:" + NEW_LINE +
                    "          multipart/form-data:" + NEW_LINE +
                    "            schema:" + NEW_LINE +
                    "              type: object" + NEW_LINE +
                    "              properties:" + NEW_LINE +
                    "                file:" + NEW_LINE +
                    "                  type: string" + NEW_LINE +
                    "                  format: binary" + NEW_LINE)
        ));

        // then - should match correct path and method
        assertThat(httpRequestsPropertiesMatcher.matches(
            null,
            request()
                .withMethod("POST")
                .withPath("/somePath")
        ), is(true));

        // then - should NOT match wrong path and wrong method
        assertThat(httpRequestsPropertiesMatcher.matches(
            null,
            request()
                .withMethod("GET")
                .withPath("/idontexist")
        ), is(false));

        // then - should NOT match wrong path with correct method
        assertThat(httpRequestsPropertiesMatcher.matches(
            null,
            request()
                .withMethod("POST")
                .withPath("/idontexist")
        ), is(false));

        // then - should NOT match wrong method with correct path
        assertThat(httpRequestsPropertiesMatcher.matches(
            null,
            request()
                .withMethod("GET")
                .withPath("/somePath")
        ), is(false));
    }

    // - JSON BODY (via JsonSchema)

    @Test
    public void shouldMatchByJsonBody() {
        // given
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(configuration, mockServerLogger);

        // when
        httpRequestsPropertiesMatcher.update(new Expectation(
            new OpenAPIDefinition()
                .withSpecUrlOrPayload("---" + NEW_LINE +
                    "openapi: 3.0.0" + NEW_LINE +
                    "paths:" + NEW_LINE +
                    "  \"/somePath\":" + NEW_LINE +
                    "    post:" + NEW_LINE +
                    "      operationId: someOperation" + NEW_LINE +
                    "      requestBody:" + NEW_LINE +
                    "        required: true" + NEW_LINE +
                    "        content:" + NEW_LINE +
                    "          application/json:" + NEW_LINE +
                    "            schema:" + NEW_LINE +
                    "              type: object" + NEW_LINE +
                    "              required:" + NEW_LINE +
                    "                - id" + NEW_LINE +
                    "                - name" + NEW_LINE +
                    "              properties:" + NEW_LINE +
                    "                id:" + NEW_LINE +
                    "                  type: integer" + NEW_LINE +
                    "                  format: int64" + NEW_LINE +
                    "                name:" + NEW_LINE +
                    "                  type: string" + NEW_LINE +
                    "          application/xml:" + NEW_LINE +
                    "            schema:" + NEW_LINE +
                    "              type: object" + NEW_LINE +
                    "              required:" + NEW_LINE +
                    "                - id" + NEW_LINE +
                    "                - name" + NEW_LINE +
                    "              properties:" + NEW_LINE +
                    "                id:" + NEW_LINE +
                    "                  type: integer" + NEW_LINE +
                    "                  format: int64" + NEW_LINE +
                    "                name:" + NEW_LINE +
                    "                  type: string" + NEW_LINE)
        ));

        // then
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withHeader("content-type", "application/json")
                .withBody(json("{ \"id\": 1, \"name\": \"abc\" }"))
        ), is(true));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withHeader("content-type", "application/json; charset=utf-8")
                .withBody(json("{ \"id\": 1, \"name\": \"abc\" }"))
        ), is(true));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withHeader("content-type", "application/json")
                .withBody(json("{ \"id\": 1, \"name\": 1 }"))
        ), is(false));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withHeader("content-type", "application/json")
                .withBody(json("{ \"id\": \"abc\", \"name\": \"abc\" }"))
        ), is(false));
    }

    @Test
    public void shouldMatchByJsonBodyWithMediaTypeRange() {
        // given
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(configuration, mockServerLogger);

        // when
        httpRequestsPropertiesMatcher.update(new Expectation(
            new OpenAPIDefinition()
                .withSpecUrlOrPayload("---" + NEW_LINE +
                    "openapi: 3.0.0" + NEW_LINE +
                    "paths:" + NEW_LINE +
                    "  \"/somePath\":" + NEW_LINE +
                    "    post:" + NEW_LINE +
                    "      operationId: someOperation" + NEW_LINE +
                    "      requestBody:" + NEW_LINE +
                    "        required: true" + NEW_LINE +
                    "        content:" + NEW_LINE +
                    "          application/*:" + NEW_LINE +
                    "            schema:" + NEW_LINE +
                    "              type: object" + NEW_LINE +
                    "              required:" + NEW_LINE +
                    "                - id" + NEW_LINE +
                    "                - name" + NEW_LINE +
                    "              properties:" + NEW_LINE +
                    "                id:" + NEW_LINE +
                    "                  type: integer" + NEW_LINE +
                    "                  format: int64" + NEW_LINE +
                    "                name:" + NEW_LINE +
                    "                  type: string" + NEW_LINE +
                    "          application/xml:" + NEW_LINE +
                    "            schema:" + NEW_LINE +
                    "              type: object" + NEW_LINE +
                    "              required:" + NEW_LINE +
                    "                - id" + NEW_LINE +
                    "                - name" + NEW_LINE +
                    "              properties:" + NEW_LINE +
                    "                id:" + NEW_LINE +
                    "                  type: integer" + NEW_LINE +
                    "                  format: int64" + NEW_LINE +
                    "                name:" + NEW_LINE +
                    "                  type: string" + NEW_LINE)
        ));

        // then
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withHeader("content-type", "application/json")
                .withBody(json("{ \"id\": 1, \"name\": \"abc\" }"))
        ), is(true));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withHeader("content-type", "application/json")
                .withBody(json("{ \"id\": 1, \"name\": 1 }"))
        ), is(false));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withHeader("content-type", "application/json")
                .withBody(json("{ \"id\": \"abc\", \"name\": \"abc\" }"))
        ), is(false));
    }

    @Test
    public void shouldMatchByJsonBodyWithDefaultMediaType() {
        // given
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(configuration, mockServerLogger);

        // when
        httpRequestsPropertiesMatcher.update(new Expectation(
            new OpenAPIDefinition()
                .withSpecUrlOrPayload("---" + NEW_LINE +
                    "openapi: 3.0.0" + NEW_LINE +
                    "paths:" + NEW_LINE +
                    "  \"/somePath\":" + NEW_LINE +
                    "    post:" + NEW_LINE +
                    "      operationId: someOperation" + NEW_LINE +
                    "      requestBody:" + NEW_LINE +
                    "        required: true" + NEW_LINE +
                    "        content:" + NEW_LINE +
                    "          \"*/*\":" + NEW_LINE +
                    "            schema:" + NEW_LINE +
                    "              type: object" + NEW_LINE +
                    "              required:" + NEW_LINE +
                    "                - id" + NEW_LINE +
                    "                - name" + NEW_LINE +
                    "              properties:" + NEW_LINE +
                    "                id:" + NEW_LINE +
                    "                  type: integer" + NEW_LINE +
                    "                  format: int64" + NEW_LINE +
                    "                name:" + NEW_LINE +
                    "                  type: string" + NEW_LINE +
                    "          application/xml:" + NEW_LINE +
                    "            schema:" + NEW_LINE +
                    "              type: object" + NEW_LINE +
                    "              required:" + NEW_LINE +
                    "                - id" + NEW_LINE +
                    "                - name" + NEW_LINE +
                    "              properties:" + NEW_LINE +
                    "                id:" + NEW_LINE +
                    "                  type: integer" + NEW_LINE +
                    "                  format: int64" + NEW_LINE +
                    "                name:" + NEW_LINE +
                    "                  type: string" + NEW_LINE)
        ));

        // then
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withHeader("content-type", "application/json")
                .withBody(json("{ \"id\": 1, \"name\": \"abc\" }"))
        ), is(true));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withHeader("content-type", "application/json")
                .withBody(json("{ \"id\": 1, \"name\": 1 }"))
        ), is(false));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withHeader("content-type", "application/json")
                .withBody(json("{ \"id\": \"abc\", \"name\": \"abc\" }"))
        ), is(false));
    }

    @Test
    public void shouldMatchByJsonBodyWithSchemaComponent() {
        // given
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(configuration, mockServerLogger);

        // when
        httpRequestsPropertiesMatcher.update(new Expectation(
            new OpenAPIDefinition()
                .withSpecUrlOrPayload("---" + NEW_LINE +
                    "openapi: 3.0.0" + NEW_LINE +
                    "paths:" + NEW_LINE +
                    "  \"/somePath\":" + NEW_LINE +
                    "    post:" + NEW_LINE +
                    "      operationId: someOperation" + NEW_LINE +
                    "      requestBody:" + NEW_LINE +
                    "        required: true" + NEW_LINE +
                    "        content:" + NEW_LINE +
                    "          application/json:" + NEW_LINE +
                    "            schema:" + NEW_LINE +
                    "              $ref: '#/components/schemas/Simple'" + NEW_LINE +
                    "          application/xml:" + NEW_LINE +
                    "            schema:" + NEW_LINE +
                    "              $ref: '#/components/schemas/Simple'" + NEW_LINE +
                    "components:" + NEW_LINE +
                    "  schemas:" + NEW_LINE +
                    "    Simple:" + NEW_LINE +
                    "      type: object" + NEW_LINE +
                    "      required:" + NEW_LINE +
                    "        - id" + NEW_LINE +
                    "        - name" + NEW_LINE +
                    "      properties:" + NEW_LINE +
                    "        id:" + NEW_LINE +
                    "          type: integer" + NEW_LINE +
                    "          format: int64" + NEW_LINE +
                    "        name:" + NEW_LINE +
                    "          type: string" + NEW_LINE)
        ));

        // then
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withHeader("content-type", "application/json")
                .withBody(json("{ \"id\": 1, \"name\": \"abc\" }"))
        ), is(true));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withHeader("content-type", "application/json")
                .withBody(json("{ \"id\": 1, \"name\": 1 }"))
        ), is(false));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withHeader("content-type", "application/json")
                .withBody(json("{ \"id\": \"abc\", \"name\": \"abc\" }"))
        ), is(false));
    }

    @Test
    public void shouldMatchByJsonBodyWithRequestBodyAndSchemaComponent() {
        // given
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(configuration, mockServerLogger);

        // when
        httpRequestsPropertiesMatcher.update(new Expectation(
            new OpenAPIDefinition()
                .withSpecUrlOrPayload("---" + NEW_LINE +
                    "openapi: 3.0.0" + NEW_LINE +
                    "paths:" + NEW_LINE +
                    "  \"/somePath\":" + NEW_LINE +
                    "    post:" + NEW_LINE +
                    "      operationId: someOperation" + NEW_LINE +
                    "      requestBody:" + NEW_LINE +
                    "        $ref: '#/components/requestBodies/SimpleBody'" + NEW_LINE +
                    "components:" + NEW_LINE +
                    "  requestBodies:" + NEW_LINE +
                    "    SimpleBody:" + NEW_LINE +
                    "      required: true" + NEW_LINE +
                    "      content:" + NEW_LINE +
                    "        application/json:" + NEW_LINE +
                    "          schema:" + NEW_LINE +
                    "            $ref: '#/components/schemas/Simple'" + NEW_LINE +
                    "        application/xml:" + NEW_LINE +
                    "          schema:" + NEW_LINE +
                    "            $ref: '#/components/schemas/Simple'" + NEW_LINE +
                    "  schemas:" + NEW_LINE +
                    "    Simple:" + NEW_LINE +
                    "      type: object" + NEW_LINE +
                    "      required:" + NEW_LINE +
                    "        - id" + NEW_LINE +
                    "        - name" + NEW_LINE +
                    "      properties:" + NEW_LINE +
                    "        id:" + NEW_LINE +
                    "          type: integer" + NEW_LINE +
                    "          format: int64" + NEW_LINE +
                    "        name:" + NEW_LINE +
                    "          type: string" + NEW_LINE)
        ));

        // then
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withHeader("content-type", "application/json")
                .withBody(json("{ \"id\": 1, \"name\": \"abc\" }"))
        ), is(true));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withHeader("content-type", "application/json")
                .withBody(json("{ \"id\": 1, \"name\": 1 }"))
        ), is(false));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withHeader("content-type", "application/json")
                .withBody(json("{ \"id\": \"abc\", \"name\": \"abc\" }"))
        ), is(false));
    }

    // - PLAIN TEXT BODY (via JsonSchema)

    @Test
    public void shouldMatchByPlainTextBody() {
        // given
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(configuration, mockServerLogger);

        // when
        httpRequestsPropertiesMatcher.update(new Expectation(
            new OpenAPIDefinition()
                .withSpecUrlOrPayload("---" + NEW_LINE +
                    "openapi: 3.0.0" + NEW_LINE +
                    "paths:" + NEW_LINE +
                    "  \"/somePath\":" + NEW_LINE +
                    "    post:" + NEW_LINE +
                    "      operationId: someOperation" + NEW_LINE +
                    "      requestBody:" + NEW_LINE +
                    "        required: true" + NEW_LINE +
                    "        content:" + NEW_LINE +
                    "          application/json:" + NEW_LINE +
                    "            schema:" + NEW_LINE +
                    "              type: object" + NEW_LINE +
                    "              required:" + NEW_LINE +
                    "                - id" + NEW_LINE +
                    "                - name" + NEW_LINE +
                    "              properties:" + NEW_LINE +
                    "                id:" + NEW_LINE +
                    "                  type: integer" + NEW_LINE +
                    "                  format: int64" + NEW_LINE +
                    "                name:" + NEW_LINE +
                    "                  type: string" + NEW_LINE +
                    "          plain/text:" + NEW_LINE +
                    "            schema:" + NEW_LINE +
                    "              type: object" + NEW_LINE +
                    "              required:" + NEW_LINE +
                    "                - id" + NEW_LINE +
                    "                - name" + NEW_LINE +
                    "              properties:" + NEW_LINE +
                    "                id:" + NEW_LINE +
                    "                  type: integer" + NEW_LINE +
                    "                  format: int64" + NEW_LINE +
                    "                name:" + NEW_LINE +
                    "                  type: string" + NEW_LINE)
        ));

        // then
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withHeader("content-type", "plain/text")
                .withBody(exact("{ \"id\": 1, \"name\": \"abc\" }"))
        ), is(true));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withHeader("content-type", "plain/text; charset=utf-8")
                .withBody(exact("{ \"id\": 1, \"name\": \"abc\" }"))
        ), is(true));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withHeader("content-type", "plain/text")
                .withBody(exact("{ \"id\": 1, \"name\": 1 }"))
        ), is(false));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withHeader("content-type", "plain/text")
                .withBody(exact("{ \"id\": \"abc\", \"name\": \"abc\" }"))
        ), is(false));
    }

    @Test
    public void shouldMatchByPlainTextBodyWithRequiredFalse() {
        // given
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(configuration, mockServerLogger);

        // when
        httpRequestsPropertiesMatcher.update(new Expectation(
            new OpenAPIDefinition()
                .withSpecUrlOrPayload("---" + NEW_LINE +
                    "openapi: 3.0.0" + NEW_LINE +
                    "paths:" + NEW_LINE +
                    "  \"/somePath\":" + NEW_LINE +
                    "    post:" + NEW_LINE +
                    "      operationId: someOperation" + NEW_LINE +
                    "      requestBody:" + NEW_LINE +
                    "        required: false" + NEW_LINE +
                    "        content:" + NEW_LINE +
                    "          application/json:" + NEW_LINE +
                    "            schema:" + NEW_LINE +
                    "              type: object" + NEW_LINE +
                    "              required:" + NEW_LINE +
                    "                - id" + NEW_LINE +
                    "                - name" + NEW_LINE +
                    "              properties:" + NEW_LINE +
                    "                id:" + NEW_LINE +
                    "                  type: integer" + NEW_LINE +
                    "                  format: int64" + NEW_LINE +
                    "                name:" + NEW_LINE +
                    "                  type: string" + NEW_LINE +
                    "          plain/text:" + NEW_LINE +
                    "            schema:" + NEW_LINE +
                    "              type: object" + NEW_LINE +
                    "              required:" + NEW_LINE +
                    "                - id" + NEW_LINE +
                    "                - name" + NEW_LINE +
                    "              properties:" + NEW_LINE +
                    "                id:" + NEW_LINE +
                    "                  type: integer" + NEW_LINE +
                    "                  format: int64" + NEW_LINE +
                    "                name:" + NEW_LINE +
                    "                  type: string" + NEW_LINE)
        ));

        // then
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath")
                .withHeader("content-type", "plain/text")
                .withBody(exact("{ \"id\": 1, \"name\": \"abc\" }"))
        ), is(true));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath")
                .withHeader("content-type", "plain/text; charset=utf-8")
                .withBody(exact("{ \"id\": 1, \"name\": \"abc\" }"))
        ), is(true));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath")
        ), is(true));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath")
                .withHeader("content-type", "plain/text")
        ), is(true));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath")
                .withHeader("content-type", "plain/text")
                .withBody(exact("{ \"id\": 1, \"name\": 1 }"))
        ), is(false));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath")
                .withHeader("content-type", "plain/text")
                .withBody(exact("{ \"id\": \"abc\", \"name\": \"abc\" }"))
        ), is(false));
    }

    @Test
    public void shouldMatchByPlainTextBodyWithRequiredDefaulted() {
        // given
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(configuration, mockServerLogger);

        // when
        httpRequestsPropertiesMatcher.update(new Expectation(
            new OpenAPIDefinition()
                .withSpecUrlOrPayload("---" + NEW_LINE +
                    "openapi: 3.0.0" + NEW_LINE +
                    "paths:" + NEW_LINE +
                    "  \"/somePath\":" + NEW_LINE +
                    "    post:" + NEW_LINE +
                    "      operationId: someOperation" + NEW_LINE +
                    "      requestBody:" + NEW_LINE +
                    "        content:" + NEW_LINE +
                    "          application/json:" + NEW_LINE +
                    "            schema:" + NEW_LINE +
                    "              type: object" + NEW_LINE +
                    "              required:" + NEW_LINE +
                    "                - id" + NEW_LINE +
                    "                - name" + NEW_LINE +
                    "              properties:" + NEW_LINE +
                    "                id:" + NEW_LINE +
                    "                  type: integer" + NEW_LINE +
                    "                  format: int64" + NEW_LINE +
                    "                name:" + NEW_LINE +
                    "                  type: string" + NEW_LINE +
                    "          plain/text:" + NEW_LINE +
                    "            schema:" + NEW_LINE +
                    "              type: object" + NEW_LINE +
                    "              required:" + NEW_LINE +
                    "                - id" + NEW_LINE +
                    "                - name" + NEW_LINE +
                    "              properties:" + NEW_LINE +
                    "                id:" + NEW_LINE +
                    "                  type: integer" + NEW_LINE +
                    "                  format: int64" + NEW_LINE +
                    "                name:" + NEW_LINE +
                    "                  type: string" + NEW_LINE)
        ));

        // then
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath")
                .withHeader("content-type", "plain/text")
                .withBody(exact("{ \"id\": 1, \"name\": \"abc\" }"))
        ), is(true));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath")
                .withHeader("content-type", "plain/text; charset=utf-8")
                .withBody(exact("{ \"id\": 1, \"name\": \"abc\" }"))
        ), is(true));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath")
        ), is(true));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath")
                .withHeader("content-type", "plain/text")
        ), is(true));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath")
                .withHeader("content-type", "plain/text")
                .withBody(exact("{ \"id\": 1, \"name\": 1 }"))
        ), is(false));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath")
                .withHeader("content-type", "plain/text")
                .withBody(exact("{ \"id\": \"abc\", \"name\": \"abc\" }"))
        ), is(false));
    }

    @Test
    public void shouldMatchByPlainTextBodyWithMediaTypeRange() {
        // given
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(configuration, mockServerLogger);

        // when
        httpRequestsPropertiesMatcher.update(new Expectation(
            new OpenAPIDefinition()
                .withSpecUrlOrPayload("---" + NEW_LINE +
                    "openapi: 3.0.0" + NEW_LINE +
                    "paths:" + NEW_LINE +
                    "  \"/somePath\":" + NEW_LINE +
                    "    post:" + NEW_LINE +
                    "      operationId: someOperation" + NEW_LINE +
                    "      requestBody:" + NEW_LINE +
                    "        required: true" + NEW_LINE +
                    "        content:" + NEW_LINE +
                    "          application/json:" + NEW_LINE +
                    "            schema:" + NEW_LINE +
                    "              type: object" + NEW_LINE +
                    "              required:" + NEW_LINE +
                    "                - id" + NEW_LINE +
                    "                - name" + NEW_LINE +
                    "              properties:" + NEW_LINE +
                    "                id:" + NEW_LINE +
                    "                  type: integer" + NEW_LINE +
                    "                  format: int64" + NEW_LINE +
                    "                name:" + NEW_LINE +
                    "                  type: string" + NEW_LINE +
                    "          plain/*:" + NEW_LINE +
                    "            schema:" + NEW_LINE +
                    "              type: object" + NEW_LINE +
                    "              required:" + NEW_LINE +
                    "                - id" + NEW_LINE +
                    "                - name" + NEW_LINE +
                    "              properties:" + NEW_LINE +
                    "                id:" + NEW_LINE +
                    "                  type: integer" + NEW_LINE +
                    "                  format: int64" + NEW_LINE +
                    "                name:" + NEW_LINE +
                    "                  type: string" + NEW_LINE)
        ));

        // then
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withHeader("content-type", "plain/text")
                .withBody(exact("{ \"id\": 1, \"name\": \"abc\" }"))
        ), is(true));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withHeader("content-type", "plain/text")
                .withBody(exact("{ \"id\": 1, \"name\": 1 }"))
        ), is(false));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withHeader("content-type", "plain/text")
                .withBody(exact("{ \"id\": \"abc\", \"name\": \"abc\" }"))
        ), is(false));
    }

    @Test
    public void shouldMatchByPlainTextBodyWithDefaultMediaType() {
        // given
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(configuration, mockServerLogger);

        // when
        httpRequestsPropertiesMatcher.update(new Expectation(
            new OpenAPIDefinition()
                .withSpecUrlOrPayload("---" + NEW_LINE +
                    "openapi: 3.0.0" + NEW_LINE +
                    "paths:" + NEW_LINE +
                    "  \"/somePath\":" + NEW_LINE +
                    "    post:" + NEW_LINE +
                    "      operationId: someOperation" + NEW_LINE +
                    "      requestBody:" + NEW_LINE +
                    "        required: true" + NEW_LINE +
                    "        content:" + NEW_LINE +
                    "          application/json:" + NEW_LINE +
                    "            schema:" + NEW_LINE +
                    "              type: object" + NEW_LINE +
                    "              required:" + NEW_LINE +
                    "                - id" + NEW_LINE +
                    "                - name" + NEW_LINE +
                    "              properties:" + NEW_LINE +
                    "                id:" + NEW_LINE +
                    "                  type: integer" + NEW_LINE +
                    "                  format: int64" + NEW_LINE +
                    "                name:" + NEW_LINE +
                    "                  type: string" + NEW_LINE +
                    "          \"*/*\":" + NEW_LINE +
                    "            schema:" + NEW_LINE +
                    "              type: object" + NEW_LINE +
                    "              required:" + NEW_LINE +
                    "                - id" + NEW_LINE +
                    "                - name" + NEW_LINE +
                    "              properties:" + NEW_LINE +
                    "                id:" + NEW_LINE +
                    "                  type: integer" + NEW_LINE +
                    "                  format: int64" + NEW_LINE +
                    "                name:" + NEW_LINE +
                    "                  type: string" + NEW_LINE)
        ));

        // then
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withBody(exact("{ \"id\": 1, \"name\": \"abc\" }"))
        ), is(true));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withBody(exact("{ \"id\": 1, \"name\": 1 }"))
        ), is(false));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withBody(exact("{ \"id\": \"abc\", \"name\": \"abc\" }"))
        ), is(false));
    }

    @Test
    public void shouldMatchByPlainTextBodyWithSchemaComponent() {
        // given
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(configuration, mockServerLogger);

        // when
        httpRequestsPropertiesMatcher.update(new Expectation(
            new OpenAPIDefinition()
                .withSpecUrlOrPayload("---" + NEW_LINE +
                    "openapi: 3.0.0" + NEW_LINE +
                    "paths:" + NEW_LINE +
                    "  \"/somePath\":" + NEW_LINE +
                    "    post:" + NEW_LINE +
                    "      operationId: someOperation" + NEW_LINE +
                    "      requestBody:" + NEW_LINE +
                    "        required: true" + NEW_LINE +
                    "        content:" + NEW_LINE +
                    "          application/json:" + NEW_LINE +
                    "            schema:" + NEW_LINE +
                    "              $ref: '#/components/schemas/Simple'" + NEW_LINE +
                    "          plain/text:" + NEW_LINE +
                    "            schema:" + NEW_LINE +
                    "              $ref: '#/components/schemas/Simple'" + NEW_LINE +
                    "components:" + NEW_LINE +
                    "  schemas:" + NEW_LINE +
                    "    Simple:" + NEW_LINE +
                    "      type: object" + NEW_LINE +
                    "      required:" + NEW_LINE +
                    "        - id" + NEW_LINE +
                    "        - name" + NEW_LINE +
                    "      properties:" + NEW_LINE +
                    "        id:" + NEW_LINE +
                    "          type: integer" + NEW_LINE +
                    "          format: int64" + NEW_LINE +
                    "        name:" + NEW_LINE +
                    "          type: string" + NEW_LINE)
        ));

        // then
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withHeader("content-type", "plain/text")
                .withBody(exact("{ \"id\": 1, \"name\": \"abc\" }"))
        ), is(true));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withHeader("content-type", "plain/text")
                .withBody(exact("{ \"id\": 1, \"name\": 1 }"))
        ), is(false));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withHeader("content-type", "plain/text")
                .withBody(exact("{ \"id\": \"abc\", \"name\": \"abc\" }"))
        ), is(false));
    }

    @Test
    public void shouldMatchByPlainTextBodyWithRequestBodyAndSchemaComponent() {
        // given
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(configuration, mockServerLogger);

        // when
        httpRequestsPropertiesMatcher.update(new Expectation(
            new OpenAPIDefinition()
                .withSpecUrlOrPayload("---" + NEW_LINE +
                    "openapi: 3.0.0" + NEW_LINE +
                    "paths:" + NEW_LINE +
                    "  \"/somePath\":" + NEW_LINE +
                    "    post:" + NEW_LINE +
                    "      operationId: someOperation" + NEW_LINE +
                    "      requestBody:" + NEW_LINE +
                    "        $ref: '#/components/requestBodies/SimpleBody'" + NEW_LINE +
                    "components:" + NEW_LINE +
                    "  requestBodies:" + NEW_LINE +
                    "    SimpleBody:" + NEW_LINE +
                    "      required: true" + NEW_LINE +
                    "      content:" + NEW_LINE +
                    "        application/json:" + NEW_LINE +
                    "          schema:" + NEW_LINE +
                    "            $ref: '#/components/schemas/Simple'" + NEW_LINE +
                    "        plain/text:" + NEW_LINE +
                    "          schema:" + NEW_LINE +
                    "            $ref: '#/components/schemas/Simple'" + NEW_LINE +
                    "  schemas:" + NEW_LINE +
                    "    Simple:" + NEW_LINE +
                    "      type: object" + NEW_LINE +
                    "      required:" + NEW_LINE +
                    "        - id" + NEW_LINE +
                    "        - name" + NEW_LINE +
                    "      properties:" + NEW_LINE +
                    "        id:" + NEW_LINE +
                    "          type: integer" + NEW_LINE +
                    "          format: int64" + NEW_LINE +
                    "        name:" + NEW_LINE +
                    "          type: string" + NEW_LINE)
        ));

        // then
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withHeader("content-type", "plain/text")
                .withBody(exact("{ \"id\": 1, \"name\": \"abc\" }"))
        ), is(true));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withHeader("content-type", "plain/text")
                .withBody(exact("{ \"id\": 1, \"name\": 1 }"))
        ), is(false));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withHeader("content-type", "plain/text")
                .withBody(exact("{ \"id\": \"abc\", \"name\": \"abc\" }"))
        ), is(false));
    }

    // - XML BODY (via JsonSchema)

    @Test
    public void shouldMatchByXmlBody() {
        // given
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(configuration, mockServerLogger);

        // when
        httpRequestsPropertiesMatcher.update(new Expectation(
            new OpenAPIDefinition()
                .withSpecUrlOrPayload("---" + NEW_LINE +
                    "openapi: 3.0.0" + NEW_LINE +
                    "paths:" + NEW_LINE +
                    "  \"/somePath\":" + NEW_LINE +
                    "    post:" + NEW_LINE +
                    "      operationId: someOperation" + NEW_LINE +
                    "      requestBody:" + NEW_LINE +
                    "        required: true" + NEW_LINE +
                    "        content:" + NEW_LINE +
                    "          application/xml:" + NEW_LINE +
                    "            schema:" + NEW_LINE +
                    "              type: object" + NEW_LINE +
                    "              required:" + NEW_LINE +
                    "                - id" + NEW_LINE +
                    "                - name" + NEW_LINE +
                    "              properties:" + NEW_LINE +
                    "                id:" + NEW_LINE +
                    "                  type: integer" + NEW_LINE +
                    "                  format: int64" + NEW_LINE +
                    "                name:" + NEW_LINE +
                    "                  type: string" + NEW_LINE +
                    "          application/x-www-form-urlencoded:" + NEW_LINE +
                    "            schema:" + NEW_LINE +
                    "              type: object" + NEW_LINE +
                    "              properties:" + NEW_LINE +
                    "                name:" + NEW_LINE +
                    "                  type: string" + NEW_LINE +
                    "                email:" + NEW_LINE +
                    "                  type: integer" + NEW_LINE +
                    "                  format: email" + NEW_LINE +
                    "              required:" + NEW_LINE +
                    "                - name" + NEW_LINE +
                    "                - email'" + NEW_LINE +
                    "          text/plain:" + NEW_LINE +
                    "            schema:" + NEW_LINE +
                    "              type: string" + NEW_LINE)
        ));

        // then
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withHeader("content-type", "application/xml")
                .withBody(xml("<root><id>1</id><name>abc</name></root>"))
        ), is(true));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withHeader("content-type", "application/xml; charset=utf-8")
                .withBody(xml("<root><id>1</id><name>abc</name></root>"))
        ), is(true));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withHeader("content-type", "application/xml")
                .withBody(xml("<root><id>1</id><name>1</name></root>"))
        ), is(false));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withHeader("content-type", "application/xml")
                .withBody(xml("<root><id>abc</id><name>1</name></root>"))
        ), is(false));
    }

    @Test
    public void shouldMatchByXmlBodyWithRequiredFalse() {
        // given
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(configuration, mockServerLogger);

        // when
        httpRequestsPropertiesMatcher.update(new Expectation(
            new OpenAPIDefinition()
                .withSpecUrlOrPayload("---" + NEW_LINE +
                    "openapi: 3.0.0" + NEW_LINE +
                    "paths:" + NEW_LINE +
                    "  \"/somePath\":" + NEW_LINE +
                    "    post:" + NEW_LINE +
                    "      operationId: someOperation" + NEW_LINE +
                    "      requestBody:" + NEW_LINE +
                    "        required: false" + NEW_LINE +
                    "        content:" + NEW_LINE +
                    "          application/xml:" + NEW_LINE +
                    "            schema:" + NEW_LINE +
                    "              type: object" + NEW_LINE +
                    "              required:" + NEW_LINE +
                    "                - id" + NEW_LINE +
                    "                - name" + NEW_LINE +
                    "              properties:" + NEW_LINE +
                    "                id:" + NEW_LINE +
                    "                  type: integer" + NEW_LINE +
                    "                  format: int64" + NEW_LINE +
                    "                name:" + NEW_LINE +
                    "                  type: string" + NEW_LINE +
                    "          application/x-www-form-urlencoded:" + NEW_LINE +
                    "            schema:" + NEW_LINE +
                    "              type: object" + NEW_LINE +
                    "              properties:" + NEW_LINE +
                    "                name:" + NEW_LINE +
                    "                  type: string" + NEW_LINE +
                    "                email:" + NEW_LINE +
                    "                  type: integer" + NEW_LINE +
                    "                  format: email" + NEW_LINE +
                    "              required:" + NEW_LINE +
                    "                - name" + NEW_LINE +
                    "                - email'" + NEW_LINE +
                    "          text/plain:" + NEW_LINE +
                    "            schema:" + NEW_LINE +
                    "              type: string" + NEW_LINE)
        ));

        // then
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath")
                .withHeader("content-type", "application/xml")
                .withBody(xml("<root><id>1</id><name>abc</name></root>"))
        ), is(true));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath")
                .withHeader("content-type", "application/xml; charset=utf-8")
                .withBody(xml("<root><id>1</id><name>abc</name></root>"))
        ), is(true));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath")
        ), is(true));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath")
                .withHeader("content-type", "application/xml")
        ), is(true));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath")
                .withHeader("content-type", "application/xml")
                .withBody(xml("<root><id>1</id><name>1</name></root>"))
        ), is(false));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath")
                .withHeader("content-type", "application/xml")
                .withBody(xml("<root><id>abc</id><name>1</name></root>"))
        ), is(false));
    }

    @Test
    public void shouldMatchByXmlBodyWithRequiredDefaulted() {
        // given
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(configuration, mockServerLogger);

        // when
        httpRequestsPropertiesMatcher.update(new Expectation(
            new OpenAPIDefinition()
                .withSpecUrlOrPayload("---" + NEW_LINE +
                    "openapi: 3.0.0" + NEW_LINE +
                    "paths:" + NEW_LINE +
                    "  \"/somePath\":" + NEW_LINE +
                    "    post:" + NEW_LINE +
                    "      operationId: someOperation" + NEW_LINE +
                    "      requestBody:" + NEW_LINE +
                    "        content:" + NEW_LINE +
                    "          application/xml:" + NEW_LINE +
                    "            schema:" + NEW_LINE +
                    "              type: object" + NEW_LINE +
                    "              required:" + NEW_LINE +
                    "                - id" + NEW_LINE +
                    "                - name" + NEW_LINE +
                    "              properties:" + NEW_LINE +
                    "                id:" + NEW_LINE +
                    "                  type: integer" + NEW_LINE +
                    "                  format: int64" + NEW_LINE +
                    "                name:" + NEW_LINE +
                    "                  type: string" + NEW_LINE +
                    "          application/x-www-form-urlencoded:" + NEW_LINE +
                    "            schema:" + NEW_LINE +
                    "              type: object" + NEW_LINE +
                    "              properties:" + NEW_LINE +
                    "                name:" + NEW_LINE +
                    "                  type: string" + NEW_LINE +
                    "                email:" + NEW_LINE +
                    "                  type: integer" + NEW_LINE +
                    "                  format: email" + NEW_LINE +
                    "              required:" + NEW_LINE +
                    "                - name" + NEW_LINE +
                    "                - email'" + NEW_LINE +
                    "          text/plain:" + NEW_LINE +
                    "            schema:" + NEW_LINE +
                    "              type: string" + NEW_LINE)
        ));

        // then
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath")
                .withHeader("content-type", "application/xml")
                .withBody(xml("<root><id>1</id><name>abc</name></root>"))
        ), is(true));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath")
                .withHeader("content-type", "application/xml; charset=utf-8")
                .withBody(xml("<root><id>1</id><name>abc</name></root>"))
        ), is(true));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath")
        ), is(true));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath")
                .withHeader("content-type", "application/xml")
        ), is(true));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath")
                .withHeader("content-type", "application/xml")
                .withBody(xml("<root><id>1</id><name>1</name></root>"))
        ), is(false));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath")
                .withHeader("content-type", "application/xml")
                .withBody(xml("<root><id>abc</id><name>1</name></root>"))
        ), is(false));
    }

    @Test
    public void shouldMatchByXmlBodyWithSchemaComponent() {
        // given
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(configuration, mockServerLogger);

        // when
        httpRequestsPropertiesMatcher.update(new Expectation(
            new OpenAPIDefinition()
                .withSpecUrlOrPayload("---" + NEW_LINE +
                    "openapi: 3.0.0" + NEW_LINE +
                    "paths:" + NEW_LINE +
                    "  \"/somePath\":" + NEW_LINE +
                    "    post:" + NEW_LINE +
                    "      operationId: someOperation" + NEW_LINE +
                    "      requestBody:" + NEW_LINE +
                    "        required: true" + NEW_LINE +
                    "        content:" + NEW_LINE +
                    "          application/json:" + NEW_LINE +
                    "            schema:" + NEW_LINE +
                    "              $ref: '#/components/schemas/Simple'" + NEW_LINE +
                    "          application/xml:" + NEW_LINE +
                    "            schema:" + NEW_LINE +
                    "              $ref: '#/components/schemas/Simple'" + NEW_LINE +
                    "components:" + NEW_LINE +
                    "  schemas:" + NEW_LINE +
                    "    Simple:" + NEW_LINE +
                    "      type: object" + NEW_LINE +
                    "      required:" + NEW_LINE +
                    "        - id" + NEW_LINE +
                    "        - name" + NEW_LINE +
                    "      properties:" + NEW_LINE +
                    "        id:" + NEW_LINE +
                    "          type: integer" + NEW_LINE +
                    "          format: int64" + NEW_LINE +
                    "        name:" + NEW_LINE +
                    "          type: string" + NEW_LINE)
        ));

        // then
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withHeader("content-type", "application/xml")
                .withBody(xml("<root><id>1</id><name>abc</name></root>"))
        ), is(true));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withHeader("content-type", "application/xml")
                .withBody(xml("<root><id>1</id><name>1</name></root>"))
        ), is(false));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withHeader("content-type", "application/xml")
                .withBody(xml("<root><id>abc</id><name>1</name></root>"))
        ), is(false));
    }

    @Test
    public void shouldMatchByXmlBodyWithRequestBodyAndSchemaComponent() {
        // given
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(configuration, mockServerLogger);

        // when
        httpRequestsPropertiesMatcher.update(new Expectation(
            new OpenAPIDefinition()
                .withSpecUrlOrPayload("---" + NEW_LINE +
                    "openapi: 3.0.0" + NEW_LINE +
                    "paths:" + NEW_LINE +
                    "  \"/somePath\":" + NEW_LINE +
                    "    post:" + NEW_LINE +
                    "      operationId: someOperation" + NEW_LINE +
                    "      requestBody:" + NEW_LINE +
                    "        $ref: '#/components/requestBodies/SimpleBody'" + NEW_LINE +
                    "components:" + NEW_LINE +
                    "  requestBodies:" + NEW_LINE +
                    "    SimpleBody:" + NEW_LINE +
                    "      required: true" + NEW_LINE +
                    "      content:" + NEW_LINE +
                    "        application/json:" + NEW_LINE +
                    "          schema:" + NEW_LINE +
                    "            $ref: '#/components/schemas/Simple'" + NEW_LINE +
                    "        application/xml:" + NEW_LINE +
                    "          schema:" + NEW_LINE +
                    "            $ref: '#/components/schemas/Simple'" + NEW_LINE +
                    "  schemas:" + NEW_LINE +
                    "    Simple:" + NEW_LINE +
                    "      type: object" + NEW_LINE +
                    "      required:" + NEW_LINE +
                    "        - id" + NEW_LINE +
                    "        - name" + NEW_LINE +
                    "      properties:" + NEW_LINE +
                    "        id:" + NEW_LINE +
                    "          type: integer" + NEW_LINE +
                    "          format: int64" + NEW_LINE +
                    "        name:" + NEW_LINE +
                    "          type: string" + NEW_LINE)
        ));

        // then
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withHeader("content-type", "application/xml")
                .withBody(xml("<root><id>1</id><name>abc</name></root>"))
        ), is(true));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withHeader("content-type", "application/xml")
                .withBody(xml("<root><id>1</id><name>1</name></root>"))
        ), is(false));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withHeader("content-type", "application/xml")
                .withBody(xml("<root><id>abc</id><name>1</name></root>"))
        ), is(false));
    }

    // - FORM BODY (via JsonSchema)

    @Test
    public void shouldMatchByFormBody() {
        // given
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(configuration, mockServerLogger);

        // when
        httpRequestsPropertiesMatcher.update(new Expectation(
            new OpenAPIDefinition()
                .withSpecUrlOrPayload("---" + NEW_LINE +
                    "openapi: 3.0.0" + NEW_LINE +
                    "paths:" + NEW_LINE +
                    "  \"/somePath\":" + NEW_LINE +
                    "    post:" + NEW_LINE +
                    "      operationId: someOperation" + NEW_LINE +
                    "      requestBody:" + NEW_LINE +
                    "        required: true" + NEW_LINE +
                    "        content:" + NEW_LINE +
                    "          application/x-www-form-urlencoded:" + NEW_LINE +
                    "            schema:" + NEW_LINE +
                    "              type: object" + NEW_LINE +
                    "              required:" + NEW_LINE +
                    "                - id" + NEW_LINE +
                    "                - name" + NEW_LINE +
                    "              properties:" + NEW_LINE +
                    "                id:" + NEW_LINE +
                    "                  type: integer" + NEW_LINE +
                    "                  format: int64" + NEW_LINE +
                    "                name:" + NEW_LINE +
                    "                  type: string" + NEW_LINE +
                    "          text/plain:" + NEW_LINE +
                    "            schema:" + NEW_LINE +
                    "              type: string" + NEW_LINE)
        ));

        // then
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withHeader("content-type", "application/x-www-form-urlencoded")
                .withBody("id=1&name=abc")
        ), is(true));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withHeader("content-type", "application/x-www-form-urlencoded; charset=utf-8")
                .withBody("id=1&name=abc")
        ), is(true));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withHeader("content-type", "application/x-www-form-urlencoded")
                .withBody("id=1&name=1")
        ), is(false));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withHeader("content-type", "application/x-www-form-urlencoded")
                .withBody("id=abc&name=1")
        ), is(false));
    }

    @Test
    public void shouldMatchByFormBodyWithFormEncoding() {
        // given
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(configuration, mockServerLogger);

        // when
        httpRequestsPropertiesMatcher.update(new Expectation(
            new OpenAPIDefinition()
                .withSpecUrlOrPayload("---" + NEW_LINE +
                    "openapi: 3.0.0" + NEW_LINE +
                    "paths:" + NEW_LINE +
                    "  \"/somePath\":" + NEW_LINE +
                    "    post:" + NEW_LINE +
                    "      operationId: someOperation" + NEW_LINE +
                    "      requestBody:" + NEW_LINE +
                    "        required: true" + NEW_LINE +
                    "        content:" + NEW_LINE +
                    "          application/x-www-form-urlencoded:" + NEW_LINE +
                    "            schema:" + NEW_LINE +
                    "              type: object" + NEW_LINE +
                    "              required:" + NEW_LINE +
                    "                - id" + NEW_LINE +
                    "                - name" + NEW_LINE +
                    "              properties:" + NEW_LINE +
                    "                id:" + NEW_LINE +
                    "                  type: array" + NEW_LINE +
                    "                  items:" + NEW_LINE +
                    "                    type: integer" + NEW_LINE +
                    "                name:" + NEW_LINE +
                    "                  type: array" + NEW_LINE +
                    "                  items:" + NEW_LINE +
                    "                    type: string" + NEW_LINE +
                    "            encoding:" + NEW_LINE +
                    "              id:" + NEW_LINE +
                    "                style: form" + NEW_LINE +
                    "                explode: false" + NEW_LINE +
                    "              name:" + NEW_LINE +
                    "                style: form" + NEW_LINE +
                    "                explode: true" + NEW_LINE)
        ));

        // then
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withHeader("content-type", "application/x-www-form-urlencoded")
                .withBody("id=1,2,3&name=abc&name=def")
        ), is(true));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withHeader("content-type", "application/x-www-form-urlencoded; charset=utf-8")
                .withBody("id=1,2,3&name=abc&name=def")
        ), is(true));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withHeader("content-type", "application/x-www-form-urlencoded")
                .withBody("id=1,2,3&name=1&name=def")
        ), is(false));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withHeader("content-type", "application/x-www-form-urlencoded")
                .withBody("id=a,2,3&name=abc&name=def")
        ), is(false));
    }

    @Test
    public void shouldMatchByFormBodyWithSpaceDelimitedEncoding() {
        // given
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(configuration, mockServerLogger);

        // when
        httpRequestsPropertiesMatcher.update(new Expectation(
            new OpenAPIDefinition()
                .withSpecUrlOrPayload("---" + NEW_LINE +
                    "openapi: 3.0.0" + NEW_LINE +
                    "paths:" + NEW_LINE +
                    "  \"/somePath\":" + NEW_LINE +
                    "    post:" + NEW_LINE +
                    "      operationId: someOperation" + NEW_LINE +
                    "      requestBody:" + NEW_LINE +
                    "        required: true" + NEW_LINE +
                    "        content:" + NEW_LINE +
                    "          application/x-www-form-urlencoded:" + NEW_LINE +
                    "            schema:" + NEW_LINE +
                    "              type: object" + NEW_LINE +
                    "              required:" + NEW_LINE +
                    "                - id" + NEW_LINE +
                    "                - name" + NEW_LINE +
                    "              properties:" + NEW_LINE +
                    "                id:" + NEW_LINE +
                    "                  type: array" + NEW_LINE +
                    "                  items:" + NEW_LINE +
                    "                    type: integer" + NEW_LINE +
                    "                name:" + NEW_LINE +
                    "                  type: array" + NEW_LINE +
                    "                  items:" + NEW_LINE +
                    "                    type: string" + NEW_LINE +
                    "            encoding:" + NEW_LINE +
                    "              id:" + NEW_LINE +
                    "                style: spaceDelimited" + NEW_LINE +
                    "                explode: false" + NEW_LINE +
                    "              name:" + NEW_LINE +
                    "                style: spaceDelimited" + NEW_LINE +
                    "                explode: true" + NEW_LINE)
        ));

        // then
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withHeader("content-type", "application/x-www-form-urlencoded")
                .withBody("id=1%202%203&name=abc&name=def")
        ), is(true));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withHeader("content-type", "application/x-www-form-urlencoded")
                .withBody("id=1+2+3&name=abc&name=def")
        ), is(true));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withHeader("content-type", "application/x-www-form-urlencoded")
                .withBody("id=1 2 3&name=abc&name=def")
        ), is(true));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withHeader("content-type", "application/x-www-form-urlencoded")
                .withBody("id=1%202%203&name=1&name=def")
        ), is(false));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withHeader("content-type", "application/x-www-form-urlencoded")
                .withBody("id=1+2+3&name=1&name=def")
        ), is(false));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withHeader("content-type", "application/x-www-form-urlencoded")
                .withBody("id=1 2 3&name=1&name=def")
        ), is(false));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withHeader("content-type", "application/x-www-form-urlencoded")
                .withBody("id=a%202%203&name=abc&name=def")
        ), is(false));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withHeader("content-type", "application/x-www-form-urlencoded")
                .withBody("id=a+2+3&name=abc&name=def")
        ), is(false));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withHeader("content-type", "application/x-www-form-urlencoded")
                .withBody("id=a 2 3&name=abc&name=def")
        ), is(false));
    }

    @Test
    public void shouldMatchByFormBodyWithPipeDelimitedEncoding() {
        // given
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(configuration, mockServerLogger);

        // when
        httpRequestsPropertiesMatcher.update(new Expectation(
            new OpenAPIDefinition()
                .withSpecUrlOrPayload("---" + NEW_LINE +
                    "openapi: 3.0.0" + NEW_LINE +
                    "paths:" + NEW_LINE +
                    "  \"/somePath\":" + NEW_LINE +
                    "    post:" + NEW_LINE +
                    "      operationId: someOperation" + NEW_LINE +
                    "      requestBody:" + NEW_LINE +
                    "        required: true" + NEW_LINE +
                    "        content:" + NEW_LINE +
                    "          application/x-www-form-urlencoded:" + NEW_LINE +
                    "            schema:" + NEW_LINE +
                    "              type: object" + NEW_LINE +
                    "              required:" + NEW_LINE +
                    "                - id" + NEW_LINE +
                    "                - name" + NEW_LINE +
                    "              properties:" + NEW_LINE +
                    "                id:" + NEW_LINE +
                    "                  type: array" + NEW_LINE +
                    "                  items:" + NEW_LINE +
                    "                    type: integer" + NEW_LINE +
                    "                name:" + NEW_LINE +
                    "                  type: array" + NEW_LINE +
                    "                  items:" + NEW_LINE +
                    "                    type: string" + NEW_LINE +
                    "            encoding:" + NEW_LINE +
                    "              id:" + NEW_LINE +
                    "                style: pipeDelimited" + NEW_LINE +
                    "                explode: false" + NEW_LINE +
                    "              name:" + NEW_LINE +
                    "                style: pipeDelimited" + NEW_LINE +
                    "                explode: true" + NEW_LINE)
        ));

        // then
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withHeader("content-type", "application/x-www-form-urlencoded")
                .withBody("id=1|2|3&name=abc&name=def")
        ), is(true));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withHeader("content-type", "application/x-www-form-urlencoded; charset=utf-8")
                .withBody("id=1|2|3&name=abc&name=def")
        ), is(true));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withHeader("content-type", "application/x-www-form-urlencoded")
                .withBody("id=1|2|3&name=1&name=def")
        ), is(false));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withHeader("content-type", "application/x-www-form-urlencoded")
                .withBody("id=a|2|3&name=abc&name=def")
        ), is(false));
    }

    @Test
    public void shouldMatchByFormBodyWithRequiredFalse() {
        // given
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(configuration, mockServerLogger);

        // when
        httpRequestsPropertiesMatcher.update(new Expectation(
            new OpenAPIDefinition()
                .withSpecUrlOrPayload("---" + NEW_LINE +
                    "openapi: 3.0.0" + NEW_LINE +
                    "paths:" + NEW_LINE +
                    "  \"/somePath\":" + NEW_LINE +
                    "    post:" + NEW_LINE +
                    "      operationId: someOperation" + NEW_LINE +
                    "      requestBody:" + NEW_LINE +
                    "        required: false" + NEW_LINE +
                    "        content:" + NEW_LINE +
                    "          application/x-www-form-urlencoded:" + NEW_LINE +
                    "            schema:" + NEW_LINE +
                    "              type: object" + NEW_LINE +
                    "              required:" + NEW_LINE +
                    "                - id" + NEW_LINE +
                    "                - name" + NEW_LINE +
                    "              properties:" + NEW_LINE +
                    "                id:" + NEW_LINE +
                    "                  type: integer" + NEW_LINE +
                    "                  format: int64" + NEW_LINE +
                    "                name:" + NEW_LINE +
                    "                  type: string" + NEW_LINE +
                    "          text/plain:" + NEW_LINE +
                    "            schema:" + NEW_LINE +
                    "              type: string" + NEW_LINE)
        ));

        // then
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath")
                .withHeader("content-type", "application/x-www-form-urlencoded")
                .withBody("id=1&name=abc")
        ), is(true));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath")
                .withHeader("content-type", "application/x-www-form-urlencoded; charset=utf-8")
                .withBody("id=1&name=abc")
        ), is(true));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath")
        ), is(true));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath")
                .withHeader("content-type", "application/x-www-form-urlencoded; charset=utf-8")
        ), is(true));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath")
                .withHeader("content-type", "application/x-www-form-urlencoded")
                .withBody("id=1&name=1")
        ), is(false));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath")
                .withHeader("content-type", "application/x-www-form-urlencoded")
                .withBody("id=abc&name=1")
        ), is(false));
    }

    @Test
    public void shouldMatchByFormBodyWithRequiredDefaulted() {
        // given
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(configuration, mockServerLogger);

        // when
        httpRequestsPropertiesMatcher.update(new Expectation(
            new OpenAPIDefinition()
                .withSpecUrlOrPayload("---" + NEW_LINE +
                    "openapi: 3.0.0" + NEW_LINE +
                    "paths:" + NEW_LINE +
                    "  \"/somePath\":" + NEW_LINE +
                    "    post:" + NEW_LINE +
                    "      operationId: someOperation" + NEW_LINE +
                    "      requestBody:" + NEW_LINE +
                    "        content:" + NEW_LINE +
                    "          application/x-www-form-urlencoded:" + NEW_LINE +
                    "            schema:" + NEW_LINE +
                    "              type: object" + NEW_LINE +
                    "              required:" + NEW_LINE +
                    "                - id" + NEW_LINE +
                    "                - name" + NEW_LINE +
                    "              properties:" + NEW_LINE +
                    "                id:" + NEW_LINE +
                    "                  type: integer" + NEW_LINE +
                    "                  format: int64" + NEW_LINE +
                    "                name:" + NEW_LINE +
                    "                  type: string" + NEW_LINE +
                    "          text/plain:" + NEW_LINE +
                    "            schema:" + NEW_LINE +
                    "              type: string" + NEW_LINE)
        ));

        // then
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath")
                .withHeader("content-type", "application/x-www-form-urlencoded")
                .withBody("id=1&name=abc")
        ), is(true));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath")
                .withHeader("content-type", "application/x-www-form-urlencoded; charset=utf-8")
                .withBody("id=1&name=abc")
        ), is(true));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath")
        ), is(true));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath")
                .withHeader("content-type", "application/x-www-form-urlencoded; charset=utf-8")
        ), is(true));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath")
                .withHeader("content-type", "application/x-www-form-urlencoded")
                .withBody("id=1&name=1")
        ), is(false));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath")
                .withHeader("content-type", "application/x-www-form-urlencoded")
                .withBody("id=abc&name=1")
        ), is(false));
    }

    @Test
    public void shouldMatchByFormBodyWithSchemaComponent() {
        // given
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(configuration, mockServerLogger);

        // when
        httpRequestsPropertiesMatcher.update(new Expectation(
            new OpenAPIDefinition()
                .withSpecUrlOrPayload("---" + NEW_LINE +
                    "openapi: 3.0.0" + NEW_LINE +
                    "paths:" + NEW_LINE +
                    "  \"/somePath\":" + NEW_LINE +
                    "    post:" + NEW_LINE +
                    "      operationId: someOperation" + NEW_LINE +
                    "      requestBody:" + NEW_LINE +
                    "        required: true" + NEW_LINE +
                    "        content:" + NEW_LINE +
                    "          application/x-www-form-urlencoded:" + NEW_LINE +
                    "            schema:" + NEW_LINE +
                    "              $ref: '#/components/schemas/Simple'" + NEW_LINE +
                    "          application/xml:" + NEW_LINE +
                    "            schema:" + NEW_LINE +
                    "              $ref: '#/components/schemas/Simple'" + NEW_LINE +
                    "components:" + NEW_LINE +
                    "  schemas:" + NEW_LINE +
                    "    Simple:" + NEW_LINE +
                    "      type: object" + NEW_LINE +
                    "      required:" + NEW_LINE +
                    "        - id" + NEW_LINE +
                    "        - name" + NEW_LINE +
                    "      properties:" + NEW_LINE +
                    "        id:" + NEW_LINE +
                    "          type: integer" + NEW_LINE +
                    "          format: int64" + NEW_LINE +
                    "        name:" + NEW_LINE +
                    "          type: string" + NEW_LINE
                )
        ));

        // then
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withHeader("content-type", "application/x-www-form-urlencoded")
                .withBody("id=1&name=abc")
        ), is(true));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withHeader("content-type", "application/x-www-form-urlencoded")
                .withBody("id=1&name=1")
        ), is(false));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withHeader("content-type", "application/x-www-form-urlencoded")
                .withBody("id=abc&name=1")
        ), is(false));
    }

    @Test
    public void shouldMatchByFormBodyWithRequestBodyAndSchemaComponent() {
        // given
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(configuration, mockServerLogger);

        // when
        httpRequestsPropertiesMatcher.update(new Expectation(
            new OpenAPIDefinition()
                .withSpecUrlOrPayload("---" + NEW_LINE +
                    "openapi: 3.0.0" + NEW_LINE +
                    "paths:" + NEW_LINE +
                    "  \"/somePath\":" + NEW_LINE +
                    "    post:" + NEW_LINE +
                    "      operationId: someOperation" + NEW_LINE +
                    "      requestBody:" + NEW_LINE +
                    "        $ref: '#/components/requestBodies/SimpleBody'" + NEW_LINE +
                    "components:" + NEW_LINE +
                    "  requestBodies:" + NEW_LINE +
                    "    SimpleBody:" + NEW_LINE +
                    "      required: true" + NEW_LINE +
                    "      content:" + NEW_LINE +
                    "        application/x-www-form-urlencoded:" + NEW_LINE +
                    "          schema:" + NEW_LINE +
                    "            $ref: '#/components/schemas/Simple'" + NEW_LINE +
                    "        application/xml:" + NEW_LINE +
                    "          schema:" + NEW_LINE +
                    "            $ref: '#/components/schemas/Simple'" + NEW_LINE +
                    "  schemas:" + NEW_LINE +
                    "    Simple:" + NEW_LINE +
                    "      type: object" + NEW_LINE +
                    "      required:" + NEW_LINE +
                    "        - id" + NEW_LINE +
                    "        - name" + NEW_LINE +
                    "      properties:" + NEW_LINE +
                    "        id:" + NEW_LINE +
                    "          type: integer" + NEW_LINE +
                    "          format: int64" + NEW_LINE +
                    "        name:" + NEW_LINE +
                    "          type: string" + NEW_LINE)
        ));

        // then
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withHeader("content-type", "application/x-www-form-urlencoded")
                .withBody("id=1&name=abc")
        ), is(true));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withHeader("content-type", "application/x-www-form-urlencoded")
                .withBody("id=1&name=1")
        ), is(false));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withHeader("content-type", "application/x-www-form-urlencoded")
                .withBody("id=abc&name=1")
        ), is(false));
    }

    /**
     * A blank spec yields no per-operation matchers, and a DATA-PLANE expectation in that state
     * must not match. This previously returned true -- i.e. one mistyped expectation silently
     * matched every request the server received and served its action for all traffic. The
     * "handle blanks without throwing" intent of this test is preserved; only the dangerous
     * match-all outcome changed. Control-plane matchers (built via update(RequestDefinition))
     * keep the match-all semantic, which is the intended "no filter" behaviour for
     * clear / retrieve / verify -- see
     * {@link HttpRequestsPropertiesMatcherLifecycleTest#shouldNotMatchEveryRequestWhenNoMatchersCouldBeDerived}.
     */
    @Test
    public void shouldHandleBlankStrings() {
        // given
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(configuration, mockServerLogger);
        httpRequestsPropertiesMatcher.update(new Expectation(
            new OpenAPIDefinition()
                .withSpecUrlOrPayload("")
                .withOperationId("")
        ));
        HttpRequest httpRequest = request();
        MatchDifference context = new MatchDifference(configuration.detailedMatchFailures(), httpRequest);

        // when
        boolean matches = httpRequestsPropertiesMatcher.matches(context, httpRequest);

        // then
        thenMatchesEmptyFieldDifferences(context, matches, false);
    }

    /**
     * The control-plane counterpart of {@link #shouldHandleBlankStrings()}: built via
     * {@code update(RequestDefinition)} rather than {@code update(Expectation)}, a blank spec is
     * an empty FILTER, and matching everything remains the intended "no restriction" semantic for
     * clear / retrieve / verify. The two tests together document the split: the behaviour was not
     * removed, it was narrowed to the plane where it is correct.
     */
    @Test
    public void shouldHandleBlankStringsOnControlPlane() {
        // given
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(configuration, mockServerLogger);
        httpRequestsPropertiesMatcher.update(
            new OpenAPIDefinition()
                .withSpecUrlOrPayload("")
                .withOperationId("")
        );
        HttpRequest httpRequest = request();
        MatchDifference context = new MatchDifference(configuration.detailedMatchFailures(), httpRequest);

        // when
        boolean matches = httpRequestsPropertiesMatcher.matches(context, httpRequest);

        // then
        thenMatchesEmptyFieldDifferences(context, matches, true);
    }

    // SECURITY SCHEMES

    // HEADER (SECURITY SCHEMES)

    @Test
    public void shouldMatchBySecuritySchemeInHeaderWithBasic() {
        // given
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(configuration, mockServerLogger);

        // when
        httpRequestsPropertiesMatcher.update(new Expectation(
            new OpenAPIDefinition()
                .withSpecUrlOrPayload("---" + NEW_LINE +
                    "openapi: 3.0.0" + NEW_LINE +
                    "paths:" + NEW_LINE +
                    "  \"/somePath\":" + NEW_LINE +
                    "    get:" + NEW_LINE +
                    "      operationId: someOperation" + NEW_LINE +
                    "      security:" + NEW_LINE +
                    "        - BasicAuth: []" + NEW_LINE +
                    "components:" + NEW_LINE +
                    "  securitySchemes:" + NEW_LINE +
                    "    BasicAuth:" + NEW_LINE +
                    "      type: http" + NEW_LINE +
                    "      scheme: basic" + NEW_LINE
                )
        ));

        // then
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withHeader("Authorization", "basic " + UUIDService.getUUID())
        ), is(true));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withHeader("Authorization", "basic")
        ), is(false));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withHeader("Authorization", "wrong_scheme " + UUIDService.getUUID())
        ), is(false));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
        ), is(false));
    }

    @Test
    public void shouldMatchBySecuritySchemeInHeaderWithBearer() {
        // given
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(configuration, mockServerLogger);

        // when
        httpRequestsPropertiesMatcher.update(new Expectation(
            new OpenAPIDefinition()
                .withSpecUrlOrPayload("---" + NEW_LINE +
                    "openapi: 3.0.0" + NEW_LINE +
                    "paths:" + NEW_LINE +
                    "  \"/somePath\":" + NEW_LINE +
                    "    get:" + NEW_LINE +
                    "      operationId: someOperation" + NEW_LINE +
                    "      security:" + NEW_LINE +
                    "        - BearerAuth: []" + NEW_LINE +
                    "components:" + NEW_LINE +
                    "  securitySchemes:" + NEW_LINE +
                    "    BearerAuth:" + NEW_LINE +
                    "      type: http" + NEW_LINE +
                    "      scheme: bearer" + NEW_LINE
                )
        ));

        // then
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withHeader("Authorization", "bearer " + UUIDService.getUUID())
        ), is(true));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withHeader("Authorization", "bearer")
        ), is(false));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withHeader("Authorization", "wrong_scheme " + UUIDService.getUUID())
        ), is(false));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
        ), is(false));
    }

    @Test
    public void shouldMatchBySecuritySchemeInHeaderWithApiKey() {
        // given
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(configuration, mockServerLogger);

        // when
        httpRequestsPropertiesMatcher.update(new Expectation(
            new OpenAPIDefinition()
                .withSpecUrlOrPayload("---" + NEW_LINE +
                    "openapi: 3.0.0" + NEW_LINE +
                    "paths:" + NEW_LINE +
                    "  \"/somePath\":" + NEW_LINE +
                    "    get:" + NEW_LINE +
                    "      operationId: someOperation" + NEW_LINE +
                    "      security:" + NEW_LINE +
                    "        - ApiKeyAuth: []" + NEW_LINE +
                    "components:" + NEW_LINE +
                    "  securitySchemes:" + NEW_LINE +
                    "    ApiKeyAuth:" + NEW_LINE +
                    "      type: apiKey" + NEW_LINE +
                    "      in: header" + NEW_LINE +
                    "      name: X-API-Key" + NEW_LINE
                )
        ));

        // then
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withHeader("X-API-Key", UUIDService.getUUID())
        ), is(true));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withHeader("X-API-Key", "")
        ), is(false));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
        ), is(false));
    }

    @Test
    public void shouldMatchBySecuritySchemeInHeaderWithOpenIdConnect() {
        // given
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(configuration, mockServerLogger);

        // when
        httpRequestsPropertiesMatcher.update(new Expectation(
            new OpenAPIDefinition()
                .withSpecUrlOrPayload("---" + NEW_LINE +
                    "openapi: 3.0.0" + NEW_LINE +
                    "paths:" + NEW_LINE +
                    "  \"/somePath\":" + NEW_LINE +
                    "    get:" + NEW_LINE +
                    "      operationId: someOperation" + NEW_LINE +
                    "      security:" + NEW_LINE +
                    "        - OpenID:" + NEW_LINE +
                    "            - read" + NEW_LINE +
                    "            - write" + NEW_LINE +
                    "components:" + NEW_LINE +
                    "  securitySchemes:" + NEW_LINE +
                    "    OpenID:" + NEW_LINE +
                    "      type: openIdConnect" + NEW_LINE +
                    "      openIdConnectUrl: https://example.com/.well-known/openid-configuration" + NEW_LINE
                )
        ));

        // then
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withHeader("Authorization", "bearer " + UUIDService.getUUID())
        ), is(true));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
        ), is(false));
    }

    @Test
    public void shouldMatchBySecuritySchemeInHeaderWithOAuth2() {
        // given
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(configuration, mockServerLogger);

        // when
        httpRequestsPropertiesMatcher.update(new Expectation(
            new OpenAPIDefinition()
                .withSpecUrlOrPayload("---" + NEW_LINE +
                    "openapi: 3.0.0" + NEW_LINE +
                    "paths:" + NEW_LINE +
                    "  \"/somePath\":" + NEW_LINE +
                    "    get:" + NEW_LINE +
                    "      operationId: someOperation" + NEW_LINE +
                    "      security:" + NEW_LINE +
                    "        - OAuth2:" + NEW_LINE +
                    "            - read" + NEW_LINE +
                    "            - write" + NEW_LINE +
                    "components:" + NEW_LINE +
                    "  securitySchemes:" + NEW_LINE +
                    "    OAuth2:" + NEW_LINE +
                    "      type: oauth2" + NEW_LINE +
                    "      flows:" + NEW_LINE +
                    "        authorizationCode:" + NEW_LINE +
                    "          authorizationUrl: https://example.com/oauth/authorize" + NEW_LINE +
                    "          tokenUrl: https://example.com/oauth/token" + NEW_LINE +
                    "          scopes:" + NEW_LINE +
                    "            read: Grants read access" + NEW_LINE +
                    "            write: Grants write access" + NEW_LINE +
                    "            admin: Grants access to admin operations"
                )
        ));

        // then
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withHeader("Authorization", "bearer " + UUIDService.getUUID())
        ), is(true));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
        ), is(false));
    }

    @Test
    public void shouldMatchBySecuritySchemeInHeaderWithMultiAuthorizationHeaderSchemes() {
        // given
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(configuration, mockServerLogger);

        // when
        httpRequestsPropertiesMatcher.update(new Expectation(
            new OpenAPIDefinition()
                .withSpecUrlOrPayload("---" + NEW_LINE +
                    "openapi: 3.0.0" + NEW_LINE +
                    "paths:" + NEW_LINE +
                    "  \"/somePath\":" + NEW_LINE +
                    "    get:" + NEW_LINE +
                    "      operationId: someOperation" + NEW_LINE +
                    "      security:" + NEW_LINE +
                    "        - BasicAuth: []" + NEW_LINE +
                    "        - BearerAuth: []" + NEW_LINE +
                    "components:" + NEW_LINE +
                    "  securitySchemes:" + NEW_LINE +
                    "    BasicAuth:" + NEW_LINE +
                    "      type: http" + NEW_LINE +
                    "      scheme: basic" + NEW_LINE +
                    "    BearerAuth:" + NEW_LINE +
                    "      type: http" + NEW_LINE +
                    "      scheme: bearer" + NEW_LINE
                )
        ));

        // then
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withHeader("Authorization", "basic " + UUIDService.getUUID())
        ), is(true));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withHeader("Authorization", "bearer " + UUIDService.getUUID())
        ), is(true));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withHeader("Authorization", "bearer")
        ), is(false));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withHeader("Authorization", "wrong_scheme " + UUIDService.getUUID())
        ), is(false));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
        ), is(false));
    }

    @Test
    public void shouldMatchBySecuritySchemeInHeaderWithMultiSchemesIncludingAPIKey() {
        // given
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(configuration, mockServerLogger);

        // when
        httpRequestsPropertiesMatcher.update(new Expectation(
            new OpenAPIDefinition()
                .withSpecUrlOrPayload("---" + NEW_LINE +
                    "openapi: 3.0.0" + NEW_LINE +
                    "paths:" + NEW_LINE +
                    "  \"/somePath\":" + NEW_LINE +
                    "    get:" + NEW_LINE +
                    "      operationId: someOperation" + NEW_LINE +
                    "      security:" + NEW_LINE +
                    "        - BasicAuth: []" + NEW_LINE +
                    "        - BearerAuth: []" + NEW_LINE +
                    "        - ApiKeyAuth: []" + NEW_LINE +
                    "components:" + NEW_LINE +
                    "  securitySchemes:" + NEW_LINE +
                    "    BasicAuth:" + NEW_LINE +
                    "      type: http" + NEW_LINE +
                    "      scheme: basic" + NEW_LINE +
                    "    BearerAuth:" + NEW_LINE +
                    "      type: http" + NEW_LINE +
                    "      scheme: bearer" + NEW_LINE +
                    "    ApiKeyAuth:" + NEW_LINE +
                    "      type: apiKey" + NEW_LINE +
                    "      in: header" + NEW_LINE +
                    "      name: X-API-Key" + NEW_LINE
                )
        ));

        // then
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withHeader("Authorization", "basic " + UUIDService.getUUID())
        ), is(true));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withHeader("Authorization", "bearer " + UUIDService.getUUID())
        ), is(true));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withHeader("X-API-Key", UUIDService.getUUID())
        ), is(true));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withHeader("Authorization", "bearer")
        ), is(false));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withHeader("Authorization", "wrong_scheme " + UUIDService.getUUID())
        ), is(false));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withHeader("X-API-Key", "")
        ), is(false));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
        ), is(false));
    }

    @Test
    public void shouldMatchByDefaultSecuritySchemeInHeaderWithMultiSchemesIncludingAPIKey() {
        // given
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(configuration, mockServerLogger);

        // when
        httpRequestsPropertiesMatcher.update(new Expectation(
            new OpenAPIDefinition()
                .withSpecUrlOrPayload("---" + NEW_LINE +
                    "openapi: 3.0.0" + NEW_LINE +
                    "security:" + NEW_LINE +
                    "  - BasicAuth: []" + NEW_LINE +
                    "  - BearerAuth: []" + NEW_LINE +
                    "  - ApiKeyAuth: []" + NEW_LINE +
                    "paths:" + NEW_LINE +
                    "  \"/somePath\":" + NEW_LINE +
                    "    get:" + NEW_LINE +
                    "      operationId: someOperation" + NEW_LINE +
                    "components:" + NEW_LINE +
                    "  securitySchemes:" + NEW_LINE +
                    "    BasicAuth:" + NEW_LINE +
                    "      type: http" + NEW_LINE +
                    "      scheme: basic" + NEW_LINE +
                    "    BearerAuth:" + NEW_LINE +
                    "      type: http" + NEW_LINE +
                    "      scheme: bearer" + NEW_LINE +
                    "    ApiKeyAuth:" + NEW_LINE +
                    "      type: apiKey" + NEW_LINE +
                    "      in: header" + NEW_LINE +
                    "      name: X-API-Key" + NEW_LINE
                )
        ));

        // then
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withHeader("Authorization", "basic " + UUIDService.getUUID())
        ), is(true));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withHeader("Authorization", "bearer " + UUIDService.getUUID())
        ), is(true));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withHeader("X-API-Key", UUIDService.getUUID())
        ), is(true));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withHeader("Authorization", "bearer")
        ), is(false));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withHeader("Authorization", "wrong_scheme " + UUIDService.getUUID())
        ), is(false));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withHeader("X-API-Key", "")
        ), is(false));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
        ), is(false));
    }

    @Test
    public void shouldMatchByDefaultSecuritySchemeInHeaderWithMultiSchemesIncludingAPIKeyWithOperationOverride() {
        // given - operation security overrides global security per OpenAPI spec
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(configuration, mockServerLogger);

        // when
        httpRequestsPropertiesMatcher.update(new Expectation(
            new OpenAPIDefinition()
                .withSpecUrlOrPayload("---" + NEW_LINE +
                    "openapi: 3.0.0" + NEW_LINE +
                    "security:" + NEW_LINE +
                    "  - BasicAuth: []" + NEW_LINE +
                    "  - BearerAuth: []" + NEW_LINE +
                    "paths:" + NEW_LINE +
                    "  \"/somePath\":" + NEW_LINE +
                    "    get:" + NEW_LINE +
                    "      operationId: someOperation" + NEW_LINE +
                    "      security:" + NEW_LINE +
                    "        - ApiKeyAuth: []" + NEW_LINE +
                    "components:" + NEW_LINE +
                    "  securitySchemes:" + NEW_LINE +
                    "    BasicAuth:" + NEW_LINE +
                    "      type: http" + NEW_LINE +
                    "      scheme: basic" + NEW_LINE +
                    "    BearerAuth:" + NEW_LINE +
                    "      type: http" + NEW_LINE +
                    "      scheme: bearer" + NEW_LINE +
                    "    ApiKeyAuth:" + NEW_LINE +
                    "      type: apiKey" + NEW_LINE +
                    "      in: header" + NEW_LINE +
                    "      name: X-API-Key" + NEW_LINE
                )
        ));

        // then - only operation-level ApiKeyAuth should be accepted, not global BasicAuth/BearerAuth
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withHeader("Authorization", "basic " + UUIDService.getUUID())
        ), is(false));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withHeader("Authorization", "bearer " + UUIDService.getUUID())
        ), is(false));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withHeader("X-API-Key", UUIDService.getUUID())
        ), is(true));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withHeader("X-API-Key", "")
        ), is(false));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
        ), is(false));
    }

    // QUERY STRING PARAMETER (SECURITY SCHEMES)

    @Test
    public void shouldMatchBySecuritySchemeInQueryStringParameterWithBasic() {
        // given
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(configuration, mockServerLogger);

        // when
        httpRequestsPropertiesMatcher.update(new Expectation(
            new OpenAPIDefinition()
                .withSpecUrlOrPayload("---" + NEW_LINE +
                    "openapi: 3.0.0" + NEW_LINE +
                    "paths:" + NEW_LINE +
                    "  \"/somePath\":" + NEW_LINE +
                    "    get:" + NEW_LINE +
                    "      operationId: someOperation" + NEW_LINE +
                    "      security:" + NEW_LINE +
                    "        - BasicAuth: []" + NEW_LINE +
                    "components:" + NEW_LINE +
                    "  securitySchemes:" + NEW_LINE +
                    "    BasicAuth:" + NEW_LINE +
                    "      type: http" + NEW_LINE +
                    "      in: query" + NEW_LINE +
                    "      scheme: basic" + NEW_LINE
                )
        ));

        // then
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withQueryStringParameter("Authorization", "basic " + UUIDService.getUUID())
        ), is(true));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withQueryStringParameter("Authorization", "basic")
        ), is(false));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withQueryStringParameter("Authorization", "wrong_scheme " + UUIDService.getUUID())
        ), is(false));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
        ), is(false));
    }

    @Test
    public void shouldMatchBySecuritySchemeInQueryStringParameterWithBearer() {
        // given
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(configuration, mockServerLogger);

        // when
        httpRequestsPropertiesMatcher.update(new Expectation(
            new OpenAPIDefinition()
                .withSpecUrlOrPayload("---" + NEW_LINE +
                    "openapi: 3.0.0" + NEW_LINE +
                    "paths:" + NEW_LINE +
                    "  \"/somePath\":" + NEW_LINE +
                    "    get:" + NEW_LINE +
                    "      operationId: someOperation" + NEW_LINE +
                    "      security:" + NEW_LINE +
                    "        - BearerAuth: []" + NEW_LINE +
                    "components:" + NEW_LINE +
                    "  securitySchemes:" + NEW_LINE +
                    "    BearerAuth:" + NEW_LINE +
                    "      type: http" + NEW_LINE +
                    "      in: query" + NEW_LINE +
                    "      scheme: bearer" + NEW_LINE
                )
        ));

        // then
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withQueryStringParameter("Authorization", "bearer " + UUIDService.getUUID())
        ), is(true));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withQueryStringParameter("Authorization", "bearer")
        ), is(false));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withQueryStringParameter("Authorization", "wrong_scheme " + UUIDService.getUUID())
        ), is(false));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
        ), is(false));
    }

    @Test
    public void shouldMatchBySecuritySchemeInQueryStringParameterWithApiKey() {
        // given
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(configuration, mockServerLogger);

        // when
        httpRequestsPropertiesMatcher.update(new Expectation(
            new OpenAPIDefinition()
                .withSpecUrlOrPayload("---" + NEW_LINE +
                    "openapi: 3.0.0" + NEW_LINE +
                    "paths:" + NEW_LINE +
                    "  \"/somePath\":" + NEW_LINE +
                    "    get:" + NEW_LINE +
                    "      operationId: someOperation" + NEW_LINE +
                    "      security:" + NEW_LINE +
                    "        - ApiKeyAuth: []" + NEW_LINE +
                    "components:" + NEW_LINE +
                    "  securitySchemes:" + NEW_LINE +
                    "    ApiKeyAuth:" + NEW_LINE +
                    "      type: apiKey" + NEW_LINE +
                    "      in: query" + NEW_LINE +
                    "      name: X-API-Key" + NEW_LINE
                )
        ));

        // then
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withQueryStringParameter("X-API-Key", UUIDService.getUUID())
        ), is(true));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withQueryStringParameter("X-API-Key", "")
        ), is(false));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
        ), is(false));
    }

    @Test
    public void shouldMatchBySecuritySchemeInQueryStringParameterWithOpenIdConnect() {
        // given
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(configuration, mockServerLogger);

        // when
        httpRequestsPropertiesMatcher.update(new Expectation(
            new OpenAPIDefinition()
                .withSpecUrlOrPayload("---" + NEW_LINE +
                    "openapi: 3.0.0" + NEW_LINE +
                    "paths:" + NEW_LINE +
                    "  \"/somePath\":" + NEW_LINE +
                    "    get:" + NEW_LINE +
                    "      operationId: someOperation" + NEW_LINE +
                    "      security:" + NEW_LINE +
                    "        - OpenID:" + NEW_LINE +
                    "            - read" + NEW_LINE +
                    "            - write" + NEW_LINE +
                    "components:" + NEW_LINE +
                    "  securitySchemes:" + NEW_LINE +
                    "    OpenID:" + NEW_LINE +
                    "      type: openIdConnect" + NEW_LINE +
                    "      in: query" + NEW_LINE +
                    "      openIdConnectUrl: https://example.com/.well-known/openid-configuration" + NEW_LINE
                )
        ));

        // then
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withQueryStringParameter("Authorization", "bearer " + UUIDService.getUUID())
        ), is(true));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
        ), is(false));
    }

    @Test
    public void shouldMatchBySecuritySchemeInQueryStringParameterWithOAuth2() {
        // given
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(configuration, mockServerLogger);

        // when
        httpRequestsPropertiesMatcher.update(new Expectation(
            new OpenAPIDefinition()
                .withSpecUrlOrPayload("---" + NEW_LINE +
                    "openapi: 3.0.0" + NEW_LINE +
                    "paths:" + NEW_LINE +
                    "  \"/somePath\":" + NEW_LINE +
                    "    get:" + NEW_LINE +
                    "      operationId: someOperation" + NEW_LINE +
                    "      security:" + NEW_LINE +
                    "        - OAuth2:" + NEW_LINE +
                    "            - read" + NEW_LINE +
                    "            - write" + NEW_LINE +
                    "components:" + NEW_LINE +
                    "  securitySchemes:" + NEW_LINE +
                    "    OAuth2:" + NEW_LINE +
                    "      type: oauth2" + NEW_LINE +
                    "      in: query" + NEW_LINE +
                    "      flows:" + NEW_LINE +
                    "        authorizationCode:" + NEW_LINE +
                    "          authorizationUrl: https://example.com/oauth/authorize" + NEW_LINE +
                    "          tokenUrl: https://example.com/oauth/token" + NEW_LINE +
                    "          scopes:" + NEW_LINE +
                    "            read: Grants read access" + NEW_LINE +
                    "            write: Grants write access" + NEW_LINE +
                    "            admin: Grants access to admin operations"
                )
        ));

        // then
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withQueryStringParameter("Authorization", "bearer " + UUIDService.getUUID())
        ), is(true));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
        ), is(false));
    }

    @Test
    public void shouldMatchBySecuritySchemeInQueryStringParameterWithMultiAuthorizationQueryStringParameterSchemes() {
        // given
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(configuration, mockServerLogger);

        // when
        httpRequestsPropertiesMatcher.update(new Expectation(
            new OpenAPIDefinition()
                .withSpecUrlOrPayload("---" + NEW_LINE +
                    "openapi: 3.0.0" + NEW_LINE +
                    "paths:" + NEW_LINE +
                    "  \"/somePath\":" + NEW_LINE +
                    "    get:" + NEW_LINE +
                    "      operationId: someOperation" + NEW_LINE +
                    "      security:" + NEW_LINE +
                    "        - BasicAuth: []" + NEW_LINE +
                    "        - BearerAuth: []" + NEW_LINE +
                    "components:" + NEW_LINE +
                    "  securitySchemes:" + NEW_LINE +
                    "    BasicAuth:" + NEW_LINE +
                    "      type: http" + NEW_LINE +
                    "      in: query" + NEW_LINE +
                    "      scheme: basic" + NEW_LINE +
                    "    BearerAuth:" + NEW_LINE +
                    "      type: http" + NEW_LINE +
                    "      in: query" + NEW_LINE +
                    "      scheme: bearer" + NEW_LINE
                )
        ));

        // then
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withQueryStringParameter("Authorization", "basic " + UUIDService.getUUID())
        ), is(true));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withQueryStringParameter("Authorization", "bearer " + UUIDService.getUUID())
        ), is(true));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withQueryStringParameter("Authorization", "bearer")
        ), is(false));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withQueryStringParameter("Authorization", "wrong_scheme " + UUIDService.getUUID())
        ), is(false));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
        ), is(false));
    }

    @Test
    public void shouldMatchBySecuritySchemeInQueryStringParameterWithMultiSchemesIncludingAPIKey() {
        // given
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(configuration, mockServerLogger);

        // when
        httpRequestsPropertiesMatcher.update(new Expectation(
            new OpenAPIDefinition()
                .withSpecUrlOrPayload("---" + NEW_LINE +
                    "openapi: 3.0.0" + NEW_LINE +
                    "paths:" + NEW_LINE +
                    "  \"/somePath\":" + NEW_LINE +
                    "    get:" + NEW_LINE +
                    "      operationId: someOperation" + NEW_LINE +
                    "      security:" + NEW_LINE +
                    "        - BasicAuth: []" + NEW_LINE +
                    "        - BearerAuth: []" + NEW_LINE +
                    "        - ApiKeyAuth: []" + NEW_LINE +
                    "components:" + NEW_LINE +
                    "  securitySchemes:" + NEW_LINE +
                    "    BasicAuth:" + NEW_LINE +
                    "      type: http" + NEW_LINE +
                    "      in: query" + NEW_LINE +
                    "      scheme: basic" + NEW_LINE +
                    "    BearerAuth:" + NEW_LINE +
                    "      type: http" + NEW_LINE +
                    "      in: query" + NEW_LINE +
                    "      scheme: bearer" + NEW_LINE +
                    "    ApiKeyAuth:" + NEW_LINE +
                    "      type: apiKey" + NEW_LINE +
                    "      in: query" + NEW_LINE +
                    "      name: X-API-Key" + NEW_LINE
                )
        ));

        // then
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withQueryStringParameter("Authorization", "basic " + UUIDService.getUUID())
        ), is(true));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withQueryStringParameter("Authorization", "bearer " + UUIDService.getUUID())
        ), is(true));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withQueryStringParameter("X-API-Key", UUIDService.getUUID())
        ), is(true));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withQueryStringParameter("Authorization", "bearer")
        ), is(false));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withQueryStringParameter("Authorization", "wrong_scheme " + UUIDService.getUUID())
        ), is(false));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withQueryStringParameter("X-API-Key", "")
        ), is(false));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
        ), is(false));
    }

    @Test
    public void shouldMatchByDefaultSecuritySchemeInQueryStringParameterWithMultiSchemesIncludingAPIKey() {
        // given
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(configuration, mockServerLogger);

        // when
        httpRequestsPropertiesMatcher.update(new Expectation(
            new OpenAPIDefinition()
                .withSpecUrlOrPayload("---" + NEW_LINE +
                    "openapi: 3.0.0" + NEW_LINE +
                    "security:" + NEW_LINE +
                    "  - BasicAuth: []" + NEW_LINE +
                    "  - BearerAuth: []" + NEW_LINE +
                    "  - ApiKeyAuth: []" + NEW_LINE +
                    "paths:" + NEW_LINE +
                    "  \"/somePath\":" + NEW_LINE +
                    "    get:" + NEW_LINE +
                    "      operationId: someOperation" + NEW_LINE +
                    "components:" + NEW_LINE +
                    "  securitySchemes:" + NEW_LINE +
                    "    BasicAuth:" + NEW_LINE +
                    "      type: http" + NEW_LINE +
                    "      in: query" + NEW_LINE +
                    "      scheme: basic" + NEW_LINE +
                    "    BearerAuth:" + NEW_LINE +
                    "      type: http" + NEW_LINE +
                    "      in: query" + NEW_LINE +
                    "      scheme: bearer" + NEW_LINE +
                    "    ApiKeyAuth:" + NEW_LINE +
                    "      type: apiKey" + NEW_LINE +
                    "      in: query" + NEW_LINE +
                    "      name: X-API-Key" + NEW_LINE
                )
        ));

        // then
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withQueryStringParameter("Authorization", "basic " + UUIDService.getUUID())
        ), is(true));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withQueryStringParameter("Authorization", "bearer " + UUID.randomUUID())
        ), is(true));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withQueryStringParameter("X-API-Key", UUID.randomUUID().toString())
        ), is(true));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withQueryStringParameter("Authorization", "bearer")
        ), is(false));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withQueryStringParameter("Authorization", "wrong_scheme " + UUID.randomUUID())
        ), is(false));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withQueryStringParameter("X-API-Key", "")
        ), is(false));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
        ), is(false));
    }

    @Test
    public void shouldMatchByDefaultSecuritySchemeInQueryStringParameterWithMultiSchemesIncludingAPIKeyWithOperationOverride() {
        // given - operation security overrides global security per OpenAPI spec
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(configuration, mockServerLogger);

        // when
        httpRequestsPropertiesMatcher.update(new Expectation(
            new OpenAPIDefinition()
                .withSpecUrlOrPayload("---" + NEW_LINE +
                    "openapi: 3.0.0" + NEW_LINE +
                    "security:" + NEW_LINE +
                    "  - BasicAuth: []" + NEW_LINE +
                    "  - BearerAuth: []" + NEW_LINE +
                    "paths:" + NEW_LINE +
                    "  \"/somePath\":" + NEW_LINE +
                    "    get:" + NEW_LINE +
                    "      operationId: someOperation" + NEW_LINE +
                    "      security:" + NEW_LINE +
                    "        - ApiKeyAuth: []" + NEW_LINE +
                    "components:" + NEW_LINE +
                    "  securitySchemes:" + NEW_LINE +
                    "    BasicAuth:" + NEW_LINE +
                    "      type: http" + NEW_LINE +
                    "      in: query" + NEW_LINE +
                    "      scheme: basic" + NEW_LINE +
                    "    BearerAuth:" + NEW_LINE +
                    "      type: http" + NEW_LINE +
                    "      in: query" + NEW_LINE +
                    "      scheme: bearer" + NEW_LINE +
                    "    ApiKeyAuth:" + NEW_LINE +
                    "      type: apiKey" + NEW_LINE +
                    "      in: query" + NEW_LINE +
                    "      name: X-API-Key" + NEW_LINE
                )
        ));

        // then - only operation-level ApiKeyAuth should be accepted, not global BasicAuth/BearerAuth
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withQueryStringParameter("Authorization", "basic " + UUID.randomUUID())
        ), is(false));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withQueryStringParameter("Authorization", "bearer " + UUID.randomUUID())
        ), is(false));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withQueryStringParameter("X-API-Key", UUID.randomUUID().toString())
        ), is(true));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withQueryStringParameter("X-API-Key", "")
        ), is(false));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
        ), is(false));
    }

    // COOKIE (SECURITY SCHEMES)

    @Test
    public void shouldMatchBySecuritySchemeInCookieWithBasic() {
        // given
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(configuration, mockServerLogger);

        // when
        httpRequestsPropertiesMatcher.update(new Expectation(
            new OpenAPIDefinition()
                .withSpecUrlOrPayload("---" + NEW_LINE +
                    "openapi: 3.0.0" + NEW_LINE +
                    "paths:" + NEW_LINE +
                    "  \"/somePath\":" + NEW_LINE +
                    "    get:" + NEW_LINE +
                    "      operationId: someOperation" + NEW_LINE +
                    "      security:" + NEW_LINE +
                    "        - BasicAuth: []" + NEW_LINE +
                    "components:" + NEW_LINE +
                    "  securitySchemes:" + NEW_LINE +
                    "    BasicAuth:" + NEW_LINE +
                    "      type: http" + NEW_LINE +
                    "      in: cookie" + NEW_LINE +
                    "      scheme: basic" + NEW_LINE
                )
        ));

        // then
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withCookie("Authorization", "basic " + UUID.randomUUID())
        ), is(true));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withCookie("Authorization", "basic")
        ), is(false));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withCookie("Authorization", "wrong_scheme " + UUID.randomUUID())
        ), is(false));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
        ), is(false));
    }

    @Test
    public void shouldMatchBySecuritySchemeInCookieWithBearer() {
        // given
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(configuration, mockServerLogger);

        // when
        httpRequestsPropertiesMatcher.update(new Expectation(
            new OpenAPIDefinition()
                .withSpecUrlOrPayload("---" + NEW_LINE +
                    "openapi: 3.0.0" + NEW_LINE +
                    "paths:" + NEW_LINE +
                    "  \"/somePath\":" + NEW_LINE +
                    "    get:" + NEW_LINE +
                    "      operationId: someOperation" + NEW_LINE +
                    "      security:" + NEW_LINE +
                    "        - BearerAuth: []" + NEW_LINE +
                    "components:" + NEW_LINE +
                    "  securitySchemes:" + NEW_LINE +
                    "    BearerAuth:" + NEW_LINE +
                    "      type: http" + NEW_LINE +
                    "      in: cookie" + NEW_LINE +
                    "      scheme: bearer" + NEW_LINE
                )
        ));

        // then
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withCookie("Authorization", "bearer " + UUID.randomUUID())
        ), is(true));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withCookie("Authorization", "bearer")
        ), is(false));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withCookie("Authorization", "wrong_scheme " + UUID.randomUUID())
        ), is(false));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
        ), is(false));
    }

    @Test
    public void shouldMatchBySecuritySchemeInCookieWithApiKey() {
        // given
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(configuration, mockServerLogger);

        // when
        httpRequestsPropertiesMatcher.update(new Expectation(
            new OpenAPIDefinition()
                .withSpecUrlOrPayload("---" + NEW_LINE +
                    "openapi: 3.0.0" + NEW_LINE +
                    "paths:" + NEW_LINE +
                    "  \"/somePath\":" + NEW_LINE +
                    "    get:" + NEW_LINE +
                    "      operationId: someOperation" + NEW_LINE +
                    "      security:" + NEW_LINE +
                    "        - ApiKeyAuth: []" + NEW_LINE +
                    "components:" + NEW_LINE +
                    "  securitySchemes:" + NEW_LINE +
                    "    ApiKeyAuth:" + NEW_LINE +
                    "      type: apiKey" + NEW_LINE +
                    "      in: cookie" + NEW_LINE +
                    "      name: X-API-Key" + NEW_LINE
                )
        ));

        // then
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withCookie("X-API-Key", UUID.randomUUID().toString())
        ), is(true));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withCookie("X-API-Key", "")
        ), is(false));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
        ), is(false));
    }

    @Test
    public void shouldMatchBySecuritySchemeInCookieWithOpenIdConnect() {
        // given
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(configuration, mockServerLogger);

        // when
        httpRequestsPropertiesMatcher.update(new Expectation(
            new OpenAPIDefinition()
                .withSpecUrlOrPayload("---" + NEW_LINE +
                    "openapi: 3.0.0" + NEW_LINE +
                    "paths:" + NEW_LINE +
                    "  \"/somePath\":" + NEW_LINE +
                    "    get:" + NEW_LINE +
                    "      operationId: someOperation" + NEW_LINE +
                    "      security:" + NEW_LINE +
                    "        - OpenID:" + NEW_LINE +
                    "            - read" + NEW_LINE +
                    "            - write" + NEW_LINE +
                    "components:" + NEW_LINE +
                    "  securitySchemes:" + NEW_LINE +
                    "    OpenID:" + NEW_LINE +
                    "      type: openIdConnect" + NEW_LINE +
                    "      in: cookie" + NEW_LINE +
                    "      openIdConnectUrl: https://example.com/.well-known/openid-configuration" + NEW_LINE
                )
        ));

        // then
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withCookie("Authorization", "bearer " + UUID.randomUUID())
        ), is(true));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
        ), is(false));
    }

    @Test
    public void shouldMatchBySecuritySchemeInCookieWithOAuth2() {
        // given
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(configuration, mockServerLogger);

        // when
        httpRequestsPropertiesMatcher.update(new Expectation(
            new OpenAPIDefinition()
                .withSpecUrlOrPayload("---" + NEW_LINE +
                    "openapi: 3.0.0" + NEW_LINE +
                    "paths:" + NEW_LINE +
                    "  \"/somePath\":" + NEW_LINE +
                    "    get:" + NEW_LINE +
                    "      operationId: someOperation" + NEW_LINE +
                    "      security:" + NEW_LINE +
                    "        - OAuth2:" + NEW_LINE +
                    "            - read" + NEW_LINE +
                    "            - write" + NEW_LINE +
                    "components:" + NEW_LINE +
                    "  securitySchemes:" + NEW_LINE +
                    "    OAuth2:" + NEW_LINE +
                    "      type: oauth2" + NEW_LINE +
                    "      in: cookie" + NEW_LINE +
                    "      flows:" + NEW_LINE +
                    "        authorizationCode:" + NEW_LINE +
                    "          authorizationUrl: https://example.com/oauth/authorize" + NEW_LINE +
                    "          tokenUrl: https://example.com/oauth/token" + NEW_LINE +
                    "          scopes:" + NEW_LINE +
                    "            read: Grants read access" + NEW_LINE +
                    "            write: Grants write access" + NEW_LINE +
                    "            admin: Grants access to admin operations"
                )
        ));

        // then
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withCookie("Authorization", "bearer " + UUID.randomUUID())
        ), is(true));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
        ), is(false));
    }

    @Test
    public void shouldMatchBySecuritySchemeInCookieWithMultiAuthorizationCookieSchemes() {
        // given
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(configuration, mockServerLogger);

        // when
        httpRequestsPropertiesMatcher.update(new Expectation(
            new OpenAPIDefinition()
                .withSpecUrlOrPayload("---" + NEW_LINE +
                    "openapi: 3.0.0" + NEW_LINE +
                    "paths:" + NEW_LINE +
                    "  \"/somePath\":" + NEW_LINE +
                    "    get:" + NEW_LINE +
                    "      operationId: someOperation" + NEW_LINE +
                    "      security:" + NEW_LINE +
                    "        - BasicAuth: []" + NEW_LINE +
                    "        - BearerAuth: []" + NEW_LINE +
                    "components:" + NEW_LINE +
                    "  securitySchemes:" + NEW_LINE +
                    "    BasicAuth:" + NEW_LINE +
                    "      type: http" + NEW_LINE +
                    "      in: cookie" + NEW_LINE +
                    "      scheme: basic" + NEW_LINE +
                    "    BearerAuth:" + NEW_LINE +
                    "      type: http" + NEW_LINE +
                    "      in: cookie" + NEW_LINE +
                    "      scheme: bearer" + NEW_LINE
                )
        ));

        // then
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withCookie("Authorization", "basic " + UUID.randomUUID())
        ), is(true));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withCookie("Authorization", "bearer " + UUID.randomUUID())
        ), is(true));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withCookie("Authorization", "bearer")
        ), is(false));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withCookie("Authorization", "wrong_scheme " + UUID.randomUUID())
        ), is(false));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
        ), is(false));
    }

    @Test
    public void shouldMatchBySecuritySchemeInCookieWithMultiSchemesIncludingAPIKey() {
        // given
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(configuration, mockServerLogger);

        // when
        httpRequestsPropertiesMatcher.update(new Expectation(
            new OpenAPIDefinition()
                .withSpecUrlOrPayload("---" + NEW_LINE +
                    "openapi: 3.0.0" + NEW_LINE +
                    "paths:" + NEW_LINE +
                    "  \"/somePath\":" + NEW_LINE +
                    "    get:" + NEW_LINE +
                    "      operationId: someOperation" + NEW_LINE +
                    "      security:" + NEW_LINE +
                    "        - BasicAuth: []" + NEW_LINE +
                    "        - BearerAuth: []" + NEW_LINE +
                    "        - ApiKeyAuth: []" + NEW_LINE +
                    "components:" + NEW_LINE +
                    "  securitySchemes:" + NEW_LINE +
                    "    BasicAuth:" + NEW_LINE +
                    "      type: http" + NEW_LINE +
                    "      in: cookie" + NEW_LINE +
                    "      scheme: basic" + NEW_LINE +
                    "    BearerAuth:" + NEW_LINE +
                    "      type: http" + NEW_LINE +
                    "      in: cookie" + NEW_LINE +
                    "      scheme: bearer" + NEW_LINE +
                    "    ApiKeyAuth:" + NEW_LINE +
                    "      type: apiKey" + NEW_LINE +
                    "      in: cookie" + NEW_LINE +
                    "      name: X-API-Key" + NEW_LINE
                )
        ));

        // then
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withCookie("Authorization", "basic " + UUID.randomUUID())
        ), is(true));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withCookie("Authorization", "bearer " + UUID.randomUUID())
        ), is(true));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withCookie("X-API-Key", UUID.randomUUID().toString())
        ), is(true));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withCookie("Authorization", "bearer")
        ), is(false));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withCookie("Authorization", "wrong_scheme " + UUID.randomUUID())
        ), is(false));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withCookie("X-API-Key", "")
        ), is(false));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
        ), is(false));
    }

    @Test
    public void shouldMatchByDefaultSecuritySchemeInCookieWithMultiSchemesIncludingAPIKey() {
        // given
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(configuration, mockServerLogger);

        // when
        httpRequestsPropertiesMatcher.update(new Expectation(
            new OpenAPIDefinition()
                .withSpecUrlOrPayload("---" + NEW_LINE +
                    "openapi: 3.0.0" + NEW_LINE +
                    "security:" + NEW_LINE +
                    "  - BasicAuth: []" + NEW_LINE +
                    "  - BearerAuth: []" + NEW_LINE +
                    "  - ApiKeyAuth: []" + NEW_LINE +
                    "paths:" + NEW_LINE +
                    "  \"/somePath\":" + NEW_LINE +
                    "    get:" + NEW_LINE +
                    "      operationId: someOperation" + NEW_LINE +
                    "components:" + NEW_LINE +
                    "  securitySchemes:" + NEW_LINE +
                    "    BasicAuth:" + NEW_LINE +
                    "      type: http" + NEW_LINE +
                    "      in: cookie" + NEW_LINE +
                    "      scheme: basic" + NEW_LINE +
                    "    BearerAuth:" + NEW_LINE +
                    "      type: http" + NEW_LINE +
                    "      in: cookie" + NEW_LINE +
                    "      scheme: bearer" + NEW_LINE +
                    "    ApiKeyAuth:" + NEW_LINE +
                    "      type: apiKey" + NEW_LINE +
                    "      in: cookie" + NEW_LINE +
                    "      name: X-API-Key" + NEW_LINE
                )
        ));

        // then
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withCookie("Authorization", "basic " + UUID.randomUUID())
        ), is(true));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withCookie("Authorization", "bearer " + UUID.randomUUID())
        ), is(true));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withCookie("X-API-Key", UUID.randomUUID().toString())
        ), is(true));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withCookie("Authorization", "bearer")
        ), is(false));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withCookie("Authorization", "wrong_scheme " + UUID.randomUUID())
        ), is(false));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withCookie("X-API-Key", "")
        ), is(false));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
        ), is(false));
    }

    @Test
    public void shouldMatchByDefaultSecuritySchemeInCookieWithMultiSchemesIncludingAPIKeyWithOperationOverride() {
        // given - operation security overrides global security per OpenAPI spec
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(configuration, mockServerLogger);

        // when
        httpRequestsPropertiesMatcher.update(new Expectation(
            new OpenAPIDefinition()
                .withSpecUrlOrPayload("---" + NEW_LINE +
                    "openapi: 3.0.0" + NEW_LINE +
                    "security:" + NEW_LINE +
                    "  - BasicAuth: []" + NEW_LINE +
                    "  - BearerAuth: []" + NEW_LINE +
                    "paths:" + NEW_LINE +
                    "  \"/somePath\":" + NEW_LINE +
                    "    get:" + NEW_LINE +
                    "      operationId: someOperation" + NEW_LINE +
                    "      security:" + NEW_LINE +
                    "        - ApiKeyAuth: []" + NEW_LINE +
                    "components:" + NEW_LINE +
                    "  securitySchemes:" + NEW_LINE +
                    "    BasicAuth:" + NEW_LINE +
                    "      type: http" + NEW_LINE +
                    "      in: cookie" + NEW_LINE +
                    "      scheme: basic" + NEW_LINE +
                    "    BearerAuth:" + NEW_LINE +
                    "      type: http" + NEW_LINE +
                    "      in: cookie" + NEW_LINE +
                    "      scheme: bearer" + NEW_LINE +
                    "    ApiKeyAuth:" + NEW_LINE +
                    "      type: apiKey" + NEW_LINE +
                    "      in: cookie" + NEW_LINE +
                    "      name: X-API-Key" + NEW_LINE
                )
        ));

        // then - only operation-level ApiKeyAuth should be accepted, not global BasicAuth/BearerAuth
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withCookie("Authorization", "basic " + UUID.randomUUID())
        ), is(false));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withCookie("Authorization", "bearer " + UUID.randomUUID())
        ), is(false));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withCookie("X-API-Key", UUID.randomUUID().toString())
        ), is(true));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withCookie("X-API-Key", "")
        ), is(false));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
        ), is(false));
    }

    // EMPTY SECURITY LIST (OPERATION DISABLES GLOBAL SECURITY — issue #1315)

    @Test
    public void shouldMatchWhenOperationSecurityIsEmptyListOverridingGlobalSecurity() {
        // given - empty security list at operation level means "no security required"
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(configuration, mockServerLogger);

        // when
        httpRequestsPropertiesMatcher.update(new Expectation(
            new OpenAPIDefinition()
                .withSpecUrlOrPayload("---" + NEW_LINE +
                    "openapi: 3.0.0" + NEW_LINE +
                    "security:" + NEW_LINE +
                    "  - BearerAuth: []" + NEW_LINE +
                    "paths:" + NEW_LINE +
                    "  \"/login\":" + NEW_LINE +
                    "    post:" + NEW_LINE +
                    "      operationId: login" + NEW_LINE +
                    "      security: []" + NEW_LINE +
                    "  \"/logout\":" + NEW_LINE +
                    "    post:" + NEW_LINE +
                    "      operationId: logout" + NEW_LINE +
                    "components:" + NEW_LINE +
                    "  securitySchemes:" + NEW_LINE +
                    "    BearerAuth:" + NEW_LINE +
                    "      type: apiKey" + NEW_LINE +
                    "      in: header" + NEW_LINE +
                    "      name: Authorization" + NEW_LINE
                )
                .withOperationId("login")
        ));

        // then - /login with security: [] should NOT require Authorization header
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withMethod("POST")
                .withPath("/login")
        ), is(true));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withMethod("POST")
                .withPath("/login")
                .withHeader("Authorization", "Bearer token123")
        ), is(true));
    }

    @Test
    public void shouldRequireGlobalSecurityWhenOperationDoesNotOverride() {
        // given - /logout does not override security, so global BearerAuth applies
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(configuration, mockServerLogger);

        // when
        httpRequestsPropertiesMatcher.update(new Expectation(
            new OpenAPIDefinition()
                .withSpecUrlOrPayload("---" + NEW_LINE +
                    "openapi: 3.0.0" + NEW_LINE +
                    "security:" + NEW_LINE +
                    "  - BearerAuth: []" + NEW_LINE +
                    "paths:" + NEW_LINE +
                    "  \"/login\":" + NEW_LINE +
                    "    post:" + NEW_LINE +
                    "      operationId: login" + NEW_LINE +
                    "      security: []" + NEW_LINE +
                    "  \"/logout\":" + NEW_LINE +
                    "    post:" + NEW_LINE +
                    "      operationId: logout" + NEW_LINE +
                    "components:" + NEW_LINE +
                    "  securitySchemes:" + NEW_LINE +
                    "    BearerAuth:" + NEW_LINE +
                    "      type: apiKey" + NEW_LINE +
                    "      in: header" + NEW_LINE +
                    "      name: Authorization" + NEW_LINE
                )
                .withOperationId("logout")
        ));

        // then - /logout should require Authorization header from global security
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withMethod("POST")
                .withPath("/logout")
        ), is(false));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withMethod("POST")
                .withPath("/logout")
                .withHeader("Authorization", "Bearer token123")
        ), is(true));
    }

    // HEADER & QUERY STRING PARAMETER & COOKIE (SECURITY SCHEMES)

    @Test
    public void shouldMatchByDefaultSecuritySchemeInMultipleWithMultiSchemesIncludingAPIKey() {
        // given
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(configuration, mockServerLogger);

        // when
        httpRequestsPropertiesMatcher.update(new Expectation(
            new OpenAPIDefinition()
                .withSpecUrlOrPayload("---" + NEW_LINE +
                    "openapi: 3.0.0" + NEW_LINE +
                    "security:" + NEW_LINE +
                    "  - BasicAuth: []" + NEW_LINE +
                    "  - BearerAuth: []" + NEW_LINE +
                    "  - ApiKeyAuth: []" + NEW_LINE +
                    "paths:" + NEW_LINE +
                    "  \"/somePath\":" + NEW_LINE +
                    "    get:" + NEW_LINE +
                    "      operationId: someOperation" + NEW_LINE +
                    "components:" + NEW_LINE +
                    "  securitySchemes:" + NEW_LINE +
                    "    BasicAuth:" + NEW_LINE +
                    "      type: http" + NEW_LINE +
                    "      scheme: basic" + NEW_LINE +
                    "    BearerAuth:" + NEW_LINE +
                    "      type: http" + NEW_LINE +
                    "      in: cookie" + NEW_LINE +
                    "      scheme: bearer" + NEW_LINE +
                    "    ApiKeyAuth:" + NEW_LINE +
                    "      type: apiKey" + NEW_LINE +
                    "      in: query" + NEW_LINE +
                    "      name: X-API-Key" + NEW_LINE
                )
        ));

        // then
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withHeader("Authorization", "basic " + UUID.randomUUID())
        ), is(true));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withCookie("Authorization", "bearer " + UUID.randomUUID())
        ), is(true));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withQueryStringParameter("X-API-Key", UUID.randomUUID().toString())
        ), is(true));
        // TODO(limitation): if multiple security requirements in different parameters types and missing
        //                   from all request will still match as each parameter is an optional match
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
        ), is(true));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withHeader("Authorization", "bearer " + UUID.randomUUID())
        ), is(false));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withHeader("Authorization", "wrong_scheme " + UUID.randomUUID())
        ), is(false));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withCookie("Authorization", "bearer")
        ), is(false));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withCookie("Authorization", "basic " + UUID.randomUUID())
        ), is(false));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withCookie("Authorization", "wrong_scheme " + UUID.randomUUID())
        ), is(false));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withQueryStringParameter("X-API-Key", "")
        ), is(false));
    }

    // OTHER TESTS

    /**
     * As {@link #shouldHandleBlankStrings()}: a null spec builds no per-operation matchers, and a
     * data-plane expectation in that state must not match every request.
     */
    @Test
    public void shouldHandleNulls() {
        // given
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(configuration, mockServerLogger);
        httpRequestsPropertiesMatcher.update(new Expectation(
            new OpenAPIDefinition()
                .withSpecUrlOrPayload(null)
                .withOperationId(null)
        ));
        HttpRequest httpRequest = request();
        MatchDifference context = new MatchDifference(configuration.detailedMatchFailures(), httpRequest);

        // when
        boolean matches = httpRequestsPropertiesMatcher.matches(context, httpRequest);

        // then
        thenMatchesEmptyFieldDifferences(context, matches, false);
    }

    /**
     * The control-plane counterpart of {@link #shouldHandleNulls()} -- see
     * {@link #shouldHandleBlankStringsOnControlPlane()} for why the two planes differ.
     */
    @Test
    public void shouldHandleNullsOnControlPlane() {
        // given
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(configuration, mockServerLogger);
        httpRequestsPropertiesMatcher.update(
            new OpenAPIDefinition()
                .withSpecUrlOrPayload(null)
                .withOperationId(null)
        );
        HttpRequest httpRequest = request();
        MatchDifference context = new MatchDifference(configuration.detailedMatchFailures(), httpRequest);

        // when
        boolean matches = httpRequestsPropertiesMatcher.matches(context, httpRequest);

        // then
        thenMatchesEmptyFieldDifferences(context, matches, true);
    }

    @Test
    public void shouldHandleInvalidOpenAPIJsonSpec() {
        // given
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(configuration, mockServerLogger);

        try {
            // when
            httpRequestsPropertiesMatcher.update(
                new OpenAPIDefinition()
                    .withSpecUrlOrPayload(FileReader.readFileFromClassPathOrPath("org/mockserver/openapi/openapi_petstore_example.json").substring(0, 100))
                    .withOperationId("listPets")
            );

            // then
            fail("expected exception");
        } catch (IllegalArgumentException iae) {
            assertThat(iae.getMessage(), is("Unable to load API spec, 1 problem found:" + NEW_LINE + NEW_LINE +
                "  syntax error (1):" + NEW_LINE +
                "    - Unexpected end-of-input in field name"));
        }
    }

    @Test
    public void shouldHandleInvalidOpenAPIYamlSpecWithYamlExtension() {
        // given
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(configuration, mockServerLogger);

        try {
            // when
            httpRequestsPropertiesMatcher.update(
                new OpenAPIDefinition()
                    .withSpecUrlOrPayload(FileReader.readFileFromClassPathOrPath("org/mockserver/openapi/openapi_petstore_example.yaml").substring(0, 100))
                    .withOperationId("listPets")
            );

            // then
            fail("expected exception");
        } catch (IllegalArgumentException iae) {
            assertThat(iae.getMessage(), is("Unable to load API spec, 1 problem found:" + NEW_LINE + NEW_LINE +
                "  syntax error (1):" + NEW_LINE +
                "    - line 8, column 8: while scanning a simple key" + NEW_LINE +
                "       in 'reader', line 8, column 1:" + NEW_LINE +
                "          servers" + NEW_LINE +
                "          ^" + NEW_LINE +
                "      could not find expected ':'" + NEW_LINE +
                "       in 'reader', line 8, column 8:" + NEW_LINE +
                "          servers" + NEW_LINE +
                "                 ^"));
        }
    }

    @Test
    public void shouldHandleInvalidOpenAPIYamlSpecWithYmlExtension() {
        // given
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(configuration, mockServerLogger);

        try {
            // when
            httpRequestsPropertiesMatcher.update(
                new OpenAPIDefinition()
                    .withSpecUrlOrPayload(FileReader.readFileFromClassPathOrPath("org/mockserver/openapi/openapi_petstore_example.yml").substring(0, 100))
                    .withOperationId("listPets")
            );

            // then
            fail("expected exception");
        } catch (IllegalArgumentException iae) {
            assertThat(iae.getMessage(), is("Unable to load API spec, 1 problem found:" + NEW_LINE + NEW_LINE +
                "  syntax error (1):" + NEW_LINE +
                "    - line 8, column 8: while scanning a simple key" + NEW_LINE +
                "       in 'reader', line 8, column 1:" + NEW_LINE +
                "          servers" + NEW_LINE +
                "          ^" + NEW_LINE +
                "      could not find expected ':'" + NEW_LINE +
                "       in 'reader', line 8, column 8:" + NEW_LINE +
                "          servers" + NEW_LINE +
                "                 ^"));
        }
    }

    @Test
    public void shouldHandleInvalidOpenAPIUrl() {
        // given
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(configuration, mockServerLogger);

        try {
            // when
            httpRequestsPropertiesMatcher.update(
                new OpenAPIDefinition()
                    .withSpecUrlOrPayload("org/mockserver/mock/does_not_exist.json")
                    .withOperationId("listPets")
            );

            // then
            fail("expected exception");
        } catch (IllegalArgumentException iae) {
            assertThat(iae.getMessage(), startsWith("Unable to load API spec, 1 problem found:" + NEW_LINE + NEW_LINE +
                "  spec could not be read (1):" + NEW_LINE +
                "    - Unable to read location `org/mockserver/mock/does_not_exist.json`"));
        }
    }

    @Test
    public void shouldMatchSingleOperationInOpenAPIJsonUrl() {
        // given
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(configuration, mockServerLogger);
        httpRequestsPropertiesMatcher.update(new Expectation(
            new OpenAPIDefinition()
                .withSpecUrlOrPayload("org/mockserver/openapi/openapi_petstore_example.json")
                .withOperationId("listPets")
        ));
        HttpRequest httpRequest = request()
            .withMethod("GET")
            .withPath("/v1/pets")
            .withQueryStringParameter("limit", "10");
        MatchDifference context = new MatchDifference(configuration.detailedMatchFailures(), httpRequest);

        // when
        boolean matches = httpRequestsPropertiesMatcher.matches(context, httpRequest);

        // then
        thenMatchesEmptyFieldDifferences(context, matches, true);
    }

    @Test
    public void shouldMatchSingleOperationWithPathVariablesInOpenAPIJsonUrl() {
        // given
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(configuration, mockServerLogger);
        httpRequestsPropertiesMatcher.update(new Expectation(
            new OpenAPIDefinition()
                .withSpecUrlOrPayload("org/mockserver/openapi/openapi_petstore_example.json")
                .withOperationId("showPetById")
        ));
        HttpRequest httpRequest = request()
            .withMethod("GET")
            .withPath("/v1/pets/12345")
            .withHeader("X-Request-ID", UUID.randomUUID().toString())
            .withHeader("Other-Header", UUID.randomUUID().toString());
        MatchDifference context = new MatchDifference(configuration.detailedMatchFailures(), httpRequest);

        // when
        boolean matches = httpRequestsPropertiesMatcher.matches(context, httpRequest);

        // then
        thenMatchesEmptyFieldDifferences(context, matches, true);
    }

    @Test
    public void shouldMatchSingleOperationInOpenAPIJsonSpec() {
        // given
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(configuration, mockServerLogger);
        httpRequestsPropertiesMatcher.update(new Expectation(
            new OpenAPIDefinition()
                .withSpecUrlOrPayload(FileReader.readFileFromClassPathOrPath("org/mockserver/openapi/openapi_petstore_example.json"))
                .withOperationId("listPets")
        ));
        HttpRequest httpRequest = request()
            .withMethod("GET")
            .withPath("/v1/pets")
            .withQueryStringParameter("limit", "10");
        MatchDifference context = new MatchDifference(configuration.detailedMatchFailures(), httpRequest);

        // when
        boolean matches = httpRequestsPropertiesMatcher.matches(context, httpRequest);

        // then
        thenMatchesEmptyFieldDifferences(context, matches, true);
    }

    @Test
    public void shouldMatchSingleOperationInOpenAPIYamlUrl() {
        // given
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(configuration, mockServerLogger);
        httpRequestsPropertiesMatcher.update(new Expectation(
            new OpenAPIDefinition()
                .withSpecUrlOrPayload("org/mockserver/openapi/openapi_petstore_example.yaml")
                .withOperationId("listPets")
        ));
        HttpRequest httpRequest = request()
            .withMethod("GET")
            .withPath("/v1/pets")
            .withQueryStringParameter("limit", "10");
        MatchDifference context = new MatchDifference(configuration.detailedMatchFailures(), httpRequest);

        // when
        boolean matches = httpRequestsPropertiesMatcher.matches(context, httpRequest);

        // then
        thenMatchesEmptyFieldDifferences(context, matches, true);
    }

    @Test
    public void shouldMatchSingleOperationInOpenAPIWithOperationServerYamlUrl() {
        // given
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(configuration, mockServerLogger);
        httpRequestsPropertiesMatcher.update(new Expectation(
            new OpenAPIDefinition()
                .withSpecUrlOrPayload("org/mockserver/openapi/openapi_petstore_example_with_operation_server.yaml")
                .withOperationId("createPets")
        ));
        HttpRequest httpRequest = request()
            .withMethod("POST")
            .withPath("/v2/pets")
            .withHeader("content-type", "application/json")
            .withBody(json(ImmutableMap.of(
                "id", 10,
                "name", "fido",
                "tag", "friendly"
            )));
        MatchDifference context = new MatchDifference(configuration.detailedMatchFailures(), httpRequest);

        // when
        boolean matches = httpRequestsPropertiesMatcher.matches(context, httpRequest);

        // then
        thenMatchesEmptyFieldDifferences(context, matches, true);
    }

    @Test
    public void shouldMatchSingleOperationInOpenAPIWithMethodServerYamlUrl() {
        // given
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(configuration, mockServerLogger);
        httpRequestsPropertiesMatcher.update(new Expectation(
            new OpenAPIDefinition()
                .withSpecUrlOrPayload("org/mockserver/openapi/openapi_petstore_example_with_operation_server.yaml")
                .withOperationId("listPets")
        ));
        HttpRequest httpRequest = request()
            .withMethod("GET")
            .withPath("/v3/pets")
            .withQueryStringParameter("limit", "10");
        MatchDifference context = new MatchDifference(configuration.detailedMatchFailures(), httpRequest);

        // when
        boolean matches = httpRequestsPropertiesMatcher.matches(context, httpRequest);

        // then
        thenMatchesEmptyFieldDifferences(context, matches, true);
    }

    @Test
    public void shouldMatchSingleOperationInOpenAPIWithServerContainingVariablesYamlUrl() {
        // given
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(configuration, mockServerLogger);
        httpRequestsPropertiesMatcher.update(new Expectation(
            new OpenAPIDefinition()
                .withSpecUrlOrPayload("org/mockserver/openapi/openapi_petstore_example_with_server_url_variables.yaml")
                .withOperationId("listPets")
        ));
        HttpRequest httpRequest = request()
            .withMethod("GET")
            .withPath("/v2/pets")
            .withQueryStringParameter("limit", "10");
        MatchDifference context = new MatchDifference(configuration.detailedMatchFailures(), httpRequest);

        // when
        boolean matches = httpRequestsPropertiesMatcher.matches(context, httpRequest);

        // then
        thenMatchesEmptyFieldDifferences(context, matches, true);
    }

    @Test
    public void shouldMatchSingleOperationInOpenAPIWithServerHavingNoPathPrefixYamlUrl() {
        // given
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(configuration, mockServerLogger);
        httpRequestsPropertiesMatcher.update(new Expectation(
            new OpenAPIDefinition()
                .withSpecUrlOrPayload("org/mockserver/openapi/openapi_petstore_example_with_no_path_prefix.yaml")
                .withOperationId("listPets")
        ));
        HttpRequest httpRequest = request()
            .withMethod("GET")
            .withPath("/pets")
            .withQueryStringParameter("limit", "10");
        MatchDifference context = new MatchDifference(configuration.detailedMatchFailures(), httpRequest);

        // when
        boolean matches = httpRequestsPropertiesMatcher.matches(context, httpRequest);

        // then
        thenMatchesEmptyFieldDifferences(context, matches, true);
    }

    @Test
    public void shouldMatchSingleOperationInOpenAPIYamlSpec() {
        // given
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(configuration, mockServerLogger);
        httpRequestsPropertiesMatcher.update(new Expectation(
            new OpenAPIDefinition()
                .withSpecUrlOrPayload(FileReader.readFileFromClassPathOrPath("org/mockserver/openapi/openapi_petstore_example.yaml"))
                .withOperationId("listPets")
        ));
        HttpRequest httpRequest = request()
            .withMethod("GET")
            .withPath("/v1/pets")
            .withQueryStringParameter("limit", "10");
        MatchDifference context = new MatchDifference(configuration.detailedMatchFailures(), httpRequest);

        // when
        boolean matches = httpRequestsPropertiesMatcher.matches(context, httpRequest);

        // then
        thenMatchesEmptyFieldDifferences(context, matches, true);
    }

    @Test
    public void shouldMatchAnyOperationInOpenAPI() {
        // given
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(configuration, mockServerLogger);
        httpRequestsPropertiesMatcher.update(new Expectation(
            new OpenAPIDefinition()
                .withSpecUrlOrPayload("org/mockserver/openapi/openapi_petstore_example.json")
                .withOperationId("")
        ));
        HttpRequest httpRequest = request()
            .withMethod("GET")
            .withPath("/v1/pets")
            .withQueryStringParameter("limit", "10");
        MatchDifference context = new MatchDifference(configuration.detailedMatchFailures(), httpRequest);

        // when
        boolean matches = httpRequestsPropertiesMatcher.matches(context, httpRequest);

        // then
        thenMatchesEmptyFieldDifferences(context, matches, true);
    }

    @Test
    public void shouldNotMatchRequestWithWrongOperationIdInOpenAPI() {
        // given
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(configuration, mockServerLogger);
        httpRequestsPropertiesMatcher.update(new Expectation(
            new OpenAPIDefinition()
                .withSpecUrlOrPayload("org/mockserver/openapi/openapi_petstore_example.json")
                .withOperationId("showPetById")
        ));
        HttpRequest httpRequest = request()
            .withMethod("GET")
            .withPath("/v1/pets")
            .withQueryStringParameter("limit", "10");
        MatchDifference context = new MatchDifference(configuration.detailedMatchFailures(), httpRequest);

        // when
        boolean matches = httpRequestsPropertiesMatcher.matches(context, httpRequest);

        // then
        assertThat(matches, is(false));
        assertThat(context.getDifferences(METHOD), nullValue());
        assertThat(context.getDifferences(PATH), containsInAnyOrder(
            "  string or regex match failed expected:" + NEW_LINE +
                NEW_LINE +
                "    /v1/pets/.*" + NEW_LINE +
                NEW_LINE +
                "   found:" + NEW_LINE +
                NEW_LINE +
                "    /v1/pets" + NEW_LINE,
            "  expected path /v1/pets/{petId} has 3 parts but path /v1/pets has 2 parts "
        ));
        assertThat(context.getDifferences(QUERY_PARAMETERS), nullValue());
        assertThat(context.getDifferences(COOKIES), nullValue());
        assertThat(context.getDifferences(HEADERS), nullValue());
        assertThat(context.getDifferences(BODY), nullValue());
        assertThat(context.getDifferences(SECURE), nullValue());
        assertThat(context.getDifferences(KEEP_ALIVE), nullValue());
        assertThat(context.getDifferences(OPERATION), nullValue());
        assertThat(context.getDifferences(OPENAPI), nullValue());
    }

    @Test
    public void shouldNotMatchRequestWithWrongOperationIdWithNullContextInOpenAPI() {
        // given
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(configuration, mockServerLogger);
        httpRequestsPropertiesMatcher.update(new Expectation(
            new OpenAPIDefinition()
                .withSpecUrlOrPayload("org/mockserver/openapi/openapi_petstore_example.json")
                .withOperationId("showPetById")
        ));
        HttpRequest httpRequest = request()
            .withMethod("GET")
            .withPath("/v1/pets")
            .withQueryStringParameter("limit", "10");
        MatchDifference context = new MatchDifference(configuration.detailedMatchFailures(), httpRequest);

        // when
        boolean matches = httpRequestsPropertiesMatcher.matches(httpRequest);

        // then
        thenMatchesEmptyFieldDifferences(context, matches, false);
    }

    @Test
    public void shouldNotMatchRequestWithMethodMismatchInOpenAPI() {
        // given
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(configuration, mockServerLogger);
        httpRequestsPropertiesMatcher.update(new Expectation(
            new OpenAPIDefinition()
                .withSpecUrlOrPayload("org/mockserver/openapi/openapi_petstore_example.json")
                .withOperationId("showPetById")
        ));
        HttpRequest httpRequest = request()
            .withMethod("PUT")
            .withPath("/v1/pets")
            .withQueryStringParameter("limit", "10");
        MatchDifference context = new MatchDifference(configuration.detailedMatchFailures(), httpRequest);

        // when
        boolean matches = httpRequestsPropertiesMatcher.matches(context, httpRequest);

        // then
        assertThat(matches, is(false));
        assertThat(context.getDifferences(METHOD), containsInAnyOrder("  string or regex match failed expected:" + NEW_LINE +
            NEW_LINE +
            "    GET" + NEW_LINE +
            NEW_LINE +
            "   found:" + NEW_LINE +
            NEW_LINE +
            "    PUT" + NEW_LINE));
        assertThat(context.getDifferences(PATH), nullValue());
        assertThat(context.getDifferences(QUERY_PARAMETERS), nullValue());
        assertThat(context.getDifferences(COOKIES), nullValue());
        assertThat(context.getDifferences(HEADERS), nullValue());
        assertThat(context.getDifferences(BODY), nullValue());
        assertThat(context.getDifferences(SECURE), nullValue());
        assertThat(context.getDifferences(KEEP_ALIVE), nullValue());
        assertThat(context.getDifferences(OPERATION), nullValue());
        assertThat(context.getDifferences(OPENAPI), nullValue());
    }

    @Test
    public void shouldNotMatchRequestWithPathMismatchInOpenAPI() {
        // given
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(configuration, mockServerLogger);
        httpRequestsPropertiesMatcher.update(new Expectation(
            new OpenAPIDefinition()
                .withSpecUrlOrPayload("org/mockserver/openapi/openapi_petstore_example.json")
                .withOperationId("showPetById")
        ));
        HttpRequest httpRequest = request()
            .withMethod("GET")
            .withPath("/wrong")
            .withQueryStringParameter("limit", "10");
        MatchDifference context = new MatchDifference(configuration.detailedMatchFailures(), httpRequest);

        // when
        boolean matches = httpRequestsPropertiesMatcher.matches(context, httpRequest);

        // then
        assertThat(matches, is(false));
        assertThat(context.getDifferences(METHOD), nullValue());
        assertThat(context.getDifferences(PATH), containsInAnyOrder(
            "  string or regex match failed expected:" + NEW_LINE +
                NEW_LINE +
                "    /v1/pets/.*" + NEW_LINE +
                NEW_LINE +
                "   found:" + NEW_LINE +
                NEW_LINE +
                "    /wrong" + NEW_LINE,
            "  expected path /v1/pets/{petId} has 3 parts but path /wrong has 1 part "
        ));
        assertThat(context.getDifferences(QUERY_PARAMETERS), nullValue());
        assertThat(context.getDifferences(COOKIES), nullValue());
        assertThat(context.getDifferences(HEADERS), nullValue());
        assertThat(context.getDifferences(BODY), nullValue());
        assertThat(context.getDifferences(SECURE), nullValue());
        assertThat(context.getDifferences(KEEP_ALIVE), nullValue());
        assertThat(context.getDifferences(OPERATION), nullValue());
        assertThat(context.getDifferences(OPENAPI), nullValue());
    }

    @Test
    public void shouldNotMatchRequestWithHeaderMismatchInOpenAPI() {
        // given
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(configuration, mockServerLogger);
        httpRequestsPropertiesMatcher.update(new Expectation(
            new OpenAPIDefinition()
                .withSpecUrlOrPayload("org/mockserver/openapi/openapi_petstore_example.json")
                .withOperationId("somePath")
        ));
        HttpRequest httpRequest = request()
            .withMethod("GET")
            .withPath("/v1/some/path")
            .withQueryStringParameter("limit", "10");
        MatchDifference context = new MatchDifference(configuration.detailedMatchFailures(), httpRequest);

        // when
        boolean matches = httpRequestsPropertiesMatcher.matches(context, httpRequest);

        // then
        assertThat(matches, is(false));
        assertThat(context.getDifferences(METHOD), nullValue());
        assertThat(context.getDifferences(PATH), nullValue());
        assertThat(context.getDifferences(QUERY_PARAMETERS), nullValue());
        assertThat(context.getDifferences(COOKIES), nullValue());
        assertThat(context.getDifferences(HEADERS), containsInAnyOrder("  multimap match failed expected:" + NEW_LINE +
            "" + NEW_LINE +
            "    {" + NEW_LINE +
            "      \"keyMatchStyle\" : \"MATCHING_KEY\"," + NEW_LINE +
            "      \"X-Request-ID\" : {" + NEW_LINE +
            "        \"parameterStyle\" : \"SIMPLE\"," + NEW_LINE +
            "        \"values\" : [ {" + NEW_LINE +
            "          \"schema\" : {" + NEW_LINE +
            "            \"format\" : \"uuid\"," + NEW_LINE +
            "            \"type\" : \"string\"" + NEW_LINE +
            "          }" + NEW_LINE +
            "        } ]" + NEW_LINE +
            "      }" + NEW_LINE +
            "    }" + NEW_LINE +
            "" + NEW_LINE +
            "   found:" + NEW_LINE +
            "" + NEW_LINE +
            "    none" + NEW_LINE +
            "" + NEW_LINE +
            "   failed because:" + NEW_LINE +
            "" + NEW_LINE +
            "    none is not a subset" + NEW_LINE));
        assertThat(context.getDifferences(BODY), nullValue());
        assertThat(context.getDifferences(SECURE), nullValue());
        assertThat(context.getDifferences(KEEP_ALIVE), nullValue());
        assertThat(context.getDifferences(OPERATION), nullValue());
        assertThat(context.getDifferences(OPENAPI), nullValue());
    }

    @Test
    public void shouldNotMatchRequestWithHeaderAndQueryParameterMismatchInOpenAPI() {
        // given
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(configuration, mockServerLogger);
        httpRequestsPropertiesMatcher.update(new Expectation(
            new OpenAPIDefinition()
                .withSpecUrlOrPayload("org/mockserver/openapi/openapi_petstore_example.json")
                .withOperationId("somePath")
        ));
        HttpRequest httpRequest = request()
            .withMethod("GET")
            .withPath("/v1/some/path")
            .withQueryStringParameter("limit", "not_a_number");
        MatchDifference context = new MatchDifference(configuration.detailedMatchFailures(), httpRequest);

        // when
        boolean matches = httpRequestsPropertiesMatcher.matches(context, httpRequest);

        // then
        assertThat(matches, is(false));
        assertThat(context.getDifferences(METHOD), nullValue());
        assertThat(context.getDifferences(PATH), nullValue());
        assertThat(context.getDifferences(QUERY_PARAMETERS), nullValue());
        assertThat(context.getDifferences(COOKIES), nullValue());
        assertThat(context.getDifferences(HEADERS), containsInAnyOrder("  multimap match failed expected:" + NEW_LINE +
            "" + NEW_LINE +
            "    {" + NEW_LINE +
            "      \"keyMatchStyle\" : \"MATCHING_KEY\"," + NEW_LINE +
            "      \"X-Request-ID\" : {" + NEW_LINE +
            "        \"parameterStyle\" : \"SIMPLE\"," + NEW_LINE +
            "        \"values\" : [ {" + NEW_LINE +
            "          \"schema\" : {" + NEW_LINE +
            "            \"format\" : \"uuid\"," + NEW_LINE +
            "            \"type\" : \"string\"" + NEW_LINE +
            "          }" + NEW_LINE +
            "        } ]" + NEW_LINE +
            "      }" + NEW_LINE +
            "    }" + NEW_LINE +
            "" + NEW_LINE +
            "   found:" + NEW_LINE +
            "" + NEW_LINE +
            "    none" + NEW_LINE +
            "" + NEW_LINE +
            "   failed because:" + NEW_LINE +
            "" + NEW_LINE +
            "    none is not a subset" + NEW_LINE));
        assertThat(context.getDifferences(BODY), nullValue());
        assertThat(context.getDifferences(SECURE), nullValue());
        assertThat(context.getDifferences(KEEP_ALIVE), nullValue());
        assertThat(context.getDifferences(OPERATION), nullValue());
        assertThat(context.getDifferences(OPENAPI), nullValue());
    }

    @Test
    public void shouldNotMatchRequestWithHeaderAndQueryParameterMismatchInOpenAPIWithoutOperationId() {
        // given
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(configuration, mockServerLogger);
        httpRequestsPropertiesMatcher.update(new Expectation(
            new OpenAPIDefinition()
                .withSpecUrlOrPayload("org/mockserver/openapi/openapi_petstore_example.json")
        ));
        HttpRequest httpRequest = request()
            .withMethod("GET")
            .withPath("/v1/some/path")
            .withQueryStringParameter("limit", "not_a_number");
        MatchDifference context = new MatchDifference(configuration.detailedMatchFailures(), httpRequest);

        // when
        boolean matches = httpRequestsPropertiesMatcher.matches(context, httpRequest);

        // then
        assertThat(matches, is(false));
        assertThat(context.getDifferences(PATH), containsInAnyOrder("  string or regex match failed expected:" + NEW_LINE +
                NEW_LINE +
                "    /v1/pets" + NEW_LINE +
                NEW_LINE +
                "   found:" + NEW_LINE +
                NEW_LINE +
                "    /v1/some/path" + NEW_LINE,
            "  string or regex match failed expected:" + NEW_LINE +
                NEW_LINE +
                "    /v1/pets/.*" + NEW_LINE +
                NEW_LINE +
                "   found:" + NEW_LINE +
                NEW_LINE +
                "    /v1/some/path" + NEW_LINE));
        assertThat(context.getDifferences(METHOD), containsInAnyOrder("  string or regex match failed expected:" + NEW_LINE +
                NEW_LINE +
                "    POST" + NEW_LINE +
                NEW_LINE +
                "   found:" + NEW_LINE +
                NEW_LINE +
                "    GET" + NEW_LINE,
            "  string or regex match failed expected:" + NEW_LINE +
                NEW_LINE +
                "    POST" + NEW_LINE +
                NEW_LINE +
                "   found:" + NEW_LINE +
                NEW_LINE +
                "    GET" + NEW_LINE));
        assertThat(context.getDifferences(QUERY_PARAMETERS), nullValue());
        assertThat(context.getDifferences(COOKIES), nullValue());
        assertThat(context.getDifferences(HEADERS), containsInAnyOrder("  multimap match failed expected:" + NEW_LINE +
            "" + NEW_LINE +
            "    {" + NEW_LINE +
            "      \"keyMatchStyle\" : \"MATCHING_KEY\"," + NEW_LINE +
            "      \"X-Request-ID\" : {" + NEW_LINE +
            "        \"parameterStyle\" : \"SIMPLE\"," + NEW_LINE +
            "        \"values\" : [ {" + NEW_LINE +
            "          \"schema\" : {" + NEW_LINE +
            "            \"format\" : \"uuid\"," + NEW_LINE +
            "            \"type\" : \"string\"" + NEW_LINE +
            "          }" + NEW_LINE +
            "        } ]" + NEW_LINE +
            "      }" + NEW_LINE +
            "    }" + NEW_LINE +
            "" + NEW_LINE +
            "   found:" + NEW_LINE +
            "" + NEW_LINE +
            "    none" + NEW_LINE +
            "" + NEW_LINE +
            "   failed because:" + NEW_LINE +
            "" + NEW_LINE +
            "    none is not a subset" + NEW_LINE));
        assertThat(context.getDifferences(BODY), nullValue());
        assertThat(context.getDifferences(SECURE), nullValue());
        assertThat(context.getDifferences(KEEP_ALIVE), nullValue());
        assertThat(context.getDifferences(OPERATION), nullValue());
        assertThat(context.getDifferences(OPENAPI), nullValue());
    }

    @Test
    public void shouldNotMatchRequestWithBodyMismatchWithContentTypeInOpenAPI() {
        // given
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(configuration, mockServerLogger);
        httpRequestsPropertiesMatcher.update(new Expectation(
            new OpenAPIDefinition()
                .withSpecUrlOrPayload("org/mockserver/openapi/openapi_petstore_example.json")
                .withOperationId("createPets")
        ));
        HttpRequest httpRequest = request()
            .withMethod("POST")
            .withPath("/v1/pets")
            .withHeader("content-type", "application/json")
            .withBody(json("{" + NEW_LINE +
                "    \"id\": \"invalid_id_format\", " + NEW_LINE +
                "    \"name\": \"scruffles\", " + NEW_LINE +
                "    \"tag\": \"dog\"" + NEW_LINE +
                "}"));
        MatchDifference context = new MatchDifference(configuration.detailedMatchFailures(), httpRequest);

        // when
        boolean matches = httpRequestsPropertiesMatcher.matches(context, httpRequest);

        // then
        assertThat(matches, is(false));
        assertThat(context.getDifferences(METHOD), nullValue());
        assertThat(context.getDifferences(PATH), nullValue());
        assertThat(context.getDifferences(QUERY_PARAMETERS), nullValue());
        assertThat(context.getDifferences(COOKIES), nullValue());
        assertThat(context.getDifferences(HEADERS), nullValue());
        String bodyError = "  json schema match failed expected:" + NEW_LINE +
            NEW_LINE +
            "    {" + NEW_LINE +
            "      \"properties\" : {" + NEW_LINE +
            "        \"id\" : {" + NEW_LINE +
            "          \"format\" : \"int64\"," + NEW_LINE +
            "          \"type\" : \"integer\"" + NEW_LINE +
            "        }," + NEW_LINE +
            "        \"name\" : {" + NEW_LINE +
            "          \"type\" : \"string\"" + NEW_LINE +
            "        }," + NEW_LINE +
            "        \"tag\" : {" + NEW_LINE +
            "          \"type\" : \"string\"" + NEW_LINE +
            "        }" + NEW_LINE +
            "      }," + NEW_LINE +
            "      \"required\" : [ \"id\", \"name\" ]," + NEW_LINE +
            "      \"type\" : \"object\"" + NEW_LINE +
            "    }" + NEW_LINE +
            NEW_LINE +
            "   found:" + NEW_LINE +
            NEW_LINE +
            "    {" + NEW_LINE +
            "        \"id\": \"invalid_id_format\", " + NEW_LINE +
            "        \"name\": \"scruffles\", " + NEW_LINE +
            "        \"tag\": \"dog\"" + NEW_LINE +
            "    }" + NEW_LINE +
            NEW_LINE +
            "   failed because:" + NEW_LINE +
            NEW_LINE +
            "    1 error:" + NEW_LINE +
            "     - $.id: string found, integer expected" + NEW_LINE;
        assertThat(context.getDifferences(BODY).get(0), equalTo(bodyError));
        assertThat(context.getDifferences(BODY), containsInAnyOrder(bodyError, bodyError));
        assertThat(context.getDifferences(SECURE), nullValue());
        assertThat(context.getDifferences(KEEP_ALIVE), nullValue());
        assertThat(context.getDifferences(OPERATION), nullValue());
        assertThat(context.getDifferences(OPENAPI), nullValue());
    }

    @Test
    public void shouldNotMatchRequestWithBodyMismatchWithContentTypeInOpenAPIWithoutOperationID() {
        // given
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(configuration, mockServerLogger);
        httpRequestsPropertiesMatcher.update(new Expectation(
            new OpenAPIDefinition()
                .withSpecUrlOrPayload("org/mockserver/openapi/openapi_petstore_example.json")
        ));
        HttpRequest httpRequest = request()
            .withMethod("POST")
            .withPath("/v1/pets")
            .withHeader("content-type", "application/json")
            .withBody(json("{" + NEW_LINE +
                "    \"id\": \"invalid_id_format\", " + NEW_LINE +
                "    \"name\": \"scruffles\", " + NEW_LINE +
                "    \"tag\": \"dog\"" + NEW_LINE +
                "}"));
        MatchDifference context = new MatchDifference(configuration.detailedMatchFailures(), httpRequest);

        // when
        boolean matches = httpRequestsPropertiesMatcher.matches(context, httpRequest);

        // then
        assertThat(matches, is(false));
        String methodError = "  string or regex match failed expected:" + NEW_LINE +
            NEW_LINE +
            "    GET" + NEW_LINE +
            NEW_LINE +
            "   found:" + NEW_LINE +
            NEW_LINE +
            "    POST" + NEW_LINE;
        assertThat(context.getDifferences(METHOD), containsInAnyOrder(methodError, methodError, methodError));
        assertThat(context.getDifferences(PATH), nullValue());
        assertThat(context.getDifferences(QUERY_PARAMETERS), nullValue());
        assertThat(context.getDifferences(COOKIES), nullValue());
        assertThat(context.getDifferences(HEADERS), nullValue());
        String bodyError = "  json schema match failed expected:" + NEW_LINE +
            NEW_LINE +
            "    {" + NEW_LINE +
            "      \"properties\" : {" + NEW_LINE +
            "        \"id\" : {" + NEW_LINE +
            "          \"format\" : \"int64\"," + NEW_LINE +
            "          \"type\" : \"integer\"" + NEW_LINE +
            "        }," + NEW_LINE +
            "        \"name\" : {" + NEW_LINE +
            "          \"type\" : \"string\"" + NEW_LINE +
            "        }," + NEW_LINE +
            "        \"tag\" : {" + NEW_LINE +
            "          \"type\" : \"string\"" + NEW_LINE +
            "        }" + NEW_LINE +
            "      }," + NEW_LINE +
            "      \"required\" : [ \"id\", \"name\" ]," + NEW_LINE +
            "      \"type\" : \"object\"" + NEW_LINE +
            "    }" + NEW_LINE +
            NEW_LINE +
            "   found:" + NEW_LINE +
            NEW_LINE +
            "    {" + NEW_LINE +
            "        \"id\": \"invalid_id_format\", " + NEW_LINE +
            "        \"name\": \"scruffles\", " + NEW_LINE +
            "        \"tag\": \"dog\"" + NEW_LINE +
            "    }" + NEW_LINE +
            NEW_LINE +
            "   failed because:" + NEW_LINE +
            NEW_LINE +
            "    1 error:" + NEW_LINE +
            "     - $.id: string found, integer expected" + NEW_LINE;
        assertThat(context.getDifferences(BODY).get(0), equalTo(bodyError));
        assertThat(context.getDifferences(BODY), containsInAnyOrder(bodyError, bodyError));
        assertThat(context.getDifferences(SECURE), nullValue());
        assertThat(context.getDifferences(KEEP_ALIVE), nullValue());
        assertThat(context.getDifferences(OPERATION), nullValue());
        assertThat(context.getDifferences(OPENAPI), nullValue());
    }

    @Test
    public void shouldNotMatchRequestInOpenAPIWithBodyMismatch() {
        // given
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(configuration, mockServerLogger);
        httpRequestsPropertiesMatcher.update(new Expectation(
            new OpenAPIDefinition()
                .withSpecUrlOrPayload("org/mockserver/openapi/openapi_petstore_example.json")
                .withOperationId("createPets")
        ));
        HttpRequest httpRequest = request()
            .withMethod("POST")
            .withPath("/v1/pets")
            .withBody(json("{" + NEW_LINE +
                "    \"id\": \"invalid_id_format\", " + NEW_LINE +
                "    \"name\": \"scruffles\", " + NEW_LINE +
                "    \"tag\": \"dog\"" + NEW_LINE +
                "}"));
        MatchDifference context = new MatchDifference(configuration.detailedMatchFailures(), httpRequest);

        // when
        boolean matches = httpRequestsPropertiesMatcher.matches(context, httpRequest);

        // then
        assertThat(matches, is(false));
        assertThat(context.getDifferences(METHOD), nullValue());
        assertThat(context.getDifferences(PATH), nullValue());
        assertThat(context.getDifferences(QUERY_PARAMETERS), nullValue());
        assertThat(context.getDifferences(COOKIES), nullValue());
        assertThat(context.getDifferences(HEADERS), nullValue());
        String schemaValidationError = "  json schema match failed expected:" + NEW_LINE +
            NEW_LINE +
            "    {" + NEW_LINE +
            "      \"properties\" : {" + NEW_LINE +
            "        \"id\" : {" + NEW_LINE +
            "          \"format\" : \"int64\"," + NEW_LINE +
            "          \"type\" : \"integer\"" + NEW_LINE +
            "        }," + NEW_LINE +
            "        \"name\" : {" + NEW_LINE +
            "          \"type\" : \"string\"" + NEW_LINE +
            "        }," + NEW_LINE +
            "        \"tag\" : {" + NEW_LINE +
            "          \"type\" : \"string\"" + NEW_LINE +
            "        }" + NEW_LINE +
            "      }," + NEW_LINE +
            "      \"required\" : [ \"id\", \"name\" ]," + NEW_LINE +
            "      \"type\" : \"object\"" + NEW_LINE +
            "    }" + NEW_LINE +
            NEW_LINE +
            "   found:" + NEW_LINE +
            NEW_LINE +
            "    {" + NEW_LINE +
            "        \"id\": \"invalid_id_format\", " + NEW_LINE +
            "        \"name\": \"scruffles\", " + NEW_LINE +
            "        \"tag\": \"dog\"" + NEW_LINE +
            "    }" + NEW_LINE +
            NEW_LINE +
            "   failed because:" + NEW_LINE +
            NEW_LINE +
            "    1 error:" + NEW_LINE +
            "     - $.id: string found, integer expected" + NEW_LINE;
        assertThat(context.getDifferences(BODY).get(0), equalTo(schemaValidationError));
        assertThat(context.getDifferences(BODY), containsInAnyOrder(schemaValidationError, schemaValidationError));
        assertThat(context.getDifferences(SECURE), nullValue());
        assertThat(context.getDifferences(KEEP_ALIVE), nullValue());
        assertThat(context.getDifferences(OPERATION), nullValue());
        assertThat(context.getDifferences(OPENAPI), nullValue());
    }

    @Test
    public void shouldMatchByPathWithGlobalContextPathPrefix() {
        // given
        Configuration configWithPrefix = configuration().openAPIContextPathPrefix("/api/v1");
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(configWithPrefix, mockServerLogger);

        // when
        httpRequestsPropertiesMatcher.update(new Expectation(
            new OpenAPIDefinition()
                .withSpecUrlOrPayload("---" + NEW_LINE +
                    "openapi: 3.0.0" + NEW_LINE +
                    "paths:" + NEW_LINE +
                    "  \"/somePath\":" + NEW_LINE +
                    "    get:" + NEW_LINE +
                    "      operationId: someOperation" + NEW_LINE)
        ));

        // then
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/api/v1/somePath")
        ), is(true));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath")
        ), is(false));
    }

    @Test
    public void shouldMatchByPathWithPerDefinitionContextPathPrefix() {
        // given
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(configuration, mockServerLogger);

        // when
        httpRequestsPropertiesMatcher.update(new Expectation(
            new OpenAPIDefinition()
                .withSpecUrlOrPayload("---" + NEW_LINE +
                    "openapi: 3.0.0" + NEW_LINE +
                    "paths:" + NEW_LINE +
                    "  \"/somePath\":" + NEW_LINE +
                    "    get:" + NEW_LINE +
                    "      operationId: someOperation" + NEW_LINE)
                .withContextPathPrefix("/api/v2")
        ));

        // then
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/api/v2/somePath")
        ), is(true));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath")
        ), is(false));
    }

    @Test
    public void shouldMatchByPathWithPerDefinitionContextPathPrefixOverridingGlobal() {
        // given
        Configuration configWithPrefix = configuration().openAPIContextPathPrefix("/api/v1");
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(configWithPrefix, mockServerLogger);

        // when
        httpRequestsPropertiesMatcher.update(new Expectation(
            new OpenAPIDefinition()
                .withSpecUrlOrPayload("---" + NEW_LINE +
                    "openapi: 3.0.0" + NEW_LINE +
                    "paths:" + NEW_LINE +
                    "  \"/somePath\":" + NEW_LINE +
                    "    get:" + NEW_LINE +
                    "      operationId: someOperation" + NEW_LINE)
                .withContextPathPrefix("/api/v2")
        ));

        // then - per-definition prefix overrides global
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/api/v2/somePath")
        ), is(true));
        assertThat(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/api/v1/somePath")
        ), is(false));
    }

    private void thenMatchesEmptyFieldDifferences(MatchDifference context, boolean matches, boolean expected) {
        assertThat(matches, is(expected));
        assertThat(context.getDifferences(METHOD), nullValue());
        assertThat(context.getDifferences(PATH), nullValue());
        assertThat(context.getDifferences(QUERY_PARAMETERS), nullValue());
        assertThat(context.getDifferences(COOKIES), nullValue());
        assertThat(context.getDifferences(HEADERS), nullValue());
        assertThat(context.getDifferences(BODY), nullValue());
        assertThat(context.getDifferences(SECURE), nullValue());
        assertThat(context.getDifferences(KEEP_ALIVE), nullValue());
        assertThat(context.getDifferences(OPERATION), nullValue());
        assertThat(context.getDifferences(OPENAPI), nullValue());
    }

}