package org.jamesdbloom.mockserver.mappers;

import com.google.common.base.Function;
import com.google.common.collect.Maps;
import org.codehaus.jackson.map.ObjectMapper;
import org.jamesdbloom.mockserver.client.ExpectationDTO;
import org.jamesdbloom.mockserver.matchers.HttpRequestMatcher;
import org.jamesdbloom.mockserver.matchers.Times;
import org.jamesdbloom.mockserver.mock.Expectation;
import org.jamesdbloom.mockserver.model.HttpRequest;
import org.jamesdbloom.mockserver.model.ModelObject;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * @author jamesdbloom
 */
public class ExpectationMapper {
    private final ObjectMapper objectMapper = new ObjectMapper();

    public HttpRequestMatcher transformsToMatcher(HttpRequest httpRequest) {
        return new HttpRequestMatcher()
                .withPath(httpRequest.getPath())
                .withBody(httpRequest.getBody())
                .withHeaders(httpRequest.getHeaders())
                .withCookies(httpRequest.getCookies())
                .withQueryParameters(httpRequest.getQueryParameters())
                .withBodyParameters(httpRequest.getBodyParameters());
    }

    public String serialize(ExpectationDTO expectationDTO) {
        try {
            return objectMapper.writeValueAsString(objectToMap(expectationDTO));
        } catch (IOException ioe) {
            throw new RuntimeException(String.format("Exception while serializing expectation to JSON with value %s", expectationDTO), ioe);
        }
    }

    public Map<String, Object> objectToMap(Object object) {
        System.out.println("object = " + object);
        Map<String, Object> objectToMap = new HashMap<String, Object>();
        if (object != null) {
            for (Field field : object.getClass().getDeclaredFields()) {
                field.setAccessible(true);
                try {
                    if (!ModelObject.class.isAssignableFrom(field.getType())) {
                        objectToMap.put(field.getName(), field.get(object));
                    } else {
                        objectToMap.put(field.getName(), objectToMap(field.get(object)));
                    }
                } catch (IllegalAccessException iae) {
                    throw new RuntimeException(String.format("IllegalAccessException when getting %s on %s", field.getName(), object.getClass().getName()), iae);
                }
            }
        }
        System.out.println("objectToMap = " + objectToMap);
        return objectToMap;
    }

    public Expectation deserialize(HttpServletRequest httpServletRequest) {
        Expectation expectation;
        try {
            Map objectMap = objectMapper.readValue(httpServletRequest.getInputStream(), Map.class);
            ExpectationDTO expectationDTO = mapToObject(objectMap, new ExpectationDTO(new HttpRequest(), Times.unlimited()));
            expectation = new Expectation(transformsToMatcher(expectationDTO.getHttpRequest()), expectationDTO.getTimes()).respond(expectationDTO.getHttpResponse());
        } catch (IOException ioe) {
            new RuntimeException("Exception while parsing response for http response expectation with value of", ioe).printStackTrace();
            throw new RuntimeException("Exception while parsing response for http response expectation with value of", ioe);
        }
        return expectation;
    }

    public <T> T mapToObject(Map<String, Object> map, T object) {
        System.out.println("map = " + map);
        Map<String, Field> fields = Maps.uniqueIndex(Arrays.asList(object.getClass().getDeclaredFields()), new Function<Field, String>() {
            public String apply(Field declaredField) {
                return declaredField.getName();
            }
        });
        for (String fieldName : map.keySet()) {
            Object fieldValue = map.get(fieldName);
            Field field = fields.get(fieldName);
            field.setAccessible(true);
            try {
                if (field.getType().isAssignableFrom(fieldValue.getClass())) {
                    field.set(object, fieldValue);
                } else if (Map.class.isAssignableFrom(fieldValue.getClass())) {
                    System.out.println("fieldName = " + fieldName);
                    field.set(object, mapToObject((Map<String, Object>) fieldValue, field.get(object)));
                }
            } catch (IllegalAccessException iae) {
                throw new RuntimeException(String.format("IllegalAccessException when getting %s on %s", fieldName, object.getClass().getName()), iae);
            }
        }
        System.out.println("object = " + object);
        return object;
    }
}
