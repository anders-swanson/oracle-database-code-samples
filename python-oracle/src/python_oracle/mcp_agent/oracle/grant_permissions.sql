alter session set container = freepdb1;

create user testuser identified by testpwd quota unlimited on users;
grant connect, resource to testuser;

create table testuser.players (
    player_id     number generated always as identity primary key,
    username      varchar2(50) not null,
    signup_date   date default sysdate,
    country       varchar2(50)
);

create table testuser.games (
    game_id       number generated always as identity primary key,
    game_name     varchar2(100) not null,
    genre         varchar2(50),
    release_year  number(4)
);

create table testuser.game_sessions (
    session_id    number generated always as identity primary key,
    player_id     number not null,
    game_id       number not null,
    play_date     date default sysdate,
    score         number,
    duration_min  number,
    foreign key (player_id) references testuser.players(player_id),
    foreign key (game_id) references testuser.games(game_id)
);

begin
    for i in 1..50 loop
        insert into testuser.players (username, signup_date, country)
        values (
            'Player' || i,
            trunc(sysdate - dbms_random.value(1, 365 * 3)),
            case
                when mod(i, 5) = 0 then 'USA'
                when mod(i, 5) = 1 then 'Canada'
                when mod(i, 5) = 2 then 'UK'
                when mod(i, 5) = 3 then 'Germany'
                else 'Japan'
            end
        );
    end loop;
    commit;
end;
/

insert into testuser.games (game_name, genre, release_year) values ('Space Invaders', 'Arcade', 1978);
insert into testuser.games (game_name, genre, release_year) values ('Mystic Quest', 'RPG', 1995);
insert into testuser.games (game_name, genre, release_year) values ('Speed Racer', 'Racing', 2018);
insert into testuser.games (game_name, genre, release_year) values ('Battle Arena', 'Fighting', 2020);
insert into testuser.games (game_name, genre, release_year) values ('Farm Frenzy', 'Simulation', 2017);
insert into testuser.games (game_name, genre, release_year) values ('Puzzle Mania', 'Puzzle', 2015);
insert into testuser.games (game_name, genre, release_year) values ('Zombie Attack', 'Shooter', 2019);
insert into testuser.games (game_name, genre, release_year) values ('Kingdom Builder', 'Strategy', 2021);
insert into testuser.games (game_name, genre, release_year) values ('Soccer Stars', 'Sports', 2022);
insert into testuser.games (game_name, genre, release_year) values ('Dragon Quest', 'RPG', 1989);
commit;

begin
    for i in 1..200 loop
        insert into testuser.game_sessions (player_id, game_id, play_date, score, duration_min)
        values (
            trunc(dbms_random.value(1, 51)),
            trunc(dbms_random.value(1, 11)),
            trunc(sysdate - dbms_random.value(1, 365)),
            trunc(dbms_random.value(0, 10001)),
            trunc(dbms_random.value(5, 181))
        );
    end loop;
    commit;
end;
/
