alter session set container = freepdb1;

-- Create app user. You may wish to modify tablespace as needed.
create user if not exists testuser identified by testpwd;
grant create session to testuser;
grant connect, resource, unlimited tablespace to testuser

-- Grants for the Kafka Java Client for Oracle Database Transactional Event Queues
grant aq_user_role to testuser;
grant execute on dbms_aq to  testuser;
grant execute on dbms_aqadm to testuser;
grant select on gv_$session to testuser;
grant select on v_$session to testuser;
grant select on gv_$instance to testuser;
grant select on gv_$listener_network to testuser;
grant select on sys.dba_rsrc_plan_directives to testuser;
grant select on gv_$pdbs to testuser;
grant select on user_queue_partition_assignment_table to testuser;
exec dbms_aqadm.grant_priv_for_rm_plan('testuser');
commit;

alter session set current_schema = testuser;

-- News articles
create table if not exists news (
    news_id   raw(16) primary key,
<<<<<<< HEAD
    highlight varchar2(2000),
=======
    title     varchar2(1000),
>>>>>>> 2f04e9360fe556b3a2a63ad010667db95076c325
    article   clob,
    published timestamp
);

-- News Vectors, one-to-one with news
create table if not exists news_vector (
<<<<<<< HEAD
    id        raw(16) primary key,
    news_id   raw(16),
    chunk     varchar2(2000),
=======
    news_id   raw(16) primary key,
>>>>>>> 2f04e9360fe556b3a2a63ad010667db95076c325
    embedding vector(1024, FLOAT64) annotations(Distance 'COSINE', IndexType 'IVF'),
    constraint fk_news_vector foreign key (news_id)
    references news(news_id) on delete cascade
);

-- Vector index for News Vectors
create vector index if not exists news_vector_index on news_vector (embedding)
    organization neighbor partitions
    distance COSINE
    with target accuracy 95
    parameters (type IVF, neighbor partitions 10);

<<<<<<< HEAD
-- JSON Relational Duality View for the News Schema
create force editionable json relational duality view news_dv
 as
news {
    _id : news_id
    news_vector
             @link (to : [news_id]) {
        news_id : news_id
        embedding
    }
};
=======
-- JSON Relational Duality View for the News Schema
>>>>>>> 2f04e9360fe556b3a2a63ad010667db95076c325
