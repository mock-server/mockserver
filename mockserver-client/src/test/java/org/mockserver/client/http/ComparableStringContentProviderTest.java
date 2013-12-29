package org.mockserver.client.http;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

/**
 * @author jamesdbloom
 */
public class ComparableStringContentProviderTest {

    @Test
    public void shouldEqualOtherProviderWithSameContent() {
        assertEquals(new ComparableStringContentProvider("identical", "UTF-8"), new ComparableStringContentProvider("identical", "UTF-8"));
        assertNotEquals(new ComparableStringContentProvider("identical", "UTF-8"), new ComparableStringContentProvider("not_identical", "UTF-8"));
    }

    @Test
    public void shouldHaveIdenticalHashCodeToOtherProviderWithSameContent() {
        assertEquals(new ComparableStringContentProvider("identical", "UTF-8").hashCode(), new ComparableStringContentProvider("identical", "UTF-8").hashCode());
        assertNotEquals(new ComparableStringContentProvider("identical", "UTF-8").hashCode(), new ComparableStringContentProvider("not_identical", "UTF-8").hashCode());
    }
}
