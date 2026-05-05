package org.mockserver.examples.proxy.web.controller.pageobjects;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.mockserver.examples.proxy.model.Book;
import org.springframework.test.web.servlet.MvcResult;

import java.io.UnsupportedEncodingException;

import static junit.framework.TestCase.assertEquals;

/**
 * @author jamesdbloom
 */
public class BookPage {
    private final Document html;

    public BookPage(MvcResult response) throws UnsupportedEncodingException {
        html = Jsoup.parse(response.getResponse().getContentAsString());
    }

    public void containsBook(Book book) {
        assertEquals("Id:" + book.getId(), html.select("#id").text());
        assertEquals("Title:" + book.getTitle(), html.select("#title").text());
        assertEquals("Author:" + book.getAuthor(), html.select("#author").text());
        assertEquals("Publication Date:" + book.getPublicationDate(), html.select("#publicationDate").text());
        assertEquals("ISBN:" + book.getIsbn(), html.select("#isbn").text());
    }
}
