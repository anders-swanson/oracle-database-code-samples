package com.example.okafkamemory.memory;

import org.junit.jupiter.api.Test;

import java.util.HexFormat;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class MemoryIdTest {
    private static final UUID SAMPLE_UUID = UUID.fromString("2f4b6f9a-1d7e-4c6b-8d4a-2c8e5f9b0a11");

    @Test
    void mapsMostSignificantThenLeastSignificantBitsToSixteenBytes() {
        MemoryId id = new MemoryId(SAMPLE_UUID);

        assertThat(HexFormat.of().withUpperCase().formatHex(id.toBytes()))
                .isEqualTo("2F4B6F9A1D7E4C6B8D4A2C8E5F9B0A11");
        assertThat(MemoryId.fromBytes(id.toBytes())).isEqualTo(id);
    }

    @Test
    void rejectsRawValuesThatAreNotExactlySixteenBytes() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> MemoryId.fromBytes(new byte[15]));
    }
}
