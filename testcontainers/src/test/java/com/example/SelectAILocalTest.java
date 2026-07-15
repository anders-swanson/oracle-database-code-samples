package com.example;

import oracle.jdbc.pool.OracleDataSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.oracle.OracleContainer;
import org.testcontainers.utility.MountableFile;

import java.sql.SQLException;
import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
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
            .withUsername("testuser")
            .withPassword("testpwd")
            .withInitScript("students.sql")
            .withEnv(Map.of("ORACLE_PASSWORD", SYS_PASSWORD,
                            "WALLET_PASSWORD", WALLET_PASSWORD,
                            "CERTS_FILE", CERTS_FILE));

    @BeforeAll
    static void setup() throws Exception {
        oracleContainer.start();

        // Download certificates, create the database wallet, and install DBMS_CLOUD.
        oracleContainer.copyFileToContainer(MountableFile.forClasspathResource("init.sh"), "/tmp/init.sh");
        assertThat(oracleContainer.execInContainer("bash", "/tmp/init.sh")
                .getExitCode())
                .isZero();

        // Initialize the testuser for DBMS_CLOUD (applying grants)
        oracleContainer.copyFileToContainer(MountableFile.forClasspathResource("dbms_cloud_grants.sql"), "/tmp/init.sql");
        assertThat(oracleContainer.execInContainer("sqlplus", "sys / as sysdba", "@/tmp/init.sql")
                .getExitCode())
                .isZero();

        // Configure Access Control Entities (ACEs) for DBMS_CLOUD to access the external internet over HTTPS
        // Download certificates, create the database wallet, and install DBMS_CLOUD.
        oracleContainer.copyFileToContainer(MountableFile.forClasspathResource("init.sh"), "/tmp/init.sh");
        assertThat(oracleContainer.execInContainer("bash", "/tmp/init.sh")
                .getExitCode())
                .isZero();

        // Configure a test datasource
        ds = new OracleDataSource();
        ds.setURL(oracleContainer.getJdbcUrl());
        ds.setUser(oracleContainer.getUsername());
        ds.setPassword(oracleContainer.getPassword());
    }

    @Test
    public void selectAIRunsLocally() throws SQLException {
        //configureGenAIProfile();
    }
}
