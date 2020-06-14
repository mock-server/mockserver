package org.mockserver.serialization.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.mockserver.model.*;

/**
 * @author jamesdbloom
 */
@SuppressWarnings("rawtypes")
public abstract class BodyWithContentTypeDTO extends BodyDTO {

    protected final String contentType;

    public BodyWithContentTypeDTO(Body.Type type, Boolean not, String contentType) {
        super(type, not);
        this.contentType = contentType;
    }

    public static BodyWithContentTypeDTO createWithContentTypeDTO(BodyWithContentType body) {
        BodyWithContentTypeDTO result = null;

        if (body instanceof BinaryBody) {
            BinaryBody binaryBody = (BinaryBody) body;
            result = new BinaryBodyDTO(binaryBody, binaryBody.getNot());
        } else if (body instanceof JsonBody) {
            JsonBody jsonBody = (JsonBody) body;
            result = new JsonBodyDTO(jsonBody, jsonBody.getNot());
        } else if (body instanceof StringBody) {
            StringBody stringBody = (StringBody) body;
            result = new StringBodyDTO(stringBody, stringBody.getNot());
        } else if (body instanceof XmlBody) {
            XmlBody xmlBody = (XmlBody) body;
            result = new XmlBodyDTO(xmlBody, xmlBody.getNot());
        } else if (body instanceof LogEventBody) {
            LogEventBody logEventBody = (LogEventBody) body;
            result = new LogEventBodyDTO(logEventBody);
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
