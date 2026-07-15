-- Customer and sales data for the NL2SQL guardrails sample.

CREATE TABLE IF NOT EXISTS customers (
    customer_id       NUMBER GENERATED ALWAYS AS IDENTITY,
    customer_number   VARCHAR2(20)  NOT NULL,
    company_name      VARCHAR2(120) NOT NULL,
    industry          VARCHAR2(60)  NOT NULL,
    customer_segment  VARCHAR2(20)  NOT NULL,
    region            VARCHAR2(30)  NOT NULL,
    country_code      CHAR(2)       NOT NULL,
    account_manager   VARCHAR2(80),
    created_at        DATE DEFAULT SYSDATE NOT NULL,
    CONSTRAINT pk_customers PRIMARY KEY (customer_id),
    CONSTRAINT uq_customers_number UNIQUE (customer_number),
    CONSTRAINT ck_customers_segment CHECK (customer_segment IN ('SMB', 'MID_MARKET', 'ENTERPRISE'))
);

CREATE TABLE IF NOT EXISTS products (
    product_id        NUMBER GENERATED ALWAYS AS IDENTITY,
    sku               VARCHAR2(30)  NOT NULL,
    product_name      VARCHAR2(120) NOT NULL,
    category          VARCHAR2(50)  NOT NULL,
    product_tier      VARCHAR2(20)  NOT NULL,
    list_price        NUMBER(12,2)  NOT NULL,
    unit_cost         NUMBER(12,2)  NOT NULL,
    CONSTRAINT pk_products PRIMARY KEY (product_id),
    CONSTRAINT uq_products_sku UNIQUE (sku),
    CONSTRAINT ck_products_price CHECK (list_price > unit_cost AND unit_cost > 0)
);

CREATE TABLE IF NOT EXISTS orders (
    order_id          NUMBER GENERATED ALWAYS AS IDENTITY,
    order_number      VARCHAR2(30) NOT NULL,
    customer_id       NUMBER       NOT NULL,
    order_date        DATE         NOT NULL,
    status            VARCHAR2(20)  NOT NULL,
    sales_channel     VARCHAR2(20)  NOT NULL,
    CONSTRAINT pk_orders PRIMARY KEY (order_id),
    CONSTRAINT uq_orders_number UNIQUE (order_number),
    CONSTRAINT fk_orders_customer FOREIGN KEY (customer_id) REFERENCES customers(customer_id),
    CONSTRAINT ck_orders_status CHECK (status IN ('COMPLETED', 'PENDING', 'CANCELLED'))
);

CREATE TABLE IF NOT EXISTS order_items (
    order_item_id     NUMBER GENERATED ALWAYS AS IDENTITY,
    order_id          NUMBER       NOT NULL,
    product_id        NUMBER       NOT NULL,
    quantity          NUMBER(8)    NOT NULL,
    unit_price        NUMBER(12,2) NOT NULL,
    discount_percent  NUMBER(5,2) DEFAULT 0 NOT NULL,
    CONSTRAINT pk_order_items PRIMARY KEY (order_item_id),
    CONSTRAINT fk_items_order FOREIGN KEY (order_id) REFERENCES orders(order_id),
    CONSTRAINT fk_items_product FOREIGN KEY (product_id) REFERENCES products(product_id),
    CONSTRAINT ck_items_quantity CHECK (quantity > 0),
    CONSTRAINT ck_items_discount CHECK (discount_percent BETWEEN 0 AND 100)
);

CREATE TABLE IF NOT EXISTS subscriptions (
    subscription_id   NUMBER GENERATED ALWAYS AS IDENTITY,
    customer_id       NUMBER       NOT NULL,
    product_id        NUMBER       NOT NULL,
    started_on        DATE         NOT NULL,
    renewed_on        DATE,
    status            VARCHAR2(20)  NOT NULL,
    monthly_value     NUMBER(12,2) NOT NULL,
    CONSTRAINT pk_subscriptions PRIMARY KEY (subscription_id),
    CONSTRAINT fk_sub_customer FOREIGN KEY (customer_id) REFERENCES customers(customer_id),
    CONSTRAINT fk_sub_product FOREIGN KEY (product_id) REFERENCES products(product_id),
    CONSTRAINT ck_sub_status CHECK (status IN ('ACTIVE', 'PAUSED', 'CANCELLED'))
);

