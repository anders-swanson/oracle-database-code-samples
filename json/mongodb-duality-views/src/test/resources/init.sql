WHENEVER SQLERROR EXIT SQL.SQLCODE;

ALTER SESSION SET CONTAINER = freepdb1;

CREATE USER projectuser IDENTIFIED BY projectpwd QUOTA UNLIMITED ON users;
GRANT create session, create table, create view, soda_app TO projectuser;

ALTER SESSION SET CURRENT_SCHEMA = projectuser;

CREATE TABLE projects (
    project_id NUMBER PRIMARY KEY,
    name VARCHAR2(100) NOT NULL,
    status VARCHAR2(30) NOT NULL,
    owner_name VARCHAR2(100) NOT NULL
);

CREATE TABLE project_tasks (
    task_id NUMBER PRIMARY KEY,
    project_id NUMBER NOT NULL,
    title VARCHAR2(200) NOT NULL,
    status VARCHAR2(30) NOT NULL,
    priority NUMBER NOT NULL,
    CONSTRAINT fk_project_tasks_project
        FOREIGN KEY (project_id) REFERENCES projects(project_id)
        ON DELETE CASCADE
);

CREATE OR REPLACE FORCE EDITIONABLE JSON RELATIONAL DUALITY VIEW projects_dv AS
projects @insert @update @delete {
    _id : project_id
    name
    status
    owner : owner_name
    tasks : project_tasks @insert @update @delete
        [ {
            _id : task_id
            title
            status
            priority
        } ]
}
/

EXIT;
