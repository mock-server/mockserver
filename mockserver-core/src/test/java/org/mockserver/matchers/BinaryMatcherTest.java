package org.mockserver.matchers;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * @author jamesdbloom
 */
public class BinaryMatcherTest {

    @Test
    public void shouldMatchMatchingString() {
        assertTrue(new BinaryMatcher("some_value".getBytes()).matches("some_value".getBytes()));
    }

    @Test
    public void shouldMatchNullExpectation() {
        assertTrue(new BinaryMatcher(null).matches("some_value".getBytes()));
    }

    @Test
    public void shouldMatchEmptyExpectation() {
        assertTrue(new BinaryMatcher("".getBytes()).matches("some_value".getBytes()));
    }

    @Test
    public void shouldNotMatchIncorrectString() {
        assertFalse(new BinaryMatcher("some_value".getBytes()).matches("not_matching".getBytes()));
    }

    @Test
    public void shouldNotMatchNullTest() {
        assertFalse(new BinaryMatcher("some_value".getBytes()).matches(null));
    }

    @Test
    public void shouldNotMatchEmptyTest() {
        assertFalse(new BinaryMatcher("some_value".getBytes()).matches("".getBytes()));
    }
}
