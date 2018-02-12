package org.mockserver.collections;

import org.junit.Test;

import java.util.Arrays;

import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.Matchers.contains;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

/**
 * @author jamesdbloom
 */
public class BoundedConcurrentLinkedQueueTest {

    @Test
    public void shouldNotAllowAddingMoreThenMaximumNumberOfEntriesWhenUsingAdd() {
        // given
        BoundedConcurrentLinkedQueue<String> concurrentLinkedQueue = new BoundedConcurrentLinkedQueue<String>(3);

        // when
        concurrentLinkedQueue.add("1");
        concurrentLinkedQueue.add("2");
        concurrentLinkedQueue.add("3");
        concurrentLinkedQueue.add("4");

        // then
        assertEquals(3, concurrentLinkedQueue.size());
        assertThat(concurrentLinkedQueue, not(contains("1")));
        assertThat(concurrentLinkedQueue, contains("2", "3", "4"));
    }

    @Test
    public void shouldNotAllowAddingMoreThenMaximumNumberOfEntriesWhenUsingAddAll() {
        // given
        BoundedConcurrentLinkedQueue<String> concurrentLinkedQueue = new BoundedConcurrentLinkedQueue<String>(3);

        // when
        concurrentLinkedQueue.addAll(Arrays.asList("1", "2", "3", "4"));

        // then
        assertEquals(3, concurrentLinkedQueue.size());
        assertThat(concurrentLinkedQueue, not(contains("1")));
        assertThat(concurrentLinkedQueue, contains("2", "3", "4"));
    }

}