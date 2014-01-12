package org.mockserver.web.controller;

import org.mockserver.service.BookService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import javax.annotation.Resource;

/**
 * @author jamesdbloom
 */
@Controller
public class BooksPageController {

    @Resource
    private BookService bookService;

    @RequestMapping(value = "/books", method = RequestMethod.GET)
    public String getBookList(Model model) {
        model.addAttribute("books", bookService.getAllBooks());
        return "books";
    }

    @RequestMapping(value = "/book/{id}", method = RequestMethod.GET)
    public String getBook(@PathVariable String id, Model model) {
        model.addAttribute("book", bookService.getBook(id));
        return "book";
    }
}
