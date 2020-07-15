package org.mockserver.collections.hashmap.nottablematchedandmatcher;

import org.junit.Test;
import org.mockserver.collections.NottableStringHashMap;
import org.mockserver.model.NottableString;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockserver.collections.NottableStringHashMap.hashMap;
import static org.mockserver.model.NottableString.not;
import static org.mockserver.model.NottableString.string;

/**
 * @author jamesdbloom
 */
public class CaseInsensitiveRegexHashMapTestNottableContainsAll {

    @Test
    public void shouldContainAllExactMatchSingleKeyAndSingleValueForNottedKey() {
        // given
        NottableStringHashMap hashMap = hashMap(
            true, new NottableString[]{not("keyOne"), string("keyOneValue")}
        );

        // then
        assertThat(hashMap.containsAll(hashMap(
            true, new NottableString[]{not("keyOne"), string("keyOneValue")}
        )), is(true));

        // and then
        assertThat(hashMap.containsAll(hashMap(
            true, new NottableString[]{not("keyOne"), string("keyOneValue")}
        )), is(true));
    }

    @Test
    public void shouldContainAllExactMatchSingleKeyAndSingleValueForNottedValue() {
        // given
        NottableStringHashMap hashMap = hashMap(
            true, new NottableString[]{string("keyOne"), not("keyOneValue")}
        );

        // then
        assertThat(hashMap.containsAll(hashMap(
            true, new NottableString[]{string("keyOne"), not("keyOneValue")}
        )), is(true));

        // and then
        assertThat(hashMap.containsAll(hashMap(
            true, new NottableString[]{string("keyOne"), not("keyOneValue")}
        )), is(true));
    }

    @Test
    public void shouldContainAllExactMatchSingleKeyAndSingleValueForNottedKeyAndValue() {
        // given
        NottableStringHashMap hashMap = hashMap(
            true, new NottableString[]{not("keyOne"), not("keyOneValue")}
        );

        // then
        assertThat(hashMap.containsAll(hashMap(
            true, new NottableString[]{not("keyOne"), not("keyOneValue")}
        )), is(true));

        // and then
        assertThat(hashMap.containsAll(hashMap(
            true, new NottableString[]{not("keyOne"), not("keyOneValue")}
        )), is(true));
    }

    @Test
    public void shouldContainAllSubSetSingleKeyAndSingleValueForNottedKey() {
        // given
        NottableStringHashMap hashMap = hashMap(
            true, new NottableString[]{not("keyOne"), string("keyOneValue")},
            new NottableString[]{string("keyTwo"), string("keyTwoValue")},
            new NottableString[]{string("keyThree"), string("keyThreeValue"), string("keyThree_valueTwo")}
        );

        // then
        assertThat(hashMap.containsAll(hashMap(
            true, new NottableString[]{not("keyOne"), string("keyOneValue")}
        )), is(true));

        // and then
        assertThat(hashMap.containsAll(hashMap(
            true, new NottableString[]{not("keyOne"), string("keyOneValue")}
        )), is(true));
    }

    @Test
    public void shouldContainAllSubSetSingleKeyAndSingleValueForNottedValue() {
        // given
        NottableStringHashMap hashMap = hashMap(
            true, new NottableString[]{string("keyOne"), not("keyOneValue")},
            new NottableString[]{string("keyTwo"), string("keyTwoValue")},
            new NottableString[]{string("keyThree"), string("keyThreeValue"), string("keyThree_valueTwo")}
        );

        // then
        assertThat(hashMap.containsAll(hashMap(
            true, new NottableString[]{string("keyOne"), not("keyOneValue")}
        )), is(true));

        // and then
        assertThat(hashMap.containsAll(hashMap(
            true, new NottableString[]{string("keyOne"), not("keyOneValue")}
        )), is(true));
    }

