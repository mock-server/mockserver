package org.mockserver.collections.hashmap.nottablematched;

import org.hamcrest.Matchers;
import org.junit.Test;
import org.mockserver.collections.CaseInsensitiveRegexHashMap;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.collection.IsIterableContainingInAnyOrder.containsInAnyOrder;
import static org.mockserver.collections.CaseInsensitiveRegexHashMap.hashMap;
import static org.mockserver.model.NottableString.not;
import static org.mockserver.model.NottableString.string;

/**
 * @author jamesdbloom
 */
public class CaseInsensitiveRegexHashMapTestNottableRemove {

    @Test
    public void shouldRemoveEntry() {
        // given
        CaseInsensitiveRegexHashMap hashMap = hashMap(
                new String[]{"keyOne", "keyOneValue"},
                new String[]{"keyTwo", "keyTwoValue"},
                new String[]{"keyThree", "keyThreeValue"}
        );

        // when
        assertThat(hashMap.remove(not("keyT.*")), is(string("keyOneValue")));

        // then
        assertThat(hashMap.size(), is(2));
        assertThat(hashMap.get("keyOne"), is(nullValue()));
        assertThat(hashMap.get("keyTwo"), is(string("keyTwoValue")));
        assertThat(hashMap.get("keyThree"), is(string("keyThreeValue")));
    }

    @Test
    public void shouldRemoveNotMatchingEntry() {
        // given
        CaseInsensitiveRegexHashMap hashMap = hashMap(
                new String[]{"keyOne", "keyOneValue"},
                new String[]{"keyTwo", "keyTwoValue"},
                new String[]{"keyThree", "keyThreeValue"}
        );

        // when
        assertThat(hashMap.remove(not("key.*")), is(Matchers.nullValue()));

        // then
        assertThat(hashMap.size(), is(3));
        assertThat(hashMap.get("keyOne"), is(string("keyOneValue")));
        assertThat(hashMap.get("keyTwo"), is(string("keyTwoValue")));
        assertThat(hashMap.get("keyThree"), is(string("keyThreeValue")));
    }
}
