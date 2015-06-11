package org.mockserver.collections.hashmap;

import org.junit.Test;
import org.mockserver.collections.CaseInsensitiveRegexHashMap;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.collection.IsIterableContainingInAnyOrder.containsInAnyOrder;
import static org.mockserver.collections.CaseInsensitiveRegexHashMap.hashMap;
import static org.mockserver.model.NottableString.string;

/**
 * @author jamesdbloom
 */
public class CaseInsensitiveRegexHashMapTestRemove {

    @Test
    public void shouldRemoveFromMapWithSingleEntry() {
        // given
        CaseInsensitiveRegexHashMap hashMap = hashMap(
                new String[]{"keyOne", "keyOneValue"}
        );

        // when
        hashMap.remove("keyOne");

        // then
        assertThat(hashMap.size(), is(0));
        assertThat(hashMap.get("keyOne"), is(nullValue()));
    }

    @Test
    public void shouldRemoveFromMapWithMultipleEntries() {
        // given
        CaseInsensitiveRegexHashMap hashMap = hashMap(
                new String[]{"keyOne", "keyOneValue"},
                new String[]{"keyTwo", "keyTwoValue"},
                new String[]{"keyThree", "keyThreeValue"}
        );

        // when
        hashMap.remove("keyOne");

        // then
        assertThat(hashMap.size(), is(2));
        assertThat(hashMap.get("keyOne"), is(nullValue()));
        assertThat(hashMap.get("keyTwo"), is(string("keyTwoValue")));
        assertThat(hashMap.get("keyThree"), is(string("keyThreeValue")));
    }

    @Test
    public void shouldRemoveNoMatchingEntry() {
        // given
        CaseInsensitiveRegexHashMap hashMap = hashMap(
                new String[]{"keyOne", "keyOneValue"},
                new String[]{"keyTwo", "keyTwoValue"},
                new String[]{"keyThree", "keyThreeValue"}
        );

        // when
        hashMap.remove("keyFour");

        // then
        assertThat(hashMap.size(), is(3));
        assertThat(hashMap.get("keyOne"), is(string("keyOneValue")));
        assertThat(hashMap.get("keyTwo"), is(string("keyTwoValue")));
        assertThat(hashMap.get("keyThree"), is(string("keyThreeValue")));
    }
}
