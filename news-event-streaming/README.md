# News Event Streaming Application 

Under Construction! Stay tuned.

# POST a single article

curl -X POST "http://localhost:8080/news" \
  -H "Content-Type: application/json" \
  -d "@src/test/resources/one-record.json"

# POST a large batch of articles

