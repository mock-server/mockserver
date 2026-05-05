package org.mockserver.serialization.deserializers.collections;

import org.mockserver.model.Parameters;

/**
 * @author jamesdbloom
 */
public class ParametersDeserializer extends KeysToMultiValuesDeserializer<Parameters> {

    public ParametersDeserializer() {
        super(Parameters.class);
    }

    @Override
    public Parameters build() {
        return new Parameters();
    }
}
