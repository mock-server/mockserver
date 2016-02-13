package org.mockserver.client.serialization.serializers.request;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.Test;
import org.mockserver.client.serialization.ObjectMapperFactory;
import org.mockserver.client.serialization.model.HttpRequestDTO;
import org.mockserver.model.Cookie;
import org.mockserver.model.Header;
import org.mockserver.model.JsonSchemaBody;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.JsonBody.json;
import static org.mockserver.model.JsonSchemaBody.jsonSchema;
import static org.mockserver.model.Parameter.param;
import static org.mockserver.model.ParameterBody.params;
import static org.mockserver.model.RegexBody.regex;
import static org.mockserver.model.XPathBody.xpath;
import static org.mockserver.model.XmlBody.xml;

public class HttpRequestDTOSerializerTest {

    @Test
    public void shouldReturnFormattedRequestWithNoFieldsSet() throws JsonProcessingException {
        assertThat(ObjectMapperFactory.createObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(
                        new HttpRequestDTO(
                                request()
                        )
                ),
                is("{ }"));
    }

    @Test
    public void shouldReturnFormattedRequestWithAllFieldsSet() throws JsonProcessingException {
        assertThat(ObjectMapperFactory.createObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(
                        new HttpRequestDTO(
                                request()
                                        .withMethod("GET")
                                        .withPath("/some/path")
                                        .withQueryStringParameters(param("parameterOneName", "parameterOneValue"))
                                        .withBody("some_body")
                                        .withHeaders(new Header("name", "value"))
                                        .withCookies(new Cookie("name", "[A-Z]{0,10}"))
                                        .withKeepAlive(true)
                                        .withSecure(true)
                        )
                ),
                is("{" + System.getProperty("line.separator") +
                        "  \"method\" : \"GET\"," + System.getProperty("line.separator") +
                        "  \"path\" : \"/some/path\"," + System.getProperty("line.separator") +
                        "  \"queryStringParameters\" : [ {" + System.getProperty("line.separator") +
                        "    \"name\" : \"parameterOneName\"," + System.getProperty("line.separator") +
                        "    \"values\" : [ \"parameterOneValue\" ]" + System.getProperty("line.separator") +
                        "  } ]," + System.getProperty("line.separator") +
                        "  \"headers\" : [ {" + System.getProperty("line.separator") +
                        "    \"name\" : \"name\"," + System.getProperty("line.separator") +
                        "    \"values\" : [ \"value\" ]" + System.getProperty("line.separator") +
                        "  } ]," + System.getProperty("line.separator") +
                        "  \"cookies\" : [ {" + System.getProperty("line.separator") +
                        "    \"name\" : \"name\"," + System.getProperty("line.separator") +
                        "    \"value\" : \"[A-Z]{0,10}\"" + System.getProperty("line.separator") +
                        "  } ]," + System.getProperty("line.separator") +
                        "  \"keepAlive\" : true," + System.getProperty("line.separator") +
                        "  \"secure\" : true," + System.getProperty("line.separator") +
                        "  \"body\" : \"some_body\"" + System.getProperty("line.separator") +
                        "}"));
    }

    @Test
    public void shouldReturnFormattedRequestWithJsonBodyInToString() throws JsonProcessingException {
        assertThat(ObjectMapperFactory.createObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(
                        new HttpRequestDTO(
                                request()
                                        .withMethod("GET")
                                        .withPath("/some/path")
                                        .withQueryStringParameters(param("parameterOneName", "parameterOneValue"))
                                        .withBody(json("{ \"key\": \"some_value\" }"))
                                        .withHeaders(new Header("name", "value"))
                                        .withCookies(new Cookie("name", "[A-Z]{0,10}"))
                        )
                ),
                is("{" + System.getProperty("line.separator") +
                        "  \"method\" : \"GET\"," + System.getProperty("line.separator") +
                        "  \"path\" : \"/some/path\"," + System.getProperty("line.separator") +
                        "  \"queryStringParameters\" : [ {" + System.getProperty("line.separator") +
                        "    \"name\" : \"parameterOneName\"," + System.getProperty("line.separator") +
                        "    \"values\" : [ \"parameterOneValue\" ]" + System.getProperty("line.separator") +
                        "  } ]," + System.getProperty("line.separator") +
                        "  \"headers\" : [ {" + System.getProperty("line.separator") +
                        "    \"name\" : \"name\"," + System.getProperty("line.separator") +
                        "    \"values\" : [ \"value\" ]" + System.getProperty("line.separator") +
                        "  } ]," + System.getProperty("line.separator") +
                        "  \"cookies\" : [ {" + System.getProperty("line.separator") +
                        "    \"name\" : \"name\"," + System.getProperty("line.separator") +
                        "    \"value\" : \"[A-Z]{0,10}\"" + System.getProperty("line.separator") +
                        "  } ]," + System.getProperty("line.separator") +
                        "  \"body\" : {" + System.getProperty("line.separator") +
                        "    \"type\" : \"JSON\"," + System.getProperty("line.separator") +
                        "    \"json\" : \"{ \\\"key\\\": \\\"some_value\\\" }\"" + System.getProperty("line.separator") +
                        "  }" + System.getProperty("line.separator") +
                        "}"));
    }

