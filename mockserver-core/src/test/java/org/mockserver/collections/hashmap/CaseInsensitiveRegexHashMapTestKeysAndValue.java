package org.mockserver.collections.hashmap;

import org.junit.Test;
import org.mockserver.collections.CaseInsensitiveRegexHashMap;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.matchers.RegexStringMatcher;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsIterableContainingInAnyOrder.containsInAnyOrder;
import static org.mockserver.collections.CaseInsensitiveRegexHashMap.hashMap;
import static org.mockserver.collections.ImmutableEntry.entry;
import static org.mockserver.model.NottableString.string;

/**
 * @author jamesdbloom
 */
public class CaseInsensitiveRegexHashMapTestKeysAndValue {

    @Test
    public void shouldReturnKeysForMapWithSingleEntry() {
        // given
        CaseInsensitiveRegexHashMap hashMap = hashMap(
            true,
            new String[]{"keyOne", "keyOneValue"}
        );

        // then
        assertThat(hashMap.keySet(), containsInAnyOrder(string("keyOne")));
    }

    @Test
    public void shouldReturnKeysForMapWithMultipleEntries() {
        // given
        CaseInsensitiveRegexHashMap hashMap = hashMap(
            true,
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
            true,
            new String[]{"keyOne", "keyOneValue"}
        );

        // then
        assertThat(hashMap.values(), containsInAnyOrder(string("keyOneValue")));
    }

    @Test
    public void shouldReturnValuesForMapWithMultipleEntries() {
        // given
        CaseInsensitiveRegexHashMap hashMap = hashMap(
            true,
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
            true,
            new String[]{"keyOne", "keyOneValue"},
            new String[]{"keyTwo", "keyTwoValue"}
        );
        RegexStringMatcher regexStringMatcher = new RegexStringMatcher(new MockServerLogger(), false);

        // then
        assertThat(hashMap.entrySet(), containsInAnyOrder(
            entry(regexStringMatcher, "keyOne", "keyOneValue"),
            entry(regexStringMatcher, "keyTwo", "keyTwoValue")
        ));
    }
}
