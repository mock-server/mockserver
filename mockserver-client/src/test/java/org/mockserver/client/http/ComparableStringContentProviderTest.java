package org.mockserver.client.http;

import org.junit.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

/**
 * @author jamesdbloom
 */
public class ComparableStringContentProviderTest {

    @Test
    public void shouldEqualOtherProviderWithSameContent() {
        assertEquals(
                new ComparableStringContentProvider("identical", StandardCharsets.UTF_8),
                new ComparableStringContentProvider("identical", StandardCharsets.UTF_8)
        );
        assertNotEquals(
                new ComparableStringContentProvider("identical", StandardCharsets.UTF_8),
                new ComparableStringContentProvider("not_identical", StandardCharsets.UTF_8)
        );
    }

    @Test
    public void shouldHaveIdenticalHashCodeToOtherProviderWithSameContent() {
        assertEquals(
                new ComparableStringContentProvider("identical", StandardCharsets.UTF_8).hashCode(),
                new ComparableStringContentProvider("identical", StandardCharsets.UTF_8).hashCode()
        );
        assertNotEquals(
                new ComparableStringContentProvider("identical", StandardCharsets.UTF_8).hashCode(),
                new ComparableStringContentProvider("not_identical", StandardCharsets.UTF_8).hashCode()
        );
    }
}
