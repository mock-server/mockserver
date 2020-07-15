package org.mockserver.collections.multimap;

import org.junit.Test;
import org.mockserver.collections.NottableStringMultiMap;
import org.mockserver.model.KeyMatchStyle;
import org.mockserver.model.NottableString;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockserver.collections.NottableStringMultiMap.multiMap;
import static org.mockserver.model.NottableSchemaString.schemaString;
import static org.mockserver.model.NottableString.string;

/**
 * @author jamesdbloom
 */
public class CaseInsensitiveRegexMultiMapTestContainsAllMatchingKey {

    // COMMON TESTS

    @Test
    public void shouldContainAllExactMatchSingleKeyAndSingleValue() {
        // given
        NottableStringMultiMap multiMap = multiMap(
            true,
            KeyMatchStyle.MATCHING_KEY,
            new String[][]{new String[]{"keyOne", "keyOne_valueOne"}}
        );

        // then
        assertThat(multiMap(
            true,
            KeyMatchStyle.MATCHING_KEY,
            new String[][]{new String[]{"keyOne", "keyOne_valueOne"}}
        ).containsAll(multiMap), is(true));
    }

    @Test
    public void shouldContainAllSubSetSingleKeyAndSingleValue() {
        // given
        NottableStringMultiMap multiMap = multiMap(
            true,
            KeyMatchStyle.MATCHING_KEY,
            new String[][]{new String[]{"keyOne", "keyOne_valueOne"}}
        );

        // then
        assertThat(multiMap(
            true,
            KeyMatchStyle.MATCHING_KEY,
            new String[]{"keyOne", "keyOne_valueOne"},
            new String[]{"keyTwo", "keyTwo_valueOne", "keyTwo_valueTwo"},
            new String[]{"keyThree", "keyThree_valueOne", "keyThree_valueTwo", "keyThree_valueThree"}
        ).containsAll(multiMap), is(true));
    }

    @Test
    public void shouldContainAllExactMatchSingleKeyAndMultipleValues() {
        // given
        NottableStringMultiMap multiMap = multiMap(
            true,
            KeyMatchStyle.MATCHING_KEY,
            new String[][]{new String[]{"keyTwo", "keyTwo_valueOne", "keyTwo_valueTwo"}}
        );

        // then
        assertThat(multiMap(
            true,
            KeyMatchStyle.MATCHING_KEY,
            new String[][]{new String[]{"keyTwo", "keyTwo_valueOne", "keyTwo_valueTwo"}}
        ).containsAll(multiMap), is(true));
    }

    @Test
    public void shouldContainAllSubSetSingleKeyAndMultipleValues() {
        // given
        NottableStringMultiMap multiMap = multiMap(
            true,
            KeyMatchStyle.MATCHING_KEY,
            new String[][]{new String[]{"keyTwo", "keyTwo_valueOne", "keyTwo_valueTwo"}}
        );

        // then
        assertThat(multiMap(
            true,
            KeyMatchStyle.MATCHING_KEY,
            new String[]{"keyOne", "keyOne_valueOne"},
            new String[]{"keyTwo", "keyTwo_valueOne", "keyTwo_valueTwo"},
            new String[]{"keyThree", "keyThree_valueOne", "keyThree_valueTwo", "keyThree_valueThree"}
        ).containsAll(multiMap), is(true));
    }

    @Test
    public void shouldContainAllExactMatchMultipleKeyAndMultipleValues() {
        // given
        NottableStringMultiMap multiMap = multiMap(
            true,
            KeyMatchStyle.MATCHING_KEY,
            new String[]{"keyOne", "keyOne_valueOne"},
            new String[]{"keyTwo", "keyTwo_valueOne", "keyTwo_valueTwo"}
        );

        // then
        assertThat(multiMap(
            true,
            KeyMatchStyle.MATCHING_KEY,
            new String[]{"keyOne", "keyOne_valueOne"},
            new String[]{"keyTwo", "keyTwo_valueOne", "keyTwo_valueTwo"}
        ).containsAll(multiMap), is(true));
    }

