package org.mockserver.openapi;

import com.fasterxml.jackson.databind.ObjectWriter;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.servers.ServerVariables;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.OpenAPIDefinition;
import org.mockserver.openapi.examples.JsonNodeExampleSerializer;
import org.mockserver.serialization.ObjectMapperFactory;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.mockserver.openapi.OpenAPIParser.buildOpenAPI;
import static org.mockserver.openapi.OpenAPIParser.mapOperations;
import static org.slf4j.event.Level.ERROR;
import static org.slf4j.event.Level.WARN;

public class OpenAPISerialiser {

    private static final ObjectWriter OBJECT_WRITER = ObjectMapperFactory.createObjectMapper(new JsonNodeExampleSerializer()).writerWithDefaultPrettyPrinter();
    private final MockServerLogger mockServerLogger;

    public OpenAPISerialiser(MockServerLogger mockServerLogger) {
        this.mockServerLogger = mockServerLogger;
    }

    public String asString(OpenAPIDefinition openAPIDefinition) {
        try {
            if (isBlank(openAPIDefinition.getOperationId())) {
                return OBJECT_WRITER.writeValueAsString(buildOpenAPI(openAPIDefinition.getSpecUrlOrPayload(), mockServerLogger));
            } else {
                Optional<Pair<String, Operation>> operation = retrieveOperation(openAPIDefinition.getSpecUrlOrPayload(), openAPIDefinition.getOperationId());
                if (operation.isPresent()) {
                    return operation.get().getLeft() + ": " + OBJECT_WRITER.writeValueAsString(operation.get().getRight());
                }
            }
        } catch (Throwable throwable) {
            mockServerLogger.logEvent(
                new LogEntry()
                    .setLogLevel(ERROR)
                    .setMessageFormat("exception while serialising specification for OpenAPIDefinition{}")
                    .setArguments(openAPIDefinition)
                    .setThrowable(throwable)
            );
        }
        return "";
    }

    /**
     * This class splits URL strings into 6 components. It was tested to work with the following URLs:
     * <li> "https://www.google.com:8080/search?q=full+url+parsing+regex&ei=CmrKYJvbI5L7-gTkrLqoDQ&oq=full+url+parsing+regex&gs_lcp=Cgdnd3Mtd2l6EAMyBQgAEM0CMgUIABDNAjoECAAQDToGCAAQBxAeOgIIADoGCAAQCBAeOgQIIRAKUNGtCFij8whg-I0JaABwAngAgAHbAYgB1AqSAQYxMS4yLjGYAQCgAQGqAQdnd3Mtd2l6wAEB&sclient=gws-wiz&ved=0ahUKEwibiMDEiZ3xAhWSvZ4KHWSWDtUQ4dUDCBE&uact=5" </li>
     * <li> "developers.procore.com/rest/v1.0/" </li>
     * <li> "/" </li>
     * <li> "file:///Users/userfolder/git/test-tools/demotests/client_ops/full_test_case_definition_file.json" </li>
     * <li> "127.0.0.1:2433/" </li>
     * <li> "https://docs.google.com/document/d/documentidstring/edit#heading=h.d68efjb5c7zp" </li>
     * <li> "" </li>
     */
    private static class UrlSplitter {
        public final String scheme, host, port, path, query, fragment;
        public UrlSplitter(String scheme, String host, String port, String path, String query, String fragment) {
            this.scheme = scheme == null ? "" : scheme;
            this.host = host == null ? "" : host;
            this.port = port == null ? "" : port;
            this.path = path == null ? "" : path;
            this.query = query == null ? "" : query;
            this.fragment = fragment == null ? "" : fragment;
        }

        /**
         * RegEx borrowed from <a href="https://stackoverflow.com/questions/27745/getting-parts-of-a-url-regex">stackoverflow.com/questions/27745</a>
         */
        static final String URL_SPLIT_REGEX =
            "^(([^:/?#]+):(?=//))?(//)?((([^:]+)(?::([^@]+)?)?@)?([^@/?#:]*)(?::(\\d+)?)?)?([^?#]*)(\\?([^#]*))?(#(.*))?";
        static final Pattern URL_SPLIT_PATTERN = Pattern.compile(URL_SPLIT_REGEX);
        public static UrlSplitter split(String url) {
            Matcher m = URL_SPLIT_PATTERN.matcher((url == null) ? "" : url);
            m.find();
            return new UrlSplitter(m.group(2), m.group(8), m.group(9), m.group(10), m.group(12), m.group(13));
        }
    }

