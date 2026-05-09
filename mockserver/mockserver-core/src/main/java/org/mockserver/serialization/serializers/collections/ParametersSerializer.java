package org.mockserver.serialization.serializers.collections;

import org.mockserver.model.Parameters;

/**
 * @author jamesdbloom
 */
public class ParametersSerializer extends KeysToMultiValuesSerializer<Parameters> {

    private static final long serialVersionUID = 1L;

    public ParametersSerializer() {
        super(Parameters.class);
    }

}
