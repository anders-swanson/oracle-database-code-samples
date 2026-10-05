package com.example.okafkamemory;

import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.UUID;

public final class UuidBytes {
    private UuidBytes() {
    }

    public static byte[] encode(UUID value) {
        Objects.requireNonNull(value, "UUID is required");
        return ByteBuffer.allocate(16)
                .putLong(value.getMostSignificantBits())
                .putLong(value.getLeastSignificantBits())
                .array();
    }

    public static UUID decode(byte[] bytes) {
        Objects.requireNonNull(bytes, "bytes are required");
        if (bytes.length != 16) {
            throw new IllegalArgumentException("Expected 16 bytes for a UUID but found " + bytes.length);
        }
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        return new UUID(buffer.getLong(), buffer.getLong());
    }
}
