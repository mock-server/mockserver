package org.mockserver.collections.hashmap;

import org.junit.Test;
import org.mockserver.collections.CaseInsensitiveRegexHashMap;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsIterableContainingInAnyOrder.containsInAnyOrder;
import static org.mockserver.collections.CaseInsensitiveRegexHashMap.hashMap;
import static org.mockserver.collections.CaseInsensitiveRegexMultiMap.entry;
import static org.mockserver.model.NottableString.string;

/**
 * @author jamesdbloom
 */
public class CaseInsensitiveRegexHashMapTestKeysAndValue {

    @Test
    public void shouldReturnKeysForMapWithSingleEntry() {
        // given
        CaseInsensitiveRegexHashMap hashMap = hashMap(
            new String[]{"keyOne", "keyOneValue"}
        );

        // then
        assertThat(hashMap.keySet(), containsInAnyOrder(string("keyOne")));
    }

    @Test
    public void shouldReturnKeysForMapWithMultipleEntries() {
        // given
        CaseInsensitiveRegexHashMap hashMap = hashMap(
            new String[]{"keyOne", "keyOneValue"},
            new String[]{"keyTwo", "keyTwoValue"}
        );

        // then
        assertThat(hashMap.keySet(), containsInAnyOrder(string("keyOne"), string("keyTwo")));
    }

    @Test
    public void shouldReturnValuesForMapWithSingleEntry() {
        // given
        CaseInsensitiveRegexHashMap hashMap = hashMap(
            new String[]{"keyOne", "keyOneValue"}
        );

        // then
        assertThat(hashMap.values(), containsInAnyOrder(string("keyOneValue")));
    }

    @Test
    public void shouldReturnValuesForMapWithMultipleEntries() {
        // given
        CaseInsensitiveRegexHashMap hashMap = hashMap(
            new String[]{"keyOne", "keyOneValue"},
            new String[]{"keyTwo", "keyTwoValue"}
        );

        // then
        assertThat(hashMap.values(), containsInAnyOrder(string("keyOneValue"), string("keyTwoValue")));
    }

    @Test
    public void shouldReturnEntrySet() {
        // given
        CaseInsensitiveRegexHashMap hashMap = hashMap(
            new String[]{"keyOne", "keyOneValue"},
            new String[]{"keyTwo", "keyTwoValue"}
        );

        // then
        assertThat(hashMap.entrySet(), containsInAnyOrder(
            entry("keyOne", "keyOneValue"),
            entry("keyTwo", "keyTwoValue")
        ));
    }
}
