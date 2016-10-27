package org.mockserver.client.serialization;

import org.junit.Test;
import org.mockserver.client.serialization.model.*;
import org.mockserver.model.*;

import java.io.IOException;
import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.mockserver.model.Delay.minutes;
import static org.mockserver.model.Delay.seconds;
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
public class HttpResponseSerializerIntegrationTest {


    @Test
    public void shouldIgnoreExtraFields() throws IOException {
        // given
        String requestBytes = "{" + System.getProperty("line.separator") +
                "    \"statusCode\": \"123\"," + System.getProperty("line.separator") +
                "    \"extra_field\": \"extra_value\"" + System.getProperty("line.separator") +
                "}";

        // when
        HttpResponse httpResponse = new HttpResponseSerializer().deserialize(requestBytes);

        // then
        assertEquals(new HttpResponseDTO()
                .setStatusCode(123)
                .buildObject(), httpResponse);
    }

    @Test
    public void shouldDeserializeCompleteObject() throws IOException {
        // given
        String requestBytes = "{" + System.getProperty("line.separator") +
                "  \"statusCode\" : \"123\"," + System.getProperty("line.separator") +
                "  \"delay\" : {" + System.getProperty("line.separator") +
                "    \"timeUnit\" : \"SECONDS\"," + System.getProperty("line.separator") +
                "    \"value\" : 5" + System.getProperty("line.separator") +
                "  }," + System.getProperty("line.separator") +
                "  \"body\" : {" + System.getProperty("line.separator") +
                "    \"type\" : \"STRING\"," + System.getProperty("line.separator") +
                "    \"value\" : \"somebody\"" + System.getProperty("line.separator") +
                "  }," + System.getProperty("line.separator") +
                "  \"cookies\" : [ {" + System.getProperty("line.separator") +
                "    \"name\" : \"someCookieName\"," + System.getProperty("line.separator") +
                "    \"value\" : \"someCookieValue\"" + System.getProperty("line.separator") +
                "  } ]," + System.getProperty("line.separator") +
                "  \"headers\" : [ {" + System.getProperty("line.separator") +
                "    \"name\" : \"someHeaderName\"," + System.getProperty("line.separator") +
                "    \"values\" : [ \"someHeaderValue\" ]" + System.getProperty("line.separator") +
                "  } ]" + System.getProperty("line.separator") +
                "}";

        // when
        HttpResponse httpResponse = new HttpResponseSerializer().deserialize(requestBytes);

        // then
        assertEquals(new HttpResponseDTO()
                .setStatusCode(123)
                .setBody(BodyDTO.createDTO(new StringBody("somebody")))
                .setHeaders(Arrays.<HeaderDTO>asList(new HeaderDTO(new Header("someHeaderName", Arrays.asList("someHeaderValue")))))
                .setCookies(Arrays.<CookieDTO>asList(new CookieDTO(new Cookie("someCookieName", "someCookieValue"))))
                .setDelay(new DelayDTO(seconds(5)))
                .buildObject(), httpResponse);
    }

    @Test
    public void shouldDeserializeStringBodyShorthand() throws IOException {
        // given
        String requestBytes = "{" + System.getProperty("line.separator") +
                "  \"body\" : \"somebody\"" + System.getProperty("line.separator") +
                "}";

        // when
        HttpResponse httpResponse = new HttpResponseSerializer().deserialize(requestBytes);

        // then
        assertEquals(new HttpResponseDTO()
                .setBody(BodyDTO.createDTO(exact("somebody")))
                .buildObject(), httpResponse);
    }

    @Test
    public void shouldDeserializeStringBodyWithType() throws IOException {
        // given
        String requestBytes = "{" + System.getProperty("line.separator") +
                "  \"body\" : {" + System.getProperty("line.separator") +
                "    \"type\" : \"STRING\"," + System.getProperty("line.separator") +
                "    \"value\" : \"somebody\"" + System.getProperty("line.separator") +
                "  }" + System.getProperty("line.separator") +
                "}";

        // when
        HttpResponse httpResponse = new HttpResponseSerializer().deserialize(requestBytes);

        // then
        assertEquals(new HttpResponseDTO()
                .setBody(BodyDTO.createDTO(exact("somebody")))
                .buildObject(), httpResponse);
    }

