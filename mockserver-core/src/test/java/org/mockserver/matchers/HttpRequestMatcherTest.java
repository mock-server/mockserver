package org.mockserver.matchers;

import org.junit.Test;
import org.mockserver.model.*;

import static junit.framework.TestCase.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockserver.matchers.NotMatcher.not;
import static org.mockserver.model.BinaryBody.binary;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.JsonBody.json;
import static org.mockserver.model.JsonSchemaBody.jsonSchema;
import static org.mockserver.model.Parameter.param;
import static org.mockserver.model.ParameterBody.params;
import static org.mockserver.model.RegexBody.regex;
import static org.mockserver.model.StringBody.exact;
import static org.mockserver.model.XPathBody.xpath;

/**
 * @author jamesdbloom
 */
public class HttpRequestMatcherTest {

    @Test
    public void shouldNotMatchMatchingRequestWithNot() {
        assertFalse(not(new HttpRequestMatcher(new HttpRequest().withMethod("HEAD"))).matches(new HttpRequest().withMethod("HEAD")));
    }

    @Test
    public void shouldMatchNotMatchingRequestWithNot() {
        assertTrue(not(new HttpRequestMatcher(new HttpRequest().withMethod("HEAD"))).matches(new HttpRequest().withMethod("OPTIONS")));
    }

    @Test
    public void matchesMatchingMethod() {
        assertTrue(new HttpRequestMatcher(new HttpRequest().withMethod("HEAD")).matches(new HttpRequest().withMethod("HEAD")));
    }

    @Test
    public void matchesMatchingMethodRegex() {
        assertTrue(new HttpRequestMatcher(new HttpRequest().withMethod("P[A-Z]{2}")).matches(new HttpRequest().withMethod("PUT")));
    }

    @Test
    public void doesNotMatchIncorrectMethod() {
        assertFalse(new HttpRequestMatcher(new HttpRequest().withMethod("HEAD")).matches(new HttpRequest().withMethod("OPTIONS")));
    }

    @Test
    public void doesNotMatchIncorrectMethodRegex() {
        assertFalse(new HttpRequestMatcher(new HttpRequest().withMethod("P[A-Z]{2}")).matches(new HttpRequest().withMethod("POST")));
    }

    @Test
    public void matchesMatchingPath() {
        assertTrue(new HttpRequestMatcher(new HttpRequest().withPath("somePath")).matches(new HttpRequest().withPath("somePath")));
    }

    @Test
    public void matchesMatchingPathRegex() {
        assertTrue(new HttpRequestMatcher(new HttpRequest().withPath("someP[a-z]{3}")).matches(new HttpRequest().withPath("somePath")));
    }

    @Test
    public void doesNotMatchIncorrectPath() {
        assertFalse(new HttpRequestMatcher(new HttpRequest().withPath("somepath")).matches(new HttpRequest().withPath("pathsome")));
    }

    @Test
    public void doesNotMatchIncorrectPathRegex() {
        assertFalse(new HttpRequestMatcher(new HttpRequest().withPath("someP[a-z]{2}")).matches(new HttpRequest().withPath("somePath")));
    }

    @Test
    public void matchesMatchingQueryString() {
        assertTrue(new HttpRequestMatcher(new HttpRequest().withQueryStringParameters(new Parameter("someKey", "someValue"))).matches(new HttpRequest().withQueryStringParameter(new Parameter("someKey", "someValue"))));
    }

    @Test
    public void matchesMatchingQueryStringRegexKeyAndValue() {
        assertTrue(new HttpRequestMatcher(new HttpRequest().withQueryStringParameters(new Parameter("someK[a-z]{2}", "someV[a-z]{4}"))).matches(new HttpRequest().withQueryStringParameter(new Parameter("someKey", "someValue"))));
    }

    @Test
    public void matchesMatchingQueryStringRegexKey() {
        assertTrue(new HttpRequestMatcher(new HttpRequest().withQueryStringParameters(new Parameter("someK[a-z]{2}", "someValue"))).matches(new HttpRequest().withQueryStringParameter(new Parameter("someKey", "someValue"))));
    }

