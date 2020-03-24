package org.mockserver.matchers;

import org.junit.Test;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.mock.Expectation;
import org.mockserver.mock.HttpStateHandler;
import org.mockserver.model.*;
import org.mockserver.serialization.model.*;

import java.nio.charset.StandardCharsets;

import static java.nio.charset.StandardCharsets.UTF_8;
import static junit.framework.TestCase.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockserver.character.Character.NEW_LINE;
import static org.mockserver.matchers.NotMatcher.not;
import static org.mockserver.model.BinaryBody.binary;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.JsonBody.json;
import static org.mockserver.model.JsonPathBody.jsonPath;
import static org.mockserver.model.JsonSchemaBody.jsonSchema;
import static org.mockserver.model.Parameter.param;
import static org.mockserver.model.ParameterBody.params;
import static org.mockserver.model.RegexBody.regex;
import static org.mockserver.model.StringBody.exact;
import static org.mockserver.model.XPathBody.xpath;
import static org.mockserver.model.XmlBody.xml;
import static org.mockserver.model.XmlSchemaBody.xmlSchema;

/**
 * @author jamesdbloom
 */
public class HttpRequestMatcherTest {

    private final HttpStateHandler httpStateHandler = mock(HttpStateHandler.class);
    private final MockServerLogger mockServerLogger = new MockServerLogger(httpStateHandler);

    @Test
    public void shouldAllowUseOfNotWithMatchingRequests() {
        // requests match - matcher HttpRequest notted
        assertFalse(new HttpRequestMatcher(mockServerLogger, org.mockserver.model.Not.not(new HttpRequest().withMethod("HEAD"))).matches(null, new HttpRequest().withMethod("HEAD")));

        // requests match - matched HttpRequest notted
        assertFalse(new HttpRequestMatcher(mockServerLogger, new HttpRequest().withMethod("HEAD")).matches(null, org.mockserver.model.Not.not(new HttpRequest().withMethod("HEAD"))));

        // requests match - matcher HttpRequest notted & HttpRequestMatch notted
        assertTrue(not(new HttpRequestMatcher(mockServerLogger, org.mockserver.model.Not.not(new HttpRequest().withMethod("HEAD")))).matches(null, new HttpRequest().withMethod("HEAD")));

        // requests match - matched HttpRequest notted & HttpRequestMatch notted
        assertTrue(not(new HttpRequestMatcher(mockServerLogger, new HttpRequest().withMethod("HEAD"))).matches(null, org.mockserver.model.Not.not(new HttpRequest().withMethod("HEAD"))));

        // requests match - matcher HttpRequest notted & matched HttpRequest notted & HttpRequestMatch notted
        assertFalse(not(new HttpRequestMatcher(mockServerLogger, org.mockserver.model.Not.not(new HttpRequest().withMethod("HEAD")))).matches(null, org.mockserver.model.Not.not(new HttpRequest().withMethod("HEAD"))));
    }

    @Test
    public void shouldAllowUseOfNotWithNonMatchingRequests() {
        // requests don't match - matcher HttpRequest notted
        assertTrue(new HttpRequestMatcher(mockServerLogger, org.mockserver.model.Not.not(new HttpRequest().withMethod("HEAD"))).matches(null, new HttpRequest().withMethod("OPTIONS")));

        // requests don't match - matched HttpRequest notted
        assertTrue(new HttpRequestMatcher(mockServerLogger, new HttpRequest().withMethod("HEAD")).matches(null, org.mockserver.model.Not.not(new HttpRequest().withMethod("OPTIONS"))));

        // requests don't match - matcher HttpRequest notted & HttpRequestMatch notted
        assertFalse(not(new HttpRequestMatcher(mockServerLogger, org.mockserver.model.Not.not(new HttpRequest().withMethod("HEAD")))).matches(null, new HttpRequest().withMethod("OPTIONS")));

        // requests don't match - matched HttpRequest notted & HttpRequestMatch notted
        assertFalse(not(new HttpRequestMatcher(mockServerLogger, new HttpRequest().withMethod("HEAD"))).matches(null, org.mockserver.model.Not.not(new HttpRequest().withMethod("OPTIONS"))));

        // requests don't match - matcher HttpRequest notted & matched HttpRequest notted & HttpRequestMatch notted
        assertTrue(not(new HttpRequestMatcher(mockServerLogger, org.mockserver.model.Not.not(new HttpRequest().withMethod("HEAD")))).matches(null, org.mockserver.model.Not.not(new HttpRequest().withMethod("OPTIONS"))));
    }

    @Test
    public void matchesMatchingKeepAlive() {
        assertTrue(new HttpRequestMatcher(mockServerLogger, new HttpRequest().withKeepAlive(true)).matches(null, new HttpRequest().withKeepAlive(true)));
        assertTrue(new HttpRequestMatcher(mockServerLogger, new HttpRequest().withKeepAlive(false)).matches(null, new HttpRequest().withKeepAlive(false)));
        assertTrue(new HttpRequestMatcher(mockServerLogger, new HttpRequest().withKeepAlive(null)).matches(null, new HttpRequest().withKeepAlive(null)));
        assertTrue(new HttpRequestMatcher(mockServerLogger, new HttpRequest().withKeepAlive(null)).matches(null, new HttpRequest().withKeepAlive(false)));
        assertTrue(new HttpRequestMatcher(mockServerLogger, new HttpRequest().withKeepAlive(null)).matches(null, new HttpRequest()));
        assertTrue(new HttpRequestMatcher(mockServerLogger, new HttpRequest()).matches(null, new HttpRequest().withKeepAlive(null)));
    }

