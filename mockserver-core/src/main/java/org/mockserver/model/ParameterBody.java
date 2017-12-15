package org.mockserver.model;

import com.google.common.base.Charsets;
import com.google.common.net.MediaType;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.List;

/**
 * @author jamesdbloom
 */
public class ParameterBody extends BodyWithContentType<List<Parameter>> {

    public static final MediaType DEFAULT_CONTENT_TYPE = MediaType.FORM_DATA;
    private Parameters parameters = new Parameters();

    public ParameterBody(Parameter... parameters) {
        this(new Parameters().withEntries(parameters));
    }

    public ParameterBody(List<Parameter> parameters) {
        this(new Parameters().withEntries(parameters));
    }

    public ParameterBody(Parameters parameters) {
        super(Type.PARAMETERS, DEFAULT_CONTENT_TYPE);
        if (parameters != null) {
            this.parameters = parameters;
        }
    }

    public static ParameterBody params(Parameter... parameters) {
        return new ParameterBody(parameters);
    }

    public static ParameterBody params(List<Parameter> parameters) {
        return new ParameterBody(parameters);
    }

    public Parameters getValue() {
        return this.parameters;
    }

    @Override
    public String toString() {
        StringBuilder body = new StringBuilder();
        List<Parameter> bodyParameters = this.parameters.getEntries();
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