    @Test
    public void matchesMatchingQueryStringRegexValue() {
        assertTrue(new HttpRequestMatcher(new HttpRequest().withQueryStringParameters(new Parameter("someKey", "someV[a-z]{4}"))).matches(new HttpRequest().withQueryStringParameter(new Parameter("someKey", "someValue"))));
    }

    @Test
    public void doesNotMatchIncorrectQueryStringName() {
        assertFalse(new HttpRequestMatcher(new HttpRequest().withQueryStringParameters(new Parameter("someKey", "someValue"))).matches(new HttpRequest().withQueryStringParameter(new Parameter("someOtherKey", "someValue"))));
    }

    @Test
    public void doesNotMatchIncorrectQueryStringValue() {
        assertFalse(new HttpRequestMatcher(new HttpRequest().withQueryStringParameters(new Parameter("someKey", "someValue"))).matches(new HttpRequest().withQueryStringParameter(new Parameter("someKey", "someOtherValue"))));
    }

    @Test
    public void doesNotMatchIncorrectQueryStringRegexKeyAndValue() {
        assertFalse(new HttpRequestMatcher(new HttpRequest().withQueryStringParameters(new Parameter("someK[a-z]{5}", "someV[a-z]{2}"))).matches(new HttpRequest().withQueryStringParameter(new Parameter("someKey", "someValue"))));
    }

    @Test
    public void doesNotMatchIncorrectQueryStringRegexKey() {
        assertFalse(new HttpRequestMatcher(new HttpRequest().withQueryStringParameters(new Parameter("someK[a-z]{5}", "someValue"))).matches(new HttpRequest().withQueryStringParameter(new Parameter("someKey", "someValue"))));
    }

    @Test
    public void doesNotMatchIncorrectQueryStringRegexValue() {
        assertFalse(new HttpRequestMatcher(new HttpRequest().withQueryStringParameters(new Parameter("someKey", "someV[a-z]{2}"))).matches(new HttpRequest().withQueryStringParameter(new Parameter("someKey", "someValue"))));
    }

    @Test
    public void matchesMatchingQueryStringParameters() {
        assertTrue(new HttpRequestMatcher(new HttpRequest().withQueryStringParameters(new Parameter("name", "value"))).matches(new HttpRequest().withQueryStringParameters(new Parameter("name", "value"))));
    }

    @Test
    public void matchesMatchingQueryStringParametersWithRegex() {
        assertTrue(new HttpRequestMatcher(new HttpRequest().withQueryStringParameters(new Parameter("name", "v[a-z]{4}"))).matches(new HttpRequest().withQueryStringParameters(new Parameter("name", "value"))));
    }

    @Test
    public void queryStringParametersMatchesMatchingQueryString() {
        assertTrue(new HttpRequestMatcher(new HttpRequest().withQueryStringParameters(new Parameter("nameOne", "valueOne"))).matches(new HttpRequest().withQueryStringParameters(
                new Parameter("nameOne", "valueOne"),
                new Parameter("nameTwo", "valueTwo")
        )));
        assertTrue(new HttpRequestMatcher(new HttpRequest().withQueryStringParameters(new Parameter("nameTwo", "valueTwo"))).matches(new HttpRequest().withQueryStringParameters(
                new Parameter("nameOne", "valueOne"),
                new Parameter("nameTwo", "valueTwo")
        )));
        assertTrue(new HttpRequestMatcher(new HttpRequest().withQueryStringParameters(new Parameter("nameTwo", "valueTwo", "valueThree"))).matches(new HttpRequest().withQueryStringParameters(
                new Parameter("nameOne", "valueOne"),
                new Parameter("nameTwo", "valueTwo"),
                new Parameter("nameTwo", "valueThree")
        )));
        assertTrue(new HttpRequestMatcher(new HttpRequest().withQueryStringParameters(new Parameter("nameTwo", "valueTwo"))).matches(new HttpRequest().withQueryStringParameters(
                new Parameter("nameOne", "valueOne"),
                new Parameter("nameTwo", "valueTwo"),
                new Parameter("nameTwo", "valueThree")
        )));
        assertTrue(new HttpRequestMatcher(new HttpRequest().withQueryStringParameters(new Parameter("nameTwo", "valueThree"))).matches(new HttpRequest().withQueryStringParameters(
                new Parameter("nameOne", "valueOne"),
                new Parameter("nameTwo", "valueTwo"),
                new Parameter("nameTwo", "valueThree")
        )));
        assertTrue(new HttpRequestMatcher(new HttpRequest().withQueryStringParameters(new Parameter("nameTwo", "valueT[a-z]{0,10}"))).matches(new HttpRequest().withQueryStringParameters(
                new Parameter("nameOne", "valueOne"),
                new Parameter("nameTwo", "valueTwo"),
                new Parameter("nameTwo", "valueThree")
        )));
    }

