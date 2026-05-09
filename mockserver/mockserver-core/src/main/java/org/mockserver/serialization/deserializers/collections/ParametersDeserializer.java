package org.mockserver.serialization.deserializers.collections;

import org.mockserver.model.Parameters;

/**
 * @author jamesdbloom
 */
public class ParametersDeserializer extends KeysToMultiValuesDeserializer<Parameters> {

    private static final long serialVersionUID = 1L;

    public ParametersDeserializer() {
        super(Parameters.class);
    }

    @Override
    public Parameters build() {
        return new Parameters();
    }
}
