-- "Hours not set" was showing for everybody, because nothing ever set them.
--
-- A new member had null sleep_start / sleep_end, so the app had nothing to say about whether
-- they were awake and fell back to an unhelpful placeholder. Give everyone a sensible default
-- they can change in settings, rather than making the first thing they see be a shrug.

alter table members alter column sleep_start set default '23:30';
alter table members alter column sleep_end   set default '07:30';

update members set sleep_start = '23:30' where sleep_start is null;
update members set sleep_end   = '07:30' where sleep_end   is null;

-- The pairing functions insert explicit column lists, so give them the same defaults.
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

  insert into members (id, couple_id, display_name, locale, timezone, sleep_start, sleep_end, last_seen_at)
  values (auth.uid(), v_couple_id, p_display_name, p_locale, p_timezone, '23:30', '07:30', now())
  on conflict (id) do update
    set couple_id = excluded.couple_id,
        display_name = excluded.display_name,
        locale = excluded.locale,
        timezone = excluded.timezone,
        sleep_start = coalesce(members.sleep_start, excluded.sleep_start),
        sleep_end = coalesce(members.sleep_end, excluded.sleep_end),
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

  select count(*) into v_count from members m
   where m.couple_id = v_couple_id and m.id <> auth.uid();
  if v_count >= 2 then
    raise exception 'couple_full' using errcode = 'P0003';
  end if;

  insert into members (id, couple_id, display_name, locale, timezone, sleep_start, sleep_end, last_seen_at)
  values (auth.uid(), v_couple_id, p_display_name, p_locale, p_timezone, '23:30', '07:30', now())
  on conflict (id) do update
    set couple_id = excluded.couple_id,
        display_name = excluded.display_name,
        locale = excluded.locale,
        timezone = excluded.timezone,
        sleep_start = coalesce(members.sleep_start, excluded.sleep_start),
        sleep_end = coalesce(members.sleep_end, excluded.sleep_end),
        last_seen_at = now();

  return query select v_couple_id, v_code;
end;
$$;
