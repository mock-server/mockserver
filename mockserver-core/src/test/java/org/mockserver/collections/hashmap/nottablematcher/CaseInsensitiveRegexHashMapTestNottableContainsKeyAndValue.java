package org.mockserver.collections.hashmap.nottablematcher;

import org.junit.Test;
import org.mockserver.collections.CaseInsensitiveRegexHashMap;
import org.mockserver.model.NottableString;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockserver.collections.CaseInsensitiveRegexHashMap.hashMap;
import static org.mockserver.model.NottableString.not;
import static org.mockserver.model.NottableString.string;

/**
 * @author jamesdbloom
 */
public class CaseInsensitiveRegexHashMapTestNottableContainsKeyAndValue {

    @Test
    public void singleValuedMapShouldContainKeyAndValueForSingleValueForNottedKey() {
        // given
        CaseInsensitiveRegexHashMap hashMap = hashMap(
            new NottableString[]{not("keyOne"), string("keyOneValue")}
        );

        // then
        assertThat(hashMap.containsKeyValue("notKeyOne", "keyOneValue"), is(true));
        assertThat(hashMap.containsKeyValue(not("keyOne"), string("keyOneValue")), is(true));
    }

    @Test
    public void singleValuedMapShouldContainKeyAndValueForSingleValueForNottedValue() {
        // given
        CaseInsensitiveRegexHashMap hashMap = hashMap(
            new NottableString[]{string("keyOne"), not("keyOneValue")}
        );

        // then
        assertThat(hashMap.containsKeyValue("keyOne", "notKeyOneValue"), is(true));
        assertThat(hashMap.containsKeyValue(string("keyOne"), not("keyOneValue")), is(true));
    }

    @Test
    public void singleValuedMapShouldContainKeyAndValueForSingleValueForNottedKeyAndValue() {
        // given
        CaseInsensitiveRegexHashMap hashMap = hashMap(
            new NottableString[]{not("keyOne"), not("keyOneValue")}
        );

        // then
        assertThat(hashMap.containsKeyValue("notKeyOne", "notKeyOneValue"), is(true));
        assertThat(hashMap.containsKeyValue(not("keyOne"), not("keyOneValue")), is(true));
    }

    @Test
    public void multiValuedMapShouldContainKeyAndValueForSingleValueForNottedKey() {
        // given
        CaseInsensitiveRegexHashMap hashMap = hashMap(
            new NottableString[]{not("keyOne"), string("keyOneValue")},
            new NottableString[]{string("keyTwo"), string("keyTwoValue")}
        );

        // then
        assertThat(hashMap.containsKeyValue("notKeyOne", "keyOneValue"), is(true));
        assertThat(hashMap.containsKeyValue(not("keyOne"), string("keyOneValue")), is(true));
    }

    @Test
    public void multiValuedMapShouldContainKeyAndValueForSingleValueForNottedValue() {
        // given
        CaseInsensitiveRegexHashMap hashMap = hashMap(
            new NottableString[]{string("keyOne"), not("keyOneValue")},
            new NottableString[]{string("keyTwo"), string("keyTwoValue")}
        );

        // then
        assertThat(hashMap.containsKeyValue("keyOne", "notKeyOneValue"), is(true));
        assertThat(hashMap.containsKeyValue(string("keyOne"), not("keyOneValue")), is(true));
    }

    @Test
    public void multiValuedMapShouldContainKeyAndValueForSingleValueForNottedKeyAndValue() {
        // given
        CaseInsensitiveRegexHashMap hashMap = hashMap(
            new NottableString[]{not("keyOne"), not("keyOneValue")},
            new NottableString[]{string("keyTwo"), string("keyTwoValue")}
        );

        // then
        assertThat(hashMap.containsKeyValue("notKeyOne", "notKeyOneValue"), is(true));
        assertThat(hashMap.containsKeyValue(not("keyOne"), not("keyOneValue")), is(true));
    }

    @Test
    public void singleValuedMapShouldNotContainKeyAndValueForSingleValueWithValueMismatchForNottedKey() {
        // given
        CaseInsensitiveRegexHashMap hashMap = hashMap(
            new NottableString[]{not("keyOne"), string("keyOneValue")}
        );

        // then
        assertThat(hashMap.containsKeyValue("keyOne", "keyOneValue"), is(false));
        assertThat(hashMap.containsKeyValue(not("notKey.*"), string("keyOneValue")), is(false));
    }

    @Test
    public void singleValuedMapShouldNotContainKeyAndValueForSingleValueWithValueMismatchForNottedValue() {
        // given
        CaseInsensitiveRegexHashMap hashMap = hashMap(
            new NottableString[]{string("keyOne"), not("keyOneValue")}
        );

        // then
        assertThat(hashMap.containsKeyValue("keyOne", "keyOneValue"), is(false));
        assertThat(hashMap.containsKeyValue(string("keyOne"), not("notKey.*")), is(false));
    }

    @Test
    public void singleValuedMapShouldNotContainKeyAndValueForSingleValueWithValueMismatchForNottedKeyAndValue() {
        // given
        CaseInsensitiveRegexHashMap hashMap = hashMap(
            new NottableString[]{not("keyOne"), not("keyOneValue")}
        );

        // then
        assertThat(hashMap.containsKeyValue("keyOne", "keyOneValue"), is(false));
        assertThat(hashMap.containsKeyValue(not("notKey.*"), not("notKey.*")), is(false));
    }

    @Test
    public void multiValuedMapShouldNotContainKeyAndValueForMultipleValuesWithValueMismatchForNottedKey() {
        // given
        CaseInsensitiveRegexHashMap hashMap = hashMap(
            new NottableString[]{string("keyOne"), string("keyOneValue")},
            new NottableString[]{not("keyTwo"), string("keyTwoValue")}
        );

        // then
        assertThat(hashMap.containsKeyValue("keyTwo", "keyTwoValue"), is(false));
        assertThat(hashMap.containsKeyValue(not("notKey.*"), string("keyTwoValue")), is(false));
    }

    @Test
    public void multiValuedMapShouldNotContainKeyAndValueForMultipleValuesWithValueMismatchForNottedValue() {
        // given
        CaseInsensitiveRegexHashMap hashMap = hashMap(
            new NottableString[]{string("keyOne"), string("keyOneValue")},
            new NottableString[]{string("keyTwo"), not("keyTwoValue")}
        );

        // then
        assertThat(hashMap.containsKeyValue("keyTwo", "keyTwoValue"), is(false));
        assertThat(hashMap.containsKeyValue(string("keyTwo"), not("notKey.*")), is(false));
    }

    @Test
    public void multiValuedMapShouldNotContainKeyAndValueForMultipleValuesWithValueMismatchForNottedKeyAndValue() {
        // given
        CaseInsensitiveRegexHashMap hashMap = hashMap(
            new NottableString[]{string("keyOne"), string("keyOneValue")},
            new NottableString[]{not("keyTwo"), not("keyTwoValue")}
        );

        // then
        assertThat(hashMap.containsKeyValue("keyTwo", "keyTwoValue"), is(false));
        assertThat(hashMap.containsKeyValue(not("key.*e"), not("key.*e_.*")), is(false));
    }
}
