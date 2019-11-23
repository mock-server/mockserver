package org.mockserver.matchers;

import org.junit.Test;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.Cookie;
import org.mockserver.model.Cookies;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockserver.model.NottableString.not;
import static org.mockserver.model.NottableString.string;

/**
 * @author jamesdbloom
 */
public class CookieMatcherTest {

    @Test
    public void shouldMatchSingleCookieMatcherAndSingleMatchingCookie() {
        assertTrue(new HashMapMatcher(new MockServerLogger(), new Cookies().withEntries(
            new Cookie("cookieOneName", "cookieOneValue")
        ), true).matches(
            null,
            new Cookies().withEntries(
                new Cookie("cookieOneName", "cookieOneValue")
            )
        ));
    }

    @Test
    public void shouldNotMatchSingleCookieMatcherAndSingleNoneMatchingCookie() {
        assertFalse(new HashMapMatcher(new MockServerLogger(), new Cookies().withEntries(
            new Cookie("cookieOneName", "cookieOneValue")
        ), true).matches(
            null,
            new Cookies().withEntries(
                new Cookie("notCookieOneName", "cookieOneValue")
            )
        ));

        assertFalse(new HashMapMatcher(new MockServerLogger(), new Cookies().withEntries(
            new Cookie("cookieOneName", "cookieOneValue")
        ), true).matches(
            null,
            new Cookies().withEntries(
                new Cookie("cookieOneName", "notCookieOneValue")
            )
        ));
    }

    @Test
    public void shouldMatchMultipleCookieMatcherAndMultipleMatchingCookies() {
        assertTrue(new HashMapMatcher(new MockServerLogger(), new Cookies().withEntries(
            new Cookie("cookieOneName", "cookieOneValue"),
            new Cookie("cookieTwoName", "cookieTwoValue")
        ), true).matches(
            null,
            new Cookies().withEntries(
                new Cookie("cookieOneName", "cookieOneValue"),
                new Cookie("cookieTwoName", "cookieTwoValue")
            )
        ));

        assertTrue(new HashMapMatcher(new MockServerLogger(), new Cookies().withEntries(
            new Cookie("cookie.*", "cookie.*")
        ), true).matches(
            null,
            new Cookies().withEntries(
                new Cookie("cookieOneName", "cookieOneValue"),
                new Cookie("cookieTwoName", "cookieTwoValue")
            )
        ));
    }

    @Test
    public void shouldNotMatchMultipleCookieMatcherAndMultipleNoneMatchingCookiesWithOneMismatch() {
        assertFalse(new HashMapMatcher(new MockServerLogger(), new Cookies().withEntries(
            new Cookie("cookieOneName", "cookieOneValue"),
            new Cookie("cookieTwoName", "cookieTwoValue")
        ), true).matches(
            null,
            new Cookies().withEntries(
                new Cookie("notCookieOneName", "cookieOneValue"),
                new Cookie("cookieTwoName", "cookieTwoValue")
            )
        ));

        assertFalse(new HashMapMatcher(new MockServerLogger(), new Cookies().withEntries(
            new Cookie("cookieOneName", "cookieOneValue"),
            new Cookie("cookieTwoName", "cookieTwoValue")
        ), true).matches(
            null,
            new Cookies().withEntries(
                new Cookie("cookieOneName", "notCookieOneValue"),
                new Cookie("cookieTwoName", "cookieTwoValue")
            )
        ));
    }