    @Test
    public void shouldReturnFormattedRequestWithJsonSchemaBodyInToString() throws JsonProcessingException {
        assertThat(ObjectMapperFactory.createObjectMapper().writeValueAsString(new JsonSchemaBody("{\"type\": \"object\", \"properties\": {\"id\": {\"type\": \"integer\"}}, \"required\": [\"id\"]}")),
                is("{\"type\":\"JSON_SCHEMA\",\"jsonSchema\":\"{\\\"type\\\": \\\"object\\\", \\\"properties\\\": {\\\"id\\\": {\\\"type\\\": \\\"integer\\\"}}, \\\"required\\\": [\\\"id\\\"]}\"}"));

        assertThat(ObjectMapperFactory.createObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(
                        new HttpRequestDTO(
                                request()
                                        .withMethod("GET")
                                        .withPath("/some/path")
                                        .withQueryStringParameters(param("parameterOneName", "parameterOneValue"))
                                        .withBody(jsonSchema("{\"type\": \"object\", \"properties\": {\"id\": {\"type\": \"integer\"}}, \"required\": [\"id\"]}"))
                                        .withHeaders(new Header("name", "value"))
                                        .withCookies(new Cookie("name", "[A-Z]{0,10}"))
                        )
                ),
                is("{" + System.getProperty("line.separator") +
                        "  \"method\" : \"GET\"," + System.getProperty("line.separator") +
                        "  \"path\" : \"/some/path\"," + System.getProperty("line.separator") +
                        "  \"queryStringParameters\" : [ {" + System.getProperty("line.separator") +
                        "    \"name\" : \"parameterOneName\"," + System.getProperty("line.separator") +
                        "    \"values\" : [ \"parameterOneValue\" ]" + System.getProperty("line.separator") +
                        "  } ]," + System.getProperty("line.separator") +
                        "  \"headers\" : [ {" + System.getProperty("line.separator") +
                        "    \"name\" : \"name\"," + System.getProperty("line.separator") +
                        "    \"values\" : [ \"value\" ]" + System.getProperty("line.separator") +
                        "  } ]," + System.getProperty("line.separator") +
                        "  \"cookies\" : [ {" + System.getProperty("line.separator") +
                        "    \"name\" : \"name\"," + System.getProperty("line.separator") +
                        "    \"value\" : \"[A-Z]{0,10}\"" + System.getProperty("line.separator") +
                        "  } ]," + System.getProperty("line.separator") +
                        "  \"body\" : {" + System.getProperty("line.separator") +
                        "    \"type\" : \"JSON_SCHEMA\"," + System.getProperty("line.separator") +
                        "    \"jsonSchema\" : \"{\\\"type\\\": \\\"object\\\", \\\"properties\\\": {\\\"id\\\": {\\\"type\\\": \\\"integer\\\"}}, \\\"required\\\": [\\\"id\\\"]}\"" + System.getProperty("line.separator") +
                        "  }" + System.getProperty("line.separator") +
                        "}"));
    }

