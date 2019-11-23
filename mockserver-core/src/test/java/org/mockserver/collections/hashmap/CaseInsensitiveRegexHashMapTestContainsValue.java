package org.mockserver.collections.hashmap;

import org.junit.Test;
import org.mockserver.collections.CaseInsensitiveRegexHashMap;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockserver.collections.CaseInsensitiveRegexHashMap.hashMap;

/**
 * @author jamesdbloom
 */
public class CaseInsensitiveRegexHashMapTestContainsValue {

    @Test
    public void singleValuedMapShouldContainValueForSingleValue() {
        // given
        CaseInsensitiveRegexHashMap hashMap = hashMap(
            true, new String[]{"keyOne", "keyOneValue"}
        );

        // then
        assertThat(hashMap.containsValue("keyOneValue"), is(true));
    }

    @Test
    public void multiValuedMapShouldContainValueForSingleValue() {
        // given
        CaseInsensitiveRegexHashMap hashMap = hashMap(
            true, new String[]{"keyOne", "keyOneValue"},
            new String[]{"keyTwo", "keyTwoValue"},
            new String[]{"keyThree", "keyThreeValue"}
        );

        // then
        assertThat(hashMap.containsValue("keyOneValue"), is(true));
    }

    @Test
    public void singleValuedMapShouldNotContainValueForSingleValueWithValueMismatch() {
        // given
        CaseInsensitiveRegexHashMap hashMap = hashMap(
            true, new String[]{"keyOne", "keyOneValue"}
        );

        // then
        assertThat(hashMap.containsValue("notKeyOneValue"), is(false));
    }

    @Test
    public void multiValuedMapShouldNotContainValueForSingleValueWithValueMismatch() {
        // given
        CaseInsensitiveRegexHashMap hashMap = hashMap(
            true, new String[]{"keyOne", "keyOneValue"},
            new String[]{"keyTwo", "keyTwoValue"},
            new String[]{"keyThree", "keyThreeValue"}
        );

        // then
        assertThat(hashMap.containsValue("notKeyOneValue"), is(false));
    }
}
