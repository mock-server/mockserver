package org.mockserver.matchers;

import org.junit.Test;
import org.mockserver.logging.MockServerLogger;

import static org.junit.Assert.*;
import static org.mockserver.matchers.NotMatcher.not;
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
    public void shouldNotMatchMatchingXMLWithNot() {
        String matched = "" +
                "<element>" +
                "   <key>some_key</key>" +
                "   <value>some_value</value>" +
                "</element>";
        assertFalse(not(new XmlStringMatcher(new MockServerLogger(),"<element><key>some_key</key><value>some_value</value></element>")).matches(matched));
        assertFalse(not(new XmlStringMatcher(new MockServerLogger(),"" +
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
        assertTrue(new XmlStringMatcher(new MockServerLogger(),"<element attributeTwo=\"two\" attributeOne=\"one\"><key attributeTwo=\"two\" attributeOne=\"one\">some_key</key><value>some_value</value></element>").matches(matched));
        assertTrue(new XmlStringMatcher(new MockServerLogger(),"<element attributeTwo=\"two\" attributeOne=\"one\">" +
                "   <key attributeTwo=\"two\" attributeOne=\"one\">some_key</key>" +
                "   <value>some_value</value>" +
                "</element>").matches(matched));
    }

    @Test
    public void shouldNotMatchInvalidXml() {
        assertFalse(new XmlStringMatcher(new MockServerLogger(),"invalid xml").matches("<element></element>"));
        assertFalse(new XmlStringMatcher(new MockServerLogger(),"<element></element>").matches("invalid_xml"));
    }

    @Test
    public void shouldNotMatchNullExpectation() {
        assertFalse(new XmlStringMatcher(new MockServerLogger(), string(null)).matches("some_value"));
        assertFalse(new XmlStringMatcher(new MockServerLogger(), (String) null).matches("some_value"));
    }

    @Test
    public void shouldNotMatchEmptyExpectation() {
        assertFalse(new XmlStringMatcher(new MockServerLogger(),"").matches("some_value"));
    }

    @Test
    public void shouldNotMatchNotMatchingXML() {
        String matched = "" +
                "<element>" +
                "   <key>some_key</key>" +
                "   <value>some_value</value>" +
                "</element>";
        assertFalse(new XmlStringMatcher(new MockServerLogger(),"" +
                "<another_element>" +
                "   <key>some_key</key>" +
                "   <value>some_value</value>" +
                "</another_element>").matches(matched));
        assertFalse(new XmlStringMatcher(new MockServerLogger(),"" +
                "<element>" +
                "   <another_key>some_key</another_key>" +
                "   <value>some_value</value>" +
                "</element>").matches(matched));
        assertFalse(new XmlStringMatcher(new MockServerLogger(),"" +
                "<element>" +
                "   <key>some_key</key>" +
                "   <another_value>some_value</another_value>" +
                "</element>").matches(matched));
        assertFalse(new XmlStringMatcher(new MockServerLogger(),"" +
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
        assertTrue(not(new XmlStringMatcher(new MockServerLogger(),"" +
                "<another_element>" +
                "   <key>some_key</key>" +
                "   <value>some_value</value>" +
                "</another_element>")).matches(matched));
        assertTrue(not(new XmlStringMatcher(new MockServerLogger(),"" +
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
        assertFalse(new XmlStringMatcher(new MockServerLogger(),"" +
                "<element someOtherAttribute=\"some_value\">" +
                "   <key>some_key</key>" +
                "   <value>some_value</value>" +
                "</element>").matches(matched));
        assertFalse(new XmlStringMatcher(new MockServerLogger(),"" +
                "<element someAttribute=\"some_other_value\">" +
                "   <key>some_key</key>" +
                "   <value>some_value</value>" +
                "</element>").matches(matched));
    }

    @Test
    public void shouldNotMatchNullTest() {
        assertFalse(new XmlStringMatcher(new MockServerLogger(),"some_value").matches(null, string(null)));
        assertFalse(new XmlStringMatcher(new MockServerLogger(),"some_value").matches((String) null));
    }

    @Test
    public void shouldMatchNullTest() {
        assertTrue(not(new XmlStringMatcher(new MockServerLogger(),"some_value")).matches(null, string(null)));
        assertTrue(not(new XmlStringMatcher(new MockServerLogger(),"some_value")).matches((String) null));
    }

    @Test
    public void shouldNotMatchEmptyTest() {
        assertFalse(new XmlStringMatcher(new MockServerLogger(),"some_value").matches(""));
    }

    @Test
    public void showHaveCorrectEqualsBehaviour() {
        MockServerLogger mockServerLogger = new MockServerLogger();
        assertEquals(new XmlStringMatcher(mockServerLogger,"some_value"), new XmlStringMatcher(mockServerLogger,"some_value"));
    }
}
