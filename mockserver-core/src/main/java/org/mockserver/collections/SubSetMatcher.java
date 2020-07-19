package org.mockserver.collections;

import org.mockserver.matchers.RegexStringMatcher;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class SubSetMatcher {

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

    private static boolean containsKey(RegexStringMatcher regexStringMatcher, List<ImmutableEntry> matchedList, ImmutableEntry matcherItem) {
        for (ImmutableEntry matchedItem : matchedList) {
            if (regexStringMatcher.matches(matcherItem.getKey(), matchedItem.getKey(), true)) {
                return true;
            }
        }
        return false;
    }

}
