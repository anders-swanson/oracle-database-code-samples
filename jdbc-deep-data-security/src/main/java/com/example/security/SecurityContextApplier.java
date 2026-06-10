package com.example.security;

import java.sql.Connection;
import java.sql.SQLException;

interface SecurityContextApplier {
    AppliedSecurityContext apply(Connection connection, SupportActor actor, boolean elevated) throws SQLException;

    interface AppliedSecurityContext extends AutoCloseable {
        @Override
        void close() throws SQLException;
    }
}
