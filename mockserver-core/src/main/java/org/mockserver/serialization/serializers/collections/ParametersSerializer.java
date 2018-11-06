package org.mockserver.serialization.serializers.collections;

import org.mockserver.model.Parameters;

/**
 * @author jamesdbloom
 */
public class ParametersSerializer extends KeysToMultiValuesSerializer<Parameters> {

    public ParametersSerializer() {
        super(Parameters.class);
    }

}
