package com.example.nl2sql;


import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@EnabledIfEnvironmentVariable(named = "OCI_COMPARTMENT_ID", matches = ".+")
public class NL2SQLTest {
    @BeforeAll
    static void beforeAll() throws Exception {
        LocalSelectAIContainer.start();
    }

    @Test
    public void nl2sqlTest() throws Exception {

    }

}
