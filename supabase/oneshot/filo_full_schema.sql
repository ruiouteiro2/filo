-- Filo: complete schema for a fresh Supabase project.
-- Paste this whole file into the SQL editor and run it once.
-- Safe to re-run: every statement is guarded.

-- ============================================================
-- 20260823120000_filo_schema.sql
-- ============================================================
-- Filo schema. One couple, two members, everything scoped by couple_id.
-- Row Level Security is on for every table without exception.

create schema if not exists extensions;
do $$
begin
  create extension if not exists pg_net with schema extensions;
exception when others then
  raise notice 'pg_net not enabled: the heart will record but will not notify until it is';
end $$;

-- ---------------------------------------------------------------- tables

create table if not exists couples (
  id            uuid primary key default gen_random_uuid(),
  invite_code   text unique not null,      -- 6 chars, uppercase, no ambiguous glyphs
  since_date    date,                      -- "together since"
  created_at    timestamptz default now()
);

create table if not exists members (
  id                  uuid primary key references auth.users(id) on delete cascade,
  couple_id           uuid references couples(id) on delete cascade,
  display_name        text not null,
  locale              text not null default 'en',   -- 'en' | 'it'
  timezone            text not null default 'UTC',  -- IANA, e.g. 'Europe/Rome'
  photo_url           text,                         -- avatar
  lat                 double precision,
  lon                 double precision,
  location_updated_at timestamptz,
  city                text,                         -- reverse geocoded once, then cached
  battery_level       int,
  battery_charging    boolean,
  battery_updated_at  timestamptz,
  sleep_start         time,                         -- local to their timezone
  sleep_end           time,
  mood_emoji          text,
  mood_text           text,
  mood_updated_at     timestamptz,
  note_text           text,                         -- "note of the day", max 140 chars
  note_updated_at     timestamptz,
  daily_photo_url     text,
  daily_photo_at      timestamptz,
  fcm_token           text,
  last_seen_at        timestamptz
);

create table if not exists countdowns (
  id         uuid primary key default gen_random_uuid(),
  couple_id  uuid references couples(id) on delete cascade,
  label_en   text not null,
  label_it   text not null,
  date       date not null,
  emoji      text,
  is_primary boolean default false,   -- exactly one is the "next visit"
  created_at timestamptz default now()
);

create table if not exists bucket_items (
  id         uuid primary key default gen_random_uuid(),
  couple_id  uuid references couples(id) on delete cascade,
  text       text not null,
  done       boolean default false,
  done_at    timestamptz,
  created_by uuid references members(id),
  created_at timestamptz default now()
);

create table if not exists pings (
  id          uuid primary key default gen_random_uuid(),
  couple_id   uuid references couples(id) on delete cascade,
  from_member uuid references members(id),
  created_at  timestamptz default now()
);

create index if not exists members_couple_id_idx on members (couple_id);
create index if not exists countdowns_couple_id_idx on countdowns (couple_id);
create index if not exists bucket_items_couple_id_idx on bucket_items (couple_id);
create index if not exists pings_couple_id_created_idx on pings (couple_id, created_at desc);

-- ---------------------------------------------------------------- helper

-- Marked stable so the planner calls it once per statement rather than per row.
create or replace function current_couple_id()
returns uuid
language sql
stable
security definer
set search_path = public
as $$
  select couple_id from members where id = auth.uid()
$$;

-- ---------------------------------------------------------------- RLS

alter table couples      enable row level security;
alter table members      enable row level security;
alter table countdowns   enable row level security;
alter table bucket_items enable row level security;
alter table pings        enable row level security;

-- couples: readable and updatable only by its own members. Inserts go through
-- create_couple() rather than straight from the client.
drop policy if exists couples_select on couples;
create policy couples_select on couples
  for select to authenticated
  using (id = current_couple_id());

drop policy if exists couples_update on couples;
create policy couples_update on couples
  for update to authenticated
  using (id = current_couple_id())
  with check (id = current_couple_id());

-- members: both people are visible to each other, but you may only edit yourself.
drop policy if exists members_select on members;
create policy members_select on members
  for select to authenticated
  using (couple_id = current_couple_id() or id = auth.uid());

drop policy if exists members_insert on members;
create policy members_insert on members
  for insert to authenticated
  with check (id = auth.uid());

drop policy if exists members_update on members;
create policy members_update on members
  for update to authenticated
  using (id = auth.uid())
  with check (id = auth.uid());

-- Everything else: one policy shape, couple scoped.
drop policy if exists countdowns_all on countdowns;
create policy countdowns_all on countdowns
  for all to authenticated
  using (couple_id = current_couple_id())
  with check (couple_id = current_couple_id());

drop policy if exists bucket_items_all on bucket_items;
create policy bucket_items_all on bucket_items
  for all to authenticated
  using (couple_id = current_couple_id())
  with check (couple_id = current_couple_id());

