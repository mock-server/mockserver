package org.mockserver.openapi;

import io.swagger.v3.oas.models.OpenAPI;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.slf4j.event.Level;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockserver.character.Character.NEW_LINE;
import static org.mockserver.openapi.OpenAPIParser.buildOpenAPI;

/**
 * Each spec is a real broken document parsed by swagger-parser; a unique title keeps it out of the shared parse cache.
 */
public class OpenAPILoadProblemsTest {

    private static String spec(String version, String body) {
        return "openapi: " + version + "\n" +
            "info:\n" +
            "  title: \"" + UUID.randomUUID() + "\"\n" +
            "  version: \"1\"\n" +
            body;
    }

    private static List<String> loadWarnings(String spec) {
        MockServerLogger logger = mock(MockServerLogger.class);
        when(logger.isEnabledForInstance(any())).thenReturn(true);
        OpenAPI openAPI = buildOpenAPI(spec, logger, null);
        assertThat(openAPI, is(not((OpenAPI) null)));
        ArgumentCaptor<LogEntry> entries = ArgumentCaptor.forClass(LogEntry.class);
        verify(logger, atLeast(0)).logEvent(entries.capture());
        return entries.getAllValues().stream()
            .filter(entry -> entry.getLogLevel() == Level.WARN && entry.getMessageFormat().startsWith("loaded OpenAPI spec with problems"))
            .map(entry -> (String) entry.getArguments()[0])
            .collect(Collectors.toList());
    }

    private static String loadFailure(String spec) {
        return assertThrows(IllegalArgumentException.class, () -> buildOpenAPI(spec, new MockServerLogger(OpenAPILoadProblemsTest.class), null)).getMessage();
    }

    @Test
    public void shouldLocateUnresolvedRefAndStillLoad() {
        List<String> warnings = loadWarnings(spec("3.0.0", "" +
            "paths:\n" +
            "  /pets:\n" +
            "    get:\n" +
            "      operationId: listPets\n" +
            "      responses:\n" +
            "        \"200\":\n" +
            "          description: ok\n" +
            "          content:\n" +
            "            application/json:\n" +
            "              schema:\n" +
            "                $ref: \"#/components/schemas/Missing\"\n" +
            "components:\n" +
            "  schemas:\n" +
            "    Pet:\n" +
            "      type: object\n"));

        assertThat(warnings.size(), is(1));
        assertThat(warnings.get(0), startsWith("1 problem found:" + NEW_LINE + NEW_LINE + "  unresolved or mistyped $ref (1):" + NEW_LINE +
            "    - #/paths/~1pets/get/responses/200/content/application~1json/schema/$ref: $ref target #/components/schemas/Missing does not exist" + NEW_LINE +
            "      hint: check that the $ref target exists"));
    }

    @Test
    public void shouldLocateRefToMissingComponentSchema() {
        List<String> warnings = loadWarnings(spec("3.0.0", "" +
            "paths:\n" +
            "  /pets:\n" +
            "    get:\n" +
            "      responses:\n" +
            "        \"200\":\n" +
            "          description: ok\n" +
            "components:\n" +
            "  schemas:\n" +
            "    Pet:\n" +
            "      type: object\n" +
            "      properties:\n" +
            "        owner:\n" +
            "          $ref: \"#/components/schemas/Owner\"\n"));

        assertThat(warnings.size(), is(1));
        assertThat(warnings.get(0), containsString("    - #/components/schemas/Pet: $ref target #/components/schemas/Owner does not exist"));
    }

    @Test
    public void shouldLocateInvalidTypeWithHint() {
        List<String> warnings = loadWarnings(spec("3.0.0", "" +
            "paths:\n" +
            "  /pets:\n" +
            "    get:\n" +
            "      responses:\n" +
            "        \"200\":\n" +
            "          description: ok\n" +
            "          content:\n" +
            "            application/json:\n" +
            "              schema:\n" +
            "                type: [string, \"null\"]\n"));

        assertThat(warnings.size(), is(1));
        assertThat(warnings.get(0), is("1 problem found:" + NEW_LINE + NEW_LINE + "  wrong value type (1):" + NEW_LINE +
            "    - #/paths/~1pets/get/responses/200/content/application~1json/schema/type: 'type' is not of type `string`" + NEW_LINE +
            "      hint: in OpenAPI 3.0 a schema's type is a single string; a list of types needs OpenAPI 3.1"));
    }

