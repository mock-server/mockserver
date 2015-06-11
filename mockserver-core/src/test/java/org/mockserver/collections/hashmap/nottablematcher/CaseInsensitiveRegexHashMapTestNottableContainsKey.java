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
public class CaseInsensitiveRegexHashMapTestNottableContainsKey {

    @Test
    public void singleValuedMapShouldContainValueForSingleValueForNottedKey() {
        // given
        CaseInsensitiveRegexHashMap hashMap = hashMap(
                new NottableString[]{not("keyOne"), string("keyOneValue")}
        );

        // then
        assertThat(hashMap.containsKey("notKeyOne"), is(true));
        assertThat(hashMap.containsKey(not("keyOne")), is(true));
    }

    @Test
    public void multiValuedMapShouldContainValueForSingleValueForNottedKey() {
        // given
        CaseInsensitiveRegexHashMap hashMap = hashMap(
                new NottableString[]{not("keyOne"), string("keyOneValue")},
                new NottableString[]{string("keyTwo"), string("keyTwoValue")}
        );

        // then
        assertThat(hashMap.containsKey("notKeyOne"), is(true));
        assertThat(hashMap.containsKey(not("keyOne")), is(true));
    }

    @Test
    public void singleValuedMapShouldNotContainValueForSingleValueWithValueMismatchForNottedKey() {
        // given
        CaseInsensitiveRegexHashMap hashMap = hashMap(
                new NottableString[]{not("keyOne"), string("keyOneValue")}
        );

        // then
        assertThat(hashMap.containsKey("keyOne"), is(false));
        assertThat(hashMap.containsKey(not("notKey.*")), is(false));
    }

    @Test
    public void multiValuedMapShouldNotContainValueForMultipleValuesWithValueMismatchForNottedKey() {
        // given
        CaseInsensitiveRegexHashMap hashMap = hashMap(
                new NottableString[]{not("keyOne"), string("keyOneValue")},
                new NottableString[]{string("keyTwo"), string("keyTwoValue")}
        );

        // then
        assertThat(hashMap.containsKey("keyOne"), is(false));
        assertThat(hashMap.containsKey(not("keyTwo|notKey.*")), is(false));
    }
}
