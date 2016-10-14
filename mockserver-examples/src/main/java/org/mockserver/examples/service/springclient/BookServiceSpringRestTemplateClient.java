package org.mockserver.examples.service.springclient;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.HttpHost;
import org.apache.http.client.HttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.DefaultProxyRoutePlanner;
import org.mockserver.examples.model.Book;
import org.mockserver.examples.service.BookService;
import org.springframework.core.env.Environment;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;

/**
 * @author jamesdbloom
 */
@Component
public class BookServiceSpringRestTemplateClient implements BookService {

    @Resource
    private Environment environment;
    private Integer port;
    private Integer proxyPort;
    private String host;
    private ObjectMapper objectMapper;
    private RestTemplate restTemplate;

    @PostConstruct
    private void initialise() {
        port = environment.getProperty("bookService.port", Integer.class);
        proxyPort = environment.getProperty("bookService.proxyPort", Integer.class);
        host = environment.getProperty("bookService.host", "localhost");
        objectMapper = createObjectMapper();
        restTemplate = createRestTemplate();
    }

    private ObjectMapper createObjectMapper() {
        ObjectMapper objectMapper = new ObjectMapper();
        // ignore failures
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        objectMapper.configure(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES, false);
        objectMapper.configure(DeserializationFeature.FAIL_ON_NUMBERS_FOR_ENUMS, false);
        // relax parsing
        objectMapper.configure(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY, true);
        objectMapper.configure(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT, true);
        // use arrays
        objectMapper.configure(DeserializationFeature.USE_JAVA_ARRAY_FOR_JSON_ARRAY, true);
        // remove empty values from JSON
        objectMapper.setSerializationInclusion(JsonInclude.Include.NON_DEFAULT);
        objectMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        objectMapper.setSerializationInclusion(JsonInclude.Include.NON_EMPTY);
        return objectMapper;
    }

    private RestTemplate createRestTemplate() {
        // jackson message converter
        MappingJackson2HttpMessageConverter mappingJacksonHttpMessageConverter = new MappingJackson2HttpMessageConverter();
        mappingJacksonHttpMessageConverter.setObjectMapper(objectMapper);

        // create message converters list
        List<HttpMessageConverter<?>> httpMessageConverters = new ArrayList<HttpMessageConverter<?>>();
        httpMessageConverters.add(mappingJacksonHttpMessageConverter);

        // create rest template
        RestTemplate restTemplate = new RestTemplate();
        restTemplate.setMessageConverters(httpMessageConverters);

        // configure proxy
        HttpHost httpHost = new HttpHost(System.getProperty("http.proxyHost"), Integer.parseInt(System.getProperty("http.proxyPort")));
        DefaultProxyRoutePlanner defaultProxyRoutePlanner = new DefaultProxyRoutePlanner(httpHost);
        HttpClient httpClient = HttpClients.custom().setRoutePlanner(defaultProxyRoutePlanner).build();
        restTemplate.setRequestFactory(new HttpComponentsClientHttpRequestFactory(httpClient));

        return restTemplate;
    }

    @Override
    public Book[] getAllBooks() {
        return restTemplate.getForObject("http://" + host + ":" + port + "/get_books", Book[].class);
    }

    @Override
    public Book getBook(String id) {
        return restTemplate.getForObject("http://" + host + ":" + port + "/get_book?id=" + id, Book.class);
    }
}
