package org.mockserver.collections.hashmap.nottablematched;

import org.junit.Test;
import org.mockserver.collections.CaseInsensitiveRegexHashMap;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockserver.collections.CaseInsensitiveRegexHashMap.hashMap;
import static org.mockserver.model.NottableString.not;

/**
 * @author jamesdbloom
 */
public class CaseInsensitiveRegexHashMapTestNottableContainsValue {

    @Test
    public void singleValuedMapShouldContainValueForSingleValue() {
        // given
        CaseInsensitiveRegexHashMap hashMap = hashMap(
                new String[]{"keyOne", "keyOneValue"}
        );

        // then
        assertThat(hashMap.containsValue(not("notKeyOneValue")), is(true));
    }

    @Test
    public void multiValuedMapShouldContainValueForSingleValue() {
        // given
        CaseInsensitiveRegexHashMap hashMap = hashMap(
                new String[]{"keyOne", "keyOneValue"},
                new String[]{"keyTwo", "keyTwoValue"},
                new String[]{"keyThree", "keyThreeValue"}
        );

        // then
        assertThat(hashMap.containsValue(not("notKeyOneValue")), is(true));
    }

    @Test
    public void singleValuedMapShouldNotContainValueForSingleValueWithValueMismatch() {
        // given
        CaseInsensitiveRegexHashMap hashMap = hashMap(
                new String[]{"keyOne", "keyOneValue"}
        );

        // then
        assertThat(hashMap.containsValue(not("key.*")), is(false));
        assertThat(hashMap.containsValue(not("keyOneValue")), is(false));
    }

    @Test
    public void multiValuedMapShouldNotContainValueForMultipleValuesWithValueMismatch() {
        // given
        CaseInsensitiveRegexHashMap hashMap = hashMap(
                new String[]{"keyOne", "keyOneValue"},
                new String[]{"keyTwo", "keyTwoValue"},
                new String[]{"keyThree", "keyThreeValue"}
        );

        // then
        assertThat(hashMap.containsValue(not("key.*")), is(false));
    }
}