    @Test
    public void bodyMatchesMatchingBodyParameters() {
        assertTrue(new HttpRequestMatcher(new HttpRequest().withBody(params(new Parameter("nameOne", "valueOne")))).matches(new HttpRequest().withBody(new ParameterBody(
                new Parameter("nameOne", "valueOne"),
                new Parameter("nameTwo", "valueTwo")
        ))));
        assertTrue(new HttpRequestMatcher(new HttpRequest().withBody(new ParameterBody(new Parameter("nameTwo", "valueTwo")))).matches(new HttpRequest().withBody(new ParameterBody(
                new Parameter("nameOne", "valueOne"),
                new Parameter("nameTwo", "valueTwo")
        ))));
        assertTrue(new HttpRequestMatcher(new HttpRequest().withBody(params(new Parameter("nameTwo", "valueTwo", "valueThree")))).matches(new HttpRequest().withBody(new ParameterBody(
                new Parameter("nameOne", "valueOne"),
                new Parameter("nameTwo", "valueTwo"),
                new Parameter("nameTwo", "valueThree")
        ))));
        assertTrue(new HttpRequestMatcher(new HttpRequest().withBody(new ParameterBody(new Parameter("nameTwo", "valueTwo")))).matches(new HttpRequest().withBody(new ParameterBody(
                new Parameter("nameOne", "valueOne"),
                new Parameter("nameTwo", "valueTwo"),
                new Parameter("nameTwo", "valueThree")
        ))));
        assertTrue(new HttpRequestMatcher(new HttpRequest().withBody(params(new Parameter("nameTwo", "valueThree")))).matches(new HttpRequest().withBody(new ParameterBody(
                new Parameter("nameOne", "valueOne"),
                new Parameter("nameTwo", "valueTwo"),
                new Parameter("nameTwo", "valueThree")
        ))));
        assertTrue(new HttpRequestMatcher(new HttpRequest().withBody(new ParameterBody(new Parameter("nameTwo", "valueT[a-z]{0,10}")))).matches(new HttpRequest().withBody(new ParameterBody(
                new Parameter("nameOne", "valueOne"),
                new Parameter("nameTwo", "valueTwo"),
                new Parameter("nameTwo", "valueThree")
        ))));
    }

