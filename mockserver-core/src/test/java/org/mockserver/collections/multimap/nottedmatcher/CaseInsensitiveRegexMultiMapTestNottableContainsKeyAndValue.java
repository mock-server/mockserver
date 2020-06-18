package org.mockserver.collections.multimap.nottedmatcher;

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
public class CaseInsensitiveRegexMultiMapTestNottableContainsKeyAndValue {

    @Test
    public void singleValuedMapShouldContainKeyAndValueForSingleValueForNottedKey() {
        // given
        CaseInsensitiveRegexMultiMap multiMap = multiMap(
            true, new NottableString[]{not("keyOne"), string("keyOne_valueOne")}
        );

        // then
        assertThat(multiMap.containsKeyValue("notKeyOne", "keyOne_valueOne"), is(true));
        assertThat(multiMap.containsKeyValue(not("keyOne"), string("keyOne_valueOne")), is(true));
    }

    @Test
    public void singleValuedMapShouldContainKeyAndValueForSingleValueForNottedValue() {
        // given
        CaseInsensitiveRegexMultiMap multiMap = multiMap(
            true, new NottableString[]{string("keyOne"), not("keyOne_valueOne")}
        );

        // then
        assertThat(multiMap.containsKeyValue("keyOne", "notKeyOne_valueOne"), is(true));
        assertThat(multiMap.containsKeyValue(string("keyOne"), not("keyOne_valueOne")), is(true));
    }

    @Test
    public void singleValuedMapShouldContainKeyAndValueForSingleValueForNottedKeyAndValue() {
        // given
        CaseInsensitiveRegexMultiMap multiMap = multiMap(
            true, new NottableString[]{not("keyOne"), not("keyOne_valueOne")}
        );

        // then
        assertThat(multiMap.containsKeyValue("notKeyOne", "notKeyOne_valueOne"), is(true));
        assertThat(multiMap.containsKeyValue(not("keyOne"), not("keyOne_valueOne")), is(true));
    }

    @Test
    public void multiValuedMapShouldContainKeyAndValueForSingleValueForNottedKey() {
        // given
        CaseInsensitiveRegexMultiMap multiMap = multiMap(
            true, new NottableString[]{not("keyOne"), string("keyOne_valueOne")},
                new NottableString[]{string("keyTwo"), string("keyTwo_valueOne"), string("keyTwo_valueTwo")}
        );

        // then
        assertThat(multiMap.containsKeyValue("notKeyOne", "keyOne_valueOne"), is(true));
        assertThat(multiMap.containsKeyValue(not("keyOne"), string("keyOne_valueOne")), is(true));
    }

    @Test
    public void multiValuedMapShouldContainKeyAndValueForSingleValueForNottedValue() {
        // given
        CaseInsensitiveRegexMultiMap multiMap = multiMap(
            true, new NottableString[]{string("keyOne"), not("keyOne_valueOne")},
                new NottableString[]{string("keyTwo"), string("keyTwo_valueOne"), string("keyTwo_valueTwo")}
        );

        // then
        assertThat(multiMap.containsKeyValue("keyOne", "notKeyOne_valueOne"), is(true));
        assertThat(multiMap.containsKeyValue(string("keyOne"), not("keyOne_valueOne")), is(true));
    }

    @Test
    public void multiValuedMapShouldContainKeyAndValueForSingleValueForNottedKeyAndValue() {
        // given
        CaseInsensitiveRegexMultiMap multiMap = multiMap(
            true, new NottableString[]{not("keyOne"), not("keyOne_valueOne")},
                new NottableString[]{string("keyTwo"), string("keyTwo_valueOne"), string("keyTwo_valueTwo")}
        );

        // then
        assertThat(multiMap.containsKeyValue("notKeyOne", "notKeyOne_valueOne"), is(true));
        assertThat(multiMap.containsKeyValue(not("keyOne"), not("keyOne_valueOne")), is(true));
    }

    @Test
    public void singleValuedMapShouldContainKeyAndValueForMultipleValuesForNottedKey() {
        // given
        CaseInsensitiveRegexMultiMap multiMap = multiMap(
            true, new NottableString[]{not("keyTwo"), string("keyTwo_valueOne"), string("keyTwo_valueTwo")}
        );

        // then
        assertThat(multiMap.containsKeyValue("notKeyTwo", "keyTwo_valueOne"), is(true));
        assertThat(multiMap.containsKeyValue("notKeyTwo", "keyTwo_valueTwo"), is(true));
        assertThat(multiMap.containsKeyValue(not("keyTwo"), string("keyTwo_valueOne")), is(true));
        assertThat(multiMap.containsKeyValue(not("keyTwo"), string("keyTwo_valueTwo")), is(true));
    }

