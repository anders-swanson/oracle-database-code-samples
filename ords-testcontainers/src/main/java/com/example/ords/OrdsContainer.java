package com.example.ords;

import java.io.IOException;
import java.time.Duration;

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

    private static final Duration DEFAULT_STARTUP_TIMEOUT = Duration.ofMinutes(5);

    private String schemaUsername;
    private String schemaPassword;
    private String schemaConnectDescriptor;

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
        if (isBlank(connectionString)) {
            throw new IllegalArgumentException("Database connection string cannot be null or empty");
        }
        return this.withEnv("CONN_STRING", connectionString);
    }

    public OrdsContainer withOraclePassword(String oraclePassword) {
        if (isBlank(oraclePassword)) {
            throw new IllegalArgumentException("Oracle password cannot be null or empty");
        }
        return this.withEnv("ORACLE_PWD", oraclePassword);
    }

    public OrdsContainer withSchema(String username, String password, String connectDescriptor) {
        if (isBlank(username) || isBlank(password) || isBlank(connectDescriptor)) {
            throw new IllegalArgumentException("Schema username, password, and connect descriptor are required");
        }
        schemaUsername = username;
        schemaPassword = password;
        schemaConnectDescriptor = connectDescriptor;
        return self();
    }

    public String getBaseUrl() {
        return "http://" + getHost() + ":" + getHttpPort();
    }

    public Integer getHttpPort() {
        return getMappedPort(HTTP_PORT);
    }

    public Integer getHttpsPort() {
        return getMappedPort(HTTPS_PORT);
    }

    public Integer getMongoDbApiPort() {
        return getMappedPort(MONGODB_API_PORT);
    }

    @Override
    public void start() {
        validateRequiredEnv("CONN_STRING");
        validateRequiredEnv("ORACLE_PWD");
        super.start();
    }

    @Override
    protected void containerIsStarted(InspectContainerResponse containerInfo) {
        super.containerIsStarted(containerInfo);
        if (schemaUsername != null) {
            enableSchema();
        }
    }

    private void enableSchema() {
        String command = String.format(
                "printf 'WHENEVER SQLERROR EXIT SQL.SQLCODE\\nEXECUTE ORDS.ENABLE_SCHEMA;\\nEXIT;\\n' | sql -s %s",
                shellQuote(schemaUsername + "/" + schemaPassword + "@" + schemaConnectDescriptor)
        );

        try {
            ExecResult result = execInContainer("bash", "-lc", command);
            if (result.getExitCode() != 0) {
                throw new ContainerLaunchException(
                        "ORDS schema enablement failed.\nstdout:\n" + result.getStdout() + "\nstderr:\n" + result.getStderr()
                );
            }
        } catch (IOException e) {
            throw new ContainerLaunchException("Failed to run ORDS schema enablement command", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ContainerLaunchException("Interrupted while enabling ORDS schema", e);
        }
    }

    private void validateRequiredEnv(String envName) {
        if (isBlank(getEnvMap().get(envName))) {
            throw new IllegalStateException(envName + " must be configured before starting ORDS");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String shellQuote(String value) {
        return "'" + value.replace("'", "'\"'\"'") + "'";
    }
}