    @Test
    public void shouldReturnFormattedRequestWithXPathBodyInToString() throws JsonProcessingException {
        assertThat(ObjectMapperFactory.createObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(
                        new HttpRequestDTO(
                                request()
                                        .withMethod("GET")
                                        .withPath("/some/path")
                                        .withQueryStringParameters(param("parameterOneName", "parameterOneValue"))
                                        .withBody(xpath("//some/xml/path"))
                                        .withHeaders(new Header("name", "value"))
                                        .withCookies(new Cookie("name", "[A-Z]{0,10}"))
                        )
                ),
                is("{" + System.getProperty("line.separator") +
                        "  \"method\" : \"GET\"," + System.getProperty("line.separator") +
                        "  \"path\" : \"/some/path\"," + System.getProperty("line.separator") +
                        "  \"queryStringParameters\" : [ {" + System.getProperty("line.separator") +
                        "    \"name\" : \"parameterOneName\"," + System.getProperty("line.separator") +
                        "    \"values\" : [ \"parameterOneValue\" ]" + System.getProperty("line.separator") +
                        "  } ]," + System.getProperty("line.separator") +
                        "  \"headers\" : [ {" + System.getProperty("line.separator") +
                        "    \"name\" : \"name\"," + System.getProperty("line.separator") +
                        "    \"values\" : [ \"value\" ]" + System.getProperty("line.separator") +
                        "  } ]," + System.getProperty("line.separator") +
                        "  \"cookies\" : [ {" + System.getProperty("line.separator") +
                        "    \"name\" : \"name\"," + System.getProperty("line.separator") +
                        "    \"value\" : \"[A-Z]{0,10}\"" + System.getProperty("line.separator") +
                        "  } ]," + System.getProperty("line.separator") +
                        "  \"body\" : {" + System.getProperty("line.separator") +
                        "    \"type\" : \"XPATH\"," + System.getProperty("line.separator") +
                        "    \"xpath\" : \"//some/xml/path\"" + System.getProperty("line.separator") +
                        "  }" + System.getProperty("line.separator") +
                        "}")
        );
    }

    @Test
    public void shouldReturnFormattedRequestWithXmlBodyInToString() throws JsonProcessingException {
        assertThat(ObjectMapperFactory.createObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(
                        new HttpRequestDTO(
                                request()
                                        .withMethod("GET")
                                        .withPath("/some/path")
                                        .withQueryStringParameters(param("parameterOneName", "parameterOneValue"))
                                        .withBody(xml("<some><xml></xml></some>"))
                                        .withHeaders(new Header("name", "value"))
                                        .withCookies(new Cookie("name", "[A-Z]{0,10}"))
                        )
                ),
                is("{" + System.getProperty("line.separator") +
                        "  \"method\" : \"GET\"," + System.getProperty("line.separator") +
                        "  \"path\" : \"/some/path\"," + System.getProperty("line.separator") +
                        "  \"queryStringParameters\" : [ {" + System.getProperty("line.separator") +
                        "    \"name\" : \"parameterOneName\"," + System.getProperty("line.separator") +
                        "    \"values\" : [ \"parameterOneValue\" ]" + System.getProperty("line.separator") +
                        "  } ]," + System.getProperty("line.separator") +
                        "  \"headers\" : [ {" + System.getProperty("line.separator") +
                        "    \"name\" : \"name\"," + System.getProperty("line.separator") +
                        "    \"values\" : [ \"value\" ]" + System.getProperty("line.separator") +
                        "  } ]," + System.getProperty("line.separator") +
                        "  \"cookies\" : [ {" + System.getProperty("line.separator") +
                        "    \"name\" : \"name\"," + System.getProperty("line.separator") +
                        "    \"value\" : \"[A-Z]{0,10}\"" + System.getProperty("line.separator") +
                        "  } ]," + System.getProperty("line.separator") +
                        "  \"body\" : {" + System.getProperty("line.separator") +
                        "    \"type\" : \"XML\"," + System.getProperty("line.separator") +
                        "    \"xml\" : \"<some><xml></xml></some>\"" + System.getProperty("line.separator") +
                        "  }" + System.getProperty("line.separator") +
                        "}")
        );
    }

