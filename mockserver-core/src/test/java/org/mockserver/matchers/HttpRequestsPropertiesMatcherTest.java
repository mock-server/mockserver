package org.mockserver.matchers;

import org.junit.Test;
import org.mockserver.file.FileReader;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.mock.Expectation;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.OpenAPIDefinition;

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
     * - allowReserved field - TODO(jamesdbloom) is this required?
     * - allowEmptyValue field -> nullable: true in json schema
     * - common parameter for all methods of path
     * - common parameter for all paths
     *
     * Test Pattern For Body Fields:
     * - requestBody (see: https://swagger.io/docs/specification/describing-request-body/)
     * - json (i.e. application/json)
     * - text (i.e. text/plain)
     * - xml (i.e. application/xml)
     * - form parameters (i.e. application/x-www-form-urlencoded)
     * - multipart form data (i.e. multipart/form-data) - TODO(jamesdbloom) is this required?
     * Then:
     * - required: false (or missing) - TODO(jamesdbloom) is this required?
     * - media type range
     * - media type default
     * - required field
     * - encoding (multipart/form-data or application/x-www-form-urlencoded) - TODO(jamesdbloom) is this required?
     *
     * Test Pattern For Security Handlers: - TODO(jamesdbloom) is this required?
     */

    // METHOD
    @Test
    public void shouldMatchByMethod() {
        // when
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(mockServerLogger);
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
        // when
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(mockServerLogger);
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
        // when
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(mockServerLogger);
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
        // when
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(mockServerLogger);
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

    // PATH PARAMETERS

    @Test
    public void shouldMatchByPathWithPathParameter() {
        // when
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(mockServerLogger);
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
        // when
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(mockServerLogger);
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
        // when
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(mockServerLogger);
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
        // when
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(mockServerLogger);
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
        // when
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(mockServerLogger);
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
        // when
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(mockServerLogger);
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

    // QUERY STRING PARAMETERS

    @Test
    public void shouldMatchByQueryStringParameter() {
        // when
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(mockServerLogger);
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
        // when
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(mockServerLogger);
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
        // when
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(mockServerLogger);
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
        // when
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(mockServerLogger);
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
        // when
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(mockServerLogger);
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
        // when
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(mockServerLogger);
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
    public void shouldMatchByQueryStringParameterCommonForPath() {
        // when
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(mockServerLogger);
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
    public void shouldMatchByQueryStringParameterCommonForAllPaths() {
        // when
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(mockServerLogger);
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

    // HEADERS

    @Test
    public void shouldMatchByHeader() {
        // when
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(mockServerLogger);
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
        // when
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(mockServerLogger);
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
        // when
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(mockServerLogger);
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
        // when
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(mockServerLogger);
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
        // when
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(mockServerLogger);
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
    public void shouldMatchByHeaderCommonForPath() {
        // when
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(mockServerLogger);
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
    public void shouldMatchByHeaderCommonForAllPaths() {
        // when
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(mockServerLogger);
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

    // COOKIES

    @Test
    public void shouldMatchByCookie() {
        // when
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(mockServerLogger);
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
        // when
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(mockServerLogger);
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
        // when
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(mockServerLogger);
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
        // when
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(mockServerLogger);
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
        // when
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(mockServerLogger);
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
        // when
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(mockServerLogger);
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
        // when
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(mockServerLogger);
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

    // BODY

    // - JSON BODY (via JsonSchema)

    @Test
    public void shouldMatchByJsonBody() {
        // when
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(mockServerLogger);
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
        // when
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(mockServerLogger);
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
        // when
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(mockServerLogger);
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
        // when
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(mockServerLogger);
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
        // when
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(mockServerLogger);
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
        // when
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(mockServerLogger);
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
    public void shouldMatchByPlainTextBodyWithMediaTypeRange() {
        // when
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(mockServerLogger);
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
        // when
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(mockServerLogger);
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
        // when
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(mockServerLogger);
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
    public void shouldMatchByPlainTextodyWithRequestBodyAndSchemaComponent() {
        // when
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(mockServerLogger);
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
        // when
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(mockServerLogger);
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
    public void shouldMatchByXmlBodyWithSchemaComponent() {
        // when
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(mockServerLogger);
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
        // when
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(mockServerLogger);
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
        // when
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(mockServerLogger);
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
    public void shouldMatchByFormBodyWithSchemaComponent() {
        // when
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(mockServerLogger);
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
        // when
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(mockServerLogger);
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
            assertThat(iae.getMessage(), is("Unable to load API spec from provided URL or payload Unexpected end-of-input in field name" + NEW_LINE +
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
            assertThat(iae.getMessage(), is("Unable to load API spec from provided URL or payload while scanning a simple key" + NEW_LINE +
                " in 'reader', line 8, column 1:" + NEW_LINE +
                "    servers" + NEW_LINE +
                "    ^" + NEW_LINE +
                "could not find expected ':'" + NEW_LINE +
                " in 'reader', line 8, column 8:" + NEW_LINE +
                "    servers" + NEW_LINE +
                "           ^" + NEW_LINE +
                "" + NEW_LINE +
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
            assertThat(iae.getMessage(), is("Unable to load API spec from provided URL or payload"));
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
        assertThat(matchDifference.getDifferences(PATH), containsInAnyOrder("  matcher path /pets/{petId} has 3 parts but matched path /pets has 2 parts "));
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
            "" + NEW_LINE +
            "    GET" + NEW_LINE +
            "" + NEW_LINE +
            "   found:" + NEW_LINE +
            "" + NEW_LINE +
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
        assertThat(matchDifference.getDifferences(PATH), containsInAnyOrder("  matcher path /pets/{petId} has 3 parts but matched path /wrong has 2 parts "));
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
            "" + NEW_LINE +
            "    {" + NEW_LINE +
            "      \"X-Request-ID\" : [ {" + NEW_LINE +
            "        \"type\" : \"string\"," + NEW_LINE +
            "        \"format\" : \"uuid\"" + NEW_LINE +
            "      } ]" + NEW_LINE +
            "    }" + NEW_LINE +
            "" + NEW_LINE +
            "   found:" + NEW_LINE +
            "" + NEW_LINE +
            "    none" + NEW_LINE +
            "" + NEW_LINE +
            "   failed because:" + NEW_LINE +
            "" + NEW_LINE +
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
            "" + NEW_LINE +
            "    {" + NEW_LINE +
            "      \"X-Request-ID\" : [ {" + NEW_LINE +
            "        \"type\" : \"string\"," + NEW_LINE +
            "        \"format\" : \"uuid\"" + NEW_LINE +
            "      } ]" + NEW_LINE +
            "    }" + NEW_LINE +
            "" + NEW_LINE +
            "   found:" + NEW_LINE +
            "" + NEW_LINE +
            "    none" + NEW_LINE +
            "" + NEW_LINE +
            "   failed because:" + NEW_LINE +
            "" + NEW_LINE +
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
                "" + NEW_LINE +
                "    /pets" + NEW_LINE +
                "" + NEW_LINE +
                "   found:" + NEW_LINE +
                "" + NEW_LINE +
                "    /some/path" + NEW_LINE,
            "  string or regex match failed expected:" + NEW_LINE +
                "" + NEW_LINE +
                "    /pets/.*" + NEW_LINE +
                "" + NEW_LINE +
                "   found:" + NEW_LINE +
                "" + NEW_LINE +
                "    /some/path" + NEW_LINE));
        assertThat(matchDifference.getDifferences(METHOD), containsInAnyOrder("  string or regex match failed expected:" + NEW_LINE +
                "" + NEW_LINE +
                "    POST" + NEW_LINE +
                "" + NEW_LINE +
                "   found:" + NEW_LINE +
                "" + NEW_LINE +
                "    GET" + NEW_LINE,
            "  string or regex match failed expected:" + NEW_LINE +
                "" + NEW_LINE +
                "    POST" + NEW_LINE +
                "" + NEW_LINE +
                "   found:" + NEW_LINE +
                "" + NEW_LINE +
                "    GET" + NEW_LINE));
        assertThat(matchDifference.getDifferences(QUERY_PARAMETERS), nullValue());
        assertThat(matchDifference.getDifferences(COOKIES), nullValue());
        assertThat(matchDifference.getDifferences(HEADERS), containsInAnyOrder("  multimap subset match failed expected:" + NEW_LINE +
            "" + NEW_LINE +
            "    {" + NEW_LINE +
            "      \"X-Request-ID\" : [ {" + NEW_LINE +
            "        \"type\" : \"string\"," + NEW_LINE +
            "        \"format\" : \"uuid\"" + NEW_LINE +
            "      } ]" + NEW_LINE +
            "    }" + NEW_LINE +
            "" + NEW_LINE +
            "   found:" + NEW_LINE +
            "" + NEW_LINE +
            "    none" + NEW_LINE +
            "" + NEW_LINE +
            "   failed because:" + NEW_LINE +
            "" + NEW_LINE +
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
            "" + NEW_LINE +
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
            "" + NEW_LINE +
            "   found:" + NEW_LINE +
            "" + NEW_LINE +
            "    {" + NEW_LINE +
            "        \"id\": \"invalid_id_format\", " + NEW_LINE +
            "        \"name\": \"scruffles\", " + NEW_LINE +
            "        \"tag\": \"dog\"" + NEW_LINE +
            "    }" + NEW_LINE +
            "" + NEW_LINE +
            "   failed because:" + NEW_LINE +
            "" + NEW_LINE +
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
            "" + NEW_LINE +
            "    GET" + NEW_LINE +
            "" + NEW_LINE +
            "   found:" + NEW_LINE +
            "" + NEW_LINE +
            "    POST" + NEW_LINE;
        assertThat(matchDifference.getDifferences(METHOD), containsInAnyOrder(methodError, methodError, methodError));
        assertThat(matchDifference.getDifferences(PATH), nullValue());
        assertThat(matchDifference.getDifferences(QUERY_PARAMETERS), nullValue());
        assertThat(matchDifference.getDifferences(COOKIES), nullValue());
        assertThat(matchDifference.getDifferences(HEADERS), nullValue());
        String bodyError = "  json schema match failed expected:" + NEW_LINE +
            "" + NEW_LINE +
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
            "" + NEW_LINE +
            "   found:" + NEW_LINE +
            "" + NEW_LINE +
            "    {" + NEW_LINE +
            "        \"id\": \"invalid_id_format\", " + NEW_LINE +
            "        \"name\": \"scruffles\", " + NEW_LINE +
            "        \"tag\": \"dog\"" + NEW_LINE +
            "    }" + NEW_LINE +
            "" + NEW_LINE +
            "   failed because:" + NEW_LINE +
            "" + NEW_LINE +
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
            "" + NEW_LINE +
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
            "" + NEW_LINE +
            "   found:" + NEW_LINE +
            "" + NEW_LINE +
            "    {" + NEW_LINE +
            "        \"id\": \"invalid_id_format\", " + NEW_LINE +
            "        \"name\": \"scruffles\", " + NEW_LINE +
            "        \"tag\": \"dog\"" + NEW_LINE +
            "    }" + NEW_LINE +
            "" + NEW_LINE +
            "   failed because:" + NEW_LINE +
            "" + NEW_LINE +
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