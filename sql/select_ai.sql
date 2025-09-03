-- Simple customer/product/order schema. A customer may have one or more orders for various products.
create table customers (
                           customer_id number generated always as identity primary key,
                           first_name varchar2(50) not null,
                           last_name varchar2(50) not null
);
create table products (
                          product_id number generated always as identity primary key,
                          product_name varchar2(100) not null,
                          price number(10,2) not null
);
create table orders (
                        order_id number generated always as identity primary key,
                        customer_id number not null,
                        product_id number not null,
                        quantity number not null,
                        order_date date default sysdate,
                        total_amount number(10,2) not null,
                        constraint fk_customer foreign key (customer_id) references customers(customer_id),
                        constraint fk_product foreign key (product_id) references products(product_id)
);

-- Insert Customers
insert into customers (first_name, last_name) values ('John', 'Doe');
insert into customers (first_name, last_name) values ('Jane', 'Smith');
insert into customers (first_name, last_name) values ('Robert', 'Johnson');
insert into customers (first_name, last_name) values ('Emily', 'Brown');
insert into customers (first_name, last_name) values ('Michael', 'Davis');
insert into customers (first_name, last_name) values ('Sarah', 'Wilson');
insert into customers (first_name, last_name) values ('David', 'Martinez');
insert into customers (first_name, last_name) values ('Lisa', 'Anderson');
insert into customers (first_name, last_name) values ('William', 'Taylor');
insert into customers (first_name, last_name) values ('Jennifer', 'Thomas');

-- Insert Products
insert into products (product_name, price) values ('Laptop', 999.99);
insert into products (product_name, price) values ('Smartphone', 599.99);
insert into products (product_name, price) values ('Tablet', 299.99);
insert into products (product_name, price) values ('Headphones', 149.99);
insert into products (product_name, price) values ('Smart Watch', 249.99);
insert into products (product_name, price) values ('Camera', 449.99);
insert into products (product_name, price) values ('Printer', 199.99);
insert into products (product_name, price) values ('External Hard Drive', 89.99);
insert into products (product_name, price) values ('Gaming Console', 399.99);
insert into products (product_name, price) values ('Wireless Mouse', 29.99);

-- Insert Orders
insert into orders (customer_id, product_id, quantity, order_date, total_amount) values (1, 1, 1, TO_DATE('2024-11-01', 'YYYY-MM-DD'), 999.99);
insert into orders (customer_id, product_id, quantity, order_date, total_amount) values (2, 2, 2, TO_DATE('2024-11-02', 'YYYY-MM-DD'), 1199.98);
insert into orders (customer_id, product_id, quantity, order_date, total_amount) values (3, 3, 1, TO_DATE('2024-11-03', 'YYYY-MM-DD'), 299.99);
insert into orders (customer_id, product_id, quantity, order_date, total_amount) values (4, 4, 3, TO_DATE('2024-11-04', 'YYYY-MM-DD'), 449.97);
insert into orders (customer_id, product_id, quantity, order_date, total_amount) values (5, 5, 1, TO_DATE('2024-11-05', 'YYYY-MM-DD'), 249.99);
insert into orders (customer_id, product_id, quantity, order_date, total_amount) values (6, 6, 1, TO_DATE('2024-11-06', 'YYYY-MM-DD'), 449.99);
insert into orders (customer_id, product_id, quantity, order_date, total_amount) values (7, 7, 2, TO_DATE('2024-11-07', 'YYYY-MM-DD'), 399.98);
insert into orders (customer_id, product_id, quantity, order_date, total_amount) values (8, 8, 4, TO_DATE('2024-11-08', 'YYYY-MM-DD'), 359.96);
insert into orders (customer_id, product_id, quantity, order_date, total_amount) values (9, 9, 1, TO_DATE('2024-11-09', 'YYYY-MM-DD'), 399.99);
insert into orders (customer_id, product_id, quantity, order_date, total_amount) values (10, 10, 5, TO_DATE('2024-11-10', 'YYYY-MM-DD'), 149.95);
insert into orders (customer_id, product_id, quantity, order_date, total_amount) values (1, 2, 1, TO_DATE('2024-11-11', 'YYYY-MM-DD'), 599.99);
insert into orders (customer_id, product_id, quantity, order_date, total_amount) values (2, 3, 2, TO_DATE('2024-11-12', 'YYYY-MM-DD'), 599.98);
insert into orders (customer_id, product_id, quantity, order_date, total_amount) values (3, 4, 1, TO_DATE('2024-11-13', 'YYYY-MM-DD'), 149.99);
insert into orders (customer_id, product_id, quantity, order_date, total_amount) values (4, 5, 1, TO_DATE('2024-11-14', 'YYYY-MM-DD'), 249.99);
insert into orders (customer_id, product_id, quantity, order_date, total_amount) values (5, 6, 1, TO_DATE('2024-11-15', 'YYYY-MM-DD'), 449.99);
insert into orders (customer_id, product_id, quantity, order_date, total_amount) values (6, 7, 1, TO_DATE('2024-11-16', 'YYYY-MM-DD'), 199.99);
insert into orders (customer_id, product_id, quantity, order_date, total_amount) values (7, 8, 2, TO_DATE('2024-11-17', 'YYYY-MM-DD'), 179.98);
insert into orders (customer_id, product_id, quantity, order_date, total_amount) values (8, 9, 1, TO_DATE('2024-11-18', 'YYYY-MM-DD'), 399.99);
insert into orders (customer_id, product_id, quantity, order_date, total_amount) values (9, 10, 3, TO_DATE('2024-11-19', 'YYYY-MM-DD'), 89.97);
insert into orders (customer_id, product_id, quantity, order_date, total_amount) values (10, 1, 1, TO_DATE('2024-11-20', 'YYYY-MM-DD'), 999.99);


BEGIN
    DBMS_CLOUD.CREATE_CREDENTIAL(
        credential_name => 'GENAI_CRED',
        user_ocid       => 'Your User OCID',
        tenancy_ocid    => 'Your Tenancy OCID',
        private_key     => 'Your Private Key in PEM format',
        fingerprint     => 'Your Private Key Fingerprint'
    );

    DBMS_CLOUD_AI.CREATE_PROFILE(
        profile_name =>'GENAI',
        attributes   =>'{"provider": "oci",
            "credential_name": "GENAI_CRED",
            "object_list": [{"owner": "ADMIN", "name": "customers"},
                            {"owner": "ADMIN", "name": "products"},
                            {"owner": "ADMIN", "name": "orders"}]
        }');
    -- Set the Select AI Profile
EXEC DBMS_CLOUD_AI.SET_PROFILE('GENAI');
END;
/