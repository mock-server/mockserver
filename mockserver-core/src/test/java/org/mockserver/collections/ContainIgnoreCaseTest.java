package org.mockserver.collections;

import org.junit.Test;
import org.mockito.internal.util.collections.Sets;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.*;
import static org.mockserver.collections.ContainIgnoreCase.containsIgnoreCase;

/**
 * @author jamesdbloom
 */
public class ContainIgnoreCaseTest {

    @Test
    public void shouldFindEntryInSet() {
        // then - at start
        assertThat(containsIgnoreCase(Sets.newSet("one", "two", "three"), "one"), is(true));
        assertThat(containsIgnoreCase(Sets.newSet("one", "two", "three"), "One"), is(true));
        assertThat(containsIgnoreCase(Sets.newSet("one", "two", "three"), "ONE"), is(true));

        // then - in middle
        assertThat(containsIgnoreCase(Sets.newSet("one", "two", "three"), "two"), is(true));
        assertThat(containsIgnoreCase(Sets.newSet("one", "two", "three"), "Two"), is(true));
        assertThat(containsIgnoreCase(Sets.newSet("one", "two", "three"), "TWO"), is(true));

        // then - at end
        assertThat(containsIgnoreCase(Sets.newSet("one", "two", "three"), "three"), is(true));
        assertThat(containsIgnoreCase(Sets.newSet("one", "two", "three"), "Three"), is(true));
        assertThat(containsIgnoreCase(Sets.newSet("one", "two", "three"), "THREE"), is(true));
    }

    @Test
    public void shouldNotFindEntryInSet() {
        // then
        assertThat(containsIgnoreCase(Sets.newSet("one", "two", "three"), "four"), is(false));
        assertThat(containsIgnoreCase(Sets.newSet("one", "two", "three"), "Four"), is(false));
        assertThat(containsIgnoreCase(Sets.newSet("one", "two", "three"), "FOUR"), is(false));
    }

}
