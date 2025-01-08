package com.example;

import java.util.Objects;
import java.util.Properties;

public class OKafkaProperties {
    public static Properties getLocalConnectionProps(String ojdbcPropertiesFile,
                                                     Integer port)  {
        Properties props = new Properties();
        // We use the default PDB for Oracle Database 23ai.
        props.put("oracle.service.name", "freepdb1");
        // The localhost connection uses PLAINTEXT.
        props.put("security.protocol", "PLAINTEXT");
        props.put("bootstrap.servers", String.format("localhost:%d",
                Objects.requireNonNullElse(port, 1521)));
        props.put("oracle.net.tns_admin", ojdbcPropertiesFile);
        return props;
    }
}
