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
public class CaseInsensitiveRegexHashMapTestNottableContainsKey {

    @Test
    public void singleValuedMapShouldContainKeyForSingleValue() {
        // given
        CaseInsensitiveRegexHashMap hashMap = hashMap(
            new String[]{"keyOne", "keyOneValue"}
        );

        // then
        assertThat(hashMap.containsKey(not("notKeyOne")), is(true));
    }

    @Test
    public void multiValuedMapShouldContainKeyForSingleValue() {
        // given
        CaseInsensitiveRegexHashMap hashMap = hashMap(
            new String[]{"keyOne", "keyOneValue"},
            new String[]{"keyTwo", "keyTwoValue"},
            new String[]{"keyThree", "keyThreeValue"}
        );

        // then
        assertThat(hashMap.containsKey(not("notKeyOne")), is(true));
    }

    @Test
    public void singleValuedMapShouldNotContainKeyForSingleValueWithKeyMismatch() {
        // given
        CaseInsensitiveRegexHashMap hashMap = hashMap(
            new String[]{"keyOne", "keyOneValue"}
        );

        // then
        assertThat(hashMap.containsKey(not("keyOne")), is(false));
    }

    @Test
    public void multiValuedMapShouldNotContainKeyForMultipleValuesWithKeyMismatch() {
        // given
        CaseInsensitiveRegexHashMap hashMap = hashMap(
            new String[]{"keyOne", "keyOneValue"},
            new String[]{"keyTwo", "keyTwoValue"},
            new String[]{"keyThree", "keyThreeValue"}
        );

        // then
        assertThat(hashMap.containsKey(not("key.*")), is(false));
    }
}
