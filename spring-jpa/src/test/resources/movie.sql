create table director (
    director_id   number(10) generated always as identity primary key,
    first_name    varchar2(50) not null,
    last_name     varchar2(50) not null
);


-- A director has exactly one biography, and a biography is mapped to one director.
create table director_bio (
    director_id   number(10) primary key,
    biography     clob,
    constraint fk_director_bio foreign key (director_id) references director(director_id) on delete cascade
);

-- A director has one or more movies. A movie has at most one director.
create table movie (
    movie_id      number(10) generated always as identity primary key,
    title         varchar2(100) not null,
    release_year  number(4),
    genre         varchar2(50),
    director_id   number(10),
    constraint fk_movie_director foreign key (director_id) references director(director_id)
);

create table actor (
    actor_id    number(10) generated always as identity primary key,
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

create index idx_movie_director_id on movie(director_id);
create index idx_movie_actor_movie_id on movie_actor(movie_id);
create index idx_movie_actor_actor_id on movie_actor(actor_id);

-- Insert Directors
insert into director (first_name, last_name)
values ('Christopher', 'Nolan');

insert into director (first_name, last_name)
values ('Quentin', 'Tarantino');

-- Insert Biography for Christopher Nolan
insert into director_bio (director_id, biography)
values (1, 'Christopher Nolan is a British-American filmmaker known for his complex storytelling and visual style.');

-- Insert Biography for Quentin Tarantino
insert into director_bio (director_id, biography)
values (2, 'Quentin Tarantino is an American filmmaker celebrated for his unique narrative style and dialogue-driven stories.');


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