    @Test
    public void shouldReturnFormattedRequestWithRegexBodyInToString() throws JsonProcessingException {
        assertThat(ObjectMapperFactory.createObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(
                        new HttpRequestDTO(
                                request()
                                        .withMethod("GET")
                                        .withPath("/some/path")
                                        .withQueryStringParameters(param("parameterOneName", "parameterOneValue"))
                                        .withBody(regex("[a-z]{1,3}"))
                                        .withHeaders(new Header("name", "value"))
                                        .withCookies(new Cookie("name", "[A-Z]{0,10}"))
                        )
                ),
                is("{" + System.getProperty("line.separator") +
                        "  \"method\" : \"GET\"," + System.getProperty("line.separator") +
                        "  \"path\" : \"/some/path\"," + System.getProperty("line.separator") +
                        "  \"queryStringParameters\" : [ {" + System.getProperty("line.separator") +
                        "    \"name\" : \"parameterOneName\"," + System.getProperty("line.separator") +
                        "    \"values\" : [ \"parameterOneValue\" ]" + System.getProperty("line.separator") +
                        "  } ]," + System.getProperty("line.separator") +
                        "  \"headers\" : [ {" + System.getProperty("line.separator") +
                        "    \"name\" : \"name\"," + System.getProperty("line.separator") +
                        "    \"values\" : [ \"value\" ]" + System.getProperty("line.separator") +
                        "  } ]," + System.getProperty("line.separator") +
                        "  \"cookies\" : [ {" + System.getProperty("line.separator") +
                        "    \"name\" : \"name\"," + System.getProperty("line.separator") +
                        "    \"value\" : \"[A-Z]{0,10}\"" + System.getProperty("line.separator") +
                        "  } ]," + System.getProperty("line.separator") +
                        "  \"body\" : {" + System.getProperty("line.separator") +
                        "    \"type\" : \"REGEX\"," + System.getProperty("line.separator") +
                        "    \"regex\" : \"[a-z]{1,3}\"" + System.getProperty("line.separator") +
                        "  }" + System.getProperty("line.separator") +
                        "}")
        );
    }

    @Test
    public void shouldReturnFormattedRequestWithParameterBodyInToString() throws JsonProcessingException {
        assertThat(ObjectMapperFactory.createObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(
                        new HttpRequestDTO(
                                request()
                                        .withMethod("GET")
                                        .withPath("/some/path")
                                        .withQueryStringParameters(param("parameterOneName", "parameterOneValue"))
                                        .withBody(params(
                                                param("queryStringParameterOneName", "queryStringParameterOneValueOne", "queryStringParameterOneValueTwo"),
                                                param("queryStringParameterTwoName", "queryStringParameterTwoValue")
                                        ))
                                        .withHeaders(new Header("name", "value"))
                                        .withCookies(new Cookie("name", "[A-Z]{0,10}"))
                        )
                ),
                is("{" + System.getProperty("line.separator") +
                        "  \"method\" : \"GET\"," + System.getProperty("line.separator") +
                        "  \"path\" : \"/some/path\"," + System.getProperty("line.separator") +
                        "  \"queryStringParameters\" : [ {" + System.getProperty("line.separator") +
                        "    \"name\" : \"parameterOneName\"," + System.getProperty("line.separator") +
                        "    \"values\" : [ \"parameterOneValue\" ]" + System.getProperty("line.separator") +
                        "  } ]," + System.getProperty("line.separator") +
                        "  \"headers\" : [ {" + System.getProperty("line.separator") +
                        "    \"name\" : \"name\"," + System.getProperty("line.separator") +
                        "    \"values\" : [ \"value\" ]" + System.getProperty("line.separator") +
                        "  } ]," + System.getProperty("line.separator") +
                        "  \"cookies\" : [ {" + System.getProperty("line.separator") +
                        "    \"name\" : \"name\"," + System.getProperty("line.separator") +
                        "    \"value\" : \"[A-Z]{0,10}\"" + System.getProperty("line.separator") +
                        "  } ]," + System.getProperty("line.separator") +
                        "  \"body\" : {" + System.getProperty("line.separator") +
                        "    \"type\" : \"PARAMETERS\"," + System.getProperty("line.separator") +
                        "    \"parameters\" : [ {" + System.getProperty("line.separator") +
                        "      \"name\" : \"queryStringParameterOneName\"," + System.getProperty("line.separator") +
                        "      \"values\" : [ \"queryStringParameterOneValueOne\", \"queryStringParameterOneValueTwo\" ]" + System.getProperty("line.separator") +
                        "    }, {" + System.getProperty("line.separator") +
                        "      \"name\" : \"queryStringParameterTwoName\"," + System.getProperty("line.separator") +
                        "      \"values\" : [ \"queryStringParameterTwoValue\" ]" + System.getProperty("line.separator") +
                        "    } ]" + System.getProperty("line.separator") +
                        "  }" + System.getProperty("line.separator") +
                        "}")
        );
    }

}