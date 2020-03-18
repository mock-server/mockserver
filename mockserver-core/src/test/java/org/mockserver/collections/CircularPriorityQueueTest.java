package org.mockserver.collections;

import org.junit.Test;
import org.mockserver.mock.Expectation;

import java.util.Arrays;
import java.util.Optional;

import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.mockserver.mock.Expectation.EXPECTATION_PRIORITY_COMPARATOR;
import static org.mockserver.mock.Expectation.when;
import static org.mockserver.model.HttpRequest.request;

public class CircularPriorityQueueTest {

    @Test
    public void shouldNotAllowAddingMoreThenMaximumNumberOfEntriesWhenUsingAdd() {
        // given
        CircularPriorityQueue<String, String> concurrentLinkedQueue = new CircularPriorityQueue<>(3, String.class, String::compareToIgnoreCase, string -> string);

        // when
        concurrentLinkedQueue.add("1");
        concurrentLinkedQueue.add("2");
        concurrentLinkedQueue.add("3");
        concurrentLinkedQueue.add("4");

        // then
        assertEquals(3, concurrentLinkedQueue.size());
        assertThat(concurrentLinkedQueue, not(contains("1")));
        assertThat(concurrentLinkedQueue, containsInAnyOrder("2", "3", "4"));
    }

    @Test
    public void shouldNotAllowAddingMoreThenMaximumNumberOfEntriesWhenUsingAddAll() {
        // given
        CircularPriorityQueue<String, String> concurrentLinkedQueue = new CircularPriorityQueue<>(3, String.class, String::compareToIgnoreCase, string -> string);

        // when
        concurrentLinkedQueue.addAll(Arrays.asList("1", "2", "3", "4"));

        // then
        assertEquals(3, concurrentLinkedQueue.size());
        assertThat(concurrentLinkedQueue, not(contains("1")));
        assertThat(concurrentLinkedQueue, containsInAnyOrder("2", "3", "4"));
    }

    @Test
    public void shouldSortOrder() {
        // given
        CircularPriorityQueue<String, String> concurrentLinkedQueue = new CircularPriorityQueue<>(3, String.class, String::compareToIgnoreCase, string -> string);

        // when
        concurrentLinkedQueue.add("4");
        assertThat(concurrentLinkedQueue.toSortedList().toArray(new String[0]), is(new String[]{"4"}));
        concurrentLinkedQueue.add("1");
        assertThat(concurrentLinkedQueue.toSortedList().toArray(new String[0]), is(new String[]{"1", "4"}));
        concurrentLinkedQueue.add("3");
        assertThat(concurrentLinkedQueue.toSortedList().toArray(new String[0]), is(new String[]{"1", "3", "4"}));
        concurrentLinkedQueue.add("2");
        assertThat(concurrentLinkedQueue.toSortedList().toArray(new String[0]), is(new String[]{"1", "2", "3"}));
        concurrentLinkedQueue.add("5");
        assertThat(concurrentLinkedQueue.toSortedList().toArray(new String[0]), is(new String[]{"2", "3", "5"}));

        // then
        assertEquals(3, concurrentLinkedQueue.size());
        assertThat(concurrentLinkedQueue, not(contains("1")));
        assertThat(concurrentLinkedQueue, not(contains("2")));
        assertThat(concurrentLinkedQueue.toSortedList(), contains("2", "3", "5"));
    }

    @Test
    public void shouldSortExpectationOrderSamePriorityInsertedInOrder() {
        // given
        CircularPriorityQueue<String, Expectation> concurrentLinkedQueue = new CircularPriorityQueue<>(3, Expectation.class, EXPECTATION_PRIORITY_COMPARATOR, Expectation::getId);

        long currentTimeMillis = System.currentTimeMillis();
        Expectation one = when(request("one"), 0).withCreated(currentTimeMillis + 1);
        Expectation two = when(request("two"), 0).withCreated(currentTimeMillis + 2);
        Expectation three = when(request("three"), 0).withCreated(currentTimeMillis + 3);
        Expectation four = when(request("four"), 0).withCreated(currentTimeMillis + 4);
        Expectation five = when(request("five"), 0).withCreated(currentTimeMillis + 5);

        // when
        concurrentLinkedQueue.add(one);
        assertThat(concurrentLinkedQueue.toSortedList().toArray(new Expectation[0]), is(new Expectation[]{
            one
        }));
        concurrentLinkedQueue.add(two);
        assertThat(concurrentLinkedQueue.toSortedList().toArray(new Expectation[0]), is(new Expectation[]{
            one,
            two
        }));
        concurrentLinkedQueue.add(three);
        assertThat(concurrentLinkedQueue.toSortedList().toArray(new Expectation[0]), is(new Expectation[]{
            one,
            two,
            three
        }));
        concurrentLinkedQueue.add(four);
        assertThat(concurrentLinkedQueue.toSortedList().toArray(new Expectation[0]), is(new Expectation[]{
            two,
            three,
            four
        }));
        concurrentLinkedQueue.add(five);
        assertThat(concurrentLinkedQueue.toSortedList().toArray(new Expectation[0]), is(new Expectation[]{
            three,
            four,
            five
        }));

        // then
        assertEquals(3, concurrentLinkedQueue.size());
        assertThat(concurrentLinkedQueue, not(contains(one)));
        assertThat(concurrentLinkedQueue, not(contains(two)));
        assertThat(concurrentLinkedQueue.toSortedList(), contains(three, four, five));
    }