    @Test
    public void bodyMatchesMatchingUrlEncodedBodyParameters() {
        // pass exact match
        assertTrue(new HttpRequestMatcher(new HttpRequest().withBody(params(param("name one", "value one"), param("nameTwo", "valueTwo"))))
                .matches(new HttpRequest().withBody("name+one=value+one&nameTwo=valueTwo")));

        // ignore extra parameters
        assertTrue(new HttpRequestMatcher(new HttpRequest().withBody(params(param("name one", "value one"))))
                .matches(new HttpRequest().withBody("name+one=value+one&nameTwo=valueTwo")));

        // matches multi-value parameters
        assertTrue(new HttpRequestMatcher(new HttpRequest().withBody(params(param("name one", "value one one", "value one two"))))
                .matches(new HttpRequest().withBody("name+one=value+one+one&name+one=value+one+two")));

        // matches multi-value parameters (ignore extra values)
        assertTrue(new HttpRequestMatcher(new HttpRequest().withBody(params(param("name one", "value one one"))))
                .matches(new HttpRequest().withBody("name+one=value+one+one&name+one=value+one+two")));
        assertTrue(new HttpRequestMatcher(new HttpRequest().withBody(params(param("name one", "value one two"))))
                .matches(new HttpRequest().withBody("name+one=value+one+one&name+one=value+one+two")));

        // matches using regex
        assertTrue(new HttpRequestMatcher(new HttpRequest().withBody(params(param("name one", "value [a-z]{0,10}"), param("nameTwo", "valueT[a-z]{0,10}"))))
                .matches(new HttpRequest().withBody("name+one=value+one&nameTwo=valueTwo")));

        // fail no match
        assertFalse(new HttpRequestMatcher(new HttpRequest().withBody(params(param("name one", "value one"))))
                .matches(new HttpRequest().withBody("name+one=value+two")));
    }

    @Test
    public void doesNotMatchIncorrectParameterName() {
        assertFalse(new HttpRequestMatcher(new HttpRequest().withBody(new ParameterBody(new Parameter("name", "value")))).matches(new HttpRequest().withBody(new ParameterBody(new Parameter("name1", "value")))));
    }

    @Test
    public void doesNotMatchIncorrectParameterValue() {
        assertFalse(new HttpRequestMatcher(new HttpRequest().withBody(new ParameterBody(new Parameter("name", "value")))).matches(new HttpRequest().withBody(new ParameterBody(new Parameter("name", "value1")))));
    }

    @Test
    public void doesNotMatchIncorrectParameterValueRegex() {
        assertFalse(new HttpRequestMatcher(new HttpRequest().withBody(new ParameterBody(new Parameter("name", "va[0-9]{1}ue")))).matches(new HttpRequest().withBody(new ParameterBody(new Parameter("name", "value1")))));
    }

    @Test
    public void matchesMatchingBody() {
        assertTrue(new HttpRequestMatcher(new HttpRequest().withBody(new StringBody("somebody"))).matches(new HttpRequest().withBody("somebody")));
    }

    @Test
    public void doesNotMatchIncorrectBody() {
        assertFalse(new HttpRequestMatcher(new HttpRequest().withBody(exact("somebody"))).matches(new HttpRequest().withBody("bodysome")));
    }

    @Test
    public void matchesMatchingBodyRegex() {
        assertTrue(new HttpRequestMatcher(new HttpRequest().withBody(regex("some[a-z]{4}"))).matches(new HttpRequest().withBody("somebody")));
    }

    @Test
    public void doesNotMatchIncorrectBodyRegex() {
        assertFalse(new HttpRequestMatcher(new HttpRequest().withBody(regex("some[a-z]{3}"))).matches(new HttpRequest().withBody("bodysome")));
    }

    @Test
    public void matchesMatchingBodyXPath() {
        String matched = "" +
                "<element>" +
                "   <key>some_key</key>" +
                "   <value>some_value</value>" +
                "</element>";
        assertTrue(new HttpRequestMatcher(new HttpRequest().withBody(xpath("/element[key = 'some_key' and value = 'some_value']"))).matches(new HttpRequest().withBody(matched)));
    }

    @Test
    public void doesNotMatchIncorrectBodyXPath() {
        String matched = "" +
                "<element>" +
                "   <key>some_key</key>" +
                "</element>";
        assertFalse(new HttpRequestMatcher(new HttpRequest().withBody(xpath("/element[key = 'some_key' and value = 'some_value']"))).matches(new HttpRequest().withBody(matched)));
    }

