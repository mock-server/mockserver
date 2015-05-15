package org.mockserver.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.mockserver.matchers.MatchType;

import java.nio.charset.Charset;

import static org.mockserver.mappers.ContentTypeMapper.DEFAULT_HTTP_CHARACTER_SET;

/**
 * @author jamesdbloom
 */
public class JsonBody extends Body {

    public static final MatchType DEFAULT_MATCH_TYPE = MatchType.ONLY_MATCHING_FIELDS;
    private final String json;
    private final byte[] rawBinaryData;
    private final MatchType matchType;
    private final Charset charset;

    public JsonBody(String json) {
        this(json, DEFAULT_MATCH_TYPE);
    }

    public JsonBody(String json, MatchType matchType) {
        this(json, null, matchType);
    }

    public JsonBody(String json, Charset charset, MatchType matchType) {
        super(Type.JSON);
        this.json = json;
        this.matchType = matchType;
        this.charset = charset;

        if (json != null) {
            this.rawBinaryData = json.getBytes(charset != null ? charset : DEFAULT_HTTP_CHARACTER_SET);
        } else {
            this.rawBinaryData = new byte[0];
        }
    }

    public static JsonBody json(String json) {
        return new JsonBody(json);
    }

    public static JsonBody json(String json, MatchType matchType) {
        return new JsonBody(json, matchType);
    }

    public static JsonBody json(String json, Charset charset) {
        return new JsonBody(json, charset, DEFAULT_MATCH_TYPE);
    }

    public static JsonBody json(String json, Charset charset, MatchType matchType) {
        return new JsonBody(json, charset, matchType);
    }

    public String getValue() {
        return json;
    }

    public byte[] getRawBytes() {
        return rawBinaryData;
    }

    public MatchType getMatchType() {
        return matchType;
    }

    public Charset getCharset() {
        return charset;
    }

    @JsonIgnore
    public Charset getCharset(Charset defaultIfNotSet) {
        return charset != null ? charset : defaultIfNotSet;
    }

    @JsonIgnore
    public String getContentType() {
        return "application/json";
    }

}
