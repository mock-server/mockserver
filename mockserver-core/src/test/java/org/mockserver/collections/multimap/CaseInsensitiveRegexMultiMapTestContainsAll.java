package org.mockserver.collections.multimap;

import org.junit.Test;
import org.mockserver.collections.CaseInsensitiveRegexMultiMap;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockserver.collections.CaseInsensitiveRegexMultiMap.multiMap;

/**
 * @author jamesdbloom
 */
public class CaseInsensitiveRegexMultiMapTestContainsAll {

    @Test
    public void shouldContainAllExactMatchSingleKeyAndSingleValue() {
        // given
        CaseInsensitiveRegexMultiMap multiMap = multiMap(
            true, new String[]{"keyOne", "keyOne_valueOne"}
        );

        // then
        assertThat(multiMap.containsAll(multiMap(
            true, new String[]{"keyOne", "keyOne_valueOne"}
        )), is(true));
    }

    @Test
    public void shouldContainAllSubSetSingleKeyAndSingleValue() {
        // given
        CaseInsensitiveRegexMultiMap multiMap = multiMap(
            true, new String[]{"keyOne", "keyOne_valueOne"},
                new String[]{"keyTwo", "keyTwo_valueOne", "keyTwo_valueTwo"},
                new String[]{"keyThree", "keyThree_valueOne", "keyThree_valueTwo", "keyThree_valueThree"}
        );

        // then
        assertThat(multiMap.containsAll(multiMap(
            true, new String[]{"keyOne", "keyOne_valueOne"}
        )), is(true));
    }

    @Test
    public void shouldContainAllExactMatchSingleKeyAndMultipleValues() {
        // given
        CaseInsensitiveRegexMultiMap multiMap = multiMap(
            true, new String[]{"keyTwo", "keyTwo_valueOne", "keyTwo_valueTwo"}
        );

        // then
        assertThat(multiMap.containsAll(multiMap(
            true, new String[]{"keyTwo", "keyTwo_valueOne", "keyTwo_valueTwo"}
        )), is(true));
    }

    @Test
    public void shouldContainAllSubSetSingleKeyAndMultipleValues() {
        // given
        CaseInsensitiveRegexMultiMap multiMap = multiMap(
            true, new String[]{"keyOne", "keyOne_valueOne"},
                new String[]{"keyTwo", "keyTwo_valueOne", "keyTwo_valueTwo"},
                new String[]{"keyThree", "keyThree_valueOne", "keyThree_valueTwo", "keyThree_valueThree"}
        );

        // then
        assertThat(multiMap.containsAll(multiMap(
            true, new String[]{"keyTwo", "keyTwo_valueOne", "keyTwo_valueTwo"}
        )), is(true));
    }

    @Test
    public void shouldContainAllExactMatchMultipleKeyAndMultipleValues() {
        // given
        CaseInsensitiveRegexMultiMap multiMap = multiMap(
            true, new String[]{"keyOne", "keyOne_valueOne"},
                new String[]{"keyTwo", "keyTwo_valueOne", "keyTwo_valueTwo"}
        );

        // then
        assertThat(multiMap.containsAll(multiMap(
            true, new String[]{"keyOne", "keyOne_valueOne"},
                new String[]{"keyTwo", "keyTwo_valueOne", "keyTwo_valueTwo"}
        )), is(true));
    }

    @Test
    public void shouldContainAllSubSetMultipleKeyAndMultipleValues() {
        // given
        CaseInsensitiveRegexMultiMap multiMap = multiMap(
            true, new String[]{"keyOne", "keyOne_valueOne"},
                new String[]{"keyTwo", "keyTwo_valueOne", "keyTwo_valueTwo"},
                new String[]{"keyThree", "keyThree_valueOne", "keyThree_valueTwo", "keyThree_valueThree"}
        );

        // then
        assertThat(multiMap.containsAll(multiMap(
            true, new String[]{"keyOne", "keyOne_valueOne"},
                new String[]{"keyTwo", "keyTwo_valueOne", "keyTwo_valueTwo"}
        )), is(true));
    }

