package org.mockserver.model;

import java.util.List;

public class HeadersModifier extends KeysToMultiValuesModifier<Headers, HeadersModifier, Header> {

    /**
     * Static builder to create a headers modifier.
     */
    public static HeadersModifier headersModifier() {
        return new HeadersModifier();
    }

    @Override
    Headers construct(List<Header> headers) {
        return new Headers(headers);
    }

    @Override
    Headers construct(Header... headers) {
        return new Headers(headers);
    }

}
