package org.mockserver.serialization.model;

import org.mockserver.model.*;

/**
 * @author jamesdbloom
 */
public abstract class BodyDTO extends NotDTO implements DTO<Body> {

    private Body.Type type;

    public BodyDTO(Body.Type type, Boolean not) {
        super(not);
        this.type = type;
    }

    public static BodyDTO createDTO(Body body) {
        BodyDTO result = null;

        if (body instanceof BinaryBody) {
            BinaryBody binaryBody = (BinaryBody) body;
            result = new BinaryBodyDTO(binaryBody, binaryBody.getNot());
        } else if (body instanceof JsonBody) {
            JsonBody jsonBody = (JsonBody) body;
            result = new JsonBodyDTO(jsonBody, jsonBody.getNot());
        } else if (body instanceof JsonSchemaBody) {
            JsonSchemaBody jsonSchemaBody = (JsonSchemaBody) body;
            result = new JsonSchemaBodyDTO(jsonSchemaBody, jsonSchemaBody.getNot());
        } else if (body instanceof ParameterBody) {
            ParameterBody parameterBody = (ParameterBody) body;
            result = new ParameterBodyDTO(parameterBody, parameterBody.getNot());
        } else if (body instanceof RegexBody) {
            RegexBody regexBody = (RegexBody) body;
            result = new RegexBodyDTO(regexBody, regexBody.getNot());
        } else if (body instanceof StringBody) {
            StringBody stringBody = (StringBody) body;
            result = new StringBodyDTO(stringBody, stringBody.getNot());
        } else if (body instanceof XmlBody) {
            XmlBody xmlBody = (XmlBody) body;
            result = new XmlBodyDTO(xmlBody, xmlBody.getNot());
        } else if (body instanceof XmlSchemaBody) {
            XmlSchemaBody xmlSchemaBody = (XmlSchemaBody) body;
            result = new XmlSchemaBodyDTO(xmlSchemaBody, xmlSchemaBody.getNot());
        } else if (body instanceof XPathBody) {
            XPathBody xPathBody = (XPathBody) body;
            result = new XPathBodyDTO(xPathBody, xPathBody.getNot());
        }

        return result;
    }

    public Body.Type getType() {
        return type;
    }

    public abstract Body buildObject();

}
