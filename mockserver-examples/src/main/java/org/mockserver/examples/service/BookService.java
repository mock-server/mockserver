package org.mockserver.examples.service;

import org.mockserver.examples.model.Book;

/**
 * @author jamesdbloom
 */
public interface BookService {
    Book[] getAllBooks();

    Book getBook(String id);
}
