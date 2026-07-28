whenever sqlerror exit failure rollback;

-- Set as appropriate for your database. "freepdb1" is the default PDB in Oracle AI Database Free
alter session set container = freepdb1;

-- add grants for DMBS_CLOUD family packages
create user selectai identified by Welcome12345 quota unlimited on users;
grant connect, resource to selectai;
grant create public synonym to selectai;
grant execute on dbms_cloud to selectai;
grant execute on dbms_cloud_ai to selectai;
grant select any table on schema HEROES to selectai;

-- Local end users have no schema objects. Data roles grant them only the
-- sample data required for their Select AI requests.
create end user "batman" identified by Welcome12345;
create end user "admin" identified by Welcome12345;

create data role batman_role;
create data role admin_role;

create role heroes_role;
grant create session to heroes_role;
grant execute on dbms_cloud_ai to heroes_role;
grant heroes_role to batman_role;
grant heroes_role to admin_role;

grant data role batman_role to "batman";
grant data role admin_role to "admin";

-- Batman's end-user identity determines which hero's battle claims are visible.
create data grant heroes.batman_claim_access
    as select
    on heroes.insurance_claims
    where battle_id in (
        select b.battle_id
        from heroes.battles b
        join heroes.heroes h on h.hero_id = b.hero_id
        where lower(h.hero_name) = ORA_END_USER_CONTEXT.username
    )
    to batman_role;

-- The admin end user can read the complete superhero schema.
create data grant heroes.admin_heroes_access
    as select on heroes.heroes to admin_role;
create data grant heroes.admin_villains_access
    as select on heroes.villains to admin_role;
create data grant heroes.admin_districts_access
    as select on heroes.city_districts to admin_role;
create data grant heroes.admin_battles_access
    as select on heroes.battles to admin_role;
create data grant heroes.admin_claims_access
    as select on heroes.insurance_claims to admin_role;

-- allow end users to read the instantiated context attributes
create data grant heroes.hcm_context_read
    as select
       on sys.end_user_context
       where owner = 'HEROES' and name = 'HCM_CONTEXT'
           to batman_role, admin_role;

-- Prevent direct object grants from bypassing Batman's row-level claim policy.
set use data grants only on heroes.insurance_claims enabled;