    @Test
    public void shouldDeserializeJsonBody() throws IOException {
        // given
        String requestBytes = "{" + System.getProperty("line.separator") +
                "  \"body\" : {" + System.getProperty("line.separator") +
                "    \"type\" : \"JSON\"," + System.getProperty("line.separator") +
                "    \"value\" : \"{ \\\"key\\\": \\\"value\\\" }\"" + System.getProperty("line.separator") +
                "  }" + System.getProperty("line.separator") +
                "}";

        // when
        HttpResponse httpResponse = new HttpResponseSerializer().deserialize(requestBytes);

        // then
        HttpResponse expected = new HttpResponseDTO()
                .setBody(BodyDTO.createDTO(json("{ \"key\": \"value\" }")))
                .buildObject();
        assertEquals(expected, httpResponse);
    }

    @Test
    public void shouldDeserializeParameterBody() throws IOException {
        // given
        String requestBytes = "{" + System.getProperty("line.separator") +
                "  \"body\" : {" + System.getProperty("line.separator") +
                "    \"type\" : \"PARAMETERS\"," + System.getProperty("line.separator") +
                "    \"parameters\" : [ {" + System.getProperty("line.separator") +
                "      \"name\" : \"nameOne\"," + System.getProperty("line.separator") +
                "      \"values\" : [ \"valueOne\" ]" + System.getProperty("line.separator") +
                "    }, {" + System.getProperty("line.separator") +
                "      \"name\" : \"nameTwo\"," + System.getProperty("line.separator") +
                "      \"values\" : [ \"valueTwo_One\", \"valueTwo_Two\" ]" + System.getProperty("line.separator") +
                "    } ]" + System.getProperty("line.separator") +
                "  }" + System.getProperty("line.separator") +
                "}";

        // when
        HttpResponse httpResponse = new HttpResponseSerializer().deserialize(requestBytes);

        // then
        assertEquals(new HttpResponseDTO()
                .setBody(BodyDTO.createDTO(params(
                        new Parameter("nameOne", "valueOne"),
                        new Parameter("nameTwo", "valueTwo_One", "valueTwo_Two")
                )))
                .buildObject(), httpResponse);
    }

    @Test
    public void shouldDeserializePartialObject() throws IOException {
        // given
        String requestBytes = "{" + System.getProperty("line.separator") +
                "    \"statusCode\": \"123\"" + System.getProperty("line.separator") +
                "}";

        // when
        HttpResponse httpResponse = new HttpResponseSerializer().deserialize(requestBytes);

        // then
        assertEquals(new HttpResponseDTO()
                .setStatusCode(123)
                .buildObject(), httpResponse);
    }

    @Test
    public void shouldDeserializeAsHttpResponseField() throws IOException {
        // given
        String requestBytes = "{" + System.getProperty("line.separator") +
                "    \"httpResponse\": {" + System.getProperty("line.separator") +
                "        \"statusCode\": \"123\"," + System.getProperty("line.separator") +
                "        \"body\" : {" + System.getProperty("line.separator") +
                "          \"type\" : \"STRING\"," + System.getProperty("line.separator") +
                "          \"value\" : \"somebody\"" + System.getProperty("line.separator") +
                "        }" + System.getProperty("line.separator") +
                "    }" + System.getProperty("line.separator") +
                "}";

        // when
        HttpResponse httpResponse = new HttpResponseSerializer().deserialize(requestBytes);

        // then
        assertEquals(new HttpResponseDTO()
                .setStatusCode(123)
                .setBody(new StringBodyDTO(exact("somebody")))
                .buildObject(), httpResponse);
    }