    @Test
    public void shouldSortExpectationOrderSamePriorityInsertedOutOfOrder() {
        // given
        CircularPriorityQueue<String, Expectation> concurrentLinkedQueue = new CircularPriorityQueue<>(3, Expectation.class, EXPECTATION_PRIORITY_COMPARATOR, Expectation::getId);

        long currentTimeMillis = System.currentTimeMillis();
        Expectation one = when(request("one"), 0).withCreated(currentTimeMillis + 1);
        Expectation two = when(request("two"), 0).withCreated(currentTimeMillis + 2);
        Expectation three = when(request("three"), 0).withCreated(currentTimeMillis + 3);
        Expectation four = when(request("four"), 0).withCreated(currentTimeMillis + 4);
        Expectation five = when(request("five"), 0).withCreated(currentTimeMillis + 5);

        // when
        concurrentLinkedQueue.add(two);
        assertThat(concurrentLinkedQueue.toSortedList().toArray(new Expectation[0]), is(new Expectation[]{
            two
        }));
        concurrentLinkedQueue.add(three);
        assertThat(concurrentLinkedQueue.toSortedList().toArray(new Expectation[0]), is(new Expectation[]{
            two,
            three
        }));
        concurrentLinkedQueue.add(one);
        assertThat(concurrentLinkedQueue.toSortedList().toArray(new Expectation[0]), is(new Expectation[]{
            one,
            two,
            three
        }));
        concurrentLinkedQueue.add(five);
        assertThat(concurrentLinkedQueue.toSortedList().toArray(new Expectation[0]), is(new Expectation[]{
            one,
            three,
            five
        }));
        concurrentLinkedQueue.add(four);
        assertThat(concurrentLinkedQueue.toSortedList().toArray(new Expectation[0]), is(new Expectation[]{
            one,
            four,
            five
        }));

        // then
        assertEquals(3, concurrentLinkedQueue.size());
        assertThat(concurrentLinkedQueue, not(contains(one)));
        assertThat(concurrentLinkedQueue, not(contains(two)));
        assertThat(concurrentLinkedQueue.toSortedList(), contains(one, four, five));
        assertThat(concurrentLinkedQueue.getByKey(five.getId()), is(Optional.of(five)));
        assertThat(concurrentLinkedQueue.getByKey(four.getId()), is(Optional.of(four)));
        assertThat(concurrentLinkedQueue.getByKey(three.getId()), is(Optional.empty()));
        assertThat(concurrentLinkedQueue.getByKey(two.getId()), is(Optional.empty()));
        assertThat(concurrentLinkedQueue.getByKey(one.getId()), is(Optional.of(one)));
    }

