package org.mockserver.matchers;

import org.junit.Test;

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
        assertTrue(new SubStringMatcher("some_value").matches("some_value"));
    }

    @Test
    public void shouldMatchMatchingSubString() {
        assertTrue(new SubStringMatcher("value").matches("some_value"));
        assertTrue(new SubStringMatcher("some").matches("some_value"));
        assertTrue(new SubStringMatcher("e_v").matches("some_value"));
    }

    @Test
    public void shouldNotMatchMatchingString() {
        assertFalse(not(new SubStringMatcher("some_value")).matches("some_value"));
    }

    @Test
    public void shouldNotMatchMatchingSubString() {
        assertFalse(not(new SubStringMatcher("value")).matches("some_value"));
        assertFalse(not(new SubStringMatcher("some")).matches("some_value"));
        assertFalse(not(new SubStringMatcher("e_v")).matches("some_value"));
    }

    @Test
    public void shouldMatchNullExpectation() {
        assertTrue(new SubStringMatcher(string(null)).matches("some_value"));
        assertTrue(new SubStringMatcher((String) null).matches("some_value"));
    }

    @Test
    public void shouldNotMatchNullExpectation() {
        assertFalse(not(new SubStringMatcher(string(null))).matches("some_value"));
    }

    @Test
    public void shouldMatchEmptyExpectation() {
        assertTrue(new SubStringMatcher("").matches("some_value"));
    }

    @Test
    public void shouldNotMatchEmptyExpectation() {
        assertFalse(not(new SubStringMatcher("")).matches("some_value"));
    }

    @Test
    public void shouldNotMatchIncorrectString() {
        assertFalse(new SubStringMatcher("some_value").matches("not_matching"));
    }

    @Test
    public void shouldNotMatchIncorrectSubString() {
        assertFalse(new SubStringMatcher("matching_not").matches("not_matching"));
    }

    @Test
    public void shouldMatchIncorrectString() {
        assertTrue(not(new SubStringMatcher("some_value")).matches("not_matching"));
    }

    @Test
    public void shouldNotMatchNullTest() {
        assertFalse(new SubStringMatcher("some_value").matches(string(null)));
        assertFalse(new SubStringMatcher("some_value").matches((String) null));
    }

    @Test
    public void shouldMatchNullTest() {
        assertTrue(not(new SubStringMatcher("some_value")).matches(string(null)));
        assertTrue(not(new SubStringMatcher("some_value")).matches((String) null));
    }

    @Test
    public void shouldNotMatchEmptyTest() {
        assertFalse(new SubStringMatcher("some_value").matches(""));
    }

    @Test
    public void shouldMatchEmptyTest() {
        assertTrue(not(new SubStringMatcher("some_value")).matches(""));
    }
}