    @Test
    public void shouldSerializeCompleteObject() throws IOException {
        // when
        String jsonHttpResponse = new HttpResponseSerializer().serialize(
                new HttpResponseDTO()
                        .setStatusCode(123)
                        .setBody(BodyDTO.createDTO(new StringBody("somebody")))
                        .setHeaders(Arrays.<HeaderDTO>asList(new HeaderDTO(new Header("someHeaderName", Arrays.asList("someHeaderValue")))))
                        .setCookies(Arrays.<CookieDTO>asList(new CookieDTO(new Cookie("someCookieName", "someCookieValue"))))
                        .setDelay(new DelayDTO(minutes(1)))
                        .buildObject()
        );

        // then
        assertEquals("{" + System.getProperty("line.separator") +
                "  \"statusCode\" : 123," + System.getProperty("line.separator") +
                "  \"headers\" : [ {" + System.getProperty("line.separator") +
                "    \"name\" : \"someHeaderName\"," + System.getProperty("line.separator") +
                "    \"values\" : [ \"someHeaderValue\" ]" + System.getProperty("line.separator") +
                "  } ]," + System.getProperty("line.separator") +
                "  \"cookies\" : [ {" + System.getProperty("line.separator") +
                "    \"name\" : \"someCookieName\"," + System.getProperty("line.separator") +
                "    \"value\" : \"someCookieValue\"" + System.getProperty("line.separator") +
                "  } ]," + System.getProperty("line.separator") +
                "  \"body\" : \"somebody\"," + System.getProperty("line.separator") +
                "  \"delay\" : {" + System.getProperty("line.separator") +
                "    \"timeUnit\" : \"MINUTES\"," + System.getProperty("line.separator") +
                "    \"value\" : 1" + System.getProperty("line.separator") +
                "  }" + System.getProperty("line.separator") +
                "}", jsonHttpResponse);
    }

    @Test
    public void shouldSerializeArray() throws IOException {
        // when
        String jsonHttpResponse = new HttpResponseSerializer().serialize(
                new HttpResponse[]{
                        new HttpResponseDTO()
                                .setStatusCode(123)
                                .setBody(BodyDTO.createDTO(new StringBody("somebody_one")))
                                .setHeaders(Arrays.<HeaderDTO>asList(new HeaderDTO(new Header("someHeaderName", Arrays.asList("someHeaderValue")))))
                                .setCookies(Arrays.<CookieDTO>asList(new CookieDTO(new Cookie("someCookieName", "someCookieValue"))))
                                .setDelay(new DelayDTO(minutes(1)))
                                .buildObject(),
                        new HttpResponseDTO()
                                .setStatusCode(456)
                                .setBody(BodyDTO.createDTO(new StringBody("somebody_two")))
                                .setHeaders(Arrays.<HeaderDTO>asList(new HeaderDTO(new Header("someHeaderName", Arrays.asList("someHeaderValue")))))
                                .setCookies(Arrays.<CookieDTO>asList(new CookieDTO(new Cookie("someCookieName", "someCookieValue"))))
                                .setDelay(new DelayDTO(minutes(1)))
                                .buildObject()
                }
        );

        // then
        assertEquals("[ {" + System.getProperty("line.separator") +
                "  \"statusCode\" : 123," + System.getProperty("line.separator") +
                "  \"headers\" : [ {" + System.getProperty("line.separator") +
                "    \"name\" : \"someHeaderName\"," + System.getProperty("line.separator") +
                "    \"values\" : [ \"someHeaderValue\" ]" + System.getProperty("line.separator") +
                "  } ]," + System.getProperty("line.separator") +
                "  \"cookies\" : [ {" + System.getProperty("line.separator") +
                "    \"name\" : \"someCookieName\"," + System.getProperty("line.separator") +
                "    \"value\" : \"someCookieValue\"" + System.getProperty("line.separator") +
                "  } ]," + System.getProperty("line.separator") +
                "  \"body\" : \"somebody_one\"," + System.getProperty("line.separator") +
                "  \"delay\" : {" + System.getProperty("line.separator") +
                "    \"timeUnit\" : \"MINUTES\"," + System.getProperty("line.separator") +
                "    \"value\" : 1" + System.getProperty("line.separator") +
                "  }" + System.getProperty("line.separator") +
                "}, {" + System.getProperty("line.separator") +
                "  \"statusCode\" : 456," + System.getProperty("line.separator") +
                "  \"headers\" : [ {" + System.getProperty("line.separator") +
                "    \"name\" : \"someHeaderName\"," + System.getProperty("line.separator") +
                "    \"values\" : [ \"someHeaderValue\" ]" + System.getProperty("line.separator") +
                "  } ]," + System.getProperty("line.separator") +
                "  \"cookies\" : [ {" + System.getProperty("line.separator") +
                "    \"name\" : \"someCookieName\"," + System.getProperty("line.separator") +
                "    \"value\" : \"someCookieValue\"" + System.getProperty("line.separator") +
                "  } ]," + System.getProperty("line.separator") +
                "  \"body\" : \"somebody_two\"," + System.getProperty("line.separator") +
                "  \"delay\" : {" + System.getProperty("line.separator") +
                "    \"timeUnit\" : \"MINUTES\"," + System.getProperty("line.separator") +
                "    \"value\" : 1" + System.getProperty("line.separator") +
                "  }" + System.getProperty("line.separator") +
                "} ]", jsonHttpResponse);
    }

