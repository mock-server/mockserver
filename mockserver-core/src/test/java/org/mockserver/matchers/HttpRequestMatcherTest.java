package org.mockserver.matchers;

import com.google.common.base.Charsets;
import org.junit.Test;
import org.mockserver.client.serialization.model.*;
import org.mockserver.logging.LoggingFormatter;
import org.mockserver.mock.HttpStateHandler;
import org.mockserver.model.*;
import org.slf4j.LoggerFactory;

import static com.google.common.base.Charsets.UTF_8;
import static junit.framework.TestCase.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockserver.character.Character.NEW_LINE;
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
import static org.mockserver.model.XmlBody.xml;
import static org.mockserver.model.XmlSchemaBody.xmlSchema;

/**
 * @author jamesdbloom
 */
public class HttpRequestMatcherTest {

    private HttpStateHandler httpStateHandler = mock(HttpStateHandler.class);
    private LoggingFormatter loggingFormatter = new LoggingFormatter(LoggerFactory.getLogger(HttpRequestMatcherTest.class), httpStateHandler);

    @Test
    public void shouldAllowUseOfNotWithMatchingRequests() {
        // requests match - matcher HttpRequest notted
        assertFalse(new HttpRequestMatcher(org.mockserver.model.Not.not(new HttpRequest().withMethod("HEAD")), loggingFormatter).matches(new HttpRequest().withMethod("HEAD")));

        // requests match - matched HttpRequest notted
        assertFalse(new HttpRequestMatcher(new HttpRequest().withMethod("HEAD"), loggingFormatter).matches(org.mockserver.model.Not.not(new HttpRequest().withMethod("HEAD"))));

        // requests match - matcher HttpRequest notted & HttpRequestMatch notted
        assertTrue(not(new HttpRequestMatcher(org.mockserver.model.Not.not(new HttpRequest().withMethod("HEAD")), loggingFormatter)).matches(new HttpRequest().withMethod("HEAD")));

        // requests match - matched HttpRequest notted & HttpRequestMatch notted
        assertTrue(not(new HttpRequestMatcher(new HttpRequest().withMethod("HEAD"), loggingFormatter)).matches(org.mockserver.model.Not.not(new HttpRequest().withMethod("HEAD"))));

        // requests match - matcher HttpRequest notted & matched HttpRequest notted & HttpRequestMatch notted
        assertFalse(not(new HttpRequestMatcher(org.mockserver.model.Not.not(new HttpRequest().withMethod("HEAD")), loggingFormatter)).matches(org.mockserver.model.Not.not(new HttpRequest().withMethod("HEAD"))));
    }

    @Test
    public void shouldAllowUseOfNotWithNonMatchingRequests() {
        // requests don't match - matcher HttpRequest notted
        assertTrue(new HttpRequestMatcher(org.mockserver.model.Not.not(new HttpRequest().withMethod("HEAD")), loggingFormatter).matches(new HttpRequest().withMethod("OPTIONS")));

        // requests don't match - matched HttpRequest notted
        assertTrue(new HttpRequestMatcher(new HttpRequest().withMethod("HEAD"), loggingFormatter).matches(org.mockserver.model.Not.not(new HttpRequest().withMethod("OPTIONS"))));

        // requests don't match - matcher HttpRequest notted & HttpRequestMatch notted
        assertFalse(not(new HttpRequestMatcher(org.mockserver.model.Not.not(new HttpRequest().withMethod("HEAD")), loggingFormatter)).matches(new HttpRequest().withMethod("OPTIONS")));

        // requests don't match - matched HttpRequest notted & HttpRequestMatch notted
        assertFalse(not(new HttpRequestMatcher(new HttpRequest().withMethod("HEAD"), loggingFormatter)).matches(org.mockserver.model.Not.not(new HttpRequest().withMethod("OPTIONS"))));

        // requests don't match - matcher HttpRequest notted & matched HttpRequest notted & HttpRequestMatch notted
        assertTrue(not(new HttpRequestMatcher(org.mockserver.model.Not.not(new HttpRequest().withMethod("HEAD")), loggingFormatter)).matches(org.mockserver.model.Not.not(new HttpRequest().withMethod("OPTIONS"))));
    }

