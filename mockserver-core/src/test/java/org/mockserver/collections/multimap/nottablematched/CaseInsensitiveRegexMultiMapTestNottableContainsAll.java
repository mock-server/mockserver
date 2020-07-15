package org.mockserver.collections.multimap.nottablematched;

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
            new String[][]{new String[]{"keyOne", "keyOne_valueOne"}}
        );

        // then
        assertThat(multiMap.containsAll(multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{not("notKeyOne"), string("keyOne_valueOne")}
        )), is(true));
    }

    @Test
    public void shouldContainAllExactMatchSingleKeyAndSingleValueForNottedValue() {
        // given
        NottableStringMultiMap multiMap = multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new String[][]{new String[]{"keyOne", "keyOne_valueOne"}}
        );

        // then
        assertThat(multiMap.containsAll(multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{string("keyOne"), not("notKeyOne_valueOne")}
        )), is(true));
    }

    @Test
    public void shouldContainAllExactMatchSingleKeyAndSingleValueForNottedKeyAndValue() {
        // given
        NottableStringMultiMap multiMap = multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new String[][]{new String[]{"keyOne", "keyOne_valueOne"}}
        );

        // then
        assertThat(multiMap.containsAll(multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{not("notKeyOne"), not("notKeyOne_valueOne")}
        )), is(true));
    }

    @Test
    public void shouldContainAllSubSetSingleKeyAndSingleValueForNottedKey() {
        // given
        NottableStringMultiMap multiMap = multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new String[]{"keyOne", "keyOne_valueOne"},
            new String[]{"keyTwo", "keyTwo_valueOne", "keyTwo_valueTwo"},
            new String[]{"keyThree", "keyThree_valueOne", "keyThree_valueTwo", "keyThree_valueThree"}
        );

        // then
        assertThat(multiMap.containsAll(multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{not("notKeyOne"), string("keyOne_valueOne")}
        )), is(true));
    }

    @Test
    public void shouldContainAllSubSetSingleKeyAndSingleValueForNottedValue() {
        // given
        NottableStringMultiMap multiMap = multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new String[]{"keyOne", "keyOne_valueOne"},
            new String[]{"keyTwo", "keyTwo_valueOne", "keyTwo_valueTwo"},
            new String[]{"keyThree", "keyThree_valueOne", "keyThree_valueTwo", "keyThree_valueThree"}
        );

        // then
        assertThat(multiMap.containsAll(multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{string("keyOne"), not("notKeyOne_valueOne")}
        )), is(true));
    }

    @Test
    public void shouldContainAllSubSetSingleKeyAndSingleValueForNottedKeyAndValue() {
        // given
        NottableStringMultiMap multiMap = multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new String[]{"keyOne", "keyOne_valueOne"},
            new String[]{"keyTwo", "keyTwo_valueOne", "keyTwo_valueTwo"},
            new String[]{"keyThree", "keyThree_valueOne", "keyThree_valueTwo", "keyThree_valueThree"}
        );

        // then
        assertThat(multiMap.containsAll(multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{not("notKeyOne"), not("notKeyOne_valueOne")}
        )), is(true));
    }

    @Test
    public void shouldContainAllExactMatchSingleKeyAndMultipleValuesForNottedKey() {
        // given
        NottableStringMultiMap multiMap = multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new String[][]{new String[]{"keyTwo", "keyTwo_valueOne", "keyTwo_valueTwo"}}
        );

        // then
        assertThat(multiMap.containsAll(multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{not("notKeyTwo"), string("keyTwo_valueOne"), string("keyTwo_valueTwo")}
        )), is(true));
    }

    @Test
    public void shouldContainAllExactMatchSingleKeyAndMultipleValuesForNottedValue() {
        // given
        NottableStringMultiMap multiMap = multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new String[][]{new String[]{"keyTwo", "keyTwo_valueOne", "keyTwo_valueTwo"}}
        );

        // then
        assertThat(multiMap.containsAll(multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{string("keyTwo"), not("notKeyTwo_valueOne"), string("keyTwo_valueTwo")}
        )), is(true));
        assertThat(multiMap.containsAll(multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{string("keyTwo"), string("keyTwo_valueOne"), not("notKeyTwo_valueTwo")}
        )), is(true));
        assertThat(multiMap.containsAll(multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{string("keyTwo"), not("notKeyTwo_valueOne"), not("notKeyTwo_valueTwo")}
        )), is(true));
    }

    @Test
    public void shouldContainAllExactMatchSingleKeyAndMultipleValuesForNottedKeyAndValue() {
        // given
        NottableStringMultiMap multiMap = multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new String[][]{new String[]{"keyTwo", "keyTwo_valueOne", "keyTwo_valueTwo"}}
        );

        // then
        assertThat(multiMap.containsAll(multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{not("notKeyTwo"), string("keyTwo_valueOne"), not("notKeyTwo_valueTwo")}
        )), is(true));
        assertThat(multiMap.containsAll(multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{not("notKeyTwo"), not("notKeyTwo_valueOne"), string("keyTwo_valueTwo")}
        )), is(true));
        assertThat(multiMap.containsAll(multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{not("notKeyTwo"), not("notKeyTwo_valueOne"), not("notKeyTwo_valueTwo")}
        )), is(true));
    }

    @Test
    public void shouldContainAllSubSetSingleKeyAndMultipleValuesForNottedKey() {
        // given
        NottableStringMultiMap multiMap = multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new String[]{"keyOne", "keyOne_valueOne"},
            new String[]{"keyTwo", "keyTwo_valueOne", "keyTwo_valueTwo"},
            new String[]{"keyThree", "keyThree_valueOne", "keyThree_valueTwo", "keyThree_valueThree"}
        );

        // then
        assertThat(multiMap.containsAll(multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{not("notKeyTwo"), string("keyTwo_valueOne"), string("keyTwo_valueTwo")}
        )), is(true));
    }

    @Test
    public void shouldContainAllSubSetSingleKeyAndMultipleValuesForNottedValue() {
        // given
        NottableStringMultiMap multiMap = multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new String[]{"keyOne", "keyOne_valueOne"},
            new String[]{"keyTwo", "keyTwo_valueOne", "keyTwo_valueTwo"},
            new String[]{"keyThree", "keyThree_valueOne", "keyThree_valueTwo", "keyThree_valueThree"}
        );

        // then
        assertThat(multiMap.containsAll(multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{string("keyTwo"), not("notKeyTwo_valueOne"), string("keyTwo_valueTwo")}
        )), is(true));
        assertThat(multiMap.containsAll(multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{string("keyTwo"), string("keyTwo_valueOne"), not("notKeyTwo_valueTwo")}
        )), is(true));
        assertThat(multiMap.containsAll(multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{string("keyTwo"), not("notKeyTwo_valueOne"), not("notKeyTwo_valueTwo")}
        )), is(true));
    }

    @Test
    public void shouldContainAllSubSetSingleKeyAndMultipleValuesForNottedKeyAndValue() {
        // given
        NottableStringMultiMap multiMap = multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new String[]{"keyOne", "keyOne_valueOne"},
            new String[]{"keyTwo", "keyTwo_valueOne", "keyTwo_valueTwo"},
            new String[]{"keyThree", "keyThree_valueOne", "keyThree_valueTwo", "keyThree_valueThree"}
        );

        // then
        assertThat(multiMap.containsAll(multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{not("notKeyTwo"), not("notKeyTwo_valueOne"), string("keyTwo_valueTwo")}
        )), is(true));
        assertThat(multiMap.containsAll(multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{not("notKeyTwo"), string("keyTwo_valueOne"), not("notKeyTwo_valueTwo")}
        )), is(true));
        assertThat(multiMap.containsAll(multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{not("notKeyTwo"), not("notKeyTwo_valueOne"), not("notKeyTwo_valueTwo")}
        )), is(true));
    }

    @Test
    public void shouldContainAllExactMatchMultipleKeyAndMultipleValuesForNottedKey() {
        // given
        NottableStringMultiMap multiMap = multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new String[]{"keyOne", "keyOne_valueOne"},
            new String[]{"keyTwo", "keyTwo_valueOne", "keyTwo_valueTwo"}
        );

        // then
        assertThat(multiMap.containsAll(multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{string("keyOne"), string("keyOne_valueOne")},
            new NottableString[]{not("notKeyTwo"), string("keyTwo_valueOne"), string("keyTwo_valueTwo")}
        )), is(true));
        assertThat(multiMap.containsAll(multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{not("notKeyOne"), string("keyOne_valueOne")},
            new NottableString[]{string("keyTwo"), string("keyTwo_valueOne"), string("keyTwo_valueTwo")}
        )), is(true));
        assertThat(multiMap.containsAll(multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{not("notKeyOne"), string("keyOne_valueOne")},
            new NottableString[]{not("notKeyTwo"), string("keyTwo_valueOne"), string("keyTwo_valueTwo")}
        )), is(true));
    }

    @Test
    public void shouldContainAllExactMatchMultipleKeyAndMultipleValuesForNottedValue() {
        // given
        NottableStringMultiMap multiMap = multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new String[]{"keyOne", "keyOne_valueOne"},
            new String[]{"keyTwo", "keyTwo_valueOne", "keyTwo_valueTwo"}
        );

        // then
        assertThat(multiMap.containsAll(multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{string("keyOne"), not("notKeyOne_valueOne")},
            new NottableString[]{string("keyTwo"), string("keyTwo_valueOne"), string("keyTwo_valueTwo")}
        )), is(true));
        assertThat(multiMap.containsAll(multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{string("keyOne"), string("keyOne_valueOne")},
            new NottableString[]{string("keyTwo"), not("notKeyTwo_valueOne"), string("keyTwo_valueTwo")}
        )), is(true));
        assertThat(multiMap.containsAll(multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{string("keyOne"), string("keyOne_valueOne")},
            new NottableString[]{string("keyTwo"), string("keyTwo_valueOne"), not("notKeyTwo_valueTwo")}
        )), is(true));
        assertThat(multiMap.containsAll(multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{string("keyOne"), string("keyOne_valueOne")},
            new NottableString[]{string("keyTwo"), not("notKeyTwo_valueOne"), not("notKeyTwo_valueTwo")}
        )), is(true));
        assertThat(multiMap.containsAll(multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{string("keyOne"), not("notKeyOne_valueOne")},
            new NottableString[]{string("keyTwo"), not("notKeyTwo_valueOne"), string("keyTwo_valueTwo")}
        )), is(true));
        assertThat(multiMap.containsAll(multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{string("keyOne"), not("notKeyOne_valueOne")},
            new NottableString[]{string("keyTwo"), string("keyTwo_valueOne"), not("notKeyTwo_valueTwo")}
        )), is(true));
        assertThat(multiMap.containsAll(multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{string("keyOne"), not("notKeyOne_valueOne")},
            new NottableString[]{string("keyTwo"), not("notKeyTwo_valueOne"), not("notKeyTwo_valueTwo")}
        )), is(true));
    }

    @Test
    public void shouldContainAllExactMatchMultipleKeyAndMultipleValuesForNottedKeyAndValue() {
        // given
        NottableStringMultiMap multiMap = multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new String[]{"keyOne", "keyOne_valueOne"},
            new String[]{"keyTwo", "keyTwo_valueOne", "keyTwo_valueTwo"}
        );

        // then
        assertThat(multiMap.containsAll(multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{not("notKeyOne"), not("notKeyOne_valueOne")},
            new NottableString[]{string("keyTwo"), string("keyTwo_valueOne"), string("keyTwo_valueTwo")}
        )), is(true));
        assertThat(multiMap.containsAll(multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{string("keyOne"), string("keyOne_valueOne")},
            new NottableString[]{not("notKeyTwo"), not("notKeyTwo_valueOne"), string("keyTwo_valueTwo")}
        )), is(true));
        assertThat(multiMap.containsAll(multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{string("keyOne"), string("keyOne_valueOne")},
            new NottableString[]{not("notKeyTwo"), string("keyTwo_valueOne"), not("notKeyTwo_valueTwo")}
        )), is(true));
        assertThat(multiMap.containsAll(multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{string("keyOne"), string("keyOne_valueOne")},
            new NottableString[]{not("notKeyTwo"), not("notKeyTwo_valueOne"), not("notKeyTwo_valueTwo")}
        )), is(true));
        assertThat(multiMap.containsAll(multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{not("notKeyOne"), not("notKeyOne_valueOne")},
            new NottableString[]{not("notKeyTwo"), not("notKeyTwo_valueOne"), string("keyTwo_valueTwo")}
        )), is(true));
        assertThat(multiMap.containsAll(multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{not("notKeyOne"), not("notKeyOne_valueOne")},
            new NottableString[]{string("keyTwo"), string("keyTwo_valueOne"), not("notKeyTwo_valueTwo")}
        )), is(true));
        assertThat(multiMap.containsAll(multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{not("notKeyOne"), not("notKeyOne_valueOne")},
            new NottableString[]{not("notKeyTwo"), not("notKeyTwo_valueOne"), not("notKeyTwo_valueTwo")}
        )), is(true));
    }

    @Test
    public void shouldContainAllSubSetMultipleKeyAndMultipleValuesForNottedKey() {
        // given
        NottableStringMultiMap multiMap = multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new String[]{"keyOne", "keyOne_valueOne"},
            new String[]{"keyTwo", "keyTwo_valueOne", "keyTwo_valueTwo"},
            new String[]{"keyThree", "keyThree_valueOne", "keyThree_valueTwo", "keyThree_valueThree"}
        );

        // then
        assertThat(multiMap.containsAll(multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{string("keyOne"), string("keyOne_valueOne")},
            new NottableString[]{not("notKeyTwo"), string("keyTwo_valueOne"), string("keyTwo_valueTwo")}
        )), is(true));
        assertThat(multiMap.containsAll(multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{not("notKeyOne"), string("keyOne_valueOne")},
            new NottableString[]{string("keyTwo"), string("keyTwo_valueOne"), string("keyTwo_valueTwo")}
        )), is(true));
        assertThat(multiMap.containsAll(multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{not("notKeyOne"), string("keyOne_valueOne")},
            new NottableString[]{not("notKeyTwo"), string("keyTwo_valueOne"), string("keyTwo_valueTwo")}
        )), is(true));
    }

    @Test
    public void shouldContainAllSubSetMultipleKeyAndMultipleValuesForNottedValue() {
        // given
        NottableStringMultiMap multiMap = multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new String[]{"keyOne", "keyOne_valueOne"},
            new String[]{"keyTwo", "keyTwo_valueOne", "keyTwo_valueTwo"},
            new String[]{"keyThree", "keyThree_valueOne", "keyThree_valueTwo", "keyThree_valueThree"}
        );

        // then
        assertThat(multiMap.containsAll(multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{string("keyOne"), not("notKeyOne_valueOne")},
            new NottableString[]{string("keyTwo"), string("keyTwo_valueOne"), string("keyTwo_valueTwo")}
        )), is(true));
        assertThat(multiMap.containsAll(multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{string("keyOne"), string("keyOne_valueOne")},
            new NottableString[]{string("keyTwo"), not("notKeyTwo_valueOne"), string("keyTwo_valueTwo")}
        )), is(true));
        assertThat(multiMap.containsAll(multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{string("keyOne"), string("keyOne_valueOne")},
            new NottableString[]{string("keyTwo"), string("keyTwo_valueOne"), not("notKeyTwo_valueTwo")}
        )), is(true));
        assertThat(multiMap.containsAll(multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{string("keyOne"), string("keyOne_valueOne")},
            new NottableString[]{string("keyTwo"), not("notKeyTwo_valueOne"), not("notKeyTwo_valueTwo")}
        )), is(true));
        assertThat(multiMap.containsAll(multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{string("keyOne"), not("notKeyOne_valueOne")},
            new NottableString[]{string("keyTwo"), not("notKeyTwo_valueOne"), string("keyTwo_valueTwo")}
        )), is(true));
        assertThat(multiMap.containsAll(multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{string("keyOne"), not("notKeyOne_valueOne")},
            new NottableString[]{string("keyTwo"), string("keyTwo_valueOne"), not("notKeyTwo_valueTwo")}
        )), is(true));
        assertThat(multiMap.containsAll(multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{string("keyOne"), not("notKeyOne_valueOne")},
            new NottableString[]{string("keyTwo"), not("notKeyTwo_valueOne"), not("notKeyTwo_valueTwo")}
        )), is(true));
    }

    @Test
    public void shouldContainAllSubSetMultipleKeyAndMultipleValuesForNottedKeyAndValue() {
        // given
        NottableStringMultiMap multiMap = multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new String[]{"keyOne", "keyOne_valueOne"},
            new String[]{"keyTwo", "keyTwo_valueOne", "keyTwo_valueTwo"},
            new String[]{"keyThree", "keyThree_valueOne", "keyThree_valueTwo", "keyThree_valueThree"}
        );

        // then
        assertThat(multiMap.containsAll(multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{not("notKeyOne"), not("notKeyOne_valueOne")},
            new NottableString[]{string("keyTwo"), string("keyTwo_valueOne"), string("keyTwo_valueTwo")}
        )), is(true));
        assertThat(multiMap.containsAll(multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{string("keyOne"), string("keyOne_valueOne")},
            new NottableString[]{not("notKeyTwo"), not("notKeyTwo_valueOne"), string("keyTwo_valueTwo")}
        )), is(true));
        assertThat(multiMap.containsAll(multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{string("keyOne"), string("keyOne_valueOne")},
            new NottableString[]{not("notKeyTwo"), string("keyTwo_valueOne"), not("notKeyTwo_valueTwo")}
        )), is(true));
        assertThat(multiMap.containsAll(multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{string("keyOne"), string("keyOne_valueOne")},
            new NottableString[]{not("notKeyTwo"), not("notKeyTwo_valueOne"), not("notKeyTwo_valueTwo")}
        )), is(true));
        assertThat(multiMap.containsAll(multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{not("notKeyOne"), not("notKeyOne_valueOne")},
            new NottableString[]{not("notKeyTwo"), not("notKeyTwo_valueOne"), string("keyTwo_valueTwo")}
        )), is(true));
        assertThat(multiMap.containsAll(multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{not("notKeyOne"), not("notKeyOne_valueOne")},
            new NottableString[]{string("keyTwo"), string("keyTwo_valueOne"), not("notKeyTwo_valueTwo")}
        )), is(true));
        assertThat(multiMap.containsAll(multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{not("notKeyOne"), not("notKeyOne_valueOne")},
            new NottableString[]{not("notKeyTwo"), not("notKeyTwo_valueOne"), not("notKeyTwo_valueTwo")}
        )), is(true));

        // and then
        assertThat(multiMap.containsAll(multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{not("notKeyOne"), not("keyOne_valueOne")},
            new NottableString[]{string("keyTwo"), string("keyTwo_valueOne"), string("keyTwo_valueTwo")}
        )), is(true));
        assertThat(multiMap.containsAll(multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{not("keyOne"), not("notKeyOne_valueOne")},
            new NottableString[]{string("keyTwo"), string("keyTwo_valueOne"), string("keyTwo_valueTwo")}
        )), is(true));
        assertThat(multiMap.containsAll(multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{string("keyOne"), string("keyOne_valueOne")},
            new NottableString[]{not("notKeyTwo"), not("keyTwo_valueOne"), not("keyTwo_valueTwo")}
        )), is(true));
        assertThat(multiMap.containsAll(multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{string("keyOne"), string("keyOne_valueOne")},
            new NottableString[]{not("keyTwo"), not("notKeyTwo_valueOne"), not("notKeyTwo_valueTwo")}
        )), is(true));
        assertThat(multiMap.containsAll(multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{not("notKeyOne"), not("keyOne_valueOne")},
            new NottableString[]{not("notKeyTwo"), not("keyTwo_valueOne"), not("keyTwo_valueTwo")}
        )), is(true));
        assertThat(multiMap.containsAll(multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{not("keyOne"), not("notKeyOne_valueOne")},
            new NottableString[]{not("keyTwo"), not("notKeyTwo_valueOne"), not("notKeyTwo_valueTwo")}
        )), is(true));
    }

    @Test
    public void shouldNotContainAllNotMatchSingleKeySingleEntry() {
        // given
        NottableStringMultiMap multiMap = multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new String[][]{new String[]{"keyOne", "keyOne_valueOne"}}
        );

        // then
        assertThat(multiMap.containsAll(multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{not("keyOne"), string("keyOne_valueOne")}
        )), is(false));
    }

    @Test
    public void shouldNotContainAllNotMatchSingleValueSingleEntry() {
        // given
        NottableStringMultiMap multiMap = multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new String[][]{new String[]{"keyOne", "keyOne_valueOne"}}
        );

        // then
        assertThat(multiMap.containsAll(multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{string("keyOne"), not("keyOne_valueOne")}
        )), is(false));
    }

    @Test
    public void shouldNotContainAllNotMatchSingleKeyMultipleEntries() {
        // given
        NottableStringMultiMap multiMap = multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new String[]{"keyOne", "keyOne_valueOne"},
            new String[]{"keyTwo", "keyTwo_valueOne", "keyTwo_valueTwo"},
            new String[]{"keyThree", "keyThree_valueOne", "keyThree_valueTwo", "keyThree_valueThree"}
        );

        // then
        assertThat(multiMap.containsAll(multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{not("keyOne"), string("keyOne_valueOne")}
        )), is(false));
    }

    @Test
    public void shouldNotContainAllNotMatchSingleValueMultipleEntries() {
        // given
        NottableStringMultiMap multiMap = multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new String[]{"keyOne", "keyOne_valueOne"},
            new String[]{"keyTwo", "keyTwo_valueOne", "keyTwo_valueTwo"},
            new String[]{"keyThree", "keyThree_valueOne", "keyThree_valueTwo", "keyThree_valueThree"}
        );

        // then
        assertThat(multiMap.containsAll(multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{string("keyOne"), not("keyOne_valueOne")}
        )), is(false));
        assertThat(multiMap.containsAll(multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{string("keyTwo"), string("keyTwo_valueOne"), not("keyTwo.*")}
        )), is(false));
    }

    @Test
    public void shouldNotContainAllNotMatchMultipleKeysMultipleEntries() {
        // given
        NottableStringMultiMap multiMap = multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new String[]{"keyOne", "keyOne_valueOne"},
            new String[]{"keyTwo", "keyTwo_valueOne", "keyTwo_valueTwo"},
            new String[]{"keyThree", "keyThree_valueOne", "keyThree_valueTwo", "keyThree_valueThree"}
        );

        assertThat(multiMap.containsAll(multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{string("keyOne"), string("keyOne_valueOne")},
            new NottableString[]{not("keyTwo"), string("notKey.*")}
        )), is(false));
        // then
        assertThat(multiMap.containsAll(multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{not("keyOne"), string("keyOne_valueOne")},
            new NottableString[]{not("keyTwo"), string("keyTwo_valueOne"), string("keyTwo_valueTwo")}
        )), is(false));
    }

    @Test
    public void shouldNotContainAllNotMatchMultipleKeysAndValuesMultipleEntries() {
        // given
        NottableStringMultiMap multiMap = multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new String[]{"keyOne", "keyOne_valueOne"},
            new String[]{"keyTwo", "keyTwo_valueOne", "keyTwo_valueTwo"},
            new String[]{"keyThree", "keyThree_valueOne", "keyThree_valueTwo", "keyThree_valueThree"}
        );

        // then
        assertThat(multiMap.containsAll(multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{not("keyOne"), not("keyOne_valueOne")},
            new NottableString[]{not("keyTwo"), not("key.*")}
        )), is(false));
    }

    @Test
    public void shouldNotContainAllNotMatchMultipleValuesMultipleEntries() {
        // given
        NottableStringMultiMap multiMap = multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new String[]{"keyOne", "keyOne_valueOne"},
            new String[]{"keyTwo", "keyTwo_valueOne", "keyTwo_valueTwo"},
            new String[]{"keyThree", "keyThree_valueOne", "keyThree_valueTwo", "keyThree_valueThree"}
        );

        // then
        assertThat(multiMap.containsAll(multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{string("keyOne"), not("keyOne_valueOne")},
            new NottableString[]{string("keyTwo"), string("keyTwo_valueOne"), not("keyTwo_valueTwo")}
        )), is(false));
        assertThat(multiMap.containsAll(multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{string("keyTwo"), not("keyTwo.*")}
        )), is(false));
    }
}
