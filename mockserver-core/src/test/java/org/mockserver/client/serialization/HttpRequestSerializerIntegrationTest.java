package org.mockserver.client.serialization;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockserver.client.serialization.model.*;
import org.mockserver.model.*;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.mockserver.character.Character.NEW_LINE;
import static org.mockserver.model.JsonBody.json;
import static org.mockserver.model.JsonSchemaBody.jsonSchema;
import static org.mockserver.model.NottableString.string;
import static org.mockserver.model.ParameterBody.params;
import static org.mockserver.model.RegexBody.regex;
import static org.mockserver.model.StringBody.exact;
import static org.mockserver.model.XPathBody.xpath;
import static org.mockserver.model.XmlBody.xml;

/**
 * @author jamesdbloom
 */
public class HttpRequestSerializerIntegrationTest {

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Test
    public void shouldIgnoreExtraFields() throws IOException {
        // given
        String requestBytes = "{" + NEW_LINE +
                "    \"path\": \"somePath\"," + NEW_LINE +
                "    \"extra_field\": \"extra_value\"" + NEW_LINE +
                "}";

        // then
        thrown.expect(IllegalArgumentException.class);
        thrown.expectMessage("1 error:" + NEW_LINE +
                " - object instance has properties which are not allowed by the schema: [\"extra_field\"]");

        // when
        new HttpRequestSerializer().deserialize(requestBytes);
    }

    @Test
    public void shouldDeserializeCompleteObject() throws IOException {
        // given
        String requestBytes = "{" + NEW_LINE +
                "  \"method\" : \"someMethod\"," + NEW_LINE +
                "  \"keepAlive\" : false," + NEW_LINE +
                "  \"queryStringParameters\" : [ {" + NEW_LINE +
                "    \"name\" : \"queryParameterName\"," + NEW_LINE +
                "    \"values\" : [ \"queryParameterValue\" ]" + NEW_LINE +
                "  } ]," + NEW_LINE +
                "  \"body\" : {" + NEW_LINE +
                "    \"type\" : \"STRING\"," + NEW_LINE +
                "    \"string\" : \"somebody\"" + NEW_LINE +
                "  }," + NEW_LINE +
                "  \"cookies\" : [ {" + NEW_LINE +
                "    \"name\" : \"someCookieName\"," + NEW_LINE +
                "    \"value\" : \"someCookieValue\"" + NEW_LINE +
                "  } ]," + NEW_LINE +
                "  \"headers\" : [ {" + NEW_LINE +
                "    \"name\" : \"someHeaderName\"," + NEW_LINE +
                "    \"values\" : [ \"someHeaderValue\" ]" + NEW_LINE +
                "  } ]," + NEW_LINE +
                "  \"path\" : \"somePath\"," + NEW_LINE +
                "  \"secure\" : true" + NEW_LINE +
                "}";

        // when
        HttpRequest httpRequest = new HttpRequestSerializer().deserialize(requestBytes);

        // then
        assertEquals(new HttpRequestDTO()
                .setMethod(string("someMethod"))
                .setPath(string("somePath"))
                .setQueryStringParameters(Arrays.<ParameterDTO>asList((ParameterDTO) new ParameterDTO(new Parameter("queryParameterName", Arrays.asList("queryParameterValue")))))
                .setBody(BodyDTO.createDTO(new StringBody("somebody")))
                .setHeaders(Collections.<HeaderDTO>singletonList(new HeaderDTO(new Header("someHeaderName", Arrays.asList("someHeaderValue")))))
                .setCookies(Collections.<CookieDTO>singletonList(new CookieDTO(new Cookie("someCookieName", "someCookieValue"))))
                .setSecure(true)
                .setKeepAlive(false)
                .buildObject(), httpRequest);
    }

    @Test
    public void shouldDeserializeStringBodyShorthand() throws IOException {
        // given
        String requestBytes = "{" + NEW_LINE +
                "  \"body\" : \"somebody\"" + NEW_LINE +
                "}";

        // when
        HttpRequest httpRequest = new HttpRequestSerializer().deserialize(requestBytes);

        // then
        assertEquals(new HttpRequestDTO()
                .setBody(BodyDTO.createDTO(exact("somebody")))
                .buildObject(), httpRequest);
    }

