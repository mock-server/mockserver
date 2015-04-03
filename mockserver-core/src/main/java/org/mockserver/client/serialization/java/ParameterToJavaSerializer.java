package org.mockserver.client.serialization.java;

import com.google.common.base.Strings;
import org.mockserver.model.Parameter;

import java.util.Arrays;
import java.util.List;

/**
 * @author jamesdbloom
 */
public class ParameterToJavaSerializer implements MultiValueToJavaSerializer<Parameter> {
    @Override
    public String serializeAsJava(int numberOfSpacesToIndent, Parameter parameter) {
        StringBuilder output = new StringBuilder();
        output.append(System.getProperty("line.separator")).append(Strings.padStart("", numberOfSpacesToIndent, ' '));
        output.append("new Parameter(\"").append(parameter.getName()).append("\"");
        for (String value : parameter.getValues()) {
            output.append(", \"").append(value).append("\"");
        }
        output.append(")");
        return output.toString();
    }

    @Override
    public String serializeAsJava(int numberOfSpacesToIndent, List<Parameter> parameters) {
        StringBuilder output = new StringBuilder();
        for (int i = 0; i < parameters.size(); i++) {
            output.append(serializeAsJava(numberOfSpacesToIndent, parameters.get(i)));
            if (i < (parameters.size() - 1)) {
                output.append(",");
            }
        }
        return output.toString();
    }

    @Override
    public String serializeAsJava(int numberOfSpacesToIndent, Parameter... object) {
        return serializeAsJava(numberOfSpacesToIndent, Arrays.asList(object));
    }
}
