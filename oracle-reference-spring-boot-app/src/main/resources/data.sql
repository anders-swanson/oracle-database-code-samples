-- Entirely fictional lab data. Names, titles, stores and 13-digit identifiers are invented.
-- Coordinates are synthetic WGS84 test points, not actual business locations.
insert into authors (name, biography) values ('Demo Avery Quill', 'Fictional test author of Java programming and database applications.');
insert into authors (name, biography) values ('Demo Morgan Fable', 'Fictional test author of software architecture and design.');
insert into authors (name, biography) values ('Demo Sora Fern', 'Fictional test author of gardening stories for small spaces.');
insert into authors (name, biography) values ('Demo Jules Lumen', 'Fictional test author of reliable services and distributed systems.');

insert into publishers (name, country_code) values ('Demo Lanternleaf Press', 'US');
insert into publishers (name, country_code) values ('Demo Clockwork Books', 'GB');
insert into publishers (name, country_code) values ('Demo Mossgarden Press', 'ES');
insert into books (isbn, title, publisher_id)
select '0000000000001', 'Demo Java Foundations', id from publishers where name = 'Demo Lanternleaf Press';
insert into book_authors (book_id, author_id)
select b.id, a.id from books b cross join authors a
where b.isbn = '0000000000001' and a.name = 'Demo Avery Quill';
insert into book_embeddings (book_id, synopsis)
select id, 'An introduction to Java programming and objects.' from books where isbn = '0000000000001';

insert into books (isbn, title, publisher_id)
select '0000000000002', 'Demo Software Design', id from publishers where name = 'Demo Clockwork Books';
insert into book_authors (book_id, author_id)
select b.id, a.id from books b cross join authors a
where b.isbn = '0000000000002' and a.name = 'Demo Morgan Fable';
insert into book_embeddings (book_id, synopsis)
select id, 'Design patterns and maintainable software architecture.' from books where isbn = '0000000000002';

insert into books (isbn, title, publisher_id)
select '0000000000003', 'Demo Reliable Java Services', id from publishers where name = 'Demo Lanternleaf Press';
insert into book_authors (book_id, author_id)
select b.id, a.id from books b cross join authors a
where b.isbn = '0000000000003' and a.name = 'Demo Avery Quill';
insert into book_authors (book_id, author_id)
select b.id, a.id from books b cross join authors a
where b.isbn = '0000000000003' and a.name = 'Demo Jules Lumen';
insert into book_embeddings (book_id, synopsis)
select id, 'Build reliable Java services with database transactions and connection pooling.' from books where isbn = '0000000000003';

insert into books (isbn, title, publisher_id)
select '0000000000004', 'Demo Gardening for Beginners', id from publishers where name = 'Demo Mossgarden Press';
insert into book_authors (book_id, author_id)
select b.id, a.id from books b cross join authors a
where b.isbn = '0000000000004' and a.name = 'Demo Sora Fern';
insert into book_embeddings (book_id, synopsis)
select id, 'Growing flowers and vegetables in a home garden.' from books where isbn = '0000000000004';

insert into books (isbn, title, publisher_id)
select '0000000000005', 'Demo Java Concurrency Handbook', id from publishers where name = 'Demo Lanternleaf Press';
insert into book_authors (book_id, author_id)
select b.id, a.id from books b cross join authors a
where b.isbn = '0000000000005' and a.name = 'Demo Morgan Fable';
insert into book_embeddings (book_id, synopsis)
select id, 'Building concurrent Java applications with executors, threads and database transactions.' from books where isbn = '0000000000005';

insert into books (isbn, title, publisher_id)
select '0000000000006', 'Demo Distributed Java Systems', id from publishers where name = 'Demo Clockwork Books';
insert into book_authors (book_id, author_id)
select b.id, a.id from books b cross join authors a
where b.isbn = '0000000000006' and a.name = 'Demo Jules Lumen';
insert into book_embeddings (book_id, synopsis)
select id, 'Reliable distributed Java services with pooled database connections and transaction boundaries.' from books where isbn = '0000000000006';

