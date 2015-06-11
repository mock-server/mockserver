package org.mockserver.collections.multimap.nottablematcher;

import org.junit.Ignore;
import org.junit.Test;
import org.mockserver.collections.CaseInsensitiveRegexMultiMap;
import org.mockserver.model.NottableString;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockserver.collections.CaseInsensitiveRegexMultiMap.multiMap;
import static org.mockserver.model.NottableString.not;
import static org.mockserver.model.NottableString.string;

/**
 * @author jamesdbloom
 */
public class CaseInsensitiveRegexMultiMapTestNottableContainsKey {

    @Test
    public void singleValuedMapShouldContainValueForSingleValueForNottedKey() {
        // given
        CaseInsensitiveRegexMultiMap multiMap = multiMap(
                new NottableString[]{not("keyOne"), string("keyOne_valueOne")}
        );

        // then
        assertThat(multiMap.containsKey("notKeyOne"), is(true));
        assertThat(multiMap.containsKey(not("keyOne")), is(true));
    }

    @Test
    public void multiValuedMapShouldContainValueForSingleValueForNottedKey() {
        // given
        CaseInsensitiveRegexMultiMap multiMap = multiMap(
                new NottableString[]{not("keyOne"), string("keyOne_valueOne")},
                new NottableString[]{string("keyTwo"), string("keyTwo_valueOne"), string("keyTwo_valueTwo")}
        );

        // then
        assertThat(multiMap.containsKey("notKeyOne"), is(true));
        assertThat(multiMap.containsKey(not("keyOne")), is(true));
    }

    @Test
    public void singleValuedMapShouldContainValueForMultipleValuesForNottedKey() {
        // given
        CaseInsensitiveRegexMultiMap multiMap = multiMap(
                new NottableString[]{not("keyTwo"), string("keyTwo_valueOne"), string("keyTwo_valueTwo")}
        );

        // then
        assertThat(multiMap.containsKey("notKeyTwo"), is(true));
        assertThat(multiMap.containsKey(not("keyTwo")), is(true));
    }

    @Test
    public void multiValuedMapShouldContainValueForMultipleValuesForNottedKey() {
        // given
        CaseInsensitiveRegexMultiMap multiMap = multiMap(
                new NottableString[]{string("keyOne"), string("keyOne_valueOne")},
                new NottableString[]{not("keyTwo"), string("keyTwo_valueOne"), string("keyTwo_valueTwo")},
                new NottableString[]{string("keyThree"), string("keyThree_valueOne"), string("keyThree_valueTwo"), string("keyThree_valueThree")}
        );

        // then
        assertThat(multiMap.containsKey("key.*e"), is(true));
        assertThat(multiMap.containsKey(not("keyTwo")), is(true));
    }

    @Test
    public void singleValuedMapShouldNotContainValueForSingleValueWithValueMismatchForNottedKey() {
        // given
        CaseInsensitiveRegexMultiMap multiMap = multiMap(
                new NottableString[]{not("keyOne"), string("keyOne_valueOne")}
        );

        // then
        assertThat(multiMap.containsKey("keyOne"), is(false));
        assertThat(multiMap.containsKey(not("notKey.*")), is(false));
    }

    @Test
    public void singleValuedMapShouldNotContainValueForMultipleValuesWithValueMismatchForNottedKey() {
        // given
        CaseInsensitiveRegexMultiMap multiMap = multiMap(
                new NottableString[]{not("keyTwo"), string("keyTwo_valueOne"), string("keyTwo_valueTwo")}
        );

        // then
        assertThat(multiMap.containsKey("keyTwo"), is(false));
        assertThat(multiMap.containsKey(not("notKey.*")), is(false));
    }

    @Test
    public void multiValuedMapShouldNotContainValueForMultipleValuesWithValueMismatchForNottedKey() {
        // given
        CaseInsensitiveRegexMultiMap multiMap = multiMap(
                new NottableString[]{string("keyOne"), string("keyOne_valueOne")},
                new NottableString[]{not("keyTwo"), string("keyTwo_valueOne"), string("keyTwo_valueTwo")},
                new NottableString[]{string("keyThree"), string("keyThree_valueOne"), string("keyThree_valueTwo"), string("keyThree_valueThree")}
        );

        // then
        assertThat(multiMap.containsKey("keyTwo"), is(false));
        assertThat(multiMap.containsKey(not("key.*e")), is(false));
    }
}