drop policy if exists pings_select on pings;
create policy pings_select on pings
  for select to authenticated
  using (couple_id = current_couple_id());

drop policy if exists pings_insert on pings;
create policy pings_insert on pings
  for insert to authenticated
  with check (couple_id = current_couple_id() and from_member = auth.uid());

-- ---------------------------------------------------------------- pairing

-- Alphabet with no I, O, 0 or 1, so a code read out over WhatsApp is unambiguous.
create or replace function generate_invite_code()
returns text
language plpgsql
volatile
as $$
declare
  alphabet text := 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';
  result text := '';
  i int;
begin
  for i in 1..6 loop
    result := result || substr(alphabet, 1 + floor(random() * length(alphabet))::int, 1);
  end loop;
  return result;
end;
$$;

-- Creating a couple and joining one both need to touch rows the caller cannot yet see,
-- so they are security definer functions rather than direct table writes.
create or replace function create_couple(
  p_display_name text,
  p_locale text,
  p_timezone text
)
returns table (couple_id uuid, invite_code text)
language plpgsql
volatile
security definer
set search_path = public
as $$
declare
  v_code text;
  v_couple_id uuid;
  v_attempts int := 0;
begin
  if auth.uid() is null then
    raise exception 'not authenticated' using errcode = 'P0001';
  end if;

  loop
    v_code := generate_invite_code();
    exit when not exists (select 1 from couples c where c.invite_code = v_code);
    v_attempts := v_attempts + 1;
    if v_attempts > 20 then
      raise exception 'could not allocate an invite code' using errcode = 'P0001';
    end if;
  end loop;

  insert into couples (invite_code) values (v_code) returning id into v_couple_id;

  insert into members (id, couple_id, display_name, locale, timezone, last_seen_at)
  values (auth.uid(), v_couple_id, p_display_name, p_locale, p_timezone, now())
  on conflict (id) do update
    set couple_id = excluded.couple_id,
        display_name = excluded.display_name,
        locale = excluded.locale,
        timezone = excluded.timezone,
        last_seen_at = now();

  return query select v_couple_id, v_code;
end;
$$;

create or replace function join_couple(
  p_code text,
  p_display_name text,
  p_locale text,
  p_timezone text
)
returns table (couple_id uuid, invite_code text)
language plpgsql
volatile
security definer
set search_path = public
as $$
declare
  v_couple_id uuid;
  v_code text;
  v_count int;
begin
  if auth.uid() is null then
    raise exception 'not authenticated' using errcode = 'P0001';
  end if;

  v_code := upper(regexp_replace(coalesce(p_code, ''), '[^A-Za-z0-9]', '', 'g'));

  select c.id into v_couple_id from couples c where c.invite_code = v_code;
  if v_couple_id is null then
    raise exception 'no_such_code' using errcode = 'P0002';
  end if;

  -- A couple is full at two members. Someone already in it may rejoin freely.
  select count(*) into v_count from members m
   where m.couple_id = v_couple_id and m.id <> auth.uid();
  if v_count >= 2 then
    raise exception 'couple_full' using errcode = 'P0003';
  end if;

  insert into members (id, couple_id, display_name, locale, timezone, last_seen_at)
  values (auth.uid(), v_couple_id, p_display_name, p_locale, p_timezone, now())
  on conflict (id) do update
    set couple_id = excluded.couple_id,
        display_name = excluded.display_name,
        locale = excluded.locale,
        timezone = excluded.timezone,
        last_seen_at = now();

  return query select v_couple_id, v_code;
end;
$$;

-- Belt and braces against a third member arriving by any other route.
create or replace function enforce_two_members()
returns trigger
language plpgsql
as $$
declare
  v_count int;
begin
  if new.couple_id is null then
    return new;
  end if;
  select count(*) into v_count from members
   where couple_id = new.couple_id and id <> new.id;
  if v_count >= 2 then
    raise exception 'couple_full' using errcode = 'P0003';
  end if;
  return new;
end;
$$;

drop trigger if exists members_two_max on members;
create trigger members_two_max
  before insert or update of couple_id on members
  for each row execute function enforce_two_members();

-- ---------------------------------------------------------------- push trigger

-- Config the Edge Function trigger needs. Not readable by clients.
create schema if not exists private;
revoke all on schema private from anon, authenticated;

create table if not exists private.config (
  key   text primary key,
  value text not null
);
alter table private.config enable row level security;  -- no policies: nobody but the server

create or replace function notify_ping()
returns trigger
language plpgsql
security definer
set search_path = public, extensions
as $$
declare
  v_url text;
  v_key text;
