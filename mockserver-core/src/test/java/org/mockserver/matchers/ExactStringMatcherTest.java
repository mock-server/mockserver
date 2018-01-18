package org.mockserver.matchers;

import org.junit.Test;
import org.mockserver.logging.MockServerLogger;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockserver.matchers.NotMatcher.not;
import static org.mockserver.model.NottableString.string;

/**
 * @author jamesdbloom
 */
public class ExactStringMatcherTest {

    @Test
    public void shouldMatchMatchingString() {
        assertTrue(new ExactStringMatcher(new MockServerLogger(), "some_value").matches("some_value"));
    }

    @Test
    public void shouldNotMatchMatchingString() {
        assertFalse(not(new ExactStringMatcher(new MockServerLogger(), "some_value")).matches("some_value"));
    }

    @Test
    public void shouldMatchNullExpectation() {
        assertTrue(new ExactStringMatcher(new MockServerLogger(), string(null)).matches("some_value"));
        assertTrue(new ExactStringMatcher(new MockServerLogger(), (String) null).matches("some_value"));
    }

    @Test
    public void shouldNotMatchNullExpectation() {
        assertFalse(not(new ExactStringMatcher(new MockServerLogger(), string(null))).matches("some_value"));
    }

    @Test
    public void shouldMatchEmptyExpectation() {
        assertTrue(new ExactStringMatcher(new MockServerLogger(), "").matches("some_value"));
    }

    @Test
    public void shouldNotMatchEmptyExpectation() {
        assertFalse(not(new ExactStringMatcher(new MockServerLogger(), "")).matches("some_value"));
    }

    @Test
    public void shouldNotMatchIncorrectString() {
        assertFalse(new ExactStringMatcher(new MockServerLogger(), "some_value").matches("not_matching"));
    }

    @Test
    public void shouldMatchIncorrectString() {
        assertTrue(not(new ExactStringMatcher(new MockServerLogger(), "some_value")).matches("not_matching"));
    }

    @Test
    public void shouldNotMatchNullTest() {
        assertFalse(new ExactStringMatcher(new MockServerLogger(), "some_value").matches(null, string(null)));
        assertFalse(new ExactStringMatcher(new MockServerLogger(), "some_value").matches((String) null));
    }

    @Test
    public void shouldMatchNullTest() {
        assertTrue(not(new ExactStringMatcher(new MockServerLogger(), "some_value")).matches(null, string(null)));
        assertTrue(not(new ExactStringMatcher(new MockServerLogger(), "some_value")).matches((String) null));
    }

    @Test
    public void shouldNotMatchEmptyTest() {
        assertFalse(new ExactStringMatcher(new MockServerLogger(), "some_value").matches(""));
    }

    @Test
    public void shouldMatchEmptyTest() {
        assertTrue(not(new ExactStringMatcher(new MockServerLogger(), "some_value")).matches(""));
    }
}
