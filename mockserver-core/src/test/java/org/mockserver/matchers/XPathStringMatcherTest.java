package org.mockserver.matchers;

import org.junit.Test;
import org.mockserver.logging.MockServerLogger;

import static org.junit.Assert.*;
import static org.mockserver.matchers.NotMatcher.not;

/**
 * @author jamesdbloom
 */
public class XPathStringMatcherTest {

    @Test
    public void shouldMatchMatchingXPath() {
        String matched = "" +
                "<element>" +
                "   <key>some_key</key>" +
                "   <value>some_value</value>" +
                "</element>";
        assertTrue(new XPathStringMatcher(new MockServerLogger(),"/element[key = 'some_key' and value = 'some_value']").matches(null, matched));
        assertTrue(new XPathStringMatcher(new MockServerLogger(),"/element[key = 'some_key']").matches(null, matched));
        assertTrue(new XPathStringMatcher(new MockServerLogger(),"/element/key").matches(null, matched));
        assertTrue(new XPathStringMatcher(new MockServerLogger(),"/element[key and value]").matches(null, matched));
    }

    @Test
    public void shouldNotMatchMatchingXPathWithNot() {
        String matched = "" +
                "<element>" +
                "   <key>some_key</key>" +
                "   <value>some_value</value>" +
                "</element>";
        assertFalse(not(new XPathStringMatcher(new MockServerLogger(),"/element[key = 'some_key' and value = 'some_value']")).matches(null, matched));
        assertFalse(not(new XPathStringMatcher(new MockServerLogger(),"/element[key = 'some_key']")).matches(null, matched));
        assertFalse(not(new XPathStringMatcher(new MockServerLogger(),"/element/key")).matches(null, matched));
        assertFalse(not(new XPathStringMatcher(new MockServerLogger(),"/element[key and value]")).matches(null, matched));
    }

    @Test
    public void shouldMatchMatchingString() {
        assertTrue(new XPathStringMatcher(new MockServerLogger(),"some_value").matches(null, "some_value"));
        assertFalse(new XPathStringMatcher(new MockServerLogger(),"some_value").matches(null, "some_other_value"));
    }

    @Test
    public void shouldNotMatchNullExpectation() {
        assertFalse(new XPathStringMatcher(new MockServerLogger(),null).matches(null, "some_value"));
    }

    @Test
    public void shouldNotMatchEmptyExpectation() {
        assertFalse(new XPathStringMatcher(new MockServerLogger(),"").matches(null, "some_value"));
    }

    @Test
    public void shouldNotMatchNotMatchingXPath() {
        String matched = "" +
                "<element>" +
                "   <key>some_key</key>" +
                "   <value>some_value</value>" +
                "</element>";
        assertFalse(new XPathStringMatcher(new MockServerLogger(),"/element[key = 'some_key' and value = 'some_other_value']").matches(null, matched));
        assertFalse(new XPathStringMatcher(new MockServerLogger(),"/element[key = 'some_other_key']").matches(null, matched));
        assertFalse(new XPathStringMatcher(new MockServerLogger(),"/element/not_key").matches(null, matched));
        assertFalse(new XPathStringMatcher(new MockServerLogger(),"/element[key and not_value]").matches(null, matched));
    }
    @Test
    public void shouldMatchNotMatchingXPathWithNot() {
        String matched = "" +
                "<element>" +
                "   <key>some_key</key>" +
                "   <value>some_value</value>" +
                "</element>";
        assertTrue(not(new XPathStringMatcher(new MockServerLogger(),"/element[key = 'some_key' and value = 'some_other_value']")).matches(null, matched));
        assertTrue(not(new XPathStringMatcher(new MockServerLogger(),"/element[key = 'some_other_key']")).matches(null, matched));
        assertTrue(not(new XPathStringMatcher(new MockServerLogger(),"/element/not_key")).matches(null, matched));
        assertTrue(not(new XPathStringMatcher(new MockServerLogger(),"/element[key and not_value]")).matches(null, matched));
    }

    @Test
    public void shouldNotMatchNullTest() {
        assertFalse(new XPathStringMatcher(new MockServerLogger(),"some_value").matches(null, null));
    }

    @Test
    public void shouldNotMatchEmptyTest() {
        assertFalse(new XPathStringMatcher(new MockServerLogger(),"some_value").matches(null, ""));
    }

    @Test
    public void showHaveCorrectEqualsBehaviour() {
        MockServerLogger mockServerLogger = new MockServerLogger();
        assertEquals(new XPathStringMatcher(mockServerLogger,"some_value"), new XPathStringMatcher(mockServerLogger,"some_value"));
    }
}
