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
