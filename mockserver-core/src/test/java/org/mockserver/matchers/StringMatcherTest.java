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