    @Test
    public void matchesMatchingKeepAlive() {
        assertTrue(new HttpRequestMatcher(new HttpRequest().withKeepAlive(true), loggingFormatter).matches(new HttpRequest().withKeepAlive(true)));
        assertTrue(new HttpRequestMatcher(new HttpRequest().withKeepAlive(false), loggingFormatter).matches(new HttpRequest().withKeepAlive(false)));
        assertTrue(new HttpRequestMatcher(new HttpRequest().withKeepAlive(null), loggingFormatter).matches(new HttpRequest().withKeepAlive(null)));
        assertTrue(new HttpRequestMatcher(new HttpRequest().withKeepAlive(null), loggingFormatter).matches(new HttpRequest().withKeepAlive(false)));
        assertTrue(new HttpRequestMatcher(new HttpRequest().withKeepAlive(null), loggingFormatter).matches(new HttpRequest()));
        assertTrue(new HttpRequestMatcher(new HttpRequest(), loggingFormatter).matches(new HttpRequest().withKeepAlive(null)));
    }

    @Test
    public void doesNotMatchIncorrectKeepAlive() {
        assertFalse(new HttpRequestMatcher(new HttpRequest().withKeepAlive(true), loggingFormatter).matches(new HttpRequest().withKeepAlive(false)));
        assertFalse(new HttpRequestMatcher(new HttpRequest().withKeepAlive(false), loggingFormatter).matches(new HttpRequest().withKeepAlive(true)));
        assertFalse(new HttpRequestMatcher(new HttpRequest().withKeepAlive(true), loggingFormatter).matches(new HttpRequest().withKeepAlive(null)));
        assertFalse(new HttpRequestMatcher(new HttpRequest().withKeepAlive(false), loggingFormatter).matches(new HttpRequest().withKeepAlive(null)));
    }

    @Test
    public void matchesMatchingSsl() {
        assertTrue(new HttpRequestMatcher(new HttpRequest().withSecure(true), loggingFormatter).matches(new HttpRequest().withSecure(true)));
        assertTrue(new HttpRequestMatcher(new HttpRequest().withSecure(false), loggingFormatter).matches(new HttpRequest().withSecure(false)));
        assertTrue(new HttpRequestMatcher(new HttpRequest().withSecure(null), loggingFormatter).matches(new HttpRequest().withSecure(null)));
        assertTrue(new HttpRequestMatcher(new HttpRequest().withSecure(null), loggingFormatter).matches(new HttpRequest().withSecure(false)));
        assertTrue(new HttpRequestMatcher(new HttpRequest().withSecure(null), loggingFormatter).matches(new HttpRequest()));
        assertTrue(new HttpRequestMatcher(new HttpRequest(), loggingFormatter).matches(new HttpRequest().withSecure(null)));
    }

    @Test
    public void doesNotMatchIncorrectSsl() {
        assertFalse(new HttpRequestMatcher(new HttpRequest().withSecure(true), loggingFormatter).matches(new HttpRequest().withSecure(false)));
        assertFalse(new HttpRequestMatcher(new HttpRequest().withSecure(false), loggingFormatter).matches(new HttpRequest().withSecure(true)));
        assertFalse(new HttpRequestMatcher(new HttpRequest().withSecure(true), loggingFormatter).matches(new HttpRequest().withSecure(null)));
        assertFalse(new HttpRequestMatcher(new HttpRequest().withSecure(false), loggingFormatter).matches(new HttpRequest().withSecure(null)));
    }

    @Test
    public void matchesMatchingMethod() {
        assertTrue(new HttpRequestMatcher(new HttpRequest().withMethod("HEAD"), loggingFormatter).matches(new HttpRequest().withMethod("HEAD")));
    }

    @Test
    public void matchesMatchingMethodRegex() {
        assertTrue(new HttpRequestMatcher(new HttpRequest().withMethod("P[A-Z]{2}"), loggingFormatter).matches(new HttpRequest().withMethod("PUT")));
    }

    @Test
    public void doesNotMatchIncorrectMethod() {
        assertFalse(new HttpRequestMatcher(new HttpRequest().withMethod("HEAD"), loggingFormatter).matches(new HttpRequest().withMethod("OPTIONS")));
    }

    @Test
    public void doesNotMatchIncorrectMethodRegex() {
        assertFalse(new HttpRequestMatcher(new HttpRequest().withMethod("P[A-Z]{2}"), loggingFormatter).matches(new HttpRequest().withMethod("POST")));
    }

    @Test
    public void matchesMatchingPath() {
        assertTrue(new HttpRequestMatcher(new HttpRequest().withPath("somePath"), loggingFormatter).matches(new HttpRequest().withPath("somePath")));
    }

    @Test
    public void doesNotMatchEncodedMatcherPath() {
        assertFalse(new HttpRequestMatcher(new HttpRequest().withPath("/dWM%2FdWM+ZA=="), loggingFormatter).matches(new HttpRequest().withPath("/dWM/dWM+ZA==")));
    }

