package com.example.reference.graph;

import com.example.reference.book.BookService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
public class BookGraphController {
    private final BookService books;
    private final BookGraphService graph;

    public BookGraphController(BookService books, BookGraphService graph) {
        this.books = books;
        this.graph = graph;
    }

    @PutMapping("/books/{sourceId}/related-books/{targetId}")
    public ResponseEntity<Void> relate(@PathVariable @Positive long sourceId, @PathVariable @Positive long targetId) {
        graph.relate(sourceId, targetId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/books/{id}/related-books")
    public List<Long> related(@PathVariable @Positive long id,
                              @RequestParam(defaultValue = "100") @Min(1) @Max(100) int limit) {
        books.get(id);
        return graph.relatedBooks(id, limit);
    }
}
