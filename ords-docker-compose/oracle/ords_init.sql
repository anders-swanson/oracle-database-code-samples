whenever sqlerror exit failure
whenever oserror exit failure
alter session set container = freepdb1;

-- Startup scripts run on every container start. Keep existing lab data intact.
create user if not exists testuser identified by testpwd
  default tablespace users quota unlimited on users;
grant connect, resource to testuser;

create table if not exists testuser.customers (
  id number primary key,
  name varchar2(100) not null,
  email varchar2(100) not null unique,
  city varchar2(50) not null,
  country varchar2(2) not null
);

create table if not exists testuser.products (
  id number primary key,
  name varchar2(100) not null,
  category varchar2(50) not null,
  unit_price number(10, 2) not null check (unit_price >= 0),
  stock_quantity number(10) not null check (stock_quantity >= 0)
);

create table if not exists testuser.orders (
  id number primary key,
  customer_id number not null references testuser.customers (id),
  ordered_at date not null,
  status varchar2(20) not null
    check (status in ('PENDING', 'SHIPPED', 'DELIVERED', 'CANCELLED'))
);

create table if not exists testuser.order_items (
  order_id number references testuser.orders (id),
  product_id number references testuser.products (id),
  quantity number(10) not null check (quantity > 0),
  -- Preserve the price at the time of purchase, independent of the product price.
  unit_price number(10, 2) not null check (unit_price >= 0),
  primary key (order_id, product_id)
);

-- Synthetic customers and sales; fixed dates make the example results repeatable.
merge into testuser.customers target
using (
  select 1 id, 'Alex Morgan' name, 'alex@example.com' email, 'Portland' city, 'US' country from dual union all
  select 2, 'Sam Patel', 'sam@example.com', 'Austin', 'US' from dual union all
  select 3, 'Jordan Lee', 'jordan@example.com', 'Seattle', 'US' from dual union all
  select 4, 'Casey Brown', 'casey@example.com', 'Denver', 'US' from dual union all
  select 5, 'Taylor Chen', 'taylor@example.com', 'Vancouver', 'CA' from dual union all
  select 6, 'Riley Smith', 'riley@example.com', 'Toronto', 'CA' from dual
) source
on (target.id = source.id)
when not matched then
  insert (id, name, email, city, country)
  values (source.id, source.name, source.email, source.city, source.country);

merge into testuser.products target
using (
  select 1 id, 'Keyboard' name, 'Electronics' category, 89.99 unit_price, 40 stock_quantity from dual union all
  select 2, 'Mouse', 'Electronics', 29.99, 75 from dual union all
  select 3, 'Monitor', 'Electronics', 249.99, 12 from dual union all
  select 4, 'Notebook', 'Office', 5.50, 200 from dual union all
  select 5, 'Pen pack', 'Office', 12.00, 150 from dual union all
  select 6, 'Desk lamp', 'Home', 39.95, 30 from dual union all
  select 7, 'Water bottle', 'Outdoors', 19.99, 80 from dual union all
  select 8, 'Backpack', 'Outdoors', 59.95, 25 from dual
) source
on (target.id = source.id)
when not matched then
  insert (id, name, category, unit_price, stock_quantity)
  values (source.id, source.name, source.category, source.unit_price, source.stock_quantity);

merge into testuser.orders target
using (
  select 1 id, 1 customer_id, date '2026-10-02' ordered_at, 'DELIVERED' status from dual union all
  select 2, 2, date '2026-10-05', 'DELIVERED' from dual union all
  select 3, 3, date '2026-10-09', 'SHIPPED' from dual union all
  select 4, 4, date '2026-10-14', 'CANCELLED' from dual union all
  select 5, 5, date '2026-10-19', 'DELIVERED' from dual union all
  select 6, 6, date '2026-10-24', 'SHIPPED' from dual union all
  select 7, 1, date '2026-11-01', 'SHIPPED' from dual union all
  select 8, 2, date '2026-11-03', 'PENDING' from dual union all
  select 9, 3, date '2026-11-05', 'PENDING' from dual union all
  select 10, 4, date '2026-11-07', 'DELIVERED' from dual union all
  select 11, 5, date '2026-11-09', 'SHIPPED' from dual union all
  select 12, 6, date '2026-11-11', 'PENDING' from dual
) source
on (target.id = source.id)
when not matched then
  insert (id, customer_id, ordered_at, status)
  values (source.id, source.customer_id, source.ordered_at, source.status);

merge into testuser.order_items target
using (
  select 1 order_id, 1 product_id, 1 quantity, 89.99 unit_price from dual union all
  select 1, 2, 1, 29.99 from dual union all
  select 2, 3, 1, 249.99 from dual union all
  select 2, 6, 1, 39.95 from dual union all
  select 3, 4, 5, 5.50 from dual union all
  select 3, 5, 2, 12.00 from dual union all
  select 4, 8, 1, 59.95 from dual union all
  select 4, 7, 2, 19.99 from dual union all
  select 5, 1, 2, 89.99 from dual union all
  select 5, 2, 2, 29.99 from dual union all
  select 6, 6, 2, 39.95 from dual union all
  select 6, 4, 4, 5.50 from dual union all
  select 7, 3, 1, 249.99 from dual union all
  select 7, 1, 1, 89.99 from dual union all
  select 8, 8, 2, 59.95 from dual union all
  select 8, 7, 2, 19.99 from dual union all
  select 9, 2, 3, 29.99 from dual union all
  select 9, 5, 1, 12.00 from dual union all
  select 10, 6, 1, 39.95 from dual union all
  select 10, 7, 1, 19.99 from dual union all
  select 11, 4, 10, 5.50 from dual union all
  select 11, 5, 3, 12.00 from dual union all
  select 12, 3, 2, 249.99 from dual union all
  select 12, 8, 1, 59.95 from dual
) source
on (target.order_id = source.order_id and target.product_id = source.product_id)
when not matched then
  insert (order_id, product_id, quantity, unit_price)
  values (source.order_id, source.product_id, source.quantity, source.unit_price);

create user if not exists mcp_reader identified by ReadOnly12345;
grant create session to mcp_reader;
grant select on testuser.customers to mcp_reader;
grant select on testuser.products to mcp_reader;
grant select on testuser.orders to mcp_reader;
grant select on testuser.order_items to mcp_reader;

-- Expose discoverable, read-only views in the MCP pool's own schema.
create or replace view mcp_reader.customers as
  select id, name, email, city, country from testuser.customers
  with read only;

create or replace view mcp_reader.products as
  select id, name, category, unit_price, stock_quantity from testuser.products
  with read only;

create or replace view mcp_reader.order_items as
  select order_id, product_id, quantity, unit_price from testuser.order_items
  with read only;

-- Compute order totals from the purchase prices rather than storing a second total.
create or replace view mcp_reader.orders as
  select o.id, o.customer_id, o.ordered_at, o.status,
         nvl(sum(i.quantity * i.unit_price), 0) as amount
  from testuser.orders o
  left join testuser.order_items i on i.order_id = o.id
  group by o.id, o.customer_id, o.ordered_at, o.status
  with read only;

commit;
exit
