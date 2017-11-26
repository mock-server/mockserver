package org.mockserver.matchers;

import org.junit.Test;

import static com.google.common.base.Charsets.UTF_8;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockserver.matchers.NotMatcher.not;

/**
 * @author jamesdbloom
 */
public class BinaryMatcherTest {

    @Test
    public void shouldMatchMatchingString() {
        assertTrue(new BinaryMatcher("some_value".getBytes(UTF_8)).matches("some_value".getBytes(UTF_8)));
    }

    @Test
    public void shouldNotMatchMatchingString() {
        assertFalse(not(new BinaryMatcher("some_value".getBytes(UTF_8))).matches("some_value".getBytes(UTF_8)));
    }

    @Test
    public void shouldMatchNullExpectation() {
        assertTrue(new BinaryMatcher(null).matches("some_value".getBytes(UTF_8)));
    }

    @Test
    public void shouldNotMatchNullExpectation() {
        assertFalse(not(new BinaryMatcher(null)).matches("some_value".getBytes(UTF_8)));
    }

    @Test
    public void shouldMatchEmptyExpectation() {
        assertTrue(new BinaryMatcher("".getBytes(UTF_8)).matches("some_value".getBytes(UTF_8)));
    }

    @Test
    public void shouldNotMatchEmptyExpectation() {
        assertFalse(not(new BinaryMatcher("".getBytes(UTF_8))).matches("some_value".getBytes(UTF_8)));
    }

    @Test
    public void shouldNotMatchIncorrectString() {
        assertFalse(new BinaryMatcher("some_value".getBytes(UTF_8)).matches("not_matching".getBytes(UTF_8)));
    }

    @Test
    public void shouldMatchIncorrectString() {
        assertTrue(not(new BinaryMatcher("some_value".getBytes(UTF_8))).matches("not_matching".getBytes(UTF_8)));
    }

    @Test
    public void shouldNotMatchNullTest() {
        assertFalse(new BinaryMatcher("some_value".getBytes(UTF_8)).matches(null));
    }

    @Test
    public void shouldMatchNullTest() {
        assertTrue(not(new BinaryMatcher("some_value".getBytes(UTF_8))).matches(null));
    }

    @Test
    public void shouldNotMatchEmptyTest() {
        assertFalse(new BinaryMatcher("some_value".getBytes(UTF_8)).matches("".getBytes(UTF_8)));
    }

    @Test
    public void shouldMatchEmptyTest() {
        assertTrue(not(new BinaryMatcher("some_value".getBytes(UTF_8))).matches("".getBytes(UTF_8)));
    }
}
