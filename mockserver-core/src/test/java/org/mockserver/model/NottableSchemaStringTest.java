package org.mockserver.model;

import org.junit.Test;

import java.util.UUID;

import static junit.framework.TestCase.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsNot.not;
import static org.mockserver.model.NottableSchemaString.notSchema;
import static org.mockserver.model.NottableSchemaString.schemaString;
import static org.mockserver.model.NottableString.string;

public class NottableSchemaStringTest {

    @Test
    public void shouldReturnValuesSetInConstructors() {
        // when
        NottableSchemaString nottableString = notSchema("{ \"type\": \"string\" }");

        // then
        assertThat(nottableString.isNot(), is(true));
        assertThat(nottableString.getValue(), is("{ \"type\": \"string\" }"));
    }

    @Test
    public void shouldReturnValuesSetInConstructorsWithDefaultNotSetting() {
        // when
        NottableSchemaString nottableString = schemaString("{ \"type\": \"string\" }");

        // then
        assertThat(nottableString.isNot(), is(false));
        assertThat(nottableString.getValue(), is("{ \"type\": \"string\" }"));
    }

    @Test
    public void shouldReturnValuesSetInConstructorsWithDefaultNottedString() {
        // when
        NottableSchemaString nottableString = schemaString("!{ \"type\": \"string\" }");

        // then
        assertThat(nottableString.isNot(), is(true));
        assertThat(nottableString.getValue(), is("{ \"type\": \"string\" }"));
    }

    @Test
    public void shouldReturnValuesSetInConstructorsWithNullNotParameter() {
        // when
        NottableSchemaString nottableString = schemaString("{ \"type\": \"string\" }", null);

        // then
        assertThat(nottableString.isNot(), is(false));
        assertThat(nottableString.getValue(), is("{ \"type\": \"string\" }"));
    }

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
    public void shouldMatchNumberMultiple() {
        String schema = "{\n" +
            "    \"type\"       : \"number\",\n" +
            "    \"multipleOf\" : 10\n" +
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
        String schema = "{\n" +
            "  \"type\": \"string\",\n" +
            "  \"minLength\": 2,\n" +
            "  \"maxLength\": 3\n" +
            "}";
        NottableSchemaString string = schemaString(schema);
        NottableSchemaString notString = schemaString("!" + schema);
        assertThat(string.matches("abc"), is(true));
        assertThat(string.matches("a"), is(false));
        assertThat(notString.matches("abc"), is(false));
        assertThat(notString.matches("a"), is(true));
    }

    @Test
    public void shouldMatchStringByRegex() {
        String schema = "{\n" +
            "   \"type\": \"string\",\n" +
            "   \"pattern\": \"^(\\\\([0-9]{3}\\\\))?[0-9]{3}-[0-9]{4}$\"\n" +
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
        String schema = "{\n" +
            "    \"type\": \"string\",\n" +
            "    \"format\": \"uuid\"\n" +
            "}";
        NottableSchemaString string = schemaString(schema);
        NottableSchemaString notString = schemaString("!" + schema);
        assertThat(string.matches(UUID.randomUUID().toString()), is(true));
        assertThat(string.matches("abc"), is(false));
        assertThat(notString.matches(UUID.randomUUID().toString()), is(false));
        assertThat(notString.matches("abc"), is(true));
    }

    @Test
    public void shouldMatchStringByEmailPattern() {
        String schema = "{\n" +
            "    \"type\": \"string\",\n" +
            "    \"format\": \"email\"\n" +
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
        String schema = "{\n" +
            "    \"type\": \"string\",\n" +
            "    \"format\": \"ipv4\"\n" +
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
        String schema = "{\n" +
            "    \"type\": \"string\",\n" +
            "    \"format\": \"hostname\"\n" +
            "}";
        NottableSchemaString string = schemaString(schema);
        NottableSchemaString notString = schemaString("!" + schema);
        assertThat(string.matches("mock-server.com"), is(true));
        assertThat(string.matches("12345"), is(false));
        assertThat(notString.matches("mock-server.com"), is(false));
        assertThat(notString.matches("12345"), is(true));
    }

    @Test
    public void shouldMatchStringByDateTimePattern() {
        String schema = "{\n" +
            "    \"type\": \"string\",\n" +
            "    \"format\": \"date-time\"\n" +
            "}";
        NottableSchemaString string = schemaString(schema);
        NottableSchemaString notString = schemaString("!" + schema);
        assertThat(string.matches("2018-11-13T20:20:39+00:00"), is(true));
        assertThat(string.matches("2018-11-13 20:20:39"), is(false));
        assertThat(notString.matches("2018-11-13T20:20:39+00:00"), is(false));
        assertThat(notString.matches("2018-11-13 20:20:39"), is(true));
    }

    @Test
    public void shouldEqualWhenNull() {
        assertThat(schemaString(null), is(schemaString(null)));
        assertThat(schemaString("{ \"type\": \"string\" }"), not(schemaString(null)));
        assertThat(schemaString(null), not(schemaString("{ \"type\": \"string\" }")));
    }

    @Test
    public void shouldEqualForDoubleNegative() {
        assertThat(notSchema("{ \"type\": \"string\" }"), not(schemaString("{ \"type\": \"string\" }")));
        assertThat(notSchema("{ \"type\": \"string\" }"), not((Object) "{ \"type\": \"string\" }"));

        assertThat(schemaString("{ \"type\": \"string\" }"), not(schemaString("{\n" +
            "  \"type\": \"string\",\n" +
            "  \"minLength\": 2,\n" +
            "  \"maxLength\": 3\n" +
            "}")));
        assertThat(schemaString("{ \"type\": \"string\" }"), not((Object) "{\n" +
            "  \"type\": \"string\",\n" +
            "  \"minLength\": 2,\n" +
            "  \"maxLength\": 3\n" +
            "}"));

        assertThat(schemaString("{ \"type\": \"string\" }"), not(notSchema("{ \"type\": \"string\" }")));
    }
}
