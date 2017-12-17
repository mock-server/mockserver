package org.mockserver.examples.proxy.web.controller.pageobjects;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.mockserver.examples.proxy.model.Book;
import org.springframework.test.web.servlet.MvcResult;

import java.io.UnsupportedEncodingException;
import java.util.Collection;

import static org.junit.Assert.assertEquals;

/**
 * @author jamesdbloom
 */
public class BooksPage {
    private final Document html;

    public BooksPage(MvcResult response) throws UnsupportedEncodingException {
        html = Jsoup.parse(response.getResponse().getContentAsString());
    }

    public void containsListOfBooks(Collection<Book> books) {
        for (Book book : books) {
            Element bookRow = html.select("#" + book.getId()).first();
            assertEquals("" + book.getId(), bookRow.select(".id").text());
            assertEquals(book.getTitle(), bookRow.select(".title").text());
            assertEquals(book.getAuthor(), bookRow.select(".author").text());
        }
    }
}
