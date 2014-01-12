package org.mockserver.web.controller;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockserver.client.proxy.Times;
import org.mockserver.configuration.RootConfiguration;
import org.mockserver.integration.ClientAndProxy;
import org.mockserver.model.Parameter;
import org.mockserver.servicebackend.BackEndServiceConfiguration;
import org.mockserver.servicebackend.BookServer;
import org.mockserver.web.configuration.WebMvcConfiguration;
import org.springframework.core.env.Environment;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.ContextHierarchy;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.context.WebApplicationContext;

import javax.annotation.Resource;

import static org.mockserver.integration.ClientAndProxy.startClientAndProxy;
import static org.mockserver.model.HttpRequest.request;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

/**
 * @author jamesdbloom
 */
@RunWith(SpringJUnit4ClassRunner.class)
@WebAppConfiguration
@ContextHierarchy({
        @ContextConfiguration(
                classes = {
                        RootConfiguration.class,
                        BackEndServiceConfiguration.class
                }
        ),
        @ContextConfiguration(
                classes = {
                        WebMvcConfiguration.class
                }
        )
})
@ActiveProfiles(profiles = "dev")
public class BooksPageEndToEndIntegrationTest {

    private ClientAndProxy proxy;
    @Resource
    private Environment environment;
    @Resource
    private BookServer bookServer;
    @Resource
    private WebApplicationContext webApplicationContext;
    private MockMvc mockMvc;

    @Before
    public void setupFixture() {
        mockMvc = webAppContextSetup(webApplicationContext).build();
    }

    @Before
    public void startProxy() {
        proxy = startClientAndProxy(environment.getProperty("bookService.proxyPort", Integer.class));
        bookServer.startServer();
    }

    @Test
    public void shouldLoadListOfBooks() throws Exception {
        // when
        MvcResult response = mockMvc.perform(get("/books").accept(MediaType.TEXT_HTML))
                .andExpect(status().isOk())
                .andExpect(content().contentType("text/html;charset=UTF-8"))
                .andReturn();

        // then
        BooksPage booksPage = new BooksPage(response);
        booksPage.containsListOfBooks(bookServer.getBooksDB().values());
        proxy.verify(
                request()
                        .withPath("/get_books"),
                Times.exactly(1)
        );
    }

    @Test
    public void shouldLoadSingleBook() throws Exception {
        // when
        MvcResult response = mockMvc.perform(get("/book/1").accept(MediaType.TEXT_HTML))
                .andExpect(status().isOk())
                .andExpect(content().contentType("text/html;charset=UTF-8"))
                .andReturn();

        // then
        BookPage bookPage = new BookPage(response);
        bookPage.containsBook(bookServer.getBooksDB().get("1"));
        proxy.verify(
                request()
                        .withPath("/get_book")
                        .withParameters(
                                new Parameter("id", "1")
                        ),
                Times.exactly(1)
        );
    }

    @After
    public void stopProxy() throws Exception {
        proxy.stop();
        bookServer.stopServer();
    }

}
