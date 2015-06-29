package org.mockserver.matchers;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class BooleanMatcherTest {

    @Test
    public void shouldMatchMatchingExpectations() {
        assertTrue(new BooleanMatcher(true).matches(true));
        assertTrue(new BooleanMatcher(false).matches(false));
    }

    @Test
    public void shouldMatchNullExpectations() {
        assertTrue(new BooleanMatcher(null).matches(null));
        assertTrue(new BooleanMatcher(null).matches(false));
    }

    @Test
    public void shouldNotMatchNonMatchingExpectations() {
        assertFalse(new BooleanMatcher(true).matches(false));
        assertFalse(new BooleanMatcher(false).matches(true));
    }

    @Test
    public void shouldNotMatchNullAgainstNonMatchingExpectations() {
        assertFalse(new BooleanMatcher(true).matches(null));
        assertFalse(new BooleanMatcher(false).matches(null));
    }

}