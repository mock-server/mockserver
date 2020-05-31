package org.mockserver.model;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

/**
 * @author jamesdbloom
 */
public abstract class ObjectWithReflectiveEqualsHashCodeToString {

    private static final String[] IGNORE_KEY_FIELD = {};

    protected String[] fieldsExcludedFromEqualsAndHashCode() {
        return IGNORE_KEY_FIELD;
    }

    @Override
    public String toString() {
        return new ReflectionToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE, null, ObjectWithReflectiveEqualsHashCodeToString.class, false, false).setExcludeFieldNames(fieldsExcludedFromEqualsAndHashCode()).toString();
    }

    @Override
    @SuppressWarnings("EqualsWhichDoesntCheckParameterClass")
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (other == null) {
            return false;
        }
        return new EqualsBuilder()
                    .setExcludeFields(fieldsExcludedFromEqualsAndHashCode())
                    .setReflectUpToClass(ObjectWithReflectiveEqualsHashCodeToString.class)
                    .setTestTransients(false)
                    .setTestRecursive(false)
                    .reflectionAppend(this, other)
                    .isEquals();
    }

    @Override
    public int hashCode() {
        return HashCodeBuilder.reflectionHashCode(this, fieldsExcludedFromEqualsAndHashCode());
    }

}
