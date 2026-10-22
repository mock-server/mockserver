package org.mockserver.serialization.model;

import org.mockserver.model.AnyOfRequestDefinition;
import org.mockserver.model.BinaryRequestDefinition;
import org.mockserver.model.ConditionalRequestDefinition;
import org.mockserver.model.DnsRequestDefinition;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.OpenAPIDefinition;
import org.mockserver.model.RequestDefinition;

public abstract class RequestDefinitionDTO extends NotDTO {

    public RequestDefinitionDTO(Boolean not) {
        super(not);
    }

    /**
     * The DTO for any request definition the control plane accepts, or null for null. An unsupported type throws
     * rather than being dropped: a dropped clear filter is an empty body, which clears everything.
     */
    public static RequestDefinitionDTO of(RequestDefinition requestDefinition) {
        if (requestDefinition == null) {
            return null;
        } else if (requestDefinition instanceof HttpRequest) {
            return new HttpRequestDTO((HttpRequest) requestDefinition);
        } else if (requestDefinition instanceof OpenAPIDefinition) {
            return new OpenAPIDefinitionDTO((OpenAPIDefinition) requestDefinition);
        } else if (requestDefinition instanceof DnsRequestDefinition) {
            return new DnsRequestDefinitionDTO((DnsRequestDefinition) requestDefinition);
        } else if (requestDefinition instanceof BinaryRequestDefinition) {
            return new BinaryRequestDefinitionDTO((BinaryRequestDefinition) requestDefinition);
        } else if (requestDefinition instanceof ConditionalRequestDefinition) {
            return new ConditionalRequestDefinitionDTO((ConditionalRequestDefinition) requestDefinition);
        } else if (requestDefinition instanceof AnyOfRequestDefinition) {
            return new AnyOfRequestDefinitionDTO((AnyOfRequestDefinition) requestDefinition);
        } else {
            throw new IllegalArgumentException("a " + requestDefinition.getClass().getName() + " cannot be sent to MockServer as a request definition,"
                + " only an http request, OpenAPI, DNS, binary, conditional or anyOf request definition can");
        }
    }

    public abstract RequestDefinition buildObject();

}