    @Test
    public void doesNotMatchIncorrectKeepAlive() {
        assertFalse(new HttpRequestMatcher(mockServerLogger, new HttpRequest().withKeepAlive(true)).matches(null, new HttpRequest().withKeepAlive(false)));
        assertFalse(new HttpRequestMatcher(mockServerLogger, new HttpRequest().withKeepAlive(false)).matches(null, new HttpRequest().withKeepAlive(true)));
        assertFalse(new HttpRequestMatcher(mockServerLogger, new HttpRequest().withKeepAlive(true)).matches(null, new HttpRequest().withKeepAlive(null)));
        assertFalse(new HttpRequestMatcher(mockServerLogger, new HttpRequest().withKeepAlive(false)).matches(null, new HttpRequest().withKeepAlive(null)));
    }

    @Test
    public void matchesMatchingSsl() {
        assertTrue(new HttpRequestMatcher(mockServerLogger, new HttpRequest().withSecure(true)).matches(null, new HttpRequest().withSecure(true)));
        assertTrue(new HttpRequestMatcher(mockServerLogger, new HttpRequest().withSecure(false)).matches(null, new HttpRequest().withSecure(false)));
        assertTrue(new HttpRequestMatcher(mockServerLogger, new HttpRequest().withSecure(null)).matches(null, new HttpRequest().withSecure(null)));
        assertTrue(new HttpRequestMatcher(mockServerLogger, new HttpRequest().withSecure(null)).matches(null, new HttpRequest().withSecure(false)));
        assertTrue(new HttpRequestMatcher(mockServerLogger, new HttpRequest().withSecure(null)).matches(null, new HttpRequest()));
        assertTrue(new HttpRequestMatcher(mockServerLogger, new HttpRequest()).matches(null, new HttpRequest().withSecure(null)));
    }

    @Test
    public void doesNotMatchIncorrectSsl() {
        assertFalse(new HttpRequestMatcher(mockServerLogger, new HttpRequest().withSecure(true)).matches(null, new HttpRequest().withSecure(false)));
        assertFalse(new HttpRequestMatcher(mockServerLogger, new HttpRequest().withSecure(false)).matches(null, new HttpRequest().withSecure(true)));
        assertFalse(new HttpRequestMatcher(mockServerLogger, new HttpRequest().withSecure(true)).matches(null, new HttpRequest().withSecure(null)));
        assertFalse(new HttpRequestMatcher(mockServerLogger, new HttpRequest().withSecure(false)).matches(null, new HttpRequest().withSecure(null)));
    }

    @Test
    public void matchesMatchingMethod() {
        assertTrue(new HttpRequestMatcher(mockServerLogger, new HttpRequest().withMethod("HEAD")).matches(null, new HttpRequest().withMethod("HEAD")));
    }

    @Test
    public void matchesMatchingMethodRegex() {
        assertTrue(new HttpRequestMatcher(mockServerLogger, new HttpRequest().withMethod("P[A-Z]{2}")).matches(null, new HttpRequest().withMethod("PUT")));
    }

    @Test
    public void doesNotMatchIncorrectMethod() {
        assertFalse(new HttpRequestMatcher(mockServerLogger, new HttpRequest().withMethod("HEAD")).matches(null, new HttpRequest().withMethod("OPTIONS")));
    }

    @Test
    public void doesNotMatchIncorrectMethodRegex() {
        assertFalse(new HttpRequestMatcher(mockServerLogger, new HttpRequest().withMethod("P[A-Z]{2}")).matches(null, new HttpRequest().withMethod("POST")));
    }

    @Test
    public void matchesMatchingPath() {
        assertTrue(new HttpRequestMatcher(mockServerLogger, new HttpRequest().withPath("somePath")).matches(null, new HttpRequest().withPath("somePath")));
    }

    @Test
    public void doesNotMatchEncodedMatcherPath() {
        assertFalse(new HttpRequestMatcher(mockServerLogger, new HttpRequest().withPath("/dWM%2FdWM+ZA==")).matches(null, new HttpRequest().withPath("/dWM/dWM+ZA==")));
    }

    @Test
    public void doesNotMatchEncodedRequestPath() {
        assertFalse(new HttpRequestMatcher(mockServerLogger, new HttpRequest().withPath("/dWM/dWM+ZA==")).matches(null, new HttpRequest().withPath("/dWM%2FdWM+ZA==")));
    }

    @Test
    public void matchesMatchingEncodedMatcherAndRequestPath() {
        assertTrue(new HttpRequestMatcher(mockServerLogger, new HttpRequest().withPath("/dWM%2FdWM+ZA==")).matches(null, new HttpRequest().withPath("/dWM%2FdWM+ZA==")));
    }

    @Test
    public void matchesMatchingPathRegex() {
        assertTrue(new HttpRequestMatcher(mockServerLogger, new HttpRequest().withPath("someP[a-z]{3}")).matches(null, new HttpRequest().withPath("somePath")));
    }

    @Test
    public void doesNotMatchIncorrectPath() {
        assertFalse(new HttpRequestMatcher(mockServerLogger, new HttpRequest().withPath("somepath")).matches(null, new HttpRequest().withPath("pathsome")));
    }

    @Test
    public void doesNotMatchIncorrectPathRegex() {
        assertFalse(new HttpRequestMatcher(mockServerLogger, new HttpRequest().withPath("someP[a-z]{2}")).matches(null, new HttpRequest().withPath("somePath")));
    }

    @Test
    public void matchesMatchingQueryString() {
        assertTrue(new HttpRequestMatcher(mockServerLogger, new HttpRequest().withQueryStringParameters(new Parameter("someKey", "someValue"))).matches(null, new HttpRequest().withQueryStringParameter(new Parameter("someKey", "someValue"))));
    }

