package com.example.web;

import com.example.errors.OracleDatabaseError;
import com.example.errors.OracleErrorExtractor;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@ControllerAdvice
public class StudentExceptionHandler {
    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<OracleErrorResponse> handleDataAccessException(DataAccessException exception) {
        return OracleErrorExtractor.from(exception)
                .map(this::handleOracleError)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(new OracleErrorResponse(
                                "DATA_ACCESS_ERROR",
                                "Oracle AI Database rejected the JPA operation.",
                                exception.getMessage()
                        )));
    }

    private ResponseEntity<OracleErrorResponse> handleOracleError(OracleDatabaseError error) {
        // Map ORA errors as needed.
        HttpStatus status = error.errorCode() == 2290
                ? HttpStatus.BAD_REQUEST
                : HttpStatus.INTERNAL_SERVER_ERROR;

        OracleErrorResponse response = new OracleErrorResponse(
                error.oraCode(),
                "Oracle AI Database rejected the JPA operation.",
                error.message()
        );
        return ResponseEntity.status(status).body(response);
    }
}
