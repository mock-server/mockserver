package org.mockserver.collections.multimap.nottablematchedandmatcher;

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
public class CaseInsensitiveRegexMultiMapTestNottableContainsAll {

    @Test
    public void shouldContainAllExactMatchSingleKeyAndSingleValueForNottedKey() {
        // given
        CaseInsensitiveRegexMultiMap multiMap = multiMap(
            true, new NottableString[]{not("keyOne"), string("keyOne_valueOne")}
        );

        // then
        assertThat(multiMap.containsAll(multiMap(
            true, new NottableString[]{not("keyOne"), string("keyOne_valueOne")}
        )), is(true));
    }

    @Test
    public void shouldContainAllExactMatchSingleKeyAndSingleValueForNottedValue() {
        // given
        CaseInsensitiveRegexMultiMap multiMap = multiMap(
            true, new NottableString[]{string("keyOne"), not("keyOne_valueOne")}
        );

        // then
        assertThat(multiMap.containsAll(multiMap(
            true, new NottableString[]{string("keyOne"), not("keyOne_valueOne")}
        )), is(true));
    }

    @Test
    public void shouldContainAllExactMatchSingleKeyAndSingleValueForNottedKeyAndValue() {
        // given
        CaseInsensitiveRegexMultiMap multiMap = multiMap(
            true, new NottableString[]{not("keyOne"), not("keyOne_valueOne")}
        );

        // then
        assertThat(multiMap.containsAll(multiMap(
            true, new NottableString[]{not("keyOne"), not("keyOne_valueOne")}
        )), is(true));
    }

    @Test
    public void shouldContainAllSubSetSingleKeyAndSingleValueForNottedKey() {
        // given
        CaseInsensitiveRegexMultiMap multiMap = multiMap(
            true, new NottableString[]{not("keyOne"), string("keyOne_valueOne")},
            new NottableString[]{string("keyTwo"), string("keyTwo_valueOne")},
            new NottableString[]{string("keyThree"), string("keyThree_valueOne"), string("keyThree_valueTwo")}
        );

        // then
        assertThat(multiMap.containsAll(multiMap(
            true, new NottableString[]{not("keyOne"), string("keyOne_valueOne")}
        )), is(true));
    }

    @Test
    public void shouldContainAllSubSetSingleKeyAndSingleValueForNottedValue() {
        // given
        CaseInsensitiveRegexMultiMap multiMap = multiMap(
            true, new NottableString[]{string("keyOne"), not("keyOne_valueOne")},
            new NottableString[]{string("keyTwo"), string("keyTwo_valueOne")},
            new NottableString[]{string("keyThree"), string("keyThree_valueOne"), string("keyThree_valueTwo")}
        );

        // then
        assertThat(multiMap.containsAll(multiMap(
            true, new NottableString[]{string("keyOne"), not("keyOne_valueOne")}
        )), is(true));
    }

    @Test
    public void shouldContainAllSubSetSingleKeyAndSingleValueForNottedKeyAndValue() {
        // given
        CaseInsensitiveRegexMultiMap multiMap = multiMap(
            true, new NottableString[]{not("keyOne"), not("keyOne_valueOne")},
            new NottableString[]{string("keyTwo"), string("keyTwo_valueOne")},
            new NottableString[]{string("keyThree"), string("keyThree_valueOne"), string("keyThree_valueTwo")}
        );

        // then
        assertThat(multiMap.containsAll(multiMap(
            true, new NottableString[]{not("keyOne"), string("notKeyOneValue")}
        )), is(true));
    }

    @Test
    public void shouldContainAllExactMatchMultipleKeyAndMultipleValuesForNottedKey() {
        // given
        CaseInsensitiveRegexMultiMap multiMap = multiMap(
            true, new NottableString[]{string("keyOne"), string("keyOne_valueOne")},
            new NottableString[]{not("keyTwo"), string("keyTwo_valueOne")}
        );

        // then
        assertThat(multiMap.containsAll(multiMap(
            true, new NottableString[]{string("keyOne"), string("keyOne_valueOne")},
            new NottableString[]{not("keyTwo"), string("keyTwo_valueOne")}
        )), is(true));
    }

    @Test
    public void shouldContainAllExactMatchMultipleKeyAndMultipleValuesForNottedValue() {
        // given
        CaseInsensitiveRegexMultiMap multiMap = multiMap(
            true, new NottableString[]{string("keyOne"), string("keyOne_valueOne")},
            new NottableString[]{string("keyTwo"), not("keyTwo_valueOne")}
        );

        // then
        assertThat(multiMap.containsAll(multiMap(
            true, new NottableString[]{string("keyOne"), string("keyOne_valueOne")},
            new NottableString[]{string("keyTwo"), not("keyTwo_valueOne")}
        )), is(true));
    }

