package org.mockserver.model;

import java.util.Collection;
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
    public Header build(NottableString name, Collection<NottableString> values) {
        return new Header(name, values);
    }

    @SuppressWarnings("MethodDoesntCallSuperMethod")
    public Headers clone() {
        return new Headers().withEntries(getEntries());
    }

}
