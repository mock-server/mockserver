package org.mockserver.matchers;

import org.junit.Test;
import org.mockserver.model.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockserver.matchers.NotMatcher.not;
import static org.mockserver.model.NottableString.string;

/**
 * @author jamesdbloom
 */
public class CookieMatcherTest {

    @Test
    public void shouldMatchSingleCookieMatcherAndSingleMatchingCookie() {
        assertTrue(new HashMapMatcher(KeysAndValues.toCaseInsensitiveRegexMultiMap(
                new Cookie("cookieOneName", "cookieOneValue")
        )).matches(
                new Cookie("cookieOneName", "cookieOneValue")
        ));
    }

    @Test
    public void shouldNotMatchSingleCookieMatcherAndSingleNoneMatchingCookie() {
        assertFalse(new HashMapMatcher(KeysAndValues.toCaseInsensitiveRegexMultiMap(
                new Cookie("cookieOneName", "cookieOneValue")
        )).matches(
                new Cookie("notCookieOneName", "cookieOneValue")
        ));

        assertFalse(new HashMapMatcher(KeysAndValues.toCaseInsensitiveRegexMultiMap(
                new Cookie("cookieOneName", "cookieOneValue")
        )).matches(
                new Cookie("cookieOneName", "notCookieOneValue")
        ));
    }

    @Test
    public void shouldMatchMultipleCookieMatcherAndMultipleMatchingCookies() {
        assertTrue(new HashMapMatcher(KeysAndValues.toCaseInsensitiveRegexMultiMap(
                new Cookie("cookieOneName", "cookieOneValue"),
                new Cookie("cookieTwoName", "cookieTwoValue")
        )).matches(
                new Cookie("cookieOneName", "cookieOneValue"),
                new Cookie("cookieTwoName", "cookieTwoValue")
        ));

        assertTrue(new HashMapMatcher(KeysAndValues.toCaseInsensitiveRegexMultiMap(
                new Cookie("cookie.*", "cookie.*")
        )).matches(
                new Cookie("cookieOneName", "cookieOneValue"),
                new Cookie("cookieTwoName", "cookieTwoValue")
        ));
    }

    @Test
    public void shouldNotMatchMultipleCookieMatcherAndMultipleNoneMatchingCookiesWithOneMismatch() {
        assertFalse(new HashMapMatcher(KeysAndValues.toCaseInsensitiveRegexMultiMap(
                new Cookie("cookieOneName", "cookieOneValue"),
                new Cookie("cookieTwoName", "cookieTwoValue")
        )).matches(
                new Cookie("notCookieOneName", "cookieOneValue"),
                new Cookie("cookieTwoName", "cookieTwoValue")
        ));

        assertFalse(new HashMapMatcher(KeysAndValues.toCaseInsensitiveRegexMultiMap(
                new Cookie("cookieOneName", "cookieOneValue"),
                new Cookie("cookieTwoName", "cookieTwoValue")
        )).matches(
                new Cookie("cookieOneName", "notCookieOneValue"),
                new Cookie("cookieTwoName", "cookieTwoValue")
        ));
    }

    @Test
    public void shouldNotMatchMultipleCookieMatcherAndMultipleNoneMatchingCookiesWithMultipleMismatches() {
        assertFalse(new HashMapMatcher(KeysAndValues.toCaseInsensitiveRegexMultiMap(
                new Cookie("cookieOneName", "cookieOneValue"),
                new Cookie("cookieTwoName", "cookieTwoValue")
        )).matches(
                new Cookie("notCookieOneName", "cookieOneValue"),
                new Cookie("notCookieTwoName", "cookieTwoValue")
        ));

        assertFalse(new HashMapMatcher(KeysAndValues.toCaseInsensitiveRegexMultiMap(
                new Cookie("cookieOneName", "cookieOneValue"),
                new Cookie("cookieTwoName", "cookieTwoValue")
        )).matches(
                new Cookie("cookieOneName", "notCookieOneValue"),
                new Cookie("cookieTwoName", "notCookieTwoValue")
        ));

        assertFalse(new HashMapMatcher(KeysAndValues.toCaseInsensitiveRegexMultiMap(
                new Cookie("cookie.*", "cookie.*")
        )).matches(
                new Cookie("notCookieOneName", "cookieOneValue"),
                new Cookie("notCookieTwoName", "cookieTwoValue")
        ));

        assertFalse(new HashMapMatcher(KeysAndValues.toCaseInsensitiveRegexMultiMap(
                new Cookie("cookie.*", "cookie.*")
        )).matches(
                new Cookie("cookieOneName", "notCookieOneValue"),
                new Cookie("cookieTwoName", "notCookieTwoValue")
        ));
    }

