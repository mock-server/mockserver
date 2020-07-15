package org.mockserver.collections.hashmap;

import org.junit.Test;
import org.mockserver.collections.NottableStringHashMap;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockserver.collections.NottableStringHashMap.hashMap;

/**
 * @author jamesdbloom
 */
public class CaseInsensitiveRegexHashMapTestContainsAll {

    @Test
    public void shouldContainAllForEmptyMapMatchedAgainstMapWithSingleEntry() {
        // given
        NottableStringHashMap hashMap = hashMap(
            true, new String[]{"keyOne", "keyOneValue"}
        );

        // then
        assertThat(hashMap.containsAll(hashMap(
            true, new String[]{}
        )), is(true));
    }

    @Test
    public void shouldContainAllForEmptyMapMatchedAgainstMapWithMultipleEntries() {
        // given
        NottableStringHashMap hashMap = hashMap(
            true, new String[]{"keyOne", "keyOneValue"},
            new String[]{"keyTwo", "keyTwoValue"},
            new String[]{"keyThree", "keyThreeValue"}
        );

        // then
        assertThat(hashMap.containsAll(hashMap(
            true, new String[]{}
        )), is(true));
    }

    @Test
    public void shouldContainAllExactMatchSingleKeyAndSingleValue() {
        // given
        NottableStringHashMap hashMap = hashMap(
            true, new String[]{"keyOne", "keyOneValue"}
        );

        // then
        assertThat(hashMap.containsAll(hashMap(
            true, new String[]{"keyOne", "keyOneValue"}
        )), is(true));
    }

    @Test
    public void shouldContainAllSubSetSingleKeyAndSingleValue() {
        // given
        NottableStringHashMap hashMap = hashMap(
            true, new String[]{"keyOne", "keyOneValue"},
            new String[]{"keyTwo", "keyTwoValue"},
            new String[]{"keyThree", "keyThreeValue"}
        );

        // then
        assertThat(hashMap.containsAll(hashMap(
            true, new String[]{"keyOne", "keyOneValue"}
        )), is(true));
    }

    @Test
    public void shouldContainAllExactMatchMultipleKeyAndMultipleValues() {
        // given
        NottableStringHashMap hashMap = hashMap(
            true, new String[]{"keyOne", "keyOneValue"},
            new String[]{"keyTwo", "keyTwoValue"}
        );

        // then
        assertThat(hashMap.containsAll(hashMap(
            true, new String[]{"keyOne", "keyOneValue"},
            new String[]{"keyTwo", "keyTwoValue"}
        )), is(true));
    }

    @Test
    public void shouldContainAllSubSetMultipleKeyAndMultipleValues() {
        // given
        NottableStringHashMap hashMap = hashMap(
            true, new String[]{"keyOne", "keyOneValue"},
            new String[]{"keyTwo", "keyTwoValue"},
            new String[]{"keyThree", "keyThreeValue"}
        );

        // then
        assertThat(hashMap.containsAll(hashMap(
            true, new String[]{"keyOne", "keyOneValue"},
            new String[]{"keyTwo", "keyTwoValue"}
        )), is(true));
    }

    @Test
    public void shouldNotContainAllNotMatchSingleKeySingleEntry() {
        // given
        NottableStringHashMap hashMap = hashMap(
            true, new String[]{"keyOne", "keyOneValue"}
        );

        // then
        assertThat(hashMap.containsAll(hashMap(
            true, new String[]{"notKeyOne", "keyOneValue"}
        )), is(false));
    }

    @Test
    public void shouldNotContainAllNotMatchSingleValueSingleEntry() {
        // given
        NottableStringHashMap hashMap = hashMap(
            true, new String[]{"keyOne", "keyOneValue"}
        );

        // then
        assertThat(hashMap.containsAll(hashMap(
            true, new String[]{"keyOne", "notKeyOneValue"}
        )), is(false));
    }

    @Test
    public void shouldNotContainAllNotMatchSingleKeyMultipleEntries() {
        // given
        NottableStringHashMap hashMap = hashMap(
            true, new String[]{"keyOne", "keyOneValue"},
            new String[]{"keyTwo", "keyTwoValue"},
            new String[]{"keyThree", "keyThreeValue"}
        );

        // then
        assertThat(hashMap.containsAll(hashMap(
            true, new String[]{"notKeyOne", "keyOneValue"}
        )), is(false));
    }

    @Test
    public void shouldNotContainAllNotMatchSingleValueMultipleEntries() {
        // given
        NottableStringHashMap hashMap = hashMap(
            true, new String[]{"keyOne", "keyOneValue"},
            new String[]{"keyTwo", "keyTwoValue"},
            new String[]{"keyThree", "keyThreeValue"}
        );

        // then
        assertThat(hashMap.containsAll(hashMap(
            true, new String[]{"keyOne", "notKeyOneValue"}
        )), is(false));
    }

    @Test
    public void shouldNotContainAllNotMatchMultipleKeysMultipleEntries() {
        // given
        NottableStringHashMap hashMap = hashMap(
            true, new String[]{"keyOne", "keyOneValue"},
            new String[]{"keyTwo", "keyTwoValue"},
            new String[]{"keyThree", "keyThreeValue"}
        );

        // then
        assertThat(hashMap.containsAll(hashMap(
            true, new String[]{"notKeyOne", "keyOneValue"},
            new String[]{"notKeyTwo", "keyTwoValue"}
        )), is(false));
    }

    @Test
    public void shouldNotContainAllNotMatchMultipleValuesMultipleEntries() {
        // given
        NottableStringHashMap hashMap = hashMap(
            true, new String[]{"keyOne", "keyOneValue"},
            new String[]{"keyTwo", "keyTwoValue"},
            new String[]{"keyThree", "keyThreeValue"}
        );

        // then
        assertThat(hashMap.containsAll(hashMap(
            true, new String[]{"keyOne", "notKeyOneValue"},
            new String[]{"keyTwo", "keyTwoValue"}
        )), is(false));
        assertThat(hashMap.containsAll(hashMap(
            true, new String[]{"keyTwo", "notKeyTwoValue"}
        )), is(false));
    }
}
