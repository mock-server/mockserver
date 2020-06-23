package org.mockserver.collections.multimap.nottablematcher;

import org.junit.Test;
import org.mockserver.collections.CaseInsensitiveRegexMultiMap;
import org.mockserver.model.NottableString;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.collection.IsIterableContainingInAnyOrder.containsInAnyOrder;
import static org.mockserver.collections.CaseInsensitiveRegexMultiMap.multiMap;
import static org.mockserver.model.NottableString.not;
import static org.mockserver.model.NottableString.string;

/**
 * @author jamesdbloom
 */
@SuppressWarnings("SuspiciousMethodCalls")
public class CaseInsensitiveRegexMultiMapTestNottableRemove {

    @Test
    public void shouldRemoveSingleValueEntry() {
        // given
        CaseInsensitiveRegexMultiMap multiMap = multiMap(
            true, new NottableString[]{not("keyOne"), string("keyOne_valueOne")},
                new NottableString[]{not("keyTwo"), string("keyTwo_valueOne"), string("keyTwo_valueTwo")},
                new NottableString[]{not("keyThree"), string("keyThree_valueOne"), string("keyThree_valueTwo"), string("keyThree_valueThree")}
        );

        // when
        assertThat(multiMap.remove("keyT.*"), is(string("keyOne_valueOne")));

        // then
        assertThat(multiMap.size(), is(2));
        assertThat(multiMap.getAll("keyOne"), containsInAnyOrder(string("keyTwo_valueOne"), string("keyTwo_valueTwo"), string("keyThree_valueOne"), string("keyThree_valueTwo"), string("keyThree_valueThree")));
        assertThat(multiMap.getAll("keyTwo"), containsInAnyOrder(string("keyThree_valueOne"), string("keyThree_valueTwo"), string("keyThree_valueThree")));
        assertThat(multiMap.getAll("keyThree"), containsInAnyOrder(string("keyTwo_valueOne"), string("keyTwo_valueTwo")));
    }

    @Test
    public void shouldRemoveSingleValueEntryWithNottedKey() {
        // given
        CaseInsensitiveRegexMultiMap multiMap = multiMap(
            true, new NottableString[]{not("keyOne"), string("keyOne_valueOne")},
                new NottableString[]{not("keyTwo"), string("keyTwo_valueOne"), string("keyTwo_valueTwo")},
                new NottableString[]{not("keyThree"), string("keyThree_valueOne"), string("keyThree_valueTwo"), string("keyThree_valueThree")}
        );

        // when
        assertThat(multiMap.remove(not("keyO.*")), is(string("keyOne_valueOne")));

        // then
        assertThat(multiMap.size(), is(2));
        assertThat(multiMap.getAll("keyOne"), containsInAnyOrder(string("keyTwo_valueOne"), string("keyTwo_valueTwo"), string("keyThree_valueOne"), string("keyThree_valueTwo"), string("keyThree_valueThree")));
        assertThat(multiMap.getAll("keyTwo"), containsInAnyOrder(string("keyThree_valueOne"), string("keyThree_valueTwo"), string("keyThree_valueThree")));
        assertThat(multiMap.getAll("keyThree"), containsInAnyOrder(string("keyTwo_valueOne"), string("keyTwo_valueTwo")));
    }

    @Test
    public void shouldRemoveMultiValueEntry() {
        // given
        CaseInsensitiveRegexMultiMap multiMap = multiMap(
            true, new NottableString[]{not("keyOne"), string("keyOne_valueOne")},
                new NottableString[]{not("keyTwo"), string("keyTwo_valueOne"), string("keyTwo_valueTwo")},
                new NottableString[]{not("keyThree"), string("keyThree_valueOne"), string("keyThree_valueTwo"), string("keyThree_valueThree")}
        );

        // when
        assertThat(multiMap.remove("keyO.*"), is(string("keyTwo_valueOne")));

        // then
        assertThat(multiMap.size(), is(3));
        assertThat(multiMap.getAll("keyOne"), containsInAnyOrder(string("keyTwo_valueTwo"), string("keyThree_valueOne"), string("keyThree_valueTwo"), string("keyThree_valueThree")));
        assertThat(multiMap.getAll("keyTwo"), containsInAnyOrder(string("keyOne_valueOne"), string("keyThree_valueOne"), string("keyThree_valueTwo"), string("keyThree_valueThree")));
        assertThat(multiMap.getAll("keyThree"), containsInAnyOrder(string("keyOne_valueOne"), string("keyTwo_valueTwo")));
    }