    @Test
    public void shouldNotMatchMultipleCookieMatcherAndMultipleNotEnoughMatchingCookies() {
        assertFalse(new HashMapMatcher(KeysAndValues.toCaseInsensitiveRegexMultiMap(
                new Cookie("cookieOneName", "cookieOneValue"),
                new Cookie("cookieTwoName", "cookieTwoValue")
        )).matches(
                new Cookie("cookieTwoName", "cookieTwoValue")
        ));

        assertFalse(new HashMapMatcher(KeysAndValues.toCaseInsensitiveRegexMultiMap(
                new Cookie("cookieOneName", "cookieOneValue"),
                new Cookie("cookieTwoName", "cookieTwoValue")
        )).matches(
                new Cookie("cookieOneName", "cookieOneValue")
        ));
    }

    @Test
    public void shouldMatchMatchingCookie() {
        assertTrue(new HashMapMatcher(KeysAndValues.toCaseInsensitiveRegexMultiMap(
            new Cookie("cookieOneName", "cookieOneValue"),
            new Cookie("cookieTwoName", "cookieTwoValue")
        )).matches(
            new Cookie("cookieOneName", "cookieOneValue"),
            new Cookie("cookieTwoName", "cookieTwoValue")
        ));

        assertTrue(new HashMapMatcher(KeysAndValues.toCaseInsensitiveRegexMultiMap(
            new Cookie("cookie.*", "cookie.*")
        )).matches(
            new Cookie("cookieOneName", "cookieOneValue"),
            new Cookie("cookieTwoName", "cookieTwoValue")
        ));
    }

    @Test
    public void shouldNotMatchMatchingCookieWhenNotAppliedToMatcher() {
        // given
        assertTrue(new HashMapMatcher(KeysAndValues.toCaseInsensitiveRegexMultiMap(
            new Cookie("cookieOneName", "cookieOneValue"),
            new Cookie("cookieTwoName", "cookieTwoValue")
        )).matches(
            new Cookie("cookieOneName", "cookieOneValue"),
            new Cookie("cookieTwoName", "cookieTwoValue")
        ));

        // then - not matcher
        assertFalse(NotMatcher.not(new HashMapMatcher(KeysAndValues.toCaseInsensitiveRegexMultiMap(
            new Cookie("cookieOneName", "cookieOneValue"),
            new Cookie("cookieTwoName", "cookieTwoValue")
        ))).matches(
            new Cookie("cookieOneName", "cookieOneValue"),
            new Cookie("cookieTwoName", "cookieTwoValue")
        ));

        // and - not cookie
        assertFalse(new HashMapMatcher(KeysAndValues.toCaseInsensitiveRegexMultiMap(
            new Cookie("cookieOneName", "cookieOneValue"),
            new Cookie(NottableString.not("cookie.*Name"), NottableString.not("cookie.*Value"))
        )).matches(
            new Cookie("cookieOneName", "cookieOneValue"),
            new Cookie("cookieTwoName", "cookieTwoValue")
        ));

        // and - not matcher and not cookie
        assertTrue(NotMatcher.not(new HashMapMatcher(KeysAndValues.toCaseInsensitiveRegexMultiMap(
            new Cookie("cookieOneName", "cookieOneValue"),
            new Cookie(NottableString.not("cookie.*Name"), NottableString.not("cookie.*Value"))
        ))).matches(
            new Cookie("cookieOneName", "cookieOneValue"),
            new Cookie("cookieTwoName", "cookieTwoValue")
        ));
    }

