package org.mockserver.collections.hashmap.nottablematched;

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
            true, new String[]{"keyOne", "keyOneValue"}
        );

        // then
        assertThat(hashMap.containsAll(hashMap(
            true, new NottableString[]{not("notKeyOne"), string("keyOneValue")}
        )), is(true));
    }

    @Test
    public void shouldContainAllExactMatchSingleKeyAndSingleValueForNottedValue() {
        // given
        NottableStringHashMap hashMap = hashMap(
            true, new String[]{"keyOne", "keyOneValue"}
        );

        // then
        assertThat(hashMap.containsAll(hashMap(
            true, new NottableString[]{string("keyOne"), not("notKeyOneValue")}
        )), is(true));
    }

    @Test
    public void shouldContainAllExactMatchSingleKeyAndSingleValueForNottedKeyAndValue() {
        // given
        NottableStringHashMap hashMap = hashMap(
            true, new String[]{"keyOne", "keyOneValue"}
        );

        // then
        assertThat(hashMap.containsAll(hashMap(
            true, new NottableString[]{not("notKeyOne"), not("notKeyOneValue")}
        )), is(true));
    }

    @Test
    public void shouldContainAllSubSetSingleKeyAndSingleValueForNottedKey() {
        // given
        NottableStringHashMap hashMap = hashMap(
            true, new String[]{"keyOne", "keyOneValue"},
            new String[]{"keyTwo", "keyTwoValue"},
            new String[]{"keyThree", "keyThreeValue"}
        );

        // then
        assertThat(hashMap.containsAll(hashMap(
            true, new NottableString[]{not("notKeyOne"), string("keyOneValue")}
        )), is(true));
    }

    @Test
    public void shouldContainAllSubSetSingleKeyAndSingleValueForNottedValue() {
        // given
        NottableStringHashMap hashMap = hashMap(
            true, new String[]{"keyOne", "keyOneValue"},
            new String[]{"keyTwo", "keyTwoValue"},
            new String[]{"keyThree", "keyThreeValue"}
        );

        // then
        assertThat(hashMap.containsAll(hashMap(
            true, new NottableString[]{string("keyOne"), not("notKeyOneValue")}
        )), is(true));
    }

    @Test
    public void shouldContainAllSubSetSingleKeyAndSingleValueForNottedKeyAndValue() {
        // given
        NottableStringHashMap hashMap = hashMap(
            true, new String[]{"keyOne", "keyOneValue"},
            new String[]{"keyTwo", "keyTwoValue"},
            new String[]{"keyThree", "keyThreeValue"}
        );

        // then
        assertThat(hashMap.containsAll(hashMap(
            true, new NottableString[]{not("notKeyOne"), not("notKeyOneValue")}
        )), is(true));
    }

    @Test
    public void shouldContainAllExactMatchMultipleKeyAndMultipleValuesForNottedKey() {
        // given
        NottableStringHashMap hashMap = hashMap(
            true, new String[]{"keyOne", "keyOneValue"},
            new String[]{"keyTwo", "keyTwoValue"}
        );

        // then
        assertThat(hashMap.containsAll(hashMap(
            true, new NottableString[]{string("keyOne"), string("keyOneValue")},
            new NottableString[]{not("notKeyTwo"), string("keyTwoValue")}
        )), is(true));
        assertThat(hashMap.containsAll(hashMap(
            true, new NottableString[]{not("notKeyOne"), string("keyOneValue")},
            new NottableString[]{string("keyTwo"), string("keyTwoValue")}
        )), is(true));
        assertThat(hashMap.containsAll(hashMap(
            true, new NottableString[]{not("notKeyOne"), string("keyOneValue")},
            new NottableString[]{not("notKeyTwo"), string("keyTwoValue")}
        )), is(true));
    }

    @Test
    public void shouldContainAllExactMatchMultipleKeyAndMultipleValuesForNottedValue() {
        // given
        NottableStringHashMap hashMap = hashMap(
            true, new String[]{"keyOne", "keyOneValue"},
            new String[]{"keyTwo", "keyTwoValue"}
        );

        // then
        assertThat(hashMap.containsAll(hashMap(
            true, new NottableString[]{string("keyOne"), not("notKeyOneValue")},
            new NottableString[]{string("keyTwo"), string("keyTwoValue")}
        )), is(true));
        assertThat(hashMap.containsAll(hashMap(
            true, new NottableString[]{string("keyOne"), string("keyOneValue")},
            new NottableString[]{string("keyTwo"), not("notKeyTwoValue")}
        )), is(true));
        assertThat(hashMap.containsAll(hashMap(
            true, new NottableString[]{string("keyOne"), string("keyOneValue")},
            new NottableString[]{string("keyTwo"), string("keyTwoValue")}
        )), is(true));
        assertThat(hashMap.containsAll(hashMap(
            true, new NottableString[]{string("keyOne"), string("keyOneValue")},
            new NottableString[]{string("keyTwo"), not("notKeyTwoValue")}
        )), is(true));
        assertThat(hashMap.containsAll(hashMap(
            true, new NottableString[]{string("keyOne"), not("notKeyOneValue")},
            new NottableString[]{string("keyTwo"), not("notKeyTwoValue")}
        )), is(true));
        assertThat(hashMap.containsAll(hashMap(
            true, new NottableString[]{string("keyOne"), not("notKeyOneValue")},
            new NottableString[]{string("keyTwo"), string("keyTwoValue")}
        )), is(true));
        assertThat(hashMap.containsAll(hashMap(
            true, new NottableString[]{string("keyOne"), not("notKeyOneValue")},
            new NottableString[]{string("keyTwo"), not("notKeyTwoValue")}
        )), is(true));
    }

    @Test
    public void shouldContainAllExactMatchMultipleKeyAndMultipleValuesForNottedKeyAndValue() {
        // given
        NottableStringHashMap hashMap = hashMap(
            true, new String[]{"keyOne", "keyOneValue"},
            new String[]{"keyTwo", "keyTwoValue"}
        );

        // then
        assertThat(hashMap.containsAll(hashMap(
            true, new NottableString[]{not("notKeyOne"), not("notKeyOneValue")},
            new NottableString[]{string("keyTwo"), string("keyTwoValue")}
        )), is(true));
        assertThat(hashMap.containsAll(hashMap(
            true, new NottableString[]{string("keyOne"), string("keyOneValue")},
            new NottableString[]{not("notKeyTwo"), not("notKeyTwoValue")}
        )), is(true));
        assertThat(hashMap.containsAll(hashMap(
            true, new NottableString[]{string("keyOne"), string("keyOneValue")},
            new NottableString[]{not("notKeyTwo"), string("keyTwoValue")}
        )), is(true));
        assertThat(hashMap.containsAll(hashMap(
            true, new NottableString[]{string("keyOne"), string("keyOneValue")},
            new NottableString[]{not("notKeyTwo"), not("notKeyTwoValue")}
        )), is(true));
        assertThat(hashMap.containsAll(hashMap(
            true, new NottableString[]{not("notKeyOne"), not("notKeyOneValue")},
            new NottableString[]{not("notKeyTwo"), not("notKeyTwoValue")}
        )), is(true));
        assertThat(hashMap.containsAll(hashMap(
            true, new NottableString[]{not("notKeyOne"), not("notKeyOneValue")},
            new NottableString[]{string("keyTwo"), string("keyTwoValue")}
        )), is(true));
        assertThat(hashMap.containsAll(hashMap(
            true, new NottableString[]{not("notKeyOne"), not("notKeyOneValue")},
            new NottableString[]{not("notKeyTwo"), not("notKeyTwoValue")}
        )), is(true));
    }

    @Test
    public void shouldContainAllSubSetMultipleKeyAndMultipleValuesForNottedKey() {
        // given
        NottableStringHashMap hashMap = hashMap(
            true, new String[]{"keyOne", "keyOneValue"},
            new String[]{"keyTwo", "keyTwoValue"},
            new String[]{"keyThree", "keyThreeValue"}
        );

        // then
        assertThat(hashMap.containsAll(hashMap(
            true, new NottableString[]{string("keyOne"), string("keyOneValue")},
            new NottableString[]{not("notKeyTwo"), string("keyTwoValue")}
        )), is(true));
        assertThat(hashMap.containsAll(hashMap(
            true, new NottableString[]{not("notKeyOne"), string("keyOneValue")},
            new NottableString[]{string("keyTwo"), string("keyTwoValue")}
        )), is(true));
        assertThat(hashMap.containsAll(hashMap(
            true, new NottableString[]{not("notKeyOne"), string("keyOneValue")},
            new NottableString[]{not("notKeyTwo"), string("keyTwoValue")}
        )), is(true));
    }

    @Test
    public void shouldContainAllSubSetMultipleKeyAndMultipleValuesForNottedValue() {
        // given
        NottableStringHashMap hashMap = hashMap(
            true, new String[]{"keyOne", "keyOneValue"},
            new String[]{"keyTwo", "keyTwoValue"},
            new String[]{"keyThree", "keyThreeValue"}
        );

        // then
        assertThat(hashMap.containsAll(hashMap(
            true, new NottableString[]{string("keyOne"), not("notKeyOneValue")},
            new NottableString[]{string("keyTwo"), string("keyTwoValue")}
        )), is(true));
        assertThat(hashMap.containsAll(hashMap(
            true, new NottableString[]{string("keyOne"), string("keyOneValue")},
            new NottableString[]{string("keyTwo"), not("notKeyTwoValue")}
        )), is(true));
        assertThat(hashMap.containsAll(hashMap(
            true, new NottableString[]{string("keyOne"), string("keyOneValue")},
            new NottableString[]{string("keyTwo"), string("keyTwoValue")}
        )), is(true));
        assertThat(hashMap.containsAll(hashMap(
            true, new NottableString[]{string("keyOne"), string("keyOneValue")},
            new NottableString[]{string("keyTwo"), not("notKeyTwoValue")}
        )), is(true));
        assertThat(hashMap.containsAll(hashMap(
            true, new NottableString[]{string("keyOne"), not("notKeyOneValue")},
            new NottableString[]{string("keyTwo"), not("notKeyTwoValue")}
        )), is(true));
        assertThat(hashMap.containsAll(hashMap(
            true, new NottableString[]{string("keyOne"), not("notKeyOneValue")},
            new NottableString[]{string("keyTwo"), string("keyTwoValue")}
        )), is(true));
        assertThat(hashMap.containsAll(hashMap(
            true, new NottableString[]{string("keyOne"), not("notKeyOneValue")},
            new NottableString[]{string("keyTwo"), not("notKeyTwoValue")}
        )), is(true));
    }

    @Test
    public void shouldContainAllSubSetMultipleKeyAndMultipleValuesForNottedKeyAndValue() {
        // given
        NottableStringHashMap hashMap = hashMap(
            true, new String[]{"keyOne", "keyOneValue"},
            new String[]{"keyTwo", "keyTwoValue"},
            new String[]{"keyThree", "keyThreeValue"}
        );

        // then
        assertThat(hashMap.containsAll(hashMap(
            true, new NottableString[]{not("notKeyOne"), not("notKeyOneValue")},
            new NottableString[]{string("keyTwo"), string("keyTwoValue")}
        )), is(true));
        assertThat(hashMap.containsAll(hashMap(
            true, new NottableString[]{string("keyOne"), string("keyOneValue")},
            new NottableString[]{not("notKeyTwo"), not("notKeyTwoValue")}
        )), is(true));
        assertThat(hashMap.containsAll(hashMap(
            true, new NottableString[]{string("keyOne"), string("keyOneValue")},
            new NottableString[]{not("notKeyTwo"), string("keyTwoValue")}
        )), is(true));
        assertThat(hashMap.containsAll(hashMap(
            true, new NottableString[]{string("keyOne"), string("keyOneValue")},
            new NottableString[]{not("notKeyTwo"), not("notKeyTwoValue")}
        )), is(true));
        assertThat(hashMap.containsAll(hashMap(
            true, new NottableString[]{not("notKeyOne"), not("notKeyOneValue")},
            new NottableString[]{not("notKeyTwo"), not("notKeyTwoValue")}
        )), is(true));
        assertThat(hashMap.containsAll(hashMap(
            true, new NottableString[]{not("notKeyOne"), not("notKeyOneValue")},
            new NottableString[]{string("keyTwo"), string("keyTwoValue")}
        )), is(true));
        assertThat(hashMap.containsAll(hashMap(
            true, new NottableString[]{not("notKeyOne"), not("notKeyOneValue")},
            new NottableString[]{not("notKeyTwo"), not("notKeyTwoValue")}
        )), is(true));

        // and then
        assertThat(hashMap.containsAll(hashMap(
            true, new NottableString[]{not("notKeyOne"), not("keyOneValue")},
            new NottableString[]{string("keyTwo"), string("keyTwoValue")}
        )), is(true));
        assertThat(hashMap.containsAll(hashMap(
            true, new NottableString[]{not("keyOne"), not("notKeyOneValue")},
            new NottableString[]{string("keyTwo"), string("keyTwoValue")}
        )), is(true));
        assertThat(hashMap.containsAll(hashMap(
            true, new NottableString[]{string("keyOne"), string("keyOneValue")},
            new NottableString[]{not("notKeyTwo"), not("keyTwoValue")}
        )), is(true));
        assertThat(hashMap.containsAll(hashMap(
            true, new NottableString[]{string("keyOne"), string("keyOneValue")},
            new NottableString[]{not("keyTwo"), not("notKeyTwoValue")}
        )), is(true));
        assertThat(hashMap.containsAll(hashMap(
            true, new NottableString[]{not("notKeyOne"), not("keyOneValue")},
            new NottableString[]{not("notKeyTwo"), not("keyTwoValue")}
        )), is(true));
        assertThat(hashMap.containsAll(hashMap(
            true, new NottableString[]{not("keyOne"), not("notKeyOneValue")},
            new NottableString[]{not("keyTwo"), not("notKeyTwoValue")}
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
            true, new NottableString[]{not("keyOne"), string("keyOneValue")}
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
            true, new NottableString[]{string("keyOne"), not("keyOneValue")}
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
            true, new NottableString[]{not("keyOne"), string("keyOneValue")}
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
            true, new NottableString[]{string("keyOne"), not("keyOneValue")}
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
            true, new NottableString[]{not("keyOne"), string("keyOneValue")},
            new NottableString[]{not("keyTwo"), string("keyTwoValue")}
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
            true, new NottableString[]{string("keyOne"), not("keyOneValue")},
            new NottableString[]{string("keyTwo"), string("keyTwoValue")}
        )), is(false));
        assertThat(hashMap.containsAll(hashMap(
            true, new NottableString[]{string("keyTwo"), not("keyTwo.*")}
        )), is(false));
    }
}