    @Test
    public void doesNotMatchEncodedRequestPath() {
        assertFalse(new HttpRequestMatcher(new HttpRequest().withPath("/dWM/dWM+ZA=="), loggingFormatter).matches(new HttpRequest().withPath("/dWM%2FdWM+ZA==")));
    }

    @Test
    public void matchesMatchingEncodedMatcherAndRequestPath() {
        assertTrue(new HttpRequestMatcher(new HttpRequest().withPath("/dWM%2FdWM+ZA=="), loggingFormatter).matches(new HttpRequest().withPath("/dWM%2FdWM+ZA==")));
    }

    @Test
    public void matchesMatchingPathRegex() {
        assertTrue(new HttpRequestMatcher(new HttpRequest().withPath("someP[a-z]{3}"), loggingFormatter).matches(new HttpRequest().withPath("somePath")));
    }

    @Test
    public void doesNotMatchIncorrectPath() {
        assertFalse(new HttpRequestMatcher(new HttpRequest().withPath("somepath"), loggingFormatter).matches(new HttpRequest().withPath("pathsome")));
    }

    @Test
    public void doesNotMatchIncorrectPathRegex() {
        assertFalse(new HttpRequestMatcher(new HttpRequest().withPath("someP[a-z]{2}"), loggingFormatter).matches(new HttpRequest().withPath("somePath")));
    }

    @Test
    public void matchesMatchingQueryString() {
        assertTrue(new HttpRequestMatcher(new HttpRequest().withQueryStringParameters(new Parameter("someKey", "someValue")), loggingFormatter).matches(new HttpRequest().withQueryStringParameter(new Parameter("someKey", "someValue"))));
    }

    @Test
    public void matchesMatchingQueryStringRegexKeyAndValue() {
        assertTrue(new HttpRequestMatcher(new HttpRequest().withQueryStringParameters(new Parameter("someK[a-z]{2}", "someV[a-z]{4}")), loggingFormatter).matches(new HttpRequest().withQueryStringParameter(new Parameter("someKey", "someValue"))));
    }

    @Test
    public void matchesMatchingQueryStringRegexKey() {
        assertTrue(new HttpRequestMatcher(new HttpRequest().withQueryStringParameters(new Parameter("someK[a-z]{2}", "someValue")), loggingFormatter).matches(new HttpRequest().withQueryStringParameter(new Parameter("someKey", "someValue"))));
    }

    @Test
    public void matchesMatchingQueryStringRegexValue() {
        assertTrue(new HttpRequestMatcher(new HttpRequest().withQueryStringParameters(new Parameter("someKey", "someV[a-z]{4}")), loggingFormatter).matches(new HttpRequest().withQueryStringParameter(new Parameter("someKey", "someValue"))));
    }

    @Test
    public void doesNotMatchIncorrectQueryStringName() {
        assertFalse(new HttpRequestMatcher(new HttpRequest().withQueryStringParameters(new Parameter("someKey", "someValue")), loggingFormatter).matches(new HttpRequest().withQueryStringParameter(new Parameter("someOtherKey", "someValue"))));
    }

    @Test
    public void doesNotMatchIncorrectQueryStringValue() {
        assertFalse(new HttpRequestMatcher(new HttpRequest().withQueryStringParameters(new Parameter("someKey", "someValue")), loggingFormatter).matches(new HttpRequest().withQueryStringParameter(new Parameter("someKey", "someOtherValue"))));
    }

    @Test
    public void doesNotMatchIncorrectQueryStringRegexKeyAndValue() {
        assertFalse(new HttpRequestMatcher(new HttpRequest().withQueryStringParameters(new Parameter("someK[a-z]{5}", "someV[a-z]{2}")), loggingFormatter).matches(new HttpRequest().withQueryStringParameter(new Parameter("someKey", "someValue"))));
    }

    @Test
    public void doesNotMatchIncorrectQueryStringRegexKey() {
        assertFalse(new HttpRequestMatcher(new HttpRequest().withQueryStringParameters(new Parameter("someK[a-z]{5}", "someValue")), loggingFormatter).matches(new HttpRequest().withQueryStringParameter(new Parameter("someKey", "someValue"))));
    }

    @Test
    public void doesNotMatchIncorrectQueryStringRegexValue() {
        assertFalse(new HttpRequestMatcher(new HttpRequest().withQueryStringParameters(new Parameter("someKey", "someV[a-z]{2}")), loggingFormatter).matches(new HttpRequest().withQueryStringParameter(new Parameter("someKey", "someValue"))));
    }

