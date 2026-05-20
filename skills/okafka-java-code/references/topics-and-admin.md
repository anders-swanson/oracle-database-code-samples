# Topics And Admin

Create TxEventQ topics through standard Kafka admin APIs backed by OKafka:

```java
try (Admin admin = org.oracle.okafka.clients.admin.AdminClient.create(okafkaProperties)) {
    NewTopic topic = new NewTopic(topicName, partitions, (short) 0);
    admin.createTopics(List.of(topic)).all().get();
}
```

Use replication factor `0`. In Oracle AI Database, durability and replication are database responsibilities rather than Kafka broker replication.

Use idempotent creation in tests and startup paths:

```java
static void createTopicIfNotExists(Properties okafkaProperties, NewTopic topic) {
    try (Admin admin = org.oracle.okafka.clients.admin.AdminClient.create(okafkaProperties)) {
        admin.createTopics(List.of(topic)).all().get();
    } catch (InterruptedException exception) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException("Interrupted while creating OKafka topic " + topic.name(), exception);
    } catch (ExecutionException exception) {
        if (exception.getCause() instanceof TopicExistsException) {
            return;
        }
        throw new IllegalStateException("Unable to create OKafka topic " + topic.name(), exception);
    }
}
```

For Spring apps, create topics before producer/consumer beans are used. Use an initializer bean or startup component and make raw OKafka clients depend on it when startup ordering matters.
