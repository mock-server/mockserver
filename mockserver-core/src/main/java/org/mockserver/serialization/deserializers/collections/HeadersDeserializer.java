package org.mockserver.serialization.deserializers.collections;

import org.mockserver.model.Headers;

/**
 * @author jamesdbloom
 */
public class HeadersDeserializer extends KeysToMultiValuesDeserializer<Headers> {

    public HeadersDeserializer() {
        super(Headers.class);
    }

    @Override
    public Headers build() {
        return new Headers();
    }
}