CREATE TABLE IF NOT EXISTS support_interactions (
    interaction_id   NUMBER GENERATED ALWAYS AS IDENTITY,
    customer_id      NUMBER       NOT NULL,
    interaction_date DATE         NOT NULL,
    channel          VARCHAR2(20) NOT NULL,
    reason           VARCHAR2(50) NOT NULL,
    outcome          VARCHAR2(30) NOT NULL,
    satisfaction     NUMBER(2),
    CONSTRAINT pk_support_interactions PRIMARY KEY (interaction_id),
    CONSTRAINT fk_support_customer FOREIGN KEY (customer_id) REFERENCES customers(customer_id),
    CONSTRAINT ck_support_satisfaction CHECK (satisfaction BETWEEN 1 AND 5)
);

CREATE TABLE IF NOT EXISTS customer_churn_scores (
    churn_score_id   NUMBER GENERATED ALWAYS AS IDENTITY,
    customer_id      NUMBER       NOT NULL,
    scored_on        DATE         NOT NULL,
    model_version    VARCHAR2(30) NOT NULL,
    churn_score      NUMBER(5,4)  NOT NULL,
    risk_band        VARCHAR2(20) NOT NULL,
    primary_driver   VARCHAR2(80) NOT NULL,
    CONSTRAINT pk_churn_scores PRIMARY KEY (churn_score_id),
    CONSTRAINT fk_score_customer FOREIGN KEY (customer_id) REFERENCES customers(customer_id),
    CONSTRAINT uq_score_customer_date UNIQUE (customer_id, scored_on),
    CONSTRAINT ck_churn_score CHECK (churn_score BETWEEN 0 AND 1),
    CONSTRAINT ck_risk_band CHECK (risk_band IN ('LOW', 'MEDIUM', 'HIGH'))
);

CREATE INDEX IF NOT EXISTS ix_orders_customer_date ON orders(customer_id, order_date);
CREATE INDEX IF NOT EXISTS ix_items_order ON order_items(order_id);
CREATE INDEX IF NOT EXISTS ix_support_customer_date ON support_interactions(customer_id, interaction_date);
CREATE INDEX IF NOT EXISTS ix_churn_scores_customer_date ON customer_churn_scores(customer_id, scored_on);

INSERT ALL
    INTO customers (customer_number, company_name, industry, customer_segment, region, country_code, account_manager) VALUES ('CUST-1001', 'Acme Health Systems', 'Healthcare', 'ENTERPRISE', 'West', 'US', 'Jordan Lee')
    INTO customers VALUES (DEFAULT, 'CUST-1002', 'Northwind Logistics', 'Logistics', 'MID_MARKET', 'Central', 'US', 'Priya Shah', SYSDATE)
    INTO customers VALUES (DEFAULT, 'CUST-1003', 'Bluebird Retail', 'Retail', 'SMB', 'East', 'US', 'Mateo Ruiz', SYSDATE)
    INTO customers VALUES (DEFAULT, 'CUST-1004', 'Contoso Manufacturing', 'Manufacturing', 'West', 'US', 'ENTERPRISE', 'Jordan Lee', SYSDATE)
    INTO customers VALUES (DEFAULT, 'CUST-1005', 'Greenfield Energy', 'Energy', 'Europe', 'DE', 'ENTERPRISE', 'Priya Shah', SYSDATE)
    INTO customers VALUES (DEFAULT, 'CUST-1006', 'Globex Media', 'Media', 'East', 'US', 'MID_MARKET', 'Mateo Ruiz', SYSDATE)
    INTO customers VALUES (DEFAULT, 'CUST-1007', 'Initech Software', 'Technology', 'West', 'US', 'MID_MARKET', 'Jordan Lee', SYSDATE)
    INTO customers VALUES (DEFAULT, 'CUST-1008', 'Starlight Education', 'Education', 'Europe', 'GB', 'SMB', 'Priya Shah', SYSDATE)
    INTO customers VALUES (DEFAULT, 'CUST-1009', 'Umbrella Labs', 'Healthcare', 'East', 'US', 'ENTERPRISE', 'Mateo Ruiz', SYSDATE)
    INTO customers VALUES (DEFAULT, 'CUST-1010', 'Wayne Enterprises', 'Financial Services', 'West', 'US', 'ENTERPRISE', 'Jordan Lee', SYSDATE)
SELECT 1 FROM dual;

