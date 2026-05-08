package org.mockserver.openapi;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.examples.Example;
import io.swagger.v3.oas.models.headers.Header;
import io.swagger.v3.oas.models.media.ComposedSchema;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponses;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.mock.Expectation;
import org.mockserver.model.HttpResponse;
import org.mockserver.openapi.examples.ExampleBuilder;
import org.mockserver.openapi.examples.JsonNodeExampleSerializer;
import org.mockserver.openapi.examples.models.StringExample;
import org.mockserver.serialization.ObjectMapperFactory;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.JsonBody.json;
import static org.mockserver.model.OpenAPIDefinition.openAPI;
import static org.mockserver.openapi.OpenAPIParser.buildOpenAPI;
import static org.slf4j.event.Level.ERROR;
import static org.slf4j.event.Level.WARN;

public class OpenAPIConverter {

    private static final ObjectWriter OBJECT_WRITER = ObjectMapperFactory.createObjectMapper(new JsonNodeExampleSerializer()).writerWithDefaultPrettyPrinter();
    private static final int MAX_REF_DEPTH = 100;
    private static final int MAX_STRUCTURE_DEPTH = 1000;
    private final MockServerLogger mockServerLogger;

    public OpenAPIConverter(MockServerLogger mockServerLogger) {
        this.mockServerLogger = mockServerLogger;
    }

    public List<Expectation> buildExpectations(String specUrlOrPayload, Map<String, String> operationsAndResponses) {
        OpenAPI openAPI = buildOpenAPI(specUrlOrPayload, mockServerLogger);
        AtomicInteger expectationCounter = new AtomicInteger(0);
        return openAPI
            .getPaths()
            .values()
            .stream()
            .flatMap(pathItem ->
                pathItem
                    .readOperations()
                    .stream()
            )
            .filter(operation -> operationsAndResponses == null || operationsAndResponses.containsKey(operation.getOperationId()))
            .map(operation -> new Expectation(openAPI(specUrlOrPayload, operation.getOperationId()))
                .thenRespond(buildHttpResponse(
                    openAPI,
                    operation.getResponses(),
                    operationsAndResponses != null ? operationsAndResponses.get(operation.getOperationId()) : null
                ))
            )
            .map(expectation -> {
                int index = expectationCounter.incrementAndGet();
                return expectation.withId(new UUID((long) Objects.hash(specUrlOrPayload, operationsAndResponses) * index, (long) Objects.hash(specUrlOrPayload, operationsAndResponses) * index).toString());
            })
            .collect(Collectors.toList());
    }