    @Test
    public void shouldContainAllSubSetMultipleKeyAndMultipleValues() {
        // given
        NottableStringMultiMap multiMap = multiMap(
            true,
            KeyMatchStyle.MATCHING_KEY,
            new String[]{"keyOne", "keyOne_valueOne"},
            new String[]{"keyTwo", "keyTwo_valueOne", "keyTwo_valueTwo"}
        );

        // then
        assertThat(multiMap(
            true,
            KeyMatchStyle.MATCHING_KEY,
            new String[]{"keyOne", "keyOne_valueOne"},
            new String[]{"keyTwo", "keyTwo_valueOne", "keyTwo_valueTwo"},
            new String[]{"keyThree", "keyThree_valueOne", "keyThree_valueTwo", "keyThree_valueThree"}
        ).containsAll(multiMap), is(true));
    }

    @Test
    public void shouldNotContainAllNotMatchSingleKeySingleEntry() {
        // given
        NottableStringMultiMap multiMap = multiMap(
            true,
            KeyMatchStyle.MATCHING_KEY,
            new String[][]{new String[]{"keyOne", "keyOne_valueOne"}}
        );

        // then
        assertThat(multiMap(
            true,
            KeyMatchStyle.MATCHING_KEY,
            new String[][]{new String[]{"notKeyOne", "keyOne_valueOne"}}
        ).containsAll(multiMap), is(false));
    }

    @Test
    public void shouldNotContainAllNotMatchSingleValueSingleEntry() {
        // given
        NottableStringMultiMap multiMap = multiMap(
            true,
            KeyMatchStyle.MATCHING_KEY,
            new String[][]{new String[]{"keyOne", "keyOne_valueOne"}}
        );

        // then
        assertThat(multiMap(
            true,
            KeyMatchStyle.MATCHING_KEY,
            new String[][]{new String[]{"keyOne", "notKeyOne_valueOne"}}
        ).containsAll(multiMap), is(false));
    }

    @Test
    public void shouldNotContainAllNotMatchSingleKeyMultipleEntries() {
        // given
        NottableStringMultiMap multiMap = multiMap(
            true,
            KeyMatchStyle.MATCHING_KEY,
            new String[]{"keyOne", "keyOne_valueOne"},
            new String[]{"keyTwo", "keyTwo_valueOne", "keyTwo_valueTwo"},
            new String[]{"keyThree", "keyThree_valueOne", "keyThree_valueTwo", "keyThree_valueThree"}
        );

        // then
        assertThat(multiMap(
            true,
            KeyMatchStyle.MATCHING_KEY,
            new String[][]{new String[]{"notKeyOne", "keyOne_valueOne"}}
        ).containsAll(multiMap), is(false));
    }

    @Test
    public void shouldNotContainAllNotMatchSingleValueMultipleEntries() {
        // given
        NottableStringMultiMap multiMap = multiMap(
            true,
            KeyMatchStyle.MATCHING_KEY,
            new String[]{"keyOne", "keyOne_valueOne"},
            new String[]{"keyTwo", "keyTwo_valueOne", "keyTwo_valueTwo"},
            new String[]{"keyThree", "keyThree_valueOne", "keyThree_valueTwo", "keyThree_valueThree"}
        );

        // then
        assertThat(multiMap(
            true,
            KeyMatchStyle.MATCHING_KEY,
            new String[][]{new String[]{"keyOne", "notKeyOne_valueOne"}}
        ).containsAll(multiMap), is(false));
        assertThat(multiMap(
            true,
            KeyMatchStyle.MATCHING_KEY,
            new String[][]{new String[]{"keyTwo", "keyTwo_valueOne", "notKeyTwo_valueTwo"}}
        ).containsAll(multiMap), is(false));
    }

