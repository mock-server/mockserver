package org.mockserver.collections.hashmap;

import org.junit.Test;
import org.mockserver.collections.CaseInsensitiveRegexHashMap;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockserver.collections.CaseInsensitiveRegexHashMap.hashMap;

/**
 * @author jamesdbloom
 */
public class CaseInsensitiveRegexHashMapTestClearingAndSize {

    @Test
    public void shouldReturnSize() {
        // when
        CaseInsensitiveRegexHashMap hashMap = hashMap(
            true, new String[]{"keyOne", "keyOneValue"},
            new String[]{"keyTwo", "keyTwoValue"},
            new String[]{"keyThree", "keyThreeValue"}
        );

        // then
        assertThat(hashMap.size(), is(3));
        assertThat(hashMap.isEmpty(), is(false));
    }

    @Test
    public void shouldReturnSizeWhenEmpty() {
        // when
        CaseInsensitiveRegexHashMap hashMap = hashMap(true, new String[]{});

        // then
        assertThat(hashMap.size(), is(0));
        assertThat(hashMap.isEmpty(), is(true));
    }

    @Test
    public void shouldClear() {
        // given
        CaseInsensitiveRegexHashMap hashMap = hashMap(
            true, new String[]{"keyOne", "keyOneValue"},
            new String[]{"keyTwo", "keyTwoValue"},
            new String[]{"keyThree", "keyThreeValue"}
        );

        // when
        hashMap.clear();

        // then
        assertThat(hashMap.size(), is(0));
        assertThat(hashMap.isEmpty(), is(true));
    }
}
