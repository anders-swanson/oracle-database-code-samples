package com.example.reference.book;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequestMapping("/api/books")
public class BookController {
    private final BookService books;

    public BookController(BookService books) {
        this.books = books;
    }

    @GetMapping
    public BookService.BookPage list(@RequestParam(defaultValue = "0") @Min(0) int page,
                                     @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return books.list(page, size);
    }

    @GetMapping("/{id}")
    public BookView get(@PathVariable @Positive long id) {
        return books.get(id);
    }

    @PostMapping
    public ResponseEntity<BookView> create(@RequestBody @Valid CreateBookRequest request) {
        BookView book = books.create(request);
        return ResponseEntity.created(URI.create("/api/books/" + book.id())).body(book);
    }
}
