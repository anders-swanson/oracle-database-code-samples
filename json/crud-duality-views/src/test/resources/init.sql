create table products (
    product_id number generated as identity primary key,
    name varchar2(255) not null,
    price number(10,2) not null
);

create table orders (
    order_id number generated as identity primary key,
    product_id number not null,
    quantity number not null,
    order_date date default sysdate,
    constraint fk_orders_product foreign key (product_id) references products(product_id) on delete cascade
);

create or replace force editionable json relational duality view
orders_dv as orders @insert @update @delete {
  _id : order_id
  quantity
  orderDate : order_date
  product : products @insert @update {
    _id : product_id
    name
    price
  }
}
/
