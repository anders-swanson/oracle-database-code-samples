package com.example.errors;

import java.sql.SQLException;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Optional;
import java.util.Set;

public final class OracleErrorExtractor {
    public static Optional<OracleDatabaseError> from(Throwable throwable) {
        Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());

        Throwable current = throwable;
        while (current != null && seen.add(current)) {
            if (current instanceof SQLException sqlException) {
                Optional<OracleDatabaseError> error = fromSqlException(sqlException, seen);
                if (error.isPresent()) {
                    return error;
                }
            }
            current = current.getCause();
        }

        return Optional.empty();
    }

    private static Optional<OracleDatabaseError> fromSqlException(SQLException exception, Set<Throwable> seen) {
        SQLException current = exception;
        while (current != null) {
            if (current.getErrorCode() > 0) {
                return Optional.of(OracleDatabaseError.fromErrorCode(
                        current.getErrorCode(),
                        current.getMessage()
                ));
            }

            SQLException next = current.getNextException();
            if (next == null || !seen.add(next)) {
                return Optional.empty();
            }
            current = next;
        }

        return Optional.empty();
    }
}
