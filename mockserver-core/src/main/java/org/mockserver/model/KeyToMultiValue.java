package org.mockserver.model;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * @author jamesdbloom
 */
public class KeyToMultiValue extends ModelObject {
    private final String name;
    private final List<String> values;

    public KeyToMultiValue(String name, String... values) {
        this(name, Arrays.asList(values));
    }

    public KeyToMultiValue(String name, Collection<String> values) {
        this.name = name;
        if (values != null) {
            this.values = new ArrayList<>(values);
        } else {
            this.values = new ArrayList<>();
        }
    }

    public static Multimap<String, String> toMultiMap(List<? extends KeyToMultiValue> keyToMultiValues) {
        Multimap<String, String> headersMap = HashMultimap.create();
        for (KeyToMultiValue keyToMultiValue : keyToMultiValues) {
            for (String value : keyToMultiValue.getValues()) {
                headersMap.put(keyToMultiValue.getName(), value);
            }
        }
        return headersMap;
    }

    public static Multimap<String, String> toMultiMap(KeyToMultiValue... keyToMultiValues) {
        return toMultiMap(Arrays.asList(keyToMultiValues));
    }

    public String getName() {
        return name;
    }

    public List<String> getValues() {
        return values;
    }
}
