# Properties Management API

This README provides curl commands to interact with the PropertiesController API for managing properties in the database.

The API endpoints are mapped under `/api/properties`. Assume the server is running on `http://localhost:8888` (default Spring Boot port).

## Get All Properties

Retrieve a list of all properties.

```bash
curl http://localhost:8888/api/properties
```

## Get Property by ID

Retrieve a specific property by its ID. Replace `{id}` with the actual ID.

```bash
curl http://localhost:8888/api/properties/{id}
```

Example:
```bash
curl http://localhost:8888/api/properties/1
```

## Create a New Property

Create a new property. Provide the property details in JSON format.

```bash
curl -X POST http://localhost:8888/api/properties \
     -H "Content-Type: application/json" \
     -d '{
           "application": "myapp",
           "profile": "dev",
           "label": "latest",
           "propKey": "config.key",
           "value": "config-value"
         }'
```

## Update a Property

Update an existing property by its ID. Replace `{id}` with the actual ID and provide updated details in JSON.

```bash
curl -X PUT http://localhost:8888/api/properties/{id} \
     -H "Content-Type: application/json" \
     -d '{
           "application": "myapp",
           "profile": "prod",
           "label": "latest",
           "propKey": "config.key",
           "value": "updated-value"
         }'
```

Example:
```bash
curl -X PUT http://localhost:8888/api/properties/1 \
     -H "Content-Type: application/json" \
     -d '{
           "application": "myapp",
           "profile": "prod",
           "label": "latest",
           "propKey": "config.key",
           "value": "updated-value"
         }'
```

## Delete a Property

Delete a property by its ID. Replace `{id}` with the actual ID.

```bash
curl -X DELETE http://localhost:8888/api/properties/{id}
```

Example:
```bash
curl -X DELETE http://localhost:8888/api/properties/1
```

Note: The `propKey` field corresponds to the `key` column in the database, renamed to avoid Java keyword conflicts.