    /**
     * Tool function that produces a URL string for a {@link Server} object by substituting every variable placeholder
     * with the variables' default values provided in the specification OR empty string if not defined.
     * @param server a {@link Server} object from the OAS3 object model
     * @return the URL string
     */
    private static String getDefaultUrl(Server server) {
        String serverUrl = null;
        ServerVariables vars;
        if (server != null && (serverUrl = server.getUrl()) != null && (vars = server.getVariables()) != null) {
            for (String varName : vars.keySet()) {
                String varValue = vars.get(varName).getDefault();
                serverUrl = serverUrl.replaceAll("\\{" + varName + "}", (varValue == null ? "" : varValue));
            }
        }
        return serverUrl;
    }

    /**
     * Tool function that extracts the path from the URL of the first entry of the provided server objects list
     * @param serverList list of {@link Server} objects
     * @return the path string from the first server object
     */
    private String getServerPath(List<Server> serverList) {
        if (serverList != null && serverList.size() > 0) {
            try {
                return UrlSplitter.split(getDefaultUrl(serverList.get(0))).path;
            } catch (Exception e) {
                mockServerLogger.logEvent(
                    new LogEntry().setLogLevel(WARN)
                        .setMessageFormat("Failed extracting the server path from \"servers\" section {}\n  DUE TO: {}")
                        .setArguments(serverList, e)
                );
            }
        }
        return "";
    }

    @SafeVarargs
    private final String getPriorityServerPath(List<Server>... serverLists) {
        String prefixPath = "";
        for (List<Server> serverList : serverLists) {
            // Traversing from inner to outer "servers" sections - pick the first
            // that is defined and has at least one item
            if (serverList != null && serverList.size() > 0) {
                prefixPath = getServerPath(serverList);
                break;
            }
        }
        if (!prefixPath.startsWith("/")) {
            prefixPath = "/" + prefixPath;
        }
        if (prefixPath.endsWith("/")) {
            prefixPath = prefixPath.substring(0, prefixPath.length() - 1);
        }
        return prefixPath;
    }

    public Optional<Pair<String, Operation>> retrieveOperation(String specUrlOrPayload, String operationId) {
        return buildOpenAPI(specUrlOrPayload, mockServerLogger)
            .getPaths()
            .values()
            .stream()
            .flatMap(pathItem -> mapOperations(pathItem).stream())
            .filter(operation -> isBlank(operationId) || operation.getRight().getOperationId().equals(operationId))
            .findFirst();
    }

    public Map<String, List<Pair<String, Operation>>> retrieveOperations(OpenAPI openAPI, String operationId) {
        Map<String, List<Pair<String, Operation>>> operations = new LinkedHashMap<>();
        if (openAPI != null) {
            List<Server> specLevelServers = openAPI.getServers();
            openAPI
                .getPaths()
                .forEach((pathString, pathObject) -> {
                    if (pathString != null && pathObject != null) {
                        List<Server> pathLevelServers = pathObject.getServers();
                        List<Pair<String, Operation>> filteredOperations = mapOperations(pathObject)
                            .stream()
                            .filter(operation -> isBlank(operationId) || operationId.equals(operation.getRight().getOperationId()))
                            .sorted(Comparator.comparing(Pair::getLeft))
                            .collect(Collectors.toList());
                        if (!filteredOperations.isEmpty()) {
                            filteredOperations.forEach((opPair) -> {
                                List<Server> opLevelServers = opPair.getValue().getServers();
                                String serverPath = getPriorityServerPath(opLevelServers, pathLevelServers, specLevelServers);
                                String fullPathString = serverPath + pathString;
                                List<Pair<String, Operation>> pathList =
                                    operations.computeIfAbsent(fullPathString, k -> new ArrayList<>());
                                pathList.add(opPair);
                            });
                        }
                    }
                });
        }
        return operations;
    }

}
