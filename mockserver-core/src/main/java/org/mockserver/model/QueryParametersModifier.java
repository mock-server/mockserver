package org.mockserver.model;

import java.util.List;

public class QueryParametersModifier extends KeysToMultiValuesModifier<Parameters, QueryParametersModifier, Parameter> {

    /**
     * Static builder to create a query parameters modifier.
     */
    public static QueryParametersModifier queryParametersModifier() {
        return new QueryParametersModifier();
    }

    @Override
    Parameters construct(List<Parameter> parameters) {
        return new Parameters(parameters);
    }

    @Override
    Parameters construct(Parameter... parameters) {
        return new Parameters(parameters);
    }

}