package org.mockserver.examples.proxy.model;

/**
 * @author jamesdbloom
 */
public class Book {
    private int id;
    private String title;
    private String author;
    private String isbn;
    private String publicationDate;

    public Book(int id, String title, String author, String isbn, String publicationDate) {
        this.id = id;
        this.title = title;
        this.author = author;
        this.isbn = isbn;
        this.publicationDate = publicationDate;
    }

    protected Book() {

    }

    public int getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getAuthor() {
        return author;
    }

    public String getIsbn() {
        return isbn;
    }

    public String getPublicationDate() {
        return publicationDate;
    }
}