begin
  select value into v_url from private.config where key = 'functions_url';
  select value into v_key from private.config where key = 'service_role_key';

  -- If push has not been configured yet the ping still records; it just does not notify.
  if v_url is null or v_key is null then
    return new;
  end if;

  perform net.http_post(
    url := v_url || '/ping-notify',
    headers := jsonb_build_object(
      'Content-Type', 'application/json',
      'Authorization', 'Bearer ' || v_key
    ),
    body := jsonb_build_object('ping_id', new.id::text)
  );
  return new;
end;
$$;

drop trigger if exists pings_notify on pings;
create trigger pings_notify
  after insert on pings
  for each row execute function notify_ping();

-- ---------------------------------------------------------------- realtime

do $$ begin alter publication supabase_realtime add table members; exception when duplicate_object then null; end $$;
do $$ begin alter publication supabase_realtime add table countdowns; exception when duplicate_object then null; end $$;
do $$ begin alter publication supabase_realtime add table bucket_items; exception when duplicate_object then null; end $$;

-- Realtime only ships the primary key on updates unless the table replicates full rows.
alter table members replica identity full;
alter table countdowns replica identity full;
alter table bucket_items replica identity full;

-- ---------------------------------------------------------------- storage

-- Private bucket. Getting RLS right on tables and leaving the bucket public is the
-- classic mistake, so this one is locked and read through signed URLs.
insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values ('couple-photos', 'couple-photos', false, 5242880, array['image/jpeg', 'image/png', 'image/webp'])
on conflict (id) do nothing;

-- Objects live under <couple_id>/..., so the folder name is the tenant check.
drop policy if exists couple_photos_select on storage.objects;
create policy couple_photos_select on storage.objects
  for select to authenticated
  using (
    bucket_id = 'couple-photos'
    and (storage.foldername(name))[1] = current_couple_id()::text
  );

drop policy if exists couple_photos_insert on storage.objects;
create policy couple_photos_insert on storage.objects
  for insert to authenticated
  with check (
    bucket_id = 'couple-photos'
    and (storage.foldername(name))[1] = current_couple_id()::text
  );

drop policy if exists couple_photos_update on storage.objects;
create policy couple_photos_update on storage.objects
  for update to authenticated
  using (
    bucket_id = 'couple-photos'
    and (storage.foldername(name))[1] = current_couple_id()::text
  );

drop policy if exists couple_photos_delete on storage.objects;
create policy couple_photos_delete on storage.objects
  for delete to authenticated
  using (
    bucket_id = 'couple-photos'
    and (storage.foldername(name))[1] = current_couple_id()::text
  );

-- ---------------------------------------------------------------- grants

grant usage on schema public to anon, authenticated;
grant select, insert, update, delete on couples, members, countdowns, bucket_items, pings to authenticated;
grant execute on function create_couple(text, text, text) to authenticated;
grant execute on function join_couple(text, text, text, text) to authenticated;
grant execute on function current_couple_id() to authenticated;

-- ============================================================
-- 20260823140000_service_role_grants.sql
-- ============================================================
-- The Edge Function runs as service_role and reads pings and members to work out who to
-- notify. Without these grants it gets "permission denied for table pings" and the heart
-- silently never arrives.
grant usage on schema public to service_role;
grant select, insert, update, delete on couples, members, countdowns, bucket_items, pings to service_role;
grant execute on function current_couple_id() to service_role;

-- ============================================================
-- 20260823160000_spotify_and_live_location.sql
-- ============================================================
-- What each person is listening to right now, and how fresh their position is.
--
-- The track is written by the phone that is playing it, from its own Spotify token, and read
-- by the partner through the existing members RLS policy. No Spotify credential ever leaves
-- the phone that owns it.

alter table members
  add column if not exists spotify_track_id    text,
  add column if not exists spotify_track_name  text,
  add column if not exists spotify_artist      text,
  add column if not exists spotify_art_url     text,
  add column if not exists spotify_is_playing  boolean,
  add column if not exists spotify_updated_at  timestamptz;

-- Always-on tracking writes far more often than the old foreground-only sync, so the
-- accuracy of each fix is worth keeping: it is the difference between naming a town and
-- naming a street.
alter table members
  add column if not exists location_accuracy_m double precision,
  add column if not exists location_is_live    boolean default false;

comment on column members.spotify_track_id is
  'Spotify track id, so the partner can deep link straight to it.';
comment on column members.location_is_live is
  'True while that phone has background tracking switched on and reporting.';

-- ============================================================
-- 20260823180000_ping_trigger_no_stored_key.sql
-- ============================================================
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


-- ============================================================
-- Point the ping trigger at your deployed Edge Function.
-- ============================================================
insert into private.config (key, value)
values ('functions_url', 'https://lrmyvjzvsamqabofkmzz.supabase.co/functions/v1')
on conflict (key) do update set value = excluded.value;
