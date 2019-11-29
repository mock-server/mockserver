package org.mockserver.model;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.nio.charset.Charset;

/**
 * @author jamesdbloom
 */
public abstract class BodyWithContentType<T> extends Body {

    protected final MediaType contentType;

    public BodyWithContentType(Type type, MediaType contentType) {
        super(type);
        this.contentType = contentType;
    }

    @JsonIgnore
    Charset determineCharacterSet(MediaType contentType, Charset defaultCharset) {
        if (contentType != null) {
            Charset charset = contentType.getCharset();
            if (charset != null) {
                return charset;
            }
        }
        return defaultCharset;
    }

    @Override
    @JsonIgnore
    public Charset getCharset(Charset defaultIfNotSet) {
        return determineCharacterSet(contentType, defaultIfNotSet);
    }

    @Override
    public String getContentType() {
        return (contentType != null ? contentType.toString() : null);
    }

}
