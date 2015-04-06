package org.mockserver.client.serialization.model;

import org.mockserver.model.*;

/**
 * @author jamesdbloom
 */
public abstract class BodyDTO extends NotDTO {

    private Body.Type type;

    public BodyDTO(Body.Type type, boolean not) {
        super(not);
        this.type = type;
    }

    public BodyDTO() {
    }

    public static BodyDTO createDTO(Body body) {
        BodyDTO result = null;

        if (body instanceof StringBody) {
            StringBody stringBody = (StringBody) body;
            result = new StringBodyDTO(stringBody, stringBody.isNot());
        } else if (body instanceof RegexBody) {
            RegexBody regexBody = (RegexBody) body;
            result = new RegexBodyDTO(regexBody, regexBody.isNot());
        } else if (body instanceof JsonBody) {
            JsonBody jsonBody = (JsonBody) body;
            result = new JsonBodyDTO(jsonBody, jsonBody.isNot());
        } else if (body instanceof JsonSchemaBody) {
            JsonSchemaBody jsonSchemaBody = (JsonSchemaBody) body;
            result = new JsonSchemaBodyDTO(jsonSchemaBody, jsonSchemaBody.isNot());
        } else if (body instanceof XPathBody) {
            XPathBody xPathBody = (XPathBody) body;
            result = new XPathBodyDTO(xPathBody, xPathBody.isNot());
        } else if (body instanceof ParameterBody) {
            ParameterBody parameterBody = (ParameterBody) body;
            result = new ParameterBodyDTO(parameterBody, parameterBody.isNot());
        } else if (body instanceof BinaryBody) {
            BinaryBody binaryBody = (BinaryBody) body;
            result = new BinaryBodyDTO(binaryBody, binaryBody.isNot());
        }

        return result;
    }

    public Body.Type getType() {
        return type;
    }

    public abstract Body buildObject();

}
