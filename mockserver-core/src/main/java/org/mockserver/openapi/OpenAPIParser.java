package org.mockserver.openapi;

import com.google.common.base.Joiner;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.parser.OpenAPIResolver;
import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.parser.core.extensions.SwaggerParserExtension;
import io.swagger.v3.parser.core.models.AuthorizationValue;
import io.swagger.v3.parser.core.models.ParseOptions;
import io.swagger.v3.parser.core.models.SwaggerParseResult;
import io.swagger.v3.parser.util.ResolverFully;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.mockserver.cache.LRUCache;
import org.mockserver.logging.MockServerLogger;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import static io.swagger.v3.parser.OpenAPIV3Parser.getExtensions;
import static java.util.concurrent.TimeUnit.MINUTES;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

public class OpenAPIParser {

    private final static LRUCache<String, OpenAPI> openAPILRUCache = new LRUCache<>(new MockServerLogger(), 250, MINUTES.toMillis(30));

    public static final String OPEN_API_LOAD_ERROR = "Unable to load API spec";

    public static OpenAPI buildOpenAPI(String specUrlOrPayload) {
        OpenAPI openAPI = openAPILRUCache.get(specUrlOrPayload);
        if (openAPI == null) {
            SwaggerParseResult swaggerParseResult = null;
            List<AuthorizationValue> auths = null;
            ParseOptions parseOptions = new ParseOptions();
            parseOptions.setResolve(true);
            parseOptions.setResolveFully(true);
            parseOptions.setResolveCombinators(true);
            parseOptions.setFlatten(true);
            parseOptions.setFlattenComposedSchemas(true);

            List<String> errorMessage = new ArrayList<>();
            try {
                if (specUrlOrPayload.endsWith(".json") || specUrlOrPayload.endsWith(".yaml")) {
                    specUrlOrPayload = specUrlOrPayload.replaceAll("\\\\", "/");
                    List<SwaggerParserExtension> parserExtensions = getExtensions();
                    for (SwaggerParserExtension extension : parserExtensions) {
                        swaggerParseResult = extension.readLocation(specUrlOrPayload, auths, parseOptions);
                        openAPI = swaggerParseResult.getOpenAPI();
                        if (openAPI != null) {
                            break;
                        } else {
                            errorMessage.addAll(swaggerParseResult.getMessages());
                        }
                    }
                } else {
                    swaggerParseResult = new OpenAPIV3Parser().readContents(specUrlOrPayload, auths, parseOptions);
                    openAPI = swaggerParseResult.getOpenAPI();
                    if (openAPI == null) {
                        errorMessage.addAll(swaggerParseResult.getMessages());
                    }
                }
            } catch (Throwable throwable) {
                throw new IllegalArgumentException(OPEN_API_LOAD_ERROR + (errorMessage.isEmpty() ? ", " + throwable.getMessage() : ", " + Joiner.on(", ").skipNulls().join(errorMessage)), throwable);
            }
            if (openAPI != null) {
                try {
                    openAPI = resolve(openAPI, auths, specUrlOrPayload);
                } catch (Throwable throwable) {
                    throw new IllegalArgumentException(OPEN_API_LOAD_ERROR + (errorMessage.isEmpty() ? ", " + throwable.getMessage() : ", " + Joiner.on(", ").skipNulls().join(errorMessage)), throwable);
                }
            } else {
                if (swaggerParseResult != null) {
                    String message = errorMessage.stream().filter(Objects::nonNull).collect(Collectors.joining(" and ")).trim();
                    throw new IllegalArgumentException((OPEN_API_LOAD_ERROR + (isNotBlank(message) ? ", " + message : "")));
                } else {
                    throw new IllegalArgumentException(OPEN_API_LOAD_ERROR);
                }
            }
            addMissingOperationIds(openAPI);
            openAPILRUCache.put(specUrlOrPayload, openAPI);
        }
        return openAPI;
    }

    private static OpenAPI resolve(OpenAPI openAPI, List<AuthorizationValue> auths, String specUrlOrPayload) {
        if (openAPI != null) {
            OpenAPIResolver.Settings settings = new OpenAPIResolver.Settings();
            settings.addParametersToEachOperation(true);
            openAPI = new OpenAPIResolver(openAPI, auths, specUrlOrPayload, settings).resolve();
            new ResolverFully().resolveFully(openAPI);
        }
        return openAPI;
    }

    private static void addMissingOperationIds(OpenAPI openAPI) {
        openAPI.getPaths().forEach(
            (path, pathItem) -> {
                mapOperations(pathItem).forEach(
                    stringOperationPair -> {
                        if (isBlank(stringOperationPair.getRight().getOperationId())) {
                            stringOperationPair.getRight().setOperationId(stringOperationPair.getLeft() + " " + path);
                        }
                    }
                );
            }
        );
    }

    public static List<Pair<String, Operation>> mapOperations(PathItem pathItem) {
        List<Pair<String, Operation>> allOperations = new ArrayList<>();
        if (pathItem.getGet() != null) {
            allOperations.add(new ImmutablePair<>("GET", pathItem.getGet()));
        }
        if (pathItem.getPut() != null) {
            allOperations.add(new ImmutablePair<>("PUT", pathItem.getPut()));
        }
        if (pathItem.getPost() != null) {
            allOperations.add(new ImmutablePair<>("POST", pathItem.getPost()));
        }
        if (pathItem.getPatch() != null) {
            allOperations.add(new ImmutablePair<>("PATCH", pathItem.getPatch()));
        }
        if (pathItem.getDelete() != null) {
            allOperations.add(new ImmutablePair<>("DELETE", pathItem.getDelete()));
        }
        if (pathItem.getHead() != null) {
            allOperations.add(new ImmutablePair<>("HEAD", pathItem.getHead()));
        }
        if (pathItem.getOptions() != null) {
            allOperations.add(new ImmutablePair<>("OPTIONS", pathItem.getOptions()));
        }
        if (pathItem.getTrace() != null) {
            allOperations.add(new ImmutablePair<>("TRACE", pathItem.getTrace()));
        }
        return allOperations;
    }
}
