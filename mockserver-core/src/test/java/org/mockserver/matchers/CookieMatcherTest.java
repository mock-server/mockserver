package org.mockserver.matchers;

import org.junit.Test;
import org.mockserver.model.Cookie;
import org.mockserver.model.KeyAndValue;
import org.mockserver.model.KeyAndValue;

import java.util.ArrayList;
import java.util.Arrays;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockserver.matchers.NotMatcher.not;

/**
 * @author jamesdbloom
 */
public class CookieMatcherTest {

    @Test
    public void shouldMatchMatchingString() {
        assertTrue(new HashMapMatcher(KeyAndValue.toHashMap(
                new Cookie("cookieOneName", "cookieOneValue"),
                new Cookie("cookieTwoName", "cookieTwoValue")
        )).matches(new ArrayList<KeyAndValue>(Arrays.asList(
                new Cookie("cookieOneName", "cookieOneValue"),
                new Cookie("cookieTwoName", "cookieTwoValue")
        ))));
    }

    @Test
    public void shouldNotMatchMatchingStringWhenNotApplied() {
        assertFalse(not(new HashMapMatcher(KeyAndValue.toHashMap(
                new Cookie("cookieOneName", "cookieOneValue"),
                new Cookie("cookieTwoName", "cookieTwoValue")
        ))).matches(new ArrayList<KeyAndValue>(Arrays.asList(
                new Cookie("cookieOneName", "cookieOneValue"),
                new Cookie("cookieTwoName", "cookieTwoValue")
        ))));
    }

    @Test
    public void shouldMatchMatchingStringWithNotCookieAndNormalCookie() {
        assertTrue(new HashMapMatcher(KeyAndValue.toHashMap(
                new Cookie("cookieOneName", "cookieOneValue"),
                org.mockserver.model.Not.not(new Cookie("cookieThree", "cookieThreeValueOne"))
        )).matches(new ArrayList<KeyAndValue>(Arrays.asList(
                new Cookie("cookieOneName", "cookieOneValue"),
                new Cookie("cookieTwoName", "cookieTwoValue")
        ))));
    }

    @Test
    public void shouldMatchMatchingStringWithOnlyCookie() {
        assertTrue(new HashMapMatcher(KeyAndValue.toHashMap(
                org.mockserver.model.Not.not(new Cookie("cookieThree", "cookieThreeValueOne"))
        )).matches(new ArrayList<KeyAndValue>(Arrays.asList(
                new Cookie("cookieOneName", "cookieOneValue"),
                new Cookie("cookieTwoName", "cookieTwoValue")
        ))));
    }

    @Test
    public void shouldMatchMatchingStringWithOnlyCookieForEmptyList() {
        assertTrue(new HashMapMatcher(KeyAndValue.toHashMap(
                org.mockserver.model.Not.not(new Cookie("cookieThree", "cookieThreeValueOne"))
        )).matches(new ArrayList<KeyAndValue>()));
    }

    @Test
    public void shouldNotMatchMatchingStringWithNotCookieAndNormalCookie() {
        assertFalse(new HashMapMatcher(KeyAndValue.toHashMap(
                new Cookie("cookieOneName", "cookieOneValue"),
                org.mockserver.model.Not.not(new Cookie("cookieTwoName", "cookieTwoValue")
                ))).matches(new ArrayList<KeyAndValue>(Arrays.asList(
                new Cookie("cookieOneName", "cookieOneValue"),
                new Cookie("cookieTwoName", "cookieTwoValue")
        ))));
    }

    @Test
    public void shouldNotMatchMatchingStringWithOnlyNotCookie() {
        assertFalse(new HashMapMatcher(KeyAndValue.toHashMap(
                org.mockserver.model.Not.not(new Cookie("cookieTwoName", "cookieTwoValue")
                ))).matches(new ArrayList<KeyAndValue>(Arrays.asList(
                new Cookie("cookieOneName", "cookieOneValue"),
                new Cookie("cookieTwoName", "cookieTwoValue")
        ))));
    }

