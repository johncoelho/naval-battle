#!/usr/bin/env bash
# Rotina da medição semanal (#94, plano #86). SÓ LEITURA no banco: roda docs/medicao-semanal.sql e monta o
# comentário da semana (tabela, variação sobre a semana anterior, gatilhos). Não grava no banco, não muda
# flag e não precisa de dependência além de bash, awk, supabase CLI (logado) e gh.
#
# Uso (de qualquer pasta):
#   bash tools/medicao-semanal.sh                  # só imprime o comentário (padrão, não publica nada)
#   bash tools/medicao-semanal.sh --postar         # imprime e comenta na issue #86 (precisa do gh logado)
#   bash tools/medicao-semanal.sh --csv ARQ.csv    # usa um CSV já gerado em vez de consultar o banco (teste)
#   bash tools/medicao-semanal.sh --sem-anterior   # não lê o marcador da semana passada na #86 (teste)
#
# Só agregados: a consulta nunca devolve e-mail, uid nem apelido, e este script não imprime nada além dela.
#
# Marcador: a última linha do comentário é um comentário HTML com DAU/WAU/MAU e as sequências dos gatilhos.
# É a "semana anterior" para o que a consulta só sabe no momento (DAU/WAU/MAU, retorno) e para os gatilhos
# de sequência (WAU >= 60 por 2 semanas, DAU >= 30 por 4 semanas). Sem marcador anterior, a sequência vale 1.
#
# Armadilha (#94): `supabase db query --linked` lê o projeto de supabase/.temp/project-ref do diretório
# atual e o repositório não guarda esse arquivo; por isso o CLI roda numa pasta temporária com o project-ref.
set -u

REF=cwtslesnthbenxswdcbv
ISSUE=86
REPO=johncoelho/naval-battle
ROOT=$(cd "$(dirname "$0")/.." && pwd)
SQL="$ROOT/docs/medicao-semanal.sql"

POSTAR=0; CSV=""; LER_ANTERIOR=1
while [ $# -gt 0 ]; do
  case "$1" in
    --postar) POSTAR=1 ;;
    --csv) shift; CSV="${1:-}" ;;
    --sem-anterior) LER_ANTERIOR=0 ;;
    -h|--help) sed -n '2,15p' "$0"; exit 0 ;;
    *) echo "argumento desconhecido: $1" >&2; exit 2 ;;
  esac
  shift
done

WORK=$(mktemp -d)
trap 'rm -rf "$WORK"' EXIT

if [ -z "$CSV" ]; then
  mkdir -p "$WORK/supabase/.temp"
  printf '%s' "$REF" > "$WORK/supabase/.temp/project-ref"
  CSV="$WORK/saida.csv"
  if ! (cd "$WORK" && supabase db query --linked -o csv -f "$SQL") >"$CSV" 2>"$WORK/erro.txt"; then
    echo "ERRO: a consulta falhou (supabase CLI logado? rede?)" >&2
    grep -iv 'password\|token\|secret' "$WORK/erro.txt" | tail -n 5 >&2
    exit 1
  fi
fi

# Segurança: a consulta tem que ser só select (confere o arquivo, não o banco).
if grep -iEq '^[^-]*\b(insert|update|delete|drop|alter|truncate|create|grant)\b' "$SQL"; then
  echo "ERRO: $SQL tem comando que grava; nao rode" >&2; exit 1
fi

