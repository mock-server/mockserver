package org.mockserver.matchers;

import org.junit.Test;
import org.mockserver.logging.MockServerLogger;

import static java.nio.charset.StandardCharsets.UTF_8;
import static junit.framework.TestCase.assertFalse;
import static junit.framework.TestCase.assertTrue;
import static org.mockserver.matchers.NotMatcher.notMatcher;

/**
 * @author jamesdbloom
 */
public class BinaryMatcherTest {

    @Test
    public void shouldMatchMatchingString() {
        assertTrue(new BinaryMatcher(new MockServerLogger(), "some_value".getBytes(UTF_8)).matches(null, "some_value".getBytes(UTF_8)));
    }

    @Test
    public void shouldNotMatchMatchingString() {
        assertFalse(notMatcher(new BinaryMatcher(new MockServerLogger(), "some_value".getBytes(UTF_8))).matches(null, "some_value".getBytes(UTF_8)));
    }

    @Test
    public void shouldMatchNullExpectation() {
        assertTrue(new BinaryMatcher(new MockServerLogger(), null).matches(null, "some_value".getBytes(UTF_8)));
    }

    @Test
    public void shouldNotMatchNullExpectation() {
        assertFalse(notMatcher(new BinaryMatcher(new MockServerLogger(), null)).matches(null, "some_value".getBytes(UTF_8)));
    }

    @Test
    public void shouldMatchEmptyExpectation() {
        assertTrue(new BinaryMatcher(new MockServerLogger(), "".getBytes(UTF_8)).matches(null, "some_value".getBytes(UTF_8)));
    }

    @Test
    public void shouldNotMatchEmptyExpectation() {
        assertFalse(notMatcher(new BinaryMatcher(new MockServerLogger(), "".getBytes(UTF_8))).matches(null, "some_value".getBytes(UTF_8)));
    }

    @Test
    public void shouldNotMatchIncorrectString() {
        assertFalse(new BinaryMatcher(new MockServerLogger(), "some_value".getBytes(UTF_8)).matches(null, "not_matching".getBytes(UTF_8)));
    }

    @Test
    public void shouldMatchIncorrectString() {
        assertTrue(notMatcher(new BinaryMatcher(new MockServerLogger(), "some_value".getBytes(UTF_8))).matches(null, "not_matching".getBytes(UTF_8)));
    }

    @Test
    public void shouldNotMatchNullTest() {
        assertFalse(new BinaryMatcher(new MockServerLogger(), "some_value".getBytes(UTF_8)).matches(null, null));
    }

    @Test
    public void shouldMatchNullTest() {
        assertTrue(notMatcher(new BinaryMatcher(new MockServerLogger(), "some_value".getBytes(UTF_8))).matches(null, null));
    }

    @Test
    public void shouldNotMatchEmptyTest() {
        assertFalse(new BinaryMatcher(new MockServerLogger(), "some_value".getBytes(UTF_8)).matches(null, "".getBytes(UTF_8)));
    }

    @Test
    public void shouldMatchEmptyTest() {
        assertTrue(notMatcher(new BinaryMatcher(new MockServerLogger(), "some_value".getBytes(UTF_8))).matches(null, "".getBytes(UTF_8)));
    }
}
