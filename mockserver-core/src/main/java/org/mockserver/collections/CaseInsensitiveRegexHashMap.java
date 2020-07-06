package org.mockserver.collections;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.matchers.RegexStringMatcher;
import org.mockserver.model.NottableString;

import java.util.*;
import java.util.stream.Collectors;

import static org.mockserver.collections.ImmutableEntry.entry;
import static org.mockserver.collections.ImmutableEntry.listsEqual;
import static org.mockserver.collections.SubSets.distinctSubSetsMap;
import static org.mockserver.model.NottableString.string;
import static org.slf4j.event.Level.TRACE;

/**
 * Map that uses case insensitive regex expression matching for keys and values
 *
 * @author jamesdbloom
 */
public class CaseInsensitiveRegexHashMap extends LinkedHashMap<NottableString, NottableString> implements Map<NottableString, NottableString> {

    private final RegexStringMatcher regexStringMatcher;
    private final MockServerLogger mockServerLogger;
    private boolean noOptionals = true;

    public CaseInsensitiveRegexHashMap(MockServerLogger mockServerLogger, boolean controlPlaneMatcher) {
        this.mockServerLogger = mockServerLogger;
        regexStringMatcher = new RegexStringMatcher(mockServerLogger, controlPlaneMatcher);
    }

    @VisibleForTesting
    public static CaseInsensitiveRegexHashMap hashMap(boolean controlPlaneMatcher, String[]... keyAndValues) {
        CaseInsensitiveRegexHashMap hashMap = new CaseInsensitiveRegexHashMap(new MockServerLogger(), controlPlaneMatcher);
        for (String[] keyAndValue : keyAndValues) {
            if (keyAndValue.length >= 2) {
                hashMap.put(keyAndValue[0], keyAndValue[1]);
            }
        }
        return hashMap;
    }

    @VisibleForTesting
    public static CaseInsensitiveRegexHashMap hashMap(boolean controlPlaneMatcher, NottableString[]... keyAndValues) {
        CaseInsensitiveRegexHashMap hashMap = new CaseInsensitiveRegexHashMap(new MockServerLogger(), controlPlaneMatcher);
        for (NottableString[] keyAndValue : keyAndValues) {
            if (keyAndValue.length >= 2) {
                hashMap.put(keyAndValue[0], keyAndValue[1]);
            }
        }
        return hashMap;
    }

    public boolean isNoOptionals() {
        return noOptionals;
    }

    public boolean containsAll(CaseInsensitiveRegexHashMap matcher) {
        return containsAll(matcher, null);
    }

    public boolean containsAll(CaseInsensitiveRegexHashMap matcher, String logCorrelationId) {

        List<ImmutableEntry> matchedEntries = entryList();
        Multimap<Integer, List<ImmutableEntry>> allMatchedSubSets
            = distinctSubSetsMap(matchedEntries, ArrayListMultimap.create(), matchedEntries.size() - 1);

        if (MockServerLogger.isEnabled(TRACE)) {
            mockServerLogger.logEvent(
                new LogEntry()
                    .setLogLevel(TRACE)
                    .setCorrelationId(logCorrelationId)
                    .setMessageFormat("attempting to match subset from{}against hashmap{}")
                    .setArguments(allMatchedSubSets, matcher.entryList())

            );
        }

        if (isEmpty() && matcher.allKeysNotted()) {

            return true;

        } else if (noOptionals && matcher.isNoOptionals()) {

            // all non-optionals
            List<ImmutableEntry> matcherEntries = matcher.entryList();
            for (List<ImmutableEntry> matchedSubSet : allMatchedSubSets.get(matcherEntries.size())) {
                if (listsEqual(matcherEntries, matchedSubSet)) {
                    if (MockServerLogger.isEnabled(TRACE)) {
                        mockServerLogger.logEvent(
                            new LogEntry()
                                .setLogLevel(TRACE)
                                .setCorrelationId(logCorrelationId)
                                .setMessageFormat("hashmap{}containsAll subset{}in{}")
                                .setArguments(this, matchedSubSet, matcherEntries)

                        );
                    }
                    return true;
                }
            }
            if (MockServerLogger.isEnabled(TRACE)) {
                mockServerLogger.logEvent(
                    new LogEntry()
                        .setLogLevel(TRACE)
                        .setCorrelationId(logCorrelationId)
                        .setMessageFormat("hashmap{}containsAll found no subset equal to{}from{}")
                        .setArguments(this, matcherEntries, allMatchedSubSets)

                );
            }
        } else {

            // some optionals exist
            boolean result = false;

            // first check non-optionals
            List<ImmutableEntry> matcherEntriesWithoutOptionals = matcher.entryList().stream().filter(entry -> !entry.getKey().isOptional()).collect(Collectors.toList());
            for (List<ImmutableEntry> matchedSubSet : allMatchedSubSets.get(matcherEntriesWithoutOptionals.size())) {
                if (listsEqual(matcherEntriesWithoutOptionals, matchedSubSet)) {
                    if (MockServerLogger.isEnabled(TRACE)) {
                        mockServerLogger.logEvent(
                            new LogEntry()
                                .setLogLevel(TRACE)
                                .setCorrelationId(logCorrelationId)
                                .setMessageFormat("hashmap{}containsAll subset of non-optionals{}in{}")
                                .setArguments(this, matchedSubSet, matcherEntriesWithoutOptionals)

                        );
                    }
                    result = true;
                }
            }

            // then check optionals
            if (result) {
                List<ImmutableEntry> optionalMatcherEntries = matcher.entryList().stream().filter(entry -> entry.getKey().isOptional()).collect(Collectors.toList());
                if (!optionalMatcherEntries.isEmpty()) {
                    Set<ImmutableEntry> matchedSubSet = new HashSet<>();
                    for (ImmutableEntry optionalMatcherEntry : optionalMatcherEntries) {
                        for (ImmutableEntry matchedEntry : matchedEntries) {
                            if (optionalMatcherEntry.equals(matchedEntry)) {
                                matchedSubSet.add(matchedEntry);
                            } else {
                                if (MockServerLogger.isEnabled(TRACE)) {
                                    mockServerLogger.logEvent(
                                        new LogEntry()
                                            .setLogLevel(TRACE)
                                            .setCorrelationId(logCorrelationId)
                                            .setMessageFormat("hashmap{}failed to match optional{}with{}")
                                            .setArguments(this, optionalMatcherEntry, matchedEntry)

                                    );
                                }
                                return false;
                            }
                        }
                    }
                    if (MockServerLogger.isEnabled(TRACE)) {
                        mockServerLogger.logEvent(
                            new LogEntry()
                                .setLogLevel(TRACE)
                                .setCorrelationId(logCorrelationId)
                                .setMessageFormat("hashmap{}containsAll subset of optionals{}in{}")
                                .setArguments(this, matchedSubSet, optionalMatcherEntries)

                        );
                    }
                    return true;
                } else {
                    return true;
                }
            }

            if (MockServerLogger.isEnabled(TRACE)) {
                mockServerLogger.logEvent(
                    new LogEntry()
                        .setLogLevel(TRACE)
                        .setCorrelationId(logCorrelationId)
                        .setMessageFormat("hashmap{}containsAll found no subset equal to{}from{}")
                        .setArguments(this, matcher.entryList(), matchedEntries)

                );
            }
        }
        return false;
    }

