-- The name you call them. It lives on YOUR row because it is your choice, not theirs:
-- each of you can call the other whatever you like, and neither sees the other's choice.
alter table members add column if not exists partner_nickname text;

alter table members drop constraint if exists members_partner_nickname_length;
alter table members add constraint members_partner_nickname_length
  check (partner_nickname is null or (length(partner_nickname) between 1 and 30));

-- A nickname change repaints the other widgets too (it is your own phone's snapshot that
-- carries it, so this is really about your own app catching up on another device).
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
