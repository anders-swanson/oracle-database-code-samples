package com.example.errors;

public record OracleDatabaseError(
        int errorCode,
        String oraCode,
        String message
) {
    public static OracleDatabaseError fromErrorCode(int errorCode, String message) {
        return new OracleDatabaseError(
                errorCode,
                "ORA-%05d".formatted(errorCode),
                message
        );
    }
}