insert into books (isbn, title, publisher_id)
select '0000000000007', 'Demo Transactional Java Applications', id from publishers where name = 'Demo Clockwork Books';
insert into book_authors (book_id, author_id)
select b.id, a.id from books b cross join authors a
where b.isbn = '0000000000007' and a.name = 'Demo Avery Quill';
insert into book_authors (book_id, author_id)
select b.id, a.id from books b cross join authors a
where b.isbn = '0000000000007' and a.name = 'Demo Morgan Fable';
insert into book_embeddings (book_id, synopsis)
select id, 'Practical database transactions and reliable Java application design.' from books where isbn = '0000000000007';

insert into books (isbn, title, publisher_id)
select '0000000000008', 'Demo Connection Pooling in Practice', id from publishers where name = 'Demo Lanternleaf Press';
insert into book_authors (book_id, author_id)
select b.id, a.id from books b cross join authors a
where b.isbn = '0000000000008' and a.name = 'Demo Jules Lumen';
insert into book_embeddings (book_id, synopsis)
select id, 'Configure database connection pools for dependable Java services.' from books where isbn = '0000000000008';

-- Curated recommendations include a two-hop path, a cycle, and a cross-topic alternative.
insert into book_relations (source_id, target_id)
select s.id, t.id from books s cross join books t
where s.isbn = '0000000000001' and t.isbn = '0000000000002';
insert into book_relations (source_id, target_id)
select s.id, t.id from books s cross join books t
where s.isbn = '0000000000002' and t.isbn = '0000000000003';
insert into book_relations (source_id, target_id)
select s.id, t.id from books s cross join books t
where s.isbn = '0000000000003' and t.isbn = '0000000000001';
insert into book_relations (source_id, target_id)
select s.id, t.id from books s cross join books t
where s.isbn = '0000000000001' and t.isbn = '0000000000004';
insert into book_relations (source_id, target_id)
select s.id, t.id from books s cross join books t
where s.isbn = '0000000000001' and t.isbn = '0000000000006';
insert into book_relations (source_id, target_id)
select s.id, t.id from books s cross join books t
where s.isbn = '0000000000001' and t.isbn = '0000000000007';
insert into book_relations (source_id, target_id)
select s.id, t.id from books s cross join books t
where s.isbn = '0000000000001' and t.isbn = '0000000000008';

insert into bookstores (name, location)
values ('Demo Willow Quay Books', mdsys.sdo_geometry(2001, 8307,
    mdsys.sdo_point_type(-30.0000, 35.0000, null), null, null));
insert into bookstores (name, location)
values ('Demo Cloudbank Books', mdsys.sdo_geometry(2001, 8307,
    mdsys.sdo_point_type(-30.0100, 35.0100, null), null, null));
insert into bookstores (name, location)
values ('Demo Far Harbor Books', mdsys.sdo_geometry(2001, 8307,
    mdsys.sdo_point_type(-60.0000, 10.0000, null), null, null));

-- One quantity per store/book is the only inventory source.
insert into store_stock (store_id, book_id, quantity)
select s.id, b.id, 4 from bookstores s cross join books b
where s.name = 'Demo Willow Quay Books' and b.isbn = '0000000000003';
insert into store_stock (store_id, book_id, quantity)
select s.id, b.id, 4 from bookstores s cross join books b
where s.name = 'Demo Willow Quay Books' and b.isbn = '0000000000004';
insert into store_stock (store_id, book_id, quantity)
select s.id, b.id, 4 from bookstores s cross join books b
where s.name = 'Demo Willow Quay Books' and b.isbn = '0000000000005';
insert into store_stock (store_id, book_id, quantity)
select s.id, b.id, 0 from bookstores s cross join books b
where s.name = 'Demo Willow Quay Books' and b.isbn = '0000000000008';
insert into store_stock (store_id, book_id, quantity)
select s.id, b.id, 8 from bookstores s cross join books b
where s.name = 'Demo Cloudbank Books' and b.isbn = '0000000000003';
insert into store_stock (store_id, book_id, quantity)
select s.id, b.id, 3 from bookstores s cross join books b
where s.name = 'Demo Far Harbor Books' and b.isbn = '0000000000006';
insert into store_stock (store_id, book_id, quantity)
select s.id, b.id, 0 from bookstores s cross join books b
where s.name = 'Demo Willow Quay Books' and b.isbn = '0000000000007';
