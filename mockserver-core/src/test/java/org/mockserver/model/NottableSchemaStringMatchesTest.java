package org.mockserver.model;

import org.junit.Test;
import org.mockserver.uuid.UUIDService;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.mockserver.character.Character.NEW_LINE;
import static org.mockserver.model.NottableSchemaString.schemaString;

public class NottableSchemaStringMatchesTest {

    @Test
    public void shouldMatchNumber() {
        String schema = "{ \"type\": \"number\" }";
        NottableSchemaString string = schemaString(schema);
        NottableSchemaString notString = schemaString("!" + schema);
        assertThat(string.matches("1"), is(true));
        assertThat(string.matches("2.5"), is(true));
        assertThat(string.matches("a"), is(false));
        assertThat(notString.matches("1"), is(false));
        assertThat(notString.matches("2.5"), is(false));
        assertThat(notString.matches("a"), is(true));
    }

    @Test
    public void shouldMatchInteger() {
        String schema = "{ \"type\": \"integer\" }";
        NottableSchemaString string = schemaString(schema);
        NottableSchemaString notString = schemaString("!" + schema);
        assertThat(string.matches("1"), is(true));
        assertThat(string.matches("a"), is(false));
        assertThat(notString.matches("1"), is(false));
        assertThat(notString.matches("a"), is(true));
    }

    @Test
    public void shouldMatchNullableInteger() {
        String schema = "{ \"type\": \"integer\", \"nullable\": true }";
        NottableSchemaString string = schemaString(schema);
        NottableSchemaString notString = schemaString("!" + schema);
        assertThat(string.matches("1"), is(true));
        assertThat(string.matches(""), is(true));
        assertThat(string.matches("a"), is(false));
        assertThat(notString.matches("1"), is(false));
        assertThat(notString.matches(""), is(false));
        assertThat(notString.matches("a"), is(true));
    }

    @Test
    public void shouldMatchNumberMultiple() {
        String schema = "{" + NEW_LINE +
            "    \"type\"       : \"number\"," + NEW_LINE +
            "    \"multipleOf\" : 10" + NEW_LINE +
            "}";
        NottableSchemaString string = schemaString(schema);
        NottableSchemaString notString = schemaString("!" + schema);
        assertThat(string.matches("10"), is(true));
        assertThat(string.matches("20"), is(true));
        assertThat(string.matches("23"), is(false));
        assertThat(notString.matches("10"), is(false));
        assertThat(notString.matches("20"), is(false));
        assertThat(notString.matches("23"), is(true));
    }

    @Test
    public void shouldMatchStringByLength() {
        String schema = "{" + NEW_LINE +
            "  \"type\": \"string\"," + NEW_LINE +
            "  \"minLength\": 2," + NEW_LINE +
            "  \"maxLength\": 3" + NEW_LINE +
            "}";
        NottableSchemaString string = schemaString(schema);
        NottableSchemaString notString = schemaString("!" + schema);
        assertThat(string.matches("abc"), is(true));
        assertThat(string.matches("a"), is(false));
        assertThat(notString.matches("abc"), is(false));
        assertThat(notString.matches("a"), is(true));
    }

    @Test
    public void shouldMatchNullableStringByLength() {
        String schema = "{" + NEW_LINE +
            "  \"type\": \"string\"," + NEW_LINE +
            "  \"minLength\": 2," + NEW_LINE +
            "  \"maxLength\": 3," + NEW_LINE +
            "  \"nullable\": true" + NEW_LINE +
            "}";
        NottableSchemaString string = schemaString(schema);
        NottableSchemaString notString = schemaString("!" + schema);
        assertThat(string.matches("abc"), is(true));
        assertThat(string.matches(""), is(true));
        assertThat(string.matches("a"), is(false));
        assertThat(notString.matches("abc"), is(false));
        assertThat(notString.matches(""), is(false));
        assertThat(notString.matches("a"), is(true));
    }