    @Test
    public void shouldMatchMatchingCookieWithNotCookieAndNormalCookie() {
        // not matching cookie
        assertFalse(new HashMapMatcher(KeysAndValues.toCaseInsensitiveRegexMultiMap(
            new Cookie("cookieOneName", "cookieOneValue"),
            new Cookie(NottableString.not("cookie.*Name"), NottableString.not("cookie.*Value"))
        )).matches(
            new Cookie("cookieOneName", "cookieOneValue"),
            new Cookie("cookieTwoName", "cookieTwoValue")
        ));

        // not extra cookie
        assertFalse(new HashMapMatcher(KeysAndValues.toCaseInsensitiveRegexMultiMap(
            new Cookie("cookieOneName", "cookieOneValue"),
            new Cookie("cookieTwoName", "cookieTwoValue"),
            new Cookie(NottableString.not("cookie.*Name"), NottableString.not("cookie.*Value"))
        )).matches(
            new Cookie("cookieOneName", "cookieOneValue"),
            new Cookie("cookieTwoName", "cookieTwoValue")
        ));

        // not extra cookie
        assertTrue(new HashMapMatcher(KeysAndValues.toCaseInsensitiveRegexMultiMap(
            new Cookie("cookieOneName", "cookieOneValue"),
            new Cookie("cookieTwoName", "cookieTwoValue"),
            new Cookie(NottableString.not("cookieThreeName"), NottableString.not("cookieThreeValue"))
        )).matches(
            new Cookie("cookieOneName", "cookieOneValue"),
            new Cookie("cookieTwoName", "cookieTwoValue")
        ));

        // not only cookie
        assertTrue(new HashMapMatcher(KeysAndValues.toCaseInsensitiveRegexMultiMap(
            new Cookie(NottableString.not("cookieThreeName"), NottableString.not("cookieThreeValue"))
        )).matches(
            new Cookie("cookieOneName", "cookieOneValue"),
            new Cookie("cookieTwoName", "cookieTwoValue")
        ));

        // not all cookies (but matching)
        assertFalse(new HashMapMatcher(KeysAndValues.toCaseInsensitiveRegexMultiMap(
            new Cookie(NottableString.not("cookie.*"), NottableString.not(".*"))
        )).matches(
            new Cookie("cookieOneName", "cookieOneValue"),
            new Cookie("cookieTwoName", "cookieTwoValue")
        ));

        // not all cookies (but not matching name)
        assertFalse(new HashMapMatcher(KeysAndValues.toCaseInsensitiveRegexMultiMap(
            new Cookie(NottableString.not("cookie.*"), NottableString.not("cookie.*"))
        )).matches(
            new Cookie("notCookieOneName", "cookieOneValue"),
            new Cookie("notCookieTwoName", "cookieTwoValue")
        ));

        // not all cookies (but not matching value)
        assertTrue(new HashMapMatcher(KeysAndValues.toCaseInsensitiveRegexMultiMap(
            new Cookie(string("cookie.*"), NottableString.not("cookie.*"))
        )).matches(
            new Cookie("cookieOneName", "notCookieOneValue"),
            new Cookie("cookieTwoName", "notCookieTwoValue")
        ));
    }