    @Test
    public void shouldLocateDuplicateOperationIdAndKeepBothOperations() {
        MockServerLogger logger = mock(MockServerLogger.class);
        when(logger.isEnabledForInstance(any())).thenReturn(true);
        String spec = spec("3.0.0", "" +
            "paths:\n" +
            "  /pets:\n" +
            "    get:\n" +
            "      operationId: listPets\n" +
            "      responses:\n" +
            "        \"200\":\n" +
            "          description: ok\n" +
            "  /pets/{petId}:\n" +
            "    get:\n" +
            "      operationId: listPets\n" +
            "      parameters:\n" +
            "        - name: petId\n" +
            "          in: path\n" +
            "          required: true\n" +
            "          schema:\n" +
            "            type: string\n" +
            "      responses:\n" +
            "        \"200\":\n" +
            "          description: ok\n");

        List<String> warnings = loadWarnings(spec);

        assertThat(warnings.size(), is(1));
        assertThat(warnings.get(0), containsString("  duplicate value (1):" + NEW_LINE +
            "    - #/paths/~1pets~1{petId}/get/operationId: 'operationId' is repeated" + NEW_LINE +
            "      hint: operationIds must be unique: MockServer keeps both operations but renames the later one by appending \" (2)\""));
        assertThat(buildOpenAPI(spec, logger, null).getPaths().get("/pets/{petId}").getGet().getOperationId(), is("listPets (2)"));
    }

    @Test
    public void shouldGroupSeveralProblemsByKind() {
        List<String> warnings = loadWarnings("openapi: 3.0.0\n" +
            "info:\n" +
            "  title: \"" + UUID.randomUUID() + "\"\n" +
            "paths:\n" +
            "  /pets:\n" +
            "    get:\n" +
            "      responses:\n" +
            "        \"200\":\n" +
            "          content: {}\n" +
            "  pets:\n" +
            "    get:\n" +
            "      responses:\n" +
            "        \"200\":\n" +
            "          description: ok\n");

        assertThat(warnings.size(), is(1));
        assertThat(warnings.get(0), is("3 problems found:" + NEW_LINE + NEW_LINE +
            "  missing required field (2):" + NEW_LINE +
            "    - #/info: required field 'version' is missing" + NEW_LINE +
            "    - #/paths/~1pets/get/responses/200: required field 'description' is missing" + NEW_LINE + NEW_LINE +
            "  invalid path (1):" + NEW_LINE +
            "    - #/paths/pets: path 'pets' does not start with /" + NEW_LINE +
            "      hint: each key under paths must start with '/', for example /pets"));
    }

    @Test
    public void shouldReturnProblemsOnEveryLoadButWarnOnlyWhenParsed() {
        MockServerLogger logger = mock(MockServerLogger.class);
        when(logger.isEnabledForInstance(any())).thenReturn(true);
        String spec = spec("3.0.0", "" +
            "paths:\n" +
            "  pets:\n" +
            "    get:\n" +
            "      responses:\n" +
            "        \"200\":\n" +
            "          description: ok\n");

        OpenAPIParser.LoadedSpec first = OpenAPIParser.loadOpenAPI(spec, logger, null);
        OpenAPIParser.LoadedSpec cached = OpenAPIParser.loadOpenAPI(spec, logger, null);

        OpenAPILoadProblems.Problem expected = new OpenAPILoadProblems.Problem(OpenAPILoadProblems.Kind.INVALID_PATH, "#/paths/pets", "path 'pets' does not start with /", "each key under paths must start with '/', for example /pets");
        assertThat(first.problems(), is(List.of(expected)));
        assertThat(cached.problems(), is(List.of(expected)));
        assertThat(cached.openAPI(), is(first.openAPI()));
        ArgumentCaptor<LogEntry> entries = ArgumentCaptor.forClass(LogEntry.class);
        verify(logger, atLeast(0)).logEvent(entries.capture());
        assertThat(entries.getAllValues().stream().filter(entry -> entry.getLogLevel() == Level.WARN).count(), is(1L));
    }

    @Test
    public void shouldReturnNoProblemsForCleanSpec() {
        assertThat(OpenAPIParser.loadOpenAPI(spec("3.0.0", "" +
            "paths:\n" +
            "  /pets:\n" +
            "    get:\n" +
            "      responses:\n" +
            "        \"200\":\n" +
            "          description: ok\n"), new MockServerLogger(OpenAPILoadProblemsTest.class), null).problems(), is(empty()));
    }

