package com.example.security;

import java.sql.Connection;
import java.sql.SQLException;

import oracle.jdbc.EndUserSecurityContext;
import oracle.jdbc.OracleConnection;
import oracle.sql.json.OracleJsonFactory;
import oracle.sql.json.OracleJsonObject;

final class OracleEndUserContextApplier implements SecurityContextApplier {
    private static final String SUPPORT_CASE_CONTEXT = "support_case";

    private final String databaseAccessToken;
    private final OracleJsonFactory jsonFactory = new OracleJsonFactory();

    OracleEndUserContextApplier(String databaseAccessToken) {
        this.databaseAccessToken = databaseAccessToken;
    }

    @Override
    public AppliedSecurityContext apply(Connection connection, SupportActor actor, boolean elevated) throws SQLException {
        OracleConnection oracleConnection = connection.unwrap(OracleConnection.class);
        OracleJsonObject attributes = jsonFactory.createObject();
        attributes.put("tenant_id", actor.tenantId());
        attributes.put("region", actor.region());
        attributes.put("role", actor.role());
        attributes.put("elevated", Boolean.toString(elevated));

        EndUserSecurityContext context = EndUserSecurityContext
                .createWithName(databaseAccessToken, actor.username())
                .withDataRoles(actor.dataRoles(elevated))
                .withAttributes(SUPPORT_CASE_CONTEXT, attributes);

        oracleConnection.setEndUserSecurityContext(context);
        return oracleConnection::clearEndUserSecurityContext;
    }
}
