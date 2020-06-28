package org.mockserver.collections.multimap;

import org.junit.Test;
import org.mockserver.collections.CaseInsensitiveRegexMultiMap;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.KeyMatchStyle;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.fail;
import static org.mockserver.collections.CaseInsensitiveRegexMultiMap.multiMap;

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
                new String[]{"?keyOne", "keyOne_valueOne", "keyOne_valueTwo"}
            ).allKeysOptional(), is(true));

            fail("expected exception");
        } catch (IllegalArgumentException iae) {
            assertThat(iae.getMessage(), is("multiple values for optional key are not allowed, value \"keyOne_valueOne\" already exists for \"?keyOne\""));
        }
    }

    @Test
    @SuppressWarnings("MismatchedQueryAndUpdateOfCollection")
    public void shouldThrowExceptionForPut() {
        // given
        CaseInsensitiveRegexMultiMap multiMap = new CaseInsensitiveRegexMultiMap(new MockServerLogger(), true);
        multiMap.put("?keyOne", "keyOne_valueOne");

        try {
            multiMap.put("?keyOne", "keyOne_valueTwo");

            fail("expected exception");
        } catch (IllegalArgumentException iae) {
            assertThat(iae.getMessage(), is("multiple values for optional key are not allowed, value \"keyOne_valueOne\" already exists for \"?keyOne\""));
        }
    }

}
