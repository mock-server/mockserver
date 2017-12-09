package org.mockserver.model;

import java.util.List;

/**
 * @author jamesdbloom
 */
public class Headers extends KeysToMultiValues<Header, Headers> {

    @Override
    public Header build(NottableString name, List<NottableString> values) {
        return new Header(name, values);
    }

}
