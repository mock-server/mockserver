package org.mockserver.client.serialization.serializers.collections;

import org.mockserver.model.Headers;

/**
 * @author jamesdbloom
 */
public class HeadersSerializer extends KeysToMultiValuesSerializer<Headers> {

    public HeadersSerializer() {
        super(Headers.class);
    }

}
