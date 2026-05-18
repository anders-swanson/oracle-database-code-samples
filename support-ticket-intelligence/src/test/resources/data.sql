insert into customers (customer_id, name, tier, region)
values (1, 'Acme Manufacturing', 'ENTERPRISE', 'WEST');

insert into customers (customer_id, name, tier, region)
values (2, 'Brightline Retail', 'ENTERPRISE', 'EAST');

insert into customers (customer_id, name, tier, region)
values (3, 'Cedar Labs', 'SMB', 'CENTRAL');

insert into products (product_id, name, specs)
values (100, 'Checkout Router 9000', json('
{
  "sku": "CXROUTER9K",
  "family": "checkout-networking",
  "diagnostics": ["ORA12541", "listener", "connection-refused"],
  "release": "2026.04"
}'));

insert into products (product_id, name, specs)
values (200, 'Inventory Sync Gateway', json('
{
  "sku": "INVGATE4",
  "family": "inventory-sync",
  "diagnostics": ["timeout", "queue-lag"],
  "release": "2026.02"
}'));

insert into customer_orders (order_id, customer_id, product_id, order_status)
values (500, 1, 100, 'OPEN');

insert into customer_orders (order_id, customer_id, product_id, order_status)
values (501, 2, 100, 'OPEN');

insert into customer_orders (order_id, customer_id, product_id, order_status)
values (502, 3, 200, 'SHIPPED');

insert into runbooks (runbook_id, product_family, error_code, title, body)
values (
    1,
    'checkout-networking',
    'ORA12541',
    'Resolve checkout router listener refusals',
    'For CXROUTER9K incidents with ORA12541, verify the listener route, refresh service registration, and compare with prior ticket TCK1001 before restarting checkout services.'
);

insert into runbooks (runbook_id, product_family, error_code, title, body)
values (
    2,
    'inventory-sync',
    'QUEUEBACKLOG',
    'Clear inventory queue backlog',
    'For INVGATE4 queue backlog incidents, drain retry messages before scaling workers.'
);

insert into support_tickets (
    ticket_id, customer_id, order_id, product_id, subject, body, status, sla_status, payload
) values (
    1001,
    2,
    501,
    100,
    'Checkout terminals cannot reach router after update',
    'Prior incident TCK1001: enterprise checkout terminals hit ORA12541 while connecting through CXROUTER9K after a routing table update.',
    'OPEN',
    'OPEN',
    json('
{
  "ticketCode": "TCK1001",
  "subject": "Checkout terminals cannot reach router after update",
  "body": "Enterprise checkout terminals hit ORA12541 while connecting through CXROUTER9K after a routing table update.",
  "errorCode": "ORA12541",
  "sku": "CXROUTER9K",
  "severity": "HIGH"
}')
);

insert into support_tickets (
    ticket_id, customer_id, order_id, product_id, subject, body, status, sla_status, payload
) values (
    1002,
    3,
    502,
    200,
    'Inventory gateway retry queue is delayed',
    'Prior incident TCK1002: inventory sync experienced QUEUEBACKLOG for INVGATE4 after retry workers fell behind.',
    'CLOSED',
    'RESOLVED',
    json('
{
  "ticketCode": "TCK1002",
  "subject": "Inventory gateway retry queue is delayed",
  "body": "Inventory sync experienced QUEUEBACKLOG for INVGATE4 after retry workers fell behind.",
  "errorCode": "QUEUEBACKLOG",
  "sku": "INVGATE4",
  "severity": "MEDIUM"
}')
);

insert into ticket_product_edges (ticket_id, product_id, relationship)
values (1001, 100, 'AFFECTS');

insert into ticket_product_edges (ticket_id, product_id, relationship)
values (1002, 200, 'AFFECTS');

commit;
