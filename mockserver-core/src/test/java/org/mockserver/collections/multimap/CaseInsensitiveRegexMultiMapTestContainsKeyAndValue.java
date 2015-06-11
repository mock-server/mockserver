package org.mockserver.collections.multimap;

import org.junit.Test;
import org.mockserver.collections.CaseInsensitiveRegexMultiMap;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockserver.collections.CaseInsensitiveRegexMultiMap.multiMap;

/**
 * @author jamesdbloom
 */
public class CaseInsensitiveRegexMultiMapTestContainsKeyAndValue {

    @Test
    public void singleValuedMapShouldContainKeyAndValueForSingleValue() {
        // given
        CaseInsensitiveRegexMultiMap multiMap = multiMap(
                new String[]{"keyOne", "keyOne_valueOne"}
        );

        // then
        assertThat(multiMap.containsKeyValue("keyOne", "keyOne_valueOne"), is(true));
    }

    @Test
    public void multiValuedMapShouldContainKeyAndValueForSingleValue() {
        // given
        CaseInsensitiveRegexMultiMap multiMap = multiMap(
                new String[]{"keyOne", "keyOne_valueOne"},
                new String[]{"keyTwo", "keyTwo_valueOne", "keyTwo_valueTwo"},
                new String[]{"keyThree", "keyThree_valueOne", "keyThree_valueTwo", "keyThree_valueThree"}
        );

        // then
        assertThat(multiMap.containsKeyValue("keyOne", "keyOne_valueOne"), is(true));
    }

    @Test
    public void singleValuedMapShouldContainKeyAndValueForMultipleValues() {
        // given
        CaseInsensitiveRegexMultiMap multiMap = multiMap(
                new String[]{"keyTwo", "keyTwo_valueOne", "keyTwo_valueTwo"}
        );

        // then
        assertThat(multiMap.containsKeyValue("keyTwo", "keyTwo_valueOne"), is(true));
        assertThat(multiMap.containsKeyValue("keyTwo", "keyTwo_valueTwo"), is(true));
    }

    @Test
    public void multiValuedMapShouldContainKeyAndValueForMultipleValues() {
        // given
        CaseInsensitiveRegexMultiMap multiMap = multiMap(
                new String[]{"keyOne", "keyOne_valueOne"},
                new String[]{"keyTwo", "keyTwo_valueOne", "keyTwo_valueTwo"},
                new String[]{"keyThree", "keyThree_valueOne", "keyThree_valueTwo", "keyThree_valueThree"}
        );

        // then
        assertThat(multiMap.containsKeyValue("keyTwo", "keyTwo_valueOne"), is(true));
        assertThat(multiMap.containsKeyValue("keyTwo", "keyTwo_valueTwo"), is(true));
    }

    @Test
    public void singleValuedMapShouldNotContainKeyAndValueForSingleValueWithKeyMismatch() {
        // given
        CaseInsensitiveRegexMultiMap multiMap = multiMap(
                new String[]{"keyOne", "keyOne_valueOne"}
        );

        // then
        assertThat(multiMap.containsKeyValue("notKeyOne", "keyOne_valueOne"), is(false));
    }

    @Test
    public void singleValuedMapShouldNotContainKeyAndValueForSingleValueWithValueMismatch() {
        // given
        CaseInsensitiveRegexMultiMap multiMap = multiMap(
                new String[]{"keyOne", "keyOne_valueOne"}
        );

        // then
        assertThat(multiMap.containsKeyValue("keyOne", "notKeyOne_valueOne"), is(false));
    }

    @Test
    public void singleValuedMapShouldNotContainKeyAndValueForSingleValueWithKeyAndValueMismatch() {
        // given
        CaseInsensitiveRegexMultiMap multiMap = multiMap(
                new String[]{"keyOne", "keyOne_valueOne"}
        );

        // then
        assertThat(multiMap.containsKeyValue("notKeyOne", "notKeyOne_valueOne"), is(false));
    }

    @Test
    public void multiValuedMapShouldNotContainKeyAndValueForSingleValueWithKeyMismatch() {
        // given
        CaseInsensitiveRegexMultiMap multiMap = multiMap(
                new String[]{"keyOne", "keyOne_valueOne"},
                new String[]{"keyTwo", "keyTwo_valueOne", "keyTwo_valueTwo"},
                new String[]{"keyThree", "keyThree_valueOne", "keyThree_valueTwo", "keyThree_valueThree"}
        );

        // then
        assertThat(multiMap.containsKeyValue("notKeyOne", "keyOne_valueOne"), is(false));
    }

    @Test
    public void multiValuedMapShouldNotContainKeyAndValueForSingleValueWithValueMismatch() {
        // given
        CaseInsensitiveRegexMultiMap multiMap = multiMap(
                new String[]{"keyOne", "keyOne_valueOne"},
                new String[]{"keyTwo", "keyTwo_valueOne", "keyTwo_valueTwo"},
                new String[]{"keyThree", "keyThree_valueOne", "keyThree_valueTwo", "keyThree_valueThree"}
        );

        // then
        assertThat(multiMap.containsKeyValue("keyOne", "notKeyOne_valueOne"), is(false));
    }

