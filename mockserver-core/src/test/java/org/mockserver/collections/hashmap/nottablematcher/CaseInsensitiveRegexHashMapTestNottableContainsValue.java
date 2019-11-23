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
public class CaseInsensitiveRegexHashMapTestNottableContainsValue {

    @Test
    public void singleValuedMapShouldContainValueForSingleValue() {
        // given
        CaseInsensitiveRegexHashMap hashMap = hashMap(
            true, new NottableString[]{string("keyOne"), not("keyOneValue")}
        );

        // then
        assertThat(hashMap.containsValue(not("key.*")), is(true));
        assertThat(hashMap.containsValue("notKey.*"), is(true));
    }

    @Test
    public void multiValuedMapShouldContainValueForSingleValue() {
        // given
        CaseInsensitiveRegexHashMap hashMap = hashMap(
            true, new NottableString[]{string("keyOne"), not("keyOneValue")},
            new NottableString[]{string("keyTwo"), string("keyTwoValue")}
        );

        // then
        assertThat(hashMap.containsValue(not("key.*")), is(true));
        assertThat(hashMap.containsValue("notKey.*"), is(true));
    }

    @Test
    public void singleValuedMapShouldNotContainValueForSingleValueWithValueMismatch() {
        // given
        CaseInsensitiveRegexHashMap hashMap = hashMap(
            true, new NottableString[]{string("keyOne"), not("keyOneValue")}
        );

        // then
        assertThat(hashMap.containsValue("keyOneValue"), is(false));
    }

    @Test
    public void singleValuedMapShouldNotContainValueForMultipleValuesWithValueMismatch() {
        // given
        CaseInsensitiveRegexHashMap hashMap = hashMap(
            true, new NottableString[]{string("keyOne"), not("keyOneValue")}
        );

        // then
        assertThat(hashMap.containsValue(not("notKey.*")), is(false));
        assertThat(hashMap.containsValue("key.*"), is(false));
    }

    @Test
    public void multiValuedMapShouldNotContainValueForMultipleValuesWithValueMismatch() {
        // given
        CaseInsensitiveRegexHashMap hashMap = hashMap(
            true, new NottableString[]{string("keyOne"), not("keyOneValue")},
            new NottableString[]{string("keyTwo"), not("keyTwoValue")}
        );

        // then
        assertThat(hashMap.containsValue(not("notKey.*")), is(false));
        assertThat(hashMap.containsValue("key.*"), is(false));
    }
}
