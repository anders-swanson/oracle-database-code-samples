ALTER SESSION SET CONTAINER = CDB$ROOT;

WHENEVER SQLERROR CONTINUE;
ALTER PLUGGABLE DATABASE studentpdb CLOSE IMMEDIATE;
DROP PLUGGABLE DATABASE studentpdb INCLUDING DATAFILES;
ALTER PLUGGABLE DATABASE coursepdb CLOSE IMMEDIATE;
DROP PLUGGABLE DATABASE coursepdb INCLUDING DATAFILES;
WHENEVER SQLERROR EXIT SQL.SQLCODE;

CREATE PLUGGABLE DATABASE studentpdb
    ADMIN USER studentpdb_admin IDENTIFIED BY testpwd
    FILE_NAME_CONVERT = (
        '/opt/oracle/oradata/FREE/pdbseed/',
        '/opt/oracle/oradata/FREE/studentpdb/'
    );

CREATE PLUGGABLE DATABASE coursepdb
    ADMIN USER coursepdb_admin IDENTIFIED BY testpwd
    FILE_NAME_CONVERT = (
        '/opt/oracle/oradata/FREE/pdbseed/',
        '/opt/oracle/oradata/FREE/coursepdb/'
    );

ALTER PLUGGABLE DATABASE studentpdb OPEN;
ALTER PLUGGABLE DATABASE coursepdb OPEN;
ALTER PLUGGABLE DATABASE studentpdb SAVE STATE;
ALTER PLUGGABLE DATABASE coursepdb SAVE STATE;

ALTER SESSION SET CONTAINER = studentpdb;

CREATE USER students_app IDENTIFIED BY testpwd;
GRANT create session, create table, create sequence, create view, unlimited tablespace TO students_app;

CREATE TABLE students_app.students (
    id              NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    university_id   VARCHAR2(50) NOT NULL UNIQUE,
    first_name      VARCHAR2(100) NOT NULL,
    last_name       VARCHAR2(100) NOT NULL,
    status          VARCHAR2(50) NOT NULL
);

CREATE TABLE students_app.student_completed_courses (
    student_id   NUMBER NOT NULL,
    course_code  VARCHAR2(50) NOT NULL,
    CONSTRAINT fk_student_completed_courses_student
        FOREIGN KEY (student_id) REFERENCES students_app.students (id),
    CONSTRAINT uq_student_completed_courses
        UNIQUE (student_id, course_code)
);

ALTER SESSION SET CONTAINER = coursepdb;

CREATE USER courses_app IDENTIFIED BY testpwd;
GRANT create session, create table, create sequence, create view, unlimited tablespace TO courses_app;

CREATE TABLE courses_app.course_catalog (
    id            NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    course_code   VARCHAR2(50) NOT NULL UNIQUE,
    title         VARCHAR2(200) NOT NULL
);

CREATE TABLE courses_app.course_prerequisites (
    course_id             NUMBER NOT NULL,
    required_course_code  VARCHAR2(50) NOT NULL,
    CONSTRAINT fk_course_prerequisites_course
        FOREIGN KEY (course_id) REFERENCES courses_app.course_catalog (id),
    CONSTRAINT uq_course_prerequisites UNIQUE (course_id, required_course_code)
);

CREATE TABLE courses_app.course_offerings (
    course_id       NUMBER NOT NULL,
    term_code       VARCHAR2(50) NOT NULL,
    capacity        NUMBER(4) NOT NULL,
    enrolled_count  NUMBER(4) NOT NULL,
    CONSTRAINT fk_course_offerings_course
        FOREIGN KEY (course_id) REFERENCES courses_app.course_catalog (id),
    CONSTRAINT uq_course_offerings UNIQUE (course_id, term_code)
);

ALTER SESSION SET CONTAINER = CDB$ROOT;
