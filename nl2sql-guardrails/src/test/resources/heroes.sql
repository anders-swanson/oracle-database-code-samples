-- schema for NL2SQL guardrail tests.
-- District and claim geometries use WGS 84 longitude/latitude coordinates (SRID 8307).

create table heroes (
    hero_id number primary key,
    hero_name varchar2(100) not null unique,
    civilian_name varchar2(100),
    primary_power varchar2(100) not null,
    headquarters_city varchar2(100) not null
);

create table villains (
    villain_id number primary key,
    villain_name varchar2(100) not null unique,
    alter_ego_name varchar2(100),
    alter_ego_status varchar2(20) not null,
    threat_level varchar2(20) not null,
    constraint villains_alter_ego_status_ck
        check (alter_ego_status in ('active', 'reformed', 'unknown')),
    constraint villains_threat_level_ck
        check (threat_level in ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL'))
);

create table city_districts (
    district_id number primary key,
    city_name varchar2(100) not null,
    district_name varchar2(100) not null,
    district_boundary mdsys.sdo_geometry not null,
    constraint city_districts_name_uk unique (city_name, district_name)
);

create table battles (
    battle_id number primary key,
    battle_name varchar2(200) not null,
    occurred_at timestamp not null,
    hero_id number not null references heroes(hero_id),
    villain_id number not null references villains(villain_id),
    battle_location mdsys.sdo_geometry not null,
    outcome varchar2(30) not null,
    constraint battles_outcome_ck check (outcome in ('HERO_VICTORY', 'VILLAIN_ESCAPED', 'DRAW'))
);

create table insurance_claims (
    claim_id number primary key,
    battle_id number not null references battles(battle_id),
    claimant_name varchar2(150) not null,
    property_type varchar2(50) not null,
    damage_description varchar2(1000) not null,
    damage_location mdsys.sdo_geometry not null,
    claimed_amount number(14, 2) not null,
    approved_amount number(14, 2) not null,
    claim_status varchar2(20) not null,
    filed_at date not null,
    constraint insurance_claims_amount_ck
        check (claimed_amount >= 0 and approved_amount >= 0 and approved_amount <= claimed_amount),
    constraint insurance_claims_status_ck
        check (claim_status in ('PENDING', 'APPROVED', 'DENIED', 'PAID'))
);

insert into user_sdo_geom_metadata (table_name, column_name, diminfo, srid)
values (
    'CITY_DISTRICTS',
    'DISTRICT_BOUNDARY',
    mdsys.sdo_dim_array(
        mdsys.sdo_dim_element('LONGITUDE', -180, 180, 0.005),
        mdsys.sdo_dim_element('LATITUDE', -90, 90, 0.005)
    ),
    8307
);

insert into user_sdo_geom_metadata (table_name, column_name, diminfo, srid)
values (
    'INSURANCE_CLAIMS',
    'DAMAGE_LOCATION',
    mdsys.sdo_dim_array(
        mdsys.sdo_dim_element('LONGITUDE', -180, 180, 0.005),
        mdsys.sdo_dim_element('LATITUDE', -90, 90, 0.005)
    ),
    8307
);

create index city_districts_boundary_idx
    on city_districts(district_boundary)
    indextype is mdsys.spatial_index_v2;

create index insurance_claims_location_idx
    on insurance_claims(damage_location)
    indextype is mdsys.spatial_index_v2;

insert into heroes values (1, 'Batman', 'Bruce Wayne', 'Human intelligence and tactical skill', 'Gotham');
insert into heroes values (2, 'Superman', 'Clark Kent', 'Kryptonian strength and flight', 'Metropolis');
insert into heroes values (3, 'Wonder Woman', 'Diana Prince', 'Amazonian strength and combat skill', 'Themyscira');

insert into villains values (1, 'The Riddler', 'Edward Nigma', 'active', 'HIGH');
insert into villains values (2, 'The Penguin', 'Oswald Cobblepot', 'reformed', 'MEDIUM');
insert into villains values (3, 'The Joker', 'Unknown', 'active', 'CRITICAL');
insert into villains values (4, 'Cat Burglar', 'Selina Kyle', 'reformed', 'LOW');
insert into villains values (5, 'Tidal Wave', 'Calder Voss', 'active', 'HIGH');

-- Gotham contains two adjacent districts; Star City makes the city filter meaningful.
insert into city_districts values (
    1, 'Gotham', 'Financial District',
    mdsys.sdo_geometry(2003, 8307, null, mdsys.sdo_elem_info_array(1, 1003, 1),
        mdsys.sdo_ordinate_array(-74.020, 40.700, -74.000, 40.700, -74.000, 40.720, -74.020, 40.720, -74.020, 40.700))
);
insert into city_districts values (
    2, 'Gotham', 'The Narrows',
    mdsys.sdo_geometry(2003, 8307, null, mdsys.sdo_elem_info_array(1, 1003, 1),
        mdsys.sdo_ordinate_array(-74.000, 40.700, -73.980, 40.700, -73.980, 40.720, -74.000, 40.720, -74.000, 40.700))
);
insert into city_districts values (
    3, 'Star City', 'Harborfront',
    mdsys.sdo_geometry(2003, 8307, null, mdsys.sdo_elem_info_array(1, 1003, 1),
        mdsys.sdo_ordinate_array(-122.430, 37.770, -122.410, 37.770, -122.410, 37.790, -122.430, 37.790, -122.430, 37.770))
);

insert into battles values (101, 'Museum Riddle Rampage', timestamp '2026-03-04 20:15:00', 1, 1,
    mdsys.sdo_geometry(2001, 8307, mdsys.sdo_point_type(-74.015, 40.705, null), null, null), 'HERO_VICTORY');
insert into battles values (102, 'Iceberg Lounge Evacuation', timestamp '2026-03-11 22:30:00', 2, 2,
    mdsys.sdo_geometry(2001, 8307, mdsys.sdo_point_type(-74.005, 40.710, null), null, null), 'HERO_VICTORY');
insert into battles values (103, 'Narrows Parade Panic', timestamp '2026-04-02 18:00:00', 1, 3,
    mdsys.sdo_geometry(2001, 8307, mdsys.sdo_point_type(-73.990, 40.708, null), null, null), 'VILLAIN_ESCAPED');
insert into battles values (104, 'Rooftop Recovery', timestamp '2026-04-16 02:00:00', 2, 4,
    mdsys.sdo_geometry(2001, 8307, mdsys.sdo_point_type(-73.985, 40.714, null), null, null), 'HERO_VICTORY');
insert into battles values (105, 'Harbor Surge', timestamp '2026-05-09 09:45:00', 3, 5,
    mdsys.sdo_geometry(2001, 8307, mdsys.sdo_point_type(-122.420, 37.780, null), null, null), 'DRAW');

insert into insurance_claims values (1001, 101, 'Gotham Museum', 'MUSEUM', 'Collapsed exhibition wall and damaged artifacts.',
    mdsys.sdo_geometry(2001, 8307, mdsys.sdo_point_type(-74.015, 40.705, null), null, null), 1250000, 1200000, 'PAID', date '2026-03-05');
insert into insurance_claims values (1002, 102, 'Iceberg Lounge', 'RESTAURANT', 'Broken windows and flood damage in the dining room.',
    mdsys.sdo_geometry(2001, 8307, mdsys.sdo_point_type(-74.005, 40.710, null), null, null), 425000, 400000, 'PAID', date '2026-03-12');
insert into insurance_claims values (1003, 103, 'Narrows Transit Authority', 'TRANSIT', 'Derailment damage to a station platform.',
    mdsys.sdo_geometry(2001, 8307, mdsys.sdo_point_type(-73.990, 40.708, null), null, null), 2100000, 0, 'PENDING', date '2026-04-03');
insert into insurance_claims values (1004, 103, 'Park Row Apartments', 'RESIDENTIAL', 'Fire damage to the lobby and three apartments.',
    mdsys.sdo_geometry(2001, 8307, mdsys.sdo_point_type(-73.988, 40.712, null), null, null), 680000, 0, 'PENDING', date '2026-04-04');
insert into insurance_claims values (1005, 104, 'Wayne Tower', 'COMMERCIAL', 'Damaged rooftop greenhouse panels.',
    mdsys.sdo_geometry(2001, 8307, mdsys.sdo_point_type(-73.985, 40.714, null), null, null), 175000, 160000, 'APPROVED', date '2026-04-17');
insert into insurance_claims values (1006, 105, 'Star City Port Authority', 'INDUSTRIAL', 'Damaged cranes and warehouse flooding.',
    mdsys.sdo_geometry(2001, 8307, mdsys.sdo_point_type(-122.420, 37.780, null), null, null), 900000, 0, 'PENDING', date '2026-05-10');

commit;