    @Test
    public void shouldContainAllExactMatchMultipleKeyAndMultipleValuesForNottedKeyAndValue() {
        // given
        CaseInsensitiveRegexMultiMap multiMap = multiMap(
            true, new NottableString[]{string("keyOne"), string("keyOne_valueOne")},
            new NottableString[]{not("keyTwo"), not("keyTwo_valueOne")}
        );

        // then
        assertThat(multiMap.containsAll(multiMap(
            true, new NottableString[]{string("keyOne"), string("keyOne_valueOne")},
            new NottableString[]{not("keyTwo"), not("keyTwo_valueOne")}
        )), is(true));
    }

    @Test
    public void shouldContainAllSubSetMultipleKeyAndMultipleValuesForNottedKey() {
        // given
        CaseInsensitiveRegexMultiMap multiMap = multiMap(
            true, new NottableString[]{string("keyOne"), string("keyOne_valueOne")},
            new NottableString[]{not("keyTwo"), string("keyTwo_valueOne")},
            new NottableString[]{string("keyThree"), string("keyThree_valueOne"), string("keyThree_valueTwo")}
        );

        // then
        assertThat(multiMap.containsAll(multiMap(
            true, new NottableString[]{string("keyOne"), string("keyOne_valueOne")},
            new NottableString[]{not("keyTwo"), string("keyTwo_valueOne")}
        )), is(true));
    }

    @Test
    public void shouldContainAllSubSetMultipleKeyAndMultipleValuesForNottedValue() {
        // given
        CaseInsensitiveRegexMultiMap multiMap = multiMap(
            true, new NottableString[]{string("keyOne"), string("keyOne_valueOne")},
            new NottableString[]{string("keyTwo"), not("keyTwo_valueOne")},
            new NottableString[]{string("keyThree"), string("keyThree_valueOne"), string("keyThree_valueTwo")}
        );

        // then
        assertThat(multiMap.containsAll(multiMap(
            true, new NottableString[]{string("keyOne"), string("keyOne_valueOne")},
            new NottableString[]{string("keyTwo"), not("keyTwo_valueOne")}
        )), is(true));
    }

    @Test
    public void shouldContainAllSubSetMultipleKeyAndMultipleValuesForNottedKeyAndValue() {
        // given
        CaseInsensitiveRegexMultiMap multiMap = multiMap(
            true, new NottableString[]{string("keyOne"), string("keyOne_valueOne")},
            new NottableString[]{not("keyTwo"), not("keyTwo_valueOne")},
            new NottableString[]{string("keyThree"), string("keyThree_valueOne"), string("keyThree_valueTwo")}
        );

        // then
        assertThat(multiMap.containsAll(multiMap(
            true, new NottableString[]{string("keyOne"), string("keyOne_valueOne")},
            new NottableString[]{not("keyTwo"), not("keyTwo_valueOne")}
        )), is(true));
    }

    @Test
    public void shouldContainAllBothNottedMultipleKeysMultipleEntries() {
        // given
        CaseInsensitiveRegexMultiMap multiMap = multiMap(
            true, new NottableString[]{not("keyOne"), string("keyOne_valueOne")},
            new NottableString[]{not("keyTwo"), string("keyTwo_valueOne")},
            new NottableString[]{string("keyThree"), string("keyThree_valueOne"), string("keyThree_valueTwo")}
        );

        // then
        assertThat(multiMap.containsAll(multiMap(
            true, new NottableString[]{not("keyOne"), string("keyOne_valueOne")},
            new NottableString[]{not("keyTwo"), string("keyTwo_valueOne")}
        )), is(true));
    }

    @Test
    public void shouldContainAllBothNottedMultipleValuesMultipleEntries() {
        // given
        CaseInsensitiveRegexMultiMap multiMap = multiMap(
            true, new NottableString[]{string("keyOne"), not("keyOne_valueOne")},
            new NottableString[]{string("keyTwo"), not("keyTwo_valueOne")},
            new NottableString[]{string("keyThree"), string("keyThree_valueOne"), string("keyThree_valueTwo")}
        );

        // then
        assertThat(multiMap.containsAll(multiMap(
            true, new NottableString[]{string("keyOne"), not("keyOne_valueOne")},
            new NottableString[]{string("keyTwo"), not("keyTwo_valueOne")}
        )), is(true));
        assertThat(multiMap.containsAll(multiMap(
            true, new NottableString[]{string("keyTwo"), not("keyTwo.*")}
        )), is(true));
    }

    @Test
    public void shouldContainAllBothNottedMultipleKeyAndValuesMultipleEntries() {
        // given
        CaseInsensitiveRegexMultiMap multiMap = multiMap(
            true, new NottableString[]{not("keyOne"), not("keyOne_valueOne")},
            new NottableString[]{not("keyTwo"), not("keyTwo_valueOne")},
            new NottableString[]{string("keyThree"), string("keyThree_valueOne"), string("keyThree_valueTwo")}
        );

        // then
        assertThat(multiMap.containsAll(multiMap(
            true, new NottableString[]{not("keyOne"), not("keyOne_valueOne")},
            new NottableString[]{not("keyTwo"), not("keyTwo_valueOne")},
            new NottableString[]{string("keyThree"), string("keyThree_valueOne"), string("keyThree_valueTwo")}
        )), is(true));
        assertThat(multiMap.containsAll(multiMap(
            true, new NottableString[]{not("keyOne"), not("keyOne_valueOne")},
            new NottableString[]{not("keyTwo"), not("keyTwo_valueOne")}
        )), is(true));
        assertThat(multiMap.containsAll(multiMap(
            true, new NottableString[]{not("keyTwo"), not("keyTwo.*")}
        )), is(true));
    }