INSERT ALL
    INTO products VALUES (DEFAULT, 'PLAT-CORE', 'Core Analytics Platform', 'Analytics', 'STANDARD', 1200, 420)
    INTO products VALUES (DEFAULT, 'PLAT-ADV', 'Advanced Analytics Platform', 'Analytics', 'PREMIUM', 2800, 850)
    INTO products VALUES (DEFAULT, 'AI-ASSIST', 'AI Analyst Assistant', 'Artificial Intelligence', 'PREMIUM', 950, 210)
    INTO products VALUES (DEFAULT, 'DATA-GOV', 'Data Governance Suite', 'Governance', 'STANDARD', 1600, 560)
    INTO products VALUES (DEFAULT, 'SECURE-EDGE', 'Secure Data Gateway', 'Security', 'STANDARD', 700, 250)
    INTO products VALUES (DEFAULT, 'CONSULT-10', 'Implementation Services', 'Services', 'SERVICES', 2400, 1200)
SELECT 1 FROM dual;

INSERT ALL
    INTO orders VALUES (DEFAULT, 'SO-24001', 1, TRUNC(SYSDATE, 'Q') + 2, 'COMPLETED', 'DIRECT')
    INTO orders VALUES (DEFAULT, 'SO-24002', 2, TRUNC(SYSDATE, 'Q') + 8, 'COMPLETED', 'PARTNER')
    INTO orders VALUES (DEFAULT, 'SO-24003', 3, TRUNC(SYSDATE, 'Q') + 15, 'COMPLETED', 'WEB')
    INTO orders VALUES (DEFAULT, 'SO-24004', 4, TRUNC(SYSDATE, 'Q') + 22, 'COMPLETED', 'DIRECT')
    INTO orders VALUES (DEFAULT, 'SO-24005', 5, TRUNC(SYSDATE, 'Q') + 30, 'COMPLETED', 'DIRECT')
    INTO orders VALUES (DEFAULT, 'SO-24006', 6, TRUNC(SYSDATE, 'Q') + 38, 'COMPLETED', 'PARTNER')
    INTO orders VALUES (DEFAULT, 'SO-24007', 7, TRUNC(SYSDATE, 'Q') + 46, 'COMPLETED', 'WEB')
    INTO orders VALUES (DEFAULT, 'SO-24008', 8, TRUNC(SYSDATE, 'Q') + 54, 'COMPLETED', 'WEB')
    INTO orders VALUES (DEFAULT, 'SO-24009', 9, TRUNC(SYSDATE, 'Q') + 62, 'COMPLETED', 'DIRECT')
    INTO orders VALUES (DEFAULT, 'SO-24010', 10, TRUNC(SYSDATE, 'Q') + 70, 'COMPLETED', 'DIRECT')
    INTO orders VALUES (DEFAULT, 'SO-24011', 1, ADD_MONTHS(TRUNC(SYSDATE, 'Q'), -3) + 20, 'COMPLETED', 'DIRECT')
    INTO orders VALUES (DEFAULT, 'SO-24012', 2, ADD_MONTHS(TRUNC(SYSDATE, 'Q'), -3) + 45, 'COMPLETED', 'PARTNER')
SELECT 1 FROM dual;

INSERT ALL
    INTO order_items VALUES (DEFAULT, 1, 2, 4, 2800, 10)
    INTO order_items VALUES (DEFAULT, 1, 3, 5, 950, 0)
    INTO order_items VALUES (DEFAULT, 2, 1, 8, 1200, 5)
    INTO order_items VALUES (DEFAULT, 2, 5, 5, 700, 0)
    INTO order_items VALUES (DEFAULT, 3, 1, 2, 1200, 0)
    INTO order_items VALUES (DEFAULT, 4, 2, 6, 2800, 15)
    INTO order_items VALUES (DEFAULT, 4, 4, 3, 1600, 0)
    INTO order_items VALUES (DEFAULT, 5, 3, 10, 950, 5)
    INTO order_items VALUES (DEFAULT, 6, 1, 3, 1200, 0)
    INTO order_items VALUES (DEFAULT, 7, 4, 2, 1600, 0)
    INTO order_items VALUES (DEFAULT, 8, 5, 4, 700, 0)
    INTO order_items VALUES (DEFAULT, 9, 2, 5, 2800, 10)
    INTO order_items VALUES (DEFAULT, 10, 6, 3, 2400, 0)
    INTO order_items VALUES (DEFAULT, 11, 2, 3, 2800, 0)
    INTO order_items VALUES (DEFAULT, 12, 1, 5, 1200, 0)
