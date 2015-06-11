package org.mockserver.matchers;

import org.junit.Test;
import org.mockserver.model.KeyAndValue;
import org.mockserver.model.NottableString;

import java.util.Arrays;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockserver.collections.CaseInsensitiveRegexHashMap.hashMap;
import static org.mockserver.model.NottableString.not;
import static org.mockserver.model.NottableString.string;

public class HashMapMatcherWithNottedStringsTest {

    @Test
    public void shouldMatchSingleKeyAndValueForSingleItemMatcherForNottedKey() {
        // given
        HashMapMatcher hashMapMatcher = new HashMapMatcher(hashMap(
                new NottableString[]{not("keyOne"), string("keyOneValue")}
        ));

        // then
        assertThat(hashMapMatcher.matches(Arrays.asList(
                new KeyAndValue("notKeyOne", "keyOneValue")
        )), is(true));
    }

    @Test
    public void shouldMatchSingleKeyAndValueForSingleItemMatcherForNottedValue() {
        // given
        HashMapMatcher hashMapMatcher = new HashMapMatcher(hashMap(
                new NottableString[]{string("keyOne"), not("keyOneValue")}
        ));

        // then
        assertThat(hashMapMatcher.matches(Arrays.asList(
                new KeyAndValue("keyOne", "notKeyOneValue")
        )), is(true));
    }

    @Test
    public void shouldMatchSingleKeyAndValueForSingleItemMatcherForNottedKeyAndValue() {
        // given
        HashMapMatcher hashMapMatcher = new HashMapMatcher(hashMap(
                new NottableString[]{not("keyOne"), not("keyOneValue")}
        ));

        // then
        assertThat(hashMapMatcher.matches(Arrays.asList(
                new KeyAndValue("notKeyOne", "notKeyOneValue")
        )), is(true));
    }

    @Test
    public void shouldMatchMultipleKeyAndValueForSingleItemMatcherForNottedKey() {
        // given
        HashMapMatcher hashMapMatcher = new HashMapMatcher(hashMap(
                new NottableString[]{not("keyOne"), string("keyOneValue")}
        ));

        // then
        assertThat(hashMapMatcher.matches(Arrays.asList(
                new KeyAndValue("notKeyOne", "keyOneValue"),
                new KeyAndValue("keyTwo", "keyTwoValue"),
                new KeyAndValue("keyThree", "keyThreeValue")
        )), is(true));
    }

    @Test
    public void shouldMatchMultipleKeyAndValueForSingleItemMatcherForNottedValue() {
        // given
        HashMapMatcher hashMapMatcher = new HashMapMatcher(hashMap(
                new NottableString[]{string("keyOne"), not("keyOneValue")}
        ));

        // then
        assertThat(hashMapMatcher.matches(Arrays.asList(
                new KeyAndValue("keyOne", "notKeyOneValue"),
                new KeyAndValue("keyTwo", "keyTwoValue"),
                new KeyAndValue("keyThree", "keyThreeValue")
        )), is(true));
    }

    @Test
    public void shouldMatchMultipleKeyAndValueForSingleItemMatcherForNottedKeyAndValue() {
        // given
        HashMapMatcher hashMapMatcher = new HashMapMatcher(hashMap(
                new NottableString[]{not("keyOne"), not("keyOneValue")}
        ));

        // then
        assertThat(hashMapMatcher.matches(Arrays.asList(
                new KeyAndValue("notKeyOne", "notKeyOneValue"),
                new KeyAndValue("keyTwo", "keyTwoValue"),
                new KeyAndValue("keyThree", "keyThreeValue")
        )), is(true));
    }

    @Test
    public void shouldMatchMultipleKeyAndValueForMultipleItemMatcherForNottedKey() {
        // given
        HashMapMatcher hashMapMatcher = new HashMapMatcher(hashMap(
                new NottableString[]{not("keyOne"), string("keyOneValue")},
                new NottableString[]{not("keyTwo"), string("keyTwoValue")}
        ));

        // then
        assertThat(hashMapMatcher.matches(Arrays.asList(
                new KeyAndValue("notKeyOne", "keyOneValue"),
                new KeyAndValue("notKeyTwo", "keyTwoValue"),
                new KeyAndValue("keyThree", "keyThreeValue")
        )), is(true));
    }

    @Test
    public void shouldMatchMultipleKeyAndValueForMultipleItemMatcherForNottedValue() {
        // given
        HashMapMatcher hashMapMatcher = new HashMapMatcher(hashMap(
                new NottableString[]{string("keyOne"), not("keyOneValue")},
                new NottableString[]{string("keyTwo"), not("keyTwoValue")}
        ));

        // then
        assertThat(hashMapMatcher.matches(Arrays.asList(
                new KeyAndValue("keyOne", "notKeyOneValue"),
                new KeyAndValue("keyTwo", "notKeyTwoValue"),
                new KeyAndValue("keyThree", "keyThreeValue")
        )), is(true));
    }

    @Test
    public void shouldMatchMultipleKeyAndValueForMultipleItemMatcherForNottedKeyAndValue() {
        // given
        HashMapMatcher hashMapMatcher = new HashMapMatcher(hashMap(
                new NottableString[]{not("keyOne"), not("keyOneValue")},
                new NottableString[]{not("keyTwo"), not("keyTwoValue")}
        ));

        // then
        assertThat(hashMapMatcher.matches(Arrays.asList(
                new KeyAndValue("notKeyOne", "notKeyOneValue"),
                new KeyAndValue("notKeyTwo", "notKeyTwoValue"),
                new KeyAndValue("keyThree", "keyThreeValue")
        )), is(true));
    }
}