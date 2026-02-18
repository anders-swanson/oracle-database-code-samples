package com.example.jdv.crud;

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
 * Utility class to map Java Objects to OSON (Oracle's native binary JSON format), and OSON to Java Objects.
 */
public class OSONMapper {
    private final OracleJsonFactory oracleJsonFactory;
    private final YassonJsonb jsonb;


    /**
     * Create a new JSONB instance.
     * @return Default JSONB instance.
     */
    public static OSONMapper createDefault() {
        return new OSONMapper(new OracleJsonFactory(), (YassonJsonb) JsonbBuilder.create());
    }

    public OSONMapper(OracleJsonFactory oracleJsonFactory, YassonJsonb jsonb) {
        this.oracleJsonFactory = oracleJsonFactory;
        this.jsonb = jsonb;
    }

    /**
     * Converts a Java object to an OSON byte array.
     * @param o Java object to convert to OSON.
     * @return OSON byte array.
     */
    public byte[] toOSON(Object o) {
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream())  {
            JsonGenerator gen = oracleJsonFactory.createJsonBinaryGenerator(outputStream).wrap(JsonGenerator.class);
            jsonb.toJson(o, gen);
            gen.close();
            return outputStream.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Creates an OSON JsonParser from a Java object.
     * @param o Java object to create a JsonParser from.
     * @return JsonParser for generating OSON.
     */
    public JsonParser toJsonParser(Object o) {
        byte[] oson = toOSON(o);
        ByteBuffer buf = ByteBuffer.wrap(oson);
        return oracleJsonFactory.createJsonBinaryParser(buf).wrap(JsonParser.class);
    }

    /**
     * Convert an OSON byte array to a Java object of type T.
     * @param oson OSON byte array.
     * @param clazz Java class to convert OSON to.
     * @param <T> Type parameter for the Java conversion class.
     * @return Converted Java object of type T.
     * @throws IOException When OSON parsing fails.
     */
    public <T> T fromOSON(byte[] oson, Class<T> clazz) throws IOException {
        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(oson)) {
            return fromOSON(inputStream, clazz);
        }
    }

    /**
     * Create a Java object of type T from an OSON JsonParser.
     * @param parser OSON JsonParser.
     * @param clazz Java object to create.
     * @param <T> Type parameter for the Java object.
     * @return Converted Java object of type T.
     */
    public <T> T fromOSON(JsonParser parser, Class<T> clazz) {
        return jsonb.fromJson(parser, clazz);
    }

    /**
     * Create a Java object from an OSON InputStream.
     * @param inputStream OSON InputStream.
     * @param clazz Java object to create.
     * @param <T> Type parameter for the Java object.
     * @return Converted Java object of type T.
     */
    public <T> T fromOSON(InputStream inputStream, Class<T> clazz) {
        JsonParser jsonParser = oracleJsonFactory.createJsonBinaryParser(inputStream).wrap(JsonParser.class);
        return jsonb.fromJson(jsonParser, clazz);
    }

    /** Create a Java Object from an OSON ByteBuffer.
     * @param byteBuffer OSON ByteBuffer.
     * @param clazz Java object to create.
     * @param <T> Type parameter for the Java object.
     * @return Converted Java object of type T.
     */
    public <T> T fromOSON(ByteBuffer byteBuffer, Class<T> clazz) {
        JsonParser jsonParser = oracleJsonFactory.createJsonBinaryParser(byteBuffer).wrap(JsonParser.class);
        return jsonb.fromJson(jsonParser, clazz);
    }
}
