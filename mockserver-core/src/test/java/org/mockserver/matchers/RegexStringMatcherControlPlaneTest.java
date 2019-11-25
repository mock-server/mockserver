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
public class RegexStringMatcherControlPlaneTest {

    @Test
    public void shouldMatchMatchingString() {
        assertTrue(new RegexStringMatcher(new MockServerLogger(), string("some_value"), true).matches("some_value"));
    }

    @Test
    public void shouldMatchUnMatchingNottedString() {
        assertTrue(new RegexStringMatcher(new MockServerLogger(), string("some_value"), true).matches(null, NottableString.not("not_value")));
    }

    @Test
    public void shouldMatchUnMatchingNottedMatcher() {
        assertTrue(new RegexStringMatcher(new MockServerLogger(), NottableString.not("not_value"), true).matches("some_value"));
    }

    @Test
    public void shouldMatchUnMatchingNottedMatcherAndNottedString() {
        assertFalse(new RegexStringMatcher(new MockServerLogger(), NottableString.not("not_matcher"), true).matches(null, NottableString.not("not_value")));
    }

    @Test
    public void shouldNotMatchMatchingNottedString() {
        assertFalse(new RegexStringMatcher(new MockServerLogger(), string("some_value"), true).matches(null, NottableString.not("some_value")));
    }

    @Test
    public void shouldNotMatchMatchingNottedMatcher() {
        assertFalse(new RegexStringMatcher(new MockServerLogger(), NottableString.not("some_value"), true).matches("some_value"));
    }

    @Test
    public void shouldNotMatchMatchingNottedMatcherAndNottedString() {
        assertTrue(new RegexStringMatcher(new MockServerLogger(), NottableString.not("some_value"), true).matches(null, NottableString.not("some_value")));
    }

    @Test
    public void shouldNotMatchMatchingString() {
        assertFalse(not(new RegexStringMatcher(new MockServerLogger(), string("some_value"), true)).matches("some_value"));
    }

    @Test
    public void shouldMatchMatchingStringWithRegexSymbols() {
        assertTrue(new RegexStringMatcher(new MockServerLogger(), string("text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8"), true).matches("text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8"));
    }

    @Test
    public void shouldMatchMatchingRegex() {
        assertTrue(new RegexStringMatcher(new MockServerLogger(), string("some_[a-z]{5}"), true).matches("some_value"));
    }

    @Test
    public void shouldMatchNullExpectation() {
        assertTrue(new RegexStringMatcher(new MockServerLogger(), string(null), true).matches("some_value"));
    }

    @Test
    public void shouldMatchEmptyExpectation() {
        assertTrue(new RegexStringMatcher(new MockServerLogger(), string(""), true).matches("some_value"));
    }

    @Test
    public void shouldNotMatchIncorrectString() {
        assertFalse(new RegexStringMatcher(new MockServerLogger(), string("some_value"), true).matches("not_matching"));
    }

    @Test
    public void shouldMatchIncorrectString() {
        assertTrue(not(new RegexStringMatcher(new MockServerLogger(), string("some_value"), true)).matches("not_matching"));
    }

    @Test
    public void shouldNotMatchMatchingControlPlaneRegex() {
        assertTrue(new RegexStringMatcher(new MockServerLogger(), string("some_value"), true).matches("some_[a-z]{5}"));
    }

    @Test
    public void shouldNotMatchIncorrectStringWithRegexSymbols() {
        assertFalse(new RegexStringMatcher(new MockServerLogger(), string("text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8"), true).matches("text/html,application/xhtml+xml,application/xml;q=0.9;q=0.8"));
    }

    @Test
    public void shouldNotMatchIncorrectRegex() {
        assertFalse(new RegexStringMatcher(new MockServerLogger(), string("some_[a-z]{4}"), true).matches("some_value"));
    }

    @Test
    public void shouldNotMatchNullTest() {
        assertFalse(new RegexStringMatcher(new MockServerLogger(), string("some_value"), true).matches(null, string(null)));
    }

    @Test
    public void shouldNotMatchEmptyTest() {
        assertFalse(new RegexStringMatcher(new MockServerLogger(), string("some_value"), true).matches(""));
    }

    @Test
    public void shouldHandleIllegalRegexPatternForExpectationAndTest() {
        assertFalse(new RegexStringMatcher(new MockServerLogger(), string("/{}"), true).matches("/{{}"));
        assertFalse(new RegexStringMatcher(new MockServerLogger(), string("/{}"), true).matches("some_value"));
        assertFalse(new RegexStringMatcher(new MockServerLogger(), string("some_value"), true).matches("/{}"));
    }

    @Test
    public void shouldHandleIllegalRegexPatternForExpectation() {
        assertFalse(new RegexStringMatcher(new MockServerLogger(), string("/{}"), true).matches("some_value"));
    }

    @Test
    public void shouldHandleIllegalRegexPatternForTest() {
        assertFalse(new RegexStringMatcher(new MockServerLogger(), string("some_value"), true).matches("/{}"));
    }
}
