package org.mockserver.matchers;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * @author jamesdbloom
 */
public class RegexStringMatcherTest {

    @Test
    public void shouldMatchMatchingString() {
        assertTrue(new RegexStringMatcher("some_value").matches("some_value"));
    }

    @Test
    public void shouldMatchMatchingStringWithRegexSymbols() {
        assertTrue(new RegexStringMatcher("text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8").matches("text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8"));
    }

    @Test
    public void shouldMatchMatchingRegex() {
        assertTrue(new RegexStringMatcher("some_[a-z]{5}").matches("some_value"));
    }

    @Test
    public void shouldMatchNullExpectation() {
        assertTrue(new RegexStringMatcher(null).matches("some_value"));
    }

    @Test
    public void shouldMatchEmptyExpectation() {
        assertTrue(new RegexStringMatcher("").matches("some_value"));
    }

    @Test
    public void shouldNotMatchIncorrectString() {
        assertFalse(new RegexStringMatcher("some_value").matches("not_matching"));
    }

    @Test
    public void shouldNotMatchIncorrectStringWithRegexSymbols() {
        assertFalse(new RegexStringMatcher("text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8").matches("text/html,application/xhtml+xml,application/xml;q=0.9;q=0.8"));
    }

    @Test
    public void shouldNotMatchIncorrectRegex() {
        assertFalse(new RegexStringMatcher("some_[a-z]{4}").matches("some_value"));
    }

    @Test
    public void shouldNotMatchNullTest() {
        assertFalse(new RegexStringMatcher("some_value").matches(null));
    }

    @Test
    public void shouldNotMatchEmptyTest() {
        assertFalse(new RegexStringMatcher("some_value").matches(""));
    }

    @Test
    public void shouldHandleIllegalRegexPatternForExpectationAndTest() {
        assertFalse(new RegexStringMatcher("/{}").matches("/{{}"));
        assertFalse(new RegexStringMatcher("/{}").matches("some_value"));
        assertFalse(new RegexStringMatcher("some_value").matches("/{}"));
    }

    @Test
    public void shouldHandleIllegalRegexPatternForExpectation() {
        assertFalse(new RegexStringMatcher("/{}").matches("some_value"));
    }

    @Test
    public void shouldHandleIllegalRegexPatternForTest() {
        assertFalse(new RegexStringMatcher("some_value").matches("/{}"));
    }
}