    @Test
    public void shouldNotContainAllNotMatchMultipleKeysMultipleEntries() {
        // given
        NottableStringMultiMap multiMap = multiMap(
            true,
            KeyMatchStyle.MATCHING_KEY,
            new String[]{"keyOne", "keyOne_valueOne"},
            new String[]{"keyTwo", "keyTwo_valueOne", "keyTwo_valueTwo"},
            new String[]{"keyThree", "keyThree_valueOne", "keyThree_valueTwo", "keyThree_valueThree"}
        );

        // then
        assertThat(multiMap(
            true,
            KeyMatchStyle.MATCHING_KEY,
            new String[]{"notKeyOne", "keyOne_valueOne"},
            new String[]{"notKeyTwo", "keyTwo_valueOne", "keyTwo_valueTwo"}
        ).containsAll(multiMap), is(false));
    }

    @Test
    public void shouldNotContainAllNotMatchMultipleValuesMultipleEntries() {
        // given
        NottableStringMultiMap multiMap = multiMap(
            true,
            KeyMatchStyle.MATCHING_KEY,
            new String[]{"keyOne", "keyOne_valueOne"},
            new String[]{"keyTwo", "keyTwo_valueOne", "keyTwo_valueTwo"},
            new String[]{"keyThree", "keyThree_valueOne", "keyThree_valueTwo", "keyThree_valueThree"}
        );

        // then
        assertThat(multiMap(
            true,
            KeyMatchStyle.MATCHING_KEY,
            new String[]{"keyOne", "notKeyOne_valueOne"},
            new String[]{"keyTwo", "keyTwo_valueOne", "notKeyTwo_valueTwo"}
        ).containsAll(multiMap), is(false));
        assertThat(multiMap(
            true,
            KeyMatchStyle.MATCHING_KEY,
            new String[][]{new String[]{"keyTwo", "notKeyTwo_valueOne", "notKeyTwo_valueTwo"}}
        ).containsAll(multiMap), is(false));
    }

    // KEY SPECIFIC TESTS

    @Test
    public void shouldMatchSubSetWithSchema() {
        // given
        NottableStringMultiMap multiMap = multiMap(
            true,
            KeyMatchStyle.MATCHING_KEY,
            new NottableString[]{string("keyOne"), schemaString("{ \"type\": \"integer\" }")},
            new NottableString[]{string("keyTwo"), string("keyTwo_valueOne"), string("keyTwo_valueTwo")}
        );

        // then
        assertThat(multiMap(
            true,
            KeyMatchStyle.MATCHING_KEY,
            new String[]{"keyOne", "1"},
            new String[]{"keyTwo", "keyTwo_valueOne", "keyTwo_valueTwo"}
        ).containsAll(multiMap), is(true));
        assertThat(multiMap(
            true,
            KeyMatchStyle.MATCHING_KEY,
            new String[]{"keyOne", "1", "2", "3"},
            new String[]{"keyTwo", "keyTwo_valueOne", "keyTwo_valueTwo"}
        ).containsAll(multiMap), is(true));
        assertThat(multiMap(
            true,
            KeyMatchStyle.MATCHING_KEY,
            new String[][]{new String[]{"keyTwo", "keyTwo_valueOne", "keyTwo_valueTwo"}}
        ).containsAll(multiMap), is(false));
        assertThat(multiMap(
            true,
            KeyMatchStyle.MATCHING_KEY,
            new String[]{"keyOne", "1", "2", "a"},
            new String[]{"keyTwo", "keyTwo_valueOne", "keyTwo_valueTwo"}
        ).containsAll(multiMap), is(false));
        assertThat(multiMap(
            true,
            KeyMatchStyle.MATCHING_KEY,
            new String[]{"keyOne", "1", "a", "b"},
            new String[]{"keyTwo", "keyTwo_valueOne", "keyTwo_valueTwo"}
        ).containsAll(multiMap), is(false));
    }

