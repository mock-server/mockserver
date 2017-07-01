package org.mockserver.client.serialization.serializers.response;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.Test;
import org.mockserver.client.serialization.ObjectMapperFactory;
import org.mockserver.client.serialization.model.HttpResponseDTO;
import org.mockserver.model.ConnectionOptions;
import org.mockserver.model.Cookie;
import org.mockserver.model.Delay;
import org.mockserver.model.Header;

import java.util.concurrent.TimeUnit;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;
import static org.mockserver.character.Character.NEW_LINE;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.JsonBody.json;
import static org.mockserver.model.XPathBody.xpath;

public class HttpResponseSerializerTest {

    @Test
    public void shouldReturnFormattedResponseWithNoFieldsSet() throws JsonProcessingException {
        assertThat(ObjectMapperFactory.createObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(new HttpResponseDTO(response())),
                is("{ }"));
    }

    @Test
    public void shouldReturnFormattedResponseWithAllFieldsSet() throws JsonProcessingException {
        assertThat(ObjectMapperFactory.createObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(
                        response()
                                .withStatusCode(302)
                                .withBody("some_body")
                                .withHeaders(new Header("header_name", "header_value"))
                                .withCookies(new Cookie("cookie_name", "cookie_value"))
                                .withDelay(new Delay(TimeUnit.MICROSECONDS, 1))
                                .withConnectionOptions(
                                        new ConnectionOptions()
                                                .withSuppressContentLengthHeader(true)
                                                .withContentLengthHeaderOverride(50)
                                                .withSuppressConnectionHeader(true)
                                                .withKeepAliveOverride(true)
                                                .withCloseSocket(true)
                                )
                ),
                is("{" + NEW_LINE +
                        "  \"statusCode\" : 302," + NEW_LINE +
                        "  \"headers\" : [ {" + NEW_LINE +
                        "    \"name\" : \"header_name\"," + NEW_LINE +
                        "    \"values\" : [ \"header_value\" ]" + NEW_LINE +
                        "  } ]," + NEW_LINE +
                        "  \"cookies\" : [ {" + NEW_LINE +
                        "    \"name\" : \"cookie_name\"," + NEW_LINE +
                        "    \"value\" : \"cookie_value\"" + NEW_LINE +
                        "  } ]," + NEW_LINE +
                        "  \"body\" : \"some_body\"," + NEW_LINE +
                        "  \"delay\" : {" + NEW_LINE +
                        "    \"timeUnit\" : \"MICROSECONDS\"," + NEW_LINE +
                        "    \"value\" : 1" + NEW_LINE +
                        "  }," + NEW_LINE +
                        "  \"connectionOptions\" : {" + NEW_LINE +
                        "    \"suppressContentLengthHeader\" : true," + NEW_LINE +
                        "    \"contentLengthHeaderOverride\" : 50," + NEW_LINE +
                        "    \"suppressConnectionHeader\" : true," + NEW_LINE +
                        "    \"keepAliveOverride\" : true," + NEW_LINE +
                        "    \"closeSocket\" : true" + NEW_LINE +
                        "  }" + NEW_LINE +
                        "}"));
    }

    @Test
    public void shouldReturnFormattedResponseWithJsonBodyInToString() throws JsonProcessingException {
        assertThat(ObjectMapperFactory.createObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(
                        response()
                                .withStatusCode(302)
                                .withBody(json("{ \"key\": \"some_value\" }"))
                                .withHeaders(new Header("header_name", "header_value"))
                                .withCookies(new Cookie("cookie_name", "cookie_value"))
                ),
                is("{" + NEW_LINE +
                        "  \"statusCode\" : 302," + NEW_LINE +
                        "  \"headers\" : [ {" + NEW_LINE +
                        "    \"name\" : \"header_name\"," + NEW_LINE +
                        "    \"values\" : [ \"header_value\" ]" + NEW_LINE +
                        "  } ]," + NEW_LINE +
                        "  \"cookies\" : [ {" + NEW_LINE +
                        "    \"name\" : \"cookie_name\"," + NEW_LINE +
                        "    \"value\" : \"cookie_value\"" + NEW_LINE +
                        "  } ]," + NEW_LINE +
                        "  \"body\" : \"{ \\\"key\\\": \\\"some_value\\\" }\"" + NEW_LINE +
                        "}"));
    }

    @Test
    public void shouldReturnFormattedResponseWithDefaultStatusCode() throws JsonProcessingException {
        assertThat(ObjectMapperFactory.createObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(
                        response()
                                .withStatusCode(200)
                                .withHeaders(new Header("header_name", "header_value"))
                                .withCookies(new Cookie("cookie_name", "cookie_value"))
                ),
                is("{" + NEW_LINE +
                        "  \"statusCode\" : 200," + NEW_LINE +
                        "  \"headers\" : [ {" + NEW_LINE +
                        "    \"name\" : \"header_name\"," + NEW_LINE +
                        "    \"values\" : [ \"header_value\" ]" + NEW_LINE +
                        "  } ]," + NEW_LINE +
                        "  \"cookies\" : [ {" + NEW_LINE +
                        "    \"name\" : \"cookie_name\"," + NEW_LINE +
                        "    \"value\" : \"cookie_value\"" + NEW_LINE +
                        "  } ]" + NEW_LINE +
                        "}")
        );
    }

}