    @Test
    public void shouldNotMatchMultipleCookieMatcherAndMultipleNoneMatchingCookiesWithMultipleMismatches() {
        assertFalse(new HashMapMatcher(new MockServerLogger(), new Cookies().withEntries(
            new Cookie("cookieOneName", "cookieOneValue"),
            new Cookie("cookieTwoName", "cookieTwoValue")
        ), true).matches(
            null,
            new Cookies().withEntries(
                new Cookie("notCookieOneName", "cookieOneValue"),
                new Cookie("notCookieTwoName", "cookieTwoValue")
            )
        ));

        assertFalse(new HashMapMatcher(new MockServerLogger(), new Cookies().withEntries(
            new Cookie("cookieOneName", "cookieOneValue"),
            new Cookie("cookieTwoName", "cookieTwoValue")
        ), true).matches(
            null,
            new Cookies().withEntries(
                new Cookie("cookieOneName", "notCookieOneValue"),
                new Cookie("cookieTwoName", "notCookieTwoValue")
            )
        ));

        assertFalse(new HashMapMatcher(new MockServerLogger(), new Cookies().withEntries(
            new Cookie("cookie.*", "cookie.*")
        ), true).matches(
            null,
            new Cookies().withEntries(
                new Cookie("notCookieOneName", "cookieOneValue"),
                new Cookie("notCookieTwoName", "cookieTwoValue")
            )
        ));

        assertFalse(new HashMapMatcher(new MockServerLogger(), new Cookies().withEntries(
            new Cookie("cookie.*", "cookie.*")
        ), true).matches(
            null,
            new Cookies().withEntries(
                new Cookie("cookieOneName", "notCookieOneValue"),
                new Cookie("cookieTwoName", "notCookieTwoValue")
            )
        ));
    }

    @Test
    public void shouldNotMatchMultipleCookieMatcherAndMultipleNotEnoughMatchingCookies() {
        assertFalse(new HashMapMatcher(new MockServerLogger(), new Cookies().withEntries(
            new Cookie("cookieOneName", "cookieOneValue"),
            new Cookie("cookieTwoName", "cookieTwoValue")
        ), true).matches(
            null,
            new Cookies().withEntries(
                new Cookie("cookieTwoName", "cookieTwoValue")
            )
        ));

        assertFalse(new HashMapMatcher(new MockServerLogger(), new Cookies().withEntries(
            new Cookie("cookieOneName", "cookieOneValue"),
            new Cookie("cookieTwoName", "cookieTwoValue")
        ), true).matches(
            null,
            new Cookies().withEntries(
                new Cookie("cookieOneName", "cookieOneValue")
            )
        ));
    }

    @Test
    public void shouldMatchMatchingCookie() {
        assertTrue(new HashMapMatcher(new MockServerLogger(), new Cookies().withEntries(
            new Cookie("cookieOneName", "cookieOneValue"),
            new Cookie("cookieTwoName", "cookieTwoValue")
        ), true).matches(
            null,
            new Cookies().withEntries(
                new Cookie("cookieOneName", "cookieOneValue"),
                new Cookie("cookieTwoName", "cookieTwoValue")
            )
        ));

        assertTrue(new HashMapMatcher(new MockServerLogger(), new Cookies().withEntries(
            new Cookie("cookie.*", "cookie.*")
        ), true).matches(
            null,
            new Cookies().withEntries(
                new Cookie("cookieOneName", "cookieOneValue"),
                new Cookie("cookieTwoName", "cookieTwoValue")
            )
        ));
    }

    @Test
    public void shouldNotMatchMatchingCookieWhenNotAppliedToMatcher() {
        // given
        assertTrue(new HashMapMatcher(new MockServerLogger(), new Cookies().withEntries(
            new Cookie("cookieOneName", "cookieOneValue"),
            new Cookie("cookieTwoName", "cookieTwoValue")
        ), true).matches(
            null,
            new Cookies().withEntries(
                new Cookie("cookieOneName", "cookieOneValue"),
                new Cookie("cookieTwoName", "cookieTwoValue")
            )
        ));

        // then - not matcher
        assertFalse(NotMatcher.not(new HashMapMatcher(new MockServerLogger(), new Cookies().withEntries(
            new Cookie("cookieOneName", "cookieOneValue"),
            new Cookie("cookieTwoName", "cookieTwoValue")
        ), true)).matches(
            null,
            new Cookies().withEntries(
                new Cookie("cookieOneName", "cookieOneValue"),
                new Cookie("cookieTwoName", "cookieTwoValue")
            )
        ));

        // and - not cookie
        assertFalse(new HashMapMatcher(new MockServerLogger(), new Cookies().withEntries(
            new Cookie("cookieOneName", "cookieOneValue"),
            new Cookie(not("cookie.*oName"), not("cookie.*oValue"))
        ), true).matches(
            null,
            new Cookies().withEntries(
                new Cookie("cookieOneName", "cookieOneValue"),
                new Cookie("cookieTwoName", "cookieTwoValue")
            )
        ));

        // and - multiple not cookies
        assertFalse(new HashMapMatcher(new MockServerLogger(), new Cookies().withEntries(
            new Cookie(not("cookieOneName"), not("cookieOneValue")),
            new Cookie(not("cookie.*Name"), not("cookie.*Value"))
        ), true).matches(
            null,
            new Cookies().withEntries(
                new Cookie("cookieOneName", "cookieOneValue"),
                new Cookie("cookieTwoName", "cookieTwoValue")
            )
        ));

        // and - not matcher and not cookie
        assertTrue(NotMatcher.not(new HashMapMatcher(new MockServerLogger(), new Cookies().withEntries(
            new Cookie("cookieOneName", "cookieOneValue"),
            new Cookie(not("cookie.*oName"), not("cookie.*oValue"))
        ), true)).matches(
            null,
            new Cookies().withEntries(
                new Cookie("cookieOneName", "cookieOneValue"),
                new Cookie("cookieTwoName", "cookieTwoValue")
            )
        ));
    }

