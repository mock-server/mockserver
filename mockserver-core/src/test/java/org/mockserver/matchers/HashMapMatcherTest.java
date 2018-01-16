package org.mockserver.matchers;

import org.junit.Test;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.KeyAndValue;
import org.mockserver.model.NottableString;

import java.util.Arrays;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockserver.collections.CaseInsensitiveRegexHashMap.hashMap;
import static org.mockserver.model.NottableString.not;
import static org.mockserver.model.NottableString.string;

public class HashMapMatcherTest {

    @Test
    public void shouldMatchSingleKeyAndValueForEmptyListMatcher() {
        // given
        HashMapMatcher hashMapMatcher = new HashMapMatcher(new MockServerLogger(), hashMap(
            new String[]{}
        ));

        // then
        assertThat(hashMapMatcher.matches(null, Arrays.asList(
            new KeyAndValue("keyOne", "keyOneValue")
        )), is(true));
    }

    @Test
    public void shouldMatchMultipleKeyAndValueForEmptyListMatcher() {
        // given
        HashMapMatcher hashMapMatcher = new HashMapMatcher(new MockServerLogger(), hashMap(
            new String[]{}
        ));

        // then
        assertThat(hashMapMatcher.matches(null, Arrays.asList(
            new KeyAndValue("keyOne", "keyOneValue"),
            new KeyAndValue("keyTwo", "keyTwoValue"),
            new KeyAndValue("keyThree", "keyThreeValue")
        )), is(true));
    }

    @Test
    public void shouldMatchEmptyKeyAndValueForMatcherWithOnlySingleNottedKey() {
        // given
        HashMapMatcher hashMapMatcher = new HashMapMatcher(new MockServerLogger(), hashMap(
            new NottableString[]{not("keyOne"), string("keyOneValue")}
        ));

        // then
        assertThat(hashMapMatcher.matches(null, Arrays.<KeyAndValue>asList()), is(true));
    }

    @Test
    public void shouldMatchEmptyKeyAndValueForMatcherWithOnlyMultipleNottedKeys() {
        // given
        HashMapMatcher hashMapMatcher = new HashMapMatcher(new MockServerLogger(), hashMap(
            new NottableString[]{not("keyOne"), string("keyOneValue")},
            new NottableString[]{not("keyTwo"), string("keyTwoValue")},
            new NottableString[]{not("keyThree"), string("keyThreeValue")}
        ));

        // then
        assertThat(hashMapMatcher.matches(null, Arrays.<KeyAndValue>asList()), is(true));
    }

    @Test
    public void shouldNotMatchEmptyKeyAndValueForMatcherWithOnlyAtLeastOneNotNottedKey() {
        // given
        HashMapMatcher hashMapMatcher = new HashMapMatcher(new MockServerLogger(), hashMap(
            new NottableString[]{not("keyOne"), string("keyOneValue")},
            new NottableString[]{not("keyTwo"), string("keyTwoValue")},
            new NottableString[]{string("keyThree"), string("keyThreeValue")}
        ));

        // then
        assertThat(hashMapMatcher.matches(null, Arrays.<KeyAndValue>asList()), is(false));
    }

    @Test
    public void shouldMatchSingleKeyAndValueForSingleItemMatcher() {
        // given
        HashMapMatcher hashMapMatcher = new HashMapMatcher(new MockServerLogger(), hashMap(
            new String[]{"keyOne", "keyOneValue"}
        ));

        // then
        assertThat(hashMapMatcher.matches(null, Arrays.asList(
            new KeyAndValue("keyOne", "keyOneValue")
        )), is(true));
    }

    @Test
    public void shouldMatchMultipleKeyAndValueForSingleItemMatcher() {
        // given
        HashMapMatcher hashMapMatcher = new HashMapMatcher(new MockServerLogger(), hashMap(
            new String[]{"keyOne", "keyOneValue"}
        ));

        // then
        assertThat(hashMapMatcher.matches(null, Arrays.asList(
            new KeyAndValue("keyOne", "keyOneValue"),
            new KeyAndValue("keyTwo", "keyTwoValue"),
            new KeyAndValue("keyThree", "keyThreeValue")
        )), is(true));
    }

    @Test
    public void shouldMatchMultipleKeyAndValueForMultiItemMatcherButSubSet() {
        // given
        HashMapMatcher hashMapMatcher = new HashMapMatcher(new MockServerLogger(), hashMap(
            new String[]{"keyOne", "keyOneValue"},
            new String[]{"keyTwo", "keyTwoValue"}
        ));

        // then
        assertThat(hashMapMatcher.matches(null, Arrays.asList(
            new KeyAndValue("keyOne", "keyOneValue"),
            new KeyAndValue("keyTwo", "keyTwoValue"),
            new KeyAndValue("keyThree", "keyThreeValue")
        )), is(true));
    }

    @Test
    public void shouldMatchMultipleKeyAndValueForMultiItemMatcherButExactMatch() {
        // given
        HashMapMatcher hashMapMatcher = new HashMapMatcher(new MockServerLogger(), hashMap(
            new String[]{"keyOne", "keyOneValue"},
            new String[]{"keyTwo", "keyTwoValue"},
            new String[]{"keyThree", "keyThreeValue"}
        ));

        // then
        assertThat(hashMapMatcher.matches(null, Arrays.asList(
            new KeyAndValue("keyOne", "keyOneValue"),
            new KeyAndValue("keyTwo", "keyTwoValue"),
            new KeyAndValue("keyThree", "keyThreeValue")
        )), is(true));
    }
}
