package org.mockserver.model;

import com.google.common.base.Charsets;
import com.google.common.net.MediaType;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.Arrays;
import java.util.List;

/**
 * @author jamesdbloom
 */
public class ParameterBody extends Body<List<Parameter>> {

    public static final MediaType DEFAULT_CONTENT_TYPE = MediaType.FORM_DATA;
    private final List<Parameter> parameters;

    public ParameterBody(Parameter... parameters) {
        this(Arrays.asList(parameters));
    }

    public ParameterBody(List<Parameter> parameters) {
        super(Type.PARAMETERS, DEFAULT_CONTENT_TYPE);
        this.parameters = parameters;
    }

    public static ParameterBody params(Parameter... parameters) {
        return new ParameterBody(parameters);
    }

    public static ParameterBody params(List<Parameter> parameters) {
        return new ParameterBody(parameters);
    }

    public List<Parameter> getValue() {
        return parameters;
    }

    @Override
    public String toString() {
        StringBuilder body = new StringBuilder();
        List<Parameter> bodyParameters = parameters;
        for (int i = 0; i < bodyParameters.size(); i++) {
            Parameter parameter = bodyParameters.get(i);
            if (parameter.getValues().isEmpty()) {
                body.append(parameter.getName().getValue());
                body.append('=');
            } else {
                List<NottableString> values = parameter.getValues();
                for (int j = 0; j < values.size(); j++) {
                    String value = values.get(j).getValue();
                    body.append(parameter.getName().getValue());
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
