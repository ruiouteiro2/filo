-- Remove the couple used to verify nicknames, the emoji picker and pull to refresh.
do $$
declare
  test_couple uuid;
begin
  select id into test_couple from couples where invite_code = 'ETJ98Q'
    and id::text not like '096aa18f%';
  if test_couple is not null then
    delete from pings where couple_id = test_couple;
    delete from bucket_items where couple_id = test_couple;
    delete from countdowns where couple_id = test_couple;
    delete from members where couple_id = test_couple;
    delete from couples where id = test_couple;
  end if;
end $$;