    @Test
    public void shouldMatchMatchingCookieWithOnlyCookie() {
        assertTrue(new HashMapMatcher(KeysAndValues.toCaseInsensitiveRegexMultiMap(
            new Cookie(NottableString.not("cookieThreeName"), NottableString.not("cookieThreeValue"))
        )).matches(
            new Cookie("cookieOneName", "cookieOneValue"),
            new Cookie("cookieTwoName", "cookieTwoValue")
        ));

        assertFalse(new HashMapMatcher(KeysAndValues.toCaseInsensitiveRegexMultiMap(
            new Cookie("cookieThree", "cookieThreeValueOne")
        )).matches(
            new Cookie("cookieOneName", "cookieOneValue"),
            new Cookie("cookieTwoName", "cookieTwoValue")
        ));

        assertTrue(new HashMapMatcher(KeysAndValues.toCaseInsensitiveRegexMultiMap(
            new Cookie(NottableString.not("cookieOneName"), NottableString.not("cookieOneValue"))
        )).matches(
            new Cookie("notCookieOneName", "notCookieOneValue"),
            new Cookie("cookieTwoName", "cookieTwoValue")
        ));

        assertFalse(new HashMapMatcher(KeysAndValues.toCaseInsensitiveRegexMultiMap(
            new Cookie(NottableString.not("cookieOneName"), NottableString.not("cookieOneValue"))
        )).matches(
            new Cookie("cookieOneName", "cookieOneValue"),
            new Cookie("cookieTwoName", "cookieTwoValue")
        ));

        assertTrue(new HashMapMatcher(KeysAndValues.toCaseInsensitiveRegexMultiMap(
            new Cookie("cookieOneName", "cookieOneValue")
        )).matches(
            new Cookie("cookieOneName", "cookieOneValue"),
            new Cookie("cookieTwoName", "cookieTwoValue")
        ));
    }

    @Test
    public void shouldMatchMatchingCookieWithOnlyCookieForEmptyList() {
        assertTrue(new HashMapMatcher(KeysAndValues.toCaseInsensitiveRegexMultiMap(
            new ArrayList<KeyAndValue>()
        )).matches(
            new Cookie("cookieThree", "cookieThreeValueOne")
        ));

        assertFalse(new HashMapMatcher(KeysAndValues.toCaseInsensitiveRegexMultiMap(
            new Cookie("cookieThree", "cookieThreeValueOne")
        )).matches(new ArrayList<KeyAndValue>()));

        assertTrue(new HashMapMatcher(KeysAndValues.toCaseInsensitiveRegexMultiMap(
            new Cookie(NottableString.not("cookieThree"), NottableString.not("cookieThreeValueOne"))
        )).matches(new ArrayList<KeyAndValue>()));
    }

    @Test
    public void shouldNotMatchMatchingCookieWithNotCookieAndNormalCookie() {
        assertFalse(new HashMapMatcher(KeysAndValues.toCaseInsensitiveRegexMultiMap(
            new Cookie("cookieOneName", "cookieOneValue"),
            new Cookie(NottableString.not("cookieTwoName"), NottableString.not("cookieTwoValue")))).matches(
            new Cookie("cookieOneName", "cookieOneValue"),
            new Cookie("cookieTwoName", "cookieTwoValue")
        ));
    }

    @Test
    public void shouldNotMatchMatchingCookieWithOnlyNotCookie() {
        assertFalse(
            new HashMapMatcher(KeysAndValues.toCaseInsensitiveRegexMultiMap(
                new Cookie(NottableString.not("cookie.*"), NottableString.not("cookie.*")))).matches(
                new Cookie("cookieOneName", "cookieOneValue"),
                new Cookie("cookieTwoName", "cookieTwoValue")
            ));
    }

    @Test
    public void shouldNotMatchMatchingCookieWithOnlyNotCookieForBodyWithSingleCookie() {
        assertFalse(new HashMapMatcher(KeysAndValues.toCaseInsensitiveRegexMultiMap(
            new Cookie(NottableString.not("cookieTwoName"), NottableString.not("cookieTwoValue")))).matches(
            new Cookie("cookieTwoName", "cookieTwoValue")
        ));
    }

