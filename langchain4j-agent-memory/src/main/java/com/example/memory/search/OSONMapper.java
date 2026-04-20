package com.example.memory.search;

import jakarta.json.bind.JsonbBuilder;
import jakarta.json.stream.JsonGenerator;
import jakarta.json.stream.JsonParser;
import oracle.sql.json.OracleJsonFactory;
import org.eclipse.yasson.YassonJsonb;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

final class OSONMapper {
    private final OracleJsonFactory oracleJsonFactory;
    private final YassonJsonb jsonb;

    static OSONMapper createDefault() {
        return new OSONMapper(new OracleJsonFactory(), (YassonJsonb) JsonbBuilder.create());
    }

    OSONMapper(OracleJsonFactory oracleJsonFactory, YassonJsonb jsonb) {
        this.oracleJsonFactory = oracleJsonFactory;
        this.jsonb = jsonb;
    }

    byte[] toOSON(Object value) {
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            JsonGenerator generator = oracleJsonFactory.createJsonBinaryGenerator(outputStream).wrap(JsonGenerator.class);
            jsonb.toJson(value, generator);
            generator.close();
            return outputStream.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Failed to convert object to OSON", e);
        }
    }

    <T> T fromOSON(JsonParser parser, Class<T> type) {
        return jsonb.fromJson(parser, type);
    }
}
