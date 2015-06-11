package org.mockserver.matchers;

import org.junit.Test;
import org.mockserver.model.Cookie;
import org.mockserver.model.KeyAndValue;
import org.mockserver.model.Not;

import java.util.ArrayList;
import java.util.Arrays;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockserver.matchers.NotMatcher.not;

/**
 * @author jamesdbloom
 */
public class CookieMatcherTestNew {

    @Test
    public void shouldMatchSingleCookieMatcherAndSingleMatchingCookie() {
        assertTrue(new HashMapMatcher(KeyAndValue.toHashMap(
                new Cookie("cookieOneName", "cookieOneValue")
        )).matches(new ArrayList<KeyAndValue>(Arrays.asList(
                new Cookie("cookieOneName", "cookieOneValue")
        ))));
    }

    @Test
    public void shouldNotMatchSingleCookieMatcherAndSingleNoneMatchingCookie() {
        assertFalse(new HashMapMatcher(KeyAndValue.toHashMap(
                new Cookie("cookieOneName", "cookieOneValue")
        )).matches(new ArrayList<KeyAndValue>(Arrays.asList(
                new Cookie("notCookieOneName", "cookieOneValue")
        ))));

        assertFalse(new HashMapMatcher(KeyAndValue.toHashMap(
                new Cookie("cookieOneName", "cookieOneValue")
        )).matches(new ArrayList<KeyAndValue>(Arrays.asList(
                new Cookie("cookieOneName", "notCookieOneValue")
        ))));
    }

    @Test
    public void shouldMatchMultipleCookieMatcherAndMultipleMatchingCookies() {
        assertTrue(new HashMapMatcher(KeyAndValue.toHashMap(
                new Cookie("cookieOneName", "cookieOneValue"),
                new Cookie("cookieTwoName", "cookieTwoValue")
        )).matches(new ArrayList<KeyAndValue>(Arrays.asList(
                new Cookie("cookieOneName", "cookieOneValue"),
                new Cookie("cookieTwoName", "cookieTwoValue")
        ))));

        assertTrue(new HashMapMatcher(KeyAndValue.toHashMap(
                new Cookie("cookie.*", "cookie.*")
        )).matches(new ArrayList<KeyAndValue>(Arrays.asList(
                new Cookie("cookieOneName", "cookieOneValue"),
                new Cookie("cookieTwoName", "cookieTwoValue")
        ))));
    }

    @Test
    public void shouldNotMatchMultipleCookieMatcherAndMultipleNoneMatchingCookiesWithOneMismatch() {
        assertFalse(new HashMapMatcher(KeyAndValue.toHashMap(
                new Cookie("cookieOneName", "cookieOneValue"),
                new Cookie("cookieTwoName", "cookieTwoValue")
        )).matches(new ArrayList<KeyAndValue>(Arrays.asList(
                new Cookie("notCookieOneName", "cookieOneValue"),
                new Cookie("cookieTwoName", "cookieTwoValue")
        ))));

        assertFalse(new HashMapMatcher(KeyAndValue.toHashMap(
                new Cookie("cookieOneName", "cookieOneValue"),
                new Cookie("cookieTwoName", "cookieTwoValue")
        )).matches(new ArrayList<KeyAndValue>(Arrays.asList(
                new Cookie("cookieOneName", "notCookieOneValue"),
                new Cookie("cookieTwoName", "cookieTwoValue")
        ))));
    }

    @Test
    public void shouldNotMatchMultipleCookieMatcherAndMultipleNoneMatchingCookiesWithMultipleMismatches() {
        assertFalse(new HashMapMatcher(KeyAndValue.toHashMap(
                new Cookie("cookieOneName", "cookieOneValue"),
                new Cookie("cookieTwoName", "cookieTwoValue")
        )).matches(new ArrayList<KeyAndValue>(Arrays.asList(
                new Cookie("notCookieOneName", "cookieOneValue"),
                new Cookie("notCookieTwoName", "cookieTwoValue")
        ))));

        assertFalse(new HashMapMatcher(KeyAndValue.toHashMap(
                new Cookie("cookieOneName", "cookieOneValue"),
                new Cookie("cookieTwoName", "cookieTwoValue")
        )).matches(new ArrayList<KeyAndValue>(Arrays.asList(
                new Cookie("cookieOneName", "notCookieOneValue"),
                new Cookie("cookieTwoName", "notCookieTwoValue")
        ))));

        assertFalse(new HashMapMatcher(KeyAndValue.toHashMap(
                new Cookie("cookie.*", "cookie.*")
        )).matches(new ArrayList<KeyAndValue>(Arrays.asList(
                new Cookie("notCookieOneName", "cookieOneValue"),
                new Cookie("notCookieTwoName", "cookieTwoValue")
        ))));

        assertFalse(new HashMapMatcher(KeyAndValue.toHashMap(
                new Cookie("cookie.*", "cookie.*")
        )).matches(new ArrayList<KeyAndValue>(Arrays.asList(
                new Cookie("cookieOneName", "notCookieOneValue"),
                new Cookie("cookieTwoName", "notCookieTwoValue")
        ))));
    }

    @Test
    public void shouldNotMatchMultipleCookieMatcherAndMultipleNotEnoughMatchingCookies() {
        assertFalse(new HashMapMatcher(KeyAndValue.toHashMap(
                new Cookie("cookieOneName", "cookieOneValue"),
                new Cookie("cookieTwoName", "cookieTwoValue")
        )).matches(new ArrayList<KeyAndValue>(Arrays.asList(
                new Cookie("cookieTwoName", "cookieTwoValue")
        ))));

        assertFalse(new HashMapMatcher(KeyAndValue.toHashMap(
                new Cookie("cookieOneName", "cookieOneValue"),
                new Cookie("cookieTwoName", "cookieTwoValue")
        )).matches(new ArrayList<KeyAndValue>(Arrays.asList(
                new Cookie("cookieOneName", "cookieOneValue")
        ))));
    }

}
