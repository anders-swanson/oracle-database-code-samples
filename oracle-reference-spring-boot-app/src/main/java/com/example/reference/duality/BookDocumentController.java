package com.example.reference.duality;

import jakarta.validation.constraints.Positive;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class BookDocumentController {
    private final BookDocumentService documents;

    public BookDocumentController(BookDocumentService documents) {
        this.documents = documents;
    }

    @GetMapping(value = "/books/{id}/document", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> document(@PathVariable @Positive long id) {
        return documents.find(id).map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }
}