    @Test
    public void shouldMatchMatchingCookieWithNotCookieAndNormalCookie() {
        // not matching cookie
        assertFalse(new HashMapMatcher(new MockServerLogger(), new Cookies().withEntries(
            new Cookie("cookieOneName", "cookieOneValue"),
            new Cookie(not("cookie.*oName"), not("cookie.*oValue"))
        ), true).matches(
            null,
            new Cookies().withEntries(
                new Cookie("cookieOneName", "cookieOneValue"),
                new Cookie("cookieTwoName", "cookieTwoValue")
            )
        ));

        // not extra cookie
        assertFalse(new HashMapMatcher(new MockServerLogger(), new Cookies().withEntries(
            new Cookie("cookieOneName", "cookieOneValue"),
            new Cookie(not("cookie.*oName"), not("cookie.*oValue"))
        ), true).matches(
            null,
            new Cookies().withEntries(
                new Cookie("cookieOneName", "cookieOneValue"),
                new Cookie("cookieTwoName", "cookieTwoValue")
            )
        ));

        // not extra cookie
        assertTrue(new HashMapMatcher(new MockServerLogger(), new Cookies().withEntries(
            new Cookie("cookieOneName", "cookieOneValue"),
            new Cookie("cookieTwoName", "cookieTwoValue"),
            new Cookie(not("cookieThreeName"), not("cookieThreeValue"))
        ), true).matches(
            null,
            new Cookies().withEntries(
                new Cookie("cookieOneName", "cookieOneValue"),
                new Cookie("cookieTwoName", "cookieTwoValue")
            )
        ));

        // not single cookie
        assertTrue(new HashMapMatcher(new MockServerLogger(), new Cookies().withEntries(
            new Cookie(not("cookieThreeName"), not("cookieThreeValue"))
        ), true).matches(
            null,
            new Cookies().withEntries(
                new Cookie("cookieOneName", "cookieOneValue"),
                new Cookie("cookieTwoName", "cookieTwoValue")
            )
        ));

        // not multiple cookies
        assertTrue(new HashMapMatcher(new MockServerLogger(), new Cookies().withEntries(
            new Cookie(not("cookieOneName"), not("cookieOneValue")),
            new Cookie(not("cookieTwoName"), not("cookieTwoValue"))
        ), true).matches(
            null,
            new Cookies().withEntries(
                new Cookie("notCookieOneName", "notCookieOneValue"),
                new Cookie("notCookieTwoName", "notCookieTwoValue")
            )
        ));

        // not all cookies (but not matching name and value)
        assertFalse(new HashMapMatcher(new MockServerLogger(), new Cookies().withEntries(
            new Cookie(not("cookie.*"), not(".*"))
        ), true).matches(
            null,
            new Cookies().withEntries(
                new Cookie("cookieOneName", "cookieOneValue"),
                new Cookie("cookieTwoName", "cookieTwoValue")
            )
        ));

        // not all cookies (but not matching name)
        assertFalse(new HashMapMatcher(new MockServerLogger(), new Cookies().withEntries(
            new Cookie(not("cookie.*"), not("cookie.*"))
        ), true).matches(
            null,
            new Cookies().withEntries(
                new Cookie("notCookieOneName", "cookieOneValue"),
                new Cookie("notCookieTwoName", "cookieTwoValue")
            )
        ));

        // not all cookies (but not matching value)
        assertTrue(new HashMapMatcher(new MockServerLogger(), new Cookies().withEntries(
            new Cookie(string("cookie.*"), not("cookie.*"))
        ), true).matches(
            null,
            new Cookies().withEntries(
                new Cookie("cookieOneName", "notCookieOneValue"),
                new Cookie("cookieTwoName", "notCookieTwoValue")
            )
        ));
    }