    @Test
    public void matchesMatchingQueryStringParameters() {
        assertTrue(new HttpRequestMatcher(new HttpRequest().withQueryStringParameters(new Parameter("name", "value")), loggingFormatter).matches(new HttpRequest().withQueryStringParameters(new Parameter("name", "value"))));
    }

    @Test
    public void matchesMatchingQueryStringParametersWithRegex() {
        assertTrue(new HttpRequestMatcher(new HttpRequest().withQueryStringParameters(new Parameter("name", "v[a-z]{4}")), loggingFormatter).matches(new HttpRequest().withQueryStringParameters(new Parameter("name", "value"))));
    }

    @Test
    public void queryStringParametersMatchesMatchingQueryString() {
        assertTrue(new HttpRequestMatcher(new HttpRequest().withQueryStringParameters(new Parameter("nameOne", "valueOne")), loggingFormatter).matches(new HttpRequest().withQueryStringParameters(
            new Parameter("nameOne", "valueOne"),
            new Parameter("nameTwo", "valueTwo")
        )));
        assertTrue(new HttpRequestMatcher(new HttpRequest().withQueryStringParameters(new Parameter("nameTwo", "valueTwo")), loggingFormatter).matches(new HttpRequest().withQueryStringParameters(
            new Parameter("nameOne", "valueOne"),
            new Parameter("nameTwo", "valueTwo")
        )));
        assertTrue(new HttpRequestMatcher(new HttpRequest().withQueryStringParameters(new Parameter("nameTwo", "valueTwo", "valueThree")), loggingFormatter).matches(new HttpRequest().withQueryStringParameters(
            new Parameter("nameOne", "valueOne"),
            new Parameter("nameTwo", "valueTwo"),
            new Parameter("nameTwo", "valueThree")
        )));
        assertTrue(new HttpRequestMatcher(new HttpRequest().withQueryStringParameters(new Parameter("nameTwo", "valueTwo")), loggingFormatter).matches(new HttpRequest().withQueryStringParameters(
            new Parameter("nameOne", "valueOne"),
            new Parameter("nameTwo", "valueTwo"),
            new Parameter("nameTwo", "valueThree")
        )));
        assertTrue(new HttpRequestMatcher(new HttpRequest().withQueryStringParameters(new Parameter("nameTwo", "valueThree")), loggingFormatter).matches(new HttpRequest().withQueryStringParameters(
            new Parameter("nameOne", "valueOne"),
            new Parameter("nameTwo", "valueTwo"),
            new Parameter("nameTwo", "valueThree")
        )));
        assertTrue(new HttpRequestMatcher(new HttpRequest().withQueryStringParameters(new Parameter("nameTwo", "valueT[a-z]{0,10}")), loggingFormatter).matches(new HttpRequest().withQueryStringParameters(
            new Parameter("nameOne", "valueOne"),
            new Parameter("nameTwo", "valueTwo"),
            new Parameter("nameTwo", "valueThree")
        )));
    }

    @Test
    public void bodyMatchesMatchingBodyParameters() {
        assertTrue(new HttpRequestMatcher(new HttpRequest().withBody(params(new Parameter("nameOne", "valueOne"))), loggingFormatter).matches(new HttpRequest().withBody(new ParameterBody(
            new Parameter("nameOne", "valueOne"),
            new Parameter("nameTwo", "valueTwo")
        ))));
        assertTrue(new HttpRequestMatcher(new HttpRequest().withBody(new ParameterBody(new Parameter("nameTwo", "valueTwo"))), loggingFormatter).matches(new HttpRequest().withBody(new ParameterBody(
            new Parameter("nameOne", "valueOne"),
            new Parameter("nameTwo", "valueTwo")
        ))));
        assertTrue(new HttpRequestMatcher(new HttpRequest().withBody(params(new Parameter("nameTwo", "valueTwo", "valueThree"))), loggingFormatter).matches(new HttpRequest().withBody(new ParameterBody(
            new Parameter("nameOne", "valueOne"),
            new Parameter("nameTwo", "valueTwo"),
            new Parameter("nameTwo", "valueThree")
        ))));
        assertTrue(new HttpRequestMatcher(new HttpRequest().withBody(new ParameterBody(new Parameter("nameTwo", "valueTwo"))), loggingFormatter).matches(new HttpRequest().withBody(new ParameterBody(
            new Parameter("nameOne", "valueOne"),
            new Parameter("nameTwo", "valueTwo"),
            new Parameter("nameTwo", "valueThree")
        ))));
        assertTrue(new HttpRequestMatcher(new HttpRequest().withBody(params(new Parameter("nameTwo", "valueThree"))), loggingFormatter).matches(new HttpRequest().withBody(new ParameterBody(
            new Parameter("nameOne", "valueOne"),
            new Parameter("nameTwo", "valueTwo"),
            new Parameter("nameTwo", "valueThree")
        ))));
        assertTrue(new HttpRequestMatcher(new HttpRequest().withBody(new ParameterBody(new Parameter("nameTwo", "valueT[a-z]{0,10}"))), loggingFormatter).matches(new HttpRequest().withBody(new ParameterBody(
            new Parameter("nameOne", "valueOne"),
            new Parameter("nameTwo", "valueTwo"),
            new Parameter("nameTwo", "valueThree")
        ))));
    }

