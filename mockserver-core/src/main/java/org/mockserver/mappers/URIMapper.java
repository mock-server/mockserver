package org.mockserver.mappers;

import org.mockserver.model.HttpRequest;
import org.mockserver.model.Parameter;

import java.util.List;

/**
 * @author jamesdbloom
 */
public class URIMapper {

    public static String getURI(HttpRequest httpRequest) {
        StringBuilder queryString = new StringBuilder();
        List<Parameter> queryStringParameters = httpRequest.getQueryStringParameters();
        for (int i = 0; i < queryStringParameters.size(); i++) {
            Parameter parameter = queryStringParameters.get(i);
            if (parameter.getValues().isEmpty()) {
                queryString.append(parameter.getName());
                queryString.append('=');
            } else {
                List<String> values = parameter.getValues();
                for (int j = 0; j < values.size(); j++) {
                    String value = values.get(j);
                    queryString.append(parameter.getName());
                    queryString.append('=');
                    queryString.append(value);
                    if (j < (values.size() - 1)) {
                        queryString.append('&');
                    }
                }
            }
            if (i < (queryStringParameters.size() - 1)) {
                queryString.append('&');
            }
        }
        return httpRequest.getPath() + queryString.toString();
    }
}
