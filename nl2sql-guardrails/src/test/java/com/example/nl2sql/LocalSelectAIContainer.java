package com.example.nl2sql;

import com.oracle.bmc.ConfigFileReader;
import org.testcontainers.oracle.OracleContainer;
import org.testcontainers.utility.MountableFile;

import javax.sql.DataSource;
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


    static DataSource admin;
    static DataSource batman;
    static DataSource selectai;


    /**
     * The "full" image is required to run catcon.pl inside the container.
     */
    public static OracleContainer oracleContainer = new OracleContainer("gvenzl/oracle-free:23.26.2-full-faststart")
            .withStartupTimeout(Duration.ofMinutes(5))
            .withUsername("HEROES")
            .withPassword("Welcome12345")
            .withInitScript("heroes.sql")
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
        selectai = dataSource("selectai");
        admin = dataSource("\"admin\"");
        batman = dataSource("\"batman\"");

        System.out.println("Creating DBMS_CLOUD profiles...");
        createGenAiProfile(selectai);
    }

    private static oracle.jdbc.datasource.impl.OracleDataSource dataSource(String endUser) throws SQLException {
        oracle.jdbc.datasource.impl.OracleDataSource dataSource = new oracle.jdbc.datasource.impl.OracleDataSource();
        dataSource.setURL(LocalSelectAIContainer.oracleContainer.getJdbcUrl());
        dataSource.setUser(endUser);
        dataSource.setPassword("Welcome12345"); // use your own secure password
        return dataSource;
    }

    private static void createGenAiProfile(DataSource dataSource) throws IOException, SQLException {
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

                    EXECUTE IMMEDIATE
                      'CREATE OR REPLACE PUBLIC SYNONYM OCI_GENAI FOR SELECTAI.GENAI_CRED';
                    EXECUTE IMMEDIATE
                      'GRANT EXECUTE ON OCI_GENAI TO HEROES_ROLE';

                    DBMS_CLOUD_AI.CREATE_PROFILE(
                            profile_name => 'MY_PROFILE',
                            attributes   => '{
                              "provider": "oci",
                              "credential_name": "OCI_GENAI",
                              "region": "us-chicago-1",
                              "oci_compartment_id": "%s",
                              "object_list": [
                                { "owner": "HEROES", "name": "HEROES" },
                                { "owner": "HEROES", "name": "VILLAINS" },
                                { "owner": "HEROES", "name": "CITY_DISTRICTS" },
                                { "owner": "HEROES", "name": "BATTLES" },
                                { "owner": "HEROES", "name": "INSURANCE_CLAIMS" }
                              ],
                              "enforce_object_list": true
                            }'
                    );
                    DBMS_CLOUD_AI.GRANT_PROFILE_ACCESS('MY_PROFILE', 'HEROES_ROLE');
                END;
                """.formatted(System.getenv("OCI_COMPARTMENT_ID"));

        try (var connection = dataSource.getConnection();
             var statement = connection.prepareStatement(profileSQL)) {
            statement.setString(1, configFile.get("user"));
            statement.setString(2, configFile.get("tenancy"));
            statement.setString(3, privateKey);
            statement.setString(4, configFile.get("fingerprint"));
            statement.execute();
        }
    }
}
