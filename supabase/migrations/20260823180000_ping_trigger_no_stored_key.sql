-- Stop requiring the service role key to be stored in the database.
--
-- The ping trigger used to refuse to fire unless private.config held both the functions URL
-- and a service role key, and it sent that key as a bearer token. But the function is
-- deployed with --no-verify-jwt (it is only ever called by this trigger, and it validates the
-- ping id it is given), so the token buys nothing and keeping a key that bypasses every RLS
-- policy inside the database it protects is a bad trade.
--
-- The Authorization header is still sent when a key IS configured, so an existing setup keeps
-- working unchanged.

create or replace function notify_ping()
returns trigger
language plpgsql
security definer
set search_path = public, extensions
as $$
declare
  v_url text;
  v_key text;
  v_headers jsonb;
begin
  select value into v_url from private.config where key = 'functions_url';

  -- Not configured yet: the ping still records, it just does not notify.
  if v_url is null then
    return new;
  end if;

  select value into v_key from private.config where key = 'service_role_key';

  v_headers := jsonb_build_object('Content-Type', 'application/json');
  if v_key is not null then
    v_headers := v_headers || jsonb_build_object('Authorization', 'Bearer ' || v_key);
  end if;

  perform net.http_post(
    url := v_url || '/ping-notify',
    headers := v_headers,
    body := jsonb_build_object('ping_id', new.id::text)
  );
  return new;
end;
$$;
