package org.mockserver.matchers;

import org.junit.Test;
import org.mockserver.logging.MockServerLogger;

import static junit.framework.TestCase.*;
import static org.mockserver.character.Character.NEW_LINE;
import static org.mockserver.matchers.NotMatcher.notMatcher;
import static org.mockserver.model.NottableString.string;

/**
 * @author jamesdbloom
 */
public class XmlStringMatcherTest {

    @Test
    public void shouldMatchMatchingXML() {
        String matched = "" +
            "<element>" +
            "   <key>some_key</key>" +
            "   <value>some_value</value>" +
            "</element>";
        assertTrue(new XmlStringMatcher(new MockServerLogger(), "<element><key>some_key</key><value>some_value</value></element>").matches(matched));
        assertTrue(new XmlStringMatcher(new MockServerLogger(), "" +
            "<element>" +
            "   <key>some_key</key>" +
            "   <value>some_value</value>" +
            "</element>").matches(matched));
    }

    @Test
    public void shouldMatchMatchingXMLWithIsNumberPlaceholder() {
        String matched = "" +
            "<message>" + NEW_LINE +
            "  <id>67890</id>" + NEW_LINE +
            "  <content>Hello</content>" + NEW_LINE +
            "</message>";
        assertTrue(new XmlStringMatcher(new MockServerLogger(), "" +
            "<message>" + NEW_LINE +
            "  <id>${xmlunit.isNumber}</id>" + NEW_LINE +
            "  <content>Hello</content>" + NEW_LINE +
            "</message>").matches(matched));
    }

    @Test
    public void shouldNotMatchMatchingXMLWithIsNumberPlaceholder() {
        String matched = "" +
            "<message>" + NEW_LINE +
            "  <id>foo</id>" + NEW_LINE +
            "  <content>Hello</content>" + NEW_LINE +
            "</message>";
        assertFalse(new XmlStringMatcher(new MockServerLogger(), "" +
            "<message>" + NEW_LINE +
            "  <id>${xmlunit.isNumber}</id>" + NEW_LINE +
            "  <content>Hello</content>" + NEW_LINE +
            "</message>").matches(matched));
    }

    @Test
    public void shouldMatchMatchingXMLWithIgnorePlaceholder() {
        String matched = "" +
            "<message>" + NEW_LINE +
            "  <id>67890</id>" + NEW_LINE +
            "  <content>Hello</content>" + NEW_LINE +
            "</message>";
        assertTrue(new XmlStringMatcher(new MockServerLogger(), "" +
            "<message>" + NEW_LINE +
            "  <id>${xmlunit.ignore}</id>" + NEW_LINE +
            "  <content>Hello</content>" + NEW_LINE +
            "</message>").matches(matched));
    }

    @Test
    public void shouldMatchMatchingXMLWithDifferentNamespaceOrders() {
        String matched = "" +
            "<?xml version=\"1.0\"?>" + NEW_LINE +
            NEW_LINE +
            "<soap:Envelope" + NEW_LINE +
            "xmlns:soap=\"http://www.w3.org/2003/05/soap-envelope/\"" + NEW_LINE +
            "soap:encodingStyle=\"http://www.w3.org/2003/05/soap-encoding\">" + NEW_LINE +
            NEW_LINE +
            "<soap:Body xmlns:m=\"http://www.example.org/stock\">" + NEW_LINE +
            "  <m:GetStockPriceResponse>" + NEW_LINE +
            "    <m:Price>34.5</m:Price>" + NEW_LINE +
            "  </m:GetStockPriceResponse>" + NEW_LINE +
            "</soap:Body>" + NEW_LINE +
            NEW_LINE +
            "</soap:Envelope>";
        assertTrue(new XmlStringMatcher(new MockServerLogger(), "" +
            "<?xml version=\"1.0\"?>" + NEW_LINE +
            "<soap:Envelope" + NEW_LINE +
            "soap:encodingStyle=\"http://www.w3.org/2003/05/soap-encoding\"" + NEW_LINE +
            "xmlns:soap=\"http://www.w3.org/2003/05/soap-envelope/\"" + NEW_LINE +
            "xmlns:m=\"http://www.example.org/stock\">" + NEW_LINE +
            "<soap:Body>" + NEW_LINE +
            "  <m:GetStockPriceResponse>" + NEW_LINE +
            "    <m:Price>34.5</m:Price>" + NEW_LINE +
            "  </m:GetStockPriceResponse>" + NEW_LINE +
            "</soap:Body>" + NEW_LINE +
            "</soap:Envelope>").matches(matched));
    }

    @Test
    public void shouldMatchMatchingXMLWithDifferentNamespacePrefixes() {
        String matcher = "<a:element xmlns:a=\"the_namespace\"><a:key>some_key</a:key><a:value>some_value</a:value></a:element>";
        String matched = "<b:element xmlns:b=\"the_namespace\"><b:key>some_key</b:key><b:value>some_value</b:value></b:element>";
        assertTrue(new XmlStringMatcher(new MockServerLogger(), matcher).matches(matched));
    }

    @Test
    public void shouldNotMatchMatchingXMLWithNot() {
        String matched = "" +
            "<element>" +
            "   <key>some_key</key>" +
            "   <value>some_value</value>" +
            "</element>";
        assertFalse(notMatcher(new XmlStringMatcher(new MockServerLogger(), "<element><key>some_key</key><value>some_value</value></element>")).matches(matched));
        assertFalse(notMatcher(new XmlStringMatcher(new MockServerLogger(), "" +
            "<element>" +
            "   <key>some_key</key>" +
            "   <value>some_value</value>" +
            "</element>")).matches(matched));
    }