    @Test
    public void shouldNotContainAllNotMatchSingleKeySingleEntry() {
        // given
        CaseInsensitiveRegexMultiMap multiMap = multiMap(
            true, new NottableString[]{not("keyOne"), string("keyOne_valueOne")}
        );

        // then
        assertThat(multiMap.containsAll(multiMap(
            true, new NottableString[]{not("notKeyOne"), string("keyOne_valueOne")}
        )), is(false));
    }

    @Test
    public void shouldNotContainAllNotMatchSingleValueSingleEntry() {
        // given
        CaseInsensitiveRegexMultiMap multiMap = multiMap(
            true, new NottableString[]{string("keyOne"), not("keyOne_valueOne")}
        );

        // then
        assertThat(multiMap.containsAll(multiMap(
            true, new NottableString[]{string("keyOne"), not("notKeyOne_valueOne")}
        )), is(false));
    }

    @Test
    public void shouldNotContainAllNotMatchSingleKeyMultipleEntries() {
        // given
        CaseInsensitiveRegexMultiMap multiMap = multiMap(
            true, new NottableString[]{not("keyOne"), string("keyOne_valueOne")},
            new NottableString[]{not("keyTwo"), string("keyTwo_valueOne"), string("keyTwo_valueTwo")},
            new NottableString[]{not("keyThree"), string("keyThree_valueOne"), string("keyThree_valueTwo"), string("keyThree_valueThree")}
        );

        // then
        assertThat(multiMap.containsAll(multiMap(
            true, new NottableString[]{not("notKeyOne"), string("keyOne_valueOne")}
        )), is(false));
    }

    @Test
    public void shouldNotContainAllNotMatchSingleValueMultipleEntries() {
        // given
        CaseInsensitiveRegexMultiMap multiMap = multiMap(
            true, new NottableString[]{string("keyOne"), not("keyOne_valueOne")},
            new NottableString[]{string("keyTwo"), not("keyTwo_valueOne"), not("keyTwo_valueTwo")},
            new NottableString[]{string("keyThree"), not("keyThree_valueOne"), not("keyThree_valueTwo"), not("keyThree_valueThree")}
        );

        // then
        assertThat(multiMap.containsAll(multiMap(
            true, new NottableString[]{string("keyOne"), not("notKeyOne_valueOne")}
        )), is(false));
        assertThat(multiMap.containsAll(multiMap(
            true, new NottableString[]{string("keyThree_valueOne"), not("keyThree_valueTwo"), not("keyThree_valueThree")}
        )), is(false));
    }

    @Test
    public void shouldNotContainAllNotMatchMultipleKeysMultipleEntries() {
        // given
        CaseInsensitiveRegexMultiMap multiMap = multiMap(
            true, new NottableString[]{not("keyOne"), string("keyOne_valueOne")},
            new NottableString[]{not("keyTwo"), string("keyTwo_valueOne"), string("keyTwo_valueTwo")},
            new NottableString[]{not("keyThree"), string("keyThree_valueOne"), string("keyThree_valueTwo"), string("keyThree_valueThree")}
        );

        // then
        assertThat(multiMap.containsAll(multiMap(
            true, new NottableString[]{not("notKeyOne"), string("keyOne_valueOne")},
            new NottableString[]{not("notKeyTwo"), string("keyTwo_valueOne"), string("keyTwo_valueTwo")}
        )), is(false));
    }

    @Test
    public void shouldNotContainAllNotMatchMultipleValuesMultipleEntries() {
        // given
        CaseInsensitiveRegexMultiMap multiMap = multiMap(
            true, new NottableString[]{string("keyOne"), not("keyOne_valueOne")},
            new NottableString[]{string("keyTwo"), not("keyTwo_valueOne"), not("keyTwo_valueTwo")},
            new NottableString[]{string("keyThree"), not("keyThree_valueOne"), not("keyThree_valueTwo"), not("keyThree_valueThree")}
        );

        // then
        assertThat(multiMap.containsAll(multiMap(
            true, new NottableString[]{string("keyOne"), not("notKeyOne_valueOne")},
            new NottableString[]{string("keyTwo"), not("keyTwo_valueOne"), not("notKeyTwo_valueTwo")}
        )), is(false));
        assertThat(multiMap.containsAll(multiMap(
            true, new NottableString[]{string("keyTwo"), not("notKeyTwo_valueOne"), not("notKeyTwo_valueTwo")}
        )), is(false));
    }

}
