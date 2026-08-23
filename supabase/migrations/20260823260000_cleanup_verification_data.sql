-- The verification round is over: drop the throwaway diagnostics and the couples
-- created by the emulator during it. The real couple is untouched.
drop function if exists filo_diag4();
drop function if exists filo_diag5();

do $$
declare
  test_couple uuid;
begin
  for test_couple in
    select id from couples
    where id::text like 'a8633da8%' or id::text like '3aaed165%'
  loop
    delete from pings where couple_id = test_couple;
    delete from bucket_items where couple_id = test_couple;
    delete from countdowns where couple_id = test_couple;
    delete from members where couple_id = test_couple;
    delete from couples where id = test_couple;
  end loop;
end $$;
