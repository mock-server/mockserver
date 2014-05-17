package org.mockserver.matchers;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

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
        assertTrue(new XPathStringMatcher("/element[key = 'some_key' and value = 'some_value']").matches(matched));
        assertTrue(new XPathStringMatcher("/element[key = 'some_key']").matches(matched));
        assertTrue(new XPathStringMatcher("/element/key").matches(matched));
        assertTrue(new XPathStringMatcher("/element[key and value]").matches(matched));
    }

    @Test
    public void shouldNotMatchNullExpectation() {
        assertFalse(new XPathStringMatcher(null).matches("some_value"));
    }

    @Test
    public void shouldNotMatchEmptyExpectation() {
        assertFalse(new XPathStringMatcher("").matches("some_value"));
    }

    @Test
    public void shouldNotMatchMatchingXPath() {
        String matched = "" +
                "<element>" +
                "   <key>some_key</key>" +
                "   <value>some_value</value>" +
                "</element>";
        assertFalse(new XPathStringMatcher("/element[key = 'some_key' and value = 'some_other_value']").matches(matched));
        assertFalse(new XPathStringMatcher("/element[key = 'some_other_key']").matches(matched));
        assertFalse(new XPathStringMatcher("/element/not_key").matches(matched));
        assertFalse(new XPathStringMatcher("/element[key and not_value]").matches(matched));
    }

    @Test
    public void shouldNotMatchNullTest() {
        assertFalse(new XPathStringMatcher("some_value").matches(null));
    }

    @Test
    public void shouldNotMatchEmptyTest() {
        assertFalse(new XPathStringMatcher("some_value").matches(""));
    }

    @Test
    public void showHaveCorrectEqualsBehaviour(){
        assertEquals(new XPathStringMatcher("some_value"), new XPathStringMatcher("some_value"));
    }
}
