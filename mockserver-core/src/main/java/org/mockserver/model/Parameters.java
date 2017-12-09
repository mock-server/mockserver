package org.mockserver.model;

import java.util.List;

/**
 * @author jamesdbloom
 */
public class Parameters extends KeysToMultiValues<Parameter, Parameters> {

    @Override
    public Parameter build(NottableString name, List<NottableString> values) {
        return new Parameter(name, values);
    }

}