    @Test
    public void shouldMatchMatchingXMLWithDifferentAttributeOrder() {
        String matched = "" +
            "<element attributeOne=\"one\" attributeTwo=\"two\">" +
            "   <key attributeOne=\"one\" attributeTwo=\"two\">some_key</key>" +
            "   <value>some_value</value>" +
            "</element>";
        assertTrue(new XmlStringMatcher(new MockServerLogger(), "<element attributeTwo=\"two\" attributeOne=\"one\"><key attributeTwo=\"two\" attributeOne=\"one\">some_key</key><value>some_value</value></element>").matches(matched));
        assertTrue(new XmlStringMatcher(new MockServerLogger(), "<element attributeTwo=\"two\" attributeOne=\"one\">" +
            "   <key attributeTwo=\"two\" attributeOne=\"one\">some_key</key>" +
            "   <value>some_value</value>" +
            "</element>").matches(matched));
    }

    @Test
    public void shouldNotMatchInvalidXml() {
        assertFalse(new XmlStringMatcher(new MockServerLogger(), "invalid xml").matches("<element></element>"));
        assertFalse(new XmlStringMatcher(new MockServerLogger(), "<element></element>").matches("invalid_xml"));
    }

    @Test
    public void shouldNotMatchNullExpectation() {
        assertFalse(new XmlStringMatcher(new MockServerLogger(), string(null)).matches("some_value"));
        assertFalse(new XmlStringMatcher(new MockServerLogger(), (String) null).matches("some_value"));
    }

    @Test
    public void shouldNotMatchEmptyExpectation() {
        assertFalse(new XmlStringMatcher(new MockServerLogger(), "").matches("some_value"));
    }

    @Test
    public void shouldNotMatchNotMatchingXML() {
        String matched = "" +
            "<element>" +
            "   <key>some_key</key>" +
            "   <value>some_value</value>" +
            "</element>";
        assertFalse(new XmlStringMatcher(new MockServerLogger(), "" +
            "<another_element>" +
            "   <key>some_key</key>" +
            "   <value>some_value</value>" +
            "</another_element>").matches(matched));
        assertFalse(new XmlStringMatcher(new MockServerLogger(), "" +
            "<element>" +
            "   <another_key>some_key</another_key>" +
            "   <value>some_value</value>" +
            "</element>").matches(matched));
        assertFalse(new XmlStringMatcher(new MockServerLogger(), "" +
            "<element>" +
            "   <key>some_key</key>" +
            "   <another_value>some_value</another_value>" +
            "</element>").matches(matched));
        assertFalse(new XmlStringMatcher(new MockServerLogger(), "" +
            "<element>" +
            "   <key>some_other_key</key>" +
            "   <value>some_value</value>" +
            "</element>").matches(matched));
    }

    @Test
    public void shouldMatchNotMatchingXMLWithNot() {
        String matched = "" +
            "<element>" +
            "   <key>some_key</key>" +
            "   <value>some_value</value>" +
            "</element>";
        assertTrue(notMatcher(new XmlStringMatcher(new MockServerLogger(), "" +
            "<another_element>" +
            "   <key>some_key</key>" +
            "   <value>some_value</value>" +
            "</another_element>")).matches(matched));
        assertTrue(notMatcher(new XmlStringMatcher(new MockServerLogger(), "" +
            "<element>" +
            "   <another_key>some_key</another_key>" +
            "   <value>some_value</value>" +
            "</element>")).matches(matched));
    }

    @Test
    public void shouldNotMatchXMLWithDifferentAttributes() {
        String matched = "" +
            "<element someAttribute=\"some_value\">" +
            "   <key>some_key</key>" +
            "   <value>some_value</value>" +
            "</element>";
        assertFalse(new XmlStringMatcher(new MockServerLogger(), "" +
            "<element someOtherAttribute=\"some_value\">" +
            "   <key>some_key</key>" +
            "   <value>some_value</value>" +
            "</element>").matches(matched));
        assertFalse(new XmlStringMatcher(new MockServerLogger(), "" +
            "<element someAttribute=\"some_other_value\">" +
            "   <key>some_key</key>" +
            "   <value>some_value</value>" +
            "</element>").matches(matched));
    }

    @Test
    public void shouldNotMatchNullTest() {
        assertFalse(new XmlStringMatcher(new MockServerLogger(), "some_value").matches(null, null));
        assertFalse(new XmlStringMatcher(new MockServerLogger(), "some_value").matches(null));
    }

    @Test
    public void shouldMatchNullTest() {
        assertTrue(notMatcher(new XmlStringMatcher(new MockServerLogger(), "some_value")).matches(null, null));
        assertTrue(notMatcher(new XmlStringMatcher(new MockServerLogger(), "some_value")).matches(null));
    }

    @Test
    public void shouldNotMatchEmptyTest() {
        assertFalse(new XmlStringMatcher(new MockServerLogger(), "some_value").matches(""));
    }

    @Test
    public void showHaveCorrectEqualsBehaviour() {
        MockServerLogger mockServerLogger = new MockServerLogger();
        assertEquals(new XmlStringMatcher(mockServerLogger, "some_value"), new XmlStringMatcher(mockServerLogger, "some_value"));
    }
}
