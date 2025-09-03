
-- create a table with a json column.
create table employees (
                           id   number generated always as identity primary key,
                           data json
);
-- insert a json object in the employees table.
insert into employees (data) values (
                                        json_object(
                                                'name' value 'alice johnson',
                                                'skills' value json_array('sql', 'git'),
                                                'contact' value json_object(
            'email' value 'alice.johnson@example.com',
            'phone' value '123-456-7890'
        )
                                        )
                                    );
-- Update JSON data on the fly with JSON_TRANSFORM:
update employees
set data = json_transform(
        data,
    -- add a 'position' field.
    set '$.position' = 'software engineer',
    -- add a new skill to the skills array.
        append '$.skills' = 'java',
    -- rename the name field to fullname.
        rename '$.name' = 'fullname'
           )
-- filter with JSON_EXISTS
where json_exists(data, '$.name?(@ == "alice johnson")');

select json_serialize(e.data pretty) as emp from employees e;
-- {
--  "position" : "Software Engineer",
--  "fullname" : "Alice Johnson",
--  ...
-- }