    @Test
    public void shouldNotWarnForCleanSpec() {
        List<String> warnings = loadWarnings(spec("3.0.0", "" +
            "paths:\n" +
            "  /pets:\n" +
            "    get:\n" +
            "      operationId: listPets\n" +
            "      responses:\n" +
            "        \"200\":\n" +
            "          description: ok\n"));

        assertThat(warnings, is(empty()));
    }

    @Test
    public void shouldReportYamlTabWithLineAndHint() {
        String message = loadFailure(spec("3.0.0", "" +
            "paths:\n" +
            "  /pets:\n" +
            "\tget:\n" +
            "      responses:\n" +
            "        \"200\":\n" +
            "          description: ok\n"));

        assertThat(message, startsWith("Unable to load API spec, 1 problem found:" + NEW_LINE + NEW_LINE +
            "  syntax error (1):" + NEW_LINE +
            "    - line 7, column 1: while scanning for the next token" + NEW_LINE +
            "      found character '\\t(TAB)' that cannot start any token. (Do not use \\t(TAB) for indentation)"));
        assertThat(message, containsString(NEW_LINE + "      hint: YAML does not allow tabs for indentation: replace each tab with spaces"));
    }

    @Test
    public void shouldReportYamlSyntaxErrorAtProblemLine() {
        String message = loadFailure(spec("3.0.0", "" +
            "paths:\n" +
            "  /pets:\n" +
            "    get:\n" +
            "      responses:\n" +
            "        \"200\":\n" +
            "          description: ok\n" +
            "      tags: [unclosed\n" +
            "    put:\n"));

        assertThat(message, startsWith("Unable to load API spec, 1 problem found:" + NEW_LINE + NEW_LINE +
            "  syntax error (1):" + NEW_LINE +
            "    - line 12, column 8: while parsing a flow sequence" + NEW_LINE +
            "       in 'reader', line 11, column 13:"));
        assertThat(message, not(containsString("hint:")));
    }

    @Test
    public void shouldReportJsonSyntaxErrorWithLine() {
        String message = loadFailure("{\"openapi\": \"3.0.0\",\n \"info\": {\"title\": \"" + UUID.randomUUID() + "\", \"version\": \"1\"},\n \"paths\": {");

        assertThat(message, startsWith("Unable to load API spec, 1 problem found:" + NEW_LINE + NEW_LINE +
            "  syntax error (1):" + NEW_LINE +
            "    - line 3, column 11: Unexpected end-of-input"));
    }

    @Test
    public void shouldReportUnreadableLocationWithHint() {
        String message = loadFailure("org/mockserver/openapi/" + UUID.randomUUID() + ".yaml");

        assertThat(message, startsWith("Unable to load API spec, 1 problem found:" + NEW_LINE + NEW_LINE +
            "  spec could not be read (1):" + NEW_LINE +
            "    - Unable to read location `org/mockserver/openapi/"));
        assertThat(message, containsString(NEW_LINE + "      hint: check that the file path, classpath resource or URL is correct and that MockServer can read it"));
    }

    @Test
    public void shouldConvertSwaggerParserLocationsToJsonPointers() {
        assertThat(OpenAPILoadProblems.problem("attribute paths.'/v1.0/pets~old'(post).requestBody.content.'application/json'.schema.type is not of type `string`").location(),
            is("#/paths/~1v1.0~1pets~0old/post/requestBody/content/application~1json/schema/type"));
        assertThat(OpenAPILoadProblems.problem("attribute webhooks.'newPet'(post).responses.200.description is missing").location(),
            is("#/webhooks/newPet/post/responses/200"));
        assertThat(OpenAPILoadProblems.problem("attribute paths.'/pets'(get).parameters.schemas.type is not of type `string`").location(),
            is("#/paths/~1pets/get/parameters"));
        assertThat(OpenAPILoadProblems.problem("attribute components.parameters.limit.in is not of type `[query|header|path|cookie]`").location(),
            is("#/components/parameters/limit/in"));
        assertThat(OpenAPILoadProblems.problem("attribute openapi is missing").location(), is("#"));
        assertThat(OpenAPILoadProblems.problem("The provided definition does not specify a valid version field").location(), is((String) null));
    }
}
