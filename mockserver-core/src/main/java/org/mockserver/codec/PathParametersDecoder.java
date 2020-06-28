package org.mockserver.codec;

import org.mockserver.model.HttpRequest;
import org.mockserver.model.NottableString;
import org.mockserver.model.Parameters;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.mockserver.model.NottableString.string;

public class PathParametersDecoder {

    private static final Pattern PATH_VARIABLE_NAME_PATTERN = Pattern.compile("\\{[.;]?([^}*]+)\\*?}");

    public String validatePath(HttpRequest matcher) {
        String error = "";
        if (matcher.getPath() != null) {
            if (matcher.getPathParameters() != null && !matcher.getPathParameters().isEmpty()) {
                List<String> actualParameterNames = new ArrayList<>();
                for (String matcherPathPart : matcher.getPath().getValue().split("/")) {
                    Matcher pathParameterName = PATH_VARIABLE_NAME_PATTERN.matcher(matcherPathPart);
                    if (pathParameterName.matches()) {
                        actualParameterNames.add(pathParameterName.group(1));
                    }
                }
                List<String> expectedParameterNames = matcher.getPathParameters().keySet().stream().map(NottableString::getValue).collect(Collectors.toList());
                if (!expectedParameterNames.equals(actualParameterNames)) {
                    error = "path parameters specified " + expectedParameterNames + " but found " + actualParameterNames + " in path matcher";
                }
            }
        }
        // Unable to load API spec from provided URL or payload attribute paths.'/pets/{petId}'. Declared path parameter petId needs to be defined as a path parameter in path or operation level
        return error;
    }

    public NottableString normalisePathWithParametersForMatching(HttpRequest matcher) {
        NottableString result = null;
        if (matcher.getPath() != null) {
            if (matcher.getPathParameters() != null && !matcher.getPathParameters().isEmpty()) {
                String value = matcher.getPath().getValue();
                if (value.contains("{")) {
                    value = PATH_VARIABLE_NAME_PATTERN.matcher(value).replaceAll(".*");
                    result = string(value);
                } else {
                    result = matcher.getPath();
                }
            } else {
                result = matcher.getPath();
            }
        }
        // Unable to load API spec from provided URL or payload attribute paths.'/pets/{petId}'. Declared path parameter petId needs to be defined as a path parameter in path or operation level
        return result;
    }

    public Parameters retrievePathParameters(HttpRequest matcher, HttpRequest matched) {
        Parameters parsedParameters = matched.getPathParameters() != null ? matched.getPathParameters() : new Parameters();
        if (matcher.getPath() != null && matched.getPath() != null) {
            if (matcher.getPathParameters() != null && !matcher.getPathParameters().isEmpty()) {
                String[] matcherPathParts = matcher.getPath().getValue().split("/");
                String[] matchedPathParts = matched.getPath().getValue().split("/");
                if (matcherPathParts.length != matchedPathParts.length) {
                    throw new IllegalArgumentException("matcher path " + matcher.getPath().getValue() + " has " + matcherPathParts.length + " parts but matched path " + matched.getPath().getValue() + " has " + matchedPathParts.length + " parts ");
                }
                for (int i = 0; i < matcherPathParts.length; i++) {
                    Matcher pathParameterName = PATH_VARIABLE_NAME_PATTERN.matcher(matcherPathParts[i]);
                    if (pathParameterName.matches()) {
                        String parameterName = pathParameterName.group(1);
                        List<String> parameterValues = new ArrayList<>();
                        Matcher pathParameterValue = Pattern.compile("[.;]?(?:" + parameterName + "=)?([^,]++)[.,;]?").matcher(matchedPathParts[i]);
                        while (pathParameterValue.find()) {
                            parameterValues.add(pathParameterValue.group(1));
                        }
                        parsedParameters.withEntry(parameterName, parameterValues);
                    }
                }
            }
        }
        // ensure actions have path parameters available to them
        matched.withPathParameters(parsedParameters);
        return parsedParameters;
    }
}