    @Test
    public void shouldMatchMatchingCookieWithOnlyCookie() {
        assertTrue(new HashMapMatcher(new MockServerLogger(), new Cookies().withEntries(
            new Cookie(not("cookieThreeName"), not("cookieThreeValue"))
        ), true).matches(
            null,
            new Cookies().withEntries(
                new Cookie("cookieOneName", "cookieOneValue"),
                new Cookie("cookieTwoName", "cookieTwoValue")
            )
        ));

        assertFalse(new HashMapMatcher(new MockServerLogger(), new Cookies().withEntries(
            new Cookie("cookieThree", "cookieThreeValueOne")
        ), true).matches(
            null,
            new Cookies().withEntries(
                new Cookie("cookieOneName", "cookieOneValue"),
                new Cookie("cookieTwoName", "cookieTwoValue")
            )
        ));

        assertTrue(new HashMapMatcher(new MockServerLogger(), new Cookies().withEntries(
            new Cookie(not("cookieOneName"), not("cookieOneValue"))
        ), true).matches(
            null,
            new Cookies().withEntries(
                new Cookie("notCookieOneName", "notCookieOneValue"),
                new Cookie("cookieTwoName", "cookieTwoValue")
            )
        ));

        assertFalse(new HashMapMatcher(new MockServerLogger(), new Cookies().withEntries(
            new Cookie(not("cookieOneName"), not("cookieOneValue"))
        ), true).matches(
            null,
            new Cookies().withEntries(
                new Cookie("cookieOneName", "cookieOneValue"),
                new Cookie("cookieTwoName", "cookieTwoValue")
            )
        ));

        assertTrue(new HashMapMatcher(new MockServerLogger(), new Cookies().withEntries(
            new Cookie("cookieOneName", "cookieOneValue")
        ), true).matches(
            null,
            new Cookies().withEntries(
                new Cookie("cookieOneName", "cookieOneValue"),
                new Cookie("cookieTwoName", "cookieTwoValue")
            )
        ));
    }

    @Test
    public void shouldMatchMatchingCookieWithOnlyCookieForEmptyList() {
        assertTrue(new HashMapMatcher(new MockServerLogger(), new Cookies(), true).matches(
            null,
            new Cookies().withEntries(
                new Cookie("cookieThree", "cookieThreeValueOne")
            )
        ));

        assertFalse(new HashMapMatcher(new MockServerLogger(), new Cookies().withEntries(
            new Cookie("cookieThree", "cookieThreeValueOne")
        ), true).matches(
            null,
            new Cookies()
        ));

        assertTrue(new HashMapMatcher(new MockServerLogger(), new Cookies().withEntries(
            new Cookie(not("cookieThree"), not("cookieThreeValueOne"))
        ), true).matches(
            null,
            new Cookies()
        ));
    }

    @Test
    public void shouldNotMatchMatchingCookieWithNotCookieAndNormalCookie() {
        assertFalse(new HashMapMatcher(new MockServerLogger(), new Cookies().withEntries(
            new Cookie("cookieOneName", "cookieOneValue"),
            new Cookie(not("cookieTwoName"), not("cookieTwoValue"))),
            true).matches(
            null,
            new Cookies().withEntries(
                new Cookie("cookieOneName", "cookieOneValue"),
                new Cookie("cookieTwoName", "cookieTwoValue")
            )
        ));
    }

    @Test
    public void shouldNotMatchMatchingCookieWithOnlyNotCookie() {
        assertFalse(new HashMapMatcher(new MockServerLogger(), new Cookies().withEntries(
            new Cookie(not("cookie.*"), not("cookie.*"))),
            true).matches(
            null,
            new Cookies().withEntries(
                new Cookie("cookieOneName", "cookieOneValue"),
                new Cookie("cookieTwoName", "cookieTwoValue")
            )
        ));
    }

