---
name: golang/dualityviews
description: Go sample for creating and querying JSON Relational Duality Views in Oracle AI Database.
tags:
  - Database
  - Duality Views
  - Go
  - JSON
blog_post: ""
---

# Go JSON Relational Duality Views Sample

This sample demonstrates how to use Go, `godror`, and Oracle AI Database JSON Relational Duality Views together. The program creates a `student` table, creates a `student_dv` duality view, inserts JSON data into the view, and queries the JSON document back.

The sample uses the connection helper from [`../connection/README.md`](../connection/README.md), so it expects a local Oracle AI Database instance at `localhost:1521/freepdb1` by default.

## Prerequisites

- Go
- Oracle client libraries available through `DYLD_LIBRARY_PATH`
- An Oracle AI Database instance reachable at `localhost:1521/freepdb1`

## Run the sample

From the `golang/` directory:

```bash
go run ./dualityviews
```

The program prints progress as it creates the table and duality view, inserts a sample `Student` document, and selects the JSON document back from `student_dv`.
