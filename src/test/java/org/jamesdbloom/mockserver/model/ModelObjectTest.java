package org.jamesdbloom.mockserver.model;

import org.junit.Test;

import static org.hamcrest.core.IsInstanceOf.instanceOf;
import static org.junit.Assert.*;

/**
 * This test is mainly for coverage but also to check underlying API is called correctly
 *
 * @author jamesdbloom
 */
public class ModelObjectTest {

    @Test
    public void hashCodeIdentical() {
        assertEquals(new Parameter("name", "value").hashCode(), new Parameter("name", "value").hashCode());
    }

    @Test
    public void hashCodeDifferent() {
        assertNotEquals(new Parameter("name", "value").hashCode(), new Parameter("foo", "bar").hashCode());
    }

    @Test
    public void equalsIdentical() {
        assertTrue(new Parameter("name", "value").equals(new Parameter("name", "value")));
    }

    @Test
    public void notEqualsDifferent() {
        assertFalse(new Parameter("name", "value").equals(new Parameter("foo", "bar")));
    }

    @Test
    public void toStringReturnStrings() {
        assertThat(new Parameter("name", "value").toString(), instanceOf(String.class));
    }
}
