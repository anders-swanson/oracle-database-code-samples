package com.example.reference.book;

public record AuthorView(Long id, String name, String biography) {
    static AuthorView from(Author author) {
        return new AuthorView(author.getId(), author.getName(), author.getBiography());
    }
}