    @Test
    public void bodyMatchesMatchingUrlEncodedBodyParameters() {
        // pass exact match
        assertTrue(new HttpRequestMatcher(new HttpRequest().withBody(params(param("name one", "value one"), param("nameTwo", "valueTwo"))), loggingFormatter)
            .matches(new HttpRequest().withBody("name+one=value+one&nameTwo=valueTwo")));

        // ignore extra parameters
        assertTrue(new HttpRequestMatcher(new HttpRequest().withBody(params(param("name one", "value one"))), loggingFormatter)
            .matches(new HttpRequest().withBody("name+one=value+one&nameTwo=valueTwo")));

        // matches multi-value parameters
        assertTrue(new HttpRequestMatcher(new HttpRequest().withBody(params(param("name one", "value one one", "value one two"))), loggingFormatter)
            .matches(new HttpRequest().withBody("name+one=value+one+one&name+one=value+one+two")));

        // matches multi-value parameters (ignore extra values)
        assertTrue(new HttpRequestMatcher(new HttpRequest().withBody(params(param("name one", "value one one"))), loggingFormatter)
            .matches(new HttpRequest().withBody("name+one=value+one+one&name+one=value+one+two")));
        assertTrue(new HttpRequestMatcher(new HttpRequest().withBody(params(param("name one", "value one two"))), loggingFormatter)
            .matches(new HttpRequest().withBody("name+one=value+one+one&name+one=value+one+two")));

        // matches using regex
        assertTrue(new HttpRequestMatcher(new HttpRequest().withBody(params(param("name one", "value [a-z]{0,10}"), param("nameTwo", "valueT[a-z]{0,10}"))), loggingFormatter)
            .matches(new HttpRequest().withBody("name+one=value+one&nameTwo=valueTwo")));

        // fail no match
        assertFalse(new HttpRequestMatcher(new HttpRequest().withBody(params(param("name one", "value one"))), loggingFormatter)
            .matches(new HttpRequest().withBody("name+one=value+two")));
    }

    @Test
    public void bodyMatchesParameterBodyDTO() {
        assertTrue(new HttpRequestMatcher(
            new HttpRequest().withBody(params(
                new Parameter("nameOne", "valueOne"),
                new Parameter("nameTwo", "valueTwo")
            ))
            , loggingFormatter).matches(
            new HttpRequest().withBody(new ParameterBodyDTO(params(
                new Parameter("nameOne", "valueOne"),
                new Parameter("nameTwo", "valueTwo")
            )).toString())
        ));
    }

    @Test
    public void doesNotMatchIncorrectParameterName() {
        assertFalse(new HttpRequestMatcher(new HttpRequest().withBody(new ParameterBody(new Parameter("name", "value"))), loggingFormatter).matches(new HttpRequest().withBody(new ParameterBody(new Parameter("name1", "value")))));
    }

    @Test
    public void doesNotMatchIncorrectParameterValue() {
        assertFalse(new HttpRequestMatcher(new HttpRequest().withBody(new ParameterBody(new Parameter("name", "value"))), loggingFormatter).matches(new HttpRequest().withBody(new ParameterBody(new Parameter("name", "value1")))));
    }

    @Test
    public void doesNotMatchIncorrectParameterValueRegex() {
        assertFalse(new HttpRequestMatcher(new HttpRequest().withBody(new ParameterBody(new Parameter("name", "va[0-9]{1}ue"))), loggingFormatter).matches(new HttpRequest().withBody(new ParameterBody(new Parameter("name", "value1")))));
    }

