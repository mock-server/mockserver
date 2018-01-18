package org.mockserver.client.serialization.serializers.request;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.Test;
import org.mockserver.client.serialization.ObjectMapperFactory;
import org.mockserver.model.Cookie;
import org.mockserver.model.Header;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;
import static org.mockserver.character.Character.NEW_LINE;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.JsonBody.json;
import static org.mockserver.model.Parameter.param;
import static org.mockserver.model.XPathBody.xpath;

public class HttpRequestSerializerTest {

    @Test
    public void shouldReturnFormattedRequestWithNoFieldsSet() throws JsonProcessingException {
        assertThat(ObjectMapperFactory.createObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(request()),
                is("{ }"));
    }

    @Test
    public void shouldReturnFormattedRequestWithAllFieldsSet() throws JsonProcessingException {
        assertThat(ObjectMapperFactory.createObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(
                        request()
                                .withMethod("GET")
                                .withPath("/some/path")
                                .withQueryStringParameters(param("parameterOneName", "parameterOneValue"))
                                .withBody("some_body")
                                .withHeaders(new Header("name", "value"))
                                .withCookies(new Cookie("name", "[A-Z]{0,10}"))
                                .withSecure(true)
                                .withKeepAlive(true)
                ),
                is("{" + NEW_LINE +
                        "  \"method\" : \"GET\"," + NEW_LINE +
                        "  \"path\" : \"/some/path\"," + NEW_LINE +
                        "  \"queryStringParameters\" : {" + NEW_LINE +
                        "    \"parameterOneName\" : [ \"parameterOneValue\" ]" + NEW_LINE +
                        "  }," + NEW_LINE +
                        "  \"headers\" : {" + NEW_LINE +
                        "    \"name\" : [ \"value\" ]" + NEW_LINE +
                        "  }," + NEW_LINE +
                        "  \"cookies\" : {" + NEW_LINE +
                        "    \"name\" : \"[A-Z]{0,10}\"" + NEW_LINE +
                        "  }," + NEW_LINE +
                        "  \"keepAlive\" : true," + NEW_LINE +
                        "  \"secure\" : true," + NEW_LINE +
                        "  \"body\" : \"some_body\"" + NEW_LINE +
                        "}"));
    }

    @Test
    public void shouldReturnFormattedRequestWithJsonBodyInToString() throws JsonProcessingException {
        assertThat(ObjectMapperFactory.createObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(request()
                        .withMethod("GET")
                        .withPath("/some/path")
                        .withQueryStringParameters(param("parameterOneName", "parameterOneValue"))
                        .withBody(json("{ \"key\": \"some_value\" }"))
                        .withHeaders(new Header("name", "value"))
                        .withCookies(new Cookie("name", "[A-Z]{0,10}"))),
                is("{" + NEW_LINE +
                        "  \"method\" : \"GET\"," + NEW_LINE +
                        "  \"path\" : \"/some/path\"," + NEW_LINE +
                        "  \"queryStringParameters\" : {" + NEW_LINE +
                        "    \"parameterOneName\" : [ \"parameterOneValue\" ]" + NEW_LINE +
                        "  }," + NEW_LINE +
                        "  \"headers\" : {" + NEW_LINE +
                        "    \"name\" : [ \"value\" ]" + NEW_LINE +
                        "  }," + NEW_LINE +
                        "  \"cookies\" : {" + NEW_LINE +
                        "    \"name\" : \"[A-Z]{0,10}\"" + NEW_LINE +
                        "  }," + NEW_LINE +
                        "  \"body\" : {" + NEW_LINE +
                        "    \"type\" : \"JSON\"," + NEW_LINE +
                        "    \"json\" : \"{ \\\"key\\\": \\\"some_value\\\" }\"" + NEW_LINE +
                        "  }" + NEW_LINE +
                        "}"));
    }

    @Test
    public void shouldReturnFormattedRequestWithXPathBodyInToString() throws JsonProcessingException {
        assertThat(ObjectMapperFactory.createObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(request()
                        .withMethod("GET")
                        .withPath("/some/path")
                        .withQueryStringParameters(param("parameterOneName", "parameterOneValue"))
                        .withBody(xpath("//some/xml/path"))
                        .withHeaders(new Header("name", "value"))
                        .withCookies(new Cookie("name", "[A-Z]{0,10}"))),
                is("{" + NEW_LINE +
                        "  \"method\" : \"GET\"," + NEW_LINE +
                        "  \"path\" : \"/some/path\"," + NEW_LINE +
                        "  \"queryStringParameters\" : {" + NEW_LINE +
                        "    \"parameterOneName\" : [ \"parameterOneValue\" ]" + NEW_LINE +
                        "  }," + NEW_LINE +
                        "  \"headers\" : {" + NEW_LINE +
                        "    \"name\" : [ \"value\" ]" + NEW_LINE +
                        "  }," + NEW_LINE +
                        "  \"cookies\" : {" + NEW_LINE +
                        "    \"name\" : \"[A-Z]{0,10}\"" + NEW_LINE +
                        "  }," + NEW_LINE +
                        "  \"body\" : {" + NEW_LINE +
                        "    \"type\" : \"XPATH\"," + NEW_LINE +
                        "    \"xpath\" : \"//some/xml/path\"" + NEW_LINE +
                        "  }" + NEW_LINE +
                        "}")
        );
    }

}
