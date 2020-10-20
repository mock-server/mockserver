package org.mockserver.model;

import com.google.common.collect.Multimap;

import java.util.Collection;
import java.util.List;

/**
 * @author jamesdbloom
 */
public class Parameters extends KeysToMultiValues<Parameter, Parameters> {

    public Parameters(List<Parameter> parameters) {
        withEntries(parameters);
    }

    public Parameters(Parameter... parameters) {
        withEntries(parameters);
    }

    public Parameters(Multimap<NottableString, NottableString> headers) {
        super(headers);
    }

    @Override
    public Parameter build(NottableString name, Collection<NottableString> values) {
        return new Parameter(name, values);
    }

    public Parameters withKeyMatchStyle(KeyMatchStyle keyMatchStyle) {
        super.withKeyMatchStyle(keyMatchStyle);
        return this;
    }

    @SuppressWarnings("MethodDoesntCallSuperMethod")
    public Parameters clone() {
        return new Parameters(getMultimap());
    }

}
