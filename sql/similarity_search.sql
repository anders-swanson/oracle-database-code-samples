-- Create a table using the vector datatype
-- https://docs.oracle.com/en/database/oracle/oracle-database/23/vecse/create-tables-using-vector-data-type.html
create table my_vector_table (
                                 id number generated always as identity primary key,
                                 content clob,
                                 embedding vector(1024,FLOAT64) -- replace '1024' with your vector dimension
        annotations(Distance 'COSINE', IndexType 'IVF')
);

-- Create a vector index
-- https://docs.oracle.com/en/database/oracle/oracle-database/23/sqlrf/create-vector-index.html
-- https://docs.oracle.com/en/database/oracle/oracle-database/23/vecse/create-vector-indexes-and-hybrid-vector-indexes.html
create vector index vector_index on my_vector_table (embedding)
    organization neighbor partitions
    distance COSINE -- see other distance metrics: https://docs.oracle.com/en/database/oracle/oracle-database/23/vecse/vector-distance-metrics.html
    with target accuracy 95 -- high value is more accurate, also more computationally expensive
    parameters (
        type IVF, -- You may also use an in-memory graph vector index
        neighbor partitions 10
    );

-- Vector search (Similarity search) on vector table
-- https://docs.oracle.com/en/database/oracle/oracle-database/23/vecse/overview-ai-vector-search.html
select * from (
                  -- calculate similarity score using vector_distance function
                  select id, content, embedding, (1 - vector_distance(embedding, ?, COSINE)) as score
                  from my_vector_table
                  order by score desc
              ) where score >= ? -- filter any vectors less similar than a given score
    fetch first 5 rows only;