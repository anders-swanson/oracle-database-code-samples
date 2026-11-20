whenever sqlerror exit failure
whenever oserror exit failure

-- Each MCP pool logs in as a local Deep Data Security end user.
create end user if not exists sales_us_reader identified by "LocalUSReader12345";
create end user if not exists sales_ca_reader identified by "LocalCAReader12345";

create role if not exists sales_reader_connection;
grant create session to sales_reader_connection;

create data role if not exists ords_sales_us;
create data role if not exists ords_sales_ca;
grant sales_reader_connection to ords_sales_us;
grant sales_reader_connection to ords_sales_ca;
grant data role ords_sales_us to sales_us_reader;
grant data role ords_sales_ca to sales_ca_reader;

create or replace data grant testuser.ords_sales_us_customers
  as select (id, name, city, country)
  on testuser.customers
  where country = 'US'
  to ords_sales_us;

create or replace data grant testuser.ords_sales_ca_customers
  as select (id, name, email, city, country)
  on testuser.customers
  where country = 'CA'
  to ords_sales_ca;

exit
