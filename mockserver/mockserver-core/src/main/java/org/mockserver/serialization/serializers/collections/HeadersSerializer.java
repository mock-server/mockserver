package org.mockserver.serialization.serializers.collections;

import org.mockserver.model.Headers;

/**
 * @author jamesdbloom
 */
public class HeadersSerializer extends KeysToMultiValuesSerializer<Headers> {

    private static final long serialVersionUID = 1L;

    public HeadersSerializer() {
        super(Headers.class);
    }

}
