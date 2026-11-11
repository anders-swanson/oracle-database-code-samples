package com.example.reference.book;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class BookCatalogService {
    private final AuthorRepository authors;
    private final PublisherRepository publishers;

    public BookCatalogService(AuthorRepository authors, PublisherRepository publishers) {
        this.authors = authors;
        this.publishers = publishers;
    }

    public AuthorView getAuthor(long id) {
        return AuthorView.from(findAuthor(id));
    }

    public PublisherView getPublisher(long id) {
        return PublisherView.from(findPublisher(id));
    }

    @Transactional
    public AuthorView createAuthor(String name, String biography) {
        return AuthorView.from(authors.save(new Author(name.strip(), biography)));
    }

    @Transactional
    public PublisherView createPublisher(String name, String countryCode) {
        return PublisherView.from(publishers.save(new Publisher(name.strip(), countryCode)));
    }

    Author findAuthor(long id) {
        return authors.findById(id).orElseThrow(() -> new CatalogItemNotFound("Author", id));
    }

    Publisher findPublisher(long id) {
        return publishers.findById(id).orElseThrow(() -> new CatalogItemNotFound("Publisher", id));
    }
}
