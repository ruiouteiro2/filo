create or replace function filo_diag3()
returns jsonb language sql security definer set search_path = public, extensions as $$
  select jsonb_build_object(
    'members', (select coalesce(jsonb_agg(jsonb_build_object(
        'who', display_name,
        'fcm', case when fcm_token is null then 'NULL' else left(fcm_token, 12) || '...' end,
        'couple', left(couple_id::text, 8))), '[]'::jsonb) from members),
    'recent_http', (select coalesce(jsonb_agg(jsonb_build_object(
        'status', status_code, 'body', left(coalesce(content,''),240)) order by id desc), '[]'::jsonb)
      from (select * from net._http_response order by id desc limit 4) t)
  );
$$;
grant execute on function filo_diag3() to authenticated;
