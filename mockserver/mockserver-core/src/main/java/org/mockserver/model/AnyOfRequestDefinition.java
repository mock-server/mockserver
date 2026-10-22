package org.mockserver.model;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * A logical OR of request matchers: it matches a request when at least one of its request definitions
 * matches it. Branches are evaluated in order and evaluation stops at the first that matches.
 * <p>
 * Each branch reuses the existing {@link RequestDefinition} matching machinery, so a branch may be an
 * {@link HttpRequest}, an {@link OpenAPIDefinition}, a {@link ConditionalRequestDefinition} or a nested
 * {@link AnyOfRequestDefinition}. With {@code not} set it matches when no branch matches.
 * </p>
 * <p>
 * Serialised as an {@code httpRequest} holding an {@code anyOf} array, for example
 * {@code {"httpRequest": {"anyOf": [{"path": "/a"}, {"path": "/b"}]}}}.
 * </p>
 */
public class AnyOfRequestDefinition extends RequestDefinition {

    private int hashCode;
    private List<RequestDefinition> anyOf = Collections.emptyList();

    public static AnyOfRequestDefinition requestAnyOf(RequestDefinition... requestDefinitions) {
        return new AnyOfRequestDefinition().withAnyOf(requestDefinitions);
    }

    public static AnyOfRequestDefinition requestAnyOf(List<? extends RequestDefinition> requestDefinitions) {
        return new AnyOfRequestDefinition().withAnyOf(requestDefinitions);
    }

    public List<RequestDefinition> getAnyOf() {
        return anyOf;
    }

    public AnyOfRequestDefinition withAnyOf(RequestDefinition... requestDefinitions) {
        return withAnyOf(requestDefinitions != null ? Arrays.asList(requestDefinitions) : null);
    }

    public AnyOfRequestDefinition withAnyOf(List<? extends RequestDefinition> requestDefinitions) {
        this.anyOf = requestDefinitions != null ? Collections.unmodifiableList(new ArrayList<>(requestDefinitions)) : Collections.emptyList();
        this.hashCode = 0;
        return this;
    }

    @Override
    public AnyOfRequestDefinition withNot(Boolean not) {
        super.withNot(not);
        this.hashCode = 0;
        return this;
    }

    @Override
    public AnyOfRequestDefinition shallowClone() {
        return (AnyOfRequestDefinition) new AnyOfRequestDefinition()
            .withAnyOf(anyOf)
            .withNot(getNot());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (hashCode() != o.hashCode()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        AnyOfRequestDefinition that = (AnyOfRequestDefinition) o;
        return Objects.equals(anyOf, that.anyOf);
    }

    @Override
    public int hashCode() {
        if (hashCode == 0) {
            hashCode = Objects.hash(super.hashCode(), anyOf);
        }
        return hashCode;
    }
}
