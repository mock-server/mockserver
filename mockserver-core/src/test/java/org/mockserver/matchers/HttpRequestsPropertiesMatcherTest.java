package org.mockserver.matchers;

import org.junit.Ignore;
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

/**
 * @author jamesdbloom
 */
public class HttpRequestsPropertiesMatcherTest {

    private final MockServerLogger mockServerLogger = new MockServerLogger(HttpRequestsPropertiesMatcherTest.class);

    /*
     - test order
       - path
         - basic
         - with path parameter
       - method
         - basic
       - parameters (see: https://swagger.io/docs/specification/describing-parameters/)
         - path
         - query string
         - header
         - cookie
         - body
         - required field
         - allowReserved field
         - allowEmptyValue field -> nullable: true in json schema
         - common parameter for all methods of path
         - common parameter for all various paths
       - requestBody (see: https://swagger.io/docs/specification/describing-request-body/)
         - json
           - application/json
           - application-* (as json)
           - *-* (as json)
         - xml
           - application/xml
           - application-* (as xml) - could I know this?
           - *-* (as xml) - could I know this?
         - form parameters
           - application/x-www-form-urlencoded
         - form data
           - multipart/form-data

     Swagger v2
     - body format?
     */

    @Test
    public void shouldMatchByMethod() {
        // when
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(mockServerLogger);
        httpRequestsPropertiesMatcher.update(new Expectation(
            new OpenAPIDefinition()
                .withSpecUrlOrPayload("---\n" +
                    "openapi: 3.0.0\n" +
                    "paths:\n" +
                    "  \"/somePath\":\n" +
                    "    get:\n" +
                    "      operationId: someOperation\n")
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
                .withSpecUrlOrPayload("---\n" +
                    "openapi: 3.0.0\n" +
                    "paths:\n" +
                    "  \"/somePath\":\n" +
                    "    get:\n" +
                    "      operationId: someOperation\n")
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

    @Test
    public void shouldMatchByPath() {
        // when
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(mockServerLogger);
        httpRequestsPropertiesMatcher.update(new Expectation(
            new OpenAPIDefinition()
                .withSpecUrlOrPayload("---\n" +
                    "openapi: 3.0.0\n" +
                    "paths:\n" +
                    "  \"/somePath\":\n" +
                    "    get:\n" +
                    "      operationId: someOperation\n")
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
                .withSpecUrlOrPayload("---\n" +
                    "openapi: 3.0.0\n" +
                    "paths:\n" +
                    "  \"/somePath\":\n" +
                    "    get:\n" +
                    "      operationId: someOperation\n")
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

    @Test
    public void shouldMatchByPathWithPathParameter() {
        // when
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(mockServerLogger);
        httpRequestsPropertiesMatcher.update(new Expectation(
            new OpenAPIDefinition()
                .withSpecUrlOrPayload("---\n" +
                    "openapi: 3.0.0\n" +
                    "paths:\n" +
                    "  \"/somePath/{someParam}\":\n" +
                    "    get:\n" +
                    "      operationId: someOperation\n" +
                    "      parameters:\n" +
                    "        - in: path\n" +
                    "          name: someParam\n" +
                    "          required: true\n" +
                    "          schema:\n" +
                    "            type: integer\n" +
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
                .withSpecUrlOrPayload("---\n" +
                    "openapi: 3.0.0\n" +
                    "paths:\n" +
                    "  \"/somePath/{someParam}/{someOtherParam}\":\n" +
                    "    get:\n" +
                    "      operationId: someOperation\n" +
                    "      parameters:\n" +
                    "        - in: path\n" +
                    "          name: someParam\n" +
                    "          required: true\n" +
                    "          schema:\n" +
                    "            type: integer\n" +
                    "            minimum: 1\n" +
                    "        - in: path\n" +
                    "          name: someOtherParam\n" +
                    "          required: true\n" +
                    "          schema:\n" +
                    "            type: string\n" +
                    "            minLength: 2\n" +
                    "            maxLength: 3\n")
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
                .withSpecUrlOrPayload("---\n" +
                    "openapi: 3.0.0\n" +
                    "paths:\n" +
                    "  \"/somePath\":\n" +
                    "    get:\n" +
                    "      operationId: someOperation\n" +
                    "      parameters:\n" +
                    "        - in: path\n" +
                    "          name: someParam\n" +
                    "          required: true\n" +
                    "          schema:\n" +
                    "            type: integer\n" +
                    "            minimum: 1\n" +
                    "        - in: path\n" +
                    "          name: someOtherParam\n" +
                    "          required: true\n" +
                    "          schema:\n" +
                    "            type: string\n" +
                    "            minLength: 2\n" +
                    "            maxLength: 3\n")
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
                .withSpecUrlOrPayload("---\n" +
                    "openapi: 3.0.0\n" +
                    "paths:\n" +
                    "  \"/somePath/{someParam}\":\n" +
                    "    get:\n" +
                    "      operationId: someOperation\n" +
                    "      parameters:\n" +
                    "        - in: path\n" +
                    "          name: someParam\n" +
                    "          required: true\n" +
                    "          schema:\n" +
                    "            type: integer\n" +
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
                .withSpecUrlOrPayload("---\n" +
                    "openapi: 3.0.0\n" +
                    "paths:\n" +
                    "  \"/somePath/{someParam}\":\n" +
                    "    get:\n" +
                    "      operationId: someOperation\n" +
                    "      parameters:\n" +
                    "        - in: path\n" +
                    "          name: someParam\n" +
                    "          required: false\n" +
                    "          schema:\n" +
                    "            type: integer\n" +
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
                .withSpecUrlOrPayload("---\n" +
                    "openapi: 3.0.0\n" +
                    "paths:\n" +
                    "  \"/somePath/{someParam}\":\n" +
                    "    get:\n" +
                    "      operationId: someOperation\n" +
                    "      parameters:\n" +
                    "        - in: path\n" +
                    "          name: someParam\n" +
                    "          required: true\n" +
                    "          allowEmptyValue: true\n" +
                    "          schema:\n" +
                    "            type: integer\n" +
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
    public void shouldMatchByQueryStringParameter() {
        // when
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(mockServerLogger);
        httpRequestsPropertiesMatcher.update(new Expectation(
            new OpenAPIDefinition()
                .withSpecUrlOrPayload("---\n" +
                    "openapi: 3.0.0\n" +
                    "paths:\n" +
                    "  \"/somePath\":\n" +
                    "    get:\n" +
                    "      operationId: someOperation\n" +
                    "      parameters:\n" +
                    "        - in: query\n" +
                    "          name: someParam\n" +
                    "          required: true\n" +
                    "          schema:\n" +
                    "            type: integer\n" +
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
                .withSpecUrlOrPayload("---\n" +
                    "openapi: 3.0.0\n" +
                    "paths:\n" +
                    "  \"/somePath\":\n" +
                    "    get:\n" +
                    "      operationId: someOperation\n" +
                    "      parameters:\n" +
                    "        - in: query\n" +
                    "          name: someParam\n" +
                    "          required: true\n" +
                    "          schema:\n" +
                    "            type: integer\n" +
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
                .withSpecUrlOrPayload("---\n" +
                    "openapi: 3.0.0\n" +
                    "paths:\n" +
                    "  \"/somePath\":\n" +
                    "    get:\n" +
                    "      operationId: someOperation\n" +
                    "      parameters:\n" +
                    "        - in: query\n" +
                    "          name: someParam\n" +
                    "          required: false\n" +
                    "          schema:\n" +
                    "            type: integer\n" +
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
                .withSpecUrlOrPayload("---\n" +
                    "openapi: 3.0.0\n" +
                    "paths:\n" +
                    "  \"/somePath\":\n" +
                    "    get:\n" +
                    "      operationId: someOperation\n" +
                    "      parameters:\n" +
                    "        - in: query\n" +
                    "          name: someParam\n" +
                    "          schema:\n" +
                    "            type: integer\n" +
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
                .withSpecUrlOrPayload("---\n" +
                    "openapi: 3.0.0\n" +
                    "paths:\n" +
                    "  \"/somePath\":\n" +
                    "    get:\n" +
                    "      operationId: someOperation\n" +
                    "      parameters:\n" +
                    "        - in: query\n" +
                    "          name: someParam\n" +
                    "          required: true\n" +
                    "          allowEmptyValue: true\n" +
                    "          schema:\n" +
                    "            type: integer\n" +
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
                .withSpecUrlOrPayload("---\n" +
                    "openapi: 3.0.0\n" +
                    "paths:\n" +
                    "  \"/somePath\":\n" +
                    "    get:\n" +
                    "      operationId: someOperation\n" +
                    "      parameters:\n" +
                    "        - in: query\n" +
                    "          name: someParam\n" +
                    "          required: false\n" +
                    "          allowEmptyValue: true\n" +
                    "          schema:\n" +
                    "            type: integer\n" +
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
                .withSpecUrlOrPayload("---\n" +
                    "openapi: 3.0.0\n" +
                    "paths:\n" +
                    "  \"/somePath\":\n" +
                    "    parameters:\n" +
                    "      - in: query\n" +
                    "        name: someParam\n" +
                    "        required: true\n" +
                    "        schema:\n" +
                    "          type: integer\n" +
                    "          minimum: 1\n" +
                    "    get:\n" +
                    "      operationId: someOperation\n")
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
                .withSpecUrlOrPayload("---\n" +
                    "openapi: 3.0.0\n" +
                    "components:\n" +
                    "  parameters:\n" +
                    "    someParam:\n" +
                    "      in: query\n" +
                    "      name: someParam\n" +
                    "      required: true\n" +
                    "      schema:\n" +
                    "        type: integer\n" +
                    "        minimum: 1\n" +
                    "    someOtherParam:\n" +
                    "      in: query\n" +
                    "      name: someOtherParam\n" +
                    "      required: true\n" +
                    "      schema:\n" +
                    "        type: string\n" +
                    "        minLength: 2\n" +
                    "        maxLength: 3\n" +
                    "paths:\n" +
                    "  \"/somePath\":\n" +
                    "    parameters:\n" +
                    "      - $ref: '#/components/parameters/someParam'\n" +
                    "    get:\n" +
                    "      operationId: someOperation\n" +
                    "      parameters:\n" +
                    "        - $ref: '#/components/parameters/someOtherParam'\n")
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
    public void shouldMatchByHeader() {
        // when
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(mockServerLogger);
        httpRequestsPropertiesMatcher.update(new Expectation(
            new OpenAPIDefinition()
                .withSpecUrlOrPayload("---\n" +
                    "openapi: 3.0.0\n" +
                    "paths:\n" +
                    "  \"/somePath\":\n" +
                    "    get:\n" +
                    "      operationId: someOperation\n" +
                    "      parameters:\n" +
                    "        - in: header\n" +
                    "          name: someParam\n" +
                    "          required: true\n" +
                    "          schema:\n" +
                    "            type: integer\n" +
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
                .withSpecUrlOrPayload("---\n" +
                    "openapi: 3.0.0\n" +
                    "paths:\n" +
                    "  \"/somePath\":\n" +
                    "    get:\n" +
                    "      operationId: someOperation\n" +
                    "      parameters:\n" +
                    "        - in: header\n" +
                    "          name: someParam\n" +
                    "          required: true\n" +
                    "          schema:\n" +
                    "            type: integer\n" +
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
                .withSpecUrlOrPayload("---\n" +
                    "openapi: 3.0.0\n" +
                    "paths:\n" +
                    "  \"/somePath\":\n" +
                    "    get:\n" +
                    "      operationId: someOperation\n" +
                    "      parameters:\n" +
                    "        - in: header\n" +
                    "          name: someParam\n" +
                    "          required: false\n" +
                    "          schema:\n" +
                    "            type: integer\n" +
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
                .withSpecUrlOrPayload("---\n" +
                    "openapi: 3.0.0\n" +
                    "paths:\n" +
                    "  \"/somePath\":\n" +
                    "    get:\n" +
                    "      operationId: someOperation\n" +
                    "      parameters:\n" +
                    "        - in: header\n" +
                    "          name: someParam\n" +
                    "          schema:\n" +
                    "            type: integer\n" +
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
                .withSpecUrlOrPayload("---\n" +
                    "openapi: 3.0.0\n" +
                    "paths:\n" +
                    "  \"/somePath\":\n" +
                    "    get:\n" +
                    "      operationId: someOperation\n" +
                    "      parameters:\n" +
                    "        - in: header\n" +
                    "          name: someParam\n" +
                    "          required: true\n" +
                    "          allowEmptyValue: true\n" +
                    "          schema:\n" +
                    "            type: integer\n" +
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
                .withSpecUrlOrPayload("---\n" +
                    "openapi: 3.0.0\n" +
                    "paths:\n" +
                    "  \"/somePath\":\n" +
                    "    parameters:\n" +
                    "      - in: header\n" +
                    "        name: someParam\n" +
                    "        required: true\n" +
                    "        schema:\n" +
                    "          type: integer\n" +
                    "          minimum: 1\n" +
                    "    get:\n" +
                    "      operationId: someOperation\n")
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
                .withSpecUrlOrPayload("---\n" +
                    "openapi: 3.0.0\n" +
                    "components:\n" +
                    "  parameters:\n" +
                    "    someParam:\n" +
                    "      in: header\n" +
                    "      name: someParam\n" +
                    "      required: true\n" +
                    "      schema:\n" +
                    "        type: integer\n" +
                    "        minimum: 1\n" +
                    "    someOtherParam:\n" +
                    "      in: header\n" +
                    "      name: someOtherParam\n" +
                    "      required: true\n" +
                    "      schema:\n" +
                    "        type: string\n" +
                    "        minLength: 2\n" +
                    "        maxLength: 3\n" +
                    "paths:\n" +
                    "  \"/somePath\":\n" +
                    "    parameters:\n" +
                    "      - $ref: '#/components/parameters/someParam'\n" +
                    "    get:\n" +
                    "      operationId: someOperation\n" +
                    "      parameters:\n" +
                    "        - $ref: '#/components/parameters/someOtherParam'\n")
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
    public void shouldMatchByCookie() {
        // when
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(mockServerLogger);
        httpRequestsPropertiesMatcher.update(new Expectation(
            new OpenAPIDefinition()
                .withSpecUrlOrPayload("---\n" +
                    "openapi: 3.0.0\n" +
                    "paths:\n" +
                    "  \"/somePath\":\n" +
                    "    get:\n" +
                    "      operationId: someOperation\n" +
                    "      parameters:\n" +
                    "        - in: cookie\n" +
                    "          name: someParam\n" +
                    "          required: true\n" +
                    "          schema:\n" +
                    "            type: integer\n" +
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
                .withSpecUrlOrPayload("---\n" +
                    "openapi: 3.0.0\n" +
                    "paths:\n" +
                    "  \"/somePath\":\n" +
                    "    get:\n" +
                    "      operationId: someOperation\n" +
                    "      parameters:\n" +
                    "        - in: cookie\n" +
                    "          name: someParam\n" +
                    "          required: true\n" +
                    "          schema:\n" +
                    "            type: integer\n" +
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
                .withSpecUrlOrPayload("---\n" +
                    "openapi: 3.0.0\n" +
                    "paths:\n" +
                    "  \"/somePath\":\n" +
                    "    get:\n" +
                    "      operationId: someOperation\n" +
                    "      parameters:\n" +
                    "        - in: cookie\n" +
                    "          name: someParam\n" +
                    "          required: false\n" +
                    "          schema:\n" +
                    "            type: integer\n" +
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
                .withSpecUrlOrPayload("---\n" +
                    "openapi: 3.0.0\n" +
                    "paths:\n" +
                    "  \"/somePath\":\n" +
                    "    get:\n" +
                    "      operationId: someOperation\n" +
                    "      parameters:\n" +
                    "        - in: cookie\n" +
                    "          name: someParam\n" +
                    "          schema:\n" +
                    "            type: integer\n" +
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
                .withSpecUrlOrPayload("---\n" +
                    "openapi: 3.0.0\n" +
                    "paths:\n" +
                    "  \"/somePath\":\n" +
                    "    get:\n" +
                    "      operationId: someOperation\n" +
                    "      parameters:\n" +
                    "        - in: cookie\n" +
                    "          name: someParam\n" +
                    "          required: true\n" +
                    "          allowEmptyValue: true\n" +
                    "          schema:\n" +
                    "            type: integer\n" +
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
                .withSpecUrlOrPayload("---\n" +
                    "openapi: 3.0.0\n" +
                    "paths:\n" +
                    "  \"/somePath\":\n" +
                    "    parameters:\n" +
                    "      - in: cookie\n" +
                    "        name: someParam\n" +
                    "        required: true\n" +
                    "        schema:\n" +
                    "          type: integer\n" +
                    "          minimum: 1\n" +
                    "    get:\n" +
                    "      operationId: someOperation\n")
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
                .withSpecUrlOrPayload("---\n" +
                    "openapi: 3.0.0\n" +
                    "components:\n" +
                    "  parameters:\n" +
                    "    someParam:\n" +
                    "      in: cookie\n" +
                    "      name: someParam\n" +
                    "      required: true\n" +
                    "      schema:\n" +
                    "        type: integer\n" +
                    "        minimum: 1\n" +
                    "    someOtherParam:\n" +
                    "      in: cookie\n" +
                    "      name: someOtherParam\n" +
                    "      required: true\n" +
                    "      schema:\n" +
                    "        type: string\n" +
                    "        minLength: 2\n" +
                    "        maxLength: 3\n" +
                    "paths:\n" +
                    "  \"/somePath\":\n" +
                    "    parameters:\n" +
                    "      - $ref: '#/components/parameters/someParam'\n" +
                    "    get:\n" +
                    "      operationId: someOperation\n" +
                    "      parameters:\n" +
                    "        - $ref: '#/components/parameters/someOtherParam'\n")
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
    public void shouldMatchByJsonBody() {
        // when
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(mockServerLogger);
        httpRequestsPropertiesMatcher.update(new Expectation(
            new OpenAPIDefinition()
                .withSpecUrlOrPayload("---\n" +
                    "openapi: 3.0.0\n" +
                    "paths:\n" +
                    "  \"/somePath\":\n" +
                    "    post:\n" +
                    "      operationId: someOperation\n" +
                    "      requestBody:\n" +
                    "        required: true\n" +
                    "        content:\n" +
                    "          application/json:\n" +
                    "            schema:\n" +
                    "              type: object\n" +
                    "              required:\n" +
                    "                - id\n" +
                    "                - name\n" +
                    "              properties:\n" +
                    "                id:\n" +
                    "                  type: integer\n" +
                    "                  format: int64\n" +
                    "                name:\n" +
                    "                  type: string\n" +
                    "          application/xml:\n" +
                    "            schema:\n" +
                    "              type: object\n" +
                    "              required:\n" +
                    "                - id\n" +
                    "                - name\n" +
                    "              properties:\n" +
                    "                id:\n" +
                    "                  type: integer\n" +
                    "                  format: int64\n" +
                    "                name:\n" +
                    "                  type: string\n")
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
                .withSpecUrlOrPayload("---\n" +
                    "openapi: 3.0.0\n" +
                    "paths:\n" +
                    "  \"/somePath\":\n" +
                    "    post:\n" +
                    "      operationId: someOperation\n" +
                    "      requestBody:\n" +
                    "        required: true\n" +
                    "        content:\n" +
                    "          application/json:\n" +
                    "            schema:\n" +
                    "              $ref: '#/components/schemas/Simple'\n" +
                    "          application/xml:\n" +
                    "            schema:\n" +
                    "              $ref: '#/components/schemas/Simple'\n" +
                    "components:\n" +
                    "  schemas:\n" +
                    "    Simple:\n" +
                    "      type: object\n" +
                    "      required:\n" +
                    "        - id\n" +
                    "        - name\n" +
                    "      properties:\n" +
                    "        id:\n" +
                    "          type: integer\n" +
                    "          format: int64\n" +
                    "        name:\n" +
                    "          type: string\n")
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
                .withSpecUrlOrPayload("---\n" +
                    "openapi: 3.0.0\n" +
                    "paths:\n" +
                    "  \"/somePath\":\n" +
                    "    post:\n" +
                    "      operationId: someOperation\n" +
                    "      requestBody:\n" +
                    "        $ref: '#/components/requestBodies/SimpleBody'\n" +
                    "components:\n" +
                    "  requestBodies:\n" +
                    "    SimpleBody:\n" +
                    "      required: true\n" +
                    "      content:\n" +
                    "        application/json:\n" +
                    "          schema:\n" +
                    "            $ref: '#/components/schemas/Simple'\n" +
                    "        application/xml:\n" +
                    "          schema:\n" +
                    "            $ref: '#/components/schemas/Simple'\n" +
                    "  schemas:\n" +
                    "    Simple:\n" +
                    "      type: object\n" +
                    "      required:\n" +
                    "        - id\n" +
                    "        - name\n" +
                    "      properties:\n" +
                    "        id:\n" +
                    "          type: integer\n" +
                    "          format: int64\n" +
                    "        name:\n" +
                    "          type: string\n")
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
    @Ignore("TODO: transform into json and compare with schema")
    public void shouldMatchByXmlBody() {
        // when
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(mockServerLogger);
        httpRequestsPropertiesMatcher.update(new Expectation(
            new OpenAPIDefinition()
                .withSpecUrlOrPayload("---\n" +
                    "openapi: 3.0.0\n" +
                    "paths:\n" +
                    "  \"/somePath\":\n" +
                    "    post:\n" +
                    "      operationId: someOperation\n" +
                    "      requestBody:\n" +
                    "        required: true\n" +
                    "        content:\n" +
                    "          application/xml:\n" +
                    "            schema:\n" +
                    "              type: object\n" +
                    "              required:\n" +
                    "                - id\n" +
                    "                - name\n" +
                    "              properties:\n" +
                    "                id:\n" +
                    "                  type: integer\n" +
                    "                  format: int64\n" +
                    "                name:\n" +
                    "                  type: string\n" +
                    "          application/x-www-form-urlencoded:\n" +
                    "            schema:\n" +
                    "              type: object\n" +
                    "              properties:\n" +
                    "                name:\n" +
                    "                  type: string\n" +
                    "                email:\n" +
                    "                  type: integer\n" +
                    "                  format: email\n" +
                    "              required:\n" +
                    "                - name\n" +
                    "                - email'\n" +
                    "          text/plain:\n" +
                    "            schema:\n" +
                    "              type: string\n")
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
    @Ignore("TODO: transform into json and compare with schema")
    public void shouldMatchByXmlBodyWithSchemaComponent() {
        // when
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(mockServerLogger);
        httpRequestsPropertiesMatcher.update(new Expectation(
            new OpenAPIDefinition()
                .withSpecUrlOrPayload("---\n" +
                    "openapi: 3.0.0\n" +
                    "paths:\n" +
                    "  \"/somePath\":\n" +
                    "    post:\n" +
                    "      operationId: someOperation\n" +
                    "      requestBody:\n" +
                    "        required: true\n" +
                    "        content:\n" +
                    "          application/json:\n" +
                    "            schema:\n" +
                    "              $ref: '#/components/schemas/Simple'\n" +
                    "          application/xml:\n" +
                    "            schema:\n" +
                    "              $ref: '#/components/schemas/Simple'\n" +
                    "components:\n" +
                    "  schemas:\n" +
                    "    Simple:\n" +
                    "      type: object\n" +
                    "      required:\n" +
                    "        - id\n" +
                    "        - name\n" +
                    "      properties:\n" +
                    "        id:\n" +
                    "          type: integer\n" +
                    "          format: int64\n" +
                    "        name:\n" +
                    "          type: string\n")
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
    @Ignore("TODO: transform into json and compare with schema")
    public void shouldMatchByXmlBodyWithRequestBodyAndSchemaComponent() {
        // when
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(mockServerLogger);
        httpRequestsPropertiesMatcher.update(new Expectation(
            new OpenAPIDefinition()
                .withSpecUrlOrPayload("---\n" +
                    "openapi: 3.0.0\n" +
                    "paths:\n" +
                    "  \"/somePath\":\n" +
                    "    post:\n" +
                    "      operationId: someOperation\n" +
                    "      requestBody:\n" +
                    "        $ref: '#/components/requestBodies/SimpleBody'\n" +
                    "components:\n" +
                    "  requestBodies:\n" +
                    "    SimpleBody:\n" +
                    "      required: true\n" +
                    "      content:\n" +
                    "        application/json:\n" +
                    "          schema:\n" +
                    "            $ref: '#/components/schemas/Simple'\n" +
                    "        application/xml:\n" +
                    "          schema:\n" +
                    "            $ref: '#/components/schemas/Simple'\n" +
                    "  schemas:\n" +
                    "    Simple:\n" +
                    "      type: object\n" +
                    "      required:\n" +
                    "        - id\n" +
                    "        - name\n" +
                    "      properties:\n" +
                    "        id:\n" +
                    "          type: integer\n" +
                    "          format: int64\n" +
                    "        name:\n" +
                    "          type: string\n")
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
    @Ignore("TODO: transform into json and compare with schema")
    public void shouldMatchByFormBody() {
        // when
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(mockServerLogger);
        httpRequestsPropertiesMatcher.update(new Expectation(
            new OpenAPIDefinition()
                .withSpecUrlOrPayload("---\n" +
                    "openapi: 3.0.0\n" +
                    "paths:\n" +
                    "  \"/somePath\":\n" +
                    "    post:\n" +
                    "      operationId: someOperation\n" +
                    "      requestBody:\n" +
                    "        required: true\n" +
                    "        content:\n" +
                    "          application/x-www-form-urlencoded:\n" +
                    "            schema:\n" +
                    "              type: object\n" +
                    "              properties:\n" +
                    "                name:\n" +
                    "                  type: string\n" +
                    "                email:\n" +
                    "                  type: integer\n" +
                    "                  format: email\n" +
                    "              required:\n" +
                    "                - name\n" +
                    "                - email'\n" +
                    "          text/plain:\n" +
                    "            schema:\n" +
                    "              type: string\n")
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
    @Ignore("TODO: transform into json and compare with schema")
    public void shouldMatchByFormBodyWithSchemaComponent() {
        // when
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(mockServerLogger);
        httpRequestsPropertiesMatcher.update(new Expectation(
            new OpenAPIDefinition()
                .withSpecUrlOrPayload("---\n" +
                    "openapi: 3.0.0\n" +
                    "paths:\n" +
                    "  \"/somePath\":\n" +
                    "    post:\n" +
                    "      operationId: someOperation\n" +
                    "      requestBody:\n" +
                    "        required: true\n" +
                    "        content:\n" +
                    "          application/x-www-form-urlencoded:\n" +
                    "            schema:\n" +
                    "              $ref: '#/components/schemas/Simple'\n" +
                    "          application/xml:\n" +
                    "            schema:\n" +
                    "              $ref: '#/components/schemas/Simple'\n" +
                    "components:\n" +
                    "  schemas:\n" +
                    "    Simple:\n" +
                    "      type: object\n" +
                    "      required:\n" +
                    "        - id\n" +
                    "        - name\n" +
                    "      properties:\n" +
                    "        id:\n" +
                    "          type: integer\n" +
                    "          format: int64\n" +
                    "        name:\n" +
                    "          type: string\n")
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
    @Ignore("TODO: transform into json and compare with schema")
    public void shouldMatchByFormBodyWithRequestBodyAndSchemaComponent() {
        // when
        HttpRequestsPropertiesMatcher httpRequestsPropertiesMatcher = new HttpRequestsPropertiesMatcher(mockServerLogger);
        httpRequestsPropertiesMatcher.update(new Expectation(
            new OpenAPIDefinition()
                .withSpecUrlOrPayload("---\n" +
                    "openapi: 3.0.0\n" +
                    "paths:\n" +
                    "  \"/somePath\":\n" +
                    "    post:\n" +
                    "      operationId: someOperation\n" +
                    "      requestBody:\n" +
                    "        $ref: '#/components/requestBodies/SimpleBody'\n" +
                    "components:\n" +
                    "  requestBodies:\n" +
                    "    SimpleBody:\n" +
                    "      required: true\n" +
                    "      content:\n" +
                    "        application/x-www-form-urlencoded:\n" +
                    "          schema:\n" +
                    "            $ref: '#/components/schemas/Simple'\n" +
                    "        application/xml:\n" +
                    "          schema:\n" +
                    "            $ref: '#/components/schemas/Simple'\n" +
                    "  schemas:\n" +
                    "    Simple:\n" +
                    "      type: object\n" +
                    "      required:\n" +
                    "        - id\n" +
                    "        - name\n" +
                    "      properties:\n" +
                    "        id:\n" +
                    "          type: integer\n" +
                    "          format: int64\n" +
                    "        name:\n" +
                    "          type: string\n")
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
        assertThat(matchDifference.getDifferences(METHOD), containsInAnyOrder("  string or regex match failed expected:\n" +
            "\n" +
            "    GET\n" +
            "\n" +
            "   found:\n" +
            "\n" +
            "    PUT\n"));
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
        assertThat(matchDifference.getDifferences(HEADERS), containsInAnyOrder("  multimap subset match failed expected:\n" +
            "\n" +
            "    {\n" +
            "      \"X-Request-ID\" : [ {\n" +
            "        \"type\" : \"string\",\n" +
            "        \"format\" : \"uuid\"\n" +
            "      } ]\n" +
            "    }\n" +
            "\n" +
            "   found:\n" +
            "\n" +
            "    none\n" +
            "\n" +
            "   failed because:\n" +
            "\n" +
            "    none is not a subset\n"));
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
        assertThat(matchDifference.getDifferences(HEADERS), containsInAnyOrder("  multimap subset match failed expected:\n" +
            "\n" +
            "    {\n" +
            "      \"X-Request-ID\" : [ {\n" +
            "        \"type\" : \"string\",\n" +
            "        \"format\" : \"uuid\"\n" +
            "      } ]\n" +
            "    }\n" +
            "\n" +
            "   found:\n" +
            "\n" +
            "    none\n" +
            "\n" +
            "   failed because:\n" +
            "\n" +
            "    none is not a subset\n"));
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
        assertThat(matchDifference.getDifferences(PATH), containsInAnyOrder("  string or regex match failed expected:\n" +
                "\n" +
                "    /pets\n" +
                "\n" +
                "   found:\n" +
                "\n" +
                "    /some/path\n",
            "  string or regex match failed expected:\n" +
                "\n" +
                "    /pets/.*\n" +
                "\n" +
                "   found:\n" +
                "\n" +
                "    /some/path\n"));
        assertThat(matchDifference.getDifferences(METHOD), containsInAnyOrder("  string or regex match failed expected:\n" +
                "\n" +
                "    POST\n" +
                "\n" +
                "   found:\n" +
                "\n" +
                "    GET\n",
            "  string or regex match failed expected:\n" +
                "\n" +
                "    POST\n" +
                "\n" +
                "   found:\n" +
                "\n" +
                "    GET\n"));
        assertThat(matchDifference.getDifferences(QUERY_PARAMETERS), nullValue());
        assertThat(matchDifference.getDifferences(COOKIES), nullValue());
        assertThat(matchDifference.getDifferences(HEADERS), containsInAnyOrder("  multimap subset match failed expected:\n" +
            "\n" +
            "    {\n" +
            "      \"X-Request-ID\" : [ {\n" +
            "        \"type\" : \"string\",\n" +
            "        \"format\" : \"uuid\"\n" +
            "      } ]\n" +
            "    }\n" +
            "\n" +
            "   found:\n" +
            "\n" +
            "    none\n" +
            "\n" +
            "   failed because:\n" +
            "\n" +
            "    none is not a subset\n"));
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
        String bodyError = "  json schema match failed expected:\n" +
            "\n" +
            "    {\n" +
            "      \"required\" : [ \"id\", \"name\" ],\n" +
            "      \"type\" : \"object\",\n" +
            "      \"properties\" : {\n" +
            "        \"id\" : {\n" +
            "          \"type\" : \"integer\",\n" +
            "          \"format\" : \"int64\"\n" +
            "        },\n" +
            "        \"name\" : {\n" +
            "          \"type\" : \"string\"\n" +
            "        },\n" +
            "        \"tag\" : {\n" +
            "          \"type\" : \"string\"\n" +
            "        }\n" +
            "      }\n" +
            "    }\n" +
            "\n" +
            "   found:\n" +
            "\n" +
            "    {\n" +
            "        \"id\": \"invalid_id_format\", \n" +
            "        \"name\": \"scruffles\", \n" +
            "        \"tag\": \"dog\"\n" +
            "    }\n" +
            "\n" +
            "   failed because:\n" +
            "\n" +
            "    2 errors:\n" +
            "     - field: \"/id\" for schema: \"/properties/id\" has error: \"instance type (string) does not match any allowed primitive type (allowed: [\"integer\"])\"\n" +
            "     - schema: \"/properties/id\" has error: \"format attribute \"int64\" not supported\"\n";
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
        String methodError = "  string or regex match failed expected:\n" +
            "\n" +
            "    GET\n" +
            "\n" +
            "   found:\n" +
            "\n" +
            "    POST\n";
        assertThat(matchDifference.getDifferences(METHOD), containsInAnyOrder(methodError, methodError, methodError));
        assertThat(matchDifference.getDifferences(PATH), nullValue());
        assertThat(matchDifference.getDifferences(QUERY_PARAMETERS), nullValue());
        assertThat(matchDifference.getDifferences(COOKIES), nullValue());
        assertThat(matchDifference.getDifferences(HEADERS), nullValue());
        String bodyError = "  json schema match failed expected:\n" +
            "\n" +
            "    {\n" +
            "      \"required\" : [ \"id\", \"name\" ],\n" +
            "      \"type\" : \"object\",\n" +
            "      \"properties\" : {\n" +
            "        \"id\" : {\n" +
            "          \"type\" : \"integer\",\n" +
            "          \"format\" : \"int64\"\n" +
            "        },\n" +
            "        \"name\" : {\n" +
            "          \"type\" : \"string\"\n" +
            "        },\n" +
            "        \"tag\" : {\n" +
            "          \"type\" : \"string\"\n" +
            "        }\n" +
            "      }\n" +
            "    }\n" +
            "\n" +
            "   found:\n" +
            "\n" +
            "    {\n" +
            "        \"id\": \"invalid_id_format\", \n" +
            "        \"name\": \"scruffles\", \n" +
            "        \"tag\": \"dog\"\n" +
            "    }\n" +
            "\n" +
            "   failed because:\n" +
            "\n" +
            "    2 errors:\n" +
            "     - field: \"/id\" for schema: \"/properties/id\" has error: \"instance type (string) does not match any allowed primitive type (allowed: [\"integer\"])\"\n" +
            "     - schema: \"/properties/id\" has error: \"format attribute \"int64\" not supported\"\n";
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