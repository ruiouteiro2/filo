-- A ping can carry words now. Empty means the plain "thinking of you", which is what every
-- ping sent before this migration was, so nothing needs backfilling.
alter table pings add column if not exists message text;

-- Keep it short enough to read on a lock screen, and never empty-but-not-null.
alter table pings drop constraint if exists pings_message_length;
alter table pings add constraint pings_message_length
  check (message is null or (length(message) between 1 and 140));
