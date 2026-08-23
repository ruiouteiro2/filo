-- Environment configuration, not schema.
--
-- The ping trigger needs to know where the Edge Function lives. The project URL is not a
-- secret (every request the app makes goes to it), but it is specific to this project, so it
-- lives in one clearly named place rather than being scattered.
--
-- If this schema is ever pointed at a different Supabase project, change this one row.

insert into private.config (key, value)
values ('functions_url', 'https://lrmyvjzvsamqabofkmzz.supabase.co/functions/v1')
on conflict (key) do update set value = excluded.value;

-- The service role key is deliberately NOT stored. The function is deployed with
-- --no-verify-jwt and validates the ping id it is handed, so a key that bypasses every RLS
-- policy has no reason to sit inside the database those policies protect.
delete from private.config where key = 'service_role_key';

-- Tidy up the couple created while verifying the project was reachable.
delete from couples where invite_code = '7WHZDC';