    @Test
    public void shouldSerializeList() throws IOException {
        // when
        String jsonHttpResponse = new HttpResponseSerializer().serialize(
                Arrays.asList(
                        new HttpResponseDTO()
                                .setStatusCode(123)
                                .setBody(BodyDTO.createDTO(new StringBody("somebody_one")))
                                .setHeaders(Arrays.<HeaderDTO>asList(new HeaderDTO(new Header("someHeaderName", Arrays.asList("someHeaderValue")))))
                                .setCookies(Arrays.<CookieDTO>asList(new CookieDTO(new Cookie("someCookieName", "someCookieValue"))))
                                .setDelay(new DelayDTO(minutes(1)))
                                .buildObject(),
                        new HttpResponseDTO()
                                .setStatusCode(456)
                                .setBody(BodyDTO.createDTO(new StringBody("somebody_two")))
                                .setHeaders(Arrays.<HeaderDTO>asList(new HeaderDTO(new Header("someHeaderName", Arrays.asList("someHeaderValue")))))
                                .setCookies(Arrays.<CookieDTO>asList(new CookieDTO(new Cookie("someCookieName", "someCookieValue"))))
                                .setDelay(new DelayDTO(minutes(1)))
                                .buildObject()
                )
        );

        // then
        assertEquals("[ {" + System.getProperty("line.separator") +
                "  \"statusCode\" : 123," + System.getProperty("line.separator") +
                "  \"headers\" : [ {" + System.getProperty("line.separator") +
                "    \"name\" : \"someHeaderName\"," + System.getProperty("line.separator") +
                "    \"values\" : [ \"someHeaderValue\" ]" + System.getProperty("line.separator") +
                "  } ]," + System.getProperty("line.separator") +
                "  \"cookies\" : [ {" + System.getProperty("line.separator") +
                "    \"name\" : \"someCookieName\"," + System.getProperty("line.separator") +
                "    \"value\" : \"someCookieValue\"" + System.getProperty("line.separator") +
                "  } ]," + System.getProperty("line.separator") +
                "  \"body\" : \"somebody_one\"," + System.getProperty("line.separator") +
                "  \"delay\" : {" + System.getProperty("line.separator") +
                "    \"timeUnit\" : \"MINUTES\"," + System.getProperty("line.separator") +
                "    \"value\" : 1" + System.getProperty("line.separator") +
                "  }" + System.getProperty("line.separator") +
                "}, {" + System.getProperty("line.separator") +
                "  \"statusCode\" : 456," + System.getProperty("line.separator") +
                "  \"headers\" : [ {" + System.getProperty("line.separator") +
                "    \"name\" : \"someHeaderName\"," + System.getProperty("line.separator") +
                "    \"values\" : [ \"someHeaderValue\" ]" + System.getProperty("line.separator") +
                "  } ]," + System.getProperty("line.separator") +
                "  \"cookies\" : [ {" + System.getProperty("line.separator") +
                "    \"name\" : \"someCookieName\"," + System.getProperty("line.separator") +
                "    \"value\" : \"someCookieValue\"" + System.getProperty("line.separator") +
                "  } ]," + System.getProperty("line.separator") +
                "  \"body\" : \"somebody_two\"," + System.getProperty("line.separator") +
                "  \"delay\" : {" + System.getProperty("line.separator") +
                "    \"timeUnit\" : \"MINUTES\"," + System.getProperty("line.separator") +
                "    \"value\" : 1" + System.getProperty("line.separator") +
                "  }" + System.getProperty("line.separator") +
                "} ]", jsonHttpResponse);
    }

