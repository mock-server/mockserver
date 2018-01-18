package org.mockserver.matchers;

import org.junit.Test;
import org.mockserver.logging.MockServerLogger;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class BooleanMatcherTest {

    @Test
    public void shouldMatchMatchingExpectations() {
        assertTrue(new BooleanMatcher(new MockServerLogger(),true).matches(null, true));
        assertTrue(new BooleanMatcher(new MockServerLogger(),false).matches(null, false));
    }

    @Test
    public void shouldMatchNullExpectations() {
        assertTrue(new BooleanMatcher(new MockServerLogger(),null).matches(null, null));
        assertTrue(new BooleanMatcher(new MockServerLogger(),null).matches(null, false));
    }

    @Test
    public void shouldNotMatchNonMatchingExpectations() {
        assertFalse(new BooleanMatcher(new MockServerLogger(),true).matches(null, false));
        assertFalse(new BooleanMatcher(new MockServerLogger(),false).matches(null, true));
    }

    @Test
    public void shouldNotMatchNullAgainstNonMatchingExpectations() {
        assertFalse(new BooleanMatcher(new MockServerLogger(),true).matches(null, null));
        assertFalse(new BooleanMatcher(new MockServerLogger(),false).matches(null, null));
    }

}
