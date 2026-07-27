package com.example.nl2sql;

import com.oracle.bmc.ConfigFileReader;
import oracle.jdbc.pool.OracleDataSource;
import org.testcontainers.oracle.OracleContainer;
import org.testcontainers.utility.MountableFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class LocalSelectAIContainer {
    private final static String CERTS_FILE = "https://objectstorage.us-phoenix-1.oraclecloud.com/p/KB63IAuDCGhz_azOVQ07Qa_mxL3bGrFh1dtsltreRJPbmb-VwsH2aQ4Pur2ADBMA/n/adwcdemo/b/CERTS/o/dbc_certs.tar";
    private static final String WALLET_PASSWORD = "MyWalletPassword12345";
    private final static String SYS_PASSWORD = "Welcome12345";
    private static OracleDataSource ds;

    /**
     * The "full" image is required to run catcon.pl inside the container.
     */
    public static OracleContainer oracleContainer = new OracleContainer("gvenzl/oracle-free:23.26.2-full-faststart")
            .withStartupTimeout(Duration.ofMinutes(5))
            .withUsername("TESTUSER")
            .withPassword("Welcome12345")
            .withEnv(Map.of("ORACLE_PASSWORD", SYS_PASSWORD,
                    "WALLET_PASSWORD", WALLET_PASSWORD,
                    "CERTS_FILE", CERTS_FILE));


    public static void start() throws Exception {
        oracleContainer.start();

        System.out.println("Installing certificates and DBMS_CLOUD family of PL/SQL packages...");
        // Download certificates, create the database wallet, and install DBMS_CLOUD.
        oracleContainer.copyFileToContainer(MountableFile.forClasspathResource("selectai/init.sh"), "/tmp/init.sh");
        var initResult = oracleContainer.execInContainer("bash", "/tmp/init.sh");
        assertThat(initResult.getExitCode())
                .withFailMessage(initResult.getStdout())
                .isZero();

        System.out.println("Configuring Oracle AI Database for outbound HTTPS connections (ACES)...");
        oracleContainer.copyFileToContainer(MountableFile.forClasspathResource("selectai/dbms_cloud_aces.sql"), "/tmp/dbms_cloud_aces.sql");
        var acesResult = oracleContainer.execInContainer("sqlplus", "sys / as sysdba", "@/tmp/dbms_cloud_aces.sql");
        assertThat(acesResult.getExitCode())
                .withFailMessage(acesResult.getStdout())
                .isZero();

        System.out.println("Configuring grants...");
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

        System.out.println("Creating DBMS_CLOUD profile for Select AI...");
        createGenAiProfile();
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
                                { "owner": "UNI", "name": "STUDENTS" },
                                { "owner": "UNI", "name": "COURSES" },
                                { "owner": "UNI", "name": "LECTURE_HALLS" },
                                { "owner": "UNI", "name": "ENROLLMENTS" }
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
