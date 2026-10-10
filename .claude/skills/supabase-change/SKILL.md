---
name: supabase-change
description: Como mudar o banco do Naval Battle (Supabase) com segurança — tabelas, RPCs, regras de pontuação/moeda/milhas, gatilhos de push, app_config. Use sempre que for criar ou alterar SQL, função, política RLS ou configuração no projeto Supabase.
---

# Mudança no Supabase

Projeto `cwtslesnthbenxswdcbv` ("Batalha Naval"). O banco é de produção: os jogadores reais usam o
mesmo.

## Regras

1. **O arquivo em `supabase/<domínio>.sql` é a fonte da verdade.** Mude o arquivo primeiro; aplique
   no banco uma migração com o mesmo conteúdo (`apply_migration`, nome em snake_case que descreva a
   mudança). Repositório e banco nunca divergem: se a aplicação for barrada, guarde o patch fora do
   repo e não commite o arquivo até aplicar.
2. Regra que vale ponto, moeda, milha ou recompensa roda no servidor, em RPC
   `security definer set search_path = public`; o app só pede. `revoke all … from public` e
   `grant execute … to authenticated` (ou `anon` quando for público de propósito).
3. RLS ligado em toda tabela nova; política mínima.
4. Mesma assinatura: `create or replace function` (sem janela sem a função). Mudou o retorno:
   `drop` + `create` — e avise, porque versões antigas do app chamam a função.
5. Número ajustável vai para `app_config` e é lido com `public.config_int('chave', padrão)`; o seed
   fica no `.sql` com `on conflict (key) do nothing`.
6. Datas de "dia" no fuso de Brasília: `public.brt_today()`.
7. Compatibilidade: a versão anterior do app continua no ar por dias (revisão da Play). Mudança de
   regra precisa funcionar com o app antigo, ou ser feita em duas etapas.

## Testar antes de anunciar

Monte o cenário e chame a RPC como cada usuário dentro de um bloco que **desfaz tudo** no fim:

```sql
do $$
declare a uuid; b uuid; mid uuid; r record; out text := '';
begin
  select id into a from auth.users where email = 'aigamesfactory.qa01@gmail.com';
  select id into b from auth.users where email = 'aigamesfactory.qa02@gmail.com';
  -- monte o cenário (insert …)
  perform set_config('request.jwt.claims', json_build_object('sub', a, 'role', 'authenticated')::text, true);
  select * into r from minha_rpc(...);
  out := out || format('resultado: %s | ', r);
  raise exception 'RESULTADO (rollback): %', out;
end $$;
```
O erro devolvido traz o resultado e nada fica gravado. Use as contas QA, nunca dados de jogador real.

## Depois

- Registre a mudança no CHANGELOG da versão e, se for regra de jogo, no README e no HISTORY.
- Mudou algo que o QA cobre: ajuste `docs/QA_TEST_PLAN.md`.
- Push: gatilhos gravam em `push_outbox`; a Edge Function `push` envia (FCM v1). Segredo
  `FCM_SERVICE_ACCOUNT` só nos secrets do Supabase.