    @Test
    public void shouldContainAllSubSetSingleKeyAndSingleValueForNottedKeyAndValue() {
        // given
        NottableStringHashMap hashMap = hashMap(
            true, new NottableString[]{not("keyOne"), not("keyOneValue")},
            new NottableString[]{string("keyTwo"), string("keyTwoValue")},
            new NottableString[]{string("keyThree"), string("keyThreeValue"), string("keyThree_valueTwo")}
        );

        // then
        assertThat(hashMap.containsAll(hashMap(
            true, new NottableString[]{not("keyOne"), string("notKeyOneValue")}
        )), is(true));

        // and then
        assertThat(hashMap.containsAll(hashMap(
            true, new NottableString[]{not("keyOne"), not("keyOneValue")}
        )), is(true));
    }

    @Test
    public void shouldContainAllExactMatchMultipleKeyAndMultipleValuesForNottedKey() {
        // given
        NottableStringHashMap hashMap = hashMap(
            true, new NottableString[]{string("keyOne"), string("keyOneValue")},
            new NottableString[]{not("keyTwo"), string("keyTwoValue")}
        );

        // then
        assertThat(hashMap.containsAll(hashMap(
            true, new NottableString[]{string("keyOne"), string("keyOneValue")},
            new NottableString[]{not("keyTwo"), string("keyTwoValue")}
        )), is(true));

        // and then
        assertThat(hashMap.containsAll(hashMap(
            true, new NottableString[]{string("keyOne"), string("keyOneValue")},
            new NottableString[]{not("keyTwo"), string("keyTwoValue")}
        )), is(true));
    }

    @Test
    public void shouldContainAllExactMatchMultipleKeyAndMultipleValuesForNottedValue() {
        // given
        NottableStringHashMap hashMap = hashMap(
            true, new NottableString[]{string("keyOne"), string("keyOneValue")},
            new NottableString[]{string("keyTwo"), not("keyTwoValue")}
        );

        // then
        assertThat(hashMap.containsAll(hashMap(
            true, new NottableString[]{string("keyOne"), string("keyOneValue")},
            new NottableString[]{string("keyTwo"), not("keyTwoValue")}
        )), is(true));

        // and then
        assertThat(hashMap.containsAll(hashMap(
            true, new NottableString[]{string("keyOne"), string("keyOneValue")},
            new NottableString[]{string("keyTwo"), not("keyTwoValue")}
        )), is(true));
    }

    @Test
    public void shouldContainAllExactMatchMultipleKeyAndMultipleValuesForNottedKeyAndValue() {
        // given
        NottableStringHashMap hashMap = hashMap(
            true, new NottableString[]{string("keyOne"), string("keyOneValue")},
            new NottableString[]{not("keyTwo"), not("keyTwoValue")}
        );

        // then
        assertThat(hashMap.containsAll(hashMap(
            true, new NottableString[]{string("keyOne"), string("keyOneValue")},
            new NottableString[]{not("keyTwo"), not("keyTwoValue")}
        )), is(true));

        // and then
        assertThat(hashMap.containsAll(hashMap(
            true, new NottableString[]{string("keyOne"), string("keyOneValue")},
            new NottableString[]{not("keyTwo"), not("keyTwoValue")}
        )), is(true));
    }

    @Test
    public void shouldContainAllSubSetMultipleKeyAndMultipleValuesForNottedKey() {
        // given
        NottableStringHashMap hashMap = hashMap(
            true, new NottableString[]{string("keyOne"), string("keyOneValue")},
            new NottableString[]{not("keyTwo"), string("keyTwoValue")},
            new NottableString[]{string("keyThree"), string("keyThreeValue"), string("keyThree_valueTwo")}
        );

        // then
        assertThat(hashMap.containsAll(hashMap(
            true, new NottableString[]{string("keyOne"), string("keyOneValue")},
            new NottableString[]{not("keyTwo"), string("keyTwoValue")}
        )), is(true));

        // and then
        assertThat(hashMap.containsAll(hashMap(
            true, new NottableString[]{string("keyOne"), string("keyOneValue")},
            new NottableString[]{not("keyTwo"), string("keyTwoValue")}
        )), is(true));
    }