    @Test
    public void shouldDeserializeStringBodyWithType() throws IOException {
        // given
        String requestBytes = "{" + NEW_LINE +
                "  \"body\" : {" + NEW_LINE +
                "    \"type\" : \"STRING\"," + NEW_LINE +
                "    \"string\" : \"somebody\"" + NEW_LINE +
                "  }" + NEW_LINE +
                "}";

        // when
        HttpRequest httpRequest = new HttpRequestSerializer().deserialize(requestBytes);

        // then
        assertEquals(new HttpRequestDTO()
                .setBody(BodyDTO.createDTO(exact("somebody")))
                .buildObject(), httpRequest);
    }

    @Test
    public void shouldDeserializeJsonBody() throws IOException {
        // given
        String requestBytes = "{" + NEW_LINE +
                "  \"body\" : {" + NEW_LINE +
                "    \"type\" : \"JSON\"," + NEW_LINE +
                "    \"json\" : \"{ \\\"key\\\": \\\"value\\\" }\"" + NEW_LINE +
                "  }" + NEW_LINE +
                "}";

        // when
        HttpRequest httpRequest = new HttpRequestSerializer().deserialize(requestBytes);

        // then
        HttpRequest expected = new HttpRequestDTO()
                .setBody(BodyDTO.createDTO(json("{ \"key\": \"value\" }")))
                .buildObject();
        assertEquals(expected, httpRequest);
    }

    @Test
    public void shouldDeserializeJsonSchemaBody() throws IOException {
        // given
        String requestBytes = "{" + NEW_LINE +
                "  \"body\" : {" + NEW_LINE +
                "    \"type\" : \"JSON_SCHEMA\"," + NEW_LINE +
                "    \"jsonSchema\" : \"{ \\\"key\\\": \\\"value\\\" }\"" + NEW_LINE +
                "  }" + NEW_LINE +
                "}";

        // when
        HttpRequest httpRequest = new HttpRequestSerializer().deserialize(requestBytes);

        // then
        assertEquals(new HttpRequestDTO()
                .setBody(BodyDTO.createDTO(jsonSchema("{ \"key\": \"value\" }")))
                .buildObject(), httpRequest);
    }

    @Test
    public void shouldDeserializeRegexBody() throws IOException {
        // given
        String requestBytes = "{" + NEW_LINE +
                "  \"body\" : {" + NEW_LINE +
                "    \"type\" : \"REGEX\"," + NEW_LINE +
                "    \"regex\" : \"some[a-z]{3}\"" + NEW_LINE +
                "  }" + NEW_LINE +
                "}";

        // when
        HttpRequest httpRequest = new HttpRequestSerializer().deserialize(requestBytes);

        // then
        assertEquals(new HttpRequestDTO()
                .setBody(BodyDTO.createDTO(regex("some[a-z]{3}")))
                .buildObject(), httpRequest);
    }

    @Test
    public void shouldDeserializeXpathBody() throws IOException {
        // given
        String requestBytes = "{" + NEW_LINE +
                "  \"body\" : {" + NEW_LINE +
                "    \"type\" : \"XPATH\"," + NEW_LINE +
                "    \"xpath\" : \"/element[key = 'some_key' and value = 'some_value']\"" + NEW_LINE +
                "  }" + NEW_LINE +
                "}";

        // when
        HttpRequest httpRequest = new HttpRequestSerializer().deserialize(requestBytes);

        // then
        assertEquals(new HttpRequestDTO()
                .setBody(BodyDTO.createDTO(xpath("/element[key = 'some_key' and value = 'some_value']")))
                .buildObject(), httpRequest);
    }

    @Test
    public void shouldDeserializeParameterBody() throws IOException {
        // given
        String requestBytes = "{" + NEW_LINE +
                "  \"body\" : {" + NEW_LINE +
                "    \"type\" : \"PARAMETERS\"," + NEW_LINE +
                "    \"parameters\" : [ {" + NEW_LINE +
                "      \"name\" : \"nameOne\"," + NEW_LINE +
                "      \"values\" : [ \"valueOne\" ]" + NEW_LINE +
                "    }, {" + NEW_LINE +
                "      \"name\" : \"nameTwo\"," + NEW_LINE +
                "      \"values\" : [ \"valueTwo_One\", \"valueTwo_Two\" ]" + NEW_LINE +
                "    } ]" + NEW_LINE +
                "  }" + NEW_LINE +
                "}";

        // when
        HttpRequest httpRequest = new HttpRequestSerializer().deserialize(requestBytes);

        // then
        assertEquals(new HttpRequestDTO()
                .setBody(BodyDTO.createDTO(params(
                        new Parameter("nameOne", "valueOne"),
                        new Parameter("nameTwo", "valueTwo_One", "valueTwo_Two")
                )))
                .buildObject(), httpRequest);
    }