    @Test
    public void shouldRemoveMultiValueEntryWithNottedKey() {
        // given
        CaseInsensitiveRegexMultiMap multiMap = multiMap(
            true, new NottableString[]{not("keyOne"), string("keyOne_valueOne")},
                new NottableString[]{not("keyTwo"), string("keyTwo_valueOne"), string("keyTwo_valueTwo")},
                new NottableString[]{not("keyThree"), string("keyThree_valueOne"), string("keyThree_valueTwo"), string("keyThree_valueThree")}
        );

        // when
        assertThat(multiMap.remove(not("keyT.*")), is(string("keyTwo_valueOne")));

        // then
        assertThat(multiMap.size(), is(3));
        assertThat(multiMap.getAll("keyOne"), containsInAnyOrder(string("keyTwo_valueTwo"), string("keyThree_valueOne"), string("keyThree_valueTwo"), string("keyThree_valueThree")));
        assertThat(multiMap.getAll("keyTwo"), containsInAnyOrder(string("keyOne_valueOne"), string("keyThree_valueOne"), string("keyThree_valueTwo"), string("keyThree_valueThree")));
        assertThat(multiMap.getAll("keyThree"), containsInAnyOrder(string("keyOne_valueOne"), string("keyTwo_valueTwo")));
    }

    @Test
    public void shouldRemoveNotMatchingEntry() {
        // given
        CaseInsensitiveRegexMultiMap multiMap = multiMap(
            true, new NottableString[]{not("keyOne"), string("keyOne_valueOne")},
                new NottableString[]{not("keyTwo"), string("keyTwo_valueOne"), string("keyTwo_valueTwo")},
                new NottableString[]{not("keyThree"), string("keyThree_valueOne"), string("keyThree_valueTwo"), string("keyThree_valueThree")}
        );

        // when
        assertThat(multiMap.remove("key.*"), nullValue());

        // then
        assertThat(multiMap.size(), is(3));
        assertThat(multiMap.getAll("keyOne"), containsInAnyOrder(string("keyTwo_valueOne"), string("keyTwo_valueTwo"), string("keyThree_valueOne"), string("keyThree_valueTwo"), string("keyThree_valueThree")));
        assertThat(multiMap.getAll("keyTwo"), containsInAnyOrder(string("keyOne_valueOne"), string("keyThree_valueOne"), string("keyThree_valueTwo"), string("keyThree_valueThree")));
        assertThat(multiMap.getAll("keyThree"), containsInAnyOrder(string("keyOne_valueOne"), string("keyTwo_valueOne"), string("keyTwo_valueTwo")));
    }

    @Test
    public void shouldRemoveNotMatchingEntryWithNottedKey() {
        // given
        CaseInsensitiveRegexMultiMap multiMap = multiMap(
            true, new NottableString[]{not("keyOne"), string("keyOne_valueOne")},
                new NottableString[]{not("keyTwo"), string("keyTwo_valueOne"), string("keyTwo_valueTwo")},
                new NottableString[]{not("keyThree"), string("keyThree_valueOne"), string("keyThree_valueTwo"), string("keyThree_valueThree")}
        );

        // when
        assertThat(multiMap.remove(not("notKey.*")), nullValue());

        // then
        assertThat(multiMap.size(), is(3));
        assertThat(multiMap.getAll("keyOne"), containsInAnyOrder(string("keyTwo_valueOne"), string("keyTwo_valueTwo"), string("keyThree_valueOne"), string("keyThree_valueTwo"), string("keyThree_valueThree")));
        assertThat(multiMap.getAll("keyTwo"), containsInAnyOrder(string("keyOne_valueOne"), string("keyThree_valueOne"), string("keyThree_valueTwo"), string("keyThree_valueThree")));
        assertThat(multiMap.getAll("keyThree"), containsInAnyOrder(string("keyOne_valueOne"), string("keyTwo_valueOne"), string("keyTwo_valueTwo")));
    }

