package org.mockserver.model;

import java.util.*;

import static org.mockserver.model.NottableString.*;

/**
 * @author jamesdbloom
 */
public class KeyToMultiValue extends ObjectWithJsonToString {
    private final NottableString name;
    private final List<NottableString> values;
    private Integer hashCode;

    KeyToMultiValue(final String name, final String... values) {
        this(string(name), strings(values));
    }

    @SuppressWarnings({"UseBulkOperation", "ManualArrayToCollectionCopy"})
    KeyToMultiValue(final NottableString name, final NottableString... values) {
        if (name == null) {
            throw new IllegalArgumentException("key must not be null");
        }
        this.name = name;
        if (values == null || values.length == 0) {
            this.values = Collections.singletonList(string(".*"));
        } else if (values.length == 1) {
            this.values = Collections.singletonList(values[0]);
        } else {
            this.values = new LinkedList<>();
            for (NottableString value : values) {
                this.values.add(value);
            }
        }
    }

    KeyToMultiValue(final String name, final Collection<String> values) {
        this(string(name), strings(values));
    }

    KeyToMultiValue(final NottableString name, final Collection<NottableString> values) {
        this.name = name;
        if (values == null || values.isEmpty()) {
            this.values = Collections.singletonList(string(".*"));
        } else {
            this.values = new LinkedList<>(values);
        }
        this.hashCode = Objects.hash(this.name, this.values);
    }

    public NottableString getName() {
        return name;
    }

    public List<NottableString> getValues() {
        return values;
    }

    public void replaceValues(List<NottableString> values) {
        if (this.values != values) {
            this.values.clear();
            this.values.addAll(values);
        }
    }

    public void addValue(final String value) {
        addValue(string(value));
    }

    private void addValue(final NottableString value) {
        if (values != null && !values.contains(value)) {
            values.add(value);
        }
        this.hashCode = Objects.hash(name, values);
    }

    private void addValues(final List<String> values) {
        addNottableValues(deserializeNottableStrings(values));
    }

    private void addNottableValues(final List<NottableString> values) {
        if (this.values != null) {
            for (NottableString value : values) {
                if (!this.values.contains(value)) {
                    this.values.add(value);
                }
            }
        }
    }

    public void addValues(final String... values) {
        addValues(Arrays.asList(values));
    }

    public void addValues(final NottableString... values) {
        addNottableValues(Arrays.asList(values));
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (hashCode() != o.hashCode()) {
            return false;
        }
        KeyToMultiValue that = (KeyToMultiValue) o;
        return Objects.equals(name, that.name) &&
            Objects.equals(values, that.values);
    }

    @Override
    public int hashCode() {
        if (hashCode == null) {
            this.hashCode = Objects.hash(this.name, this.values);
        }
        return hashCode;
    }
}