    @Test
    public void shouldNotMatchMatchingStringWithOnlyNotCookieForBodyWithSingleCookie() {
        assertFalse(new HashMapMatcher(KeyAndValue.toHashMap(
                org.mockserver.model.Not.not(new Cookie("cookieTwoName", "cookieTwoValue")
                ))).matches(new ArrayList<KeyAndValue>(Arrays.asList(
                new Cookie("cookieTwoName", "cookieTwoValue")
        ))));
    }

    @Test
    public void shouldMatchNullExpectation() {
        assertTrue(new HashMapMatcher(null).matches(new ArrayList<KeyAndValue>(Arrays.asList(
                new Cookie("cookieOneName", "cookieOneValue"),
                new Cookie("cookieTwoName", "cookieTwoValue")
        ))));
    }

    @Test
    public void shouldNotMatchNullExpectationWhenNotApplied() {
        assertFalse(not(new HashMapMatcher(null)).matches(new ArrayList<KeyAndValue>(Arrays.asList(
                new Cookie("cookieOneName", "cookieOneValue"),
                new Cookie("cookieTwoName", "cookieTwoValue")
        ))));
    }

    @Test
    public void shouldMatchEmptyExpectation() {
        assertTrue(new HashMapMatcher(KeyAndValue.toHashMap()).matches(new ArrayList<KeyAndValue>(Arrays.asList(
                new Cookie("cookieOneName", "cookieOneValue"),
                new Cookie("cookieTwoName", "cookieTwoValue")
        ))));
    }

    @Test
    public void shouldNotMatchEmptyExpectationWhenNotApplied() {
        assertFalse(not(new HashMapMatcher(KeyAndValue.toHashMap())).matches(new ArrayList<KeyAndValue>(Arrays.asList(
                new Cookie("cookieOneName", "cookieOneValue"),
                new Cookie("cookieTwoName", "cookieTwoValue")
        ))));
    }

    @Test
    public void shouldNotMatchIncorrectCookieName() {
        assertFalse(new HashMapMatcher(KeyAndValue.toHashMap(
                new Cookie("cookieOneName", "cookieOneValue"),
                new Cookie("cookieTwoName", "cookieTwoValue")
        )).matches(new ArrayList<KeyAndValue>(Arrays.asList(
                new Cookie("cookieOneName", "cookieOneValue"),
                new Cookie("INCORRECTcookieTwoName", "cookieTwoValue")
        ))));
    }

    @Test
    public void shouldMatchIncorrectCookieNameWhenNotApplied() {
        assertTrue(not(new HashMapMatcher(KeyAndValue.toHashMap(
                new Cookie("cookieOneName", "cookieOneValue"),
                new Cookie("cookieTwoName", "cookieTwoValue")
        ))).matches(new ArrayList<KeyAndValue>(Arrays.asList(
                new Cookie("cookieOneName", "cookieOneValue"),
                new Cookie("INCORRECTcookieTwoName", "cookieTwoValue")
        ))));
    }

    @Test
    public void shouldNotMatchIncorrectCookieValue() {
        assertFalse(new HashMapMatcher(KeyAndValue.toHashMap(
                new Cookie("cookieOneName", "cookieOneValue"),
                new Cookie("cookieTwoName", "cookieTwoValue")
        )).matches(new ArrayList<KeyAndValue>(Arrays.asList(
                new Cookie("cookieOneName", "cookieOneValue"),
                new Cookie("cookieTwoName", "INCORRECTcookieTwoValue")
        ))));
    }

    @Test
    public void shouldMatchIncorrectCookieValueWhenNotApplied() {
        assertTrue(not(new HashMapMatcher(KeyAndValue.toHashMap(
                new Cookie("cookieOneName", "cookieOneValue"),
                new Cookie("cookieTwoName", "cookieTwoValue")
        ))).matches(new ArrayList<KeyAndValue>(Arrays.asList(
                new Cookie("cookieOneName", "cookieOneValue"),
                new Cookie("cookieTwoName", "INCORRECTcookieTwoValue")
        ))));
    }