    @Test
    public void matchesMatchingQueryStringRegexKeyAndValue() {
        assertTrue(new HttpRequestMatcher(mockServerLogger, new HttpRequest().withQueryStringParameters(new Parameter("someK[a-z]{2}", "someV[a-z]{4}"))).matches(null, new HttpRequest().withQueryStringParameter(new Parameter("someKey", "someValue"))));
    }

    @Test
    public void matchesMatchingQueryStringRegexKey() {
        assertTrue(new HttpRequestMatcher(mockServerLogger, new HttpRequest().withQueryStringParameters(new Parameter("someK[a-z]{2}", "someValue"))).matches(null, new HttpRequest().withQueryStringParameter(new Parameter("someKey", "someValue"))));
    }

    @Test
    public void matchesMatchingQueryStringRegexValue() {
        assertTrue(new HttpRequestMatcher(mockServerLogger, new HttpRequest().withQueryStringParameters(new Parameter("someKey", "someV[a-z]{4}"))).matches(null, new HttpRequest().withQueryStringParameter(new Parameter("someKey", "someValue"))));
    }

    @Test
    public void doesNotMatchIncorrectQueryStringName() {
        assertFalse(new HttpRequestMatcher(mockServerLogger, new HttpRequest().withQueryStringParameters(new Parameter("someKey", "someValue"))).matches(null, new HttpRequest().withQueryStringParameter(new Parameter("someOtherKey", "someValue"))));
    }

    @Test
    public void doesNotMatchIncorrectQueryStringValue() {
        assertFalse(new HttpRequestMatcher(mockServerLogger, new HttpRequest().withQueryStringParameters(new Parameter("someKey", "someValue"))).matches(null, new HttpRequest().withQueryStringParameter(new Parameter("someKey", "someOtherValue"))));
    }

    @Test
    public void doesNotMatchIncorrectQueryStringRegexKeyAndValue() {
        assertFalse(new HttpRequestMatcher(mockServerLogger, new HttpRequest().withQueryStringParameters(new Parameter("someK[a-z]{5}", "someV[a-z]{2}"))).matches(null, new HttpRequest().withQueryStringParameter(new Parameter("someKey", "someValue"))));
    }

    @Test
    public void doesNotMatchIncorrectQueryStringRegexKey() {
        assertFalse(new HttpRequestMatcher(mockServerLogger, new HttpRequest().withQueryStringParameters(new Parameter("someK[a-z]{5}", "someValue"))).matches(null, new HttpRequest().withQueryStringParameter(new Parameter("someKey", "someValue"))));
    }

    @Test
    public void doesNotMatchIncorrectQueryStringRegexValue() {
        assertFalse(new HttpRequestMatcher(mockServerLogger, new HttpRequest().withQueryStringParameters(new Parameter("someKey", "someV[a-z]{2}"))).matches(null, new HttpRequest().withQueryStringParameter(new Parameter("someKey", "someValue"))));
    }

    @Test
    public void matchesMatchingQueryStringParameters() {
        assertTrue(new HttpRequestMatcher(mockServerLogger, new HttpRequest().withQueryStringParameters(new Parameter("name", "value"))).matches(null, new HttpRequest().withQueryStringParameters(new Parameter("name", "value"))));
    }

    @Test
    public void matchesMatchingQueryStringParametersWithRegex() {
        assertTrue(new HttpRequestMatcher(mockServerLogger, new HttpRequest().withQueryStringParameters(new Parameter("name", "v[a-z]{4}"))).matches(null, new HttpRequest().withQueryStringParameters(new Parameter("name", "value"))));
    }

    @Test
    public void queryStringParametersMatchesMatchingQueryString() {
        assertTrue(new HttpRequestMatcher(mockServerLogger, new HttpRequest().withQueryStringParameters(new Parameter("nameOne", "valueOne"))).matches(null, new HttpRequest().withQueryStringParameters(
            new Parameter("nameOne", "valueOne"),
            new Parameter("nameTwo", "valueTwo")
        )));
        assertTrue(new HttpRequestMatcher(mockServerLogger, new HttpRequest().withQueryStringParameters(new Parameter("nameTwo", "valueTwo"))).matches(null, new HttpRequest().withQueryStringParameters(
            new Parameter("nameOne", "valueOne"),
            new Parameter("nameTwo", "valueTwo")
        )));
        assertTrue(new HttpRequestMatcher(mockServerLogger, new HttpRequest().withQueryStringParameters(new Parameter("nameTwo", "valueTwo", "valueThree"))).matches(null, new HttpRequest().withQueryStringParameters(
            new Parameter("nameOne", "valueOne"),
            new Parameter("nameTwo", "valueTwo"),
            new Parameter("nameTwo", "valueThree")
        )));
        assertTrue(new HttpRequestMatcher(mockServerLogger, new HttpRequest().withQueryStringParameters(new Parameter("nameTwo", "valueTwo"))).matches(null, new HttpRequest().withQueryStringParameters(
            new Parameter("nameOne", "valueOne"),
            new Parameter("nameTwo", "valueTwo"),
            new Parameter("nameTwo", "valueThree")
        )));
        assertTrue(new HttpRequestMatcher(mockServerLogger, new HttpRequest().withQueryStringParameters(new Parameter("nameTwo", "valueThree"))).matches(null, new HttpRequest().withQueryStringParameters(
            new Parameter("nameOne", "valueOne"),
            new Parameter("nameTwo", "valueTwo"),
            new Parameter("nameTwo", "valueThree")
        )));
        assertTrue(new HttpRequestMatcher(mockServerLogger, new HttpRequest().withQueryStringParameters(new Parameter("nameTwo", "valueT[a-z]{0,10}"))).matches(null, new HttpRequest().withQueryStringParameters(
            new Parameter("nameOne", "valueOne"),
            new Parameter("nameTwo", "valueTwo"),
            new Parameter("nameTwo", "valueThree")
        )));
    }

