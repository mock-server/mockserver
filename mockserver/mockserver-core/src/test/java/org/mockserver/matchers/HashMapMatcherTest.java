package org.mockserver.matchers;

import org.junit.Test;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.Cookie;
import org.mockserver.model.Cookies;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockserver.model.NottableString.not;
import static org.mockserver.model.NottableString.string;

public class HashMapMatcherTest {

    @Test
    public void shouldMatchSingleKeyAndValueForEmptyListMatcher() {
        // given
        HashMapMatcher hashMapMatcher = new HashMapMatcher(new MockServerLogger(), new Cookies(), true);

        // then
        assertThat(hashMapMatcher.matches(null, new Cookies().withEntries(
            new Cookie("keyOne", "keyOneValue")
        )), is(true));
    }

    @Test
    public void shouldMatchMultipleKeyAndValueForEmptyListMatcher() {
        // given
        HashMapMatcher hashMapMatcher = new HashMapMatcher(new MockServerLogger(), new Cookies(), true);

        // then
        assertThat(hashMapMatcher.matches(null, new Cookies().withEntries(
            new Cookie("keyOne", "keyOneValue"),
            new Cookie("keyTwo", "keyTwoValue"),
            new Cookie("keyThree", "keyThreeValue")
        )), is(true));
    }

    @Test
    public void shouldMatchEmptyKeyAndValueForMatcherWithOnlySingleNottedKey() {
        // given
        HashMapMatcher hashMapMatcher = new HashMapMatcher(new MockServerLogger(), new Cookies().withEntries(
            new Cookie(not("keyOne"), string("keyOneValue"))
        ), true);

        // then
        assertThat(hashMapMatcher.matches(null, new Cookies()), is(true));
    }

    @Test
    public void shouldMatchEmptyKeyAndValueForMatcherWithOnlyMultipleNottedKeys() {
        // given
        HashMapMatcher hashMapMatcher = new HashMapMatcher(new MockServerLogger(), new Cookies().withEntries(
            new Cookie(not("keyOne"), string("keyOneValue")),
            new Cookie(not("keyTwo"), string("keyTwoValue")),
            new Cookie(not("keyThree"), string("keyThreeValue"))
        ), true);

        // then
        assertThat(hashMapMatcher.matches(null, new Cookies()), is(true));
    }

    @Test
    public void shouldNotMatchEmptyKeyAndValueForMatcherWithOnlyAtLeastOneNotNottedKey() {
        // given
        HashMapMatcher hashMapMatcher = new HashMapMatcher(new MockServerLogger(), new Cookies().withEntries(
            new Cookie(not("keyOne"), string("keyOneValue")),
            new Cookie(not("keyTwo"), string("keyTwoValue")),
            new Cookie(string("keyThree"), string("keyThreeValue"))
        ), true);

        // then
        assertThat(hashMapMatcher.matches(null, new Cookies()), is(false));
    }

    @Test
    public void shouldMatchSingleKeyAndValueForSingleItemMatcher() {
        // given
        HashMapMatcher hashMapMatcher = new HashMapMatcher(new MockServerLogger(), new Cookies().withEntries(
            new Cookie("keyOne", "keyOneValue")
        ), true);

        // then
        assertThat(hashMapMatcher.matches(null, new Cookies().withEntries(
            new Cookie("keyOne", "keyOneValue")
        )), is(true));
    }

    @Test
    public void shouldMatchMultipleKeyAndValueForSingleItemMatcher() {
        // given
        HashMapMatcher hashMapMatcher = new HashMapMatcher(new MockServerLogger(), new Cookies().withEntries(
            new Cookie("keyOne", "keyOneValue")
        ), true);

        // then
        assertThat(hashMapMatcher.matches(null, new Cookies().withEntries(
            new Cookie("keyOne", "keyOneValue"),
            new Cookie("keyTwo", "keyTwoValue"),
            new Cookie("keyThree", "keyThreeValue")
        )), is(true));
    }

    @Test
    public void shouldMatchMultipleKeyAndValueForMultiItemMatcherButSubSet() {
        // given
        HashMapMatcher hashMapMatcher = new HashMapMatcher(new MockServerLogger(), new Cookies().withEntries(
            new Cookie("keyOne", "keyOneValue"),
            new Cookie("keyTwo", "keyTwoValue")
        ), true);

        // then
        assertThat(hashMapMatcher.matches(null, new Cookies().withEntries(
            new Cookie("keyOne", "keyOneValue"),
            new Cookie("keyTwo", "keyTwoValue"),
            new Cookie("keyThree", "keyThreeValue")
        )), is(true));
    }

    @Test
    public void shouldMatchMultipleKeyAndValueForMultiItemMatcherButExactMatch() {
        // given
        HashMapMatcher hashMapMatcher = new HashMapMatcher(new MockServerLogger(), new Cookies().withEntries(
            new Cookie("keyOne", "keyOneValue"),
            new Cookie("keyTwo", "keyTwoValue"),
            new Cookie("keyThree", "keyThreeValue")
        ), true);

        // then
        assertThat(hashMapMatcher.matches(null, new Cookies().withEntries(
            new Cookie("keyOne", "keyOneValue"),
            new Cookie("keyTwo", "keyTwoValue"),
            new Cookie("keyThree", "keyThreeValue")
        )), is(true));
    }
}
