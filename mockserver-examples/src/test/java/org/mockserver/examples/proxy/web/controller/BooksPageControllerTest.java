package org.mockserver.examples.proxy.web.controller;

import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockserver.examples.proxy.model.Book;
import org.mockserver.examples.proxy.service.BookService;
import org.springframework.ui.Model;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.openMocks;

/**
 * @author jamesdbloom
 */
public class BooksPageControllerTest {

    @Mock
    private BookService bookService;
    @InjectMocks
    private BooksPageController booksPageController;

    @Before
    public void setupMocks() {
        booksPageController = new BooksPageController();

        openMocks(this);
    }

    @Test
    public void shouldLoadListOfBooks() {
        // given
        Model mockModel = mock(Model.class);
        Book[] bookList = {};
        when(bookService.getAllBooks()).thenReturn(bookList);

        // when
        String viewName = booksPageController.getBookList(mockModel);

        // then
        assertEquals("books", viewName);
        verify(mockModel).addAttribute(eq("books"), eq(bookList));
    }

    @Test
    public void shouldLoadSingleBook() {
        // given
        Model mockModel = mock(Model.class);
        Book book = new Book(1, "title", "author", "isbn", "publicationDate");
        when(bookService.getBook("1")).thenReturn(book);

        // when
        String viewName = booksPageController.getBook("1", mockModel);

        // then
        assertEquals("book", viewName);
        verify(mockModel).addAttribute(eq("book"), same(book));
    }
}
