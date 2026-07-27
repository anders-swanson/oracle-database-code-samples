package com.example.nl2sql;


import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.testcontainers.junit.jupiter.Testcontainers;

import static com.example.nl2sql.LocalSelectAIContainer.admin;
import static com.example.nl2sql.LocalSelectAIContainer.batman;
import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@Disabled
@EnabledIfEnvironmentVariable(named = "OCI_COMPARTMENT_ID", matches = ".+")
public class NL2SQLTest {

    @BeforeAll
    static void beforeAll() throws Exception {
        LocalSelectAIContainer.start();
    }

    private final SelectAI selectai = new SelectAI("MY_PROFILE");

    @Test
    void adminSeesAllClaimsWhileBatmanSeesOnlyHisClaims() throws Exception {
        String prompt = """
                Can you show me all my insurance claims?
                """;

        String adminClaims;
        try (var connection = admin.getConnection()) {
            adminClaims = selectai.call(connection, prompt, SelectAI.Action.RUNSQL);
        }
        System.out.println("ADMIN claims view:\n" + adminClaims);

        String batmanClaims;
        try (var connection = batman.getConnection()) {
            batmanClaims = selectai.call(connection, prompt, SelectAI.Action.RUNSQL);
        }

        System.out.println("BATMAN claims view:\n" + adminClaims);

        assertThat(adminClaims).contains("1001", "1002", "1003", "1004", "1005", "1006");
        assertThat(batmanClaims)
                .contains("1001", "1003", "1004")
                .doesNotContain("1002", "1005", "1006");
    }
}
