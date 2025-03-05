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
    news_id    varchar2(36) default sys_guid() primary key,
    article    clob
);

-- News Vectors, many-to-one with news
create table if not exists news_vector (
    id        varchar2(36) default sys_guid() primary key,
    news_id   varchar2(36) ,
    chunk     varchar2(2250),
    embedding vector(1024, FLOAT64) annotations(Distance 'COSINE', IndexType 'IVF'),
    constraint fk_news_vector foreign key (news_id)
    references news(news_id) on delete cascade
);

-- Vector index for News Vectors
create vector index news_vector_ivf_idx on news_vector (embedding) organization neighbor partitions
distance COSINE
with target accuracy 90
parameters (type IVF, neighbor partitions 10);

-- JSON Relational Duality View for the News Schema
create or replace force editionable json relational duality view news_dv
 as
news @insert @update @delete {
    _id : news_id
    article
    news_vector @insert @update @delete
             @link (to : [NEWS_ID]) {
        id
        chunk
        embedding
    }
}
;