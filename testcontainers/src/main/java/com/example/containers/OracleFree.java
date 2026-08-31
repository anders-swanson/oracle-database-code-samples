package com.example.containers;

import java.time.Duration;
import java.util.Collections;
import java.util.Locale;
import java.util.Set;

import org.testcontainers.containers.JdbcDatabaseContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.containers.wait.strategy.WaitStrategy;
import org.testcontainers.images.builder.Transferable;
import org.testcontainers.utility.DockerImageName;

/**
 * Testcontainers implementation for the official Oracle AI Database Free image.
 *
 * <p>The image provides the {@code SYS}, {@code SYSTEM}, and {@code PDBADMIN}
 * accounts. This container creates a {@code TEST} application user in
 * {@code FREEPDB1} by default.</p>
 *
 * <p>Supported image: {@code container-registry.oracle.com/database/free}</p>
 *
 * <p>Exposed port: 1521</p>
 */
public class OracleFree extends JdbcDatabaseContainer<OracleFree> {

    // Uses the official Oracle AI Database free image from the Oracle Container Registry
    public static final String IMAGE_NAME = "container-registry.oracle.com/database/free";
    // Use "latest" or a specific version tag if you need the full database feature set.
    // "latest-lite" is much smaller, and very fast to start.
    public static final String DEFAULT_TAG = "latest-lite";
    public static final int ORACLE_PORT = 1521;
    public static final String DEFAULT_DATABASE_NAME = "FREEPDB1";
    public static final String DEFAULT_SID = "FREE";
    public static final String DEFAULT_USERNAME = "TEST";
    public static final String DEFAULT_PASSWORD = "TestPassword1";

    private static final DockerImageName DEFAULT_IMAGE = DockerImageName.parse(IMAGE_NAME);
    private static final int DEFAULT_STARTUP_TIMEOUT_MINUTES = 10;
    private static final int DEFAULT_CONNECT_TIMEOUT_SECONDS = 120;
    private static final String SYSTEM_USERNAME = "SYSTEM";
    private static final String PDBADMIN_USERNAME = "PDBADMIN";
    private static final String APP_USER_STARTUP_SCRIPT = "/opt/oracle/scripts/startup/01_testcontainers_app_user.sql";
    private static final String DATABASE_READY_LOG = ".*DATABASE IS READY TO USE!.*\\s";
    private static final String APP_USER_READY_LOG = ".*TESTCONTAINERS APP USER IS READY.*\\s";
    private static final String ORACLE_USERNAME_PATTERN = "[A-Z][A-Z0-9_$#]{0,127}";

    private String username = DEFAULT_USERNAME;
    private String password = DEFAULT_PASSWORD;
    private String adminPassword = DEFAULT_PASSWORD;
    private boolean appUser = true;
    private boolean usingSid;

    public OracleFree() {
        this(IMAGE_NAME + ":" + DEFAULT_TAG);
    }

    public OracleFree(String dockerImageName) {
        this(DockerImageName.parse(dockerImageName));
    }

    public OracleFree(DockerImageName dockerImageName) {
        super(dockerImageName);
        dockerImageName.assertCompatibleWith(DEFAULT_IMAGE);
        addExposedPort(ORACLE_PORT);
        waitingFor(waitForLogMessage(APP_USER_READY_LOG));
        withConnectTimeoutSeconds(DEFAULT_CONNECT_TIMEOUT_SECONDS);
    }

    @Override
    protected void configure() {
        withEnv("ORACLE_PWD", adminPassword);
        if (appUser) {
            withCopyToContainer(Transferable.of(createAppUserScript()), APP_USER_STARTUP_SCRIPT);
        }
    }

    @Override
    protected void waitUntilContainerStarted() {
        getWaitStrategy().waitUntilReady(this);
    }

    @Override
    public Set<Integer> getLivenessCheckPortNumbers() {
        return Collections.singleton(getMappedPort(ORACLE_PORT));
    }

    @Override
    public String getDriverClassName() {
        return "oracle.jdbc.OracleDriver";
    }

    @Override
    public String getJdbcUrl() {
        String address = getHost() + ":" + getOraclePort();
        return usingSid
                ? "jdbc:oracle:thin:@" + address + ":" + DEFAULT_SID
                : "jdbc:oracle:thin:@//" + address + "/" + DEFAULT_DATABASE_NAME;
    }

    @Override
    public String getUsername() {
        return usingSid ? SYSTEM_USERNAME : username;
    }

    @Override
    public String getPassword() {
        return usingSid ? adminPassword : password;
    }

    @Override
    public String getDatabaseName() {
        return DEFAULT_DATABASE_NAME;
    }

