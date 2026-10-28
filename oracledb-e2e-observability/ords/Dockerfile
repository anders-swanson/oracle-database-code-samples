FROM container-registry.oracle.com/database/ords:latest

# Pin the agent and verify the release artifact at build time.
RUN curl -fsSL https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/download/v2.33.1/opentelemetry-javaagent.jar \
        -o /opt/oracle/ords/opentelemetry-javaagent.jar \
    && echo 'e43a270df53c6f423e0ab157be9c27ccc01d5dc3232e162fe54777573db2f149  /opt/oracle/ords/opentelemetry-javaagent.jar' | sha256sum -c -

# Keep installation and SQLcl commands free of instrumentation.
RUN mv /opt/oracle/ords/bin/ords /opt/oracle/ords/bin/ords-original
COPY --chown=oracle:oinstall serve-with-agent.sh /opt/oracle/ords/bin/ords
RUN chmod +x /opt/oracle/ords/bin/ords
COPY setup-schema.sh /ords-entrypoint.d/10-setup-schema.sh
COPY schema.sql /opt/oracle/lab-schema.sql
