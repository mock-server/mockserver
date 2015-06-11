package org.mockserver.matchers;

import org.junit.Test;
import org.mockserver.model.Cookie;
import org.mockserver.model.KeyAndValue;

import java.util.ArrayList;
import java.util.Arrays;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockserver.model.NottableString.not;
import static org.mockserver.model.NottableString.string;

/**
 * @author jamesdbloom
 */
public class CookieMatcherTest {

    @Test
    public void shouldMatchMatchingCookie() {
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
    public void shouldNotMatchMatchingCookieWhenNotAppliedToMatcher() {
        // given
        assertTrue(new HashMapMatcher(KeyAndValue.toHashMap(
                new Cookie("cookieOneName", "cookieOneValue"),
                new Cookie("cookieTwoName", "cookieTwoValue")
        )).matches(new ArrayList<KeyAndValue>(Arrays.asList(
                new Cookie("cookieOneName", "cookieOneValue"),
                new Cookie("cookieTwoName", "cookieTwoValue")
        ))));

        // then - not matcher
        assertFalse(NotMatcher.not(new HashMapMatcher(KeyAndValue.toHashMap(
                new Cookie("cookieOneName", "cookieOneValue"),
                new Cookie("cookieTwoName", "cookieTwoValue")
        ))).matches(new ArrayList<KeyAndValue>(Arrays.asList(
                new Cookie("cookieOneName", "cookieOneValue"),
                new Cookie("cookieTwoName", "cookieTwoValue")
        ))));

        // and - not cookie
        assertFalse(new HashMapMatcher(KeyAndValue.toHashMap(
                new Cookie("cookieOneName", "cookieOneValue"),
                new Cookie(not("cookie.*Name"), not("cookie.*Value"))
        )).matches(new ArrayList<KeyAndValue>(Arrays.asList(
                new Cookie("cookieOneName", "cookieOneValue"),
                new Cookie("cookieTwoName", "cookieTwoValue")
        ))));

        // and - not matcher and not cookie
        assertTrue(NotMatcher.not(new HashMapMatcher(KeyAndValue.toHashMap(
                new Cookie("cookieOneName", "cookieOneValue"),
                new Cookie(not("cookie.*Name"), not("cookie.*Value"))
        ))).matches(new ArrayList<KeyAndValue>(Arrays.asList(
                new Cookie("cookieOneName", "cookieOneValue"),
                new Cookie("cookieTwoName", "cookieTwoValue")
        ))));
    }

    @Test
    public void shouldMatchMatchingCookieWithNotCookieAndNormalCookie() {
        // not matching cookie
        assertFalse(new HashMapMatcher(KeyAndValue.toHashMap(
                new Cookie("cookieOneName", "cookieOneValue"),
                new Cookie(not("cookie.*Name"), not("cookie.*Value"))
        )).matches(new ArrayList<KeyAndValue>(Arrays.asList(
                new Cookie("cookieOneName", "cookieOneValue"),
                new Cookie("cookieTwoName", "cookieTwoValue")
        ))));

        // not extra cookie
        assertFalse(new HashMapMatcher(KeyAndValue.toHashMap(
                new Cookie("cookieOneName", "cookieOneValue"),
                new Cookie("cookieTwoName", "cookieTwoValue"),
                new Cookie(not("cookie.*Name"), not("cookie.*Value"))
        )).matches(new ArrayList<KeyAndValue>(Arrays.asList(
                new Cookie("cookieOneName", "cookieOneValue"),
                new Cookie("cookieTwoName", "cookieTwoValue")
        ))));

        // not extra cookie
        assertTrue(new HashMapMatcher(KeyAndValue.toHashMap(
                new Cookie("cookieOneName", "cookieOneValue"),
                new Cookie("cookieTwoName", "cookieTwoValue"),
                new Cookie(not("cookieThreeName"), not("cookieThreeValue"))
        )).matches(new ArrayList<KeyAndValue>(Arrays.asList(
                new Cookie("cookieOneName", "cookieOneValue"),
                new Cookie("cookieTwoName", "cookieTwoValue")
        ))));

        // not only cookie
        assertTrue(new HashMapMatcher(KeyAndValue.toHashMap(
                new Cookie(not("cookieThreeName"), not("cookieThreeValue"))
        )).matches(new ArrayList<KeyAndValue>(Arrays.asList(
                new Cookie("cookieOneName", "cookieOneValue"),
                new Cookie("cookieTwoName", "cookieTwoValue")
        ))));

        // not all cookies (but matching)
        assertFalse(new HashMapMatcher(KeyAndValue.toHashMap(
                new Cookie(not("cookie.*"), not(".*"))
        )).matches(new ArrayList<KeyAndValue>(Arrays.asList(
                new Cookie("cookieOneName", "cookieOneValue"),
                new Cookie("cookieTwoName", "cookieTwoValue")
        ))));

        // not all cookies (but not matching name)
        assertFalse(new HashMapMatcher(KeyAndValue.toHashMap(
                new Cookie(not("cookie.*"), not("cookie.*"))
        )).matches(new ArrayList<KeyAndValue>(Arrays.asList(
                new Cookie("notCookieOneName", "cookieOneValue"),
                new Cookie("notCookieTwoName", "cookieTwoValue")
        ))));

        // not all cookies (but not matching value)
        assertTrue(new HashMapMatcher(KeyAndValue.toHashMap(
                new Cookie(string("cookie.*"), not("cookie.*"))
        )).matches(new ArrayList<KeyAndValue>(Arrays.asList(
                new Cookie("cookieOneName", "notCookieOneValue"),
                new Cookie("cookieTwoName", "notCookieTwoValue")
        ))));
    }

    @Test
    public void shouldMatchMatchingCookieWithOnlyCookie() {
        assertTrue(new HashMapMatcher(KeyAndValue.toHashMap(
                new Cookie(not("cookieThreeName"), not("cookieThreeValue"))
        )).matches(new ArrayList<KeyAndValue>(Arrays.asList(
                new Cookie("cookieOneName", "cookieOneValue"),
                new Cookie("cookieTwoName", "cookieTwoValue")
        ))));

        assertFalse(new HashMapMatcher(KeyAndValue.toHashMap(
                new Cookie("cookieThree", "cookieThreeValueOne")
        )).matches(new ArrayList<KeyAndValue>(Arrays.asList(
                new Cookie("cookieOneName", "cookieOneValue"),
                new Cookie("cookieTwoName", "cookieTwoValue")
        ))));

        assertTrue(new HashMapMatcher(KeyAndValue.toHashMap(
                new Cookie(not("cookieOneName"), not("cookieOneValue"))
        )).matches(new ArrayList<KeyAndValue>(Arrays.asList(
                new Cookie("notCookieOneName", "notCookieOneValue"),
                new Cookie("cookieTwoName", "cookieTwoValue")
        ))));

        assertFalse(new HashMapMatcher(KeyAndValue.toHashMap(
                new Cookie(not("cookieOneName"), not("cookieOneValue"))
        )).matches(new ArrayList<KeyAndValue>(Arrays.asList(
                new Cookie("cookieOneName", "cookieOneValue"),
                new Cookie("cookieTwoName", "cookieTwoValue")
        ))));

        assertTrue(new HashMapMatcher(KeyAndValue.toHashMap(
                new Cookie("cookieOneName", "cookieOneValue")
        )).matches(new ArrayList<KeyAndValue>(Arrays.asList(
                new Cookie("cookieOneName", "cookieOneValue"),
                new Cookie("cookieTwoName", "cookieTwoValue")
        ))));
    }

    @Test
    public void shouldMatchMatchingCookieWithOnlyCookieForEmptyList() {
        assertTrue(new HashMapMatcher(KeyAndValue.toHashMap(
                new ArrayList<KeyAndValue>()
        )).matches(new ArrayList<KeyAndValue>(Arrays.asList(
                new Cookie("cookieThree", "cookieThreeValueOne")
        ))));

        assertFalse(new HashMapMatcher(KeyAndValue.toHashMap(
                new Cookie("cookieThree", "cookieThreeValueOne")
        )).matches(new ArrayList<KeyAndValue>()));

        assertTrue(new HashMapMatcher(KeyAndValue.toHashMap(
                new Cookie(not("cookieThree"), not("cookieThreeValueOne"))
        )).matches(new ArrayList<KeyAndValue>()));
    }

    @Test
    public void shouldNotMatchMatchingCookieWithNotCookieAndNormalCookie() {
        assertFalse(new HashMapMatcher(KeyAndValue.toHashMap(
                new Cookie("cookieOneName", "cookieOneValue"),
                new Cookie(not("cookieTwoName"), not("cookieTwoValue")))).matches(new ArrayList<KeyAndValue>(Arrays.asList(
                new Cookie("cookieOneName", "cookieOneValue"),
                new Cookie("cookieTwoName", "cookieTwoValue")
        ))));
    }

    @Test
    public void shouldNotMatchMatchingCookieWithOnlyNotCookie() {
        assertFalse(
                new HashMapMatcher(KeyAndValue.toHashMap(
                        new Cookie(not("cookie.*"), not("cookie.*")))).matches(new ArrayList<KeyAndValue>(Arrays.asList(
                        new Cookie("cookieOneName", "cookieOneValue"),
                        new Cookie("cookieTwoName", "cookieTwoValue")
                ))));
    }

    @Test
    public void shouldNotMatchMatchingCookieWithOnlyNotCookieForBodyWithSingleCookie() {
        assertFalse(new HashMapMatcher(KeyAndValue.toHashMap(
                new Cookie(not("cookieTwoName"), not("cookieTwoValue")))).matches(new ArrayList<KeyAndValue>(Arrays.asList(
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
        assertFalse(NotMatcher.not(new HashMapMatcher(null)).matches(new ArrayList<KeyAndValue>(Arrays.asList(
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
        assertFalse(NotMatcher.not(new HashMapMatcher(KeyAndValue.toHashMap())).matches(new ArrayList<KeyAndValue>(Arrays.asList(
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
        assertTrue(NotMatcher.not(new HashMapMatcher(KeyAndValue.toHashMap(
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
        assertTrue(NotMatcher.not(new HashMapMatcher(KeyAndValue.toHashMap(
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
        assertTrue(NotMatcher.not(new HashMapMatcher(KeyAndValue.toHashMap(
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
        assertTrue(NotMatcher.not(new HashMapMatcher(KeyAndValue.toHashMap(
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
        assertTrue(NotMatcher.not(new HashMapMatcher(KeyAndValue.toHashMap(
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
        assertFalse(NotMatcher.not(new HashMapMatcher(KeyAndValue.toHashMap())).matches(null));
    }

    @Test
    public void shouldMatchEmptyTest() {
        assertTrue(new HashMapMatcher(KeyAndValue.toHashMap()).matches(new ArrayList<KeyAndValue>()));
    }

    @Test
    public void shouldNotMatchEmptyTestWhenNotApplied() {
        assertFalse(NotMatcher.not(new HashMapMatcher(KeyAndValue.toHashMap())).matches(new ArrayList<KeyAndValue>()));
    }
}