    private HttpResponse buildHttpResponse(OpenAPI openAPI, ApiResponses apiResponses, String apiResponseKey) {
        HttpResponse response = response();
        Optional
            .ofNullable(apiResponses)
            .flatMap(notNullApiResponses -> notNullApiResponses.entrySet().stream().filter(entry -> isBlank(apiResponseKey) | entry.getKey().equals(apiResponseKey)).findFirst())
            .ifPresent(apiResponse -> {
                if (!apiResponse.getKey().equalsIgnoreCase("default")) {
                    response.withStatusCode(Integer.parseInt(apiResponse.getKey()));
                }
                Optional
                    .ofNullable(apiResponse.getValue().getHeaders())
                    .map(Map::entrySet)
                    .map(Set::stream)
                    .ifPresent(stream -> stream
                        .forEach(entry -> {
                            Header value = entry.getValue();
                            Object headerExample = findHeaderExample(value, openAPI);
                            if (headerExample != null) {
                                response.withHeader(entry.getKey(), String.valueOf(headerExample));
                            } else if (value.getSchema() != null) {
                                org.mockserver.openapi.examples.models.Example generatedExample = ExampleBuilder.fromSchema(value.getSchema(), openAPI.getComponents() != null ? openAPI.getComponents().getSchemas() : null);
                                if (generatedExample instanceof StringExample) {
                                    response.withHeader(entry.getKey(), ((StringExample) generatedExample).getValue());
                                } else {
                                    response.withHeader(entry.getKey(), serialise(generatedExample));
                                }
                            }
                        })
                    );
                Optional
                    .ofNullable(apiResponse.getValue().getContent())
                    .flatMap(content -> content
                        .entrySet()
                        .stream()
                        .findFirst()
                    )
                    .ifPresent(contentType -> {
                        response.withHeader("content-type", contentType.getKey());
                        Optional
                            .ofNullable(contentType.getValue())
                            .ifPresent(mediaType -> {
                                Object example = findExample(mediaType, openAPI);
                                if (example != null) {
                                    if (isJsonContentType(contentType.getKey())) {
                                        response.withBody(json(serialise(example)));
                                    } else {
                                        response.withBody(String.valueOf(example));
                                    }
                                } else if (mediaType.getSchema() != null) {
                                    Object schemaExample = resolveSchemaExample(mediaType.getSchema(), openAPI);
                                    if (schemaExample != null) {
                                        if (isJsonContentType(contentType.getKey())) {
                                            response.withBody(json(serialise(schemaExample)));
                                        } else {
                                            response.withBody(serialise(schemaExample));
                                        }
                                    } else {
                                        org.mockserver.openapi.examples.models.Example generatedExample = ExampleBuilder.fromSchema(mediaType.getSchema(), openAPI.getComponents() != null ? openAPI.getComponents().getSchemas() : null);
                                        if (generatedExample instanceof StringExample) {
                                            if (isJsonContentType(contentType.getKey())) {
                                                response.withBody(json(serialise(((StringExample) generatedExample).getValue())));
                                            } else {
                                                response.withBody(((StringExample) generatedExample).getValue());
                                            }
                                        } else if (generatedExample != null) {
                                            String serialise = serialise(generatedExample);
                                            if (isJsonContentType(contentType.getKey())) {
                                                response.withBody(json(serialise));
                                            } else {
                                                response.withBody(serialise);
                                            }
                                        }
                                    }
                                }
                            });
                    });
            });
        return response;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private Object resolveSchemaExample(io.swagger.v3.oas.models.media.Schema schema, OpenAPI openAPI) {
        return resolveSchemaExample(schema, openAPI, Collections.newSetFromMap(new IdentityHashMap<>()));
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private Object resolveSchemaExample(io.swagger.v3.oas.models.media.Schema schema, OpenAPI openAPI, Set<io.swagger.v3.oas.models.media.Schema> activeStack) {
        if (schema == null || !activeStack.add(schema)) {
            return null;
        }
        try {
            if (schema.getExample() != null) {
                return resolveExampleRefs(schema.getExample(), openAPI);
            }
            if (schema instanceof ComposedSchema) {
                ComposedSchema composedSchema = (ComposedSchema) schema;
                if (composedSchema.getAllOf() != null) {
                    Map<String, Object> merged = new LinkedHashMap<>();
                    for (Schema<?> subSchema : composedSchema.getAllOf()) {
                        Object subExample = resolveSchemaExample(subSchema, openAPI, activeStack);
                        if (subExample instanceof Map) {
                            merged.putAll((Map<String, Object>) subExample);
                        }
                    }
                    if (composedSchema.getProperties() != null) {
                        Map<String, Schema> ownProperties = composedSchema.getProperties();
                        for (Map.Entry<String, Schema> entry : ownProperties.entrySet()) {
                            Object propExample = resolveSchemaExample(entry.getValue(), openAPI, activeStack);
                            if (propExample == null) {
                                return null;
                            }
                            merged.put(entry.getKey(), propExample);
                        }
                    }
                    return merged.isEmpty() ? null : merged;
                }
                if (composedSchema.getAnyOf() != null) {
                    for (Schema<?> subSchema : composedSchema.getAnyOf()) {
                        Object subExample = resolveSchemaExample(subSchema, openAPI, activeStack);
                        if (subExample != null) {
                            return subExample;
                        }
                    }
                }
                if (composedSchema.getOneOf() != null) {
                    for (Schema<?> subSchema : composedSchema.getOneOf()) {
                        Object subExample = resolveSchemaExample(subSchema, openAPI, activeStack);
                        if (subExample != null) {
                            return subExample;
                        }
                    }
                }
            }
            if (schema.getProperties() != null) {
                Map<String, io.swagger.v3.oas.models.media.Schema> properties = schema.getProperties();
                Map<String, Object> result = new LinkedHashMap<>();
                for (Map.Entry<String, io.swagger.v3.oas.models.media.Schema> entry : properties.entrySet()) {
                    Object propExample = resolveSchemaExample(entry.getValue(), openAPI, activeStack);
                    if (propExample == null) {
                        return null;
                    }
                    result.put(entry.getKey(), propExample);
                }
                return result.isEmpty() ? null : result;
            }
            if (schema instanceof io.swagger.v3.oas.models.media.ArraySchema) {
                io.swagger.v3.oas.models.media.ArraySchema arraySchema = (io.swagger.v3.oas.models.media.ArraySchema) schema;
                if (arraySchema.getItems() != null) {
                    Object itemExample = resolveSchemaExample(arraySchema.getItems(), openAPI, activeStack);
                    if (itemExample != null) {
                        return Collections.singletonList(itemExample);
                    }
                }
            }
            return null;
        } finally {
            activeStack.remove(schema);
        }
    }

    public static boolean isJsonContentType(String contentType) {
        return org.mockserver.model.MediaType.parse(contentType).isJson();
    }

    private Object findHeaderExample(Header value, OpenAPI openAPI) {
        if (value.getExample() instanceof Example) {
            Object resolved = resolveExampleRefs(((Example) value.getExample()).getValue(), openAPI);
            return resolved != null ? resolved : ((Example) value.getExample()).getValue();
        } else if (value.getExample() != null) {
            return resolveExampleRefs(value.getExample(), openAPI);
        } else if (value.getExamples() != null && !value.getExamples().isEmpty()) {
            Example example = value.getExamples().values().stream().findFirst().orElse(null);
            if (example != null) {
                Object resolved = resolveExampleRefs(example.getValue(), openAPI);
                return resolved != null ? resolved : example.getValue();
            }
        }
        return null;
    }

    private Object findExample(MediaType mediaType, OpenAPI openAPI) {
        Object example = null;
        if (mediaType.getExample() != null) {
            Object raw = mediaType.getExample();
            if (raw instanceof Example) {
                example = ((Example) raw).getValue();
            } else {
                example = raw;
            }
        } else if (mediaType.getExamples() != null && !mediaType.getExamples().isEmpty()) {
            Example namedExample = mediaType.getExamples().values().stream().findFirst().orElse(null);
            if (namedExample != null) {
                example = namedExample.getValue();
            }
        }
        if (example != null) {
            example = resolveExampleRefs(example, openAPI);
        }
        return example;
    }

    @SuppressWarnings("unchecked")
    private Object resolveExampleRefs(Object value, OpenAPI openAPI) {
        return resolveExampleRefs(value, openAPI, new HashSet<>(), 0, 0);
    }

    @SuppressWarnings("unchecked")
    private Object resolveExampleRefs(Object value, OpenAPI openAPI, Set<String> activeRefChain, int refDepth, int structureDepth) {
        if (structureDepth > MAX_STRUCTURE_DEPTH) {
            return value;
        }
        if (value instanceof ObjectNode) {
            ObjectNode node = (ObjectNode) value;
            if (node.size() == 1 && node.has("$ref")) {
                String ref = node.get("$ref").asText();
                if (activeRefChain.contains(ref)) {
                    mockServerLogger.logEvent(
                        new LogEntry()
                            .setLogLevel(WARN)
                            .setMessageFormat("cyclic $ref detected for {} — returning literal value")
                            .setArguments(ref)
                    );
                    return value;
                }
                if (refDepth >= MAX_REF_DEPTH) {
                    mockServerLogger.logEvent(
                        new LogEntry()
                            .setLogLevel(WARN)
                            .setMessageFormat("$ref resolution exceeded maximum depth of {} for {} — returning literal value")
                            .setArguments(MAX_REF_DEPTH, ref)
                    );
                    return value;
                }
                Object resolved = resolveRef(ref, openAPI);
                if (resolved != null) {
                    activeRefChain.add(ref);
                    Object result = resolveExampleRefs(resolved, openAPI, activeRefChain, refDepth + 1, structureDepth + 1);
                    activeRefChain.remove(ref);
                    return result;
                }
            }
            ObjectNode resolvedNode = node.objectNode();
            node.fields().forEachRemaining(entry -> {
                Object resolvedField = resolveExampleRefs(entry.getValue(), openAPI, activeRefChain, refDepth, structureDepth + 1);
                if (resolvedField instanceof JsonNode) {
                    resolvedNode.set(entry.getKey(), (JsonNode) resolvedField);
                } else {
                    resolvedNode.putPOJO(entry.getKey(), resolvedField);
                }
            });
            return resolvedNode;
        } else if (value instanceof ArrayNode) {
            ArrayNode node = (ArrayNode) value;
            ArrayNode resolvedNode = node.arrayNode();
            for (JsonNode item : node) {
                Object resolvedItem = resolveExampleRefs(item, openAPI, activeRefChain, refDepth, structureDepth + 1);
                if (resolvedItem instanceof JsonNode) {
                    resolvedNode.add((JsonNode) resolvedItem);
                } else {
                    resolvedNode.addPOJO(resolvedItem);
                }
            }
            return resolvedNode;
        } else if (value instanceof Map) {
            Map<String, Object> map = (Map<String, Object>) value;
            if (map.size() == 1 && map.containsKey("$ref")) {
                String ref = String.valueOf(map.get("$ref"));
                if (activeRefChain.contains(ref)) {
                    mockServerLogger.logEvent(
                        new LogEntry()
                            .setLogLevel(WARN)
                            .setMessageFormat("cyclic $ref detected for {} — returning literal value")
                            .setArguments(ref)
                    );
                    return value;
                }
                if (refDepth >= MAX_REF_DEPTH) {
                    mockServerLogger.logEvent(
                        new LogEntry()
                            .setLogLevel(WARN)
                            .setMessageFormat("$ref resolution exceeded maximum depth of {} for {} — returning literal value")
                            .setArguments(MAX_REF_DEPTH, ref)
                    );
                    return value;
                }
                Object resolved = resolveRef(ref, openAPI);
                if (resolved != null) {
                    activeRefChain.add(ref);
                    Object result = resolveExampleRefs(resolved, openAPI, activeRefChain, refDepth + 1, structureDepth + 1);
                    activeRefChain.remove(ref);
                    return result;
                }
            }
            Map<String, Object> resolvedMap = new LinkedHashMap<>();
            for (Map.Entry<String, Object> entry : map.entrySet()) {
                resolvedMap.put(entry.getKey(), resolveExampleRefs(entry.getValue(), openAPI, activeRefChain, refDepth, structureDepth + 1));
            }
            return resolvedMap;
        } else if (value instanceof List) {
            List<Object> list = (List<Object>) value;
            List<Object> resolvedList = new ArrayList<>(list.size());
            for (Object item : list) {
                resolvedList.add(resolveExampleRefs(item, openAPI, activeRefChain, refDepth, structureDepth + 1));
            }
            return resolvedList;
        }
        return value;
    }

    private Object resolveRef(String ref, OpenAPI openAPI) {
        if (ref != null && ref.startsWith("#/components/examples/") && openAPI.getComponents() != null && openAPI.getComponents().getExamples() != null) {
            String path = ref.substring("#/components/examples/".length());
            String[] parts = path.split("/");
            if (parts.length >= 1) {
                Example componentExample = openAPI.getComponents().getExamples().get(parts[0]);
                if (componentExample != null) {
                    return componentExample.getValue();
                }
            }
            mockServerLogger.logEvent(
                new LogEntry()
                    .setLogLevel(WARN)
                    .setMessageFormat("unable to resolve $ref {} in example")
                    .setArguments(ref)
            );
        }
        return null;
    }

    private String serialise(Object example) {
        try {
            return OBJECT_WRITER.writeValueAsString(example);
        } catch (Throwable throwable) {
            mockServerLogger.logEvent(
                new LogEntry()
                    .setLogLevel(ERROR)
                    .setMessageFormat("exception while serialising " + example.getClass() + " {}")
                    .setArguments(example)
                    .setThrowable(throwable)
            );
            return "";
        }
    }
}