    @Test
    public void shouldMatchNullExpectation() {
        assertTrue(new HashMapMatcher(null).matches(
            new Cookie("cookieOneName", "cookieOneValue"),
            new Cookie("cookieTwoName", "cookieTwoValue")
        ));
    }

    @Test
    public void shouldNotMatchNullExpectationWhenNotApplied() {
        assertFalse(NotMatcher.not(new HashMapMatcher(null)).matches(
            new Cookie("cookieOneName", "cookieOneValue"),
            new Cookie("cookieTwoName", "cookieTwoValue")
        ));
    }

    @Test
    public void shouldMatchEmptyExpectation() {
        assertTrue(new HashMapMatcher(KeysAndValues.toCaseInsensitiveRegexMultiMap(new ArrayList<KeyAndValue>())).matches(
            new Cookie("cookieOneName", "cookieOneValue"),
            new Cookie("cookieTwoName", "cookieTwoValue")
        ));
    }

    @Test
    public void shouldNotMatchEmptyExpectationWhenNotApplied() {
        assertFalse(NotMatcher.not(new HashMapMatcher(KeysAndValues.toCaseInsensitiveRegexMultiMap(new ArrayList<KeyAndValue>()))).matches(
            new Cookie("cookieOneName", "cookieOneValue"),
            new Cookie("cookieTwoName", "cookieTwoValue")
        ));
    }

    @Test
    public void shouldNotMatchIncorrectCookieName() {
        assertFalse(new HashMapMatcher(KeysAndValues.toCaseInsensitiveRegexMultiMap(
            new Cookie("cookieOneName", "cookieOneValue"),
            new Cookie("cookieTwoName", "cookieTwoValue")
        )).matches(
            new Cookie("cookieOneName", "cookieOneValue"),
            new Cookie("INCORRECTcookieTwoName", "cookieTwoValue")
        ));
    }

    @Test
    public void shouldMatchIncorrectCookieNameWhenNotApplied() {
        assertTrue(NotMatcher.not(new HashMapMatcher(KeysAndValues.toCaseInsensitiveRegexMultiMap(
            new Cookie("cookieOneName", "cookieOneValue"),
            new Cookie("cookieTwoName", "cookieTwoValue")
        ))).matches(
            new Cookie("cookieOneName", "cookieOneValue"),
            new Cookie("INCORRECTcookieTwoName", "cookieTwoValue")
        ));
    }

    @Test
    public void shouldNotMatchIncorrectCookieValue() {
        assertFalse(new HashMapMatcher(KeysAndValues.toCaseInsensitiveRegexMultiMap(
            new Cookie("cookieOneName", "cookieOneValue"),
            new Cookie("cookieTwoName", "cookieTwoValue")
        )).matches(
            new Cookie("cookieOneName", "cookieOneValue"),
            new Cookie("cookieTwoName", "INCORRECTcookieTwoValue")
        ));
    }

    @Test
    public void shouldMatchIncorrectCookieValueWhenNotApplied() {
        assertTrue(NotMatcher.not(new HashMapMatcher(KeysAndValues.toCaseInsensitiveRegexMultiMap(
            new Cookie("cookieOneName", "cookieOneValue"),
            new Cookie("cookieTwoName", "cookieTwoValue")
        ))).matches(
            new Cookie("cookieOneName", "cookieOneValue"),
            new Cookie("cookieTwoName", "INCORRECTcookieTwoValue")
        ));
    }

    @Test
    public void shouldNotMatchIncorrectCookieNameAndValue() {
        assertFalse(new HashMapMatcher(KeysAndValues.toCaseInsensitiveRegexMultiMap(
            new Cookie("cookieOneName", "cookieOneValue"),
            new Cookie("cookieTwoName", "cookieTwoValue")
        )).matches(
            new Cookie("cookieOneName", "cookieOneValue"),
            new Cookie("INCORRECTcookieTwoName", "INCORRECTcookieTwoValue")
        ));
    }

