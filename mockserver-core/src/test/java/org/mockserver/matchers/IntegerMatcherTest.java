package org.mockserver.matchers;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * @author jamesdbloom
 */
public class IntegerMatcherTest {

    @Test
    public void shouldMatchIdenticalIntegers() {
        assertTrue(new IntegerMatcher(1).matches(1));
        assertTrue(new IntegerMatcher(null).matches(1));
        assertTrue(new IntegerMatcher(null).matches(null));
    }

    @Test
    public void shouldNotMatchDifferentIntegers() {
        assertFalse(new IntegerMatcher(2).matches(1));
        assertFalse(new IntegerMatcher(1).matches(null));
    }
}
