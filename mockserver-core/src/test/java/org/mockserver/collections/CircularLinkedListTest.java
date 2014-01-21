package org.mockserver.collections;

import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.*;

/**
 * @author jamesdbloom
 */
public class CircularLinkedListTest {

    @Test
    public void shouldNotAllowAddingMoreThenMaximumNumberOfEntriesWhenUsingAdd() {
        // given
        CircularLinkedList<String> circularLinkedList = new CircularLinkedList<String>(3);

        // when
        circularLinkedList.add("1");
        circularLinkedList.add("2");
        circularLinkedList.add("3");
        circularLinkedList.add("4");

        // then
        assertEquals(3, circularLinkedList.size());
        assertFalse(circularLinkedList.contains("1"));
        assertTrue(circularLinkedList.contains("2"));
        assertTrue(circularLinkedList.contains("3"));
        assertTrue(circularLinkedList.contains("4"));
        assertEquals(Arrays.asList("2", "3", "4"), circularLinkedList);
    }

    @Test
    public void shouldNotAllowAddingMoreThenMaximumNumberOfEntriesWhenUsingAddFirst() {
        // given
        CircularLinkedList<String> circularLinkedList = new CircularLinkedList<String>(3);

        // when
        circularLinkedList.addFirst("1");
        circularLinkedList.addFirst("2");
        circularLinkedList.addFirst("3");
        circularLinkedList.addFirst("4");

        // then
        assertEquals(3, circularLinkedList.size());
        assertFalse(circularLinkedList.contains("3"));
        assertTrue(circularLinkedList.contains("4"));
        assertTrue(circularLinkedList.contains("2"));
        assertTrue(circularLinkedList.contains("1"));
        assertEquals(Arrays.asList("4", "2", "1"), circularLinkedList);
    }

    @Test
    public void shouldNotAllowAddingMoreThenMaximumNumberOfEntriesWhenUsingAddLast() {
        // given
        CircularLinkedList<String> circularLinkedList = new CircularLinkedList<String>(3);

        // when
        circularLinkedList.addLast("1");
        circularLinkedList.addLast("2");
        circularLinkedList.addLast("3");
        circularLinkedList.addLast("4");

        // then
        assertEquals(3, circularLinkedList.size());
        assertFalse(circularLinkedList.contains("1"));
        assertTrue(circularLinkedList.contains("2"));
        assertTrue(circularLinkedList.contains("3"));
        assertTrue(circularLinkedList.contains("4"));
        assertEquals(Arrays.asList("2", "3", "4"), circularLinkedList);
    }

    @Test
    public void shouldNotAllowAddingMoreThenMaximumNumberOfEntriesWhenUsingAddAll() {
        // given
        CircularLinkedList<String> circularLinkedList = new CircularLinkedList<String>(3);

        // when
        circularLinkedList.addAll(Arrays.asList("1", "2", "3", "4"));

        // then
        assertEquals(3, circularLinkedList.size());
        assertFalse(circularLinkedList.contains("1"));
        assertTrue(circularLinkedList.contains("2"));
        assertTrue(circularLinkedList.contains("3"));
        assertTrue(circularLinkedList.contains("4"));
        assertEquals(Arrays.asList("2", "3", "4"), circularLinkedList);
    }

    @Test
    public void shouldNotAllowAddingMoreThenMaximumNumberOfEntriesWhenUsingAddAllWithIndex() {
        // given
        CircularLinkedList<String> circularLinkedList = new CircularLinkedList<String>(3);

        // when
        circularLinkedList.add("1");
        circularLinkedList.add("2");
        circularLinkedList.add("3");
        circularLinkedList.addAll(2, Arrays.asList("a", "b"));

        // then
        assertEquals(3, circularLinkedList.size());
        assertFalse(circularLinkedList.contains("1"));
        assertFalse(circularLinkedList.contains("2"));
        assertTrue(circularLinkedList.contains("a"));
        assertTrue(circularLinkedList.contains("b"));
        assertTrue(circularLinkedList.contains("3"));
        assertEquals(Arrays.asList("a", "b", "3"), circularLinkedList);
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldNotAllowInsertingListInIndexGreaterThenMaximumNumberOfEntries() {
        // given
        CircularLinkedList<String> circularLinkedList = new CircularLinkedList<String>(3);

        // when
        circularLinkedList.add("1");
        circularLinkedList.add("2");
        circularLinkedList.add("3");
        circularLinkedList.addAll(3, Arrays.asList("a", "b"));
    }

    @Test
    public void shouldNotAllowAddingMoreThenMaximumNumberOfEntriesWhenUsingAddWithIndex() {
        // given
        CircularLinkedList<String> circularLinkedList = new CircularLinkedList<String>(3);

        // when
        circularLinkedList.add("1");
        circularLinkedList.add("2");
        circularLinkedList.add("3");
        circularLinkedList.add(1, "4");

        // then
        assertEquals(3, circularLinkedList.size());
        assertFalse(circularLinkedList.contains("1"));
        assertTrue(circularLinkedList.contains("4"));
        assertTrue(circularLinkedList.contains("2"));
        assertTrue(circularLinkedList.contains("3"));
        assertEquals(Arrays.asList("4", "2", "3"), circularLinkedList);
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldNotAllowInsertingItemInIndexGreaterThenMaximumNumberOfEntries() {
        // given
        CircularLinkedList<String> circularLinkedList = new CircularLinkedList<String>(3);

        // when
        circularLinkedList.add("1");
        circularLinkedList.add("2");
        circularLinkedList.add("3");
        circularLinkedList.add(3, "4");
    }
}
