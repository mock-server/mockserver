package org.mockserver.collections;

import org.mockserver.matchers.RegexStringMatcher;
import org.mockserver.model.NottableString;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.mockserver.model.NottableString.string;

public class SubSetMatcher {

    static boolean containsSubset(RegexStringMatcher regexStringMatcher, List<ImmutableEntry> subset, List<ImmutableEntry> superset) {
        boolean result = true;
        Set<Integer> matchingIndexes = new HashSet<>();
        for (ImmutableEntry subsetItem : subset) {
            Set<Integer> subsetItemMatchingIndexes = matchesIndexes(regexStringMatcher, subsetItem, superset);
            boolean optionalAndNotPresent = subsetItem.isOptional() && !containsKey(regexStringMatcher, subsetItem, superset);
            boolean nottedAndPresent = nottedAndPresent(regexStringMatcher, subsetItem, superset);
            if ((!optionalAndNotPresent && subsetItemMatchingIndexes.isEmpty()) || nottedAndPresent) {
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

    private static Set<Integer> matchesIndexes(RegexStringMatcher regexStringMatcher, ImmutableEntry matcherItem, List<ImmutableEntry> matchedList) {
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

    private static boolean containsKey(RegexStringMatcher regexStringMatcher, ImmutableEntry matcherItem, List<ImmutableEntry> matchedList) {
        for (ImmutableEntry matchedItem : matchedList) {
            if (regexStringMatcher.matches(matcherItem.getKey(), matchedItem.getKey(), true)) {
                return true;
            }
        }
        return false;
    }

    private static boolean nottedAndPresent(RegexStringMatcher regexStringMatcher, ImmutableEntry matcherItem, List<ImmutableEntry> matchedList) {
        if (matcherItem.getKey().isNot()) {
            NottableString unNottedMatcherItemKey = string(matcherItem.getKey().getValue());
            for (ImmutableEntry matchedItem : matchedList) {
                if (!matchedItem.getKey().isNot()) {
                    if (regexStringMatcher.matches(unNottedMatcherItemKey, matchedItem.getKey(), true)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

}
