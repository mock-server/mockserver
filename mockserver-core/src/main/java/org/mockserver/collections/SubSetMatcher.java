package org.mockserver.collections;

import org.mockserver.logging.MockServerLogger;
import org.mockserver.matchers.RegexStringMatcher;

import java.util.*;

public class SubSetMatcher {

    // TODO(jamesdbloom) convert to separate test and pull out static methods
    public static void main(String[] args) {
        RegexStringMatcher regexStringMatcher = new RegexStringMatcher(new MockServerLogger(), false);
        System.out.println("empty\n\t\t\t\t\t\t\t\ttrue = " + containsSubset(regexStringMatcher,
            Collections.emptyList(),
            new ArrayList<>(Arrays.asList(
                new ImmutableEntry(regexStringMatcher, "one", "one"),
                new ImmutableEntry(regexStringMatcher, "two", "two"),
                new ImmutableEntry(regexStringMatcher, "three", "three")
            )))
        );
        System.out.println("multi values all matching\n\t\t\t\t\t\t\t\ttrue = " + containsSubset(regexStringMatcher,
            Arrays.asList(
                new ImmutableEntry(regexStringMatcher, "one", "one_one"),
                new ImmutableEntry(regexStringMatcher, "one", "one_two"),
                new ImmutableEntry(regexStringMatcher, "two", "two"),
                new ImmutableEntry(regexStringMatcher, "?four", "four")
            ),
            new ArrayList<>(Arrays.asList(
                new ImmutableEntry(regexStringMatcher, "one", "one_one"),
                new ImmutableEntry(regexStringMatcher, "one", "one_two"),
                new ImmutableEntry(regexStringMatcher, "two", "two"),
                new ImmutableEntry(regexStringMatcher, "three", "three")
            )))
        );
        System.out.println("multi values subset\n\t\t\t\t\t\t\t\ttrue = " + containsSubset(regexStringMatcher,
            Arrays.asList(
                new ImmutableEntry(regexStringMatcher, "one", "one_one"),
                new ImmutableEntry(regexStringMatcher, "two", "two"),
                new ImmutableEntry(regexStringMatcher, "?four", "four")
            ),
            new ArrayList<>(Arrays.asList(
                new ImmutableEntry(regexStringMatcher, "one", "one_one"),
                new ImmutableEntry(regexStringMatcher, "one", "one_two"),
                new ImmutableEntry(regexStringMatcher, "two", "two"),
                new ImmutableEntry(regexStringMatcher, "three", "three")
            )))
        );
        System.out.println("notted key\n\t\t\t\t\t\t\t\ttrue = " + containsSubset(regexStringMatcher,
            Arrays.asList(
                new ImmutableEntry(regexStringMatcher, "one", "one"),
                new ImmutableEntry(regexStringMatcher, "!two", "two"),
                new ImmutableEntry(regexStringMatcher, "?four", "four")
            ),
            new ArrayList<>(Arrays.asList(
                new ImmutableEntry(regexStringMatcher, "one", "one"),
                new ImmutableEntry(regexStringMatcher, "notTwo", "two"),
                new ImmutableEntry(regexStringMatcher, "three", "three")
            )))
        );
        System.out.println("notted value\n\t\t\t\t\t\t\t\ttrue = " + containsSubset(regexStringMatcher,
            Arrays.asList(
                new ImmutableEntry(regexStringMatcher, "one", "one"),
                new ImmutableEntry(regexStringMatcher, "two", "!two"),
                new ImmutableEntry(regexStringMatcher, "?four", "four")
            ),
            new ArrayList<>(Arrays.asList(
                new ImmutableEntry(regexStringMatcher, "one", "one"),
                new ImmutableEntry(regexStringMatcher, "two", "notTwo"),
                new ImmutableEntry(regexStringMatcher, "three", "three")
            )))
        );
        System.out.println("multi values notted value\n\t\t\t\t\t\t\t\ttrue = " + containsSubset(regexStringMatcher,
            Arrays.asList(
                new ImmutableEntry(regexStringMatcher, "one", "one_one"),
                new ImmutableEntry(regexStringMatcher, "one", "!one_two"),
                new ImmutableEntry(regexStringMatcher, "two", "two"),
                new ImmutableEntry(regexStringMatcher, "?four", "four")
            ),
            new ArrayList<>(Arrays.asList(
                new ImmutableEntry(regexStringMatcher, "one", "one_one"),
                new ImmutableEntry(regexStringMatcher, "one", "notOne_two"),
                new ImmutableEntry(regexStringMatcher, "two", "two"),
                new ImmutableEntry(regexStringMatcher, "three", "three")
            )))
        );
        System.out.println("subset optional present\n\t\t\t\t\t\t\t\ttrue = " + containsSubset(regexStringMatcher,
            Arrays.asList(
                new ImmutableEntry(regexStringMatcher, "one", "one"),
                new ImmutableEntry(regexStringMatcher, "two", "two"),
                new ImmutableEntry(regexStringMatcher, "?four", "four")
            ),
            new ArrayList<>(Arrays.asList(
                new ImmutableEntry(regexStringMatcher, "one", "one"),
                new ImmutableEntry(regexStringMatcher, "two", "two"),
                new ImmutableEntry(regexStringMatcher, "three", "three"),
                new ImmutableEntry(regexStringMatcher, "four", "four")
            )))
        );
        System.out.println("subset and optional not present\n\t\t\t\t\t\t\t\ttrue = " + containsSubset(regexStringMatcher,
            Arrays.asList(
                new ImmutableEntry(regexStringMatcher, "one", "one"),
                new ImmutableEntry(regexStringMatcher, "two", "two"),
                new ImmutableEntry(regexStringMatcher, "?four", "four")
            ),
            new ArrayList<>(Arrays.asList(
                new ImmutableEntry(regexStringMatcher, "one", "one"),
                new ImmutableEntry(regexStringMatcher, "two", "two"),
                new ImmutableEntry(regexStringMatcher, "three", "three")
            )))
        );
        System.out.println("optional wrong value\n\t\t\t\t\t\t\t\tfalse = " + containsSubset(regexStringMatcher,
            Arrays.asList(
                new ImmutableEntry(regexStringMatcher, "one", "one"),
                new ImmutableEntry(regexStringMatcher, "two", "two"),
                new ImmutableEntry(regexStringMatcher, "?four", "four")
            ),
            new ArrayList<>(Arrays.asList(
                new ImmutableEntry(regexStringMatcher, "one", "one"),
                new ImmutableEntry(regexStringMatcher, "two", "two"),
                new ImmutableEntry(regexStringMatcher, "three", "three"),
                new ImmutableEntry(regexStringMatcher, "four", "wrong")
            )))
        );
        System.out.println("optional wrong value in reverse\n\t\t\t\t\t\t\t\tfalse = " + containsSubset(regexStringMatcher,
            Arrays.asList(
                new ImmutableEntry(regexStringMatcher, "one", "one"),
                new ImmutableEntry(regexStringMatcher, "two", "two"),
                new ImmutableEntry(regexStringMatcher, "four", "four")
            ),
            new ArrayList<>(Arrays.asList(
                new ImmutableEntry(regexStringMatcher, "one", "one"),
                new ImmutableEntry(regexStringMatcher, "two", "two"),
                new ImmutableEntry(regexStringMatcher, "three", "three"),
                new ImmutableEntry(regexStringMatcher, "?four", "wrong")
            )))
        );
        System.out.println("extra value\n\t\t\t\t\t\t\t\tfalse = " + containsSubset(regexStringMatcher,
            Arrays.asList(
                new ImmutableEntry(regexStringMatcher, "one", "one"),
                new ImmutableEntry(regexStringMatcher, "two", "two"),
                new ImmutableEntry(regexStringMatcher, "?four", "four"),
                new ImmutableEntry(regexStringMatcher, "five", "five")
            ),
            new ArrayList<>(Arrays.asList(
                new ImmutableEntry(regexStringMatcher, "one", "one"),
                new ImmutableEntry(regexStringMatcher, "two", "two"),
                new ImmutableEntry(regexStringMatcher, "three", "three"),
                new ImmutableEntry(regexStringMatcher, "four", "four")
            )))
        );
        System.out.println("value wrong\n\t\t\t\t\t\t\t\tfalse = " + containsSubset(regexStringMatcher,
            Arrays.asList(
                new ImmutableEntry(regexStringMatcher, "one", "one"),
                new ImmutableEntry(regexStringMatcher, "two", "two"),
                new ImmutableEntry(regexStringMatcher, "?four", "four")
            ),
            new ArrayList<>(Arrays.asList(
                new ImmutableEntry(regexStringMatcher, "one", "one"),
                new ImmutableEntry(regexStringMatcher, "two", "wrong"),
                new ImmutableEntry(regexStringMatcher, "three", "three"),
                new ImmutableEntry(regexStringMatcher, "four", "four")
            )))
        );
        System.out.println("key wrong\n\t\t\t\t\t\t\t\tfalse = " + containsSubset(regexStringMatcher,
            Arrays.asList(
                new ImmutableEntry(regexStringMatcher, "one", "one"),
                new ImmutableEntry(regexStringMatcher, "two", "two"),
                new ImmutableEntry(regexStringMatcher, "?four", "four")
            ),
            new ArrayList<>(Arrays.asList(
                new ImmutableEntry(regexStringMatcher, "one", "one"),
                new ImmutableEntry(regexStringMatcher, "wrong", "two"),
                new ImmutableEntry(regexStringMatcher, "three", "three"),
                new ImmutableEntry(regexStringMatcher, "four", "four")
            )))
        );
    }

