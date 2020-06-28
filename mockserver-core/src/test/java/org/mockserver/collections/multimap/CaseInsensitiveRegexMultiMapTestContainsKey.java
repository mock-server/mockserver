package org.mockserver.collections.multimap;

import org.junit.Test;
import org.mockserver.collections.CaseInsensitiveRegexMultiMap;
import org.mockserver.model.KeyMatchStyle;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockserver.collections.CaseInsensitiveRegexMultiMap.multiMap;

/**
 * @author jamesdbloom
 */
public class CaseInsensitiveRegexMultiMapTestContainsKey {

    @Test
    public void singleValuedMapShouldContainKeyForSingleValue() {
        // given
        CaseInsensitiveRegexMultiMap multiMap = multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new String[]{"keyOne", "keyOne_valueOne"}
        );

        // then
        assertThat(multiMap.containsKey("keyOne"), is(true));
    }

    @Test
    public void multiValuedMapShouldContainKeyForSingleValue() {
        // given
        CaseInsensitiveRegexMultiMap multiMap = multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new String[]{"keyOne", "keyOne_valueOne"},
            new String[]{"keyTwo", "keyTwo_valueOne", "keyTwo_valueTwo"},
            new String[]{"keyThree", "keyThree_valueOne", "keyThree_valueTwo", "keyThree_valueThree"}
        );

        // then
        assertThat(multiMap.containsKey("keyOne"), is(true));
    }

    @Test
    public void singleValuedMapShouldContainKeyForMultipleValues() {
        // given
        CaseInsensitiveRegexMultiMap multiMap = multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new String[]{"keyTwo", "keyTwo_valueOne", "keyTwo_valueTwo"}
        );

        // then
        assertThat(multiMap.containsKey("keyTwo"), is(true));
    }

    @Test
    public void multiValuedMapShouldContainKeyForMultipleValues() {
        // given
        CaseInsensitiveRegexMultiMap multiMap = multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new String[]{"keyOne", "keyOne_valueOne"},
            new String[]{"keyTwo", "keyTwo_valueOne", "keyTwo_valueTwo"},
            new String[]{"keyThree", "keyThree_valueOne", "keyThree_valueTwo", "keyThree_valueThree"}
        );

        // then
        assertThat(multiMap.containsKey("keyTwo"), is(true));
    }

    @Test
    public void singleValuedMapShouldNotContainKeyForSingleValueWithKeyMismatch() {
        // given
        CaseInsensitiveRegexMultiMap multiMap = multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new String[]{"keyOne", "keyOne_valueOne"}
        );

        // then
        assertThat(multiMap.containsKey("notKeyOne"), is(false));
    }

    @Test
    public void multiValuedMapShouldNotContainKeyForSingleValueWithKeyMismatch() {
        // given
        CaseInsensitiveRegexMultiMap multiMap = multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new String[]{"keyOne", "keyOne_valueOne"},
            new String[]{"keyTwo", "keyTwo_valueOne", "keyTwo_valueTwo"},
            new String[]{"keyThree", "keyThree_valueOne", "keyThree_valueTwo", "keyThree_valueThree"}
        );

        // then
        assertThat(multiMap.containsKey("notKeyOne"), is(false));
    }

    @Test
    public void singleValuedMapShouldNotContainKeyForMultipleValuesWithKeyMismatch() {
        // given
        CaseInsensitiveRegexMultiMap multiMap = multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new String[]{"keyTwo", "keyTwo_valueOne", "keyTwo_valueTwo"}
        );

        // then
        assertThat(multiMap.containsKey("notKeyTwo"), is(false));
    }

    @Test
    public void multiValuedMapShouldNotContainKeyForMultipleValuesWithKeyMismatch() {
        // given
        CaseInsensitiveRegexMultiMap multiMap = multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new String[]{"keyOne", "keyOne_valueOne"},
            new String[]{"keyTwo", "keyTwo_valueOne", "keyTwo_valueTwo"},
            new String[]{"keyThree", "keyThree_valueOne", "keyThree_valueTwo", "keyThree_valueThree"}
        );

        // then
        assertThat(multiMap.containsKey("notKeyTwo"), is(false));
    }
}
