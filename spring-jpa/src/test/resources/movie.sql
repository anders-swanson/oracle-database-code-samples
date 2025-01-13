create table director (
    director_id   number generated always as identity primary key,
    first_name    varchar2(50) not null,
    last_name     varchar2(50) not null
);

-- A director has one or more movies. A movie has at most one director.
create table movie (
    movie_id      number generated always as identity primary key,
    title         varchar2(100) not null,
    release_year  number(4),
    genre         varchar2(50),
    director_id   number(10),
    constraint fk_movie_director foreign key (director_id) references director(director_id)
);

create table actor (
    actor_id    number generated always as identity primary key,
    first_name  varchar2(50) not null,
    last_name   varchar2(50) not null
);

-- A movie has zero or more actors, and an actor can be in many movies.
create table movie_actor (
    movie_id   number(10),
    actor_id   number(10),
    primary key (movie_id, actor_id),
    constraint fk_movie_actor_movie foreign key (movie_id) references movie(movie_id),
    constraint fk_movie_actor_actor foreign key (actor_id) references actor(actor_id)
);

-- Insert Directors
insert into director (first_name, last_name)
values ('Christopher', 'Nolan');

insert into director (first_name, last_name)
values ('Quentin', 'Tarantino');

-- Insert Movies
insert into movie (title, release_year, genre, director_id)
values ('Inception', 2010, 'Sci-Fi', 1);

insert into movie (title, release_year, genre, director_id)
values ('Pulp Fiction', 1994, 'Crime', 2);

insert into movie (title, release_year, genre, director_id)
values ('Django Unchained', 2012, 'Western', 2);

insert into movie (title, release_year, genre, director_id)
values ('Kill Bill: Vol. 1', 2003, 'Action', 2);

-- Insert Actors
insert into actor (first_name, last_name)
values ('Leonardo', 'DiCaprio');

insert into actor (first_name, last_name)
values ('Samuel', 'Jackson');

insert into actor (first_name, last_name)
values ('John', 'Travolta');

-- Insert Movie_Actor Records (Many-to-Many)
insert into movie_actor (movie_id, actor_id)
values (1, 1); -- Inception, Leonardo DiCaprio
insert into movie_actor (movie_id, actor_id)
values (2, 3); -- Pulp Fiction, John Travolta

insert into movie_actor (movie_id, actor_id)
values (2, 2); -- Pulp Fiction, Samuel Jackson

insert into movie_actor (movie_id, actor_id)
values (3, 2); -- Django Unchained, Samuel Jackson
