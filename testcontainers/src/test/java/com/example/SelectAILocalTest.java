package com.example;

import com.oracle.bmc.ConfigFileReader;
import oracle.jdbc.pool.OracleDataSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.oracle.OracleContainer;
import org.testcontainers.utility.MountableFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@EnabledIfEnvironmentVariable(named = "OCI_COMPARTMENT_ID", matches = ".+")
public class SelectAILocalTest {
    private final static String CERTS_FILE = "https://objectstorage.us-phoenix-1.oraclecloud.com/p/KB63IAuDCGhz_azOVQ07Qa_mxL3bGrFh1dtsltreRJPbmb-VwsH2aQ4Pur2ADBMA/n/adwcdemo/b/CERTS/o/dbc_certs.tar";
    private static final String WALLET_PASSWORD = "MyWalletPassword12345";
    private final static String SYS_PASSWORD = "Welcome12345";
    private static OracleDataSource ds;

    /**
     * The "full" image is required to run catcon.pl inside the container.
     */
    @Container
    static OracleContainer oracleContainer = new OracleContainer("gvenzl/oracle-free:23.26.2-full-faststart")
            .withStartupTimeout(Duration.ofMinutes(5))
            .withUsername("TESTUSER")
            .withPassword("testpwd")
            .withInitScript("students.sql")
            .withEnv(Map.of("ORACLE_PASSWORD", SYS_PASSWORD,
                    "WALLET_PASSWORD", WALLET_PASSWORD,
                    "CERTS_FILE", CERTS_FILE));

    @BeforeAll
    static void setup() throws Exception {
        oracleContainer.start();

        // Download certificates, create the database wallet, and install DBMS_CLOUD.
        oracleContainer.copyFileToContainer(MountableFile.forClasspathResource("selectai/init.sh"), "/tmp/init.sh");
        var initResult = oracleContainer.execInContainer("bash", "/tmp/init.sh");
        assertThat(initResult.getExitCode())
                .withFailMessage(initResult.getStdout())
                .isZero();

        oracleContainer.copyFileToContainer(MountableFile.forClasspathResource("selectai/dbms_cloud_aces.sql"), "/tmp/dbms_cloud_aces.sql");
        var acesResult = oracleContainer.execInContainer("sqlplus", "sys / as sysdba", "@/tmp/dbms_cloud_aces.sql");
        assertThat(acesResult.getExitCode())
                .withFailMessage(acesResult.getStdout())
                .isZero();

        // Initialize the testuser for DBMS_CLOUD (applying grants)
        oracleContainer.copyFileToContainer(MountableFile.forClasspathResource("selectai/dbms_cloud_grants.sql"), "/tmp/init.sql");
        var grantsResult = oracleContainer.execInContainer("sqlplus", "sys / as sysdba", "@/tmp/init.sql");
        assertThat(grantsResult.getExitCode())
                .withFailMessage(grantsResult.getStdout())
                .isZero();

        // Configure a test datasource
        ds = new OracleDataSource();
        ds.setURL(oracleContainer.getJdbcUrl());
        ds.setUser("selectai");
        ds.setPassword("Welcome12345");

        createGenAiProfile();
    }

    @Test
    public void selectAIRunsLocally() {
        System.out.println("Generated SQL:");
        System.out.println(selectai("what are the courses available?", "showsql"));
    }

    private String selectai(String prompt, String action) {
        try (Connection conn = ds.getConnection()) {
            String sql = """
                    BEGIN
                    ? := DBMS_CLOUD_AI.GENERATE(
                             prompt       => ?,
                             action       => ?,
                             profile_name => ?);
                END;
                """;

            try (CallableStatement statement = conn.prepareCall(sql)) {
                statement.registerOutParameter(1, Types.CLOB);
                statement.setString(2, prompt);
                statement.setString(3, action);
                statement.setString(4, "MY_PROFILE");
                statement.execute();
                return statement.getString(1);
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private static void createGenAiProfile() throws IOException, SQLException {
        ConfigFileReader.ConfigFile configFile = ConfigFileReader.parseDefault();
        String privateKey = Files.readString(Path.of(configFile.get("key_file")));

        final String profileSQL = """
                BEGIN
                    DBMS_CLOUD.CREATE_CREDENTIAL(
                        credential_name => 'GENAI_CRED',
                        user_ocid       => ?,
                        tenancy_ocid    => ?,
                        private_key     => ?,
                        fingerprint     => ?
                    );
                    DBMS_CLOUD_AI.CREATE_PROFILE(
                            profile_name => 'MY_PROFILE',
                            attributes   => '{
                              "provider": "oci",
                              "credential_name": "GENAI_CRED",
                              "region": "us-chicago-1",
                              "oci_compartment_id": "%s",
                              "object_list": [
                                { "owner": "TESTUSER", "name": "STUDENTS" },
                                { "owner": "TESTUSER", "name": "COURSES" },
                                { "owner": "TESTUSER", "name": "LECTURE_HALLS" },
                                { "owner": "TESTUSER", "name": "ENROLLMENTS" }
                              ],
                              "enforce_object_list": true
                            }'
                    );
                END;
                """.formatted(System.getenv("OCI_COMPARTMENT_ID"));

        try (var connection = ds.getConnection();
             var statement = connection.prepareStatement(profileSQL)) {
            statement.setString(1, configFile.get("user"));
            statement.setString(2, configFile.get("tenancy"));
            statement.setString(3, privateKey);
            statement.setString(4, configFile.get("fingerprint"));
            statement.execute();
        }
    }
}
