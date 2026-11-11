package com.example.reference.stock;

public class InventoryConflict extends RuntimeException {
    public InventoryConflict(String message) {
        super(message);
    }
}
