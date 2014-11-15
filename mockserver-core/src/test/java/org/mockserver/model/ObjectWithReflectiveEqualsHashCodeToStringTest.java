package org.mockserver.model;

import org.junit.Test;

import static org.hamcrest.core.IsInstanceOf.instanceOf;
import static org.junit.Assert.*;

/**
 * This test is mainly for coverage but also to check underlying API is called correctly
 *
 * @author jamesdbloom
 */
public class ObjectWithReflectiveEqualsHashCodeToStringTest {

    @Test
    public void hashCodeIdentical() {
        assertEquals(new Header("name", "value").hashCode(), new Header("name", "value").hashCode());
    }

    @Test
    public void hashCodeDifferent() {
        assertNotEquals(new Header("name", "value").hashCode(), new Header("foo", "bar").hashCode());
    }

    @Test
    public void equalsIdentical() {
        assertTrue(new Header("name", "value").equals(new Header("name", "value")));
    }

    @Test
    public void notEqualsDifferent() {
        assertFalse(new Header("name", "value").equals(new Header("foo", "bar")));
    }

    @Test
    public void toStringReturnStrings() {
        assertThat(new Header("name", "value").toString(), instanceOf(String.class));
    }
}
