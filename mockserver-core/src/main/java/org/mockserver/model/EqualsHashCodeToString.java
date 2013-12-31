package org.mockserver.model;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author jamesdbloom
 */
public abstract class EqualsHashCodeToString {
    protected Logger logger = LoggerFactory.getLogger(this.getClass());

    static {
        ReflectionToStringBuilder.setDefaultStyle(ToStringStyle.SHORT_PREFIX_STYLE);
    }

    @Override
    public String toString() {
        return ReflectionToStringBuilder.toStringExclude(this, "logger");
    }

    @Override
    public boolean equals(Object other) {
        return EqualsBuilder.reflectionEquals(this, other);
    }

    @Override
    public int hashCode() {
        return HashCodeBuilder.reflectionHashCode(this);
    }
}
