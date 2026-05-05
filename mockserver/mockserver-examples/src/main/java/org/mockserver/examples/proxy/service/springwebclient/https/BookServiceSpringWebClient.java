package org.mockserver.examples.proxy.service.springwebclient.https;

import io.netty.handler.ssl.SslContext;
import org.mockserver.examples.proxy.model.Book;
import org.mockserver.examples.proxy.service.BookService;
import org.mockserver.examples.proxy.service.ExampleNettySslContextFactory;
import org.mockserver.logging.MockServerLogger;
import org.springframework.core.env.Environment;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;
import reactor.netty.tcp.SslProvider;
import reactor.netty.transport.ProxyProvider;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;

import static org.mockserver.configuration.Configuration.configuration;

/**
 * @author jamesdbloom
 */
@Component
public class BookServiceSpringWebClient implements BookService {

    @Resource
    private Environment environment;
    private String baseUrl;
    private WebClient webClient;

    @PostConstruct
    private void initialise() {
        baseUrl = "https://" + environment.getProperty("bookService.host", "localhost") + ":" + Integer.parseInt(System.getProperty("bookService.port"));
        webClient = createWebClient();
    }

    private WebClient createWebClient() {
        SslContext sslContext = new ExampleNettySslContextFactory(configuration(), new MockServerLogger(getClass())).createClientSslContext(false);
        return WebClient.builder()
            .clientConnector(
                new ReactorClientHttpConnector(
                    HttpClient
                        .create()
                        .secure(SslProvider.builder().sslContext(sslContext).build())
                        .proxy(proxy -> proxy
                            .type(ProxyProvider.Proxy.HTTP)
                            .host(System.getProperty("http.proxyHost"))
                            .port(Integer.parseInt(System.getProperty("http.proxyPort")))
                        )
                )
            )
            .baseUrl(baseUrl)
            .build();
    }

    @Override
    @SuppressWarnings("ConstantConditions")
    public Book[] getAllBooks() {
        try {
            logger.info("Sending request to " + baseUrl + "/get_books");
            WebClient.RequestHeadersSpec<?> uri = webClient
                .get()
                .uri("/get_books");
            ResponseEntity<Book[]> responseEntity = uri.retrieve().toEntity(Book[].class).block();
            return responseEntity.getBody();
        } catch (Exception e) {
            logger.info("Exception sending request to " + baseUrl + "/get_books", e);
            throw new RuntimeException("Exception making request to retrieve all books", e);
        }
    }

    @Override
    @SuppressWarnings("ConstantConditions")
    public Book getBook(String id) {
        try {
            logger.info("Sending request to " + baseUrl + "/get_book?id=" + id);
            WebClient.RequestHeadersSpec<?> uri = webClient
                .get()
                .uri("/get_book?id=" + id);
            ResponseEntity<Book> responseEntity = uri.retrieve().toEntity(Book.class).block();
            return responseEntity.getBody();
        } catch (Exception e) {
            logger.info("Exception sending request to " + baseUrl + "/get_books", e);
            throw new RuntimeException("Exception making request to retrieve a book with id [" + id + "]", e);
        }
    }
}