    @Test
    public void shouldMatchIncorrectCookieNameAndValueWhenNotApplied() {
        assertTrue(NotMatcher.not(new HashMapMatcher(KeysAndValues.toCaseInsensitiveRegexMultiMap(
            new Cookie("cookieOneName", "cookieOneValue"),
            new Cookie("cookieTwoName", "cookieTwoValue")
        ))).matches(
            new Cookie("cookieOneName", "cookieOneValue"),
            new Cookie("INCORRECTcookieTwoName", "INCORRECTcookieTwoValue")
        ));
    }

    @Test
    public void shouldNotMatchNullCookieValue() {
        assertFalse(new HashMapMatcher(KeysAndValues.toCaseInsensitiveRegexMultiMap(
            new Cookie("cookieOneName", "cookieOneValue"),
            new Cookie("cookieTwoName", "cookieTwoValue")
        )).matches(
            new Cookie("cookieOneName", "cookieOneValue"),
            new Cookie("cookieTwoName", null)
        ));
    }

    @Test
    public void shouldMatchNullCookieValueWhenNotApplied() {
        assertTrue(NotMatcher.not(new HashMapMatcher(KeysAndValues.toCaseInsensitiveRegexMultiMap(
            new Cookie("cookieOneName", "cookieOneValue"),
            new Cookie("cookieTwoName", "cookieTwoValue")
        ))).matches(
            new Cookie("cookieOneName", "cookieOneValue"),
            new Cookie("cookieTwoName", null)
        ));
    }

    @Test
    public void shouldMatchNullCookieValueInExpectation() {
        assertTrue(new HashMapMatcher(KeysAndValues.toCaseInsensitiveRegexMultiMap(
            new Cookie("cookieOneName", "cookieOneValue"),
            new Cookie("cookieTwoName", "")
        )).matches(
            new Cookie("cookieOneName", "cookieOneValue"),
            new Cookie("cookieTwoName", "cookieTwoValue")
        ));
    }

    @Test
    public void shouldNotMatchMissingCookie() {
        assertFalse(new HashMapMatcher(KeysAndValues.toCaseInsensitiveRegexMultiMap(
            new Cookie("cookieOneName", "cookieOneValue"),
            new Cookie("cookieTwoName", "cookieTwoValue")
        )).matches(
            new Cookie("cookieOneName", "cookieOneValue")
        ));
    }

    @Test
    public void shouldMatchMissingCookieWhenNotApplied() {
        assertTrue(NotMatcher.not(new HashMapMatcher(KeysAndValues.toCaseInsensitiveRegexMultiMap(
            new Cookie("cookieOneName", "cookieOneValue"),
            new Cookie("cookieTwoName", "cookieTwoValue")
        ))).matches(
            new Cookie("cookieOneName", "cookieOneValue")
        ));
    }

    @Test
    public void shouldMatchNullTest() {
        assertTrue(new HashMapMatcher(KeysAndValues.toCaseInsensitiveRegexMultiMap((List<KeyAndValue>) null)).matches((List<KeyAndValue>) null));
    }

    @Test
    public void shouldNotMatchNullTestWhenNotApplied() {
        assertFalse(NotMatcher.not(new HashMapMatcher(KeysAndValues.toCaseInsensitiveRegexMultiMap((List<KeyAndValue>) null))).matches((List<KeyAndValue>) null));
    }

    @Test
    public void shouldMatchEmptyTest() {
        assertTrue(new HashMapMatcher(KeysAndValues.toCaseInsensitiveRegexMultiMap(new ArrayList<KeyAndValue>())).matches(new ArrayList<KeyAndValue>()));
    }

    @Test
    public void shouldNotMatchEmptyTestWhenNotApplied() {
        assertFalse(NotMatcher.not(new HashMapMatcher(KeysAndValues.toCaseInsensitiveRegexMultiMap(new ArrayList<KeyAndValue>()))).matches(new ArrayList<KeyAndValue>()));
    }

}
