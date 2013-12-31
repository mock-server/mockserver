package org.mockserver.matchers;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * @author jamesdbloom
 */
public class StringMatcherTest {

    @Test
    public void shouldMatchMatchingString() {
        assertTrue(new StringMatcher("some_value").matches("some_value"));
    }

    @Test
    public void shouldMatchMatchingRegex() {
        assertTrue(new StringMatcher("some_[a-z]{5}").matches("some_value"));
    }

    @Test
    public void shouldMatchMatchingXPath() {
        String matched = "" +
                "<element>" +
                "   <key>some_key</key>" +
                "   <value>some_value</value>" +
                "</element>";
        assertTrue(new StringMatcher("/element[key = 'some_key' and value = 'some_value']").matches(matched));
        assertTrue(new StringMatcher("/element[key = 'some_key']").matches(matched));
        assertTrue(new StringMatcher("/element/key").matches(matched));
        assertTrue(new StringMatcher("/element[key and value]").matches(matched));
    }

    @Test
    public void shouldMatchNullExpectation() {
        assertTrue(new StringMatcher(null).matches("some_value"));
    }

    @Test
    public void shouldMatchEmptyExpectation() {
        assertTrue(new StringMatcher("").matches("some_value"));
    }

    @Test
    public void shouldNotMatchIncorrectString() {
        assertFalse(new StringMatcher("some_value").matches("not_matching"));
    }

    @Test
    public void shouldNotMatchIncorrectRegex() {
        assertFalse(new StringMatcher("some_[a-z]{4}").matches("some_value"));
    }

    @Test
    public void shouldNotMatchMatchingXPath() {
        String matched = "" +
                "<element>" +
                "   <key>some_key</key>" +
                "   <value>some_value</value>" +
                "</element>";
        assertFalse(new StringMatcher("/element[key = 'some_key' and value = 'some_other_value']").matches(matched));
        assertFalse(new StringMatcher("/element[key = 'some_other_key']").matches(matched));
        assertFalse(new StringMatcher("/element/not_key").matches(matched));
        assertFalse(new StringMatcher("/element[key and not_value]").matches(matched));
    }

    @Test
    public void shouldNotMatchNullTest() {
        assertFalse(new StringMatcher("some_value").matches(null));
    }

    @Test
    public void shouldNotMatchEmptyTest() {
        assertFalse(new StringMatcher("some_value").matches(""));
    }

    @Test
    public void shouldHandleIllegalRegexPatternForExpectationAndTest() {
        assertFalse(new StringMatcher("/{}").matches("/{}"));
    }

    @Test
    public void shouldHandleIllegalRegexPatternForExpectation() {
        assertFalse(new StringMatcher("/{}").matches("some_value"));
    }

    @Test
    public void shouldHandleIllegalRegexPatternForTest() {
        assertFalse(new StringMatcher("some_value").matches("/{}"));
    }
}
