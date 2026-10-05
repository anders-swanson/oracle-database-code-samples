-- Each stage commits its database changes and next-stage TxEventQ events in one transaction.
CREATE TABLE transcripts (
    transcript_id NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    source_event_id RAW(16) NOT NULL UNIQUE CHECK (VSIZE(source_event_id) = 16),
    owner_scope VARCHAR2(256 CHAR) NOT NULL,
    event_payload JSON NOT NULL,
    preparation_status VARCHAR2(16 CHAR) DEFAULT 'READY' NOT NULL
        CHECK (preparation_status IN ('READY', 'DONE', 'NO_MEMORY')),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL
);

-- Only admitted candidates are stored, alongside their memory rows.
CREATE TABLE candidate_work (
    candidate_id RAW(16) PRIMARY KEY,
    transcript_id NUMBER NOT NULL REFERENCES transcripts(transcript_id),
    evidence CLOB NOT NULL,
    candidate_text CLOB NOT NULL,
    judge_score NUMBER(3) NOT NULL CHECK (judge_score BETWEEN 0 AND 100),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL
);

CREATE INDEX ix_candidate_work_transcript ON candidate_work(transcript_id);

-- Text is immutable in this lab. A null embedding keeps the memory out of search.
CREATE TABLE event_memories (
    id RAW(16) PRIMARY KEY,
    candidate_id RAW(16) NOT NULL UNIQUE REFERENCES candidate_work(candidate_id),
    owner_scope VARCHAR2(256 CHAR) NOT NULL,
    memory_text CLOB NOT NULL,
    embedding VECTOR,
    status VARCHAR2(16 CHAR) DEFAULT 'active' NOT NULL CHECK (status IN ('active', 'inactive')),
    expires_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL
);
