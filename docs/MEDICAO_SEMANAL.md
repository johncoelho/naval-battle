# Medição semanal do público e da receita (#94)

Consulta de **só leitura** que mostra, uma vez por semana, quanto público e receita o Naval Battle tem,
para os gatilhos do plano [#86](https://github.com/johncoelho/naval-battle/issues/86). O banco não
muda: nenhuma tabela, função ou flag nova.

## Como rodar

```bash
SUPABASE_PROJECT_ID=cwtslesnthbenxswdcbv supabase db query --linked -o csv -f docs/medicao-semanal.sql
```

**Neste checkout o `--linked` só funciona com `SUPABASE_PROJECT_ID=cwtslesnthbenxswdcbv`**: o repositório
não guarda `supabase/.temp/project-ref`, e sem a variável o comando não acha o projeto. (Alternativa antiga:
um diretório temporário fora do repositório com esse arquivo; ver o cabeçalho do `.sql`.) O conector do
Supabase também serve, mas já falhou em rodada; o CLI com a variável é o caminho de reserva.

Os arquivos têm só `select`. Se aparecer `insert`, `update`, `delete`, DDL ou `perform` de função que
grava, está errado: não rode.

## O que sai

Quatro linhas: escopo (`sem_interna`, jogadores de fora; `com_interna`, só para comparar) x semana
(`fechada`, a última segunda a domingo em America/Sao_Paulo; `anterior`, a de antes, para a variação).
Só agregados: **nunca** e-mail, uid, apelido nem lista de conta (repositório e issues são públicos).

| Medida | De onde vem | Aproximação |
|---|---|---|
| Perfis reais, novos na semana | `profiles` (sem `is_test` e sem contas internas) | exata |
| DAU / WAU / MAU | `profiles.last_seen_at` no momento da consulta | só guarda a última visita; pode superestimar |
| Retorno 1 e 7 dias | última visita >= criação + N dias | não há histórico de sessões; superestima |
| Partidas online finalizadas / ranqueadas | `online_matches` | contra a IA não é medida no servidor |
| Contas com push ativo | token com `check_status` diferente de `unregistered` | |
| Compras por `kind` (real, premio, test, ad) | `dobroes_purchases` | |
| Vales aplicados e pendentes | `ad_vales` | desfecho no aparelho em `docs/medicao-vales.sql` (#95) |
| Contas que gastaram dobrões | `dobroes_ledger` com `delta < 0` | |
| Receita estimada | compras `real` x preço de tabela (`dobroes_products.price_cents`), bruta e líquida (x 0,85, taxa de 15% da Play) | estimativa: não há valor cobrado gravado |

Contas internas (dono e QA) saem pelo predicado `is_internal_account` (#96); robôs `is_test` ficam fora. A
receita de anúncio não soma na receita estimada (não há tabela de receita de anúncio no banco).

## Gatilhos do #86 que a rotina confere

WAU >= 60 por 2 semanas seguidas (precisa da linha `anterior`); DAU >= 30 por 4 semanas; retorno em 7 dias
38% -> 45%; vales aplicados >= 80% dos iniciados (use `docs/medicao-vales.sql`); 2% dos ativos de 7 dias
comprando em 30 dias. Ainda sem gatilho escrito pelo John (sugestões do PO): receita em R$, alerta de vales
pendentes e queda de WAU.

## Rotina semanal

Tarefa agendada da esteira (skill `esteira`), domingo à noite (a semana fecha no domingo):

1. Rodar `docs/medicao-semanal.sql` e, depois da migração do #95, `docs/medicao-vales.sql`, com o comando acima.
2. Postar na #86 a tabela e a variação contra a semana anterior (só agregados) e a coluna `alertas`.
3. Se algum gatilho do #86 foi atingido, avisar o PO.
4. Se `alerta_real_nao_marcada` for maior que zero: conferir na view privada
   `dobroes_purchases_com_origem` se o comprador é do time (skill `beta-testers`).

**Estado:** a consulta e este roteiro estão no repositório; a tarefa agendada **ainda não foi criada** (aguarda
as decisões do PO para o John: contar a compra interna, bruta ou líquida, anúncio na receita, alertas
extras, quem avisa, dia e hora). Ligar a venda real e qualquer gasto continuam pedindo o OK do John.

## Retorno de verdade: `activity_days` (#97 bloco B, 0.44.4)

Depois da migração `behavior_activity_days`, a série exata de retorno sai de `docs/medicao-retorno.sql`
(views privadas `behavior_*`, só service_role): ativos por dia e por semana e retorno D1/D7/D30 por coorte
semanal de criação, com `completa_dN` (coorte inteira já passou pelo dia N; senão a taxa é parcial).

- **Quando a série começa:** em `app_config.activity_series_start` (o dia seguinte ao da aplicação), também
  na coluna `serie_atividade_desde` de `docs/medicao-semanal.sql`. Antes disso a tabela não existia, então as
  coortes anteriores continuam só com o retorno **aproximado** da consulta semanal (última visita) e o D30 de
  uma coorte nova leva 30 dias para fechar. Enquanto a série não tem coorte fechada, não comparar com os
  gatilhos de retorno do #86.
- **O que D1 mede:** "abriu o app com conta e com rede" (a partida contra a IA conta, porque o ponto sai
  como `in_match`; convidado e aparelho sem rede o dia todo não aparecem). Não é "jogou". Partidas contra bot
  (#105) entram à parte quando `bot_matches` existir.
- **Privacidade:** célula com menos de 5 contas vem nula (não reconstruir por subtração), exclusão de teste e
  internas na leitura, nunca postar linha por conta; retenção de 90 dias (`select public.purge_activity();`
  na rodada diária), então só coortes dos últimos 89 dias aparecem.
- **Guardar a semana fechada:** a retenção apaga o que passa de 90 dias, então salve o resultado da semana
  fechada (só agregados, na issue #86) antes de ela sair da janela; a semana de borda fica parcial.
- **Retorno nulo:** `retorno_dN` também vem nulo quando quem voltou ou quem não voltou é menor que 5; taxa
  nula com coorte preenchida = célula pequena.