    @Test
    public void multiValuedMapShouldNotContainKeyAndValueForSingleValueWithKeyAndValueMismatch() {
        // given
        CaseInsensitiveRegexMultiMap multiMap = multiMap(
                new String[]{"keyOne", "keyOne_valueOne"},
                new String[]{"keyTwo", "keyTwo_valueOne", "keyTwo_valueTwo"},
                new String[]{"keyThree", "keyThree_valueOne", "keyThree_valueTwo", "keyThree_valueThree"}
        );

        // then
        assertThat(multiMap.containsKeyValue("notKeyOne", "notKeyOne_valueOne"), is(false));
    }

    @Test
    public void singleValuedMapShouldNotContainKeyAndValueForMultipleValuesWithKeyMismatch() {
        // given
        CaseInsensitiveRegexMultiMap multiMap = multiMap(
                new String[]{"keyTwo", "keyTwo_valueOne", "keyTwo_valueTwo"}
        );

        // then
        assertThat(multiMap.containsKeyValue("notKeyTwo", "keyTwo_valueOne"), is(false));
        assertThat(multiMap.containsKeyValue("notKeyTwo", "keyTwo_valueTwo"), is(false));
    }

    @Test
    public void singleValuedMapShouldNotContainKeyAndValueForMultipleValuesWithValueMismatch() {
        // given
        CaseInsensitiveRegexMultiMap multiMap = multiMap(
                new String[]{"keyTwo", "keyTwo_valueOne", "keyTwo_valueTwo"}
        );

        // then
        assertThat(multiMap.containsKeyValue("keyTwo", "notKeyTwo_valueOne"), is(false));
        assertThat(multiMap.containsKeyValue("keyTwo", "notKeyTwo_valueTwo"), is(false));
    }

    @Test
    public void singleValuedMapShouldNotContainKeyAndValueForMultipleValuesWithKeyAndValueMismatch() {
        // given
        CaseInsensitiveRegexMultiMap multiMap = multiMap(
                new String[]{"keyTwo", "keyTwo_valueOne", "keyTwo_valueTwo"}
        );

        // then
        assertThat(multiMap.containsKeyValue("notKeyTwo", "notKeyTwo_valueOne"), is(false));
        assertThat(multiMap.containsKeyValue("notKeyTwo", "notKeyTwo_valueTwo"), is(false));
    }

    @Test
    public void multiValuedMapShouldNotContainKeyAndValueForMultipleValuesWithKeyMismatch() {
        // given
        CaseInsensitiveRegexMultiMap multiMap = multiMap(
                new String[]{"keyOne", "keyOne_valueOne"},
                new String[]{"keyTwo", "keyTwo_valueOne", "keyTwo_valueTwo"},
                new String[]{"keyThree", "keyThree_valueOne", "keyThree_valueTwo", "keyThree_valueThree"}
        );

        // then
        assertThat(multiMap.containsKeyValue("notKeyTwo", "keyTwo_valueOne"), is(false));
        assertThat(multiMap.containsKeyValue("notKeyTwo", "keyTwo_valueTwo"), is(false));
    }

    @Test
    public void multiValuedMapShouldNotContainKeyAndValueForMultipleValuesWithValueMismatch() {
        // given
        CaseInsensitiveRegexMultiMap multiMap = multiMap(
                new String[]{"keyOne", "keyOne_valueOne"},
                new String[]{"keyTwo", "keyTwo_valueOne", "keyTwo_valueTwo"},
                new String[]{"keyThree", "keyThree_valueOne", "keyThree_valueTwo", "keyThree_valueThree"}
        );

        // then
        assertThat(multiMap.containsKeyValue("keyTwo", "notKeyTwo_valueOne"), is(false));
        assertThat(multiMap.containsKeyValue("keyTwo", "notKeyTwo_valueTwo"), is(false));
    }

    @Test
    public void multiValuedMapShouldNotContainKeyAndValueForMultipleValuesWithKeyAndValueMismatch() {
        // given
        CaseInsensitiveRegexMultiMap multiMap = multiMap(
                new String[]{"keyOne", "keyOne_valueOne"},
                new String[]{"keyTwo", "keyTwo_valueOne", "keyTwo_valueTwo"},
                new String[]{"keyThree", "keyThree_valueOne", "keyThree_valueTwo", "keyThree_valueThree"}
        );

        // then
        assertThat(multiMap.containsKeyValue("notKeyTwo", "notKeyTwo_valueOne"), is(false));
        assertThat(multiMap.containsKeyValue("notKeyTwo", "notKeyTwo_valueTwo"), is(false));
    }
}