    @Test
    public void shouldDeserializePartialObject() throws IOException {
        // given
        String requestBytes = "{" + NEW_LINE +
                "    \"path\": \"somePath\"" + NEW_LINE +
                "}";

        // when
        HttpRequest httpRequest = new HttpRequestSerializer().deserialize(requestBytes);

        // then
        assertEquals(new HttpRequestDTO()
                .setPath(string("somePath"))
                .buildObject(), httpRequest);
    }

    @Test
    public void shouldDeserializeAsHttpRequestField() throws IOException {
        // given
        String requestBytes = "{" + NEW_LINE +
                "    \"httpRequest\": {" + NEW_LINE +
                "        \"path\": \"somePath\"," + NEW_LINE +
                "        \"queryStringParameters\" : [ {" + NEW_LINE +
                "            \"name\" : \"queryParameterName\"," + NEW_LINE +
                "            \"values\" : [ \"queryParameterValue\" ]" + NEW_LINE +
                "        } ]" + NEW_LINE +
                "    }" + NEW_LINE +
                "}";

        // when
        HttpRequest httpRequest = new HttpRequestSerializer().deserialize(requestBytes);

        // then
        assertEquals(new HttpRequestDTO()
                .setPath(string("somePath"))
                .setQueryStringParameters(Arrays.<ParameterDTO>asList((ParameterDTO) new ParameterDTO(new Parameter("queryParameterName", Arrays.asList("queryParameterValue")))))
                .buildObject(), httpRequest);
    }

    @Test
    public void shouldSerializeCompleteObject() throws IOException {
        // when
        String jsonHttpRequest = new HttpRequestSerializer().serialize(
                new HttpRequestDTO()
                        .setMethod(string("someMethod"))
                        .setPath(string("somePath"))
                        .setQueryStringParameters(Arrays.<ParameterDTO>asList((ParameterDTO) new ParameterDTO(new Parameter("queryParameterName", Arrays.asList("queryParameterValue")))))
                        .setBody(BodyDTO.createDTO(new StringBody("somebody")))
                        .setHeaders(Collections.<HeaderDTO>singletonList(new HeaderDTO(new Header("someHeaderName", Arrays.asList("someHeaderValue")))))
                        .setCookies(Collections.<CookieDTO>singletonList(new CookieDTO(new Cookie("someCookieName", "someCookieValue"))))
                        .buildObject()
        );

        // then
        assertEquals("{" + NEW_LINE +
                "  \"method\" : \"someMethod\"," + NEW_LINE +
                "  \"path\" : \"somePath\"," + NEW_LINE +
                "  \"queryStringParameters\" : [ {" + NEW_LINE +
                "    \"name\" : \"queryParameterName\"," + NEW_LINE +
                "    \"values\" : [ \"queryParameterValue\" ]" + NEW_LINE +
                "  } ]," + NEW_LINE +
                "  \"headers\" : [ {" + NEW_LINE +
                "    \"name\" : \"someHeaderName\"," + NEW_LINE +
                "    \"values\" : [ \"someHeaderValue\" ]" + NEW_LINE +
                "  } ]," + NEW_LINE +
                "  \"cookies\" : [ {" + NEW_LINE +
                "    \"name\" : \"someCookieName\"," + NEW_LINE +
                "    \"value\" : \"someCookieValue\"" + NEW_LINE +
                "  } ]," + NEW_LINE +
                "  \"body\" : \"somebody\"" + NEW_LINE +
                "}", jsonHttpRequest);
    }

