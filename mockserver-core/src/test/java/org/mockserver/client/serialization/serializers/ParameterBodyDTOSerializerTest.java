package org.mockserver.client.serialization.serializers;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.Test;
import org.mockserver.client.serialization.ObjectMapperFactory;
import org.mockserver.client.serialization.model.ParameterBodyDTO;
import org.mockserver.client.serialization.model.XPathBodyDTO;
import org.mockserver.model.XPathBody;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;
import static org.mockserver.model.Parameter.param;
import static org.mockserver.model.ParameterBody.params;

public class ParameterBodyDTOSerializerTest {

    @Test
    public void shouldSerializeXPathBodyDTO() throws JsonProcessingException {
        assertThat(ObjectMapperFactory.createObjectMapper().writeValueAsString(new ParameterBodyDTO(params(
                        param("queryStringParameterOneName", "queryStringParameterOneValueOne", "queryStringParameterOneValueTwo"),
                        param("queryStringParameterTwoName", "queryStringParameterTwoValue")
                ), false)),
                is("{" +
                        "\"type\":\"PARAMETERS\"," +
                        "\"parameters\":[" +
                        "{\"name\":\"queryStringParameterOneName\",\"values\":[\"queryStringParameterOneValueOne\",\"queryStringParameterOneValueTwo\"]}," +
                        "{\"name\":\"queryStringParameterTwoName\",\"values\":[\"queryStringParameterTwoValue\"]}" +
                        "]" +
                        "}"));
    }

    @Test
    public void shouldSerializeXPathBodyDTOWithNot() throws JsonProcessingException {
        assertThat(ObjectMapperFactory.createObjectMapper().writeValueAsString(new ParameterBodyDTO(params(
                        param("queryStringParameterOneName", "queryStringParameterOneValueOne", "queryStringParameterOneValueTwo"),
                        param("queryStringParameterTwoName", "queryStringParameterTwoValue")
                ), true)),
                is("{" +
                        "\"not\":true," +
                        "\"type\":\"PARAMETERS\"," +
                        "\"parameters\":[" +
                        "{\"name\":\"queryStringParameterOneName\",\"values\":[\"queryStringParameterOneValueOne\",\"queryStringParameterOneValueTwo\"]}," +
                        "{\"name\":\"queryStringParameterTwoName\",\"values\":[\"queryStringParameterTwoValue\"]}" +
                        "]" +
                        "}"));
    }

    @Test
    public void shouldSerializeXPathBodyDTOWithNotParameter() throws JsonProcessingException {
        assertThat(ObjectMapperFactory.createObjectMapper().writeValueAsString(new ParameterBodyDTO(params(
                        org.mockserver.model.Not.not(param("queryStringParameterOneName", "queryStringParameterOneValueOne", "queryStringParameterOneValueTwo")),
                        param("queryStringParameterTwoName", "queryStringParameterTwoValue")
                ), false)),
                is("{" +
                        "\"type\":\"PARAMETERS\"," +
                        "\"parameters\":[" +
                        "{\"not\":true,\"name\":\"queryStringParameterOneName\",\"values\":[\"queryStringParameterOneValueOne\",\"queryStringParameterOneValueTwo\"]}," +
                        "{\"name\":\"queryStringParameterTwoName\",\"values\":[\"queryStringParameterTwoValue\"]}" +
                        "]" +
                        "}"));
    }

}