    @Test
    public void doesNotMatchBodyMatchesParameterBodyDTOIncorrectParameters() {
        assertFalse(new HttpRequestMatcher(
            new HttpRequest().withBody(params(
                new Parameter("nameOne", "valueOne"),
                new Parameter("nameTwo", "valueTwo")
            ))
            , loggingFormatter).matches(
            new HttpRequest().withBody(new ParameterBodyDTO(params(
                new Parameter("nameOne", "valueOne")
            )).toString())
        ));
    }

    @Test
    public void matchesMatchingBody() {
        assertTrue(new HttpRequestMatcher(new HttpRequest().withBody(new StringBody("somebody")), loggingFormatter).matches(new HttpRequest().withBody("somebody")));
    }

    @Test
    public void matchesMatchingBodyWithCharset() {
        assertTrue(new HttpRequestMatcher(new HttpRequest().withBody(new StringBody("我说中国话", Charsets.UTF_16)), loggingFormatter).matches(new HttpRequest().withBody("我说中国话", Charsets.UTF_16)));
    }

    @Test
    public void doesNotMatchIncorrectBody() {
        assertFalse(new HttpRequestMatcher(new HttpRequest().withBody(exact("somebody")), loggingFormatter).matches(new HttpRequest().withBody("bodysome")));
    }

    @Test
    public void matchesMatchingBodyRegex() {
        assertTrue(new HttpRequestMatcher(new HttpRequest().withBody(regex("some[a-z]{4}")), loggingFormatter).matches(new HttpRequest().withBody("somebody")));
    }

    @Test
    public void doesNotMatchIncorrectBodyRegex() {
        assertFalse(new HttpRequestMatcher(new HttpRequest().withBody(regex("some[a-z]{3}")), loggingFormatter).matches(new HttpRequest().withBody("bodysome")));
    }

    @Test
    public void matchesMatchingBodyXPath() {
        String matched = "" +
            "<element>" +
            "   <key>some_key</key>" +
            "   <value>some_value</value>" +
            "</element>";
        assertTrue(new HttpRequestMatcher(new HttpRequest().withBody(xpath("/element[key = 'some_key' and value = 'some_value']")), loggingFormatter).matches(new HttpRequest().withBody(matched)));
    }

    @Test
    public void matchesMatchingBodyXPathBodyDTO() {
        assertTrue(new HttpRequestMatcher(
                new HttpRequest().withBody(xpath("/element[key = 'some_key' and value = 'some_value']"))
                , loggingFormatter).matches(
            new HttpRequest().withBody(new XPathBodyDTO(xpath("/element[key = 'some_key' and value = 'some_value']")).toString())
            )
        );
    }

    @Test
    public void doesNotMatchIncorrectBodyXPath() {
        String matched = "" +
            "<element>" +
            "   <key>some_key</key>" +
            "</element>";
        assertFalse(new HttpRequestMatcher(new HttpRequest().withBody(xpath("/element[key = 'some_key' and value = 'some_value']")), loggingFormatter).matches(new HttpRequest().withBody(matched)));
    }