    @Test
    public void shouldNotMatchMatchingCookieWithOnlyNotCookieForBodyWithSingleCookie() {
        assertFalse(new HashMapMatcher(new MockServerLogger(), new Cookies().withEntries(
            new Cookie(not("cookieTwoName"), not("cookieTwoValue"))),
            true).matches(
            null,
            new Cookies().withEntries(
                new Cookie("cookieTwoName", "cookieTwoValue")
            )
        ));
    }

    @Test
    public void shouldMatchNullExpectation() {
        assertTrue(new HashMapMatcher(new MockServerLogger(), null, true).matches(
            null,
            new Cookies().withEntries(
                new Cookie("cookieOneName", "cookieOneValue"),
                new Cookie("cookieTwoName", "cookieTwoValue")
            )
        ));
    }

    @Test
    public void shouldNotMatchNullExpectationWhenNotApplied() {
        assertFalse(NotMatcher.not(new HashMapMatcher(new MockServerLogger(), null, true))
            .matches(
                null,
                new Cookies().withEntries(
                    new Cookie("cookieOneName", "cookieOneValue"),
                    new Cookie("cookieTwoName", "cookieTwoValue")
                )
            ));
    }

    @Test
    public void shouldMatchEmptyExpectation() {
        assertTrue(new HashMapMatcher(new MockServerLogger(), new Cookies(), true).matches(
            null,
            new Cookies().withEntries(
                new Cookie("cookieOneName", "cookieOneValue"),
                new Cookie("cookieTwoName", "cookieTwoValue")
            )
        ));
    }

    @Test
    public void shouldNotMatchEmptyExpectationWhenNotApplied() {
        assertFalse(NotMatcher.not(new HashMapMatcher(new MockServerLogger(), new Cookies(), true))
            .matches(
                null,
                new Cookies().withEntries(
                    new Cookie("cookieOneName", "cookieOneValue"),
                    new Cookie("cookieTwoName", "cookieTwoValue")
                )
            ));
    }

    @Test
    public void shouldNotMatchIncorrectCookieName() {
        assertFalse(new HashMapMatcher(new MockServerLogger(), new Cookies().withEntries(
            new Cookie("cookieOneName", "cookieOneValue"),
            new Cookie("cookieTwoName", "cookieTwoValue")
        ), true).matches(
            null,
            new Cookies().withEntries(
                new Cookie("cookieOneName", "cookieOneValue"),
                new Cookie("INCORRECTcookieTwoName", "cookieTwoValue")
            )
        ));
    }

    @Test
    public void shouldMatchIncorrectCookieNameWhenNotApplied() {
        assertTrue(NotMatcher.not(new HashMapMatcher(new MockServerLogger(), new Cookies().withEntries(
            new Cookie("cookieOneName", "cookieOneValue"),
            new Cookie("cookieTwoName", "cookieTwoValue")
        ), true)).matches(
            null,
            new Cookies().withEntries(
                new Cookie("cookieOneName", "cookieOneValue"),
                new Cookie("INCORRECTcookieTwoName", "cookieTwoValue")
            )
        ));
    }

    @Test
    public void shouldNotMatchIncorrectCookieValue() {
        assertFalse(new HashMapMatcher(new MockServerLogger(), new Cookies().withEntries(
            new Cookie("cookieOneName", "cookieOneValue"),
            new Cookie("cookieTwoName", "cookieTwoValue")
        ), true).matches(
            null,
            new Cookies().withEntries(
                new Cookie("cookieOneName", "cookieOneValue"),
                new Cookie("cookieTwoName", "INCORRECTcookieTwoValue")
            )
        ));
    }

    @Test
    public void shouldMatchIncorrectCookieValueWhenNotApplied() {
        assertTrue(NotMatcher.not(new HashMapMatcher(new MockServerLogger(), new Cookies().withEntries(
            new Cookie("cookieOneName", "cookieOneValue"),
            new Cookie("cookieTwoName", "cookieTwoValue")
        ), true)).matches(
            null,
            new Cookies().withEntries(
                new Cookie("cookieOneName", "cookieOneValue"),
                new Cookie("cookieTwoName", "INCORRECTcookieTwoValue")
            )
        ));
    }

