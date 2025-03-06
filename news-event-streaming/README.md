# News Event Streaming Application 

Under Construction! Stay tuned for a complete README and article.

# POST a single article

```bash
curl -X POST "http://localhost:8080/news" \
  -H "Content-Type: application/json" \
  -d "@src/test/resources/one-record.json"
```

# POST a small batch of articles

Ingest a subset of the news dataset.

```bash
curl -X POST "http://localhost:8080/news" \
  -H "Content-Type: application/json" \
  -d "@src/test/resources/one-record.json"
```

# POST a large batch of articles

> Warning: this contains almost 12,000 records, and may be expensive to embed. 

```bash
curl -X POST "http://localhost:8080/news" \
  -H "Content-Type: application/json" \
  -d "@src/test/resources/input-data.json"
```

# Truncate data (cleanup all embeddings and data)

```bash
curl -X POST "http://localhost:8080/news/reset"
```

# Find articles similar to a search query

```bash
curl -X GET "http://localhost:8080/news" \
  -H "Content-Type: application/json" \
  -d '{ "input": "CNN", "minScore": 0.5}'
```