    @Test
    public void shouldSerializeArray() throws IOException {
        // when
        String jsonHttpRequest = new HttpRequestSerializer().serialize(
                new HttpRequest[]{
                        new HttpRequestDTO()
                                .setMethod(string("some_method_one"))
                                .setPath(string("some_path_one"))
                                .setBody(BodyDTO.createDTO(new StringBody("some_body_one")))
                                .setHeaders(Arrays.<HeaderDTO>asList(new HeaderDTO(new Header("some_header_name_one", Arrays.asList("some_header_value_one")))))
                                .buildObject(),
                        new HttpRequestDTO()
                                .setMethod(string("some_method_two"))
                                .setPath(string("some_path_two"))
                                .setBody(BodyDTO.createDTO(new StringBody("some_body_two")))
                                .setHeaders(Arrays.<HeaderDTO>asList(new HeaderDTO(new Header("some_header_name_two", Arrays.asList("some_header_value_two")))))
                                .buildObject()
                }
        );

        // then
        assertEquals("[ {" + NEW_LINE +
                "  \"method\" : \"some_method_one\"," + NEW_LINE +
                "  \"path\" : \"some_path_one\"," + NEW_LINE +
                "  \"headers\" : [ {" + NEW_LINE +
                "    \"name\" : \"some_header_name_one\"," + NEW_LINE +
                "    \"values\" : [ \"some_header_value_one\" ]" + NEW_LINE +
                "  } ]," + NEW_LINE +
                "  \"body\" : \"some_body_one\"" + NEW_LINE +
                "}, {" + NEW_LINE +
                "  \"method\" : \"some_method_two\"," + NEW_LINE +
                "  \"path\" : \"some_path_two\"," + NEW_LINE +
                "  \"headers\" : [ {" + NEW_LINE +
                "    \"name\" : \"some_header_name_two\"," + NEW_LINE +
                "    \"values\" : [ \"some_header_value_two\" ]" + NEW_LINE +
                "  } ]," + NEW_LINE +
                "  \"body\" : \"some_body_two\"" + NEW_LINE +
                "} ]", jsonHttpRequest);
    }

    @Test
    public void shouldSerializeList() throws IOException {
        // when
        String jsonHttpRequest = new HttpRequestSerializer().serialize(
                Arrays.asList(
                        new HttpRequestDTO()
                                .setMethod(string("some_method_one"))
                                .setPath(string("some_path_one"))
                                .setBody(BodyDTO.createDTO(new StringBody("some_body_one")))
                                .setHeaders(Arrays.<HeaderDTO>asList(new HeaderDTO(new Header("some_header_name_one", Arrays.asList("some_header_value_one")))))
                                .buildObject(),
                        new HttpRequestDTO()
                                .setMethod(string("some_method_two"))
                                .setPath(string("some_path_two"))
                                .setBody(BodyDTO.createDTO(new StringBody("some_body_two")))
                                .setHeaders(Arrays.<HeaderDTO>asList(new HeaderDTO(new Header("some_header_name_two", Arrays.asList("some_header_value_two")))))
                                .buildObject()
                )
        );

        // then
        assertEquals("[ {" + NEW_LINE +
                "  \"method\" : \"some_method_one\"," + NEW_LINE +
                "  \"path\" : \"some_path_one\"," + NEW_LINE +
                "  \"headers\" : [ {" + NEW_LINE +
                "    \"name\" : \"some_header_name_one\"," + NEW_LINE +
                "    \"values\" : [ \"some_header_value_one\" ]" + NEW_LINE +
                "  } ]," + NEW_LINE +
                "  \"body\" : \"some_body_one\"" + NEW_LINE +
                "}, {" + NEW_LINE +
                "  \"method\" : \"some_method_two\"," + NEW_LINE +
                "  \"path\" : \"some_path_two\"," + NEW_LINE +
                "  \"headers\" : [ {" + NEW_LINE +
                "    \"name\" : \"some_header_name_two\"," + NEW_LINE +
                "    \"values\" : [ \"some_header_value_two\" ]" + NEW_LINE +
                "  } ]," + NEW_LINE +
                "  \"body\" : \"some_body_two\"" + NEW_LINE +
                "} ]", jsonHttpRequest);
    }

    @Test
    public void shouldSerializeStringBody() throws IOException {
        // when
        String jsonHttpRequest = new HttpRequestSerializer().serialize(
                new HttpRequestDTO()
                        .setBody(BodyDTO.createDTO(exact("somebody")))
                        .buildObject()
        );

        // then
        assertEquals("{" + NEW_LINE +
                "  \"body\" : \"somebody\"" + NEW_LINE +
                "}", jsonHttpRequest);
    }

