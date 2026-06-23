package com.example.ucp;

import javax.sql.DataSource;

import oracle.ucp.jdbc.PoolDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
class PoolReporter implements ApplicationRunner {
    private static final Logger LOGGER = LoggerFactory.getLogger(PoolReporter.class);

    private final DataSource dataSource;

    PoolReporter(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        PoolDataSource poolDataSource = dataSource.unwrap(PoolDataSource.class);
        LOGGER.info("Oracle UCP configuration: {}", PoolReport.from(poolDataSource));
        LOGGER.info("Oracle UCP pool metrics: {}", PoolMetrics.from(poolDataSource));
        LOGGER.info("Oracle UCP diagnostics: {}", PoolDiagnostics.from(poolDataSource));
    }
}