    @Test
    public void bodyMatchesMatchingBodyParameters() {
        assertTrue(new HttpRequestMatcher(mockServerLogger, new HttpRequest().withBody(params(new Parameter("nameOne", "valueOne")))).matches(null, new HttpRequest().withBody(new ParameterBody(
            new Parameter("nameOne", "valueOne"),
            new Parameter("nameTwo", "valueTwo")
        ))));
        assertTrue(new HttpRequestMatcher(mockServerLogger, new HttpRequest().withBody(new ParameterBody(new Parameter("nameTwo", "valueTwo")))).matches(null, new HttpRequest().withBody(new ParameterBody(
            new Parameter("nameOne", "valueOne"),
            new Parameter("nameTwo", "valueTwo")
        ))));
        assertTrue(new HttpRequestMatcher(mockServerLogger, new HttpRequest().withBody(params(new Parameter("nameTwo", "valueTwo", "valueThree")))).matches(null, new HttpRequest().withBody(new ParameterBody(
            new Parameter("nameOne", "valueOne"),
            new Parameter("nameTwo", "valueTwo"),
            new Parameter("nameTwo", "valueThree")
        ))));
        assertTrue(new HttpRequestMatcher(mockServerLogger, new HttpRequest().withBody(new ParameterBody(new Parameter("nameTwo", "valueTwo")))).matches(null, new HttpRequest().withBody(new ParameterBody(
            new Parameter("nameOne", "valueOne"),
            new Parameter("nameTwo", "valueTwo"),
            new Parameter("nameTwo", "valueThree")
        ))));
        assertTrue(new HttpRequestMatcher(mockServerLogger, new HttpRequest().withBody(params(new Parameter("nameTwo", "valueThree")))).matches(null, new HttpRequest().withBody(new ParameterBody(
            new Parameter("nameOne", "valueOne"),
            new Parameter("nameTwo", "valueTwo"),
            new Parameter("nameTwo", "valueThree")
        ))));
        assertTrue(new HttpRequestMatcher(mockServerLogger, new HttpRequest().withBody(new ParameterBody(new Parameter("nameTwo", "valueT[a-z]{0,10}")))).matches(null, new HttpRequest().withBody(new ParameterBody(
            new Parameter("nameOne", "valueOne"),
            new Parameter("nameTwo", "valueTwo"),
            new Parameter("nameTwo", "valueThree")
        ))));
    }

    @Test
    public void bodyMatchesMatchingUrlEncodedBodyParameters() {
        // pass exact match
        assertTrue(new HttpRequestMatcher(mockServerLogger, new HttpRequest().withBody(params(param("name one", "value one"), param("nameTwo", "valueTwo"))))
            .matches(null, new HttpRequest().withBody("name+one=value+one&nameTwo=valueTwo")));

        // ignore extra parameters
        assertTrue(new HttpRequestMatcher(mockServerLogger, new HttpRequest().withBody(params(param("name one", "value one"))))
            .matches(null, new HttpRequest().withBody("name+one=value+one&nameTwo=valueTwo")));

        // matches multi-value parameters
        assertTrue(new HttpRequestMatcher(mockServerLogger, new HttpRequest().withBody(params(param("name one", "value one one", "value one two"))))
            .matches(null, new HttpRequest().withBody("name+one=value+one+one&name+one=value+one+two")));

        // matches multi-value parameters (ignore extra values)
        assertTrue(new HttpRequestMatcher(mockServerLogger, new HttpRequest().withBody(params(param("name one", "value one one"))))
            .matches(null, new HttpRequest().withBody("name+one=value+one+one&name+one=value+one+two")));
        assertTrue(new HttpRequestMatcher(mockServerLogger, new HttpRequest().withBody(params(param("name one", "value one two"))))
            .matches(null, new HttpRequest().withBody("name+one=value+one+one&name+one=value+one+two")));

        // matches using regex
        assertTrue(new HttpRequestMatcher(mockServerLogger, new HttpRequest().withBody(params(param("name one", "value [a-z]{0,10}"), param("nameTwo", "valueT[a-z]{0,10}"))))
            .matches(null, new HttpRequest().withBody("name+one=value+one&nameTwo=valueTwo")));

        // fail no match
        assertFalse(new HttpRequestMatcher(mockServerLogger, new HttpRequest().withBody(params(param("name one", "value one"))))
            .matches(null, new HttpRequest().withBody("name+one=value+two")));
    }

    @Test
    public void bodyMatchesParameterBodyDTO() {
        assertTrue(new HttpRequestMatcher(
            mockServerLogger, new HttpRequest()
                .withBody(params(
                    new Parameter("nameOne", "valueOne"),
                    new Parameter("nameTwo", "valueTwo")
                ))
        )
            .matches(
                null, new HttpRequest()
                    .withBody(new ParameterBodyDTO(params(
                        new Parameter("nameOne", "valueOne"),
                        new Parameter("nameTwo", "valueTwo")
                    )).toString())
                    .withMethod("PUT")
            ));
    }

    @Test
    public void doesNotMatchIncorrectParameterName() {
        assertFalse(new HttpRequestMatcher(mockServerLogger, new HttpRequest().withBody(new ParameterBody(new Parameter("name", "value")))).matches(null, new HttpRequest().withBody(new ParameterBody(new Parameter("name1", "value")))));
    }

