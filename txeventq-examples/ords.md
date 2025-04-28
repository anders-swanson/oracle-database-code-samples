# TxEventQ with ORDS

### Environment variables

The ORDS commands in this document use the following environment variables. Configure these as appropriate for your ORDS instance.

```bash
export ORDS_URL=<ORDS URL>
export DB_USERNAME=<ORDS username>
export DB_PASSWORD='<ORDS Password>'
export DB_NAME=<Database cluster name>
```

### (1) Create a new topic

The following cURL command creates a new topic named `ords_test` with 10 partitions:

```bash
curl -X POST -u "$DB_USERNAME:$DB_PASSWORD" \
    -H 'Content-Type: application/json' \
    "${ORDS_URL}admin/_/db-api/stable/database/txeventq/clusters/${DB_NAME}/topics" -d '{
      "topic_name": "ords_test",
      "partitions_count": "10"
}'
```

#### (2) Create a consumer group

The following cURL commands creates a consumer group named `my_grp` for the `ords_test` topic:

```bash
curl -X POST -u "$DB_USERNAME:$DB_PASSWORD" \
  -H 'Content-Type: application/json' \
  "${ORDS_URL}admin/_/db-api/stable/database/txeventq/clusters/${DB_NAME}/consumer-groups/my_grp" -d '{
    "topic_name": "ords_test"
}'
```

### (3) Create a consumer

The following cURL command creates a consumer for the `my_grp` consumer group:

```bash
curl -X POST -u "$DB_USERNAME:$DB_PASSWORD" \
  "${ORDS_URL}admin/_/db-api/stable/database/txeventq/consumers/my_grp"
```

Response:
```bash
{"instance_id":"my_grp_Cons_c27b15cc560a8c2b64500d80d64c594e"}
```

The "create consumer" response contains the consumer's instance ID. save this value to the `CONSUMER_ID` environment variable. In this example, the value is `my_grp_Cons_c27b15cc560a8c2b64500d80d64c594e`; you will have your own, unique instance id.

```bash
export CONSUMER_ID=<my unique consumer instance id>
```

### (4) Produce records

The following cURL command produces a record to the `ords_test` topic:

```bash
curl -X POST -u "$DB_USERNAME:$DB_PASSWORD" \
    -H 'Content-Type: application/json' \
    "${ORDS_URL}admin/_/db-api/stable/database/txeventq/topics/ords_test" -d '{
    "records": [
      { "key": "abc", "value": "abc"}
    ]
}'
```

### (5) Consume records

The following cURL command consumes a record using the `my_grp` consumer group and the consumer instance ID from (3): 

```bash
curl -X GET -u "$DB_USERNAME:$DB_PASSWORD" \
    "${ORDS_URL}admin/_/db-api/stable/database/txeventq/consumers/my_grp/instances/${CONSUMER_ID}/records"
```
