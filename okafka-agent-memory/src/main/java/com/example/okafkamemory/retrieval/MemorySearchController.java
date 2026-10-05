package com.example.okafkamemory.retrieval;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/memories")
public class MemorySearchController {
    private final MemorySearchService service;

    public MemorySearchController(MemorySearchService service) {
        this.service = service;
    }

    @PostMapping("/search")
    public List<MemorySearchResult> search(@RequestBody MemorySearchRequest request) {
        return service.search(request);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> badRequest(IllegalArgumentException error) {
        return Map.of("error", error.getMessage());
    }
}
