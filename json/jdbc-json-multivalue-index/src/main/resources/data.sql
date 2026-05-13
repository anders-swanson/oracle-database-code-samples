insert into json_inventory (product_doc) values (json('
{
  "sku": "BACKPACK-20",
  "name": "Commuter Backpack",
  "category": "accessory",
  "compatiblePartIds": [501, 502],
  "warehouses": [
    { "region": "US-EAST", "quantity": 12 }
  ],
  "components": [
    { "type": "strap", "partId": 501 },
    { "type": "zipper", "partId": 502 }
  ]
}
'))
;
insert into json_inventory (product_doc) values (json('
{
  "sku": "DOCK-USB-C",
  "name": "USB-C Dock",
  "category": "hardware",
  "compatiblePartIds": [102, 203, 401],
  "warehouses": [
    { "region": "US-WEST", "quantity": 7 },
    { "region": "EU-CENTRAL", "quantity": 2 }
  ],
  "components": [
    { "type": "adapter", "partId": 102 },
    { "type": "port", "partId": 203 }
  ]
}
'))
;
insert into json_inventory (product_doc) values (json('
{
  "sku": "LAPTOP-15",
  "name": "Developer Laptop",
  "category": "hardware",
  "compatiblePartIds": [101, 102, 301],
  "warehouses": [
    { "region": "US-EAST", "quantity": 4 },
    { "region": "US-WEST", "quantity": 3 }
  ],
  "components": [
    { "type": "battery", "partId": 101 },
    { "type": "adapter", "partId": 102 }
  ]
}
'))
;
insert into json_inventory (product_doc) values (json('
{
  "sku": "MONITOR-27",
  "name": "27 Inch Monitor",
  "category": "hardware",
  "compatiblePartIds": [203, 301],
  "warehouses": [
    { "region": "US-EAST", "quantity": 5 }
  ],
  "components": [
    { "type": "port", "partId": 203 },
    { "type": "stand", "partId": 301 }
  ]
}
'))
;
