package org.mockserver.collections.hashmap;

import org.junit.Test;
import org.mockserver.collections.CaseInsensitiveRegexHashMap;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockserver.collections.CaseInsensitiveRegexHashMap.hashMap;

/**
 * @author jamesdbloom
 */
public class CaseInsensitiveRegexHashMapTestContainsAll {

    @Test
    public void shouldContainAllForEmptyMapMatchedAgainstMapWithSingleEntry() {
        // given
        CaseInsensitiveRegexHashMap hashMap = hashMap(
            new String[]{"keyOne", "keyOneValue"}
        );

        // then
        assertThat(hashMap.containsAll(hashMap(
            new String[]{}
        )), is(true));
    }

    @Test
    public void shouldContainAllForEmptyMapMatchedAgainstMapWithMultipleEntries() {
        // given
        CaseInsensitiveRegexHashMap hashMap = hashMap(
            new String[]{"keyOne", "keyOneValue"},
            new String[]{"keyTwo", "keyTwoValue"},
            new String[]{"keyThree", "keyThreeValue"}
        );

        // then
        assertThat(hashMap.containsAll(hashMap(
            new String[]{}
        )), is(true));
    }

    @Test
    public void shouldContainAllExactMatchSingleKeyAndSingleValue() {
        // given
        CaseInsensitiveRegexHashMap hashMap = hashMap(
            new String[]{"keyOne", "keyOneValue"}
        );

        // then
        assertThat(hashMap.containsAll(hashMap(
            new String[]{"keyOne", "keyOneValue"}
        )), is(true));
    }

    @Test
    public void shouldContainAllSubSetSingleKeyAndSingleValue() {
        // given
        CaseInsensitiveRegexHashMap hashMap = hashMap(
            new String[]{"keyOne", "keyOneValue"},
            new String[]{"keyTwo", "keyTwoValue"},
            new String[]{"keyThree", "keyThreeValue"}
        );

        // then
        assertThat(hashMap.containsAll(hashMap(
            new String[]{"keyOne", "keyOneValue"}
        )), is(true));
    }

    @Test
    public void shouldContainAllExactMatchMultipleKeyAndMultipleValues() {
        // given
        CaseInsensitiveRegexHashMap hashMap = hashMap(
            new String[]{"keyOne", "keyOneValue"},
            new String[]{"keyTwo", "keyTwoValue"}
        );

        // then
        assertThat(hashMap.containsAll(hashMap(
            new String[]{"keyOne", "keyOneValue"},
            new String[]{"keyTwo", "keyTwoValue"}
        )), is(true));
    }

    @Test
    public void shouldContainAllSubSetMultipleKeyAndMultipleValues() {
        // given
        CaseInsensitiveRegexHashMap hashMap = hashMap(
            new String[]{"keyOne", "keyOneValue"},
            new String[]{"keyTwo", "keyTwoValue"},
            new String[]{"keyThree", "keyThreeValue"}
        );

        // then
        assertThat(hashMap.containsAll(hashMap(
            new String[]{"keyOne", "keyOneValue"},
            new String[]{"keyTwo", "keyTwoValue"}
        )), is(true));
    }

    @Test
    public void shouldNotContainAllNotMatchSingleKeySingleEntry() {
        // given
        CaseInsensitiveRegexHashMap hashMap = hashMap(
            new String[]{"keyOne", "keyOneValue"}
        );

        // then
        assertThat(hashMap.containsAll(hashMap(
            new String[]{"notKeyOne", "keyOneValue"}
        )), is(false));
    }

    @Test
    public void shouldNotContainAllNotMatchSingleValueSingleEntry() {
        // given
        CaseInsensitiveRegexHashMap hashMap = hashMap(
            new String[]{"keyOne", "keyOneValue"}
        );

        // then
        assertThat(hashMap.containsAll(hashMap(
            new String[]{"keyOne", "notKeyOneValue"}
        )), is(false));
    }

    @Test
    public void shouldNotContainAllNotMatchSingleKeyMultipleEntries() {
        // given
        CaseInsensitiveRegexHashMap hashMap = hashMap(
            new String[]{"keyOne", "keyOneValue"},
            new String[]{"keyTwo", "keyTwoValue"},
            new String[]{"keyThree", "keyThreeValue"}
        );

        // then
        assertThat(hashMap.containsAll(hashMap(
            new String[]{"notKeyOne", "keyOneValue"}
        )), is(false));
    }

    @Test
    public void shouldNotContainAllNotMatchSingleValueMultipleEntries() {
        // given
        CaseInsensitiveRegexHashMap hashMap = hashMap(
            new String[]{"keyOne", "keyOneValue"},
            new String[]{"keyTwo", "keyTwoValue"},
            new String[]{"keyThree", "keyThreeValue"}
        );

        // then
        assertThat(hashMap.containsAll(hashMap(
            new String[]{"keyOne", "notKeyOneValue"}
        )), is(false));
    }

    @Test
    public void shouldNotContainAllNotMatchMultipleKeysMultipleEntries() {
        // given
        CaseInsensitiveRegexHashMap hashMap = hashMap(
            new String[]{"keyOne", "keyOneValue"},
            new String[]{"keyTwo", "keyTwoValue"},
            new String[]{"keyThree", "keyThreeValue"}
        );

        // then
        assertThat(hashMap.containsAll(hashMap(
            new String[]{"notKeyOne", "keyOneValue"},
            new String[]{"notKeyTwo", "keyTwoValue"}
        )), is(false));
    }

    @Test
    public void shouldNotContainAllNotMatchMultipleValuesMultipleEntries() {
        // given
        CaseInsensitiveRegexHashMap hashMap = hashMap(
            new String[]{"keyOne", "keyOneValue"},
            new String[]{"keyTwo", "keyTwoValue"},
            new String[]{"keyThree", "keyThreeValue"}
        );

        // then
        assertThat(hashMap.containsAll(hashMap(
            new String[]{"keyOne", "notKeyOneValue"},
            new String[]{"keyTwo", "keyTwoValue"}
        )), is(false));
        assertThat(hashMap.containsAll(hashMap(
            new String[]{"keyTwo", "notKeyTwoValue"}
        )), is(false));
    }
}