    @Test
    public void shouldSortExpectationOrderDifferentPriorityButConsistentWithTimeInsertedInOrder() {
        // given
        CircularPriorityQueue<String, Expectation> concurrentLinkedQueue = new CircularPriorityQueue<>(3, Expectation.class, EXPECTATION_PRIORITY_COMPARATOR, Expectation::getId);

        long currentTimeMillis = System.currentTimeMillis();
        Expectation one = when(request("one"), 4).withCreated(currentTimeMillis + 1);
        Expectation two = when(request("two"), 3).withCreated(currentTimeMillis + 2);
        Expectation three = when(request("three"), 2).withCreated(currentTimeMillis + 3);
        Expectation four = when(request("four"), 1).withCreated(currentTimeMillis + 4);
        Expectation five = when(request("five"), 0).withCreated(currentTimeMillis + 5);

        // when
        concurrentLinkedQueue.add(one);
        assertThat(concurrentLinkedQueue.toSortedList().toArray(new Expectation[0]), is(new Expectation[]{
            one
        }));
        concurrentLinkedQueue.add(two);
        assertThat(concurrentLinkedQueue.toSortedList().toArray(new Expectation[0]), is(new Expectation[]{
            one,
            two
        }));
        concurrentLinkedQueue.add(three);
        assertThat(concurrentLinkedQueue.toSortedList().toArray(new Expectation[0]), is(new Expectation[]{
            one,
            two,
            three
        }));
        concurrentLinkedQueue.add(four);
        assertThat(concurrentLinkedQueue.toSortedList().toArray(new Expectation[0]), is(new Expectation[]{
            two,
            three,
            four
        }));
        concurrentLinkedQueue.add(five);
        assertThat(concurrentLinkedQueue.toSortedList().toArray(new Expectation[0]), is(new Expectation[]{
            three,
            four,
            five
        }));

        // then
        assertEquals(3, concurrentLinkedQueue.size());
        assertThat(concurrentLinkedQueue, not(contains(one)));
        assertThat(concurrentLinkedQueue, not(contains(two)));
        assertThat(concurrentLinkedQueue.toSortedList(), contains(three, four, five));
        assertThat(concurrentLinkedQueue.getByKey(five.getId()), is(Optional.of(five)));
        assertThat(concurrentLinkedQueue.getByKey(four.getId()), is(Optional.of(four)));
        assertThat(concurrentLinkedQueue.getByKey(three.getId()), is(Optional.of(three)));
        assertThat(concurrentLinkedQueue.getByKey(two.getId()), is(Optional.empty()));
        assertThat(concurrentLinkedQueue.getByKey(one.getId()), is(Optional.empty()));
    }

    @Test
    public void shouldSortExpectationOrderDifferentPriorityButConsistentWithTimeInsertedOutOfOrder() {
        // given
        CircularPriorityQueue<String, Expectation> concurrentLinkedQueue = new CircularPriorityQueue<>(3, Expectation.class, EXPECTATION_PRIORITY_COMPARATOR, Expectation::getId);

        long currentTimeMillis = System.currentTimeMillis();
        Expectation one = when(request("one"), 4).withCreated(currentTimeMillis + 1);
        Expectation two = when(request("two"), 3).withCreated(currentTimeMillis + 2);
        Expectation three = when(request("three"), 2).withCreated(currentTimeMillis + 3);
        Expectation four = when(request("four"), 1).withCreated(currentTimeMillis + 4);
        Expectation five = when(request("five"), 0).withCreated(currentTimeMillis + 5);

        // when
        concurrentLinkedQueue.add(two);
        assertThat(concurrentLinkedQueue.toSortedList().toArray(new Expectation[0]), is(new Expectation[]{
            two
        }));
        concurrentLinkedQueue.add(three);
        assertThat(concurrentLinkedQueue.toSortedList().toArray(new Expectation[0]), is(new Expectation[]{
            two,
            three
        }));
        concurrentLinkedQueue.add(one);
        assertThat(concurrentLinkedQueue.toSortedList().toArray(new Expectation[0]), is(new Expectation[]{
            one,
            two,
            three
        }));
        concurrentLinkedQueue.add(five);
        assertThat(concurrentLinkedQueue.toSortedList().toArray(new Expectation[0]), is(new Expectation[]{
            one,
            three,
            five
        }));
        concurrentLinkedQueue.add(four);
        assertThat(concurrentLinkedQueue.toSortedList().toArray(new Expectation[0]), is(new Expectation[]{
            one,
            four,
            five
        }));

        // then
        assertEquals(3, concurrentLinkedQueue.size());
        assertThat(concurrentLinkedQueue, not(contains(two)));
        assertThat(concurrentLinkedQueue, not(contains(three)));
        assertThat(concurrentLinkedQueue.toSortedList(), contains(one, four, five));
        assertThat(concurrentLinkedQueue.getByKey(five.getId()), is(Optional.of(five)));
        assertThat(concurrentLinkedQueue.getByKey(four.getId()), is(Optional.of(four)));
        assertThat(concurrentLinkedQueue.getByKey(three.getId()), is(Optional.empty()));
        assertThat(concurrentLinkedQueue.getByKey(two.getId()), is(Optional.empty()));
        assertThat(concurrentLinkedQueue.getByKey(one.getId()), is(Optional.of(one)));
    }