    @Test
    public void doesNotMatchIncorrectParameterValue() {
        assertFalse(new HttpRequestMatcher(mockServerLogger, new HttpRequest().withBody(new ParameterBody(new Parameter("name", "value")))).matches(null, new HttpRequest().withBody(new ParameterBody(new Parameter("name", "value1")))));
    }

    @Test
    public void doesNotMatchIncorrectParameterValueRegex() {
        assertFalse(new HttpRequestMatcher(mockServerLogger, new HttpRequest().withBody(new ParameterBody(new Parameter("name", "va[0-9]{1}ue")))).matches(null, new HttpRequest().withBody(new ParameterBody(new Parameter("name", "value1")))));
    }

    @Test
    public void matchesMatchingBody() {
        assertTrue(new HttpRequestMatcher(mockServerLogger, new HttpRequest().withBody(new StringBody("somebody"))).matches(null, new HttpRequest().withBody("somebody")));
    }

    @Test
    public void jsonBodyThatIsNotValidDTODoesNotThrowException() {
        assertFalse(new HttpRequestMatcher(mockServerLogger, new HttpRequest().withBody(new StringBody("somebody"))).matches(null, new HttpRequest().withBody("{\"method\":\"any\",\"service\":\"any_service\", \"parameters\": { \"applicationName\":\"name\",\"password\":\"pwd\",\"username\":\"user\" } }")));
    }

    @Test
    public void matchesMatchingBodyWithCharset() {
        assertTrue(new HttpRequestMatcher(mockServerLogger, new HttpRequest().withBody(new StringBody("我说中国话", StandardCharsets.UTF_16))).matches(null, new HttpRequest().withBody("我说中国话", StandardCharsets.UTF_16)));
    }

    @Test
    public void doesNotMatchIncorrectBody() {
        assertFalse(new HttpRequestMatcher(mockServerLogger, new HttpRequest().withBody(exact("somebody"))).matches(null, new HttpRequest().withBody("bodysome")));
    }

    @Test
    public void doesNotMatchEmptyBodyAgainstMatcherWithStringBody() {
        assertFalse(new HttpRequestMatcher(mockServerLogger, new Expectation(new HttpRequest().withBody(exact("somebody")))).matches(null, new HttpRequest()));
    }

    @Test
    public void matchesStringBodyAgainstMatcherWithEmptyBody() {
        assertTrue(new HttpRequestMatcher(mockServerLogger, new Expectation(new HttpRequest())).matches(null, new HttpRequest().withBody(exact("somebody"))));
    }

    @Test
    public void matchesEmptyBodyAgainstMatcherWithStringBodyForControlPlane() {
        assertFalse(new HttpRequestMatcher(mockServerLogger, new HttpRequest().withBody(exact("somebody"))).matches(null, new HttpRequest()));
    }

    @Test
    public void doesNotMatchStringBodyAgainstMatcherWithEmptyBodyForControlPlane() {
        assertTrue(new HttpRequestMatcher(mockServerLogger, new HttpRequest()).matches(null, new HttpRequest().withBody(exact("somebody"))));
    }

    @Test
    public void matchesMatchingBodyRegex() {
        assertTrue(new HttpRequestMatcher(mockServerLogger, new HttpRequest().withBody(regex("some[a-z]{4}"))).matches(null, new HttpRequest().withBody("somebody")));
    }

    @Test
    public void doesNotMatchIncorrectBodyRegex() {
        assertFalse(new HttpRequestMatcher(mockServerLogger, new HttpRequest().withBody(regex("some[a-z]{3}"))).matches(null, new HttpRequest().withBody("bodysome")));
    }

    @Test
    public void matchesMatchingBodyXPath() {
        String matched = "" +
            "<element>" +
            "   <key>some_key</key>" +
            "   <value>some_value</value>" +
            "</element>";
        assertTrue(
            new HttpRequestMatcher(
                mockServerLogger, new HttpRequest()
                    .withBody(xpath("/element[key = 'some_key' and value = 'some_value']"))
            )
                .matches(
                    null, new HttpRequest()
                        .withBody(matched)
                        .withMethod("PUT")
                )
        );
    }

    @Test
    public void matchesMatchingBodyXPathBodyDTO() {
        assertTrue(new HttpRequestMatcher(
                mockServerLogger, new HttpRequest()
                    .withBody(xpath("/element[key = 'some_key' and value = 'some_value']"))
            )
                .matches(
                    null, new HttpRequest()
                        .withBody(new XPathBodyDTO(xpath("/element[key = 'some_key' and value = 'some_value']")).toString())
                        .withMethod("PUT")
                )
        );
    }

    @Test
    public void doesNotMatchIncorrectBodyXPath() {
        String matched = "" +
            "<element>" +
            "   <key>some_key</key>" +
            "</element>";
        assertFalse(new HttpRequestMatcher(mockServerLogger, new HttpRequest().withBody(xpath("/element[key = 'some_key' and value = 'some_value']"))).matches(null, new HttpRequest().withBody(matched)));
    }

    @Test
    public void doesNotMatchIncorrectBodyXPathBodyDTO() {
        assertFalse(new HttpRequestMatcher(
                mockServerLogger, new HttpRequest().withBody(
                    xpath("/element[key = 'some_key' and value = 'some_value']")
                )
            ).matches(
            null, new HttpRequest().withBody(
                new XPathBodyDTO(xpath("/element[key = 'some_other_key' and value = 'some_value']")).toString()
            )
            )
        );
    }

