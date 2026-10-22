package org.mockserver.serialization.model;

import org.mockserver.model.AnyOfRequestDefinition;
import org.mockserver.model.ConditionalRequestDefinition;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.OpenAPIDefinition;
import org.mockserver.model.RequestDefinition;

import java.util.ArrayList;
import java.util.List;

/**
 * DTO for {@link AnyOfRequestDefinition} (a logical OR of request matchers).
 */
public class AnyOfRequestDefinitionDTO extends RequestDefinitionDTO {

    private List<RequestDefinitionDTO> anyOf;

    public AnyOfRequestDefinitionDTO(AnyOfRequestDefinition anyOfRequestDefinition) {
        super(anyOfRequestDefinition != null ? anyOfRequestDefinition.getNot() : null);
        if (anyOfRequestDefinition != null) {
            anyOf = new ArrayList<>();
            for (RequestDefinition branch : anyOfRequestDefinition.getAnyOf()) {
                anyOf.add(branchDTO(branch));
            }
        }
    }

    public AnyOfRequestDefinitionDTO() {
        super(false);
    }

    /**
     * The DTO of an anyOf or conditional branch. Branch types are limited to those the anyOfRequestDefinition and
     * conditionalRequestDefinition JSON schemas accept, so an expectation built in Java always round-trips over the
     * JSON API.
     */
    static RequestDefinitionDTO branchDTO(RequestDefinition requestDefinition) {
        if (requestDefinition instanceof HttpRequest) {
            return new HttpRequestDTO((HttpRequest) requestDefinition);
        } else if (requestDefinition instanceof OpenAPIDefinition) {
            return new OpenAPIDefinitionDTO((OpenAPIDefinition) requestDefinition);
        } else if (requestDefinition instanceof ConditionalRequestDefinition) {
            return new ConditionalRequestDefinitionDTO((ConditionalRequestDefinition) requestDefinition);
        } else if (requestDefinition instanceof AnyOfRequestDefinition) {
            return new AnyOfRequestDefinitionDTO((AnyOfRequestDefinition) requestDefinition);
        } else if (requestDefinition != null) {
            throw new IllegalArgumentException("Unsupported request matcher branch type: "
                + requestDefinition.getClass().getSimpleName()
                + " (only httpRequest, openAPIDefinition, a conditional or an anyOf request matcher can be a branch)");
        } else {
            return null;
        }
    }

    public AnyOfRequestDefinition buildObject() {
        List<RequestDefinition> branches = new ArrayList<>();
        if (anyOf != null) {
            for (RequestDefinitionDTO branch : anyOf) {
                if (branch != null) {
                    branches.add(branch.buildObject());
                }
            }
        }
        return (AnyOfRequestDefinition) new AnyOfRequestDefinition()
            .withAnyOf(branches)
            .withNot(getNot());
    }

    public List<RequestDefinitionDTO> getAnyOf() {
        return anyOf;
    }

    public AnyOfRequestDefinitionDTO setAnyOf(List<RequestDefinitionDTO> anyOf) {
        this.anyOf = anyOf;
        return this;
    }
}
