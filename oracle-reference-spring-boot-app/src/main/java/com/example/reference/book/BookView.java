package com.example.reference.book;

import java.util.Comparator;
import java.util.List;

public record BookView(Long id, String isbn, String title,
                       List<AuthorView> authors, PublisherView publisher) {
    static BookView from(Book book) {
        var authors = book.getAuthors().stream().map(AuthorView::from)
                .sorted(Comparator.comparing(AuthorView::id)).toList();
        var publisher = book.getPublisher() == null ? null : PublisherView.from(book.getPublisher());
        return new BookView(book.getId(), book.getIsbn(), book.getTitle(),
                authors, publisher);
    }
}
