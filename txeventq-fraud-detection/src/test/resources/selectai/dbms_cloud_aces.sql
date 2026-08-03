whenever sqlerror exit failure rollback;

@$ORACLE_HOME/rdbms/admin/sqlsessstart.sql

-- you must not change the owner of the functionality to avoid future issues
define clouduser=C##CLOUD$SERVICE

-- Create New ACL / ACE s
begin
    -- Allow all hosts for HTTP/HTTP_PROXY
    dbms_network_acl_admin.append_host_ace(
            host =>'*',
            lower_port => 443,
            upper_port => 443,
            ace => xs$ace_type(
                    privilege_list => xs$name_list('http', 'http_proxy'),
                    principal_name => upper('&clouduser'),
                    principal_type => xs_acl.ptype_db
                   )
    );
end;
/

-- Setting SSL_WALLET database property
alter database property set SSL_WALLET='/opt/oracle/product/26ai/dbhomeFree/data/wallet';

@$ORACLE_HOME/rdbms/admin/sqlsessend.sql
