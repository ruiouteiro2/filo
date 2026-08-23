-- Widgets used to learn about the partner's music, mood, note and photos only when the
-- other phone happened to sync - up to half an hour late. Now any change to something a
-- widget shows posts one silent push to the partner, and their phone re-syncs in seconds.
--
-- Deliberately NOT on battery, presence, or location columns: those churn constantly and
-- would turn a courtesy tap on the shoulder into a firehose. The heartbeat that merely
-- touches spotify_updated_at does not fire this either.

create or replace function notify_member_sync()
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
  if v_url is null then
    return new;
  end if;

  select value into v_key from private.config where key = 'service_role_key';
  v_headers := jsonb_build_object('Content-Type', 'application/json');
  if v_key is not null then
    v_headers := v_headers || jsonb_build_object('Authorization', 'Bearer ' || v_key);
  end if;

  perform net.http_post(
    url := v_url || '/sync-notify',
    headers := v_headers,
    body := jsonb_build_object('member_id', new.id::text)
  );
  return new;
end;
$$;

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
    old.daily_photo_url    is distinct from new.daily_photo_url
  )
  execute function notify_member_sync();
