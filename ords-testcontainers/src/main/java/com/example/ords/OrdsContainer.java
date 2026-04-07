package com.example.ords;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import com.github.dockerjava.api.command.InspectContainerResponse;
import org.testcontainers.containers.ContainerLaunchException;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

public class OrdsContainer extends GenericContainer<OrdsContainer> {
    public static final String DEFAULT_IMAGE = "container-registry.oracle.com/database/ords:latest";
    public static final int HTTP_PORT = 8080;
    public static final int HTTPS_PORT = 8443;
    public static final int MONGODB_API_PORT = 27017;

    private static final String CONNECTION_STRING_ENV = "CONN_STRING";
    private static final String ORACLE_PASSWORD_ENV = "ORACLE_PWD";
    private static final Duration DEFAULT_STARTUP_TIMEOUT = Duration.ofMinutes(5);

    private final List<SchemaConfiguration> schemas = new ArrayList<>();

    public OrdsContainer() {
        this(DockerImageName.parse(DEFAULT_IMAGE));
    }

    public OrdsContainer(String dockerImageName) {
        this(DockerImageName.parse(dockerImageName));
    }

    public OrdsContainer(DockerImageName dockerImageName) {
        super(dockerImageName);
        this.withExposedPorts(HTTP_PORT, HTTPS_PORT, MONGODB_API_PORT);
        this.waitingFor(Wait.forHttp("/")
                .forPort(HTTP_PORT)
                .forStatusCodeMatching(status -> status >= 200 && status < 500)
                .withStartupTimeout(DEFAULT_STARTUP_TIMEOUT));
    }

    public OrdsContainer withDatabaseConnectionString(String connectionString) {
        return withEnv(CONNECTION_STRING_ENV, requireNonBlank(
                connectionString,
                "Database connection string cannot be null or empty"
        ));
    }

    public OrdsContainer withOraclePassword(String oraclePassword) {
        return withEnv(ORACLE_PASSWORD_ENV, requireNonBlank(
                oraclePassword,
                "Oracle password cannot be null or empty"
        ));
    }

    public OrdsContainer withSchema(String username, String password, String connectDescriptor) {
        schemas.add(new SchemaConfiguration(
                requireNonBlank(username, "Schema username is required"),
                requireNonBlank(password, "Schema password is required"),
                requireNonBlank(connectDescriptor, "Schema connect descriptor is required")
        ));
        return self();
    }

    public String getBaseUrl() {
        return "http://" + getHost() + ":" + getHttpPort();
    }

    public int getHttpPort() {
        return getMappedPort(HTTP_PORT);
    }

    public int getHttpsPort() {
        return getMappedPort(HTTPS_PORT);
    }

    public int getMongoDbApiPort() {
        return getMappedPort(MONGODB_API_PORT);
    }

    @Override
    public void start() {
        validateRequiredEnv(CONNECTION_STRING_ENV);
        validateRequiredEnv(ORACLE_PASSWORD_ENV);
        super.start();
    }

    @Override
    protected void containerIsStarted(InspectContainerResponse containerInfo) {
        super.containerIsStarted(containerInfo);
        schemas.forEach(this::enableSchema);
    }

    private void enableSchema(SchemaConfiguration schema) {
        String command = String.format(
                "printf 'WHENEVER SQLERROR EXIT SQL.SQLCODE\\nEXECUTE ORDS.ENABLE_SCHEMA;\\nEXIT;\\n' | sql -s %s",
                shellQuote(schema.username() + "/" + schema.password() + "@" + schema.connectDescriptor())
        );

        try {
            execInContainerOrThrow("ORDS schema enablement failed", "bash", "-lc", command);
        } catch (IOException e) {
            throw new ContainerLaunchException("Failed to run ORDS schema enablement command", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ContainerLaunchException("Interrupted while enabling ORDS schema", e);
        }
    }

    private void execInContainerOrThrow(String failureMessage, String... command)
            throws IOException, InterruptedException {
        ExecResult result = execInContainer(command);
        if (result.getExitCode() == 0) {
            return;
        }

        throw new ContainerLaunchException(
                failureMessage + ".\nstdout:\n" + result.getStdout() + "\nstderr:\n" + result.getStderr()
        );
    }

    private void validateRequiredEnv(String envName) {
        if (isBlank(getEnvMap().get(envName))) {
            throw new IllegalStateException(envName + " must be configured before starting ORDS");
        }
    }

    private static String requireNonBlank(String value, String message) {
        if (isBlank(value)) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String shellQuote(String value) {
        return "'" + value.replace("'", "'\"'\"'") + "'";
    }

    private record SchemaConfiguration(String username, String password, String connectDescriptor) {
    }
}
