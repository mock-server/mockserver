package org.mockserver.examples.proxy.service;

import org.mockserver.examples.proxy.model.Book;

/**
 * @author jamesdbloom
 */
public interface BookService {
    Book[] getAllBooks();

    Book getBook(String id);
}