    @Test
    public void shouldRemoveAllSingleValueEntry() {
        // given
        CaseInsensitiveRegexMultiMap multiMap = multiMap(
            true, new NottableString[]{not("keyOne"), string("keyOne_valueOne")},
                new NottableString[]{not("keyTwo"), string("keyTwo_valueOne"), string("keyTwo_valueTwo")},
                new NottableString[]{not("keyThree"), string("keyThree_valueOne"), string("keyThree_valueTwo"), string("keyThree_valueThree")}
        );

        // when
        assertThat(multiMap.removeAll("keyT.*"), containsInAnyOrder(string("keyOne_valueOne")));

        // then
        assertThat(multiMap.size(), is(2));
        assertThat(multiMap.getAll("keyOne"), containsInAnyOrder(string("keyTwo_valueOne"), string("keyTwo_valueTwo"), string("keyThree_valueOne"), string("keyThree_valueTwo"), string("keyThree_valueThree")));
        assertThat(multiMap.getAll("keyTwo"), containsInAnyOrder(string("keyThree_valueOne"), string("keyThree_valueTwo"), string("keyThree_valueThree")));
        assertThat(multiMap.getAll("keyThree"), containsInAnyOrder(string("keyTwo_valueOne"), string("keyTwo_valueTwo")));
    }

    @Test
    public void shouldRemoveAllSingleValueEntryWithNottedKey() {
        // given
        CaseInsensitiveRegexMultiMap multiMap = multiMap(
            true, new NottableString[]{not("keyOne"), string("keyOne_valueOne")},
                new NottableString[]{not("keyTwo"), string("keyTwo_valueOne"), string("keyTwo_valueTwo")},
                new NottableString[]{not("keyThree"), string("keyThree_valueOne"), string("keyThree_valueTwo"), string("keyThree_valueThree")}
        );

        // when
        assertThat(multiMap.removeAll(not("keyO.*")), containsInAnyOrder(string("keyOne_valueOne")));

        // then
        assertThat(multiMap.size(), is(2));
        assertThat(multiMap.getAll("keyOne"), containsInAnyOrder(string("keyTwo_valueOne"), string("keyTwo_valueTwo"), string("keyThree_valueOne"), string("keyThree_valueTwo"), string("keyThree_valueThree")));
        assertThat(multiMap.getAll("keyTwo"), containsInAnyOrder(string("keyThree_valueOne"), string("keyThree_valueTwo"), string("keyThree_valueThree")));
        assertThat(multiMap.getAll("keyThree"), containsInAnyOrder(string("keyTwo_valueOne"), string("keyTwo_valueTwo")));
    }

    @Test
    public void shouldRemoveAllMultiValueEntry() {
        // given
        CaseInsensitiveRegexMultiMap multiMap = multiMap(
            true, new NottableString[]{not("keyOne"), string("keyOne_valueOne")},
                new NottableString[]{not("keyTwo"), string("keyTwo_valueOne"), string("keyTwo_valueTwo")},
                new NottableString[]{not("keyThree"), string("keyThree_valueOne"), string("keyThree_valueTwo"), string("keyThree_valueThree")}
        );

        // when
        assertThat(multiMap.removeAll("key.{3}"), containsInAnyOrder(string("keyThree_valueOne"), string("keyThree_valueTwo"), string("keyThree_valueThree")));

        // then
        assertThat(multiMap.size(), is(2));
        assertThat(multiMap.getAll("keyOne"), containsInAnyOrder(string("keyTwo_valueOne"), string("keyTwo_valueTwo")));
        assertThat(multiMap.getAll("keyTwo"), containsInAnyOrder(string("keyOne_valueOne")));
        assertThat(multiMap.getAll("keyThree"), containsInAnyOrder(string("keyOne_valueOne"), string("keyTwo_valueOne"), string("keyTwo_valueTwo")));
    }