    @Test
    public void shouldSerializeStringBody() throws IOException {
        // when
        String jsonHttpResponse = new HttpResponseSerializer().serialize(
                new HttpResponseDTO()
                        .setBody(BodyDTO.createDTO(exact("somebody")))
                        .buildObject()
        );

        // then
        assertEquals("{" + System.getProperty("line.separator") +
                "  \"body\" : \"somebody\"" + System.getProperty("line.separator") +
                "}", jsonHttpResponse);
    }

    @Test
    public void shouldSerializeJsonBody() throws IOException {
        // when
        String jsonHttpResponse = new HttpResponseSerializer().serialize(
                new HttpResponseDTO()
                        .setBody(BodyDTO.createDTO(json("{ \"key\": \"value\" }")))
                        .buildObject()
        );

        // then
        assertEquals("{" + System.getProperty("line.separator") +
                "  \"body\" : \"{ \\\"key\\\": \\\"value\\\" }\"" + System.getProperty("line.separator") +
                "}", jsonHttpResponse);
    }

    @Test
    public void shouldSerializeJsonSchemaBody() throws IOException {
        // when
        String jsonHttpResponse = new HttpResponseSerializer().serialize(
                new HttpResponseDTO()
                        .setBody(BodyDTO.createDTO(jsonSchema("{ \"key\": \"value\" }")))
                        .buildObject()
        );

        // then
        assertEquals("{" + System.getProperty("line.separator") +
                "  \"body\" : \"{ \\\"key\\\": \\\"value\\\" }\"" + System.getProperty("line.separator") +
                "}", jsonHttpResponse);
    }

    @Test
    public void shouldSerializeXmlBody() throws IOException {
        // when
        String jsonHttpResponse = new HttpResponseSerializer().serialize(
                new HttpResponseDTO()
                        .setBody(BodyDTO.createDTO(xml("<some><xml></xml></some>")))
                        .buildObject()
        );

        // then
        assertEquals("{" + System.getProperty("line.separator") +
                "  \"body\" : \"<some><xml></xml></some>\"" + System.getProperty("line.separator") +
                "}", jsonHttpResponse);
    }

    @Test
    public void shouldSerializeParameterBody() throws IOException {
        // when
        String jsonHttpResponse = new HttpResponseSerializer().serialize(
                new HttpResponseDTO()
                        .setBody(BodyDTO.createDTO(params(
                                new Parameter("nameOne", "valueOne"),
                                new Parameter("nameTwo", "valueTwo_One", "valueTwo_Two")
                        )))
                        .buildObject()
        );

        // then
        assertEquals("{" + System.getProperty("line.separator") +
                "  \"body\" : \"nameOne=valueOne&nameTwo=valueTwo_One&nameTwo=valueTwo_Two\"" + System.getProperty("line.separator") +
                "}", jsonHttpResponse);
    }

    @Test
    public void shouldSerializePartialHttpResponse() throws IOException {
        // when
        String jsonHttpResponse = new HttpResponseSerializer().serialize(new HttpResponseDTO()
                        .setStatusCode(123)
                        .buildObject()
        );

        // then
        assertEquals("{" + System.getProperty("line.separator") +
                "  \"statusCode\" : 123" + System.getProperty("line.separator") +
                "}", jsonHttpResponse);
    }
}