    @Test
    public void singleValuedMapShouldContainKeyAndValueForMultipleValuesForNottedValue() {
        // given
        CaseInsensitiveRegexMultiMap multiMap = multiMap(
            true, new NottableString[]{string("keyTwo"), not("keyTwo_valueOne"), string("keyTwo_valueTwo")}
        );

        // then
        assertThat(multiMap.containsKeyValue("keyTwo", "notKey.*"), is(true));
        assertThat(multiMap.containsKeyValue(string("keyTwo"), not("keyTwo_valueOne")), is(true));
    }

    @Test
    public void singleValuedMapShouldContainKeyAndValueForMultipleValuesForNottedKeyAndValue() {
        // given
        CaseInsensitiveRegexMultiMap multiMap = multiMap(
            true, new NottableString[]{not("keyTwo"), not("keyTwo_valueOne"), not("keyTwo_valueTwo")}
        );

        // then
        assertThat(multiMap.containsKeyValue("notKeyTwo", "notKey.*"), is(true));
        assertThat(multiMap.containsKeyValue(not("keyTwo"), not("key.*")), is(true));
    }

    @Test
    public void multiValuedMapShouldContainKeyAndValueForMultipleValuesForNottedKey() {
        // given
        CaseInsensitiveRegexMultiMap multiMap = multiMap(
            true, new NottableString[]{string("keyOne"), string("keyOne_valueOne")},
                new NottableString[]{not("keyTwo"), string("keyTwo_valueOne"), string("keyTwo_valueTwo")},
                new NottableString[]{string("keyThree"), string("keyThree_valueOne"), string("keyThree_valueTwo"), string("keyThree_valueThree")}
        );

        // then
        assertThat(multiMap.containsKeyValue("key.*e", "keyTwo_valueOne"), is(true));
        assertThat(multiMap.containsKeyValue("key.*e", "keyTwo_valueTwo"), is(true));
        assertThat(multiMap.containsKeyValue(not("keyTwo"), string("keyTwo_valueOne")), is(true));
        assertThat(multiMap.containsKeyValue(not("keyTwo"), string("keyTwo_valueTwo")), is(true));
    }

    @Test
    public void multiValuedMapShouldContainKeyAndValueForMultipleValuesForNottedValue() {
        // given
        CaseInsensitiveRegexMultiMap multiMap = multiMap(
            true, new NottableString[]{string("keyOne"), string("keyOne_valueOne")},
                new NottableString[]{string("keyTwo"), not("keyTwo_valueOne"), string("keyTwo_valueTwo")},
                new NottableString[]{string("keyThree"), string("keyThree_valueOne"), string("keyThree_valueTwo"), string("keyThree_valueThree")}
        );

        // then
        assertThat(multiMap.containsKeyValue("keyTwo", "notKey.*"), is(true));
        assertThat(multiMap.containsKeyValue(not("key.*e"), not("key.*")), is(true));
    }

    @Test
    public void multiValuedMapShouldContainKeyAndValueForMultipleValuesForNottedKeyAndValue() {
        // given
        CaseInsensitiveRegexMultiMap multiMap = multiMap(
            true, new NottableString[]{string("keyOne"), string("keyOne_valueOne")},
                new NottableString[]{not("keyTwo"), not("keyTwo_valueOne"), not("keyTwo_valueTwo")},
                new NottableString[]{string("keyThree"), string("keyThree_valueOne"), string("keyThree_valueTwo"), string("keyThree_valueThree")}
        );

        // then
        assertThat(multiMap.containsKeyValue("notKey.*", "notKey.*"), is(true));
        assertThat(multiMap.containsKeyValue(not("key.*"), not("key.*")), is(true));
    }

    @Test
    public void singleValuedMapShouldNotContainKeyAndValueForSingleValueWithValueMismatchForNottedKey() {
        // given
        CaseInsensitiveRegexMultiMap multiMap = multiMap(
            true, new NottableString[]{not("keyOne"), string("keyOne_valueOne")}
        );

        // then
        assertThat(multiMap.containsKeyValue("keyOne", "keyOne_valueOne"), is(false));
        assertThat(multiMap.containsKeyValue(not("notKey.*"), string("keyOne_valueOne")), is(false));
    }

    @Test
    public void singleValuedMapShouldNotContainKeyAndValueForSingleValueWithValueMismatchForNottedValue() {
        // given
        CaseInsensitiveRegexMultiMap multiMap = multiMap(
            true, new NottableString[]{string("keyOne"), not("keyOne_valueOne")}
        );

        // then
        assertThat(multiMap.containsKeyValue("keyOne", "keyOne_valueOne"), is(false));
        assertThat(multiMap.containsKeyValue(string("keyOne"), not("notKey.*")), is(false));
    }

    @Test
    public void singleValuedMapShouldNotContainKeyAndValueForSingleValueWithValueMismatchForNottedKeyAndValue() {
        // given
        CaseInsensitiveRegexMultiMap multiMap = multiMap(
            true, new NottableString[]{not("keyOne"), not("keyOne_valueOne")}
        );

        // then
        assertThat(multiMap.containsKeyValue("keyOne", "keyOne_valueOne"), is(false));
        assertThat(multiMap.containsKeyValue(not("notKey.*"), not("notKey.*")), is(false));
    }

