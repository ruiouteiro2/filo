-- One-off: remove the couples created while testing the release build and the self updater
-- on the emulator. Targeted at Tester rows only.
delete from couples where id in (
  select couple_id from members where display_name in ('Tester') and couple_id is not null
);
