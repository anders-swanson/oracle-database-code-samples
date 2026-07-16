create table STUDENTS (
    id         varchar2(36) default sys_guid() primary key,
    first_name varchar2(50) not null,
    last_name  varchar2(50) not null,
    email      varchar2(100),
    major      varchar2(50) not null,
    credits    number(10),
    gpa        binary_double
);

create table LECTURE_HALLS (
    id         varchar2(36) default sys_guid() primary key,
    name       varchar2(50) not null
);

create table COURSES (
    id              varchar2(36) default sys_guid() primary key,
    lecture_hall_id varchar2(36) not null,
    name            varchar2(50) not null,
    description     varchar2(250) not null,
    credits         number check (credits between 0 and 10),
    constraint lecture_hall_fk foreign key (lecture_hall_id)
        references lecture_halls(id)
);

create table ENROLLMENTS (
    id         varchar2(36) default sys_guid() primary key,
    student_id varchar2(36) not null,
    course_id  varchar2(36) not null,
    constraint student_fk foreign key (student_id)
        references students(id),
    constraint course_fk foreign key (course_id)
        references courses(id)
);


-- sample students data
insert into students (first_name, last_name, email, major, credits, gpa)
values ('Alice', 'Smith', 'alice.smith@example.edu', 'Computer Science', 77, 3.86);
insert into lecture_halls (name)
values ('Hoffman Hall');
insert into courses (lecture_hall_id, name, description, credits)
values (
    (select id from lecture_halls where name = 'Hoffman Hall'),
    'Introduction to Computer Science',
    'A foundational course covering basic principles of computer science and programming.',
    4
);
insert into courses (lecture_hall_id, name, description, credits)
values (
    (select id from lecture_halls where name = 'Hoffman Hall'),
    'Data Structures and Algorithms',
    'An in-depth study of various data structures and fundamental algorithms in computer science.',
    5
);

insert into students (first_name, last_name, email, major, credits, gpa)
values ('Bob', 'Johnson', 'bob.johnson@example.edu', 'Mathematics', 64, 3.42);
insert into students (first_name, last_name, email, major, credits, gpa)
values ('Carol', 'Williams', 'carol.williams@example.edu', 'Physics', 91, 3.74);
insert into students (first_name, last_name, email, major, credits, gpa)
values ('David', 'Brown', 'david.brown@example.edu', 'Computer Science', 48, 3.18);

insert into lecture_halls (name)
values ('Science Center 101');
insert into lecture_halls (name)
values ('Engineering Building 204');

insert into courses (lecture_hall_id, name, description, credits)
values (
    (select id from lecture_halls where name = 'Science Center 101'),
    'Calculus II',
    'A continuation of differential and integral calculus with applications.',
    4
);
insert into courses (lecture_hall_id, name, description, credits)
values (
    (select id from lecture_halls where name = 'Science Center 101'),
    'Classical Mechanics',
    'An introduction to the laws of motion, energy, momentum, and rotational dynamics.',
    4
);
insert into courses (lecture_hall_id, name, description, credits)
values (
    (select id from lecture_halls where name = 'Engineering Building 204'),
    'Database Systems',
    'A practical introduction to relational databases, SQL, and data modeling.',
    3
);

insert into enrollments (student_id, course_id)
values (
    (select id from students where email = 'alice.smith@example.edu'),
    (select id from courses where name = 'Data Structures and Algorithms')
);
insert into enrollments (student_id, course_id)
values (
    (select id from students where email = 'bob.johnson@example.edu'),
    (select id from courses where name = 'Calculus II')
);
insert into enrollments (student_id, course_id)
values (
    (select id from students where email = 'carol.williams@example.edu'),
    (select id from courses where name = 'Classical Mechanics')
);
insert into enrollments (student_id, course_id)
values (
    (select id from students where email = 'david.brown@example.edu'),
    (select id from courses where name = 'Database Systems')
);
