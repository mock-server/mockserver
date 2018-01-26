package org.mockserver.matchers;

import org.junit.Test;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.Cookie;
import org.mockserver.model.Cookies;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockserver.model.NottableString.not;
import static org.mockserver.model.NottableString.string;

public class HashMapMatcherWithNottedStringsTest {

    @Test
    public void shouldMatchSingleKeyAndValueForSingleItemMatcherForNottedKey() {
        // given
        HashMapMatcher hashMapMatcher = new HashMapMatcher(new MockServerLogger(), new Cookies().withEntries(
            new Cookie(not("keyOne"), string("keyOneValue"))
        ));

        // then
        assertThat(hashMapMatcher.matches(null, new Cookies().withEntries(
            new Cookie("notKeyOne", "keyOneValue")
        )), is(true));
    }

    @Test
    public void shouldMatchSingleKeyAndValueForSingleItemMatcherForNottedValue() {
        // given
        HashMapMatcher hashMapMatcher = new HashMapMatcher(new MockServerLogger(), new Cookies().withEntries(
            new Cookie(string("keyOne"), not("keyOneValue"))
        ));

        // then
        assertThat(hashMapMatcher.matches(null, new Cookies().withEntries(
            new Cookie("keyOne", "notKeyOneValue")
        )), is(true));
    }

    @Test
    public void shouldMatchSingleKeyAndValueForSingleItemMatcherForNottedKeyAndValue() {
        // given
        HashMapMatcher hashMapMatcher = new HashMapMatcher(new MockServerLogger(), new Cookies().withEntries(
            new Cookie(not("keyOne"), not("keyOneValue"))
        ));

        // then
        assertThat(hashMapMatcher.matches(null, new Cookies().withEntries(
            new Cookie("notKeyOne", "notKeyOneValue")
        )), is(true));
    }

    @Test
    public void shouldMatchMultipleKeyAndValueForSingleItemMatcherForNottedKey() {
        // given
        HashMapMatcher hashMapMatcher = new HashMapMatcher(new MockServerLogger(), new Cookies().withEntries(
            new Cookie(not("keyOne"), string("keyOneValue"))
        ));

        // then
        assertThat(hashMapMatcher.matches(null, new Cookies().withEntries(
            new Cookie("notKeyOne", "keyOneValue"),
            new Cookie("keyTwo", "keyTwoValue"),
            new Cookie("keyThree", "keyThreeValue")
        )), is(true));
    }

    @Test
    public void shouldMatchMultipleKeyAndValueForSingleItemMatcherForNottedValue() {
        // given
        HashMapMatcher hashMapMatcher = new HashMapMatcher(new MockServerLogger(), new Cookies().withEntries(
            new Cookie(string("keyOne"), not("keyOneValue"))
        ));

        // then
        assertThat(hashMapMatcher.matches(null, new Cookies().withEntries(
            new Cookie("keyOne", "notKeyOneValue"),
            new Cookie("keyTwo", "keyTwoValue"),
            new Cookie("keyThree", "keyThreeValue")
        )), is(true));
    }

    @Test
    public void shouldMatchMultipleKeyAndValueForSingleItemMatcherForNottedKeyAndValue() {
        // given
        HashMapMatcher hashMapMatcher = new HashMapMatcher(new MockServerLogger(), new Cookies().withEntries(
            new Cookie(not("keyOne"), not("keyOneValue"))
        ));

        // then
        assertThat(hashMapMatcher.matches(null, new Cookies().withEntries(
            new Cookie("notKeyOne", "notKeyOneValue"),
            new Cookie("keyTwo", "keyTwoValue"),
            new Cookie("keyThree", "keyThreeValue")
        )), is(true));
    }

    @Test
    public void shouldMatchMultipleKeyAndValueForMultipleItemMatcherForNottedKey() {
        // given
        HashMapMatcher hashMapMatcher = new HashMapMatcher(new MockServerLogger(), new Cookies().withEntries(
            new Cookie(not("keyOne"), string("keyOneValue")),
            new Cookie(not("keyTwo"), string("keyTwoValue"))
        ));

        // then
        assertThat(hashMapMatcher.matches(null, new Cookies().withEntries(
            new Cookie("notKeyOne", "keyOneValue"),
            new Cookie("notKeyTwo", "keyTwoValue"),
            new Cookie("keyThree", "keyThreeValue")
        )), is(true));
    }

    @Test
    public void shouldMatchMultipleKeyAndValueForMultipleItemMatcherForNottedValue() {
        // given
        HashMapMatcher hashMapMatcher = new HashMapMatcher(new MockServerLogger(), new Cookies().withEntries(
            new Cookie(string("keyOne"), not("keyOneValue")),
            new Cookie(string("keyTwo"), not("keyTwoValue"))
        ));

        // then
        assertThat(hashMapMatcher.matches(null, new Cookies().withEntries(
            new Cookie("keyOne", "notKeyOneValue"),
            new Cookie("keyTwo", "notKeyTwoValue"),
            new Cookie("keyThree", "keyThreeValue")
        )), is(true));
    }

    @Test
    public void shouldMatchMultipleKeyAndValueForMultipleItemMatcherForNottedKeyAndValue() {
        // given
        HashMapMatcher hashMapMatcher = new HashMapMatcher(new MockServerLogger(), new Cookies().withEntries(
            new Cookie(not("keyOne"), not("keyOneValue")),
            new Cookie(not("keyTwo"), not("keyTwoValue"))
        ));

        // then
        assertThat(hashMapMatcher.matches(null, new Cookies().withEntries(
            new Cookie("notKeyOne", "notKeyOneValue"),
            new Cookie("notKeyTwo", "notKeyTwoValue"),
            new Cookie("keyThree", "keyThreeValue")
        )), is(true));
    }
}
