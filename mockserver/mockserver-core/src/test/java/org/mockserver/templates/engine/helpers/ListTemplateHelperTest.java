package org.mockserver.templates.engine.helpers;

import org.junit.Test;
import org.mockserver.templates.engine.TemplateFunctions;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.isIn;
import static org.junit.Assert.assertThrows;

public class ListTemplateHelperTest {

    private final ListTemplateHelper lists = new ListTemplateHelper();

    @Test
    public void shouldPickEveryValueFromArgumentsOverManyPicks() {
        Set<Object> picked = new HashSet<>();
        for (int i = 0; i < 200; i++) {
            picked.add(lists.pickRandom("red", "green", "blue"));
        }
        assertThat(picked, containsInAnyOrder("red", "green", "blue"));
    }

    @Test
    public void shouldPickFromASingleListArrayOrMapValues() {
        assertThat(lists.pickRandom(List.of("only")), is("only"));
        assertThat(lists.pickRandom((Object) new String[]{"only"}), is("only"));
        assertThat(lists.pickRandom((Object) new int[]{7}), is(7));
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("a", "x");
        assertThat(lists.pickRandom(map), is("x"));
        assertThat(lists.pickRandom("a", "b"), isIn(List.of("a", "b")));
    }

    @Test
    public void shouldFailToPickFromNothing() {
        assertThat(assertThrows(IllegalArgumentException.class, () -> lists.pickRandom()).getMessage(),
            is("lists.pickRandom: there is nothing to pick from; pass at least one value"));
        assertThrows(IllegalArgumentException.class, () -> lists.pickRandom(List.of()));
    }

    @Test
    public void shouldPickTheSameSequenceForTheSameSeed() {
        ListTemplateHelper first = new ListTemplateHelper(new Random(42));
        ListTemplateHelper second = new ListTemplateHelper(new Random(42));
        List<Object> firstPicks = new ArrayList<>();
        List<Object> secondPicks = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            firstPicks.add(first.pickRandom("a", "b", "c", "d"));
            secondPicks.add(second.pickRandom("a", "b", "c", "d"));
        }
        assertThat(firstPicks, is(secondPicks));
        assertThat(TemplateFunctions.resolveLists(0L), is(TemplateFunctions.ON_DEMAND_HELPERS.get("lists")));
    }

    @Test
    public void shouldBuildAndJoinArrays() {
        assertThat(lists.array("a", 1, true), contains("a", 1, true));
        assertThat(lists.array(), is(empty()));
        assertThat(lists.array(Arrays.asList("x", null)), contains("x", null));
        assertThat(lists.arrayJoin(", ", "a", "b", "c"), is("a, b, c"));
        assertThat(lists.arrayJoin("-", lists.array("a", "b")), is("a-b"));
        assertThat(lists.arrayJoin(",", lists.range(1, 3)), is("1,2,3"));
        assertThat(lists.arrayJoin(null, "a", "b"), is("ab"));
        assertThat(lists.arrayJoin(","), is(""));
    }

    @Test
    public void shouldBuildInclusiveRangesInEitherDirection() {
        assertThat(lists.range(1, 5), contains(1, 2, 3, 4, 5));
        assertThat(lists.range(3, 1), contains(3, 2, 1));
        assertThat(lists.range("-1", " 1 "), contains(-1, 0, 1));
        assertThat(lists.range(2.0, 2), contains(2));
    }

    @Test
    public void shouldCapRangesAndArraysWithoutAllocatingThem() {
        assertThat(lists.range(1, ListTemplateHelper.MAX_SIZE).size(), is(ListTemplateHelper.MAX_SIZE));
        assertThat(assertThrows(IllegalArgumentException.class, () -> lists.range(0, ListTemplateHelper.MAX_SIZE)).getMessage(),
            is("lists.range: a range has at most 10000 values, but 0 to 10000 has 10001"));
        assertThat(assertThrows(IllegalArgumentException.class, () -> lists.range(Integer.MIN_VALUE, Integer.MAX_VALUE)).getMessage(),
            is("lists.range: a range has at most 10000 values, but -2147483648 to 2147483647 has 4294967296"));
        assertThat(assertThrows(IllegalArgumentException.class, () -> lists.array(new Object[ListTemplateHelper.MAX_SIZE + 1])).getMessage(),
            is("lists.array: a list has at most 10000 values, but this one has 10001"));
    }

    @Test
    public void shouldCapTheLengthOfJoinedText() {
        String separator = "x".repeat(1_000);
        assertThat(lists.arrayJoin(separator, "a", "b").length(), is(1_002));
        assertThat(assertThrows(IllegalArgumentException.class, () -> lists.arrayJoin(separator, lists.range(1, 100))).getMessage(),
            is("lists.arrayJoin: the joined text would be 99192 characters, over the limit of 65536"));
        String longValue = "y".repeat(100_000);
        assertThat(lists.arrayJoin(",", longValue, longValue).length(), is(200_001));
    }

    @Test
    public void shouldRefuseAnOversizedCollectionBeforeCopyingIt() {
        java.util.Collection<Object> huge = new java.util.AbstractCollection<>() {
            @Override
            public java.util.Iterator<Object> iterator() {
                throw new AssertionError("the collection must not be copied");
            }

            @Override
            public int size() {
                return ListTemplateHelper.MAX_SIZE + 1;
            }
        };
        assertThat(assertThrows(IllegalArgumentException.class, () -> lists.pickRandom(huge)).getMessage(),
            is("lists.pickRandom: a list has at most 10000 values, but this one has 10001"));
    }

    @Test
    public void shouldRejectRangeBoundsThatAreNotWholeNumbers() {
        assertThat(assertThrows(IllegalArgumentException.class, () -> lists.range("one", 5)).getMessage(),
            is("lists.range: from must be a whole number, but was \"one\""));
        assertThat(assertThrows(IllegalArgumentException.class, () -> lists.range(1, 2.5)).getMessage(),
            is("lists.range: to must be a whole number, but was 2.5"));
        assertThrows(IllegalArgumentException.class, () -> lists.range(1, 1e12));
        assertThrows(IllegalArgumentException.class, () -> lists.range(null, 1));
    }

    @Test
    public void shouldBeRegisteredForTemplates() {
        assertThat(TemplateFunctions.ON_DEMAND_HELPERS.get("lists"), instanceOf(ListTemplateHelper.class));
        assertThat(TemplateFunctions.BUILT_IN_HELPERS.containsKey("lists"), is(false));
    }
}