    @Test
    public void doesNotMatchIncorrectBodyXPathBodyDTO() {
        assertFalse(new HttpRequestMatcher(
                new HttpRequest().withBody(
                    xpath("/element[key = 'some_key' and value = 'some_value']")
                )
                , loggingFormatter).matches(
            new HttpRequest().withBody(
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
        assertTrue(new HttpRequestMatcher(new HttpRequest().withBody(xml("" +
            "<element attributeTwo=\"two\" attributeOne=\"one\">" +
            "   <key>some_key</key>" +
            "   <value>some_value</value>" +
            "</element>")), loggingFormatter).matches(new HttpRequest().withBody(matched)));
    }

    @Test
    public void matchesMatchingBodyXmlBodyDTO() {
        assertTrue(new HttpRequestMatcher(
                new HttpRequest().withBody(xml("" +
                    "<element attributeOne=\"one\" attributeTwo=\"two\">" +
                    "   <key>some_key</key>" +
                    "   <value>some_value</value>" +
                    "</element>"))
                , loggingFormatter).matches(
            new HttpRequest().withBody(new XmlBodyDTO(xml("" +
                "<element attributeOne=\"one\" attributeTwo=\"two\">" +
                "   <key>some_key</key>" +
                "   <value>some_value</value>" +
                "</element>")).toString())
            )
        );
    }

    @Test
    public void doesNotMatchIncorrectBodyXml() {
        String matched = "" +
            "<element>" +
            "   <key>some_key</key>" +
            "</element>";
        assertFalse(new HttpRequestMatcher(new HttpRequest().withBody(xml("" +
            "<element>" +
            "   <key>some_key</key>" +
            "   <value>some_value</value>" +
            "</element>")), loggingFormatter).matches(new HttpRequest().withBody(matched)));
    }

    @Test
    public void doesNotMatchIncorrectBodyXmlBodyDTO() {
        assertFalse(new HttpRequestMatcher(
                new HttpRequest().withBody(xml("" +
                    "<element>" +
                    "   <key>some_key</key>" +
                    "</element>"))
                , loggingFormatter).matches(
            new HttpRequest().withBody(new XmlBodyDTO(xml("" +
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
        assertTrue(new HttpRequestMatcher(new HttpRequest().withBody(xmlSchema(matcher)), loggingFormatter).matches(new HttpRequest().withBody("<?xml version=\"1.0\" encoding=\"utf-8\"?>" + NEW_LINE +
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
        assertTrue(new HttpRequestMatcher(new HttpRequest().withBody(xmlSchema(matcher)), loggingFormatter).matches(new HttpRequest().withBody(xml("<?xml version=\"1.0\" encoding=\"utf-8\"?>" + NEW_LINE +
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
        assertFalse(new HttpRequestMatcher(new HttpRequest().withBody(xmlSchema(matcher)), loggingFormatter).matches(new HttpRequest().withBody("<?xml version=\"1.0\" encoding=\"utf-8\"?>" + NEW_LINE +
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
        assertFalse(new HttpRequestMatcher(new HttpRequest().withBody(xmlSchema(matcher)), loggingFormatter).matches(new HttpRequest().withBody(xml("<?xml version=\"1.0\" encoding=\"utf-8\"?>" + NEW_LINE +
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
        assertTrue(new HttpRequestMatcher(new HttpRequest().withBody(json("{ \"some_field\": \"some_value\" }")), loggingFormatter).matches(new HttpRequest().withBody(matched)));
    }

    @Test
    public void matchesMatchingJSONBodyWithCharset() {
        String matched = "" +
            "{ " +
            "   \"some_field\": \"我说中国话\", " +
            "   \"some_other_field\": \"some_other_value\" " +
            "}";
        assertTrue(new HttpRequestMatcher(new HttpRequest().withBody(json("{ \"some_field\": \"我说中国话\" }", Charsets.UTF_16, MatchType.ONLY_MATCHING_FIELDS)), loggingFormatter).matches(new HttpRequest().withBody(matched, Charsets.UTF_16)));
    }

    @Test
    public void matchesMatchingJSONBodyDTO() {
        assertTrue(new HttpRequestMatcher(
            new HttpRequest().withBody(
                json("{ \"some_field\": \"some_value\" }")
            )
            , loggingFormatter).matches(
            new HttpRequest().withBody(
                new JsonBodyDTO(json("{ \"some_field\": \"some_value\" }")).toString()
            ))
        );
    }

    @Test
    public void doesNotMatchIncorrectJSONBody() {
        String matched = "" +
            "{ " +
            "   \"some_incorrect_field\": \"some_value\", " +
            "   \"some_other_field\": \"some_other_value\" " +
            "}";
        assertFalse(new HttpRequestMatcher(new HttpRequest().withBody(json("{ \"some_field\": \"some_value\" }")), loggingFormatter).matches(new HttpRequest().withBody(matched)));
    }

    @Test
    public void doesNotMatchIncorrectJSONBodyDTO() {
        assertFalse(new HttpRequestMatcher(
            new HttpRequest().withBody(
                json("{ \"some_field\": \"some_value\" }")
            )
            , loggingFormatter).matches(
            new HttpRequest().withBody(
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
        assertTrue(new HttpRequestMatcher(new HttpRequest().withBody(jsonSchema("{" + NEW_LINE +
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
            "}")), loggingFormatter).matches(new HttpRequest().withBody(matched)));
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
        assertTrue(new HttpRequestMatcher(
            new HttpRequest().withBody(jsonSchemaBody)
            , loggingFormatter).matches(new HttpRequest().withBody(
            new JsonSchemaBodyDTO(jsonSchemaBody).toString()))
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
        assertFalse(new HttpRequestMatcher(new HttpRequest().withBody(jsonSchema("{" + NEW_LINE +
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
            "}")), loggingFormatter).matches(new HttpRequest().withBody(matched)));
    }

    @Test
    public void matchesMatchingBinaryBody() {
        byte[] matched = "some binary value".getBytes(UTF_8);
        assertTrue(new HttpRequestMatcher(new HttpRequest().withBody(binary("some binary value".getBytes(UTF_8))), loggingFormatter).matches(new HttpRequest().withBody(binary(matched))));
    }

    @Test
    public void matchesMatchingBinaryBodyDTO() {
        assertTrue(new HttpRequestMatcher(
            new HttpRequest().withBody(binary("some binary value".getBytes(UTF_8)))
            , loggingFormatter).matches(
            new HttpRequest().withBody(new BinaryBodyDTO(binary("some binary value".getBytes(UTF_8))).toString()))
        );
    }

    @Test
    public void doesNotMatchIncorrectBinaryBody() {
        byte[] matched = "some other binary value".getBytes(UTF_8);
        assertFalse(new HttpRequestMatcher(new HttpRequest().withBody(binary("some binary value".getBytes(UTF_8))), loggingFormatter).matches(new HttpRequest().withBody(binary(matched))));
    }

    @Test
    public void doesNotMatchIncorrectBinaryBodyDTO() {
        assertFalse(new HttpRequestMatcher(
            new HttpRequest().withBody(binary("some binary value".getBytes(UTF_8)))
            , loggingFormatter).matches(
            new HttpRequest().withBody(new BinaryBodyDTO(binary("some other binary value".getBytes(UTF_8))).toString()))
        );
    }

    @Test
    public void matchesMatchingHeaders() {
        assertTrue(new HttpRequestMatcher(new HttpRequest().withHeaders(new Header("name", "value")), loggingFormatter).matches(new HttpRequest().withHeaders(new Header("name", "value"))));
    }

    @Test
    public void matchesMatchingHeadersWithRegex() {
        assertTrue(new HttpRequestMatcher(new HttpRequest().withHeaders(new Header("name", ".*")), loggingFormatter).matches(new HttpRequest().withHeaders(new Header("name", "value"))));
    }

    @Test
    public void doesNotMatchIncorrectHeaderName() {
        assertFalse(new HttpRequestMatcher(new HttpRequest().withHeaders(new Header("name", "value")), loggingFormatter).matches(new HttpRequest().withHeaders(new Header("name1", "value"))));
    }

    @Test
    public void doesNotMatchIncorrectHeaderValue() {
        assertFalse(new HttpRequestMatcher(new HttpRequest().withHeaders(new Header("name", "value")), loggingFormatter).matches(new HttpRequest().withHeaders(new Header("name", "value1"))));
    }

    @Test
    public void doesNotMatchIncorrectHeaderValueRegex() {
        assertFalse(new HttpRequestMatcher(new HttpRequest().withHeaders(new Header("name", "[0-9]{0,100}")), loggingFormatter).matches(new HttpRequest().withHeaders(new Header("name", "value1"))));
    }

    @Test
    public void matchesMatchingCookies() {
        assertTrue(new HttpRequestMatcher(new HttpRequest().withCookies(new Cookie("name", "value")), loggingFormatter).matches(new HttpRequest().withCookies(new Cookie("name", "value"))));
    }

    @Test
    public void matchesMatchingCookiesWithRegex() {
        assertTrue(new HttpRequestMatcher(new HttpRequest().withCookies(new Cookie("name", "[a-z]{0,20}lue")), loggingFormatter).matches(new HttpRequest().withCookies(new Cookie("name", "value"))));
    }

    @Test
    public void doesNotMatchIncorrectCookieName() {
        assertFalse(new HttpRequestMatcher(new HttpRequest().withCookies(new Cookie("name", "value")), loggingFormatter).matches(new HttpRequest().withCookies(new Cookie("name1", "value"))));
    }

    @Test
    public void doesNotMatchIncorrectCookieValue() {
        assertFalse(new HttpRequestMatcher(new HttpRequest().withCookies(new Cookie("name", "value")), loggingFormatter).matches(new HttpRequest().withCookies(new Cookie("name", "value1"))));
    }

    @Test
    public void doesNotMatchIncorrectCookieValueRegex() {
        assertFalse(new HttpRequestMatcher(new HttpRequest().withCookies(new Cookie("name", "[A-Z]{0,10}")), loggingFormatter).matches(new HttpRequest().withCookies(new Cookie("name", "value1"))));
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
                request()
                    .withMethod("GET")
                    .withPath("/some/path")
                    .withQueryStringParameters(param("parameterOneName", "parameterOneValue"))
                    .withBody("some_body")
                    .withHeaders(new Header("name", "value"))
                    .withCookies(new Cookie("name", "[A-Z]{0,10}")),
                loggingFormatter
            ).toString()
        );
    }
}
