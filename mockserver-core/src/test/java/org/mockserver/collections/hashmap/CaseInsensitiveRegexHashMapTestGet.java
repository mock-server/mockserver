package org.mockserver.collections.hashmap;

import org.junit.Test;
import org.mockserver.collections.CaseInsensitiveRegexHashMap;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockserver.collections.CaseInsensitiveRegexHashMap.hashMap;
import static org.mockserver.model.NottableString.string;

/**
 * @author jamesdbloom
 */
public class CaseInsensitiveRegexHashMapTestGet {

    @Test
    public void shouldGetValueFromMapWithSingleEntry() {
        // given
        CaseInsensitiveRegexHashMap hashMap = hashMap(
            true, new String[]{"keyOne", "keyOneValue"}
        );

        // then
        assertThat(hashMap.get("keyOne"), is(string("keyOneValue")));
    }

    @Test
    public void shouldGetValueFromMapWithMultipleEntries() {
        // given
        CaseInsensitiveRegexHashMap hashMap = hashMap(
            true, new String[]{"keyOne", "keyOneValue"},
            new String[]{"keyTwo", "keyTwoValue"}
        );

        // then
        assertThat(hashMap.get("keyOne"), is(string("keyOneValue")));
    }
}