    @Test
    public void shouldNotMatchIncorrectCookieNameAndValue() {
        assertFalse(new HashMapMatcher(new MockServerLogger(), new Cookies().withEntries(
            new Cookie("cookieOneName", "cookieOneValue"),
            new Cookie("cookieTwoName", "cookieTwoValue")
        ), true).matches(
            null,
            new Cookies().withEntries(
                new Cookie("cookieOneName", "cookieOneValue"),
                new Cookie("INCORRECTcookieTwoName", "INCORRECTcookieTwoValue")
            )
        ));
    }

    @Test
    public void shouldMatchIncorrectCookieNameAndValueWhenNotApplied() {
        assertTrue(NotMatcher.not(new HashMapMatcher(new MockServerLogger(), new Cookies().withEntries(
            new Cookie("cookieOneName", "cookieOneValue"),
            new Cookie("cookieTwoName", "cookieTwoValue")
        ), true)).matches(
            null,
            new Cookies().withEntries(
                new Cookie("cookieOneName", "cookieOneValue"),
                new Cookie("INCORRECTcookieTwoName", "INCORRECTcookieTwoValue")
            )
        ));
    }

    @Test
    public void shouldNotMatchNullCookieValue() {
        assertFalse(new HashMapMatcher(new MockServerLogger(), new Cookies().withEntries(
            new Cookie("cookieOneName", "cookieOneValue"),
            new Cookie("cookieTwoName", "cookieTwoValue")
        ), true).matches(
            null,
            new Cookies().withEntries(
                new Cookie("cookieOneName", "cookieOneValue"),
                new Cookie("cookieTwoName", null)
            )
        ));
    }

    @Test
    public void shouldMatchNullCookieValueWhenNotApplied() {
        assertTrue(NotMatcher.not(new HashMapMatcher(new MockServerLogger(), new Cookies().withEntries(
            new Cookie("cookieOneName", "cookieOneValue"),
            new Cookie("cookieTwoName", "cookieTwoValue")
        ), true)).matches(
            null,
            new Cookies().withEntries(
                new Cookie("cookieOneName", "cookieOneValue"),
                new Cookie("cookieTwoName", null)
            )
        ));
    }

    @Test
    public void shouldMatchNullCookieValueInExpectation() {
        assertTrue(new HashMapMatcher(new MockServerLogger(), new Cookies().withEntries(
            new Cookie("cookieOneName", "cookieOneValue"),
            new Cookie("cookieTwoName", "")
        ), true).matches(
            null,
            new Cookies().withEntries(
                new Cookie("cookieOneName", "cookieOneValue"),
                new Cookie("cookieTwoName", "cookieTwoValue")
            )
        ));
    }

    @Test
    public void shouldNotMatchMissingCookie() {
        assertFalse(new HashMapMatcher(new MockServerLogger(), new Cookies().withEntries(
            new Cookie("cookieOneName", "cookieOneValue"),
            new Cookie("cookieTwoName", "cookieTwoValue")
        ), true).matches(
            null,
            new Cookies().withEntries(
                new Cookie("cookieOneName", "cookieOneValue")
            )
        ));
    }

    @Test
    public void shouldMatchMissingCookieWhenNotApplied() {
        assertTrue(NotMatcher.not(new HashMapMatcher(new MockServerLogger(), new Cookies().withEntries(
            new Cookie("cookieOneName", "cookieOneValue"),
            new Cookie("cookieTwoName", "cookieTwoValue")
        ), true)).matches(
            null,
            new Cookies().withEntries(
                new Cookie("cookieOneName", "cookieOneValue")
            )
        ));
    }

    @Test
    public void shouldMatchNullTest() {
        assertTrue(new HashMapMatcher(new MockServerLogger(), new Cookies(), true).matches(
            null,
            new Cookies()
        ));
    }

    @Test
    public void shouldNotMatchNullTestWhenNotApplied() {
        assertFalse(NotMatcher.not(new HashMapMatcher(new MockServerLogger(), new Cookies(), true)).matches(
            null,
            new Cookies()
        ));
    }

    @Test
    public void shouldMatchEmptyTest() {
        assertTrue(new HashMapMatcher(new MockServerLogger(), new Cookies(), true).matches(
            null,
            new Cookies()
        ));
    }

    @Test
    public void shouldNotMatchEmptyTestWhenNotApplied() {
        assertFalse(NotMatcher.not(new HashMapMatcher(new MockServerLogger(), new Cookies(), true)).matches(
            null,
            new Cookies()
        ));
    }

}
