package org.mockserver.matchers;

import org.junit.Test;
import org.mockserver.file.FileReader;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.mock.Expectation;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.OpenAPIDefinition;
import org.mockserver.uuid.UUIDService;

import java.util.UUID;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsNull.nullValue;
import static org.junit.Assert.*;
import static org.mockserver.character.Character.NEW_LINE;
import static org.mockserver.matchers.MatchDifference.Field.*;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.JsonBody.json;
import static org.mockserver.model.StringBody.exact;
import static org.mockserver.model.XmlBody.xml;

/**
 * @author jamesdbloom
 */
public class HttpRequestsPropertiesMatcherTest {

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
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(mockServerLogger);

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
        assertTrue(httpRequestsPropertiesMatcher.matches(
            request()
                .withMethod("GET")
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
                .withMethod("POST")
        ));
    }

    @Test
    public void shouldMatchByMethodWithOperationId() {
        // given
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(mockServerLogger);

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
        assertTrue(httpRequestsPropertiesMatcher.matches(
            request()
                .withMethod("GET")
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
                .withMethod("POST")
        ));
    }

    // PATH

    @Test
    public void shouldMatchByPath() {
        // given
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(mockServerLogger);

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
        assertTrue(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath")
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/someOtherPath")
        ));
    }

    @Test
    public void shouldMatchByPathWithOperationId() {
        // given
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(mockServerLogger);

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
        assertTrue(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath")
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/someOtherPath")
        ));
    }

    // PARAMETERS

    @Test
    public void shouldThrowExceptionForAllowReserved() {
        // given
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(mockServerLogger);

        try {
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
                        "          allowReserved: true" + NEW_LINE +
                        "          schema:" + NEW_LINE +
                        "            type: integer" + NEW_LINE +
                        "            minimum: 1")
            ));

            // then
            fail("expected exception");
        } catch (IllegalArgumentException iae) {
            assertThat(iae.getMessage(), is("Unable to load API spec, allowReserved field is not supported on parameters, found on operation: \"someOperation\" method: \"GET\" parameter: \"someParam\" in: \"path\""));
        }
    }

    // PATH PARAMETERS

    @Test
    public void shouldMatchByPathWithPathParameter() {
        // given
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(mockServerLogger);

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
        assertTrue(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath/1")
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath/0")
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath/a")
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath")
        ));
    }

    @Test
    public void shouldMatchByPathWithMultiplePathParameter() {
        // given
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(mockServerLogger);

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
        assertTrue(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath/1/ab")
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath/0/ab")
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath/1/a")
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath/a")
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath")
        ));
    }

    @Test
    public void shouldMatchByPathWithMultipleMissingPathParameter() {
        // given
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(mockServerLogger);

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
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath")
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath/0/ab")
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath/1/a")
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath/a")
        ));
    }

    @Test
    public void shouldMatchByPathWithPathParameterAndOperationId() {
        // given
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(mockServerLogger);

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
        assertTrue(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath/1")
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath/0")
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath/a")
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath")
        ));
    }

    @Test
    public void shouldMatchByPathWithOptionalPathParameter() {
        // given
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(mockServerLogger);

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
        assertTrue(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath/1")
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath/")
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath/0")
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath/a")
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath")
        ));
    }

    @Test
    public void shouldMatchByPathWithAllowEmptyPathParameter() {
        // given
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(mockServerLogger);

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
        assertTrue(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath/1")
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath/")
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath/0")
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath/a")
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath")
        ));
    }

    @Test
    public void shouldMatchByPathWithSimplePathParameter() {
        // given
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(mockServerLogger);

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
        assertTrue(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath/1,2,3")
        ));
        assertTrue(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath/1")
        ));
        assertTrue(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath/1,2,3,4")
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath/0,1,2")
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath/a,1,2")
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath")
        ));
    }

    @Test
    public void shouldMatchByPathWithSimpleExplodedPathParameter() {
        // given
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(mockServerLogger);

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
        assertTrue(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath/1,2,3")
        ));
        assertTrue(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath/1")
        ));
        assertTrue(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath/1,2,3,4")
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath/0,1,2")
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath/a,1,2")
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath")
        ));
    }

    @Test
    public void shouldMatchByPathWithLabelPathParameter() {
        // given
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(mockServerLogger);

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
        assertTrue(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath/.1,2,3")
        ));
        assertTrue(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath/.1")
        ));
        assertTrue(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath/.1,2,3,4")
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath/.0,1,2")
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath/.a,1,2")
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath")
        ));
    }

    @Test
    public void shouldMatchByPathWithLabelExplodedPathParameter() {
        // given
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(mockServerLogger);

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
        assertTrue(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath/.1.2.3")
        ));
        assertTrue(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath/.1")
        ));
        assertTrue(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath/.1.2.3.4")
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath/.0.1.2")
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath/.a.1.2")
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath")
        ));
    }

    @Test
    public void shouldMatchByPathWithMatrixPathParameter() {
        // given
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(mockServerLogger);

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
        assertTrue(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath/;someParam=1,2,3")
        ));
        assertTrue(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath/;someParam=1")
        ));
        assertTrue(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath/;someParam=1,2,3,4")
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath/;someOtherParam=1,2,3")
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath/;someParam=0,1,2")
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath/;someParam=a,1,2")
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath")
        ));
    }

    @Test
    public void shouldMatchByPathWithMatrixExplodedPathParameter() {
        // given
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(mockServerLogger);

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
        assertTrue(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath/;someParam=1;someParam=2;someParam=3")
        ));
        assertTrue(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath/;someParam=1")
        ));
        assertTrue(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath/;someParam=1;someParam=2;someParam=3;someParam=4")
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath/;someOtherParam=1;someOtherParam=2;someOtherParam=3")
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath/;someParam=0;someParam=1;someParam=2")
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath/;someParam=a;someParam=1;someParam=2")
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath")
        ));
    }

    @Test
    public void shouldMatchByPathWithSimpleAndLabelExplodedPathParameter() {
        // given
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(mockServerLogger);

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
        assertTrue(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath/1,2,3/.1.2.3")
        ));
        assertTrue(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath/1/.1.2.3")
        ));
        assertTrue(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath/1,2,3/.1")
        ));
        assertTrue(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath/1/.1")
        ));
        assertTrue(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath/1,2,3/.1.2.3.4")
        ));
        assertTrue(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath/1,2,3,4/.1.2.3")
        ));
        assertTrue(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath/1,2,3,4/.1.2.3.4")
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath/1,2,3/.0.1.2")
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath/0,1,2/.1.2.3")
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath/0,1,2/.0.1.2")
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath/1,2,3/.a.1.2")
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath/a,1,2/.1.2.3")
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath/a,1,2/.a.1.2")
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath")
        ));
    }

    @Test
    public void shouldMatchByPathWithLabelAndMatrixExplodedPathParameter() {
        // given
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(mockServerLogger);

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
        assertTrue(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath/.1,2,3/;someMatrixParam=1;someMatrixParam=2;someMatrixParam=3")
        ));
        assertTrue(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath/.1/;someMatrixParam=1")
        ));
        assertTrue(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath/.1,2,3,4/;someMatrixParam=1;someMatrixParam=2;someMatrixParam=3;someMatrixParam=4")
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath/.1,2,3/;someMatrixParam=0;someMatrixParam=1;someMatrixParam=2")
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath/.0,1,2/;someMatrixParam=1;someMatrixParam=2;someMatrixParam=3")
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath/.0,1,2/;someMatrixParam=0;someMatrixParam=1;someMatrixParam=2")
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath/.1,2,3/;someMatrixParam=a;someMatrixParam=1;someMatrixParam=2")
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath/.a,1,2/;someMatrixParam=1;someMatrixParam=2;someMatrixParam=3")
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath/.a,1,2/;someMatrixParam=a;someMatrixParam=1;someMatrixParam=2")
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath")
        ));
    }

    // QUERY STRING PARAMETERS

    @Test
    public void shouldMatchByQueryStringParameter() {
        // given
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(mockServerLogger);

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
        assertTrue(httpRequestsPropertiesMatcher.matches(
            request()
                .withQueryStringParameter("someParam", "1")
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
                .withQueryStringParameter("someParam", "0")
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
                .withQueryStringParameter("someOtherParam", "1")
        ));
    }

    @Test
    public void shouldMatchByQueryStringParameterWithOperationId() {
        // given
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(mockServerLogger);

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
        assertTrue(httpRequestsPropertiesMatcher.matches(
            request()
                .withQueryStringParameter("someParam", "1")
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
                .withQueryStringParameter("someParam", "0")
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
                .withQueryStringParameter("someOtherParam", "1")
        ));
    }

    @Test
    public void shouldMatchByOptionalQueryString() {
        // given
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(mockServerLogger);

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
        assertTrue(httpRequestsPropertiesMatcher.matches(
            request()
                .withQueryStringParameter("someParam", "1")
        ));
        assertTrue(httpRequestsPropertiesMatcher.matches(
            request()
                .withQueryStringParameter("someOtherParam", "1")
        ));
        assertTrue(httpRequestsPropertiesMatcher.matches(
            request()
                .withQueryStringParameter("someOtherParam", "0")
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
                .withQueryStringParameter("someParam", "0")
        ));
    }

    @Test
    public void shouldMatchByOptionalNotSpecifiedQueryString() {
        // given
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(mockServerLogger);

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
        assertTrue(httpRequestsPropertiesMatcher.matches(
            request()
                .withQueryStringParameter("someParam", "1")
        ));
        assertTrue(httpRequestsPropertiesMatcher.matches(
            request()
                .withQueryStringParameter("someOtherParam", "1")
        ));
        assertTrue(httpRequestsPropertiesMatcher.matches(
            request()
                .withQueryStringParameter("someOtherParam", "0")
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
                .withQueryStringParameter("someParam", "0")
        ));
    }

    @Test
    public void shouldMatchQueryStringWithAllowEmpty() {
        // given
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(mockServerLogger);

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
        assertTrue(httpRequestsPropertiesMatcher.matches(
            request()
                .withQueryStringParameter("someParam", "1")
        ));
        assertTrue(httpRequestsPropertiesMatcher.matches(
            request()
                .withQueryStringParameter("someParam")
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
                .withQueryStringParameter("someOtherParam", "1")
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
                .withQueryStringParameter("someParam", "0")
        ));
    }

    @Test
    public void shouldMatchByOptionalQueryStringWithAllowEmpty() {
        // given
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(mockServerLogger);

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
        assertTrue(httpRequestsPropertiesMatcher.matches(
            request()
                .withQueryStringParameter("someParam", "1")
        ));
        assertTrue(httpRequestsPropertiesMatcher.matches(
            request()
                .withQueryStringParameter("someParam")
        ));
        assertTrue(httpRequestsPropertiesMatcher.matches(
            request()
                .withQueryStringParameter("someOtherParam", "1")
        ));
        assertTrue(httpRequestsPropertiesMatcher.matches(
            request()
                .withQueryStringParameter("someOtherParam", "0")
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
                .withQueryStringParameter("someParam", "0")
        ));
    }

    @Test
    public void shouldMatchByQueryStringParameterCommonForAllPaths() {
        // given
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(mockServerLogger);

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
        assertTrue(httpRequestsPropertiesMatcher.matches(
            request()
                .withQueryStringParameter("someParam", "1")
                .withQueryStringParameter("someOtherParam", "ab")
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
                .withQueryStringParameter("someParam", "0")
                .withQueryStringParameter("someOtherParam", "ab")
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
                .withQueryStringParameter("someParam", "1")
                .withQueryStringParameter("someOtherParam", "a")
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
                .withQueryStringParameter("someOtherParam", "1")
        ));
    }

    @Test
    public void shouldMatchByQueryStringParameterCommonForPath() {
        // given
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(mockServerLogger);

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
        assertTrue(httpRequestsPropertiesMatcher.matches(
            request()
                .withQueryStringParameter("someParam", "1")
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
                .withQueryStringParameter("someParam", "0")
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
                .withQueryStringParameter("someOtherParam", "1")
        ));
    }

    @Test
    public void shouldMatchByQueryStringParameterCommonForAllPathsOverriddenAtPath() {
        // given
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(mockServerLogger);

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
        assertTrue(httpRequestsPropertiesMatcher.matches(
            request()
                .withQueryStringParameter("someParam", "10")
                .withQueryStringParameter("someOtherParam", "ab")
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
                .withQueryStringParameter("someParam", "5")
                .withQueryStringParameter("someOtherParam", "ab")
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
                .withQueryStringParameter("someParam", "10")
                .withQueryStringParameter("someOtherParam", "a")
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
                .withQueryStringParameter("someOtherParam", "1")
        ));
    }

    @Test
    public void shouldMatchByQueryStringParameterCommonForAllPathsOverriddenAtMethod() {
        // given
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(mockServerLogger);

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
        assertTrue(httpRequestsPropertiesMatcher.matches(
            request()
                .withQueryStringParameter("someParam", "100")
                .withQueryStringParameter("someOtherParam", "ab")
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
                .withQueryStringParameter("someParam", "50")
                .withQueryStringParameter("someOtherParam", "ab")
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
                .withQueryStringParameter("someParam", "100")
                .withQueryStringParameter("someOtherParam", "a")
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
                .withQueryStringParameter("someOtherParam", "1")
        ));
    }

    @Test
    public void shouldMatchByPathWithFormQueryStringParameter() {
        // given
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(mockServerLogger);

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
        assertTrue(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath")
                .withQueryStringParameter("someParam", "1,2,3")
        ));
        assertTrue(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath")
                .withQueryStringParameter("someParam", "1")
        ));
        assertTrue(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath")
                .withQueryStringParameter("someParam", "1,2,3,4")
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath")
                .withQueryStringParameter("someParam", "0,1,2")
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath")
                .withQueryStringParameter("someParam", "a,1,2")
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath")
        ));
    }

    @Test
    public void shouldMatchByPathWithFormExplodedQueryStringParameter() {
        // given
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(mockServerLogger);

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
        assertTrue(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath")
                .withQueryStringParameter("someParam", "1", "2", "3")
        ));
        assertTrue(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath")
                .withQueryStringParameter("someParam", "1")
        ));
        assertTrue(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath")
                .withQueryStringParameter("someParam", "1", "2", "3", "4")
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath")
                .withQueryStringParameter("someParam", "0", "1", "2")
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath")
                .withQueryStringParameter("someParam", "a", "1", "2")
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath")
        ));
    }

    @Test
    public void shouldMatchByPathWithSpaceDelimitedQueryStringParameter() {
        // given
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(mockServerLogger);

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
        assertTrue(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath")
                .withQueryStringParameter("someParam", "1%202%203")
        ));
        assertTrue(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath")
                .withQueryStringParameter("someParam", "1 2 3")
        ));
        assertTrue(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath")
                .withQueryStringParameter("someParam", "1+2+3")
        ));
        assertTrue(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath")
                .withQueryStringParameter("someParam", "1")
        ));
        assertTrue(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath")
                .withQueryStringParameter("someParam", "1%202%203%204")
        ));
        assertTrue(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath")
                .withQueryStringParameter("someParam", "1 2 3 4")
        ));
        assertTrue(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath")
                .withQueryStringParameter("someParam", "1+2+3+4")
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath")
                .withQueryStringParameter("someParam", "0%201%202")
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath")
                .withQueryStringParameter("someParam", "0 1 2")
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath")
                .withQueryStringParameter("someParam", "0+1+2")
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath")
                .withQueryStringParameter("someParam", "a%202%203")
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath")
                .withQueryStringParameter("someParam", "a 2 3")
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath")
                .withQueryStringParameter("someParam", "a+2+3")
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath")
        ));
    }

    @Test
    public void shouldMatchByPathWithSpaceDelimitedExplodedQueryStringParameter() {
        // given
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(mockServerLogger);

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
        assertTrue(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath")
                .withQueryStringParameter("someParam", "1", "2", "3")
        ));
        assertTrue(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath")
                .withQueryStringParameter("someParam", "1")
        ));
        assertTrue(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath")
                .withQueryStringParameter("someParam", "1", "2", "3", "4")
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath")
                .withQueryStringParameter("someParam", "0", "1", "2")
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath")
                .withQueryStringParameter("someParam", "a", "1", "2")
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath")
        ));
    }

    @Test
    public void shouldMatchByPathWithPipeDelimitedQueryStringParameter() {
        // given
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(mockServerLogger);

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
        assertTrue(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath")
                .withQueryStringParameter("someParam", "1|2|3")
        ));
        assertTrue(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath")
                .withQueryStringParameter("someParam", "1")
        ));
        assertTrue(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath")
                .withQueryStringParameter("someParam", "1|2|3|4")
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath")
                .withQueryStringParameter("someParam", "0|1|2")
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath")
                .withQueryStringParameter("someParam", "a|1|2")
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath")
        ));
    }

    @Test
    public void shouldMatchByPathWithPipeDelimitedExplodedQueryStringParameter() {
        // given
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(mockServerLogger);

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
        assertTrue(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath")
                .withQueryStringParameter("someParam", "1", "2", "3")
        ));
        assertTrue(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath")
                .withQueryStringParameter("someParam", "1")
        ));
        assertTrue(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath")
                .withQueryStringParameter("someParam", "1", "2", "3", "4")
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath")
                .withQueryStringParameter("someParam", "0", "1", "2")
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath")
                .withQueryStringParameter("someParam", "a", "1", "2")
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath")
        ));
    }

    @Test
    public void shouldMatchByPathWithFormExplodedAndPipeDelimitedQueryStringParameter() {
        // given
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(mockServerLogger);

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
        assertTrue(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath")
                .withQueryStringParameter("someFormParam", "1", "2", "3")
                .withQueryStringParameter("someSpaceDelimitedParam", "1|2|3")
        ));
        assertTrue(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath")
                .withQueryStringParameter("someFormParam", "1")
                .withQueryStringParameter("someSpaceDelimitedParam", "1")
        ));
        assertTrue(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath")
                .withQueryStringParameter("someFormParam", "1", "2", "3", "4")
                .withQueryStringParameter("someSpaceDelimitedParam", "1|2|3|4")
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath")
                .withQueryStringParameter("someFormParam", "0", "1", "2")
                .withQueryStringParameter("someSpaceDelimitedParam", "1|2|3")
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath")
                .withQueryStringParameter("someFormParam", "1", "2", "3")
                .withQueryStringParameter("someSpaceDelimitedParam", "0|1|2")
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath")
                .withQueryStringParameter("someFormParam", "0", "1", "2")
                .withQueryStringParameter("someSpaceDelimitedParam", "0|1|2")
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath")
                .withQueryStringParameter("someFormParam", "a", "1", "2")
                .withQueryStringParameter("someSpaceDelimitedParam", "1|2|3")
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath")
                .withQueryStringParameter("someFormParam", "1", "2", "3")
                .withQueryStringParameter("someSpaceDelimitedParam", "a|1|2")
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath")
                .withQueryStringParameter("someFormParam", "a", "1", "2")
                .withQueryStringParameter("someSpaceDelimitedParam", "a|1|2")
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath")
        ));
    }

    @Test
    public void shouldMatchByPathWithSpaceDelimitedAndPipeDelimitedQueryStringParameter() {
        // given
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(mockServerLogger);

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
        assertTrue(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath")
                .withQueryStringParameter("someSpaceDelimitedParam", "1%202%203")
                .withQueryStringParameter("somePipeDelimitedParam", "1|2|3")
        ));
        assertTrue(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath")
                .withQueryStringParameter("someSpaceDelimitedParam", "1 2 3")
                .withQueryStringParameter("somePipeDelimitedParam", "1|2|3")
        ));
        assertTrue(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath")
                .withQueryStringParameter("someSpaceDelimitedParam", "1")
                .withQueryStringParameter("somePipeDelimitedParam", "1")
        ));
        assertTrue(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath")
                .withQueryStringParameter("someSpaceDelimitedParam", "1+2+3+4")
                .withQueryStringParameter("somePipeDelimitedParam", "1|2|3|4")
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath")
                .withQueryStringParameter("someSpaceDelimitedParam", "0+1+2")
                .withQueryStringParameter("somePipeDelimitedParam", "1|2|3")
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath")
                .withQueryStringParameter("someSpaceDelimitedParam", "1+2+3")
                .withQueryStringParameter("somePipeDelimitedParam", "0|1|2")
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath")
                .withQueryStringParameter("someSpaceDelimitedParam", "0+1+2")
                .withQueryStringParameter("somePipeDelimitedParam", "0|1|2")
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath")
                .withQueryStringParameter("someSpaceDelimitedParam", "a%201%202")
                .withQueryStringParameter("somePipeDelimitedParam", "1|2|3")
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath")
                .withQueryStringParameter("someSpaceDelimitedParam", "1%202%203")
                .withQueryStringParameter("somePipeDelimitedParam", "a|1|2")
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath")
                .withQueryStringParameter("someSpaceDelimitedParam", "a%201%202")
                .withQueryStringParameter("somePipeDelimitedParam", "a|1|2")
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath")
        ));
    }

    // HEADERS

    @Test
    public void shouldMatchByHeader() {
        // given
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(mockServerLogger);

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
        assertTrue(httpRequestsPropertiesMatcher.matches(
            request()
                .withHeader("someParam", "1")
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
                .withHeader("someParam", "0")
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
                .withHeader("someOtherParam", "1")
        ));
    }

    @Test
    public void shouldMatchByHeaderWithOperationId() {
        // given
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(mockServerLogger);

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
        assertTrue(httpRequestsPropertiesMatcher.matches(
            request()
                .withHeader("someParam", "1")
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
                .withHeader("someParam", "0")
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
                .withHeader("someOtherParam", "1")
        ));
    }

    @Test
    public void shouldMatchByOptionalHeader() {
        // given
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(mockServerLogger);

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
        assertTrue(httpRequestsPropertiesMatcher.matches(
            request()
                .withHeader("someParam", "1")
        ));
        assertTrue(httpRequestsPropertiesMatcher.matches(
            request()
                .withHeader("someOtherParam", "1")
        ));
        assertTrue(httpRequestsPropertiesMatcher.matches(
            request()
                .withHeader("someOtherParam", "0")
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
                .withHeader("someParam", "0")
        ));
    }

    @Test
    public void shouldMatchByOptionalNotSpecifiedHeader() {
        // given
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(mockServerLogger);

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
        assertTrue(httpRequestsPropertiesMatcher.matches(
            request()
                .withHeader("someParam", "1")
        ));
        assertTrue(httpRequestsPropertiesMatcher.matches(
            request()
                .withHeader("someOtherParam", "1")
        ));
        assertTrue(httpRequestsPropertiesMatcher.matches(
            request()
                .withHeader("someOtherParam", "0")
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
                .withHeader("someParam", "0")
        ));
    }

    @Test
    public void shouldMatchHeaderWithAllowEmpty() {
        // given
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(mockServerLogger);

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
        assertTrue(httpRequestsPropertiesMatcher.matches(
            request()
                .withHeader("someParam", "1")
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
                .withHeader("someParam", "")
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
                .withHeader("someOtherParam", "1")
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
                .withHeader("someParam", "0")
        ));
    }

    @Test
    public void shouldMatchByHeaderCommonForAllPaths() {
        // given
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(mockServerLogger);

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
        assertTrue(httpRequestsPropertiesMatcher.matches(
            request()
                .withHeader("someParam", "1")
                .withHeader("someOtherParam", "ab")
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
                .withHeader("someParam", "0")
                .withHeader("someOtherParam", "ab")
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
                .withHeader("someParam", "1")
                .withHeader("someOtherParam", "a")
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
                .withHeader("someOtherParam", "1")
        ));
    }

    @Test
    public void shouldMatchByHeaderCommonForPath() {
        // given
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(mockServerLogger);

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
        assertTrue(httpRequestsPropertiesMatcher.matches(
            request()
                .withHeader("someParam", "1")
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
                .withHeader("someParam", "0")
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
                .withHeader("someOtherParam", "1")
        ));
    }

    @Test
    public void shouldMatchByHeaderCommonForAllPathsOverriddenAtMethod() {
        // given
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(mockServerLogger);

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
        assertTrue(httpRequestsPropertiesMatcher.matches(
            request()
                .withHeader("someParam", "100")
                .withHeader("someOtherParam", "ab")
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
                .withHeader("someParam", "50")
                .withHeader("someOtherParam", "ab")
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
                .withHeader("someParam", "100")
                .withHeader("someOtherParam", "a")
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
                .withHeader("someOtherParam", "1")
        ));
    }

    // COOKIES

    @Test
    public void shouldMatchByCookie() {
        // given
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(mockServerLogger);

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
        assertTrue(httpRequestsPropertiesMatcher.matches(
            request()
                .withCookie("someParam", "1")
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
                .withCookie("someParam", "0")
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
                .withCookie("someOtherParam", "1")
        ));
    }

    @Test
    public void shouldMatchByCookieWithOperationId() {
        // given
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(mockServerLogger);

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
        assertTrue(httpRequestsPropertiesMatcher.matches(
            request()
                .withCookie("someParam", "1")
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
                .withCookie("someParam", "0")
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
                .withCookie("someOtherParam", "1")
        ));
    }

    @Test
    public void shouldMatchByOptionalCookie() {
        // given
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(mockServerLogger);

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
        assertTrue(httpRequestsPropertiesMatcher.matches(
            request()
                .withCookie("someParam", "1")
        ));
        assertTrue(httpRequestsPropertiesMatcher.matches(
            request()
                .withCookie("someOtherParam", "1")
        ));
        assertTrue(httpRequestsPropertiesMatcher.matches(
            request()
                .withCookie("someOtherParam", "0")
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
                .withCookie("someParam", "0")
        ));
    }

    @Test
    public void shouldMatchByOptionalNotSpecifiedCookie() {
        // given
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(mockServerLogger);

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
        assertTrue(httpRequestsPropertiesMatcher.matches(
            request()
                .withCookie("someParam", "1")
        ));
        assertTrue(httpRequestsPropertiesMatcher.matches(
            request()
                .withCookie("someOtherParam", "1")
        ));
        assertTrue(httpRequestsPropertiesMatcher.matches(
            request()
                .withCookie("someOtherParam", "0")
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
                .withCookie("someParam", "0")
        ));
    }

    @Test
    public void shouldMatchCookieWithAllowEmpty() {
        // given
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(mockServerLogger);

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
        assertTrue(httpRequestsPropertiesMatcher.matches(
            request()
                .withCookie("someParam", "1")
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
                .withCookie("someParam", "")
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
                .withCookie("someOtherParam", "1")
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
                .withCookie("someParam", "0")
        ));
    }

    @Test
    public void shouldMatchByCookieCommonForPath() {
        // given
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(mockServerLogger);

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
        assertTrue(httpRequestsPropertiesMatcher.matches(
            request()
                .withCookie("someParam", "1")
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
                .withCookie("someParam", "0")
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
                .withCookie("someOtherParam", "1")
        ));
    }

    @Test
    public void shouldMatchByCookieCommonForAllPaths() {
        // given
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(mockServerLogger);

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
        assertTrue(httpRequestsPropertiesMatcher.matches(
            request()
                .withCookie("someParam", "1")
                .withCookie("someOtherParam", "ab")
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
                .withCookie("someParam", "0")
                .withCookie("someOtherParam", "ab")
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
                .withCookie("someParam", "1")
                .withCookie("someOtherParam", "a")
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
                .withCookie("someOtherParam", "1")
        ));
    }

    @Test
    public void shouldMatchByCookieCommonForAllPathsOverriddenAtMethod() {
        // given
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(mockServerLogger);

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
        assertTrue(httpRequestsPropertiesMatcher.matches(
            request()
                .withCookie("someParam", "100")
                .withCookie("someOtherParam", "ab")
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
                .withCookie("someParam", "50")
                .withCookie("someOtherParam", "ab")
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
                .withCookie("someParam", "100")
                .withCookie("someOtherParam", "a")
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
                .withCookie("someOtherParam", "1")
        ));
    }

    // BODY

    @Test
    public void shouldThrowExceptionForRequestBodyWithMultipartForm() {
        // given
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(mockServerLogger);

        try {
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
            fail("expected exception");
        } catch (IllegalArgumentException iae) {
            assertThat(iae.getMessage(), is("Unable to load API spec, multipart form data is not supported on requestBody, found on operation: \"someOperation\" method: \"POST\""));
        }
    }

    // - JSON BODY (via JsonSchema)

    @Test
    public void shouldMatchByJsonBody() {
        // given
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(mockServerLogger);

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
        assertTrue(httpRequestsPropertiesMatcher.matches(
            request()
                .withHeader("content-type", "application/json")
                .withBody(json("{ \"id\": 1, \"name\": \"abc\" }"))
        ));
        assertTrue(httpRequestsPropertiesMatcher.matches(
            request()
                .withHeader("content-type", "application/json; charset=utf-8")
                .withBody(json("{ \"id\": 1, \"name\": \"abc\" }"))
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
                .withHeader("content-type", "application/json")
                .withBody(json("{ \"id\": 1, \"name\": 1 }"))
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
                .withHeader("content-type", "application/json")
                .withBody(json("{ \"id\": \"abc\", \"name\": \"abc\" }"))
        ));
    }

    @Test
    public void shouldMatchByJsonBodyWithMediaTypeRange() {
        // given
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(mockServerLogger);

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
        assertTrue(httpRequestsPropertiesMatcher.matches(
            request()
                .withHeader("content-type", "application/json")
                .withBody(json("{ \"id\": 1, \"name\": \"abc\" }"))
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
                .withHeader("content-type", "application/json")
                .withBody(json("{ \"id\": 1, \"name\": 1 }"))
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
                .withHeader("content-type", "application/json")
                .withBody(json("{ \"id\": \"abc\", \"name\": \"abc\" }"))
        ));
    }

    @Test
    public void shouldMatchByJsonBodyWithDefaultMediaType() {
        // given
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(mockServerLogger);

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
        assertTrue(httpRequestsPropertiesMatcher.matches(
            request()
                .withHeader("content-type", "application/json")
                .withBody(json("{ \"id\": 1, \"name\": \"abc\" }"))
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
                .withHeader("content-type", "application/json")
                .withBody(json("{ \"id\": 1, \"name\": 1 }"))
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
                .withHeader("content-type", "application/json")
                .withBody(json("{ \"id\": \"abc\", \"name\": \"abc\" }"))
        ));
    }

    @Test
    public void shouldMatchByJsonBodyWithSchemaComponent() {
        // given
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(mockServerLogger);

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
        assertTrue(httpRequestsPropertiesMatcher.matches(
            request()
                .withHeader("content-type", "application/json")
                .withBody(json("{ \"id\": 1, \"name\": \"abc\" }"))
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
                .withHeader("content-type", "application/json")
                .withBody(json("{ \"id\": 1, \"name\": 1 }"))
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
                .withHeader("content-type", "application/json")
                .withBody(json("{ \"id\": \"abc\", \"name\": \"abc\" }"))
        ));
    }

    @Test
    public void shouldMatchByJsonBodyWithRequestBodyAndSchemaComponent() {
        // given
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(mockServerLogger);

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
        assertTrue(httpRequestsPropertiesMatcher.matches(
            request()
                .withHeader("content-type", "application/json")
                .withBody(json("{ \"id\": 1, \"name\": \"abc\" }"))
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
                .withHeader("content-type", "application/json")
                .withBody(json("{ \"id\": 1, \"name\": 1 }"))
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
                .withHeader("content-type", "application/json")
                .withBody(json("{ \"id\": \"abc\", \"name\": \"abc\" }"))
        ));
    }

    // - PLAIN TEXT BODY (via JsonSchema)

    @Test
    public void shouldMatchByPlainTextBody() {
        // given
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(mockServerLogger);

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
        assertTrue(httpRequestsPropertiesMatcher.matches(
            request()
                .withHeader("content-type", "plain/text")
                .withBody(exact("{ \"id\": 1, \"name\": \"abc\" }"))
        ));
        assertTrue(httpRequestsPropertiesMatcher.matches(
            request()
                .withHeader("content-type", "plain/text; charset=utf-8")
                .withBody(exact("{ \"id\": 1, \"name\": \"abc\" }"))
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
                .withHeader("content-type", "plain/text")
                .withBody(exact("{ \"id\": 1, \"name\": 1 }"))
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
                .withHeader("content-type", "plain/text")
                .withBody(exact("{ \"id\": \"abc\", \"name\": \"abc\" }"))
        ));
    }

    @Test
    public void shouldMatchByPlainTextBodyWithRequiredFalse() {
        // given
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(mockServerLogger);

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
        assertTrue(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath")
                .withHeader("content-type", "plain/text")
                .withBody(exact("{ \"id\": 1, \"name\": \"abc\" }"))
        ));
        assertTrue(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath")
                .withHeader("content-type", "plain/text; charset=utf-8")
                .withBody(exact("{ \"id\": 1, \"name\": \"abc\" }"))
        ));
        assertTrue(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath")
        ));
        assertTrue(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath")
                .withHeader("content-type", "plain/text")
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath")
                .withHeader("content-type", "plain/text")
                .withBody(exact("{ \"id\": 1, \"name\": 1 }"))
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath")
                .withHeader("content-type", "plain/text")
                .withBody(exact("{ \"id\": \"abc\", \"name\": \"abc\" }"))
        ));
    }

    @Test
    public void shouldMatchByPlainTextBodyWithRequiredDefaulted() {
        // given
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(mockServerLogger);

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
        assertTrue(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath")
                .withHeader("content-type", "plain/text")
                .withBody(exact("{ \"id\": 1, \"name\": \"abc\" }"))
        ));
        assertTrue(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath")
                .withHeader("content-type", "plain/text; charset=utf-8")
                .withBody(exact("{ \"id\": 1, \"name\": \"abc\" }"))
        ));
        assertTrue(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath")
        ));
        assertTrue(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath")
                .withHeader("content-type", "plain/text")
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath")
                .withHeader("content-type", "plain/text")
                .withBody(exact("{ \"id\": 1, \"name\": 1 }"))
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath")
                .withHeader("content-type", "plain/text")
                .withBody(exact("{ \"id\": \"abc\", \"name\": \"abc\" }"))
        ));
    }

    @Test
    public void shouldMatchByPlainTextBodyWithMediaTypeRange() {
        // given
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(mockServerLogger);

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
        assertTrue(httpRequestsPropertiesMatcher.matches(
            request()
                .withHeader("content-type", "plain/text")
                .withBody(exact("{ \"id\": 1, \"name\": \"abc\" }"))
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
                .withHeader("content-type", "plain/text")
                .withBody(exact("{ \"id\": 1, \"name\": 1 }"))
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
                .withHeader("content-type", "plain/text")
                .withBody(exact("{ \"id\": \"abc\", \"name\": \"abc\" }"))
        ));
    }

    @Test
    public void shouldMatchByPlainTextBodyWithDefaultMediaType() {
        // given
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(mockServerLogger);

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
        assertTrue(httpRequestsPropertiesMatcher.matches(
            request()
                .withBody(exact("{ \"id\": 1, \"name\": \"abc\" }"))
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
                .withBody(exact("{ \"id\": 1, \"name\": 1 }"))
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
                .withBody(exact("{ \"id\": \"abc\", \"name\": \"abc\" }"))
        ));
    }

    @Test
    public void shouldMatchByPlainTextBodyWithSchemaComponent() {
        // given
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(mockServerLogger);

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
        assertTrue(httpRequestsPropertiesMatcher.matches(
            request()
                .withHeader("content-type", "plain/text")
                .withBody(exact("{ \"id\": 1, \"name\": \"abc\" }"))
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
                .withHeader("content-type", "plain/text")
                .withBody(exact("{ \"id\": 1, \"name\": 1 }"))
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
                .withHeader("content-type", "plain/text")
                .withBody(exact("{ \"id\": \"abc\", \"name\": \"abc\" }"))
        ));
    }

    @Test
    public void shouldMatchByPlainTextBodyWithRequestBodyAndSchemaComponent() {
        // given
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(mockServerLogger);

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
        assertTrue(httpRequestsPropertiesMatcher.matches(
            request()
                .withHeader("content-type", "plain/text")
                .withBody(exact("{ \"id\": 1, \"name\": \"abc\" }"))
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
                .withHeader("content-type", "plain/text")
                .withBody(exact("{ \"id\": 1, \"name\": 1 }"))
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
                .withHeader("content-type", "plain/text")
                .withBody(exact("{ \"id\": \"abc\", \"name\": \"abc\" }"))
        ));
    }

    // - XML BODY (via JsonSchema)

    @Test
    public void shouldMatchByXmlBody() {
        // given
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(mockServerLogger);

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
        assertTrue(httpRequestsPropertiesMatcher.matches(
            request()
                .withHeader("content-type", "application/xml")
                .withBody(xml("<root><id>1</id><name>abc</name></root>"))
        ));
        assertTrue(httpRequestsPropertiesMatcher.matches(
            request()
                .withHeader("content-type", "application/xml; charset=utf-8")
                .withBody(xml("<root><id>1</id><name>abc</name></root>"))
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
                .withHeader("content-type", "application/xml")
                .withBody(xml("<root><id>1</id><name>1</name></root>"))
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
                .withHeader("content-type", "application/xml")
                .withBody(xml("<root><id>abc</id><name>1</name></root>"))
        ));
    }

    @Test
    public void shouldMatchByXmlBodyWithRequiredFalse() {
        // given
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(mockServerLogger);

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
        assertTrue(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath")
                .withHeader("content-type", "application/xml")
                .withBody(xml("<root><id>1</id><name>abc</name></root>"))
        ));
        assertTrue(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath")
                .withHeader("content-type", "application/xml; charset=utf-8")
                .withBody(xml("<root><id>1</id><name>abc</name></root>"))
        ));
        assertTrue(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath")
        ));
        assertTrue(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath")
                .withHeader("content-type", "application/xml")
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath")
                .withHeader("content-type", "application/xml")
                .withBody(xml("<root><id>1</id><name>1</name></root>"))
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath")
                .withHeader("content-type", "application/xml")
                .withBody(xml("<root><id>abc</id><name>1</name></root>"))
        ));
    }

    @Test
    public void shouldMatchByXmlBodyWithRequiredDefaulted() {
        // given
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(mockServerLogger);

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
        assertTrue(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath")
                .withHeader("content-type", "application/xml")
                .withBody(xml("<root><id>1</id><name>abc</name></root>"))
        ));
        assertTrue(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath")
                .withHeader("content-type", "application/xml; charset=utf-8")
                .withBody(xml("<root><id>1</id><name>abc</name></root>"))
        ));
        assertTrue(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath")
        ));
        assertTrue(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath")
                .withHeader("content-type", "application/xml")
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath")
                .withHeader("content-type", "application/xml")
                .withBody(xml("<root><id>1</id><name>1</name></root>"))
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath")
                .withHeader("content-type", "application/xml")
                .withBody(xml("<root><id>abc</id><name>1</name></root>"))
        ));
    }

    @Test
    public void shouldMatchByXmlBodyWithSchemaComponent() {
        // given
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(mockServerLogger);

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
        assertTrue(httpRequestsPropertiesMatcher.matches(
            request()
                .withHeader("content-type", "application/xml")
                .withBody(xml("<root><id>1</id><name>abc</name></root>"))
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
                .withHeader("content-type", "application/xml")
                .withBody(xml("<root><id>1</id><name>1</name></root>"))
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
                .withHeader("content-type", "application/xml")
                .withBody(xml("<root><id>abc</id><name>1</name></root>"))
        ));
    }

    @Test
    public void shouldMatchByXmlBodyWithRequestBodyAndSchemaComponent() {
        // given
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(mockServerLogger);

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
        assertTrue(httpRequestsPropertiesMatcher.matches(
            request()
                .withHeader("content-type", "application/xml")
                .withBody(xml("<root><id>1</id><name>abc</name></root>"))
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
                .withHeader("content-type", "application/xml")
                .withBody(xml("<root><id>1</id><name>1</name></root>"))
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
                .withHeader("content-type", "application/xml")
                .withBody(xml("<root><id>abc</id><name>1</name></root>"))
        ));
    }

    // - FORM BODY (via JsonSchema)

    @Test
    public void shouldMatchByFormBody() {
        // given
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(mockServerLogger);

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
        assertTrue(httpRequestsPropertiesMatcher.matches(
            request()
                .withHeader("content-type", "application/x-www-form-urlencoded")
                .withBody("id=1&name=abc")
        ));
        assertTrue(httpRequestsPropertiesMatcher.matches(
            request()
                .withHeader("content-type", "application/x-www-form-urlencoded; charset=utf-8")
                .withBody("id=1&name=abc")
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
                .withHeader("content-type", "application/x-www-form-urlencoded")
                .withBody("id=1&name=1")
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
                .withHeader("content-type", "application/x-www-form-urlencoded")
                .withBody("id=abc&name=1")
        ));
    }

    @Test
    public void shouldMatchByFormBodyWithFormEncoding() {
        // given
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(mockServerLogger);

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
        assertTrue(httpRequestsPropertiesMatcher.matches(
            request()
                .withHeader("content-type", "application/x-www-form-urlencoded")
                .withBody("id=1,2,3&name=abc&name=def")
        ));
        assertTrue(httpRequestsPropertiesMatcher.matches(
            request()
                .withHeader("content-type", "application/x-www-form-urlencoded; charset=utf-8")
                .withBody("id=1,2,3&name=abc&name=def")
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
                .withHeader("content-type", "application/x-www-form-urlencoded")
                .withBody("id=1,2,3&name=1&name=def")
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
                .withHeader("content-type", "application/x-www-form-urlencoded")
                .withBody("id=a,2,3&name=abc&name=def")
        ));
    }

    @Test
    public void shouldMatchByFormBodyWithSpaceDelimitedEncoding() {
        // given
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(mockServerLogger);

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
        assertTrue(httpRequestsPropertiesMatcher.matches(
            request()
                .withHeader("content-type", "application/x-www-form-urlencoded")
                .withBody("id=1%202%203&name=abc&name=def")
        ));
        assertTrue(httpRequestsPropertiesMatcher.matches(
            request()
                .withHeader("content-type", "application/x-www-form-urlencoded")
                .withBody("id=1+2+3&name=abc&name=def")
        ));
        assertTrue(httpRequestsPropertiesMatcher.matches(
            request()
                .withHeader("content-type", "application/x-www-form-urlencoded")
                .withBody("id=1 2 3&name=abc&name=def")
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
                .withHeader("content-type", "application/x-www-form-urlencoded")
                .withBody("id=1%202%203&name=1&name=def")
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
                .withHeader("content-type", "application/x-www-form-urlencoded")
                .withBody("id=1+2+3&name=1&name=def")
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
                .withHeader("content-type", "application/x-www-form-urlencoded")
                .withBody("id=1 2 3&name=1&name=def")
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
                .withHeader("content-type", "application/x-www-form-urlencoded")
                .withBody("id=a%202%203&name=abc&name=def")
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
                .withHeader("content-type", "application/x-www-form-urlencoded")
                .withBody("id=a+2+3&name=abc&name=def")
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
                .withHeader("content-type", "application/x-www-form-urlencoded")
                .withBody("id=a 2 3&name=abc&name=def")
        ));
    }

    @Test
    public void shouldMatchByFormBodyWithPipeDelimitedEncoding() {
        // given
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(mockServerLogger);

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
        assertTrue(httpRequestsPropertiesMatcher.matches(
            request()
                .withHeader("content-type", "application/x-www-form-urlencoded")
                .withBody("id=1|2|3&name=abc&name=def")
        ));
        assertTrue(httpRequestsPropertiesMatcher.matches(
            request()
                .withHeader("content-type", "application/x-www-form-urlencoded; charset=utf-8")
                .withBody("id=1|2|3&name=abc&name=def")
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
                .withHeader("content-type", "application/x-www-form-urlencoded")
                .withBody("id=1|2|3&name=1&name=def")
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
                .withHeader("content-type", "application/x-www-form-urlencoded")
                .withBody("id=a|2|3&name=abc&name=def")
        ));
    }

    @Test
    public void shouldMatchByFormBodyWithRequiredFalse() {
        // given
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(mockServerLogger);

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
        assertTrue(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath")
                .withHeader("content-type", "application/x-www-form-urlencoded")
                .withBody("id=1&name=abc")
        ));
        assertTrue(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath")
                .withHeader("content-type", "application/x-www-form-urlencoded; charset=utf-8")
                .withBody("id=1&name=abc")
        ));
        assertTrue(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath")
        ));
        assertTrue(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath")
                .withHeader("content-type", "application/x-www-form-urlencoded; charset=utf-8")
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath")
                .withHeader("content-type", "application/x-www-form-urlencoded")
                .withBody("id=1&name=1")
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath")
                .withHeader("content-type", "application/x-www-form-urlencoded")
                .withBody("id=abc&name=1")
        ));
    }

    @Test
    public void shouldMatchByFormBodyWithRequiredDefaulted() {
        // given
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(mockServerLogger);

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
        assertTrue(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath")
                .withHeader("content-type", "application/x-www-form-urlencoded")
                .withBody("id=1&name=abc")
        ));
        assertTrue(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath")
                .withHeader("content-type", "application/x-www-form-urlencoded; charset=utf-8")
                .withBody("id=1&name=abc")
        ));
        assertTrue(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath")
        ));
        assertTrue(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath")
                .withHeader("content-type", "application/x-www-form-urlencoded; charset=utf-8")
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath")
                .withHeader("content-type", "application/x-www-form-urlencoded")
                .withBody("id=1&name=1")
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
                .withPath("/somePath")
                .withHeader("content-type", "application/x-www-form-urlencoded")
                .withBody("id=abc&name=1")
        ));
    }

    @Test
    public void shouldMatchByFormBodyWithSchemaComponent() {
        // given
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(mockServerLogger);

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
        assertTrue(httpRequestsPropertiesMatcher.matches(
            request()
                .withHeader("content-type", "application/x-www-form-urlencoded")
                .withBody("id=1&name=abc")
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
                .withHeader("content-type", "application/x-www-form-urlencoded")
                .withBody("id=1&name=1")
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
                .withHeader("content-type", "application/x-www-form-urlencoded")
                .withBody("id=abc&name=1")
        ));
    }

    @Test
    public void shouldMatchByFormBodyWithRequestBodyAndSchemaComponent() {
        // given
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(mockServerLogger);

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
        assertTrue(httpRequestsPropertiesMatcher.matches(
            request()
                .withHeader("content-type", "application/x-www-form-urlencoded")
                .withBody("id=1&name=abc")
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
                .withHeader("content-type", "application/x-www-form-urlencoded")
                .withBody("id=1&name=1")
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
                .withHeader("content-type", "application/x-www-form-urlencoded")
                .withBody("id=abc&name=1")
        ));
    }

    @Test
    public void shouldHandleBlankStrings() {
        // given
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(mockServerLogger);
        httpRequestsPropertiesMatcher.update(new Expectation(
            new OpenAPIDefinition()
                .withSpecUrlOrPayload("")
                .withOperationId("")
        ));
        HttpRequest httpRequest = request();
        MatchDifference matchDifference = new MatchDifference(httpRequest);

        // when
        boolean matches = httpRequestsPropertiesMatcher.matches(matchDifference, httpRequest);

        // then
        thenMatchesEmptyFieldDifferences(matchDifference, matches, true);
    }

    // SECURITY SCHEMES

    // HEADER (SECURITY SCHEMES)

    @Test
    public void shouldMatchBySecuritySchemeInHeaderWithBasic() {
        // given
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(mockServerLogger);

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
        assertTrue(httpRequestsPropertiesMatcher.matches(
            request()
                .withHeader("Authorization", "basic " + UUIDService.getUUID())
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
                .withHeader("Authorization", "basic")
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
                .withHeader("Authorization", "wrong_scheme " + UUIDService.getUUID())
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
        ));
    }

    @Test
    public void shouldMatchBySecuritySchemeInHeaderWithBearer() {
        // given
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(mockServerLogger);

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
        assertTrue(httpRequestsPropertiesMatcher.matches(
            request()
                .withHeader("Authorization", "bearer " + UUIDService.getUUID())
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
                .withHeader("Authorization", "bearer")
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
                .withHeader("Authorization", "wrong_scheme " + UUIDService.getUUID())
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
        ));
    }

    @Test
    public void shouldMatchBySecuritySchemeInHeaderWithApiKey() {
        // given
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(mockServerLogger);

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
        assertTrue(httpRequestsPropertiesMatcher.matches(
            request()
                .withHeader("X-API-Key", UUIDService.getUUID())
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
                .withHeader("X-API-Key", "")
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
        ));
    }

    @Test
    public void shouldMatchBySecuritySchemeInHeaderWithOpenIdConnect() {
        // given
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(mockServerLogger);

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
        assertTrue(httpRequestsPropertiesMatcher.matches(
            request()
                .withHeader("Authorization", "bearer " + UUIDService.getUUID())
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
        ));
    }

    @Test
    public void shouldMatchBySecuritySchemeInHeaderWithOAuth2() {
        // given
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(mockServerLogger);

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
        assertTrue(httpRequestsPropertiesMatcher.matches(
            request()
                .withHeader("Authorization", "bearer " + UUIDService.getUUID())
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
        ));
    }

    @Test
    public void shouldMatchBySecuritySchemeInHeaderWithMultiAuthorizationHeaderSchemes() {
        // given
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(mockServerLogger);

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
        assertTrue(httpRequestsPropertiesMatcher.matches(
            request()
                .withHeader("Authorization", "basic " + UUIDService.getUUID())
        ));
        assertTrue(httpRequestsPropertiesMatcher.matches(
            request()
                .withHeader("Authorization", "bearer " + UUIDService.getUUID())
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
                .withHeader("Authorization", "bearer")
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
                .withHeader("Authorization", "wrong_scheme " + UUIDService.getUUID())
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
        ));
    }

    @Test
    public void shouldMatchBySecuritySchemeInHeaderWithMultiSchemesIncludingAPIKey() {
        // given
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(mockServerLogger);

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
        assertTrue(httpRequestsPropertiesMatcher.matches(
            request()
                .withHeader("Authorization", "basic " + UUIDService.getUUID())
        ));
        assertTrue(httpRequestsPropertiesMatcher.matches(
            request()
                .withHeader("Authorization", "bearer " + UUIDService.getUUID())
        ));
        assertTrue(httpRequestsPropertiesMatcher.matches(
            request()
                .withHeader("X-API-Key", UUIDService.getUUID())
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
                .withHeader("Authorization", "bearer")
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
                .withHeader("Authorization", "wrong_scheme " + UUIDService.getUUID())
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
                .withHeader("X-API-Key", "")
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
        ));
    }

    @Test
    public void shouldMatchByDefaultSecuritySchemeInHeaderWithMultiSchemesIncludingAPIKey() {
        // given
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(mockServerLogger);

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
        assertTrue(httpRequestsPropertiesMatcher.matches(
            request()
                .withHeader("Authorization", "basic " + UUIDService.getUUID())
        ));
        assertTrue(httpRequestsPropertiesMatcher.matches(
            request()
                .withHeader("Authorization", "bearer " + UUIDService.getUUID())
        ));
        assertTrue(httpRequestsPropertiesMatcher.matches(
            request()
                .withHeader("X-API-Key", UUIDService.getUUID())
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
                .withHeader("Authorization", "bearer")
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
                .withHeader("Authorization", "wrong_scheme " + UUIDService.getUUID())
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
                .withHeader("X-API-Key", "")
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
        ));
    }

    @Test
    public void shouldMatchByDefaultSecuritySchemeInHeaderWithMultiSchemesIncludingAPIKeyWithOperationOverride() {
        // given
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(mockServerLogger);

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

        // then
        assertTrue(httpRequestsPropertiesMatcher.matches(
            request()
                .withHeader("Authorization", "basic " + UUIDService.getUUID())
        ));
        assertTrue(httpRequestsPropertiesMatcher.matches(
            request()
                .withHeader("Authorization", "bearer " + UUIDService.getUUID())
        ));
        assertTrue(httpRequestsPropertiesMatcher.matches(
            request()
                .withHeader("X-API-Key", UUIDService.getUUID())
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
                .withHeader("Authorization", "bearer")
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
                .withHeader("Authorization", "wrong_scheme " + UUIDService.getUUID())
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
                .withHeader("X-API-Key", "")
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
        ));
    }

    // QUERY STRING PARAMETER (SECURITY SCHEMES)

    @Test
    public void shouldMatchBySecuritySchemeInQueryStringParameterWithBasic() {
        // given
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(mockServerLogger);

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
        assertTrue(httpRequestsPropertiesMatcher.matches(
            request()
                .withQueryStringParameter("Authorization", "basic " + UUIDService.getUUID())
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
                .withQueryStringParameter("Authorization", "basic")
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
                .withQueryStringParameter("Authorization", "wrong_scheme " + UUIDService.getUUID())
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
        ));
    }

    @Test
    public void shouldMatchBySecuritySchemeInQueryStringParameterWithBearer() {
        // given
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(mockServerLogger);

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
        assertTrue(httpRequestsPropertiesMatcher.matches(
            request()
                .withQueryStringParameter("Authorization", "bearer " + UUIDService.getUUID())
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
                .withQueryStringParameter("Authorization", "bearer")
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
                .withQueryStringParameter("Authorization", "wrong_scheme " + UUIDService.getUUID())
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
        ));
    }

    @Test
    public void shouldMatchBySecuritySchemeInQueryStringParameterWithApiKey() {
        // given
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(mockServerLogger);

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
        assertTrue(httpRequestsPropertiesMatcher.matches(
            request()
                .withQueryStringParameter("X-API-Key", UUIDService.getUUID())
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
                .withQueryStringParameter("X-API-Key", "")
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
        ));
    }

    @Test
    public void shouldMatchBySecuritySchemeInQueryStringParameterWithOpenIdConnect() {
        // given
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(mockServerLogger);

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
        assertTrue(httpRequestsPropertiesMatcher.matches(
            request()
                .withQueryStringParameter("Authorization", "bearer " + UUIDService.getUUID())
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
        ));
    }

    @Test
    public void shouldMatchBySecuritySchemeInQueryStringParameterWithOAuth2() {
        // given
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(mockServerLogger);

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
        assertTrue(httpRequestsPropertiesMatcher.matches(
            request()
                .withQueryStringParameter("Authorization", "bearer " + UUIDService.getUUID())
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
        ));
    }

    @Test
    public void shouldMatchBySecuritySchemeInQueryStringParameterWithMultiAuthorizationQueryStringParameterSchemes() {
        // given
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(mockServerLogger);

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
        assertTrue(httpRequestsPropertiesMatcher.matches(
            request()
                .withQueryStringParameter("Authorization", "basic " + UUIDService.getUUID())
        ));
        assertTrue(httpRequestsPropertiesMatcher.matches(
            request()
                .withQueryStringParameter("Authorization", "bearer " + UUIDService.getUUID())
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
                .withQueryStringParameter("Authorization", "bearer")
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
                .withQueryStringParameter("Authorization", "wrong_scheme " + UUIDService.getUUID())
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
        ));
    }

    @Test
    public void shouldMatchBySecuritySchemeInQueryStringParameterWithMultiSchemesIncludingAPIKey() {
        // given
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(mockServerLogger);

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
        assertTrue(httpRequestsPropertiesMatcher.matches(
            request()
                .withQueryStringParameter("Authorization", "basic " + UUIDService.getUUID())
        ));
        assertTrue(httpRequestsPropertiesMatcher.matches(
            request()
                .withQueryStringParameter("Authorization", "bearer " + UUIDService.getUUID())
        ));
        assertTrue(httpRequestsPropertiesMatcher.matches(
            request()
                .withQueryStringParameter("X-API-Key", UUIDService.getUUID())
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
                .withQueryStringParameter("Authorization", "bearer")
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
                .withQueryStringParameter("Authorization", "wrong_scheme " + UUIDService.getUUID())
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
                .withQueryStringParameter("X-API-Key", "")
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
        ));
    }

    @Test
    public void shouldMatchByDefaultSecuritySchemeInQueryStringParameterWithMultiSchemesIncludingAPIKey() {
        // given
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(mockServerLogger);

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
        assertTrue(httpRequestsPropertiesMatcher.matches(
            request()
                .withQueryStringParameter("Authorization", "basic " + UUIDService.getUUID())
        ));
        assertTrue(httpRequestsPropertiesMatcher.matches(
            request()
                .withQueryStringParameter("Authorization", "bearer " + UUID.randomUUID().toString())
        ));
        assertTrue(httpRequestsPropertiesMatcher.matches(
            request()
                .withQueryStringParameter("X-API-Key", UUID.randomUUID().toString())
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
                .withQueryStringParameter("Authorization", "bearer")
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
                .withQueryStringParameter("Authorization", "wrong_scheme " + UUID.randomUUID().toString())
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
                .withQueryStringParameter("X-API-Key", "")
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
        ));
    }

    @Test
    public void shouldMatchByDefaultSecuritySchemeInQueryStringParameterWithMultiSchemesIncludingAPIKeyWithOperationOverride() {
        // given
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(mockServerLogger);

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

        // then
        assertTrue(httpRequestsPropertiesMatcher.matches(
            request()
                .withQueryStringParameter("Authorization", "basic " + UUID.randomUUID().toString())
        ));
        assertTrue(httpRequestsPropertiesMatcher.matches(
            request()
                .withQueryStringParameter("Authorization", "bearer " + UUID.randomUUID().toString())
        ));
        assertTrue(httpRequestsPropertiesMatcher.matches(
            request()
                .withQueryStringParameter("X-API-Key", UUID.randomUUID().toString())
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
                .withQueryStringParameter("Authorization", "bearer")
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
                .withQueryStringParameter("Authorization", "wrong_scheme " + UUID.randomUUID().toString())
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
                .withQueryStringParameter("X-API-Key", "")
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
        ));
    }

    // COOKIE (SECURITY SCHEMES)

    @Test
    public void shouldMatchBySecuritySchemeInCookieWithBasic() {
        // given
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(mockServerLogger);

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
        assertTrue(httpRequestsPropertiesMatcher.matches(
            request()
                .withCookie("Authorization", "basic " + UUID.randomUUID().toString())
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
                .withCookie("Authorization", "basic")
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
                .withCookie("Authorization", "wrong_scheme " + UUID.randomUUID().toString())
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
        ));
    }

    @Test
    public void shouldMatchBySecuritySchemeInCookieWithBearer() {
        // given
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(mockServerLogger);

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
        assertTrue(httpRequestsPropertiesMatcher.matches(
            request()
                .withCookie("Authorization", "bearer " + UUID.randomUUID().toString())
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
                .withCookie("Authorization", "bearer")
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
                .withCookie("Authorization", "wrong_scheme " + UUID.randomUUID().toString())
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
        ));
    }

    @Test
    public void shouldMatchBySecuritySchemeInCookieWithApiKey() {
        // given
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(mockServerLogger);

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
        assertTrue(httpRequestsPropertiesMatcher.matches(
            request()
                .withCookie("X-API-Key", UUID.randomUUID().toString())
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
                .withCookie("X-API-Key", "")
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
        ));
    }

    @Test
    public void shouldMatchBySecuritySchemeInCookieWithOpenIdConnect() {
        // given
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(mockServerLogger);

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
        assertTrue(httpRequestsPropertiesMatcher.matches(
            request()
                .withCookie("Authorization", "bearer " + UUID.randomUUID().toString())
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
        ));
    }

    @Test
    public void shouldMatchBySecuritySchemeInCookieWithOAuth2() {
        // given
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(mockServerLogger);

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
        assertTrue(httpRequestsPropertiesMatcher.matches(
            request()
                .withCookie("Authorization", "bearer " + UUID.randomUUID().toString())
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
        ));
    }

    @Test
    public void shouldMatchBySecuritySchemeInCookieWithMultiAuthorizationCookieSchemes() {
        // given
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(mockServerLogger);

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
        assertTrue(httpRequestsPropertiesMatcher.matches(
            request()
                .withCookie("Authorization", "basic " + UUID.randomUUID().toString())
        ));
        assertTrue(httpRequestsPropertiesMatcher.matches(
            request()
                .withCookie("Authorization", "bearer " + UUID.randomUUID().toString())
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
                .withCookie("Authorization", "bearer")
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
                .withCookie("Authorization", "wrong_scheme " + UUID.randomUUID().toString())
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
        ));
    }

    @Test
    public void shouldMatchBySecuritySchemeInCookieWithMultiSchemesIncludingAPIKey() {
        // given
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(mockServerLogger);

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
        assertTrue(httpRequestsPropertiesMatcher.matches(
            request()
                .withCookie("Authorization", "basic " + UUID.randomUUID().toString())
        ));
        assertTrue(httpRequestsPropertiesMatcher.matches(
            request()
                .withCookie("Authorization", "bearer " + UUID.randomUUID().toString())
        ));
        assertTrue(httpRequestsPropertiesMatcher.matches(
            request()
                .withCookie("X-API-Key", UUID.randomUUID().toString())
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
                .withCookie("Authorization", "bearer")
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
                .withCookie("Authorization", "wrong_scheme " + UUID.randomUUID().toString())
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
                .withCookie("X-API-Key", "")
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
        ));
    }

    @Test
    public void shouldMatchByDefaultSecuritySchemeInCookieWithMultiSchemesIncludingAPIKey() {
        // given
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(mockServerLogger);

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
        assertTrue(httpRequestsPropertiesMatcher.matches(
            request()
                .withCookie("Authorization", "basic " + UUID.randomUUID().toString())
        ));
        assertTrue(httpRequestsPropertiesMatcher.matches(
            request()
                .withCookie("Authorization", "bearer " + UUID.randomUUID().toString())
        ));
        assertTrue(httpRequestsPropertiesMatcher.matches(
            request()
                .withCookie("X-API-Key", UUID.randomUUID().toString())
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
                .withCookie("Authorization", "bearer")
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
                .withCookie("Authorization", "wrong_scheme " + UUID.randomUUID().toString())
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
                .withCookie("X-API-Key", "")
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
        ));
    }

    @Test
    public void shouldMatchByDefaultSecuritySchemeInCookieWithMultiSchemesIncludingAPIKeyWithOperationOverride() {
        // given
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(mockServerLogger);

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

        // then
        assertTrue(httpRequestsPropertiesMatcher.matches(
            request()
                .withCookie("Authorization", "basic " + UUID.randomUUID().toString())
        ));
        assertTrue(httpRequestsPropertiesMatcher.matches(
            request()
                .withCookie("Authorization", "bearer " + UUID.randomUUID().toString())
        ));
        assertTrue(httpRequestsPropertiesMatcher.matches(
            request()
                .withCookie("X-API-Key", UUID.randomUUID().toString())
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
                .withCookie("Authorization", "bearer")
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
                .withCookie("Authorization", "wrong_scheme " + UUID.randomUUID().toString())
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
                .withCookie("X-API-Key", "")
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
        ));
    }

    // HEADER & QUERY STRING PARAMETER & COOKIE (SECURITY SCHEMES)

    @Test
    public void shouldMatchByDefaultSecuritySchemeInMultipleWithMultiSchemesIncludingAPIKey() {
        // given
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(mockServerLogger);

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
        assertTrue(httpRequestsPropertiesMatcher.matches(
            request()
                .withHeader("Authorization", "basic " + UUID.randomUUID().toString())
        ));
        assertTrue(httpRequestsPropertiesMatcher.matches(
            request()
                .withCookie("Authorization", "bearer " + UUID.randomUUID().toString())
        ));
        assertTrue(httpRequestsPropertiesMatcher.matches(
            request()
                .withQueryStringParameter("X-API-Key", UUID.randomUUID().toString())
        ));
        // TODO(limitation): if multiple security requirements in different parameters types and missing
        //                   from all request will still match as each parameter is an optional match
        assertTrue(httpRequestsPropertiesMatcher.matches(
            request()
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
                .withHeader("Authorization", "bearer " + UUID.randomUUID().toString())
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
                .withHeader("Authorization", "wrong_scheme " + UUID.randomUUID().toString())
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
                .withCookie("Authorization", "bearer")
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
                .withCookie("Authorization", "basic " + UUID.randomUUID().toString())
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
                .withCookie("Authorization", "wrong_scheme " + UUID.randomUUID().toString())
        ));
        assertFalse(httpRequestsPropertiesMatcher.matches(
            request()
                .withQueryStringParameter("X-API-Key", "")
        ));
    }

    // OTHER TESTS

    @Test
    public void shouldHandleNulls() {
        // given
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(mockServerLogger);
        httpRequestsPropertiesMatcher.update(new Expectation(
            new OpenAPIDefinition()
                .withSpecUrlOrPayload(null)
                .withOperationId(null)
        ));
        HttpRequest httpRequest = request();
        MatchDifference matchDifference = new MatchDifference(httpRequest);

        // when
        boolean matches = httpRequestsPropertiesMatcher.matches(matchDifference, httpRequest);

        // then
        thenMatchesEmptyFieldDifferences(matchDifference, matches, true);
    }

    @Test
    public void shouldHandleInvalidOpenAPIJsonSpec() {
        // given
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(mockServerLogger);

        try {
            // when
            httpRequestsPropertiesMatcher.update(
                new OpenAPIDefinition()
                    .withSpecUrlOrPayload(FileReader.readFileFromClassPathOrPath("org/mockserver/mock/openapi_petstore_example.json").substring(0, 100))
                    .withOperationId("listPets")
            );

            // then
            fail("expected exception");
        } catch (IllegalArgumentException iae) {
            assertThat(iae.getMessage(), is("Unable to load API spec, Unexpected end-of-input in field name" + NEW_LINE +
                " at [Source: (String)\"{" + NEW_LINE +
                "  \"openapi\": \"3.0.0\"," + NEW_LINE +
                "  \"info\": {" + NEW_LINE +
                "    \"version\": \"1.0.0\"," + NEW_LINE +
                "    \"title\": \"Swagger Petstore\"," + NEW_LINE +
                "    \"li\"; line: 6, column: 8]"));
        }
    }

    @Test
    public void shouldHandleInvalidOpenAPIYamlSpec() {
        // given
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(mockServerLogger);

        try {
            // when
            httpRequestsPropertiesMatcher.update(
                new OpenAPIDefinition()
                    .withSpecUrlOrPayload(FileReader.readFileFromClassPathOrPath("org/mockserver/mock/openapi_petstore_example.yaml").substring(0, 100))
                    .withOperationId("listPets")
            );

            // then
            fail("expected exception");
        } catch (IllegalArgumentException iae) {
            assertThat(iae.getMessage(), is("Unable to load API spec, while scanning a simple key" + NEW_LINE +
                " in 'reader', line 8, column 1:" + NEW_LINE +
                "    servers" + NEW_LINE +
                "    ^" + NEW_LINE +
                "could not find expected ':'" + NEW_LINE +
                " in 'reader', line 8, column 8:" + NEW_LINE +
                "    servers" + NEW_LINE +
                "           ^" + NEW_LINE +
                NEW_LINE +
                " at [Source: (StringReader); line: 8, column: 1]"));
        }
    }

    @Test
    public void shouldHandleInvalidOpenAPIUrl() {
        // given
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(mockServerLogger);

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
            assertThat(iae.getMessage(), is("Unable to load API spec, unable to read location `org/mockserver/mock/does_not_exist.json`"));
        }
    }

    @Test
    public void shouldMatchSingleOperationInOpenAPIJsonUrl() {
        // given
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(mockServerLogger);
        httpRequestsPropertiesMatcher.update(new Expectation(
            new OpenAPIDefinition()
                .withSpecUrlOrPayload("org/mockserver/mock/openapi_petstore_example.json")
                .withOperationId("listPets")
        ));
        HttpRequest httpRequest = request()
            .withMethod("GET")
            .withPath("/pets")
            .withQueryStringParameter("limit", "10");
        MatchDifference matchDifference = new MatchDifference(httpRequest);

        // when
        boolean matches = httpRequestsPropertiesMatcher.matches(matchDifference, httpRequest);

        // then
        thenMatchesEmptyFieldDifferences(matchDifference, matches, true);
    }

    @Test
    public void shouldMatchSingleOperationWithPathVariablesInOpenAPIJsonUrl() {
        // given
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(mockServerLogger);
        httpRequestsPropertiesMatcher.update(new Expectation(
            new OpenAPIDefinition()
                .withSpecUrlOrPayload("org/mockserver/mock/openapi_petstore_example.json")
                .withOperationId("showPetById")
        ));
        HttpRequest httpRequest = request()
            .withMethod("GET")
            .withPath("/pets/12345")
            .withHeader("X-Request-ID", UUID.randomUUID().toString())
            .withHeader("Other-Header", UUID.randomUUID().toString());
        MatchDifference matchDifference = new MatchDifference(httpRequest);

        // when
        boolean matches = httpRequestsPropertiesMatcher.matches(matchDifference, httpRequest);

        // then
        thenMatchesEmptyFieldDifferences(matchDifference, matches, true);
    }

    @Test
    public void shouldMatchSingleOperationInOpenAPIJsonSpec() {
        // given
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(mockServerLogger);
        httpRequestsPropertiesMatcher.update(new Expectation(
            new OpenAPIDefinition()
                .withSpecUrlOrPayload(FileReader.readFileFromClassPathOrPath("org/mockserver/mock/openapi_petstore_example.json"))
                .withOperationId("listPets")
        ));
        HttpRequest httpRequest = request()
            .withMethod("GET")
            .withPath("/pets")
            .withQueryStringParameter("limit", "10");
        MatchDifference matchDifference = new MatchDifference(httpRequest);

        // when
        boolean matches = httpRequestsPropertiesMatcher.matches(matchDifference, httpRequest);

        // then
        thenMatchesEmptyFieldDifferences(matchDifference, matches, true);
    }

    @Test
    public void shouldMatchSingleOperationInOpenAPIYamlUrl() {
        // given
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(mockServerLogger);
        httpRequestsPropertiesMatcher.update(new Expectation(
            new OpenAPIDefinition()
                .withSpecUrlOrPayload("org/mockserver/mock/openapi_petstore_example.yaml")
                .withOperationId("listPets")
        ));
        HttpRequest httpRequest = request()
            .withMethod("GET")
            .withPath("/pets")
            .withQueryStringParameter("limit", "10");
        MatchDifference matchDifference = new MatchDifference(httpRequest);

        // when
        boolean matches = httpRequestsPropertiesMatcher.matches(matchDifference, httpRequest);

        // then
        thenMatchesEmptyFieldDifferences(matchDifference, matches, true);
    }

    @Test
    public void shouldMatchSingleOperationInOpenAPIYamlSpec() {
        // given
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(mockServerLogger);
        httpRequestsPropertiesMatcher.update(new Expectation(
            new OpenAPIDefinition()
                .withSpecUrlOrPayload(FileReader.readFileFromClassPathOrPath("org/mockserver/mock/openapi_petstore_example.yaml"))
                .withOperationId("listPets")
        ));
        HttpRequest httpRequest = request()
            .withMethod("GET")
            .withPath("/pets")
            .withQueryStringParameter("limit", "10");
        MatchDifference matchDifference = new MatchDifference(httpRequest);

        // when
        boolean matches = httpRequestsPropertiesMatcher.matches(matchDifference, httpRequest);

        // then
        thenMatchesEmptyFieldDifferences(matchDifference, matches, true);
    }

    @Test
    public void shouldMatchAnyOperationInOpenAPI() {
        // given
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(mockServerLogger);
        httpRequestsPropertiesMatcher.update(new Expectation(
            new OpenAPIDefinition()
                .withSpecUrlOrPayload("org/mockserver/mock/openapi_petstore_example.json")
                .withOperationId("")
        ));
        HttpRequest httpRequest = request()
            .withMethod("GET")
            .withPath("/pets")
            .withQueryStringParameter("limit", "10");
        MatchDifference matchDifference = new MatchDifference(httpRequest);

        // when
        boolean matches = httpRequestsPropertiesMatcher.matches(matchDifference, httpRequest);

        // then
        thenMatchesEmptyFieldDifferences(matchDifference, matches, true);
    }

    @Test
    public void shouldNotMatchRequestWithWrongOperationIdInOpenAPI() {
        // given
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(mockServerLogger);
        httpRequestsPropertiesMatcher.update(new Expectation(
            new OpenAPIDefinition()
                .withSpecUrlOrPayload("org/mockserver/mock/openapi_petstore_example.json")
                .withOperationId("showPetById")
        ));
        HttpRequest httpRequest = request()
            .withMethod("GET")
            .withPath("/pets")
            .withQueryStringParameter("limit", "10");
        MatchDifference matchDifference = new MatchDifference(httpRequest);

        // when
        boolean matches = httpRequestsPropertiesMatcher.matches(matchDifference, httpRequest);

        // then
        assertThat(matches, is(false));
        assertThat(matchDifference.getDifferences(METHOD), nullValue());
        assertThat(matchDifference.getDifferences(PATH), containsInAnyOrder(
            "  string or regex match failed expected:" + NEW_LINE +
                NEW_LINE +
                "    /pets/.*" + NEW_LINE +
                NEW_LINE +
                "   found:" + NEW_LINE +
                NEW_LINE +
                "    /pets" + NEW_LINE,
            "  expected path /pets/{petId} has 2 parts but path /pets has 1 part "
        ));
        assertThat(matchDifference.getDifferences(QUERY_PARAMETERS), nullValue());
        assertThat(matchDifference.getDifferences(COOKIES), nullValue());
        assertThat(matchDifference.getDifferences(HEADERS), nullValue());
        assertThat(matchDifference.getDifferences(BODY), nullValue());
        assertThat(matchDifference.getDifferences(SSL_MATCHES), nullValue());
        assertThat(matchDifference.getDifferences(KEEP_ALIVE), nullValue());
        assertThat(matchDifference.getDifferences(OPERATION), nullValue());
        assertThat(matchDifference.getDifferences(OPENAPI), nullValue());
    }

    @Test
    public void shouldNotMatchRequestWithWrongOperationIdWithNullContextInOpenAPI() {
        // given
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(mockServerLogger);
        httpRequestsPropertiesMatcher.update(new Expectation(
            new OpenAPIDefinition()
                .withSpecUrlOrPayload("org/mockserver/mock/openapi_petstore_example.json")
                .withOperationId("showPetById")
        ));
        HttpRequest httpRequest = request()
            .withMethod("GET")
            .withPath("/pets")
            .withQueryStringParameter("limit", "10");
        MatchDifference matchDifference = new MatchDifference(httpRequest);

        // when
        boolean matches = httpRequestsPropertiesMatcher.matches(httpRequest);

        // then
        thenMatchesEmptyFieldDifferences(matchDifference, matches, false);
    }

    @Test
    public void shouldNotMatchRequestWithMethodMismatchInOpenAPI() {
        // given
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(mockServerLogger);
        httpRequestsPropertiesMatcher.update(new Expectation(
            new OpenAPIDefinition()
                .withSpecUrlOrPayload("org/mockserver/mock/openapi_petstore_example.json")
                .withOperationId("showPetById")
        ));
        HttpRequest httpRequest = request()
            .withMethod("PUT")
            .withPath("/pets")
            .withQueryStringParameter("limit", "10");
        MatchDifference matchDifference = new MatchDifference(httpRequest);

        // when
        boolean matches = httpRequestsPropertiesMatcher.matches(matchDifference, httpRequest);

        // then
        assertThat(matches, is(false));
        assertThat(matchDifference.getDifferences(METHOD), containsInAnyOrder("  string or regex match failed expected:" + NEW_LINE +
            NEW_LINE +
            "    GET" + NEW_LINE +
            NEW_LINE +
            "   found:" + NEW_LINE +
            NEW_LINE +
            "    PUT" + NEW_LINE));
        assertThat(matchDifference.getDifferences(PATH), nullValue());
        assertThat(matchDifference.getDifferences(QUERY_PARAMETERS), nullValue());
        assertThat(matchDifference.getDifferences(COOKIES), nullValue());
        assertThat(matchDifference.getDifferences(HEADERS), nullValue());
        assertThat(matchDifference.getDifferences(BODY), nullValue());
        assertThat(matchDifference.getDifferences(SSL_MATCHES), nullValue());
        assertThat(matchDifference.getDifferences(KEEP_ALIVE), nullValue());
        assertThat(matchDifference.getDifferences(OPERATION), nullValue());
        assertThat(matchDifference.getDifferences(OPENAPI), nullValue());
    }

    @Test
    public void shouldNotMatchRequestWithPathMismatchInOpenAPI() {
        // given
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(mockServerLogger);
        httpRequestsPropertiesMatcher.update(new Expectation(
            new OpenAPIDefinition()
                .withSpecUrlOrPayload("org/mockserver/mock/openapi_petstore_example.json")
                .withOperationId("showPetById")
        ));
        HttpRequest httpRequest = request()
            .withMethod("GET")
            .withPath("/wrong")
            .withQueryStringParameter("limit", "10");
        MatchDifference matchDifference = new MatchDifference(httpRequest);

        // when
        boolean matches = httpRequestsPropertiesMatcher.matches(matchDifference, httpRequest);

        // then
        assertThat(matches, is(false));
        assertThat(matchDifference.getDifferences(METHOD), nullValue());
        assertThat(matchDifference.getDifferences(PATH), containsInAnyOrder(
            "  string or regex match failed expected:" + NEW_LINE +
                NEW_LINE +
                "    /pets/.*" + NEW_LINE +
                NEW_LINE +
                "   found:" + NEW_LINE +
                NEW_LINE +
                "    /wrong" + NEW_LINE,
            "  expected path /pets/{petId} has 2 parts but path /wrong has 1 part "
        ));
        assertThat(matchDifference.getDifferences(QUERY_PARAMETERS), nullValue());
        assertThat(matchDifference.getDifferences(COOKIES), nullValue());
        assertThat(matchDifference.getDifferences(HEADERS), nullValue());
        assertThat(matchDifference.getDifferences(BODY), nullValue());
        assertThat(matchDifference.getDifferences(SSL_MATCHES), nullValue());
        assertThat(matchDifference.getDifferences(KEEP_ALIVE), nullValue());
        assertThat(matchDifference.getDifferences(OPERATION), nullValue());
        assertThat(matchDifference.getDifferences(OPENAPI), nullValue());
    }

    @Test
    public void shouldNotMatchRequestWithHeaderMismatchInOpenAPI() {
        // given
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(mockServerLogger);
        httpRequestsPropertiesMatcher.update(new Expectation(
            new OpenAPIDefinition()
                .withSpecUrlOrPayload("org/mockserver/mock/openapi_petstore_example.json")
                .withOperationId("somePath")
        ));
        HttpRequest httpRequest = request()
            .withMethod("GET")
            .withPath("/some/path")
            .withQueryStringParameter("limit", "10");
        MatchDifference matchDifference = new MatchDifference(httpRequest);

        // when
        boolean matches = httpRequestsPropertiesMatcher.matches(matchDifference, httpRequest);

        // then
        assertThat(matches, is(false));
        assertThat(matchDifference.getDifferences(METHOD), nullValue());
        assertThat(matchDifference.getDifferences(PATH), nullValue());
        assertThat(matchDifference.getDifferences(QUERY_PARAMETERS), nullValue());
        assertThat(matchDifference.getDifferences(COOKIES), nullValue());
        assertThat(matchDifference.getDifferences(HEADERS), containsInAnyOrder("  multimap subset match failed expected:" + NEW_LINE +
            NEW_LINE +
            "    {" + NEW_LINE +
            "      \"keyMatchStyle\" : \"MATCHING_KEY\"," + NEW_LINE +
            "      \"X-Request-ID\" : {" + NEW_LINE +
            "        \"parameterStyle\" : \"SIMPLE\"," + NEW_LINE +
            "        \"values\" : [ {" + NEW_LINE +
            "          \"schema\" : {" + NEW_LINE +
            "            \"type\" : \"string\"," + NEW_LINE +
            "            \"format\" : \"uuid\"" + NEW_LINE +
            "          }" + NEW_LINE +
            "        } ]" + NEW_LINE +
            "      }" + NEW_LINE +
            "    }" + NEW_LINE +
            NEW_LINE +
            "   found:" + NEW_LINE +
            NEW_LINE +
            "    none" + NEW_LINE +
            NEW_LINE +
            "   failed because:" + NEW_LINE +
            NEW_LINE +
            "    none is not a subset" + NEW_LINE));
        assertThat(matchDifference.getDifferences(BODY), nullValue());
        assertThat(matchDifference.getDifferences(SSL_MATCHES), nullValue());
        assertThat(matchDifference.getDifferences(KEEP_ALIVE), nullValue());
        assertThat(matchDifference.getDifferences(OPERATION), nullValue());
        assertThat(matchDifference.getDifferences(OPENAPI), nullValue());
    }

    @Test
    public void shouldNotMatchRequestWithHeaderAndQueryParameterMismatchInOpenAPI() {
        // given
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(mockServerLogger);
        httpRequestsPropertiesMatcher.update(new Expectation(
            new OpenAPIDefinition()
                .withSpecUrlOrPayload("org/mockserver/mock/openapi_petstore_example.json")
                .withOperationId("somePath")
        ));
        HttpRequest httpRequest = request()
            .withMethod("GET")
            .withPath("/some/path")
            .withQueryStringParameter("limit", "not_a_number");
        MatchDifference matchDifference = new MatchDifference(httpRequest);

        // when
        boolean matches = httpRequestsPropertiesMatcher.matches(matchDifference, httpRequest);

        // then
        assertThat(matches, is(false));
        assertThat(matchDifference.getDifferences(METHOD), nullValue());
        assertThat(matchDifference.getDifferences(PATH), nullValue());
        assertThat(matchDifference.getDifferences(QUERY_PARAMETERS), nullValue());
        assertThat(matchDifference.getDifferences(COOKIES), nullValue());
        assertThat(matchDifference.getDifferences(HEADERS), containsInAnyOrder("  multimap subset match failed expected:" + NEW_LINE +
            NEW_LINE +
            "    {" + NEW_LINE +
            "      \"keyMatchStyle\" : \"MATCHING_KEY\"," + NEW_LINE +
            "      \"X-Request-ID\" : {" + NEW_LINE +
            "        \"parameterStyle\" : \"SIMPLE\"," + NEW_LINE +
            "        \"values\" : [ {" + NEW_LINE +
            "          \"schema\" : {" + NEW_LINE +
            "            \"type\" : \"string\"," + NEW_LINE +
            "            \"format\" : \"uuid\"" + NEW_LINE +
            "          }" + NEW_LINE +
            "        } ]" + NEW_LINE +
            "      }" + NEW_LINE +
            "    }" + NEW_LINE +
            NEW_LINE +
            "   found:" + NEW_LINE +
            NEW_LINE +
            "    none" + NEW_LINE +
            NEW_LINE +
            "   failed because:" + NEW_LINE +
            NEW_LINE +
            "    none is not a subset" + NEW_LINE));
        assertThat(matchDifference.getDifferences(BODY), nullValue());
        assertThat(matchDifference.getDifferences(SSL_MATCHES), nullValue());
        assertThat(matchDifference.getDifferences(KEEP_ALIVE), nullValue());
        assertThat(matchDifference.getDifferences(OPERATION), nullValue());
        assertThat(matchDifference.getDifferences(OPENAPI), nullValue());
    }

    @Test
    public void shouldNotMatchRequestWithHeaderAndQueryParameterMismatchInOpenAPIWithoutOperationId() {
        // given
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(mockServerLogger);
        httpRequestsPropertiesMatcher.update(new Expectation(
            new OpenAPIDefinition()
                .withSpecUrlOrPayload("org/mockserver/mock/openapi_petstore_example.json")
        ));
        HttpRequest httpRequest = request()
            .withMethod("GET")
            .withPath("/some/path")
            .withQueryStringParameter("limit", "not_a_number");
        MatchDifference matchDifference = new MatchDifference(httpRequest);

        // when
        boolean matches = httpRequestsPropertiesMatcher.matches(matchDifference, httpRequest);

        // then
        assertThat(matches, is(false));
        assertThat(matchDifference.getDifferences(PATH), containsInAnyOrder("  string or regex match failed expected:" + NEW_LINE +
                NEW_LINE +
                "    /pets" + NEW_LINE +
                NEW_LINE +
                "   found:" + NEW_LINE +
                NEW_LINE +
                "    /some/path" + NEW_LINE,
            "  string or regex match failed expected:" + NEW_LINE +
                NEW_LINE +
                "    /pets/.*" + NEW_LINE +
                NEW_LINE +
                "   found:" + NEW_LINE +
                NEW_LINE +
                "    /some/path" + NEW_LINE));
        assertThat(matchDifference.getDifferences(METHOD), containsInAnyOrder("  string or regex match failed expected:" + NEW_LINE +
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
        assertThat(matchDifference.getDifferences(QUERY_PARAMETERS), nullValue());
        assertThat(matchDifference.getDifferences(COOKIES), nullValue());
        assertThat(matchDifference.getDifferences(HEADERS), containsInAnyOrder("  multimap subset match failed expected:" + NEW_LINE +
            NEW_LINE +
            "    {" + NEW_LINE +
            "      \"keyMatchStyle\" : \"MATCHING_KEY\"," + NEW_LINE +
            "      \"X-Request-ID\" : {" + NEW_LINE +
            "        \"parameterStyle\" : \"SIMPLE\"," + NEW_LINE +
            "        \"values\" : [ {" + NEW_LINE +
            "          \"schema\" : {" + NEW_LINE +
            "            \"type\" : \"string\"," + NEW_LINE +
            "            \"format\" : \"uuid\"" + NEW_LINE +
            "          }" + NEW_LINE +
            "        } ]" + NEW_LINE +
            "      }" + NEW_LINE +
            "    }" + NEW_LINE +
            NEW_LINE +
            "   found:" + NEW_LINE +
            NEW_LINE +
            "    none" + NEW_LINE +
            NEW_LINE +
            "   failed because:" + NEW_LINE +
            NEW_LINE +
            "    none is not a subset" + NEW_LINE));
        assertThat(matchDifference.getDifferences(BODY), nullValue());
        assertThat(matchDifference.getDifferences(SSL_MATCHES), nullValue());
        assertThat(matchDifference.getDifferences(KEEP_ALIVE), nullValue());
        assertThat(matchDifference.getDifferences(OPERATION), nullValue());
        assertThat(matchDifference.getDifferences(OPENAPI), nullValue());
    }

    @Test
    public void shouldNotMatchRequestWithBodyMismatchWithContentTypeInOpenAPI() {
        // given
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(mockServerLogger);
        httpRequestsPropertiesMatcher.update(new Expectation(
            new OpenAPIDefinition()
                .withSpecUrlOrPayload("org/mockserver/mock/openapi_petstore_example.json")
                .withOperationId("createPets")
        ));
        HttpRequest httpRequest = request()
            .withMethod("POST")
            .withPath("/pets")
            .withHeader("content-type", "application/json")
            .withBody(json("{" + NEW_LINE +
                "    \"id\": \"invalid_id_format\", " + NEW_LINE +
                "    \"name\": \"scruffles\", " + NEW_LINE +
                "    \"tag\": \"dog\"" + NEW_LINE +
                "}"));
        MatchDifference matchDifference = new MatchDifference(httpRequest);

        // when
        boolean matches = httpRequestsPropertiesMatcher.matches(matchDifference, httpRequest);

        // then
        assertThat(matches, is(false));
        assertThat(matchDifference.getDifferences(METHOD), nullValue());
        assertThat(matchDifference.getDifferences(PATH), nullValue());
        assertThat(matchDifference.getDifferences(QUERY_PARAMETERS), nullValue());
        assertThat(matchDifference.getDifferences(COOKIES), nullValue());
        assertThat(matchDifference.getDifferences(HEADERS), nullValue());
        String bodyError = "  json schema match failed expected:" + NEW_LINE +
            NEW_LINE +
            "    {" + NEW_LINE +
            "      \"required\" : [ \"id\", \"name\" ]," + NEW_LINE +
            "      \"type\" : \"object\"," + NEW_LINE +
            "      \"properties\" : {" + NEW_LINE +
            "        \"id\" : {" + NEW_LINE +
            "          \"type\" : \"integer\"," + NEW_LINE +
            "          \"format\" : \"int64\"" + NEW_LINE +
            "        }," + NEW_LINE +
            "        \"name\" : {" + NEW_LINE +
            "          \"type\" : \"string\"" + NEW_LINE +
            "        }," + NEW_LINE +
            "        \"tag\" : {" + NEW_LINE +
            "          \"type\" : \"string\"" + NEW_LINE +
            "        }" + NEW_LINE +
            "      }" + NEW_LINE +
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
            "    2 errors:" + NEW_LINE +
            "     - field: \"/id\" for schema: \"/properties/id\" has error: \"instance type (string) does not match any allowed primitive type (allowed: [\"integer\"])\"" + NEW_LINE +
            "     - schema: \"/properties/id\" has error: \"format attribute \"int64\" not supported\"" + NEW_LINE;
        assertThat(matchDifference.getDifferences(BODY), containsInAnyOrder(bodyError, bodyError));
        assertThat(matchDifference.getDifferences(SSL_MATCHES), nullValue());
        assertThat(matchDifference.getDifferences(KEEP_ALIVE), nullValue());
        assertThat(matchDifference.getDifferences(OPERATION), nullValue());
        assertThat(matchDifference.getDifferences(OPENAPI), nullValue());
    }

    @Test
    public void shouldNotMatchRequestWithBodyMismatchWithContentTypeInOpenAPIWithoutOperationID() {
        // given
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(mockServerLogger);
        httpRequestsPropertiesMatcher.update(new Expectation(
            new OpenAPIDefinition()
                .withSpecUrlOrPayload("org/mockserver/mock/openapi_petstore_example.json")
        ));
        HttpRequest httpRequest = request()
            .withMethod("POST")
            .withPath("/pets")
            .withHeader("content-type", "application/json")
            .withBody(json("{" + NEW_LINE +
                "    \"id\": \"invalid_id_format\", " + NEW_LINE +
                "    \"name\": \"scruffles\", " + NEW_LINE +
                "    \"tag\": \"dog\"" + NEW_LINE +
                "}"));
        MatchDifference matchDifference = new MatchDifference(httpRequest);

        // when
        boolean matches = httpRequestsPropertiesMatcher.matches(matchDifference, httpRequest);

        // then
        assertThat(matches, is(false));
        String methodError = "  string or regex match failed expected:" + NEW_LINE +
            NEW_LINE +
            "    GET" + NEW_LINE +
            NEW_LINE +
            "   found:" + NEW_LINE +
            NEW_LINE +
            "    POST" + NEW_LINE;
        assertThat(matchDifference.getDifferences(METHOD), containsInAnyOrder(methodError, methodError, methodError));
        assertThat(matchDifference.getDifferences(PATH), nullValue());
        assertThat(matchDifference.getDifferences(QUERY_PARAMETERS), nullValue());
        assertThat(matchDifference.getDifferences(COOKIES), nullValue());
        assertThat(matchDifference.getDifferences(HEADERS), nullValue());
        String bodyError = "  json schema match failed expected:" + NEW_LINE +
            NEW_LINE +
            "    {" + NEW_LINE +
            "      \"required\" : [ \"id\", \"name\" ]," + NEW_LINE +
            "      \"type\" : \"object\"," + NEW_LINE +
            "      \"properties\" : {" + NEW_LINE +
            "        \"id\" : {" + NEW_LINE +
            "          \"type\" : \"integer\"," + NEW_LINE +
            "          \"format\" : \"int64\"" + NEW_LINE +
            "        }," + NEW_LINE +
            "        \"name\" : {" + NEW_LINE +
            "          \"type\" : \"string\"" + NEW_LINE +
            "        }," + NEW_LINE +
            "        \"tag\" : {" + NEW_LINE +
            "          \"type\" : \"string\"" + NEW_LINE +
            "        }" + NEW_LINE +
            "      }" + NEW_LINE +
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
            "    2 errors:" + NEW_LINE +
            "     - field: \"/id\" for schema: \"/properties/id\" has error: \"instance type (string) does not match any allowed primitive type (allowed: [\"integer\"])\"" + NEW_LINE +
            "     - schema: \"/properties/id\" has error: \"format attribute \"int64\" not supported\"" + NEW_LINE;
        assertThat(matchDifference.getDifferences(BODY), containsInAnyOrder(bodyError, bodyError));
        assertThat(matchDifference.getDifferences(SSL_MATCHES), nullValue());
        assertThat(matchDifference.getDifferences(KEEP_ALIVE), nullValue());
        assertThat(matchDifference.getDifferences(OPERATION), nullValue());
        assertThat(matchDifference.getDifferences(OPENAPI), nullValue());
    }

    @Test
    public void shouldNotMatchRequestInOpenAPIWithBodyMismatch() {
        // given
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(mockServerLogger);
        httpRequestsPropertiesMatcher.update(new Expectation(
            new OpenAPIDefinition()
                .withSpecUrlOrPayload("org/mockserver/mock/openapi_petstore_example.json")
                .withOperationId("createPets")
        ));
        HttpRequest httpRequest = request()
            .withMethod("POST")
            .withPath("/pets")
            .withBody(json("{" + NEW_LINE +
                "    \"id\": \"invalid_id_format\", " + NEW_LINE +
                "    \"name\": \"scruffles\", " + NEW_LINE +
                "    \"tag\": \"dog\"" + NEW_LINE +
                "}"));
        MatchDifference matchDifference = new MatchDifference(httpRequest);

        // when
        boolean matches = httpRequestsPropertiesMatcher.matches(matchDifference, httpRequest);

        // then
        assertThat(matches, is(false));
        assertThat(matchDifference.getDifferences(METHOD), nullValue());
        assertThat(matchDifference.getDifferences(PATH), nullValue());
        assertThat(matchDifference.getDifferences(QUERY_PARAMETERS), nullValue());
        assertThat(matchDifference.getDifferences(COOKIES), nullValue());
        assertThat(matchDifference.getDifferences(HEADERS), nullValue());
        String schemaValidationError = "  json schema match failed expected:" + NEW_LINE +
            NEW_LINE +
            "    {" + NEW_LINE +
            "      \"required\" : [ \"id\", \"name\" ]," + NEW_LINE +
            "      \"type\" : \"object\"," + NEW_LINE +
            "      \"properties\" : {" + NEW_LINE +
            "        \"id\" : {" + NEW_LINE +
            "          \"type\" : \"integer\"," + NEW_LINE +
            "          \"format\" : \"int64\"" + NEW_LINE +
            "        }," + NEW_LINE +
            "        \"name\" : {" + NEW_LINE +
            "          \"type\" : \"string\"" + NEW_LINE +
            "        }," + NEW_LINE +
            "        \"tag\" : {" + NEW_LINE +
            "          \"type\" : \"string\"" + NEW_LINE +
            "        }" + NEW_LINE +
            "      }" + NEW_LINE +
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
            "    2 errors:" + NEW_LINE +
            "     - field: \"/id\" for schema: \"/properties/id\" has error: \"instance type (string) does not match any allowed primitive type (allowed: [\"integer\"])\"" + NEW_LINE +
            "     - schema: \"/properties/id\" has error: \"format attribute \"int64\" not supported\"" + NEW_LINE;
        assertThat(matchDifference.getDifferences(BODY), containsInAnyOrder(schemaValidationError, schemaValidationError));
        assertThat(matchDifference.getDifferences(SSL_MATCHES), nullValue());
        assertThat(matchDifference.getDifferences(KEEP_ALIVE), nullValue());
        assertThat(matchDifference.getDifferences(OPERATION), nullValue());
        assertThat(matchDifference.getDifferences(OPENAPI), nullValue());
    }

    private void thenMatchesEmptyFieldDifferences(MatchDifference matchDifference, boolean matches, boolean expected) {
        assertThat(matches, is(expected));
        assertThat(matchDifference.getDifferences(METHOD), nullValue());
        assertThat(matchDifference.getDifferences(PATH), nullValue());
        assertThat(matchDifference.getDifferences(QUERY_PARAMETERS), nullValue());
        assertThat(matchDifference.getDifferences(COOKIES), nullValue());
        assertThat(matchDifference.getDifferences(HEADERS), nullValue());
        assertThat(matchDifference.getDifferences(BODY), nullValue());
        assertThat(matchDifference.getDifferences(SSL_MATCHES), nullValue());
        assertThat(matchDifference.getDifferences(KEEP_ALIVE), nullValue());
        assertThat(matchDifference.getDifferences(OPERATION), nullValue());
        assertThat(matchDifference.getDifferences(OPENAPI), nullValue());
    }

}