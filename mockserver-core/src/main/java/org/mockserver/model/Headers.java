package org.mockserver.model;

import java.util.List;

/**
 * @author jamesdbloom
 */
public class Headers extends KeysToMultiValues<Header, Headers> {

    public Headers(List<Header> headers) {
        withEntries(headers);
    }

    public Headers(Header... headers) {
        withEntries(headers);
    }

    @Override
    public Header build(NottableString name, List<NottableString> values) {
        return new Header(name, values);
    }

    public Headers clone() {
        return new Headers().withEntries(getEntries());
    }
}