    @Test
    public void shouldSortExpectationOrderDifferentPriorityInconsistentWithTimeInsertedOutOfOrder() {
        // given
        CircularPriorityQueue<String, Expectation> concurrentLinkedQueue = new CircularPriorityQueue<>(3, Expectation.class, EXPECTATION_PRIORITY_COMPARATOR, Expectation::getId);

        long currentTimeMillis = 0;
        Expectation one = when(request("one"), 0).withCreated(currentTimeMillis + 1);
        Expectation two = when(request("two"), 0).withCreated(currentTimeMillis + 2);
        Expectation three = when(request("three"), 2).withCreated(currentTimeMillis + 3);
        Expectation four = when(request("four"), 1).withCreated(currentTimeMillis + 4);
        Expectation five = when(request("five"), 3).withCreated(currentTimeMillis + 5);

        // when
        concurrentLinkedQueue.add(one);
        assertThat(concurrentLinkedQueue.toSortedList().toArray(new Expectation[0]), is(new Expectation[]{
            one,
        }));
        concurrentLinkedQueue.add(two);
        assertThat(concurrentLinkedQueue.toSortedList().toArray(new Expectation[0]), is(new Expectation[]{
            one,
            two,
        }));
        concurrentLinkedQueue.add(three);
        assertThat(concurrentLinkedQueue.toSortedList().toArray(new Expectation[0]), is(new Expectation[]{
            three,
            one,
            two,
        }));
        concurrentLinkedQueue.add(four);
        assertThat(concurrentLinkedQueue.toSortedList().toArray(new Expectation[0]), is(new Expectation[]{
            three,
            four,
            two,
        }));
        concurrentLinkedQueue.add(five);
        assertThat(concurrentLinkedQueue.toSortedList().toArray(new Expectation[0]), is(new Expectation[]{
            five,
            three,
            four,
        }));

        // then
        assertEquals(3, concurrentLinkedQueue.size());
        assertThat(concurrentLinkedQueue, not(contains(one)));
        assertThat(concurrentLinkedQueue, not(contains(two)));
        assertThat(concurrentLinkedQueue.toSortedList(), contains(five, three, four));
        assertThat(concurrentLinkedQueue.getByKey(five.getId()), is(Optional.of(five)));
        assertThat(concurrentLinkedQueue.getByKey(four.getId()), is(Optional.of(four)));
        assertThat(concurrentLinkedQueue.getByKey(three.getId()), is(Optional.of(three)));
        assertThat(concurrentLinkedQueue.getByKey(two.getId()), is(Optional.empty()));
        assertThat(concurrentLinkedQueue.getByKey(one.getId()), is(Optional.empty()));
    }

    @Test
    public void shouldSortExpectationOrderDifferentPriorityGroupsLargerQueue() {
        // given
        CircularPriorityQueue<String, Expectation> concurrentLinkedQueue = new CircularPriorityQueue<>(5, Expectation.class, EXPECTATION_PRIORITY_COMPARATOR, Expectation::getId);

        long currentTimeMillis = 0;
        Expectation one = when(request("one"), 1).withCreated(currentTimeMillis);
        Expectation two = when(request("two"), 1).withCreated(currentTimeMillis + 1);
        Expectation three = when(request("three"), 2).withCreated(currentTimeMillis);
        Expectation four = when(request("four"), 3).withCreated(currentTimeMillis);
        Expectation five = when(request("five"), 3).withCreated(currentTimeMillis + 1);

        // when
        concurrentLinkedQueue.add(two);
        assertThat(concurrentLinkedQueue.toSortedList().toArray(new Expectation[0]), is(new Expectation[]{
            two,
        }));
        concurrentLinkedQueue.add(one);
        assertThat(concurrentLinkedQueue.toSortedList().toArray(new Expectation[0]), is(new Expectation[]{
            one,
            two,
        }));
        concurrentLinkedQueue.add(three);
        assertThat(concurrentLinkedQueue.toSortedList().toArray(new Expectation[0]), is(new Expectation[]{
            three,
            one,
            two,
        }));
        concurrentLinkedQueue.add(five);
        assertThat(concurrentLinkedQueue.toSortedList().toArray(new Expectation[0]), is(new Expectation[]{
            five,
            three,
            one,
            two,
        }));
        concurrentLinkedQueue.add(four);
        assertThat(concurrentLinkedQueue.toSortedList().toArray(new Expectation[0]), is(new Expectation[]{
            four,
            five,
            three,
            one,
            two,
        }));

        // then
        assertEquals(5, concurrentLinkedQueue.size());
        assertThat(concurrentLinkedQueue.toSortedList(), contains(four, five, three, one, two));
        assertThat(concurrentLinkedQueue.getByKey(five.getId()), is(Optional.of(five)));
        assertThat(concurrentLinkedQueue.getByKey(four.getId()), is(Optional.of(four)));
        assertThat(concurrentLinkedQueue.getByKey(three.getId()), is(Optional.of(three)));
        assertThat(concurrentLinkedQueue.getByKey(two.getId()), is(Optional.of(two)));
        assertThat(concurrentLinkedQueue.getByKey(one.getId()), is(Optional.of(one)));
    }

