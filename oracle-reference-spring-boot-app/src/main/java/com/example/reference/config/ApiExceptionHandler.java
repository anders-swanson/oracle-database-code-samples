package com.example.reference.config;

import com.example.reference.book.CatalogItemNotFound;
import com.example.reference.stock.InventoryConflict;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {
    @ExceptionHandler(CatalogItemNotFound.class)
    ProblemDetail notFound(RuntimeException failure) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, failure.getMessage());
    }

    @ExceptionHandler(InventoryConflict.class)
    ProblemDetail inventoryConflict(InventoryConflict failure) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, failure.getMessage());
    }

    @ExceptionHandler({DataIntegrityViolationException.class, OptimisticLockingFailureException.class})
    ProblemDetail persistenceConflict(RuntimeException failure) {
        // Never expose SQL, constraint names, or raw ORA messages to the HTTP client.
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
                "The write conflicts with existing data. Reload the resource and check its identifiers.");
    }

    @ExceptionHandler(DataAccessResourceFailureException.class)
    ProblemDetail databaseUnavailable(DataAccessResourceFailureException failure) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE,
                "The database is temporarily unavailable.");
    }
}