    static boolean containsSubset(RegexStringMatcher regexStringMatcher, List<ImmutableEntry> subset, List<ImmutableEntry> superset) {
        boolean result = true;
        Set<Integer> matchingIndexes = new HashSet<>();
        for (ImmutableEntry subsetItem : subset) {
            Set<Integer> subsetItemMatchingIndexes = matchesIndexes(regexStringMatcher, superset, subsetItem);
            boolean optionalAndNotPresent = subsetItem.isOptional() && !containsKey(regexStringMatcher, superset, subsetItem);
            if (!optionalAndNotPresent && subsetItemMatchingIndexes.isEmpty()) {
                result = false;
                break;
            }
            matchingIndexes.addAll(subsetItemMatchingIndexes);
        }

        if (result) {
            long subsetNonOptionalSize = subset.stream().filter(ImmutableEntry::isNotOptional).count();
            // this prevents multiple items in the subset from being matched by a single item in the superset
            result = matchingIndexes.size() >= subsetNonOptionalSize;
        }
        return result;
    }

    private static Set<Integer> matchesIndexes(RegexStringMatcher regexStringMatcher, List<ImmutableEntry> matchedList, ImmutableEntry matcherItem) {
        Set<Integer> matchingIndexes = new HashSet<>();
        for (int i = 0; i < matchedList.size(); i++) {
            ImmutableEntry matchedItem = matchedList.get(i);
            boolean keyMatches = regexStringMatcher.matches(matcherItem.getKey(), matchedItem.getKey(), true);
            boolean valueMatches = regexStringMatcher.matches(matcherItem.getValue(), matchedItem.getValue(), true);
            if (keyMatches && valueMatches) {
                matchingIndexes.add(i);
            }
        }
        return matchingIndexes;
    }

    private static boolean containsKey(RegexStringMatcher regexStringMatcher, List<ImmutableEntry> list, ImmutableEntry item) {
        for (ImmutableEntry matcherItem : list) {
            if (regexStringMatcher.matches(matcherItem.getKey(), item.getKey(), true)) {
                return true;
            }
        }
        return false;
    }

}
