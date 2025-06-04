package com.example.kafka2;

import com.oracle.spring.json.jsonb.JSONB;
import org.apache.kafka.common.serialization.Serializer;

public class OSONSerializer<T> implements Serializer<T> {
    private final JSONB jsonb;

    public OSONSerializer(JSONB jsonb) {
        this.jsonb = jsonb;
    }

    @Override
    public byte[] serialize(String s, T obj) {
        return jsonb.toOSON(obj);
    }
}
