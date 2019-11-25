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
            true, new NottableString[]{not("keyOne"), string("keyOneValue")}
        );

        // then
        assertThat(multiMap.containsAll(multiMap(
            true, new NottableString[]{not("keyOne"), string("keyOneValue")}
        )), is(true));

        // and then
        assertThat(multiMap.containsAll(multiMap(
            true, new NottableString[]{not("keyOne"), string("keyOneValue")}
        )), is(true));
    }

    @Test
    public void shouldContainAllExactMatchSingleKeyAndSingleValueForNottedValue() {
        // given
        CaseInsensitiveRegexMultiMap multiMap = multiMap(
            true, new NottableString[]{string("keyOne"), not("keyOneValue")}
        );

        // then
        assertThat(multiMap.containsAll(multiMap(
            true, new NottableString[]{string("keyOne"), not("keyOneValue")}
        )), is(true));

        // and then
        assertThat(multiMap.containsAll(multiMap(
            true, new NottableString[]{string("keyOne"), not("keyOneValue")}
        )), is(true));
    }

    @Test
    public void shouldContainAllExactMatchSingleKeyAndSingleValueForNottedKeyAndValue() {
        // given
        CaseInsensitiveRegexMultiMap multiMap = multiMap(
            true, new NottableString[]{not("keyOne"), not("keyOneValue")}
        );

        // then
        assertThat(multiMap.containsAll(multiMap(
            true, new NottableString[]{not("keyOne"), not("keyOneValue")}
        )), is(true));

        // and then
        assertThat(multiMap.containsAll(multiMap(
            true, new NottableString[]{not("keyOne"), not("keyOneValue")}
        )), is(true));
    }

    @Test
    public void shouldContainAllSubSetSingleKeyAndSingleValueForNottedKey() {
        // given
        CaseInsensitiveRegexMultiMap multiMap = multiMap(
            true, new NottableString[]{not("keyOne"), string("keyOneValue")},
            new NottableString[]{string("keyTwo"), string("keyTwoValue")},
            new NottableString[]{string("keyThree"), string("keyThreeValue"), string("keyThree_valueTwo")}
        );

        // then
        assertThat(multiMap.containsAll(multiMap(
            true, new NottableString[]{not("keyOne"), string("keyOneValue")}
        )), is(true));

        // and then
        assertThat(multiMap.containsAll(multiMap(
            true, new NottableString[]{not("keyOne"), string("keyOneValue")}
        )), is(true));
    }

    @Test
    public void shouldContainAllSubSetSingleKeyAndSingleValueForNottedValue() {
        // given
        CaseInsensitiveRegexMultiMap multiMap = multiMap(
            true, new NottableString[]{string("keyOne"), not("keyOneValue")},
            new NottableString[]{string("keyTwo"), string("keyTwoValue")},
            new NottableString[]{string("keyThree"), string("keyThreeValue"), string("keyThree_valueTwo")}
        );

        // then
        assertThat(multiMap.containsAll(multiMap(
            true, new NottableString[]{string("keyOne"), not("keyOneValue")}
        )), is(true));

        // and then
        assertThat(multiMap.containsAll(multiMap(
            true, new NottableString[]{string("keyOne"), not("keyOneValue")}
        )), is(true));
    }

    @Test
    public void shouldContainAllSubSetSingleKeyAndSingleValueForNottedKeyAndValue() {
        // given
        CaseInsensitiveRegexMultiMap multiMap = multiMap(
            true, new NottableString[]{not("keyOne"), not("keyOneValue")},
            new NottableString[]{string("keyTwo"), string("keyTwoValue")},
            new NottableString[]{string("keyThree"), string("keyThreeValue"), string("keyThree_valueTwo")}
        );

        // then
        assertThat(multiMap.containsAll(multiMap(
            true, new NottableString[]{not("keyOne"), string("notKeyOneValue")}
        )), is(true));

        // and then
        assertThat(multiMap.containsAll(multiMap(
            true, new NottableString[]{not("keyOne"), not("keyOneValue")}
        )), is(true));
    }

    @Test
    public void shouldContainAllExactMatchMultipleKeyAndMultipleValuesForNottedKey() {
        // given
        CaseInsensitiveRegexMultiMap multiMap = multiMap(
            true, new NottableString[]{string("keyOne"), string("keyOneValue")},
            new NottableString[]{not("keyTwo"), string("keyTwoValue")}
        );

        // then
        assertThat(multiMap.containsAll(multiMap(
            true, new NottableString[]{string("keyOne"), string("keyOneValue")},
            new NottableString[]{not("keyTwo"), string("keyTwoValue")}
        )), is(true));

        // and then
        assertThat(multiMap.containsAll(multiMap(
            true, new NottableString[]{string("keyOne"), string("keyOneValue")},
            new NottableString[]{not("keyTwo"), string("keyTwoValue")}
        )), is(true));
    }

    @Test
    public void shouldContainAllExactMatchMultipleKeyAndMultipleValuesForNottedValue() {
        // given
        CaseInsensitiveRegexMultiMap multiMap = multiMap(
            true, new NottableString[]{string("keyOne"), string("keyOneValue")},
            new NottableString[]{string("keyTwo"), not("keyTwoValue")}
        );

        // then
        assertThat(multiMap.containsAll(multiMap(
            true, new NottableString[]{string("keyOne"), string("keyOneValue")},
            new NottableString[]{string("keyTwo"), not("keyTwoValue")}
        )), is(true));

        // and then
        assertThat(multiMap.containsAll(multiMap(
            true, new NottableString[]{string("keyOne"), string("keyOneValue")},
            new NottableString[]{string("keyTwo"), not("keyTwoValue")}
        )), is(true));
    }

    @Test
    public void shouldContainAllExactMatchMultipleKeyAndMultipleValuesForNottedKeyAndValue() {
        // given
        CaseInsensitiveRegexMultiMap multiMap = multiMap(
            true, new NottableString[]{string("keyOne"), string("keyOneValue")},
            new NottableString[]{not("keyTwo"), not("keyTwoValue")}
        );

        // then
        assertThat(multiMap.containsAll(multiMap(
            true, new NottableString[]{string("keyOne"), string("keyOneValue")},
            new NottableString[]{not("keyTwo"), not("keyTwoValue")}
        )), is(true));

        // and then
        assertThat(multiMap.containsAll(multiMap(
            true, new NottableString[]{string("keyOne"), string("keyOneValue")},
            new NottableString[]{not("keyTwo"), not("keyTwoValue")}
        )), is(true));
    }

    @Test
    public void shouldContainAllSubSetMultipleKeyAndMultipleValuesForNottedKey() {
        // given
        CaseInsensitiveRegexMultiMap multiMap = multiMap(
            true, new NottableString[]{string("keyOne"), string("keyOneValue")},
            new NottableString[]{not("keyTwo"), string("keyTwoValue")},
            new NottableString[]{string("keyThree"), string("keyThreeValue"), string("keyThree_valueTwo")}
        );

        // then
        assertThat(multiMap.containsAll(multiMap(
            true, new NottableString[]{string("keyOne"), string("keyOneValue")},
            new NottableString[]{not("keyTwo"), string("keyTwoValue")}
        )), is(true));

        // and then
        assertThat(multiMap.containsAll(multiMap(
            true, new NottableString[]{string("keyOne"), string("keyOneValue")},
            new NottableString[]{not("keyTwo"), string("keyTwoValue")}
        )), is(true));
    }

    @Test
    public void shouldContainAllSubSetMultipleKeyAndMultipleValuesForNottedValue() {
        // given
        CaseInsensitiveRegexMultiMap multiMap = multiMap(
            true, new NottableString[]{string("keyOne"), string("keyOneValue")},
            new NottableString[]{string("keyTwo"), not("keyTwoValue")},
            new NottableString[]{string("keyThree"), string("keyThreeValue"), string("keyThree_valueTwo")}
        );

        // then
        assertThat(multiMap.containsAll(multiMap(
            true, new NottableString[]{string("keyOne"), string("keyOneValue")},
            new NottableString[]{string("keyTwo"), not("keyTwoValue")}
        )), is(true));

        // and then
        assertThat(multiMap.containsAll(multiMap(
            true, new NottableString[]{string("keyOne"), string("keyOneValue")},
            new NottableString[]{string("keyTwo"), not("keyTwoValue")}
        )), is(true));
    }

    @Test
    public void shouldContainAllSubSetMultipleKeyAndMultipleValuesForNottedKeyAndValue() {
        // given
        CaseInsensitiveRegexMultiMap multiMap = multiMap(
            true, new NottableString[]{string("keyOne"), string("keyOneValue")},
            new NottableString[]{not("keyTwo"), not("keyTwoValue")},
            new NottableString[]{string("keyThree"), string("keyThreeValue"), string("keyThree_valueTwo")}
        );

        // then
        assertThat(multiMap.containsAll(multiMap(
            true, new NottableString[]{string("keyOne"), string("keyOneValue")},
            new NottableString[]{not("keyTwo"), not("keyTwoValue")}
        )), is(true));

        // and then
        assertThat(multiMap.containsAll(multiMap(
            true, new NottableString[]{string("keyOne"), string("keyOneValue")},
            new NottableString[]{not("keyTwo"), not("keyTwoValue")}
        )), is(true));
    }

    // FROM HERE

    @Test
    public void shouldContainAllBothNottedMultipleKeysMultipleEntries() {
        // given
        CaseInsensitiveRegexMultiMap multiMap = multiMap(
            true, new NottableString[]{not("keyOne"), string("keyOneValue")},
            new NottableString[]{not("keyTwo"), string("keyTwoValue")},
            new NottableString[]{string("keyThree"), string("keyThreeValue"), string("keyThree_valueTwo")}
        );

        // then
        assertThat(multiMap.containsAll(multiMap(
            true, new NottableString[]{not("keyOne"), string("keyOneValue")},
            new NottableString[]{not("keyTwo"), string("keyTwoValue")}
        )), is(true));
    }

    @Test
    public void shouldContainAllBothNottedMultipleValuesMultipleEntries() {
        // given
        CaseInsensitiveRegexMultiMap multiMap = multiMap(
            true, new NottableString[]{string("keyOne"), not("keyOneValue")},
            new NottableString[]{string("keyTwo"), not("keyTwoValue")},
            new NottableString[]{string("keyThree"), string("keyThreeValue"), string("keyThree_valueTwo")}
        );

        // then
        assertThat(multiMap.containsAll(multiMap(
            true, new NottableString[]{string("keyOne"), not("keyOneValue")},
            new NottableString[]{string("keyTwo"), not("keyTwoValue")}
        )), is(true));
        assertThat(multiMap.containsAll(multiMap(
            true, new NottableString[]{string("keyTwo"), not("keyTwo.*")}
        )), is(true));
    }

    @Test
    public void shouldContainAllBothNottedMultipleKeyAndValuesMultipleEntries() {
        // given
        CaseInsensitiveRegexMultiMap multiMap = multiMap(
            true, new NottableString[]{not("keyOne"), not("keyOneValue")},
            new NottableString[]{not("keyTwo"), not("keyTwoValue")},
            new NottableString[]{string("keyThree"), string("keyThreeValue"), string("keyThree_valueTwo")}
        );

        // then
        assertThat(multiMap.containsAll(multiMap(
            true, new NottableString[]{not("keyOne"), not("keyOneValue")},
            new NottableString[]{not("keyTwo"), not("keyTwoValue")},
            new NottableString[]{string("keyThree"), string("keyThreeValue"), string("keyThree_valueTwo")}
        )), is(true));
        assertThat(multiMap.containsAll(multiMap(
            true, new NottableString[]{not("keyOne"), not("keyOneValue")},
            new NottableString[]{not("keyTwo"), not("keyTwoValue")}
        )), is(true));
        assertThat(multiMap.containsAll(multiMap(
            true, new NottableString[]{not("keyTwo"), not("keyTwo.*")}
        )), is(true));
    }

}
