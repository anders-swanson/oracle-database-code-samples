package com.example.errors;

public class OracleDataAccessException extends RuntimeException {
    private final OracleDatabaseError oracleError;

    public OracleDataAccessException(OracleDatabaseError oracleError, Throwable cause) {
        super("%s from Oracle AI Database JPA operation".formatted(oracleError.oraCode()), cause);
        this.oracleError = oracleError;
    }

    public OracleDatabaseError getOracleError() {
        return oracleError;
    }
}
