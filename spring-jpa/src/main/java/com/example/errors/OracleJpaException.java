package com.example.errors;

public class OracleJpaException extends RuntimeException {
    private final OracleDatabaseError oracleError;

    public OracleJpaException(OracleDatabaseError oracleError, Throwable cause) {
        super("%s from Oracle AI Database JPA operation".formatted(oracleError.oraCode()), cause);
        this.oracleError = oracleError;
    }

    public OracleDatabaseError getOracleError() {
        return oracleError;
    }
}