# CSV -> linhas "escopo/semana/coluna<TAB>valor" (awk, com aspas do CSV desfeitas).
declare -A V
while IFS=$'\t' read -r k v; do V["$k"]="$v"; done < <(tr -d '\r' < "$CSV" | awk '
  function split_csv(line, out,    n, i, c, cur, q) {
    n = 0; cur = ""; q = 0
    for (i = 1; i <= length(line); i++) {
      c = substr(line, i, 1)
      if (q) {
        if (c == "\"") { if (substr(line, i + 1, 1) == "\"") { cur = cur "\""; i++ } else q = 0 }
        else cur = cur c
      } else if (c == "\"") q = 1
      else if (c == ",") { out[++n] = cur; cur = "" }
      else cur = cur c
    }
    out[++n] = cur
    return n
  }
  NR == 1 { nh = split_csv($0, H); next }
  NF || length($0) { split_csv($0, R); for (i = 3; i <= nh; i++) printf "%s/%s/%s\t%s\n", R[1], R[2], H[i], R[i] }
')

if [ -z "${V[sem_interna/fechada/perfis_reais]:-}" ]; then
  echo "ERRO: a saida nao tem a linha sem_interna/fechada (consulta mudou?)" >&2; exit 1
fi

F() { printf '%s' "${V[sem_interna/fechada/$1]:-}"; }
A() { printf '%s' "${V[sem_interna/anterior/$1]:-}"; }
C() { printf '%s' "${V[com_interna/fechada/$1]:-}"; }

# Marcador da semana passada na #86 (o mais recente).
PREV_WAU=""; PREV_DAU=""; PREV_MAU=""; PREV_SEQ_WAU=0; PREV_SEQ_DAU=0; PREV_FIM=""
if [ "$LER_ANTERIOR" -eq 1 ]; then
  MARC=$(gh issue view "$ISSUE" --repo "$REPO" --json comments -q '.comments[].body' 2>/dev/null \
         | tr -d '\r' | grep -o '<!-- medicao-semanal [^>]*-->' | tail -n 1 || true)
  if [ -n "$MARC" ]; then
    g() { printf '%s' "$MARC" | grep -o "$1=[^ ]*" | head -n 1 | cut -d= -f2; }
    PREV_FIM=$(g fim); PREV_WAU=$(g wau); PREV_DAU=$(g dau); PREV_MAU=$(g mau)
    PREV_SEQ_WAU=$(g seq_wau60); PREV_SEQ_DAU=$(g seq_dau30)
    PREV_SEQ_WAU=${PREV_SEQ_WAU:-0}; PREV_SEQ_DAU=${PREV_SEQ_DAU:-0}
  fi
fi

fmt() { [ -n "$1" ] && printf '%s' "$1" || printf -- '-'; }
pct() { [ -n "$1" ] && awk -v x="$1" 'BEGIN { printf "%.0f%%", x * 100 }' || printf -- '-'; }
# var ATUAL ANTERIOR: diferença com sinal, ou "-" se faltar um dos lados.
var() {
  if [ -n "$1" ] && [ -n "$2" ]; then
    awk -v a="$1" -v b="$2" 'BEGIN { d = a - b; printf "%s%s", (d > 0 ? "+" : ""), (d == int(d) ? d : sprintf("%.2f", d)) }'
  else printf -- '-'; fi
}
linha() { printf '| %s | %s | %s | %s |\n' "$1" "$2" "$3" "$4"; }

# Retorno: a consulta devolve contagens (retorno_dN = quem voltou, coorte_dN = base); a taxa sai daqui.
ret() {  # ret VOLTARAM COORTE -> "N de M (P%)" ou "-"
  if [ -n "$1" ] && [ -n "$2" ] && [ "$2" != "0" ]; then
    awk -v r="$1" -v c="$2" 'BEGIN { printf "%d de %d (%.0f%%)", r, c, r * 100 / c }'
  else printf -- '-'; fi
}
RD1_TXT=$(ret "$(F retorno_d1)" "$(F coorte_d1)")
RD7_TXT=$(ret "$(F retorno_d7)" "$(F coorte_d7)")
RD7_PCT=""
[ -n "$(F retorno_d7)" ] && [ -n "$(F coorte_d7)" ] && [ "$(F coorte_d7)" != "0" ] && RD7_PCT=$(awk -v r="$(F retorno_d7)" -v c="$(F coorte_d7)" 'BEGIN { printf "%.4f", r / c }')

# Gatilhos ---------------------------------------------------------------------------------------------
WAU=$(F wau_agora); DAU=$(F dau_agora); MAU=$(F mau_agora)
SEQ_WAU=0; SEQ_DAU=0
[ -n "$WAU" ] && awk -v x="$WAU" 'BEGIN { exit !(x >= 60) }' && SEQ_WAU=$((PREV_SEQ_WAU + 1))
[ -n "$DAU" ] && awk -v x="$DAU" 'BEGIN { exit !(x >= 30) }' && SEQ_DAU=$((PREV_SEQ_DAU + 1))
FIM=$(F semana_fim)

GATILHOS=()
[ "$SEQ_WAU" -ge 2 ] && GATILHOS+=("WAU >= 60 por $SEQ_WAU semanas seguidas: abre a fase 2 e libera o #89")
[ "$SEQ_WAU" -eq 1 ] && GATILHOS+=("WAU >= 60 nesta semana (falta mais 1 semana seguida para abrir a fase 2, #89)")
[ "$SEQ_DAU" -ge 4 ] && GATILHOS+=("DAU >= 30 por $SEQ_DAU semanas seguidas: abre a fase 3 e o #91")
[ "$SEQ_DAU" -ge 1 ] && [ "$SEQ_DAU" -lt 4 ] && GATILHOS+=("DAU >= 30 ha $SEQ_DAU semana(s) (precisa de 4 seguidas, #91)")
if [ -n "$RD7_PCT" ] && awk -v x="$RD7_PCT" 'BEGIN { exit !(x >= 0.45) }'; then
  GATILHOS+=("Retorno em 7 dias >= 45% (meta da fase 1; valor APROXIMADO, conferir na serie exata de docs/medicao-retorno.sql antes de dar como atingido)")
fi
ALERTAS_SQL=$(F alertas)
[ -n "$ALERTAS_SQL" ] && GATILHOS+=("Da consulta: $ALERTAS_SQL")
NAO_MARC=$(F alerta_real_nao_marcada)
if [ -n "$NAO_MARC" ] && [ "$NAO_MARC" != "0" ]; then
  GATILHOS+=("$NAO_MARC compra(s) real(is) de conta nao marcada nos ultimos 7 dias: conferir em dobroes_purchases_com_origem se o comprador e do time (skill beta-testers)")
fi
if [ -n "$WAU" ] && awk -v x="$WAU" 'BEGIN { exit !(x < 15) }'; then
  GATILHOS+=("WAU abaixo de 15 (alerta sugerido pelo PO, ainda nao escrito pelo John)")
fi

# Comentário -------------------------------------------------------------------------------------------
OUT="$WORK/comentario.md"
{
  echo "Medição semanal (rotina da #94): semana de $(F semana_inicio) a $FIM (segunda a domingo, America/Sao_Paulo). Só leitura, só agregados, sem contas de teste nem internas."
  echo
  echo "| Medida | Semana fechada | Semana anterior | Variação |"
  echo "|---|---|---|---|"
  linha "Perfis reais" "$(fmt "$(F perfis_reais)")" "$(fmt "$(A perfis_reais)")" "$(var "$(F perfis_reais)" "$(A perfis_reais)")"
  linha "Novos na semana" "$(fmt "$(F novos)")" "$(fmt "$(A novos)")" "$(var "$(F novos)" "$(A novos)")"
  linha "DAU (hoje)" "$(fmt "$DAU")" "$(fmt "$PREV_DAU")" "$(var "$DAU" "$PREV_DAU")"
  linha "WAU (hoje)" "$(fmt "$WAU")" "$(fmt "$PREV_WAU")" "$(var "$WAU" "$PREV_WAU")"
  linha "MAU (hoje)" "$(fmt "$MAU")" "$(fmt "$PREV_MAU")" "$(var "$MAU" "$PREV_MAU")"
  linha "Retorno 1 dia (aprox.)" "$RD1_TXT" "-" "-"
  linha "Retorno 7 dias (aprox.)" "$RD7_TXT" "-" "-"
  linha "Partidas online finalizadas" "$(fmt "$(F partidas_online_finalizadas)")" "$(fmt "$(A partidas_online_finalizadas)")" "$(var "$(F partidas_online_finalizadas)" "$(A partidas_online_finalizadas)")"
  linha "Partidas ranqueadas" "$(fmt "$(F partidas_ranqueadas)")" "$(fmt "$(A partidas_ranqueadas)")" "$(var "$(F partidas_ranqueadas)" "$(A partidas_ranqueadas)")"
  linha "Contas com push ativo (hoje)" "$(fmt "$(F push_ativos_agora)")" "-" "-"
  for k in real premio test ad; do
    linha "Compras $k" "$(fmt "$(F compras_$k)")" "$(fmt "$(A compras_$k)")" "$(var "$(F compras_$k)" "$(A compras_$k)")"
  done
  linha "Vales iniciados" "$(fmt "$(F vales_iniciados)")" "$(fmt "$(A vales_iniciados)")" "$(var "$(F vales_iniciados)" "$(A vales_iniciados)")"
  linha "Vales aplicados" "$(fmt "$(F vales_aplicados)")" "$(fmt "$(A vales_aplicados)")" "$(var "$(F vales_aplicados)" "$(A vales_aplicados)")"
  linha "Vales pendentes" "$(fmt "$(F vales_pendentes)")" "$(fmt "$(A vales_pendentes)")" "$(var "$(F vales_pendentes)" "$(A vales_pendentes)")"
  linha "Contas que gastaram dobrões" "$(fmt "$(F contas_gastaram_dobroes)")" "$(fmt "$(A contas_gastaram_dobroes)")" "$(var "$(F contas_gastaram_dobroes)" "$(A contas_gastaram_dobroes)")"
  linha "Receita estimada bruta (R\$)" "$(fmt "$(F receita_bruta_brl)")" "$(fmt "$(A receita_bruta_brl)")" "$(var "$(F receita_bruta_brl)" "$(A receita_bruta_brl)")"
  linha "Receita estimada líquida (R\$, x 0,85)" "$(fmt "$(F receita_liquida_brl)")" "$(fmt "$(A receita_liquida_brl)")" "$(var "$(F receita_liquida_brl)" "$(A receita_liquida_brl)")"
  linha "Receita bruta acumulada (R\$)" "$(fmt "$(F receita_bruta_acum_brl)")" "$(fmt "$(A receita_bruta_acum_brl)")" "$(var "$(F receita_bruta_acum_brl)" "$(A receita_bruta_acum_brl)")"
  echo
  echo "Com as contas internas (só para comparar): perfis $(fmt "$(C perfis_reais)"), WAU $(fmt "$(C wau_agora)"), compras reais $(fmt "$(C compras_real)"), receita bruta R\$ $(fmt "$(C receita_bruta_brl)")."
  echo
  echo "Premissas: DAU/WAU/MAU e retorno vêm de \`last_seen_at\` no momento da consulta e superestimam (a série exata fica em \`docs/medicao-retorno.sql\`); receita é preço de tabela das compras \`real\`, sem anúncio e sem valor cobrado gravado; a venda real está \`real_billing_enabled = $(fmt "$(F real_billing_enabled)")\` (só lido). Série de atividade desde: $(fmt "$(F serie_atividade_desde)")."
  echo
  if [ "${#GATILHOS[@]}" -gt 0 ]; then
    echo "**GATILHO** (aviso ao PO, que decide o próximo passo; nada foi ligado):"
    for g in "${GATILHOS[@]}"; do echo "- $g"; done
  else
    echo "Nenhum gatilho do plano atingido nesta semana."
  fi
  echo
  echo "<!-- medicao-semanal fim=$FIM dau=${DAU:-0} wau=${WAU:-0} mau=${MAU:-0} seq_wau60=$SEQ_WAU seq_dau30=$SEQ_DAU -->"
} > "$OUT"

cat "$OUT"

if [ "$POSTAR" -eq 1 ]; then
  if gh issue comment "$ISSUE" --repo "$REPO" --body-file "$OUT" >/dev/null; then
    echo "Comentario postado na #$ISSUE." >&2
  else
    echo "ERRO: nao consegui postar na #$ISSUE (gh logado?)" >&2; exit 1
  fi
fi
