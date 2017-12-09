package org.mockserver.client.serialization.deserializers.collections;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.JsonTokenId;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.mockserver.model.KeysToMultiValues;
import org.mockserver.model.NottableString;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.mockserver.model.NottableString.*;

/**
 * @author jamesdbloom
 */
public abstract class KeysToMultiValuesDeserializer<T extends KeysToMultiValues> extends StdDeserializer<T> {

    KeysToMultiValuesDeserializer(Class<T> valueClass) {
        super(valueClass);
    }

    public abstract T build();

    @Override
    public T deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        if (p.isExpectedStartArrayToken()) {
            return deserializeArray(p, ctxt, ctxt.getNodeFactory());
        } else if (p.isExpectedStartObjectToken()) {
            return deserializeObject(p, ctxt, ctxt.getNodeFactory());
        } else {
            return (T) ctxt.handleUnexpectedToken(_valueClass, p);
        }
    }

    private T deserializeObject(JsonParser p, DeserializationContext ctxt, JsonNodeFactory nodeFactory) throws IOException {
        T headers = build();
        NottableString key = string("");
        while (true) {
            JsonToken t = p.nextToken();
            switch (t.id()) {
                case JsonTokenId.ID_FIELD_NAME:
                    key = deserializeNottableString(ctxt.readValue(p, String.class));
                    break;
                case JsonTokenId.ID_START_ARRAY:
                    headers.withEntry(key, deserializeNottableStrings(ctxt.readValue(p, List.class)));
                    break;
                case JsonTokenId.ID_END_OBJECT:
                    return headers;
                default:
                    throw new RuntimeException("Unexpected token: \"" + t + "\" id: \"" + t.id() + "\" text: \"" + p.getText());
            }
        }
    }

    private T deserializeArray(JsonParser p, DeserializationContext ctxt, JsonNodeFactory nodeFactory) throws IOException {
        T headers = build();
        NottableString key = string("");
        List<NottableString> values = new ArrayList<>();
        while (true) {
            JsonToken t = p.nextToken();
            switch (t.id()) {
                case JsonTokenId.ID_END_ARRAY:
                    return headers;
                case JsonTokenId.ID_START_OBJECT:
                    key = null;
                    values = new ArrayList<>();
                    break;
                case JsonTokenId.ID_FIELD_NAME:
                    break;
                case JsonTokenId.ID_STRING:
                    key = deserializeNottableString(ctxt.readValue(p, String.class));
                    break;
                case JsonTokenId.ID_START_ARRAY:
                    values = deserializeNottableStrings(ctxt.readValue(p, List.class));
                    break;
                case JsonTokenId.ID_END_OBJECT:
                    headers.withEntry(key, values);
                    break;
                default:
                    throw new RuntimeException("Unexpected token: \"" + t + "\" id: \"" + t.id() + "\" text: \"" + p.getText());
            }
        }
    }
}
