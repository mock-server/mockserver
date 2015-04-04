package org.mockserver.matchers;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockserver.matchers.NotMatcher.not;

/**
 * @author jamesdbloom
 */
public class BinaryMatcherTest {

    @Test
    public void shouldMatchMatchingString() {
        assertTrue(new BinaryMatcher("some_value".getBytes()).matches("some_value".getBytes()));
    }

    @Test
    public void shouldNotMatchMatchingString() {
        assertFalse(not(new BinaryMatcher("some_value".getBytes())).matches("some_value".getBytes()));
    }

    @Test
    public void shouldMatchNullExpectation() {
        assertTrue(new BinaryMatcher(null).matches("some_value".getBytes()));
    }

    @Test
    public void shouldNotMatchNullExpectation() {
        assertFalse(not(new BinaryMatcher(null)).matches("some_value".getBytes()));
    }

    @Test
    public void shouldMatchEmptyExpectation() {
        assertTrue(new BinaryMatcher("".getBytes()).matches("some_value".getBytes()));
    }

    @Test
    public void shouldNotMatchEmptyExpectation() {
        assertFalse(not(new BinaryMatcher("".getBytes())).matches("some_value".getBytes()));
    }

    @Test
    public void shouldNotMatchIncorrectString() {
        assertFalse(new BinaryMatcher("some_value".getBytes()).matches("not_matching".getBytes()));
    }

    @Test
    public void shouldMatchIncorrectString() {
        assertTrue(not(new BinaryMatcher("some_value".getBytes())).matches("not_matching".getBytes()));
    }

    @Test
    public void shouldNotMatchNullTest() {
        assertFalse(new BinaryMatcher("some_value".getBytes()).matches(null));
    }

    @Test
    public void shouldMatchNullTest() {
        assertTrue(not(new BinaryMatcher("some_value".getBytes())).matches(null));
    }

    @Test
    public void shouldNotMatchEmptyTest() {
        assertFalse(new BinaryMatcher("some_value".getBytes()).matches("".getBytes()));
    }

    @Test
    public void shouldMatchEmptyTest() {
        assertTrue(not(new BinaryMatcher("some_value".getBytes())).matches("".getBytes()));
    }
}
