package org.mockserver.client.serialization.model;

import org.mockserver.model.*;

/**
 * @author jamesdbloom
 */
public abstract class BodyDTO extends NotDTO {

    private Body.Type type;
    protected String contentType;

    public BodyDTO(Body.Type type, Boolean not, String contentType) {
        super(not);
        this.type = type;
        this.contentType = contentType;
    }

    public BodyDTO() {
    }

    public static BodyDTO createDTO(Body body) {
        BodyDTO result = null;

        if (body instanceof StringBody) {
            StringBody stringBody = (StringBody) body;
            result = new StringBodyDTO(stringBody, stringBody.getNot());
        } else if (body instanceof RegexBody) {
            RegexBody regexBody = (RegexBody) body;
            result = new RegexBodyDTO(regexBody, regexBody.getNot());
        } else if (body instanceof JsonBody) {
            JsonBody jsonBody = (JsonBody) body;
            result = new JsonBodyDTO(jsonBody, jsonBody.getNot());
        } else if (body instanceof JsonSchemaBody) {
            JsonSchemaBody jsonSchemaBody = (JsonSchemaBody) body;
            result = new JsonSchemaBodyDTO(jsonSchemaBody, jsonSchemaBody.getNot());
        } else if (body instanceof XPathBody) {
            XPathBody xPathBody = (XPathBody) body;
            result = new XPathBodyDTO(xPathBody, xPathBody.getNot());
        } else if (body instanceof XmlBody) {
            XmlBody xmlBody = (XmlBody) body;
            result = new XmlBodyDTO(xmlBody, xmlBody.getNot());
        } else if (body instanceof ParameterBody) {
            ParameterBody parameterBody = (ParameterBody) body;
            result = new ParameterBodyDTO(parameterBody, parameterBody.getNot());
        } else if (body instanceof BinaryBody) {
            BinaryBody binaryBody = (BinaryBody) body;
            result = new BinaryBodyDTO(binaryBody, binaryBody.getNot());
        }

        return result;
    }

    public Body.Type getType() {
        return type;
    }

    public String getContentType() {
        return contentType;
    }

    public abstract Body buildObject();

}
