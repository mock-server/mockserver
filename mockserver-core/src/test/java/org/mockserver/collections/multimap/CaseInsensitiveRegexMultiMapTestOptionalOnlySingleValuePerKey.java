package org.mockserver.collections.multimap;

import org.junit.Test;
import org.mockserver.collections.NottableStringMultiMap;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.KeyMatchStyle;
import org.mockserver.model.NottableString;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.fail;
import static org.mockserver.collections.NottableStringMultiMap.multiMap;
import static org.mockserver.model.NottableString.string;

/**
 * @author jamesdbloom
 */
public class CaseInsensitiveRegexMultiMapTestOptionalOnlySingleValuePerKey {

    @Test
    public void shouldThrowExceptionForStaticBuilder() {
        try {
            assertThat(multiMap(
                true,
                KeyMatchStyle.SUB_SET,
                new NottableString[]{string("?keyOne"), string("keyOne_valueOne"), string("keyOne_valueTwo")}
            ).allKeysOptional(), is(true));

            fail("expected exception");
        } catch (IllegalArgumentException iae) {
            assertThat(iae.getMessage(), is("multiple values for optional key are not allowed, key \"?keyOne\" has values \"[keyOne_valueOne, keyOne_valueTwo]\""));
        }
    }

    @Test
    @SuppressWarnings("MismatchedQueryAndUpdateOfCollection")
    public void shouldThrowExceptionForPut() {
        try {
            new NottableStringMultiMap(new MockServerLogger(), true, KeyMatchStyle.defaultValue, "?keyOne", "keyOne_valueOne", "?keyOne", "keyOne_valueTwo");

            fail("expected exception");
        } catch (IllegalArgumentException iae) {
            assertThat(iae.getMessage(), is("multiple values for optional key are not allowed, key \"?keyOne\" has values \"[keyOne_valueOne, keyOne_valueTwo]\""));
        }
    }

}
