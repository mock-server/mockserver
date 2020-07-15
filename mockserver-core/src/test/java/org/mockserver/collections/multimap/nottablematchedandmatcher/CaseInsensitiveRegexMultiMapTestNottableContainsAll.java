package org.mockserver.collections.multimap.nottablematchedandmatcher;

import org.junit.Test;
import org.mockserver.collections.NottableStringMultiMap;
import org.mockserver.model.KeyMatchStyle;
import org.mockserver.model.NottableString;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockserver.collections.NottableStringMultiMap.multiMap;
import static org.mockserver.model.NottableString.not;
import static org.mockserver.model.NottableString.string;

/**
 * @author jamesdbloom
 */
public class CaseInsensitiveRegexMultiMapTestNottableContainsAll {

    @Test
    public void shouldContainAllExactMatchSingleKeyAndSingleValueForNottedKey() {
        // given
        NottableStringMultiMap multiMap = multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{not("keyOne"), string("keyOne_valueOne")}
        );

        // then
        assertThat(multiMap.containsAll(multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{not("keyOne"), string("keyOne_valueOne")}
        )), is(true));
    }

    @Test
    public void shouldContainAllExactMatchSingleKeyAndSingleValueForNottedValue() {
        // given
        NottableStringMultiMap multiMap = multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{string("keyOne"), not("keyOne_valueOne")}
        );

        // then
        assertThat(multiMap.containsAll(multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{string("keyOne"), not("keyOne_valueOne")}
        )), is(true));
    }

    @Test
    public void shouldContainAllExactMatchSingleKeyAndSingleValueForNottedKeyAndValue() {
        // given
        NottableStringMultiMap multiMap = multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{not("keyOne"), not("keyOne_valueOne")}
        );

        // then
        assertThat(multiMap.containsAll(multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{not("keyOne"), not("keyOne_valueOne")}
        )), is(true));
    }

    @Test
    public void shouldContainAllSubSetSingleKeyAndSingleValueForNottedKey() {
        // given
        NottableStringMultiMap multiMap = multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{not("keyOne"), string("keyOne_valueOne")},
            new NottableString[]{string("keyTwo"), string("keyTwo_valueOne")},
            new NottableString[]{string("keyThree"), string("keyThree_valueOne"), string("keyThree_valueTwo")}
        );

        // then
        assertThat(multiMap.containsAll(multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{not("keyOne"), string("keyOne_valueOne")}
        )), is(true));
    }

    @Test
    public void shouldContainAllSubSetSingleKeyAndSingleValueForNottedValue() {
        // given
        NottableStringMultiMap multiMap = multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{string("keyOne"), not("keyOne_valueOne")},
            new NottableString[]{string("keyTwo"), string("keyTwo_valueOne")},
            new NottableString[]{string("keyThree"), string("keyThree_valueOne"), string("keyThree_valueTwo")}
        );

        // then
        assertThat(multiMap.containsAll(multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{string("keyOne"), not("keyOne_valueOne")}
        )), is(true));
    }

    @Test
    public void shouldContainAllSubSetSingleKeyAndSingleValueForNottedKeyAndValue() {
        // given
        NottableStringMultiMap multiMap = multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{not("keyOne"), not("keyOne_valueOne")},
            new NottableString[]{string("keyTwo"), string("keyTwo_valueOne")},
            new NottableString[]{string("keyThree"), string("keyThree_valueOne"), string("keyThree_valueTwo")}
        );

        // then
        assertThat(multiMap.containsAll(multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{not("keyOne"), string("notKeyOneValue")}
        )), is(true));
    }

    @Test
    public void shouldContainAllExactMatchMultipleKeyAndMultipleValuesForNottedKey() {
        // given
        NottableStringMultiMap multiMap = multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{string("keyOne"), string("keyOne_valueOne")},
            new NottableString[]{not("keyTwo"), string("keyTwo_valueOne")}
        );

        // then
        assertThat(multiMap.containsAll(multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{string("keyOne"), string("keyOne_valueOne")},
            new NottableString[]{not("keyTwo"), string("keyTwo_valueOne")}
        )), is(true));
    }

    @Test
    public void shouldContainAllExactMatchMultipleKeyAndMultipleValuesForNottedValue() {
        // given
        NottableStringMultiMap multiMap = multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{string("keyOne"), string("keyOne_valueOne")},
            new NottableString[]{string("keyTwo"), not("keyTwo_valueOne")}
        );

        // then
        assertThat(multiMap.containsAll(multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{string("keyOne"), string("keyOne_valueOne")},
            new NottableString[]{string("keyTwo"), not("keyTwo_valueOne")}
        )), is(true));
    }

    @Test
    public void shouldContainAllExactMatchMultipleKeyAndMultipleValuesForNottedKeyAndValue() {
        // given
        NottableStringMultiMap multiMap = multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{string("keyOne"), string("keyOne_valueOne")},
            new NottableString[]{not("keyTwo"), not("keyTwo_valueOne")}
        );

        // then
        assertThat(multiMap.containsAll(multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{string("keyOne"), string("keyOne_valueOne")},
            new NottableString[]{not("keyTwo"), not("keyTwo_valueOne")}
        )), is(true));
    }

    @Test
    public void shouldContainAllSubSetMultipleKeyAndMultipleValuesForNottedKey() {
        // given
        NottableStringMultiMap multiMap = multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{string("keyOne"), string("keyOne_valueOne")},
            new NottableString[]{not("keyTwo"), string("keyTwo_valueOne")},
            new NottableString[]{string("keyThree"), string("keyThree_valueOne"), string("keyThree_valueTwo")}
        );

        // then
        assertThat(multiMap.containsAll(multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{string("keyOne"), string("keyOne_valueOne")},
            new NottableString[]{not("keyTwo"), string("keyTwo_valueOne")}
        )), is(true));
    }

    @Test
    public void shouldContainAllSubSetMultipleKeyAndMultipleValuesForNottedValue() {
        // given
        NottableStringMultiMap multiMap = multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{string("keyOne"), string("keyOne_valueOne")},
            new NottableString[]{string("keyTwo"), not("keyTwo_valueOne")},
            new NottableString[]{string("keyThree"), string("keyThree_valueOne"), string("keyThree_valueTwo")}
        );

        // then
        assertThat(multiMap.containsAll(multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{string("keyOne"), string("keyOne_valueOne")},
            new NottableString[]{string("keyTwo"), not("keyTwo_valueOne")}
        )), is(true));
    }

    @Test
    public void shouldContainAllSubSetMultipleKeyAndMultipleValuesForNottedKeyAndValue() {
        // given
        NottableStringMultiMap multiMap = multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{string("keyOne"), string("keyOne_valueOne")},
            new NottableString[]{not("keyTwo"), not("keyTwo_valueOne")},
            new NottableString[]{string("keyThree"), string("keyThree_valueOne"), string("keyThree_valueTwo")}
        );

        // then
        assertThat(multiMap.containsAll(multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{string("keyOne"), string("keyOne_valueOne")},
            new NottableString[]{not("keyTwo"), not("keyTwo_valueOne")}
        )), is(true));
    }

    @Test
    public void shouldContainAllBothNottedMultipleKeysMultipleEntries() {
        // given
        NottableStringMultiMap multiMap = multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{not("keyOne"), string("keyOne_valueOne")},
            new NottableString[]{not("keyTwo"), string("keyTwo_valueOne")},
            new NottableString[]{string("keyThree"), string("keyThree_valueOne"), string("keyThree_valueTwo")}
        );

        // then
        assertThat(multiMap.containsAll(multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{not("keyOne"), string("keyOne_valueOne")},
            new NottableString[]{not("keyTwo"), string("keyTwo_valueOne")}
        )), is(true));
    }

    @Test
    public void shouldContainAllBothNottedMultipleValuesMultipleEntries() {
        // given
        NottableStringMultiMap multiMap = multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{string("keyOne"), not("keyOne_valueOne")},
            new NottableString[]{string("keyTwo"), not("keyTwo_valueOne")},
            new NottableString[]{string("keyThree"), string("keyThree_valueOne"), string("keyThree_valueTwo")}
        );

        // then
        assertThat(multiMap.containsAll(multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{string("keyOne"), not("keyOne_valueOne")},
            new NottableString[]{string("keyTwo"), not("keyTwo_valueOne")}
        )), is(true));
        assertThat(multiMap.containsAll(multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{string("keyTwo"), not("keyTwo.*")}
        )), is(true));
    }

    @Test
    public void shouldContainAllBothNottedMultipleKeyAndValuesMultipleEntries() {
        // given
        NottableStringMultiMap multiMap = multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{not("keyOne"), not("keyOne_valueOne")},
            new NottableString[]{not("keyTwo"), not("keyTwo_valueOne")},
            new NottableString[]{string("keyThree"), string("keyThree_valueOne"), string("keyThree_valueTwo")}
        );

        // then
        assertThat(multiMap.containsAll(multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{not("keyOne"), not("keyOne_valueOne")},
            new NottableString[]{not("keyTwo"), not("keyTwo_valueOne")},
            new NottableString[]{string("keyThree"), string("keyThree_valueOne"), string("keyThree_valueTwo")}
        )), is(true));
        assertThat(multiMap.containsAll(multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{not("keyOne"), not("keyOne_valueOne")},
            new NottableString[]{not("keyTwo"), not("keyTwo_valueOne")}
        )), is(true));
        assertThat(multiMap.containsAll(multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{not("keyTwo"), not("keyTwo.*")}
        )), is(true));
    }

    @Test
    public void shouldNotContainAllNotMatchSingleKeySingleEntry() {
        // given
        NottableStringMultiMap multiMap = multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{not("keyOne"), string("keyOne_valueOne")}
        );

        // then
        assertThat(multiMap.containsAll(multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{not("notKeyOne"), string("keyOne_valueOne")}
        )), is(false));
    }

    @Test
    public void shouldNotContainAllNotMatchSingleValueSingleEntry() {
        // given
        NottableStringMultiMap multiMap = multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{string("keyOne"), not("keyOne_valueOne")}
        );

        // then
        assertThat(multiMap.containsAll(multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{string("keyOne"), not("notKeyOne_valueOne")}
        )), is(false));
    }

    @Test
    public void shouldNotContainAllNotMatchSingleKeyMultipleEntries() {
        // given
        NottableStringMultiMap multiMap = multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{not("keyOne"), string("keyOne_valueOne")},
            new NottableString[]{not("keyTwo"), string("keyTwo_valueOne"), string("keyTwo_valueTwo")},
            new NottableString[]{not("keyThree"), string("keyThree_valueOne"), string("keyThree_valueTwo"), string("keyThree_valueThree")}
        );

        // then
        assertThat(multiMap.containsAll(multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{not("notKeyOne"), string("keyOne_valueOne")}
        )), is(false));
    }

    @Test
    public void shouldNotContainAllNotMatchSingleValueMultipleEntries() {
        // given
        NottableStringMultiMap multiMap = multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{string("keyOne"), not("keyOne_valueOne")},
            new NottableString[]{string("keyTwo"), not("keyTwo_valueOne"), not("keyTwo_valueTwo")},
            new NottableString[]{string("keyThree"), not("keyThree_valueOne"), not("keyThree_valueTwo"), not("keyThree_valueThree")}
        );

        // then
        assertThat(multiMap.containsAll(multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{string("keyOne"), not("notKeyOne_valueOne")}
        )), is(false));
        assertThat(multiMap.containsAll(multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{string("keyThree_valueOne"), not("keyThree_valueTwo"), not("keyThree_valueThree")}
        )), is(false));
    }

    @Test
    public void shouldNotContainAllNotMatchMultipleKeysMultipleEntries() {
        // given
        NottableStringMultiMap multiMap = multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{not("keyOne"), string("keyOne_valueOne")},
            new NottableString[]{not("keyTwo"), string("keyTwo_valueOne"), string("keyTwo_valueTwo")},
            new NottableString[]{not("keyThree"), string("keyThree_valueOne"), string("keyThree_valueTwo"), string("keyThree_valueThree")}
        );

        // then
        assertThat(multiMap.containsAll(multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{not("notKeyOne"), string("keyOne_valueOne")},
            new NottableString[]{not("notKeyTwo"), string("keyTwo_valueOne"), string("keyTwo_valueTwo")}
        )), is(false));
    }

    @Test
    public void shouldNotContainAllNotMatchMultipleValuesMultipleEntries() {
        // given
        NottableStringMultiMap multiMap = multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{string("keyOne"), not("keyOne_valueOne")},
            new NottableString[]{string("keyTwo"), not("keyTwo_valueOne"), not("keyTwo_valueTwo")},
            new NottableString[]{string("keyThree"), not("keyThree_valueOne"), not("keyThree_valueTwo"), not("keyThree_valueThree")}
        );

        // then
        assertThat(multiMap.containsAll(multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{string("keyOne"), not("notKeyOne_valueOne")},
            new NottableString[]{string("keyTwo"), not("keyTwo_valueOne"), not("notKeyTwo_valueTwo")}
        )), is(false));
        assertThat(multiMap.containsAll(multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{string("keyTwo"), not("notKeyTwo_valueOne"), not("notKeyTwo_valueTwo")}
        )), is(false));
    }

}
