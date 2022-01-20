package org.mockserver.serialization.deserializers.request;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.apache.commons.io.IOUtils;
import org.apache.commons.text.StringEscapeUtils;
import org.junit.Test;
import org.mockserver.file.FileReader;
import org.mockserver.model.*;
import org.mockserver.serialization.ObjectMapperFactory;
import org.mockserver.serialization.model.*;

import java.io.IOException;

import static junit.framework.TestCase.assertEquals;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.mockserver.character.Character.NEW_LINE;
import static org.mockserver.file.FileReader.openStreamToFileFromClassPathOrPath;
import static org.mockserver.model.BinaryBody.binary;
import static org.mockserver.model.Cookie.cookie;
import static org.mockserver.model.Header.header;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.JsonBody.json;
import static org.mockserver.model.JsonPathBody.jsonPath;
import static org.mockserver.model.JsonSchemaBody.jsonSchema;
import static org.mockserver.model.NottableString.string;
import static org.mockserver.model.OpenAPIDefinition.openAPI;
import static org.mockserver.model.Parameter.param;
import static org.mockserver.model.ParameterBody.params;
import static org.mockserver.model.RegexBody.regex;
import static org.mockserver.model.StringBody.exact;
import static org.mockserver.model.XPathBody.xpath;
import static org.mockserver.model.XmlBody.xml;
import static org.mockserver.model.XmlSchemaBody.xmlSchema;

public class RequestDefinitionDTODeserializerTest {

    @Test
    public void shouldParseJsonWithNoFieldsSetAndDefaultToHttpRequest() throws JsonProcessingException {
        assertThat(ObjectMapperFactory.createObjectMapper().readValue("{ }", RequestDefinitionDTO.class), is(new HttpRequestDTO(request())));
    }

    @Test
    public void shouldParseJsonWithOpenAPIClasspathLocationAndOperationId() throws JsonProcessingException {
        assertThat(ObjectMapperFactory.createObjectMapper().readValue("" +
            "{" + NEW_LINE +
            "  \"specUrlOrPayload\" : \"org/mockserver/mock/openapi_simple_example.json\"," + NEW_LINE +
            "  \"operationId\" : \"listPets\"" + NEW_LINE +
            "}", RequestDefinitionDTO.class), is(
            new OpenAPIDefinitionDTO(openAPI()
                .withSpecUrlOrPayload("org/mockserver/mock/openapi_simple_example.json")
                .withOperationId("listPets")
            )));
    }

    @Test
    public void shouldParseJsonWithOpenAPIClasspathLocation() throws JsonProcessingException {
        assertThat(ObjectMapperFactory.createObjectMapper().readValue("" +
            "{" + NEW_LINE +
            "  \"specUrlOrPayload\" : \"org/mockserver/mock/openapi_simple_example.json\"" + NEW_LINE +
            "}", RequestDefinitionDTO.class), is(
            new OpenAPIDefinitionDTO(openAPI()
                .withSpecUrlOrPayload("org/mockserver/mock/openapi_simple_example.json")
            )));
    }

    @Test
    public void shouldParseJsonWithOpenAPIUrlAndOperationId() throws JsonProcessingException {
        assertThat(ObjectMapperFactory.createObjectMapper().readValue("" +
            "{" + NEW_LINE +
            "  \"specUrlOrPayload\" : \"" + FileReader.getURL("org/mockserver/mock/openapi_simple_example.json").toString() + "\"," + NEW_LINE +
            "  \"operationId\" : \"listPets\"" + NEW_LINE +
            "}", RequestDefinitionDTO.class), is(
            new OpenAPIDefinitionDTO(openAPI()
                .withSpecUrlOrPayload(FileReader.getURL("org/mockserver/mock/openapi_simple_example.json").toString())
                .withOperationId("listPets")
            )));
    }