    @Test
    public void shouldRemove() {
        // given - a queue
        CircularPriorityQueue<String, Expectation> concurrentLinkedQueue = new CircularPriorityQueue<>(5, Expectation.class, EXPECTATION_PRIORITY_COMPARATOR, Expectation::getId);

        long currentTimeMillis = 0;
        Expectation one = when(request("one"), 1).withCreated(currentTimeMillis);
        Expectation two = when(request("two"), 1).withCreated(currentTimeMillis + 1);
        Expectation three = when(request("three"), 2).withCreated(currentTimeMillis);
        Expectation four = when(request("four"), 3).withCreated(currentTimeMillis);
        Expectation five = when(request("five"), 3).withCreated(currentTimeMillis + 1);

        // given - added items
        concurrentLinkedQueue.add(two);
        concurrentLinkedQueue.add(one);
        concurrentLinkedQueue.add(three);
        concurrentLinkedQueue.add(five);
        concurrentLinkedQueue.add(four);

        // four -> five -> three -> one -> two

        // then
        assertThat(concurrentLinkedQueue.toSortedList().toArray(new Expectation[0]), is(new Expectation[]{
            four,
            five,
            three,
            one,
            two,
        }));
        assertThat(concurrentLinkedQueue.keyMap().get(one.getId()), is(one));
        assertThat(concurrentLinkedQueue.keyMap().get(two.getId()), is(two));
        assertThat(concurrentLinkedQueue.keyMap().get(three.getId()), is(three));
        assertThat(concurrentLinkedQueue.keyMap().get(four.getId()), is(four));
        assertThat(concurrentLinkedQueue.keyMap().get(five.getId()), is(five));

        // when
        concurrentLinkedQueue.remove(three);
        assertThat(concurrentLinkedQueue.toSortedList().toArray(new Expectation[0]), is(new Expectation[]{
            four,
            five,
            one,
            two,
        }));
        assertThat(concurrentLinkedQueue.getByKey(five.getId()), is(Optional.of(five)));
        assertThat(concurrentLinkedQueue.getByKey(four.getId()), is(Optional.of(four)));
        assertThat(concurrentLinkedQueue.getByKey(three.getId()), is(Optional.empty()));
        assertThat(concurrentLinkedQueue.getByKey(two.getId()), is(Optional.of(two)));
        assertThat(concurrentLinkedQueue.getByKey(one.getId()), is(Optional.of(one)));
        assertThat(concurrentLinkedQueue.keyMap().get(three.getId()), is(nullValue()));

        // and
        concurrentLinkedQueue.remove(four);
        assertThat(concurrentLinkedQueue.toSortedList().toArray(new Expectation[0]), is(new Expectation[]{
            five,
            one,
            two,
        }));
        assertThat(concurrentLinkedQueue.getByKey(five.getId()), is(Optional.of(five)));
        assertThat(concurrentLinkedQueue.getByKey(four.getId()), is(Optional.empty()));
        assertThat(concurrentLinkedQueue.getByKey(three.getId()), is(Optional.empty()));
        assertThat(concurrentLinkedQueue.getByKey(two.getId()), is(Optional.of(two)));
        assertThat(concurrentLinkedQueue.getByKey(one.getId()), is(Optional.of(one)));
        assertThat(concurrentLinkedQueue.keyMap().get(three.getId()), is(nullValue()));
        assertThat(concurrentLinkedQueue.keyMap().get(four.getId()), is(nullValue()));

        // then
        assertEquals(3, concurrentLinkedQueue.size());
        assertThat(concurrentLinkedQueue.toSortedList(), contains(five, one, two));
    }

}