    @Test
    public void shouldNotContainAllNotMatchSingleKeySingleEntry() {
        // given
        CaseInsensitiveRegexMultiMap multiMap = multiMap(
            true, new String[]{"keyOne", "keyOne_valueOne"}
        );

        // then
        assertThat(multiMap.containsAll(multiMap(
            true, new String[]{"notKeyOne", "keyOne_valueOne"}
        )), is(false));
    }

    @Test
    public void shouldNotContainAllNotMatchSingleValueSingleEntry() {
        // given
        CaseInsensitiveRegexMultiMap multiMap = multiMap(
            true, new String[]{"keyOne", "keyOne_valueOne"}
        );

        // then
        assertThat(multiMap.containsAll(multiMap(
            true, new String[]{"keyOne", "notKeyOne_valueOne"}
        )), is(false));
    }

    @Test
    public void shouldNotContainAllNotMatchSingleKeyMultipleEntries() {
        // given
        CaseInsensitiveRegexMultiMap multiMap = multiMap(
            true, new String[]{"keyOne", "keyOne_valueOne"},
                new String[]{"keyTwo", "keyTwo_valueOne", "keyTwo_valueTwo"},
                new String[]{"keyThree", "keyThree_valueOne", "keyThree_valueTwo", "keyThree_valueThree"}
        );

        // then
        assertThat(multiMap.containsAll(multiMap(
            true, new String[]{"notKeyOne", "keyOne_valueOne"}
        )), is(false));
    }

    @Test
    public void shouldNotContainAllNotMatchSingleValueMultipleEntries() {
        // given
        CaseInsensitiveRegexMultiMap multiMap = multiMap(
            true, new String[]{"keyOne", "keyOne_valueOne"},
                new String[]{"keyTwo", "keyTwo_valueOne", "keyTwo_valueTwo"},
                new String[]{"keyThree", "keyThree_valueOne", "keyThree_valueTwo", "keyThree_valueThree"}
        );

        // then
        assertThat(multiMap.containsAll(multiMap(
            true, new String[]{"keyOne", "notKeyOne_valueOne"}
        )), is(false));
        assertThat(multiMap.containsAll(multiMap(
            true, new String[]{"keyTwo", "keyTwo_valueOne", "notKeyTwo_valueTwo"}
        )), is(false));
    }

    @Test
    public void shouldNotContainAllNotMatchMultipleKeysMultipleEntries() {
        // given
        CaseInsensitiveRegexMultiMap multiMap = multiMap(
            true, new String[]{"keyOne", "keyOne_valueOne"},
                new String[]{"keyTwo", "keyTwo_valueOne", "keyTwo_valueTwo"},
                new String[]{"keyThree", "keyThree_valueOne", "keyThree_valueTwo", "keyThree_valueThree"}
        );

        // then
        assertThat(multiMap.containsAll(multiMap(
            true, new String[]{"notKeyOne", "keyOne_valueOne"},
                new String[]{"notKeyTwo", "keyTwo_valueOne", "keyTwo_valueTwo"}
        )), is(false));
    }

    @Test
    public void shouldNotContainAllNotMatchMultipleValuesMultipleEntries() {
        // given
        CaseInsensitiveRegexMultiMap multiMap = multiMap(
            true, new String[]{"keyOne", "keyOne_valueOne"},
                new String[]{"keyTwo", "keyTwo_valueOne", "keyTwo_valueTwo"},
                new String[]{"keyThree", "keyThree_valueOne", "keyThree_valueTwo", "keyThree_valueThree"}
        );

        // then
        assertThat(multiMap.containsAll(multiMap(
            true, new String[]{"keyOne", "notKeyOne_valueOne"},
                new String[]{"keyTwo", "keyTwo_valueOne", "notKeyTwo_valueTwo"}
        )), is(false));
        assertThat(multiMap.containsAll(multiMap(
            true, new String[]{"keyTwo", "notKeyTwo_valueOne", "notKeyTwo_valueTwo"}
        )), is(false));
    }
}