    @Test
    public void shouldParseJsonWithOpenAPIUrl() throws JsonProcessingException {
        assertThat(ObjectMapperFactory.createObjectMapper().readValue("" +
            "{" + NEW_LINE +
            "  \"specUrlOrPayload\" : \"" + FileReader.getURL("org/mockserver/mock/openapi_simple_example.json").toString() + "\"" + NEW_LINE +
            "}", RequestDefinitionDTO.class), is(
            new OpenAPIDefinitionDTO(openAPI()
                .withSpecUrlOrPayload(FileReader.getURL("org/mockserver/mock/openapi_simple_example.json").toString())
            )));
    }

    @Test
    public void shouldParseJsonWithOpenAPISpecAndOperationId() throws JsonProcessingException {
        assertThat(ObjectMapperFactory.createObjectMapper().readValue("" +
            "{" + NEW_LINE +
            "  \"specUrlOrPayload\" : " + ObjectMapperFactory.createObjectMapper().readTree(FileReader.readFileFromClassPathOrPath("org/mockserver/mock/openapi_simple_example.json")).toPrettyString().replaceAll("\\R", "\n  ") + "," + NEW_LINE +
            "  \"operationId\" : \"listPets\"" + NEW_LINE +
            "}", RequestDefinitionDTO.class), is(
            new OpenAPIDefinitionDTO(openAPI()
                .withSpecUrlOrPayload(ObjectMapperFactory.createObjectMapper().readTree(FileReader.readFileFromClassPathOrPath("org/mockserver/mock/openapi_simple_example.json")).toPrettyString())
                .withOperationId("listPets")
            )));
    }

    @Test
    public void shouldParseJsonWithOpenAPISpec() throws JsonProcessingException {
        assertThat(ObjectMapperFactory.createObjectMapper().readValue("" +
            "{" + NEW_LINE +
            "  \"specUrlOrPayload\" : " + ObjectMapperFactory.createObjectMapper().readTree(FileReader.readFileFromClassPathOrPath("org/mockserver/mock/openapi_simple_example.json")).toPrettyString().replaceAll("\\R", "\n  ") + NEW_LINE +
            "}", RequestDefinitionDTO.class), is(
            new OpenAPIDefinitionDTO(openAPI()
                .withSpecUrlOrPayload(ObjectMapperFactory.createObjectMapper().readTree(FileReader.readFileFromClassPathOrPath("org/mockserver/mock/openapi_simple_example.json")).toPrettyString())
            )));
    }

    @Test
    public void shouldDeserializeCompleteObject() throws JsonProcessingException {
        // given
        String requestBytes = "{" + NEW_LINE +
            "  \"method\" : \"someMethod\"," + NEW_LINE +
            "  \"keepAlive\" : false," + NEW_LINE +
            "  \"pathParameters\" : [ {" + NEW_LINE +
            "    \"name\" : \"path_queryParameterName\"," + NEW_LINE +
            "    \"values\" : [ \"path_queryParameterValue\" ]" + NEW_LINE +
            "  } ]," + NEW_LINE +
            "  \"queryStringParameters\" : [ {" + NEW_LINE +
            "    \"name\" : \"queryParameterName\"," + NEW_LINE +
            "    \"values\" : [ \"queryParameterValue\" ]" + NEW_LINE +
            "  } ]," + NEW_LINE +
            "  \"body\" : {" + NEW_LINE +
            "    \"type\" : \"STRING\"," + NEW_LINE +
            "    \"string\" : \"someBody\"" + NEW_LINE +
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
            "  \"secure\" : true," + NEW_LINE +
            "  \"socketAddress\" : {" + NEW_LINE +
            "    \"host\" : \"someHost\"," + NEW_LINE +
            "    \"port\" : 1234," + NEW_LINE +
            "    \"scheme\" : \"HTTPS\"" + NEW_LINE +
            "  }" + NEW_LINE +
            "}";

        // when
        RequestDefinitionDTO requestDefinitionDTO = ObjectMapperFactory.createObjectMapper().readValue(requestBytes, RequestDefinitionDTO.class);

        // then
        assertEquals(new HttpRequestDTO()
                .setMethod(string("someMethod"))
                .setPath(string("somePath"))
                .setPathParameters(new Parameters().withEntries(
                    param("path_queryParameterName", "path_queryParameterValue")
                ))
                .setQueryStringParameters(new Parameters().withEntries(
                    param("queryParameterName", "queryParameterValue")
                ))
                .setBody(new StringBodyDTO(exact("someBody")))
                .setHeaders(new Headers().withEntries(
                    header("someHeaderName", "someHeaderValue")
                ))
                .setCookies(new Cookies().withEntries(
                    cookie("someCookieName", "someCookieValue")
                ))
                .setSecure(true)
                .setKeepAlive(false)
                .setSocketAddress(new SocketAddressDTO(
                    new SocketAddress()
                        .withHost("someHost")
                        .withPort(1234)
                        .withScheme(SocketAddress.Scheme.HTTPS)
                )),
            requestDefinitionDTO);
    }

