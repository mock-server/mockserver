package org.mockserver.collections.multimap.nottablematcher;

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
            new NottableString[]{string("notKeyOne"), string("keyOne_valueOne")}
        )), is(true));

        // and then
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
            new NottableString[]{string("keyOne"), string("notKeyOne_valueOne")}
        )), is(true));

        // and then
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
            new NottableString[]{string("notKeyOne"), string("notKeyOne_valueOne")}
        )), is(true));

        // and then
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
            new NottableString[]{string("keyTwo"), string("keyTwo_valueOne"), string("keyTwo_valueTwo")},
            new NottableString[]{string("keyThree"), string("keyThree_valueOne"), string("keyThree_valueTwo"), string("keyThree_valueThree")}
        );

        // then
        assertThat(multiMap.containsAll(multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{string("notKeyOne"), string("keyOne_valueOne")}
        )), is(true));

        // and then
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
            new NottableString[]{string("keyTwo"), string("keyTwo_valueOne"), string("keyTwo_valueTwo")},
            new NottableString[]{string("keyThree"), string("keyThree_valueOne"), string("keyThree_valueTwo"), string("keyThree_valueThree")}
        );

        // then
        assertThat(multiMap.containsAll(multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{string("keyOne"), string("notKeyOne_valueOne")}
        )), is(true));

        // and then
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
            new NottableString[]{string("keyTwo"), string("keyTwo_valueOne"), string("keyTwo_valueTwo")},
            new NottableString[]{string("keyThree"), string("keyThree_valueOne"), string("keyThree_valueTwo"), string("keyThree_valueThree")}
        );

        // then
        assertThat(multiMap.containsAll(multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{string("notKeyOne"), string("notKeyOne_valueOne")}
        )), is(true));

        // and then
        assertThat(multiMap.containsAll(multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{not("keyOne"), not("keyOne_valueOne")}
        )), is(true));
    }

    @Test
    public void shouldContainAllExactMatchSingleKeyAndMultipleValuesForNottedKey() {
        // given
        NottableStringMultiMap multiMap = multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{not("keyTwo"), string("keyTwo_valueOne"), string("keyTwo_valueTwo")}
        );

        // then
        assertThat(multiMap.containsAll(multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{string("notKeyTwo"), string("keyTwo_valueOne"), string("keyTwo_valueTwo")}
        )), is(true));

        // and then
        assertThat(multiMap.containsAll(multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{not("keyTwo"), string("keyTwo_valueOne"), string("keyTwo_valueTwo")}
        )), is(true));
    }

    @Test
    public void shouldContainAllExactMatchSingleKeyAndMultipleValuesForNottedValue() {
        // given
        NottableStringMultiMap multiMap = multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{string("keyTwo"), not("keyTwo_valueOne"), not("keyTwo_valueTwo")}
        );

        // then
        assertThat(multiMap.containsAll(multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{string("keyTwo"), string("notKeyTwo_valueOne"), string("keyTwo_valueTwo")}
        )), is(true));
        assertThat(multiMap.containsAll(multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{string("keyTwo"), string("keyTwo_valueOne"), string("notKeyTwo_valueTwo")}
        )), is(true));
        assertThat(multiMap.containsAll(multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{string("keyTwo"), string("notKeyTwo_valueOne"), string("notKeyTwo_valueTwo")}
        )), is(true));
    }

    @Test
    public void shouldContainAllExactMatchSingleKeyAndMultipleValuesForNottedKeyAndValue() {
        // given
        NottableStringMultiMap multiMap = multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{not("keyTwo"), not("keyTwo_valueOne"), not("keyTwo_valueTwo")}
        );

        // then
        assertThat(multiMap.containsAll(multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{string("notKeyTwo"), string("keyTwo_valueOne"), string("notKeyTwo_valueTwo")}
        )), is(true));
        assertThat(multiMap.containsAll(multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{string("notKeyTwo"), string("notKeyTwo_valueOne"), string("keyTwo_valueTwo")}
        )), is(true));
        assertThat(multiMap.containsAll(multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{string("notKeyTwo"), string("notKeyTwo_valueOne"), string("notKeyTwo_valueTwo")}
        )), is(true));

        // and then
        assertThat(multiMap.containsAll(multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{not("keyTwo"), string("notKeyTwo_valueOne"), not("keyTwo_valueTwo")}
        )), is(true));
        assertThat(multiMap.containsAll(multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{not("keyTwo"), not("keyTwo_valueOne"), string("notKeyTwo_valueTwo")}
        )), is(true));
        assertThat(multiMap.containsAll(multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{not("keyTwo"), not("keyTwo_valueOne"), not("keyTwo_valueTwo")}
        )), is(true));
    }

    @Test
    public void shouldContainAllSubSetSingleKeyAndMultipleValuesForNottedKey() {
        // given
        NottableStringMultiMap multiMap = multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{string("keyOne"), string("keyOne_valueOne")},
            new NottableString[]{not("keyTwo"), string("keyTwo_valueOne"), string("keyTwo_valueTwo")},
            new NottableString[]{string("keyThree"), string("keyThree_valueOne"), string("keyThree_valueTwo"), string("keyThree_valueThree")}
        );

        // then
        assertThat(multiMap.containsAll(multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{string("notKeyTwo"), string("keyTwo_valueOne"), string("keyTwo_valueTwo")}
        )), is(true));

        // and then
        assertThat(multiMap.containsAll(multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{not("keyTwo"), string("keyTwo_valueOne"), string("keyTwo_valueTwo")}
        )), is(true));
    }

    @Test
    public void shouldContainAllSubSetSingleKeyAndMultipleValuesForNottedValue() {
        // given
        NottableStringMultiMap multiMap = multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{string("keyOne"), string("keyOne_valueOne")},
            new NottableString[]{string("keyTwo"), not("keyTwo_valueOne"), not("keyTwo_valueTwo")},
            new NottableString[]{string("keyThree"), string("keyThree_valueOne"), string("keyThree_valueTwo"), string("keyThree_valueThree")}
        );

        // then
        assertThat(multiMap.containsAll(multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{string("keyTwo"), string("notKeyTwo_valueOne"), string("keyTwo_valueTwo")}
        )), is(true));
        assertThat(multiMap.containsAll(multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{string("keyTwo"), string("keyTwo_valueOne"), string("notKeyTwo_valueTwo")}
        )), is(true));
        assertThat(multiMap.containsAll(multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{string("keyTwo"), string("notKeyTwo_valueOne"), string("notKeyTwo_valueTwo")}
        )), is(true));
    }

    @Test
    public void shouldContainAllSubSetSingleKeyAndMultipleValuesForNottedKeyAndValue() {
        // given
        NottableStringMultiMap multiMap = multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{string("keyOne"), string("keyOne_valueOne")},
            new NottableString[]{not("keyTwo"), not("keyTwo_valueOne"), not("keyTwo_valueTwo")},
            new NottableString[]{string("keyThree"), string("keyThree_valueOne"), string("keyThree_valueTwo"), string("keyThree_valueThree")}
        );

        // then
        assertThat(multiMap.containsAll(multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{string("notKeyTwo"), string("notKeyTwo_valueOne"), string("keyTwo_valueTwo")}
        )), is(true));
        assertThat(multiMap.containsAll(multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{string("notKeyTwo"), string("keyTwo_valueOne"), string("notKeyTwo_valueTwo")}
        )), is(true));
        assertThat(multiMap.containsAll(multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{string("notKeyTwo"), string("notKeyTwo_valueOne"), string("notKeyTwo_valueTwo")}
        )), is(true));

        // and then
        assertThat(multiMap.containsAll(multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{not("keyTwo"), not("keyTwo_valueOne"), string("keyTwo_valueTwo")}
        )), is(true));
        assertThat(multiMap.containsAll(multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{not("keyTwo"), string("keyTwo_valueOne"), not("keyTwo_valueTwo")}
        )), is(true));
        assertThat(multiMap.containsAll(multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{not("keyTwo"), not("keyTwo_valueOne"), not("keyTwo_valueTwo")}
        )), is(true));
    }

    @Test
    public void shouldContainAllExactMatchMultipleKeyAndMultipleValuesForNottedKey() {
        // given
        NottableStringMultiMap multiMap = multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{string("keyOne"), string("keyOne_valueOne")},
            new NottableString[]{not("keyTwo"), string("keyTwo_valueOne"), string("keyTwo_valueTwo")}
        );

        // then
        assertThat(multiMap.containsAll(multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{string("keyOne"), string("keyOne_valueOne")},
            new NottableString[]{string("notKeyTwo"), string("keyTwo_valueOne"), string("keyTwo_valueTwo")}
        )), is(true));

        // and then
        assertThat(multiMap.containsAll(multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{string("keyOne"), string("keyOne_valueOne")},
            new NottableString[]{not("keyTwo"), string("keyTwo_valueOne"), string("keyTwo_valueTwo")}
        )), is(true));
    }

    @Test
    public void shouldContainAllExactMatchMultipleKeyAndMultipleValuesForNottedValue() {
        // given
        NottableStringMultiMap multiMap = multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{string("keyOne"), string("keyOne_valueOne")},
            new NottableString[]{string("keyTwo"), not("keyTwo_valueOne"), not("keyTwo_valueTwo")}
        );

        // then
        assertThat(multiMap.containsAll(multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{string("keyOne"), string("keyOne_valueOne")},
            new NottableString[]{string("keyTwo"), string("notKeyTwo_valueOne"), string("keyTwo_valueTwo")}
        )), is(true));
        assertThat(multiMap.containsAll(multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{string("keyOne"), string("keyOne_valueOne")},
            new NottableString[]{string("keyTwo"), string("keyTwo_valueOne"), string("notKeyTwo_valueTwo")}
        )), is(true));
        assertThat(multiMap.containsAll(multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{string("keyOne"), string("keyOne_valueOne")},
            new NottableString[]{string("keyTwo"), string("notKeyTwo_valueOne"), string("notKeyTwo_valueTwo")}
        )), is(true));
    }

    @Test
    public void shouldContainAllExactMatchMultipleKeyAndMultipleValuesForNottedKeyAndValue() {
        // given
        NottableStringMultiMap multiMap = multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{string("keyOne"), string("keyOne_valueOne")},
            new NottableString[]{not("keyTwo"), not("keyTwo_valueOne"), not("keyTwo_valueTwo")}
        );

        // then
        assertThat(multiMap.containsAll(multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{string("keyOne"), string("keyOne_valueOne")},
            new NottableString[]{string("notKeyTwo"), string("notKeyTwo_valueOne"), string("keyTwo_valueTwo")}
        )), is(true));
        assertThat(multiMap.containsAll(multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{string("keyOne"), string("keyOne_valueOne")},
            new NottableString[]{string("notKeyTwo"), string("keyTwo_valueOne"), string("notKeyTwo_valueTwo")}
        )), is(true));
        assertThat(multiMap.containsAll(multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{string("keyOne"), string("keyOne_valueOne")},
            new NottableString[]{string("notKeyTwo"), string("notKeyTwo_valueOne"), string("notKeyTwo_valueTwo")}
        )), is(true));

        // and then
        assertThat(multiMap.containsAll(multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{string("keyOne"), string("keyOne_valueOne")},
            new NottableString[]{not("keyTwo"), not("keyTwo_valueOne"), string("keyTwo_valueTwo")}
        )), is(true));
        assertThat(multiMap.containsAll(multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{string("keyOne"), string("keyOne_valueOne")},
            new NottableString[]{not("keyTwo"), string("keyTwo_valueOne"), not("keyTwo_valueTwo")}
        )), is(true));
        assertThat(multiMap.containsAll(multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{string("keyOne"), string("keyOne_valueOne")},
            new NottableString[]{not("keyTwo"), not("keyTwo_valueOne"), not("keyTwo_valueTwo")}
        )), is(true));
    }

    @Test
    public void shouldContainAllSubSetMultipleKeyAndMultipleValuesForNottedKey() {
        // given
        NottableStringMultiMap multiMap = multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{string("keyOne"), string("keyOne_valueOne")},
            new NottableString[]{not("keyTwo"), string("keyTwo_valueOne"), string("keyTwo_valueTwo")},
            new NottableString[]{string("keyThree"), string("keyThree_valueOne"), string("keyThree_valueTwo"), string("keyThree_valueThree")}
        );

        // then
        assertThat(multiMap.containsAll(multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{string("keyOne"), string("keyOne_valueOne")},
            new NottableString[]{string("notKeyTwo"), string("keyTwo_valueOne"), string("keyTwo_valueTwo")}
        )), is(true));

        // and then
        assertThat(multiMap.containsAll(multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{string("keyOne"), string("keyOne_valueOne")},
            new NottableString[]{not("keyTwo"), string("keyTwo_valueOne"), string("keyTwo_valueTwo")}
        )), is(true));
    }

    @Test
    public void shouldContainAllSubSetMultipleKeyAndMultipleValuesForNottedValue() {
        // given
        NottableStringMultiMap multiMap = multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{string("keyOne"), string("keyOne_valueOne")},
            new NottableString[]{string("keyTwo"), not("keyTwo_valueOne"), not("keyTwo_valueTwo")},
            new NottableString[]{string("keyThree"), string("keyThree_valueOne"), string("keyThree_valueTwo"), string("keyThree_valueThree")}
        );

        // then
        assertThat(multiMap.containsAll(multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{string("keyOne"), string("keyOne_valueOne")},
            new NottableString[]{string("keyTwo"), string("notKeyTwo_valueOne"), string("keyTwo_valueTwo")}
        )), is(true));
        assertThat(multiMap.containsAll(multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{string("keyOne"), string("keyOne_valueOne")},
            new NottableString[]{string("keyTwo"), string("keyTwo_valueOne"), string("notKeyTwo_valueTwo")}
        )), is(true));
        assertThat(multiMap.containsAll(multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{string("keyOne"), string("keyOne_valueOne")},
            new NottableString[]{string("keyTwo"), string("notKeyTwo_valueOne"), string("notKeyTwo_valueTwo")}
        )), is(true));
    }

    @Test
    public void shouldContainAllSubSetMultipleKeyAndMultipleValuesForNottedKeyAndValue() {
        // given
        NottableStringMultiMap multiMap = multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{string("keyOne"), string("keyOne_valueOne")},
            new NottableString[]{not("keyTwo"), not("keyTwo_valueOne"), not("keyTwo_valueTwo")},
            new NottableString[]{string("keyThree"), string("keyThree_valueOne"), string("keyThree_valueTwo"), string("keyThree_valueThree")}
        );

        // then
        assertThat(multiMap.containsAll(multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{string("keyOne"), string("keyOne_valueOne")},
            new NottableString[]{string("notKeyTwo"), string("keyTwo_valueOne"), string("keyTwo_valueTwo")}
        )), is(true));
        assertThat(multiMap.containsAll(multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{string("keyOne"), string("keyOne_valueOne")},
            new NottableString[]{string("notKeyTwo"), string("notKeyTwo_valueOne"), string("keyTwo_valueTwo")}
        )), is(true));
        assertThat(multiMap.containsAll(multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{string("keyOne"), string("keyOne_valueOne")},
            new NottableString[]{string("notKeyTwo"), string("keyTwo_valueOne"), string("notKeyTwo_valueTwo")}
        )), is(true));
        assertThat(multiMap.containsAll(multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{string("keyOne"), string("keyOne_valueOne")},
            new NottableString[]{string("notKeyTwo"), string("notKeyTwo_valueOne"), string("notKeyTwo_valueTwo")}
        )), is(true));

        // and then
        assertThat(multiMap.containsAll(multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{string("keyOne"), string("keyOne_valueOne")},
            new NottableString[]{not("keyTwo"), string("keyTwo_valueOne"), string("keyTwo_valueTwo")}
        )), is(true));
        assertThat(multiMap.containsAll(multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{string("keyOne"), string("keyOne_valueOne")},
            new NottableString[]{not("keyTwo"), not("keyTwo_valueOne"), string("keyTwo_valueTwo")}
        )), is(true));
        assertThat(multiMap.containsAll(multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{string("keyOne"), string("keyOne_valueOne")},
            new NottableString[]{not("keyTwo"), string("keyTwo_valueOne"), not("keyTwo_valueTwo")}
        )), is(true));
        assertThat(multiMap.containsAll(multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{string("keyOne"), string("keyOne_valueOne")},
            new NottableString[]{not("keyTwo"), not("keyTwo_valueOne"), not("keyTwo_valueTwo")}
        )), is(true));
    }

    @Test
    public void shouldContainAllEmptySubSetMultipleKeyAndMultipleValuesForNottedKeyAndValue() {
        // given
        NottableStringMultiMap multiMap = multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{string("keyOne"), string("keyOneValue")},
            new NottableString[]{not("keyTwo"), not("keyTwoValue")},
            new NottableString[]{string("keyThree"), string("keyThreeValue"), string("keyThree_valueTwo")}
        );

        // then
        assertThat(multiMap.containsAll(multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{}
        )), is(true));
    }

    @Test
    public void shouldContainAllSubSetMultipleKeyForEmptyMap() {
        // given
        NottableStringMultiMap multiMap = multiMap(
            false,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{}
        );

        // then
        assertThat(multiMap.containsAll(multiMap(
            false,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{not("keyOne"), string("keyOneValue")},
            new NottableString[]{not("keyTwo"), string("keyTwoValue")}
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
            new NottableString[]{string("keyOne"), string("keyOne_valueOne")}
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
            new NottableString[]{string("keyOne"), string("keyOne_valueOne")}
        )), is(false));
    }

    @Test
    public void shouldNotContainAllNotMatchSingleKeyAndValueSingleEntry() {
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
            new NottableString[]{string("keyOne"), string("keyOne_valueOne")}
        )), is(false));
    }

    @Test
    public void shouldNotContainAllNotMatchSingleKeyMultipleEntries() {
        // given
        NottableStringMultiMap multiMap = multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{not("keyOne"), string("keyOne_valueOne")},
            new NottableString[]{string("keyTwo"), string("keyTwo_valueOne"), string("keyTwo_valueTwo")},
            new NottableString[]{string("keyThree"), string("keyThree_valueOne"), string("keyThree_valueTwo"), string("keyThree_valueThree")}
        );

        // then
        assertThat(multiMap.containsAll(multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{string("keyOne"), string("keyOne_valueOne")}
        )), is(false));
    }

    @Test
    public void shouldNotContainAllNotMatchSingleValueMultipleEntries() {
        // given
        NottableStringMultiMap multiMap = multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{string("keyOne"), not("keyOne_valueOne")},
            new NottableString[]{string("keyTwo"), string("keyTwo_valueOne"), string("keyTwo_valueTwo")},
            new NottableString[]{string("keyThree"), string("keyThree_valueOne"), string("keyThree_valueTwo"), string("keyThree_valueThree")}
        );

        // then
        assertThat(multiMap.containsAll(multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{string("keyOne"), string("keyOne_valueOne")}
        )), is(false));
    }

    @Test
    public void shouldNotContainAllNotMatchSingleKeyAndValueMultipleEntries() {
        // given
        NottableStringMultiMap multiMap = multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{not("keyOne"), not("keyOne_valueOne")},
            new NottableString[]{string("keyTwo"), string("keyTwo_valueOne"), string("keyTwo_valueTwo")},
            new NottableString[]{string("keyThree"), string("keyThree_valueOne"), string("keyThree_valueTwo"), string("keyThree_valueThree")}
        );

        // then
        assertThat(multiMap.containsAll(multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{string("keyOne"), string("keyOne_valueOne")}
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
            new NottableString[]{string("keyThree"), string("keyThree_valueOne"), string("keyThree_valueTwo"), string("keyThree_valueThree")}
        );

        // then
        assertThat(multiMap.containsAll(multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{string("keyOne"), string("keyOne_valueOne")},
            new NottableString[]{string("keyTwo"), string("keyTwo_valueOne"), string("keyTwo_valueTwo")}
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
            new NottableString[]{string("keyThree"), string("keyThree_valueOne"), string("keyThree_valueTwo"), string("keyThree_valueThree")}
        );

        // then
        assertThat(multiMap.containsAll(multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{string("keyOne"), string("keyOne_valueOne")},
            new NottableString[]{string("keyTwo"), string("keyTwo_valueOne"), string("keyTwo_valueTwo")}
        )), is(false));
        assertThat(multiMap.containsAll(multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{string("keyTwo"), string("keyTwo.*")}
        )), is(false));
    }

    @Test
    public void shouldNotContainAllNotMatchMultipleValuesMultipleEntriesContradiction() {
        // given
        NottableStringMultiMap multiMap = multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{string("keyOne"), string("keyOneValue")}
        );

        // then
        assertThat(multiMap.containsAll(multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{string("keyOne"), string("keyOneValue")},
            new NottableString[]{not("keyOne"), not("keyOneValue")}
        )), is(false));
    }

    @Test
    public void shouldNotContainAllSubSetMultipleKeyForEmptyMap() {
        // given
        NottableStringMultiMap multiMap = multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{}
        );

        // then
        assertThat(multiMap.containsAll(multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{not("keyOne"), string("keyOneValue")},
            new NottableString[]{string("keyTwo"), string("keyTwoValue")}
        )), is(false));
    }
}
