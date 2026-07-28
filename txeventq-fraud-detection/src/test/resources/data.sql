insert into cardholders (cardholder_id, display_name, known_device_id, normal_amount)
values ('alice', 'Alice Garcia', 'alice-phone', 60);

insert into cardholders (cardholder_id, display_name, known_device_id, normal_amount)
values ('bob', 'Bob Lee', 'bob-phone', 50);

insert into cardholder_behavior_profiles (cardholder_id, profile_name, embedding)
values ('alice', 'local grocery on known phone', vector('[0.12,1,0,0,1,1,1,1]', 8, float32));

insert into cardholder_behavior_profiles (cardholder_id, profile_name, embedding)
values ('bob', 'local dining on known phone', vector('[0.10,0,0,1,1,1,1,1]', 8, float32));
