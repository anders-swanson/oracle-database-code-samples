package com.example.security;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

final class DeepSecDetector {
    private static final int ORA_INVALID_IDENTIFIER = 904;

    private DeepSecDetector() {
    }

    static boolean isAvailable(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet ignored = statement.executeQuery("select ora_end_user_context.username from dual")) {
            return true;
        } catch (SQLException exception) {
            if (isUnsupportedFeature(exception)) {
                return false;
            }
            throw exception;
        }
    }

    static boolean isUnsupportedFeature(SQLException exception) {
        for (SQLException current = exception; current != null; current = current.getNextException()) {
            if (Math.abs(current.getErrorCode()) == ORA_INVALID_IDENTIFIER) {
                return true;
            }
        }
        return false;
    }

    static IllegalStateException unavailableException() {
        return new IllegalStateException("""
                Deep Data Security mode requires an Oracle AI Database 26ai environment with Deep Data Security enabled,
                policy administration privileges, and token-based end-user identity configuration.
                Run DeepDataSecurityTest for the deterministic local workflow, or inspect
                src/test/resources/sql/deepsec-security.sql for the Deep Data Security data role and data grant shape.
                """);
    }
}
