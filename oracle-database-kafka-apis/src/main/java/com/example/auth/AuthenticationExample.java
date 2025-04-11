package com.example.auth;

import java.util.List;
import java.util.Properties;
import java.util.concurrent.ExecutionException;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.errors.TopicExistsException;

public class AuthenticationExample {
    // For this example, we'll configure our authentication parameters with environment variables.
    // The security.protocol property supports PLAINTEXT (insecure) and SSL (secure) authentication.
    private static final String securityProtocol = System.getenv("SECURITY_PROTOCOL");

    // For PLAINTEXT authentication, provide the HOSTNAME:PORT as the bootstrap.servers property.
    private static final String bootstrapServers = System.getenv("BOOTSTRAP_SERVERS");

    // The TNS Admin alias / Oracle Database Service name.
    private static final String tnsAdmin = System.getenv("TNS_ADMIN");

    // The directory containing the database wallet. For PLAINTEXT, this directory need only
    // contain an ojdbc.properties file with the "user" and "password" properties configured.
    private static final String walletDir = System.getenv("WALLET_DIR");

    public static void main(String[] args) {
        // Just like kafka-clients, we can use a Java Properties object to configure connection parameters.
        Properties props = new Properties();

        // oracle.service.name is a custom property to configure the Database service name.
        props.put("oracle.service.name", tnsAdmin);
        // oracle.net.tns_admin is a custom property to configure the directory containing Oracle Database connection files.
        // If you are using mTLS authentication, client certificates must be present in this directory.
        props.put("oracle.net.tns_admin", walletDir);
        // security.protocol is a standard Kafka property, set to PLAINTEXT or SSL for Oracle Database.
        // (SASL is not supported with Oracle Database).
        props.put("security.protocol", securityProtocol);
        if (securityProtocol.equals("SSL")) {
            // For SSL authentication, pass the TNS alias (such as "mydb_tp") to be used from the tnsnames.ora file
            // found in the WALLET_DIR directory.
            props.put("tns.alias", tnsAdmin);
        } else {
            // For PLAINTEXT authentication, we provide the database URL in the format
            // HOSTNAME:PORT as the bootstrap.servers property.
            props.put("bootstrap.servers", bootstrapServers);
        }

        // Using our connection properties, let's create a Kafka admin client connected to Oracle Database.
        // The fully qualified types are provided for illustrative purposes:
        // The org.oracle.okafka.clients.admin.AdminClient.create method creates a KafkaAdminClient for
        // Oracle Database implementing the org.apache.kafka.clients.admin.Admin interface.
        try (org.apache.kafka.clients.admin.Admin admin =
                     org.oracle.okafka.clients.admin.AdminClient.create(props)) {

            // Note that the replication factor is 0. Replication of topic data in Oracle Database is handled
            // by external database controls, not the message broker.
            NewTopic topic = new NewTopic("authentication_example", 5, (short) 0);
            // Create the topic using standard kafka-clients APIs.
            admin.createTopics(List.of(topic))
                    .all()
                    .get();
        } catch (InterruptedException | ExecutionException e) {
            if (e.getCause() instanceof TopicExistsException) {
                // If the topic already exists, we ignore the error.
                System.out.println("Topic already exists, skipping creation");
            } else {
                throw new RuntimeException(e);
            }
        }
    }
}