    @Test
    public void matchesMatchingBodyXml() {
        String matched = "" +
            "<element attributeOne=\"one\" attributeTwo=\"two\">" +
            "   <key>some_key</key>" +
            "   <value>some_value</value>" +
            "</element>";
        assertTrue(new HttpRequestMatcher(mockServerLogger, new HttpRequest().withBody(xml("" +
            "<element attributeTwo=\"two\" attributeOne=\"one\">" +
            "   <key>some_key</key>" +
            "   <value>some_value</value>" +
            "</element>"))).matches(null, new HttpRequest().withBody(matched)));
    }

    @Test
    public void matchesMatchingBodyXmlBodyDTO() {
        assertTrue(new HttpRequestMatcher(
                mockServerLogger, new HttpRequest()
                    .withBody(xml("" +
                        "<element attributeOne=\"one\" attributeTwo=\"two\">" +
                        "   <key>some_key</key>" +
                        "   <value>some_value</value>" +
                        "</element>"))
            )
                .matches(
                    null, new HttpRequest()
                        .withBody(new XmlBodyDTO(xml("" +
                            "<element attributeOne=\"one\" attributeTwo=\"two\">" +
                            "   <key>some_key</key>" +
                            "   <value>some_value</value>" +
                            "</element>")).toString())
                        .withMethod("PUT")
                )
        );
    }

    @Test
    public void doesNotMatchIncorrectBodyXml() {
        String matched = "" +
            "<element>" +
            "   <key>some_key</key>" +
            "</element>";
        assertFalse(new HttpRequestMatcher(mockServerLogger, new HttpRequest().withBody(xml("" +
            "<element>" +
            "   <key>some_key</key>" +
            "   <value>some_value</value>" +
            "</element>"))).matches(null, new HttpRequest().withBody(matched)));
    }

    @Test
    public void doesNotMatchIncorrectBodyXmlBodyDTO() {
        assertFalse(new HttpRequestMatcher(
                mockServerLogger, new HttpRequest().withBody(xml("" +
                    "<element>" +
                    "   <key>some_key</key>" +
                    "</element>"))
            ).matches(
            null, new HttpRequest().withBody(new XmlBodyDTO(xml("" +
                "<element>" +
                "   <value>some_value</value>" +
                "   <key>some_key</key>" +
                "</element>")).toString())
            )
        );
    }

    @Test
    public void matchesMatchingBodyByXmlSchema() {
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
        assertTrue(new HttpRequestMatcher(mockServerLogger, new HttpRequest().withBody(xmlSchema(matcher))).matches(null, new HttpRequest().withBody("<?xml version=\"1.0\" encoding=\"utf-8\"?>" + NEW_LINE +
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
            "</notes>")));
    }

    @Test
    public void matchesMatchingBodyXmlSchemaBodyDTO() {
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
        assertTrue(new HttpRequestMatcher(mockServerLogger, new HttpRequest().withBody(xmlSchema(matcher))).matches(null, new HttpRequest().withBody(xml("<?xml version=\"1.0\" encoding=\"utf-8\"?>" + NEW_LINE +
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
            "</notes>"))));
    }

