package com.example.security;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import javax.sql.DataSource;

final class SupportCaseRepository {
    private static final String SELECT_VISIBLE_CASES_SQL = """
            select case_id,
                   tenant_id,
                   region,
                   assigned_agent,
                   severity,
                   status,
                   subject,
                   customer_email,
                   ssn,
                   internal_notes,
                   policy_reason
            from support_case_access_v
            order by case_id
            """;
    private static final String GUARDED_UPDATE_SQL = """
            update support_cases
            set status = ?
            where case_id = ?
              and support_security.can_update_case(tenant_id, region, assigned_agent) = 1
            """;
    private static final String INSERT_AUDIT_SQL = """
            insert into support_case_audit (
                actor_name,
                actor_role,
                security_mode,
                operation,
                case_id,
                rows_affected,
                elevated
            )
            values (?, ?, ?, ?, ?, ?, ?)
            """;

    private final DataSource dataSource;
    private final SecurityContextApplier contextApplier;
    private final String securityMode;

    SupportCaseRepository(DataSource dataSource, SecurityContextApplier contextApplier, String securityMode) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource is required");
        this.contextApplier = Objects.requireNonNull(contextApplier, "contextApplier is required");
        this.securityMode = Objects.requireNonNull(securityMode, "securityMode is required");
    }

    List<SupportCaseView> findVisibleCases(SupportActor actor, boolean elevated) throws SQLException {
        return withActor(actor, elevated, connection -> {
            List<SupportCaseView> cases = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(SELECT_VISIBLE_CASES_SQL);
                 ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    cases.add(new SupportCaseView(
                            resultSet.getLong("case_id"),
                            resultSet.getString("tenant_id"),
                            resultSet.getString("region"),
                            resultSet.getString("assigned_agent"),
                            resultSet.getString("severity"),
                            resultSet.getString("status"),
                            resultSet.getString("subject"),
                            resultSet.getString("customer_email"),
                            resultSet.getString("ssn"),
                            resultSet.getString("internal_notes"),
                            resultSet.getString("policy_reason")
                    ));
                }
            }
            insertAudit(connection, actor, elevated, "SELECT_CASES", null, cases.size());
            return cases;
        });
    }

    int updateStatus(SupportActor actor, boolean elevated, long caseId, String status) throws SQLException {
        return withActor(actor, elevated, connection -> {
            connection.setAutoCommit(false);
            int rows;
            try (PreparedStatement statement = connection.prepareStatement(GUARDED_UPDATE_SQL)) {
                statement.setString(1, status);
                statement.setLong(2, caseId);
                rows = statement.executeUpdate();
            }
            insertAudit(connection, actor, elevated, "UPDATE_STATUS", caseId, rows);
            connection.commit();
            return rows;
        }, true);
    }

    private <T> T withActor(SupportActor actor, boolean elevated, SqlWork<T> work) throws SQLException {
        return withActor(actor, elevated, work, false);
    }

    private <T> T withActor(
            SupportActor actor,
            boolean elevated,
            SqlWork<T> work,
            boolean transactional
    ) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             SecurityContextApplier.AppliedSecurityContext ignored = contextApplier.apply(connection, actor, elevated)) {
            try {
                return work.execute(connection);
            } catch (SQLException | RuntimeException exception) {
                if (transactional) {
                    connection.rollback();
                }
                throw exception;
            }
        }
    }

    List<AuditEvent> listAuditEvents() throws SQLException {
        List<AuditEvent> events = new ArrayList<>();
        String sql = """
                select actor_name,
                       actor_role,
                       security_mode,
                       operation,
                       case_id,
                       rows_affected,
                       elevated
                from support_case_audit
                order by audit_id
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                long caseId = resultSet.getLong("case_id");
                boolean caseIdWasNull = resultSet.wasNull();
                events.add(new AuditEvent(
                        resultSet.getString("actor_name"),
                        resultSet.getString("actor_role"),
                        resultSet.getString("security_mode"),
                        resultSet.getString("operation"),
                        caseIdWasNull ? null : caseId,
                        resultSet.getInt("rows_affected"),
                        Boolean.parseBoolean(resultSet.getString("elevated"))
                ));
            }
        }
        return events;
    }

    private void insertAudit(
            Connection connection,
            SupportActor actor,
            boolean elevated,
            String operation,
            Long caseId,
            int rowsAffected
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(INSERT_AUDIT_SQL)) {
            statement.setString(1, actor.username());
            statement.setString(2, actor.role());
            statement.setString(3, securityMode);
            statement.setString(4, operation);
            if (caseId == null) {
                statement.setNull(5, Types.NUMERIC);
            } else {
                statement.setLong(5, caseId);
            }
            statement.setInt(6, rowsAffected);
            statement.setString(7, Boolean.toString(elevated));
            statement.executeUpdate();
        }
    }

    @FunctionalInterface
    private interface SqlWork<T> {
        T execute(Connection connection) throws SQLException;
    }
}