    @Test
    public void shouldContainAllSubSetMultipleKeyAndMultipleValuesForNottedValue() {
        // given
        NottableStringHashMap hashMap = hashMap(
            true, new NottableString[]{string("keyOne"), string("keyOneValue")},
            new NottableString[]{string("keyTwo"), not("keyTwoValue")},
            new NottableString[]{string("keyThree"), string("keyThreeValue"), string("keyThree_valueTwo")}
        );

        // then
        assertThat(hashMap.containsAll(hashMap(
            true, new NottableString[]{string("keyOne"), string("keyOneValue")},
            new NottableString[]{string("keyTwo"), not("keyTwoValue")}
        )), is(true));

        // and then
        assertThat(hashMap.containsAll(hashMap(
            true, new NottableString[]{string("keyOne"), string("keyOneValue")},
            new NottableString[]{string("keyTwo"), not("keyTwoValue")}
        )), is(true));
    }

    @Test
    public void shouldContainAllSubSetMultipleKeyAndMultipleValuesForNottedKeyAndValue() {
        // given
        NottableStringHashMap hashMap = hashMap(
            true, new NottableString[]{string("keyOne"), string("keyOneValue")},
            new NottableString[]{not("keyTwo"), not("keyTwoValue")},
            new NottableString[]{string("keyThree"), string("keyThreeValue"), string("keyThree_valueTwo")}
        );

        // then
        assertThat(hashMap.containsAll(hashMap(
            true, new NottableString[]{string("keyOne"), string("keyOneValue")},
            new NottableString[]{not("keyTwo"), not("keyTwoValue")}
        )), is(true));

        // and then
        assertThat(hashMap.containsAll(hashMap(
            true, new NottableString[]{string("keyOne"), string("keyOneValue")},
            new NottableString[]{not("keyTwo"), not("keyTwoValue")}
        )), is(true));
    }

    // FROM HERE

    @Test
    public void shouldContainAllBothNottedMultipleKeysMultipleEntries() {
        // given
        NottableStringHashMap hashMap = hashMap(
            true, new NottableString[]{not("keyOne"), string("keyOneValue")},
            new NottableString[]{not("keyTwo"), string("keyTwoValue")},
            new NottableString[]{string("keyThree"), string("keyThreeValue"), string("keyThree_valueTwo")}
        );

        // then
        assertThat(hashMap.containsAll(hashMap(
            true, new NottableString[]{not("keyOne"), string("keyOneValue")},
            new NottableString[]{not("keyTwo"), string("keyTwoValue")}
        )), is(true));
    }

    @Test
    public void shouldContainAllBothNottedMultipleValuesMultipleEntries() {
        // given
        NottableStringHashMap hashMap = hashMap(
            true, new NottableString[]{string("keyOne"), not("keyOneValue")},
            new NottableString[]{string("keyTwo"), not("keyTwoValue")},
            new NottableString[]{string("keyThree"), string("keyThreeValue"), string("keyThree_valueTwo")}
        );

        // then
        assertThat(hashMap.containsAll(hashMap(
            true, new NottableString[]{string("keyOne"), not("keyOneValue")},
            new NottableString[]{string("keyTwo"), not("keyTwoValue")}
        )), is(true));
        assertThat(hashMap.containsAll(hashMap(
            true, new NottableString[]{string("keyTwo"), not("keyTwo.*")}
        )), is(true));
    }

    @Test
    public void shouldContainAllBothNottedMultipleKeyAndValuesMultipleEntries() {
        // given
        NottableStringHashMap hashMap = hashMap(
            true, new NottableString[]{not("keyOne"), not("keyOneValue")},
            new NottableString[]{not("keyTwo"), not("keyTwoValue")},
            new NottableString[]{string("keyThree"), string("keyThreeValue"), string("keyThree_valueTwo")}
        );

        // then
        assertThat(hashMap.containsAll(hashMap(
            true, new NottableString[]{not("keyOne"), not("keyOneValue")},
            new NottableString[]{not("keyTwo"), not("keyTwoValue")},
            new NottableString[]{string("keyThree"), string("keyThreeValue"), string("keyThree_valueTwo")}
        )), is(true));
        assertThat(hashMap.containsAll(hashMap(
            true, new NottableString[]{not("keyOne"), not("keyOneValue")},
            new NottableString[]{not("keyTwo"), not("keyTwoValue")}
        )), is(true));
        assertThat(hashMap.containsAll(hashMap(
            true, new NottableString[]{not("keyTwo"), not("keyTwo.*")}
        )), is(true));
    }

}
