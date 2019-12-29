package org.mockserver.examples.proxy.service;

import org.mockserver.examples.proxy.model.Book;
import org.mockserver.examples.proxy.service.googleclient.http.BookServiceGoogleHttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author jamesdbloom
 */
public interface BookService {

    Logger logger = LoggerFactory.getLogger(BookServiceGoogleHttpClient.class);

    Book[] getAllBooks();

    Book getBook(String id);

}
