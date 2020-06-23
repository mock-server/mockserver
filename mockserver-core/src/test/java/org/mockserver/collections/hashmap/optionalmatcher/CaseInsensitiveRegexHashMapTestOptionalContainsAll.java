package org.mockserver.collections.hashmap.optionalmatcher;

import org.junit.Test;
import org.mockserver.collections.CaseInsensitiveRegexHashMap;
import org.mockserver.model.NottableString;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockserver.collections.CaseInsensitiveRegexHashMap.hashMap;
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
        CaseInsensitiveRegexHashMap hashMap = hashMap(true,
            new NottableString[]{string("keyOne"), string("keyOneValue")},
            new NottableString[]{string("keyTwo"), string("keyTwoValue")},
            new NottableString[]{optionalString("keyThree"), string("keyThreeValue")}
        );

        // then
        assertThat(hashMap.containsAll(hashMap(true,
            new NottableString[]{string("keyOne"), string("keyOneValue")},
            new NottableString[]{string("keyTwo"), string("keyTwoValue")}
        )), is(true));
        assertThat(hashMap.containsAll(hashMap(true,
            new NottableString[]{string("keyOne"), string("keyOneValue")},
            new NottableString[]{string("keyTwo"), string("keyTwoValue")},
            new NottableString[]{string("keyThree"), string("keyThreeValue")}
        )), is(true));
        assertThat(hashMap.containsAll(hashMap(true,
            new NottableString[]{string("keyOne"), string("keyOneValue")},
            new NottableString[]{string("keyTwo"), string("keyTwoValue")},
            new NottableString[]{string("keyThree"), string("keyThreeOtherValue")}
        )), is(false));
    }

    @Test
    public void shouldContainAllSubSetMultipleKeyForOptionalKeysWithNottedValue() {
        // given
        CaseInsensitiveRegexHashMap hashMap = hashMap(true,
            new NottableString[]{string("keyOne"), string("keyOneValue")},
            new NottableString[]{string("keyTwo"), string("keyTwoValue")},
            new NottableString[]{optionalString("keyThree"), not("keyThreeValue")}
        );

        // then
        assertThat(hashMap.containsAll(hashMap(true,
            new NottableString[]{string("keyOne"), string("keyOneValue")},
            new NottableString[]{string("keyTwo"), string("keyTwoValue")}
        )), is(true));
        assertThat(hashMap.containsAll(hashMap(true,
            new NottableString[]{string("keyOne"), string("keyOneValue")},
            new NottableString[]{string("keyTwo"), string("keyTwoValue")},
            new NottableString[]{string("keyThree"), string("keyThreeValue")}
        )), is(false));
        assertThat(hashMap.containsAll(hashMap(true,
            new NottableString[]{string("keyOne"), string("keyOneValue")},
            new NottableString[]{string("keyTwo"), string("keyTwoValue")},
            new NottableString[]{string("keyOtherThree"), string("keyThreeValue")}
        )), is(true));
        assertThat(hashMap.containsAll(hashMap(true,
            new NottableString[]{string("keyOne"), string("keyOneValue")},
            new NottableString[]{string("keyTwo"), string("keyTwoValue")},
            new NottableString[]{string("keyThree"), string("keyThreeOtherValue")}
        )), is(true));
    }
}
