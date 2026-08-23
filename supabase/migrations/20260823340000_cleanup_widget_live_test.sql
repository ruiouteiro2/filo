-- Remove the couple used to verify the live-widget work (invite GPAWQL) and the earlier
-- one from the same session (BA2SYP). The real couple is untouched.
do $$
declare
  test_couple uuid;
begin
  for test_couple in
    select id from couples where invite_code in ('GPAWQL', 'BA2SYP')
      and id::text not like '096aa18f%'
  loop
    delete from pings where couple_id = test_couple;
    delete from bucket_items where couple_id = test_couple;
    delete from countdowns where couple_id = test_couple;
    delete from members where couple_id = test_couple;
    delete from couples where id = test_couple;
  end loop;
end $$;
