-- Remove the couple created while verifying the 1.2 -> 1.3 self-update (invite RC6ZXE).
-- The real couple (096aa18f...) is untouched.
do $$
declare
  test_couple uuid;
begin
  select id into test_couple from couples where invite_code = 'RC6ZXE'
    and id::text not like '096aa18f%';
  if test_couple is not null then
    delete from pings where couple_id = test_couple;
    delete from bucket_items where couple_id = test_couple;
    delete from countdowns where couple_id = test_couple;
    delete from members where couple_id = test_couple;
    delete from couples where id = test_couple;
  end if;
end $$;
