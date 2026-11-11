package com.example.reference.stock;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "store_stock")
public class Stock {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private long storeId;
    private long bookId;
    private int quantity;
    @Version
    private Long version;

    protected Stock() { }

    Stock(long storeId, long bookId, int quantity) {
        this.storeId = storeId;
        this.bookId = bookId;
        this.quantity = quantity;
    }

    void adjust(int delta) {
        long updated = (long) quantity + delta;
        if (delta == 0 || updated < 0 || updated > 1_000_000) {
            throw new InventoryConflict("The adjustment must be nonzero and leave quantity between 0 and 1000000.");
        }
        quantity = (int) updated;
    }

    public Long getId() { return id; }
    public long getStoreId() { return storeId; }
    public long getBookId() { return bookId; }
    public int getQuantity() { return quantity; }
    public Long getVersion() { return version; }
}
