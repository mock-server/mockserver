package org.mockserver.client.serialization.deserializers.collections;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.mockserver.model.KeysToMultiValues;
import org.mockserver.model.NottableString;

import java.io.IOException;

import static org.mockserver.model.NottableString.deserializeNottableStrings;
import static org.mockserver.model.NottableString.string;

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
            return null;
        }
    }

    private T deserializeObject(JsonParser jsonParser, DeserializationContext ctxt, JsonNodeFactory nodeFactory) throws IOException {
        T enteries = build();
        NottableString key = string("");
        while (true) {
            JsonToken token = jsonParser.nextToken();
            switch (token) {
                case FIELD_NAME:
                    key = string(jsonParser.getText());
                    break;
                case START_ARRAY:
                    enteries.withEntry(key, ctxt.readValue(jsonParser, NottableString[].class));
                    break;
                case END_OBJECT:
                    return enteries;
                default:
                    throw new RuntimeException("Unexpected token: \"" + token + "\" id: \"" + token.id() + "\" text: \"" + jsonParser.getText());
            }
        }
    }

    private T deserializeArray(JsonParser jsonParser, DeserializationContext ctxt, JsonNodeFactory nodeFactory) throws IOException {
        T entries = build();
        NottableString key = string("");
        NottableString[] values = null;
        while (true) {
            JsonToken token = jsonParser.nextToken();
            switch (token) {
                case START_ARRAY:
                    values = ctxt.readValue(jsonParser, NottableString[].class);
                    break;
                case END_ARRAY:
                    return entries;
                case START_OBJECT:
                    if (key != null) {
                        key = null;
                    } else {
                        key = ctxt.readValue(jsonParser, NottableString.class);
                    }
                    values = null;
                    break;
                case END_OBJECT:
                    entries.withEntry(key, values);
                    break;
                case FIELD_NAME:
                    break;
                case VALUE_STRING:
                    key = ctxt.readValue(jsonParser, NottableString.class);
                    break;
                default:
                    throw new RuntimeException("Unexpected token: \"" + token + "\" id: \"" + token.id() + "\" text: \"" + jsonParser.getText());
            }
        }
    }
}
