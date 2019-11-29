package org.mockserver.serialization.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.mockserver.model.*;

/**
 * @author jamesdbloom
 */
public abstract class BodyWithContentTypeDTO extends BodyDTO {

    protected String contentType;

    public BodyWithContentTypeDTO(Body.Type type, Boolean not, String contentType) {
        super(type, not);
        this.contentType = contentType;
    }

    public static BodyWithContentTypeDTO createDTO(BodyWithContentType body) {
        BodyWithContentTypeDTO result = null;

        if (body instanceof BinaryBody) {
            BinaryBody binaryBody = (BinaryBody) body;
            result = new BinaryBodyDTO(binaryBody, binaryBody.getNot());
        } else if (body instanceof JsonBody) {
            JsonBody jsonBody = (JsonBody) body;
            result = new JsonBodyDTO(jsonBody, jsonBody.getNot());
        } else if (body instanceof ParameterBody) {
            ParameterBody parameterBody = (ParameterBody) body;
            result = new ParameterBodyDTO(parameterBody, parameterBody.getNot());
        } else if (body instanceof StringBody) {
            StringBody stringBody = (StringBody) body;
            result = new StringBodyDTO(stringBody, stringBody.getNot());
        } else if (body instanceof XmlBody) {
            XmlBody xmlBody = (XmlBody) body;
            result = new XmlBodyDTO(xmlBody, xmlBody.getNot());
        }

        return result;
    }

    public String getContentType() {
        return contentType;
    }

    public abstract BodyWithContentType buildObject();

    @JsonIgnore
    MediaType getMediaType() {
        return contentType != null ? MediaType.parse(contentType) : null;
    }

}
