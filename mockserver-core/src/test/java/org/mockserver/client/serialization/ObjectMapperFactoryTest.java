package org.mockserver.client.serialization;

import org.junit.Test;
import org.mockserver.client.serialization.model.*;
import org.mockserver.matchers.Times;
import org.mockserver.model.*;

import javax.xml.bind.DatatypeConverter;
import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;

/**
 * @author jamesdbloom
 */
public class ObjectMapperFactoryTest {


    @Test
    public void shouldDeserializeCompleteObject() throws IOException {
        // given
        String json = ("{" + System.getProperty("line.separator") +
                "  \"httpRequest\" : {" + System.getProperty("line.separator") +
                "    \"method\" : \"someMethod\"," + System.getProperty("line.separator") +
                "    \"path\" : \"somePath\"," + System.getProperty("line.separator") +
                "    \"queryStringParameters\" : [ {" + System.getProperty("line.separator") +
                "      \"name\" : \"queryStringParameterNameOne\"," + System.getProperty("line.separator") +
                "      \"values\" : [ \"queryStringParameterValueOne_One\", \"queryStringParameterValueOne_Two\" ]" + System.getProperty("line.separator") +
                "    }, {" + System.getProperty("line.separator") +
                "      \"name\" : \"queryStringParameterNameTwo\"," + System.getProperty("line.separator") +
                "      \"values\" : [ \"queryStringParameterValueTwo_One\" ]" + System.getProperty("line.separator") +
                "    } ]," + System.getProperty("line.separator") +
                "    \"body\" : {" + System.getProperty("line.separator") +
                "      \"type\" : \"STRING\"," + System.getProperty("line.separator") +
                "      \"string\" : \"someBody\"" + System.getProperty("line.separator") +
                "    }," + System.getProperty("line.separator") +
                "    \"cookies\" : [ {" + System.getProperty("line.separator") +
                "      \"name\" : \"someCookieName\"," + System.getProperty("line.separator") +
                "      \"value\" : \"someCookieValue\"" + System.getProperty("line.separator") +
                "    } ]," + System.getProperty("line.separator") +
                "    \"headers\" : [ {" + System.getProperty("line.separator") +
                "      \"name\" : \"someHeaderName\"," + System.getProperty("line.separator") +
                "      \"values\" : [ \"someHeaderValue\" ]" + System.getProperty("line.separator") +
                "    } ]" + System.getProperty("line.separator") +
                "  }," + System.getProperty("line.separator") +
                "  \"httpResponse\" : {" + System.getProperty("line.separator") +
                "    \"statusCode\" : 304," + System.getProperty("line.separator") +
                "    \"body\" : \"someBody\"," + System.getProperty("line.separator") +
                "    \"cookies\" : [ {" + System.getProperty("line.separator") +
                "      \"name\" : \"someCookieName\"," + System.getProperty("line.separator") +
                "      \"value\" : \"someCookieValue\"" + System.getProperty("line.separator") +
                "    } ]," + System.getProperty("line.separator") +
                "    \"headers\" : [ {" + System.getProperty("line.separator") +
                "      \"name\" : \"someHeaderName\"," + System.getProperty("line.separator") +
                "      \"values\" : [ \"someHeaderValue\" ]" + System.getProperty("line.separator") +
                "    } ]," + System.getProperty("line.separator") +
                "    \"delay\" : {" + System.getProperty("line.separator") +
                "      \"timeUnit\" : \"MICROSECONDS\"," + System.getProperty("line.separator") +
                "      \"value\" : 1" + System.getProperty("line.separator") +
                "    }" + System.getProperty("line.separator") +
                "  }," + System.getProperty("line.separator") +
                "  \"times\" : {" + System.getProperty("line.separator") +
                "    \"remainingTimes\" : 5," + System.getProperty("line.separator") +
                "    \"unlimited\" : false" + System.getProperty("line.separator") +
                "  }" + System.getProperty("line.separator") +
                "}");

        // when
        ExpectationDTO expectationDTO = ObjectMapperFactory.createObjectMapper().readValue(json, ExpectationDTO.class);

        // then
        assertEquals(new ExpectationDTO()
                .setHttpRequest(
                        new HttpRequestDTO()
                                .setMethod("someMethod")
                                .setPath("somePath")
                                .setQueryStringParameters(Arrays.asList(
                                        new ParameterDTO(new Parameter("queryStringParameterNameOne", "queryStringParameterValueOne_One", "queryStringParameterValueOne_Two")),
                                        new ParameterDTO(new Parameter("queryStringParameterNameTwo", "queryStringParameterValueTwo_One"))
                                ))
                                .setBody(new StringBodyDTO(new StringBody("someBody")))
                                .setHeaders(Arrays.<HeaderDTO>asList(new HeaderDTO(new Header("someHeaderName", Arrays.asList("someHeaderValue")))))
                                .setCookies(Arrays.<CookieDTO>asList(new CookieDTO(new Cookie("someCookieName", "someCookieValue"))))
                )
                .setHttpResponse(
                        new HttpResponseDTO()
                                .setStatusCode(304)
                                .setBody(new StringBodyDTO(new StringBody("someBody")))
                                .setHeaders(Arrays.<HeaderDTO>asList(new HeaderDTO(new Header("someHeaderName", Arrays.asList("someHeaderValue")))))
                                .setCookies(Arrays.<CookieDTO>asList(new CookieDTO(new Cookie("someCookieName", "someCookieValue"))))
                                .setDelay(
                                        new DelayDTO()
                                                .setTimeUnit(TimeUnit.MICROSECONDS)
                                                .setValue(1)
                                )
                )
                .setTimes(new TimesDTO(Times.exactly(5))), expectationDTO);
    }

}
