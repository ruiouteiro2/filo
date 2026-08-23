-- The Edge Function runs as service_role and reads pings and members to work out who to
-- notify. Without these grants it gets "permission denied for table pings" and the heart
-- silently never arrives.
grant usage on schema public to service_role;
grant select, insert, update, delete on couples, members, countdowns, bucket_items, pings to service_role;
grant execute on function current_couple_id() to service_role;
