package org.mockserver.collections.multimap.nottablematcher;

import org.junit.Test;
import org.mockserver.collections.CaseInsensitiveRegexMultiMap;
import org.mockserver.model.NottableString;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsIterableContainingInAnyOrder.containsInAnyOrder;
import static org.mockserver.collections.CaseInsensitiveRegexMultiMap.multiMap;
import static org.mockserver.model.NottableString.not;
import static org.mockserver.model.NottableString.string;

/**
 * @author jamesdbloom
 */
@SuppressWarnings("SuspiciousMethodCalls")
public class CaseInsensitiveRegexMultiMapTestNottableGet {

    @Test
    public void shouldGetSingeValue() {
        // given
        CaseInsensitiveRegexMultiMap multiMap = multiMap(
            true, new NottableString[]{not("keyOne"), string("keyOne_valueOne")},
                new NottableString[]{not("keyTwo"), string("keyTwo_valueOne"), string("keyTwo_valueTwo")}
        );

        // then
        assertThat(multiMap.get("keyT.*"), is(string("keyOne_valueOne")));
        assertThat(multiMap.get(not("keyO.*")), is(string("keyOne_valueOne")));
    }

    @Test
    public void shouldGetFirstMultiValue() {
        // given
        CaseInsensitiveRegexMultiMap multiMap = multiMap(
            true, new NottableString[]{not("keyOne"), string("keyOne_valueOne")},
                new NottableString[]{not("keyTwo"), string("keyTwo_valueOne"), string("keyTwo_valueTwo")},
                new NottableString[]{not("keyThree"), string("keyThree_valueOne"), string("keyThree_valueTwo"), string("keyThree_valueThree")}
        );

        // then
        assertThat(multiMap.get("key.{1,4}e"), is(string("keyTwo_valueOne")));
        assertThat(multiMap.get(not("keyTwo")), is(string("keyTwo_valueOne")));
    }

    @Test
    public void shouldGetAllMultiValues() {
        // given
        CaseInsensitiveRegexMultiMap multiMap = multiMap(
            true, new NottableString[]{not("keyOne"), string("keyOne_valueOne")},
                new NottableString[]{not("keyTwo"), string("keyTwo_valueOne"), string("keyTwo_valueTwo")}
        );

        // then
        assertThat(multiMap.getAll("keyO.*"), containsInAnyOrder(string("keyTwo_valueOne"), string("keyTwo_valueTwo")));
        assertThat(multiMap.getAll(not("keyT.*")), containsInAnyOrder(string("keyTwo_valueOne"), string("keyTwo_valueTwo")));
    }

    @Test
    public void shouldGetAllSingeValue() {
        // given
        CaseInsensitiveRegexMultiMap multiMap = multiMap(
            true, new NottableString[]{not("keyOne"), string("keyOne_valueOne")},
                new NottableString[]{not("keyTwo"), string("keyTwo_valueOne"), string("keyTwo_valueTwo")}
        );

        // then
        assertThat(multiMap.getAll("keyT.*"), containsInAnyOrder(string("keyOne_valueOne")));
        assertThat(multiMap.getAll(not("keyO.*")), containsInAnyOrder(string("keyOne_valueOne")));
    }

    @Test
    public void shouldGetAllMultiValuesFromMultipleKeys() {
        // given
        CaseInsensitiveRegexMultiMap multiMap = multiMap(
            true, new NottableString[]{not("keyOne"), string("keyOne_valueOne")},
                new NottableString[]{not("keyTwo"), string("keyTwo_valueOne"), string("keyTwo_valueTwo")},
                new NottableString[]{not("keyThree"), string("keyThree_valueOne"), string("keyThree_valueTwo"), string("keyThree_valueThree")}
        );

        // then
        assertThat(multiMap.getAll("keyO.*"), containsInAnyOrder(string("keyTwo_valueOne"), string("keyTwo_valueTwo"), string("keyThree_valueOne"), string("keyThree_valueTwo"), string("keyThree_valueThree")));
        assertThat(multiMap.getAll(not("keyT.*")), containsInAnyOrder(string("keyTwo_valueOne"), string("keyTwo_valueTwo"), string("keyThree_valueOne"), string("keyThree_valueTwo"), string("keyThree_valueThree")));
    }

    @Test
    public void shouldGetAllSingeValueFromMultipleKeys() {
        // given
        CaseInsensitiveRegexMultiMap multiMap = multiMap(
            true, new NottableString[]{not("keyOne"), string("keyOne_valueOne")},
                new NottableString[]{not("keyTwo"), string("keyTwo_valueOne"), string("keyTwo_valueTwo")},
                new NottableString[]{not("keyThree"), string("keyThree_valueOne"), string("keyThree_valueTwo"), string("keyThree_valueThree")}
        );

        // then
        assertThat(multiMap.getAll("keyT.*"), containsInAnyOrder(string("keyOne_valueOne")));
        assertThat(multiMap.getAll(not("keyO.*")), containsInAnyOrder(string("keyOne_valueOne")));
    }
}
