package org.mockserver.model;

import org.junit.Test;

import java.util.UUID;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsNot.not;
import static org.mockserver.character.Character.NEW_LINE;
import static org.mockserver.model.NottableSchemaString.notSchema;
import static org.mockserver.model.NottableSchemaString.schemaString;
import static org.mockserver.model.NottableString.string;

public class NottableSchemaStringEqualsTest {

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

        assertThat(schemaString("{ \"type\": \"string\" }"), not(schemaString("{" + NEW_LINE +
            "  \"type\": \"string\"," + NEW_LINE +
            "  \"minLength\": 2," + NEW_LINE +
            "  \"maxLength\": 3" + NEW_LINE +
            "}")));
        assertThat(schemaString("{ \"type\": \"string\" }"), not((Object) "{" + NEW_LINE +
            "  \"type\": \"string\"," + NEW_LINE +
            "  \"minLength\": 2," + NEW_LINE +
            "  \"maxLength\": 3" + NEW_LINE +
            "}"));

        assertThat(schemaString("{ \"type\": \"string\" }"), not(notSchema("{ \"type\": \"string\" }")));
    }

    @Test
    public void schemaForNumberShouldEqualString(){
        String schema = "{ \"type\": \"number\" }";
        NottableSchemaString string = schemaString(schema);
        NottableSchemaString notString = schemaString("!" + schema);
        assertThat(string.equals(string("1")), is(true));
        assertThat(string.equals(string("2.5")), is(true));
        assertThat(string.equals(string("a")), is(false));
        assertThat(notString.equals(string("1")), is(false));
        assertThat(notString.equals(string("2.5")), is(false));
        assertThat(notString.equals(string("a")), is(true));
    }

    @Test
    public void schemaForIntegerShouldEqualString(){
        String schema = "{ \"type\": \"integer\" }";
        NottableSchemaString string = schemaString(schema);
        NottableSchemaString notString = schemaString("!" + schema);
        assertThat(string.equals(string("1")), is(true));
        assertThat(string.equals(string("a")), is(false));
        assertThat(notString.equals(string("1")), is(false));
        assertThat(notString.equals(string("a")), is(true));
    }

    @Test
    public void schemaForNumberMultipleShouldEqualString(){
        String schema = "{" + NEW_LINE +
            "    \"type\"       : \"number\"," + NEW_LINE +
            "    \"multipleOf\" : 10" + NEW_LINE +
            "}";
        NottableSchemaString string = schemaString(schema);
        NottableSchemaString notString = schemaString("!" + schema);
        assertThat(string.equals(string("10")), is(true));
        assertThat(string.equals(string("20")), is(true));
        assertThat(string.equals(string("23")), is(false));
        assertThat(notString.equals(string("10")), is(false));
        assertThat(notString.equals(string("20")), is(false));
        assertThat(notString.equals(string("23")), is(true));
    }

    @Test
    public void schemaForStringByLengthShouldEqualString(){
        String schema = "{" + NEW_LINE +
            "  \"type\": \"string\"," + NEW_LINE +
            "  \"minLength\": 2," + NEW_LINE +
            "  \"maxLength\": 3" + NEW_LINE +
            "}";
        NottableSchemaString string = schemaString(schema);
        NottableSchemaString notString = schemaString("!" + schema);
        assertThat(string.equals(string("abc")), is(true));
        assertThat(string.equals(string("a")), is(false));
        assertThat(notString.equals(string("abc")), is(false));
        assertThat(notString.equals(string("a")), is(true));
    }

    @Test
    public void schemaForStringByRegexShouldEqualString(){
        String schema = "{" + NEW_LINE +
            "   \"type\": \"string\"," + NEW_LINE +
            "   \"pattern\": \"^(\\\\([0-9]{3}\\\\))?[0-9]{3}-[0-9]{4}$\"" + NEW_LINE +
            "}";
        NottableSchemaString string = schemaString(schema);
        NottableSchemaString notString = schemaString("!" + schema);
        assertThat(string.equals(string("555-1212")), is(true));
        assertThat(string.equals(string("(888)555-1212")), is(true));
        assertThat(string.equals(string("(888)555-1212 ext. 532")), is(false));
        assertThat(string.equals(string("(800)FLOWERS")), is(false));
        assertThat(notString.equals(string("555-1212")), is(false));
        assertThat(notString.equals(string("(888)555-1212")), is(false));
        assertThat(notString.equals(string("(888)555-1212 ext. 532")), is(true));
        assertThat(notString.equals(string("(800)FLOWERS")), is(true));
    }

    @Test
    public void schemaForStringByUUIDPatternShouldEqualString(){
        String schema = "{" + NEW_LINE +
            "    \"type\": \"string\"," + NEW_LINE +
            "    \"format\": \"uuid\"" + NEW_LINE +
            "}";
        NottableSchemaString string = schemaString(schema);
        NottableSchemaString notString = schemaString("!" + schema);
        assertThat(string.equals(string(UUID.randomUUID().toString())), is(true));
        assertThat(string.equals(string("abc")), is(false));
        assertThat(notString.equals(string(UUID.randomUUID().toString())), is(false));
        assertThat(notString.equals(string("abc")), is(true));
    }

    @Test
    public void schemaForStringByEmailPatternShouldEqualString(){
        String schema = "{" + NEW_LINE +
            "    \"type\": \"string\"," + NEW_LINE +
            "    \"format\": \"email\"" + NEW_LINE +
            "}";
        NottableSchemaString string = schemaString(schema);
        NottableSchemaString notString = schemaString("!" + schema);
        assertThat(string.equals(string("someone@mockserver.com")), is(true));
        assertThat(string.equals(string("abc")), is(false));
        assertThat(notString.equals(string("someone@mockserver.com")), is(false));
        assertThat(notString.equals(string("abc")), is(true));
    }

    @Test
    public void schemaForStringByIPv4PatternShouldEqualString(){
        String schema = "{" + NEW_LINE +
            "    \"type\": \"string\"," + NEW_LINE +
            "    \"format\": \"ipv4\"" + NEW_LINE +
            "}";
        NottableSchemaString string = schemaString(schema);
        NottableSchemaString notString = schemaString("!" + schema);
        assertThat(string.equals(string("192.168.1.30")), is(true));
        assertThat(string.equals(string("abc")), is(false));
        assertThat(notString.equals(string("192.168.1.30")), is(false));
        assertThat(notString.equals(string("abc")), is(true));
    }

    @Test
    public void schemaForStringByHostnamePatternShouldEqualString(){
        String schema = "{" + NEW_LINE +
            "    \"type\": \"string\"," + NEW_LINE +
            "    \"format\": \"hostname\"" + NEW_LINE +
            "}";
        NottableSchemaString string = schemaString(schema);
        NottableSchemaString notString = schemaString("!" + schema);
        assertThat(string.equals(string("mock-server.com")), is(true));
        assertThat(string.equals(string("12345")), is(false));
        assertThat(notString.equals(string("mock-server.com")), is(false));
        assertThat(notString.equals(string("12345")), is(true));
    }

    @Test
    public void schemaForStringByDateTimePatternShouldEqualString(){
        String schema = "{" + NEW_LINE +
            "    \"type\": \"string\"," + NEW_LINE +
            "    \"format\": \"date-time\"" + NEW_LINE +
            "}";
        NottableSchemaString string = schemaString(schema);
        NottableSchemaString notString = schemaString("!" + schema);
        assertThat(string.equals(string("2018-11-13T20:20:39+00:00")), is(true));
        assertThat(string.equals(string("2018-11-13 20:20:39")), is(false));
        assertThat(notString.equals(string("2018-11-13T20:20:39+00:00")), is(false));
        assertThat(notString.equals(string("2018-11-13 20:20:39")), is(true));
    }

    @Test
    public void shouldEqualSchemaForNumber() {
        String schema = "{ \"type\": \"number\" }";
        NottableSchemaString string = schemaString(schema);
        NottableSchemaString notString = schemaString("!" + schema);
        assertThat(string("1").equals(string), is(true));
        assertThat(string("2.5").equals(string), is(true));
        assertThat(string("a").equals(string), is(false));
        assertThat(string("1").equals(notString), is(false));
        assertThat(string("2.5").equals(notString), is(false));
        assertThat(string("a").equals(notString), is(true));
    }

    @Test
    public void shouldEqualSchemaForInteger() {
        String schema = "{ \"type\": \"integer\" }";
        NottableSchemaString string = schemaString(schema);
        NottableSchemaString notString = schemaString("!" + schema);
        assertThat(string("1").equals(string), is(true));
        assertThat(string("a").equals(string), is(false));
        assertThat(string("1").equals(notString), is(false));
        assertThat(string("a").equals(notString), is(true));
    }

    @Test
    public void shouldEqualSchemaForNumberMultiple() {
        String schema = "{" + NEW_LINE +
            "    \"type\"       : \"number\"," + NEW_LINE +
            "    \"multipleOf\" : 10" + NEW_LINE +
            "}";
        NottableSchemaString string = schemaString(schema);
        NottableSchemaString notString = schemaString("!" + schema);
        assertThat(string("10").equals(string), is(true));
        assertThat(string("20").equals(string), is(true));
        assertThat(string("23").equals(string), is(false));
        assertThat(string("10").equals(notString), is(false));
        assertThat(string("20").equals(notString), is(false));
        assertThat(string("23").equals(notString), is(true));
    }

    @Test
    public void shouldEqualSchemaForStringByLength() {
        String schema = "{" + NEW_LINE +
            "  \"type\": \"string\"," + NEW_LINE +
            "  \"minLength\": 2," + NEW_LINE +
            "  \"maxLength\": 3" + NEW_LINE +
            "}";
        NottableSchemaString string = schemaString(schema);
        NottableSchemaString notString = schemaString("!" + schema);
        assertThat(string("abc").equals(string), is(true));
        assertThat(string("a").equals(string), is(false));
        assertThat(string("abc").equals(notString), is(false));
        assertThat(string("a").equals(notString), is(true));
    }

    @Test
    public void shouldEqualSchemaForStringByRegex() {
        String schema = "{" + NEW_LINE +
            "   \"type\": \"string\"," + NEW_LINE +
            "   \"pattern\": \"^(\\\\([0-9]{3}\\\\))?[0-9]{3}-[0-9]{4}$\"" + NEW_LINE +
            "}";
        NottableSchemaString string = schemaString(schema);
        NottableSchemaString notString = schemaString("!" + schema);
        assertThat(string("555-1212").equals(string), is(true));
        assertThat(string("(888)555-1212").equals(string), is(true));
        assertThat(string("(888)555-1212 ext. 532").equals(string), is(false));
        assertThat(string("(800)FLOWERS").equals(string), is(false));
        assertThat(string("555-1212").equals(notString), is(false));
        assertThat(string("(888)555-1212").equals(notString), is(false));
        assertThat(string("(888)555-1212 ext. 532").equals(notString), is(true));
        assertThat(string("(800)FLOWERS").equals(notString), is(true));
    }

    @Test
    public void shouldEqualSchemaForStringByUUIDPattern() {
        String schema = "{" + NEW_LINE +
            "    \"type\": \"string\"," + NEW_LINE +
            "    \"format\": \"uuid\"" + NEW_LINE +
            "}";
        NottableSchemaString string = schemaString(schema);
        NottableSchemaString notString = schemaString("!" + schema);
        assertThat(string.equals(string(UUID.randomUUID().toString())), is(true));
        assertThat(string("abc").equals(string), is(false));
        assertThat(notString.equals(string(UUID.randomUUID().toString())), is(false));
        assertThat(string("abc").equals(notString), is(true));
    }

    @Test
    public void shouldEqualSchemaForStringByEmailPattern() {
        String schema = "{" + NEW_LINE +
            "    \"type\": \"string\"," + NEW_LINE +
            "    \"format\": \"email\"" + NEW_LINE +
            "}";
        NottableSchemaString string = schemaString(schema);
        NottableSchemaString notString = schemaString("!" + schema);
        assertThat(string("someone@mockserver.com").equals(string), is(true));
        assertThat(string("abc").equals(string), is(false));
        assertThat(string("someone@mockserver.com").equals(notString), is(false));
        assertThat(string("abc").equals(notString), is(true));
    }

    @Test
    public void shouldEqualSchemaForStringByIPv4Pattern() {
        String schema = "{" + NEW_LINE +
            "    \"type\": \"string\"," + NEW_LINE +
            "    \"format\": \"ipv4\"" + NEW_LINE +
            "}";
        NottableSchemaString string = schemaString(schema);
        NottableSchemaString notString = schemaString("!" + schema);
        assertThat(string("192.168.1.30").equals(string), is(true));
        assertThat(string("abc").equals(string), is(false));
        assertThat(string("192.168.1.30").equals(notString), is(false));
        assertThat(string("abc").equals(notString), is(true));
    }

    @Test
    public void shouldEqualSchemaForStringByHostnamePattern() {
        String schema = "{" + NEW_LINE +
            "    \"type\": \"string\"," + NEW_LINE +
            "    \"format\": \"hostname\"" + NEW_LINE +
            "}";
        NottableSchemaString string = schemaString(schema);
        NottableSchemaString notString = schemaString("!" + schema);
        assertThat(string("mock-server.com").equals(string), is(true));
        assertThat(string("12345").equals(string), is(false));
        assertThat(string("mock-server.com").equals(notString), is(false));
        assertThat(string("12345").equals(notString), is(true));
    }

    @Test
    public void shouldEqualSchemaForStringByDateTimePattern() {
        String schema = "{" + NEW_LINE +
            "    \"type\": \"string\"," + NEW_LINE +
            "    \"format\": \"date-time\"" + NEW_LINE +
            "}";
        NottableSchemaString string = schemaString(schema);
        NottableSchemaString notString = schemaString("!" + schema);
        assertThat(string("2018-11-13T20:20:39+00:00").equals(string), is(true));
        assertThat(string("2018-11-13 20:20:39").equals(string), is(false));
        assertThat(string("2018-11-13T20:20:39+00:00").equals(notString), is(false));
        assertThat(string("2018-11-13 20:20:39").equals(notString), is(true));
    }
}
