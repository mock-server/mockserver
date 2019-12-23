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
public class SubStringMatcherTest {

    @Test
    public void shouldMatchMatchingString() {
        assertTrue(new SubStringMatcher(new MockServerLogger(), string("me_val")).matches(null, "some_value"));
        assertTrue(new SubStringMatcher(new MockServerLogger(), NottableString.not("me_val")).matches(null, NottableString.not("some_value")));
        assertTrue(not(new SubStringMatcher(new MockServerLogger(), string("me_val"))).matches(null, NottableString.not("some_value")));
        assertTrue(not(new SubStringMatcher(new MockServerLogger(), NottableString.not("me_val"))).matches(null, "some_value"));

        assertFalse(new SubStringMatcher(new MockServerLogger(), string("me_val")).matches(null, NottableString.not("some_value")));
        assertFalse(not(new SubStringMatcher(new MockServerLogger(), string("me_val"))).matches(null, "some_value"));
        assertFalse(new SubStringMatcher(new MockServerLogger(), NottableString.not("me_val")).matches(null, "some_value"));
        assertFalse(not(new SubStringMatcher(new MockServerLogger(), NottableString.not("me_val"))).matches(null, NottableString.not("some_value")));
    }

    @Test
    public void shouldMatchNotMatchingString() {
        assertFalse(new SubStringMatcher(new MockServerLogger(), string("me_val")).matches(null, "some_other_value"));
        assertFalse(new SubStringMatcher(new MockServerLogger(), NottableString.not("me_val")).matches(null, NottableString.not("some_other_value")));
        assertFalse(not(new SubStringMatcher(new MockServerLogger(), string("me_val"))).matches(null, NottableString.not("some_other_value")));
        assertFalse(not(new SubStringMatcher(new MockServerLogger(), NottableString.not("me_val"))).matches(null, "some_other_value"));

        assertTrue(new SubStringMatcher(new MockServerLogger(), string("me_val")).matches(null, NottableString.not("some_other_value")));
        assertTrue(not(new SubStringMatcher(new MockServerLogger(), string("me_val"))).matches(null, "some_other_value"));
        assertTrue(new SubStringMatcher(new MockServerLogger(), NottableString.not("me_val")).matches(null, "some_other_value"));
        assertTrue(not(new SubStringMatcher(new MockServerLogger(), NottableString.not("me_val"))).matches(null, NottableString.not("some_other_value")));
    }

    @Test
    public void shouldMatchNullMatcher() {
        assertTrue(new SubStringMatcher(new MockServerLogger(), string(null)).matches(null, "some_value"));
        assertTrue(new SubStringMatcher(new MockServerLogger(), string(null)).matches(null, "some_value"));
        assertTrue(new SubStringMatcher(new MockServerLogger(), NottableString.not(null)).matches(null, NottableString.not("some_value")));
        assertTrue(not(new SubStringMatcher(new MockServerLogger(), string(null))).matches(null, NottableString.not("some_value")));
        assertTrue(not(new SubStringMatcher(new MockServerLogger(), string(null))).matches(null, NottableString.not("some_value")));
        assertTrue(not(new SubStringMatcher(new MockServerLogger(), NottableString.not(null))).matches(null, "some_value"));

        assertFalse(new SubStringMatcher(new MockServerLogger(), string(null)).matches(null, NottableString.not("some_value")));
        assertFalse(new SubStringMatcher(new MockServerLogger(), string(null)).matches(null, NottableString.not("some_value")));
        assertFalse(not(new SubStringMatcher(new MockServerLogger(), string(null))).matches(null, "some_value"));
        assertFalse(not(new SubStringMatcher(new MockServerLogger(), string(null))).matches(null, "some_value"));
        assertFalse(new SubStringMatcher(new MockServerLogger(), NottableString.not(null)).matches(null, "some_value"));
        assertFalse(not(new SubStringMatcher(new MockServerLogger(), NottableString.not(null))).matches(null, NottableString.not("some_value")));
    }

