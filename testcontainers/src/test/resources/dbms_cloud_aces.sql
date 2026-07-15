@$ORACLE_HOME/rdbms/admin/sqlsessstart.sql

-- you must not change the owner of the functionality to avoid future issues
define clouduser=C##CLOUD$SERVICE

-- CUSTOMER SPECIFIC SETUP, NEEDS TO BE PROVIDED BY THE CUSTOMER-- - SSL Wallet directory
define sslwalletdir='/opt/oracle/product/26ai/dbhomeFree/data/wallet'

---- UNCOMMENT AND SET THE PROXY SETTINGS VARIABLES IF YOUR ENVIRONMENT NEEDS PROXYS--

-- define proxy_uri=<your proxy URI address>
-- define proxy_host=<your proxy DNS name>
-- define proxy_low_port=<your_proxy_low_port>
-- define proxy_high_port=<your_proxy_high_port>

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
    --
-- UNCOMMENT THE PROXY SETTINGS SECTION IF YOUR ENVIRONMENT NEEDS PROXYS
--
-- Allow Proxy for HTTP/HTTP_PROXY
-- dbms_network_acl_admin.append_host_ace(
-- host =>'&proxy_host',
-- lower_port => &proxy_low_port,
-- upper_port => &proxy_high_port,
-- ace => xs$ace_type(
-- privilege_list => xs$name_list('http', 'http_proxy'),
-- principal_name => upper('&clouduser'),
-- principal_type => xs_acl.ptype_db));
--
-- END PROXY SECTION
--
-- Setting SSL_WALLET database property
    begin
        if sys_context('userenv', 'con_name') = 'CDB$ROOT' then
            execute immediate 'alter database property set ssl_wallet=''&sslwalletdir''';
            --
-- UNCOMMENT THE FOLLOWING COMMAND IF YOU ARE USING A PROXY
--
--        execute immediate 'alter database property set http_proxy=''&proxy_uri''';
        end if;
    end;
    /

    @$ORACLE_HOME/rdbms/admin/sqlsessend.sql