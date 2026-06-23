package com.example.errors;

import java.sql.SQLException;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Optional;
import java.util.Set;

public final class OracleErrorExtractor {
    private OracleErrorExtractor() {}

    public static Optional<OracleDatabaseError> from(Throwable throwable) {
        Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        return findSQLException(throwable, seen)
                .filter(sqlException -> sqlException.getErrorCode() > 0)
                .map(sqlException -> OracleDatabaseError.fromErrorCode(
                        sqlException.getErrorCode(),
                        sqlException.getMessage()
                ));
    }

    private static Optional<SQLException> findSQLException(Throwable throwable, Set<Throwable> seen) {
        if (throwable == null || !seen.add(throwable)) {
            return Optional.empty();
        }

        if (throwable instanceof SQLException sqlException) {
            if (sqlException.getErrorCode() > 0) {
                return Optional.of(sqlException);
            }

            SQLException next = sqlException.getNextException();
            while (next != null && seen.add(next)) {
                if (next.getErrorCode() > 0) {
                    return Optional.of(next);
                }
                next = next.getNextException();
            }
        }

        return findSQLException(throwable.getCause(), seen);
    }
}