    @Test
    public void shouldMatchNullMatched() {
        assertFalse(new SubStringMatcher(new MockServerLogger(), string("me_val")).matches(null, (String) null));
        assertFalse(new SubStringMatcher(new MockServerLogger(), string("me_val")).matches(null, string(null)));
        assertFalse(new SubStringMatcher(new MockServerLogger(), NottableString.not("me_val")).matches(null, NottableString.not(null)));
        assertFalse(not(new SubStringMatcher(new MockServerLogger(), string("me_val"))).matches(null, NottableString.not(null)));
        assertFalse(not(new SubStringMatcher(new MockServerLogger(), NottableString.not("me_val"))).matches(null, (String) null));
        assertFalse(not(new SubStringMatcher(new MockServerLogger(), NottableString.not("me_val"))).matches(null, string(null)));

        assertTrue(new SubStringMatcher(new MockServerLogger(), string("me_val")).matches(null, NottableString.not(null)));
        assertTrue(not(new SubStringMatcher(new MockServerLogger(), string("me_val"))).matches(null, (String) null));
        assertTrue(not(new SubStringMatcher(new MockServerLogger(), string("me_val"))).matches(null, string(null)));
        assertTrue(new SubStringMatcher(new MockServerLogger(), NottableString.not("me_val")).matches(null, (String) null));
        assertTrue(new SubStringMatcher(new MockServerLogger(), NottableString.not("me_val")).matches(null, string(null)));
        assertTrue(not(new SubStringMatcher(new MockServerLogger(), NottableString.not("me_val"))).matches(null, NottableString.not(null)));
    }

    @Test
    public void shouldMatchEmptyMatcher() {
        assertTrue(new SubStringMatcher(new MockServerLogger(), string("")).matches(null, "some_value"));
        assertTrue(new SubStringMatcher(new MockServerLogger(), string("")).matches(null, "some_value"));
        assertTrue(new SubStringMatcher(new MockServerLogger(), NottableString.not("")).matches(null, NottableString.not("some_value")));
        assertTrue(not(new SubStringMatcher(new MockServerLogger(), string(""))).matches(null, NottableString.not("some_value")));
        assertTrue(not(new SubStringMatcher(new MockServerLogger(), string(""))).matches(null, NottableString.not("some_value")));
        assertTrue(not(new SubStringMatcher(new MockServerLogger(), NottableString.not(""))).matches(null, "some_value"));

        assertFalse(new SubStringMatcher(new MockServerLogger(), string("")).matches(null, NottableString.not("some_value")));
        assertFalse(new SubStringMatcher(new MockServerLogger(), string("")).matches(null, NottableString.not("some_value")));
        assertFalse(not(new SubStringMatcher(new MockServerLogger(), string(""))).matches(null, "some_value"));
        assertFalse(not(new SubStringMatcher(new MockServerLogger(), string(""))).matches(null, "some_value"));
        assertFalse(new SubStringMatcher(new MockServerLogger(), NottableString.not("")).matches(null, "some_value"));
        assertFalse(not(new SubStringMatcher(new MockServerLogger(), NottableString.not(""))).matches(null, NottableString.not("some_value")));
    }

    @Test
    public void shouldMatchEmptyMatched() {
        assertFalse(new SubStringMatcher(new MockServerLogger(), string("me_val")).matches(null, ""));
        assertFalse(new SubStringMatcher(new MockServerLogger(), NottableString.not("me_val")).matches(null, NottableString.not("")));
        assertFalse(not(new SubStringMatcher(new MockServerLogger(), string("me_val"))).matches(null, NottableString.not("")));
        assertFalse(not(new SubStringMatcher(new MockServerLogger(), NottableString.not("me_val"))).matches(null, ""));

        assertTrue(new SubStringMatcher(new MockServerLogger(), string("me_val")).matches(null, NottableString.not("")));
        assertTrue(not(new SubStringMatcher(new MockServerLogger(), string("me_val"))).matches(null, ""));
        assertTrue(new SubStringMatcher(new MockServerLogger(), NottableString.not("me_val")).matches(null, ""));
        assertTrue(not(new SubStringMatcher(new MockServerLogger(), NottableString.not("me_val"))).matches(null, NottableString.not("")));
    }
}
