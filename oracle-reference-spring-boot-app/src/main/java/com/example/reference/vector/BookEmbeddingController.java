package com.example.reference.vector;

import com.example.reference.book.BookService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
public class BookEmbeddingController {
    private final BookService books;
    private final BookEmbeddingService vectors;

    public BookEmbeddingController(BookService books, BookEmbeddingService vectors) {
        this.books = books;
        this.vectors = vectors;
    }

    public record SynopsisRequest(@NotBlank @Size(max = 2000) String synopsis) { }

    @PutMapping("/books/{id}/synopsis")
    public ResponseEntity<Void> index(@PathVariable @Positive long id, @RequestBody @Valid SynopsisRequest request) {
        books.get(id);
        vectors.index(id, request.synopsis());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/books/semantic-search")
    public List<BookEmbeddingService.SimilarBook> semanticSearch(
            @RequestParam @NotBlank @Size(max = 2000) String q,
            @RequestParam(defaultValue = "10") @Min(1) @Max(50) int limit) {
        return vectors.search(q, limit);
    }
}