    @Test
    public void doesNotMatchIncorrectBodyByXmlSchema() {
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
        assertFalse(new HttpRequestMatcher(mockServerLogger, new HttpRequest().withBody(xmlSchema(matcher))).matches(null, new HttpRequest().withBody("<?xml version=\"1.0\" encoding=\"utf-8\"?>" + NEW_LINE +
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
    }

    @Test
    public void doesNotMatchIncorrectBodyXmlSchemaBodyDTO() {
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
        assertFalse(new HttpRequestMatcher(mockServerLogger, new HttpRequest().withBody(xmlSchema(matcher))).matches(null, new HttpRequest().withBody(xml("<?xml version=\"1.0\" encoding=\"utf-8\"?>" + NEW_LINE +
            "<notes>" + NEW_LINE +
            "    <note>" + NEW_LINE +
            "        <to>Bob</to>" + NEW_LINE +
            "        <from>Bill</from>" + NEW_LINE +
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
            "</notes>"))));
    }

    @Test
    public void matchesMatchingJSONBody() {
        String matched = "" +
            "{ " +
            "   \"some_field\": \"some_value\", " +
            "   \"some_other_field\": \"some_other_value\" " +
            "}";
        assertTrue(
            new HttpRequestMatcher(
                mockServerLogger, new HttpRequest()
                    .withBody(json("{ \"some_field\": \"some_value\" }"))
            )
                .matches(
                    null, new HttpRequest()
                        .withBody(matched)
                        .withMethod("PUT")
                )
        );
    }

    @Test
    public void matchesMatchingJSONBodyWithCharset() {
        String matched = "" +
            "{ " +
            "   \"some_field\": \"我说中国话\", " +
            "   \"some_other_field\": \"some_other_value\" " +
            "}";
        assertTrue(
            new HttpRequestMatcher(
                mockServerLogger, new HttpRequest()
                    .withBody(json("{ \"some_field\": \"我说中国话\" }", StandardCharsets.UTF_16, MatchType.ONLY_MATCHING_FIELDS))
            )
                .matches(
                    null, new HttpRequest()
                        .withBody(matched, StandardCharsets.UTF_16)
                        .withMethod("PUT")
                )
        );
    }

    @Test
    public void matchesMatchingJSONBodyDTO() {
        assertTrue(
            new HttpRequestMatcher(
                mockServerLogger, new HttpRequest()
                    .withBody(json("{ \"some_field\": \"some_value\" }"))
            )
                .matches(
                    null, new HttpRequest()
                        .withBody(new JsonBodyDTO(json("{ \"some_field\": \"some_value\" }")).toString())
                        .withMethod("PUT")
                )
        );
    }

    @Test
    public void doesNotMatchIncorrectJSONBody() {
        String matched = "" +
            "{ " +
            "   \"some_incorrect_field\": \"some_value\", " +
            "   \"some_other_field\": \"some_other_value\" " +
            "}";
        assertFalse(new HttpRequestMatcher(mockServerLogger, new HttpRequest().withBody(json("{ \"some_field\": \"some_value\" }"))).matches(null, new HttpRequest().withBody(matched)));
    }

    @Test
    public void doesNotMatchIncorrectJSONBodyDTO() {
        assertFalse(new HttpRequestMatcher(
                mockServerLogger, new HttpRequest().withBody(
                json("{ \"some_field\": \"some_value\" }")
            )
            ).matches(
            null, new HttpRequest().withBody(
                new JsonBodyDTO(json("{ \"some_other_field\": \"some_value\" }")).toString()
            ))
        );
    }

    @Test
    public void matchesMatchingJSONSchemaBody() {
        String matched = "" +
            "{" + NEW_LINE +
            "    \"id\": 1," + NEW_LINE +
            "    \"name\": \"A green door\"," + NEW_LINE +
            "    \"price\": 12.50," + NEW_LINE +
            "    \"tags\": [\"home\", \"green\"]" + NEW_LINE +
            "}";
        assertTrue(new HttpRequestMatcher(mockServerLogger, new HttpRequest().withBody(jsonSchema("{" + NEW_LINE +
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
            "}"))).matches(null, new HttpRequest().withBody(matched)));
    }

    @Test
    public void matchesMatchingJSONSchemaBodyDTO() {
        JsonSchemaBody jsonSchemaBody = jsonSchema("{" + NEW_LINE +
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
        assertTrue(
            new HttpRequestMatcher(
                mockServerLogger, new HttpRequest()
                    .withBody(jsonSchemaBody)
            )
                .matches(
                    null, new HttpRequest()
                        .withBody(new JsonSchemaBodyDTO(jsonSchemaBody).toString())
                        .withMethod("PUT")
                )
        );
    }

    @Test
    public void doesNotMatchIncorrectJSONSchemaBody() {
        String matched = "" +
            "{" + NEW_LINE +
            "    \"id\": 1," + NEW_LINE +
            "    \"name\": \"A green door\"," + NEW_LINE +
            "    \"price\": 12.50," + NEW_LINE +
            "    \"tags\": []" + NEW_LINE +
            "}";
        assertFalse(new HttpRequestMatcher(mockServerLogger, new HttpRequest().withBody(jsonSchema("{" + NEW_LINE +
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
            "}"))).matches(null, new HttpRequest().withBody(matched)));
    }

    @Test
    public void matchesMatchingBodyJsonPath() {
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
        assertTrue(
            new HttpRequestMatcher(
                mockServerLogger, new HttpRequest()
                    .withBody(jsonPath("$..book[?(@.price <= $['expensive'])]"))
            )
                .matches(
                    null, new HttpRequest()
                        .withBody(matched)
                        .withMethod("PUT")
                )
        );
    }

    @Test
    public void matchesMatchingBodyJsonPathBodyDTO() {
        assertTrue(new HttpRequestMatcher(
                mockServerLogger, new HttpRequest()
                    .withBody(jsonPath("$..book[?(@.price > $['expensive'])]"))
            )
                .matches(
                    null, new HttpRequest()
                        .withBody(new JsonPathBodyDTO(jsonPath("$..book[?(@.price > $['expensive'])]")).toString())
                        .withMethod("PUT")
                )
        );
    }

    @Test
    public void doesNotMatchIncorrectBodyJsonPath() {
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
        assertFalse(new HttpRequestMatcher(mockServerLogger, new HttpRequest().withBody(jsonPath("$..book[?(@.price > $['expensive'])]"))).matches(null, new HttpRequest().withBody(matched)));
    }

    @Test
    public void doesNotMatchIncorrectBodyJsonPathBodyDTO() {
        assertFalse(new HttpRequestMatcher(
                mockServerLogger, new HttpRequest().withBody(
                    xpath("$..book[?(@.price > $['expensive'])]")
                )
            ).matches(
            null, new HttpRequest().withBody(
                new JsonPathBodyDTO(jsonPath("$..book[?(@.price <= $['expensive'])]")).toString()
            )
            )
        );
    }

    @Test
    public void matchesMatchingBinaryBody() {
        byte[] matched = "some binary value".getBytes(UTF_8);
        assertTrue(new HttpRequestMatcher(mockServerLogger, new HttpRequest().withBody(binary("some binary value".getBytes(UTF_8)))).matches(null, new HttpRequest().withBody(binary(matched))));
    }

    @Test
    public void matchesMatchingBinaryBodyDTO() {
        assertTrue(
            new HttpRequestMatcher(
                mockServerLogger, new HttpRequest()
                    .withBody(binary("some binary value".getBytes(UTF_8)))
            )
                .matches(
                    null, new HttpRequest()
                        .withMethod("PUT")
                        .withBody(new BinaryBodyDTO(binary("some binary value".getBytes(UTF_8))).toString())
                )
        );
    }

    @Test
    public void doesNotMatchIncorrectBinaryBody() {
        byte[] matched = "some other binary value".getBytes(UTF_8);
        assertFalse(new HttpRequestMatcher(mockServerLogger, new HttpRequest().withBody(binary("some binary value".getBytes(UTF_8)))).matches(null, new HttpRequest().withBody(binary(matched))));
    }

    @Test
    public void doesNotMatchIncorrectBinaryBodyDTO() {
        assertFalse(new HttpRequestMatcher(
                mockServerLogger, new HttpRequest().withBody(binary("some binary value".getBytes(UTF_8)))
            ).matches(
            null, new HttpRequest().withBody(new BinaryBodyDTO(binary("some other binary value".getBytes(UTF_8))).toString()))
        );
    }

    @Test
    public void matchesMatchingHeaders() {
        assertTrue(new HttpRequestMatcher(mockServerLogger, new HttpRequest().withHeaders(new Header("name", "value"))).matches(null, new HttpRequest().withHeaders(new Header("name", "value"))));
    }

    @Test
    public void matchesMatchingHeadersWithRegex() {
        assertTrue(new HttpRequestMatcher(mockServerLogger, new HttpRequest().withHeaders(new Header("name", ".*"))).matches(null, new HttpRequest().withHeaders(new Header("name", "value"))));
    }

    @Test
    public void doesNotMatchIncorrectHeaderName() {
        assertFalse(new HttpRequestMatcher(mockServerLogger, new HttpRequest().withHeaders(new Header("name", "value"))).matches(null, new HttpRequest().withHeaders(new Header("name1", "value"))));
    }

    @Test
    public void doesNotMatchIncorrectHeaderValue() {
        assertFalse(new HttpRequestMatcher(mockServerLogger, new HttpRequest().withHeaders(new Header("name", "value"))).matches(null, new HttpRequest().withHeaders(new Header("name", "value1"))));
    }

    @Test
    public void doesNotMatchIncorrectHeaderValueRegex() {
        assertFalse(new HttpRequestMatcher(mockServerLogger, new HttpRequest().withHeaders(new Header("name", "[0-9]{0,100}"))).matches(null, new HttpRequest().withHeaders(new Header("name", "value1"))));
    }

    @Test
    public void matchesMatchingCookies() {
        assertTrue(new HttpRequestMatcher(mockServerLogger, new HttpRequest().withCookies(new Cookie("name", "value"))).matches(null, new HttpRequest().withCookies(new Cookie("name", "value"))));
    }

    @Test
    public void matchesMatchingCookiesWithRegex() {
        assertTrue(new HttpRequestMatcher(mockServerLogger, new HttpRequest().withCookies(new Cookie("name", "[a-z]{0,20}lue"))).matches(null, new HttpRequest().withCookies(new Cookie("name", "value"))));
    }

    @Test
    public void doesNotMatchIncorrectCookieName() {
        assertFalse(new HttpRequestMatcher(mockServerLogger, new HttpRequest().withCookies(new Cookie("name", "value"))).matches(null, new HttpRequest().withCookies(new Cookie("name1", "value"))));
    }

    @Test
    public void doesNotMatchIncorrectCookieValue() {
        assertFalse(new HttpRequestMatcher(mockServerLogger, new HttpRequest().withCookies(new Cookie("name", "value"))).matches(null, new HttpRequest().withCookies(new Cookie("name", "value1"))));
    }

    @Test
    public void doesNotMatchIncorrectCookieValueRegex() {
        assertFalse(new HttpRequestMatcher(mockServerLogger, new HttpRequest().withCookies(new Cookie("name", "[A-Z]{0,10}"))).matches(null, new HttpRequest().withCookies(new Cookie("name", "value1"))));
    }

    @Test
    public void shouldReturnFormattedRequestWithStringBodyInToString() {
        assertEquals("{" + NEW_LINE +
                "  \"method\" : \"GET\"," + NEW_LINE +
                "  \"path\" : \"/some/path\"," + NEW_LINE +
                "  \"queryStringParameters\" : {" + NEW_LINE +
                "    \"parameterOneName\" : [ \"parameterOneValue\" ]" + NEW_LINE +
                "  }," + NEW_LINE +
                "  \"headers\" : {" + NEW_LINE +
                "    \"name\" : [ \"value\" ]" + NEW_LINE +
                "  }," + NEW_LINE +
                "  \"cookies\" : {" + NEW_LINE +
                "    \"name\" : \"[A-Z]{0,10}\"" + NEW_LINE +
                "  }," + NEW_LINE +
                "  \"body\" : \"some_body\"" + NEW_LINE +
                "}",
            new HttpRequestMatcher(
                mockServerLogger, request()
                    .withMethod("GET")
                    .withPath("/some/path")
                    .withQueryStringParameters(param("parameterOneName", "parameterOneValue"))
                    .withBody("some_body")
                    .withHeaders(new Header("name", "value"))
                    .withCookies(new Cookie("name", "[A-Z]{0,10}"))
            ).toString()
        );
    }

    @Test
    public void shouldUpdateMatchingPath() {
        // given
        HttpRequestMatcher httpRequestMatcher = new HttpRequestMatcher(mockServerLogger, new HttpRequest().withPath("somePath"));

        // then
        assertFalse(httpRequestMatcher.matches(null, new HttpRequest().withPath("someOtherPath")));

        // when
        httpRequestMatcher.update(new HttpRequest().withPath("someOtherPath"));

        // then
        assertTrue(httpRequestMatcher.matches(null, new HttpRequest().withPath("someOtherPath")));
    }

    @Test
    public void shouldUpdateMatchingHeaders() {
        // given
        HttpRequestMatcher httpRequestMatcher = new HttpRequestMatcher(mockServerLogger, new HttpRequest().withHeaders(new Header("name", "value")));

        // then
        assertFalse(httpRequestMatcher.matches(null, new HttpRequest().withHeaders(new Header("name", "otherValue"))));

        // when
        httpRequestMatcher.update(new HttpRequest().withHeaders(new Header("name", "otherValue")));

        // then
        assertTrue(httpRequestMatcher.matches(null, new HttpRequest().withHeaders(new Header("name", "otherValue"))));
    }
}
