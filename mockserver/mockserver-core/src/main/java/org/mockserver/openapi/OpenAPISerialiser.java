package org.mockserver.openapi;

import com.fasterxml.jackson.databind.ObjectWriter;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.servers.ServerVariables;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.OpenAPIDefinition;
import org.mockserver.openapi.examples.JsonNodeExampleSerializer;
import org.mockserver.serialization.ObjectMapperFactory;
import org.mockserver.url.URLParser;

import java.util.*;
import java.util.stream.Collectors;

import static org.apache.commons.lang3.StringUtils.*;
import static org.mockserver.openapi.OpenAPIParser.buildOpenAPI;
import static org.mockserver.openapi.OpenAPIParser.mapOperations;
import static org.slf4j.event.Level.ERROR;

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

    private static String serverUrlWithVariablesExpanded(Server server) {
        String serverUrl = "";
        if (server != null && server.getUrl() != null) {
            serverUrl = server.getUrl();
            if (server.getVariables() != null) {
                ServerVariables variables = server.getVariables();
                for (String variableName : variables.keySet()) {
                    serverUrl = serverUrl.replaceAll("\\{" + variableName + "}", defaultString(variables.get(variableName).getDefault()));
                }
            }
        }
        return serverUrl;
    }

    @SafeVarargs
    private final String firstValidServerPath(List<Server>... serverLists) {
        for (List<Server> serverList : serverLists) {
            if (serverList != null && serverList.size() > 0) {
                String returnPath = URLParser.returnPath(serverUrlWithVariablesExpanded(serverList.get(0)));
                if (isNotBlank(returnPath)) {
                    return StringUtils.removeEnd(StringUtils.prependIfMissing(returnPath, "/"), "/");
                }
            }
        }
        return "";
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
            openAPI
                .getPaths()
                .forEach((pathString, pathObject) -> {
                    if (pathString != null && pathObject != null) {
                        List<Pair<String, Operation>> filteredOperations = mapOperations(pathObject)
                            .stream()
                            .filter(operation -> isBlank(operationId) || operationId.equals(operation.getRight().getOperationId()))
                            .sorted(Comparator.comparing(Pair::getLeft))
                            .collect(Collectors.toList());
                        if (!filteredOperations.isEmpty()) {
                            // add server path prefix to each operation path
                            filteredOperations.forEach((methodOperationPair) -> {
                                String pathWithServerPrefixAdded = firstValidServerPath(methodOperationPair.getValue().getServers(), pathObject.getServers(), openAPI.getServers()) + pathString;
                                operations.computeIfAbsent(pathWithServerPrefixAdded, k -> new ArrayList<>()).add(methodOperationPair);
                            });
                        }
                    }
                });
        }
        return operations;
    }

}
