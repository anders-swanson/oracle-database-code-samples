---
name: python-oracle/src/python_oracle/full_text_search
description: Native Python full-text JSON search sample for Oracle AI Database using python-oracledb and Oracle Text.
tags:
  - Database
  - python
  - JSON
  - Oracle Text
blog_post: ""
---

# Python Full-Text Search Sample

This folder contains a native Python full-text search sample for Oracle AI Database using `python-oracledb` and Oracle Text.

The sample file is [json_text_search.py](https://github.com/anders-swanson/oracle-database-code-samples/blob/main/python-oracle/src/python_oracle/full_text_search/json_text_search.py).

## Prerequisites

- Python 3.13+
- Poetry
- Docker compatible environment

Install dependencies from the `python-oracle/` directory:

```bash
poetry install
```

## Run the sample

From the `python-oracle/` directory:

```bash
poetry run python src/python_oracle/full_text_search/json_text_search.py
```

The script uses the local Testcontainers helper to start the full Oracle AI Database Free image required by Oracle Text, creates a JSON table and search index, inserts four JSON documents, validates the expected search results, and prints ranked keyword, proximity, and filtered JSON matches.
