create table cardholders (
    cardholder_id varchar2(40) primary key,
    display_name varchar2(120) not null,
    known_device_id varchar2(120) not null,
    normal_amount number(10,2) not null
);

create table cardholder_behavior_profiles (
    profile_id number generated always as identity primary key,
    cardholder_id varchar2(40) not null references cardholders(cardholder_id),
    profile_name varchar2(100) not null,
    embedding vector(8, float32) not null
);

create table card_transactions (
    transaction_id varchar2(40) primary key,
    cardholder_id varchar2(40) not null references cardholders(cardholder_id),
    occurred_at timestamp with time zone not null,
    amount number(10,2) not null,
    currency varchar2(3) not null,
    merchant_name varchar2(200) not null,
    merchant_category varchar2(40) not null,
    channel varchar2(40) not null,
    device_id varchar2(120) not null,
    raw_event json not null,
    location mdsys.sdo_geometry not null,
    behavior_vector vector(8, float32) not null
);

insert into user_sdo_geom_metadata (table_name, column_name, diminfo, srid)
values (
    'CARD_TRANSACTIONS',
    'LOCATION',
    mdsys.sdo_dim_array(
        mdsys.sdo_dim_element('LONGITUDE', -180, 180, 0.005),
        mdsys.sdo_dim_element('LATITUDE', -90, 90, 0.005)
    ),
    8307
);

create index card_transactions_location_idx
on card_transactions(location)
indextype is mdsys.spatial_index_v2;

create vector index cardholder_behavior_profile_idx
on cardholder_behavior_profiles(embedding)
organization neighbor partitions
distance cosine
with target accuracy 95
parameters (type ivf, neighbor partitions 4);

create table fraud_assessments (
    transaction_id varchar2(40) primary key references card_transactions(transaction_id),
    spatial_score number(5,2) not null,
    behavior_score number(5,2) not null,
    amount_score number(5,2) not null,
    velocity_score number(5,2) not null,
    total_score number(5,2) not null,
    decision varchar2(10) not null check (decision in ('APPROVE', 'REVIEW', 'DECLINE')),
    reason_codes varchar2(500) not null
);

create index card_transactions_recent_idx on card_transactions(cardholder_id, occurred_at);
