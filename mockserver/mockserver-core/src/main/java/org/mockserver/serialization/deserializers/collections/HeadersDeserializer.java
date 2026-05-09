package org.mockserver.serialization.deserializers.collections;

import org.mockserver.model.Headers;

/**
 * @author jamesdbloom
 */
public class HeadersDeserializer extends KeysToMultiValuesDeserializer<Headers> {

    private static final long serialVersionUID = 1L;

    public HeadersDeserializer() {
        super(Headers.class);
    }

    @Override
    public Headers build() {
        return new Headers();
    }
}
