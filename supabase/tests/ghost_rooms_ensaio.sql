-- Ensaio que desfaz tudo (#113, 0.44.7): sala fantasma da partida rápida (supabase/ghost_rooms.sql).
-- Rode no SQL Editor do projeto cwtslesnthbenxswdcbv ANTES de aplicar a migração `ghost_rooms`: cole o texto inteiro de
-- ghost_rooms.sql e este bloco na MESMA execução. O raise no fim cancela a transação inteira, DDL inclusive, e devolve o
-- resultado na mensagem de erro. Contas fictícias criadas no bloco; as salas `waiting` reais só são lidas por dentro do
-- bloco (o `select public.sweep_empty_rooms()` do arquivo fecha as velhas, e o rollback devolve tudo).
--
-- Resultado esperado:
--   velha_select=0 velha_join=0       sala rápida de 5 dias (anfitrião sem presença): invisível e recusada ao convidado
--   viva_select=1 viva_join=1         sala rápida de 1 min: visível e aceita
--   host_sumiu_select=0 join=0        sala de 1 min mas anfitrião visto há 5 min: morta
--   host_presente_select=1            sala de 1 min com presença de 5 s: viva
--   dono_ve_velha=1                   o dono ainda lê a própria sala velha
--   convite_velho_join=1              sala de convite (invited_id) velha continua entrando
--   lista_antiga_pega_a_velha=0       a leitura mais antiga primeiro do app antigo não devolve a sala morta
--   sweep: codigo_1d=waiting codigo_3d=abandoned velha=abandoned viva=active (a viva foi aceita acima)
do $$
declare
  a uuid := gen_random_uuid(); b uuid := gen_random_uuid(); c uuid := gen_random_uuid();
  velha uuid := gen_random_uuid(); viva uuid := gen_random_uuid(); sumiu uuid := gen_random_uuid();
  pres uuid := gen_random_uuid(); conv uuid := gen_random_uuid(); cod1 uuid := gen_random_uuid(); cod3 uuid := gen_random_uuid();
  n int; out text := ''; st text;
begin
  insert into auth.users (id, email) select u, 'ensaio-' || u || '@ensaio.invalid' from unnest(array[a, b, c]) u;
  update public.profiles set is_test = false where id in (a, b, c);
  -- fecha as salas reais para a contagem do ensaio ficar limpa (o rollback devolve)
  update public.online_matches set status = 'abandoned' where status = 'waiting';

  insert into public.online_matches (id, host_id, host_name, is_quick_match, status, created_at, host_seen_at)
  values (velha, a, 'Velha', true, 'waiting', now() - interval '5 days', null),
         (viva, a, 'Viva', true, 'waiting', now() - interval '1 minute', null),
         (sumiu, a, 'Sumiu', true, 'waiting', now() - interval '1 minute', now() - interval '5 minutes'),
         (pres, a, 'Presente', true, 'waiting', now() - interval '1 minute', now() - interval '5 seconds');
  insert into public.online_matches (id, host_id, host_name, is_quick_match, status, created_at, invited_id)
  values (conv, a, 'Convite', false, 'waiting', now() - interval '3 hours', b);
  insert into public.online_matches (id, host_id, host_name, is_quick_match, status, created_at)
  values (cod1, a, 'Cod1', false, 'waiting', now() - interval '1 day'),
         (cod3, a, 'Cod3', false, 'waiting', now() - interval '3 days');

  perform set_config('request.jwt.claims', json_build_object('sub', b, 'role', 'authenticated')::text, true);
  set local role authenticated;
  select count(*) into n from public.online_matches where id = velha;       out := out || format('velha_select=%s ', n);
  select count(*) into n from public.join_online_match(velha, 'B');         out := out || format('velha_join=%s | ', n);
  select count(*) into n from public.online_matches where id = viva;        out := out || format('viva_select=%s ', n);
  select count(*) into n from public.online_matches where id = sumiu;       out := out || format('host_sumiu_select=%s ', n);
  select count(*) into n from public.join_online_match(sumiu, 'B');         out := out || format('join=%s | ', n);
  select count(*) into n from public.online_matches where id = pres;        out := out || format('host_presente_select=%s | ', n);
  select count(*) into n from public.join_online_match(conv, 'B');          out := out || format('convite_velho_join=%s | ', n);
  select count(*) into n from public.join_online_match(viva, 'B');          out := out || format('viva_join=%s | ', n);
  reset role;

  perform set_config('request.jwt.claims', json_build_object('sub', a, 'role', 'authenticated')::text, true);
  set local role authenticated;
  select count(*) into n from public.online_matches where id = velha;       out := out || format('dono_ve_velha=%s | ', n);
  reset role;

  -- a busca por listagem direta do app antigo (mais antiga primeiro) nunca devolve a sala morta
  perform set_config('request.jwt.claims', json_build_object('sub', c, 'role', 'authenticated')::text, true);
  set local role authenticated;
  select count(*) into n from (select id from public.online_matches where status = 'waiting' and is_quick_match
                               order by created_at asc limit 1) q where id = velha;
  out := out || format('lista_antiga_pega_a_velha=%s | ', n);
  reset role;

  perform public.sweep_empty_rooms();
  select string_agg(r.l || '=' || r.s, ' ' order by r.l) into st from (
    select case id when velha then 'velha' when viva then 'viva' when cod1 then 'codigo_1d' when cod3 then 'codigo_3d' end l, status s
    from public.online_matches where id in (velha, viva, cod1, cod3)) r;
  out := out || 'sweep: ' || st;
  raise exception 'RESULTADO (rollback): %', out;
end $$;
