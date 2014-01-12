package org.mockserver.service;

import org.mockserver.model.Book;

/**
 * @author jamesdbloom
 */
public interface BookService {
    Book[] getAllBooks();

    Book getBook(String id);
}
