package org.mockserver.matchers;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * @author jamesdbloom
 */
public class ExactStringMatcherTest {

    @Test
    public void shouldMatchMatchingString() {
        assertTrue(new ExactStringMatcher("some_value").matches("some_value"));
    }

    @Test
    public void shouldMatchNullExpectation() {
        assertTrue(new ExactStringMatcher(null).matches("some_value"));
    }

    @Test
    public void shouldMatchEmptyExpectation() {
        assertTrue(new ExactStringMatcher("").matches("some_value"));
    }

    @Test
    public void shouldNotMatchIncorrectString() {
        assertFalse(new ExactStringMatcher("some_value").matches("not_matching"));
    }

    @Test
    public void shouldNotMatchNullTest() {
        assertFalse(new ExactStringMatcher("some_value").matches(null));
    }

    @Test
    public void shouldNotMatchEmptyTest() {
        assertFalse(new ExactStringMatcher("some_value").matches(""));
    }
}