SELECT 1 FROM dual;

INSERT ALL
    INTO subscriptions VALUES (DEFAULT, 1, 2, ADD_MONTHS(TRUNC(SYSDATE), -18), ADD_MONTHS(TRUNC(SYSDATE), 0), 'ACTIVE', 2800)
    INTO subscriptions VALUES (DEFAULT, 2, 1, ADD_MONTHS(TRUNC(SYSDATE), -12), ADD_MONTHS(TRUNC(SYSDATE), 0), 'ACTIVE', 1200)
    INTO subscriptions VALUES (DEFAULT, 4, 4, ADD_MONTHS(TRUNC(SYSDATE), -24), ADD_MONTHS(TRUNC(SYSDATE), -1), 'ACTIVE', 1600)
    INTO subscriptions VALUES (DEFAULT, 5, 3, ADD_MONTHS(TRUNC(SYSDATE), -9), ADD_MONTHS(TRUNC(SYSDATE), -2), 'PAUSED', 950)
    INTO subscriptions VALUES (DEFAULT, 7, 1, ADD_MONTHS(TRUNC(SYSDATE), -15), ADD_MONTHS(TRUNC(SYSDATE), -4), 'CANCELLED', 1200)
    INTO subscriptions VALUES (DEFAULT, 9, 2, ADD_MONTHS(TRUNC(SYSDATE), -20), ADD_MONTHS(TRUNC(SYSDATE), 0), 'ACTIVE', 2800)
SELECT 1 FROM dual;

INSERT ALL
    INTO support_interactions VALUES (DEFAULT, 4, TRUNC(SYSDATE) - 12, 'PHONE', 'PRODUCT_ISSUE', 'ESCALATED', 2)
    INTO support_interactions VALUES (DEFAULT, 5, TRUNC(SYSDATE) - 18, 'EMAIL', 'BILLING', 'UNRESOLVED', 1)
    INTO support_interactions VALUES (DEFAULT, 7, TRUNC(SYSDATE) - 30, 'CHAT', 'PRODUCT_ISSUE', 'RESOLVED', 3)
    INTO support_interactions VALUES (DEFAULT, 9, TRUNC(SYSDATE) - 5, 'PHONE', 'FEATURE_REQUEST', 'RESOLVED', 4)
    INTO support_interactions VALUES (DEFAULT, 10, TRUNC(SYSDATE) - 8, 'EMAIL', 'BILLING', 'RESOLVED', 5)
SELECT 1 FROM dual;

INSERT ALL
    INTO customer_churn_scores VALUES (DEFAULT, 1, TRUNC(SYSDATE), 'churn-v3.2', 0.08, 'LOW', 'Strong product engagement')
    INTO customer_churn_scores VALUES (DEFAULT, 2, TRUNC(SYSDATE), 'churn-v3.2', 0.34, 'MEDIUM', 'Reduced usage')
    INTO customer_churn_scores VALUES (DEFAULT, 3, TRUNC(SYSDATE), 'churn-v3.2', 0.61, 'HIGH', 'Low purchase frequency')
    INTO customer_churn_scores VALUES (DEFAULT, 4, TRUNC(SYSDATE), 'churn-v3.2', 0.78, 'HIGH', 'Repeated unresolved issues')
    INTO customer_churn_scores VALUES (DEFAULT, 5, TRUNC(SYSDATE), 'churn-v3.2', 0.91, 'HIGH', 'Subscription paused')
    INTO customer_churn_scores VALUES (DEFAULT, 6, TRUNC(SYSDATE), 'churn-v3.2', 0.27, 'LOW', 'Stable renewal history')
    INTO customer_churn_scores VALUES (DEFAULT, 7, TRUNC(SYSDATE), 'churn-v3.2', 0.88, 'HIGH', 'Subscription cancelled')
    INTO customer_churn_scores VALUES (DEFAULT, 8, TRUNC(SYSDATE), 'churn-v3.2', 0.49, 'MEDIUM', 'Low engagement')
    INTO customer_churn_scores VALUES (DEFAULT, 9, TRUNC(SYSDATE), 'churn-v3.2', 0.19, 'LOW', 'Recent expansion')
    INTO customer_churn_scores VALUES (DEFAULT, 10, TRUNC(SYSDATE), 'churn-v3.2', 0.12, 'LOW', 'High satisfaction')
SELECT 1 FROM dual;

COMMIT;
