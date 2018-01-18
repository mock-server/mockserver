package org.mockserver.model;

import java.util.*;

import static org.mockserver.model.NottableString.string;
import static org.mockserver.model.NottableString.strings;

/**
 * @author jamesdbloom
 */
public class KeyToMultiValue extends ObjectWithJsonToString {
    private final NottableString name;
    private final List<NottableString> values;

    public KeyToMultiValue(String name, String... values) {
        this(string(name), strings(values));
    }

    public KeyToMultiValue(NottableString name, NottableString... values) {
        this(name, Arrays.asList(values));
    }

    public KeyToMultiValue(String name, Collection<String> values) {
        this(string(name), strings(values));
    }

    public KeyToMultiValue(NottableString name, Collection<NottableString> values) {
        this.name = name;
        if (values == null || values.isEmpty()) {
            this.values = Collections.singletonList(string(".*"));
        } else {
            this.values = new ArrayList<>(values);
        }
    }

    public NottableString getName() {
        return name;
    }

    public List<NottableString> getValues() {
        return values;
    }

    public void addValue(String value) {
        if (values != null && !values.contains(string(value))) {
            values.add(string(value));
        }
    }

    public void addValue(NottableString value) {
        if (values != null && !values.contains(value)) {
            values.add(value);
        }
    }

    public void addValues(List<String> values) {
        if (this.values != null) {
            for (String value : values) {
                if (!this.values.contains(string(value))) {
                    this.values.add(string(value));
                }
            }
        }
    }

    public void addNottableValues(List<NottableString> values) {
        if (this.values != null) {
            for (NottableString value : values) {
                if (!this.values.contains(value)) {
                    this.values.add(value);
                }
            }
        }
    }

    public void addValues(String... values) {
        addValues(Arrays.asList(values));
    }

    public void addValues(NottableString... values) {
        addNottableValues(Arrays.asList(values));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        KeyToMultiValue that = (KeyToMultiValue) o;
        return Objects.equals(name, that.name) && Objects.equals(values, that.values);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, values);
    }
}