    @Override
    public OracleFree withUsername(String username) {
        requireNotBlank(username, "Username");
        String normalizedUsername = username.toUpperCase(Locale.ROOT);
        if ("SYS".equals(normalizedUsername)) {
            throw new IllegalArgumentException("SYS connections require SYSDBA privileges");
        }

        appUser = !SYSTEM_USERNAME.equals(normalizedUsername) && !PDBADMIN_USERNAME.equals(normalizedUsername);
        if (appUser && !normalizedUsername.matches(ORACLE_USERNAME_PATTERN)) {
            throw new IllegalArgumentException("Username must be a valid unquoted Oracle AI Database identifier");
        }

        this.username = normalizedUsername;
        this.password = appUser ? password : adminPassword;
        waitingFor(waitForLogMessage(appUser ? APP_USER_READY_LOG : DATABASE_READY_LOG));
        return self();
    }

    @Override
    public OracleFree withPassword(String password) {
        requireNotBlank(password, "Password");
        requireValidPassword(password);
        this.password = password;
        if (!appUser) {
            this.adminPassword = password;
        }
        return self();
    }

    public OracleFree withAdminPassword(String adminPassword) {
        requireNotBlank(adminPassword, "Admin password");
        requireValidPassword(adminPassword);
        this.adminPassword = adminPassword;
        if (!appUser) {
            this.password = adminPassword;
        }
        return self();
    }

    @Override
    public OracleFree withDatabaseName(String databaseName) {
        requireNotBlank(databaseName, "Database name");
        if (!DEFAULT_DATABASE_NAME.equalsIgnoreCase(databaseName)) {
            throw new IllegalArgumentException(
                    "The Oracle AI Database Free PDB name is fixed as " + DEFAULT_DATABASE_NAME);
        }
        return self();
    }

    /**
     * Use a SID-style connection to {@code FREE} as {@code SYSTEM} instead of
     * connecting to the {@code FREEPDB1} service.
     *
     * @return this container
     */
    public OracleFree usingSid() {
        usingSid = true;
        return self();
    }

    public OracleFree withCharacterSet(String characterSet) {
        requireNotBlank(characterSet, "Character set");
        return withEnv("ORACLE_CHARACTERSET", characterSet);
    }

    public OracleFree withArchiveLog(boolean enabled) {
        return withEnv("ENABLE_ARCHIVELOG", Boolean.toString(enabled));
    }

    public OracleFree withForceLogging(boolean enabled) {
        return withEnv("ENABLE_FORCE_LOGGING", Boolean.toString(enabled));
    }

    public Integer getOraclePort() {
        return getMappedPort(ORACLE_PORT);
    }

    @Override
    protected String getTestQueryString() {
        return "SELECT 1 FROM DUAL";
    }

    @Override
    public OracleFree withUrlParam(String paramName, String paramValue) {
        throw new UnsupportedOperationException("Oracle JDBC URL parameters are not supported");
    }

    private static void requireNotBlank(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " cannot be null or blank");
        }
    }

    private static void requireValidPassword(String password) {
        if (password.indexOf('"') >= 0 || password.indexOf('\n') >= 0 || password.indexOf('\r') >= 0) {
            throw new IllegalArgumentException("Password cannot contain double quotes or line breaks");
        }
    }

    private String createAppUserScript() {
        String escapedPassword = password.replace("'", "''");
        return """
                WHENEVER SQLERROR EXIT SQL.SQLCODE
                ALTER SESSION SET CONTAINER=FREEPDB1;
                DECLARE
                    USER_COUNT NUMBER;
                    TABLESPACE_COUNT NUMBER;
                BEGIN
                    SELECT COUNT(*) INTO TABLESPACE_COUNT FROM DBA_TABLESPACES WHERE TABLESPACE_NAME = 'USERS';
                    IF TABLESPACE_COUNT = 0 THEN
                        EXECUTE IMMEDIATE 'CREATE TABLESPACE USERS DATAFILE ''/opt/oracle/oradata/FREE/FREEPDB1/users01.dbf'' SIZE 100M AUTOEXTEND ON NEXT 100M MAXSIZE UNLIMITED';
                    END IF;

                    SELECT COUNT(*) INTO USER_COUNT FROM DBA_USERS WHERE USERNAME = '%s';
                    IF USER_COUNT = 0 THEN
                        EXECUTE IMMEDIATE 'CREATE USER %s IDENTIFIED BY "%s" DEFAULT TABLESPACE USERS QUOTA UNLIMITED ON USERS';
                    ELSE
                        EXECUTE IMMEDIATE 'ALTER USER %s IDENTIFIED BY "%s" ACCOUNT UNLOCK';
                    END IF;
                END;
                /
                GRANT DB_DEVELOPER_ROLE TO %s;
                PROMPT TESTCONTAINERS APP USER IS READY
                EXIT;
                """.formatted(
                username,
                username,
                escapedPassword,
                username,
                escapedPassword,
                username);
    }

    private static WaitStrategy waitForLogMessage(String message) {
        return Wait.forLogMessage(message, 1)
                .withStartupTimeout(Duration.ofMinutes(DEFAULT_STARTUP_TIMEOUT_MINUTES));
    }
}
