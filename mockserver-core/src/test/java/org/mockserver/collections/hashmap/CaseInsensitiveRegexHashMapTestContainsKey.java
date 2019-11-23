package org.mockserver.collections.hashmap;

import org.junit.Test;
import org.mockserver.collections.CaseInsensitiveRegexHashMap;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockserver.collections.CaseInsensitiveRegexHashMap.hashMap;

/**
 * @author jamesdbloom
 */
public class CaseInsensitiveRegexHashMapTestContainsKey {

    @Test
    public void singleValuedMapShouldContainKeyForSingleValue() {
        // given
        CaseInsensitiveRegexHashMap hashMap = hashMap(
            true, new String[]{"keyOne", "keyOneValue"}
        );

        // then
        assertThat(hashMap.containsKey("keyOne"), is(true));
    }

    @Test
    public void multiValuedMapShouldContainKeyForSingleValue() {
        // given
        CaseInsensitiveRegexHashMap hashMap = hashMap(
            true, new String[]{"keyOne", "keyOneValue"},
            new String[]{"keyTwo", "keyTwoValue"},
            new String[]{"keyThree", "keyThreeValue"}
        );

        // then
        assertThat(hashMap.containsKey("keyOne"), is(true));
    }

    @Test
    public void singleValuedMapShouldNotContainKeyForSingleValueWithKeyMismatch() {
        // given
        CaseInsensitiveRegexHashMap hashMap = hashMap(
            true, new String[]{"keyOne", "keyOneValue"}
        );

        // then
        assertThat(hashMap.containsKey("notKeyOne"), is(false));
    }

    @Test
    public void multiValuedMapShouldNotContainKeyForSingleValueWithKeyMismatch() {
        // given
        CaseInsensitiveRegexHashMap hashMap = hashMap(
            true, new String[]{"keyOne", "keyOneValue"},
            new String[]{"keyTwo", "keyTwoValue"},
            new String[]{"keyThree", "keyThreeValue"}
        );

        // then
        assertThat(hashMap.containsKey("notKeyOne"), is(false));
    }
}
