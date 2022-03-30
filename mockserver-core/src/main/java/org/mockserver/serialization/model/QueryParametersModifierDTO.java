package org.mockserver.serialization.model;

import org.mockserver.model.Parameter;
import org.mockserver.model.Parameters;
import org.mockserver.model.QueryParametersModifier;

public class QueryParametersModifierDTO extends KeysToMultiValuesModifierDTO<Parameters, QueryParametersModifier, Parameter, QueryParametersModifierDTO> {

    public QueryParametersModifierDTO() {
    }

    public QueryParametersModifierDTO(QueryParametersModifier queryParameterModifier) {
        super(queryParameterModifier);
    }

    @Override
    QueryParametersModifier newKeysToMultiValuesModifier() {
        return new QueryParametersModifier();
    }
}
