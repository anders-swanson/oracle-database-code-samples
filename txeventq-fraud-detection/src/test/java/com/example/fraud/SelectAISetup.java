package com.example.fraud;

import com.oracle.bmc.ConfigFileReader;
import oracle.jdbc.pool.OracleDataSource;
import org.testcontainers.oracle.OracleContainer;
import org.testcontainers.utility.MountableFile;

import javax.sql.DataSource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;

public class SelectAISetup {
    static void setupWithSelectAI(OracleContainer oracleContainer) throws Exception {
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


        OracleDataSource ds = new OracleDataSource();
        ds.setURL(oracleContainer.getJdbcUrl());
        ds.setUser("selectai");
        ds.setPassword("Welcome12345");

        System.out.println("Creating DBMS_CLOUD profile for Select AI...");
        createGenAiProfile(ds);
    }

    private static void createGenAiProfile(DataSource ds) throws IOException, SQLException {
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
                      'GRANT EXECUTE ON OCI_GENAI TO TESTUSER';
                    DBMS_CLOUD_AI.CREATE_PROFILE(
                            profile_name => 'MY_PROFILE',
                            attributes   => '{
                              "provider": "oci",
                              "credential_name": "OCI_GENAI",
                              "region": "us-chicago-1",
                              "oci_compartment_id": "%s",
                              "object_list": [
                                { "owner": "TESTUSER", "name": "CARDHOLDERS" },
                                { "owner": "TESTUSER", "name": "CARDHOLDER_BEHAVIOR_PROFILES" },
                                { "owner": "TESTUSER", "name": "CARD_TRANSACTIONS" },
                                { "owner": "TESTUSER", "name": "FRAUD_ASSESSMENTS" }
                              ],
                              "enforce_object_list": true
                            }'
                    );
                    DBMS_CLOUD_AI.GRANT_PROFILE_ACCESS('MY_PROFILE', 'TESTUSER');
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