    @Test
    public void shouldSerializeJsonBody() throws IOException {
        // when
        String jsonHttpRequest = new HttpRequestSerializer().serialize(
                new HttpRequestDTO()
                        .setBody(BodyDTO.createDTO(json("{ \"key\": \"value\" }")))
                        .buildObject()
        );

        // then
        assertEquals("{" + NEW_LINE +
                "  \"body\" : {" + NEW_LINE +
                "    \"type\" : \"JSON\"," + NEW_LINE +
                "    \"json\" : \"{ \\\"key\\\": \\\"value\\\" }\"" + NEW_LINE +
                "  }" + NEW_LINE +
                "}", jsonHttpRequest);
    }

    @Test
    public void shouldSerializeRegexBody() throws IOException {
        // when
        String jsonHttpRequest = new HttpRequestSerializer().serialize(
                new HttpRequestDTO()
                        .setBody(BodyDTO.createDTO(regex("some[a-z]{3}")))
                        .buildObject()
        );

        // then
        assertEquals("{" + NEW_LINE +
                "  \"body\" : {" + NEW_LINE +
                "    \"type\" : \"REGEX\"," + NEW_LINE +
                "    \"regex\" : \"some[a-z]{3}\"" + NEW_LINE +
                "  }" + NEW_LINE +
                "}", jsonHttpRequest);
    }

    @Test
    public void shouldSerializeXpathBody() throws IOException {
        // when
        String jsonHttpRequest = new HttpRequestSerializer().serialize(
                new HttpRequestDTO()
                        .setBody(BodyDTO.createDTO(xpath("/element[key = 'some_key' and value = 'some_value']")))
                        .buildObject()
        );

        // then
        assertEquals("{" + NEW_LINE +
                "  \"body\" : {" + NEW_LINE +
                "    \"type\" : \"XPATH\"," + NEW_LINE +
                "    \"xpath\" : \"/element[key = 'some_key' and value = 'some_value']\"" + NEW_LINE +
                "  }" + NEW_LINE +
                "}", jsonHttpRequest);
    }

    @Test
    public void shouldSerializeXmlBody() throws IOException {
        // when
        String jsonHttpRequest = new HttpRequestSerializer().serialize(
                new HttpRequestDTO()
                        .setBody(BodyDTO.createDTO(xml("<some><xml></xml></some>")))
                        .buildObject()
        );

        // then
        assertEquals("{" + NEW_LINE +
                "  \"body\" : {" + NEW_LINE +
                "    \"type\" : \"XML\"," + NEW_LINE +
                "    \"xml\" : \"<some><xml></xml></some>\"" + NEW_LINE +
                "  }" + NEW_LINE +
                "}", jsonHttpRequest);
    }

    @Test
    public void shouldSerializeParameterBody() throws IOException {
        // when
        String jsonHttpRequest = new HttpRequestSerializer().serialize(
                new HttpRequestDTO()
                        .setBody(BodyDTO.createDTO(params(
                                new Parameter("nameOne", "valueOne"),
                                new Parameter("nameTwo", "valueTwo_One", "valueTwo_Two")
                        )))
                        .buildObject()
        );

        // then
        assertEquals("{" + NEW_LINE +
                "  \"body\" : {" + NEW_LINE +
                "    \"type\" : \"PARAMETERS\"," + NEW_LINE +
                "    \"parameters\" : [ {" + NEW_LINE +
                "      \"name\" : \"nameOne\"," + NEW_LINE +
                "      \"values\" : [ \"valueOne\" ]" + NEW_LINE +
                "    }, {" + NEW_LINE +
                "      \"name\" : \"nameTwo\"," + NEW_LINE +
                "      \"values\" : [ \"valueTwo_One\", \"valueTwo_Two\" ]" + NEW_LINE +
                "    } ]" + NEW_LINE +
                "  }" + NEW_LINE +
                "}", jsonHttpRequest);
    }

    @Test
    public void shouldSerializePartialHttpRequest() throws IOException {
        // when
        String jsonHttpRequest = new HttpRequestSerializer().serialize(new HttpRequestDTO()
                        .setPath(string("somePath"))
                        .buildObject()
        );

        // then
        assertEquals("{" + NEW_LINE +
                "  \"path\" : \"somePath\"" + NEW_LINE +
                "}", jsonHttpRequest);
    }
}
