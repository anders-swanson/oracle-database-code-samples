create table student (
    id         varchar2(36) default sys_guid() primary key,
    first_name varchar2(50) not null,
    last_name  varchar2(50) not null,
    email      varchar2(100),
    major      varchar2(20) not null,
    credits    number(10),
    gpa        number(3,2) check (gpa between 0.00 and 4.00)
);

insert into student (first_name, last_name, email, major, credits, gpa)
values ('Alice', 'Cooper', 'alice.cooper@example.com', 'Computer Science', 90, 3.8);

insert into student (first_name, last_name, email, major, credits, gpa)
values ('Jane', 'Smith', 'jane.smith@example.com', 'Biology', 75, 3.6);

insert into student (first_name, last_name, email, major, credits, gpa)
values ('Michael', 'Johnson', 'michael.j@example.com', 'Mathematics', 110, 3.9);

insert into student (first_name, last_name, email, major, credits, gpa)
values ('Emily', 'Davis', 'emily.davis@example.com', 'History', 45, 3.4);

insert into student (first_name, last_name, email, major, credits, gpa)
values ('William', 'Brown', 'william.brown@example.com', 'Physics', 120, 3.7);
