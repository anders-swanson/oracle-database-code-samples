package com.example.memory;

import com.example.memory.model.MemoryDocument;
import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

public final class SeedMemories {
    private static final String RESOURCE = "/memories.json";

    public static List<MemoryDocument> records() {
        try (InputStream stream = SeedMemories.class.getResourceAsStream(RESOURCE)) {
            if (stream == null) {
                throw new IOException("Seed memory resource not found: " + RESOURCE);
            }
            try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                Jsonb jsonb = JsonbBuilder.create();
                try {
                    return Arrays.stream(jsonb.fromJson(reader, MemoryDocument[].class)).toList();
                } finally {
                    jsonb.close();
                }
            } catch (Exception e) {
                throw new IllegalStateException("Unable to deserialize seeded memories.", e);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Unable to load seeded memories.", e);
        }
    }
}
