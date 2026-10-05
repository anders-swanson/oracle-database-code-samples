package com.example.okafkamemory.memory;

import com.example.okafkamemory.UuidBytes;

import java.util.Objects;
import java.util.UUID;

/**
 * Application-generated UUID used as the RAW(16) key for a memory row.
 */
public record MemoryId(UUID value) {
    public MemoryId {
        Objects.requireNonNull(value, "value is required");
    }

    public static MemoryId fromBytes(byte[] bytes) {
        return new MemoryId(UuidBytes.decode(bytes));
    }

    public byte[] toBytes() {
        return UuidBytes.encode(value);
    }
}
