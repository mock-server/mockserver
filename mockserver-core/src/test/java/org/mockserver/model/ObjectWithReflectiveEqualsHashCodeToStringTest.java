package org.mockserver.model;

import org.junit.Test;

import static junit.framework.TestCase.assertEquals;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsInstanceOf.instanceOf;
import static org.junit.Assert.assertNotEquals;

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
        assertEquals(new Header("name", "value"), new Header("name", "value"));
    }

    @Test
    public void notEqualsDifferent() {
        assertNotEquals(new Header("name", "value"), new Header("foo", "bar"));
    }

    @Test
    public void toStringReturnStrings() {
        assertThat(new Header("name", "value").toString(), instanceOf(String.class));
    }
}
