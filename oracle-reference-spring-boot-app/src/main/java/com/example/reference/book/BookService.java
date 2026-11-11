package com.example.reference.book;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class BookService {
    private final BookRepository books;
    private final BookCatalogService catalog;

    public BookService(BookRepository books, BookCatalogService catalog) {
        this.books = books;
        this.catalog = catalog;
    }

    public record BookPage(List<BookView> items, int page, int size, long totalElements) {
    }

    public BookPage list(int page, int size) {
        var result = books.findAll(PageRequest.of(page, size, Sort.by("id")));
        return new BookPage(result.map(BookView::from).getContent(), page, size, result.getTotalElements());
    }

    public BookView get(long id) {
        return BookView.from(books.findById(id).orElseThrow(() -> new CatalogItemNotFound("Book", id)));
    }

    @Transactional
    public BookView create(CreateBookRequest request) {
        var authors = new LinkedHashSet<Author>();
        if (request.authorIds() != null) {
            request.authorIds().forEach(id -> authors.add(catalog.findAuthor(id)));
        }
        var publisher = request.publisherId() == null ? null : catalog.findPublisher(request.publisherId());
        return BookView.from(books.save(new Book(request.isbn(), request.title().strip(),
                authors, publisher)));
    }
}
