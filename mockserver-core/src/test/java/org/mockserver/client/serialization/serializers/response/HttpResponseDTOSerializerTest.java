package org.mockserver.client.serialization.serializers.response;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.Test;
import org.mockserver.client.serialization.ObjectMapperFactory;
import org.mockserver.client.serialization.model.HttpResponseDTO;
import org.mockserver.model.Cookie;
import org.mockserver.model.Header;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.JsonBody.json;
import static org.mockserver.model.XPathBody.xpath;

public class HttpResponseDTOSerializerTest {

    @Test
    public void shouldReturnFormattedResponseWithNoFieldsSet() throws JsonProcessingException {
        assertThat(ObjectMapperFactory.createObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(
                        new HttpResponseDTO(
                                response()
                        )
                ),
                is("{ }"));
    }

    @Test
    public void shouldReturnFormattedResponseWithAllFieldsSet() throws JsonProcessingException {
        assertThat(ObjectMapperFactory.createObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(
                        new HttpResponseDTO(
                                response()
                                        .withStatusCode(302)
                                        .withBody("some_body")
                                        .withHeaders(new Header("header_name", "header_value"))
                                        .withCookies(new Cookie("cookie_name", "cookie_value"))
                        )
                ),
                is("{" + System.getProperty("line.separator") +
                        "  \"statusCode\" : 302," + System.getProperty("line.separator") +
                        "  \"headers\" : [ {" + System.getProperty("line.separator") +
                        "    \"name\" : \"header_name\"," + System.getProperty("line.separator") +
                        "    \"values\" : [ \"header_value\" ]" + System.getProperty("line.separator") +
                        "  } ]," + System.getProperty("line.separator") +
                        "  \"cookies\" : [ {" + System.getProperty("line.separator") +
                        "    \"name\" : \"cookie_name\"," + System.getProperty("line.separator") +
                        "    \"value\" : \"cookie_value\"" + System.getProperty("line.separator") +
                        "  } ]," + System.getProperty("line.separator") +
                        "  \"body\" : \"some_body\"" + System.getProperty("line.separator") +
                        "}"));
    }

    @Test
    public void shouldReturnFormattedResponseWithJsonBodyInToString() throws JsonProcessingException {
        assertThat(ObjectMapperFactory.createObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(
                        new HttpResponseDTO(
                                response()
                                        .withStatusCode(302)
                                        .withBody(json("{ \"key\": \"some_value\" }"))
                                        .withHeaders(new Header("header_name", "header_value"))
                                        .withCookies(new Cookie("cookie_name", "cookie_value"))
                        )
                ),
                is("{" + System.getProperty("line.separator") +
                        "  \"statusCode\" : 302," + System.getProperty("line.separator") +
                        "  \"headers\" : [ {" + System.getProperty("line.separator") +
                        "    \"name\" : \"header_name\"," + System.getProperty("line.separator") +
                        "    \"values\" : [ \"header_value\" ]" + System.getProperty("line.separator") +
                        "  } ]," + System.getProperty("line.separator") +
                        "  \"cookies\" : [ {" + System.getProperty("line.separator") +
                        "    \"name\" : \"cookie_name\"," + System.getProperty("line.separator") +
                        "    \"value\" : \"cookie_value\"" + System.getProperty("line.separator") +
                        "  } ]," + System.getProperty("line.separator") +
                        "  \"body\" : \"{ \\\"key\\\": \\\"some_value\\\" }\"" + System.getProperty("line.separator") +
                        "}"));
    }

    @Test
    public void shouldReturnFormattedResponseWithDefaultStatusCode() throws JsonProcessingException {
        assertThat(ObjectMapperFactory.createObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(
                        new HttpResponseDTO(
                                response()
                                        .withStatusCode(200)
                                        .withHeaders(new Header("header_name", "header_value"))
                                        .withCookies(new Cookie("cookie_name", "cookie_value"))
                        )
                ),
                is("{" + System.getProperty("line.separator") +
                        "  \"headers\" : [ {" + System.getProperty("line.separator") +
                        "    \"name\" : \"header_name\"," + System.getProperty("line.separator") +
                        "    \"values\" : [ \"header_value\" ]" + System.getProperty("line.separator") +
                        "  } ]," + System.getProperty("line.separator") +
                        "  \"cookies\" : [ {" + System.getProperty("line.separator") +
                        "    \"name\" : \"cookie_name\"," + System.getProperty("line.separator") +
                        "    \"value\" : \"cookie_value\"" + System.getProperty("line.separator") +
                        "  } ]" + System.getProperty("line.separator") +
                        "}")
        );
    }

    @Test
    public void shouldReturnFormattedResponseWithInvalidBody() throws JsonProcessingException {
        assertThat(ObjectMapperFactory.createObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(
                        new HttpResponseDTO(
                                response()
                                        .withBody(xpath("//some/xml/path"))
                                        .withHeaders(new Header("header_name", "header_value"))
                                        .withCookies(new Cookie("cookie_name", "cookie_value"))
                        )
                ),
                is("{" + System.getProperty("line.separator") +
                        "  \"headers\" : [ {" + System.getProperty("line.separator") +
                        "    \"name\" : \"header_name\"," + System.getProperty("line.separator") +
                        "    \"values\" : [ \"header_value\" ]" + System.getProperty("line.separator") +
                        "  } ]," + System.getProperty("line.separator") +
                        "  \"cookies\" : [ {" + System.getProperty("line.separator") +
                        "    \"name\" : \"cookie_name\"," + System.getProperty("line.separator") +
                        "    \"value\" : \"cookie_value\"" + System.getProperty("line.separator") +
                        "  } ]" + System.getProperty("line.separator") +
                        "}")
        );
    }

}