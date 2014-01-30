package org.mockserver.model;

import com.google.common.base.Charsets;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.Arrays;
import java.util.List;

/**
 * @author jamesdbloom
 */
public class ParameterBody extends Body {

    private final List<Parameter> parameters;

    public ParameterBody(Parameter... parameters) {
        this(Arrays.asList(parameters));
    }

    public ParameterBody(List<Parameter> parameters) {
        super(Type.PARAMETERS);
        this.parameters = parameters;
    }

    public List<Parameter> getParameters() {
        return parameters;
    }

    @Override
    public String toString() {
        StringBuilder body = new StringBuilder();
        List<Parameter> bodyParameters = parameters;
        for (int i = 0; i < bodyParameters.size(); i++) {
            Parameter parameter = bodyParameters.get(i);
            if (parameter.getValues().isEmpty()) {
                body.append(parameter.getName());
                body.append('=');
            } else {
                List<String> values = parameter.getValues();
                for (int j = 0; j < values.size(); j++) {
                    String value = values.get(j);
                    body.append(parameter.getName());
                    body.append('=');
                    try {
                        body.append(URLEncoder.encode(value, Charsets.UTF_8.name()));
                    } catch (UnsupportedEncodingException uee) {
                        throw new RuntimeException("UnsupportedEncodingException while encoding body parameters for " + parameters, uee);
                    }
                    if (j < (values.size() - 1)) {
                        body.append('&');
                    }
                }
            }
            if (i < (bodyParameters.size() - 1)) {
                body.append('&');
            }
        }
        return body.toString();
    }
}
