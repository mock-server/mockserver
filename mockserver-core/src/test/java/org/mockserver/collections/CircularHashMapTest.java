package org.mockserver.collections;

import org.junit.Test;

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
}
