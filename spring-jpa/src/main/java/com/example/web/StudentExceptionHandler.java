package com.example.web;

import com.example.errors.OracleDatabaseError;
import com.example.errors.OracleJpaException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@ControllerAdvice
public class StudentExceptionHandler {
    @ExceptionHandler(OracleJpaException.class)
    public ResponseEntity<OracleErrorResponse> handleOracleJpaException(OracleJpaException exception) {
        OracleDatabaseError error = exception.getOracleError();
        // map and Handle ORA errors as needed
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
