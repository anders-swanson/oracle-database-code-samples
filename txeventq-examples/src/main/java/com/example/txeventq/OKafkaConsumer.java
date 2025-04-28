package com.example.txeventq;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.oracle.okafka.clients.consumer.KafkaConsumer;

import static com.example.txeventq.Prompt.prompt;
import static com.example.txeventq.Values.OKAFKA_TOPIC_NAME;

public class OKafkaConsumer {
    public static void main(String[] args) throws SQLException {
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

        props.put("group.id" , "MY_CONSUMER_GROUP");
        props.put("enable.auto.commit","false");
        props.put("max.poll.records", 2000);
        props.put("auto.offset.reset", "earliest");
        props.put("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
        props.put("value.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
        KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props);

        consumer.subscribe(List.of(OKAFKA_TOPIC_NAME));
        while (true) {
            ConsumerRecords<String, String> records = consumer.poll(Duration.ofSeconds(1));

            for (ConsumerRecord<String, String> record : records) {
                String value = record.value();
                System.out.println("Consumed message: " + value);
            }
            // Blocking commit on the current batch of records. For non-blocking, use commitAsync()
            consumer.commitSync();
        }
    }
}
