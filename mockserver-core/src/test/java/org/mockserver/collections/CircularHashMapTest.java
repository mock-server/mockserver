package org.mockserver.collections;

import org.junit.Test;

import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.*;


/**
 * @author jamesdbloom
 */
public class CircularHashMapTest {

    @Test
    public void shouldNotContainMoreThenMaximumNumberOfEntries() {
        // given
        CircularHashMap<String, String> circularHashMap = new CircularHashMap<String, String>(3);

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
        CircularHashMap<String, String> circularHashMap = new CircularHashMap<String, String>(5);

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
}
