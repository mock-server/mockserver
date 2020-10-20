package org.mockserver.model;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.nio.charset.Charset;
import java.util.Objects;

import static org.mockserver.model.MediaType.DEFAULT_HTTP_CHARACTER_SET;

/**
 * @author jamesdbloom
 */
public abstract class BodyWithContentType<T> extends Body<T> {
    private int hashCode;
    protected final MediaType contentType;

    public BodyWithContentType(Type type, MediaType contentType) {
        super(type);
        this.contentType = contentType;
    }

    @JsonIgnore
    Charset determineCharacterSet(MediaType mediaType, Charset defaultCharset) {
        if (mediaType != null) {
            Charset charset = mediaType.getCharset();
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
        BodyWithContentType<?> that = (BodyWithContentType<?>) o;
        return Objects.equals(contentType, that.contentType);
    }

    @Override
    public int hashCode() {
        if (hashCode == 0) {
            hashCode = Objects.hash(super.hashCode(), contentType);
        }
        return hashCode;
    }
}
