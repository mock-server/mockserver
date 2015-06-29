package org.mockserver.client.serialization.serializers.request;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.Test;
import org.mockserver.client.serialization.ObjectMapperFactory;
import org.mockserver.client.serialization.model.HttpRequestDTO;
import org.mockserver.model.Cookie;
import org.mockserver.model.Header;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.JsonBody.json;
import static org.mockserver.model.Parameter.param;
import static org.mockserver.model.XPathBody.xpath;

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

}