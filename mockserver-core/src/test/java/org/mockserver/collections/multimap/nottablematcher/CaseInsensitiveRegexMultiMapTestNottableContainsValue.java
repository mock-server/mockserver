package org.mockserver.collections.multimap.nottablematcher;

import org.junit.Test;
import org.mockserver.collections.CaseInsensitiveRegexMultiMap;
import org.mockserver.model.KeyMatchStyle;
import org.mockserver.model.NottableString;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockserver.collections.CaseInsensitiveRegexMultiMap.multiMap;
import static org.mockserver.model.NottableString.not;
import static org.mockserver.model.NottableString.string;

/**
 * @author jamesdbloom
 */
@SuppressWarnings("SuspiciousMethodCalls")
public class CaseInsensitiveRegexMultiMapTestNottableContainsValue {

    @Test
    public void singleValuedMapShouldContainValueForSingleValue() {
        // given
        CaseInsensitiveRegexMultiMap multiMap = multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{string("keyOne"), not("keyOne_valueOne")}
        );

        // then
        assertThat(multiMap.containsValue(not("key.*")), is(true));
        assertThat(multiMap.containsValue("notKey.*"), is(true));
    }

    @Test
    public void multiValuedMapShouldContainValueForSingleValue() {
        // given
        CaseInsensitiveRegexMultiMap multiMap = multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{string("keyOne"), not("keyOne_valueOne")},
            new NottableString[]{string("keyTwo"), string("keyTwo_valueOne"), string("keyTwo_valueTwo")},
            new NottableString[]{string("keyThree"), string("keyThree_valueOne"), string("keyThree_valueTwo"), string("keyThree_valueThree")}
        );

        // then
        assertThat(multiMap.containsValue(not("key.*")), is(true));
        assertThat(multiMap.containsValue("notKey.*"), is(true));
    }

    @Test
    public void singleValuedMapShouldContainValueForMultipleValues() {
        // given
        CaseInsensitiveRegexMultiMap multiMap = multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{string("keyTwo"), not("keyTwo_valueOne"), not("keyTwo_valueTwo")}
        );

        // then
        assertThat(multiMap.containsValue(not("key.*")), is(true));
        assertThat(multiMap.containsValue("notKey.*"), is(true));
        assertThat(multiMap.containsValue("keyTwo_valueOne"), is(true));
        assertThat(multiMap.containsValue("keyTwo_valueTwo"), is(true));
    }

    @Test
    public void multiValuedMapShouldContainValueForMultipleValues() {
        // given
        CaseInsensitiveRegexMultiMap multiMap = multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{string("keyOne"), string("keyOne_valueOne")},
            new NottableString[]{string("keyTwo"), not("keyTwo_valueOne"), not("keyTwo_valueTwo")},
            new NottableString[]{string("keyThree"), string("keyThree_valueOne"), string("keyThree_valueTwo"), string("keyThree_valueThree")}
        );

        // then
        assertThat(multiMap.containsValue(not("key.*")), is(true));
        assertThat(multiMap.containsValue("notKey.*"), is(true));
        assertThat(multiMap.containsValue("keyTwo_valueOne"), is(true));
        assertThat(multiMap.containsValue("keyTwo_valueTwo"), is(true));
    }

    @Test
    public void singleValuedMapShouldNotContainValueForSingleValueWithValueMismatch() {
        // given
        CaseInsensitiveRegexMultiMap multiMap = multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{string("keyOne"), not("keyOne_valueOne")}
        );

        // then
        assertThat(multiMap.containsValue("keyOne_valueOne"), is(false));
    }

    @Test
    public void singleValuedMapShouldNotContainValueForMultipleValuesWithValueMismatch() {
        // given
        CaseInsensitiveRegexMultiMap multiMap = multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{string("keyTwo"), not("keyTwo_valueOne"), not("keyTwo_valueTwo")}
        );

        // then
        assertThat(multiMap.containsValue(not("notKey.*")), is(false));
        assertThat(multiMap.containsValue("key.*"), is(false));
    }

    @Test
    public void multiValuedMapShouldNotContainValueForMultipleValuesWithValueMismatch() {
        // given
        CaseInsensitiveRegexMultiMap multiMap = multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{string("keyOne"), not("keyOne_valueOne")},
            new NottableString[]{string("keyTwo"), not("keyTwo_valueOne"), not("keyTwo_valueTwo")},
            new NottableString[]{string("keyThree"), not("keyThree_valueOne"), not("keyThree_valueTwo"), not("keyThree_valueThree")}
        );

        // then
        assertThat(multiMap.containsValue(not("notKey.*")), is(false));
        assertThat(multiMap.containsValue("key.*"), is(false));
    }
}
