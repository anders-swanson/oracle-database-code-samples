create table spring_property (
    key varchar2(255) primary key not null ,
    value varchar2(255) not null
);

insert into spring_property (key, value) values ('property1', 'initial value');
