package org.mockserver.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

import java.util.UUID;

/**
 * @author jamesdbloom
 */
public abstract class ObjectWithReflectiveEqualsHashCodeToString {

    private static final String[] IGNORE_KEY_FIELD = {"key"};

    static {
        ReflectionToStringBuilder.setDefaultStyle(ToStringStyle.SHORT_PREFIX_STYLE);
    }

    private String key;

    protected String[] fieldsExcludedFromEqualsAndHashCode() {
        return IGNORE_KEY_FIELD;
    }

    @Override
    public String toString() {
        return ReflectionToStringBuilder.toStringExclude(this, fieldsExcludedFromEqualsAndHashCode());
    }

    @Override
    public boolean equals(Object other) {
        return EqualsBuilder.reflectionEquals(this, other, fieldsExcludedFromEqualsAndHashCode());
    }

    @Override
    public int hashCode() {
        return HashCodeBuilder.reflectionHashCode(this, fieldsExcludedFromEqualsAndHashCode());
    }

    @JsonIgnore
    public String key() {
        if (key == null) {
            key = UUID.randomUUID().toString();
        }
        return key;
    }
}