    @Test
    public void shouldDeserializeStringBodyShorthand() throws JsonProcessingException {
        // given
        String requestBytes = "{" + NEW_LINE +
            "  \"body\" : \"somebody\"" + NEW_LINE +
            "}";

        // when
        RequestDefinitionDTO requestDefinitionDTO = ObjectMapperFactory.createObjectMapper().readValue(requestBytes, RequestDefinitionDTO.class);

        // then
        assertEquals(new HttpRequestDTO()
                .setBody(BodyDTO.createDTO(exact("somebody"))),
            requestDefinitionDTO);
    }

    @Test
    public void shouldDeserializeStringBodyWithType() throws JsonProcessingException {
        // given
        String requestBytes = "{" + NEW_LINE +
            "  \"body\" : {" + NEW_LINE +
            "    \"type\" : \"STRING\"," + NEW_LINE +
            "    \"string\" : \"somebody\"" + NEW_LINE +
            "  }" + NEW_LINE +
            "}";

        // when
        RequestDefinitionDTO requestDefinitionDTO = ObjectMapperFactory.createObjectMapper().readValue(requestBytes, RequestDefinitionDTO.class);

        // then
        assertEquals(new HttpRequestDTO()
                .setBody(BodyDTO.createDTO(exact("somebody"))),
            requestDefinitionDTO);
    }

    @Test
    public void shouldDeserializeRegexBody() throws JsonProcessingException {
        // given
        String requestBytes = "{" + NEW_LINE +
            "  \"body\" : {" + NEW_LINE +
            "    \"type\" : \"REGEX\"," + NEW_LINE +
            "    \"regex\" : \"some[a-z]{3}\"" + NEW_LINE +
            "  }" + NEW_LINE +
            "}";

        // when
        RequestDefinitionDTO requestDefinitionDTO = ObjectMapperFactory.createObjectMapper().readValue(requestBytes, RequestDefinitionDTO.class);

        // then
        assertEquals(new HttpRequestDTO()
                .setBody(BodyDTO.createDTO(regex("some[a-z]{3}"))),
            requestDefinitionDTO);
    }

    @Test
    public void shouldDeserializeJsonBody() throws JsonProcessingException {
        // given
        String requestBytes = "{" + NEW_LINE +
            "  \"body\" : {" + NEW_LINE +
            "    \"type\" : \"JSON\"," + NEW_LINE +
            "    \"json\" : \"{ \\\"key\\\": \\\"value\\\" }\"" + NEW_LINE +
            "  }" + NEW_LINE +
            "}";

        // when
        RequestDefinitionDTO requestDefinitionDTO = ObjectMapperFactory.createObjectMapper().readValue(requestBytes, RequestDefinitionDTO.class);

        // then
        assertEquals(new HttpRequestDTO()
                .setBody(BodyDTO.createDTO(json("{ \"key\": \"value\" }"))),
            requestDefinitionDTO);
    }