    @Test
    public void matchesMatchingJSONBody() {
        String matched = "" +
                "{ " +
                "   \"some_field\": \"some_value\", " +
                "   \"some_other_field\": \"some_other_value\" " +
                "}";
        assertTrue(new HttpRequestMatcher(new HttpRequest().withBody(json("{ \"some_field\": \"some_value\" }"))).matches(new HttpRequest().withBody(matched)));
    }

    @Test
    public void doesNotMatchIncorrectJSONBody() {
        String matched = "" +
                "{ " +
                "   \"some_incorrect_field\": \"some_value\", " +
                "   \"some_other_field\": \"some_other_value\" " +
                "}";
        assertFalse(new HttpRequestMatcher(new HttpRequest().withBody(json("{ \"some_field\": \"some_value\" }"))).matches(new HttpRequest().withBody(matched)));
    }

    @Test
    public void matchesMatchingJSONSchemaBody() {
        String matched = "" +
                "{" + System.getProperty("line.separator") +
                "    \"id\": 1," + System.getProperty("line.separator") +
                "    \"name\": \"A green door\"," + System.getProperty("line.separator") +
                "    \"price\": 12.50," + System.getProperty("line.separator") +
                "    \"tags\": [\"home\", \"green\"]" + System.getProperty("line.separator") +
                "}";
        assertTrue(new HttpRequestMatcher(new HttpRequest().withBody(jsonSchema("{" + System.getProperty("line.separator") +
                "    \"$schema\": \"http://json-schema.org/draft-04/schema#\"," + System.getProperty("line.separator") +
                "    \"title\": \"Product\"," + System.getProperty("line.separator") +
                "    \"description\": \"A product from Acme's catalog\"," + System.getProperty("line.separator") +
                "    \"type\": \"object\"," + System.getProperty("line.separator") +
                "    \"properties\": {" + System.getProperty("line.separator") +
                "        \"id\": {" + System.getProperty("line.separator") +
                "            \"description\": \"The unique identifier for a product\"," + System.getProperty("line.separator") +
                "            \"type\": \"integer\"" + System.getProperty("line.separator") +
                "        }," + System.getProperty("line.separator") +
                "        \"name\": {" + System.getProperty("line.separator") +
                "            \"description\": \"Name of the product\"," + System.getProperty("line.separator") +
                "            \"type\": \"string\"" + System.getProperty("line.separator") +
                "        }," + System.getProperty("line.separator") +
                "        \"price\": {" + System.getProperty("line.separator") +
                "            \"type\": \"number\"," + System.getProperty("line.separator") +
                "            \"minimum\": 0," + System.getProperty("line.separator") +
                "            \"exclusiveMinimum\": true" + System.getProperty("line.separator") +
                "        }," + System.getProperty("line.separator") +
                "        \"tags\": {" + System.getProperty("line.separator") +
                "            \"type\": \"array\"," + System.getProperty("line.separator") +
                "            \"items\": {" + System.getProperty("line.separator") +
                "                \"type\": \"string\"" + System.getProperty("line.separator") +
                "            }," + System.getProperty("line.separator") +
                "            \"minItems\": 1," + System.getProperty("line.separator") +
                "            \"uniqueItems\": true" + System.getProperty("line.separator") +
                "        }" + System.getProperty("line.separator") +
                "    }," + System.getProperty("line.separator") +
                "    \"required\": [\"id\", \"name\", \"price\"]" + System.getProperty("line.separator") +
                "}"))).matches(new HttpRequest().withBody(matched)));
    }