    public boolean allKeysNotted() {
        for (NottableString key : keySet()) {
            if (!key.isNot()) {
                return false;
            }
        }
        return true;
    }

    public boolean allKeysOptional() {
        for (NottableString key : keySet()) {
            if (!key.isOptional()) {
                return false;
            }
        }
        return true;
    }

    public synchronized boolean containsKeyValue(String key, String value) {
        return containsKeyValue(string(key), string(value));
    }

    public synchronized boolean containsKeyValue(NottableString key, NottableString value) {
        boolean result = false;

        for (Entry<NottableString, NottableString> matcherEntry : entrySet()) {
            if (regexStringMatcher.matches(value, matcherEntry.getValue(), true)
                && regexStringMatcher.matches(key, matcherEntry.getKey(), true)) {
                result = true;
                break;
            }
        }

        return result;
    }

    @Override
    public synchronized boolean containsKey(Object key) {
        boolean result = false;

        if (key instanceof NottableString) {
            for (NottableString keyToCompare : keySet()) {
                if (regexStringMatcher.matches(((NottableString) key), keyToCompare, true)) {
                    result = true;
                    break;
                }
            }
        } else if (key instanceof String) {
            result = containsKey(string((String) key));
        }

        return result;
    }

    @Override
    public synchronized boolean containsValue(Object value) {
        boolean result = false;

        if (value instanceof NottableString) {
            for (Entry<NottableString, NottableString> entry : entrySet()) {
                if (regexStringMatcher.matches((NottableString) value, entry.getValue(), true)) {
                    return true;
                }
            }
        } else if (value instanceof String) {
            result = containsValue(string((String) value));
        }

        return result;
    }

    @Override
    public synchronized NottableString get(Object key) {
        if (key instanceof NottableString) {
            for (Entry<NottableString, NottableString> entry : entrySet()) {
                if (regexStringMatcher.matches((NottableString) key, entry.getKey(), true)) {
                    return super.get(entry.getKey());
                }
            }
        } else if (key instanceof String) {
            return get(string((String) key));
        }
        return null;
    }

    @Override
    public synchronized NottableString put(NottableString key, NottableString value) {
        if (key == null) {
            throw new IllegalArgumentException("key must not be null");
        }
        if (value == null) {
            throw new IllegalArgumentException("value must not be null");
        }
        if (key.isOptional()) {
            noOptionals = false;
        }
        return super.put(key, value);
    }

    public synchronized NottableString put(String key, String value) {
        return super.put(string(key), string(value));
    }

    @Override
    public synchronized NottableString remove(Object key) {
        if (key instanceof NottableString) {
            for (Entry<NottableString, NottableString> entry : entrySet()) {
                if (regexStringMatcher.matches((NottableString) key, entry.getKey(), true)) {
                    return super.remove(entry.getKey());
                }
            }
        } else if (key instanceof String) {
            return remove(string((String) key));
        }
        return null;
    }

    public synchronized List<ImmutableEntry> entryList() {
        if (!isEmpty()) {
            List<ImmutableEntry> entrySet = new ArrayList<>();
            for (Entry<NottableString, NottableString> entry : entrySet()) {
                entrySet.add(entry(regexStringMatcher, entry.getKey(), entry.getValue()));
            }
            return entrySet;
        } else {
            return Collections.emptyList();
        }
    }
}