    @Test
    public void shouldDeserializeJsonSchemaBody() throws JsonProcessingException {
        // given
        String requestBytes = "{" + NEW_LINE +
            "  \"body\" : {" + NEW_LINE +
            "    \"type\" : \"JSON_SCHEMA\"," + NEW_LINE +
            "    \"jsonSchema\" : \"{ \\\"key\\\": \\\"value\\\" }\"" + NEW_LINE +
            "  }" + NEW_LINE +
            "}";

        // when
        RequestDefinitionDTO requestDefinitionDTO = ObjectMapperFactory.createObjectMapper().readValue(requestBytes, RequestDefinitionDTO.class);

        // then
        assertEquals(new HttpRequestDTO()
                .setBody(BodyDTO.createDTO(jsonSchema("{ \"key\": \"value\" }"))),
            requestDefinitionDTO);
    }

    @Test
    public void shouldDeserializeJsonPathBody() throws JsonProcessingException {
        // given
        String requestBytes = "{" + NEW_LINE +
            "  \"body\" : {" + NEW_LINE +
            "    \"type\" : \"JSON_PATH\"," + NEW_LINE +
            "    \"jsonPath\" : \"$..book[?(@.price <= $['expensive'])]\"" + NEW_LINE +
            "  }" + NEW_LINE +
            "}";

        // when
        RequestDefinitionDTO requestDefinitionDTO = ObjectMapperFactory.createObjectMapper().readValue(requestBytes, RequestDefinitionDTO.class);

        // then
        assertEquals(new HttpRequestDTO()
                .setBody(BodyDTO.createDTO(jsonPath("$..book[?(@.price <= $['expensive'])]"))),
            requestDefinitionDTO);
    }

    @Test
    public void shouldDeserializeXmlBody() throws JsonProcessingException {
        // given
        String requestBytes = "{" + NEW_LINE +
            "  \"body\" : {" + NEW_LINE +
            "    \"type\" : \"XML\"," + NEW_LINE +
            "    \"xml\" : \"<some><xml></xml></some>\"" + NEW_LINE +
            "  }" + NEW_LINE +
            "}";

        // when
        RequestDefinitionDTO requestDefinitionDTO = ObjectMapperFactory.createObjectMapper().readValue(requestBytes, RequestDefinitionDTO.class);

        // then
        assertEquals(new HttpRequestDTO()
                .setBody(BodyDTO.createDTO(xml("<some><xml></xml></some>"))),
            requestDefinitionDTO);
    }

    @Test
    public void shouldDeserializeXmlSchemaBody() throws JsonProcessingException {
        // given
        String xmlSchema = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
            "<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\" elementFormDefault=\"qualified\" attributeFormDefault=\"unqualified\">" +
            "    <!-- XML Schema Generated from XML Document on Wed Jun 28 2017 21:52:45 GMT+0100 (BST) -->" +
            "    <!-- with XmlGrid.net Free Online Service http://xmlgrid.net -->" +
            "    <xs:element name=\"notes\">" +
            "        <xs:complexType>" +
            "            <xs:sequence>" +
            "                <xs:element name=\"note\" maxOccurs=\"unbounded\">" +
            "                    <xs:complexType>" +
            "                        <xs:sequence>" +
            "                            <xs:element name=\"to\" minOccurs=\"1\" maxOccurs=\"1\" type=\"xs:string\"></xs:element>" +
            "                            <xs:element name=\"from\" minOccurs=\"1\" maxOccurs=\"1\" type=\"xs:string\"></xs:element>" +
            "                            <xs:element name=\"heading\" minOccurs=\"1\" maxOccurs=\"1\" type=\"xs:string\"></xs:element>" +
            "                            <xs:element name=\"body\" minOccurs=\"1\" maxOccurs=\"1\" type=\"xs:string\"></xs:element>" +
            "                        </xs:sequence>" +
            "                    </xs:complexType>" +
            "                </xs:element>" +
            "            </xs:sequence>" +
            "        </xs:complexType>" +
            "    </xs:element>" +
            "</xs:schema>";
        String requestBytes = "{" + NEW_LINE +
            "  \"body\" : {" + NEW_LINE +
            "    \"type\" : \"XML_SCHEMA\"," + NEW_LINE +
            "    \"xmlSchema\" : \"" + StringEscapeUtils.escapeJava(xmlSchema) + "\"" + NEW_LINE +
            "  }" + NEW_LINE +
            "}";


        // when
        RequestDefinitionDTO requestDefinitionDTO = ObjectMapperFactory.createObjectMapper().readValue(requestBytes, RequestDefinitionDTO.class);

        // then
        assertEquals(new HttpRequestDTO()
                .setBody(BodyDTO.createDTO(xmlSchema(xmlSchema))),
            requestDefinitionDTO);
    }

