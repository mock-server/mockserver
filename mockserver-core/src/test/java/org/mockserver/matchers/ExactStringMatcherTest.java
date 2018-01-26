package org.mockserver.matchers;

import org.junit.Test;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.NottableString;

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
        assertTrue(new ExactStringMatcher(new MockServerLogger(), "some_value").matches(null, "some_value"));
        assertTrue(new ExactStringMatcher(new MockServerLogger(), NottableString.not("some_value")).matches(null, NottableString.not("some_value")));
        assertTrue(not(new ExactStringMatcher(new MockServerLogger(), "some_value")).matches(null, NottableString.not("some_value")));
        assertTrue(not(new ExactStringMatcher(new MockServerLogger(), NottableString.not("some_value"))).matches(null, "some_value"));

        assertFalse(new ExactStringMatcher(new MockServerLogger(), "some_value").matches(null, NottableString.not("some_value")));
        assertFalse(not(new ExactStringMatcher(new MockServerLogger(), "some_value")).matches(null, "some_value"));
        assertFalse(new ExactStringMatcher(new MockServerLogger(), NottableString.not("some_value")).matches(null, "some_value"));
        assertFalse(not(new ExactStringMatcher(new MockServerLogger(), NottableString.not("some_value"))).matches(null, NottableString.not("some_value")));
    }

    @Test
    public void shouldMatchNotMatchingString() {
        assertFalse(new ExactStringMatcher(new MockServerLogger(), "some_value").matches(null, "some_other_value"));
        assertFalse(new ExactStringMatcher(new MockServerLogger(), NottableString.not("some_value")).matches(null, NottableString.not("some_other_value")));
        assertFalse(not(new ExactStringMatcher(new MockServerLogger(), "some_value")).matches(null, NottableString.not("some_other_value")));
        assertFalse(not(new ExactStringMatcher(new MockServerLogger(), NottableString.not("some_value"))).matches(null, "some_other_value"));

        assertTrue(new ExactStringMatcher(new MockServerLogger(), "some_value").matches(null, NottableString.not("some_other_value")));
        assertTrue(not(new ExactStringMatcher(new MockServerLogger(), "some_value")).matches(null, "some_other_value"));
        assertTrue(new ExactStringMatcher(new MockServerLogger(), NottableString.not("some_value")).matches(null, "some_other_value"));
        assertTrue(not(new ExactStringMatcher(new MockServerLogger(), NottableString.not("some_value"))).matches(null, NottableString.not("some_other_value")));
    }

    @Test
    public void shouldMatchNullMatcher() {
        assertTrue(new ExactStringMatcher(new MockServerLogger(), (String) null).matches(null, "some_value"));
        assertTrue(new ExactStringMatcher(new MockServerLogger(), string(null)).matches(null, "some_value"));
        assertTrue(new ExactStringMatcher(new MockServerLogger(), NottableString.not(null)).matches(null, NottableString.not("some_value")));
        assertTrue(not(new ExactStringMatcher(new MockServerLogger(), (String) null)).matches(null, NottableString.not("some_value")));
        assertTrue(not(new ExactStringMatcher(new MockServerLogger(), string(null))).matches(null, NottableString.not("some_value")));
        assertTrue(not(new ExactStringMatcher(new MockServerLogger(), NottableString.not(null))).matches(null, "some_value"));

        assertFalse(new ExactStringMatcher(new MockServerLogger(), (String) null).matches(null, NottableString.not("some_value")));
        assertFalse(new ExactStringMatcher(new MockServerLogger(), string(null)).matches(null, NottableString.not("some_value")));
        assertFalse(not(new ExactStringMatcher(new MockServerLogger(), (String) null)).matches(null, "some_value"));
        assertFalse(not(new ExactStringMatcher(new MockServerLogger(), string(null))).matches(null, "some_value"));
        assertFalse(new ExactStringMatcher(new MockServerLogger(), NottableString.not(null)).matches(null, "some_value"));
        assertFalse(not(new ExactStringMatcher(new MockServerLogger(), NottableString.not(null))).matches(null, NottableString.not("some_value")));
    }

    @Test
    public void shouldMatchNullMatched() {
        assertFalse(new ExactStringMatcher(new MockServerLogger(), "some_value").matches(null, (String) null));
        assertFalse(new ExactStringMatcher(new MockServerLogger(), "some_value").matches(null, string(null)));
        assertFalse(new ExactStringMatcher(new MockServerLogger(), NottableString.not("some_value")).matches(null, NottableString.not(null)));
        assertFalse(not(new ExactStringMatcher(new MockServerLogger(), "some_value")).matches(null, NottableString.not(null)));
        assertFalse(not(new ExactStringMatcher(new MockServerLogger(), NottableString.not("some_value"))).matches(null, (String) null));
        assertFalse(not(new ExactStringMatcher(new MockServerLogger(), NottableString.not("some_value"))).matches(null, string(null)));

        assertTrue(new ExactStringMatcher(new MockServerLogger(), "some_value").matches(null, NottableString.not(null)));
        assertTrue(not(new ExactStringMatcher(new MockServerLogger(), "some_value")).matches(null, (String) null));
        assertTrue(not(new ExactStringMatcher(new MockServerLogger(), "some_value")).matches(null, string(null)));
        assertTrue(new ExactStringMatcher(new MockServerLogger(), NottableString.not("some_value")).matches(null, (String) null));
        assertTrue(new ExactStringMatcher(new MockServerLogger(), NottableString.not("some_value")).matches(null, string(null)));
        assertTrue(not(new ExactStringMatcher(new MockServerLogger(), NottableString.not("some_value"))).matches(null, NottableString.not(null)));
    }

    @Test
    public void shouldMatchEmptyMatcher() {
        assertTrue(new ExactStringMatcher(new MockServerLogger(), "").matches(null, "some_value"));
        assertTrue(new ExactStringMatcher(new MockServerLogger(), string("")).matches(null, "some_value"));
        assertTrue(new ExactStringMatcher(new MockServerLogger(), NottableString.not("")).matches(null, NottableString.not("some_value")));
        assertTrue(not(new ExactStringMatcher(new MockServerLogger(), "")).matches(null, NottableString.not("some_value")));
        assertTrue(not(new ExactStringMatcher(new MockServerLogger(), string(""))).matches(null, NottableString.not("some_value")));
        assertTrue(not(new ExactStringMatcher(new MockServerLogger(), NottableString.not(""))).matches(null, "some_value"));

        assertFalse(new ExactStringMatcher(new MockServerLogger(), "").matches(null, NottableString.not("some_value")));
        assertFalse(new ExactStringMatcher(new MockServerLogger(), string("")).matches(null, NottableString.not("some_value")));
        assertFalse(not(new ExactStringMatcher(new MockServerLogger(), "")).matches(null, "some_value"));
        assertFalse(not(new ExactStringMatcher(new MockServerLogger(), string(""))).matches(null, "some_value"));
        assertFalse(new ExactStringMatcher(new MockServerLogger(), NottableString.not("")).matches(null, "some_value"));
        assertFalse(not(new ExactStringMatcher(new MockServerLogger(), NottableString.not(""))).matches(null, NottableString.not("some_value")));
    }

    @Test
    public void shouldMatchEmptyMatched() {
        assertFalse(new ExactStringMatcher(new MockServerLogger(), "some_value").matches(null, ""));
        assertFalse(new ExactStringMatcher(new MockServerLogger(), NottableString.not("some_value")).matches(null, NottableString.not("")));
        assertFalse(not(new ExactStringMatcher(new MockServerLogger(), "some_value")).matches(null, NottableString.not("")));
        assertFalse(not(new ExactStringMatcher(new MockServerLogger(), NottableString.not("some_value"))).matches(null, ""));

        assertTrue(new ExactStringMatcher(new MockServerLogger(), "some_value").matches(null, NottableString.not("")));
        assertTrue(not(new ExactStringMatcher(new MockServerLogger(), "some_value")).matches(null, ""));
        assertTrue(new ExactStringMatcher(new MockServerLogger(), NottableString.not("some_value")).matches(null, ""));
        assertTrue(not(new ExactStringMatcher(new MockServerLogger(), NottableString.not("some_value"))).matches(null, NottableString.not("")));
    }
}