    @Test
    public void shouldMatchStringByRegex() {
        String schema = "{" + NEW_LINE +
            "   \"type\": \"string\"," + NEW_LINE +
            "   \"pattern\": \"^(\\\\([0-9]{3}\\\\))?[0-9]{3}-[0-9]{4}$\"" + NEW_LINE +
            "}";
        NottableSchemaString string = schemaString(schema);
        NottableSchemaString notString = schemaString("!" + schema);
        assertThat(string.matches("555-1212"), is(true));
        assertThat(string.matches("(888)555-1212"), is(true));
        assertThat(string.matches("(888)555-1212 ext. 532"), is(false));
        assertThat(string.matches("(800)FLOWERS"), is(false));
        assertThat(notString.matches("555-1212"), is(false));
        assertThat(notString.matches("(888)555-1212"), is(false));
        assertThat(notString.matches("(888)555-1212 ext. 532"), is(true));
        assertThat(notString.matches("(800)FLOWERS"), is(true));
    }

    @Test
    public void shouldMatchStringByUUIDPattern() {
        String schema = "{" + NEW_LINE +
            "    \"type\": \"string\"," + NEW_LINE +
            "    \"format\": \"uuid\"" + NEW_LINE +
            "}";
        NottableSchemaString string = schemaString(schema);
        NottableSchemaString notString = schemaString("!" + schema);
        assertThat(string.matches(UUIDService.getUUID()), is(true));
        assertThat(string.matches("abc"), is(false));
        assertThat(notString.matches(UUIDService.getUUID()), is(false));
        assertThat(notString.matches("abc"), is(true));
    }

    @Test
    public void shouldMatchStringByEmailPattern() {
        String schema = "{" + NEW_LINE +
            "    \"type\": \"string\"," + NEW_LINE +
            "    \"format\": \"email\"" + NEW_LINE +
            "}";
        NottableSchemaString string = schemaString(schema);
        NottableSchemaString notString = schemaString("!" + schema);
        assertThat(string.matches("someone@mockserver.com"), is(true));
        assertThat(string.matches("abc"), is(false));
        assertThat(notString.matches("someone@mockserver.com"), is(false));
        assertThat(notString.matches("abc"), is(true));
    }

    @Test
    public void shouldMatchStringByIPv4Pattern() {
        String schema = "{" + NEW_LINE +
            "    \"type\": \"string\"," + NEW_LINE +
            "    \"format\": \"ipv4\"" + NEW_LINE +
            "}";
        NottableSchemaString string = schemaString(schema);
        NottableSchemaString notString = schemaString("!" + schema);
        assertThat(string.matches("192.168.1.30"), is(true));
        assertThat(string.matches("abc"), is(false));
        assertThat(notString.matches("192.168.1.30"), is(false));
        assertThat(notString.matches("abc"), is(true));
    }

    @Test
    public void shouldMatchStringByHostnamePattern() {
        String schema = "{" + NEW_LINE +
            "    \"type\": \"string\"," + NEW_LINE +
            "    \"format\": \"hostname\"" + NEW_LINE +
            "}";
        NottableSchemaString string = schemaString(schema);
        NottableSchemaString notString = schemaString("!" + schema);
        assertThat(string.matches("mock-server.com"), is(true));
        assertThat(string.matches("%@12345"), is(false));
        assertThat(notString.matches("mock-server.com"), is(false));
        assertThat(notString.matches("12$^345"), is(true));
    }

    @Test
    public void shouldMatchStringByDateTimePattern() {
        String schema = "{" + NEW_LINE +
            "    \"type\": \"string\"," + NEW_LINE +
            "    \"format\": \"date-time\"" + NEW_LINE +
            "}";
        NottableSchemaString string = schemaString(schema);
        NottableSchemaString notString = schemaString("!" + schema);
        assertThat(string.matches("2018-11-13T20:20:39+00:00"), is(true));
        assertThat(string.matches("2018-11-13 20:20:39"), is(false));
        assertThat(notString.matches("2018-11-13T20:20:39+00:00"), is(false));
        assertThat(notString.matches("2018-11-13 20:20:39"), is(true));
    }
}
