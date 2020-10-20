package org.mockserver.collections;

import org.apache.commons.lang3.tuple.Pair;
import org.mockserver.matchers.RegexStringMatcher;
import org.mockserver.model.NottableString;

import java.util.*;

import static org.mockserver.model.NottableString.string;

public class ImmutableEntry extends Pair<NottableString, NottableString> implements Map.Entry<NottableString, NottableString> {
    private final RegexStringMatcher regexStringMatcher;
    private final NottableString key;
    private final NottableString value;

    public static ImmutableEntry entry(RegexStringMatcher regexStringMatcher, String key, String value) {
        return new ImmutableEntry(regexStringMatcher, key, value);
    }

    public static ImmutableEntry entry(RegexStringMatcher regexStringMatcher, NottableString key, NottableString value) {
        return new ImmutableEntry(regexStringMatcher, key, value);
    }

    ImmutableEntry(RegexStringMatcher regexStringMatcher, String key, String value) {
        this.regexStringMatcher = regexStringMatcher;
        this.key = string(key);
        this.value = string(value);
    }

    ImmutableEntry(RegexStringMatcher regexStringMatcher, NottableString key, NottableString value) {
        this.regexStringMatcher = regexStringMatcher;
        this.key = key;
        this.value = value;
    }

    public boolean isOptional() {
        return getKey().isOptional();
    }

    public boolean isNotted() {
        return getKey().isNot() && !getValue().isNot();
    }

    public boolean isNotOptional() {
        return !isOptional();
    }

    @Override
    public NottableString getLeft() {
        return key;
    }

    @Override
    public NottableString getRight() {
        return value;
    }

    @Override
    public NottableString setValue(NottableString value) {
        throw new UnsupportedOperationException("ImmutableEntry is immutable");
    }

    @Override
    public String toString() {
        return "(" + key + ": " + value + ")";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        ImmutableEntry that = (ImmutableEntry) o;
        return regexStringMatcher.matches(key, that.key, true) &&
            regexStringMatcher.matches(value, that.value, true) ||
            (
                !regexStringMatcher.matches(key, that.key, true) &&
                    (
                        key.isOptional() || that.key.isOptional()
                    )
            );
    }

    @Override
    public int hashCode() {
        return Objects.hash(key, value);
    }

    public static <T> boolean listsEqual(List<T> matcher, List<T> matched) {
        boolean matches = false;
        if (matcher.size() == matched.size()) {
            Set<Integer> matchedIndexes = new HashSet<>();
            Set<Integer> matcherIndexes = new HashSet<>();
            for (int i = 0; i < matcher.size(); i++) {
                T matcherItem = matcher.get(i);
                for (int j = 0; j < matched.size(); j++) {
                    T matchedItem = matched.get(j);
                    if (matcherItem != null && matcherItem.equals(matchedItem)) {
                        matchedIndexes.add(j);
                        matcherIndexes.add(i);
                    }
                }
            }
            matches = matchedIndexes.size() == matched.size() && matcherIndexes.size() == matcher.size();
        }
        return matches;
    }

    public static boolean listsEqualWithOptionals(RegexStringMatcher regexStringMatcher, List<ImmutableEntry> matcher, List<ImmutableEntry> matched) {
        Set<Integer> matchingMatchedIndexes = new HashSet<>();
        Set<Integer> matchingMatcherIndexes = new HashSet<>();
        Set<NottableString> matcherKeys = new HashSet<>();
        matcher.forEach(matcherItem -> matcherKeys.add(matcherItem.getKey()));
        Set<NottableString> matchedKeys = new HashSet<>();
        matched.forEach(matchedItem -> matchedKeys.add(matchedItem.getKey()));
        for (int i = 0; i < matcher.size(); i++) {
            ImmutableEntry matcherItem = matcher.get(i);
            if (matcherItem != null) {
                for (int j = 0; j < matched.size(); j++) {
                    ImmutableEntry matchedItem = matched.get(j);
                    if (matchedItem != null) {
                        if (matcherItem.equals(matchedItem)) {
                            matchingMatchedIndexes.add(j);
                            matchingMatcherIndexes.add(i);
                        } else if (matcherItem.getKey().isOptional() && !contains(regexStringMatcher, matchedKeys, matcherItem.getKey())) {
                            matchingMatchedIndexes.add(j);
                            matchingMatcherIndexes.add(i);
                        } else if (matchedItem.getKey().isOptional() && !contains(regexStringMatcher, matcherKeys, matchedItem.getKey())) {
                            matchingMatchedIndexes.add(j);
                            matchingMatcherIndexes.add(i);
                        }
                    }
                }
            }
        }
        return matchingMatchedIndexes.size() == matched.size() && matchingMatcherIndexes.size() == matcher.size();
    }

    private static boolean contains(RegexStringMatcher regexStringMatcher, Set<NottableString> matchedKeys, NottableString matcherItem) {
        boolean result = false;
        for (NottableString matchedKey : matchedKeys) {
            if (regexStringMatcher.matches(matchedKey, matcherItem, true)) {
                return true;
            }
        }
        return result;
    }
}