    @Test
    public void shouldDeserializeXpathBody() throws JsonProcessingException {
        // given
        String requestBytes = "{" + NEW_LINE +
            "  \"body\" : {" + NEW_LINE +
            "    \"type\" : \"XPATH\"," + NEW_LINE +
            "    \"xpath\" : \"/element[key = 'some_key' and value = 'some_value']\"" + NEW_LINE +
            "  }" + NEW_LINE +
            "}";

        // when
        RequestDefinitionDTO requestDefinitionDTO = ObjectMapperFactory.createObjectMapper().readValue(requestBytes, RequestDefinitionDTO.class);

        // then
        assertEquals(new HttpRequestDTO()
                .setBody(BodyDTO.createDTO(xpath("/element[key = 'some_key' and value = 'some_value']"))),
            requestDefinitionDTO);
    }

    @Test
    public void shouldDeserializeParameterBody() throws JsonProcessingException {
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
        RequestDefinitionDTO requestDefinitionDTO = ObjectMapperFactory.createObjectMapper().readValue(requestBytes, RequestDefinitionDTO.class);

        // then
        assertEquals(new HttpRequestDTO()
                .setBody(BodyDTO.createDTO(params(
                    new Parameter("nameOne", "valueOne"),
                    new Parameter("nameTwo", "valueTwo_One", "valueTwo_Two")
                ))),
            requestDefinitionDTO);
    }

    @Test
    public void shouldDeserializeBinaryBody() throws IOException {
        // given
        String requestBytes = "{" + NEW_LINE +
            "  \"body\" : {" + NEW_LINE +
            "    \"type\" : \"BINARY\"," + NEW_LINE +
            "    \"base64Bytes\" : \"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\"" + NEW_LINE +
            "  }" + NEW_LINE +
            "}";

        // when
        RequestDefinitionDTO requestDefinitionDTO = ObjectMapperFactory.createObjectMapper().readValue(requestBytes, RequestDefinitionDTO.class);

        // then
        assertEquals(new HttpRequestDTO()
                .setBody(BodyWithContentTypeDTO.createWithContentTypeDTO(binary(IOUtils.toByteArray(openStreamToFileFromClassPathOrPath("org/mockserver/serialization/forkme_right_red.png"))))),
            requestDefinitionDTO);
    }

    @Test
    public void shouldDeserializePartialObject() throws JsonProcessingException {
        // given
        String requestBytes = "{" + NEW_LINE +
            "    \"path\": \"somePath\"" + NEW_LINE +
            "}";

        // when
        RequestDefinitionDTO requestDefinitionDTO = ObjectMapperFactory.createObjectMapper().readValue(requestBytes, RequestDefinitionDTO.class);

        // then
        assertEquals(new HttpRequestDTO()
                .setPath(string("somePath")),
            requestDefinitionDTO);
    }

    @Test
    public void shouldDeserializeAsHttpRequestField() throws JsonProcessingException {
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
        RequestDefinitionDTO requestDefinitionDTO = ObjectMapperFactory.createObjectMapper().readValue(requestBytes, RequestDefinitionDTO.class);

        // then
        assertEquals(new HttpRequestDTO()
                .setPath(string("somePath"))
                .setQueryStringParameters(new Parameters().withEntries(
                    param("queryParameterName", "queryParameterValue")
                )),
            requestDefinitionDTO);
    }

}