    @Test
    public void doesNotMatchIncorrectJSONSchemaBody() {
        String matched = "" +
                "{" + System.getProperty("line.separator") +
                "    \"id\": 1," + System.getProperty("line.separator") +
                "    \"name\": \"A green door\"," + System.getProperty("line.separator") +
                "    \"price\": 12.50," + System.getProperty("line.separator") +
                "    \"tags\": []" + System.getProperty("line.separator") +
                "}";
        assertFalse(new HttpRequestMatcher(new HttpRequest().withBody(jsonSchema("{" + System.getProperty("line.separator") +
                "    \"$schema\": \"http://json-schema.org/draft-04/schema#\"," + System.getProperty("line.separator") +
                "    \"title\": \"Product\"," + System.getProperty("line.separator") +
                "    \"description\": \"A product from Acme's catalog\"," + System.getProperty("line.separator") +
                "    \"type\": \"object\"," + System.getProperty("line.separator") +
                "    \"properties\": {" + System.getProperty("line.separator") +
                "        \"id\": {" + System.getProperty("line.separator") +
                "            \"description\": \"The unique identifier for a product\"," + System.getProperty("line.separator") +
                "            \"type\": \"integer\"" + System.getProperty("line.separator") +
                "        }," + System.getProperty("line.separator") +
                "        \"name\": {" + System.getProperty("line.separator") +
                "            \"description\": \"Name of the product\"," + System.getProperty("line.separator") +
                "            \"type\": \"string\"" + System.getProperty("line.separator") +
                "        }," + System.getProperty("line.separator") +
                "        \"price\": {" + System.getProperty("line.separator") +
                "            \"type\": \"number\"," + System.getProperty("line.separator") +
                "            \"minimum\": 0," + System.getProperty("line.separator") +
                "            \"exclusiveMinimum\": true" + System.getProperty("line.separator") +
                "        }," + System.getProperty("line.separator") +
                "        \"tags\": {" + System.getProperty("line.separator") +
                "            \"type\": \"array\"," + System.getProperty("line.separator") +
                "            \"items\": {" + System.getProperty("line.separator") +
                "                \"type\": \"string\"" + System.getProperty("line.separator") +
                "            }," + System.getProperty("line.separator") +
                "            \"minItems\": 1," + System.getProperty("line.separator") +
                "            \"uniqueItems\": true" + System.getProperty("line.separator") +
                "        }" + System.getProperty("line.separator") +
                "    }," + System.getProperty("line.separator") +
                "    \"required\": [\"id\", \"name\", \"price\"]" + System.getProperty("line.separator") +
                "}"))).matches(new HttpRequest().withBody(matched)));
    }


    @Test
    public void matchesMatchingBinaryBody() {
        byte[] matched = "some binary value".getBytes();
        assertTrue(new HttpRequestMatcher(new HttpRequest().withBody(binary("some binary value".getBytes()))).matches(new HttpRequest().withBody(binary(matched))));
    }

    @Test
    public void doesNotMatchIncorrectBinaryBody() {
        byte[] matched = "some other binary value".getBytes();
        assertFalse(new HttpRequestMatcher(new HttpRequest().withBody(binary("some binary value".getBytes()))).matches(new HttpRequest().withBody(binary(matched))));
    }

    @Test
    public void matchesMatchingHeaders() {
        assertTrue(new HttpRequestMatcher(new HttpRequest().withHeaders(new Header("name", "value"))).matches(new HttpRequest().withHeaders(new Header("name", "value"))));
    }

    @Test
    public void matchesMatchingHeadersWithRegex() {
        assertTrue(new HttpRequestMatcher(new HttpRequest().withHeaders(new Header("name", ".*"))).matches(new HttpRequest().withHeaders(new Header("name", "value"))));
    }

    @Test
    public void doesNotMatchIncorrectHeaderName() {
        assertFalse(new HttpRequestMatcher(new HttpRequest().withHeaders(new Header("name", "value"))).matches(new HttpRequest().withHeaders(new Header("name1", "value"))));
    }

    @Test
    public void doesNotMatchIncorrectHeaderValue() {
        assertFalse(new HttpRequestMatcher(new HttpRequest().withHeaders(new Header("name", "value"))).matches(new HttpRequest().withHeaders(new Header("name", "value1"))));
    }

    @Test
    public void doesNotMatchIncorrectHeaderValueRegex() {
        assertFalse(new HttpRequestMatcher(new HttpRequest().withHeaders(new Header("name", "[0-9]{0,100}"))).matches(new HttpRequest().withHeaders(new Header("name", "value1"))));
    }

