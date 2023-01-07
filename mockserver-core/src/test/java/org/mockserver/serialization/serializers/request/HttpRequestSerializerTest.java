package org.mockserver.serialization.serializers.request;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectWriter;
import org.junit.Test;
import org.mockserver.model.Cookie;
import org.mockserver.model.Header;
import org.mockserver.model.Protocol;
import org.mockserver.serialization.ObjectMapperFactory;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.mockserver.character.Character.NEW_LINE;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.JsonBody.json;
import static org.mockserver.model.Parameter.param;
import static org.mockserver.model.XPathBody.xpath;

public class HttpRequestSerializerTest {

    private final ObjectWriter objectMapper = ObjectMapperFactory.createObjectMapper(true, false);

    @Test
    public void shouldReturnJsontWithNoFieldsSet() throws JsonProcessingException {
        assertThat(objectMapper.writeValueAsString(request()),
            is("{ }"));
    }

    @Test
    public void shouldReturnJsontWithAllFieldsSet() throws JsonProcessingException {
        assertThat(objectMapper.writeValueAsString(
                request()
                    .withMethod("GET")
                    .withPath("/some/path")
                    .withPathParameters(param("path_parameterOneName", "path_parameterOneValue"))
                    .withQueryStringParameters(param("parameterOneName", "parameterOneValue"))
                    .withBody("some_body")
                    .withHeaders(new Header("name", "value"))
                    .withCookies(new Cookie("name", "[A-Z]{0,10}"))
                    .withSecure(true)
                    .withLocalAddress("local_addr:1234")
                    .withRemoteAddress("remote_addr")
                    .withKeepAlive(true)
                    .withProtocol(Protocol.HTTP_2)
            ),
            is("{" + NEW_LINE +
                "  \"method\" : \"GET\"," + NEW_LINE +
                "  \"path\" : \"/some/path\"," + NEW_LINE +
                "  \"pathParameters\" : {" + NEW_LINE +
                "    \"path_parameterOneName\" : [ \"path_parameterOneValue\" ]" + NEW_LINE +
                "  }," + NEW_LINE +
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
                "  \"protocol\" : \"HTTP_2\"," + NEW_LINE +
                "  \"localAddress\" : \"local_addr:1234\"," + NEW_LINE +
                "  \"remoteAddress\" : \"remote_addr\"," + NEW_LINE +
                "  \"body\" : \"some_body\"" + NEW_LINE +
                "}"));
    }

    @Test
    public void shouldReturnJsontWithJsonBodyInToString() throws JsonProcessingException {
        assertThat(objectMapper.writeValueAsString(request()
                .withMethod("GET")
                .withPath("/some/path")
                .withPathParameters(param("path_parameterOneName", "path_parameterOneValue"))
                .withQueryStringParameters(param("parameterOneName", "parameterOneValue"))
                .withBody(json("{ \"key\": \"some_value\" }"))
                .withHeaders(new Header("name", "value"))
                .withCookies(new Cookie("name", "[A-Z]{0,10}"))),
            is("{" + NEW_LINE +
                "  \"method\" : \"GET\"," + NEW_LINE +
                "  \"path\" : \"/some/path\"," + NEW_LINE +
                "  \"pathParameters\" : {" + NEW_LINE +
                "    \"path_parameterOneName\" : [ \"path_parameterOneValue\" ]" + NEW_LINE +
                "  }," + NEW_LINE +
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
                "    \"key\" : \"some_value\"" + NEW_LINE +
                "  }" + NEW_LINE +
                "}"));
    }

    @Test
    public void shouldReturnJsontWithXPathBodyInToString() throws JsonProcessingException {
        assertThat(objectMapper.writeValueAsString(request()
                .withMethod("GET")
                .withPath("/some/path")
                .withPathParameters(param("path_parameterOneName", "path_parameterOneValue"))
                .withQueryStringParameters(param("parameterOneName", "parameterOneValue"))
                .withBody(xpath("//some/xml/path"))
                .withHeaders(new Header("name", "value"))
                .withCookies(new Cookie("name", "[A-Z]{0,10}"))),
            is("{" + NEW_LINE +
                "  \"method\" : \"GET\"," + NEW_LINE +
                "  \"path\" : \"/some/path\"," + NEW_LINE +
                "  \"pathParameters\" : {" + NEW_LINE +
                "    \"path_parameterOneName\" : [ \"path_parameterOneValue\" ]" + NEW_LINE +
                "  }," + NEW_LINE +
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
