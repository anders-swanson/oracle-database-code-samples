package com.example.json.jdbc;

import jakarta.json.bind.JsonbBuilder;
import jakarta.json.stream.JsonGenerator;
import jakarta.json.stream.JsonParser;
import oracle.sql.json.OracleJsonFactory;
import org.eclipse.yasson.YassonJsonb;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

/**
 * Utility class to map Java objects to Oracle JSON (OSON) and back.
 */
public class OSONMapper {
    private final OracleJsonFactory oracleJsonFactory;
    private final YassonJsonb jsonb;

    public static OSONMapper createDefault() {
        return new OSONMapper(new OracleJsonFactory(), (YassonJsonb) JsonbBuilder.create());
    }

    public OSONMapper(OracleJsonFactory oracleJsonFactory, YassonJsonb jsonb) {
        this.oracleJsonFactory = oracleJsonFactory;
        this.jsonb = jsonb;
    }

    public byte[] toOSON(Object value) {
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            JsonGenerator generator = oracleJsonFactory.createJsonBinaryGenerator(outputStream).wrap(JsonGenerator.class);
            jsonb.toJson(value, generator);
            generator.close();
            return outputStream.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public JsonParser toJsonParser(Object value) {
        return oracleJsonFactory.createJsonBinaryParser(ByteBuffer.wrap(toOSON(value))).wrap(JsonParser.class);
    }

    public <T> T fromOSON(byte[] payload, Class<T> type) throws IOException {
        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(payload)) {
            return fromOSON(inputStream, type);
        }
    }

    public <T> T fromOSON(InputStream inputStream, Class<T> type) {
        JsonParser parser = oracleJsonFactory.createJsonBinaryParser(inputStream).wrap(JsonParser.class);
        return jsonb.fromJson(parser, type);
    }

    public <T> T fromOSON(ByteBuffer bytes, Class<T> type) {
        JsonParser parser = oracleJsonFactory.createJsonBinaryParser(bytes).wrap(JsonParser.class);
        return jsonb.fromJson(parser, type);
    }

    public <T> T fromOSON(JsonParser parser, Class<T> type) {
        return jsonb.fromJson(parser, type);
    }
}