    @Test
    public void shouldNotMatchIncorrectCookieNameAndValue() {
        assertFalse(new HashMapMatcher(KeyAndValue.toHashMap(
                new Cookie("cookieOneName", "cookieOneValue"),
                new Cookie("cookieTwoName", "cookieTwoValue")
        )).matches(new ArrayList<KeyAndValue>(Arrays.asList(
                new Cookie("cookieOneName", "cookieOneValue"),
                new Cookie("INCORRECTcookieTwoName", "INCORRECTcookieTwoValue")
        ))));
    }

    @Test
    public void shouldMatchIncorrectCookieNameAndValueWhenNotApplied() {
        assertTrue(not(new HashMapMatcher(KeyAndValue.toHashMap(
                new Cookie("cookieOneName", "cookieOneValue"),
                new Cookie("cookieTwoName", "cookieTwoValue")
        ))).matches(new ArrayList<KeyAndValue>(Arrays.asList(
                new Cookie("cookieOneName", "cookieOneValue"),
                new Cookie("INCORRECTcookieTwoName", "INCORRECTcookieTwoValue")
        ))));
    }

    @Test
    public void shouldNotMatchNullCookieValue() {
        assertFalse(new HashMapMatcher(KeyAndValue.toHashMap(
                new Cookie("cookieOneName", "cookieOneValue"),
                new Cookie("cookieTwoName", "cookieTwoValue")
        )).matches(new ArrayList<KeyAndValue>(Arrays.asList(
                new Cookie("cookieOneName", "cookieOneValue"),
                new Cookie("cookieTwoName", null)
        ))));
    }

    @Test
    public void shouldMatchNullCookieValueWhenNotApplied() {
        assertTrue(not(new HashMapMatcher(KeyAndValue.toHashMap(
                new Cookie("cookieOneName", "cookieOneValue"),
                new Cookie("cookieTwoName", "cookieTwoValue")
        ))).matches(new ArrayList<KeyAndValue>(Arrays.asList(
                new Cookie("cookieOneName", "cookieOneValue"),
                new Cookie("cookieTwoName", null)
        ))));
    }

    @Test
    public void shouldMatchNullCookieValueInExpectation() {
        assertTrue(new HashMapMatcher(KeyAndValue.toHashMap(
                new Cookie("cookieOneName", "cookieOneValue"),
                new Cookie("cookieTwoName", "")
        )).matches(new ArrayList<KeyAndValue>(Arrays.asList(
                new Cookie("cookieOneName", "cookieOneValue"),
                new Cookie("cookieTwoName", "cookieTwoValue")
        ))));
    }

    @Test
    public void shouldNotMatchMissingCookie() {
        assertFalse(new HashMapMatcher(KeyAndValue.toHashMap(
                new Cookie("cookieOneName", "cookieOneValue"),
                new Cookie("cookieTwoName", "cookieTwoValue")
        )).matches(new ArrayList<KeyAndValue>(Arrays.asList(
                new Cookie("cookieOneName", "cookieOneValue")
        ))));
    }

    @Test
    public void shouldMatchMissingCookieWhenNotApplied() {
        assertTrue(not(new HashMapMatcher(KeyAndValue.toHashMap(
                new Cookie("cookieOneName", "cookieOneValue"),
                new Cookie("cookieTwoName", "cookieTwoValue")
        ))).matches(new ArrayList<KeyAndValue>(Arrays.asList(
                new Cookie("cookieOneName", "cookieOneValue")
        ))));
    }

    @Test
    public void shouldMatchNullTest() {
        assertTrue(new HashMapMatcher(KeyAndValue.toHashMap()).matches(null));
    }

    @Test
    public void shouldNotMatchNullTestWhenNotApplied() {
        assertFalse(not(new HashMapMatcher(KeyAndValue.toHashMap())).matches(null));
    }

    @Test
    public void shouldMatchEmptyTest() {
        assertTrue(new HashMapMatcher(KeyAndValue.toHashMap()).matches(new ArrayList<KeyAndValue>()));
    }

    @Test
    public void shouldNotMatchEmptyTestWhenNotApplied() {
        assertFalse(not(new HashMapMatcher(KeyAndValue.toHashMap())).matches(new ArrayList<KeyAndValue>()));
    }
}
