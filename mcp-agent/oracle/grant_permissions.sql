-- Set as appropriate for your database.
alter session set container = freepdb1;

create user testuser identified by testpwd;
grant create session to testuser;
grant unlimited tablespace to testuser;
grant connect, resource to testuser;

-- schema for a games database
CREATE TABLE testuser.players (
    player_id     NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    username      VARCHAR2(50) NOT NULL,
    signup_date   DATE DEFAULT SYSDATE,
    country       VARCHAR2(50)
);

CREATE TABLE testuser.games (
    game_id       NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    game_name     VARCHAR2(100) NOT NULL,
    genre         VARCHAR2(50),
    release_year  NUMBER(4)
);

CREATE TABLE testuser.game_sessions (
    session_id    NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    player_id     NUMBER NOT NULL,
    game_id       NUMBER NOT NULL,
    play_date     DATE DEFAULT SYSDATE,
    score         NUMBER,
    duration_min  NUMBER,
    FOREIGN KEY (player_id) REFERENCES testuser.players(player_id),
    FOREIGN KEY (game_id)   REFERENCES testuser.games(game_id)
);

-- Create games database records
BEGIN
FOR i IN 1..50 LOOP
        INSERT INTO testuser.players (username, signup_date, country)
        VALUES (
            'Player' || i,
            TRUNC(SYSDATE - DBMS_RANDOM.VALUE(1, 365*3)), -- random signup in last 3 years
            CASE
                WHEN MOD(i,5)=0 THEN 'USA'
                WHEN MOD(i,5)=1 THEN 'Canada'
                WHEN MOD(i,5)=2 THEN 'UK'
                WHEN MOD(i,5)=3 THEN 'Germany'
                ELSE 'Japan'
            END
        );
END LOOP;
COMMIT;
END;
/

INSERT INTO testuser.games (game_name, genre, release_year) VALUES ('Space Invaders', 'Arcade', 1978);
INSERT INTO testuser.games (game_name, genre, release_year) VALUES ('Mystic Quest', 'RPG', 1995);
INSERT INTO testuser.games (game_name, genre, release_year) VALUES ('Speed Racer', 'Racing', 2018);
INSERT INTO testuser.games (game_name, genre, release_year) VALUES ('Battle Arena', 'Fighting', 2020);
INSERT INTO testuser.games (game_name, genre, release_year) VALUES ('Farm Frenzy', 'Simulation', 2017);
INSERT INTO testuser.games (game_name, genre, release_year) VALUES ('Puzzle Mania', 'Puzzle', 2015);
INSERT INTO testuser.games (game_name, genre, release_year) VALUES ('Zombie Attack', 'Shooter', 2019);
INSERT INTO testuser.games (game_name, genre, release_year) VALUES ('Kingdom Builder', 'Strategy', 2021);
INSERT INTO testuser.games (game_name, genre, release_year) VALUES ('Soccer Stars', 'Sports', 2022);
INSERT INTO testuser.games (game_name, genre, release_year) VALUES ('Dragon Quest', 'RPG', 1989);
COMMIT;

BEGIN
FOR i IN 1..200 LOOP
        INSERT INTO testuser.game_sessions (player_id, game_id, play_date, score, duration_min)
        VALUES (
            TRUNC(DBMS_RANDOM.VALUE(1,51)),  -- player_id 1-50
            TRUNC(DBMS_RANDOM.VALUE(1,11)),  -- game_id 1-10
            TRUNC(SYSDATE - DBMS_RANDOM.VALUE(1, 365)),  -- play in last year
            TRUNC(DBMS_RANDOM.VALUE(0,10001)), -- score 0-10000
            TRUNC(DBMS_RANDOM.VALUE(5,181))   -- duration 5-180 min
        );
END LOOP;
COMMIT;
END;
/
