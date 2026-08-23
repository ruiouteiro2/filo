-- The widget-verification couple (4f27a172) goes away. Its single storage object stays
-- orphaned (storage rows cannot be deleted from SQL here); RLS makes it unreachable.
do $$
declare
  test_couple uuid;
begin
  select id into test_couple from couples where id::text like '4f27a172%';
  if test_couple is not null then
    delete from pings where couple_id = test_couple;
    delete from bucket_items where couple_id = test_couple;
    delete from countdowns where couple_id = test_couple;
    delete from members where couple_id = test_couple;
    delete from couples where id = test_couple;
  end if;
end $$;
