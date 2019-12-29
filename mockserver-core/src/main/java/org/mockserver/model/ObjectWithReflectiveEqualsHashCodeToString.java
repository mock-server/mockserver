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

    static {
        ReflectionToStringBuilder.setDefaultStyle(ToStringStyle.SHORT_PREFIX_STYLE);
    }

    protected String[] fieldsExcludedFromEqualsAndHashCode() {
        return IGNORE_KEY_FIELD;
    }

    @Override
    public String toString() {
        return ReflectionToStringBuilder.toStringExclude(this, fieldsExcludedFromEqualsAndHashCode());
    }

    @Override
    @SuppressWarnings("EqualsWhichDoesntCheckParameterClass")
    public boolean equals(Object other) {
        return EqualsBuilder.reflectionEquals(this, other, fieldsExcludedFromEqualsAndHashCode());
    }

    @Override
    public int hashCode() {
        return HashCodeBuilder.reflectionHashCode(this, fieldsExcludedFromEqualsAndHashCode());
    }

}