    @Test
    public void matchesMatchingCookies() {
        assertTrue(new HttpRequestMatcher(new HttpRequest().withCookies(new Cookie("name", "value"))).matches(new HttpRequest().withCookies(new Cookie("name", "value"))));
    }

    @Test
    public void matchesMatchingCookiesWithRegex() {
        assertTrue(new HttpRequestMatcher(new HttpRequest().withCookies(new Cookie("name", "[a-z]{0,20}lue"))).matches(new HttpRequest().withCookies(new Cookie("name", "value"))));
    }

    @Test
    public void doesNotMatchIncorrectCookieName() {
        assertFalse(new HttpRequestMatcher(new HttpRequest().withCookies(new Cookie("name", "value"))).matches(new HttpRequest().withCookies(new Cookie("name1", "value"))));
    }

    @Test
    public void doesNotMatchIncorrectCookieValue() {
        assertFalse(new HttpRequestMatcher(new HttpRequest().withCookies(new Cookie("name", "value"))).matches(new HttpRequest().withCookies(new Cookie("name", "value1"))));
    }

    @Test
    public void doesNotMatchIncorrectCookieValueRegex() {
        assertFalse(new HttpRequestMatcher(new HttpRequest().withCookies(new Cookie("name", "[A-Z]{0,10}"))).matches(new HttpRequest().withCookies(new Cookie("name", "value1"))));
    }

    @Test
    public void shouldReturnFormattedRequestWithStringBodyInToString() {
        assertEquals("{" + System.getProperty("line.separator") +
                        "  \"body\" : \"some_body\"," + System.getProperty("line.separator") +
                        "  \"headers\" : [ {" + System.getProperty("line.separator") +
                        "    \"name\" : \"name\"," + System.getProperty("line.separator") +
                        "    \"values\" : [ \"value\" ]" + System.getProperty("line.separator") +
                        "  } ]," + System.getProperty("line.separator") +
                        "  \"cookies\" : [ {" + System.getProperty("line.separator") +
                        "    \"name\" : \"name\"," + System.getProperty("line.separator") +
                        "    \"value\" : \"[A-Z]{0,10}\"" + System.getProperty("line.separator") +
                        "  } ]" + System.getProperty("line.separator") +
                        "}",
                new HttpRequestMatcher(
                        request()
                                .withBody("some_body")
                                .withHeaders(new Header("name", "value"))
                                .withCookies(new Cookie("name", "[A-Z]{0,10}"))
                ).toString()
        );
    }

    @Test
    public void shouldReturnFormattedRequestWithJsonBodyInToString() {
        assertEquals("{" + System.getProperty("line.separator") +
                        "  \"body\" : {" + System.getProperty("line.separator") +
                        "    \"type\" : \"JSON\"," + System.getProperty("line.separator") +
                        "    \"value\" : \"{ \\\"key\\\": \\\"some_value\\\" }\"" + System.getProperty("line.separator") +
                        "  }," + System.getProperty("line.separator") +
                        "  \"headers\" : [ {" + System.getProperty("line.separator") +
                        "    \"name\" : \"name\"," + System.getProperty("line.separator") +
                        "    \"values\" : [ \"value\" ]" + System.getProperty("line.separator") +
                        "  } ]," + System.getProperty("line.separator") +
                        "  \"cookies\" : [ {" + System.getProperty("line.separator") +
                        "    \"name\" : \"name\"," + System.getProperty("line.separator") +
                        "    \"value\" : \"[A-Z]{0,10}\"" + System.getProperty("line.separator") +
                        "  } ]" + System.getProperty("line.separator") +
                        "}",
                new HttpRequestMatcher(
                        request()
                                .withBody(json("{ \"key\": \"some_value\" }"))
                                .withHeaders(new Header("name", "value"))
                                .withCookies(new Cookie("name", "[A-Z]{0,10}"))
                ).toString()
        );
    }
}
