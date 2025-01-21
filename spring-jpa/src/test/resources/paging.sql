-- author table
create table author (
    author_id   number(10) generated always as identity primary key,
    name        varchar2(100) not null,
    birth_year  number(4),
    bio         varchar2(800)
);

-- book genre table
create table book_genre (
    genre_id    number(10) generated always as identity primary key,
    genre_name  varchar2(50) unique not null
);

-- book table
create table book (
    book_id         number(10) generated always as identity primary key,
    title           varchar2(200) not null,
    genre_id        number,
    pages           number(5),
    published_year  number(4),
    author_id       number,
    constraint fk_book_author foreign key (author_id) references author (author_id) on delete cascade,
    constraint fk_book_genre foreign key (genre_id) references book_genre (genre_id)
);

-- Insert sample genres
insert into book_genre (genre_name) values ('Fantasy');
insert into book_genre (genre_name) values ('Science Fiction');
insert into book_genre (genre_name) values ('Mystery');
insert into book_genre (genre_name) values ('Non-fiction');

-- Insert sample authors
insert into author (name, birth_year, bio)
values (
    'Douglas Adams',
    1952,
    'Douglas Adams was an English author, screenwriter, and satirist best known for "The Hitchhiker''s Guide to the Galaxy".'
);

insert into author (name, birth_year, bio)
values (
    'Terry Pratchett',
    1948,
    'Terry Pratchett was an English author famous for his satirical fantasy series "Discworld", which spans over 40 books.'
);

insert into author (name, birth_year, bio)
values (
    'J.R.R. Tolkien',
    1892,
    'English writer, poet, and author of The Lord of the Rings'
);

-- Insert sample books
insert into book (title, genre_id, pages, published_year, author_id)
values ('The Hitchhiker''s Guide to the Galaxy', 2, 224, 1979, 1);

insert into book (title, genre_id, pages, published_year, author_id)
values ('The Restaurant at the End of the Universe', 2, 208, 1980, 1);

insert into book (title, genre_id, pages, published_year, author_id)
values ('Life, the Universe and Everything', 2, 240, 1982, 1);

insert into book (title, genre_id, pages, published_year, author_id)
values ('So Long, and Thanks for All the Fish', 2, 191, 1984, 1);

insert into book (title, genre_id, pages, published_year, author_id)
values ('Mostly Harmless', 2, 230, 1992, 1);

insert into book (title, genre_id, pages, published_year, author_id)
values ('The Lord of the Rings', 1, 1178, 1954, 2);

insert into book (title, genre_id, pages, published_year, author_id)
values ('The Hobbit', 1, 310, 1937, 2);

insert into book (title, genre_id, pages, published_year, author_id)
values ('The Silmarillion', 1, 365, 1977, 2);

insert into book (title, genre_id, pages, published_year, author_id)
values ('Unfinished Tales', 1, 472, 1980, 2);

insert into book (title, genre_id, pages, published_year, author_id)
values ('The Colour of Magic', 3, 206, 1983, 3);

insert into book (title, genre_id, pages, published_year, author_id)
values ('The Light Fantastic', 3, 241, 1986, 3);

insert into book (title, genre_id, pages, published_year, author_id)
values ('Equal Rites', 3, 228, 1987, 3);

insert into book (title, genre_id, pages, published_year, author_id)
values ('Mort', 3, 272, 1987, 3);

insert into book (title, genre_id, pages, published_year, author_id)
values ('Sourcery', 3, 243, 1988, 3);

insert into book (title, genre_id, pages, published_year, author_id)
values ('Guards! Guards!', 3, 416, 1989, 3);

insert into book (title, genre_id, pages, published_year, author_id)
values ('Pyramids', 3, 385, 1989, 3);