    @Test
    public void singleValuedMapShouldNotContainKeyAndValueForMultipleValuesWithValueMismatchForNottedKey() {
        // given
        CaseInsensitiveRegexMultiMap multiMap = multiMap(
            true, new NottableString[]{not("keyTwo"), string("keyTwo_valueOne"), string("keyTwo_valueTwo")}
        );

        // then
        assertThat(multiMap.containsKeyValue("keyTwo", "keyTwo_valueOne"), is(false));
        assertThat(multiMap.containsKeyValue("keyTwo", "keyTwo_valueTwo"), is(false));
        assertThat(multiMap.containsKeyValue(not("notKey.*"), string("keyTwo_valueOne")), is(false));
        assertThat(multiMap.containsKeyValue(not("notKey.*"), string("keyTwo_valueTwo")), is(false));
    }

    @Test
    public void singleValuedMapShouldNotContainKeyAndValueForMultipleValuesWithValueMismatchForNottedValue() {
        // given
        CaseInsensitiveRegexMultiMap multiMap = multiMap(
            true, new NottableString[]{string("keyTwo"), not("keyTwo_valueOne"), string("keyTwo_valueTwo")}
        );

        // then
        assertThat(multiMap.containsKeyValue("keyOne", "keyTwo_valueOne"), is(false));
        assertThat(multiMap.containsKeyValue(string("keyTwo"), not("keyTwo_valueTwo|notKey.*")), is(false));
    }

    @Test
    public void singleValuedMapShouldNotContainKeyAndValueForMultipleValuesWithValueMismatchForNottedKeyAndValue() {
        // given
        CaseInsensitiveRegexMultiMap multiMap = multiMap(
            true, new NottableString[]{not("keyTwo"), not("keyTwo_valueOne"), not("keyTwo_valueTwo")}
        );

        // then
        assertThat(multiMap.containsKeyValue("keyOne", "keyTwo_valueTwo|keyTwo_valueOne"), is(false));
        assertThat(multiMap.containsKeyValue(not("notKey.*"), not("notKey.*")), is(false));
    }

    @Test
    public void multiValuedMapShouldNotContainKeyAndValueForMultipleValuesWithValueMismatchForNottedKey() {
        // given
        CaseInsensitiveRegexMultiMap multiMap = multiMap(
            true, new NottableString[]{string("keyOne"), string("keyOne_valueOne")},
                new NottableString[]{not("keyTwo"), string("keyTwo_valueOne"), string("keyTwo_valueTwo")},
                new NottableString[]{string("keyThree"), string("keyThree_valueOne"), string("keyThree_valueTwo"), string("keyThree_valueThree")}
        );

        // then
        assertThat(multiMap.containsKeyValue("keyTwo", "keyTwo_valueOne"), is(false));
        assertThat(multiMap.containsKeyValue("keyTwo", "keyTwo_valueTwo"), is(false));
        assertThat(multiMap.containsKeyValue(not("notKey.*"), string("keyTwo_valueOne")), is(false));
        assertThat(multiMap.containsKeyValue(not("notKey.*"), string("keyTwo_valueTwo")), is(false));
    }

    @Test
    public void multiValuedMapShouldNotContainKeyAndValueForMultipleValuesWithValueMismatchForNottedValue() {
        // given
        CaseInsensitiveRegexMultiMap multiMap = multiMap(
            true, new NottableString[]{string("keyOne"), string("keyOne_valueOne")},
                new NottableString[]{string("keyTwo"), not("keyTwo_valueOne"), string("keyTwo_valueTwo")},
                new NottableString[]{string("keyThree"), string("keyThree_valueOne"), string("keyThree_valueTwo"), string("keyThree_valueThree")}
        );

        // then
        assertThat(multiMap.containsKeyValue("keyTwo", "keyTwo_valueOne"), is(false));
        assertThat(multiMap.containsKeyValue(string("keyTwo"), not("keyTwo_valueTwo|notKey.*")), is(false));
    }

    @Test
    public void multiValuedMapShouldNotContainKeyAndValueForMultipleValuesWithValueMismatchForNottedKeyAndValue() {
        // given
        CaseInsensitiveRegexMultiMap multiMap = multiMap(
            true, new NottableString[]{string("keyOne"), string("keyOne_valueOne")},
                new NottableString[]{not("keyTwo"), not("keyTwo_valueOne"), not("keyTwo_valueTwo")},
                new NottableString[]{string("keyThree"), string("keyThree_valueOne"), string("keyThree_valueTwo"), string("keyThree_valueThree")}
        );

        // then
        assertThat(multiMap.containsKeyValue("keyTwo", "keyTwo_valueTwo|keyTwo_valueOne"), is(false));
        assertThat(multiMap.containsKeyValue(not("key.*e"), not("key.*e_.*")), is(false));
    }
}