    @Test
    public void shouldRemoveAllMultiValueEntryWithNottedKey() {
        // given
        CaseInsensitiveRegexMultiMap multiMap = multiMap(
            true, new NottableString[]{not("keyOne"), string("keyOne_valueOne")},
                new NottableString[]{not("keyTwo"), string("keyTwo_valueOne"), string("keyTwo_valueTwo")},
                new NottableString[]{not("keyThree"), string("keyThree_valueOne"), string("keyThree_valueTwo"), string("keyThree_valueThree")}
        );

        // when
        assertThat(multiMap.removeAll(not("key.{5}")), containsInAnyOrder(string("keyThree_valueOne"), string("keyThree_valueTwo"), string("keyThree_valueThree")));

        // then
        assertThat(multiMap.size(), is(2));
        assertThat(multiMap.getAll("keyOne"), containsInAnyOrder(string("keyTwo_valueOne"), string("keyTwo_valueTwo")));
        assertThat(multiMap.getAll("keyTwo"), containsInAnyOrder(string("keyOne_valueOne")));
        assertThat(multiMap.getAll("keyThree"), containsInAnyOrder(string("keyOne_valueOne"), string("keyTwo_valueOne"), string("keyTwo_valueTwo")));
    }

    @Test
    public void shouldRemoveAllNoMatchingEntry() {
        // given
        CaseInsensitiveRegexMultiMap multiMap = multiMap(
            true, new NottableString[]{not("keyOne"), string("keyOne_valueOne")},
                new NottableString[]{not("keyTwo"), string("keyTwo_valueOne"), string("keyTwo_valueTwo")},
                new NottableString[]{not("keyThree"), string("keyThree_valueOne"), string("keyThree_valueTwo"), string("keyThree_valueThree")}
        );

        // when
        assertThat(multiMap.removeAll("key.*"), empty());

        // then
        assertThat(multiMap.size(), is(3));
        assertThat(multiMap.getAll("keyOne"), containsInAnyOrder(string("keyTwo_valueOne"), string("keyTwo_valueTwo"), string("keyThree_valueOne"), string("keyThree_valueTwo"), string("keyThree_valueThree")));
        assertThat(multiMap.getAll("keyTwo"), containsInAnyOrder(string("keyOne_valueOne"), string("keyThree_valueOne"), string("keyThree_valueTwo"), string("keyThree_valueThree")));
        assertThat(multiMap.getAll("keyThree"), containsInAnyOrder(string("keyOne_valueOne"), string("keyTwo_valueOne"), string("keyTwo_valueTwo")));
    }

    @Test
    public void shouldRemoveAllNoMatchingEntryWithNottedKey() {
        // given
        CaseInsensitiveRegexMultiMap multiMap = multiMap(
            true, new NottableString[]{not("keyOne"), string("keyOne_valueOne")},
                new NottableString[]{not("keyTwo"), string("keyTwo_valueOne"), string("keyTwo_valueTwo")},
                new NottableString[]{not("keyThree"), string("keyThree_valueOne"), string("keyThree_valueTwo"), string("keyThree_valueThree")}
        );

        // when
        assertThat(multiMap.removeAll(not("notKey.*")), empty());

        // then
        assertThat(multiMap.size(), is(3));
        assertThat(multiMap.getAll("keyOne"), containsInAnyOrder(string("keyTwo_valueOne"), string("keyTwo_valueTwo"), string("keyThree_valueOne"), string("keyThree_valueTwo"), string("keyThree_valueThree")));
        assertThat(multiMap.getAll("keyTwo"), containsInAnyOrder(string("keyOne_valueOne"), string("keyThree_valueOne"), string("keyThree_valueTwo"), string("keyThree_valueThree")));
        assertThat(multiMap.getAll("keyThree"), containsInAnyOrder(string("keyOne_valueOne"), string("keyTwo_valueOne"), string("keyTwo_valueTwo")));
    }
}
