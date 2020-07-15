package org.mockserver.collections.hashmap.optionalmatcher;

import org.junit.Test;
import org.mockserver.collections.NottableStringHashMap;
import org.mockserver.model.NottableString;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockserver.collections.NottableStringHashMap.hashMap;
import static org.mockserver.model.NottableOptionalString.optionalString;
import static org.mockserver.model.NottableString.not;
import static org.mockserver.model.NottableString.string;

/**
 * @author jamesdbloom
 */
public class CaseInsensitiveRegexHashMapTestOptionalContainsAll {

    @Test
    public void shouldContainAllSubSetMultipleKeyForOptionalKeys() {
        // given
        NottableStringHashMap matcher = hashMap(true,
            new NottableString[]{string("keyOne"), string("keyOneValue")},
            new NottableString[]{string("keyTwo"), string("keyTwoValue")},
            new NottableString[]{optionalString("keyThree"), string("keyThreeValue")}
        );

        // then
        assertThat(hashMap(true,
            new NottableString[]{string("keyOne"), string("keyOneValue")},
            new NottableString[]{string("keyTwo"), string("keyTwoValue")}
        ).containsAll(matcher), is(true));
        assertThat(hashMap(true,
            new NottableString[]{string("keyOne"), string("keyOneValue")},
            new NottableString[]{string("keyTwo"), string("keyTwoValue")},
            new NottableString[]{string("keyThree"), string("keyThreeValue")}
        ).containsAll(matcher), is(true));
        assertThat(hashMap(true,
            new NottableString[]{string("keyOne"), string("keyOneValue")},
            new NottableString[]{string("keyTwo"), string("keyTwoValue")},
            new NottableString[]{string("keyThree"), string("keyThreeOtherValue")}
        ).containsAll(matcher), is(false));
    }

    @Test
    public void shouldContainAllSubSetMultipleKeyForOptionalKeysWithNottedValue() {
        // given
        NottableStringHashMap matcher = hashMap(true,
            new NottableString[]{string("keyOne"), string("keyOneValue")},
            new NottableString[]{string("keyTwo"), string("keyTwoValue")},
            new NottableString[]{optionalString("keyThree"), not("keyThreeValue")}
        );

        // then
        assertThat(hashMap(true,
            new NottableString[]{string("keyOne"), string("keyOneValue")},
            new NottableString[]{string("keyTwo"), string("keyTwoValue")}
        ).containsAll(matcher), is(true));
        assertThat(hashMap(true,
            new NottableString[]{string("keyOne"), string("keyOneValue")},
            new NottableString[]{string("keyTwo"), string("keyTwoValue")},
            new NottableString[]{string("keyThree"), string("keyThreeValue")}
        ).containsAll(matcher), is(false));
        assertThat(hashMap(true,
            new NottableString[]{string("keyOne"), string("keyOneValue")},
            new NottableString[]{string("keyTwo"), string("keyTwoValue")},
            new NottableString[]{string("keyOtherThree"), string("keyThreeValue")}
        ).containsAll(matcher), is(true));
        assertThat(hashMap(true,
            new NottableString[]{string("keyOne"), string("keyOneValue")},
            new NottableString[]{string("keyTwo"), string("keyTwoValue")},
            new NottableString[]{string("keyThree"), string("keyThreeOtherValue")}
        ).containsAll(matcher), is(true));
    }

    @Test
    public void shouldContainAllSubSetMultipleKeyForAllOptionalKeys() {
        // given
        NottableStringHashMap matcher = hashMap(true,
            new NottableString[]{string("?keyOne"), string("keyOneValue")},
            new NottableString[]{string("?keyTwo"), string("keyTwoValue")},
            new NottableString[]{string("?keyThree"), string("keyThreeValue")}
        );

        // then - all three
        assertThat(hashMap(true,
            new NottableString[]{string("keyOne"), string("keyOneValue")},
            new NottableString[]{string("keyTwo"), string("keyTwoValue")},
            new NottableString[]{string("keyThree"), string("keyThreeValue")}
        ).containsAll(matcher), is(true));
        // then - any two
        assertThat(hashMap(true,
            new NottableString[]{string("keyOne"), string("keyOneValue")},
            new NottableString[]{string("keyTwo"), string("keyTwoValue")}
        ).containsAll(matcher), is(true));
        assertThat(hashMap(true,
            new NottableString[]{string("keyOne"), string("keyOneValue")},
            new NottableString[]{string("keyThree"), string("keyThreeValue")}
        ).containsAll(matcher), is(true));
        assertThat(hashMap(true,
            new NottableString[]{string("keyTwo"), string("keyTwoValue")},
            new NottableString[]{string("keyThree"), string("keyThreeValue")}
        ).containsAll(matcher), is(true));
        assertThat(hashMap(true,
            new NottableString[]{string("keyTwo"), string("keyTwoValue")},
            new NottableString[]{string("keyOne"), string("keyOneValue")}
        ).containsAll(matcher), is(true));
        // then - any single value
        assertThat(hashMap(true,
            new NottableString[]{string("keyOne"), string("keyOneValue")}
        ).containsAll(matcher), is(true));
        assertThat(hashMap(true,
            new NottableString[]{string("keyTwo"), string("keyTwoValue")}
        ).containsAll(matcher), is(true));
        assertThat(hashMap(true,
            new NottableString[]{string("keyThree"), string("keyThreeValue")}
        ).containsAll(matcher), is(true));
        // then - empty
        assertThat(hashMap(true,
            new NottableString[0][]
        ).containsAll(matcher), is(true));
        // then - other value
        assertThat(hashMap(true,
            new NottableString[]{string("otherKey"), string("otherValue")}
        ).containsAll(matcher), is(true));
    }

    @Test
    public void shouldContainAllSubSetMultipleKeyForAllOptionalEitherOr() {
        // given
        NottableStringHashMap matcher = hashMap(true,
            new NottableString[]{string("?keyOne"), string("keyOneValue")},
            new NottableString[]{string("?keyTwo"), string("keyTwoValue")},
            new NottableString[]{string("keyOne|keyTwo"), string("key.*")}
        );

        // then - any two
        assertThat(hashMap(true,
            new NottableString[]{string("keyOne"), string("keyOneValue")},
            new NottableString[]{string("keyTwo"), string("keyTwoValue")}
        ).containsAll(matcher), is(true));
        assertThat(hashMap(true,
            new NottableString[]{string("keyTwo"), string("keyTwoValue")},
            new NottableString[]{string("keyOne"), string("keyOneValue")}
        ).containsAll(matcher), is(true));
        // then - any single value
        assertThat(hashMap(true,
            new NottableString[]{string("keyOne"), string("keyOneValue")}
        ).containsAll(matcher), is(true));
        assertThat(hashMap(true,
            new NottableString[]{string("keyTwo"), string("keyTwoValue")}
        ).containsAll(matcher), is(true));
        // then - not
        assertThat(hashMap(true,
            new NottableString[]{string("keyOne"), string("otherValue")}
        ).containsAll(matcher), is(false));
        assertThat(hashMap(true,
            new NottableString[]{string("keyTwo"), string("otherValue")}
        ).containsAll(matcher), is(false));
        assertThat(hashMap(true,
            new NottableString[]{string("otherKey"), string("otherValue")}
        ).containsAll(matcher), is(false));
        assertThat(hashMap(true,
            new NottableString[0][]
        ).containsAll(matcher), is(false));
    }
}
