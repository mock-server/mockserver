package org.jamesdbloom.mockserver.matchers;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * @author jamesdbloom
 */
public class StringMatcherTest {

    @Test
    public void matchesMatchingString() {
        assertTrue(new StringMatcher("somepath").matches("somepath"));
    }

    @Test
    public void matchesMatchingRegex() {
        assertTrue(new StringMatcher("some[a-z]{4}").matches("somepath"));
    }

    @Test
    public void matchesNullExpectation() {
        assertTrue(new StringMatcher(null).matches("somepath"));
    }

    @Test
    public void doesNotMatchIncorrectString() {
        assertFalse(new StringMatcher("somepath").matches("pathsome"));
    }

    @Test
    public void doesNotMatchIncorrectRegex() {
        assertFalse(new StringMatcher("some[a-z]{3}").matches("pathsome"));
    }

    @Test
    public void doesNotMatchesNullTest() {
        assertFalse(new StringMatcher("somepath").matches(null));
    }
}
