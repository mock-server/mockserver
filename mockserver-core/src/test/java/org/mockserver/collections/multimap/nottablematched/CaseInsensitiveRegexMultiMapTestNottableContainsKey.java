package org.mockserver.collections.multimap.nottablematched;

import org.junit.Test;
import org.mockserver.collections.CaseInsensitiveRegexMultiMap;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockserver.collections.CaseInsensitiveRegexMultiMap.multiMap;
import static org.mockserver.model.NottableString.not;

/**
 * @author jamesdbloom
 */
public class CaseInsensitiveRegexMultiMapTestNottableContainsKey {

    @Test
    public void singleValuedMapShouldContainKeyForSingleValue() {
        // given
        CaseInsensitiveRegexMultiMap multiMap = multiMap(
            true, new String[]{"keyOne", "keyOne_valueOne"}
        );

        // then
        assertThat(multiMap.containsKey(not("notKeyOne")), is(true));
    }

    @Test
    public void multiValuedMapShouldContainKeyForSingleValue() {
        // given
        CaseInsensitiveRegexMultiMap multiMap = multiMap(
            true, new String[]{"keyOne", "keyOne_valueOne"},
                new String[]{"keyTwo", "keyTwo_valueOne", "keyTwo_valueTwo"},
                new String[]{"keyThree", "keyThree_valueOne", "keyThree_valueTwo", "keyThree_valueThree"}
        );

        // then
        assertThat(multiMap.containsKey(not("notKeyOne")), is(true));
    }

    @Test
    public void singleValuedMapShouldContainKeyForMultipleValues() {
        // given
        CaseInsensitiveRegexMultiMap multiMap = multiMap(
            true, new String[]{"keyTwo", "keyTwo_valueOne", "keyTwo_valueTwo"}
        );

        // then
        assertThat(multiMap.containsKey(not("notKeyTwo")), is(true));
    }

    @Test
    public void multiValuedMapShouldContainKeyForMultipleValues() {
        // given
        CaseInsensitiveRegexMultiMap multiMap = multiMap(
            true, new String[]{"keyOne", "keyOne_valueOne"},
                new String[]{"keyTwo", "keyTwo_valueOne", "keyTwo_valueTwo"},
                new String[]{"keyThree", "keyThree_valueOne", "keyThree_valueTwo", "keyThree_valueThree"}
        );

        // then
        assertThat(multiMap.containsKey(not("notKeyTwo")), is(true));
    }

    @Test
    public void singleValuedMapShouldNotContainKeyForSingleValueWithKeyMismatch() {
        // given
        CaseInsensitiveRegexMultiMap multiMap = multiMap(
            true, new String[]{"keyOne", "keyOne_valueOne"}
        );

        // then
        assertThat(multiMap.containsKey(not("keyOne")), is(false));
    }

    @Test
    public void singleValuedMapShouldNotContainKeyForMultipleValuesWithKeyMismatch() {
        // given
        CaseInsensitiveRegexMultiMap multiMap = multiMap(
            true, new String[]{"keyTwo", "keyTwo_valueOne", "keyTwo_valueTwo"}
        );

        // then
        assertThat(multiMap.containsKey(not("keyTwo")), is(false));
    }

    @Test
    public void multiValuedMapShouldNotContainKeyForMultipleValuesWithKeyMismatch() {
        // given
        CaseInsensitiveRegexMultiMap multiMap = multiMap(
            true, new String[]{"keyOne", "keyOne_valueOne"},
                new String[]{"keyTwo", "keyTwo_valueOne", "keyTwo_valueTwo"},
                new String[]{"keyThree", "keyThree_valueOne", "keyThree_valueTwo", "keyThree_valueThree"}
        );

        // then
        assertThat(multiMap.containsKey(not("key.*")), is(false));
    }
}