    @Test
    public void shouldMatchSubSetWithNottableString() {
        // given
        NottableStringMultiMap multiMap = multiMap(
            true,
            KeyMatchStyle.MATCHING_KEY,
            new NottableString[]{string("keyOne"), string("keyOne_valueOne")},
            new NottableString[]{string("keyTwo"), string("keyTwo_valueOne"), string("keyTwo_valueTwo")}
        );

        // then
        assertThat(multiMap(
            true,
            KeyMatchStyle.MATCHING_KEY,
            new String[]{"keyOne", "keyOne_valueOne"},
            new String[]{"keyTwo", "keyTwo_valueOne", "keyTwo_valueTwo"}
        ).containsAll(multiMap), is(true));
        assertThat(multiMap(
            true,
            KeyMatchStyle.MATCHING_KEY,
            new String[]{"keyOne", "keyOne_valueOne", "keyOne_valueOne", "keyOne_valueOne"},
            new String[]{"keyTwo", "keyTwo_valueOne", "keyTwo_valueTwo"}
        ).containsAll(multiMap), is(true));
        assertThat(multiMap(
            true,
            KeyMatchStyle.MATCHING_KEY,
            new String[][]{new String[]{"keyTwo", "keyTwo_valueOne", "keyTwo_valueTwo"}}
        ).containsAll(multiMap), is(false));
        assertThat(multiMap(
            true,
            KeyMatchStyle.MATCHING_KEY,
            new String[]{"keyOne", "keyOne_valueOne", "keyOne_valueOne", "notKeyOne_valueOne"},
            new String[]{"keyTwo", "keyTwo_valueOne", "keyTwo_valueTwo"}
        ).containsAll(multiMap), is(false));
        assertThat(multiMap(
            true,
            KeyMatchStyle.MATCHING_KEY,
            new String[]{"keyOne", "keyOne_valueOne", "notKeyOne_valueOne", "notKeyOne_valueOne"},
            new String[]{"keyTwo", "keyTwo_valueOne", "keyTwo_valueTwo"}
        ).containsAll(multiMap), is(false));
    }

    @Test
    public void shouldMatchSubSetWithOptionalString() {
        // given
        NottableStringMultiMap multiMap = multiMap(
            true,
            KeyMatchStyle.MATCHING_KEY,
            new NottableString[]{string("?keyOne"), string("keyOne_valueOne")},
            new NottableString[]{string("keyTwo"), string("keyTwo_valueOne"), string("keyTwo_valueTwo")}
        );

        // then
        assertThat(multiMap(
            true,
            KeyMatchStyle.MATCHING_KEY,
            new String[]{"keyOne", "keyOne_valueOne"},
            new String[]{"keyTwo", "keyTwo_valueOne", "keyTwo_valueTwo"}
        ).containsAll(multiMap), is(true));
        assertThat(multiMap(
            true,
            KeyMatchStyle.MATCHING_KEY,
            new String[][]{new String[]{"keyTwo", "keyTwo_valueOne", "keyTwo_valueTwo"}}
        ).containsAll(multiMap), is(true));
        assertThat(multiMap(
            true,
            KeyMatchStyle.MATCHING_KEY,
            new String[]{"keyOne", "keyOne_valueOne", "keyOne_valueOne", "keyOne_valueOne"},
            new String[]{"keyTwo", "keyTwo_valueOne", "keyTwo_valueTwo"}
        ).containsAll(multiMap), is(true));
        assertThat(multiMap(
            true,
            KeyMatchStyle.MATCHING_KEY,
            new String[][]{new String[]{"keyTwo", "keyTwo_valueOne", "keyTwo_valueTwo"}}
        ).containsAll(multiMap), is(true));
        assertThat(multiMap(
            true,
            KeyMatchStyle.MATCHING_KEY,
            new String[]{"keyOne", "keyOne_valueOne", "keyOne_valueOne", "notKeyOne_valueOne"},
            new String[]{"keyTwo", "keyTwo_valueOne", "keyTwo_valueTwo"}
        ).containsAll(multiMap), is(false));
        assertThat(multiMap(
            true,
            KeyMatchStyle.MATCHING_KEY,
            new String[]{"keyOne", "keyOne_valueOne", "notKeyOne_valueOne", "notKeyOne_valueOne"},
            new String[]{"keyTwo", "keyTwo_valueOne", "keyTwo_valueTwo"}
        ).containsAll(multiMap), is(false));
    }
}
