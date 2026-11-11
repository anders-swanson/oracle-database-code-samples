package com.example.reference.book;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequestMapping("/api")
public class BookCatalogController {
    private final BookCatalogService catalog;

    public BookCatalogController(BookCatalogService catalog) {
        this.catalog = catalog;
    }

    public record AuthorRequest(@NotBlank @Size(max = 100) String name, @Size(max = 800) String biography) { }
    public record PublisherRequest(@NotBlank @Size(max = 120) String name,
                                   @Pattern(regexp = "[A-Z]{2}") String countryCode) { }

    @PostMapping("/authors")
    public ResponseEntity<AuthorView> createAuthor(@RequestBody @Valid AuthorRequest request) {
        var author = catalog.createAuthor(request.name(), request.biography());
        return ResponseEntity.created(URI.create("/api/authors/" + author.id())).body(author);
    }

    @GetMapping("/authors/{id}")
    public AuthorView getAuthor(@PathVariable @Positive long id) {
        return catalog.getAuthor(id);
    }

    @PostMapping("/publishers")
    public ResponseEntity<PublisherView> createPublisher(@RequestBody @Valid PublisherRequest request) {
        var publisher = catalog.createPublisher(request.name(), request.countryCode());
        return ResponseEntity.created(URI.create("/api/publishers/" + publisher.id())).body(publisher);
    }

    @GetMapping("/publishers/{id}")
    public PublisherView getPublisher(@PathVariable @Positive long id) {
        return catalog.getPublisher(id);
    }
}
