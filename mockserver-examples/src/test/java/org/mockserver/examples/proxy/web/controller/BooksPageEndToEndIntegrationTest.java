package org.mockserver.examples.proxy.web.controller;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockserver.examples.proxy.servicebackend.BookServer;
import org.mockserver.examples.proxy.web.controller.pageobjects.BookPage;
import org.mockserver.examples.proxy.web.controller.pageobjects.BooksPage;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.model.Parameter;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.context.WebApplicationContext;

import javax.annotation.Resource;

import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.stop.Stop.stopQuietly;
import static org.mockserver.verify.VerificationTimes.exactly;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

/**
 * @author jamesdbloom
 */
public abstract class BooksPageEndToEndIntegrationTest {

    private static ClientAndServer proxy;
    @Resource
    private BookServer bookServer;
    @Resource
    private WebApplicationContext webApplicationContext;
    private MockMvc mockMvc;

    @BeforeClass
    public static void startProxy() {
        proxy = ClientAndServer.startClientAndServer();
        System.setProperty("http.proxyHost", "127.0.0.1");
        System.setProperty("http.proxyPort", String.valueOf(proxy.getLocalPort()));
    }

    @AfterClass
    public static void stopProxy() {
        stopQuietly(proxy);
        System.clearProperty("http.proxyHost");
        System.clearProperty("http.proxyPort");
    }

    public void testProxyTypeEnabled() {

    }

    @Before
    public void setupFixture() {
        mockMvc = webAppContextSetup(webApplicationContext).build();
    }

    @Test
    public void shouldLoadListOfBooks() throws Exception {
        // given
        testProxyTypeEnabled();

        // when
        MvcResult response = mockMvc.perform(get("/books").accept(MediaType.TEXT_HTML))
            .andExpect(status().isOk())
            .andExpect(content().contentType("text/html; charset=utf-8"))
            .andReturn();

        // then
        BooksPage booksPage = new BooksPage(response);
        booksPage.containsListOfBooks(bookServer.getBooksDB().values());
        proxy.verify(
            request()
                .withPath("/get_books"),
            exactly(1)
        );
    }

    @Test
    public void shouldLoadSingleBook() throws Exception {
        // given
        testProxyTypeEnabled();

        // when
        MvcResult response = mockMvc.perform(get("/book/1").accept(MediaType.TEXT_HTML))
            .andExpect(status().isOk())
            .andExpect(content().contentType("text/html; charset=utf-8"))
            .andReturn();

        // then
        BookPage bookPage = new BookPage(response);
        bookPage.containsBook(bookServer.getBooksDB().get("1"));
        proxy.verify(
            request()
                .withPath("/get_book")
                .withQueryStringParameter(
                    new Parameter("id", "1")
                ),
            exactly(1)
        );
    }

}
