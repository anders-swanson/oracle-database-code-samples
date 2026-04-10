create table if not exists json_documents (
    id number generated always as identity primary key,
    search_document json not null
);

create search index if not exists json_documents_search_idx
on json_documents (search_document)
for json
parameters ('sync (on commit) search_on text include ($.title, $.summary, $.body)');
