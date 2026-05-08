package org.mockserver.collections;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static junit.framework.TestCase.*;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;


/**
 * @author jamesdbloom
 */
public class CircularHashMapTest {

    @Test
    public void shouldNotContainMoreThenMaximumNumberOfEntries() {
        // given
        CircularHashMap<String, String> circularHashMap = new CircularHashMap<>(3);

        // when
        circularHashMap.put("1", "1");
        circularHashMap.put("2", "2");
        circularHashMap.put("3", "3");
        circularHashMap.put("4", "4");

        // then
        assertEquals(3, circularHashMap.size());
        assertFalse(circularHashMap.containsKey("1"));
        assertTrue(circularHashMap.containsKey("2"));
        assertTrue(circularHashMap.containsKey("3"));
        assertTrue(circularHashMap.containsKey("4"));
    }

    @Test
    public void shouldFindKeyByObject() {
        // given
        CircularHashMap<String, String> circularHashMap = new CircularHashMap<>(5);

        // when
        circularHashMap.put("0", "a");
        circularHashMap.put("1", "b");
        circularHashMap.put("2", "c");
        circularHashMap.put("3", "d");
        circularHashMap.put("4", "d");
        circularHashMap.put("5", "e");

        // then
        assertThat(circularHashMap.findKey("b"), is("1"));
        assertThat(circularHashMap.findKey("c"), is("2"));
        assertThat(circularHashMap.findKey("x"), nullValue());
        assertThat(circularHashMap.findKey("a"), nullValue());
        assertThat(circularHashMap.findKey("d"), is("3"));
    }

    @Test
    public void shouldCallEvictionListenerWhenEntryEvicted() {
        List<String> evicted = new ArrayList<>();
        CircularHashMap<String, String> circularHashMap = new CircularHashMap<>(2, evicted::add);

        circularHashMap.put("1", "a");
        circularHashMap.put("2", "b");
        assertTrue(evicted.isEmpty());

        circularHashMap.put("3", "c");
        assertEquals(1, evicted.size());
        assertEquals("a", evicted.get(0));

        circularHashMap.put("4", "d");
        assertEquals(2, evicted.size());
        assertEquals("b", evicted.get(1));
    }
}
