package org.mockserver.collections.hashmap.nottablematcher;

import org.junit.Test;
import org.mockserver.collections.CaseInsensitiveRegexHashMap;
import org.mockserver.model.NottableString;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockserver.collections.CaseInsensitiveRegexHashMap.hashMap;
import static org.mockserver.model.NottableString.not;
import static org.mockserver.model.NottableString.string;

/**
 * @author jamesdbloom
 */
public class CaseInsensitiveRegexHashMapTestNottableRemove {

    @Test
    public void shouldRemoveSingleValueEntry() {
        // given
        CaseInsensitiveRegexHashMap hashMap = hashMap(
            true, new NottableString[]{not("keyOne"), string("keyOneValue")},
            new NottableString[]{not("keyTwo"), string("keyTwoValue")},
            new NottableString[]{not("keyThree"), string("keyThreeValue")}
        );

        // when
        assertThat(hashMap.remove("keyT.*"), is(string("keyOneValue")));

        // then
        assertThat(hashMap.size(), is(2));
        assertThat(hashMap.get("keyOne"), is(string("keyTwoValue")));
        assertThat(hashMap.get("keyTwo"), is(string("keyThreeValue")));
        assertThat(hashMap.get("keyThree"), is(string("keyTwoValue")));
    }

    @Test
    public void shouldRemoveSingleValueEntryWithNottedKey() {
        // given
        CaseInsensitiveRegexHashMap hashMap = hashMap(
            true, new NottableString[]{not("keyOne"), string("keyOneValue")},
            new NottableString[]{not("keyTwo"), string("keyTwoValue")},
            new NottableString[]{not("keyThree"), string("keyThreeValue")}
        );

        // when
        assertThat(hashMap.remove(not("keyO.*")), is(string("keyOneValue")));

        // then
        assertThat(hashMap.size(), is(2));
        assertThat(hashMap.get("keyOne"), is(string("keyTwoValue")));
        assertThat(hashMap.get("keyTwo"), is(string("keyThreeValue")));
        assertThat(hashMap.get("keyThree"), is(string("keyTwoValue")));
    }

    @Test
    public void shouldRemoveNotMatchingEntry() {
        // given
        CaseInsensitiveRegexHashMap hashMap = hashMap(
            true, new NottableString[]{not("keyOne"), string("keyOneValue")},
            new NottableString[]{not("keyTwo"), string("keyTwoValue")},
            new NottableString[]{not("keyThree"), string("keyThreeValue")}
        );

        // when
        assertThat(hashMap.remove("key.*"), nullValue());

        // then
        assertThat(hashMap.size(), is(3));
        assertThat(hashMap.get("keyOne"), is(string("keyTwoValue")));
        assertThat(hashMap.get("keyTwo"), is(string("keyOneValue")));
        assertThat(hashMap.get("keyThree"), is(string("keyOneValue")));
    }

    @Test
    public void shouldRemoveNotMatchingEntryWithNottedKey() {
        // given
        CaseInsensitiveRegexHashMap hashMap = hashMap(
            true, new NottableString[]{not("keyOne"), string("keyOneValue")},
            new NottableString[]{not("keyTwo"), string("keyTwoValue")},
            new NottableString[]{not("keyThree"), string("keyThreeValue")}
        );

        // when
        assertThat(hashMap.remove(not("key.*")), is(string("keyOneValue")));

        // then
        assertThat(hashMap.size(), is(2));
        assertThat(hashMap.get("keyOne"), is(string("keyTwoValue")));
        assertThat(hashMap.get("keyTwo"), is(string("keyThreeValue")));
        assertThat(hashMap.get("keyThree"), is(string("keyTwoValue")));
    }
}
