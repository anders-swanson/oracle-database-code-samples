package com.example.txeventq;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.Collections;
import java.util.Properties;
import java.util.concurrent.ExecutionException;

import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.errors.TopicExistsException;
import org.oracle.okafka.clients.admin.AdminClient;
import org.oracle.okafka.clients.producer.KafkaProducer;

import static com.example.txeventq.Prompt.prompt;
import static com.example.txeventq.Values.OKAFKA_TOPIC_NAME;

public class OKafkaProducer {
    public static void main(String[] args) {
        // Connection properties
        Properties props = new Properties();
        props.setProperty("bootstrap.servers", "localhost:1521");
        props.setProperty("security.protocol", "PLAINTEXT");

        // Database service name / TNS Alias
        props.put("oracle.service.name", "freepdb1");
        // If using Oracle Database wallet, pass wallet directory
        String resourcesDir = new File(OKafkaProducer.class.getClassLoader()
                .getResource("")
                .getFile())
                .getAbsolutePath();
        props.put("oracle.net.tns_admin", resourcesDir);

        // Create a topic using the Admin client
        try (Admin admin = AdminClient.create(props)) {
            admin.createTopics(Collections.singletonList(new NewTopic(OKAFKA_TOPIC_NAME, 1, (short) 1)))
                    .all()
                    .get();
        } catch (ExecutionException | InterruptedException e) {
            if (e.getCause() instanceof TopicExistsException) {
                // If the topic already exists, log a message and continue
                System.out.println("Topic already exists, skipping creation");
            } else {
                throw new RuntimeException(e);
            }
        }

        // Producer props
        props.put("enable.idempotence", "true");
        props.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        props.put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        // This property is required for transactional producers
        props.put("oracle.transactional.producer", "true");

        KafkaProducer<String, String> producer = new KafkaProducer<>(props);
        // Call once after object creation
        producer.initTransactions();

        // Produce messages in a prompt loop
        prompt((s) -> {
            // Begin a new producer transaction
            producer.beginTransaction();

            // Get the producer's database connection
            Connection conn = producer.getDBConnection();
            try (PreparedStatement ps = conn.prepareStatement("insert into okafka_messages (message) values (?)")) {
                // Send the message to the topic
                producer.send(new ProducerRecord<>(OKAFKA_TOPIC_NAME, s));

                // Save the message to a database table in the same transaction
                ps.setString(1, s);
                ps.executeUpdate();

                // When done processing, commit the transaction
                producer.commitTransaction();
            } catch (Exception e) {
                producer.abortTransaction();
            }
        });
    }
}
