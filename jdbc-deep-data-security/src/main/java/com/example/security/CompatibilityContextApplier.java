package com.example.security;

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.SQLException;

final class CompatibilityContextApplier implements SecurityContextApplier {
    @Override
    public AppliedSecurityContext apply(Connection connection, SupportActor actor, boolean elevated) throws SQLException {
        try (CallableStatement statement = connection.prepareCall("{call support_security.set_actor(?, ?, ?, ?, ?)}")) {
            statement.setString(1, actor.username());
            statement.setString(2, actor.tenantId());
            statement.setString(3, actor.region());
            statement.setString(4, actor.role());
            statement.setString(5, Boolean.toString(elevated));
            statement.execute();
        }

        return () -> {
            try (CallableStatement statement = connection.prepareCall("{call support_security.clear_actor}")) {
                statement.execute();
            }
        };
    }
}
