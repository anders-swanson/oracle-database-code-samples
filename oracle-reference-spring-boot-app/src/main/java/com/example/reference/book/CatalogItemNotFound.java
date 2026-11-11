package com.example.reference.book;

public class CatalogItemNotFound extends RuntimeException {
    public CatalogItemNotFound(String type, long id) {
        super("%s %d was not found.".formatted(type, id));
    }
}
