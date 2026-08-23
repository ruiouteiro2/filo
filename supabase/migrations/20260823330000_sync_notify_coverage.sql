-- The silent sync push covered music, mood, note and photos but not the other things a
-- widget renders: the sleep window it draws Awake/Asleep from, the timezone its clock ticks
-- in, the name it shows, and the countdown it counts. Those changes left the widgets stale
-- until the half-hour worker, while the app updated instantly.

drop trigger if exists members_sync_notify on members;
create trigger members_sync_notify
  after update on members
  for each row
  when (
    old.spotify_track_id   is distinct from new.spotify_track_id or
    old.spotify_is_playing is distinct from new.spotify_is_playing or
    old.spotify_track_name is distinct from new.spotify_track_name or
    old.mood_emoji         is distinct from new.mood_emoji or
    old.mood_text          is distinct from new.mood_text or
    old.note_text          is distinct from new.note_text or
    old.photo_url          is distinct from new.photo_url or
    old.daily_photo_url    is distinct from new.daily_photo_url or
    old.display_name       is distinct from new.display_name or
    old.timezone           is distinct from new.timezone or
    old.sleep_start        is distinct from new.sleep_start or
    old.sleep_end          is distinct from new.sleep_end
  )
  execute function notify_member_sync();

-- Countdowns belong to the couple rather than to a member, so this variant tells everyone
-- in the couple; sync-notify already works out who the recipient is from a member id.
create or replace function notify_countdown_sync()
returns trigger
language plpgsql
security definer
set search_path = public, extensions
as $$
declare
  v_url text;
  v_key text;
  v_headers jsonb;
  v_couple uuid;
  v_member uuid;
begin
  select value into v_url from private.config where key = 'functions_url';
  if v_url is null then
    return coalesce(new, old);
  end if;

  select value into v_key from private.config where key = 'service_role_key';
  v_headers := jsonb_build_object('Content-Type', 'application/json');
  if v_key is not null then
    v_headers := v_headers || jsonb_build_object('Authorization', 'Bearer ' || v_key);
  end if;

  v_couple := coalesce(new.couple_id, old.couple_id);
  -- One message per member: each one's partner is the other, so both phones get told.
  for v_member in select id from members where couple_id = v_couple loop
    perform net.http_post(
      url := v_url || '/sync-notify',
      headers := v_headers,
      body := jsonb_build_object('member_id', v_member::text)
    );
  end loop;
  return coalesce(new, old);
end;
$$;

drop trigger if exists countdowns_sync_notify on countdowns;
create trigger countdowns_sync_notify
  after insert or update or delete on countdowns
  for each row
  execute function notify_countdown_sync();
